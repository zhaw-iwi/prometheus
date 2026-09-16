package ch.zhaw.prometheus.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import ch.zhaw.prometheus.logging.LatencyTrace;

/** Bounded pure inference only. Workers never receive an agent, history or persistence context. */
@Component
public final class GuardInferenceExecutor implements AutoCloseable {
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore admission, global;
    private final int perTurn, capacity;
    private final long queueWaitNanos, turnTimeoutNanos;
    private final java.util.function.LongSupplier clock;

    @org.springframework.beans.factory.annotation.Autowired
    public GuardInferenceExecutor(OpenAIProperties properties) {
        this(properties, System::nanoTime);
    }

    GuardInferenceExecutor(OpenAIProperties properties, java.util.function.LongSupplier clock) {
        this.clock = clock;
        int parallelism = properties.getGuardParallelism(), queue = properties.getGuardQueueCapacity();
        perTurn = properties.getGuardPerTurnParallelism();
        if (parallelism < 1 || parallelism > 32 || queue < 0 || queue > 256 || perTurn < 1 || perTurn > parallelism
                || properties.getGuardQueueWaitMs() < 1 || properties.getGuardQueueWaitMs() > 30000
                || properties.getGuardTurnTimeoutMs() < 1 || properties.getGuardTurnTimeoutMs() > 300000) {
            throw new IllegalArgumentException("Invalid guard concurrency limits");
        }
        capacity = parallelism + queue;
        admission = new Semaphore(capacity, true); global = new Semaphore(parallelism, true);
        queueWaitNanos = TimeUnit.MILLISECONDS.toNanos(properties.getGuardQueueWaitMs());
        turnTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(properties.getGuardTurnTimeoutMs());
    }

    public Session start(List<InferenceRequest> requests, Function<InferenceRequest, String> inference) {
        List<InferenceRequest> frozen = List.copyOf(requests);
        if (frozen.stream().anyMatch(request -> request.purpose() != InferencePurpose.DECISION
                || (request.output() != InferenceRequest.Output.BOOLEAN && request.output() != InferenceRequest.Output.JSON_OBJECT))) {
            throw new IllegalArgumentException("Only typed guard requests may run concurrently");
        }
        if (frozen.isEmpty() || frozen.size() > 64 || frozen.size() > capacity) throw new IllegalStateException("Guard inference capacity exceeded");
        long now = clock.getAsLong(), queueDeadline = now + Math.min(queueWaitNanos, turnTimeoutNanos);
        try {
            if (!admission.tryAcquire(frozen.size(), Math.max(0, queueDeadline - clock.getAsLong()), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("Guard inference capacity deadline exceeded");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Guard inference interrupted");
        }
        var turn = new Semaphore(perTurn, true);
        Session session = new Session(now + turnTimeoutNanos);
        for (InferenceRequest request : frozen) {
            session.tasks.add(new Task(() -> {
                boolean ownTurn = false, ownGlobal = false;
                try {
                    ownTurn = acquire(turn, queueDeadline);
                    ownGlobal = acquire(global, queueDeadline);
                    org.slf4j.LoggerFactory.getLogger(GuardInferenceExecutor.class).info(
                            "latency trace={} request={} stage=inference_queue durationMs={}", request.traceId(), request.requestId(),
                            Math.max(0, clock.getAsLong() - now) / 1_000_000.0);
                    try (var trace = new LatencyTrace(request.traceId(), ignored -> {})) {
                        return inference.apply(request);
                    }
                } finally {
                    if (ownGlobal) global.release();
                    if (ownTurn) turn.release();
                }
            }));
        }
        try { session.tasks.forEach(workers::execute); }
        catch (RejectedExecutionException closed) {
            session.close(); throw new IllegalStateException("Guard inference executor is closed");
        }
        return session;
    }

    private boolean acquire(Semaphore semaphore, long deadline) throws InterruptedException {
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0 || !semaphore.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException("Guard inference queue deadline exceeded");
        }
        return true;
    }

    public final class Session implements AutoCloseable {
        private final List<Task> tasks = new ArrayList<>();
        private final long deadline;
        private Session(long deadline) { this.deadline = deadline; }
        public String await(int index) {
            try {
                long remaining = deadline - clock.getAsLong();
                if (remaining <= 0) throw new TimeoutException();
                return tasks.get(index).get(remaining, TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException interrupted) {
                close(); Thread.currentThread().interrupt(); throw new IllegalStateException("Guard inference interrupted");
            } catch (TimeoutException timeout) {
                close(); throw new IllegalStateException("Guard inference turn deadline exceeded");
            } catch (ExecutionException | CancellationException failed) {
                close(); throw new IllegalStateException("Required guard inference failed");
            }
        }
        @Override public void close() { tasks.forEach(task -> task.cancel(true)); }
    }

    private final class Task extends FutureTask<String> {
        private final AtomicBoolean entered = new AtomicBoolean();
        Task(Callable<String> work) { super(work); }
        @Override public void run() {
            if (!entered.compareAndSet(false, true)) return;
            try { super.run(); } finally { admission.release(); }
        }
        // Cancel-before-run releases admission; running cancellation releases only when the worker exits.
        @Override protected void done() { if (entered.compareAndSet(false, true)) admission.release(); }
    }

    @Override @PreDestroy public void close() { workers.shutdownNow(); }
}
