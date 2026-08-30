package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledAction;

public record IncrementActionComponent(String targetStorageKey) implements CompiledAction {
}
