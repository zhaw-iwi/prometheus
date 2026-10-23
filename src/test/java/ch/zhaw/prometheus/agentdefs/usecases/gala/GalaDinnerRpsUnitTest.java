package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerRpsUnitTest {
    final ch.zhaw.prometheus.model.Agent agent = new GalaDinnerReception().createAgent();
    final ch.zhaw.prometheus.model.Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);
    Event start() {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        return say("Schere Stein Papier bitte");
    }
    Event say(String words) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, words), runtime); }
    Event played(String id) { return agent.acknowledge(new Event(GalaDinnerPolicy.COUNTDOWN_PLAYED, "system", Event.KIND_SYSTEM, id), runtime); }
    boolean capture(String words, boolean complete, ExternalSpeech spokenOwner, Instant started) {
        Event spoken = agent.recordExternalSpeech(words, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                spokenOwner.sessionId(), spokenOwner.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), complete));
        return GalaDinnerRps.captured(memory, spoken, owner, started, Instant.now());
    }
    @Test void selectionIsVisibleButOnlyCapturedCountdownAndCurrentPlaybackReleaseGestureOnce() {
        var entry = BehaviourPlan.fromJson(start().getPayload());
        assertNull(entry.getMotion()); assertNull(entry.getSpeech());
        assertEquals("rock", entry.getDisplay().getAsJsonObject().get("agentSign").getAsString());
        assertFalse(memory.containsKey(CAPTURE_ID));
        String intro = text(memory, GalaDinnerRps.INTRO, "");
        assertNull(played(intro));
        assertFalse(capture("eins, zwei", true, owner, Instant.now()));
        assertFalse(capture("eins, zwei, drei", false, owner, Instant.now()));
        assertFalse(capture("eins, zwei, drei", true, new ExternalSpeech(UUID.randomUUID(), owner.epoch()), Instant.now()));
        assertFalse(capture("eins, zwei, drei", true, owner, time(memory, GalaDinnerRps.INTRO_AT).minusSeconds(1)));
        assertTrue(capture("Zeig mir deine Hand. Eins, zwei, drei!", true, owner, Instant.now()));
        assertFalse(memory.containsKey(CAPTURE_ID)); assertNull(played(UUID.randomUUID().toString()));
        var reveal = BehaviourPlan.fromJson(played(intro).getPayload());
        assertEquals("rock", reveal.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertTrue(memory.containsKey(CAPTURE_ID)); assertNull(played(intro));
        assertNull(new GalaDinnerPolicy(memory).liveActivityContext().playbackCue());
        verify(gateway, times(1)).infer(any());
    }
    @Test void stopTimeoutAndNextRoundInvalidateOldCompletion() {
        start(); String old = text(memory, GalaDinnerRps.INTRO, "");
        assertTrue(capture("one, two, three", true, owner, Instant.now()));
        say("Stop"); assertNull(played(old)); assertEquals("RECEPTION", mode(memory));
        start(); assertNotEquals(old, text(memory, GalaDinnerRps.INTRO, "")); assertNull(played(old));
        put(memory, GalaDinnerRps.DEADLINE, Instant.now().minusSeconds(1).toString());
        agent.acknowledge(new Event("sys.tick", "system", Event.KIND_SYSTEM, ""), runtime);
        assertEquals("RECEPTION", mode(memory)); assertFalse(memory.containsKey(CAPTURE_ID));
        assertFalse(memory.containsKey(GalaDinnerRps.INTRO));
    }
    @Test void earlierEchoInSameSegmentDoesNotAuthorizeOrInvalidateALaterCountdown() {
        start(); Instant before = time(memory, GalaDinnerRps.INTRO_AT).minusSeconds(1), after = Instant.now();
        var event = agent.recordExternalSpeech("Nochmal bitte. Eins, zwei, drei.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), "merged", SpeechProvenance.Association.NONE, List.of(), true));
        assertFalse(GalaDinnerRps.captured(memory, event, owner, before, after, List.of(
                new LiveSpeechPart("Nochmal bitte. Eins,", before), new LiveSpeechPart(" zwei, drei.", after))));
        assertTrue(GalaDinnerRps.captured(memory, event, owner, before, after, List.of(
                new LiveSpeechPart("Nochmal bitte.", before), new LiveSpeechPart(" Eins, zwei, drei.", after))));
        assertNotNull(played(text(memory, GalaDinnerRps.INTRO, "")));
    }
    @Test void exitProposalSuspendsCountdownAndResumeRequiresFreshIntroduction() {
        start(); String old = text(memory, GalaDinnerRps.INTRO, ""), sign = text(memory, SIGN, "");
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHECK_EXIT", ""));
        say("Was macht das IWI?"); assertNull(GalaDinnerRps.cue(memory, Instant.now()));
        assertFalse(capture("eins zwei drei", true, owner, Instant.now()));
        say("Nein"); assertEquals(sign, text(memory, SIGN, ""));
        assertNotEquals(old, text(memory, GalaDinnerRps.INTRO, "")); assertNull(played(old));
        assertNotNull(GalaRpsTestFixture.countdownPlayed(agent, runtime));
    }
    @Test void aCompletedCountdownWithTrailingSpeechStillNeedsPlaybackAcknowledgement() {
        start(); String intro = text(memory, GalaDinnerRps.INTRO, "");
        assertTrue(capture("Halt eine Hand bereit. Eins, zwei, drei. Ich warte kurz.", true, owner, Instant.now()));
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNotNull(played(intro)); assertTrue(memory.containsKey(CAPTURE_ID));
        assertNull(played(intro));
    }
}
