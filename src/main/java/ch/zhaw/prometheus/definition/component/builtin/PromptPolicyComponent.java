package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;
import java.util.Set;

import ch.zhaw.prometheus.definition.compiled.CompiledStorageBinding;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;

public record PromptPolicyComponent(
        String responsePrompt,
        String starterPrompt,
        String summaryPrompt,
        String nonverbalPlanPrompt,
        String gesturePrompt,
        List<CompiledStorageBinding> storageBindings,
        Set<String> consumedObservations,
        Set<String> emittedModalities) implements CompiledPolicy {

    public PromptPolicyComponent {
        storageBindings = storageBindings == null ? List.of() : List.copyOf(storageBindings);
        consumedObservations = consumedObservations == null ? Set.of() : Set.copyOf(consumedObservations);
        emittedModalities = emittedModalities == null ? Set.of() : Set.copyOf(emittedModalities);
    }
}
