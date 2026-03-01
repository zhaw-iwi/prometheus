package ch.zhaw.prometheus.integration.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.script.InteractionScript;
import ch.zhaw.prometheus.spi.script.InteractionScript.BehaviourExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScript.ScriptEvent;
import ch.zhaw.prometheus.spi.script.InteractionScript.Step;
import ch.zhaw.prometheus.spi.script.InteractionScript.StorageExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScriptLoader;

abstract class AbstractRealtimeReplayIntegrationTest {
    private static final Gson GSON = new Gson();

    @Autowired
    protected AgentRepository agentRepository;

    @LocalServerPort
    protected int port;

    private String lastUserUtterance;

    @BeforeEach
    void clearData() {
        this.agentRepository.deleteAll();
        this.lastUserUtterance = null;
    }

    @Test
    void replayScriptThroughRealtimePathAndVerifyPromptAcknowledgeAndSse() throws Exception {
        InteractionScript script = InteractionScriptLoader.load(scriptPath());
        Agent agent = this.agentRepository.save(buildAgent());
        String agentId = agent.getId().toString();

        for (Step step : script.getSteps()) {
            execute(step, agentId);

            if (!expectsInternalBehaviour(step)) {
                continue;
            }
            BehaviourExpectation expectedBehaviour = step.getExpectedBehaviour();
            JsonObject emitted = fetchLatestBehaviourSse(agentId, Duration.ofSeconds(3));
            assertNotNull(emitted, "expected behaviour SSE event for step " + step.getId());
            assertEquals("resp.behaviour_plan", emitted.get("type").getAsString());
            BehaviourPlan plan = BehaviourPlan.fromJson(emitted.get("payload").getAsString());
            assertNotNull(plan, "expected BehaviourPlan payload");
            assertEquals(expectedBehaviour.getSpeech(), plan.getSpeech(), "speech mismatch at " + step.getId());
            assertEquals(expectedBehaviour.getNonVerbal(), plan.getNonVerbal(), "nonverbal mismatch at " + step.getId());
        }

        assertFinalPromptStillAvailable(agentId, expectedFinalState());
        assertAssistantAcknowledgeInFinalState(agentId);
    }

    protected abstract Agent buildAgent();

    protected abstract String scriptPath();

    protected abstract String expectedFinalState();

    private void execute(Step step, String agentId) throws Exception {
        String action = step.getAction() == null ? "" : step.getAction().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                JsonObject response = postAndReadJson("/" + agentId + "/start", null);
                assertEquals(200, response.get("__status").getAsInt(), "start failed at step " + step.getId());
                assertResponseEventMatches(step, response);
            }
            case "acknowledge" -> executeAcknowledge(step, agentId);
            case "generate" -> executeRealtimeGenerate(step, agentId);
            case "reset" -> {
                JsonObject response = deleteAndReadJson("/" + agentId + "/reset");
                assertEquals(200, response.get("__status").getAsInt(), "reset failed at step " + step.getId());
                assertResponseEventMatches(step, response);
            }
            case "assertstate" -> assertState(agentId, step.getExpectedState(), step.getId());
            case "assertstorage" -> assertStorage(agentId, step.getExpectedStorage(), step.getId());
            default -> throw new IllegalArgumentException("unsupported action in script: " + step.getAction());
        }
    }

    private void executeAcknowledge(Step step, String agentId) throws Exception {
        ScriptEvent event = step.getEvent();
        JsonObject body = new JsonObject();
        body.addProperty("type", event.getType());
        body.addProperty("actor", event.getActor());
        body.addProperty("kind", event.getKind());
        body.addProperty("payload", event.getPayload());

        JsonObject response = postAndReadJson("/" + agentId + "/acknowledge", GSON.toJson(body));
        assertEquals(200, response.get("__status").getAsInt(), "acknowledge failed at step " + step.getId());

        boolean isUserEvent = event != null && EventActor.USER.equalsIgnoreCase(event.getActor());
        boolean isAssistantEvent = event != null && EventActor.ASSISTANT.equalsIgnoreCase(event.getActor());

        if (isUserEvent && event.getPayload() != null) {
            this.lastUserUtterance = event.getPayload();
        }
        if (isAssistantEvent) {
            assertNoResponseEvent(step, response);
            assertEventHistoryTailContainsAssistantSpeech(agentId, event.getPayload(), step.getId());
            return;
        }

        if (step.getExpectedBehaviour() == null) {
            assertNoResponseEvent(step, response);
            return;
        }
        assertResponseEventMatches(step, response);
    }

    private void executeRealtimeGenerate(Step step, String agentId) throws Exception {
        String promptPath = "/" + agentId + "/prompt?profile=realtime_speech";
        HttpURLConnection promptConnection = get(promptPath);
        assertEquals(200, promptConnection.getResponseCode(), "prompt failed at step " + step.getId());
        JsonObject prompt = readJsonObject(promptConnection);
        promptConnection.disconnect();

        assertTrue(prompt.has("promptMessages") && prompt.get("promptMessages").isJsonArray(),
                "promptMessages missing at step " + step.getId());
        if (this.lastUserUtterance != null && !this.lastUserUtterance.isBlank()) {
            assertPromptContainsUser(prompt.getAsJsonArray("promptMessages"), this.lastUserUtterance, step.getId());
        }

        BehaviourExpectation expected = step.getExpectedBehaviour();
        assertNotNull(expected, "generate step must include expectedBehaviour in realtime mode");
        String speech = expected.getSpeech();
        assertTrue(speech != null && !speech.isBlank(),
                "expectedBehaviour.speech must be non-empty for generate step " + step.getId());
    }

    private boolean expectsInternalBehaviour(Step step) {
        BehaviourExpectation expected = step.getExpectedBehaviour();
        if (expected == null) {
            return false;
        }
        String action = step.getAction() == null ? "" : step.getAction().trim().toLowerCase(Locale.ROOT);
        if ("start".equals(action) || "reset".equals(action)) {
            return true;
        }
        if (!"acknowledge".equals(action) || step.getEvent() == null || step.getEvent().getActor() == null) {
            return false;
        }
        return EventActor.USER.equalsIgnoreCase(step.getEvent().getActor());
    }

    private void assertState(String agentId, String expectedState, String stepId) throws Exception {
        HttpURLConnection connection = get("/" + agentId + "/state");
        assertEquals(200, connection.getResponseCode(), "state endpoint failed at step " + stepId);
        JsonObject state = readJsonObject(connection);
        connection.disconnect();
        String name = state.has("name") && !state.get("name").isJsonNull() ? state.get("name").getAsString() : null;
        String innerName = state.has("innerName") && !state.get("innerName").isJsonNull()
                ? state.get("innerName").getAsString()
                : null;
        boolean matches = Objects.equals(expectedState, name) || Objects.equals(expectedState, innerName);
        if (!matches && state.has("innerNames") && state.get("innerNames").isJsonArray()) {
            JsonArray innerNames = state.getAsJsonArray("innerNames");
            for (int i = 0; i < innerNames.size(); i++) {
                if (Objects.equals(expectedState, innerNames.get(i).getAsString())) {
                    matches = true;
                    break;
                }
            }
        }
        assertTrue(matches,
                "state mismatch at step " + stepId + ": expected " + expectedState + " but was name=" + name
                        + ", innerName=" + innerName);
    }

    private void assertStorage(String agentId, List<StorageExpectation> expectedStorage, String stepId)
            throws Exception {
        HttpURLConnection connection = get("/" + agentId + "/storage");
        assertEquals(200, connection.getResponseCode(), "storage endpoint failed at step " + stepId);
        JsonArray storage = readJsonArray(connection);
        connection.disconnect();

        for (StorageExpectation expectation : expectedStorage) {
            boolean matched = false;
            for (int i = 0; i < storage.size(); i++) {
                JsonObject entry = storage.get(i).getAsJsonObject();
                String key = entry.has("key") && !entry.get("key").isJsonNull() ? entry.get("key").getAsString() : null;
                String value = entry.has("value") && !entry.get("value").isJsonNull() ? entry.get("value").getAsString()
                        : null;
                if (!Objects.equals(expectation.getKey(), key)) {
                    continue;
                }
                if (value != null && expectation.getContains() != null && value.contains(expectation.getContains())) {
                    matched = true;
                    break;
                }
            }
            assertTrue(matched, "storage expectation failed at step " + stepId + " for key " + expectation.getKey());
        }
    }

    private void assertResponseEventMatches(Step step, JsonObject response) {
        BehaviourExpectation expected = step.getExpectedBehaviour();
        if (expected == null) {
            return;
        }
        assertTrue(response.has("responseEvent") && !response.get("responseEvent").isJsonNull(),
                "missing responseEvent at step " + step.getId());
        JsonObject event = response.getAsJsonObject("responseEvent");
        assertEquals("resp.behaviour_plan", event.get("type").getAsString(), "responseEvent type mismatch");
        BehaviourPlan plan = BehaviourPlan.fromJson(event.get("payload").getAsString());
        assertNotNull(plan, "responseEvent payload must deserialize to BehaviourPlan");
        assertEquals(expected.getSpeech(), plan.getSpeech(), "response speech mismatch at step " + step.getId());
    }

    private void assertNoResponseEvent(Step step, JsonObject response) {
        assertTrue(!response.has("responseEvent") || response.get("responseEvent").isJsonNull(),
                "responseEvent should be null at step " + step.getId());
    }

    private void assertPromptContainsUser(JsonArray messages, String expectedUserText, String stepId) {
        boolean found = false;
        for (int i = 0; i < messages.size(); i++) {
            JsonObject message = messages.get(i).getAsJsonObject();
            if (!message.has("role") || message.get("role").isJsonNull()) {
                continue;
            }
            if (!"user".equalsIgnoreCase(message.get("role").getAsString())) {
                continue;
            }
            if (!message.has("content") || message.get("content").isJsonNull()) {
                continue;
            }
            String content = message.get("content").getAsString();
            if (content.contains(expectedUserText)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "prompt does not include latest user utterance at step " + stepId);
    }

    private void assertEventHistoryTailContainsAssistantSpeech(String agentId, String payload, String stepId)
            throws Exception {
        HttpURLConnection connection = get("/" + agentId + "/eventhistory");
        assertEquals(200, connection.getResponseCode(), "eventhistory endpoint failed at step " + stepId);
        JsonArray history = readJsonArray(connection);
        connection.disconnect();
        assertTrue(history.size() > 0, "event history should not be empty at step " + stepId);
        JsonObject last = history.get(history.size() - 1).getAsJsonObject();
        assertEquals("assistant", last.get("actor").getAsString(), "last event actor mismatch at step " + stepId);
        assertEquals("resp.behaviour_plan", last.get("type").getAsString(), "last event type mismatch at step " + stepId);
        assertTrue(last.get("payload").getAsString().contains(extractSpeech(payload)),
                "last assistant payload missing expected speech at step " + stepId);
    }

    private void assertFinalPromptStillAvailable(String agentId, String finalState) throws Exception {
        assertState(agentId, finalState, "realtime-final-precondition");

        HttpURLConnection connection = get("/" + agentId + "/prompt?profile=realtime_speech");
        assertEquals(200, connection.getResponseCode(), "prompt endpoint failed in final state");
        JsonObject prompt = readJsonObject(connection);
        connection.disconnect();

        assertEquals(finalState, prompt.get("stateName").getAsString(), "final prompt state mismatch");
        assertTrue(prompt.has("active"), "prompt response missing active");
        assertTrue(!prompt.get("active").getAsBoolean(), "agent should be inactive in final state prompt response");
    }

    private void assertAssistantAcknowledgeInFinalState(String agentId) throws Exception {
        JsonObject behaviour = new JsonObject();
        behaviour.addProperty("speech", "Externe Echtzeit-Antwort bestaetigt.");
        JsonObject ack = new JsonObject();
        ack.addProperty("type", "resp.behaviour_plan");
        ack.addProperty("actor", "assistant");
        ack.addProperty("kind", "response");
        ack.addProperty("payload", behaviour.toString());

        JsonObject response = postAndReadJson("/" + agentId + "/acknowledge", GSON.toJson(ack));
        assertEquals(200, response.get("__status").getAsInt(), "assistant acknowledge failed in final state");
        assertNoResponseEvent(new SyntheticStep("assistant-ack-final"), response);
        assertEventHistoryTailContainsAssistantSpeech(agentId, behaviour.toString(), "assistant-ack-final");
    }

    private static String extractSpeech(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        try {
            JsonObject plan = GSON.fromJson(payload, JsonObject.class);
            if (plan != null && plan.has("speech") && !plan.get("speech").isJsonNull()) {
                return plan.get("speech").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return payload;
    }

    private HttpURLConnection post(String path, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(path)).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setDoOutput(true);
        if (jsonBody != null) {
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(body);
        }
        return connection;
    }

    private HttpURLConnection delete(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(path)).toURL().openConnection();
        connection.setRequestMethod("DELETE");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        return connection;
    }

    private HttpURLConnection get(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(path)).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        return connection;
    }

    private JsonObject postAndReadJson(String path, String jsonBody) throws Exception {
        HttpURLConnection connection = post(path, jsonBody);
        int status = connection.getResponseCode();
        JsonObject object = readJsonObject(connection);
        connection.disconnect();
        object.addProperty("__status", status);
        return object;
    }

    private JsonObject deleteAndReadJson(String path) throws Exception {
        HttpURLConnection connection = delete(path);
        int status = connection.getResponseCode();
        JsonObject object = readJsonObject(connection);
        connection.disconnect();
        object.addProperty("__status", status);
        return object;
    }

    private JsonObject fetchLatestBehaviourSse(String agentId, Duration timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url("/" + agentId + "/behaviour/stream")).toURL()
                .openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout((int) timeout.toMillis());
        connection.setReadTimeout((int) timeout.toMillis());
        assertEquals(200, connection.getResponseCode(), "failed to subscribe behaviour SSE");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring("data:".length()).trim();
                if (json.isBlank()) {
                    continue;
                }
                return GSON.fromJson(json, JsonObject.class);
            }
        } finally {
            connection.disconnect();
        }
        return null;
    }

    private JsonObject readJsonObject(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }
            if (raw.isEmpty()) {
                return new JsonObject();
            }
            return GSON.fromJson(raw.toString(), JsonObject.class);
        }
    }

    private JsonArray readJsonArray(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }
            return GSON.fromJson(raw.toString(), JsonArray.class);
        }
    }

    private String url(String path) {
        return URI.create("http://localhost:" + this.port + path).toString();
    }

    private static final class EventActor {
        private static final String USER = "user";
        private static final String ASSISTANT = "assistant";

        private EventActor() {
        }
    }

    private static final class SyntheticStep extends Step {
        private final String id;

        private SyntheticStep(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return this.id;
        }
    }
}
