package ch.zhaw.prometheus.controllers;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompilationException;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.ComponentRegistry;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionNotFoundException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;
import ch.zhaw.prometheus.definition.validation.AgentDefinitionSchemaException;
import ch.zhaw.prometheus.definition.validation.DefinitionValidationResult;
import ch.zhaw.prometheus.definition.validation.SchemaViolation;
import ch.zhaw.prometheus.definition.validation.SemanticDiagnosticCode;
import ch.zhaw.prometheus.definition.validation.ValidationDiagnostic;

@WebMvcTest(controllers = DesignerDefinitionController.class)
@TestPropertySource(properties = "prometheus.admin.token=root-token")
class DesignerDefinitionControllerWebMvcTest {
    private static final String HEADER = AdminAccessCodeController.ADMIN_TOKEN_HEADER;
    private static final String TOKEN = "root-token";
    private static final String KEY = "designer.test";
    private static final String DOCUMENT = """
            {"key":"designer.test","metadata":{"displayName":"Designer Test","description":"Test definition",
            "categoryPath":"designer.tests","languageCode":"en"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefinitionLifecycleService lifecycle;

    @MockitoBean
    private ComponentRegistry components;

    @Test
    void everyDesignerEndpointRejectsMissingAndInvalidAdminTokens() throws Exception {
        for (MockHttpServletRequestBuilder request : allRequests()) {
            this.mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder request : allRequests()) {
            this.mockMvc.perform(request.header(HEADER, "wrong-token")).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(this.lifecycle, this.components);
    }

    @Test
    void readsDefinitionsCanonicalExportAndDeterministicSafeComponentCatalog() throws Exception {
        StoredDefinition identity = identity();
        StoredDefinitionRevision revision = revision(DefinitionStatus.PUBLISHED, DefinitionProvenance.BUNDLED, 1);
        when(this.lifecycle.listDefinitions()).thenReturn(List.of(identity));
        when(this.lifecycle.requireDefinition(KEY)).thenReturn(identity);
        when(this.lifecycle.listRevisions(KEY)).thenReturn(List.of(revision));
        when(this.lifecycle.requireRevision(KEY, 1)).thenReturn(revision);
        when(this.lifecycle.export(KEY, 1)).thenReturn(DOCUMENT);
        when(this.components.definitions()).thenReturn(BuiltInComponentCatalog.createRegistry().definitions());

        this.mockMvc.perform(get("/admin/agent-definitions").header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value(KEY))
                .andExpect(jsonPath("$[0].displayName").value("Designer Test"))
                .andExpect(jsonPath("$[0].categoryPath[0]").value("designer"))
                .andExpect(jsonPath("$[0].categoryPath[1]").value("tests"))
                .andExpect(jsonPath("$[0].activeRevision").value(1))
                .andExpect(jsonPath("$[0].revisions[0].provenance").value("BUNDLED"));
        this.mockMvc.perform(get("/admin/agent-definitions/" + KEY).header(HEADER, TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key").value(KEY));
        this.mockMvc.perform(get("/admin/agent-definitions/" + KEY + "/revisions/1").header(HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.key").value(KEY))
                .andExpect(jsonPath("$.optimisticVersion").value(0));
        this.mockMvc.perform(get("/admin/agent-definitions/" + KEY + "/revisions/1/export")
                .header(HEADER, TOKEN))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(DOCUMENT, true));
        String catalog = this.mockMvc.perform(get("/admin/agent-definitions/component-catalog")
                .header(HEADER, TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(23))
                .andExpect(jsonPath("$[0].kind").value("prometheus.action.extract"))
                .andExpect(jsonPath("$[0].configSchema").isMap())
                .andExpect(jsonPath("$[0].defaultConfig").exists())
                .andExpect(jsonPath("$[0].examples").isArray())
                .andExpect(jsonPath("$[0].capabilities").isMap())
                .andExpect(jsonPath("$[0].authoringRole").value("OUTCOME_EXTRACTION"))
                .andExpect(jsonPath("$[0].exposure").value("GUIDED"))
                .andExpect(jsonPath("$[0].capabilityGroup").value("outcome-report"))
                .andExpect(jsonPath("$[0].advancedReason").doesNotExist())
                .andExpect(jsonPath("$[0].configSchema.title").value("Extract value"))
                .andExpect(jsonPath("$[0].configSchema.properties.targetStorageKey.title")
                        .value("Target Storage Key"))
                .andExpect(jsonPath("$[3].capabilityGroup").value("rock-scissor-paper"))
                .andExpect(jsonPath("$[3].authoringRole").value("DETERMINISTIC_OPERATION"))
                .andExpect(jsonPath("$[5].authoringRole").value("RULE_TRIGGER"))
                .andExpect(jsonPath("$[5].exposure").value("GENERATED_INTERNAL"))
                .andExpect(jsonPath("$[5].advancedReason").isNotEmpty())
                .andExpect(jsonPath("$[6].authoringRole").value("RULE_CONDITION"))
                .andExpect(jsonPath("$[8].authoringRole").value("DATA_INITIALIZER"))
                .andExpect(jsonPath("$[9].exposure").value("ADVANCED"))
                .andExpect(jsonPath("$[9].capabilityGroup").doesNotExist())
                .andExpect(jsonPath("$[9].advancedReason").isNotEmpty())
                .andExpect(jsonPath("$[11].capabilityGroup").value("prompt-response"))
                .andExpect(jsonPath("$[14].authoringRole").value("DATA_RESOURCE"))
                .andExpect(jsonPath("$[22].exposure").value("ADVANCED"))
                .andExpect(jsonPath("$[22].advancedReason").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(catalog.matches(
                "(?is).*\\b(?:class(?:name)?|bean(?:name)?|scripts?|sourcecode)\\b.*"));
    }

    @Test
    void performsDraftImportUpdateValidatePublishActivateArchiveAndClone() throws Exception {
        StoredDefinitionRevision draft = revision(DefinitionStatus.DRAFT, DefinitionProvenance.DESIGNER, 1);
        StoredDefinitionRevision imported = revision(DefinitionStatus.DRAFT, DefinitionProvenance.IMPORTED, 1);
        StoredDefinitionRevision published = revision(DefinitionStatus.PUBLISHED, DefinitionProvenance.DESIGNER, 1);
        StoredDefinitionRevision clone = revision(DefinitionStatus.DRAFT, DefinitionProvenance.DESIGNER, 2);
        StoredDefinition activated = new StoredDefinition(1, KEY, published.id(), 2, Instant.EPOCH, Instant.EPOCH);
        when(this.lifecycle.createDraft(anyString(), eq(DefinitionProvenance.DESIGNER), eq("designer-api")))
                .thenReturn(draft);
        when(this.lifecycle.createDraft(anyString(), eq(DefinitionProvenance.IMPORTED),
                eq("designer-api-import"))).thenReturn(imported);
        when(this.lifecycle.updateDraft(eq(KEY), eq(1), anyString(), eq(0L))).thenReturn(draft);
        when(this.lifecycle.validate(anyString())).thenReturn(new DefinitionValidationResult(List.of(
                ValidationDiagnostic.of(SemanticDiagnosticCode.UNUSED_OBSERVATION, "/interaction",
                        "Unused observation", "Remove or use it"))));
        when(this.lifecycle.validateForPublication(anyString())).thenReturn(new DefinitionValidationResult(List.of()));
        when(this.lifecycle.publish(KEY, 1, 0)).thenReturn(published);
        when(this.lifecycle.activate(KEY, 1, 1)).thenReturn(activated);
        when(this.lifecycle.listRevisions(KEY)).thenReturn(List.of(published));
        when(this.lifecycle.archive(KEY, 1, 0)).thenReturn(published);
        when(this.lifecycle.cloneRevision(KEY, 1, KEY, 2)).thenReturn(clone);

        this.mockMvc.perform(post("/admin/agent-definitions").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.provenance").value("DESIGNER"));
        this.mockMvc.perform(post("/admin/agent-definitions/imports").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentRequest().replaceFirst("}$", ",\"provenance\":\"BUNDLED\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.provenance").value("IMPORTED"));
        verify(this.lifecycle).createDraft(anyString(), eq(DefinitionProvenance.IMPORTED),
                eq("designer-api-import"));
        this.mockMvc.perform(put("/admin/agent-definitions/" + KEY + "/revisions/1").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(updateRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        this.mockMvc.perform(post("/admin/agent-definitions/validation").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.diagnostics[0].code").value("UNUSED_OBSERVATION"))
                .andExpect(jsonPath("$.diagnostics[0].pointer").value("/interaction"));
        this.mockMvc.perform(post("/admin/agent-definitions/prompt-previews").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"definition":{"states":[{"policy":{"config":{"responsePrompt":{"sections":[
                        {"id":"purpose","kind":"objective","content":"First"},
                        {"id":"guardrail","kind":"constraint","content":"Second"}]}}}}]}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pointer").value("/states/0/policy/config/responsePrompt"))
                .andExpect(jsonPath("$[0].label").value("Response Prompt"))
                .andExpect(jsonPath("$[0].composed").value("First\n\nSecond"));
        this.mockMvc.perform(post("/admin/agent-definitions/publication-readiness").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/publish")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/activate")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeRevision").value(1));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/archive")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(0)))
                .andExpect(status().isOk());
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/clone")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetKey\":\"" + KEY + "\",\"targetRevision\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(2));
    }

    @Test
    void mapsMalformedValidationNotFoundConcurrencyAndLifecycleFailuresConsistently() throws Exception {
        when(this.lifecycle.requireDefinition("missing"))
                .thenThrow(new DefinitionNotFoundException("not found"));
        when(this.lifecycle.requireRevision(KEY, 99))
                .thenThrow(new DefinitionNotFoundException("revision not found"));
        when(this.lifecycle.updateDraft(eq(KEY), eq(1), anyString(), anyLong()))
                .thenThrow(new DefinitionOptimisticLockException("stale"));
        when(this.lifecycle.updateDraft(eq(KEY), eq(2), anyString(), anyLong()))
                .thenThrow(new DefinitionLifecycleException("published content is immutable"));
        when(this.lifecycle.activate(KEY, 1, 0))
                .thenThrow(new DefinitionLifecycleException("draft cannot activate"));
        when(this.lifecycle.archive(KEY, 1, 0))
                .thenThrow(new DefinitionLifecycleException("active cannot archive"));
        when(this.lifecycle.createDraft(anyString(), eq(DefinitionProvenance.IMPORTED), anyString()))
                .thenThrow(new DefinitionLifecycleException("duplicate import"));
        when(this.lifecycle.validate(anyString())).thenThrow(new AgentDefinitionSchemaException(
                List.of(new SchemaViolation("/key", "pattern", "does not match"))));
        DefinitionValidationResult invalid = new DefinitionValidationResult(List.of(
                ValidationDiagnostic.of(SemanticDiagnosticCode.MISSING_INITIAL_STATE, "/lifecycle/initialStateId",
                        "Missing initial state", "Select an existing state")));
        when(this.lifecycle.publish(KEY, 1, 0))
                .thenThrow(new DefinitionCompilationException("invalid", invalid));
        when(this.lifecycle.validateForPublication(anyString()))
                .thenThrow(new DefinitionCompilationException("invalid", invalid));

        this.mockMvc.perform(post("/admin/agent-definitions").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        this.mockMvc.perform(get("/admin/agent-definitions/missing").header(HEADER, TOKEN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        this.mockMvc.perform(get("/admin/agent-definitions/" + KEY + "/revisions/99").header(HEADER, TOKEN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        this.mockMvc.perform(put("/admin/agent-definitions/" + KEY + "/revisions/1").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(updateRequest()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPTIMISTIC_CONFLICT"));
        this.mockMvc.perform(put("/admin/agent-definitions/" + KEY + "/revisions/2").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(updateRequest()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIFECYCLE_CONFLICT"));
        this.mockMvc.perform(post("/admin/agent-definitions/validation").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCHEMA_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("SCHEMA_PATTERN"))
                .andExpect(jsonPath("$.diagnostics[0].pointer").value("/key"));
        this.mockMvc.perform(post("/admin/agent-definitions/publication-readiness").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("MISSING_INITIAL_STATE"));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/publish")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.diagnostics[0].code").value("MISSING_INITIAL_STATE"))
                .andExpect(jsonPath("$.diagnostics[0].pointer").value("/lifecycle/initialStateId"));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/activate")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIFECYCLE_CONFLICT"));
        this.mockMvc.perform(post("/admin/agent-definitions/" + KEY + "/revisions/1/archive")
                .header(HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON).content(versionRequest(0)))
                .andExpect(status().isConflict());
        this.mockMvc.perform(post("/admin/agent-definitions/imports").header(HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(documentRequest()))
                .andExpect(status().isConflict());
    }

    private static List<MockHttpServletRequestBuilder> allRequests() {
        return List.of(
                get("/admin/agent-definitions"),
                get("/admin/agent-definitions/" + KEY),
                get("/admin/agent-definitions/" + KEY + "/revisions/1"),
                get("/admin/agent-definitions/" + KEY + "/revisions/1/export"),
                get("/admin/agent-definitions/component-catalog"),
                post("/admin/agent-definitions").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/imports").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/validation").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/prompt-previews").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/publication-readiness").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/admin/agent-definitions/" + KEY + "/revisions/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/" + KEY + "/revisions/1/publish")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/" + KEY + "/revisions/1/activate")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/" + KEY + "/revisions/1/archive")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/admin/agent-definitions/" + KEY + "/revisions/1/clone")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private static StoredDefinition identity() {
        return new StoredDefinition(1, KEY, 1L, 1, Instant.EPOCH, Instant.EPOCH);
    }

    private static StoredDefinitionRevision revision(DefinitionStatus status, DefinitionProvenance provenance,
            int number) {
        return new StoredDefinitionRevision(number, 1, KEY, number, 1, status, DOCUMENT, "a".repeat(64),
                provenance, "test", 0, Instant.EPOCH, Instant.EPOCH,
                status == DefinitionStatus.PUBLISHED ? Instant.EPOCH : null,
                status == DefinitionStatus.ARCHIVED ? Instant.EPOCH : null);
    }

    private static String documentRequest() {
        return "{\"definition\":" + DOCUMENT + "}";
    }

    private static String updateRequest() {
        return "{\"definition\":" + DOCUMENT + ",\"optimisticVersion\":0}";
    }

    private static String versionRequest(long version) {
        return "{\"optimisticVersion\":" + version + "}";
    }
}
