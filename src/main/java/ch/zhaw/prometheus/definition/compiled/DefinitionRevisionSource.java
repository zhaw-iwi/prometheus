package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;

public record DefinitionRevisionSource(long revisionId, String contentHash, AgentDefinitionDocument definition) {
    public DefinitionRevisionSource {
        if (revisionId < 1) {
            throw new IllegalArgumentException("revisionId must be positive");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hash");
        }
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
    }
}
