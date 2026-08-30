package ch.zhaw.prometheus.definition.compiled;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompiledAgentDefinition(
        int schemaVersion,
        String key,
        int revision,
        String contentHash,
        CompiledAgentMetadata metadata,
        CompiledInteraction interaction,
        CompiledLifecycle lifecycle,
        List<CompiledStorageDefinition> storage,
        List<CompiledResourceDefinition> resources,
        List<CompiledState> states,
        Map<String, CompiledState> statesById,
        List<CompiledTransition> transitions,
        Map<String, List<CompiledState>> statePathsById,
        Map<String, List<CompiledTransition>> transitionsBySourceStateId) {
    public CompiledAgentDefinition {
        storage = List.copyOf(storage);
        resources = List.copyOf(resources);
        states = List.copyOf(states);
        statesById = Collections.unmodifiableMap(new LinkedHashMap<>(statesById));
        transitions = List.copyOf(transitions);
        statePathsById = immutableListMap(statePathsById);
        transitionsBySourceStateId = immutableListMap(transitionsBySourceStateId);
    }

    public CompiledState state(String id) {
        return statesById.get(id);
    }

    public List<CompiledState> pathTo(String stateId) {
        return statePathsById.getOrDefault(stateId, List.of());
    }

    public List<CompiledTransition> transitionsFrom(String stateId) {
        return transitionsBySourceStateId.getOrDefault(stateId, List.of());
    }

    private static <K, V> Map<K, List<V>> immutableListMap(Map<K, List<V>> source) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> copy.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
