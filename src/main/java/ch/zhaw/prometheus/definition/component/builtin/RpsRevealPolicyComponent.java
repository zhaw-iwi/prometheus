package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledPolicy;

public record RpsRevealPolicyComponent(
        String currentAgentSignStorageKey,
        String currentRoundNumberStorageKey) implements CompiledPolicy {
}
