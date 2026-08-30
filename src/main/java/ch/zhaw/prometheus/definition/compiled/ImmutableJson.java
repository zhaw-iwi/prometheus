package ch.zhaw.prometheus.definition.compiled;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

public final class ImmutableJson {
    private final JsonNode value;

    public ImmutableJson(JsonNode value) {
        this.value = Objects.requireNonNull(value, "value").deepCopy();
    }

    public JsonNode value() {
        return this.value.deepCopy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImmutableJson that && this.value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
