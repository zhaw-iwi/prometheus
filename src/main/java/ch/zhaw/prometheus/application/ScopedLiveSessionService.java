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

/** GL-01 diagnostic session host. Transcript events are not yet agent input. */
@Service
public class ScopedLiveSessionService {
    public static final List<String> VOICES = List.of("marin", "quartz", "willow", "meridian");
    public record Capabilities(boolean enabled, String model, List<String> voices, boolean diagnosticOnly, boolean eligible) {}
    public record SessionView(UUID handle, String sdp, String model, String voice, boolean sidebandReady) {
        @Override public String toString() { return "SessionView[handle=" + handle + ",sdp=redacted]"; }
    }
    public record Diagnostic(String type, long sequence) {}
    public record StatusView(String state, boolean finalized, long eventCount, long inputSamples,
            long outputSamples, long voicedInputSamples, List<Diagnostic> recent, long dropped) {}

    private final ScopedDemoService demo;
    private final LiveSessionGateway gateway;
    private final LiveProperties properties;
    private final LiveAgentContextService contexts;
    private final Clock clock;
    private final Map<UUID, Lease> sessions = new ConcurrentHashMap<>();

    @Autowired public ScopedLiveSessionService(ScopedDemoService demo, LiveSessionGateway gateway, LiveProperties properties, LiveAgentContextService contexts) {
        this(demo, gateway, properties, contexts, Clock.systemUTC());
    }
    ScopedLiveSessionService(ScopedDemoService demo, LiveSessionGateway gateway, LiveProperties properties, LiveAgentContextService contexts, Clock clock) {
        this.demo = demo; this.gateway = gateway; this.properties = properties; this.contexts = contexts; this.clock = clock;
    }

    public Optional<Capabilities> capabilities(String code, UUID agentId) {
        return demo.getAgentInfo(code, agentId).map(info -> new Capabilities(properties.isEnabled(), properties.getModel(), VOICES, true,
                contexts.supported(code, agentId)));
    }

    public Optional<SessionView> create(String code, UUID agentId, LiveSessionRequest request) {
        Optional<AgentInfoView> info = demo.getAgentInfo(code, agentId);
        if (info.isEmpty()) return Optional.empty();
        if (!properties.isEnabled()) throw new LiveSessionUnavailableException(false);
        if (request == null || request.sdp() == null || !request.sdp().startsWith("v=0")
                || request.sdp().length() > 65536) throw new IllegalArgumentException("Invalid SDP offer");
        String voice = request.voice() == null ? "marin" : request.voice();
        if (!VOICES.contains(voice)) throw new IllegalArgumentException("Unsupported Live voice");
        Lease lease = new Lease(agentId, digest(code), clock.instant().plusSeconds(properties.getSessionLifetimeSeconds()));
        synchronized (sessions) {
            if (sessions.size() >= properties.getCapacity() || sessions.values().stream().anyMatch(s -> s.agentId.equals(agentId)))
                throw new LiveSessionUnavailableException(true);
            sessions.put(lease.handle, lease);
        }
        try {
            lease.context = contexts.snapshot(code, agentId).orElseThrow(() -> new IllegalArgumentException("Agent unavailable"));
            lease.provider = gateway.create(new LiveSessionGateway.Request(request.sdp(), voice,
                    lease.context.instructions() + "\nDiagnostic session: task actions are unavailable; do not claim to perform them.",
                    lease.context.startupInput()));
            lease.connection = gateway.attach(lease.provider.id(), lease::receive, () -> lease.disconnected.set(true));
            if (lease.stopped.get() || !lease.connection.isOpen() || lease.disconnected.get())
                throw new LiveProviderException("Live sideband did not become ready");
            return Optional.of(new SessionView(lease.handle, lease.provider.sdp(), properties.getModel(), voice, true));
        } catch (RuntimeException failure) {
            sessions.remove(lease.handle, lease);
            dispose(lease, false);
            throw failure;
        }
    }

    public Optional<StatusView> status(String code, UUID agentId, UUID handle) {
        return owned(code, agentId, handle).map(Lease::status);
    }

    public Optional<StatusView> mute(String code, UUID agentId, UUID handle, boolean muted) {
        return owned(code, agentId, handle).map(lease -> {
            String command = muted ? "session.input_audio.mute" : "session.input_audio.unmute";
            String acknowledgement = muted ? "session.input_audio.muted" : "session.input_audio.unmuted";
            lease.command(command, acknowledgement, properties.getRequestTimeoutMs());
            return lease.status();
        });
    }

    public Optional<StatusView> close(String code, UUID agentId, UUID handle) {
        Optional<Lease> owned = owned(code, agentId, handle);
        if (owned.isEmpty()) return Optional.empty();
        Lease lease = owned.get();
        // Retain the reservation until transport cleanup finishes.
        dispose(lease, true);
        sessions.remove(handle, lease);
        return Optional.of(lease.status());
    }

    private Optional<Lease> owned(String code, UUID agentId, UUID handle) {
        if (demo.getAgentInfo(code, agentId).isEmpty()) return Optional.empty();
        Lease lease = sessions.get(handle);
        if (lease == null || !lease.agentId.equals(agentId) || !MessageDigest.isEqual(lease.scope, digest(code))) return Optional.empty();
        return Optional.of(lease);
    }

    @Scheduled(fixedDelay = 5000)
    public void expire() {
        for (Lease lease : sessions.values()) {
            if (!clock.instant().isBefore(lease.expires) || lease.disconnected.get() || lease.finalized.isDone()) {
                dispose(lease, false); sessions.remove(lease.handle, lease);
            }
        }
    }
    @PreDestroy public void shutdown() {
        for (Lease lease : sessions.values()) dispose(lease, false);
        sessions.clear();
    }

    private void dispose(Lease lease, boolean graceful) {
        boolean first = lease.stopped.compareAndSet(false, true);
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
        final AtomicBoolean stopped = new AtomicBoolean(), disconnected = new AtomicBoolean(), hungUp = new AtomicBoolean();
        final CompletableFuture<Void> finalized = new CompletableFuture<>();
        final Map<String, Pending> pending = new ConcurrentHashMap<>();
        final Semaphore commandSlots = new Semaphore(4);
        final ArrayDeque<Diagnostic> recent = new ArrayDeque<>();
        volatile LiveSessionGateway.Session provider;
        volatile LiveContextSnapshot context;
        volatile LiveSessionGateway.Connection connection;
        volatile boolean cleanupFailed;
        long count, dropped, inputSamples, outputSamples, voicedInputSamples;
        record Pending(String type, CompletableFuture<Void> result) {}

        Lease(UUID agentId, byte[] scope, Instant expires) { this.agentId = agentId; this.scope = scope; this.expires = expires; }
        synchronized void receive(JsonObject event) {
            String type = event.has("type") ? event.get("type").getAsString() : "invalid";
            if (!type.matches("[a-z_.]{1,100}")) type = "invalid";
            count++;
            if (type.equals("session.input_audio.append") || type.equals("session.output_audio.delta")) {
                String field = type.equals("session.input_audio.append") ? "audio" : "delta";
                if (event.has(field)) {
                    byte[] pcm = Base64.getDecoder().decode(event.get(field).getAsString());
                    long samples = pcm.length / 2;
                    if (field.equals("audio")) {
                        inputSamples += samples;
                        long sum = 0;
                        for (int i = 0; i + 1 < pcm.length; i += 2) {
                            int sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8)); sum += (long) sample * sample;
                        }
                        if (samples > 0 && Math.sqrt((double) sum / samples) > 500) voicedInputSamples += samples;
                    } else outputSamples += samples;
                }
                return; // Audio is measured and discarded, never retained in the diagnostic log.
            }
            if (recent.size() == 64) { recent.removeFirst(); dropped++; }
            recent.addLast(new Diagnostic(type, count));
            if (type.equals("session.closed")) finalized.complete(null);
            String clientId = string(event, "client_event_id");
            if (type.equals("error") && event.has("error") && event.get("error").isJsonObject())
                clientId = string(event.getAsJsonObject("error"), "client_event_id");
            Pending wait = clientId == null ? null : pending.get(clientId);
            if (wait != null && wait.type().equals(type)) wait.result().complete(null);
            if (wait != null && type.equals("error")) wait.result().completeExceptionally(new LiveProviderException("Live command rejected"));
        }
        void command(String type, String ack, int timeout) {
            if (stopped.get() || disconnected.get()) throw new LiveProviderException("Live session disconnected");
            if (!commandSlots.tryAcquire()) throw new LiveProviderException("Live command capacity reached");
            String id = UUID.randomUUID().toString();
            CompletableFuture<Void> result = new CompletableFuture<>();
            pending.put(id, new Pending(ack, result));
            try {
                JsonObject event = new JsonObject(); event.addProperty("type", type); event.addProperty("event_id", id);
                connection.send(event); result.get(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new LiveProviderException("Live command not acknowledged");
            } finally { pending.remove(id); commandSlots.release(); }
        }
        private static String string(JsonObject event, String name) {
            return event.has(name) && event.get(name).isJsonPrimitive() ? event.get(name).getAsString() : null;
        }
        synchronized StatusView status() {
            String state = cleanupFailed ? "cleanup_unconfirmed" : stopped.get() ? "closed" : disconnected.get() ? "disconnected" : "attached";
            return new StatusView(state, finalized.isDone(), count, inputSamples, outputSamples, voicedInputSamples, List.copyOf(recent), dropped);
        }
    }
}
