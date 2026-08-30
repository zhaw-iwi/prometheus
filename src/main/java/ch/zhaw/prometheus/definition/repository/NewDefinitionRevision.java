package ch.zhaw.prometheus.definition.repository;

public record NewDefinitionRevision(
        long definitionId,
        int revisionNumber,
        int schemaVersion,
        DefinitionStatus status,
        String canonicalJson,
        String contentHash,
        DefinitionProvenance provenance,
        String sourceDetail) {

    public NewDefinitionRevision {
        if (definitionId < 1 || revisionNumber < 1 || schemaVersion < 1 || status == null
                || canonicalJson == null || canonicalJson.isBlank() || contentHash == null
                || !contentHash.matches("[0-9a-f]{64}") || provenance == null) {
            throw new IllegalArgumentException("Invalid new definition revision");
        }
    }
}
