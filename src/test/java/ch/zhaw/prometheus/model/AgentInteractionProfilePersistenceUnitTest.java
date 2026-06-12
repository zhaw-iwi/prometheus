package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest
class AgentInteractionProfilePersistenceUnitTest {

    @Autowired
    private AgentRepository repository;

    @Test
    void interactionProfilePersistsAcrossSaveAndReload() {
        State start = new State("start", new NoOpPolicy(), List.of());
        Agent agent = new Agent("profiled", "interaction profile persistence", start);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrRockScissorPaper());

        Agent saved = this.repository.save(agent);
        Agent loaded = this.repository.findById(saved.getId()).orElseThrow();

        AgentInteractionProfile profile = loaded.getInteractionProfile();
        assertEquals(List.of(AgentInteractionProfile.OBS_USER_UTTERANCE, AgentInteractionProfile.OBS_HAND_SIGN),
                profile.getSupportedObservations());
        assertEquals(List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                AgentInteractionProfile.MODALITY_DISPLAY), profile.getSupportedBehaviourModalities());
        assertTrue(profile.getProfileTags().contains(AgentInteractionProfile.TAG_GIGI_RPS));
    }

    @Test
    void agentsWithoutExplicitProfileExposeEmptyProfile() {
        State start = new State("start-empty-profile", new NoOpPolicy(), List.of());
        Agent saved = this.repository.save(new Agent("unprofiled", "default profile", start));

        Agent loaded = this.repository.findById(saved.getId()).orElseThrow();

        assertTrue(loaded.getInteractionProfile().getSupportedObservations().isEmpty());
        assertTrue(loaded.getInteractionProfile().getSupportedBehaviourModalities().isEmpty());
        assertTrue(loaded.getInteractionProfile().getProfileTags().isEmpty());
    }
}
