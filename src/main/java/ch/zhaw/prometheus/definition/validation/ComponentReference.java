package ch.zhaw.prometheus.definition.validation;

import ch.zhaw.prometheus.definition.component.ComponentKey;

public record ComponentReference(String id, String configPointer, ComponentKey expectedComponent) {
    public ComponentReference(String id, String configPointer) {
        this(id, configPointer, null);
    }
}
