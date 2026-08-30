package ch.zhaw.prometheus.definition.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledFinalState;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

/** Mutable per-instance data pinned to one immutable compiled revision. */
public final class AgentRuntimeInstance {
    private final long definitionRevisionId;
    private final CompiledAgentDefinition definition;
    private final Map<String, ImmutableJson> initialStorage;
    private final Map<String, ImmutableJson> storage;
    private final List<RuntimeEvent> history;
    private String activeLeafStateId;
    private boolean started;

    AgentRuntimeInstance(long definitionRevisionId, CompiledAgentDefinition definition,
            String activeLeafStateId, Map<String, ImmutableJson> initialStorage) {
        this(definitionRevisionId, definition, activeLeafStateId, initialStorage, initialStorage, List.of(), false);
    }

    AgentRuntimeInstance(long definitionRevisionId, CompiledAgentDefinition definition,
            String activeLeafStateId, Map<String, ImmutableJson> initialStorage,
            Map<String, ImmutableJson> storage, List<RuntimeEvent> history, boolean started) {
        if (definitionRevisionId < 1 || definition == null) {
            throw new IllegalArgumentException("revision ID must be positive and definition must not be null");
        }
        if (definition.state(activeLeafStateId) == null) {
            throw new IllegalArgumentException("active state is not present in the pinned definition: "
                    + activeLeafStateId);
        }
        this.definitionRevisionId = definitionRevisionId;
        this.definition = definition;
        this.activeLeafStateId = activeLeafStateId;
        this.initialStorage = new LinkedHashMap<>(initialStorage);
        this.storage = new LinkedHashMap<>(storage);
        this.history = new ArrayList<>(history);
        this.started = started;
    }

    public long definitionRevisionId() {
        return this.definitionRevisionId;
    }

    public CompiledAgentDefinition definition() {
        return this.definition;
    }

    public String activeLeafStateId() {
        return this.activeLeafStateId;
    }

    public List<String> activeStatePath() {
        return this.definition.pathTo(this.activeLeafStateId).stream().map(state -> state.id()).toList();
    }

    public boolean isActive() {
        return !(this.definition.state(this.activeLeafStateId) instanceof CompiledFinalState);
    }

    public boolean started() {
        return this.started;
    }

    public Map<String, JsonNode> storage() {
        Map<String, JsonNode> copy = new LinkedHashMap<>();
        this.storage.forEach((key, value) -> copy.put(key, value.value()));
        return Collections.unmodifiableMap(copy);
    }

    public List<RuntimeEvent> history() {
        return List.copyOf(this.history);
    }

    public AgentInstanceSnapshot snapshot() {
        return new AgentInstanceSnapshot(this.definitionRevisionId, this.definition.key(), this.definition.revision(),
                this.activeLeafStateId, activeStatePath(), this.storage, this.history, this.started, isActive());
    }

    Map<String, ImmutableJson> storageSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.storage));
    }

    Map<String, ImmutableJson> initialStorage() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.initialStorage));
    }

    public Map<String, ImmutableJson> initialStorageSnapshot() {
        return initialStorage();
    }

    RuntimeStorage mutableStorage() {
        return new RuntimeStorage() {
            @Override
            public JsonNode get(String key) {
                ImmutableJson value = storage.get(key);
                return value == null ? null : value.value();
            }

            @Override
            public void put(String key, JsonNode value) {
                if (!definition.storage().stream().anyMatch(declaration -> declaration.key().equals(key))) {
                    throw new IllegalArgumentException("storage key is not declared: " + key);
                }
                storage.put(key, new ImmutableJson(value));
            }

            @Override
            public void remove(String key) {
                storage.remove(key);
            }
        };
    }

    void setActiveLeafStateId(String stateId) {
        if (this.definition.state(stateId) == null) {
            throw new IllegalArgumentException("unknown active state: " + stateId);
        }
        this.activeLeafStateId = stateId;
    }

    void setStarted(boolean started) {
        this.started = started;
    }

    RuntimeEvent append(RuntimeEvent event) {
        RuntimeEvent recorded = event.atPath(activeStatePath());
        this.history.add(recorded);
        return recorded;
    }

    List<RuntimeEvent> clearHistory() {
        List<RuntimeEvent> removed = List.copyOf(this.history);
        this.history.clear();
        return removed;
    }

    List<RuntimeEvent> clearHistoryForState(String stateId) {
        List<RuntimeEvent> removed = this.history.stream()
                .filter(event -> event.statePath().contains(stateId)).toList();
        this.history.removeIf(event -> event.statePath().contains(stateId));
        return removed;
    }
}
