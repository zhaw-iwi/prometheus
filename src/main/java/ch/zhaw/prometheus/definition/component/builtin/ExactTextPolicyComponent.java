package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledPolicy;

public record ExactTextPolicyComponent(
        String eventType,
        String actor,
        String eventKind,
        int maxTextCodePoints) implements CompiledPolicy {
}
