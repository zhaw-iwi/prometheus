package ch.zhaw.prometheus.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ch.zhaw.prometheus.logging.LatencyTrace;

/** In-process ordering; release follows an enclosing local transaction's commit/rollback. */
final class AgentTurnSerialiser {
    private static final class Entry { final ReentrantLock lock = new ReentrantLock(true); int users; }
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    <T> T call(UUID id, Supplier<T> work) {
        java.util.Objects.requireNonNull(id, "agent id");
        Entry entry = entries.compute(id, (key, old) -> { Entry next = old == null ? new Entry() : old; next.users++; return next; });
        boolean locked = false, deferred = false;
        var released = new AtomicBoolean();
        Runnable release = () -> {
            if (!released.compareAndSet(false, true)) return;
            entry.lock.unlock(); dereference(id, entry);
        };
        try {
            locked = LatencyTrace.measure("agent_queue", () -> {
                try { return entry.lock.tryLock(60, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Agent turn interrupted"); }
            });
            if (!locked) throw new IllegalStateException("Agent turn queue deadline exceeded");
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { release.run(); }
                });
                deferred = true;
            }
            return work.get();
        } finally {
            if (!locked) dereference(id, entry);
            else if (!deferred) release.run();
        }
    }

    private void dereference(UUID id, Entry entry) {
        entries.compute(id, (key, current) -> { if (current != entry) throw new IllegalStateException("Agent lock ownership changed"); return --entry.users == 0 ? null : entry; });
    }
    int retainedAgents() { return entries.size(); }
}
