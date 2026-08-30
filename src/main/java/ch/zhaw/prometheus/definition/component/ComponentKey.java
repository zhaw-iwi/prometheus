package ch.zhaw.prometheus.definition.component;

public record ComponentKey(String kind, int version) {
    public ComponentKey {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("component kind must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("component version must be positive");
        }
    }
}
