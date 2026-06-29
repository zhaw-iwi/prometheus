package ch.zhaw.prometheus.model.interaction;

import java.util.List;

public final class AgentInteractionProfiles {
    private AgentInteractionProfiles() {
    }

    public static AgentInteractionProfile speechOnly() {
        return AgentInteractionProfile.of(
                userUtteranceObservations(),
                List.of(AgentInteractionProfile.MODALITY_SPEECH),
                List.of());
    }

    public static AgentInteractionProfile speechWithNonverbal() {
        return AgentInteractionProfile.of(
                userUtteranceObservations(),
                speechAndNonverbalModalities(),
                List.of());
    }

    public static AgentInteractionProfile multimodalInput() {
        return AgentInteractionProfile.of(
                visualInputObservations(),
                List.of(AgentInteractionProfile.MODALITY_SPEECH),
                List.of());
    }

    public static AgentInteractionProfile multimodalOutput() {
        return speechWithNonverbal();
    }

    public static AgentInteractionProfile multimodalInputOutput() {
        return AgentInteractionProfile.of(
                visualInputObservations(),
                speechAndNonverbalModalities(),
                List.of());
    }

    private static List<String> userUtteranceObservations() {
        return List.of(AgentInteractionProfile.OBS_USER_UTTERANCE);
    }

    private static List<String> visualInputObservations() {
        return List.of(
                AgentInteractionProfile.OBS_USER_UTTERANCE,
                AgentInteractionProfile.OBS_FACE_EMOTION,
                AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                AgentInteractionProfile.OBS_SOCIAL_GROUPING);
    }

    private static List<String> speechAndNonverbalModalities() {
        return List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                AgentInteractionProfile.MODALITY_NONVERBAL_MOTION);
    }

}
