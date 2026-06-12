package ch.zhaw.prometheus.model.interaction;

import java.util.List;

public final class AgentInteractionProfiles {
    private AgentInteractionProfiles() {
    }

    public static AgentInteractionProfile gigiTdsrGuessingGameWithGestures() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GAZE),
                List.of(
                        AgentInteractionProfile.TAG_GIGI_TDSR,
                        AgentInteractionProfile.TAG_GIGI_GUESSING_GAME));
    }

    public static AgentInteractionProfile gigiTdsrSocialContextSensitivity() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH),
                List.of(
                        AgentInteractionProfile.TAG_GIGI_TDSR,
                        AgentInteractionProfile.TAG_GIGI_SOCIAL_CONTEXT));
    }

    public static AgentInteractionProfile gigiTdsrRockScissorPaper() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HAND_SIGN),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                        AgentInteractionProfile.MODALITY_DISPLAY),
                List.of(
                        AgentInteractionProfile.TAG_GIGI_TDSR,
                        AgentInteractionProfile.TAG_GIGI_RPS));
    }
}
