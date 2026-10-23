package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerRoundsUnitTest {
    final Agent agent = new GalaDinnerReception().createAgent();
    final Storage memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
    final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);
    void say(String words) {
        put(memory, COUNT, "1"); put(memory, SEEN, Instant.now().toString());
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, words), runtime);
    }
    void completed(String game) { GalaDinnerRounds.complete(memory, game, "Synthetic confirmed result.", Instant.now(), agent.getEventHistory().toList().size()); }
    void spoken(String words, boolean complete, ExternalSpeech speechOwner) {
        var event = agent.recordExternalSpeech(words, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                speechOwner.sessionId(), speechOwner.epoch(), UUID.randomUUID().toString(),
                SpeechProvenance.Association.NONE, List.of(), complete));
        org.springframework.test.util.ReflectionTestUtils.setField(event, "createdDate", Instant.now());
    }
    @ParameterizedTest @ValueSource(strings = {"RPS", "GUESS"})
    void explicitReplayStartsARealNewRoundWithoutAControlModelCall(String game) {
        completed(game);
        String old = text(memory, GalaDinnerRounds.COMPLETED_ID, "");
        say("Nochmal bitte");
        assertEquals(game, mode(memory)); assertNotEquals(old, text(memory, GalaDinnerRounds.ID, ""));
        assertFalse(memory.containsKey(GalaDinnerRounds.COMPLETED_ID));
        assertFalse(memory.containsKey(OUTCOME));
        assertNotNull(new GalaDinnerPolicy(memory).liveActivityContext().continuation());
        verifyNoInteractions(gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"RPS", "GUESS"})
    void yesToACompletedReplayOfferStartsTheBackendExactlyOnce(String game) {
        completed(game); spoken("Möchtest du noch eine Runde spielen?", true, owner);
        assertTrue(new GalaDinnerPolicy(memory).liveResolvedControlReply("Ja", agent.getEventHistory(), owner));
        say("Ja"); assertEquals(game, mode(memory));
        String id = text(memory, GalaDinnerRounds.ID, "");
        say("Ja gerne"); assertEquals(id, text(memory, GalaDinnerRounds.ID, ""));
        verifyNoInteractions(gateway);
    }
    @Test void ambiguousExpiredInterruptedForeignAndSupersededRepliesDoNotStartGames() {
        for (String offer : List.of("Was möchtest du jetzt tun?", "Du hast gewonnen.", "Möchtest du etwas über das IWI hören?",
                "Möchtest du nochmal spielen oder etwas über das IWI hören?", "Möchtest du nicht nochmal spielen?",
                "Wenn ich frage: Möchtest du nochmal spielen?")) {
            completed("RPS"); spoken(offer, true, owner); say("Ja"); assertEquals("RECEPTION", mode(memory));
        }
        completed("RPS"); spoken("Möchtest du nochmal spielen?", false, owner); say("Ja"); assertEquals("RECEPTION", mode(memory));
        completed("RPS"); spoken("Möchtest du nochmal spielen?", true, new ExternalSpeech(UUID.randomUUID(), owner.epoch()));
        say("Ja"); assertEquals("RECEPTION", mode(memory));
        completed("GUESS"); put(memory, GalaDinnerRounds.COMPLETED_AT, Instant.now().minusSeconds(61).toString());
        say("Nochmal"); assertEquals("RECEPTION", mode(memory));
        completed("RPS"); spoken("Möchtest du nochmal spielen?", true, owner);
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHAT", ""));
        say("Was macht das IWI?"); say("Nochmal"); assertEquals("RECEPTION", mode(memory));
        verify(gateway, times(1)).infer(any());
    }
    @Test void eachGameAdvancesItsOwnSelectionAndCleanupPreservesNeitherTargetNorCapture() {
        when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""),
                GalaDinnerReceptionUnitTest.answer("START_GUESS", ""));
        say("Schere Stein Papier bitte"); assertEquals("rock", text(memory, SIGN, ""));
        say("Stop"); assertFalse(memory.containsKey(CAPTURE_ID)); assertFalse(memory.containsKey(GalaDinnerRounds.ID));
        say("Personenraten bitte"); assertEquals("Albert Einstein", text(memory, PERSON, ""));
        say("Ich gebe auf"); assertEquals("GUESS", text(memory, GalaDinnerRounds.COMPLETED_GAME, ""));
        say("Nochmal bitte"); assertEquals("Marie Curie", text(memory, PERSON, ""));
        assertFalse(memory.containsKey(SIGN)); assertFalse(memory.containsKey(CAPTURE_ID));
    }
}
