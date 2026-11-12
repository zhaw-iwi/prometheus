package ch.zhaw.prometheus.agentdefs.core;

import static fixtures.behaviour.HandPoseFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.spi.InferenceRequest;

/** Hand-pose support remains available to Generic agents, outside Coordination's sensing contract. */
class CoordinationHandPoseUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();

    JsonObject agreement(JsonObject task) {
        var proposal = CoordinationAgentUnitTest.dialogue();
        proposal.addProperty("goal", task.get("goal").getAsString());
        proposal.add("reactions", task);
        return proposal;
    }

    @Test void coordinationKeepsItsExistingObservationProfileAndScalarPrompt() {
        assertFalse(f.agent.getInteractionProfile().supportsObservation(Event.TYPE_HAND_POSE));
        assertTrue(f.agent.getInteractionProfile().supportsObservation(Event.TYPE_HAND_SIGN));
        assertFalse(f.agent.getInteractionProfile().getSupportedObservations().contains(AgentInteractionProfile.OBS_HAND_POSE));
        assertTrue(new GenericMultimodalBehaviourAtSocialSpace().createAgent().getInteractionProfile()
                .getSupportedObservations().contains(AgentInteractionProfile.OBS_HAND_POSE));
        f.propose(CoordinationAgentUnitTest.sensor(true), f.text);
        var request = ArgumentCaptor.forClass(InferenceRequest.class);
        verify(f.gateway).infer(request.capture());
        String prompt = request.getValue().messages().stream().map(message -> message.getContent())
                .reduce("", String::concat);
        assertTrue(prompt.contains("Supported sensor fields:"));
        assertFalse(prompt.contains("runtime hand-pose extension"));
        assertFalse(prompt.contains("do not ask the user to supply thresholds for these established labels"));
    }

    @Test void coordinationRejectsNumericAndClassifiedHandPoseReactionAgreements() {
        for (var task : java.util.List.of(task(), stateTask()))
            assertThrows(IllegalArgumentException.class, () -> CoordinationAgreement.parse(agreement(task)));
    }

    @Test void handPosePreparationCannotReplaceAnExistingProposal() {
        var saved = CoordinationAgentUnitTest.sensor(true);
        f.propose(saved, f.text);
        var invalid = saved.deepCopy();
        var check = new JsonObject(); check.addProperty("eventType", Event.TYPE_HAND_POSE);
        check.add("handPose", stateTask().getAsJsonArray("rules").get(0).getAsJsonObject().get("handPose").deepCopy());
        check.addProperty("minConfidence", 0);
        invalid.getAsJsonArray("checks").set(0, check);
        assertThrows(IllegalArgumentException.class, () -> CoordinationAgreement.parse(invalid));
        f.propose(invalid, f.text);
        assertEquals(saved, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
    }

    @Test void handPoseObservationCannotSatisfyExistingSignPreparationOrSpendAnAction() {
        f.propose(CoordinationAgentUnitTest.sensor(true), f.text);
        clearInvocations(f.gateway);
        assertNull(f.agent.acknowledge(pose(Instant.now(), classifiedHand()), f.text));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertEquals(0, CoordinationMemory.evidence(f.storage()).size());
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
        verifyNoInteractions(f.gateway);
    }
}
