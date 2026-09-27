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
            assertTrue(fresh.getCurrentState().getActiveStatePath().toString().contains("Reveal Sign"));
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
