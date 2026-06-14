package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OpenAIAudioClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transcribePostsMultipartAudioWithModelAndLanguage() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> safetyIdentifierHeader = new AtomicReference<>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            safetyIdentifierHeader.set(exchange.getRequestHeaders().getFirst("OpenAI-Safety-Identifier"));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            writeResponse(exchange, 200, "application/json", "{\"text\":\"Bereit.\"}".getBytes(StandardCharsets.UTF_8));
        }, "/transcriptions");

        OpenAIProperties props = baseProperties();
        props.setAudioTranscriptionsUrl(serverUrl("/transcriptions"));
        props.setRecordedSpeechTranscriptionModel("gpt-4o-transcribe");

        String transcript = new OpenAIAudioClient(props)
                .transcribe("audio-bytes".getBytes(StandardCharsets.UTF_8), "turn.webm", "audio/webm", "de");

        assertEquals("Bereit.", transcript);
        assertEquals("Bearer test-api-key", authorizationHeader.get());
        assertEquals("hashed-demo-user", safetyIdentifierHeader.get());
        assertTrue(contentTypeHeader.get().startsWith("multipart/form-data; boundary="));
        String body = new String(requestBody.get(), StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"model\""));
        assertTrue(body.contains("gpt-4o-transcribe"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"language\""));
        assertTrue(body.contains("de"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"file\"; filename=\"turn.webm\""));
        assertTrue(body.contains("Content-Type: audio/webm"));
        assertTrue(body.contains("audio-bytes"));
    }

    @Test
    void createSpeechPostsJsonAndReturnsAudioBytes() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        byte[] audio = new byte[] { 1, 2, 3 };
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "audio/mpeg", audio);
        }, "/speech");

        OpenAIProperties props = baseProperties();
        props.setAudioSpeechUrl(serverUrl("/speech"));
        props.setSpeechModel("gpt-4o-mini-tts");

        GeneratedSpeechAudio generated = new OpenAIAudioClient(props).createSpeech("Hallo.", "marin");

        assertArrayEquals(audio, generated.getBytes());
        assertEquals("audio/mpeg", generated.getContentType());
        JsonObject payload = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("gpt-4o-mini-tts", payload.get("model").getAsString());
        assertEquals("Hallo.", payload.get("input").getAsString());
        assertEquals("marin", payload.get("voice").getAsString());
        assertEquals("mp3", payload.get("response_format").getAsString());
    }

    private static OpenAIProperties baseProperties() {
        OpenAIProperties props = new OpenAIProperties();
        props.setOpenaivsazureopenai("openai");
        props.setKey("test-api-key");
        props.setRealtimeSafetyIdentifier("hashed-demo-user");
        return props;
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

    private static void writeResponse(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
