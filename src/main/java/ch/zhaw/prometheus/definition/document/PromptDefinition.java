package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record PromptDefinition(List<PromptSection> sections) {
    public PromptDefinition {
        sections = DocumentCollections.copyList(sections);
    }
}
