package ch.zhaw.prometheus.definition.instance;

import java.util.Optional;
import java.util.UUID;

public interface DeclarativeAgentRepository {
    Optional<PersistedDeclarativeAgent> find(UUID id);

    PersistedDeclarativeAgent create(PersistedDeclarativeAgent instance);

    PersistedDeclarativeAgent update(PersistedDeclarativeAgent instance, long expectedOptimisticVersion);

    boolean existsByDefinitionRevisionId(long revisionId);
}
