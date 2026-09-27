package ch.zhaw.prometheus.application.live;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Acoustic sample activity, not an event-delivery gap, establishes a candidate boundary. */
public final class LiveTranscriptSegmenter {
    public enum Speaker { USER, ASSISTANT }
    public enum Closure { COMPLETE, INCOMPLETE, LATE }
    public record Fragment(String eventId, Speaker speaker, Long startMs, Long endMs, long receivedMs,
            long sequence, String text) {}
    public record Segment(UUID id, Speaker speaker, List<Fragment> fragments, Closure closure,
            String reason, long firstReceivedMs, long lastReceivedMs) {
        public Segment { fragments = List.copyOf(fragments); }
        public String text() { return fragments.stream().map(Fragment::text).collect(java.util.stream.Collectors.joining()); }
        public Long startMs() { return fragments.stream().map(Fragment::startMs).filter(java.util.Objects::nonNull).min(Long::compare).orElse(null); }
        public Long endMs() { return fragments.stream().map(Fragment::endMs).filter(java.util.Objects::nonNull).max(Long::compare).orElse(null); }
    }
    public static final int MAX_CHARACTERS = 2800, MAX_FRAGMENTS = 128, MAX_RECEIPTS = 8192;
    public static final long SILENCE_MS = 800, LATENESS_MS = 400, MAX_OPEN_MS = 30000, MAX_AUDIO_GAP_MS = 500;
    private static final Comparator<Fragment> ORDER = Comparator.comparing(Fragment::startMs, Comparator.nullsLast(Long::compare))
            .thenComparing(Fragment::endMs, Comparator.nullsLast(Long::compare)).thenComparingLong(Fragment::sequence);
    private final UUID session;
    private final EnumMap<Speaker, Track> tracks = new EnumMap<>(Speaker.class);
    private final LinkedHashSet<String> received = new LinkedHashSet<>();
    private boolean closed;
    private static final class Track {
        final List<Fragment> fragments = new ArrayList<>();
        long first = -1, last = -1, audioAt = -1, silenceSamples, frozenEnd = -1;
        String uncertainty;
    }
    public LiveTranscriptSegmenter(UUID session) {
        this.session = session;
        for (Speaker speaker : Speaker.values()) tracks.put(speaker, new Track());
    }
    public synchronized List<Segment> fragment(Fragment fragment) {
        if (closed) throw new IllegalStateException("Transcript capture is closed");
        if (fragment.eventId() == null || !fragment.eventId().matches("[A-Za-z0-9_:.\\-]{1,200}")
                || fragment.speaker() == null || fragment.text() == null || fragment.text().length() > MAX_CHARACTERS)
            throw new IllegalArgumentException("Invalid or oversized transcript fragment");
        if (received.contains(fragment.eventId())) return List.of();
        if (received.size() >= MAX_RECEIPTS) throw new IllegalStateException("Transcript receipt capacity reached");
        received.add(fragment.eventId());
        Track track = tracks.get(fragment.speaker());
        boolean validTime = fragment.startMs() != null && fragment.endMs() != null && fragment.startMs() >= 0
                && fragment.endMs() >= fragment.startMs() && fragment.endMs() <= 3_600_000;
        if (validTime && fragment.startMs() <= track.frozenEnd)
            return List.of(segment(fragment.speaker(), List.of(fragment), Closure.LATE, "late_after_commit"));
        if (track.fragments.isEmpty()) { track.first = fragment.receivedMs(); track.uncertainty = null; }
        if (track.fragments.size() >= MAX_FRAGMENTS || track.fragments.stream().mapToInt(f -> f.text().length()).sum()
                + fragment.text().length() > MAX_CHARACTERS) throw new IllegalStateException("Open transcript capacity reached");
        track.fragments.add(fragment); track.last = fragment.receivedMs(); track.silenceSamples = 0;
        if (!validTime) track.uncertainty = "missing_or_invalid_provider_timing";
        return List.of();
    }
    /** PCM is 24 kHz mono; raw samples are not retained. Call once per received chunk. */
    public synchronized void audio(Speaker speaker, int samples, boolean voiced, long receivedMs) {
        audio(speaker, samples, voiced ? 0 : samples, voiced, receivedMs);
    }
    public synchronized void audio(Speaker speaker, int samples, int trailingQuietSamples, boolean voiced, long receivedMs) {
        if (closed) return;
        if (samples < 0 || samples > 100000 || trailingQuietSamples < 0 || trailingQuietSamples > samples)
            throw new IllegalArgumentException("Invalid audio activity chunk");
        Track track = tracks.get(speaker);
        long previous = track.audioAt >= 0 ? track.audioAt : track.first;
        if (!track.fragments.isEmpty() && previous >= 0 && receivedMs - previous > samples / 24L + MAX_AUDIO_GAP_MS) {
            track.uncertainty = "audio_coverage_gap"; track.silenceSamples = 0;
        }
        track.audioAt = receivedMs;
        if (voiced) track.silenceSamples = trailingQuietSamples;
        else if (!track.fragments.isEmpty() && receivedMs >= track.last) track.silenceSamples += samples;
    }
    public synchronized void uncertain(Speaker speaker, String reason) {
        if (!tracks.get(speaker).fragments.isEmpty()) tracks.get(speaker).uncertainty = reason;
    }
    public synchronized List<Segment> interrupt(Speaker speaker, String reason) {
        Track track = tracks.get(speaker);
        return track.fragments.isEmpty() ? List.of() : List.of(finish(speaker, track, Closure.INCOMPLETE, reason));
    }
    public synchronized List<Segment> poll(long nowMs) {
        List<Segment> result = new ArrayList<>();
        for (var entry : tracks.entrySet()) {
            Track track = entry.getValue();
            if (track.fragments.isEmpty()) continue;
            if (nowMs - track.first > MAX_OPEN_MS) {
                result.add(finish(entry.getKey(), track, Closure.INCOMPLETE, "activity_or_transcript_timeout"));
            } else if (track.silenceSamples >= SILENCE_MS * 24 && nowMs - track.last >= LATENESS_MS
                    && nowMs - track.audioAt <= MAX_AUDIO_GAP_MS) {
                result.add(finish(entry.getKey(), track, track.uncertainty == null ? Closure.COMPLETE : Closure.INCOMPLETE,
                        track.uncertainty == null ? "observed_silence" : track.uncertainty));
            }
        }
        return List.copyOf(result);
    }
    public synchronized List<Segment> close() {
        if (closed) return List.of(); closed = true;
        List<Segment> result = new ArrayList<>();
        for (var entry : tracks.entrySet()) if (!entry.getValue().fragments.isEmpty())
            result.add(finish(entry.getKey(), entry.getValue(), Closure.INCOMPLETE, "capture_closed"));
        return List.copyOf(result);
    }
    private Segment finish(Speaker speaker, Track track, Closure closure, String reason) {
        Segment result = segment(speaker, track.fragments, closure, reason);
        if (result.endMs() != null && result.endMs() >= 0 && result.endMs() <= 3_600_000)
            track.frozenEnd = Math.max(track.frozenEnd, result.endMs());
        track.fragments.clear(); track.first = -1; track.last = -1; track.silenceSamples = 0; track.uncertainty = null;
        return result;
    }
    private Segment segment(Speaker speaker, List<Fragment> fragments, Closure closure, String reason) {
        String firstId = fragments.getFirst().eventId();
        var sorted = fragments.stream().sorted(ORDER).toList();
        UUID id = UUID.nameUUIDFromBytes((session + ":" + speaker + ":" + firstId).getBytes(StandardCharsets.UTF_8));
        return new Segment(id, speaker, sorted, closure, reason,
                fragments.stream().mapToLong(Fragment::receivedMs).min().orElseThrow(),
                fragments.stream().mapToLong(Fragment::receivedMs).max().orElseThrow());
    }
}
