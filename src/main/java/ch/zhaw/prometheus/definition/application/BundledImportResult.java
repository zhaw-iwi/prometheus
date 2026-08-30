package ch.zhaw.prometheus.definition.application;

public record BundledImportResult(int createdDefinitions, int createdRevisions, int activatedDefinitions,
        int unchangedRevisions) {
}
