package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.validation.ComponentStorageAccess;

public record CompiledStorageBinding(
        String key,
        ComponentStorageAccess access,
        ImmutableJson expectedValueSchema) {
}
