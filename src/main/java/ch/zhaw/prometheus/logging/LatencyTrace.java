package ch.zhaw.prometheus.logging;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Request-local, content-free timing. No agent data or cross-request cache is retained. */
public final class LatencyTrace implements AutoCloseable {
    public static final String TRACE_HEADER = "X-Prometheus-Trace-Id";
    public static final String BEHAVIOUR_HEADER = "X-Prometheus-Behaviour-Id";
    private static final Logger LOG = LoggerFactory.getLogger(LatencyTrace.class);
    private static final ThreadLocal<LatencyTrace> CURRENT = new ThreadLocal<>();
    private final LatencyTrace previous;
    private final String id;
    private final LongSupplier clock;
    private final Consumer<String> behaviourHeader;

    public LatencyTrace(String requestedId, Consumer<String> behaviourHeader) {
        this(requestedId, behaviourHeader, System::nanoTime);
    }

    LatencyTrace(String requestedId, Consumer<String> behaviourHeader, LongSupplier clock) {
        this.id = requestedId != null && requestedId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                ? requestedId.toLowerCase(java.util.Locale.ROOT) : UUID.randomUUID().toString();
        this.behaviourHeader = behaviourHeader;
        this.clock = clock;
        this.previous = CURRENT.get();
        CURRENT.set(this);
    }

    public String id() { return id; }
    public static String currentId() { return CURRENT.get() == null ? "unscoped" : CURRENT.get().id; }
    public static long now() { return CURRENT.get() == null ? System.nanoTime() : CURRENT.get().clock.getAsLong(); }
    public static double elapsedMs(long start) { return Math.max(0, now() - start) / 1_000_000.0; }

    public static <T> T measure(String stage, Supplier<T> work) {
        long start = now();
        boolean success = false;
        try {
            T value = work.get();
            success = true;
            return value;
        } finally {
            LOG.info("latency trace={} stage={} status={} durationMs={}", currentId(), stage,
                    success ? "ok" : "error", elapsedMs(start));
        }
    }

    public static void behaviour(UUID eventId) {
        LatencyTrace trace = CURRENT.get();
        if (trace != null && eventId != null) {
            trace.behaviourHeader.accept(eventId.toString());
            LOG.info("latency trace={} stage=behaviour eventId={}", trace.id, eventId);
        }
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
