package ch.zhaw.prometheus.agents;

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
    void seedAgentSourcesDeclareInteractionProfiles() throws IOException {
        for (Path seedAgentFile : SEED_AGENT_FILES) {
            String source = Files.readString(seedAgentFile);
            assertTrue(source.contains("setInteractionProfile("),
                    "missing interaction profile declaration in " + seedAgentFile);
        }
    }
}
