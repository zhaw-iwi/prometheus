package ch.zhaw.prometheus.application;

import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.application.live.LivePcmActivity;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import jakarta.annotation.PreDestroy;

/** Fast bounded sideband admission; database/agent work runs in ordered session mailboxes. */
@Service
public class LiveTranscriptCaptureService {
    public record Committed(UUID agentId, ExternalSpeech owner, LiveTranscriptIngressService.Outcome outcome) {}
    public record Trace(long serverMs, String phase, String receiptId, UUID segmentId, String outcome, Long providerStartMs, Long providerEndMs) {}
    public record Status(String state, int queued, int queueHighWater, long receipts, List<Trace> recent, long dropped) {}
    private final LiveTranscriptIngressService ingress;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<UUID, Capture> captures = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> { Thread thread = new Thread(runnable, "live-transcript"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    @org.springframework.beans.factory.annotation.Autowired
    public LiveTranscriptCaptureService(LiveTranscriptIngressService ingress, ApplicationEventPublisher events) {
        this(ingress, events, Clock.systemUTC());
    }
    LiveTranscriptCaptureService(LiveTranscriptIngressService ingress, ApplicationEventPublisher events, Clock clock) {
        this.ingress = ingress; this.events = events; this.clock = clock;
    }
    public Capture open(UUID agent, ExternalSpeech owner, Supplier<List<String>> state, Consumer<String> failure) {
        Capture capture = new Capture(agent, owner, state, failure);
        if (captures.putIfAbsent(owner.sessionId(), capture) != null) throw new IllegalStateException("Capture already open");
        return capture;
    }
    @Scheduled(fixedDelay = 100) public void poll() { captures.values().forEach(Capture::poll); }
    @PreDestroy public void shutdown() { captures.values().forEach(Capture::close); workers.shutdownNow(); captures.clear(); }

    public final class Capture {
        private final UUID agent;
        private final ExternalSpeech owner;
        private final Supplier<List<String>> state;
        private final Consumer<String> failure;
        private final LiveTranscriptSegmenter segmenter;
        private final Set<String> seen = new HashSet<>();
        private final Map<String, List<String>> fragmentStates = new HashMap<>();
        private final ArrayBlockingQueue<Runnable> work = new ArrayBlockingQueue<>(128);
        private final AtomicBoolean draining = new AtomicBoolean();
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private boolean closed, muted = true, failed;
        private long sequence;
        private Long outputEnd;
        private final java.util.ArrayDeque<Trace> traces = new java.util.ArrayDeque<>();
        private long dropped;
        private int queueHighWater;
        Capture(UUID agent, ExternalSpeech owner, Supplier<List<String>> state, Consumer<String> failure) {
            this.agent = agent; this.owner = owner; this.state = state; this.failure = failure;
            this.segmenter = new LiveTranscriptSegmenter(owner.sessionId());
        }
        public synchronized void receive(JsonObject event) {
            if (closed || failed) return;
            try {
                String type = event.get("type").getAsString(); long now = clock.millis();
                if (type.equals("session.input_audio.append") || type.equals("session.output_audio.delta")) {
                    Speaker speaker = type.contains("input_") ? Speaker.USER : Speaker.ASSISTANT;
                    if (speaker == Speaker.USER && muted) return;
                    if (speaker == Speaker.ASSISTANT) {
                        Long start = number(event, "start_ms"), end = number(event, "end_ms");
                        if (start == null || end == null || start < 0 || end < start)
                            segmenter.uncertain(speaker, "missing_audio_timing");
                        else {
                            if (outputEnd != null && start > outputEnd + 500) segmenter.uncertain(speaker, "provider_audio_gap");
                            outputEnd = end;
                        }
                    }
                    var activity = LivePcmActivity.measure(Base64.getDecoder().decode(event.get(speaker == Speaker.USER ? "audio" : "delta").getAsString()));
                    segmenter.audio(speaker, activity.samples(), activity.trailingQuietSamples(), activity.voiced(), now);
                } else if (type.equals("session.input_transcript.delta") || type.equals("session.output_transcript.delta")) {
                    Speaker speaker = type.contains("input_") ? Speaker.USER : Speaker.ASSISTANT;
                    boolean identified = event.has("event_id") && !event.get("event_id").isJsonNull();
                    String id = identified ? event.get("event_id").getAsString() : "local-missing-id-" + sequence;
                    if (seen.contains(id)) return;
                    if (seen.size() >= LiveTranscriptSegmenter.MAX_RECEIPTS) throw new IllegalStateException("Receipt capacity reached");
                    Fragment fragment = new Fragment(id, speaker, number(event, "start_ms"), number(event, "end_ms"), now,
                            ++sequence, event.get("delta").getAsString());
                    List<Segment> ready = segmenter.fragment(fragment); seen.add(id); fragmentStates.put(id, List.copyOf(state.get()));
                    trace("receipt_queued", id, null, null, fragment.startMs(), fragment.endMs());
                    enqueue(() -> {
                        boolean saved = ingress.receipt(agent, owner, fragment);
                        trace("receipt_processed", id, null, saved ? "persisted" : "duplicate_or_obsolete", fragment.startMs(), fragment.endMs());
                    });
                    if (!identified) segmenter.uncertain(speaker, "missing_provider_identity");
                    if (speaker == Speaker.USER && muted) segmenter.uncertain(speaker, "input_muted");
                    completed(ready);
                }
            } catch (RuntimeException invalid) { fail("capture_input_invalid_or_full"); }
        }
        public synchronized void inputMuted(boolean value) {
            if (value && !muted) completed(segmenter.interrupt(Speaker.USER, "input_muted"));
            muted = value;
        }
        synchronized void poll() {
            if (closed || failed) return;
            try { completed(segmenter.poll(clock.millis())); }
            catch (RuntimeException invalid) { fail("capture_poll_failed"); }
        }
        public synchronized CompletableFuture<Void> close() {
            if (!closed) {
                closed = true; captures.remove(owner.sessionId(), this);
                completed(segmenter.close());
                if (work.isEmpty() && !draining.get()) drained.complete(null);
            }
            return drained;
        }
        private void completed(List<Segment> segments) {
            for (Segment segment : segments) {
                String first = segment.fragments().stream().min(java.util.Comparator.comparingLong(Fragment::sequence)).orElseThrow().eventId();
                List<String> observedState = fragmentStates.getOrDefault(first, List.copyOf(state.get()));
                segment.fragments().forEach(fragment -> fragmentStates.remove(fragment.eventId()));
                trace("segment_queued", null, segment.id(), segment.closure().name(), segment.startMs(), segment.endMs());
                enqueue(() -> {
                    trace("agent_started", null, segment.id(), segment.speaker().name(), segment.startMs(), segment.endMs());
                    var result = ingress.commit(agent, owner, segment, observedState);
                    trace("agent_completed", null, segment.id(), result.map(LiveTranscriptIngressService.Outcome::status).orElse("OBSOLETE"), segment.startMs(), segment.endMs());
                    result.ifPresent(outcome -> {
                        trace("segment_outcome", null, segment.id(), outcome.reason(), segment.startMs(), segment.endMs());
                        events.publishEvent(new Committed(agent, owner, outcome));
                    });
                });
            }
        }
        private void enqueue(Runnable action) {
            if (!work.offer(action)) { fail("capture_queue_full"); return; }
            queueHighWater = Math.max(queueHighWater, work.size());
            startDrain();
        }
        private void startDrain() {
            if (!draining.compareAndSet(false, true)) return;
            try { workers.execute(() -> {
                try {
                    Runnable action;
                    while ((action = work.poll()) != null) {
                        try { action.run(); }
                        catch (RuntimeException problem) { synchronized (this) { fail("capture_processing_failed"); } }
                    }
                } finally {
                    draining.set(false);
                    synchronized (this) {
                        if (!work.isEmpty()) startDrain();
                        else if (closed) drained.complete(null);
                    }
                }
            }); } catch (RejectedExecutionException full) { draining.set(false); fail("capture_workers_full"); }
        }
        private void fail(String code) { if (!failed) { failed = true; failure.accept(code); } }
        public synchronized boolean failed() { return failed; }
        private synchronized void trace(String phase, String receipt, UUID segment, String outcome, Long start, Long end) {
            if (traces.size() == 64) { traces.removeFirst(); dropped++; }
            String safeReceipt = receipt != null && receipt.matches("[A-Za-z0-9_-]{1,128}") ? receipt : null;
            traces.addLast(new Trace(clock.millis(), phase, safeReceipt, segment, outcome, start, end));
        }
        public synchronized Status status() {
            return new Status(failed ? "failed" : closed ? drained.isDone() ? "closed" : "draining" : "active",
                    work.size(), queueHighWater, sequence, List.copyOf(traces), dropped);
        }
        private Long number(JsonObject event, String key) {
            if (!event.has(key) || event.get(key).isJsonNull()) return null;
            try { double value = event.get(key).getAsDouble(); return Double.isFinite(value) ? Math.round(value) : null; }
            catch (RuntimeException invalid) { return null; }
        }
    }
}
