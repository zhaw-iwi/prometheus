package ch.zhaw.prometheus.definition.document;

import java.util.List;

public record AgentInteraction(
        List<String> supportedObservations,
        List<String> supportedBehaviourModalities,
        List<String> profileTags) {

    public AgentInteraction {
        supportedObservations = DocumentCollections.copyOrderedSet(supportedObservations);
        supportedBehaviourModalities = DocumentCollections.copyOrderedSet(supportedBehaviourModalities);
        profileTags = DocumentCollections.copyOrderedSet(profileTags);
    }
}
