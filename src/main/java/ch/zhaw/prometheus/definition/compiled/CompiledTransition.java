package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledDecision;

public record CompiledTransition(
        String id,
        CompiledState sourceState,
        CompiledState targetState,
        int order,
        List<CompiledDecision> decisions,
        List<CompiledAction> actions) {
    public CompiledTransition {
        decisions = List.copyOf(decisions);
        actions = List.copyOf(actions);
    }
}
