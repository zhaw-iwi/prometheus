package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

public record CompiledStorageDefinition(
        String key,
        String description,
        ImmutableJson valueSchema,
        boolean required,
        String visibility,
        String reset,
        ImmutableJson initialValue,
        List<ImmutableJson> examples) {
    public CompiledStorageDefinition {
        examples = List.copyOf(examples);
    }
}
