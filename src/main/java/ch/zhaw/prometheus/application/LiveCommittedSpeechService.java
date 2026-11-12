package ch.zhaw.prometheus.application;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

/** One scoped synthesis of committed words, followed by separate release and observed playback. */
@Service
public class LiveCommittedSpeechService {
    static final int RATE = 24000, MAX_BYTES = RATE * 2 * 45;
    public record Result(String status, UUID id, String segmentId, List<String> receiptIds,
            Instant expiresAt, String text, String pcm, int sampleRate) {
        static Result state(String status) { return new Result(status, null, null, List.of(), null, null, null, RATE); }
    }
    record Delivery(LiveCommittedSpeech speech, Instant started, String status) {}
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final ExternalSpeechOwnership ownership;
    private final SpeechSynthesisGateway gateway;
    private final AgentBehaviourBroadcaster behaviour;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Semaphore workers = new Semaphore(4);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @org.springframework.beans.factory.annotation.Autowired
    public LiveCommittedSpeechService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            SpeechSynthesisGateway gateway, PlatformTransactionManager transactions, AgentBehaviourBroadcaster behaviour,
            org.springframework.context.ApplicationEventPublisher events) {
        this(turns, agents, ownership, gateway, transactions, behaviour, events, Clock.systemUTC());
    }
    LiveCommittedSpeechService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            SpeechSynthesisGateway gateway, PlatformTransactionManager transactions, AgentBehaviourBroadcaster behaviour,
            org.springframework.context.ApplicationEventPublisher events, Clock clock) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.gateway = gateway;
        this.behaviour = behaviour; this.events = events; this.clock = clock; transaction = new TransactionTemplate(transactions);
    }
    @jakarta.annotation.PreDestroy public void close() { executor.shutdownNow(); }

    public Result synthesize(UUID id, ExternalSpeech owner, LiveSpeechApprovalService.Window window, UUID response) {
        if (!workers.tryAcquire()) return Result.state("PENDING");
        Delivery claim;
        try {
            claim = turns.serialized(id, () -> { synchronized (window) { return transaction.execute(status -> {
                Agent agent = current(id, owner); if (agent == null) return null;
                var policy = (ExternalVoicePolicy) agent.getCurrentState().ownPolicy();
                var speech = policy.liveCommittedSpeech(owner);
                if (speech == null || !speech.request().id().equals(response) || speech.synthesisId() != null
                        || speech.request().released() || !window.current(speech.request())
                        || !clock.instant().isBefore(speech.request().expiresAt()) || window.synthesized.size() >= 128) return null;
                UUID synthesis = UUID.randomUUID();
                if (!policy.liveSpeechSynthesisClaimed(speech, synthesis)) return null;
                var claimed = policy.liveCommittedSpeech(owner);
                var delivery = new Delivery(claimed, clock.instant(), "SYNTHESIZING");
                agents.saveAndFlush(agent); window.synthesized.put(response, delivery); return delivery;
            }); } });
        } catch (RuntimeException failure) { workers.release(); throw failure; }
        if (claim == null) { workers.release(); return Result.state("DISCARDED"); }
        var open = new AtomicReference<SpeechAudio>();
        Future<byte[]> work = executor.submit(() -> {
            try (SpeechAudio audio = gateway.synthesize(claim.speech().text(), "alloy", 1.0, SpeechAudioFormat.PCM)) {
                open.set(audio);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Synthesis expired");
                if (!Set.of("audio/pcm", SpeechAudioFormat.PCM.contentType()).contains(audio.getContentType()) || audio.getContentLength() > MAX_BYTES)
                    throw new IOException("Invalid synthesis format or size");
                var bytes = new ByteArrayOutputStream();
                audio.writeTo(new OutputStream() {
                    @Override public void write(int value) throws IOException { write(new byte[] {(byte) value}, 0, 1); }
                    @Override public void write(byte[] data, int offset, int length) throws IOException {
                        if (bytes.size() + length > MAX_BYTES || !clock.instant().isBefore(claim.speech().request().expiresAt()))
                            throw new IOException("Synthesis exceeded its bound");
                        bytes.write(data, offset, length);
                    }
                });
                if (bytes.size() == 0 || bytes.size() % 2 != 0) throw new IOException("Incomplete PCM");
                return bytes.toByteArray();
            } finally { open.set(null); workers.release(); }
        });
        byte[] audio;
        try {
            long remaining = Duration.between(clock.instant(), claim.speech().request().expiresAt()).toMillis();
            audio = work.get(Math.max(1, remaining), TimeUnit.MILLISECONDS);
        } catch (Exception failure) {
            work.cancel(true); SpeechAudio pending = open.getAndSet(null);
            if (pending != null) try { pending.close(); } catch (IOException ignored) { }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return Result.state("REJECTED"); // The durable claim prevents a paid retry after uncertainty.
        }
        return turns.serialized(id, () -> { synchronized (window) { return transaction.execute(status -> {
            Agent agent = current(id, owner);
            if (!valid(agent, owner, window, claim) || !clock.instant().isBefore(claim.speech().request().expiresAt())
                    || !clock.instant().plusMillis(audio.length * 1000L / (RATE * 2) + 500)
                            .isBefore(claim.speech().request().created().plusSeconds(60))) return Result.state("DISCARDED");
            var speech = claim.speech(); String synthesis = speech.synthesisId().toString();
            var policy = (TaskPolicy) agent.getCurrentState().ownPolicy();
            if (isTask(policy, response) && !TaskResponse.synthesized(policy.storage(), owner, response, synthesis, clock.instant()))
                return Result.state("DISCARDED");
            agent.recordExternalSpeech(speech.text(), new SpeechProvenance(SpeechProvenance.Origin.SYNTHESIZED,
                    owner.sessionId(), owner.epoch(), synthesis, SpeechProvenance.Association.CONFIRMED, List.of(), true, false));
            agents.saveAndFlush(agent);
            window.synthesized.put(response, new Delivery(speech, claim.started(), "APPROVED"));
            AfterCommit.run(() -> events.publishEvent(new AgentCommitted(id, owner.epoch())));
            return new Result("APPROVED", response, synthesis, List.of("synthesis-" + synthesis), speech.request().created().plusSeconds(60),
                    speech.text(), Base64.getEncoder().encodeToString(audio), RATE);
        }); } });
    }
    public boolean validate(UUID id, ExternalSpeech owner, LiveSpeechApprovalService.Window window, UUID response,
            String synthesis, boolean played) {
        return turns.serialized(id, () -> { synchronized (window) { return transaction.execute(status -> {
            Delivery delivery = window.synthesized.get(response); Agent agent = current(id, owner);
            if (delivery == null || !(played ? "RELEASED" : "APPROVED").equals(delivery.status())
                    || !delivery.speech().synthesisId().toString().equals(synthesis) || !valid(agent, owner, window, delivery)
                    || !clock.instant().isBefore(played ? delivery.speech().request().created().plusSeconds(60)
                            : delivery.speech().request().releaseBy())) return false;
            var policy = (ExternalVoicePolicy) agent.getCurrentState().ownPolicy();
            var task = (TaskPolicy) policy;
            Event event = agent.getEventHistory().toList().stream().filter(e -> {
                var p = e.speechProvenance();
                return p != null && p.origin() == SpeechProvenance.Origin.SYNTHESIZED && owner.sessionId().equals(p.sessionId())
                        && owner.epoch().equals(p.epoch()) && synthesis.equals(p.segmentId());
            }).findFirst().orElse(null);
            if (event == null || !ConversationProjection.isUnplayed(event)) return false;
            if (!played) {
                if (isTask(task, response) && !TaskResponse.release(task.storage(), owner, response, synthesis)) return false;
                if (!policy.liveSpeechReleased(delivery.speech().request(), synthesis)) throw new IllegalStateException("Synthesis release lost authority");
            } else {
                event.speechProvenance(event.speechProvenance().playback(true));
                if (isTask(task, response)) TaskResponse.played(task.storage(), response, clock.instant());
                policy.liveSpeechCaptured(event, owner, delivery.started(), clock.instant(), List.of());
                AfterCommit.run(() -> {
                    behaviour.publish(id, event); events.publishEvent(new AgentCommitted(id, owner.epoch()));
                    events.publishEvent(new ExternalSpeechRecordingService.PlaybackReady(id, owner));
                });
            }
            agents.saveAndFlush(agent);
            window.synthesized.put(response, new Delivery(played ? delivery.speech() : policy.liveCommittedSpeech(owner),
                    delivery.started(), played ? "PLAYED" : "RELEASED"));
            return true;
        }); } });
    }
    private boolean valid(Agent agent, ExternalSpeech owner, LiveSpeechApprovalService.Window window, Delivery delivery) {
        return agent != null && window.current(delivery.speech().request())
                && delivery.speech().equals(((ExternalVoicePolicy) agent.getCurrentState().ownPolicy()).liveCommittedSpeech(owner));
    }
    private Agent current(UUID id, ExternalSpeech owner) {
        Agent agent = agents.findById(id).orElse(null);
        return agent != null && owner.equals(ownership.current(agent)) && ownership.taskWorkAllowed(id, owner)
                && agent.getCurrentState().ownPolicy() instanceof TaskPolicy
                && agent.getCurrentState().ownPolicy() instanceof ExternalVoicePolicy policy && policy.liveCommittedSpeechOutput() ? agent : null;
    }
    private static boolean isTask(TaskPolicy policy, UUID response) {
        return policy.storage().containsKey(TaskResponse.KEY)
                && response.toString().equals(policy.storage().get(TaskResponse.KEY).getAsJsonObject().get("id").getAsString());
    }
}
