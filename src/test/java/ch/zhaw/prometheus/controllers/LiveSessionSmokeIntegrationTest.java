package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.application.ScopedDemoService;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;

@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
@AutoConfigureMockMvc
class LiveSessionSmokeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired ch.zhaw.prometheus.application.LiveAgentContextService contexts;
    @MockitoBean LiveSessionGateway gateway;
    @MockitoBean LanguageModelGateway language;
    @Autowired ch.zhaw.prometheus.repositories.AgentRepository agents;

    @Test void persistedCapabilityIsExposedAndLegacyProfileCannotBypassLiveGate() throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of("core.live_multimodal"));
        UUID id = demo.createAgent(code, "core.live_multimodal").getID();
        String path = "/demo/agents/" + id;
        try {
            mvc.perform(get(path + "/info").header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.interactionProfile.externalRealtimeSpeech").value(true));
            mvc.perform(get(path + "/live/capabilities").header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
            var agent = agents.findById(id).orElseThrow();
            var legacy = com.google.gson.JsonParser.parseString(agent.getInteractionProfile().toJson()).getAsJsonObject();
            legacy.remove("externalRealtimeSpeech");
            org.springframework.test.util.ReflectionTestUtils.setField(agent, "interactionProfileJson", legacy.toString());
            agents.saveAndFlush(agent);
            assertFalse(agents.findById(id).orElseThrow().getInteractionProfile().isExternalRealtimeSpeech());
            mvc.perform(get(path + "/live/capabilities").header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false));
            mvc.perform(post(path + "/live/sessions").header(ScopedDemoController.ACCESS_CODE_HEADER, code)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"sdp\":\"v=0 offer\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(gateway, language);
        } finally {
            mvc.perform(delete(path).header(ScopedDemoController.ACCESS_CODE_HEADER, code)).andExpect(status().isNoContent());
        }
    }

    @Test void realDatabaseScopeCreatesAndClosesOnlyOwnedDiagnosticSession() throws Exception {
        when(language.infer(any())).thenReturn("{\"speech\":\"Ready\",\"nonVerbal\":{\"gesture\":\"NONE\"}}");
        when(language.complete(any())).thenReturn("Ready");
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        String other = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.createAccessCode(other, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of("core.multimodal_behaviour"));
        UUID agent = demo.createAgent(code, "core.multimodal_behaviour").getID();
        var snapshot = contexts.snapshot(code, agent).orElseThrow();
        var reloaded = contexts.snapshot(code, agent).orElseThrow();
        assertEquals(snapshot.epoch(), reloaded.epoch()); assertEquals(snapshot.revision(), reloaded.revision());
        assertFalse(snapshot.items().isEmpty()); assertNotNull(snapshot.items().getFirst().receivedAt());
        assertFalse(snapshot.items().getFirst().sourceIds().isEmpty());
        assertTrue(contexts.snapshot(other, agent).isEmpty());
        when(gateway.create(any())).thenReturn(new LiveSessionGateway.Session("live_synthetic", "v=0 answer"));
        when(gateway.attach(anyString(), any(), any())).thenAnswer(call -> {
            Consumer<JsonObject> receive = call.getArgument(1);
            return new LiveSessionGateway.Connection() {
                public boolean isOpen() { return true; }
                public void close() {}
                public void send(JsonObject command) {
                    if (command.get("type").getAsString().equals("session.close")) {
                        JsonObject closed = new JsonObject(); closed.addProperty("type", "session.closed"); receive.accept(closed);
                    }
                }
            };
        });
        String path = "/demo/agents/" + agent + "/live/sessions";
        String body = mvc.perform(post(path).header(ScopedDemoController.ACCESS_CODE_HEADER, code)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sdp\":\"v=0 offer\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID handle = UUID.fromString(json.readTree(body).get("handle").asText());
        mvc.perform(get(path + "/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, other)).andExpect(status().isNotFound());
        mvc.perform(delete(path + "/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, other)).andExpect(status().isNotFound());
        mvc.perform(delete(path + "/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(true));
        mvc.perform(get(path + "/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalized").value(true));
        verify(gateway, times(1)).create(any()); verify(gateway, never()).hangup(anyString());
        assertTrue(demo.getAgentInfo(code, agent).isPresent());
    }
}
