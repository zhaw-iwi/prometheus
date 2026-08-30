package ch.zhaw.prometheus.definition.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.NewDefinitionRevision;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;

public final class InMemoryDefinitionRepository implements DefinitionRepository {
    private final Map<Long, StoredDefinition> definitions = new LinkedHashMap<>();
    private final Map<Long, StoredDefinitionRevision> revisions = new LinkedHashMap<>();
    private long nextDefinitionId = 1;
    private long nextRevisionId = 1;

    @Override
    public synchronized Optional<StoredDefinition> findDefinition(String key) {
        return this.definitions.values().stream().filter(definition -> definition.key().equals(key)).findFirst();
    }

    @Override
    public synchronized Optional<StoredDefinition> findDefinition(long id) {
        return Optional.ofNullable(this.definitions.get(id));
    }

    @Override
    public synchronized StoredDefinition createDefinition(String key) {
        if (findDefinition(key).isPresent()) {
            throw new DefinitionLifecycleException("Definition key already exists: " + key);
        }
        Instant now = Instant.now();
        StoredDefinition definition = new StoredDefinition(this.nextDefinitionId++, key, null, 0, now, now);
        this.definitions.put(definition.id(), definition);
        return definition;
    }

    @Override
    public synchronized Optional<StoredDefinitionRevision> findRevision(String key, int revisionNumber) {
        return this.revisions.values().stream()
                .filter(revision -> revision.definitionKey().equals(key)
                        && revision.revisionNumber() == revisionNumber)
                .findFirst();
    }

    @Override
    public synchronized Optional<StoredDefinitionRevision> findRevision(long revisionId) {
        return Optional.ofNullable(this.revisions.get(revisionId));
    }

    @Override
    public synchronized List<StoredDefinitionRevision> findRevisions(String key) {
        return this.revisions.values().stream().filter(revision -> revision.definitionKey().equals(key))
                .sorted(Comparator.comparingInt(StoredDefinitionRevision::revisionNumber)).toList();
    }

    @Override
    public synchronized List<StoredDefinitionRevision> findActiveRevisions() {
        List<StoredDefinitionRevision> active = new ArrayList<>();
        this.definitions.values().stream().sorted(Comparator.comparing(StoredDefinition::key)).forEach(definition -> {
            if (definition.activeRevisionId() != null) {
                active.add(this.revisions.get(definition.activeRevisionId()));
            }
        });
        return List.copyOf(active);
    }

    @Override
    public synchronized StoredDefinitionRevision createRevision(NewDefinitionRevision revision) {
        StoredDefinition definition = this.definitions.get(revision.definitionId());
        if (definition == null) {
            throw new DefinitionLifecycleException("Definition identity does not exist: " + revision.definitionId());
        }
        if (findRevision(definition.key(), revision.revisionNumber()).isPresent()) {
            throw new DefinitionLifecycleException("Definition revision already exists");
        }
        Instant now = Instant.now();
        StoredDefinitionRevision stored = new StoredDefinitionRevision(this.nextRevisionId++, definition.id(),
                definition.key(), revision.revisionNumber(), revision.schemaVersion(), revision.status(),
                revision.canonicalJson(), revision.contentHash(), revision.provenance(), revision.sourceDetail(), 0,
                now, now, revision.status() == DefinitionStatus.PUBLISHED ? now : null, null);
        this.revisions.put(stored.id(), stored);
        return stored;
    }

    @Override
    public synchronized StoredDefinitionRevision replaceDraft(long revisionId, long expectedOptimisticVersion,
            String canonicalJson, String contentHash) {
        StoredDefinitionRevision current = requireRevision(revisionId);
        if (current.status() != DefinitionStatus.DRAFT) {
            throw new DefinitionLifecycleException("Published or archived revision content is immutable: "
                    + revisionId);
        }
        requireVersion(revisionId, expectedOptimisticVersion, current.optimisticVersion());
        StoredDefinitionRevision replacement = copy(current, current.status(), canonicalJson, contentHash,
                current.optimisticVersion() + 1, current.publishedAt(), current.archivedAt());
        this.revisions.put(revisionId, replacement);
        return replacement;
    }

    @Override
    public synchronized StoredDefinitionRevision changeStatus(long revisionId, long expectedOptimisticVersion,
            DefinitionStatus status) {
        StoredDefinitionRevision current = requireRevision(revisionId);
        requireVersion(revisionId, expectedOptimisticVersion, current.optimisticVersion());
        Instant now = Instant.now();
        StoredDefinitionRevision replacement = copy(current, status, current.canonicalJson(), current.contentHash(),
                current.optimisticVersion() + 1,
                status == DefinitionStatus.PUBLISHED ? now : current.publishedAt(),
                status == DefinitionStatus.ARCHIVED ? now : null);
        this.revisions.put(revisionId, replacement);
        return replacement;
    }

    @Override
    public synchronized StoredDefinition activate(long definitionId, long revisionId,
            long expectedOptimisticVersion) {
        StoredDefinition definition = this.definitions.get(definitionId);
        if (definition == null) {
            throw new DefinitionLifecycleException("Definition identity does not exist: " + definitionId);
        }
        requireVersion(definitionId, expectedOptimisticVersion, definition.optimisticVersion());
        StoredDefinitionRevision revision = requireRevision(revisionId);
        if (revision.definitionId() != definitionId) {
            throw new DefinitionLifecycleException("Revision does not belong to definition identity");
        }
        StoredDefinition replacement = new StoredDefinition(definition.id(), definition.key(), revisionId,
                definition.optimisticVersion() + 1, definition.createdAt(), Instant.now());
        this.definitions.put(definitionId, replacement);
        return replacement;
    }

    public synchronized int definitionCount() {
        return this.definitions.size();
    }

    public synchronized int revisionCount() {
        return this.revisions.size();
    }

    private StoredDefinitionRevision requireRevision(long revisionId) {
        StoredDefinitionRevision revision = this.revisions.get(revisionId);
        if (revision == null) {
            throw new DefinitionLifecycleException("Definition revision does not exist: " + revisionId);
        }
        return revision;
    }

    private static void requireVersion(long id, long expected, long actual) {
        if (expected != actual) {
            throw new DefinitionOptimisticLockException("Optimistic version mismatch for " + id);
        }
    }

    private static StoredDefinitionRevision copy(StoredDefinitionRevision current, DefinitionStatus status,
            String canonicalJson, String contentHash, long version, Instant publishedAt, Instant archivedAt) {
        return new StoredDefinitionRevision(current.id(), current.definitionId(), current.definitionKey(),
                current.revisionNumber(), current.schemaVersion(), status, canonicalJson, contentHash,
                current.provenance(), current.sourceDetail(), version, current.createdAt(), Instant.now(),
                publishedAt, archivedAt);
    }
}
