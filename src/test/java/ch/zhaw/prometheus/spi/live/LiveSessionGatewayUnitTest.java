package ch.zhaw.prometheus.spi.live;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import ch.zhaw.prometheus.spi.OpenAIProperties;

class LiveSessionGatewayUnitTest {
    @Test void heartbeatTimesOutSilentTransportButAcceptsMatchingPongWithoutSpeech() {
        var socket = mock(WebSocket.class); var lost = new AtomicBoolean();
        var payload = new AtomicReference<java.nio.ByteBuffer>();
        when(socket.sendPing(any())).thenAnswer(call -> { payload.set(((java.nio.ByteBuffer) call.getArgument(0)).duplicate()); return java.util.concurrent.CompletableFuture.completedFuture(socket); });
        var sideband = new OpenAILiveSessionGateway.Sideband(ignored -> {}, () -> lost.set(true), 100);
        sideband.onOpen(socket); sideband.heartbeat(1000); sideband.onPong(socket, payload.get());
        sideband.heartbeat(11000); sideband.heartbeat(20999); assertFalse(lost.get());
        sideband.heartbeat(21000); assertTrue(lost.get()); assertFalse(sideband.isOpen()); verify(socket).abort();
    }
    HttpServer server;
    HttpClient client;
    LiveProperties properties;
    OpenAILiveSessionGateway gateway;
    final AtomicReference<JsonObject> request = new AtomicReference<>();
    final List<String> paths = new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile int status = 201;
    volatile String response = "{\"session\":{\"id\":\"live_test\"},\"transport\":{\"type\":\"webrtc\",\"sdp\":\"v=0 answer\"}}";

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/live/sessions", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            String content = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty()) request.set(JsonParser.parseString(content).getAsJsonObject());
            byte[] bytes = (exchange.getRequestURI().getPath().endsWith("/hangup") ? "{}" : response).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        properties = new LiveProperties();
        properties.setSessionsUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/live/sessions");
        OpenAIProperties credentials = new OpenAIProperties();
        credentials.setOpenaivsazureopenai("openai"); credentials.setKey("synthetic-test-key");
        client = HttpClient.newHttpClient();
        gateway = new OpenAILiveSessionGateway(credentials, properties, client);
    }
    @AfterEach void close() { server.stop(0); client.close(); }
    LiveSessionGateway.Request offer() { return new LiveSessionGateway.Request("v=0 offer", "marin", "Diagnostic only", new JsonArray()); }

    @Test void createsClientDelegatedNonStoredWebRtcSession() {
        var session = gateway.create(offer());
        assertEquals("live_test", session.id()); assertEquals("v=0 answer", session.sdp());
        var body = request.get(); var config = body.getAsJsonObject("session");
        assertEquals("gpt-live-1", config.get("model").getAsString());
        assertFalse(config.get("store").getAsBoolean());
        assertEquals("client", config.getAsJsonObject("delegation").get("type").getAsString());
        assertEquals("marin", config.getAsJsonObject("audio").getAsJsonObject("output").get("voice").getAsString());
        assertEquals("webrtc", body.getAsJsonObject("transport").get("type").getAsString());
        assertFalse(session.toString().contains("answer")); assertFalse(offer().toString().contains("Diagnostic"));
    }
    @Test void rejectedOrMalformedResponsesDoNotExposeProviderBodyAndMalformedSdpIsHungUp() {
        status = 403; response = "private-provider-sentinel";
        var error = assertThrows(LiveProviderException.class, () -> gateway.create(offer()));
        assertFalse(error.toString().contains(response));
        status = 201; response = "{\"session\":{\"id\":\"live_orphan\"},\"transport\":{\"sdp\":\"invalid\"}}";
        assertThrows(LiveProviderException.class, () -> gateway.create(offer()));
        assertTrue(paths.contains("/live/sessions/live_orphan/hangup"));
        assertThrows(IllegalArgumentException.class, () -> gateway.hangup("../escape"));
    }
    @Test void responseSizeAndSlowBodyAreBounded() {
        response = "x".repeat(140000);
        assertThrows(LiveProviderException.class, () -> gateway.create(offer()));
        server.removeContext("/live/sessions");
        server.createContext("/live/sessions", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
            // Deliberately leave the response open: the caller must bound the body, not just headers.
        });
        properties.setRequestTimeoutMs(100);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
                () -> assertThrows(LiveProviderException.class, () -> gateway.create(offer())));
    }
    @Test void sidebandAssemblesFragmentsAndFailsClosedOnInvalidOrOversizedEvents() {
        var socket = mock(WebSocket.class);
        var events = new ArrayList<JsonObject>(); var disconnected = new AtomicBoolean();
        var sideband = new OpenAILiveSessionGateway.Sideband(events::add, () -> disconnected.set(true), 100);
        sideband.onOpen(socket);
        sideband.onText(socket, "{\"type\":\"session.", false);
        assertTrue(events.isEmpty());
        sideband.onText(socket, "started\"}", true);
        assertEquals("session.started", events.getFirst().get("type").getAsString());
        sideband.onText(socket, "x".repeat(262145), false);
        assertTrue(disconnected.get()); assertFalse(sideband.isOpen()); verify(socket).abort();
        var invalid = new OpenAILiveSessionGateway.Sideband(events::add, () -> {}, 100);
        invalid.onOpen(socket); invalid.onText(socket, "not json", true); assertFalse(invalid.isOpen());
    }
}
