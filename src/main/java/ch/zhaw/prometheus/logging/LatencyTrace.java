package ch.zhaw.prometheus.logging;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Request-local, content-free timing. No agent data or cross-request cache is retained. */
public final class LatencyTrace implements AutoCloseable {
    public static final String TRACE_HEADER = "X-Prometheus-Trace-Id";
    public static final String BEHAVIOUR_HEADER = "X-Prometheus-Behaviour-Id";
    public static final String TIMING_HEADER = "X-Prometheus-Timing";
    private static final Logger LOG = LoggerFactory.getLogger(LatencyTrace.class);
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final ThreadLocal<LatencyTrace> CURRENT = new ThreadLocal<>();
    private final LatencyTrace previous;
    private final String id;
    private final LongSupplier clock;
    private final Consumer<String> behaviourHeader;
    private final Measurements measurements;

    public LatencyTrace(String requestedId, Consumer<String> behaviourHeader) {
        this(requestedId, behaviourHeader, System::nanoTime);
    }

    LatencyTrace(String requestedId, Consumer<String> behaviourHeader, LongSupplier clock) {
        this(requestedId, behaviourHeader, clock, null);
    }

    private LatencyTrace(String requestedId, Consumer<String> behaviourHeader, LongSupplier clock,
            Measurements measurements) {
        this.id = requestedId != null && requestedId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                ? requestedId.toLowerCase(java.util.Locale.ROOT) : UUID.randomUUID().toString();
        this.behaviourHeader = behaviourHeader;
        this.clock = clock;
        this.measurements = measurements == null ? new Measurements(clock.getAsLong()) : measurements;
        this.previous = CURRENT.get();
        CURRENT.set(this);
    }

    public String id() { return id; }
    public static String currentId() { return CURRENT.get() == null ? "unscoped" : CURRENT.get().id; }
    public static long now() { return CURRENT.get() == null ? System.nanoTime() : CURRENT.get().clock.getAsLong(); }
    public static double elapsedMs(long start) { return Math.max(0, now() - start) / 1_000_000.0; }

    /** Explicit propagation to pure inference workers; never retains a servlet response or agent. */
    public static Supplier<LatencyTrace> continuation(String id) {
        LatencyTrace parent = CURRENT.get();
        Measurements shared = parent != null && parent.id.equals(id) ? parent.measurements : null;
        LongSupplier clock = parent == null ? System::nanoTime : parent.clock;
        return () -> new LatencyTrace(id, ignored -> {}, clock, shared);
    }

    public static void record(String stage, double durationMs, boolean success) {
        record(stage, durationMs, success, null, null, null, null, null, null);
    }

    public static void record(String stage, double durationMs, boolean success, String request,
            String purpose, String model, String effort, Integer promptTokens, Integer completionTokens) {
        record(stage, durationMs, success, request, purpose, model, effort, promptTokens, completionTokens, null);
    }

    public static void record(String stage, double durationMs, boolean success, String request,
            String purpose, String model, String effort, Integer promptTokens, Integer completionTokens, Integer providerRequests) {
        LatencyTrace trace = CURRENT.get();
        if (trace == null || !Double.isFinite(durationMs) || durationMs < 0) return;
        trace.measurements.add(new Span(identifier(stage),
                Math.max(0, elapsedMs(trace.measurements.start) - durationMs), durationMs,
                success ? "ok" : "error", identifier(request), identifier(purpose), identifier(model),
                identifier(effort), promptTokens, completionTokens, providerRequests, null, null));
    }

    /** A bounded, content-free snapshot taken before response headers are committed. */
    public static String responseHeader() {
        LatencyTrace trace = CURRENT.get();
        return trace == null ? null : trace.measurements.header(elapsedMs(trace.measurements.start));
    }

    private static String identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:/-]{1,96}") ? value : null;
    }

    private record Span(String stage, double offsetMs, double durationMs, String status, String request,
            String purpose, String model, String effort, Integer promptTokens, Integer completionTokens, Integer providerRequests,
            String scope, String originTrace) {}

    /** Content-free completed inference evidence, possibly carried across acknowledge/generate requests. */
    public static final class CapturedInference {
        private final String origin;
        private final java.util.List<Span> spans;
        private CapturedInference(String origin, java.util.List<Span> spans) { this.origin = origin; this.spans = spans; }
        public void attach() {
            LatencyTrace trace = CURRENT.get();
            if (trace == null) return;
            for (Span span : spans) trace.measurements.add(new Span(span.stage, 0, span.durationMs, span.status,
                    span.request, span.purpose, span.model, span.effort, span.promptTokens, span.completionTokens,
                    span.providerRequests, "speculative", origin));
        }
    }

    public static CapturedInference captureInference() {
        LatencyTrace trace = CURRENT.get();
        if (trace == null) return new CapturedInference(null, java.util.List.of());
        synchronized (trace.measurements) {
            return new CapturedInference(trace.id, trace.measurements.spans.stream()
                    .filter(span -> "inference".equals(span.stage)).toList());
        }
    }

    private static final class Measurements {
        private final long start;
        private final ArrayList<Span> spans = new ArrayList<>();
        private boolean truncated;
        private Measurements(long start) { this.start = start; }
        synchronized void add(Span span) {
            if (spans.size() < 64) spans.add(span); else truncated = true;
        }
        synchronized String header(double durationMs) {
            var copy = new ArrayList<>(spans);
            boolean omitted = truncated;
            while (true) {
                var payload = new com.google.gson.JsonObject();
                payload.addProperty("version", 1);
                payload.addProperty("durationMs", durationMs);
                payload.addProperty("truncated", omitted);
                payload.add("spans", GSON.toJsonTree(copy));
                String encoded = Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
                if (encoded.length() <= 6000) return encoded;
                copy.removeLast();
                omitted = true;
            }
        }
    }

    public static <T> T measure(String stage, Supplier<T> work) {
        long start = now();
        boolean success = false;
        try {
            T value = work.get();
            success = true;
            return value;
        } finally {
            record(stage, elapsedMs(start), success);
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
