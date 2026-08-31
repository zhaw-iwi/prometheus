package ch.zhaw.prometheus.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompilationException;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewDiagnostic;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewOperation;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewSnapshot;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewSource;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.ScenarioExecution;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.ScenarioExpectationResult;
import ch.zhaw.prometheus.definition.preview.PreviewExecutionException;
import ch.zhaw.prometheus.definition.preview.PreviewLimitException;
import ch.zhaw.prometheus.definition.preview.PreviewNotFoundException;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.validation.DefinitionValidationResult;
import ch.zhaw.prometheus.definition.validation.SemanticDiagnosticCode;
import ch.zhaw.prometheus.definition.validation.ValidationDiagnostic;

@WebMvcTest(controllers = DesignerPreviewController.class)
@TestPropertySource(properties = "prometheus.admin.token=root-token")
class DesignerPreviewControllerWebMvcTest {
    private static final String HEADER = AdminAccessCodeController.ADMIN_TOKEN_HEADER;
    private static final String TOKEN = "root-token";
    private static final UUID ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DesignerPreviewService previews;

    @MockitoBean
    private DefinitionLifecycleService lifecycle;

    @Test
    void everyPreviewEndpointRejectsMissingAndInvalidAdminToken() throws Exception {
        for (MockHttpServletRequestBuilder request : allRequests()) {
            this.mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder request : allRequests()) {
            this.mockMvc.perform(request.header(HEADER, "wrong-token")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(this.previews, this.lifecycle);
    }

    @Test
    void createsUnsavedAndSavedPreviewsAndOperatesCompleteLifecycle() throws Exception {
        PreviewSnapshot snapshot = snapshot();
        when(this.previews.create(anyString(), eq(PreviewSource.UNSAVED), eq(null))).thenReturn(snapshot);
        StoredDefinitionRevision saved = storedRevision();
        when(this.lifecycle.requireRevision("designer.test", 2)).thenReturn(saved);
        when(this.previews.create(saved.canonicalJson(), PreviewSource.SAVED, saved.id())).thenReturn(snapshot);
        when(this.previews.inspect(ID)).thenReturn(snapshot);
        when(this.previews.acknowledge(eq(ID), any(RuntimeEvent.class))).thenReturn(snapshot);
        when(this.previews.generate(ID)).thenReturn(snapshot);
        when(this.previews.reset(ID)).thenReturn(snapshot);
        when(this.previews.executeScenario(anyString(), eq(0))).thenReturn(scenarioExecution());

        this.mockMvc.perform(post("/admin/agent-definitions/previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"definition\":{\"key\":\"draft\"}}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/admin/agent-definitions/previews/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.source").value("UNSAVED"))
                .andExpect(jsonPath("$.activeStatePath[0]").value("talk"));
        this.mockMvc.perform(post("/admin/agent-definitions/previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"designer.test\",\"revision\":2}"))
                .andExpect(status().isCreated());
        verify(this.lifecycle).requireRevision("designer.test", 2);
        this.mockMvc.perform(get("/admin/agent-definitions/previews/" + ID).header(HEADER, TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transcript[0].kind").value("CREATE"));
        this.mockMvc.perform(post("/admin/agent-definitions/previews/" + ID + "/events").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"obs.user_utterance\",\"actor\":\"user\","
                        + "\"kind\":\"observation\",\"payload\":\"hello\"}"))
                .andExpect(status().isOk());
        this.mockMvc.perform(post("/admin/agent-definitions/previews/" + ID + "/generate")
                .header(HEADER, TOKEN)).andExpect(status().isOk());
        this.mockMvc.perform(post("/admin/agent-definitions/previews/" + ID + "/reset")
                .header(HEADER, TOKEN)).andExpect(status().isOk());
        this.mockMvc.perform(post("/admin/agent-definitions/previews/scenarios").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definition\":{\"key\":\"draft\"},\"scenarioIndex\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.expectations[0].explanation").value("Matched from deterministic trace."))
                .andExpect(jsonPath("$.discarded").value(true));
        this.mockMvc.perform(delete("/admin/agent-definitions/previews/" + ID).header(HEADER, TOKEN))
                .andExpect(status().isNoContent());
        verify(this.previews).close(ID);
    }

    @Test
    void mapsInvalidSourceExpiredLimitExecutionAndCompilationFailuresSafely() throws Exception {
        when(this.previews.inspect(ID)).thenThrow(new PreviewNotFoundException());
        when(this.previews.generate(ID)).thenThrow(new PreviewLimitException("limit detail"));
        when(this.previews.reset(ID)).thenThrow(new PreviewExecutionException(
                new IllegalStateException("provider-secret-detail")));
        DefinitionValidationResult invalid = new DefinitionValidationResult(List.of(
                ValidationDiagnostic.of(SemanticDiagnosticCode.MISSING_INITIAL_STATE, "/lifecycle/initialStateId",
                        "Missing initial state", "Select an existing state")));
        when(this.previews.create(anyString(), eq(PreviewSource.UNSAVED), eq(null)))
                .thenThrow(new DefinitionCompilationException("invalid", invalid));
        when(this.lifecycle.requireRevision("published.test", 1)).thenReturn(storedRevision(DefinitionStatus.PUBLISHED));

        this.mockMvc.perform(post("/admin/agent-definitions/previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definition\":{},\"key\":\"both\",\"revision\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        this.mockMvc.perform(get("/admin/agent-definitions/previews/" + ID).header(HEADER, TOKEN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PREVIEW_NOT_FOUND"));
        this.mockMvc.perform(post("/admin/agent-definitions/previews/" + ID + "/generate")
                .header(HEADER, TOKEN)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PREVIEW_LIMIT"));
        String execution = this.mockMvc.perform(post("/admin/agent-definitions/previews/" + ID + "/reset")
                .header(HEADER, TOKEN)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREVIEW_EXECUTION_FAILED"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(execution.contains("provider-secret-detail"));
        this.mockMvc.perform(post("/admin/agent-definitions/previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"definition\":{}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("MISSING_INITIAL_STATE"));
        this.mockMvc.perform(post("/admin/agent-definitions/previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"published.test\",\"revision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIFECYCLE_CONFLICT"));
    }

    private static List<MockHttpServletRequestBuilder> allRequests() {
        return List.of(
                post("/admin/agent-definitions/previews").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/admin/agent-definitions/previews/" + ID),
                post("/admin/agent-definitions/previews/" + ID + "/events")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/previews/" + ID + "/generate"),
                post("/admin/agent-definitions/previews/" + ID + "/reset"),
                post("/admin/agent-definitions/previews/scenarios")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/admin/agent-definitions/previews/" + ID));
    }

    private static PreviewSnapshot snapshot() {
        Instant now = Instant.parse("2026-08-30T10:00:00Z");
        PreviewOperation creation = new PreviewOperation(1, "CREATE", now, null, List.of("talk"), Map.of(),
                List.of(), null, List.<PreviewDiagnostic>of());
        return new PreviewSnapshot(ID, PreviewSource.UNSAVED, null, "designer.test", 2, now, now,
                now.plusSeconds(900), List.of("talk"), Map.of(), List.of(), false, true,
                List.of(creation), List.of());
    }

    private static ScenarioExecution scenarioExecution() {
        var expectation = new ScenarioExpectationResult("active-state-path", "Active situation path", true,
                JsonNodeFactory.instance.arrayNode().add("talk"), JsonNodeFactory.instance.arrayNode().add("talk"),
                "Matched from deterministic trace.");
        return new ScenarioExecution(0, "Exact speech", true, List.of(expectation), List.of("talk"), Map.of(),
                List.of("repeat"), List.of(), List.of("speech"), snapshot().transcript(), List.of(), true);
    }

    private static StoredDefinitionRevision storedRevision() {
        return storedRevision(DefinitionStatus.DRAFT);
    }

    private static StoredDefinitionRevision storedRevision(DefinitionStatus status) {
        return new StoredDefinitionRevision(17, 3, "designer.test", 2, 1, status,
                "{\"key\":\"designer.test\"}", "a".repeat(64), DefinitionProvenance.DESIGNER, "test", 0,
                Instant.EPOCH, Instant.EPOCH, status == DefinitionStatus.DRAFT ? null : Instant.EPOCH, null);
    }
}
