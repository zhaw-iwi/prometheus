package ch.zhaw.prometheus.agentdefs.core;

import static fixtures.behaviour.NamedHandSignFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.task.*;

class CoordinationNamedSignUnitTest {
    @Test void acceptedFreshThumbsUpProducesOneSilentPeacePlan() {
        var f = new CoordinationAgentUnitTest(); var proposal = agreement();
        proposal.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("action", "Show peace silently with both hands");
        f.propose(proposal, f.text);
        f.agent.acknowledge(event("prep", "thumbs_up", "right", Instant.now()), f.text);
        f.turn("START", null, "Start now", f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.storage().put(TaskMemory.AFTER,new JsonPrimitive(Instant.now().minusSeconds(1).toString()));
        var motion=new JsonObject();motion.add("handAction",HandActions.named("peace","both_hands"));
        var output=new JsonObject();output.add("motion",motion);when(f.gateway.infer(any())).thenReturn(output.toString());
        var response=f.agent.acknowledge(event("fresh", "thumbs_up", "right", Instant.now()), f.text);
        assertNotNull(response);var plan=ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(response.getPayload());
        assertEquals(motion,plan.getMotion());assertNull(plan.getSpeech());assertEquals(1,f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(proposal,f.storage().get(CoordinationMemory.AGREEMENT));
    }
    @Test void everyShapeAndSideUsesOneHandAndStrictEvidence() {
        for (String sign : HandSignObservation.BASIC_SIGNS) for (String side : java.util.List.of("left", "right", "unknown")) {
            var input = observation("frame", sign, side, Instant.now());
            var parsed = HandSignObservation.parse(input.toString());
            assertTrue(new HandSignCondition(sign, "any").matches(parsed, .8));
            assertEquals(side.equals("left"), new HandSignCondition(sign, "left").matches(parsed, .8));
            assertFalse(new HandSignCondition(sign, "any").matches(parsed, .95));
            input.getAsJsonArray("hands").get(0).getAsJsonObject().add("confidence", JsonNull.INSTANCE);
            assertFalse(new HandSignCondition(sign, "any").matches(HandSignObservation.parse(input.toString()), 0));
        }
        var two = observation("frame", "thumbs_up", "right", Instant.now());
        var other = two.getAsJsonArray("hands").get(0).deepCopy().getAsJsonObject(); other.addProperty("id", "hand-1");
        two.getAsJsonArray("hands").add(other);
        assertFalse(new HandSignCondition("thumbs_up", "right").matches(HandSignObservation.parse(two.toString()), 0));
        other.addProperty("handedness", "left"); other.addProperty("sign", "peace");
        assertFalse(new HandSignCondition("peace", "right").matches(HandSignObservation.parse(two.toString()), 0));
        assertTrue(new HandSignCondition("peace", "left").matches(HandSignObservation.parse(two.toString()), 0));
    }
    @Test void malformedAndFutureVocabularyRejectWithoutInventingEvidence() {
        var valid = observation("frame", "peace", "left", Instant.now());
        for (String payload : java.util.List.of("{}", valid.toString().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.toString().replace("peace", "rock"), valid.toString().replace("0.9", "\"0.9\""),
                valid.toString().replace("false", "true"), " ".repeat(16385)))
            assertThrows(IllegalArgumentException.class, () -> HandSignObservation.parse(payload));
        var duplicate = valid.deepCopy(); duplicate.getAsJsonArray("hands").add(duplicate.getAsJsonArray("hands").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> HandSignObservation.parse(duplicate.toString()));
        valid.addProperty("instruction", "start");
        assertThrows(IllegalArgumentException.class, () -> HandSignObservation.parse(valid.toString()));
    }
    @Test void preparationAcceptanceAndConsumedFrameFencingRemainBackendOwned() {
        var f = new CoordinationAgentUnitTest(); var proposal = agreement();
        // Keep execution active to prove duplicate rejection independently of the final budget.
        proposal.getAsJsonObject("reactions").addProperty("maxActions", 2);
        f.propose(proposal, f.text);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        for (var event : java.util.List.of(event("wrong", "peace", "right", Instant.now()),
                event("side", "thumbs_up", "left", Instant.now()), event("old", "thumbs_up", "right", Instant.now().minusSeconds(30))))
            f.agent.acknowledge(event, f.text);
        assertEquals(0, CoordinationMemory.evidence(f.storage()).size());
        f.agent.acknowledge(event("prep", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
        f.turn("START", null, "Start now", f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.now().minusSeconds(1).toString()));
        f.agent.acknowledge(event("prep", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"Acknowledged.\"}");
        f.agent.acknowledge(event("cue", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        f.storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.now().minusSeconds(1).toString()));
        clearInvocations(f.gateway);
        f.agent.acknowledge(event("cue", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt()); verifyNoInteractions(f.gateway);
        assertEquals(proposal, f.storage().get(CoordinationMemory.AGREEMENT));
    }
    @Test void oldInstancesDoNotSilentlyGainNewAgreementCapabilities() {
        var f = new CoordinationAgentUnitTest(); f.storage().remove(HandEvidence.ENABLED);
        f.propose(agreement(), f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertTrue(HandEvidence.consumed(f.storage(), event("cue", "thumbs_up", "right", Instant.now())));
    }
    @Test void repeatedFrameCannotProvideTwoSamplesAndFutureOrMissingTimeCannotMatch() {
        var f = new CoordinationAgentUnitTest(); var proposal = agreement(); proposal.getAsJsonArray("checks").remove(0);
        proposal.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("samples", 2);
        f.propose(proposal, f.text); f.turn("START", null, "Start now", f.text);
        f.storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.now().minusSeconds(1).toString()));
        var rule = CoordinationAgreement.parse(proposal).reactions().rules().getFirst();
        assertFalse(TaskDecision.matchesObservation(rule, event("future", "thumbs_up", "right", Instant.now().plusSeconds(60)), Instant.now()));
        assertFalse(TaskDecision.matchesObservation(rule, Event.observation(Event.TYPE_HAND_SIGNS, "camera", "{}"), Instant.now()));
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"Acknowledged.\"}"); clearInvocations(f.gateway);
        f.agent.acknowledge(event("same", "thumbs_up", "right", Instant.now()), f.text);
        f.agent.acknowledge(event("same", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt()); verifyNoInteractions(f.gateway);
        f.agent.acknowledge(event("next", "thumbs_up", "right", Instant.now()), f.text);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @Test void ingressBoundsUseReceiptTimeAndIgnoreLegacyRates() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z"); var history = new java.util.ArrayList<Event>();
        String payload = observation("next", "peace", "left", now).toString();
        for (int i = 0; i < 5; i++) {
            HandObservationAdmission.validate(payload, history, now);
            var input = event("frame" + i, "peace", "left", now.minusSeconds(30));
            org.springframework.test.util.ReflectionTestUtils.setField(input, "createdDate", now.minusMillis(100)); history.add(input);
        }
        assertThrows(IllegalArgumentException.class, () -> HandObservationAdmission.validate(payload, history, now));
        HandObservationAdmission.validate(payload, history, now.plusSeconds(1));
        for (var input : history) org.springframework.test.util.ReflectionTestUtils.setField(input, "type", Event.TYPE_HAND_SIGN);
        HandObservationAdmission.validate(payload, history, now);
    }
    @Test void genericAgentsDoNotAcceptAConfigurationForUndeclaredNamedInput() {
        var f = new CoordinationAgentUnitTest(); var generic = new GenericMultimodalBehaviour().createAgent();
        when(f.gateway.infer(any())).thenReturn("{\"operation\":\"ACTIVATE\",\"task\":" + agreement().get("reactions")
                + ",\"reply\":{\"speech\":\"Ready.\"}}");
        generic.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Configure the requested named-hand task"), f.text);
        var storage = ((TaskPolicy) generic.getCurrentState().ownPolicy()).storage();
        assertFalse(storage.containsKey(TaskMemory.SPEC)); assertFalse(storage.containsKey(TaskMemory.DRAFT));
    }
}
