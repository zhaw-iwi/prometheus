package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerRoundsUnitTest {
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    private final Agent agent = new GalaDinnerReceptionRpsPreparation().createAgent();
    private final GalaDinnerPolicy policy = (GalaDinnerPolicy) agent.getCurrentState().ownPolicy();
    private final Storage storage = policy.storage();

    @BeforeEach void ready() {
        put(storage, COUNT, "1"); put(storage, SEEN, Instant.now().toString()); put(storage, RPS_PREPARED, "true");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
    }
    @Test void threeConsecutiveRoundsHaveIndependentCuesCapturesAndOneResultEach() {
        Set<String> rounds = new HashSet<>(), captures = new HashSet<>();
        String previousCapture = "old-capture";
        for (int i = 0; i < 3; i++) {
            var opening = plan(say(i == 0 ? "Schere Stein Papier bitte" : "Noch einmal"));
            assertNull(opening.getMotion()); assertFalse(opening.getDisplay().getAsJsonObject().has("agentSign"));
            assertEquals("RPS_INTRO", mode(storage)); assertFalse(storage.containsKey(CAPTURE_ID));
            var occasion = policy.liveActivityContext().continuation();
            assertEquals(GalaDinnerRounds.COUNTDOWN, occasion.requiredSpeech());
            assertEquals(text(storage, GalaDinnerRounds.CUE, ""), occasion.id().toString());
            assertEquals(GalaDinnerRounds.COUNTDOWN, opening.getDisplay().getAsJsonObject().get("speechCue")
                    .getAsJsonObject().get("utterance").getAsString());
            assertTrue(rounds.add(text(storage, GalaDinnerRounds.ID, "")));
            assertNull(hand(previousCapture));
            String cue = text(storage, GalaDinnerRounds.CUE, ""), round = text(storage, GalaDinnerRounds.ID, "");
            assertNull(cue(UUID.randomUUID().toString(), round, true));
            var capture = plan(cue(cue, round, true));
            assertNotNull(capture.getMotion()); assertEquals("RPS", mode(storage));
            String current = text(storage, CAPTURE_ID, ""); assertTrue(captures.add(current));
            assertNull(cue(cue, round, true)); assertNull(hand(previousCapture));
            var result = plan(hand(current)); assertNull(result.getMotion());
            assertEquals("RECEPTION", mode(storage)); assertFalse(storage.containsKey(SIGN));
            assertEquals(round, storage.get(GalaDinnerRounds.COMPLETED).getAsJsonObject().get("id").getAsString());
            assertNull(hand(current)); assertNull(agent.generate(runtime)); previousCapture = current;
        }
        verify(gateway, times(1)).infer(any());
    }
    @Test void switchingGamesUsesSeparateSelectionsAndReplayResolvesTheLatestCompletedGame() {
        say("Schere Stein Papier"); assertEquals("rock", text(storage, SIGN, "")); finishRps();
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_GUESS", ""));
        say("Personenraten bitte"); assertEquals(GalaDinnerPeople.PEOPLE.getFirst().name(), text(storage, PERSON, ""));
        say("Ich gebe auf"); say("Noch einmal");
        assertEquals("GUESS", mode(storage)); assertEquals(GalaDinnerPeople.PEOPLE.get(1).name(), text(storage, PERSON, ""));
        say("Stopp"); when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        say("Schere Stein Papier"); assertEquals("scissor", text(storage, SIGN, ""));
        assertEquals("true", text(storage, RPS_PREPARED, ""));
    }
    @Test void interruptionTimeoutAndStopNeverOpenCaptureOrPermitAnOldCue() {
        say("Schere Stein Papier"); String cue = text(storage, GalaDinnerRounds.CUE, ""), round = text(storage, GalaDinnerRounds.ID, "");
        assertNull(plan(say("Wie geht das?" )).getMotion()); assertEquals("RECEPTION", mode(storage));
        assertNull(cue(cue, round, true)); assertFalse(storage.containsKey(CAPTURE_ID));
        say("Schere Stein Papier"); put(storage, GalaDinnerRounds.CUE_AT, Instant.now().minusSeconds(31).toString());
        assertNotNull(agent.tick(runtime)); assertEquals("RECEPTION", mode(storage));
        say("Schere Stein Papier"); cue = text(storage, GalaDinnerRounds.CUE, ""); round = text(storage, GalaDinnerRounds.ID, "");
        say("Stopp"); assertNull(cue(cue, round, true)); assertFalse(storage.containsKey(SIGN));
        assertFalse(storage.containsKey(GalaDinnerRounds.COMPLETED));
    }
    @Test void aCancelledReplayCannotExposeOrAnnounceThePreviousResultAsCurrent() {
        say("Schere Stein Papier"); finishRps();
        assertTrue(storage.get(GalaDinnerRounds.COMPLETED).toString().contains("winner"));
        assertTrue(policy.liveActivityContext().continuation().facts().contains("winner"));
        assertFalse(policy.liveActivityContext().facts().contains("winner"));
        say("Noch einmal");
        var current = policy.liveActivityContext();
        assertEquals(GalaDinnerRounds.COUNTDOWN, current.continuation().requiredSpeech());
        assertFalse(current.facts().contains("winner"));
        assertFalse(current.continuation().facts().contains("winner"));
        cue(text(storage, GalaDinnerRounds.CUE, ""), text(storage, GalaDinnerRounds.ID, ""), false);
        assertEquals("RECEPTION", mode(storage));
        assertNull(policy.liveActivityContext().continuation().requiredSpeech());
        assertTrue(policy.liveActivityContext().facts().contains("Replay history only"));
        assertFalse(policy.liveActivityContext().facts().contains("winner"));
        assertFalse(policy.liveActivityContext().facts().contains("Robot:"));
        assertTrue(storage.containsKey(GalaDinnerRounds.COMPLETED));
    }
    @Test void aRapidYesWaitsForTheInvitationAndCannotConsentToAnInvitationSpokenLater() {
        say("Schere Stein Papier"); finishRps();
        Instant ended = Instant.parse(storage.get(GalaDinnerRounds.COMPLETED).getAsJsonObject().get("at").getAsString());
        var yes = new LiveSpeech(UUID.randomUUID(), "Ja", 501L, 600L, ended.plusMillis(400), true);
        assertEquals(LiveSpeech.Admission.ADMIT, policy.liveReplyAdmission(new LiveSpeech(UUID.randomUUID(), "Again", 501L, 600L, ended.plusMillis(400), true), ended.plusSeconds(1)));
        assertEquals(LiveSpeech.Admission.WAIT, policy.liveReplyAdmission(yes, ended.plusSeconds(1)));
        policy.liveAssistantSpeech(new LiveSpeech(UUID.randomUUID(), "Du gewinnst. Noch eine Runde?", 100L, 500L, ended.plusMillis(100), true), ended.plusSeconds(2));
        assertEquals(LiveSpeech.Admission.ADMIT, policy.liveReplyAdmission(yes, ended.plusSeconds(2)));
        var early = new LiveSpeech(UUID.randomUUID(), "Ja", 200L, 250L, ended.plusMillis(200), true);
        assertEquals(LiveSpeech.Admission.REJECT, policy.liveReplyAdmission(early, ended.plusSeconds(2)));
        clearInvocations(gateway); say("Ja gerne"); assertEquals("RPS_INTRO", mode(storage)); verifyNoInteractions(gateway);
        assertFalse(storage.containsKey(GalaDinnerRounds.INVITATION));
    }
    @Test void completedRoundSurvivesOrdinaryCleanupButInvitationsAndReadinessHaveExplicitBoundaries() {
        say("Schere Stein Papier"); finishRps();
        Instant now = Instant.now();
        policy.liveAssistantSpeech(new LiveSpeech(UUID.randomUUID(), "Noch eine Runde?", 100L, 500L, now, false), now);
        assertFalse(storage.containsKey(GalaDinnerRounds.INVITATION));
        assertEquals(LiveSpeech.Admission.REJECT, policy.liveReplyAdmission(new LiveSpeech(UUID.randomUUID(), "Ja", 600L, 700L, now, true), now.plusSeconds(30)));
        reception(storage); assertTrue(storage.containsKey(GalaDinnerRounds.COMPLETED));
        endEncounter(storage, agent.getEventHistory().toList().size()); assertTrue(storage.containsKey(GalaDinnerRounds.COMPLETED));
        put(storage, COUNT, "1"); put(storage, SEEN, Instant.now().toString());
        policy.beginLiveConversation(agent.getEventHistory());
        assertFalse(storage.containsKey(GalaDinnerRounds.COMPLETED)); assertFalse(storage.containsKey(RPS_PREPARED));
        assertFalse(storage.containsKey("gala.rps_round")); agent.reset(); assertTrue(GalaDinnerRounds.enabled(storage));
    }
    private void finishRps() { cue(text(storage, GalaDinnerRounds.CUE, ""), text(storage, GalaDinnerRounds.ID, ""), true); hand(text(storage, CAPTURE_ID, "")); }
    private Event say(String text) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text), runtime); }
    private Event hand(String capture) { return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{\"captureId\":\"" + capture + "\",\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), runtime); }
    private Event cue(String cue, String round, boolean verified) {
        var data = new JsonObject(); data.addProperty("cueId", cue); data.addProperty("roundId", round); data.addProperty("outcome", "complete"); data.addProperty("verified", verified);
        return agent.acknowledge(new Event(GalaDinnerRounds.SPEECH_CUE, "system", Event.KIND_SYSTEM, data.toString()), runtime);
    }
    private BehaviourPlan plan(Event event) { assertNotNull(event); return BehaviourPlan.fromJson(event.getPayload()); }
}
