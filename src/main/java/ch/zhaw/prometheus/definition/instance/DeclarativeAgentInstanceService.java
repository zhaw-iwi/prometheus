package ch.zhaw.prometheus.definition.instance;

import java.util.List;
import java.util.Optional;
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
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

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

    @Transactional(readOnly = true)
    public Optional<LoadedDeclarativeAgent> find(UUID instanceId) {
        return this.repository.find(instanceId).map(this::load);
    }

    @Transactional(readOnly = true)
    public List<LoadedDeclarativeAgent> findAll() {
        return this.repository.findAll().stream().map(this::load).toList();
    }

    public DeclarativeAgentExecution start(UUID instanceId, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = require(instanceId);
        AgentRuntimeInstance runtime = restore(stored);
        AgentRuntimeResult result = this.engine.start(runtime, context);
        return new DeclarativeAgentExecution(update(stored, runtime), result);
    }

    public DeclarativeAgentExecution generate(UUID instanceId, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = require(instanceId);
        AgentRuntimeInstance runtime = restore(stored);
        AgentRuntimeResult result = this.engine.generate(runtime, context);
        return new DeclarativeAgentExecution(update(stored, runtime), result);
    }

    public DeclarativeAgentExecution acknowledge(UUID instanceId, RuntimeEvent event, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = require(instanceId);
        AgentRuntimeInstance runtime = restore(stored);
        AgentRuntimeResult result = this.engine.acknowledge(runtime, event, context);
        return new DeclarativeAgentExecution(update(stored, runtime), result);
    }

    public DeclarativeAgentReset reset(UUID instanceId, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = require(instanceId);
        AgentRuntimeInstance runtime = restore(stored);
        AgentRuntimeResult result = this.engine.reset(runtime, context);
        PersistedDeclarativeAgent updated = update(stored, runtime);
        return new DeclarativeAgentReset(updated, result);
    }

    public DeclarativeAgentExecution resetAndStart(UUID instanceId, AgentRuntimeContext context) {
        PersistedDeclarativeAgent stored = require(instanceId);
        AgentRuntimeInstance runtime = restore(stored);
        this.engine.reset(runtime, context);
        AgentRuntimeResult startup = this.engine.start(runtime, context);
        return new DeclarativeAgentExecution(update(stored, runtime), startup);
    }

    public boolean delete(UUID instanceId) {
        if (instanceId == null || this.repository.find(instanceId).isEmpty()) {
            return false;
        }
        this.repository.delete(instanceId);
        return true;
    }

    private PersistedDeclarativeAgent require(UUID instanceId) {
        return this.repository.find(instanceId)
                .orElseThrow(() -> new DeclarativeAgentNotFoundException("Declarative agent not found: " + instanceId));
    }

    private LoadedDeclarativeAgent load(PersistedDeclarativeAgent stored) {
        return new LoadedDeclarativeAgent(stored,
                this.cache.resolve(this.definitions.revisionSource(stored.definitionRevisionId())));
    }

    private AgentRuntimeInstance restore(PersistedDeclarativeAgent stored) {
        CompiledAgentDefinition compiled = this.cache.resolve(this.definitions.revisionSource(
                stored.definitionRevisionId()));
        return this.engine.restore(stored.definitionRevisionId(), compiled, stored.activeLeafStateId(),
                stored.initialStorage(), stored.storage(), stored.history(), stored.started());
    }

    private PersistedDeclarativeAgent update(PersistedDeclarativeAgent stored, AgentRuntimeInstance runtime) {
        return this.repository.update(snapshot(stored.id(), runtime, stored.optimisticVersion(), stored.createdAt(),
                stored.updatedAt()), stored.optimisticVersion());
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
