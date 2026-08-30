package ch.zhaw.prometheus.definition.component.builtin;

import java.util.random.RandomGenerator;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.component.CompiledInitializer;

/** Compiler intermediate resolved to RandomChoiceInitializerComponent before runtime. */
public record ResourceChoiceInitializerComponent(String targetStorageKey, String resourceId)
        implements CompiledInitializer {
    @Override
    public JsonNode initialize(RandomGenerator random) {
        throw new IllegalStateException("resource choice initializer was not resolved during compilation");
    }
}
