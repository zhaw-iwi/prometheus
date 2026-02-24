package ch.zhaw.prometheus.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/realtime-speech-backend-complement-replay-script.json"
})
class RealtimeSpeechBackendComplementReplayIntegrationTest {
    private static final Gson GSON = new Gson();

    @Autowired
    private AgentRepository agentRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void clearData() {
        this.agentRepository.deleteAll();
    }

    @Test
    void replayRealtimePromptAndBackendComplementFlow() throws Exception {
        Agent agent = this.agentRepository.save(buildRealtimeMultimodalAgent());
        String agentId = agent.getId().toString();

        HttpURLConnection startConnection = post("/" + agentId + "/start", null);
        assertEquals(200, startConnection.getResponseCode(), "start failed");
        startConnection.disconnect();

        JsonObject userUtterance = new JsonObject();
        userUtterance.addProperty("type", "obs.user_utterance");
        userUtterance.addProperty("actor", "user");
        userUtterance.addProperty("kind", "observation");
        userUtterance.addProperty("payload", "I am overwhelmed with two deadlines and feel tense.");
        HttpURLConnection acknowledgeUtterance = post("/" + agentId + "/acknowledge", GSON.toJson(userUtterance));
        assertEquals(200, acknowledgeUtterance.getResponseCode(), "user utterance acknowledge failed");
        acknowledgeUtterance.disconnect();

        JsonObject faceEmotion = new JsonObject();
        faceEmotion.addProperty("type", "obs.emotion.face");
        faceEmotion.addProperty("actor", "user");
        faceEmotion.addProperty("kind", "observation");
        faceEmotion.addProperty("payload",
                "{\"emotion\":\"sad\",\"confidence\":0.92,\"valence\":-0.70,\"arousal\":0.61}");
        HttpURLConnection acknowledgeEmotion = post("/" + agentId + "/acknowledge", GSON.toJson(faceEmotion));
        assertEquals(200, acknowledgeEmotion.getResponseCode(), "face emotion acknowledge failed");
        acknowledgeEmotion.disconnect();

        HttpURLConnection promptConnection = get("/" + agentId + "/prompt?profile=REALTIME_SPEECH");
        assertEquals(200, promptConnection.getResponseCode(), "prompt retrieval failed");
        JsonObject promptResponse = readJsonObject(promptConnection);
        promptConnection.disconnect();
        assertTrue(promptResponse.get("active").getAsBoolean(), "agent should remain active");
        JsonArray promptMessages = promptResponse.getAsJsonArray("promptMessages");
        assertTrue(promptMessages.size() >= 3, "prompt should include system and event context");
        String firstSystemContent = promptMessages.get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(firstSystemContent.contains("respond with natural spoken assistant text only"),
                "realtime prompt should enforce speech-only contract");
        boolean hasUserUtterance = false;
        boolean hasFaceEmotionContext = false;
        for (int i = 0; i < promptMessages.size(); i++) {
            JsonObject message = promptMessages.get(i).getAsJsonObject();
            String role = message.get("role").getAsString();
            String content = message.get("content").getAsString();
            if ("user".equals(role) && content.contains("I am overwhelmed with two deadlines")) {
                hasUserUtterance = true;
            }
            if ("user".equals(role) && content.contains("User facial emotion: sad (confidence 0.92)")) {
                hasFaceEmotionContext = true;
            }
        }
        assertTrue(hasUserUtterance, "realtime prompt must include verbal user input");
        assertTrue(hasFaceEmotionContext, "realtime prompt must include visual emotion context");

        JsonObject realtimeAssistantSpeech = new JsonObject();
        realtimeAssistantSpeech.addProperty("type", "resp.behaviour_plan");
        realtimeAssistantSpeech.addProperty("actor", "assistant");
        realtimeAssistantSpeech.addProperty("kind", "response");
        realtimeAssistantSpeech.addProperty("payload",
                "{\"speech\":\"That sounds heavy. Let us choose one deadline first and make a tiny plan.\"}");
        HttpURLConnection acknowledgeRealtimeSpeech = post("/" + agentId + "/acknowledge",
                GSON.toJson(realtimeAssistantSpeech));
        assertEquals(200, acknowledgeRealtimeSpeech.getResponseCode(), "assistant realtime speech acknowledge failed");
        acknowledgeRealtimeSpeech.disconnect();

        JsonObject generateRequest = new JsonObject();
        generateRequest.addProperty("outputProfile", "BACKEND_COMPLEMENT");
        generateRequest.add("omitModalities", GSON.toJsonTree(List.of("speech")));
        HttpURLConnection generateComplement = post("/" + agentId + "/behaviour/generate", GSON.toJson(generateRequest));
        assertEquals(200, generateComplement.getResponseCode(), "backend complement generation failed");
        generateComplement.disconnect();

        JsonObject emitted = fetchLatestBehaviourSse(agentId, Duration.ofSeconds(3));
        assertNotNull(emitted, "expected behaviour SSE event");
        assertEquals("resp.behaviour_plan", emitted.get("type").getAsString());
        BehaviourPlan plan = BehaviourPlan.fromJson(emitted.get("payload").getAsString());
        assertNotNull(plan, "expected behaviour-plan payload");
        assertNull(plan.getSpeech(), "complement behaviour should not include speech");
        assertNotNull(plan.getNonVerbal(), "complement behaviour should include nonverbal");
        assertEquals("ACKNOWLEDGE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
    }

    private Agent buildRealtimeMultimodalAgent() {
        PromptPolicy policy = new PromptPolicy(
                """
                        You are a concise supportive realtime coach.
                        Inputs can include user utterances and face-emotion observations.
                        Use both modalities to calibrate tone while staying practical.
                        """,
                "Start with one short supportive question.",
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        State state = new State("RealtimeMultimodal", policy, List.of());
        return new Agent("Realtime Multimodal Replay Agent",
        "Agent used for deterministic realtime speech + backend nonverbal complement replay.", state);
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

    private String url(String path) {
        return URI.create("http://localhost:" + this.port + path).toString();
    }
}
