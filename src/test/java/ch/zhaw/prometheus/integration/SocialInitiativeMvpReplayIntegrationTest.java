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
import java.util.Map;

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
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.script.InteractionScript;
import ch.zhaw.prometheus.spi.script.InteractionScript.BehaviourExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScript.ScriptEvent;
import ch.zhaw.prometheus.spi.script.InteractionScript.Step;
import ch.zhaw.prometheus.spi.script.InteractionScript.StorageExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScriptLoader;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/social-initiative-mvp-replay-script.json"
})
class SocialInitiativeMvpReplayIntegrationTest {
    private static final Gson GSON = new Gson();
    private static final String SCRIPT_PATH = "classpath:scripts/social-initiative-mvp-replay-script.json";

    private static final String STORAGE_KEY_SOCIAL_CONTEXT = "SocialContext";

    private static final String PROMPT_CONVERSATION = """
            You are a room assistant handling direct requests from one or more users.
            Prioritize explicit user utterances over inferred nonverbal cues.
            Keep responses concise and practical.
            If no user directly asks for help, stay brief and avoid starting long new topics.
            """;
    private static final String PROMPT_CONVERSATION_STARTER = """
            Generate one short opening line inviting users to ask for help.
            """;
    private static final String PROMPT_SOCIAL_ASSESSMENT = """
            You are observing room dynamics to proactively offer socially appropriate support.
            Current social context JSON:
            ${SocialContext}

            Goals:
            - Identify if one or more users are present.
            - If a user name is known, greet them naturally.
            - If multiple users are present, greet inclusively.
            - If a user appears unnamed, politely ask for a name.
            Keep initiative light; avoid repeating the same greeting if the situation has not changed.
            """;
    private static final String PROMPT_SOCIAL_ASSESSMENT_STARTER = """
            Generate one concise proactive utterance based on the current social context.
            """;
    private static final String PROMPT_TO_SOCIAL_TRIGGER = """
            Decide true only if recent events indicate a changed social situation in the room:
            - visual/social observation events (obs.emotion.face, obs.human.presence, obs.social.grouping), and
            - there is no fresh direct user request that should be handled immediately.
            Otherwise decide false.
            """;
    private static final String PROMPT_TO_CONVERSATION_TRIGGER = """
            Decide true only if a recent user utterance clearly addresses the assistant with a request,
            question, or task that requires direct conversational handling now.
            Otherwise decide false.
            """;
    private static final String PROMPT_UPDATE_SOCIAL_CONTEXT = """
            Build a cumulative JSON object from the full event history for room-level social context.
            Return JSON only with this schema:
            {
              "lastUpdateEventType":"...",
              "estimatedUserCount":0,
              "users":[{"name":"known-or-unknown","latestEmotion":"...","confidence":0.0}],
              "latestDirectRequest":{"present":true,"summary":"..."}
            }
            Rules:
            - Infer estimatedUserCount from available observations.
            - Use "unknown" when no user name is available.
            - If no direct user request exists, set latestDirectRequest.present=false and summary="".
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
    void replaySocialInitiativeMvpScriptAndVerifyStateStorageAndBehaviour() throws Exception {
        InteractionScript script = InteractionScriptLoader.load(SCRIPT_PATH);
        Agent agent = this.agentRepository.save(buildReplayAgent());

        for (Step step : script.getSteps()) {
            execute(step, agent.getId().toString());
            BehaviourExpectation expectedBehaviour = step.getExpectedBehaviour();
            if (expectedBehaviour == null) {
                continue;
            }
            JsonObject emitted = fetchLatestBehaviourSse(agent.getId().toString(), Duration.ofSeconds(3));
            assertNotNull(emitted, "expected behaviour SSE event for step " + step.getId());
            assertEquals("resp.behaviour_plan", emitted.get("type").getAsString());
            BehaviourPlan plan = BehaviourPlan.fromJson(emitted.get("payload").getAsString());
            assertNotNull(plan, "expected BehaviourPlan payload");
            assertEquals(expectedBehaviour.getSpeech(), plan.getSpeech(), "speech mismatch at " + step.getId());
            assertEquals(expectedBehaviour.getNonVerbal(), plan.getNonVerbal(), "nonverbal mismatch at " + step.getId());
        }
    }

    private Agent buildReplayAgent() {
        Storage storage = new Storage();
        storage.put(STORAGE_KEY_SOCIAL_CONTEXT, Storage.toJsonElement(Map.of(
                "lastUpdateEventType", "none",
                "estimatedUserCount", 0,
                "users", List.of(),
                "latestDirectRequest", Map.of("present", false, "summary", ""))));

        PromptPolicy conversationPolicy = new PromptPolicy(
                PROMPT_CONVERSATION,
                PROMPT_CONVERSATION_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        conversationPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        PromptPolicy socialAssessmentPolicy = new PromptPolicy(
                PROMPT_SOCIAL_ASSESSMENT,
                PROMPT_SOCIAL_ASSESSMENT_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                storage,
                List.of(STORAGE_KEY_SOCIAL_CONTEXT),
                PromptValueShape.OBJECT);
        socialAssessmentPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State conversationState = new State("ConversationHandling", conversationPolicy, List.of());
        State socialAssessmentState = new State("SocialSituationAssessment", socialAssessmentPolicy, List.of());

        Transition conversationToSocial = new Transition(
                List.of(new StaticDecision(PROMPT_TO_SOCIAL_TRIGGER)),
                List.of(new StaticExtractionAction(PROMPT_UPDATE_SOCIAL_CONTEXT, storage, STORAGE_KEY_SOCIAL_CONTEXT)),
                socialAssessmentState);
        Transition socialToConversation = new Transition(
                List.of(new StaticDecision(PROMPT_TO_CONVERSATION_TRIGGER)),
                List.of(new StaticExtractionAction(PROMPT_UPDATE_SOCIAL_CONTEXT, storage, STORAGE_KEY_SOCIAL_CONTEXT)),
                conversationState);

        conversationState.addTransition(conversationToSocial);
        socialAssessmentState.addTransition(socialToConversation);

        return new Agent("Scripted Social Initiative MVP Replay Agent",
                "Agent used for deterministic social-initiative MVP replay through HTTP endpoints.",
                conversationState,
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
        assertEquals(expectedState, state.get("name").getAsString(), "state mismatch at step " + stepId);
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

