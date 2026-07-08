package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.core.FacialExpressionSensitivity;
import ch.zhaw.prometheus.agentdefs.core.MultimodalBehaviour;
import ch.zhaw.prometheus.agentdefs.core.RockScissorPaper;
import ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame;
import ch.zhaw.prometheus.agentdefs.core.SocialContextSensitivity;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGame;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGameUserGuess;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateHealthcareConversation;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateSmartGoalCoaching;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateTherapyAppointmentReminder;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.TwoStateTherapyAppointmentReminder;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class SeedAgentInteractionProfileContractTest {
    private static final List<AgentDefinition> MAIN_AGENT_DEFINITIONS = List.of(
            new FacialExpressionSensitivity(),
            new MultimodalBehaviour(),
            new RockScissorPaper(),
            new RoleClarificationGuessingGame(),
            new SocialContextSensitivity(),
            new SingleStateGuessingGame(),
            new SingleStateGuessingGameUserGuess(),
            new SingleStateHealthcareConversation(),
            new SingleStateSmartGoalCoaching(),
            new SingleStateTherapyAppointmentReminder(),
            new TwoStateTherapyAppointmentReminder());

    private static final List<Path> PROFILE_WIRING_FILES = List.of(
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/core/ValerianCoreAgentFactory.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare/HealthcareAgentFactory.java"),
            Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare/TwoStateTherapyAppointmentReminder.java"));

    @Test
    void mainDefinitionsDeclareUserUtteranceSpeechAndProfileTags() {
        for (AgentDefinition definition : MAIN_AGENT_DEFINITIONS) {
            Agent agent = definition.createAgent();
            AgentInteractionProfile profile = agent.getInteractionProfile();

            assertNotNull(profile, definition.key());
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
            assertFalse(profile.getProfileTags().isEmpty(), definition.key());
        }
    }

    @Test
    void keyMultimodalCapabilitiesRemainDeclared() {
        AgentInteractionProfile socialContext = new SocialContextSensitivity().createAgent().getInteractionProfile();
        assertTrue(socialContext.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertTrue(socialContext.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));

        AgentInteractionProfile rps = new RockScissorPaper().createAgent().getInteractionProfile();
        assertTrue(rps.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertTrue(rps.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
        assertTrue(rps.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));

        AgentInteractionProfile healthcare = new SingleStateTherapyAppointmentReminder().createAgent()
                .getInteractionProfile();
        assertTrue(healthcare.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(healthcare.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
    }

    @Test
    void profileWiringSourcesDeclareInteractionProfiles() throws IOException {
        for (Path wiringFile : PROFILE_WIRING_FILES) {
            String source = Files.readString(wiringFile);
            assertTrue(source.contains("setInteractionProfile("),
                    "missing interaction profile declaration in " + wiringFile);
        }
    }
}
