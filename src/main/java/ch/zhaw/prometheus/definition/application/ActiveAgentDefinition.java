package ch.zhaw.prometheus.definition.application;

import java.util.List;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;

public record ActiveAgentDefinition(long revisionId, CompiledAgentDefinition compiled, List<String> packagePath) {
    public ActiveAgentDefinition {
        packagePath = List.copyOf(packagePath);
    }
}
