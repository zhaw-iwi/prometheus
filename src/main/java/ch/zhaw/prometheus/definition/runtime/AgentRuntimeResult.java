package ch.zhaw.prometheus.definition.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentRuntimeResult(
        AgentInstanceSnapshot before,
        AgentInstanceSnapshot after,
        List<RuntimeEvent> appendedEvents,
        List<RuntimeEvent> removedEvents,
        Map<String, RuntimeStorageChange> storageChanges,
        List<String> acceptedTransitionIds,
        RuntimeBehaviour behaviour) {
    public AgentRuntimeResult {
        appendedEvents = List.copyOf(appendedEvents);
        removedEvents = List.copyOf(removedEvents);
        storageChanges = Collections.unmodifiableMap(new LinkedHashMap<>(storageChanges));
        acceptedTransitionIds = List.copyOf(acceptedTransitionIds);
    }
}
