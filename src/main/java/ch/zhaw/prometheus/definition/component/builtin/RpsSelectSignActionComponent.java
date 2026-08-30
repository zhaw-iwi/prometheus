package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledAction;

public record RpsSelectSignActionComponent(
        String roundsStorageKey,
        String currentAgentSignStorageKey,
        String currentRoundNumberStorageKey) implements CompiledAction {
}
