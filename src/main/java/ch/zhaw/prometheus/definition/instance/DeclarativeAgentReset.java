package ch.zhaw.prometheus.definition.instance;

import ch.zhaw.prometheus.definition.runtime.AgentRuntimeResult;

public record DeclarativeAgentReset(PersistedDeclarativeAgent instance, AgentRuntimeResult result) {
}
