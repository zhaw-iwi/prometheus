package ch.zhaw.prometheus.agentdefs.tdsr;

import java.util.List;

import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

public final class TdsrInteractionProfiles {
    private static final String TAG_GIGI_TDSR = "demo.gigi.tdsr";
    private static final String TAG_GIGI_GUESSING_GAME = "demo.gigi.guessing_game";
    private static final String TAG_GIGI_SOCIAL_CONTEXT = "demo.gigi.social_context";
    private static final String TAG_GIGI_RPS = "demo.gigi.rps";
    private static final String TAG_GIGI_TOUR_CONVERSATION = "demo.gigi.tour_conversation";

    private TdsrInteractionProfiles() {
    }

    public static AgentInteractionProfile gigiTdsrGuessingGameWithGestures() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                speechAndGestureModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_GUESSING_GAME));
    }

    public static AgentInteractionProfile gigiTdsrSocialContextSensitivity() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                List.of(AgentInteractionProfile.MODALITY_SPEECH),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_SOCIAL_CONTEXT));
    }

    public static AgentInteractionProfile gigiTdsrRockScissorPaper() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HAND_SIGN,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                        AgentInteractionProfile.MODALITY_DISPLAY),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_RPS));
    }

    public static AgentInteractionProfile gigiTdsrTourConversation() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                speechAndGestureModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_TOUR_CONVERSATION));
    }

    public static AgentInteractionProfile gigiTdsrTourConversationSocialContextSensitivity() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                speechAndGestureModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_TOUR_CONVERSATION,
                        TAG_GIGI_SOCIAL_CONTEXT));
    }

    private static List<String> speechAndGestureModalities() {
        return List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                AgentInteractionProfile.MODALITY_NONVERBAL_GAZE);
    }
}
