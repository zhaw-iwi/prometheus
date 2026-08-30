package ch.zhaw.prometheus.definition.repository;

import java.util.List;
import java.util.Optional;

public interface DefinitionRepository {
    Optional<StoredDefinition> findDefinition(String key);

    Optional<StoredDefinition> findDefinition(long id);

    StoredDefinition createDefinition(String key);

    Optional<StoredDefinitionRevision> findRevision(String key, int revisionNumber);

    Optional<StoredDefinitionRevision> findRevision(long revisionId);

    List<StoredDefinitionRevision> findRevisions(String key);

    List<StoredDefinitionRevision> findActiveRevisions();

    StoredDefinitionRevision createRevision(NewDefinitionRevision revision);

    StoredDefinitionRevision replaceDraft(long revisionId, long expectedOptimisticVersion,
            String canonicalJson, String contentHash);

    StoredDefinitionRevision changeStatus(long revisionId, long expectedOptimisticVersion,
            DefinitionStatus status);

    StoredDefinition activate(long definitionId, long revisionId, long expectedOptimisticVersion);
}
