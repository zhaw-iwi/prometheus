package ch.zhaw.prometheus.definition.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;

public record RuntimeInvocation(
        String stateId,
        List<String> activeStatePath,
        List<RuntimeEvent> history,
        Map<String, ImmutableJson> storage) {
    public RuntimeInvocation {
        activeStatePath = List.copyOf(activeStatePath);
        history = List.copyOf(history);
        storage = Collections.unmodifiableMap(new LinkedHashMap<>(storage));
    }
}
