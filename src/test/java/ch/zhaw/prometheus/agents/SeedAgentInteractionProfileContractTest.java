package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class SeedAgentInteractionProfileContractTest {
    private static final List<Path> SEED_AGENT_FILES = List.of(
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/basic/SingleStateGuessingGame.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/basic/SingleStateMicroCoaching.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/basic/SingleStateCoCreation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/basic/FourStatesCircular.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/basic/FourStatesLinear.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare/SingleStateTherapyAppointmentReminder.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare/SingleStateGuessingGame.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare/SingleStateGuessingGameUserGuess.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare/SingleStateSmartGoalCoaching.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de/GuessingGameWithGestures.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de/TourConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de/TourConversationSocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr/GuessingGameWithGestures.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr/TourConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr/TourConversationSocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it/GuessingGameWithGestures.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it/TourConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it/TourConversationSocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en/GuessingGameWithGestures.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en/TourConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en/TourConversationSocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon/GuessingGameWithGestures.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon/TourConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon/TourConversationSocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/de/EPFLActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/de/Furka.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/de/InterviewingPeople.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/de/SUPSIActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/de/UnisStudent.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/en/EPFLActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/en/Furka.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/en/InterviewingPeople.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/en/SUPSIActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/en/UnisStudent.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/it/EPFLActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/it/Furka.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/it/InterviewingPeople.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/it/SUPSIActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/it/UnisStudent.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/fr/EPFLActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/fr/Furka.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/fr/InterviewingPeople.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/fr/SUPSIActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/fr/UnisStudent.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/babylon/EPFLActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/babylon/Furka.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/babylon/InterviewingPeople.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/babylon/SUPSIActive.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/shhd/babylon/UnisStudent.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/SingleStateTherapyAppointmentReminder.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/TwoStateTherapyAppointmentReminder.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/SingleStateGuessingGame.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/SingleStateGuessingGameUserGuess.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/SingleStateSmartGoalCoaching.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/davos/SingleStateSummitHotelConversation.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/lab/SocialContextSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/lab/FacialExpressionSensitivity.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/lab/RockScissorPaper.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/lab/RoleClarificationGuessingGame.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/lab/MultimodalBehaviour.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/migros/AppenzellGeneral.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/migros/AppenzellScene2MenuPlanner.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/migros/AppenzellScene3CheckoutReflection.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/multimodal/SingleStateMultimodalIn.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/multimodal/SingleStateMultimodalOut.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/multimodal/SingleStateMultimodalInOut.java"));

    @Test
    void reusableSpeechOnlyFixturesDeclareUserUtteranceAndSpeech() {
        for (Agent agent : List.of(
                AgentFixtures.singleStateGuessingGame(),
                AgentFixtures.singleStateMicroCoaching(),
                AgentFixtures.singleStateCoCreation(),
                AgentFixtures.fourStatesCircular(),
                AgentFixtures.fourStatesLinear())) {
            AgentInteractionProfile profile = agent.getInteractionProfile();
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
            assertTrue(profile.getSupportedObservations().size() == 1);
            assertTrue(profile.getSupportedBehaviourModalities().size() == 1);
        }
    }

    @Test
    void reusableTdsrFixturesExposeSpecializedCapabilities() {
        AgentInteractionProfile gestureProfile = AgentFixtures.gigiTdsrGuessingGameWithGestures()
                .getInteractionProfile();
        assertTdsrWeatherSupport(gestureProfile);
        assertTrue(gestureProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(gestureProfile.supportsBehaviourModality(
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertFalse(gestureProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));

        AgentInteractionProfile socialProfile = AgentFixtures.gigiTdsrSocialContextSensitivity()
                .getInteractionProfile();
        assertTdsrWeatherSupport(socialProfile);
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));

        AgentInteractionProfile rpsProfile = AgentFixtures.gigiTdsrRockScissorPaper().getInteractionProfile();
        assertTdsrWeatherSupport(rpsProfile);
        assertTrue(rpsProfile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertTrue(rpsProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
        assertTrue(rpsProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));

        AgentInteractionProfile tourProfile = AgentFixtures.gigiTdsrTourConversation().getInteractionProfile();
        assertTdsrWeatherSupport(tourProfile);
        assertTrue(tourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(tourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(tourProfile.supportsBehaviourModality(
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertFalse(tourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        assertTrue(tourProfile.getSupportedObservations().size() == 3);

        AgentInteractionProfile socialTourProfile = AgentFixtures.gigiTdsrTourConversationSocialContextSensitivity()
                .getInteractionProfile();
        assertTdsrWeatherSupport(socialTourProfile);
        assertTrue(socialTourProfile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(socialTourProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(socialTourProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(socialTourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(socialTourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertFalse(socialTourProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
    }

    @Test
    void seedAgentSourcesDeclareInteractionProfiles() throws IOException {
        for (Path seedAgentFile : SEED_AGENT_FILES) {
            String source = Files.readString(seedAgentFile);
            assertTrue(source.contains("setInteractionProfile(") || source.contains("TdsrCoreAgentFactory.")
                    || source.contains("TdsrShhdAgentFactory.") || source.contains("DavosCareAgentFactory.")
                    || source.contains("TdsrLabAgentFactory.") || source.contains("TdsrMigrosAgentFactory."),
                    "missing interaction profile declaration in " + seedAgentFile);
        }
    }

    @Test
    void shhdAgentSourcesUseSharedSocialTourFactory() throws IOException {
        for (Path seedAgentFile : SEED_AGENT_FILES) {
            if (!seedAgentFile.toString().replace('\\', '/').contains("/tdsr/shhd/")) {
                continue;
            }
            String source = Files.readString(seedAgentFile);
            assertTrue(source.contains("TdsrShhdAgentFactory.ShhdPrompts"),
                    "missing interaction profile declaration in " + seedAgentFile);
        }
    }

    private static void assertTdsrWeatherSupport(AgentInteractionProfile profile) {
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
    }
}
