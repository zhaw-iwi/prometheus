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
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.InferenceRequest;

class CoordinationHandPoseUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    JsonObject agreement(boolean prepare) {
        return agreement(prepare, task());
    }
    JsonObject agreement(boolean prepare, JsonObject task) {
        var agreement = CoordinationAgentUnitTest.dialogue();
        agreement.addProperty("goal", task.get("goal").getAsString());
        agreement.addProperty("humanRole", "Show the agreed finger pattern when you want a joke");
        agreement.addProperty("agentRole", "Tell a short dry joke on each qualifying observation after the cooldown");
        agreement.add("reactions", task);
        if (prepare) {
            var check = new JsonObject(); check.addProperty("eventType", Event.TYPE_HAND_POSE);
            check.add("handPose", task.getAsJsonArray("rules").get(0).getAsJsonObject().get("handPose").deepCopy());
            check.addProperty("minConfidence", 0); agreement.getAsJsonArray("checks").add(check);
        }
        return agreement;
    }
    Event show(Instant at, JsonObject... hands) { return f.agent.acknowledge(pose(at, hands), f.text); }

    @Test void naturalFingerStatesPrepareAndExecuteWithoutAUserSupplyingNumericThresholds() {
        var proposal = agreement(true, stateTask());
        f.turn("PROPOSE", proposal, "Tell a joke when my middle finger is straight and index, ring and little are bent", f.text);
        var request = ArgumentCaptor.forClass(InferenceRequest.class); verify(f.gateway).infer(request.capture());
        assertTrue(request.getValue().messages().stream().anyMatch(m -> m.getContent()
                .contains("do not ask the user to supply thresholds for these established labels")));
        assertEquals(proposal, CoordinationAgreement.parse(f.storage().get(CoordinationMemory.DRAFT)).json());
        f.storage().put(CoordinationMemory.PREPARED_AFTER, new JsonPrimitive(Instant.now().minusSeconds(1).toString()));
        clearInvocations(f.gateway);
        assertNull(show(Instant.now(), matchingHand())); verifyNoInteractions(f.gateway);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertNotNull(show(Instant.now(), classifiedHand())); verifyNoInteractions(f.gateway);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        f.start(f.text); assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.openCueWindow(); clearInvocations(f.gateway);
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"A short dry joke\"}");
        assertNotNull(show(Instant.now(), classifiedHand()));
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt()); verify(f.gateway).infer(any());
    }

    @Test void numericTaskRunsOnPoseCuesAndKeepsCooldownPauseAndActionBudget() {
        var proposal = agreement(false);
        f.turn("PROPOSE", proposal,
                "Tell a joke when index, ring and little bend values are above 0.5 and middle is below 0.4", f.text);
        var requests = ArgumentCaptor.forClass(InferenceRequest.class); verify(f.gateway).infer(requests.capture());
        String prompt = requests.getValue().messages().stream().map(m -> m.getContent()).reduce("", String::concat);
        assertTrue(prompt.contains("Never replace requested bend comparisons with obs.hand.sign"));
        assertTrue(prompt.contains("SAME hand in the SAME observation"));
        assertEquals(proposal, CoordinationAgreement.parse(f.storage().get(CoordinationMemory.DRAFT)).json());
        f.start(f.text); assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(f.policy().liveActivityContext().facts().contains("\"finger\":\"middle\",\"operator\":\"lt\",\"value\":0.4"));
        f.openCueWindow(); clearInvocations(f.gateway);
        assertNull(show(Instant.now(), hand("right", .2, .3, .6, .8))); verifyNoInteractions(f.gateway);
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"One short joke\"}");
        assertNotNull(show(Instant.now(), matchingHand())); assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertNull(show(Instant.now(), matchingHand())); verify(f.gateway, times(1)).infer(any());
        f.openCueWindow(); assertNotNull(show(Instant.now(), matchingHand()));
        assertEquals(2, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.say("Pause the task", f.text); f.openCueWindow(); clearInvocations(f.gateway);
        assertNull(show(Instant.now(), matchingHand())); verifyNoInteractions(f.gateway);
        f.say("Resume the task", f.text); f.openCueWindow();
        assertNotNull(show(Instant.now(), matchingHand())); assertEquals(3, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
    }

    @Test void preparationUsesTheWholePatternAndCannotSpendAnActionOrAuthorizeOldCues() {
        f.propose(agreement(true), f.text);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertTrue(CoordinationMemory.proposalPrompt(f.storage()).contains("any single hand"));
        var before = Instant.now().minusSeconds(2);
        f.storage().put(CoordinationMemory.PREPARED_AFTER, new JsonPrimitive(before.toString()));
        clearInvocations(f.gateway);
        assertNull(show(before.minusMillis(1), matchingHand()));
        assertNull(show(Instant.now(), hand("left", .2, .3, .6, .8), hand("right", .7, .3, .2, .2)));
        assertEquals(0, CoordinationMemory.evidence(f.storage()).size());
        var prepared = Instant.now(); assertNotNull(show(prepared, matchingHand()));
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        assertEquals(1, CoordinationMemory.evidence(f.storage()).size()); verifyNoInteractions(f.gateway);
        f.start(f.text); assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt()); clearInvocations(f.gateway);
        assertNull(show(prepared, matchingHand())); verifyNoInteractions(f.gateway);
        f.storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.now().minusNanos(1).toString()));
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"A fresh joke\"}");
        assertNotNull(show(Instant.now(), matchingHand())); assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }

    @Test void liveArmingNamesEveryThresholdAndStillWaitsForConfirmedSpeech() {
        f.propose(agreement(false), f.live); f.start(f.live); f.openCueWindow(); clearInvocations(f.gateway);
        String facts = f.policy().liveActivityContext().facts();
        for (String condition : java.util.List.of("index gt 0.5", "ring gt 0.5", "little gt 0.5", "middle lt 0.4"))
            assertTrue(facts.contains(condition), condition);
        assertFalse(facts.contains("Tell one short dry joke"), "Arming does not authorize the future joke");
        assertEquals("ARMING", f.storage().get(TaskResponse.KEY).getAsJsonObject().get("purpose").getAsString());
        f.agent.acknowledge(pose(Instant.now(), matchingHand()), f.live); verifyNoInteractions(f.gateway);
        var started = Instant.parse(f.storage().get(TaskResponse.KEY).getAsJsonObject().get("created").getAsString()).plusNanos(1);
        var event = f.hear("Armed, waiting for the agreed finger bend pattern", true, started);
        var check = TaskResponse.claim(f.storage(), f.owner, event, started, Instant.now()).orElseThrow();
        assertTrue(TaskResponse.finish(f.storage(), f.owner, check, true, Instant.now()));
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"Tell a dry one-line joke\"}");
        f.agent.acknowledge(pose(Instant.now(), matchingHand()), f.live);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt()); verify(f.gateway).infer(any());
        assertEquals("PENDING", TaskResponse.status(f.storage()));
        assertTrue(f.policy().liveActivityContext().facts().contains("Tell a dry one-line joke"));
    }

    @Test void malformedUpdatesPreserveTheProposalAndNumericTermsAreProtectedFromContentOnlyRevision() {
        var proposal = agreement(true); f.propose(proposal, f.text);
        var invalid = proposal.deepCopy(); invalid.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("minConfidence", .8);
        f.propose(invalid, f.text); assertEquals(proposal, f.storage().get(CoordinationMemory.DRAFT));
        var changed = proposal.deepCopy();
        changed.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject()
                .getAsJsonObject("handPose").getAsJsonArray("all").get(0).getAsJsonObject().addProperty("value", .6);
        assertFalse(CoordinationAgreement.parse(proposal).sameTerms(CoordinationAgreement.parse(changed)));
    }
}
