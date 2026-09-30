package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.*;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.model.interaction.AgentCapabilityDescription;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.InferencePurpose;
import fixtures.gptlive.LiveSmokeConfiguration;

/** Real scoped HTTP boundary, persistence and runtime; only external providers are synthetic. */
@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
@AutoConfigureMockMvc
@Import(LiveSmokeConfiguration.class)
class CapabilityAwarenessSmokeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AgentRepository agents;
    @Autowired AccessCodeRepository codes;
    @Autowired LiveSmokeConfiguration.Provider provider;
    private String code;
    private UUID codeId;
    private final List<UUID> created = new ArrayList<>();

    @BeforeEach void setup() {
        code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true); codeId = access.getId();
        admin.replaceAllowedAgentTypes(codeId, List.of("core.facial_expression_sensitivity", "usecases.healthcare.healthcare_conversation"));
    }
    @AfterEach void cleanup() throws Exception {
        for (UUID id : created) mvc.perform(delete(path(id)).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isNoContent());
        codes.deleteById(codeId);
    }

    @Test void scopedCreationReloadGenerationResetAndLiveAllUseThePersistedProfile() throws Exception {
        int before = provider.inferences.size();
        UUID id = create("core.facial_expression_sensitivity", true);
        var initial = agents.findById(id).orElseThrow();
        assertTrue(initial.getInteractionProfile().isCapabilityAwareness());
        String context = AgentCapabilityDescription.context(initial.getInteractionProfile());
        String firstHistoryId = initial.getEventHistory().toList().getFirst().getId().toString();
        postJson(path(id) + "/behaviour/generate", Map.of());
        var loaded = agents.findById(id).orElseThrow();
        assertEquals(firstHistoryId, loaded.getEventHistory().toList().getFirst().getId().toString());
        assertEquals(context, AgentCapabilityDescription.context(loaded.getInteractionProfile()));
        mvc.perform(delete(path(id) + "/reset").header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isOk());
        assertTrue(agents.findById(id).orElseThrow().getInteractionProfile().isCapabilityAwareness());
        assertBehaviourContexts(before, true);

        String handle = postJson(path(id) + "/live/sessions", Map.of("sdp", "v=0 synthetic-offer")).get("handle").asText();
        try {
            assertTrue(provider.calls.get(provider.latest).request.instructions().endsWith(context));
            mvc.perform(get(path(id) + "/info").header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(jsonPath("$.interactionProfile.capabilityAwareness").value(true));
            var foreign = admin.createAccessCode(UUID.randomUUID().toString().replace("-", "").substring(0, 5), true);
            try {
                mvc.perform(get(path(id) + "/info").header(ScopedDemoController.ACCESS_CODE_HEADER, foreign.getCode()))
                        .andExpect(status().isNotFound());
            } finally { codes.deleteById(foreign.getId()); }
        } finally {
            mvc.perform(delete(path(id) + "/live/sessions/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(status().isOk());
        }
    }

    @Test void legacySavedProfileRemainsOffAcrossGenerationAndResetWithoutLosingHistoryOnLoad() throws Exception {
        UUID id = create("core.facial_expression_sensitivity", true);
        var saved = agents.findById(id).orElseThrow();
        var legacy = JsonParser.parseString(saved.getInteractionProfile().toJson()).getAsJsonObject();
        legacy.remove("capabilityAwareness");
        ReflectionTestUtils.setField(saved, "interactionProfileJson", legacy.toString());
        agents.saveAndFlush(saved);
        int historySize = saved.getEventHistory().toList().size();
        var loaded = agents.findById(id).orElseThrow();
        assertEquals(historySize, loaded.getEventHistory().toList().size());
        assertFalse(loaded.getInteractionProfile().isCapabilityAwareness());
        int before = provider.inferences.size();
        postJson(path(id) + "/behaviour/generate", Map.of());
        mvc.perform(delete(path(id) + "/reset").header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isOk());
        assertBehaviourContexts(before, false);
        assertFalse(agents.findById(id).orElseThrow().getInteractionProfile().isCapabilityAwareness());
    }

    @Test void nonCoreInstanceKeepsOrdinaryAndLiveContextUnchanged() throws Exception {
        int before = provider.inferences.size();
        UUID id = create("usecases.healthcare.healthcare_conversation", false);
        postJson(path(id) + "/behaviour/generate", Map.of());
        assertBehaviourContexts(before, false);
        String handle = postJson(path(id) + "/live/sessions", Map.of("sdp", "v=0 synthetic-offer")).get("handle").asText();
        try {
            assertFalse(provider.calls.get(provider.latest).request.instructions().contains(AgentCapabilityDescription.MARKER));
        } finally {
            mvc.perform(delete(path(id) + "/live/sessions/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(status().isOk());
        }
    }

    private UUID create(String key, boolean aware) throws Exception {
        var result = postJson("/demo/agents", Map.of("agentDefinitionKey", key));
        UUID id = UUID.fromString(result.get("id").asText()); created.add(id);
        assertEquals(aware, result.get("interactionProfile").get("capabilityAwareness").asBoolean());
        return id;
    }
    private JsonNode postJson(String url, Object body) throws Exception {
        String response = mvc.perform(post(url).header(ScopedDemoController.ACCESS_CODE_HEADER, code)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? json.createObjectNode() : json.readTree(response);
    }
    private void assertBehaviourContexts(int from, boolean aware) {
        var requests = provider.inferences.subList(from, provider.inferences.size()).stream()
                .filter(r -> r.purpose() == InferencePurpose.BEHAVIOUR).toList();
        assertFalse(requests.isEmpty());
        requests.forEach(r -> assertEquals(aware ? 1 : 0, r.messages().stream()
                .filter(m -> m.getContent().startsWith(AgentCapabilityDescription.MARKER)).count()));
    }
    private static String path(UUID id) { return "/demo/agents/" + id; }
}
