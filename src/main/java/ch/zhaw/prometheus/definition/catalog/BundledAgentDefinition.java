package ch.zhaw.prometheus.definition.catalog;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;

/** A schema-valid, semantically valid definition shipped with PROMETHEUS. */
public record BundledAgentDefinition(
        String resource,
        AgentDefinitionDocument document,
        CompiledAgentDefinition compiled) {
}
