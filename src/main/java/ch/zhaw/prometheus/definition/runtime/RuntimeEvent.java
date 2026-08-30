package ch.zhaw.prometheus.definition.runtime;

import java.util.List;

public record RuntimeEvent(String type, String actor, String kind, String payload, List<String> statePath) {
    public RuntimeEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("event type must not be blank");
        }
        statePath = statePath == null ? List.of() : List.copyOf(statePath);
    }

    public RuntimeEvent(String type, String actor, String kind, String payload) {
        this(type, actor, kind, payload, List.of());
    }

    public RuntimeEvent atPath(List<String> path) {
        return new RuntimeEvent(this.type, this.actor, this.kind, this.payload, path);
    }
}
