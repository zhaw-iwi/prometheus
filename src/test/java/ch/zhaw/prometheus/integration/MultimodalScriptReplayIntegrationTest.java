package ch.zhaw.prometheus.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.script.InteractionScript;
import ch.zhaw.prometheus.spi.script.InteractionScript.BehaviourExpectation;
import ch.zhaw.prometheus.spi.script.InteractionScript.ScriptEvent;
import ch.zhaw.prometheus.spi.script.InteractionScript.Step;
import ch.zhaw.prometheus.spi.script.InteractionScriptLoader;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/multimodal-replay-script.json"
})
class MultimodalScriptReplayIntegrationTest {
    private static final Gson GSON = new Gson();
    private static final String SCRIPT_PATH = "classpath:scripts/multimodal-replay-script.json";

    @Autowired
    private AgentRepository agentRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearData() {
        this.agentRepository.deleteAll();
    }

    @Test
    void replayScriptThroughEndpointsAndVerifyBehaviourSse() throws Exception {
        InteractionScript script = InteractionScriptLoader.load(SCRIPT_PATH);
        Agent agent = this.agentRepository.save(buildReplayAgent());

        for (Step step : script.getSteps()) {
            execute(step, agent.getId().toString());
            BehaviourExpectation expected = step.getExpectedBehaviour();
            if (expected == null) {
                continue;
            }
            JsonObject emitted = fetchLatestBehaviourSse(agent.getId().toString(), Duration.ofSeconds(3));
            assertNotNull(emitted, "expected behaviour SSE event for step " + step.getId());
            assertEquals("resp.behaviour_plan", emitted.get("type").getAsString());
            BehaviourPlan plan = BehaviourPlan.fromJson(emitted.get("payload").getAsString());
            assertNotNull(plan, "expected BehaviourPlan payload");
            assertEquals(expected.getSpeech(), plan.getSpeech(), "speech mismatch at step " + step.getId());
            if (expected.getNonVerbal() == null) {
                assertEquals(null, plan.getNonVerbal(), "nonverbal mismatch at step " + step.getId());
            } else {
                assertEquals(expected.getNonVerbal(), plan.getNonVerbal(),
                        "nonverbal mismatch at step " + step.getId());
            }
        }
    }

    private Agent buildReplayAgent() {
        PromptPolicy policy = new PromptPolicy(
                """
                        You are a concise coaching assistant.
                        The user input stream may include:
                        - obs.user_utterance events
                        - obs.emotion.face events
                        - obs.human.presence events
                        - obs.social.grouping events

                        Use multimodal cues to adapt tone while keeping advice actionable and short.
                        """,
                "Start with a warm one-sentence check-in question.",
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        State state = new State("MultimodalReplay", policy, java.util.List.of());
        return new Agent("Scripted Multimodal Replay Agent",
                "Agent used for deterministic multimodal replay through HTTP endpoints.", state);
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
            default -> throw new IllegalArgumentException("unsupported action in script: " + step.getAction());
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

    private String url(String path) {
        return URI.create("http://localhost:" + this.port + path).toString();
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
}
