package ch.zhaw.prometheus.application;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;
import com.google.gson.JsonElement;
import jakarta.annotation.PreDestroy;

/** One bounded, expiring, unpublished candidate per agent; survives only the acknowledge/generate gap. */
@Component
public final class BehaviourSpeculationService implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BehaviourSpeculationService.class);
    private final boolean enabled;
    private final int capacity;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final Semaphore permits;
    private final Map<UUID, Slot> candidates = new HashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("behaviour-preview-expiry").factory());
    private boolean closed;

    @org.springframework.beans.factory.annotation.Autowired
    public BehaviourSpeculationService(@Value("${prometheus.behaviour.speculation.enabled:true}") boolean enabled,
            @Value("${prometheus.behaviour.speculation.parallelism:2}") int parallelism,
            @Value("${prometheus.behaviour.speculation.capacity:64}") int capacity,
            @Value("${prometheus.behaviour.speculation.ttl-ms:30000}") long ttlMs) {
        this(enabled, parallelism, capacity, ttlMs, System::nanoTime);
    }
    BehaviourSpeculationService(boolean enabled, int parallelism, int capacity, long ttlMs, LongSupplier clock) {
        if (parallelism < 1 || parallelism > 16 || capacity < parallelism || capacity > 256 || ttlMs < 1 || ttlMs > 120000)
            throw new IllegalArgumentException("Invalid behaviour speculation limits");
        this.enabled = enabled; this.capacity = capacity; this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMs);
        this.clock = clock; permits = new Semaphore(parallelism);
        expiry.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.SECONDS);
    }

    public Scope open(UUID id, UUID epoch, boolean eligible) {
        discard(id, "new_input");
        return new Scope(id, epoch, enabled && eligible);
    }

    public final class Scope implements BehaviourSpeculation, AutoCloseable {
        private final UUID id, epoch;
        private final boolean allowed;
        private boolean attempted, handedOff;
        private UUID eventId;
        private Slot slot;
        private Scope(UUID id, UUID epoch, boolean allowed) { this.id = id; this.epoch = epoch; this.allowed = allowed; }
        @Override public void prepare(State state, Event event, PolicyRuntime runtime) {
            if (attempted || !allowed) return;
            attempted = true;
            InferenceRequest request;
            try { request = BehaviourPreview.prepare(state, event, runtime); }
            catch (RuntimeException unavailable) { LatencyTrace.record("speculation_skipped", 0, true); return; }
            if (request != null) {
                try {
                    if (runtime.languageModelGateway().supportsBehaviourSpeculation())
                        slot = begin(id, epoch, request, runtime.languageModelGateway());
                }
                catch (RuntimeException unavailable) { LatencyTrace.record("speculation_skipped", 0, true); }
            }
        }
        @Override public void invalidate() { if (slot != null) discard(id, slot, "transition"); }
        void result(UUID persistedEventId, boolean hasResponse) {
            eventId = persistedEventId;
            if (hasResponse || eventId == null) invalidate();
        }
        void commit() {
            if (slot == null) return;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { ready(); }
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) discard(id, slot, "rollback");
                    }
                });
            } else ready();
            handedOff = true;
        }
        private void ready() {
            synchronized (BehaviourSpeculationService.this) {
                if (candidates.get(id) == slot) slot.eventId = eventId;
            }
        }
        @Override public void close() { if (!handedOff && slot != null) discard(id, slot, "failed_turn"); }
    }

    private final class Slot {
        final UUID epoch;
        final InferenceRequest request;
        final Object route;
        final long deadline = clock.getAsLong() + ttlNanos;
        final Task task;
        UUID eventId;
        volatile LatencyTrace.CapturedInference evidence;
        Slot(UUID epoch, InferenceRequest request, LanguageModelGateway gateway) {
            this.epoch = epoch; this.request = request; route = gateway.guardCompatibilityKey(request);
            var marker = LatencyTrace.continuation(request.traceId());
            task = new Task(() -> {
                try (var trace = marker.get()) { stage("speculation_started", this, "dispatch"); }
                // Separate clock: this request may outlive its originating HTTP response.
                try (var trace = new LatencyTrace(request.traceId(), ignored -> {})) {
                    try { return gateway.infer(request); }
                    finally { evidence = LatencyTrace.captureInference(); }
                }
            });
        }
    }

    private final class Task extends FutureTask<String> {
        private final AtomicBoolean entered = new AtomicBoolean();
        Task(Callable<String> work) { super(work); }
        @Override public void run() {
            if (!entered.compareAndSet(false, true)) return;
            try { super.run(); } finally { permits.release(); }
        }
        @Override protected void done() { if (entered.compareAndSet(false, true)) permits.release(); }
    }

    private synchronized Slot begin(UUID id, UUID epoch, InferenceRequest request, LanguageModelGateway gateway) {
        expire();
        if (closed || candidates.size() >= capacity || !permits.tryAcquire()) {
            LatencyTrace.record("speculation_skipped", 0, true); return null;
        }
        Slot slot;
        try { slot = new Slot(epoch, request, gateway); }
        catch (RuntimeException failure) { permits.release(); throw failure; }
        candidates.put(id, slot);
        try { workers.execute(slot.task); }
        catch (RejectedExecutionException stopped) { discard(id, slot, "shutdown"); return null; }
        return slot;
    }

    public LanguageModelGateway forGeneration(UUID id, UUID epoch, UUID eventId, LanguageModelGateway delegate) {
        return new LanguageModelGateway() {
            @Override public String infer(InferenceRequest request) {
                Slot slot = take(id, epoch, eventId, request, delegate);
                if (slot == null) return delegate.infer(request);
                try {
                    return LatencyTrace.measure("speculation_wait", () -> {
                        try { return slot.task.get(Math.max(1, slot.deadline - clock.getAsLong()), TimeUnit.NANOSECONDS); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); throw new IllegalStateException("Behaviour inference interrupted");
                        } catch (ExecutionException | CancellationException | TimeoutException failure) {
                            throw new IllegalStateException("Required speculative behaviour inference failed");
                        }
                    });
                } finally {
                    slot.task.cancel(true);
                    if (slot.evidence != null) slot.evidence.attach();
                }
            }
            @Override public String complete(List<PromptMessage> m) { return delegate.complete(m); }
            @Override public boolean decide(List<PromptMessage> m) { return delegate.decide(m); }
            @Override public JsonElement extract(List<PromptMessage> m) { return delegate.extract(m); }
            @Override public JsonElement summarise(List<PromptMessage> m) { return delegate.summarise(m); }
            @Override public String summariseOffline(List<PromptMessage> m) { return delegate.summariseOffline(m); }
            @Override public GuardInferenceOptions guardInferenceOptions() { return delegate.guardInferenceOptions(); }
            @Override public GuardInferenceExecutor guardExecutor() { return delegate.guardExecutor(); }
            @Override public Object guardCompatibilityKey(InferenceRequest r) { return delegate.guardCompatibilityKey(r); }
            @Override public boolean supportsBehaviourSpeculation() { return delegate.supportsBehaviourSpeculation(); }
        };
    }

    private synchronized Slot take(UUID id, UUID epoch, UUID eventId, InferenceRequest request, LanguageModelGateway gateway) {
        if (request.purpose() != InferencePurpose.BEHAVIOUR) return null;
        Slot slot = candidates.get(id);
        if (slot == null) return null;
        if (clock.getAsLong() >= slot.deadline || eventId == null || !eventId.equals(slot.eventId)
                || !epoch.equals(slot.epoch) || !Objects.equals(slot.route, gateway.guardCompatibilityKey(request))
                || !sameRequest(slot.request, request)) {
            discard(id, slot, "changed_or_expired"); return null;
        }
        candidates.remove(id);
        stage("speculation_reused", slot, "selected");
        return slot;
    }

    static boolean sameRequest(InferenceRequest first, InferenceRequest second) {
        if (first.purpose() != second.purpose() || first.output() != second.output()
                || !Objects.equals(first.schema(), second.schema()) || first.messages().size() != second.messages().size()) return false;
        for (int i = 0; i < first.messages().size(); i++) {
            PromptMessage a = first.messages().get(i), b = second.messages().get(i);
            if (!a.getRole().equals(b.getRole()) || !a.getContent().equals(b.getContent())) return false;
        }
        return true;
    }

    public synchronized void discard(UUID id, String reason) {
        Slot slot = candidates.get(id);
        if (slot != null) discard(id, slot, reason);
    }
    private synchronized void discard(UUID id, Slot slot, String reason) {
        if (!candidates.remove(id, slot)) return;
        slot.task.cancel(true);
        if (slot.evidence != null && LatencyTrace.currentId().equals(slot.request.traceId())) slot.evidence.attach();
        stage("speculation_discarded", slot, reason);
    }
    private static void stage(String stage, Slot slot, String reason) {
        if (LatencyTrace.currentId().equals(slot.request.traceId()) || "speculation_reused".equals(stage))
            LatencyTrace.record(stage, 0, true, slot.request.requestId(), "BEHAVIOUR", null, null, null, null);
        LOG.info("latency trace={} request={} stage={} reason={}", slot.request.traceId(), slot.request.requestId(), stage, reason);
    }
    synchronized void expire() {
        for (var entry : List.copyOf(candidates.entrySet()))
            if (clock.getAsLong() >= entry.getValue().deadline) discard(entry.getKey(), entry.getValue(), "expired");
    }
    synchronized int retainedCandidates() { return candidates.size(); }
    int availableWorkers() { return permits.availablePermits(); }
    @Override @PreDestroy public synchronized void close() {
        closed = true;
        for (var entry : List.copyOf(candidates.entrySet())) discard(entry.getKey(), entry.getValue(), "shutdown");
        expiry.shutdownNow(); workers.shutdownNow();
    }
}
