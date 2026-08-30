package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

public record CompiledAgentMetadata(
        String displayName,
        String description,
        String categoryPath,
        String languageCode,
        List<String> tags) {
    public CompiledAgentMetadata {
        tags = List.copyOf(tags);
    }
}
