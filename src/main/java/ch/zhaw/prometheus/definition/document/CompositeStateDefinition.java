package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record CompositeStateDefinition(
        String id,
        String name,
        String entryMode,
        boolean oblivious,
        ComponentEnvelope eventSelector,
        ComponentEnvelope policy,
        List<String> childStateIds,
        String initialChildStateId) implements StateDefinition {

    public CompositeStateDefinition {
        childStateIds = DocumentCollections.copyList(childStateIds);
    }
}
