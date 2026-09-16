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
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SpeechProgressiveHttpIntegrationTest.Fixture.class,
        properties = "management.endpoints.enabled-by-default=false")
class SpeechProgressiveHttpIntegrationTest {
    @LocalServerPort int port;
    static volatile String providerUrl;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import(Controller.class)
    static class Fixture {}

    @RestController
    static class Controller {
        @PostMapping("/fixture-speech")
        ResponseEntity<StreamingResponseBody> speech() {
            var properties = new OpenAIProperties(); properties.setKey("fixture"); properties.setOpenaivsazureopenai("openai");
            var speech = new SpeechSynthesisProperties(); speech.setUrl(providerUrl);
            return SpeechAudioHttpResponse.stream(new OpenAISpeechSynthesisGateway(properties, speech).synthesize("Canonical", "marin", 1));
        }
    }

    @Test void tomcatDeliversFirstByteBeforeProviderEof() throws Exception {
        var tail = new CountDownLatch(1);
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/speech", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
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
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fixture-speech"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(5, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            try (var stream = response.body()) {
                assertEquals(7, executor.submit(() -> { return stream.read(); }).get(5, TimeUnit.SECONDS));
                assertEquals(1, tail.getCount());
                tail.countDown();
                assertArrayEquals(new byte[] {9}, stream.readAllBytes());
            }
        } finally { tail.countDown(); provider.stop(0); }
    }
}
