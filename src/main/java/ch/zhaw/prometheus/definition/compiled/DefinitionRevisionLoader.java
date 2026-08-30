package ch.zhaw.prometheus.definition.compiled;

@FunctionalInterface
public interface DefinitionRevisionLoader {
    DefinitionRevisionSource load(long revisionId);
}
