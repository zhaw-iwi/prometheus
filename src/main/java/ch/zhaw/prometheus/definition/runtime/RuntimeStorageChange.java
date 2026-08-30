package ch.zhaw.prometheus.definition.runtime;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record RuntimeStorageChange(ImmutableJson before, ImmutableJson after) {
}
