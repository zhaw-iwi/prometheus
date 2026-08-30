package ch.zhaw.prometheus.definition.validation;

import ch.zhaw.prometheus.definition.document.ComponentEnvelope;

@FunctionalInterface
public interface ComponentSemanticsResolver {
    ComponentSemantics resolve(ComponentEnvelope component);

    static ComponentSemanticsResolver none() {
        return component -> ComponentSemantics.none();
    }
}
