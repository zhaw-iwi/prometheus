package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;

public record CompiledCompositeState(
        String id,
        String name,
        String entryMode,
        boolean oblivious,
        CompiledSelector eventSelector,
        CompiledPolicy policy,
        List<CompiledState> childStates,
        CompiledState initialChildState) implements CompiledState {
    public CompiledCompositeState {
        childStates = List.copyOf(childStates);
    }
}
