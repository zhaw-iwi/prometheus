package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.*;
import ch.zhaw.prometheus.application.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.live.LiveProperties;
import fixtures.gptlive.LiveSmokeConfiguration;

@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
@AutoConfigureMockMvc
@Import(LiveSmokeConfiguration.class)
class LiveCockpitSmokeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AgentRepository agents;
    @Autowired LiveSmokeConfiguration.Provider provider;
    @Autowired LiveProperties properties;
    @Autowired LiveAgentContextService contexts;
    String code, path;

    JsonNode postJson(String url, Object body) throws Exception {
        return json.readTree(mvc.perform(post(url).header(ScopedDemoController.ACCESS_CODE_HEADER, code)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    @Test void liveMultimodalPersistsSeparatePoliciesAndExposesAllSensorsAndEmbodimentWithoutBackendSpeech() throws Exception {
        String key = ch.zhaw.prometheus.agentdefs.core.LiveMultimodal.KEY;
        code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true); admin.replaceAllowedAgentTypes(access.getId(), List.of(key));
        int beforeCreation = provider.inferences.size();
        UUID id = UUID.fromString(postJson("/demo/agents", Map.of("agentDefinitionKey", key)).get("id").asText());
        path = "/demo/agents/" + id;
        assertEquals(beforeCreation, provider.inferences.size());
        assertInstanceOf(ch.zhaw.prometheus.model.policy.EmbodimentPolicy.class, agents.findById(id).orElseThrow().getCurrentState().ownPolicy());
        UUID handle = UUID.fromString(postJson(path + "/live/sessions", Map.of("sdp", "v=0 synthetic-offer")).get("handle").asText());
        String providerId = provider.latest;
        try {
            assertTrue(provider.calls.get(providerId).request.instructions().contains("Backchannel policy"));
            assertFalse(provider.calls.get(providerId).request.instructions().contains("canonical JSON shape"));
            var observations = new LinkedHashMap<String, String>();
            observations.put(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9}");
            observations.put(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":1,\"avgDetectionConfidence\":0.9}");
            observations.put(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":1,\"groupCount\":0,\"singletonCount\":1,\"largestGroupSize\":1}");
            observations.put(Event.TYPE_SOCIAL_CONTEXT, "{\"humanCount\":1,\"groupCount\":0,\"largestGroupSize\":1}");
            observations.put(Event.TYPE_HAND_SIGN, "{\"sign\":\"paper\",\"confidence\":0.9}");
            observations.put(Event.TYPE_WEATHER_CURRENT, "{\"location_label\":\"Winterthur\",\"condition\":\"rain\",\"temperature_c\":17}");
            observations.put(Event.TYPE_WEATHER_FORECAST, "{\"location_label\":\"Winterthur\",\"days\":[{\"date\":\"2026-09-28\",\"condition\":\"sunny\"}]}");
            for (var observation : observations.entrySet()) {
                postJson(path + "/acknowledge", Map.of("type", observation.getKey(), "actor", "sensor", "kind", "observation", "payload", observation.getValue()));
            }
            var selected = contexts.snapshot(code, id).orElseThrow();
            var types = selected.items().stream().map(item -> item.type()).toList();
            assertTrue(types.containsAll(observations.keySet())); assertTrue(types.contains(Event.TYPE_SOCIAL_SITUATION_CHANGE));
            assertTrue(selected.startupInput().toString().contains("Winterthur"));
            await().atMost(Duration.ofSeconds(6)).until(() -> provider.calls.get(providerId).sent.stream().anyMatch(event -> event.toString().contains("Winterthur")));
            long beforeUser = provider.inferences.stream().filter(request -> request.purpose() == ch.zhaw.prometheus.spi.InferencePurpose.BEHAVIOUR).count();
            postJson(path + "/live/sessions/" + handle + "/input?muted=false", Map.of());
            provider.speak(providerId, "USER", "Show paper and a visual caption", "live_multimodal_user");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var events = agents.findById(id).orElseThrow().getEventHistory().toList();
                assertTrue(events.stream().anyMatch(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())));
                var plan = ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(events.getLast().getPayload());
                assertNull(plan.getSpeech()); assertNotNull(plan.getNonVerbal()); assertNotNull(plan.getMotion()); assertNotNull(plan.getDisplay());
                assertEquals(beforeUser + 1, provider.inferences.stream().filter(request -> request.purpose() == ch.zhaw.prometheus.spi.InferencePurpose.BEHAVIOUR).count());
            });
            assertTrue(provider.calls.get(providerId).sent.stream().noneMatch(event -> "session.commentary.append".equals(event.get("type").getAsString())));
            int beforeNative = provider.inferences.size();
            provider.speak(providerId, "ASSISTANT", "Here is a small demonstration", "live_multimodal_assistant");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(agents.findById(id).orElseThrow().getEventHistory().toList().stream()
                    .anyMatch(event -> event.speechProvenance() != null && event.speechProvenance().origin() == SpeechProvenance.Origin.NATIVE)));
            assertEquals(beforeNative, provider.inferences.size());
        } finally {
            mvc.perform(delete(path + "/live/sessions/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isOk());
            mvc.perform(delete(path).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isNoContent());
        }
    }

    @Test void realScopeSseCaptureTaskSensoryNarrationReloadAndFeatureOffSpeech() throws Exception {
        code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true); admin.replaceAllowedAgentTypes(access.getId(), List.of("core.rock_scissor_paper"));
        UUID id = UUID.fromString(postJson("/demo/agents", Map.of("agentDefinitionKey", "core.rock_scissor_paper")).get("id").asText());
        path = "/demo/agents/" + id;
        var stream = mvc.perform(get(path + "/behaviour/stream").param("projection", "conversation")
            .header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(request().asyncStarted()).andReturn();
        UUID handle = UUID.fromString(postJson(path + "/live/sessions", Map.of("sdp", "v=0 synthetic-offer")).get("handle").asText());
        postJson(path + "/live/sessions/" + handle + "/input?muted=false", Map.of());
        String providerId = provider.latest;
        provider.speak(providerId, "USER", "I am ready to start a round", "unique_user");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var fresh = agents.findById(id).orElseThrow();
            assertTrue(fresh.getCurrentState().getActiveStatePath().toString().contains("Await User Sign"));
            assertEquals(1, fresh.getEventHistory().toList().stream().filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).count());
        });
        // A duplicate provider identity cannot run the action twice.
        provider.speak(providerId, "USER", "I am ready to start a round", "unique_user");
        postJson(path + "/acknowledge", Map.of("type", "obs.weather.current", "actor", "sensor", "kind", "observation", "payload", "{\"temperature\":17,\"condition\":\"rain\"}"));
        await().atMost(Duration.ofSeconds(6)).until(() -> provider.calls.get(providerId).sent.stream().anyMatch(event -> event.toString().contains("obs.weather.current")));
        postJson(path + "/acknowledge", Map.of("type", "obs.hand.sign", "actor", "sensor", "kind", "observation", "payload", "{\"sign\":\"rock\",\"confidence\":1}"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var fresh = agents.findById(id).orElseThrow();
            assertEquals(1, fresh.getStorage().get(RpsStorageKeys.ROUNDS).getAsJsonArray().size());
            assertTrue(fresh.getEventHistory().toList().stream().anyMatch(event -> event.speechProvenance() != null && event.speechProvenance().origin() == SpeechProvenance.Origin.NATIVE));
            assertTrue(stream.getResponse().getContentAsString().contains("NATIVE"));
        });
        var fresh = agents.findById(id).orElseThrow();
        assertEquals(1, fresh.getEventHistory().toList().stream().filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).count());
        assertEquals(0, provider.tts.get());
        mvc.perform(delete(path + "/live/sessions/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(jsonPath("$.finalized").value(true));
        var selected = contexts.snapshot(code, id).orElseThrow();
        var replacement = postJson(path + "/live/sessions", Map.of("sdp", "v=0 replacement"));
        assertNotEquals(handle.toString(), replacement.get("handle").asText());
        assertEquals(selected.startupInput(), provider.calls.get(provider.latest).request.input());
        assertEquals(selected.instructions(), provider.calls.get(provider.latest).request.instructions());
        mvc.perform(delete(path + "/live/sessions/" + replacement.get("handle").asText()).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isOk());
        properties.setEnabled(false);
        try {
            mvc.perform(get(path + "/live/capabilities").header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(jsonPath("$.enabled").value(false));
            postJson(path + "/acknowledge", Map.of("type", "obs.user_utterance", "actor", "user", "kind", "observation", "payload", "Explain the result"));
            var generated = postJson(path + "/behaviour/generate", Map.of("outputProfile", "full_plan"));
            Event speech = agents.findById(id).orElseThrow().getEventHistory().toList().stream()
                .filter(event -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()) && event.speechProvenance() == null).reduce((a,b) -> b).orElseThrow();
            var response = mvc.perform(post(path + "/behaviours/" + speech.getId() + "/speech?format=pcm")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(request().asyncStarted()).andReturn();
            mvc.perform(asyncDispatch(response)).andExpect(status().isOk()); assertEquals(1, provider.tts.get());
        } finally { properties.setEnabled(true); }
        mvc.perform(delete(path).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isNoContent());
        assertTrue(agents.findById(id).isEmpty());
    }
}
