package ch.zhaw.prometheus.controllers;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewSnapshot;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewSource;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.ScenarioExecution;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

@RestController
@RequestMapping("/admin/agent-definitions/previews")
public class DesignerPreviewController {
    private static final String ADMIN_TOKEN_HEADER = AdminAccessCodeController.ADMIN_TOKEN_HEADER;

    private final DesignerPreviewService previews;
    private final DefinitionLifecycleService lifecycle;
    private final String adminToken;

    public DesignerPreviewController(DesignerPreviewService previews, DefinitionLifecycleService lifecycle,
            @Value("${prometheus.admin.token:}") String adminToken) {
        this.previews = previews;
        this.lifecycle = lifecycle;
        this.adminToken = adminToken;
    }

    @PostMapping
    public ResponseEntity<PreviewSnapshot> create(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) PreviewCreateRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        require(request != null, "A preview source is required");
        boolean unsaved = request.definition() != null;
        boolean saved = request.key() != null && !request.key().isBlank() && request.revision() != null
                && request.revision() > 0;
        require(unsaved != saved, "Provide either one unsaved definition or one saved key/revision");
        PreviewSnapshot created;
        if (unsaved) {
            require(request.key() == null && request.revision() == null,
                    "An unsaved preview cannot also select a saved revision");
            created = this.previews.create(request.definition().toString(), PreviewSource.UNSAVED, null);
        } else {
            require(request.definition() == null, "A saved preview cannot include unsaved JSON");
            StoredDefinitionRevision revision = this.lifecycle.requireRevision(request.key(), request.revision());
            if (revision.status() != DefinitionStatus.DRAFT) {
                throw new DefinitionLifecycleException("Only draft revisions can be previewed by saved identity");
            }
            created = this.previews.create(revision.canonicalJson(), PreviewSource.SAVED, revision.id());
        }
        return ResponseEntity.created(URI.create("/admin/agent-definitions/previews/" + created.id())).body(created);
    }

    @GetMapping("/{previewId}")
    public ResponseEntity<PreviewSnapshot> inspect(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID previewId) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(this.previews.inspect(previewId));
    }

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioExecution> scenario(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) ScenarioExecutionRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        require(request != null && request.definition() != null && request.scenarioIndex() >= 0,
                "An unsaved definition and scenario index are required");
        return ResponseEntity.ok(this.previews.executeScenario(request.definition().toString(),
                request.scenarioIndex()));
    }

    @PostMapping("/{previewId}/events")
    public ResponseEntity<PreviewSnapshot> event(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID previewId, @RequestBody(required = false) PreviewEventRequest request) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        require(request != null && request.type() != null && !request.type().isBlank(),
                "A preview event type is required");
        return ResponseEntity.ok(this.previews.acknowledge(previewId,
                new RuntimeEvent(request.type(), request.actor(), request.kind(), request.payload())));
    }

    @PostMapping("/{previewId}/generate")
    public ResponseEntity<PreviewSnapshot> generate(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID previewId) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(this.previews.generate(previewId));
    }

    @PostMapping("/{previewId}/reset")
    public ResponseEntity<PreviewSnapshot> reset(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID previewId) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        return ResponseEntity.ok(this.previews.reset(previewId));
    }

    @DeleteMapping("/{previewId}")
    public ResponseEntity<Void> close(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID previewId) {
        if (!isAuthorized(token)) {
            return unauthorized();
        }
        this.previews.close(previewId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private boolean isAuthorized(String token) {
        return this.adminToken != null && !this.adminToken.isBlank() && token != null && this.adminToken.equals(token);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T> ResponseEntity<T> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    public record PreviewCreateRequest(JsonNode definition, String key, Integer revision) {
    }

    public record PreviewEventRequest(String type, String actor, String kind, String payload) {
    }

    public record ScenarioExecutionRequest(JsonNode definition, int scenarioIndex) {
    }
}
