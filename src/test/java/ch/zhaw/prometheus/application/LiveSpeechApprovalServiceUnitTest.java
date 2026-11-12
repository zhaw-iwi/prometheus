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
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void deterministicSupportCannotAcquireAnUnsupportedPrefaceEvenIfTheVerifierWouldApprove(boolean unchanged) {
        String required = "The task is stopped.";
        var literal = new LiveSpeechRequest(request.id(), owner, 0, request.created(), request.expiresAt(), required,
                request.releaseBy(), null, null, false, true, required);
        when(policy.liveSpeechRequest(owner)).thenReturn(literal);
        if (!unchanged) {
            speech.setPayload("{\"speech\":\"Recognition looks good. The task is stopped.\"}");
            segment = new LiveTranscriptSegment(segmentId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT",
                    "Recognition looks good. The task is stopped.", 0L, 1000L, now.minusSeconds(4), "Coordination");
            segment.receipts(List.of("receipt")); segment.finish("COMPLETE", null, null);
            when(segments.findById(segmentId)).thenReturn(Optional.of(segment));
            when(policy.liveSpeechCorrection(literal, segmentId.toString(), now)).thenReturn(UUID.randomUUID());
        }
        assertEquals(unchanged ? "APPROVED" : "CORRECTING", approve().status());
        assertEquals(unchanged, release());
        verifyNoInteractions(gateway);
        assertTrue(ConversationProjection.isUnplayed(speech), "Approval/release still do not prove playback");
    }
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
        if (!verdict.equals("AMBIGUOUS")) verify(policy, never()).liveSpeechCorrection(any(), any(), any());
    }
    @Test void validRejectionReservesCorrectionOnlyWhileTheRequestAndInputAreCurrent() {
        when(gateway.infer(any())).thenReturn(verdict("AMBIGUOUS"));
        when(policy.liveSpeechCorrection(eq(request), eq(segmentId.toString()), eq(now))).thenReturn(UUID.randomUUID());
        assertEquals("CORRECTING", approve().status()); assertFalse(release());
        assertEquals("CORRECTING", approve().status()); verify(gateway, times(1)).infer(any());
        verify(policy).liveSpeechCorrection(request, segmentId.toString(), now);
        window.interrupt(now); assertEquals("DISCARDED", approve().status());
        verify(policy, times(1)).liveSpeechCorrection(any(), any(), any());
    }
    @Test void ordinaryCorrectionRejectsPreReservationCandidatesAndPersistedRelease() {
        var corrected = new LiveSpeechRequest(request.id(), owner, 0, request.created(), request.expiresAt(), request.facts(),
                request.releaseBy(), UUID.randomUUID(), now.minusSeconds(1), false);
        when(policy.liveSpeechRequest(owner)).thenReturn(corrected);
        window.input("old-input", request.created().minusSeconds(1));
        assertEquals("DISCARDED", approve().status()); verifyNoInteractions(gateway);
        when(policy.liveSpeechRequest(owner)).thenReturn(new LiveSpeechRequest(request.id(), owner, 0, request.created(),
                request.expiresAt(), request.facts(), request.releaseBy(), null, null, true));
        assertEquals("DISCARDED", approve().status()); verifyNoInteractions(gateway);
    }
    @Test void earlyDraftCannotSpendTheCheckOrCorrectionBeforeConfirmedSpeech() {
        when(policy.liveSpeechRequest(owner)).thenReturn(new LiveSpeechRequest(request.id(), owner, 0,
                now.minusSeconds(3), request.expiresAt(), request.facts(), request.releaseBy(), null, null, false, true));
        window.input("current-input", now.minusSeconds(6));
        assertEquals("DISCARDED", approve().status()); verifyNoInteractions(gateway);
        verify(policy, never()).liveSpeechCorrection(any(), any(), any());
        UUID laterId = UUID.randomUUID();
        var later = new LiveTranscriptSegment(laterId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT",
                "The task is stopped.", 2000L, 3000L, now.minusSeconds(2), "Coordination");
        later.receipts(List.of("later-receipt")); later.finish("COMPLETE", null, null);
        when(segments.findById(laterId)).thenReturn(Optional.of(later));
        var laterSpeech = new Event(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, Event.KIND_RESPONSE, speech.getPayload());
        laterSpeech.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                laterId.toString(), SpeechProvenance.Association.NONE, List.of(), true, false));
        agent.getEventHistory().appendEvent(laterSpeech);
        when(gateway.infer(any())).thenReturn(verdict("MATCH").replace(segmentId.toString(), laterId.toString()));
        assertEquals("APPROVED", service.approve(id, owner, window, laterId.toString()).status());
        verify(gateway, times(1)).infer(any());
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
    @ParameterizedTest @ValueSource(strings = {"after", "before", "unknown", "later_input", "interrupted", "task", "earlier_ms"})
    void receiptOrderResolvesOnlySameMillisecondOrdinaryDrafts(String boundary) {
        Instant inputAt = request.created().minusSeconds(1).plusNanos(500_000);
        Instant started = Instant.ofEpochMilli(inputAt.toEpochMilli() - (boundary.equals("earlier_ms") ? 1 : 0));
        segment = new LiveTranscriptSegment(segmentId, agent, owner.sessionId(), owner.epoch(), "ASSISTANT", "The task is stopped.",
                0L, 1000L, started, "Coordination");
        segment.receipts(List.of("receipt")); segment.finish("COMPLETE", null, null);
        when(segments.findById(segmentId)).thenReturn(Optional.of(segment));
        if (boundary.equals("before")) window.output("receipt");
        window.input("current-input", inputAt);
        if (!boundary.equals("unknown")) window.output("receipt"); // A duplicate retains its original order.
        if (boundary.equals("later_input")) window.input("later-input", inputAt.plusNanos(1));
        if (boundary.equals("interrupted")) window.interrupt(now);
        if (boundary.equals("task")) TaskResponse.expect(storage, owner, requestId, request.facts(), request.created());
        assertEquals(boundary.equals("after") ? "APPROVED" : "DISCARDED", approve().status());
        if (!boundary.equals("after")) verifyNoInteractions(gateway);
    }
    void rejectedTask() {
        storage.put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        storage.put(TaskMemory.PHASE, new JsonPrimitive("RUNNING")); storage.put(TaskMemory.ACTIONS, new JsonPrimitive("1"));
        TaskResponse.expect(storage, owner, requestId, request.facts(), request.created());
        var v = storage.get(TaskResponse.KEY).getAsJsonObject();
        v.addProperty("status", "REJECTED"); v.addProperty("segment", segmentId.toString()); storage.put(TaskResponse.KEY, v);
        when(policy.liveUnheardResponseCorrection()).thenReturn(true);
    }
    @Test void concurrentCorrectionRequestsReserveOnlyOnePersistedAttempt() throws Exception {
        rejectedTask();
        var first = java.util.concurrent.CompletableFuture.supplyAsync(this::approve);
        var second = java.util.concurrent.CompletableFuture.supplyAsync(this::approve);
        var results = List.of(first.get(2, java.util.concurrent.TimeUnit.SECONDS), second.get(2, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, results.stream().filter(r -> r.status().equals("CORRECTING")).count());
        var correction = TaskResponse.correctionId(storage); assertNotNull(correction);
        assertNotEquals("CORRECTING", approve().status()); assertEquals(correction, TaskResponse.correctionId(storage));
        assertEquals(1, storage.get(TaskMemory.ACTIONS).getAsInt()); assertFalse(release()); verifyNoInteractions(gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"interrupt", "closed", "late", "owner", "disabled", "released"})
    void rejectedDraftCannotCorrectAfterAuthorityOrDeliveryChanges(String boundary) {
        rejectedTask();
        switch (boundary) {
            case "interrupt" -> window.interrupt(now);
            case "closed" -> window.close();
            case "late" -> when(clock.instant()).thenReturn(request.created().plusSeconds(36));
            case "owner" -> when(ownership.current(agent)).thenReturn(null);
            case "disabled" -> when(policy.liveUnheardResponseCorrection()).thenReturn(false);
            case "released" -> { var v = storage.get(TaskResponse.KEY).getAsJsonObject(); v.addProperty("released", true); storage.put(TaskResponse.KEY, v); }
        }
        assertNotEquals("CORRECTING", approve().status()); assertNull(TaskResponse.correctionId(storage));
        assertFalse(release()); verifyNoInteractions(gateway);
    }
    @Test void releaseSurvivesLossOfTheInMemoryWindowWithoutGrantingAnotherPlayback() {
        rejectedTask();
        var v = storage.get(TaskResponse.KEY).getAsJsonObject(); v.addProperty("status", "CAPTURED"); storage.put(TaskResponse.KEY, v);
        assertEquals("APPROVED", approve().status()); assertTrue(release()); assertTrue(TaskResponse.released(storage));
        var freshWindow = new LiveSpeechApprovalService.Window();
        assertEquals("DISCARDED", service.approve(id, owner, freshWindow, segmentId.toString()).status());
        assertFalse(service.release(id, owner, freshWindow, requestId, segmentId.toString()));
        assertFalse(service.played(id, owner, freshWindow, requestId, segmentId.toString()));
        assertTrue(ConversationProjection.isUnplayed(speech)); assertTrue(played());
    }
}
