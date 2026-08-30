package ch.zhaw.prometheus.definition.component;

import java.util.random.RandomGenerator;

import com.fasterxml.jackson.databind.JsonNode;

public interface CompiledInitializer extends CompiledComponent {
    String targetStorageKey();

    JsonNode initialize(RandomGenerator random);
}
