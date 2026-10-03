package ch.zhaw.prometheus.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.repositories.*;

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
    public LiveTranscriptIngressService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            ExternalSpeechRecordingService recording, LiveTranscriptReceiptRepository receipts,
            LiveTranscriptSegmentRepository segments, PlatformTransactionManager transactions) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.recording = recording;
        this.receipts = receipts; this.segments = segments; this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean receipt(UUID id, ExternalSpeech owner, Fragment fragment) {
        return turns.serialized(id, () -> Boolean.TRUE.equals(transaction.execute(status -> {
            // Every delta is durable, but it does not need the agent's state/history graph.
            // Keep live scope and persisted epoch checks under the same reset/admission lock.
            if (!ownership.isCurrent(id, owner)) return false;
            if (!agents.existsByIdAndExecutionEpoch(id, owner.epoch())) {
                ownership.release(id, owner.sessionId()); return false;
            }
            if (receipts.findBySessionIdAndProviderEventId(owner.sessionId(), fragment.eventId()).isPresent()) return false;
            receipts.saveAndFlush(new LiveTranscriptReceipt(agents.getReferenceById(id), owner.sessionId(), owner.epoch(), fragment.eventId(),
                    fragment.speaker().name(), fragment.startMs(), fragment.endMs(), Instant.ofEpochMilli(fragment.receivedMs()),
                    fragment.sequence(), fragment.text()));
            return true;
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
            Agent agent = current(id, owner); if (agent == null) return Optional.empty();
            List<LiveTranscriptReceipt> sources = segment.fragments().stream().map(fragment -> receipts
                    .findBySessionIdAndProviderEventId(owner.sessionId(), fragment.eventId())
                    .orElseThrow(() -> new IllegalStateException("Transcript receipt is not durable"))).toList();
            for (LiveTranscriptReceipt source : sources) {
                if (!owner.epoch().equals(source.getEpoch()) || !segment.speaker().name().equals(source.getSpeaker()))
                    throw new IllegalArgumentException("Receipt belongs to another capture");
                if (source.getSegmentId() != null && !source.getSegmentId().equals(segment.id()))
                    throw new IllegalStateException("Transcript receipt was already consumed");
            }
            String text = segment.text().trim();
            var persisted = new LiveTranscriptSegment(segment.id(), agent, owner.sessionId(), owner.epoch(), segment.speaker().name(),
                    text, segment.startMs(), segment.endMs(), Instant.ofEpochMilli(segment.firstReceivedMs()), String.join(" / ", observedStatePath));
            persisted.receipts(segment.fragments().stream().map(Fragment::eventId).toList());
            segments.saveAndFlush(persisted); // Claim identity before any acknowledgement/action.
            sources.forEach(receipt -> receipt.assign(segment.id())); receipts.saveAll(sources);
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
                        segment.closure() == Closure.COMPLETE);
                persisted.finish(segment.closure().name(), segment.reason(), result.map(Event::getId).orElse(null));
            } else if (!ownership.acceptingInput(id, owner)) {
                persisted.finish("INCOMPLETE", "input_stopped_before_admission", null);
            } else if (segment.closure() != Closure.COMPLETE) {
                persisted.finish("INCOMPLETE", segment.reason(), null);
            } else if (ambiguousShortReply(agent, owner, segment, observedStatePath)) {
                persisted.finish("CLARIFICATION", "short_reply_without_established_context", null);
            } else {
                int before = agent.getEventHistory().toList().size();
                var response = turns.acknowledge(id, new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION, text), OutputProfile.FULL_PLAN);
                agents.flush();
                Agent fresh = agents.findById(id).orElseThrow();
                List<Event> appended = fresh.getEventHistory().toList();
                UUID eventId = appended.stream().skip(before).filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType()))
                        .map(Event::getId).findFirst().orElse(null);
                if (response.isEmpty()) throw new IllegalStateException("Agent disappeared during transcript ingress");
                persisted.finish("COMPLETE", "acknowledged", eventId);
                // Ordinary speech remains externally owned; this call can only supply configured non-speech behaviour.
                if (owner.equals(ownership.current(fresh)) && response.get().getResponseEvent() == null)
                    turns.generate(id, List.of(), OutputProfile.FULL_PLAN);
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
    private Agent current(UUID id, ExternalSpeech owner) {
        Agent agent = agents.findById(id).orElse(null);
        return agent != null && owner.equals(ownership.current(agent)) ? agent : null;
    }
    private boolean ambiguousShortReply(Agent agent, ExternalSpeech owner, Segment segment, List<String> observedState) {
        String text = segment.text().trim().toLowerCase(Locale.ROOT).replaceAll("[.!?,]", "");
        if (!List.of("yes", "no", "yeah", "yep", "ok", "okay", "ready", "again", "ja", "nein", "bereit", "weiter", "نعم", "لا").contains(text)) return false;
        if (!agent.getCurrentState().getActiveStatePath().equals(observedState)) return true;
        // A narration ACK/time proximity cannot prove which backend question was spoken.
        if (agent.getEventHistory().toList().stream().anyMatch(event -> ConversationProjection.isIntent(event)
                && owner.sessionId().equals(event.speechProvenance().sessionId())
                && ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(event.getPayload()).getSpeech() != null)) return true;
        return segments.findTop20ByAgent_IdAndEpochAndSpeakerAndStatusOrderByRecordedAtDesc(agent.getId(), owner.epoch(), "ASSISTANT", "COMPLETE")
                .stream().noneMatch(previous -> previous.getStatePath().equals(String.join(" / ", observedState))
                        && previous.getEndMs() != null && segment.startMs() != null && previous.getEndMs() < segment.startMs()
                        && previous.getRecordedAt().isBefore(Instant.ofEpochMilli(segment.firstReceivedMs())));
    }
    public List<Outcome> history(UUID agent, UUID session) {
        return segments.findTop100ByAgent_IdAndSessionIdOrderByRecordedAtDesc(agent, session).stream().map(LiveTranscriptIngressService::view).toList();
    }
    private static Outcome view(LiveTranscriptSegment value) {
        return new Outcome(value.getId(), value.getSpeaker(), value.getTranscript(), value.getStatus(), value.getReason(), value.getEventId(), value.getStartMs(), value.getEndMs(), value.receiptIds());
    }
}
