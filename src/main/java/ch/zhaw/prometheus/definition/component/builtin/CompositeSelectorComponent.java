package ch.zhaw.prometheus.definition.component.builtin;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledSelector;

public record CompositeSelectorComponent(Mode mode, List<CompiledSelector> selectors) implements CompiledSelector {
    public CompositeSelectorComponent {
        selectors = List.copyOf(selectors);
    }

    public enum Mode {
        ALL,
        ANY
    }
}
