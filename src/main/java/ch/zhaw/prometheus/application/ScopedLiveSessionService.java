package ch.zhaw.prometheus.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.google.gson.JsonObject;
import jakarta.annotation.PreDestroy;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.spi.live.LiveProperties;
import ch.zhaw.prometheus.spi.live.LiveProviderException;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;
import ch.zhaw.prometheus.application.live.LiveContextSnapshot;

/** Scoped provider session host; transcript processing runs in bounded session mailboxes. */
@Service
public class ScopedLiveSessionService {
    public static final List<String> VOICES = List.of("marin", "quartz", "willow", "meridian");
    public record Capabilities(boolean enabled, String model, List<String> voices, boolean diagnosticOnly, boolean eligible) {}
    public record SessionView(UUID handle, String sdp, String model, String voice, boolean sidebandReady) {
        @Override public String toString() { return "SessionView[handle=" + handle + ",sdp=redacted]"; }
    }
    public record Diagnostic(String type, long sequence, long serverMs, String eventId, String clientEventId) {}
    public record StatusView(String state, boolean finalized, long eventCount, long inputSamples,
            long outputSamples, long voicedInputSamples, List<Diagnostic> recent, long dropped, String captureState,
            LiveContextBridgeService.Status context, UUID handle, UUID epoch, String clock,
            LiveTranscriptCaptureService.Status capture, String reason,
            ch.zhaw.prometheus.application.live.LiveAudioDiagnostics.Status audio, ProviderTelemetry providerTelemetry) {}
    public record ProviderProblem(long serverMs, String code, String type, String clientEventId) {}
    public record ProviderTelemetry(Double usageSeconds, Double contextUsageRatio, String closeReason,
            List<ProviderProblem> problems, Map<String,Object> configuration) {}
    public record UpdatesView(StatusView status, long transcriptRevision, List<LiveTranscriptIngressService.Outcome> transcripts) {}

    private final ScopedDemoService demo;
    private final LiveSessionGateway gateway;
    private final LiveProperties properties;
    private final LiveAgentContextService contexts;
    private final ExternalSpeechOwnership ownership;
    private final Clock clock;
    private LiveTranscriptCaptureService captures;
    @Autowired void configureCaptures(LiveTranscriptCaptureService captures) { this.captures = captures; }
    private LiveContextBridgeService bridge;
    @Autowired void configureBridge(LiveContextBridgeService bridge) { this.bridge = bridge; }
    private LiveTranscriptIngressService ingress;
    @Autowired void configureIngress(LiveTranscriptIngressService ingress) { this.ingress = ingress; }
    private GenericTaskSessionLifecycleService taskLifecycle;
    @Autowired void configureTaskLifecycle(GenericTaskSessionLifecycleService lifecycle) { this.taskLifecycle = lifecycle; }
    @org.springframework.context.event.EventListener
    public void ledgerChanged(LiveTranscriptIngressService.LedgerChanged event) {
        Lease lease = sessions.get(event.sessionId());
        if (lease != null && lease.agentId.equals(event.agentId())) lease.transcriptRevision.incrementAndGet();
    }
    private final Map<UUID, Lease> sessions = new ConcurrentHashMap<>();
    private record Closed(UUID agent, byte[] scope, Instant expires, StatusView status) {}
    private final Map<UUID, Closed> closed = new ConcurrentHashMap<>();
    private final java.util.concurrent.ThreadPoolExecutor cleanup = new java.util.concurrent.ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(128), work -> { var thread = new Thread(work, "live-cleanup"); thread.setDaemon(true); return thread; });
    private java.util.concurrent.Executor disposer = Runnable::run;

    @Autowired public ScopedLiveSessionService(ScopedDemoService demo, LiveSessionGateway gateway, LiveProperties properties,
            LiveAgentContextService contexts, ExternalSpeechOwnership ownership) {
        this(demo, gateway, properties, contexts, ownership, Clock.systemUTC());
        disposer = cleanup;
    }
    ScopedLiveSessionService(ScopedDemoService demo, LiveSessionGateway gateway, LiveProperties properties,
            LiveAgentContextService contexts, ExternalSpeechOwnership ownership, Clock clock) {
        this.demo = demo; this.gateway = gateway; this.properties = properties; this.contexts = contexts; this.clock = clock;
        this.ownership = ownership;
    }

    public Optional<Capabilities> capabilities(String code, UUID agentId) {
        return demo.getAgentInfo(code, agentId).map(info -> new Capabilities(properties.isEnabled(), properties.getModel(), VOICES, false,
                contexts.supported(code, agentId)));
    }

    public Capabilities capabilities(String code) {
        demo.openSession(code);
        return new Capabilities(properties.isEnabled(), properties.getModel(), VOICES, false, false);
    }

    public Optional<SessionView> create(String code, UUID agentId, LiveSessionRequest request) {
        Optional<AgentInfoView> info = demo.getAgentInfo(code, agentId);
        if (info.isEmpty()) return Optional.empty();
        if (!properties.isEnabled()) throw new LiveSessionUnavailableException(false);
        if (request == null || request.sdp() == null || !request.sdp().startsWith("v=0")
                || request.sdp().length() > 65536) throw new IllegalArgumentException("Invalid SDP offer");
        String voice = request.voice() == null ? "marin" : request.voice();
        if (!VOICES.contains(voice)) throw new IllegalArgumentException("Unsupported Live voice");
        Lease lease = new Lease(agentId, digest(code), clock.instant().plusSeconds(properties.getSessionLifetimeSeconds()), clock);
        synchronized (sessions) {
            if (sessions.size() >= properties.getCapacity() || sessions.values().stream().anyMatch(s -> s.agentId.equals(agentId)))
                throw new LiveSessionUnavailableException(true);
            sessions.put(lease.handle, lease);
        }
        try {
            String build = System.getenv("HEROKU_SLUG_COMMIT");
            lease.configuration = Map.of("model", properties.getModel(), "voice", voice,
                    "requestTimeoutMs", properties.getRequestTimeoutMs(), "telemetryVersion", 1,
                    "build", build != null && build.matches("[a-fA-F0-9]{40,64}") ? build : "unknown",
                    "silenceMs", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.SILENCE_MS,
                    "latenessMs", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.LATENESS_MS,
                    "maxOpenMs", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.MAX_OPEN_MS,
                    "maxAudioGapMs", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.MAX_AUDIO_GAP_MS);
            lease.failure = reason -> failed(lease, reason);
            lease.context = contexts.claim(code, agentId, lease.handle).orElseThrow(() -> new IllegalArgumentException("Agent unavailable"));
            lease.authorized = () -> ownership.isCurrent(agentId, new ch.zhaw.prometheus.model.policy.ExternalSpeech(lease.handle, lease.context.epoch()));
            if (bridge != null) lease.bridge = bridge.open(lease.context,
                    new ch.zhaw.prometheus.model.policy.ExternalSpeech(lease.handle, lease.context.epoch()),
                    command -> lease.append(command, properties.getRequestTimeoutMs()), context -> lease.context = context,
                    codeName -> { lease.contextProblem = codeName; failed(lease, codeName); });
            if (captures != null) lease.capture = captures.open(agentId,
                    new ch.zhaw.prometheus.model.policy.ExternalSpeech(lease.handle, lease.context.epoch()),
                    () -> lease.context.statePath(), codeName -> { lease.captureProblem = codeName; failed(lease, codeName); });
            lease.provider = gateway.create(new LiveSessionGateway.Request(request.sdp(), voice,
                    lease.context.instructions(),
                    lease.context.startupInput()));
            lease.connection = gateway.attach(lease.provider.id(), lease::receive, () -> failed(lease, "sideband_disconnected"));
            if (lease.stopped.get() || !lease.connection.isOpen() || lease.disconnected.get())
                throw new LiveProviderException("Live sideband did not become ready");
            if (lease.bridge != null) lease.bridge.ready();
            return Optional.of(new SessionView(lease.handle, lease.provider.sdp(), properties.getModel(), voice, true));
        } catch (RuntimeException failure) {
            sessions.remove(lease.handle, lease);
            dispose(lease, false);
            throw failure;
        }
    }

    public Optional<StatusView> status(String code, UUID agentId, UUID handle) {
        return owned(code, agentId, handle).map(lease -> { lease.clientSeen = clock.instant(); return lease.status(); })
                .or(() -> closedStatus(code, agentId, handle));
    }

    public Optional<StatusView> mute(String code, UUID agentId, UUID handle, boolean muted) {
        return owned(code, agentId, handle).map(lease -> {
            String command = muted ? "session.input_audio.mute" : "session.input_audio.unmute";
            String acknowledgement = muted ? "session.input_audio.muted" : "session.input_audio.unmuted";
            lease.command(command, acknowledgement, properties.getRequestTimeoutMs());
            if (lease.capture != null) lease.capture.inputMuted(muted);
            return lease.status();
        });
    }

    public Optional<UpdatesView> updates(String code, UUID agentId, UUID handle, long transcriptRevision) {
        if (transcriptRevision < -1) throw new IllegalArgumentException("Invalid transcript revision");
        return owned(code, agentId, handle).map(lease -> {
            lease.clientSeen = clock.instant();
            // Read before the ledger: a concurrent commit causes another read next poll.
            long revision = lease.transcriptRevision.get();
            return new UpdatesView(lease.status(), revision,
                    revision == transcriptRevision ? null : ingress.history(agentId, handle));
        }).or(() -> closedStatus(code, agentId, handle).map(status -> new UpdatesView(status, -1, null)));
    }

    public Optional<StatusView> close(String code, UUID agentId, UUID handle) {
        Optional<Lease> owned = owned(code, agentId, handle);
        if (owned.isEmpty()) return closedStatus(code, agentId, handle);
        Lease lease = owned.get();
        if (!lease.disconnected.get()) lease.reason = "local_stop";
        // Retain the reservation until transport cleanup finishes.
        dispose(lease, true);
        sessions.remove(handle, lease);
        remember(lease);
        return Optional.of(lease.status());
    }

    private Optional<Lease> owned(String code, UUID agentId, UUID handle) {
        if (!demo.hasVisibleAgent(code, agentId)) return Optional.empty();
        Lease lease = sessions.get(handle);
        if (lease == null || !lease.agentId.equals(agentId) || !MessageDigest.isEqual(lease.scope, digest(code))) return Optional.empty();
        return Optional.of(lease);
    }

    @Scheduled(fixedDelay = 5000)
    public void expire() {
        closed.entrySet().removeIf(entry -> !clock.instant().isBefore(entry.getValue().expires));
        for (Lease lease : sessions.values()) {
            if (lease.connection != null && !lease.stopped.get()) lease.connection.heartbeat(clock.millis());
            String reason = !clock.instant().isBefore(lease.expires) ? "session_expired"
                    : !clock.instant().isBefore(lease.clientSeen.plusSeconds(properties.getClientIdleSeconds())) ? "client_heartbeat_expired"
                    : lease.context != null && !lease.authorized.getAsBoolean() ? "speech_scope_revoked"
                    : lease.disconnected.get() ? lease.reason : lease.finalized.isDone() ? "provider_closed" : null;
            if (reason != null && lease.cleanupQueued.compareAndSet(false, true)) {
                failed(lease, reason);
                try { disposer.execute(() -> { dispose(lease, false); sessions.remove(lease.handle, lease); remember(lease); }); }
                catch (java.util.concurrent.RejectedExecutionException full) { lease.cleanupQueued.set(false); }
            }
        }
    }
    @PreDestroy public void shutdown() {
        for (Lease lease : sessions.values()) dispose(lease, false);
        sessions.clear();
        cleanup.shutdownNow(); closed.clear();
    }

    private Optional<StatusView> closedStatus(String code, UUID agentId, UUID handle) {
        if (!demo.hasVisibleAgent(code, agentId)) return Optional.empty();
        Closed value = closed.get(handle);
        return value != null && value.agent.equals(agentId) && clock.instant().isBefore(value.expires)
                && MessageDigest.isEqual(value.scope, digest(code)) ? Optional.of(value.status) : Optional.empty();
    }
    private synchronized void remember(Lease lease) {
        if (closed.size() >= 128) closed.entrySet().stream().min(java.util.Comparator.comparing(entry -> entry.getValue().expires))
                .ifPresent(entry -> closed.remove(entry.getKey()));
        closed.put(lease.handle, new Closed(lease.agentId, lease.scope, clock.instant().plusSeconds(120), lease.status()));
    }
    private void failed(Lease lease, String reason) {
        if (lease.stopped.get()) return; // Expected pending-append/transport failures during local cleanup.
        lease.reason = reason; lease.disconnected.set(true);
        ownership.pauseInput(lease.agentId, lease.handle);
        lease.pending.values().forEach(value -> value.result.completeExceptionally(new LiveProviderException("Live session disconnected")));
    }

    private void dispose(Lease lease, boolean graceful) {
        boolean first = lease.stopped.compareAndSet(false, true);
        ownership.pauseInput(lease.agentId, lease.handle);
        if (lease.capture != null) lease.capture.inputMuted(true);
        lease.pending.values().forEach(value -> value.result.completeExceptionally(new LiveProviderException("Live session stopped")));
        if (lease.bridge != null) lease.bridge.close();
        if (first && graceful && lease.connection != null && lease.connection.isOpen()) {
            try {
                JsonObject close = new JsonObject(); close.addProperty("type", "session.close");
                lease.connection.send(close);
                lease.finalized.get(properties.getCloseTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception failure) { if (failure instanceof InterruptedException) Thread.currentThread().interrupt(); }
        }
        if (lease.connection != null) lease.connection.close();
        // Startup may finish after expiry; a second dispose still cleans up the newly created provider.
        if (lease.provider != null && !lease.finalized.isDone() && lease.hungUp.compareAndSet(false, true)) {
            try { gateway.hangup(lease.provider.id()); }
            catch (RuntimeException failure) { lease.cleanupFailed = true; }
        }
        if (lease.capture != null) {
            try { lease.capture.close().get(properties.getCloseTimeoutMs(), TimeUnit.MILLISECONDS); lease.captureDrained = true; }
            catch (Exception failure) { lease.captureProblem = "capture_finalization_unconfirmed";
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt(); }
        }
        if (first && taskLifecycle != null && lease.context != null) {
            try { taskLifecycle.closed(lease.agentId, lease.context.epoch(), lease.handle); }
            catch (RuntimeException failure) {
                lease.cleanupFailed = true;
                org.slf4j.LoggerFactory.getLogger(ScopedLiveSessionService.class).warn("Generic task session pause failed; subsequent turns remain fenced");
            }
        }
        ownership.release(lease.agentId, lease.handle);
    }

    private static byte[] digest(String code) {
        try { return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static final class Lease {
        final UUID handle = UUID.randomUUID();
        final UUID agentId;
        final byte[] scope;
        final Instant expires;
        final Clock clock;
        volatile Instant clientSeen;
        final java.util.concurrent.atomic.AtomicLong transcriptRevision = new java.util.concurrent.atomic.AtomicLong();
        volatile String reason = "operator_stop";
        volatile java.util.function.BooleanSupplier authorized = () -> true;
        volatile java.util.function.Consumer<String> failure = ignored -> {};
        final AtomicBoolean cleanupQueued = new AtomicBoolean();
        final AtomicBoolean stopped = new AtomicBoolean(), disconnected = new AtomicBoolean(), hungUp = new AtomicBoolean();
        final CompletableFuture<Void> finalized = new CompletableFuture<>();
        final Map<String, Pending> pending = new ConcurrentHashMap<>();
        final Semaphore commandSlots = new Semaphore(4);
        final ArrayDeque<Diagnostic> recent = new ArrayDeque<>();
        volatile LiveSessionGateway.Session provider;
        volatile LiveContextSnapshot context;
        volatile LiveTranscriptCaptureService.Capture capture;
        volatile LiveContextBridgeService.Session bridge;
        volatile String contextProblem;
        volatile String captureProblem;
        volatile boolean captureDrained;
        volatile LiveSessionGateway.Connection connection;
        volatile boolean cleanupFailed;
        long count, dropped, inputSamples, outputSamples, voicedInputSamples;
        final ch.zhaw.prometheus.application.live.LiveAudioDiagnostics audio = new ch.zhaw.prometheus.application.live.LiveAudioDiagnostics();
        record Pending(String type, CompletableFuture<Void> result) {}
        String providerCloseReason;
        Map<String,Object> configuration = Map.of("telemetryVersion", 1);
        Double usageSeconds, contextUsageRatio;
        final ArrayDeque<ProviderProblem> providerProblems = new ArrayDeque<>();

        Lease(UUID agentId, byte[] scope, Instant expires, Clock clock) {
            this.agentId = agentId; this.scope = scope; this.expires = expires; this.clock = clock; this.clientSeen = clock.instant();
        }
        synchronized void receive(JsonObject event) {
            String type = event.has("type") ? event.get("type").getAsString() : "invalid";
            if (!type.matches("[a-z_.]{1,100}")) type = "invalid";
            count++;
            if (event.has("usage") && event.get("usage").isJsonObject()) {
                Double seconds = numeric(event.getAsJsonObject("usage"), "seconds");
                if (seconds != null && seconds >= 0) usageSeconds = seconds;
            }
            if (event.has("context_window") && event.get("context_window").isJsonObject()) {
                Double ratio = numeric(event.getAsJsonObject("context_window"), "usage_ratio");
                if (ratio != null && ratio >= 0 && ratio <= 1) contextUsageRatio = ratio;
            }
            if (type.equals("session.closed")) providerCloseReason = safeId(string(event, "reason"));
            if (type.equals("error") && event.has("error") && event.get("error").isJsonObject()) {
                var error = event.getAsJsonObject("error");
                if (providerProblems.size() == 16) providerProblems.removeFirst();
                providerProblems.addLast(new ProviderProblem(clock.millis(), safeId(string(error, "code")),
                        safeId(string(error, "type")), safeId(string(error, "client_event_id"))));
            }
            if (capture != null) capture.receive(event);
            if (bridge != null) bridge.receive(event);
            if (type.equals("session.input_audio.append") || type.equals("session.output_audio.delta")) {
                String field = type.equals("session.input_audio.append") ? "audio" : "delta";
                if (event.has(field)) {
                    byte[] pcm = Base64.getDecoder().decode(event.get(field).getAsString());
                    var activity = audio.observe(field.equals("audio"), pcm, clock.millis());
                    long samples = pcm.length / 2;
                    if (field.equals("audio")) {
                        inputSamples += samples;
                        voicedInputSamples += activity.voicedSamples();
                    } else outputSamples += samples;
                }
                return; // Audio is measured and discarded, never retained in the diagnostic log.
            }
            if (recent.size() == 64) { recent.removeFirst(); dropped++; }
            recent.addLast(new Diagnostic(type, count, clock.millis(), safeId(string(event, "event_id")), safeId(string(event, "client_event_id"))));
            if (type.equals("session.closed")) finalized.complete(null);
            if (type.equals("session.closed")) failure.accept("provider_closed");
            String clientId = string(event, "client_event_id");
            if (type.equals("error") && event.has("error") && event.get("error").isJsonObject())
                clientId = string(event.getAsJsonObject("error"), "client_event_id");
            Pending wait = clientId == null ? null : pending.get(clientId);
            if (wait != null && wait.type().equals(type)) wait.result().complete(null);
            if (wait != null && type.equals("error")) wait.result().completeExceptionally(new LiveProviderException("Live command rejected"));
            if (type.equals("error")) failure.accept("provider_error");
        }
        void command(String type, String ack, int timeout) {
            JsonObject event = new JsonObject(); event.addProperty("type", type);
            command(event, ack, timeout);
        }
        void append(ch.zhaw.prometheus.application.live.LiveContextDelivery.Command command, int timeout) {
            JsonObject event = new JsonObject(); event.addProperty("type", command.type());
            event.addProperty("content", command.content()); event.addProperty("delegation_id", command.delegationId());
            command(event, command.type().replace(".append", ".appended"), timeout);
        }
        private void command(JsonObject event, String ack, int timeout) {
            if (stopped.get() || disconnected.get() || !authorized.getAsBoolean()) throw new LiveProviderException("Live session disconnected");
            if (!commandSlots.tryAcquire()) throw new LiveProviderException("Live command capacity reached");
            String id = UUID.randomUUID().toString();
            CompletableFuture<Void> result = new CompletableFuture<>();
            pending.put(id, new Pending(ack, result));
            try {
                event.addProperty("event_id", id);
                connection.send(event); result.get(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception failure) {
                this.failure.accept("command_ack_unconfirmed");
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new LiveProviderException("Live command not acknowledged");
            } finally { pending.remove(id); commandSlots.release(); }
        }
        private static String string(JsonObject event, String name) {
            return event.has(name) && event.get(name).isJsonPrimitive() ? event.get(name).getAsString() : null;
        }
        private static Double numeric(JsonObject object, String key) {
            try { double value = object.get(key).getAsDouble(); return Double.isFinite(value) ? value : null; }
            catch (RuntimeException invalid) { return null; }
        }
        private static String safeId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,128}") ? value : null; }
        synchronized StatusView status() {
            String state = cleanupFailed ? "cleanup_unconfirmed" : stopped.get() ? "closed" : disconnected.get() ? "disconnected" : "attached";
            return new StatusView(state, finalized.isDone(), count, inputSamples, outputSamples, voicedInputSamples, List.copyOf(recent), dropped,
                    captureProblem != null ? captureProblem : captureDrained ? "closed" : capture != null ? "active" : "unavailable",
                    bridge == null ? null : bridge.status(), handle, context == null ? null : context.epoch(), "server_unix_ms",
                    capture == null ? null : capture.status(), reason, audio.status(), new ProviderTelemetry(usageSeconds, contextUsageRatio, providerCloseReason,
                            List.copyOf(providerProblems), configuration));
        }
    }
}
