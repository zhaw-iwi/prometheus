package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record AgentLifecycle(
        String initialStateId,
        boolean startOnCreation,
        List<ComponentEnvelope> initializers,
        ResetDefinition reset) {

    public AgentLifecycle {
        initializers = DocumentCollections.copyList(initializers);
    }
}
