package ch.zhaw.prometheus.definition.instance;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

public record PersistedDeclarativeAgent(
        UUID id,
        long definitionRevisionId,
        String activeLeafStateId,
        Map<String, ImmutableJson> storage,
        Map<String, ImmutableJson> initialStorage,
        List<RuntimeEvent> history,
        boolean started,
        RuntimeInstanceStatus status,
        long optimisticVersion,
        Instant createdAt,
        Instant updatedAt) {

    public PersistedDeclarativeAgent {
        if (id == null || definitionRevisionId < 1 || activeLeafStateId == null || activeLeafStateId.isBlank()
                || storage == null || initialStorage == null || history == null || status == null
                || optimisticVersion < 0) {
            throw new IllegalArgumentException("Invalid persisted declarative agent");
        }
        storage = Collections.unmodifiableMap(new LinkedHashMap<>(storage));
        initialStorage = Collections.unmodifiableMap(new LinkedHashMap<>(initialStorage));
        history = List.copyOf(history);
    }

    public PersistedDeclarativeAgent withRuntime(String activeLeafStateId, Map<String, ImmutableJson> storage,
            List<RuntimeEvent> history, boolean started, RuntimeInstanceStatus status) {
        return new PersistedDeclarativeAgent(this.id, this.definitionRevisionId, activeLeafStateId, storage,
                this.initialStorage, history, started, status, this.optimisticVersion, this.createdAt, this.updatedAt);
    }
}
