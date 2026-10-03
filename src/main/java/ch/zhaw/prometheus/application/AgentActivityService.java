package ch.zhaw.prometheus.application;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import ch.zhaw.prometheus.logging.ActivityTrace;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.logging.LatencyTrace;
import jakarta.annotation.PreDestroy;

/** Bounded, transient operation telemetry. No repository, provider or user content. */
@Service
public class AgentActivityService {
    public record Entry(long sequence, long serverMs, UUID operationId, String traceId, UUID sessionId,
            UUID sourceId, UUID eventId, UUID spanId, String stage, String outcome, Double durationMs, Map<String,Object> details) {}
    public record Active(UUID operationId, String stage, boolean foreground, long startedAt, double elapsedMs) {}
    public record Cue(String reason, long occurrences, long serverMs, UUID sourceId) {}
    public record Snapshot(int version, UUID agentId, UUID epoch, long serverMs, long revision,
            List<Active> active, List<Entry> recent, long dropped, long omittedOperations, Cue cue) {}
    private final AgentMonitorBroadcaster monitor;
    private final Clock clock;
    private final LongSupplier nanos;
    private final LinkedHashMap<UUID, Journal> journals = new LinkedHashMap<>(16, .75f, true);
    private final ExecutorService sends = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> { var t = new Thread(runnable, "activity-stream"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    @org.springframework.beans.factory.annotation.Autowired
    public AgentActivityService(AgentMonitorBroadcaster monitor) { this(monitor, Clock.systemUTC(), System::nanoTime); }
    AgentActivityService(AgentMonitorBroadcaster monitor, Clock clock, LongSupplier nanos) {
        this.monitor = monitor; this.clock = clock; this.nanos = nanos; monitor.activitySource(this::snapshot);
    }
    private final class Journal {
        final UUID agent;
        UUID epoch;
        final LinkedHashMap<UUID, Operation> operations = new LinkedHashMap<>();
        final ArrayDeque<Entry> entries = new ArrayDeque<>();
        long sequence, dropped, omitted, lastSent, touched = clock.millis();
        boolean dirty, sending;
        Cue cue;
        Journal(UUID agent) { this.agent = agent; }
    }
    private synchronized Journal journal(UUID agent) {
        var value = journals.get(agent);
        if (value != null) return value;
        if (journals.size() >= 128) {
            var eldest = journals.entrySet().stream().filter(e -> e.getValue().operations.isEmpty()).findFirst();
            if (eldest.isEmpty()) return null;
            journals.remove(eldest.get().getKey());
        }
        value = new Journal(agent); journals.put(agent, value); return value;
    }
    public <T> T call(UUID agent, String kind, boolean foreground, Supplier<T> work) {
        if (ActivityTrace.current() != null && agent.equals(ActivityTrace.current().agent())) return work.get();
        Operation operation;
        synchronized (this) {
            Journal journal = journal(agent);
            if (journal == null) operation = null;
            else if (journal.operations.size() >= 32) { journal.omitted++; journal.dirty = true; operation = null; }
            else { operation = new Operation(journal, kind, foreground); journal.operations.put(operation.id, operation);
                if (foreground) operation.record(null, "queued", "started", null, Map.of()); }
        }
        if (operation == null) return work.get();
        try (var scope = ActivityTrace.attach(operation)) {
            try { T result = work.get(); operation.finish("complete"); return result; }
            catch (RuntimeException | Error failure) { operation.finish("failed"); throw failure; }
        }
    }
    private final class Operation implements ActivityTrace.Sink {
        final Journal journal;
        final UUID id = UUID.randomUUID();
        final String trace = safe(LatencyTrace.currentId());
        final boolean foreground;
        final String kind;
        String failure;
        final long started = nanos.getAsLong(), startedAt = clock.millis();
        final Map<UUID,String> stages = new LinkedHashMap<>();
        UUID epoch, session, source, event;
        boolean visible, finished;
        Operation(Journal journal, String kind, boolean foreground) {
            this.journal = journal; this.foreground = foreground; this.kind = safe(kind); this.epoch = journal.epoch;
            visible = foreground;
        }
        @Override public UUID agent() { return journal.agent; }
        boolean current() { return !finished && journal.operations.get(id) == this; }
        void record(UUID span, String stage, String outcome, Double duration, Map<String,Object> details) {
            if (journal.entries.size() == 128) { journal.entries.removeFirst(); journal.dropped++; }
            journal.entries.addLast(new Entry(++journal.sequence, clock.millis(), id, trace, session, source, event,
                    span, stage, outcome, duration, Map.copyOf(details)));
            journal.dirty = true; journal.touched = clock.millis();
        }
        @Override public AutoCloseable stage(String name) {
            String stage = safe(name); UUID span = UUID.randomUUID(); long start = nanos.getAsLong();
            synchronized (AgentActivityService.this) {
                if (!current() || stage == null) return () -> {};
                if (stage.equals("thinking")) visible = true;
                if (!visible) return () -> {};
                stages.put(span, stage); record(span, stage, "started", null, Map.of());
            }
            return () -> { synchronized (AgentActivityService.this) {
                if (!current()) return;
                stages.remove(span); record(span, stage, "finished", elapsed(start), Map.of());
            } };
        }
        @Override public void bind(UUID epoch, UUID session, UUID source) {
            synchronized (AgentActivityService.this) {
                if (!current()) return;
                if (epoch != null && journal.epoch != null && !epoch.equals(journal.epoch)) {
                    journal.operations.clear(); journal.operations.put(id, this); journal.entries.clear(); journal.cue = null;
                }
                if (epoch != null) journal.epoch = epoch;
                this.epoch = journal.epoch;
                if (session != null) this.session = session;
                if (source != null) this.source = source;
                if (visible) record(null, "correlation", "observed", null, Map.of());
            }
        }
        @Override public void cue(String reason, UUID source) {
            synchronized (AgentActivityService.this) {
                if (!current() || safe(reason) == null) return;
                Cue before = journal.cue;
                boolean same = before != null && before.reason().equals(reason);
                journal.cue = new Cue(reason, same ? before.occurrences() + 1 : 1, clock.millis(), source);
                if (!same) record(null, "cue", reason, null, Map.of());
            }
        }
        @Override public void event(UUID event) { synchronized (AgentActivityService.this) {
            if (!current()) return; this.event = event;
            if (visible) record(null, "behaviour", "prepared", null, Map.of());
        } }
        @Override public void inference(String request, String purpose, String model, String effort, Integer input, Integer output, int requests, boolean success) {
            synchronized (AgentActivityService.this) {
                if (!current()) return;
                var details = new LinkedHashMap<String,Object>();
                for (var pair : List.of(new String[]{"requestId",request},new String[]{"purpose",purpose},new String[]{"model",model},new String[]{"effort",effort}))
                    if (safe(pair[1]) != null) details.put(pair[0], safe(pair[1]));
                if (input != null && input >= 0) details.put("inputTokens", input);
                if (output != null && output >= 0) details.put("outputTokens", output);
                details.put("providerRequests", Math.max(0, requests));
                record(null, "inference", success ? "complete" : "failed", null, details);
            }
        }
        @Override public void failed(String reason) { synchronized (AgentActivityService.this) {
            if (current()) failure = safe(reason);
        } }
        void finish(String outcome) { synchronized (AgentActivityService.this) {
            if (!current()) return;
            if (visible || outcome.equals("failed")) record(null, "operation", failure == null ? outcome : "failed", elapsed(started),
                    failure == null ? Map.of("kind", kind == null ? "unknown" : kind) : Map.of("kind", kind == null ? "unknown" : kind, "reason", failure));
            journal.operations.remove(id); finished = true; journal.touched = clock.millis();
        } }
        String stage() { return stages.containsValue("thinking") ? "thinking" : stages.values().stream().reduce((a,b)->b).orElse("processing"); }
    }
    private double elapsed(long start) { return Math.max(0, nanos.getAsLong()-start)/1_000_000.0; }
    private static String safe(String value) { return value != null && value.matches("[A-Za-z0-9_.:/-]{1,96}") ? value : null; }
    public synchronized Snapshot snapshot(UUID agent) {
        Journal journal = journals.get(agent);
        if (journal == null) return new Snapshot(1, agent, null, clock.millis(), 0, List.of(), List.of(), 0, 0, null);
        return view(journal);
    }
    private Snapshot view(Journal journal) {
        return new Snapshot(1, journal.agent, journal.epoch, clock.millis(), journal.sequence,
                journal.operations.values().stream().filter(o -> o.visible).map(o -> new Active(o.id, o.stage(), o.foreground, o.startedAt, elapsed(o.started))).toList(),
                List.copyOf(journal.entries), journal.dropped, journal.omitted, journal.cue);
    }
    @Scheduled(fixedDelay = 250)
    public synchronized void flush() {
        journals.values().removeIf(j -> j.operations.isEmpty() && clock.millis()-j.touched > 30*60_000);
        for (Journal journal : journals.values()) {
            if (journal.sending || (!journal.dirty && (journal.operations.isEmpty() || clock.millis()-journal.lastSent < 1000))) continue;
            var snapshot = view(journal); journal.sending = true; journal.dirty = false; journal.lastSent = clock.millis();
            try { sends.execute(() -> { try { monitor.publishActivity(journal.agent, snapshot); }
                finally { synchronized (AgentActivityService.this) { journal.sending = false; } } }); }
            catch (RejectedExecutionException full) { journal.sending = false; journal.dirty = true; }
        }
    }
    @PreDestroy public void close() { sends.shutdownNow(); }
}
