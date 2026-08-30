package ch.zhaw.prometheus.definition.compiled;

import java.util.List;

public record CompiledInteraction(
        List<String> supportedObservations,
        List<String> supportedBehaviourModalities,
        List<String> profileTags) {
    public CompiledInteraction {
        supportedObservations = List.copyOf(supportedObservations);
        supportedBehaviourModalities = List.copyOf(supportedBehaviourModalities);
        profileTags = List.copyOf(profileTags);
    }
}
