package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;

@FunctionalInterface
public interface CompiledDefinitionFactory {
    CompiledAgentDefinition compile(AgentDefinitionDocument definition);
}
