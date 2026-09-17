package ch.zhaw.prometheus.application;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.PreparedAction;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.annotation.PreDestroy;

/** Bounded, volatile FIFO lanes. No worker receives a managed domain object. */
@Component
public final class BackgroundActionExecutor implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BackgroundActionExecutor.class);
    private final ExecutorService workers;
    private final Semaphore capacity;
    private final long queueTimeoutNanos;
    private final java.util.function.LongSupplier clock;
    private final Map<UUID, Lane> lanes = new HashMap<>();
    private boolean closed;

    @org.springframework.beans.factory.annotation.Autowired
    public BackgroundActionExecutor(@Value("${prometheus.actions.parallelism:4}") int parallelism,
            @Value("${prometheus.actions.capacity:64}") int limit,
            @Value("${prometheus.actions.queue-timeout-ms:30000}") long queueTimeoutMs) {
        this(parallelism, limit, queueTimeoutMs, System::nanoTime);
    }

    BackgroundActionExecutor(int parallelism, int limit, long queueTimeoutMs, java.util.function.LongSupplier clock) {
        if (parallelism < 1 || parallelism > 32 || limit < parallelism || limit > 1024
                || queueTimeoutMs < 1 || queueTimeoutMs > 300000) throw new IllegalArgumentException("Invalid background action limits");
        workers = Executors.newFixedThreadPool(parallelism, Thread.ofVirtual().name("agent-action-", 0).factory());
        capacity = new Semaphore(limit);
        queueTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(queueTimeoutMs);
        this.clock = clock;
    }

    @FunctionalInterface public interface Completion {
        String apply(PreparedAction action, Map<String, String> values, Versions versions);
    }

    /** Reserved before the transition is persisted: overload fails explicitly, never blocks or silently drops. */
    public synchronized Reservation reserve(PreparedAction action, LanguageModelGateway gateway, Completion completion) {
        if (closed || !capacity.tryAcquire()) throw new IllegalStateException("Background action capacity exceeded or executor stopped");
        return new Reservation(action, gateway, completion);
    }

    public final class Reservation implements AutoCloseable {
        private final PreparedAction action;
        private final LanguageModelGateway gateway;
        private final Completion completion;
        private final UUID jobId = UUID.randomUUID();
        private final String traceId = LatencyTrace.currentId();
        private final long reservedAt = clock.getAsLong();
        private long queuedAt;
        private final AtomicBoolean released = new AtomicBoolean();
        private boolean submitted;
        private Reservation(PreparedAction action, LanguageModelGateway gateway, Completion completion) {
            this.action = action; this.gateway = gateway; this.completion = completion;
        }
        public void commit(UUID agentId) {
            synchronized (BackgroundActionExecutor.this) {
                if (submitted || released.get()) throw new IllegalStateException("Action reservation already finished");
                submitted = true;
                queuedAt = clock.getAsLong();
                if (closed) { report(agentId, "cancelled_shutdown"); release(); return; }
                Lane lane = lanes.computeIfAbsent(agentId, ignored -> new Lane());
                lane.jobs.add(this);
                lane.versions.retain(action);
                if (!lane.running) {
                    lane.running = true;
                    try { workers.execute(() -> drain(agentId, lane)); }
                    catch (RejectedExecutionException rejected) { cancelLane(agentId, lane); }
                }
            }
        }
        private void report(UUID agentId, String status) {
            LOG.info("background_action trace={} job={} agent={} action={} status={} elapsedMs={}",
                    traceId, jobId, agentId, action.actionId(), status, (clock.getAsLong() - reservedAt) / 1_000_000.0);
        }
        private void release() { if (released.compareAndSet(false, true)) capacity.release(); }
        @Override public void close() { synchronized (BackgroundActionExecutor.this) { if (!submitted) release(); } }
    }

    private static final class Lane {
        final Deque<Reservation> jobs = new ArrayDeque<>();
        final Versions versions = new Versions();
        boolean running;
    }

    /** Rebase only through successful earlier background writes, never through foreground changes. */
    public static final class Versions {
        private record Key(UUID storage, String key, String expected) {}
        private final Map<Key, String> current = new HashMap<>();
        private final Map<Key, Integer> references = new HashMap<>();
        synchronized void retain(PreparedAction action) {
            action.expectedVersions().forEach((key, expected) -> {
                Key k = new Key(action.storageId(), key, expected);
                current.putIfAbsent(k, expected); references.merge(k, 1, Integer::sum);
            });
        }
        synchronized void release(PreparedAction action) {
            action.expectedVersions().forEach((key, expected) -> {
                Key k = new Key(action.storageId(), key, expected);
                if (references.merge(k, -1, Integer::sum) == 0) { references.remove(k); current.remove(k); }
            });
        }
        public synchronized String expected(UUID storage, String key, String original) {
            return current.getOrDefault(new Key(storage, key, original), original);
        }
        public synchronized void applied(UUID storage, String key, String before, String after) {
            current.replaceAll((k, value) -> k.storage.equals(storage) && k.key.equals(key) && value.equals(before) ? after : value);
        }
    }

    private void drain(UUID agentId, Lane lane) {
        while (true) {
            Reservation job;
            synchronized (this) {
                job = lane.jobs.peek();
                if (job == null) { lanes.remove(agentId, lane); return; }
            }
            try {
                if (clock.getAsLong() - job.queuedAt > queueTimeoutNanos) job.report(agentId, "queue_timeout");
                else if (Thread.currentThread().isInterrupted()) job.report(agentId, "cancelled_shutdown");
                else {
                    job.report(agentId, "started");
                    Map<String, String> values = Map.copyOf(job.action.work().compute(job.gateway));
                    if (!values.keySet().equals(job.action.expectedVersions().keySet()))
                        throw new IllegalStateException("Action result destinations do not match prepared destinations");
                    boolean stopped;
                    synchronized (this) { stopped = closed; }
                    job.report(agentId, stopped ? "cancelled_shutdown" : job.completion.apply(job.action, values, lane.versions));
                }
            } catch (Exception failed) {
                // Exception messages/provider payloads may contain private conversation data.
                job.report(agentId, "failed_" + failed.getClass().getSimpleName());
            } finally {
                synchronized (this) {
                    lane.jobs.remove(job); lane.versions.release(job.action); job.release();
                }
            }
        }
    }

    private void cancelLane(UUID id, Lane lane) {
        for (Reservation job : lane.jobs) { job.report(id, "cancelled_shutdown"); lane.versions.release(job.action); job.release(); }
        lane.jobs.clear(); lanes.remove(id, lane);
    }
    int availableCapacity() { return capacity.availablePermits(); }
    synchronized int retainedAgents() { return lanes.size(); }

    @Override @PreDestroy public synchronized void close() {
        closed = true;
        workers.shutdownNow();
        // Running work is interrupted; queued lane tasks may never enter drain().
        for (var entry : List.copyOf(lanes.entrySet())) {
            Lane lane = entry.getValue();
            for (Reservation job : List.copyOf(lane.jobs)) {
                job.report(entry.getKey(), "cancelled_shutdown"); job.release();
            }
        }
        lanes.clear();
    }
}
