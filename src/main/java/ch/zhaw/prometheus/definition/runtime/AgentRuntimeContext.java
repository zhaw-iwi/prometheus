package ch.zhaw.prometheus.definition.runtime;

import java.util.random.RandomGenerator;

public record AgentRuntimeContext(RuntimeComponentExecutor components, RandomGenerator random) {
    public AgentRuntimeContext {
        if (components == null || random == null) {
            throw new IllegalArgumentException("components and random must not be null");
        }
    }
}
