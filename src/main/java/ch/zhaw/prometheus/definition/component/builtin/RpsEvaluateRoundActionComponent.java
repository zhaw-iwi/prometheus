package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledAction;

public record RpsEvaluateRoundActionComponent(
        String handSignEventType,
        String currentAgentSignStorageKey,
        String currentRoundNumberStorageKey,
        String lastRoundStorageKey,
        String roundsStorageKey) implements CompiledAction {
}
