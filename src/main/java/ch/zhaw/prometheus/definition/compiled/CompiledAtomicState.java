package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;

public record CompiledAtomicState(
        String id,
        String name,
        String entryMode,
        boolean oblivious,
        CompiledSelector eventSelector,
        CompiledPolicy policy) implements CompiledState {
}
