package ch.zhaw.prometheus.definition.instance;

import ch.zhaw.prometheus.definition.runtime.AgentRuntimeResult;

public record DeclarativeAgentCreation(PersistedDeclarativeAgent instance, AgentRuntimeResult startup) {
}
