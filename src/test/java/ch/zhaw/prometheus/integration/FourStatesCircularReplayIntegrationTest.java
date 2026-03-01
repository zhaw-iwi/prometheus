package ch.zhaw.prometheus.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
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
        "prometheus.gateway.script=classpath:scripts/four-states-circular-all-options-replay-script.json"
})
class FourStatesCircularReplayIntegrationTest {
    private static final Gson GSON = new Gson();
    private static final String SCRIPT_PATH = "classpath:scripts/four-states-circular-all-options-replay-script.json";

    private static final String PROMPT_OUTER = """
            Du bist Gigi. Halte den Modus stabil und fuehre die Interaktion strukturiert.
            """;
    private static final String PROMPT_OUTER_DONE = """
            Return true only if the latest user message clearly ends the whole session (e.g. Option 4).
            Otherwise return false.
            """;
    private static final String PROMPT_BASE = """
            Basis-Menue:
            1) Ratespiel
            2) Persuasions-Mikro-Coach
            3) Story-Co-Creation
            4) Gesamte Interaktion beenden
            """;
    private static final String PROMPT_BASE_STARTER = "Begruesse kurz und bitte um Auswahl 1-4.";
    private static final String PROMPT_BASE_TO_GUESSER = "Return true only when the user selects option 1 / Ratespiel.";
    private static final String PROMPT_BASE_TO_COACH = "Return true only when the user selects option 2 / Mikro-Coach.";
    private static final String PROMPT_BASE_TO_STORY = "Return true only when the user selects option 3 / Story.";

    private static final String PROMPT_GUESSER = "Ratespiel-Modus mit Ja/Nein-Fragen bis finaler Tipp.";
    private static final String PROMPT_GUESSER_STARTER = "Fordere den Nutzer auf, an eine Sache zu denken und 'Bereit' zu schreiben.";
    private static final String PROMPT_GUESSER_TO_BASE = "Return true only when the guessing game is clearly completed.";

    private static final String PROMPT_COACH = "Mikro-Coaching-Modus bis klares Commitment.";
    private static final String PROMPT_COACH_STARTER = "Frage nach einer wichtigen Veraenderung.";
    private static final String PROMPT_COACH_TO_BASE = "Return true only when a concrete micro action and commitment are clear.";

    private static final String PROMPT_STORY = "Story-Co-Creation-Modus bis klares Story-Ende.";
    private static final String PROMPT_STORY_STARTER = "Frage nach Genre und Figur.";
    private static final String PROMPT_STORY_TO_BASE = "Return true only when story completion is clearly confirmed.";

    private static final String PROMPT_FINAL = """
            Sitzung beendet. Gib eine kurze freundliche Verabschiedung.
            """;

    private static final String PROMPT_OUTCOME_EXTRACTION = """
            Return STRICT JSON:
            {
              "flow_type": "circular",
              "outcomes": [
                {
                  "interaction_type": "guessing_game",
                  "completed": true,
                  "result_summary": "string",
                  "user_confirmation": "string|null"
                }
              ],
              "overall_summary": "string"
            }
            """;

    @Autowired
    private AgentRepository agentRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearData() {
        this.agentRepository.deleteAll();
    }

    @Test
    void replayCircularScriptThroughEndpointsAndVerifyStateStorageAndBehaviourSse() throws Exception {
        InteractionScript script = InteractionScriptLoader.load(SCRIPT_PATH);
        Agent agent = this.agentRepository.save(buildFourStatesCircularAgent());

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
                assertEquals(expectedBehaviour.getNonVerbal(), plan.getNonVerbal(),
                        "nonverbal mismatch at " + step.getId());
            }
        }
    }

    private Agent buildFourStatesCircularAgent() {
        Storage storage = new Storage();
        State sessionFinal = new Final("Session Goodbye Final", PROMPT_FINAL);

        State baseMenuState = new State(
                "Base Menu",
                new PromptPolicy(PROMPT_BASE, PROMPT_BASE_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());

        State guesserState = new State(
                "Questions Based Guesser",
                new PromptPolicy(PROMPT_GUESSER, PROMPT_GUESSER_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());

        State coachState = new State(
                "Persuasion Micro Coach",
                new PromptPolicy(PROMPT_COACH, PROMPT_COACH_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());

        State storyState = new State(
                "Story Co Creation",
                new PromptPolicy(PROMPT_STORY, PROMPT_STORY_STARTER, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                List.of());

        baseMenuState.addTransition(new Transition(new StaticDecision(PROMPT_BASE_TO_GUESSER), guesserState));
        baseMenuState.addTransition(new Transition(new StaticDecision(PROMPT_BASE_TO_COACH), coachState));
        baseMenuState.addTransition(new Transition(new StaticDecision(PROMPT_BASE_TO_STORY), storyState));

        guesserState.addTransition(new Transition(new StaticDecision(PROMPT_GUESSER_TO_BASE), baseMenuState));
        coachState.addTransition(new Transition(new StaticDecision(PROMPT_COACH_TO_BASE), baseMenuState));
        storyState.addTransition(new Transition(new StaticDecision(PROMPT_STORY_TO_BASE), baseMenuState));

        Transition outerToFinal = new Transition(
                List.of(new StaticDecision(PROMPT_OUTER_DONE)),
                List.of(new StaticExtractionAction(PROMPT_OUTCOME_EXTRACTION, storage, "outcome")),
                sessionFinal);
        State outerState = new OuterState(
                PROMPT_OUTER,
                "Gigi Demo Supervisor",
                List.of(outerToFinal),
                baseMenuState);

        return new Agent(
                "Gigi on Prometheus (4 States Circular Replay)",
                "Script-replay test agent for circular base menu with three specialized states and global quit.",
                outerState,
                storage);
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
        String name = state.has("name") && !state.get("name").isJsonNull() ? state.get("name").getAsString() : null;
        String innerName = state.has("innerName") && !state.get("innerName").isJsonNull()
                ? state.get("innerName").getAsString()
                : null;
        boolean matches = Objects.equals(expectedState, name) || Objects.equals(expectedState, innerName);
        if (!matches && state.has("innerNames") && state.get("innerNames").isJsonArray()) {
            JsonArray innerNames = state.getAsJsonArray("innerNames");
            for (int i = 0; i < innerNames.size(); i++) {
                if (innerNames.get(i).isJsonNull()) {
                    continue;
                }
                if (Objects.equals(expectedState, innerNames.get(i).getAsString())) {
                    matches = true;
                    break;
                }
            }
        }
        assertTrue(matches, "state mismatch at step " + stepId + " (name=" + name + ", innerName=" + innerName + ")");
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

    private HttpURLConnection post(String path, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url(path)).openConnection();
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
        HttpURLConnection connection = (HttpURLConnection) new URL(url(path)).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        return connection;
    }

    private JsonObject fetchLatestBehaviourSse(String agentId, Duration timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url("/" + agentId + "/behaviour/stream"))
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
