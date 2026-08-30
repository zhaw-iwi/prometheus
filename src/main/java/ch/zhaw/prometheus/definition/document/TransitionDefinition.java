package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record TransitionDefinition(
        String id,
        String sourceStateId,
        String targetStateId,
        int order,
        List<ComponentEnvelope> decisions,
        List<ComponentEnvelope> actions) {

    public TransitionDefinition {
        decisions = DocumentCollections.copyList(decisions);
        actions = DocumentCollections.copyList(actions);
    }
}
