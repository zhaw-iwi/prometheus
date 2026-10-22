package ch.zhaw.prometheus.application.live;

import java.util.*;
import ch.zhaw.prometheus.application.LiveTranscriptIngressService.Outcome;

/** Session-owned pending recovery, never another input admission or action queue. */
public final class LiveClarifications {
    public record Pending(UUID segment, Long endMs, List<String> statePath, long expiresAt) {}
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    private long latestRequest = Long.MIN_VALUE;

    public synchronized boolean captured(Outcome outcome, List<String> statePath, long now) {
        if (!"USER".equals(outcome.speaker())) return true;
        // Only a durably admitted later request supersedes recovery, never assistant text/noise.
        if ("COMPLETE".equals(outcome.status()) && "acknowledged".equals(outcome.reason())
                && outcome.startMs() != null && !LiveShortReplyContext.bareReply(outcome.text()))
            latestRequest = Math.max(latestRequest, outcome.startMs());
        pending.removeIf(item -> now >= item.expiresAt() || item.endMs() != null && item.endMs() < latestRequest);
        if (!"CLARIFICATION".equals(outcome.status()) || seen.contains(outcome.segmentId())) return true;
        if (pending.size() == 32) return false;
        seen.add(outcome.segmentId());
        if (seen.size() > 128) seen.remove(seen.iterator().next());
        pending.addLast(new Pending(outcome.segmentId(), outcome.endMs(), List.copyOf(statePath), now + 10000));
        return true;
    }
    public synchronized Pending poll() { return pending.pollFirst(); }
    public synchronized boolean valid(Pending request, List<String> statePath, long now) {
        return now < request.expiresAt()
                && (request.statePath().isEmpty() || request.statePath().equals(statePath))
                && (request.endMs() == null || request.endMs() >= latestRequest);
    }
    public synchronized int size() { return pending.size(); }
    public synchronized void clear() { pending.clear(); seen.clear(); }
}
