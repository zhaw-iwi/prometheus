package ch.zhaw.prometheus.definition.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import ch.zhaw.prometheus.definition.repository.NewDefinitionRevision;
import ch.zhaw.prometheus.definition.repository.StoredDefinition;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;

@Repository
@Transactional
public class JpaDefinitionRepository implements DefinitionRepository {
    private static final JsonMapper JSON = new JsonMapper();

    private final AgentDefinitionIdentityJpaRepository definitions;
    private final AgentDefinitionRevisionJpaRepository revisions;

    public JpaDefinitionRepository(AgentDefinitionIdentityJpaRepository definitions,
            AgentDefinitionRevisionJpaRepository revisions) {
        this.definitions = definitions;
        this.revisions = revisions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDefinition> findDefinition(String key) {
        return this.definitions.findByDefinitionKey(key).map(JpaDefinitionRepository::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDefinition> findDefinition(long id) {
        return this.definitions.findById(id).map(JpaDefinitionRepository::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDefinition> findDefinitions() {
        return this.definitions.findAllByOrderByDefinitionKey().stream().map(JpaDefinitionRepository::map).toList();
    }

    @Override
    public StoredDefinition createDefinition(String key) {
        try {
            return map(this.definitions.saveAndFlush(new AgentDefinitionIdentityEntity(key)));
        } catch (DataIntegrityViolationException exception) {
            throw new DefinitionLifecycleException("Definition key already exists: " + key, exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDefinitionRevision> findRevision(String key, int revisionNumber) {
        return this.revisions.findByDefinition_DefinitionKeyAndRevisionNumber(key, revisionNumber)
                .map(JpaDefinitionRepository::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDefinitionRevision> findRevision(long revisionId) {
        return this.revisions.findById(revisionId).map(JpaDefinitionRepository::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDefinitionRevision> findRevisions(String key) {
        return this.revisions.findByDefinition_DefinitionKeyOrderByRevisionNumber(key).stream()
                .map(JpaDefinitionRepository::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDefinitionRevision> findActiveRevisions() {
        return this.revisions.findActiveRevisions().stream().map(JpaDefinitionRepository::map).toList();
    }

    @Override
    public StoredDefinitionRevision createRevision(NewDefinitionRevision revision) {
        AgentDefinitionIdentityEntity definition = this.definitions.findById(revision.definitionId())
                .orElseThrow(() -> new DefinitionLifecycleException(
                        "Definition identity does not exist: " + revision.definitionId()));
        try {
            AgentDefinitionRevisionEntity entity = new AgentDefinitionRevisionEntity(definition,
                    revision.revisionNumber(), revision.schemaVersion(), revision.status(),
                    parseJson(revision.canonicalJson()), revision.contentHash(), revision.provenance(),
                    revision.sourceDetail());
            return map(this.revisions.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new DefinitionLifecycleException("Definition revision already exists: "
                    + definition.getDefinitionKey() + " revision " + revision.revisionNumber(), exception);
        }
    }

    @Override
    public StoredDefinitionRevision replaceDraft(long revisionId, long expectedOptimisticVersion,
            String canonicalJson, String contentHash) {
        AgentDefinitionRevisionEntity revision = requireRevision(revisionId);
        if (revision.getStatus() != DefinitionStatus.DRAFT) {
            throw new DefinitionLifecycleException("Published or archived revision content is immutable: "
                    + revisionId);
        }
        requireVersion(revisionId, expectedOptimisticVersion, revision.getOptimisticVersion());
        revision.replaceDraft(parseJson(canonicalJson), contentHash);
        return saveRevision(revision);
    }

    @Override
    public StoredDefinitionRevision changeStatus(long revisionId, long expectedOptimisticVersion,
            DefinitionStatus status) {
        AgentDefinitionRevisionEntity revision = requireRevision(revisionId);
        requireVersion(revisionId, expectedOptimisticVersion, revision.getOptimisticVersion());
        revision.changeStatus(status);
        return saveRevision(revision);
    }

    @Override
    public StoredDefinition activate(long definitionId, long revisionId, long expectedOptimisticVersion) {
        AgentDefinitionIdentityEntity definition = this.definitions.findById(definitionId)
                .orElseThrow(() -> new DefinitionLifecycleException("Definition identity does not exist: "
                        + definitionId));
        requireVersion(definitionId, expectedOptimisticVersion, definition.getOptimisticVersion());
        AgentDefinitionRevisionEntity revision = requireRevision(revisionId);
        if (!definition.getId().equals(revision.getDefinition().getId())) {
            throw new DefinitionLifecycleException("Revision does not belong to definition identity");
        }
        definition.activate(revision);
        try {
            return map(this.definitions.saveAndFlush(definition));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw optimistic(definitionId, exception);
        }
    }

    private StoredDefinitionRevision saveRevision(AgentDefinitionRevisionEntity revision) {
        try {
            return map(this.revisions.saveAndFlush(revision));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw optimistic(revision.getId(), exception);
        }
    }

    private AgentDefinitionRevisionEntity requireRevision(long revisionId) {
        return this.revisions.findById(revisionId)
                .orElseThrow(() -> new DefinitionLifecycleException("Definition revision does not exist: "
                        + revisionId));
    }

    private static void requireVersion(long id, long expected, long actual) {
        if (expected != actual) {
            throw new DefinitionOptimisticLockException("Optimistic version mismatch for " + id
                    + ": expected " + expected + " but was " + actual);
        }
    }

    private static DefinitionOptimisticLockException optimistic(long id, RuntimeException cause) {
        return new DefinitionOptimisticLockException("Concurrent update of definition record " + id, cause);
    }

    private static JsonNode parseJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new DefinitionLifecycleException("Canonical definition JSON is invalid", exception);
        }
    }

    private static StoredDefinition map(AgentDefinitionIdentityEntity entity) {
        Long activeRevisionId = entity.getActiveRevision() == null ? null : entity.getActiveRevision().getId();
        return new StoredDefinition(entity.getId(), entity.getDefinitionKey(), activeRevisionId,
                entity.getOptimisticVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static StoredDefinitionRevision map(AgentDefinitionRevisionEntity entity) {
        return new StoredDefinitionRevision(entity.getId(), entity.getDefinition().getId(),
                entity.getDefinition().getDefinitionKey(), entity.getRevisionNumber(), entity.getSchemaVersion(),
                entity.getStatus(), entity.getSpecificationJson().toString(), entity.getContentHash(),
                entity.getProvenance(), entity.getSourceDetail(), entity.getOptimisticVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getPublishedAt(), entity.getArchivedAt());
    }
}
