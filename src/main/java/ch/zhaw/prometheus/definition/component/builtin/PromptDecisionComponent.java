package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;
import java.util.Set;

import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.component.CompiledDecision;

public record PromptDecisionComponent(
        String decisionPrompt,
        List<CompiledStorageBinding> storageBindings,
        Set<String> consumedObservations) implements CompiledDecision {

    public PromptDecisionComponent {
        storageBindings = storageBindings == null ? List.of() : List.copyOf(storageBindings);
        consumedObservations = consumedObservations == null ? Set.of() : Set.copyOf(consumedObservations);
    }
}
