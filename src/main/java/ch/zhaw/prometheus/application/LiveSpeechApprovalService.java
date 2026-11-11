package ch.zhaw.prometheus.application;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;

/** Session-bounded release checks. Provider waits occur outside database transactions and agent locks. */
@Service
public class LiveSpeechApprovalService {
    public record Result(String status, UUID id, String segmentId, List<String> receiptIds, Instant expiresAt, String text) {
        public Result { receiptIds = List.copyOf(receiptIds); }
        static Result state(String status, String segment) { return new Result(status, null, segment, List.of(), null, null); }
    }
    public static final class Window {
        private final Map<UUID, Result> attempts = new LinkedHashMap<>();
        private final Set<String> inputReceipts = new HashSet<>();
        private Instant interrupted = Instant.EPOCH;
        private Instant lastInput = Instant.EPOCH;
        private boolean closed;
        public synchronized void interrupt(Instant at) { if (at.isAfter(interrupted)) interrupted = at; }
        public synchronized void input(String receipt, Instant at) {
            if (receipt == null || receipt.isBlank() || receipt.length() > 200 || inputReceipts.size() >= 4096) { close(); return; }
            if (inputReceipts.add(receipt)) { lastInput = at; interrupt(at); }
        }
        public synchronized void close() { closed = true; attempts.clear(); inputReceipts.clear(); }
        synchronized boolean current(Candidate candidate) {
            var request = candidate.request();
            if (closed || !request.created().isAfter(interrupted)) return false;
            // Existing task speech still must originate after its committed request.
            // An ordinary current-turn draft may be reviewed AFTER commitment, before
            // any playback. The last trusted input receipt prevents borrowing old speech.
            return candidate.started().isAfter(request.created()) || !candidate.task()
                    && lastInput.isAfter(Instant.EPOCH) && lastInput.equals(interrupted) && candidate.started().isAfter(interrupted);
        }
    }
    private record Candidate(LiveSpeechRequest request, String segment, String text, List<String> receipts, Instant started,
            boolean task, boolean matched, boolean rejected) {}
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final LiveTranscriptSegmentRepository segments;
    private final ExternalSpeechOwnership ownership;
    private final LanguageModelGateway gateway;
    private final AgentBehaviourBroadcaster behaviour;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Semaphore workers = new Semaphore(4);
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public LiveSpeechApprovalService(AgentApplicationService turns, AgentRepository agents, LiveTranscriptSegmentRepository segments,
            ExternalSpeechOwnership ownership, LanguageModelGateway gateway, PlatformTransactionManager transactions,
            AgentBehaviourBroadcaster behaviour, org.springframework.context.ApplicationEventPublisher events) {
        this(turns, agents, segments, ownership, gateway, transactions, behaviour, events, Clock.systemUTC());
    }
    LiveSpeechApprovalService(AgentApplicationService turns, AgentRepository agents, LiveTranscriptSegmentRepository segments,
            ExternalSpeechOwnership ownership, LanguageModelGateway gateway, PlatformTransactionManager transactions,
            AgentBehaviourBroadcaster behaviour, org.springframework.context.ApplicationEventPublisher events, Clock clock) {
        this.turns = turns; this.agents = agents; this.segments = segments; this.ownership = ownership; this.gateway = gateway;
        this.behaviour = behaviour; this.events = events; this.transaction = new TransactionTemplate(transactions); this.clock = clock;
    }
    public Result approve(UUID agentId, ExternalSpeech owner, Window window, String segmentId) {
        UUID segment = segmentId(segmentId);
        Candidate candidate = turns.serialized(agentId, () -> transaction.execute(status -> candidate(agentId, owner, segment)));
        if (candidate == null || !window.current(candidate) || !clock.instant().isBefore(candidate.request().expiresAt()))
            return Result.state("DISCARDED", segmentId);
        var request = candidate.request();
        if (candidate.rejected()) return Result.state("REJECTED", segmentId);
        if (candidate.task() && !candidate.matched()) return Result.state("PENDING", segmentId);
        synchronized (window) {
            Result previous = window.attempts.get(request.id());
            if (previous != null) return previous.segmentId().equals(segmentId) ? previous : Result.state("DISCARDED", segmentId);
            if (window.attempts.size() >= 128) return Result.state("REJECTED", segmentId);
            if (!workers.tryAcquire()) return Result.state("PENDING", segmentId);
            window.attempts.put(request.id(), Result.state("CHECKING", segmentId));
        }
        boolean match = candidate.matched();
        try {
            if (!match) {
                var check = new TaskResponse.Check(request.id(), owner.sessionId(), owner.epoch(), request.revision(),
                        segmentId, request.facts(), candidate.text(), request.expiresAt(), TaskResponse.Purpose.UNSPECIFIED, null);
                match = check.verdict(gateway.infer(check.request())) == TaskResponse.Verdict.MATCH;
            }
        } catch (RuntimeException failure) { match = false; }
        finally { workers.release(); }
        final boolean approved = match;
        return turns.serialized(agentId, () -> transaction.execute(status -> {
            Candidate fresh = candidate(agentId, owner, segment);
            synchronized (window) {
                boolean current = fresh != null && fresh.request().equals(request) && window.current(fresh)
                        && clock.instant().isBefore(request.expiresAt());
                Result result = current && approved ? new Result("APPROVED", request.id(), segmentId, candidate.receipts(),
                        request.created().plusSeconds(60), candidate.text()) : Result.state(current ? "REJECTED" : "DISCARDED", segmentId);
                if (!window.closed) window.attempts.put(request.id(), result);
                return result;
            }
        }));
    }
    /** Revalidate immediately before scheduling buffered audio, without another inference. */
    public boolean release(UUID id, ExternalSpeech owner, Window window, UUID approval, String segmentId) {
        return validate(id, owner, window, approval, segmentId, false);
    }
    /** Called only by the production player's observed completion callback. */
    public boolean played(UUID id, ExternalSpeech owner, Window window, UUID approval, String segmentId) {
        return validate(id, owner, window, approval, segmentId, true);
    }
    private boolean validate(UUID id, ExternalSpeech owner, Window window, UUID approval, String segmentId, boolean played) {
        UUID segment = segmentId(segmentId);
        return turns.serialized(id, () -> { synchronized (window) { return transaction.execute(status -> {
            Candidate candidate = candidate(id, owner, segment);
            if (candidate == null || !candidate.request().id().equals(approval) || !window.current(candidate)) return false;
            if (!played && !clock.instant().isBefore(candidate.request().releaseBy())) return false;
            Result result;
            synchronized (window) { result = window.attempts.get(approval); }
            if (result == null || !result.segmentId().equals(segmentId)
                    || !(played ? "RELEASED" : "APPROVED").equals(result.status())
                    || !clock.instant().isBefore(result.expiresAt())) return false;
            if (played) {
                Agent agent = agents.findById(id).orElseThrow();
                Event event = speech(agent, owner, segmentId);
                if (event == null || !ConversationProjection.isUnplayed(event)) return false;
                event.speechProvenance(event.speechProvenance().playback(true));
                var policy = (ExternalVoicePolicy) agent.getCurrentState().ownPolicy();
                if (policy instanceof TaskPolicy task) TaskResponse.played(task.storage(), approval, clock.instant());
                policy.liveSpeechCaptured(event, owner, candidate.started(), clock.instant(), List.of());
                agents.saveAndFlush(agent);
                AfterCommit.run(() -> {
                    behaviour.publish(id, event);
                    events.publishEvent(new AgentCommitted(id, owner.epoch()));
                    events.publishEvent(new LiveTranscriptIngressService.LedgerChanged(id, owner.sessionId()));
                });
            }
            synchronized (window) { window.attempts.put(approval, new Result(played ? "PLAYED" : "RELEASED", approval,
                    segmentId, result.receiptIds(), result.expiresAt(), result.text())); }
            return true;
        }); } });
    }
    private Candidate candidate(UUID id, ExternalSpeech owner, UUID segmentId) {
        Agent agent = agents.findById(id).orElse(null);
        if (agent == null || !owner.equals(ownership.current(agent)) || !ownership.taskWorkAllowed(id, owner)
                || !(agent.getCurrentState().ownPolicy() instanceof ExternalVoicePolicy policy) || !policy.liveSpeechApprovalRequired()) return null;
        var request = policy.liveSpeechRequest(owner);
        var segment = segments.findById(segmentId).orElse(null);
        if (request == null || segment == null || !id.equals(segment.getAgentId()) || !owner.sessionId().equals(segment.getSessionId())
                || !owner.epoch().equals(segment.getEpoch()) || !"ASSISTANT".equals(segment.getSpeaker()) || !"COMPLETE".equals(segment.getStatus())
                || segment.getFirstReceivedAt() == null
                || segment.getFirstReceivedAt().isAfter(clock.instant()) || segment.receiptIds().isEmpty() || segment.receiptIds().size() > 128) return null;
        Event event = speech(agent, owner, segmentId.toString());
        if (event == null || !event.speechProvenance().complete()) return null;
        boolean task = false, matched = false, rejected = false;
        if (policy instanceof TaskPolicy tp && tp.storage().containsKey(TaskResponse.KEY)) {
            var value = tp.storage().get(TaskResponse.KEY).getAsJsonObject();
            task = value.get("id").getAsString().equals(request.id().toString());
            if (task) {
                matched = TaskResponse.captured(tp.storage(), owner) && value.has("segment")
                        && value.get("segment").getAsString().equals(segmentId.toString())
                        && (!value.has("speechOffset") || value.get("speechOffset").getAsInt() == 0);
                rejected = "UNCERTAIN".equals(TaskResponse.status(tp.storage())) || "CAPTURED".equals(TaskResponse.status(tp.storage())) && !matched;
            }
        }
        return new Candidate(request, segmentId.toString(), segment.getTranscript(), segment.receiptIds(), segment.getFirstReceivedAt(), task, matched, rejected);
    }
    private static Event speech(Agent agent, ExternalSpeech owner, String segment) {
        return agent.getEventHistory().toList().stream().filter(e -> {
            var p = e.speechProvenance();
            return p != null && p.origin() == SpeechProvenance.Origin.NATIVE && owner.sessionId().equals(p.sessionId())
                    && owner.epoch().equals(p.epoch()) && segment.equals(p.segmentId());
        }).findFirst().orElse(null);
    }
    private static UUID segmentId(String value) {
        try { return UUID.fromString(value); } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid segment identity"); }
    }
}
