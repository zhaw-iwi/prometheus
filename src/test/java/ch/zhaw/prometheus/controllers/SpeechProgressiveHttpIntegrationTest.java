package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.logging.*;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SpeechProgressiveHttpIntegrationTest.Fixture.class,
        properties = "management.endpoints.enabled-by-default=false")
class SpeechProgressiveHttpIntegrationTest {
    @LocalServerPort int port;
    static volatile String providerUrl;
    static volatile SpeechDeliveryTrace deliveryTrace;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({Controller.class, LatencyTraceFilter.class, LatencyResponseAdvice.class})
    static class Fixture {}

    @RestController
    static class Controller {
        @PostMapping("/fixture-turn")
        java.util.Map<String, Boolean> turn() {
            return LatencyTrace.measure("acknowledge", () -> {
                LatencyTrace.record("inference", 12, true, "fixture-request", "DECISION", "fixture-model", "none", 20, 2);
                return java.util.Map.of("active", true);
            });
        }
        @PostMapping("/fixture-speech")
        ResponseEntity<StreamingResponseBody> speech(@RequestParam(defaultValue = "mp3") String format) {
            var properties = new OpenAIProperties(); properties.setKey("fixture"); properties.setOpenaivsazureopenai("openai");
            var speech = new SpeechSynthesisProperties(); speech.setUrl(providerUrl);
            var audio = new OpenAISpeechSynthesisGateway(properties, speech).synthesize("Canonical", "marin", 1, SpeechAudioFormat.parse(format));
            deliveryTrace = new SpeechDeliveryTrace();
            return SpeechAudioHttpResponse.stream(audio, deliveryTrace);
        }
        @GetMapping("/fixture-speech-timing")
        SpeechDeliveryTrace.Snapshot timing() { return deliveryTrace.snapshot(); }
    }

    @Test void jsonTimingHeadersArePresentOnTheWireWithoutChangingTheBody() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/fixture-turn"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("{\"active\":true}", response.body());
        String timing = new String(java.util.Base64.getDecoder().decode(
                response.headers().firstValue(LatencyTrace.TIMING_HEADER).orElseThrow()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(timing.contains("fixture-model"));
        assertTrue(timing.contains("acknowledge"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(SpeechAudioFormat.class)
    void tomcatDeliversFirstByteBeforeProviderEof(SpeechAudioFormat format) throws Exception {
        var tail = new CountDownLatch(1);
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/speech", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", format.contentType());
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(7); out.flush();
                try { if (!tail.await(10, TimeUnit.SECONDS)) return; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                out.write(9);
            }
        });
        provider.start();
        providerUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/speech";
        try (var client = HttpClient.newHttpClient(); var executor = Executors.newSingleThreadExecutor()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fixture-speech?format=" + format.wireValue()))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertEquals(format.contentType(), response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(deliveryTrace.id().toString(), response.headers().firstValue(SpeechDeliveryTrace.HEADER).orElseThrow());
            String timing = new String(java.util.Base64.getDecoder().decode(
                    response.headers().firstValue(LatencyTrace.TIMING_HEADER).orElseThrow()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(timing.contains("speech_headers"));
            try (var stream = response.body()) {
                assertEquals(7, executor.submit(() -> { return stream.read(); }).get(5, TimeUnit.SECONDS));
                assertEquals(1, tail.getCount());
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                        .untilAsserted(() -> assertEquals(1, deliveryTrace.snapshot().bytesFlushed()));
                assertEquals("streaming", deliveryTrace.snapshot().status());
                assertNull(deliveryTrace.snapshot().finishedMs());
                tail.countDown();
                assertArrayEquals(new byte[] {9}, stream.readAllBytes());
            }
            var detail = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fixture-speech-timing"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            var json = com.google.gson.JsonParser.parseString(detail.body()).getAsJsonObject();
            assertEquals("complete", json.get("status").getAsString());
            assertEquals(2, json.get("bytesRead").getAsLong()); assertEquals(2, json.get("bytesFlushed").getAsLong());
        } finally { tail.countDown(); provider.stop(0); }
    }
}
