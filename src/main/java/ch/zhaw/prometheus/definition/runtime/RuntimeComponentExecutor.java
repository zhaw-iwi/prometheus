package ch.zhaw.prometheus.definition.runtime;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledDecision;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;

public interface RuntimeComponentExecutor {
    RuntimeBehaviour start(List<CompiledPolicy> policies, RuntimeInvocation invocation);

    RuntimeBehaviour generate(List<CompiledPolicy> policies, RuntimeInvocation invocation);

    boolean decide(CompiledDecision decision, RuntimeInvocation invocation);

    RuntimeBehaviour execute(CompiledAction action, RuntimeInvocation invocation, RuntimeStorage storage);

    boolean selects(CompiledSelector selector, RuntimeEvent event, String evaluatingStateId);
}
