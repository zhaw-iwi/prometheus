package ch.zhaw.prometheus.definition.component;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.validation.ComponentSemantics;

public interface AgentComponentDefinition {
    ComponentKey key();

    ComponentCategory category();

    JsonNode configSchema();

    ComponentUiMetadata uiMetadata();

    ComponentSemantics semantics(JsonNode config);

    CompiledComponent compile(JsonNode config);

    default CompiledComponent compile(JsonNode config, ComponentRegistry registry) {
        return compile(config);
    }
}
