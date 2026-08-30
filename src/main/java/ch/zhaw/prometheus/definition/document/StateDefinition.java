package ch.zhaw.prometheus.definition.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AtomicStateDefinition.class, name = "atomic"),
        @JsonSubTypes.Type(value = CompositeStateDefinition.class, name = "composite"),
        @JsonSubTypes.Type(value = FinalStateDefinition.class, name = "final")
})
public sealed interface StateDefinition permits AtomicStateDefinition, CompositeStateDefinition,
        FinalStateDefinition {
    String id();

    String name();
}
