package ch.zhaw.prometheus.definition.instance;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;

public record LoadedDeclarativeAgent(PersistedDeclarativeAgent instance, CompiledAgentDefinition definition) {
}
