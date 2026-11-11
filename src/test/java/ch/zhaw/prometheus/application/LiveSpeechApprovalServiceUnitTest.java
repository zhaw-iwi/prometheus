package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;

class LiveSpeechApprovalServiceUnitTest {
    final UUID id = UUID.randomUUID(), segmentId = UUID.randomUUID(), requestId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-11T12:00:00Z");
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    final LiveSpeechRequest request = new LiveSpeechRequest(requestId, owner, 0, now.minusSeconds(5), now.plusSeconds(25), "The task is stopped, not paused.");
    final Agent agent = mock(Agent.class);
    final CoordinationPolicy policy = mock(CoordinationPolicy.class);
    final Storage storage = new Storage();
    final AgentRepository agents = mock(AgentRepository.class);
    final LiveTranscriptSegmentRepository segments = mock(LiveTranscriptSegmentRepository.class);
    final ExternalSpeechOwnership ownership = mock(ExternalSpeechOwnership.class);
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final LiveSpeechApprovalService.Window window = new LiveSpeechApprovalService.Window();
    final Clock clock = mock(Clock.class);
    LiveSpeechApprovalService service;
    LiveTranscriptSegment segment;
    Event speech;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        var turns = mock(AgentApplicationService.class);
        when(turns.serialized(eq(id), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new LiveSpeechApprovalService(turns, agents, segments, ownership, gateway, tx,
                mock(AgentBehaviourBroadcaster.class), mock(org.springframework.context.ApplicationEventPublisher.class), clock);
        when(agent.getId()).thenReturn(id);
        when(agents.findById(id)).thenReturn(Optional.of(agent));
        var state = mock(State.class); when(agent.getCurrentState()).thenReturn(state); when(state.ownPolicy()).thenReturn(policy);
        when(policy.storage()).thenReturn(storage); when(policy.liveSpeechApprovalRequired()).thenReturn(true);
        when(policy.liveSpeechRequest(owner)).thenReturn(request);
        when(ownership.current(agent)).thenReturn(owner); when(ownership.taskWorkAllowed(id, owner)).thenReturn(true);
        var history = new EventHistory(); when(agent.getEventHistory()).thenReturn(history);
        var event = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, Event.KIND_RESPONSE, "{\"speech\":\"The task is stopped.\"}");
        event.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), segmentId.toString(), SpeechProvenance.Association.NONE, List.of(), true, false));
        speech = history.appendEvent(event);
        segment = new LiveTranscriptSegment(segmentId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT", "The task is stopped.", 0L, 1000L, now.minusSeconds(4), "Coordination");
        segment.receipts(List.of("receipt")); segment.finish("COMPLETE", null, null); when(segments.findById(segmentId)).thenReturn(Optional.of(segment));
        when(gateway.infer(any())).thenAnswer(call -> verdict("MATCH"));
    }
    String verdict(String value) { return "{\"responseId\":\"" + requestId + "\",\"revision\":0,\"segmentId\":\"" + segmentId + "\",\"verdict\":\"" + value + "\"}"; }
    LiveSpeechApprovalService.Result approve() { return service.approve(id, owner, window, segmentId.toString()); }
    boolean release() { return service.release(id, owner, window, requestId, segmentId.toString()); }
    boolean played() { return service.played(id, owner, window, requestId, segmentId.toString()); }
    @Test void transientObservationCannotBeginPlaybackAfterItsOriginalExpiry() {
        var expiry = now.plusSeconds(1);
        when(policy.liveSpeechRequest(owner)).thenReturn(new LiveSpeechRequest(requestId, owner, 0, request.created(), expiry, request.facts(), expiry));
        assertEquals("APPROVED", approve().status());
        when(clock.instant()).thenReturn(expiry);
        assertFalse(release()); assertFalse(played());
        assertTrue(ConversationProjection.isUnplayed(speech));
    }
    @Test void transientObservationReleasedInTimeMayFinishAfterItsAdmissionExpiry() {
        var expiry = now.plusSeconds(1);
        when(policy.liveSpeechRequest(owner)).thenReturn(new LiveSpeechRequest(requestId, owner, 0, request.created(), expiry, request.facts(), expiry));
        assertEquals("APPROVED", approve().status()); assertTrue(release());
        when(clock.instant()).thenReturn(expiry.plusSeconds(1));
        assertTrue(played()); assertFalse(ConversationProjection.isUnplayed(speech));
    }
    @Test void approvalReleaseAndObservedCompletionAreSeparateAndIdempotent() {
        assertTrue(ConversationProjection.isUnplayed(speech)); assertFalse(played());
        assertEquals("APPROVED", approve().status()); assertEquals("APPROVED", approve().status());
        assertTrue(ConversationProjection.isUnplayed(speech)); assertFalse(played());
        assertTrue(release()); assertFalse(release()); assertTrue(ConversationProjection.isUnplayed(speech));
        assertTrue(played()); assertFalse(played()); assertFalse(ConversationProjection.isUnplayed(speech));
        verify(gateway, times(1)).infer(any()); verify(policy).liveSpeechCaptured(eq(speech), eq(owner), any(), any(), eq(List.of()));
    }
    @ParameterizedTest @ValueSource(strings = {"AMBIGUOUS", "MISMATCH", "malformed", "failure"})
    void unverifiableSpeechNeverReleasesAndIsNotRetried(String verdict) {
        when(gateway.infer(any())).thenAnswer(call -> { if (verdict.equals("failure")) throw new IllegalStateException(); return verdict.equals("malformed") ? "not JSON" : verdict(verdict); });
        assertEquals("REJECTED", approve().status()); assertEquals("REJECTED", approve().status());
        assertFalse(release()); assertFalse(played()); assertTrue(ConversationProjection.isUnplayed(speech)); verify(gateway, times(1)).infer(any());
    }
    @ParameterizedTest @ValueSource(strings = {"owner", "scope", "incomplete", "early", "epoch", "revision", "interrupted", "closed"})
    void missingOrStaleAuthorityCannotApprove(String kind) {
        switch (kind) {
            case "owner" -> when(ownership.current(agent)).thenReturn(null);
            case "scope" -> when(ownership.taskWorkAllowed(id, owner)).thenReturn(false);
            case "incomplete" -> segment.finish("INCOMPLETE", "interruption", null);
            case "early" -> { segment = new LiveTranscriptSegment(segmentId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT", "The task is stopped.", 0L, 1000L, request.created(), "Coordination"); segment.receipts(List.of("receipt")); segment.finish("COMPLETE", null, null); when(segments.findById(segmentId)).thenReturn(Optional.of(segment)); }
            case "epoch", "revision" -> when(policy.liveSpeechRequest(owner)).thenReturn(null);
            case "interrupted" -> window.interrupt(now);
            case "closed" -> window.close();
        }
        assertEquals("DISCARDED", approve().status()); assertFalse(release()); assertFalse(played()); verifyNoInteractions(gateway);
    }
    @Test void interruptionDuringInferenceAndBetweenReleaseAndCompletionCannotPublishSpeech() {
        when(gateway.infer(any())).thenAnswer(call -> { window.interrupt(now); return verdict("MATCH"); });
        assertEquals("DISCARDED", approve().status()); assertFalse(release()); assertFalse(played());
        assertTrue(ConversationProjection.isUnplayed(speech));
    }
    @Test void replacementAfterApprovalRequiresNewAuthority() {
        assertEquals("APPROVED", approve().status());
        when(policy.liveSpeechRequest(owner)).thenReturn(new LiveSpeechRequest(UUID.randomUUID(), owner, 1, now.minusSeconds(2), now.plusSeconds(28), "The task is running."));
        assertFalse(release()); assertFalse(played());
    }
    @Test void interruptionAfterReleaseNeverBecomesConversationEvidence() {
        assertEquals("APPROVED", approve().status()); assertTrue(release()); window.interrupt(now);
        assertFalse(played()); assertTrue(ConversationProjection.isUnplayed(speech));
    }
    @Test void aDuplicateInputReceiptCannotInterruptANewerApprovedRequest() {
        window.input("previous-input", request.created().minusMillis(1));
        assertEquals("APPROVED", approve().status());
        window.input("previous-input", now);
        assertTrue(release());
        window.input("new-input", now);
        assertFalse(played()); assertTrue(ConversationProjection.isUnplayed(speech));
    }
    @Test void ordinaryCurrentTurnDraftMayBeReviewedAfterCommitButCannotBorrowAnotherTurnOrTaskAuthority() {
        segment = new LiveTranscriptSegment(segmentId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT", "The task is stopped.",
                0L, 1000L, request.created().minusMillis(1), "Coordination");
        segment.receipts(List.of("receipt")); segment.finish("COMPLETE", null, null);
        when(segments.findById(segmentId)).thenReturn(Optional.of(segment));
        assertEquals("DISCARDED", approve().status()); verifyNoInteractions(gateway);
        window.input("current-turn", request.created().minusSeconds(1));
        assertEquals("APPROVED", approve().status()); verify(gateway, times(1)).infer(any());
        TaskResponse.expect(storage, owner, requestId, request.facts(), request.created());
        assertFalse(release()); // The same early generation can never satisfy task authority.
        storage.remove(TaskResponse.KEY);
        window.input("newer-turn", now);
        assertFalse(release());
    }
    @Test void taskAssociationReusesVerifierButNeverReleasesASuffixOfDifferentWholeSpeech() {
        storage.put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        TaskResponse.expect(storage, owner, requestId, "The task is stopped, not paused.", request.created());
        var value = storage.get(TaskResponse.KEY).getAsJsonObject(); value.addProperty("status", "CAPTURED"); value.addProperty("segment", segmentId.toString());
        value.addProperty("speechOffset", 10);
        assertEquals("REJECTED", approve().status()); assertFalse(release()); verifyNoInteractions(gateway);
        value.addProperty("speechOffset", 0);
        assertEquals("APPROVED", approve().status()); assertTrue(release()); assertTrue(TaskResponse.playbackPending(storage));
        assertTrue(played()); assertFalse(TaskResponse.playbackPending(storage)); verifyNoInteractions(gateway);
    }
}
