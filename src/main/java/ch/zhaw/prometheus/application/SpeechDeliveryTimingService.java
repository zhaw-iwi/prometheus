package ch.zhaw.prometheus.application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.logging.SpeechDeliveryTrace;

/** Short-lived diagnostics only. No audio, access codes, entities or durable jobs. */
@Service
public class SpeechDeliveryTimingService {
    private final ScopedDemoService access;
    private final int limit;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();

    @Autowired
    public SpeechDeliveryTimingService(ScopedDemoService access) {
        this(access, 128, Duration.ofMinutes(10), System::nanoTime);
    }

    SpeechDeliveryTimingService(ScopedDemoService access, int limit, Duration ttl, LongSupplier clock) {
        this.access = access; this.limit = limit; this.ttlNanos = ttl.toNanos(); this.clock = clock;
    }

    /** Called only after the canonical speech service has authorized this event. */
    public synchronized SpeechDeliveryTrace start(UUID agentId, UUID eventId) {
        prune();
        SpeechDeliveryTrace trace = new SpeechDeliveryTrace(clock);
        entries.put(trace.id(), new Entry(agentId, eventId, clock.getAsLong(), trace));
        while (entries.size() > limit) entries.remove(entries.keySet().iterator().next());
        return trace;
    }

    public Optional<SpeechDeliveryTrace.Snapshot> find(String accessCode, UUID agentId, UUID eventId, UUID requestId) {
        if (access.getAgentInfo(accessCode, agentId).isEmpty()) return Optional.empty();
        synchronized (this) {
            prune();
            Entry entry = entries.get(requestId);
            return entry != null && entry.agentId.equals(agentId) && entry.eventId.equals(eventId)
                    ? Optional.of(entry.trace.snapshot()) : Optional.empty();
        }
    }

    private void prune() {
        long now = clock.getAsLong();
        entries.values().removeIf(entry -> now - entry.created >= ttlNanos);
    }

    private record Entry(UUID agentId, UUID eventId, long created, SpeechDeliveryTrace trace) {}
}
