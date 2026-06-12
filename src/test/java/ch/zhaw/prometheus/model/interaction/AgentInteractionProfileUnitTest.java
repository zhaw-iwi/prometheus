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
}
