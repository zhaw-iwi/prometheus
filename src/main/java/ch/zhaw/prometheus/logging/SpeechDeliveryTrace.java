package ch.zhaw.prometheus.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Metadata only, independent of request-thread locals and servlet lifetimes. */
public final class SpeechDeliveryTrace {
    public static final String HEADER = "X-Prometheus-Speech-Delivery-Id";
    private static final int LIMIT = 256;
    private final UUID id = UUID.randomUUID();
    private final LongSupplier clock;
    private final long origin;
    private final List<Step> steps = new ArrayList<>();
    private String status = "pending", phase;
    private Double phaseStartedMs, finishedMs;
    private long sequence, bytesRead, bytesFlushed, dropped;

    public SpeechDeliveryTrace() { this(System::nanoTime); }
    public SpeechDeliveryTrace(LongSupplier clock) { this.clock = clock; this.origin = clock.getAsLong(); }
    public UUID id() { return id; }
    private double now() { return Math.max(0, clock.getAsLong() - origin) / 1_000_000.0; }

    public synchronized void begin(String phase) {
        this.status = "streaming";
        this.phase = phase;
        this.phaseStartedMs = now();
    }

    public synchronized void end(int bytes, long totalBytes) {
        double at = now();
        if ("read".equals(phase)) bytesRead = totalBytes;
        if ("flush".equals(phase)) bytesFlushed = totalBytes;
        steps.add(new Step(++sequence, phase, phaseStartedMs, at, bytes, totalBytes));
        if (steps.size() > LIMIT) { steps.remove(LIMIT / 2); dropped++; }
        phase = null;
        phaseStartedMs = null;
    }

    public synchronized void finish(boolean success) {
        this.status = success ? "complete" : "error";
        this.finishedMs = now();
        // A failed operation retains its phase and start, never the exception text.
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(1, id, status, phase, phaseStartedMs, finishedMs,
                bytesRead, bytesFlushed, dropped, List.copyOf(steps));
    }

    public record Step(long sequence, String phase, double startedMs, double completedMs, int bytes, long totalBytes) {}
    public record Snapshot(int version, UUID id, String status, String phase, Double phaseStartedMs,
            Double finishedMs, long bytesRead, long bytesFlushed, long dropped, List<Step> steps) {}
}
