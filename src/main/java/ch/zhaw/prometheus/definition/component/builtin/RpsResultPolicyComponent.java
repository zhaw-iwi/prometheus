package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledPolicy;

public record RpsResultPolicyComponent(String lastRoundStorageKey) implements CompiledPolicy {
}
