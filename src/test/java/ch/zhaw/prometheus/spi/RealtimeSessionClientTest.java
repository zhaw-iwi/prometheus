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
    void createCallUsesUnifiedWebRtcEndpointAndPromptedSessionPayload() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> safetyIdentifierHeader = new AtomicReference<>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            safetyIdentifierHeader.set(exchange.getRequestHeaders().getFirst("OpenAI-Safety-Identifier"));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Location", "/v1/realtime/calls/rtc_test_call");
            writeResponse(exchange, 200, "answer-sdp");
        }, "/calls");

        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeModel("gpt-realtime-2");
        props.setRealtimeClientSecretUrl(serverUrl("/client_secrets"));
        props.setRealtimeCallsUrl(serverUrl("/calls"));
        props.setRealtimeSafetyIdentifier("hashed-demo-user");

        RealtimeCallInfo callInfo = new RealtimeSessionClient(props).createCall("offer-sdp",
                new RealtimeCallConfig("system: PROMETHEUS instructions", "marin", "server_vad"));

        assertEquals("answer-sdp", callInfo.getSdp());
        assertEquals("gpt-realtime-2", callInfo.getModel());
        assertEquals("rtc_test_call", callInfo.getCallId());
        assertTrue(callInfo.getSidebandUrl().contains("call_id=rtc_test_call"));
        assertTrue(callInfo.getSidebandUrl().startsWith("ws://127.0.0.1:"));
        assertEquals("POST", requestMethod.get());
        assertEquals("Bearer test-api-key", authorizationHeader.get());
        assertEquals("hashed-demo-user", safetyIdentifierHeader.get());
        assertTrue(contentTypeHeader.get().startsWith("multipart/form-data; boundary="));

        String body = requestBody.get();
        assertTrue(body.contains("Content-Disposition: form-data; name=\"sdp\""));
        assertTrue(body.contains("offer-sdp"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"session\""));
        String sessionJson = body.substring(body.indexOf("{\"type\":\"realtime\""));
        sessionJson = sessionJson.substring(0, sessionJson.indexOf("\r\n--"));
        JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
        assertEquals("realtime", session.get("type").getAsString());
        assertEquals("gpt-realtime-2", session.get("model").getAsString());
        assertEquals("system: PROMETHEUS instructions", session.get("instructions").getAsString());
        assertTrue(session.getAsJsonArray("output_modalities").contains(new JsonPrimitive("audio")));
        JsonObject audio = session.getAsJsonObject("audio");
        assertEquals("whisper-1",
                audio.getAsJsonObject("input")
                        .getAsJsonObject("transcription")
                        .get("model")
                        .getAsString());
        JsonObject turnDetection = audio.getAsJsonObject("input").getAsJsonObject("turn_detection");
        assertEquals("server_vad", turnDetection.get("type").getAsString());
        assertFalse(turnDetection.get("create_response").getAsBoolean());
        assertFalse(turnDetection.get("interrupt_response").getAsBoolean());
        assertEquals("marin", audio.getAsJsonObject("output").get("voice").getAsString());
        assertFalse(session.has("modalities"));
        assertFalse(session.has("input_audio_transcription"));
    }

    @Test
    void createTranscriptionSessionUsesGaTranscriptionPayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "{\"value\":\"ek_transcription_secret\",\"session\":{\"type\":\"transcription\"}}");
        }, "/client_secrets");

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
    void createCallFailsLoudlyOnHttpError() throws Exception {
        startServer(exchange -> writeResponse(exchange, 400, "{\"error\":{\"code\":\"bad_request\"}}"), "/calls");

        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeClientSecretUrl(serverUrl("/client_secrets"));
        props.setRealtimeCallsUrl(serverUrl("/calls"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new RealtimeSessionClient(props).createCall("offer-sdp",
                        new RealtimeCallConfig("instructions", null, "server_vad")));

        assertEquals("unable to create realtime call", thrown.getMessage());
        assertTrue(thrown.getCause().getMessage().contains("http request returned status code: 400"));
    }

    private void startServer(ExchangeHandler handler, String path) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
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
