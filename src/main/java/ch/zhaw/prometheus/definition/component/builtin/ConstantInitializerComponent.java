package ch.zhaw.prometheus.definition.component.builtin;

import java.util.random.RandomGenerator;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledInitializer;

public record ConstantInitializerComponent(String targetStorageKey, ImmutableJson value)
        implements CompiledInitializer {

    @Override
    public JsonNode initialize(RandomGenerator random) {
        return this.value.value();
    }
}
