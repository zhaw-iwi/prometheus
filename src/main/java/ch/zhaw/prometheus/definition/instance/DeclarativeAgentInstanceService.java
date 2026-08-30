package ch.zhaw.prometheus.definition.instance;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.definition.application.DefinitionLifecycleService;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.repository.StoredDefinitionRevision;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeInstance;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeResult;

@Service
@Transactional
public class DeclarativeAgentInstanceService {
    private final DeclarativeAgentRepository repository;
    private final DefinitionLifecycleService definitions;
    private final CompiledDefinitionCache cache;
    private final AgentRuntimeEngine engine;

    public DeclarativeAgentInstanceService(DeclarativeAgentRepository repository,
            DefinitionLifecycleService definitions, CompiledDefinitionCache cache, AgentRuntimeEngine engine) {
        this.repository = repository;
        this.definitions = definitions;
        this.cache = cache;
        this.engine = engine;
    }

    public DeclarativeAgentCreation create(String definitionKey, AgentRuntimeContext context) {
        StoredDefinitionRevision revision = this.definitions.requireActiveRevision(definitionKey);
        CompiledAgentDefinition compiled = this.cache.resolve(this.definitions.revisionSource(revision.id()));
        var runtimeCreation = this.engine.create(revision.id(), compiled, context);
        PersistedDeclarativeAgent stored = this.repository.create(snapshot(UUID.randomUUID(), runtimeCreation.instance(),
                0, null, null));
        return new DeclarativeAgentCreation(stored, runtimeCreation.startup());
    }

    public DeclarativeAgentReset reset(UUID instanceId, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = this.repository.find(instanceId)
                .orElseThrow(() -> new DeclarativeAgentNotFoundException("Declarative agent not found: " + instanceId));
        CompiledAgentDefinition compiled = this.cache.resolve(this.definitions.revisionSource(
                stored.definitionRevisionId()));
        AgentRuntimeInstance runtime = this.engine.restore(stored.definitionRevisionId(), compiled,
                stored.activeLeafStateId(), stored.initialStorage(), stored.storage(), stored.history(), stored.started());
        AgentRuntimeResult result = this.engine.reset(runtime, context);
        PersistedDeclarativeAgent updated = this.repository.update(snapshot(stored.id(), runtime,
                stored.optimisticVersion(), stored.createdAt(), stored.updatedAt()), stored.optimisticVersion());
        return new DeclarativeAgentReset(updated, result);
    }

    private static PersistedDeclarativeAgent snapshot(UUID id, AgentRuntimeInstance runtime, long version,
            java.time.Instant createdAt, java.time.Instant updatedAt) {
        var snapshot = runtime.snapshot();
        return new PersistedDeclarativeAgent(id, snapshot.definitionRevisionId(), snapshot.activeLeafStateId(),
                snapshot.storage(), runtime.initialStorageSnapshot(), snapshot.history(), snapshot.started(),
                snapshot.active() ? RuntimeInstanceStatus.ACTIVE : RuntimeInstanceStatus.FINAL,
                version, createdAt, updatedAt);
    }
}
