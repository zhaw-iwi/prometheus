package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.LiveTranscriptReceipt;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.*;

class LiveSpeechCueServiceUnitTest {
    private final AgentApplicationService turns = mock(AgentApplicationService.class);
    private final AgentRepository agents = mock(AgentRepository.class);
    private final ExternalSpeechOwnership ownership = mock(ExternalSpeechOwnership.class);
    private final LiveTranscriptReceiptRepository receipts = mock(LiveTranscriptReceiptRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Agent agent = mock(Agent.class);
    private final State state = mock(State.class);
    private final Policy policy = mock(Policy.class, withSettings().extraInterfaces(ExternalVoicePolicy.class));
    private final ExternalVoicePolicy external = (ExternalVoicePolicy) policy;
    private final UUID id = UUID.randomUUID();
    private final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    private final LiveSpeechCue cue = new LiveSpeechCue(UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(1), Instant.now().plusSeconds(29), 500);
    private final Clock clock = mock(Clock.class);
    private final LiveSpeechCueService service = new LiveSpeechCueService(turns, agents, ownership, receipts, transactions, clock);

    @BeforeEach void current() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(clock.instant()).thenReturn(Instant.now());
        when(agent.getId()).thenReturn(id);
        when(turns.serialized(eq(id), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        when(agents.findById(id)).thenReturn(Optional.of(agent)); when(agent.getCurrentState()).thenReturn(state);
        when(state.ownPolicy()).thenReturn(policy); when(ownership.current(agent)).thenReturn(owner);
        when(ownership.taskWorkAllowed(id, owner)).thenReturn(true);
        when(external.liveSpeechCue()).thenReturn(cue); when(external.liveSpeechCueEventType()).thenReturn("sys.test.cue");
        when(receipts.findAllBySessionIdAndProviderEventIdIn(owner.sessionId(), List.of("count"))).thenReturn(List.of(
                new LiveTranscriptReceipt(agent, owner.sessionId(), owner.epoch(), "count", "ASSISTANT", 600L, 1000L,
                        Instant.now().minusMillis(100), 1, "Bereit? Schere, Stein, Papier!")));
    }
    @Test void currentCueRequiresDurableSessionEvidenceAndEmitsOneValidatedInternalEvent() {
        assertTrue(service.accept(id, owner, request()));
        var event = org.mockito.ArgumentCaptor.forClass(EventRequest.class);
        verify(turns).acknowledge(eq(id), event.capture(), eq(OutputProfile.FULL_PLAN));
        var data = JsonParser.parseString(event.getValue().getPayload()).getAsJsonObject();
        assertTrue(data.get("verified").getAsBoolean()); assertEquals(cue.id().toString(), data.get("cueId").getAsString());
    }
    @Test void missingReceiptsOrInputInterruptionRecoverWithoutVerifyingCompletion() {
        when(receipts.findAllBySessionIdAndProviderEventIdIn(any(), any())).thenReturn(List.of());
        assertFalse(service.accept(id, owner, request())); assertFalse(lastVerified());
        reset(turns); when(turns.serialized(eq(id), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        current(); when(receipts.existsBySessionIdAndEpochAndSpeakerAndReceivedAtGreaterThanEqual(any(), any(), eq("USER"), any())).thenReturn(true);
        assertFalse(service.accept(id, owner, request())); assertFalse(lastVerified());
    }
    @Test void oldRoundOwnerAndTimelineCannotOpenCurrentCapture() {
        assertFalse(service.accept(id, owner, new LiveSpeechCueService.Request(UUID.randomUUID(), cue.roundId(), "complete", List.of("count"))));
        verify(turns, never()).acknowledge(any(), any(), any());
        when(ownership.current(agent)).thenReturn(new ExternalSpeech(UUID.randomUUID(), owner.epoch()));
        assertFalse(service.accept(id, owner, request())); verifyNoInteractions(receipts);
    }
    @Test void finalCountdownAllowsEarlierMentionsButRejectsQuotesIncompleteAndTrailingSpeech() {
        assertTrue(LiveSpeechCueService.countdown("Los: Schere, Stein, Papier!"));
        assertTrue(LiveSpeechCueService.countdown("Nach meinem Countdown Schere, Stein, Papier – Schere, Stein, Papier!"));
        assertTrue(LiveSpeechCueService.countdown("Schere, Stein, Papier. SCHERE, STEIN, PAPIER!"));
        assertFalse(LiveSpeechCueService.countdown("Schere, Stein"));
        assertFalse(LiveSpeechCueService.countdown("Eins, zwei, drei!"));
        assertFalse(LiveSpeechCueService.countdown("Stein, Schere, Papier!"));
        assertFalse(LiveSpeechCueService.countdown("Ich sage \"Schere, Stein, Papier\"."));
        assertFalse(LiveSpeechCueService.countdown("Schere, Stein, Papier. Warte noch."));
        assertFalse(LiveSpeechCueService.countdown("Schere, Stein, Papier. Schere, Stein"));
        assertThrows(IllegalArgumentException.class, () -> new LiveSpeechCueService.Request(cue.id(), cue.roundId(), null, List.of()));
    }
    @Test void liveRefreshCancelsOnlyAnExpiredCurrentCueWithoutBrowserEvidence() {
        service.expire(agent, owner);
        verify(turns, never()).acknowledge(any(), any(), any());
        when(clock.instant()).thenReturn(cue.deadline());
        service.expire(agent, owner);
        var event = org.mockito.ArgumentCaptor.forClass(EventRequest.class);
        verify(turns).acknowledge(eq(id), event.capture(), eq(OutputProfile.FULL_PLAN));
        var data = JsonParser.parseString(event.getValue().getPayload()).getAsJsonObject();
        assertEquals("timeout", data.get("outcome").getAsString());
        assertEquals(cue.roundId().toString(), data.get("roundId").getAsString());
        assertFalse(data.get("verified").getAsBoolean());
        verifyNoInteractions(receipts);
    }
    @Test void stoppedOrReplacedOwnerCannotExpireAnotherRound() {
        when(clock.instant()).thenReturn(cue.deadline());
        when(ownership.taskWorkAllowed(id, owner)).thenReturn(false);
        service.expire(agent, owner);
        when(ownership.taskWorkAllowed(id, owner)).thenReturn(true);
        when(ownership.current(agent)).thenReturn(new ExternalSpeech(UUID.randomUUID(), owner.epoch()));
        service.expire(agent, owner);
        verify(turns, never()).acknowledge(any(), any(), any());
    }
    private LiveSpeechCueService.Request request() { return new LiveSpeechCueService.Request(cue.id(), cue.roundId(), "complete", List.of("count")); }
    private boolean lastVerified() {
        var event = org.mockito.ArgumentCaptor.forClass(EventRequest.class);
        verify(turns).acknowledge(eq(id), event.capture(), eq(OutputProfile.FULL_PLAN));
        return JsonParser.parseString(event.getValue().getPayload()).getAsJsonObject().get("verified").getAsBoolean();
    }
}
