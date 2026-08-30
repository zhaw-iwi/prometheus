package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record AgentMetadata(
        String displayName,
        String description,
        String categoryPath,
        String languageCode,
        List<String> tags) {

    public AgentMetadata {
        tags = DocumentCollections.copyOrderedSet(tags);
    }
}
