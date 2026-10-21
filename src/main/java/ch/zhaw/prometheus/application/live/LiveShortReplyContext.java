package ch.zhaw.prometheus.application.live;

import java.time.Instant;
import java.util.List;

/** Admission evidence only: never infers a request or a completed physical response. */
public final class LiveShortReplyContext {
    public record Spoken(String statePath, Long endMs, Instant firstReceivedAt, Instant recordedAt) {}
    private LiveShortReplyContext() {}

    public static boolean ambiguous(List<String> observedState, List<String> currentState,
            Long inputStart, Instant inputReceived, List<Spoken> recent,
            Instant latestAnnouncement, boolean activityOwned) {
        if (!observedState.equals(currentState) || inputStart == null) return true;
        // Preserve the legacy conservative rule. Fact-owning applications no longer treat
        // every historical announcement as an outstanding question for the whole session.
        if (!activityOwned && latestAnnouncement != null) return true;
        if (recent.stream().anyMatch(s -> s.endMs() == null || s.recordedAt() == null)) return true;
        var previous = recent.stream().max(java.util.Comparator.comparing(Spoken::recordedAt));
        if (previous.isEmpty()) return true;
        var spoken = previous.get();
        return spoken.endMs() >= inputStart
                || !java.util.Objects.equals(spoken.statePath(), String.join(" / ", observedState))
                || spoken.firstReceivedAt() == null || spoken.recordedAt() == null
                || !spoken.recordedAt().isBefore(inputReceived)
                || latestAnnouncement != null && latestAnnouncement.isAfter(spoken.firstReceivedAt());
    }
}
