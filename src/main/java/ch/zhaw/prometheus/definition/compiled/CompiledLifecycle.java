package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

import ch.zhaw.prometheus.definition.component.CompiledInitializer;

public record CompiledLifecycle(
        CompiledState initialState,
        boolean startOnCreation,
        List<CompiledInitializer> initializers,
        String resetStorage,
        String resetHistory) {
    public CompiledLifecycle {
        initializers = List.copyOf(initializers);
    }
}
