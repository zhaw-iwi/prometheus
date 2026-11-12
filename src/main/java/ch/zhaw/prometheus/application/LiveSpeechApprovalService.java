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
        final Map<UUID, LiveCommittedSpeechService.Delivery> synthesized = new LinkedHashMap<>();
        private final Set<String> inputReceipts = new HashSet<>();
        private final Map<String, Long> outputOrder = new HashMap<>();
        private long receiptOrder, lastInputOrder;
        private Instant interrupted = Instant.EPOCH;
        private Instant lastInput = Instant.EPOCH;
        private boolean closed;
        public synchronized void interrupt(Instant at) { if (at.isAfter(interrupted)) interrupted = at; }
        public synchronized void input(String receipt, Instant at) {
            if (receipt == null || receipt.isBlank() || receipt.length() > 200 || inputReceipts.size() >= 4096) { close(); return; }
            if (inputReceipts.add(receipt)) { lastInput = at; lastInputOrder = ++receiptOrder; interrupt(at); }
        }
        /** Sideband callback order disambiguates timestamps rounded by durable capture. */
        public synchronized void output(String receipt) {
            if (receipt == null || receipt.isBlank() || receipt.length() > 200 || outputOrder.size() >= 8192) { close(); return; }
            outputOrder.putIfAbsent(receipt, ++receiptOrder);
        }
        public synchronized void close() { closed = true; attempts.clear(); synthesized.clear(); inputReceipts.clear(); outputOrder.clear(); }
        synchronized boolean current(LiveSpeechRequest request) { return !closed && request.created().isAfter(interrupted); }
        synchronized boolean current(Candidate candidate) {
            var request = candidate.request();
            if (closed || !request.created().isAfter(interrupted)) return false;
            if (request.correctionAt() != null && !candidate.started().isAfter(request.correctionAt())) return false;
            // Existing task speech still must originate after its committed request.
            // An ordinary current-turn draft may be reviewed AFTER commitment, before
            // any playback. The last trusted input receipt prevents borrowing old speech.
            return candidate.started().isAfter(request.created()) || !candidate.task() && !request.postCommitOnly()
                    && lastInput.isAfter(Instant.EPOCH) && lastInput.equals(interrupted)
                    && (candidate.started().isAfter(interrupted)
                        || candidate.started().toEpochMilli() == interrupted.toEpochMilli()
                            && candidate.receipts().stream().allMatch(id -> outputOrder.getOrDefault(id, 0L) > lastInputOrder));
        }
    }
    private record Candidate(LiveSpeechRequest request, String segment, String text, List<String> receipts, Instant started,
            boolean task, boolean matched, boolean rejected, boolean released) {}
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
        if (candidate.released()) return Result.state("DISCARDED", segmentId);
        if (candidate.rejected()) return correct(agentId, owner, window, candidate, segment);
        if (candidate.task() && !candidate.matched()) return Result.state("PENDING", segmentId);
        synchronized (window) {
            Result previous = window.attempts.get(request.id());
            if (previous != null && !("CORRECTING".equals(previous.status()) && request.correctionId() != null
                    && !previous.segmentId().equals(segmentId)))
                return previous.segmentId().equals(segmentId) ? previous : Result.state("DISCARDED", segmentId);
            if (previous == null && window.attempts.size() >= 128) return Result.state("REJECTED", segmentId);
            if (!workers.tryAcquire()) return Result.state("PENDING", segmentId);
            window.attempts.put(request.id(), Result.state("CHECKING", segmentId));
        }
        boolean match = candidate.matched(), rejectedMeaning = false;
        try {
            if (!match) {
                var check = new TaskResponse.Check(request.id(), owner.sessionId(), owner.epoch(), request.revision(),
                        segmentId, request.facts(), candidate.text(), request.expiresAt(), TaskResponse.Purpose.UNSPECIFIED, request.exactSpeech());
                var verdict = request.exactSpeech() == null ? check.verifiedVerdict(gateway.infer(check.request()))
                        : Optional.of(check.exactlyMatches() ? TaskResponse.Verdict.MATCH : TaskResponse.Verdict.AMBIGUOUS);
                match = verdict.orElse(null) == TaskResponse.Verdict.MATCH;
                rejectedMeaning = verdict.orElse(null) == TaskResponse.Verdict.AMBIGUOUS;
            }
        } catch (RuntimeException failure) { match = false; }
        finally { workers.release(); }
        final boolean approved = match;
        final boolean correctionEligible = rejectedMeaning;
        return turns.serialized(agentId, () -> transaction.execute(status -> {
            Candidate fresh = candidate(agentId, owner, segment);
            synchronized (window) {
                boolean current = fresh != null && fresh.request().equals(request) && window.current(fresh)
                        && clock.instant().isBefore(request.expiresAt());
                if (current && correctionEligible) {
                    var agent = agents.findById(agentId).orElseThrow();
                    var policy = (ExternalVoicePolicy) agent.getCurrentState().ownPolicy();
                    UUID correction = policy.liveSpeechCorrection(request, segmentId, clock.instant());
                    if (correction != null) {
                        agents.saveAndFlush(agent);
                        var correcting = Result.state("CORRECTING", segmentId); window.attempts.put(request.id(), correcting);
                        AfterCommit.run(() -> events.publishEvent(new AgentCommitted(agentId, owner.epoch(), correction, true, request.id())));
                        return correcting;
                    }
                }
                Result result = current && approved ? new Result("APPROVED", request.id(), segmentId, candidate.receipts(),
                        request.created().plusSeconds(60), candidate.text()) : Result.state(current ? "REJECTED" : "DISCARDED", segmentId);
                if (!window.closed) window.attempts.put(request.id(), result);
                return result;
            }
        }));
    }
    private Result correct(UUID id, ExternalSpeech owner, Window window, Candidate rejected, UUID segment) {
        return turns.serialized(id, () -> { synchronized (window) { return transaction.execute(status -> {
            Candidate fresh = candidate(id, owner, segment);
            if (fresh == null || !fresh.request().equals(rejected.request()) || !window.current(fresh))
                return Result.state("DISCARDED", rejected.segment());
            var agent = agents.findById(id).orElseThrow();
            if (!fresh.task() || !(agent.getCurrentState().ownPolicy() instanceof TaskPolicy policy)
                    || !policy.liveUnheardResponseCorrection() || window.attempts.containsKey(fresh.request().id())
                    || !TaskResponse.correct(policy.storage(), owner, fresh.segment(), clock.instant()))
                return Result.state("REJECTED", rejected.segment());
            agents.saveAndFlush(agent);
            UUID correction = TaskResponse.correctionId(policy.storage());
            AfterCommit.run(() -> events.publishEvent(new AgentCommitted(id, owner.epoch(), correction, true, fresh.request().id())));
            return Result.state("CORRECTING", rejected.segment());
        }); } });
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
            if (!played && candidate.task()) {
                Agent agent = agents.findById(id).orElseThrow();
                var policy = (TaskPolicy) agent.getCurrentState().ownPolicy();
                if (!TaskResponse.release(policy.storage(), owner, approval, segmentId)) return false;
                agents.saveAndFlush(agent);
            }
            else if (!played) {
                Agent agent = agents.findById(id).orElseThrow();
                if (((ExternalVoicePolicy) agent.getCurrentState().ownPolicy()).liveSpeechReleased(candidate.request(), segmentId))
                    agents.saveAndFlush(agent);
            }
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
        boolean task = false, matched = false, rejected = false, released = request.released();
        if (policy instanceof TaskPolicy tp && tp.storage().containsKey(TaskResponse.KEY)) {
            var value = tp.storage().get(TaskResponse.KEY).getAsJsonObject();
            task = value.get("id").getAsString().equals(request.id().toString());
            if (task) {
                matched = TaskResponse.captured(tp.storage(), owner) && value.has("segment")
                        && value.get("segment").getAsString().equals(segmentId.toString())
                        && (!value.has("speechOffset") || value.get("speechOffset").getAsInt() == 0);
                rejected = Set.of("REJECTED", "UNCERTAIN").contains(TaskResponse.status(tp.storage())) || "CAPTURED".equals(TaskResponse.status(tp.storage())) && !matched;
                released = TaskResponse.released(tp.storage());
            }
        }
        return new Candidate(request, segmentId.toString(), segment.getTranscript(), segment.receiptIds(), segment.getFirstReceivedAt(), task, matched, rejected, released);
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
