package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.component.CompiledInitializer;

public record RandomChoiceInitializerComponent(String targetStorageKey, List<ImmutableJson> choices)
        implements CompiledInitializer {

    public RandomChoiceInitializerComponent {
        choices = List.copyOf(choices);
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("random-choice initializer requires choices");
        }
    }

    @Override
    public JsonNode initialize(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        return this.choices.get(random.nextInt(this.choices.size())).value();
    }
}
