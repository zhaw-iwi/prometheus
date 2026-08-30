package ch.zhaw.prometheus.definition.repository;

import java.time.Instant;

public record StoredDefinitionRevision(
        long id,
        long definitionId,
        String definitionKey,
        int revisionNumber,
        int schemaVersion,
        DefinitionStatus status,
        String canonicalJson,
        String contentHash,
        DefinitionProvenance provenance,
        String sourceDetail,
        long optimisticVersion,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant archivedAt) {

    public StoredDefinitionRevision {
        if (id < 1 || definitionId < 1 || definitionKey == null || definitionKey.isBlank()
                || revisionNumber < 1 || schemaVersion < 1 || status == null || canonicalJson == null
                || canonicalJson.isBlank() || contentHash == null || !contentHash.matches("[0-9a-f]{64}")
                || provenance == null || optimisticVersion < 0) {
            throw new IllegalArgumentException("Invalid stored definition revision");
        }
    }
}
