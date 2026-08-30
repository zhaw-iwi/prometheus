package ch.zhaw.prometheus.definition.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.compiled.DefinitionRevisionSource;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionNotFoundException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.NewDefinitionRevision;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;

@Service
@Transactional
public class DefinitionLifecycleService {
    private final DefinitionRepository repository;
    private final AgentDefinitionJson definitionJson;
    private final DefinitionCompiler compiler;
    private final CompiledDefinitionCache cache;
    private final DefinitionRevisionSources sources;

    public DefinitionLifecycleService(DefinitionRepository repository, AgentDefinitionJson definitionJson,
            DefinitionCompiler compiler, CompiledDefinitionCache cache, DefinitionRevisionSources sources) {
        this.repository = repository;
        this.definitionJson = definitionJson;
        this.compiler = compiler;
        this.cache = cache;
        this.sources = sources;
    }

    public StoredDefinitionRevision createDraft(String json, DefinitionProvenance provenance, String sourceDetail) {
        if (provenance == null || provenance == DefinitionProvenance.BUNDLED) {
            throw new IllegalArgumentException("Draft provenance must be DESIGNER or IMPORTED");
        }
        AgentDefinitionDocument document = this.definitionJson.parse(json);
        StoredDefinition definition = this.repository.findDefinition(document.key())
                .orElseGet(() -> this.repository.createDefinition(document.key()));
        List<StoredDefinitionRevision> existing = this.repository.findRevisions(document.key());
        if (existing.stream().anyMatch(revision -> revision.revisionNumber() == document.revision())) {
            throw new DefinitionLifecycleException("Definition revision already exists: " + document.key()
                    + " revision " + document.revision());
        }
        int latest = existing.stream().mapToInt(StoredDefinitionRevision::revisionNumber).max().orElse(0);
        if (document.revision() <= latest) {
            throw new DefinitionLifecycleException("New draft revision must be greater than " + latest);
        }
        return this.repository.createRevision(newRevision(definition.id(), document, DefinitionStatus.DRAFT,
                provenance, sourceDetail));
    }

    public StoredDefinitionRevision updateDraft(String key, int revisionNumber, String json,
            long expectedOptimisticVersion) {
        StoredDefinitionRevision stored = requireRevision(key, revisionNumber);
        requireStatus(stored, DefinitionStatus.DRAFT, "Only draft revisions can be updated");
        AgentDefinitionDocument document = this.definitionJson.parse(json);
        requireDocumentIdentity(stored, document);
        String canonical = this.definitionJson.canonicalJson(document);
        String hash = this.definitionJson.contentHash(document);
        return this.repository.replaceDraft(stored.id(), expectedOptimisticVersion, canonical, hash);
    }

    public StoredDefinitionRevision publish(String key, int revisionNumber, long expectedOptimisticVersion) {
        StoredDefinitionRevision stored = requireRevision(key, revisionNumber);
        requireStatus(stored, DefinitionStatus.DRAFT, "Only draft revisions can be published");
        if (stored.optimisticVersion() != expectedOptimisticVersion) {
            throw new DefinitionOptimisticLockException("Optimistic version mismatch for revision " + stored.id());
        }
        DefinitionRevisionSource source = this.sources.source(stored);
        CompiledAgentDefinition compiled = this.compiler.compile(source.definition());
        StoredDefinitionRevision published = this.repository.changeStatus(stored.id(), expectedOptimisticVersion,
                DefinitionStatus.PUBLISHED);
        this.cache.install(this.sources.source(published), compiled);
        return published;
    }

    public StoredDefinition activate(String key, int revisionNumber, long expectedOptimisticVersion) {
        StoredDefinition definition = requireDefinition(key);
        StoredDefinitionRevision revision = requireRevision(key, revisionNumber);
        requireStatus(revision, DefinitionStatus.PUBLISHED, "Only published revisions can be activated");
        this.cache.resolve(this.sources.source(revision));
        return this.repository.activate(definition.id(), revision.id(), expectedOptimisticVersion);
    }

    public StoredDefinitionRevision archive(String key, int revisionNumber, long expectedOptimisticVersion) {
        StoredDefinition definition = requireDefinition(key);
        StoredDefinitionRevision revision = requireRevision(key, revisionNumber);
        requireStatus(revision, DefinitionStatus.PUBLISHED, "Only published revisions can be archived");
        if (Long.valueOf(revision.id()).equals(definition.activeRevisionId())) {
            throw new DefinitionLifecycleException("The active revision cannot be archived");
        }
        return this.repository.changeStatus(revision.id(), expectedOptimisticVersion, DefinitionStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public StoredDefinition requireDefinition(String key) {
        return this.repository.findDefinition(key)
                .orElseThrow(() -> new DefinitionNotFoundException("Definition not found: " + key));
    }

    @Transactional(readOnly = true)
    public StoredDefinitionRevision requireRevision(String key, int revisionNumber) {
        return this.repository.findRevision(key, revisionNumber)
                .orElseThrow(() -> new DefinitionNotFoundException("Definition revision not found: " + key
                        + " revision " + revisionNumber));
    }

    @Transactional(readOnly = true)
    public StoredDefinitionRevision requireRevision(long revisionId) {
        return this.repository.findRevision(revisionId)
                .orElseThrow(() -> new DefinitionNotFoundException("Definition revision not found: " + revisionId));
    }

    @Transactional(readOnly = true)
    public StoredDefinitionRevision requireActiveRevision(String key) {
        StoredDefinition definition = requireDefinition(key);
        if (definition.activeRevisionId() == null) {
            throw new DefinitionLifecycleException("Definition has no active revision: " + key);
        }
        StoredDefinitionRevision revision = requireRevision(definition.activeRevisionId());
        requireStatus(revision, DefinitionStatus.PUBLISHED, "Active revision is not published");
        return revision;
    }

    public List<CompiledAgentDefinition> prewarmActive() {
        List<DefinitionRevisionSource> active = this.sources.active();
        for (DefinitionRevisionSource source : active) {
            StoredDefinitionRevision revision = requireRevision(source.revisionId());
            requireStatus(revision, DefinitionStatus.PUBLISHED, "Only published revisions can be prewarmed");
        }
        return this.cache.prewarm(active);
    }

    public DefinitionRevisionSource revisionSource(long revisionId) {
        return this.sources.source(requireRevision(revisionId));
    }

    private NewDefinitionRevision newRevision(long definitionId, AgentDefinitionDocument document,
            DefinitionStatus status, DefinitionProvenance provenance, String sourceDetail) {
        return new NewDefinitionRevision(definitionId, document.revision(), document.schemaVersion(), status,
                this.definitionJson.canonicalJson(document), this.definitionJson.contentHash(document), provenance,
                sourceDetail);
    }

    private static void requireDocumentIdentity(StoredDefinitionRevision stored, AgentDefinitionDocument document) {
        if (!stored.definitionKey().equals(document.key()) || stored.revisionNumber() != document.revision()
                || stored.schemaVersion() != document.schemaVersion()) {
            throw new DefinitionLifecycleException("Specification identity does not match repository revision");
        }
    }

    private static void requireStatus(StoredDefinitionRevision revision, DefinitionStatus expected, String message) {
        if (revision.status() != expected) {
            throw new DefinitionLifecycleException(message + ": " + revision.status());
        }
    }
}
