package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.component.CompiledResource;
import ch.zhaw.prometheus.definition.component.ComponentKey;

public record CompiledResourceDefinition(String id, ComponentKey componentKey, CompiledResource component) {
}
