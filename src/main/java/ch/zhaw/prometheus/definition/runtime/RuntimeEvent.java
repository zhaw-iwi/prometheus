package ch.zhaw.prometheus.definition.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuntimeEvent(UUID id, Instant createdAt, String type, String actor, String kind, String payload,
        List<String> statePath) {
    public RuntimeEvent {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("event type must not be blank");
        }
        statePath = statePath == null ? List.of() : List.copyOf(statePath);
    }

    public RuntimeEvent(String type, String actor, String kind, String payload) {
        this(null, null, type, actor, kind, payload, List.of());
    }

    public RuntimeEvent(String type, String actor, String kind, String payload, List<String> statePath) {
        this(null, null, type, actor, kind, payload, statePath);
    }

    public RuntimeEvent atPath(List<String> path) {
        return new RuntimeEvent(this.id, this.createdAt, this.type, this.actor, this.kind, this.payload, path);
    }

    public RuntimeEvent withPayload(String payload) {
        return new RuntimeEvent(this.id, this.createdAt, this.type, this.actor, this.kind, payload, this.statePath);
    }
}
