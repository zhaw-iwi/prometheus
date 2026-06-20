package ch.zhaw.prometheus.model.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AgentInteractionProfileUnitTest {

    @Test
    void profileNormalizesAndDeduplicatesDeclaredCapabilities() {
        AgentInteractionProfile profile = AgentInteractionProfile.of(
                List.of(" " + AgentInteractionProfile.OBS_HAND_SIGN + " ", AgentInteractionProfile.OBS_HAND_SIGN,
                        AgentInteractionProfile.OBS_USER_UTTERANCE, ""),
                List.of(AgentInteractionProfile.MODALITY_SPEECH, AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN),
                List.of(AgentInteractionProfile.TAG_GIGI_TDSR, " "));

        assertEquals(List.of(AgentInteractionProfile.OBS_HAND_SIGN, AgentInteractionProfile.OBS_USER_UTTERANCE),
                profile.getSupportedObservations());
        assertEquals(List.of(AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN), profile.getSupportedBehaviourModalities());
        assertEquals(List.of(AgentInteractionProfile.TAG_GIGI_TDSR), profile.getProfileTags());
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
        assertFalse(profile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
    }

    @Test
    void profileRoundTripsThroughJson() {
        AgentInteractionProfile profile = AgentInteractionProfiles.gigiTdsrRockScissorPaper();

        AgentInteractionProfile loaded = AgentInteractionProfile.fromJson(profile.toJson());

        assertEquals(profile.getSupportedObservations(), loaded.getSupportedObservations());
        assertEquals(profile.getSupportedBehaviourModalities(), loaded.getSupportedBehaviourModalities());
        assertEquals(profile.getProfileTags(), loaded.getProfileTags());
    }

    @Test
    void commonSpeechOnlyProfileDeclaresOnlyUserUtteranceAndSpeech() {
        AgentInteractionProfile profile = AgentInteractionProfiles.speechOnly();

        assertEquals(List.of(AgentInteractionProfile.OBS_USER_UTTERANCE), profile.getSupportedObservations());
        assertEquals(List.of(AgentInteractionProfile.MODALITY_SPEECH), profile.getSupportedBehaviourModalities());
        assertTrue(profile.getProfileTags().isEmpty());
    }

    @Test
    void commonMultimodalInputOutputProfileDeclaresVisualInputAndNonverbalOutput() {
        AgentInteractionProfile profile = AgentInteractionProfiles.multimodalInputOutput();

        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
    }

    @Test
    void gigiTdsrTourConversationProfileDeclaresSpeechWeatherAndNonverbalWithoutVisualInput() {
        AgentInteractionProfile profile = AgentInteractionProfiles.gigiTdsrTourConversation();

        assertEquals(List.of(
                AgentInteractionProfile.OBS_USER_UTTERANCE,
                AgentInteractionProfile.OBS_WEATHER_CURRENT,
                AgentInteractionProfile.OBS_WEATHER_FORECAST), profile.getSupportedObservations());
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        assertTrue(profile.getProfileTags().contains(AgentInteractionProfile.TAG_GIGI_TOUR_CONVERSATION));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
        assertFalse(profile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertFalse(profile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
    }
}
