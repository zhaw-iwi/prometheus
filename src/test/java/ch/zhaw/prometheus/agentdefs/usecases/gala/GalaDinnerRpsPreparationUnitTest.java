package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerRpsPreparationUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final GalaDinnerReceptionRpsPreparation definition = new GalaDinnerReceptionRpsPreparation();
    private final Agent agent = definition.createAgent();
    private final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();

    @BeforeEach void occupied() { put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString()); }

    @Test void cloneHasSeparateIdentityAndOnlyTheCloneHasAPreparationState() {
        var original = new GalaDinnerReception();
        assertNotEquals(original.key(), definition.key());
        assertEquals("usecases.gala_dinner_reception_rps_preparation", definition.key());
        assertEquals("GIGI - Gala Dinner Reception", original.displayName());
        assertEquals("GIGI - Gala Dinner Reception with RPS preparation", definition.displayName());
        assertFalse(original.createAgent().listStates().contains("Gala rock-scissor-paper preparation"));
        assertTrue(agent.listStates().contains("Gala rock-scissor-paper preparation"));
        for (var embodiment : AgentEmbodiment.values()) {
            var created = definition.createInstance(new AgentCreationContext(new PromptMessageAssembler(), gateway, embodiment));
            assertEquals(embodiment, created.agent().getEmbodiment());
            assertEquals("GIGI", created.agent().getPersonaName());
            assertEquals("de", created.agent().getLanguageCode());
            assertTrue(new LiveVoicePolicyAdapter().supports(created.agent()));
            assertTrue(created.agent().getInteractionProfile().isPresenceControlledLive());
            assertEquals(original.createAgent().getInteractionProfile().toJson(), created.agent().getInteractionProfile().toJson());
        }
        verifyNoInteractions(gateway);
    }

    @Test void rpsSelectionRequiresAllThreeDistinctSignsBeforeStartingTheExistingRound() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        var setup = BehaviourPlan.fromJson(say("Schere Stein Papier bitte").getPayload());
        assertEquals("Gala rock-scissor-paper preparation", agent.getCurrentState().getName());
        assertEquals("RPS_SETUP", mode(memory));
        assertNull(setup.getMotion()); assertNull(setup.getSpeech());
        assertFalse(memory.containsKey(SIGN)); assertFalse(memory.containsKey(ROUND));
        assertEquals("recognise_all_signs", setup.getDisplay().getAsJsonObject().get("phase").getAsString());
        assertTrue(text(memory, CONTINUATION_FACTS, "").contains("All three must be recognised before the game starts"));
        String capture = text(memory, CAPTURE_ID, "");
        String occasion = text(memory, CONTINUATION_ID, "");
        assertNotNull(hand("scissor", Instant.now(), .99));
        assertNull(hand("scissor", Instant.now(), .99));
        assertNotNull(hand("rock", Instant.now(), .99));
        assertEquals(List.of("paper"), GalaDinnerRpsPreparation.remaining(memory));
        assertEquals("RPS_SETUP", mode(memory)); assertFalse(memory.containsKey(SIGN));
        assertEquals(occasion, text(memory, CONTINUATION_ID, ""), "Progress updates must not request repeated speech");
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("still needed: [paper]"));
        var start = BehaviourPlan.fromJson(hand("paper", Instant.now(), .99).getPayload());
        assertEquals("Gala rock-scissor-paper countdown", agent.getCurrentState().getName());
        assertNull(start.getMotion());
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNull(hand("paper", Instant.now(), .99, capture));
        var captureStart = BehaviourPlan.fromJson(countdown().getPayload());
        assertEquals("rock", captureStart.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertFalse(start.getDisplay().getAsJsonObject().has("winner"));
        assertEquals("1", text(memory, "gala.rps_round", ""));
        assertNotEquals(capture, text(memory, CAPTURE_ID, ""));
        assertFalse(memory.containsKey(RPS_RECOGNISED));
        assertNull(hand("paper", Instant.now(), .99, capture), "Preparation frames cannot decide the game");
        assertNotNull(hand("paper", Instant.now(), .99));
        assertEquals("RECEPTION", mode(memory));
        assertEquals("true", text(memory, RPS_PREPARED, ""));
        var replay = BehaviourPlan.fromJson(say("Noch einmal bitte").getPayload());
        assertEquals("Gala rock-scissor-paper countdown", agent.getCurrentState().getName());
        assertEquals("2", text(memory, "gala.rps_round", ""));
        assertNull(replay.getMotion());
        assertEquals("scissor", BehaviourPlan.fromJson(countdown().getPayload()).getMotion().getAsJsonObject().get("handSign").getAsString());
        assertFalse(replay.getDisplay().getAsJsonObject().has("remainingSigns"));
        verify(gateway, times(1)).infer(any());
    }

    @Test void preparationRejectsBadEvidenceAndSpokenClaimsCannotSkipRecognition() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        say("Schere Stein Papier bitte");
        assertNull(hand("rock", time(memory, STARTED).minusMillis(1), .99));
        assertNull(hand("paper", Instant.now(), .69));
        assertNull(hand("lizard", Instant.now(), .99));
        assertNull(hand("scissor", Instant.now(), .99, "previous-capture"));
        assertNull(hand("scissor", Instant.now(), .99, null));
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{}"), runtime));
        assertTrue(GalaDinnerRpsPreparation.recognised(memory).isEmpty());
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("WAIT", ""));
        assertNull(say("Ich habe alle drei Zeichen gezeigt, fang an"));
        assertEquals("RPS_SETUP", mode(memory)); assertFalse(memory.containsKey(SIGN));
        assertNull(agent.generate(runtime));
    }

    @Test void preparationPausesForExitConfirmationPreservesProgressOnResumeAndClearsOnStop() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""), GalaDinnerReceptionUnitTest.answer("CHECK_EXIT", ""));
        say("Schere Stein Papier bitte"); hand("rock", Instant.now(), .99);
        String capture = text(memory, CAPTURE_ID, "");
        say("Was ist das IWI?");
        assertTrue(memory.containsKey(EXIT_REQUEST));
        assertTrue(new GalaDinnerPolicy(memory).liveActivityContext().facts().contains("RPS preparation paused"));
        assertNull(hand("paper", Instant.now(), .99));
        assertEquals(List.of("rock"), GalaDinnerRpsPreparation.recognised(memory));
        var resumed = BehaviourPlan.fromJson(say("Nein").getPayload());
        assertEquals("recognise_all_signs", resumed.getDisplay().getAsJsonObject().get("phase").getAsString());
        assertNotEquals(capture, text(memory, CAPTURE_ID, ""));
        assertNull(hand("paper", Instant.now(), .99, capture));
        assertEquals(List.of("rock"), GalaDinnerRpsPreparation.recognised(memory));
        clearInvocations(gateway);
        say("Stopp");
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(RPS_RECOGNISED));
        assertFalse(memory.containsKey(CAPTURE_ID)); verifyNoInteractions(gateway);
    }

    @ParameterizedTest @ValueSource(strings = {"quiet", "closed", "timeout", "reset", "confirmed_exit"})
    void leavingPreparationDiscardsRecognisedSigns(String reason) {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""), GalaDinnerReceptionUnitTest.answer("CHECK_EXIT", ""));
        say("Schere Stein Papier bitte"); hand("rock", Instant.now(), .99);
        switch (reason) {
            case "quiet" -> say("Bitte sei still");
            case "closed" -> agent.acknowledge(new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "closed"), runtime);
            case "timeout" -> { put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString()); agent.tick(runtime); }
            case "reset" -> agent.reset();
            case "confirmed_exit" -> { say("Was ist das IWI?"); say("Ja"); }
            default -> fail("Unknown exit");
        }
        assertEquals("Gala reception", agent.getCurrentState().getName());
        assertFalse(memory.containsKey(RPS_RECOGNISED)); assertFalse(memory.containsKey(CAPTURE_ID));
        assertFalse(memory.containsKey(RPS_PREPARED));
        assertFalse(memory.containsKey(SIGN)); assertFalse(memory.containsKey(ROUND));
    }

    @ParameterizedTest @ValueSource(strings = {"result", "stop", "quiet", "other_game", "confirmed_exit", "timeout", "late_turn", "brief_absence"})
    void completedPreparationIsRetainedThroughoutTheSameConversation(String change) {
        completePreparation();
        switch (change) {
            case "result" -> hand("paper", Instant.now(), .99);
            case "stop" -> say("Stopp");
            case "quiet" -> say("Bitte sei still");
            case "other_game" -> {
                say("Stopp");
                when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_GUESS", ""));
                say("Personenraten bitte");
                say("Ich gebe auf");
            }
            case "confirmed_exit" -> {
                when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHECK_EXIT", ""));
                say("Was ist das IWI?"); say("Ja");
            }
            case "timeout" -> { put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString()); agent.tick(runtime); }
            case "late_turn" -> put(memory, LAST_INPUT, Instant.now().minusSeconds(91).toString());
            case "brief_absence" -> {
                Instant absent = Instant.now();
                agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                        "{\"humanCount\":0,\"ts\":\"" + absent + "\"}"), runtime);
                assertEquals("true", text(memory, RPS_PREPARED, ""));
                assertTrue(memory.containsKey(ABSENT_SINCE));
                agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                        "{\"humanCount\":1,\"ts\":\"" + absent.plusMillis(1) + "\"}"), runtime);
                assertFalse(memory.containsKey(ABSENT_SINCE));
                say("Stopp");
            }
            default -> fail("Unknown change");
        }
        assertEquals("true", text(memory, RPS_PREPARED, ""));
        occupied();
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        var replay = BehaviourPlan.fromJson(say("Noch einmal Schere Stein Papier bitte").getPayload());
        assertEquals("Gala rock-scissor-paper countdown", agent.getCurrentState().getName());
        assertNull(replay.getMotion());
        assertNotNull(BehaviourPlan.fromJson(countdown().getPayload()).getMotion());
        assertEquals("true", text(memory, RPS_PREPARED, ""));
    }

    @ParameterizedTest @ValueSource(strings = {"new_live", "closed", "reset"})
    void aNewConversationRequiresPreparationAgain(String restart) {
        completePreparation(); hand("paper", Instant.now(), .99);
        if (restart.equals("reset")) agent.reset();
        else {
            if (restart.equals("closed")) agent.acknowledge(
                    new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "closed"), runtime);
            occupied();
            new GalaDinnerPolicy(memory).beginLiveConversation(agent.getEventHistory());
        }
        assertFalse(memory.containsKey(RPS_PREPARED));
        occupied();
        say("Schere Stein Papier bitte");
        assertEquals("Gala rock-scissor-paper preparation", agent.getCurrentState().getName());
        assertEquals(List.of("rock", "paper", "scissor"), GalaDinnerRpsPreparation.remaining(memory));
        assertFalse(memory.containsKey(SIGN));
    }

    private void completePreparation() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        say("Schere Stein Papier bitte");
        for (String sign : List.of("rock", "paper", "scissor")) hand(sign, Instant.now(), .99);
        countdown();
        assertEquals("RPS", mode(memory));
        assertEquals("true", text(memory, RPS_PREPARED, ""));
    }

    private Event say(String speech) {
        return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, speech), runtime);
    }
    private Event countdown() {
        var cue = new JsonObject(); cue.addProperty("cueId", text(memory, GalaDinnerRounds.CUE, ""));
        cue.addProperty("roundId", text(memory, GalaDinnerRounds.ID, ""));
        cue.addProperty("outcome", "complete"); cue.addProperty("verified", true);
        return agent.acknowledge(new Event(GalaDinnerRounds.SPEECH_CUE, "system", Event.KIND_SYSTEM, cue.toString()), runtime);
    }
    private Event hand(String sign, Instant time, double confidence) {
        return hand(sign, time, confidence, text(memory, CAPTURE_ID, ""));
    }
    private Event hand(String sign, Instant time, double confidence, String captureId) {
        var payload = new JsonObject(); payload.addProperty("sign", sign); payload.addProperty("confidence", confidence);
        payload.addProperty("ts", time.toString());
        if (captureId != null) payload.addProperty("captureId", captureId);
        return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor",
                payload.toString()), runtime);
    }
}
