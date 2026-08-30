package ch.zhaw.prometheus.definition.component;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record ComponentUiMetadata(
        String label,
        String description,
        ImmutableJson defaultConfig,
        List<ImmutableJson> examples) {

    public ComponentUiMetadata {
        examples = examples == null ? List.of() : List.copyOf(examples);
    }
}
