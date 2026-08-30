package ch.zhaw.prometheus.definition.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import ch.zhaw.prometheus.definition.instance.DeclarativeAgentOptimisticLockException;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentRepository;
import ch.zhaw.prometheus.definition.instance.PersistedDeclarativeAgent;

public final class InMemoryDeclarativeAgentRepository implements DeclarativeAgentRepository {
    private final Map<UUID, PersistedDeclarativeAgent> instances = new LinkedHashMap<>();

    @Override
    public synchronized Optional<PersistedDeclarativeAgent> find(UUID id) {
        return Optional.ofNullable(this.instances.get(id));
    }

    @Override
    public synchronized List<PersistedDeclarativeAgent> findAll() {
        return List.copyOf(this.instances.values());
    }

    @Override
    public synchronized PersistedDeclarativeAgent create(PersistedDeclarativeAgent instance) {
        if (this.instances.containsKey(instance.id())) {
            throw new IllegalStateException("Declarative agent already exists: " + instance.id());
        }
        Instant now = Instant.now();
        PersistedDeclarativeAgent persisted = copy(instance, 0, now, now);
        this.instances.put(persisted.id(), persisted);
        return persisted;
    }

    @Override
    public synchronized PersistedDeclarativeAgent update(PersistedDeclarativeAgent instance,
            long expectedOptimisticVersion) {
        PersistedDeclarativeAgent current = this.instances.get(instance.id());
        if (current == null) {
            throw new IllegalArgumentException("Declarative agent does not exist: " + instance.id());
        }
        if (current.optimisticVersion() != expectedOptimisticVersion) {
            throw new DeclarativeAgentOptimisticLockException("Optimistic version mismatch for " + instance.id());
        }
        if (current.definitionRevisionId() != instance.definitionRevisionId()) {
            throw new IllegalArgumentException("A declarative agent cannot change its pinned revision");
        }
        PersistedDeclarativeAgent persisted = copy(instance, current.optimisticVersion() + 1,
                current.createdAt(), Instant.now());
        this.instances.put(persisted.id(), persisted);
        return persisted;
    }

    @Override
    public synchronized boolean existsByDefinitionRevisionId(long revisionId) {
        return this.instances.values().stream()
                .anyMatch(instance -> instance.definitionRevisionId() == revisionId);
    }

    @Override
    public synchronized void delete(UUID id) {
        this.instances.remove(id);
    }

    private static PersistedDeclarativeAgent copy(PersistedDeclarativeAgent instance, long version,
            Instant createdAt, Instant updatedAt) {
        return new PersistedDeclarativeAgent(instance.id(), instance.definitionRevisionId(),
                instance.activeLeafStateId(), instance.storage(), instance.initialStorage(), instance.history(),
                instance.started(), instance.status(), version, createdAt, updatedAt);
    }
}
