package ch.zhaw.prometheus.integration;

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
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.script.InteractionScript;
import ch.zhaw.prometheus.spi.script.InteractionScript.BehaviourExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScript.ScriptEvent;
import ch.zhaw.prometheus.spi.script.InteractionScript.Step;
import ch.zhaw.prometheus.spi.script.InteractionScript.StorageExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScriptLoader;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/transition-decision-action-replay-script.json"
})
class TransitionDecisionActionReplayIntegrationTest {
    private static final Gson GSON = new Gson();
    private static final String SCRIPT_PATH = "classpath:scripts/transition-decision-action-replay-script.json";

    @Autowired
    private AgentRepository agentRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearData() {
        this.agentRepository.deleteAll();
    }

    @Test
    void replayTransitionScriptAndVerifyStateAndStorage() throws Exception {
        InteractionScript script = InteractionScriptLoader.load(SCRIPT_PATH);
        Agent agent = this.agentRepository.save(buildTransitionAgent());

        for (Step step : script.getSteps()) {
            execute(step, agent.getId().toString());
            BehaviourExpectation expectedBehaviour = step.getExpectedBehaviour();
            if (expectedBehaviour != null) {
                JsonObject emitted = fetchLatestBehaviourSse(agent.getId().toString(), Duration.ofSeconds(3));
                assertNotNull(emitted, "expected behaviour SSE event for step " + step.getId());
                assertEquals("resp.behaviour_plan", emitted.get("type").getAsString());
                BehaviourPlan plan = BehaviourPlan.fromJson(emitted.get("payload").getAsString());
                assertNotNull(plan, "expected BehaviourPlan payload");
                assertEquals(expectedBehaviour.getSpeech(), plan.getSpeech(), "speech mismatch at " + step.getId());
                assertEquals(expectedBehaviour.getNonVerbal(), plan.getNonVerbal(), "nonverbal mismatch at " + step.getId());
            }
        }
    }

    private Agent buildTransitionAgent() {
        Storage storage = new Storage();

        PromptPolicy finalPolicy = new PromptPolicy(
                "Conversation is wrapping up. Provide a short closing message.",
                "Provide one concise final encouragement sentence.",
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        finalPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        State finalWrapUp = new State("FinalWrapUp", finalPolicy, java.util.List.of(), true, false);

        PromptPolicy planningPolicy = new PromptPolicy(
                "You are in planning mode. Convert the extracted goal into a practical brief plan.",
                "Provide a concise planning starter message.",
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        planningPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        Transition planningToFinal = new Transition(
                java.util.List.of(new StaticDecision("Decide true only when user indicates they are done and ready to end.")),
                java.util.List.of(),
                finalWrapUp);
        State planning = new State("Planning", planningPolicy, java.util.List.of(planningToFinal), true, false);

        PromptPolicy rapportPolicy = new PromptPolicy(
                "You are in rapport mode and should gather enough context before planning.",
                "Ask one warm starter question.",
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        rapportPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        Transition rapportToPlanning = new Transition(
                java.util.List.of(new StaticDecision("Decide true only when user clearly states a concrete challenge and asks for a plan.")),
                java.util.List.of(new StaticExtractionAction(
                        "Extract the user goal and urgency as JSON with keys goal, urgency, next_step.",
                        storage,
                        "GoalDetected")),
                planning);
        State rapport = new State("Rapport", rapportPolicy, java.util.List.of(rapportToPlanning), true, false);

        return new Agent("Scripted Transition Replay Agent",
                "Agent used to replay deterministic decision/extraction transitions.", rapport, storage);
    }

    private void execute(Step step, String agentId) throws Exception {
        String action = step.getAction() == null ? "" : step.getAction().trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                HttpURLConnection connection = post("/" + agentId + "/start", null);
                assertEquals(200, connection.getResponseCode(), "start failed at step " + step.getId());
                connection.disconnect();
            }
            case "acknowledge" -> {
                ScriptEvent event = step.getEvent();
                JsonObject body = new JsonObject();
                body.addProperty("type", event.getType());
                body.addProperty("actor", event.getActor());
                body.addProperty("kind", event.getKind());
                body.addProperty("payload", event.getPayload());
                HttpURLConnection connection = post("/" + agentId + "/acknowledge", GSON.toJson(body));
                assertEquals(200, connection.getResponseCode(), "acknowledge failed at step " + step.getId());
                connection.disconnect();
            }
            case "generate" -> {
                HttpURLConnection connection = post("/" + agentId + "/behaviour/generate", null);
                assertEquals(200, connection.getResponseCode(), "generate failed at step " + step.getId());
                connection.disconnect();
            }
            case "assertstate" -> assertState(agentId, step.getExpectedState(), step.getId());
            case "assertstorage" -> assertStorage(agentId, step.getExpectedStorage(), step.getId());
            default -> throw new IllegalArgumentException("unsupported action in script: " + step.getAction());
        }
    }

    private void assertState(String agentId, String expectedState, String stepId) throws Exception {
        HttpURLConnection connection = get("/" + agentId + "/state");
        assertEquals(200, connection.getResponseCode(), "state endpoint failed at step " + stepId);
        JsonObject state = readJsonObject(connection);
        connection.disconnect();
        assertEquals(expectedState, state.get("name").getAsString(), "state mismatch at step " + stepId);
    }

    private void assertStorage(String agentId, java.util.List<StorageExpectation> expectedStorage, String stepId)
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
                if (!java.util.Objects.equals(expectation.getKey(), key)) {
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

    private HttpURLConnection get(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(path)).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        return connection;
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
}


