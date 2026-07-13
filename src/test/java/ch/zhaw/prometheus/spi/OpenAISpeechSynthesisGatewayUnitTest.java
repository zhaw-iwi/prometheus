package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

class OpenAISpeechSynthesisGatewayUnitTest {
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/v1/audio/speech", exchange -> {
            this.requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            this.authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = new byte[] { 7, 8, 9 };
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, response.length);
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
        TalkToMeSpeechProperties speechProperties = new TalkToMeSpeechProperties();
        speechProperties.setModel("gpt-4o-mini-tts");
        speechProperties.setUrl("http://localhost:" + this.server.getAddress().getPort() + "/v1/audio/speech");
        OpenAISpeechSynthesisGateway gateway = new OpenAISpeechSynthesisGateway(properties, speechProperties);
        String exactText = "  Gr\u00fcezi, \"Z\u00fcrich\"!\nLine two.  ";

        SpeechAudio audio = gateway.synthesize(exactText, "marin", 1.25);

        assertArrayEquals(new byte[] { 7, 8, 9 }, audio.getContent());
        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals("Bearer test-key", this.authorization.get());
        JsonObject payload = JsonParser.parseString(this.requestBody.get()).getAsJsonObject();
        assertEquals("gpt-4o-mini-tts", payload.get("model").getAsString());
        assertEquals(exactText, payload.get("input").getAsString());
        assertEquals("marin", payload.get("voice").getAsString());
        assertEquals("mp3", payload.get("response_format").getAsString());
        assertEquals(1.25, payload.get("speed").getAsDouble(), 0.0001);
    }
}
