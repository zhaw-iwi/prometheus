package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import ch.zhaw.prometheus.model.policy.PromptMessage;

class OpenAILanguageModelGatewayHttpUnitTest {
    private HttpServer server;
    private OpenAIProperties properties;
    private final AtomicReference<JsonObject> request = new AtomicReference<>();
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean hold;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        response.set(envelope("true"));
        server.createContext("/chat", exchange -> {
            calls.incrementAndGet();
            request.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            if (hold) {
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        properties = new OpenAIProperties();
        properties.setOpenaivsazureopenai("openai");
        properties.setModel("gpt-5.2");
        properties.setKey("test-key");
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
    }
    @AfterEach void stop() { release.countDown(); server.stop(0); }
    private OpenAILanguageModelGateway gateway() { return new OpenAILanguageModelGateway(properties); }
    private List<PromptMessage> messages() { return List.of(PromptMessage.system("Synthetic JSON task")); }

    @Test void exportCapturesActualRoutesUsageAndFailedRequestsWithoutProviderContent() {
        var route = new OpenAIProperties.InferenceRoute();
        route.setModel("gpt-5.6-luna"); route.setReasoningEffort("none");
        properties.getRoutes().put(InferencePurpose.DECISION, route);
        JsonObject withUsage = JsonParser.parseString(envelope("true")).getAsJsonObject();
        withUsage.add("usage", JsonParser.parseString("{\"prompt_tokens\":12,\"completion_tokens\":2}"));
        response.set(withUsage.toString());
        try (var trace = new ch.zhaw.prometheus.logging.LatencyTrace(null, ignored -> {})) {
            assertTrue(gateway().decide(messages()));
            status.set(429); response.set("private provider content");
            assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
            String exported = new String(java.util.Base64.getDecoder().decode(
                    ch.zhaw.prometheus.logging.LatencyTrace.responseHeader()), StandardCharsets.UTF_8);
            var spans = JsonParser.parseString(exported).getAsJsonObject().getAsJsonArray("spans");
            assertEquals(2, spans.size());
            assertEquals("gpt-5.6-luna", spans.get(0).getAsJsonObject().get("model").getAsString());
            assertEquals("none", spans.get(0).getAsJsonObject().get("effort").getAsString());
            assertEquals(12, spans.get(0).getAsJsonObject().get("promptTokens").getAsInt());
            assertEquals("error", spans.get(1).getAsJsonObject().get("status").getAsString());
            assertFalse(spans.get(1).getAsJsonObject().has("promptTokens"));
            assertFalse(exported.contains("private")); assertFalse(exported.contains("Synthetic"));
            assertFalse(exported.contains("test-key"));
        }
    }

    @Test void sendsPurposeRouteEffortOutputLimitAndNoIncompatibleSampling() {
        var route = new OpenAIProperties.InferenceRoute();
        route.setModel("gpt-5.6-luna"); route.setReasoningEffort("none"); route.setMaxCompletionTokens(256);
        properties.getRoutes().put(InferencePurpose.DECISION, route);
        assertTrue(gateway().decide(messages())); // Missing usage is valid.
        JsonObject payload = request.get();
        assertEquals("gpt-5.6-luna", payload.get("model").getAsString());
        assertEquals("none", payload.get("reasoning_effort").getAsString());
        assertEquals(256, payload.get("max_completion_tokens").getAsInt());
        assertFalse(payload.has("temperature")); assertFalse(payload.has("top_p"));
        JsonObject withUsage = JsonParser.parseString(envelope("answer")).getAsJsonObject();
        withUsage.add("usage", JsonParser.parseString("{\"prompt_tokens\":12,\"completion_tokens\":2}"));
        response.set(withUsage.toString());
        assertEquals("answer", gateway().complete(messages()));
        assertEquals("gpt-5.2", request.get().get("model").getAsString());
        assertFalse(request.get().has("reasoning_effort"));
    }

    @Test void schemaAndAzureDeploymentAreMappedWithoutPublicModelSubstitution() {
        properties.setOpenaivsazureopenai("azureopenai");
        response.set(envelope("{\"ok\":true}"));
        JsonObject schema = JsonParser.parseString("{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}},\"required\":[\"ok\"],\"additionalProperties\":false}").getAsJsonObject();
        gateway().infer(InferenceRequest.structured(InferencePurpose.DECISION, messages(), schema));
        assertFalse(request.get().has("model"));
        assertEquals(schema, request.get().getAsJsonObject("response_format").getAsJsonObject("json_schema").get("schema"));
    }

    @Test void malformedRefusedTruncatedAndProviderErrorDoNotReturnDecisions() {
        for (String content : new String[] { "probably", "true but...", "null" }) {
            response.set(envelope(content));
            assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        }
        response.set(envelope("true").replace("stop", "length"));
        assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        response.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"private refusal\",\"content\":null}}]}");
        assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        response.set("{}");
        assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        status.set(429); response.set("private provider content");
        var failure = assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        assertTrue(failure.getMessage().contains("429"));
        assertFalse(failure.toString().contains("private"));
        assertEquals(7, calls.get()); // One request per attempt; no silent retry.
    }

    @Test void finiteDeadlineFailsWithoutRetry() {
        hold = true; properties.setRequestTimeoutMs(100);
        var failure = assertThrows(IllegalStateException.class, () -> gateway().decide(messages()));
        assertTrue(failure.getMessage().contains("deadline"));
        assertEquals(1, calls.get());
    }

    private static String envelope(String content) {
        JsonObject message = new JsonObject(); message.addProperty("content", content);
        JsonObject choice = new JsonObject(); choice.addProperty("finish_reason", "stop"); choice.add("message", message);
        var choices = new com.google.gson.JsonArray(); choices.add(choice);
        JsonObject envelope = new JsonObject(); envelope.add("choices", choices); return envelope.toString();
    }
}
