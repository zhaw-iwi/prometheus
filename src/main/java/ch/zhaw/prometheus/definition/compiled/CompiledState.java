package ch.zhaw.prometheus.definition.compiled;

public sealed interface CompiledState permits CompiledAtomicState, CompiledCompositeState, CompiledFinalState {
    String id();

    String name();
}
