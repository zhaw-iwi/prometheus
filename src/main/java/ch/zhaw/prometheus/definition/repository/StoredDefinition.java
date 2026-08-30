package ch.zhaw.prometheus.definition.repository;

import java.time.Instant;

public record StoredDefinition(
        long id,
        String key,
        Long activeRevisionId,
        long optimisticVersion,
        Instant createdAt,
        Instant updatedAt) {

    public StoredDefinition {
        if (id < 1 || key == null || key.isBlank() || optimisticVersion < 0) {
            throw new IllegalArgumentException("Invalid stored definition identity");
        }
    }
}
