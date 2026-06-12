package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class RealtimeSessionClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createSessionUsesGaClientSecretEndpointAndPayload() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> safetyIdentifierHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            safetyIdentifierHeader.set(exchange.getRequestHeaders().getFirst("OpenAI-Safety-Identifier"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"value\":\"ek_test_secret\",\"session\":{\"type\":\"realtime\"}}");
        });

        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeModel("gpt-realtime");
        props.setRealtimeClientSecretUrl(serverUrl("/client_secrets"));
        props.setRealtimeCallsUrl("https://example.test/v1/realtime/calls");
        props.setRealtimeSafetyIdentifier("hashed-demo-user");

        RealtimeSessionInfo sessionInfo = new RealtimeSessionClient(props).createSession();

        assertEquals("ek_test_secret", sessionInfo.getClientSecret());
        assertEquals("gpt-realtime", sessionInfo.getModel());
        assertEquals("https://example.test/v1/realtime/calls", sessionInfo.getRealtimeCallsUrl());
        assertEquals("POST", requestMethod.get());
        assertEquals("Bearer test-api-key", authorizationHeader.get());
        assertEquals("hashed-demo-user", safetyIdentifierHeader.get());

        JsonObject payload = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        JsonObject session = payload.getAsJsonObject("session");
        assertEquals("realtime", session.get("type").getAsString());
        assertEquals("gpt-realtime", session.get("model").getAsString());
        assertTrue(session.getAsJsonArray("output_modalities").contains(new JsonPrimitive("audio")));
        assertEquals("whisper-1",
                session.getAsJsonObject("audio")
                        .getAsJsonObject("input")
                        .getAsJsonObject("transcription")
                        .get("model")
                        .getAsString());
        assertFalse(payload.has("modalities"));
        assertFalse(payload.has("input_audio_transcription"));
    }

    @Test
    void createTranscriptionSessionUsesGaTranscriptionPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"value\":\"ek_transcription_secret\",\"session\":{\"type\":\"transcription\"}}");
        });

        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeTranscriptionModel("gpt-realtime-whisper");
        props.setRealtimeTranscriptionLanguage("en");
        props.setRealtimeTranscriptionDelay("low");
        props.setRealtimeClientSecretUrl(serverUrl("/client_secrets"));
        props.setRealtimeCallsUrl("https://example.test/v1/realtime/calls");

        RealtimeSessionInfo sessionInfo = new RealtimeSessionClient(props).createTranscriptionSession();

        assertEquals("ek_transcription_secret", sessionInfo.getClientSecret());
        assertEquals("gpt-realtime-whisper", sessionInfo.getModel());
        assertEquals("https://example.test/v1/realtime/calls", sessionInfo.getRealtimeCallsUrl());

        JsonObject payload = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        JsonObject session = payload.getAsJsonObject("session");
        assertEquals("transcription", session.get("type").getAsString());
        JsonObject audioInput = session.getAsJsonObject("audio").getAsJsonObject("input");
        assertFalse(audioInput.has("turn_detection"));
        JsonObject transcription = audioInput.getAsJsonObject("transcription");
        assertEquals("gpt-realtime-whisper", transcription.get("model").getAsString());
        assertEquals("en", transcription.get("language").getAsString());
        assertEquals("low", transcription.get("delay").getAsString());
        assertFalse(session.has("model"));
        assertFalse(session.has("output_modalities"));
        assertFalse(payload.has("input_audio_transcription"));
    }

    @Test
    void createSessionFailsLoudlyOnHttpError() throws Exception {
        startServer(exchange -> writeResponse(exchange, 400, "{\"error\":{\"code\":\"bad_request\"}}"));

        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeClientSecretUrl(serverUrl("/client_secrets"));
        props.setRealtimeCallsUrl("https://example.test/v1/realtime/calls");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new RealtimeSessionClient(props).createSession());

        assertEquals("unable to create realtime session", thrown.getMessage());
        assertTrue(thrown.getCause().getMessage().contains("http request returned status code: 400"));
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client_secrets", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String serverUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
