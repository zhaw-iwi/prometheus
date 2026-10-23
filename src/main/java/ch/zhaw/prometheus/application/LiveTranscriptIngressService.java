package ch.zhaw.prometheus.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.ExternalVoicePolicy;
import ch.zhaw.prometheus.model.policy.LiveSpeech;
import ch.zhaw.prometheus.repositories.*;
import jakarta.persistence.EntityManager;

/** Durable receipt/segment admission. Only complete, unambiguous user input reaches acknowledgement. */
@Service
public class LiveTranscriptIngressService {
    public record LedgerChanged(UUID agentId, UUID sessionId) {}
    private org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired
    void events(org.springframework.context.ApplicationEventPublisher events) { this.events = events; }
    private void ledgerChanged(UUID agentId, UUID sessionId) {
        if (events != null) AfterCommit.run(() -> events.publishEvent(new LedgerChanged(agentId, sessionId)));
    }
    public record Outcome(UUID segmentId, String speaker, String text, String status, String reason, UUID eventId,
            Long startMs, Long endMs, List<String> receiptIds) {
        public Outcome { receiptIds = List.copyOf(receiptIds); }
        public Outcome(UUID segmentId, String speaker, String text, String status, String reason, UUID eventId, Long startMs, Long endMs) {
            this(segmentId, speaker, text, status, reason, eventId, startMs, endMs, List.of());
        }
    }
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final ExternalSpeechOwnership ownership;
    private final ExternalSpeechRecordingService recording;
    private final LiveTranscriptReceiptRepository receipts;
    private final LiveTranscriptSegmentRepository segments;
    private final TransactionTemplate transaction;
    private final TransactionTemplate receiptBoundary;
    private final LiveTranscriptReceiptWriter receiptWriter;
    private final EntityManager entityManager;
    private record Waiting(UUID agent, ExternalSpeech owner, UUID segment, List<String> state, Instant deadline) {}
    // Only never-admitted replies may wait. This map never replays an uncertain action after restart.
    private final java.util.concurrent.ConcurrentHashMap<ExternalSpeech, Waiting> waiting = new java.util.concurrent.ConcurrentHashMap<>();
    public LiveTranscriptIngressService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            ExternalSpeechRecordingService recording, LiveTranscriptReceiptRepository receipts,
            LiveTranscriptSegmentRepository segments, PlatformTransactionManager transactions, EntityManager entityManager,
            LiveTranscriptReceiptWriter receiptWriter) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.recording = recording;
        this.receipts = receipts; this.segments = segments; this.transaction = new TransactionTemplate(transactions);
        this.entityManager = entityManager;
        this.receiptWriter = receiptWriter;
        this.receiptBoundary = new TransactionTemplate(transactions);
        this.receiptBoundary.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean receipt(UUID id, ExternalSpeech owner, Fragment fragment) {
        return turns.serialized(id, () -> Boolean.TRUE.equals(receiptBoundary.execute(status -> {
            // Local ownership is only a fence: the insert checks scope AND epoch in
            // the database on every receipt, without caching authorization. Suspend
            // outer transactions so a later turn rollback cannot lose a receipt.
            var scope = ownership.receiptScope(id, owner);
            if (scope == null) return false;
            var result = receiptWriter.insert(id, owner, scope.accessCodeId(), fragment);
            if (result == LiveTranscriptReceiptWriter.Result.OBSOLETE) ownership.release(id, owner.sessionId());
            return result == LiveTranscriptReceiptWriter.Result.INSERTED;
        })));
    }
    public Optional<Outcome> commit(UUID id, ExternalSpeech owner, Segment segment, List<String> observedStatePath) {
        return turns.activity(id, "live_segment", segment.speaker() == Speaker.USER, () -> {
            ch.zhaw.prometheus.logging.ActivityTrace.bind(owner.epoch(), owner.sessionId(), segment.id());
        return turns.serialized(id, () -> {
            boolean[] claimed = {false};
            Optional<Outcome> previous = transaction.execute(status -> {
            var existing = segments.findById(segment.id());
            if (existing.isPresent()) {
                var value = existing.get();
                return owner.sessionId().equals(value.getSessionId()) && owner.epoch().equals(value.getEpoch()) ? Optional.of(view(value)) : Optional.empty();
            }
            // Claim needs current scope/epoch and durable receipts, not the state/history graph.
            if (!currentIdentity(id, owner)) return Optional.empty();
            var receiptIds = segment.fragments().stream().map(Fragment::eventId).toList();
            if (receiptIds.isEmpty() || receiptIds.size() > LiveTranscriptSegmenter.MAX_FRAGMENTS
                    || new java.util.HashSet<>(receiptIds).size() != receiptIds.size())
                throw new IllegalArgumentException("Invalid segment receipt identities");
            List<LiveTranscriptReceipt> sources = receipts.findAllBySessionIdAndProviderEventIdIn(owner.sessionId(), receiptIds);
            if (sources.size() != receiptIds.size()) throw new IllegalStateException("Transcript receipt is not durable");
            for (LiveTranscriptReceipt source : sources) {
                if (!owner.epoch().equals(source.getEpoch()) || !segment.speaker().name().equals(source.getSpeaker()))
                    throw new IllegalArgumentException("Receipt belongs to another capture");
                if (source.getSegmentId() != null && !source.getSegmentId().equals(segment.id()))
                    throw new IllegalStateException("Transcript receipt was already consumed");
            }
            String text = segment.text().trim();
            var persisted = new LiveTranscriptSegment(segment.id(), agents.getReferenceById(id), owner.sessionId(), owner.epoch(), segment.speaker().name(),
                    text, segment.startMs(), segment.endMs(), Instant.ofEpochMilli(segment.firstReceivedMs()), String.join(" / ", observedStatePath));
            persisted.receipts(receiptIds); // Preserve provider order, independent of bulk-read order.
            entityManager.persist(persisted);
            entityManager.flush(); // Claim identity before any acknowledgement/action.
            var unclaimed = sources.stream().filter(source -> source.getSegmentId() == null)
                    .map(LiveTranscriptReceipt::getProviderEventId).toList();
            if (!unclaimed.isEmpty() && receipts.claimForSegment(owner.sessionId(), owner.epoch(),
                    segment.speaker().name(), unclaimed, segment.id()) != unclaimed.size())
                throw new IllegalStateException("Transcript receipt was already consumed");
            ledgerChanged(id, owner.sessionId());
            claimed[0] = true;
            return Optional.of(view(persisted));
            });
            if (!claimed[0]) return previous;
            // The durable PENDING claim survives a failed transaction/crash. Never automatically replay an uncertain action.
            try { return transaction.execute(status -> {
            var persisted = segments.findById(segment.id()).orElseThrow();
            Agent agent = current(id, owner);
            if (agent == null) {
                persisted.finish("OBSOLETE", "speech_owner_changed", null);
                segments.save(persisted); ledgerChanged(id, owner.sessionId()); return Optional.of(view(persisted));
            }
            String text = persisted.getTranscript();
            if (segment.closure() == Closure.LATE || text.isBlank()) {
                persisted.finish("LATE", segment.reason(), null);
            } else if (segment.speaker() == Speaker.ASSISTANT) {
                var candidates = agent.getEventHistory().toList().stream().filter(ConversationProjection::isIntent)
                        .filter(event -> owner.sessionId().equals(event.speechProvenance().sessionId()))
                        .map(Event::getId).filter(java.util.Objects::nonNull).toList();
                candidates = candidates.subList(Math.max(0, candidates.size() - 64), candidates.size());
                var result = recording.record(id, owner, segment.id().toString(), text, candidates,
                        candidates.isEmpty() ? SpeechProvenance.Association.NONE : SpeechProvenance.Association.AMBIGUOUS,
                        segment.closure() == Closure.COMPLETE, Instant.ofEpochMilli(segment.firstReceivedMs()));
                persisted.finish(segment.closure().name(), segment.reason(), result.map(Event::getId).orElse(null));
                var policy = external(agent);
                if (policy != null) policy.liveAssistantSpeech(speech(persisted, segment.closure() == Closure.COMPLETE), Instant.now());
                agents.save(agent);
                AfterCommit.run(() -> resolveWaiting(owner, false));
            } else if (!ownership.acceptingInput(id, owner)) {
                persisted.finish("INCOMPLETE", "input_stopped_before_admission", null);
            } else if (segment.closure() != Closure.COMPLETE) {
                persisted.finish("INCOMPLETE", segment.reason(), null);
            } else {
                supersedeWaiting(owner);
                var policy = external(agent);
                var admission = policy == null ? LiveSpeech.Admission.DEFAULT : policy.liveReplyAdmission(speech(persisted, true), Instant.now());
                if (admission == LiveSpeech.Admission.WAIT && waiting.size() < 128) {
                    persisted.finish("WAITING_CONTEXT", "replay_invitation_settling", null);
                    var pending = new Waiting(id, owner, segment.id(), List.copyOf(observedStatePath), persisted.getFirstReceivedAt().plusSeconds(5));
                    AfterCommit.run(() -> waiting.put(owner, pending));
                } else if (admission == LiveSpeech.Admission.REJECT || admission == LiveSpeech.Admission.WAIT
                        || admission == LiveSpeech.Admission.DEFAULT && ambiguousShortReply(agent, owner, segment, observedStatePath)) {
                    persisted.finish("CLARIFICATION", "short_reply_without_established_context", null);
                } else admit(id, owner, agent, persisted);
            }
            segments.saveAndFlush(persisted);
            ledgerChanged(id, owner.sessionId());
            return Optional.of(view(persisted));
            }); } catch (RuntimeException failure) {
                transaction.executeWithoutResult(status -> segments.findById(segment.id()).ifPresent(value -> {
                    value.finish("FAILED", "processing_failed_no_automatic_retry", null); segments.save(value);
                    ledgerChanged(id, owner.sessionId());
                }));
                throw failure;
            }
        });
        });
    }
    private static ExternalVoicePolicy external(Agent agent) {
        var leaf = ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
        return leaf.ownPolicy() instanceof ExternalVoicePolicy policy ? policy : null;
    }
    private static LiveSpeech speech(LiveTranscriptSegment segment, boolean complete) {
        return new LiveSpeech(segment.getId(), segment.getTranscript(), segment.getStartMs(), segment.getEndMs(), segment.getFirstReceivedAt(), complete);
    }
    private void admit(UUID id, ExternalSpeech owner, Agent agent, LiveTranscriptSegment persisted) {
        var policy = external(agent);
        if (policy != null) policy.liveUserSpeech(speech(persisted, true));
        int before = agent.getEventHistory().toList().size();
        var response = turns.acknowledge(id, new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION, persisted.getTranscript()), OutputProfile.FULL_PLAN);
        agents.flush();
        Agent fresh = agents.findById(id).orElseThrow();
        UUID eventId = fresh.getEventHistory().toList().stream().skip(before).filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType()))
                .map(Event::getId).findFirst().orElse(null);
        if (response.isEmpty()) throw new IllegalStateException("Agent disappeared during transcript ingress");
        persisted.finish("COMPLETE", "acknowledged", eventId);
        if (owner.equals(ownership.current(fresh)) && response.get().getResponseEvent() == null)
            turns.generate(id, List.of(), OutputProfile.FULL_PLAN);
    }
    private void supersedeWaiting(ExternalSpeech owner) {
        Waiting previous = waiting.get(owner);
        if (previous == null) return;
        segments.findById(previous.segment()).filter(value -> "WAITING_CONTEXT".equals(value.getStatus())).ifPresent(value -> {
            value.finish("OBSOLETE", "superseded_reply", null); segments.save(value); ledgerChanged(previous.agent(), owner.sessionId());
        });
        AfterCommit.run(() -> waiting.remove(owner, previous));
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
    public void expireWaiting() {
        Instant now = Instant.now();
        waiting.values().stream().filter(value -> !now.isBefore(value.deadline())).forEach(value -> resolveWaiting(value.owner(), true));
    }
    /** Close calls this after fencing input; a settling reply must never outlive its speech owner. */
    public void stopped(ExternalSpeech owner) { resolveWaiting(owner, true); }
    private void resolveWaiting(ExternalSpeech owner, boolean expired) {
        Waiting pending = waiting.get(owner);
        if (pending == null) return;
        try { turns.serialized(pending.agent(), () -> transaction.execute(status -> {
            if (waiting.get(owner) != pending) return null;
            var value = segments.findById(pending.segment()).orElse(null);
            if (value == null || !"WAITING_CONTEXT".equals(value.getStatus())) { waiting.remove(owner, pending); return null; }
            var agent = current(pending.agent(), owner);
            var policy = agent == null ? null : external(agent);
            var admission = policy == null ? LiveSpeech.Admission.REJECT : policy.liveReplyAdmission(speech(value, true), Instant.now());
            if (agent == null || !ownership.acceptingInput(pending.agent(), owner)
                    || admission != LiveSpeech.Admission.ADMIT && !agent.getCurrentState().getActiveStatePath().equals(pending.state()))
                value.finish("OBSOLETE", "reply_context_changed", null);
            else if (!expired && admission == LiveSpeech.Admission.WAIT) return null;
            else if (admission == LiveSpeech.Admission.ADMIT) {
                // Claim before acknowledgement in this transaction. Failed work is never retried.
                value.finish("PENDING", "admitting_settled_reply", null);
                admit(pending.agent(), owner, agent, value);
            } else value.finish("CLARIFICATION", "replay_invitation_unconfirmed", null);
            segments.saveAndFlush(value);
            ledgerChanged(pending.agent(), owner.sessionId());
            AfterCommit.run(() -> {
                waiting.remove(owner, pending);
                if (events != null) events.publishEvent(new LiveTranscriptCaptureService.Committed(pending.agent(), owner, view(value), pending.state()));
            });
            return null;
        })); } catch (RuntimeException failure) {
            // Do not let a recovery callback invalidate the assistant transcript which just committed.
            // A failed/uncertain admission is never put back in the in-memory queue.
            waiting.remove(owner, pending);
            try { turns.serialized(pending.agent(), () -> transaction.execute(status -> {
                segments.findById(pending.segment()).filter(value -> "WAITING_CONTEXT".equals(value.getStatus()) || "PENDING".equals(value.getStatus())).ifPresent(value -> {
                    value.finish("FAILED", "settled_reply_failed_no_automatic_retry", null); segments.saveAndFlush(value);
                    ledgerChanged(pending.agent(), owner.sessionId());
                });
                return null;
            })); } catch (RuntimeException unavailable) { /* Existing durable claim remains non-replayable. */ }
            org.slf4j.LoggerFactory.getLogger(LiveTranscriptIngressService.class).warn("Settled Live reply failed; no automatic retry");
        }
    }
    private boolean currentIdentity(UUID id, ExternalSpeech owner) {
        if (!ownership.isCurrent(id, owner)) return false;
        if (agents.existsByIdAndExecutionEpoch(id, owner.epoch())) return true;
        ownership.release(id, owner.sessionId());
        return false;
    }
    private Agent current(UUID id, ExternalSpeech owner) {
        Agent agent = agents.findById(id).orElse(null);
        return agent != null && owner.equals(ownership.current(agent)) ? agent : null;
    }
    private boolean ambiguousShortReply(Agent agent, ExternalSpeech owner, Segment segment, List<String> observedState) {
        var leaf = ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast();
        if (leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy policy
                && policy.liveNonMutatingAcknowledgement(segment.text())) return false;
        if (!ch.zhaw.prometheus.application.live.LiveShortReplyContext.bareReply(segment.text())) return false;
        var latestAnnouncement = agent.getEventHistory().toList().stream().filter(event -> ConversationProjection.isIntent(event)
                && owner.sessionId().equals(event.speechProvenance().sessionId())
                && ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(event.getPayload()).getSpeech() != null)
                .map(Event::getCreatedDate).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        boolean activityOwned = leaf.ownPolicy() instanceof ch.zhaw.prometheus.model.policy.ExternalVoicePolicy policy
                && policy.liveActivityContext() != null;
        if (!agent.getCurrentState().getActiveStatePath().equals(observedState)
                || !activityOwned && latestAnnouncement != null) return true;
        var spoken = segments.findTop20ByAgent_IdAndEpochAndSpeakerAndStatusOrderByRecordedAtDesc(agent.getId(), owner.epoch(), "ASSISTANT", "COMPLETE")
                .stream().filter(previous -> owner.sessionId().equals(previous.getSessionId()))
                .map(previous -> new ch.zhaw.prometheus.application.live.LiveShortReplyContext.Spoken(
                        previous.getStatePath(), previous.getEndMs(), previous.getFirstReceivedAt(), previous.getRecordedAt())).toList();
        return ch.zhaw.prometheus.application.live.LiveShortReplyContext.ambiguous(observedState,
                agent.getCurrentState().getActiveStatePath(), segment.startMs(), Instant.ofEpochMilli(segment.firstReceivedMs()),
                spoken, latestAnnouncement, activityOwned);
    }
    public List<Outcome> history(UUID agent, UUID session) {
        return segments.findTop100ByAgent_IdAndSessionIdOrderByRecordedAtDesc(agent, session).stream().map(LiveTranscriptIngressService::view).toList();
    }
    private static Outcome view(LiveTranscriptSegment value) {
        return new Outcome(value.getId(), value.getSpeaker(), value.getTranscript(), value.getStatus(), value.getReason(), value.getEventId(), value.getStartMs(), value.getEndMs(), value.receiptIds());
    }
}
