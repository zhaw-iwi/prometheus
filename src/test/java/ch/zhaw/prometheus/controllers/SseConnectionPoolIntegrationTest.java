package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import fixtures.gptlive.LiveSmokeConfiguration;

/** Real servlet async lifecycle: deliberately no test-owned transaction or mocked SSE. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.runtime.tick.enabled=false", "prometheus.sse.heartbeat.delay-ms=100",
        "spring.datasource.hikari.maximum-pool-size=10", "spring.datasource.hikari.connection-timeout=1500"})
@Import(LiveSmokeConfiguration.class)
class SseConnectionPoolIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AccessCodeRepository codes;
    @Autowired HikariDataSource pool;
    private static final String TYPE = "core.facial_expression_sensitivity";
    record Group(String code, UUID accessId, String path) {}

    @Test void eightCockpitsLeaveConnectionsAvailableForLoginCreationInteractionAndReplay() throws Exception {
        var groups = new ArrayList<Group>();
        var streams = new ArrayList<Stream>();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            try {
                for (int i = 0; i < 8; i++) groups.add(create(client));
                for (int round = 0; round < 2; round++) {
                    for (Group group : groups) {
                        String projection = round == 0 ? "?projection=conversation" : "";
                        Stream behaviour = open(client, group, "/behaviour/stream" + projection, null);
                        streams.add(behaviour);
                        Frame initial = behaviour.next("behaviour-replay");
                        assertNotNull(initial.id());
                        Stream monitor = open(client, group, "/monitor/stream", null);
                        streams.add(monitor);
                        monitor.next("snapshot");
                    }
                    assertEquals(16, streams.size());
                    idle();
                    // A new login and creation must work while all eight cockpits stay connected.
                    assertEquals(200, request(client, "POST", "/demo/session", groups.getFirst().code(),
                            Map.of("accessCode", groups.getFirst().code())).statusCode());
                    Group extra = create(client);
                    assertEquals(204, request(client, "DELETE", extra.path(), extra.code(), null).statusCode());
                    codes.deleteById(extra.accessId());
                    for (int i = 0; i < groups.size(); i++) {
                        Group group = groups.get(i);
                        Stream behaviour = streams.get(i * 2);
                        Stream monitor = streams.get(i * 2 + 1);
                        assertEquals(200, request(client, "POST", group.path() + "/acknowledge", group.code(),
                                Map.of("type", "obs.user_utterance", "actor", "user", "kind", "observation",
                                        "payload", "Hello from classroom " + round)).statusCode());
                        assertEquals(200, request(client, "POST", group.path() + "/behaviour/generate",
                                group.code(), Map.of()).statusCode());
                        Frame live = behaviour.next("behaviour-live");
                        assertNotNull(live.id());
                        assertEquals("resp.behaviour_plan", json.readTree(live.data()).get("type").asText());
                        monitor.next("snapshot");
                        assertEquals(200, request(client, "POST", group.path() + "/behaviours/" + live.id()
                                + "/speech?format=pcm", group.code(), Map.of()).statusCode());
                        // Close, generate while disconnected, then resume from the exact persisted ID.
                        behaviour.close();
                        assertEquals(200, request(client, "POST", group.path() + "/behaviour/generate",
                                group.code(), Map.of()).statusCode());
                        Stream replacement = open(client, group, "/behaviour/stream", live.id());
                        streams.set(i * 2, replacement);
                        Frame replay = replacement.next("behaviour-replay");
                        assertNotEquals(live.id(), replay.id());
                        assertEquals(200, request(client, "POST", group.path() + "/behaviours/" + replay.id()
                                + "/speech?format=pcm", group.code(), Map.of()).statusCode());
                    }
                    idle();
                    for (Stream stream : streams) stream.close();
                    streams.clear();
                    idle();
                }
                // Scope rejection must not reserve a connection or expose another group's history.
                assertEquals(404, request(client, "GET", groups.getFirst().path() + "/behaviour/stream",
                        groups.getLast().code(), null).statusCode());
                idle();
            } finally {
                for (Stream stream : streams) stream.close();
                for (Group group : groups) {
                    request(client, "DELETE", group.path(), group.code(), null);
                    codes.deleteById(group.accessId());
                }
            }
        }
    }

    private void idle() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(), "idle streams must not own JDBC connections");
            assertEquals(0, pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
        });
    }
    private Group create(HttpClient client) throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(TYPE));
        var created = request(client, "POST", "/demo/agents", code, Map.of("agentDefinitionKey", TYPE));
        assertEquals(201, created.statusCode(), created.body());
        return new Group(code, access.getId(), "/demo/agents/" + json.readTree(created.body()).get("id").asText());
    }
    private HttpRequest.Builder builder(String path, String code) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header(ScopedDemoController.ACCESS_CODE_HEADER, code);
    }
    private HttpResponse<String> request(HttpClient client, String method, String path, String code, Object body) throws Exception {
        return client.send(builder(path, code).header("Content-Type", "application/json").method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    private Stream open(HttpClient client, Group group, String suffix, String cursor) throws Exception {
        var request = builder(group.path() + suffix, group.code()).GET();
        if (cursor != null) request.header("Last-Event-ID", cursor);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) response.body().close();
        assertEquals(200, response.statusCode(), "stream must open with a bounded pool");
        return new Stream(response.body());
    }
    record Frame(String id, String name, String data) {}
    static final class Stream implements AutoCloseable {
        final InputStream body;
        final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        final Thread reader;
        Stream(InputStream body) {
            this.body = body;
            reader = Thread.ofVirtual().start(() -> {
                try (var input = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String id = null, name = null;
                    var data = new StringBuilder();
                    for (String line; (line = input.readLine()) != null;) {
                        if (line.isEmpty()) {
                            if (name != null) frames.add(new Frame(id, name, data.toString()));
                            id = null; name = null; data.setLength(0);
                        } else if (line.startsWith("id:")) id = line.substring(3).stripLeading();
                        else if (line.startsWith("event:")) name = line.substring(6).stripLeading();
                        else if (line.startsWith("data:")) data.append(line.substring(5).stripLeading());
                    }
                } catch (IOException expectedOnClose) { }
            });
        }
        Frame next(String name) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            Frame frame;
            do {
                frame = frames.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                // Monitor consumers dispatch named events; activity and heartbeats are additive.
            } while (frame != null && (frame.name().equals("activity") || frame.name().equals("heartbeat")));
            assertNotNull(frame, "missing SSE " + name);
            assertEquals(name, frame.name());
            return frame;
        }
        public void close() throws IOException { body.close(); reader.interrupt(); }
    }
}
