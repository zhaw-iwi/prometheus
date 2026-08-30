package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledAction;

public record PromptBehaviourActionComponent(PromptPolicyComponent policy) implements CompiledAction {
}
