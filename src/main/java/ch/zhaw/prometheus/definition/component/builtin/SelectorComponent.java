package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledSelector;

public record SelectorComponent(SelectorKind selectorKind, List<String> values) implements CompiledSelector {
    public SelectorComponent {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public enum SelectorKind {
        ANY,
        ACTIVE_STATE_PATH,
        EVENT_TYPE,
        ACTOR,
        EVENT_KIND,
        STATE_ID
    }
}
