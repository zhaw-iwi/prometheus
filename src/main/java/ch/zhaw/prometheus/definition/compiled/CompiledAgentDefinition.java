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
        List<CompiledTransition> transitions) {
    public CompiledAgentDefinition {
        storage = List.copyOf(storage);
        resources = List.copyOf(resources);
        states = List.copyOf(states);
        statesById = Collections.unmodifiableMap(new LinkedHashMap<>(statesById));
        transitions = List.copyOf(transitions);
    }

    public CompiledState state(String id) {
        return statesById.get(id);
    }
}
