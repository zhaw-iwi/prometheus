package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

class OpenAISpeechSynthesisGatewayUnitTest {
    @Test
    void deliversAndFlushesFirstProviderChunkWhileTailIsWithheld() throws Exception {
        var releaseTail = new java.util.concurrent.CountDownLatch(1);
        var flushed = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/progressive", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(7); out.flush();
                try { releaseTail.await(5, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                out.write(new byte[] {8, 9});
            }
        });
        var properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai"); properties.setKey("test");
        var speech = new SpeechSynthesisProperties();
        speech.setUrl("http://localhost:" + server.getAddress().getPort() + "/progressive");
        var sink = new java.io.ByteArrayOutputStream() {
            @Override public void flush() { flushed.countDown(); }
        };
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
             var audio = new OpenAISpeechSynthesisGateway(properties, speech).synthesize("Canonical", "marin", 1)) {
            var writing = executor.submit(() -> { audio.writeTo(sink); return true; });
            try {
                org.junit.jupiter.api.Assertions.assertTrue(flushed.await(5, java.util.concurrent.TimeUnit.SECONDS));
                org.junit.jupiter.api.Assertions.assertFalse(writing.isDone());
                assertArrayEquals(new byte[] {7}, sink.toByteArray());
            } finally { releaseTail.countDown(); }
            writing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertArrayEquals(new byte[] {7, 8, 9}, sink.toByteArray());
        } finally { releaseTail.countDown(); }
    }

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseType = new AtomicReference<>("audio/mpeg");
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/v1/audio/speech", exchange -> {
            this.requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            this.authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (this.responseStatus.get() != 200) {
                byte[] failure = "private provider failure".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(this.responseStatus.get(), failure.length);
                exchange.getResponseBody().write(failure);
                exchange.close();
                return;
            }
            byte[] response = new byte[] { 7, 8, 9 };
            exchange.getResponseHeaders().set("Content-Type", responseType.get());
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        this.server.start();
    }

    @AfterEach
    void stopServer() {
        this.server.stop(0);
    }

    @Test
    void sendsExactTextAndSpeechOptionsToOpenAiSpeech() {
        OpenAIProperties properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai");
        properties.setKey("test-key");
        SpeechSynthesisProperties speechProperties = new SpeechSynthesisProperties();
        speechProperties.setModel("gpt-4o-mini-tts");
        speechProperties.setUrl("http://localhost:" + this.server.getAddress().getPort() + "/v1/audio/speech");
        OpenAISpeechSynthesisGateway gateway = new OpenAISpeechSynthesisGateway(properties, speechProperties);
        String exactText = "  Gr\u00fcezi, \"Z\u00fcrich\"!\nLine two.  ";

        SpeechAudio audio = gateway.synthesize(exactText, "marin", 1.25);

        assertArrayEquals(new byte[] { 7, 8, 9 }, audio.getContent());
        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals(-1L, audio.getContentLength());
        assertEquals("Bearer test-key", this.authorization.get());
        JsonObject payload = JsonParser.parseString(this.requestBody.get()).getAsJsonObject();
        assertEquals("gpt-4o-mini-tts", payload.get("model").getAsString());
        assertEquals(exactText, payload.get("input").getAsString());
        assertEquals("marin", payload.get("voice").getAsString());
        assertEquals("mp3", payload.get("response_format").getAsString());
        assertEquals(1.25, payload.get("speed").getAsDouble(), 0.0001);
    }

    @Test
    void requestsPcmAndLabelsRawSamplesWithoutBufferingOrAcceptingAnotherCodec() throws IOException {
        OpenAIProperties properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai"); properties.setKey("test-key");
        SpeechSynthesisProperties speech = new SpeechSynthesisProperties();
        speech.setUrl("http://localhost:" + this.server.getAddress().getPort() + "/v1/audio/speech");
        OpenAISpeechSynthesisGateway gateway = new OpenAISpeechSynthesisGateway(properties, speech);
        this.responseType.set("application/octet-stream");
        try (SpeechAudio audio = gateway.synthesize("Canonical", "cedar", 1.25, SpeechAudioFormat.PCM)) {
            assertEquals(SpeechAudioFormat.PCM.contentType(), audio.getContentType());
            assertArrayEquals(new byte[] { 7, 8, 9 }, audio.getContent());
            JsonObject payload = JsonParser.parseString(requestBody.get()).getAsJsonObject();
            assertEquals("pcm", payload.get("response_format").getAsString());
            assertEquals("Canonical", payload.get("input").getAsString());
            assertEquals("cedar", payload.get("voice").getAsString());
            assertEquals(1.25, payload.get("speed").getAsDouble());
        }
        this.responseType.set("audio/mpeg");
        assertThrows(SpeechSynthesisException.class,
                () -> gateway.synthesize("Canonical", "cedar", 1, SpeechAudioFormat.PCM));
    }

    @Test
    void translatesProviderFailureWithoutReturningItsBody() {
        this.responseStatus.set(429);
        OpenAIProperties properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai");
        properties.setKey("test-key");
        SpeechSynthesisProperties speechProperties = new SpeechSynthesisProperties();
        speechProperties.setUrl("http://localhost:" + this.server.getAddress().getPort() + "/v1/audio/speech");
        OpenAISpeechSynthesisGateway gateway = new OpenAISpeechSynthesisGateway(properties, speechProperties);

        SpeechSynthesisException failure = assertThrows(SpeechSynthesisException.class,
                () -> gateway.synthesize("Canonical", "cedar", 1.0));
        assertEquals("OpenAI Speech request returned status code 429", failure.getMessage());
    }
}
