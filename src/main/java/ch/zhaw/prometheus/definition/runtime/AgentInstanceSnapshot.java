package ch.zhaw.prometheus.definition.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record AgentInstanceSnapshot(
        long definitionRevisionId,
        String definitionKey,
        int definitionRevision,
        String activeLeafStateId,
        List<String> activeStatePath,
        Map<String, ImmutableJson> storage,
        List<RuntimeEvent> history,
        boolean started,
        boolean active) {
    public AgentInstanceSnapshot {
        activeStatePath = List.copyOf(activeStatePath);
        storage = Collections.unmodifiableMap(new LinkedHashMap<>(storage));
        history = List.copyOf(history);
    }
}
