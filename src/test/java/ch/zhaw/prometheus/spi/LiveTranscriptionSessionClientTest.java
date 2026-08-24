package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.InputLanguage;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.NoiseReduction;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TranscriptionDelay;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnDetection;
import ch.zhaw.prometheus.application.LiveTranscriptionSettings.TurnMode;

class LiveTranscriptionSessionClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    @Test
    void createsEphemeralSessionWithSafeHeadersAndExactTypedPayload() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> safetyIdentifier = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            safetyIdentifier.set(exchange.getRequestHeaders().getFirst("OpenAI-Safety-Identifier"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"value\":\"ek_live_secret\"}");
        });
        OpenAIProperties properties = properties();
        properties.setRealtimeSafetyIdentifier("stable-hash");

        LiveTranscriptionSessionInfo result = new LiveTranscriptionSessionClient(properties,
                new LiveTranscriptionProviderPayloadBuilder()).createSession(settings());

        assertEquals("ek_live_secret", result.clientSecret());
        assertEquals("gpt-live-transcribe", result.model());
        assertEquals("https://example.test/v1/realtime/calls", result.webRtcUrl());
        assertEquals("Bearer test-key", authorization.get());
        assertEquals("stable-hash", safetyIdentifier.get());
        JsonObject payload = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals("transcription", payload.getAsJsonObject("session").get("type").getAsString());
        assertEquals("gpt-live-transcribe", payload.getAsJsonObject("session")
                .getAsJsonObject("audio").getAsJsonObject("input")
                .getAsJsonObject("transcription").get("model").getAsString());
        assertFalse(body.get().contains("test-key"));
        assertFalse(body.get().contains("stable-hash"));
    }

    @Test
    void providerFailureDoesNotExposeProviderBody() throws Exception {
        startServer(exchange -> writeResponse(exchange, 400, "provider-private-sentinel"));
        LiveTranscriptionSessionClient client = new LiveTranscriptionSessionClient(properties(),
                new LiveTranscriptionProviderPayloadBuilder());

        LiveTranscriptionProviderException failure = assertThrows(LiveTranscriptionProviderException.class,
                () -> client.createSession(settings()));

        assertFalse(failure.getMessage().contains("provider-private-sentinel"));
    }

    private OpenAIProperties properties() {
        OpenAIProperties properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai");
        properties.setKey("test-key");
        properties.setRealtimeClientSecretUrl(serverUrl());
        properties.setRealtimeCallsUrl("https://example.test/v1/realtime/calls");
        return properties;
    }

    private static LiveTranscriptionSettings settings() {
        return new LiveTranscriptionSettings(
                new TurnDetection(TurnMode.LOCAL_VAD, 1.5),
                NoiseReduction.FAR_FIELD,
                "PROMETHEUS cockpit",
                List.of("Valerian"),
                List.of(InputLanguage.EN),
                TranscriptionDelay.MEDIUM);
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/client_secrets", exchange -> handler.handle(exchange));
        this.server.start();
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/client_secrets";
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
