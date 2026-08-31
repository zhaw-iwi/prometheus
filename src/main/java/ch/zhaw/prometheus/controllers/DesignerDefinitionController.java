package ch.zhaw.prometheus.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.component.AgentComponentDefinition;
import ch.zhaw.prometheus.definition.component.ComponentAuthoringExposure;
import ch.zhaw.prometheus.definition.component.ComponentAuthoringRole;
import ch.zhaw.prometheus.definition.component.ComponentCategory;
import ch.zhaw.prometheus.definition.component.ComponentRegistry;
import ch.zhaw.prometheus.definition.document.PromptDefinition;
import ch.zhaw.prometheus.definition.prompt.PromptComposer;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;
import ch.zhaw.prometheus.definition.validation.ComponentReference;
import ch.zhaw.prometheus.definition.validation.ComponentSemantics;
import ch.zhaw.prometheus.definition.validation.ComponentStorageUse;
import ch.zhaw.prometheus.definition.validation.DefinitionValidationResult;
import ch.zhaw.prometheus.definition.validation.ValidationDiagnostic;

/** Administrative JSON-definition lifecycle boundary used by Valerian Designer. */
@RestController
@RequestMapping("/admin/agent-definitions")
public class DesignerDefinitionController {
    private static final String ADMIN_TOKEN_HEADER = AdminAccessCodeController.ADMIN_TOKEN_HEADER;
    private static final PromptComposer PROMPT_COMPOSER = new PromptComposer();

    private final DefinitionLifecycleService lifecycle;
    private final ComponentRegistry components;
    private final ObjectMapper objectMapper;
    private final String adminToken;

    public DesignerDefinitionController(DefinitionLifecycleService lifecycle, ComponentRegistry components,
            ObjectMapper objectMapper, @Value("${prometheus.admin.token:}") String adminToken) {
        this.lifecycle = lifecycle;
        this.components = components;
        this.objectMapper = objectMapper;
        this.adminToken = adminToken;
    }

    @GetMapping
    public ResponseEntity<List<DefinitionSummaryView>> definitions(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(this.lifecycle.listDefinitions().stream().map(this::summary).toList());
    }

    @GetMapping("/{key}")
    public ResponseEntity<DefinitionSummaryView> definition(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(summary(this.lifecycle.requireDefinition(key)));
    }

    @GetMapping("/{key}/revisions/{revisionNumber}")
    public ResponseEntity<DefinitionRevisionView> revision(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(revisionView(this.lifecycle.requireRevision(key, revisionNumber)));
    }

    @GetMapping(value = "/{key}/revisions/{revisionNumber}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> export(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(this.lifecycle.export(key, revisionNumber));
    }

    @PostMapping
    public ResponseEntity<DefinitionRevisionView> createDraft(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) DefinitionDocumentRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        StoredDefinitionRevision created = this.lifecycle.createDraft(documentJson(request),
                DefinitionProvenance.DESIGNER, "designer-api");
        return new ResponseEntity<>(revisionView(created), HttpStatus.CREATED);
    }

    @PostMapping("/imports")
    public ResponseEntity<DefinitionRevisionView> importDefinition(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) DefinitionDocumentRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        StoredDefinitionRevision imported = this.lifecycle.createDraft(documentJson(request),
                DefinitionProvenance.IMPORTED, "designer-api-import");
        return new ResponseEntity<>(revisionView(imported), HttpStatus.CREATED);
    }

    @PutMapping("/{key}/revisions/{revisionNumber}")
    public ResponseEntity<DefinitionRevisionView> updateDraft(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber,
            @RequestBody(required = false) DefinitionDraftUpdateRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        require(request != null && request.optimisticVersion() != null && request.definition() != null,
                "A definition and optimisticVersion are required");
        return ResponseEntity.ok(revisionView(this.lifecycle.updateDraft(key, revisionNumber,
                request.definition().toString(), request.optimisticVersion())));
    }

    @PostMapping("/validation")
    public ResponseEntity<DefinitionValidationView> validate(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) DefinitionDocumentRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(validationView(this.lifecycle.validate(documentJson(request))));
    }

    @PostMapping("/prompt-previews")
    public ResponseEntity<List<PromptPreviewView>> promptPreviews(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) DefinitionDocumentRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        String json = documentJson(request);
        this.lifecycle.validate(json);
        List<PromptPreviewView> previews = new java.util.ArrayList<>();
        collectPromptPreviews(request.definition(), "", previews);
        previews.sort(java.util.Comparator.comparing(PromptPreviewView::pointer));
        return ResponseEntity.ok(List.copyOf(previews));
    }

    @PostMapping("/publication-readiness")
    public ResponseEntity<DefinitionValidationView> publicationReadiness(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) DefinitionDocumentRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(validationView(this.lifecycle.validateForPublication(documentJson(request))));
    }

    @PostMapping("/{key}/revisions/{revisionNumber}/publish")
    public ResponseEntity<DefinitionRevisionView> publish(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber,
            @RequestBody(required = false) OptimisticVersionRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(revisionView(this.lifecycle.publish(key, revisionNumber, version(request))));
    }

    @PostMapping("/{key}/revisions/{revisionNumber}/activate")
    public ResponseEntity<DefinitionSummaryView> activate(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber,
            @RequestBody(required = false) OptimisticVersionRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(summary(this.lifecycle.activate(key, revisionNumber, version(request))));
    }

    @PostMapping("/{key}/revisions/{revisionNumber}/archive")
    public ResponseEntity<DefinitionRevisionView> archive(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber,
            @RequestBody(required = false) OptimisticVersionRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(revisionView(this.lifecycle.archive(key, revisionNumber, version(request))));
    }

    @PostMapping("/{key}/revisions/{revisionNumber}/clone")
    public ResponseEntity<DefinitionRevisionView> cloneRevision(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable String key, @PathVariable int revisionNumber,
            @RequestBody(required = false) DefinitionCloneRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        require(request != null && request.targetKey() != null && !request.targetKey().isBlank()
                && request.targetRevision() != null && request.targetRevision() > 0,
                "targetKey and a positive targetRevision are required");
        StoredDefinitionRevision clone = this.lifecycle.cloneRevision(key, revisionNumber, request.targetKey(),
                request.targetRevision());
        return new ResponseEntity<>(revisionView(clone), HttpStatus.CREATED);
    }

    @GetMapping("/component-catalog")
    public ResponseEntity<List<ComponentView>> componentCatalog(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(this.components.definitions().stream().map(DesignerDefinitionController::component)
                .toList());
    }

    private DefinitionSummaryView summary(StoredDefinition definition) {
        List<StoredDefinitionRevision> revisions = this.lifecycle.listRevisions(definition.key());
        StoredDefinitionRevision descriptive = definition.activeRevisionId() == null
                ? revisions.getLast()
                : revisions.stream().filter(revision -> revision.id() == definition.activeRevisionId()).findFirst()
                        .orElse(revisions.getLast());
        JsonNode document = tree(descriptive.canonicalJson());
        Integer activeRevisionNumber = revisions.stream()
                .filter(revision -> definition.activeRevisionId() != null
                        && revision.id() == definition.activeRevisionId())
                .map(StoredDefinitionRevision::revisionNumber).findFirst().orElse(null);
        return new DefinitionSummaryView(definition.key(), definition.activeRevisionId(), activeRevisionNumber,
                definition.optimisticVersion(), document.path("metadata").path("displayName").asText(),
                document.path("metadata").path("description").asText(),
                categoryPath(document.path("metadata").path("categoryPath")),
                document.path("metadata").path("languageCode").isNull() ? null
                        : document.path("metadata").path("languageCode").asText(),
                revisions.stream().map(DesignerDefinitionController::revisionSummary).toList());
    }

    private DefinitionRevisionView revisionView(StoredDefinitionRevision revision) {
        return new DefinitionRevisionView(revision.id(), revision.definitionKey(), revision.revisionNumber(),
                revision.schemaVersion(), revision.status(), revision.contentHash(), revision.provenance(),
                revision.sourceDetail(), revision.optimisticVersion(), revision.createdAt(), revision.updatedAt(),
                revision.publishedAt(), revision.archivedAt(), tree(revision.canonicalJson()));
    }

    private JsonNode tree(String json) {
        try {
            return this.objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored canonical definition JSON is invalid", exception);
        }
    }

    private boolean isAuthorized(String token) {
        return this.adminToken != null && !this.adminToken.isBlank() && token != null && this.adminToken.equals(token);
    }

    private static String documentJson(DefinitionDocumentRequest request) {
        require(request != null && request.definition() != null, "A definition document is required");
        return request.definition().toString();
    }

    private static long version(OptimisticVersionRequest request) {
        require(request != null && request.optimisticVersion() != null,
                "An optimisticVersion is required");
        return request.optimisticVersion();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T> ResponseEntity<T> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    private static DefinitionRevisionSummaryView revisionSummary(StoredDefinitionRevision revision) {
        return new DefinitionRevisionSummaryView(revision.id(), revision.revisionNumber(), revision.status(),
                revision.contentHash(), revision.provenance(), revision.optimisticVersion(), revision.updatedAt());
    }

    private static DefinitionValidationView validationView(DefinitionValidationResult result) {
        return new DefinitionValidationView(result.isValid(), result.diagnostics().stream()
                .map(DesignerDefinitionController::diagnostic).toList());
    }

    static DefinitionDiagnosticView diagnostic(ValidationDiagnostic diagnostic) {
        return new DefinitionDiagnosticView(diagnostic.code().name(), diagnostic.severity().name(),
                diagnostic.pointer(), diagnostic.message(), diagnostic.hint());
    }

    private static ComponentView component(AgentComponentDefinition definition) {
        ComponentSemantics semantics = definition.semantics(definition.uiMetadata().defaultConfig().value());
        return new ComponentView(definition.key().kind(), definition.key().version(), definition.category(),
                definition.configSchema(), definition.uiMetadata().label(), definition.uiMetadata().description(),
                definition.uiMetadata().authoringRole(), definition.uiMetadata().exposure(),
                definition.uiMetadata().capabilityGroup(), definition.uiMetadata().advancedReason(),
                definition.uiMetadata().defaultConfig().value(), definition.uiMetadata().examples().stream()
                        .map(example -> example.value()).toList(),
                new ComponentCapabilitiesView(sorted(semantics.consumedObservations()),
                        sorted(semantics.emittedBehaviourModalities()),
                        semantics.storageUses().stream().map(DesignerDefinitionController::storageUse).toList(),
                        semantics.resourceReferences().stream().map(DesignerDefinitionController::reference).toList(),
                        semantics.stateReferences().stream().map(DesignerDefinitionController::reference).toList()));
    }

    private static ComponentStorageUseView storageUse(ComponentStorageUse use) {
        return new ComponentStorageUseView(use.key(), use.access().name(), use.expectedValueSchema(),
                use.configPointer());
    }

    private static ComponentReferenceView reference(ComponentReference reference) {
        return new ComponentReferenceView(reference.id(), reference.configPointer(),
                reference.expectedComponent() == null ? null : reference.expectedComponent().kind(),
                reference.expectedComponent() == null ? null : reference.expectedComponent().version());
    }

    private static List<String> sorted(Set<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }

    private static List<String> textValues(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private void collectPromptPreviews(JsonNode node, String pointer, List<PromptPreviewView> previews) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String childPointer = pointer + "/" + escapePointer(field.getKey());
                if (field.getKey().toLowerCase(Locale.ROOT).endsWith("prompt")
                        && field.getValue().path("sections").isArray()) {
                    try {
                        PromptDefinition prompt = this.objectMapper.treeToValue(field.getValue(), PromptDefinition.class);
                        previews.add(new PromptPreviewView(childPointer, promptLabel(field.getKey()),
                                PROMPT_COMPOSER.compose(prompt)));
                    } catch (JsonProcessingException exception) {
                        throw new IllegalArgumentException("Prompt definition is malformed", exception);
                    }
                }
                collectPromptPreviews(field.getValue(), childPointer, previews);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                collectPromptPreviews(node.get(index), pointer + "/" + index, previews);
            }
        }
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static String promptLabel(String value) {
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static List<String> categoryPath(JsonNode value) {
        if (value.isTextual()) {
            return List.of(value.asText().split("\\."));
        }
        return textValues(value);
    }

    public record DefinitionDocumentRequest(JsonNode definition) {
    }

    public record DefinitionDraftUpdateRequest(JsonNode definition, Long optimisticVersion) {
    }

    public record OptimisticVersionRequest(Long optimisticVersion) {
    }

    public record DefinitionCloneRequest(String targetKey, Integer targetRevision) {
    }

    public record DefinitionSummaryView(String key, Long activeRevisionId, Integer activeRevision,
            long optimisticVersion, String displayName, String description, List<String> categoryPath,
            String languageCode, List<DefinitionRevisionSummaryView> revisions) {
    }

    public record DefinitionRevisionSummaryView(long id, int revision, DefinitionStatus status, String contentHash,
            DefinitionProvenance provenance, long optimisticVersion, Instant updatedAt) {
    }

    public record DefinitionRevisionView(long id, String key, int revision, int schemaVersion,
            DefinitionStatus status, String contentHash, DefinitionProvenance provenance, String sourceDetail,
            long optimisticVersion, Instant createdAt, Instant updatedAt, Instant publishedAt, Instant archivedAt,
            JsonNode definition) {
    }

    public record DefinitionValidationView(boolean valid, List<DefinitionDiagnosticView> diagnostics) {
    }

    public record PromptPreviewView(String pointer, String label, String composed) {
    }

    public record DefinitionDiagnosticView(String code, String severity, String pointer, String message, String hint) {
    }

    public record ComponentView(String kind, int version, ComponentCategory category, JsonNode configSchema,
            String label, String description, ComponentAuthoringRole authoringRole,
            ComponentAuthoringExposure exposure, String capabilityGroup, String advancedReason,
            JsonNode defaultConfig, List<JsonNode> examples, ComponentCapabilitiesView capabilities) {
    }

    public record ComponentCapabilitiesView(List<String> consumedObservations,
            List<String> emittedBehaviourModalities, List<ComponentStorageUseView> storage,
            List<ComponentReferenceView> resources, List<ComponentReferenceView> states) {
    }

    public record ComponentStorageUseView(String key, String access, JsonNode expectedValueSchema,
            String configPointer) {
    }

    public record ComponentReferenceView(String id, String configPointer, String expectedKind,
            Integer expectedVersion) {
    }
}
