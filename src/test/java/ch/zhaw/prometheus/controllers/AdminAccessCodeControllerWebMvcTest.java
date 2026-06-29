package ch.zhaw.prometheus.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.application.DuplicateAccessCodeException;
import ch.zhaw.prometheus.controllers.views.AccessCodePresetEntryView;
import ch.zhaw.prometheus.controllers.views.AccessCodePresetView;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;

@WebMvcTest(controllers = AdminAccessCodeController.class)
@TestPropertySource(properties = "prometheus.admin.token=root-token")
class AdminAccessCodeControllerWebMvcTest {
    private static final String HEADER = AdminAccessCodeController.ADMIN_TOKEN_HEADER;
    private static final String TOKEN = "root-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessCodeAdminService service;

    @Test
    void rejectsMissingAdminToken() throws Exception {
        this.mockMvc.perform(get("/admin/agent-types"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(this.service);
    }

    @Test
    void rejectsMissingAdminTokenForMutationAndScopedListEndpoints() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        this.mockMvc.perform(post("/admin/access-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code": "af7u1"
                        }
                        """))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(get("/admin/access-code-presets"))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(post("/admin/access-code-presets/starter_agents/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entries": []
                        }
                        """))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(patch("/admin/access-codes/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "enabled": false
                        }
                        """))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(put("/admin/access-codes/" + id + "/agent-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentTypeKeys": []
                        }
                        """))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(get("/admin/access-codes/" + id + "/agents"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(this.service);
    }

    @Test
    void rejectsInvalidAdminToken() throws Exception {
        this.mockMvc.perform(get("/admin/agent-types")
                .header(HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(this.service);
    }

    @Test
    void acceptsValidAdminTokenForAgentTypes() throws Exception {
        when(this.service.listAgentTypes()).thenReturn(List.of(
                new AdminAgentTypeView("basic.single_state_micro_coaching", "Micro Coach", "Micro coaching",
                        List.of("basic"))));

        this.mockMvc.perform(get("/admin/agent-types")
                .header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("basic.single_state_micro_coaching"))
                .andExpect(jsonPath("$[0].displayName").value("Micro Coach"))
                .andExpect(jsonPath("$[0].packagePath[0]").value("basic"));
    }

    @Test
    void acceptsValidAdminTokenForAccessCodePresets() throws Exception {
        when(this.service.listAccessCodePresets()).thenReturn(List.of(
                new AccessCodePresetView("starter_agents", "Starter agent access codes", List.of(
                        new AccessCodePresetEntryView("start", List.of(
                                "basic.single_state_micro_coaching",
                                "multimodal.single_state_in"))))));

        this.mockMvc.perform(get("/admin/access-code-presets")
                .header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("starter_agents"))
                .andExpect(jsonPath("$[0].displayName").value("Starter agent access codes"))
                .andExpect(jsonPath("$[0].entries[0].code").value("start"))
                .andExpect(jsonPath("$[0].entries[0].agentTypeKeys[0]")
                        .value("basic.single_state_micro_coaching"));
    }

    @Test
    void createsAccessCodeWithValidAdminToken() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(this.service.createAccessCode("af7u1", true))
                .thenReturn(new AccessCodeView(id, "af7u1", true, List.of()));

        this.mockMvc.perform(post("/admin/access-codes")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code": "af7u1",
                          "enabled": true
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.code").value("af7u1"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void appliesAccessCodePresetWithValidAdminToken() throws Exception {
        UUID firstId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID secondId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(this.service.applyAccessCodePreset(eq("starter_agents"), any()))
                .thenReturn(Optional.of(List.of(
                        new AccessCodeView(firstId, "start", true, List.of("basic.single_state_micro_coaching")),
                        new AccessCodeView(secondId, "multi", true, List.of("multimodal.single_state_in")))));

        this.mockMvc.perform(post("/admin/access-code-presets/starter_agents/apply")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entries": [
                            {
                              "code": "start",
                              "agentTypeKeys": ["basic.single_state_micro_coaching"]
                            },
                            {
                              "code": "multi",
                              "agentTypeKeys": ["multimodal.single_state_in"]
                            }
                          ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(firstId.toString()))
                .andExpect(jsonPath("$[0].code").value("start"))
                .andExpect(jsonPath("$[0].allowedAgentTypeKeys[0]")
                        .value("basic.single_state_micro_coaching"))
                .andExpect(jsonPath("$[1].code").value("multi"));
    }

    @Test
    void mapsDuplicateAndInvalidCreateRequests() throws Exception {
        when(this.service.createAccessCode("duP77", true)).thenThrow(new DuplicateAccessCodeException("duP77"));
        when(this.service.createAccessCode(eq("bad!!"), any())).thenThrow(new IllegalArgumentException("invalid"));
        when(this.service.applyAccessCodePreset(eq("starter_agents"), any()))
                .thenThrow(new DuplicateAccessCodeException("start"));
        when(this.service.applyAccessCodePreset(eq("invalid_preset"), any()))
                .thenThrow(new IllegalArgumentException("invalid"));

        this.mockMvc.perform(post("/admin/access-codes")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code": "duP77",
                          "enabled": true
                        }
                        """))
                .andExpect(status().isConflict());

        this.mockMvc.perform(post("/admin/access-codes")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "code": "bad!!"
                        }
                        """))
                .andExpect(status().isBadRequest());

        this.mockMvc.perform(post("/admin/access-code-presets/starter_agents/apply")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entries": []
                        }
                        """))
                .andExpect(status().isConflict());

        this.mockMvc.perform(post("/admin/access-code-presets/invalid_preset/apply")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entries": []
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesEnabledAndAllowedTypesWithValidToken() throws Exception {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(this.service.updateAccessCodeEnabled(id, false))
                .thenReturn(Optional.of(new AccessCodeView(id, "af7u1", false, List.of())));
        when(this.service.replaceAllowedAgentTypes(id, List.of("basic.single_state_micro_coaching")))
                .thenReturn(Optional.of(new AccessCodeView(id, "af7u1", false,
                        List.of("basic.single_state_micro_coaching"))));
        when(this.service.replaceAllowedAgentTypes(id, List.of()))
                .thenReturn(Optional.of(new AccessCodeView(id, "af7u1", false, List.of())));

        this.mockMvc.perform(patch("/admin/access-codes/" + id)
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "enabled": false
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        this.mockMvc.perform(put("/admin/access-codes/" + id + "/agent-types")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentTypeKeys": ["basic.single_state_micro_coaching"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedAgentTypeKeys[0]").value("basic.single_state_micro_coaching"));

        this.mockMvc.perform(put("/admin/access-codes/" + id + "/agent-types")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentTypeKeys": []
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedAgentTypeKeys").isEmpty());
    }

    @Test
    void returnsNotFoundForMissingAccessCode() throws Exception {
        UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(this.service.updateAccessCodeEnabled(id, true)).thenReturn(Optional.empty());
        when(this.service.applyAccessCodePreset(eq("missing"), any())).thenReturn(Optional.empty());

        this.mockMvc.perform(patch("/admin/access-codes/" + id)
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "enabled": true
                        }
                        """))
                .andExpect(status().isNotFound());

        this.mockMvc.perform(post("/admin/access-code-presets/missing/apply")
                .header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entries": []
                        }
                        """))
                .andExpect(status().isNotFound());
    }
}
