package ch.zhaw.prometheus.application.live;

import java.util.*;
import ch.zhaw.prometheus.application.LiveTranscriptIngressService.Outcome;

/** Delegation carries no utterance. It can report committed work, never initiate acknowledgement. */
public final class LiveDelegation {
    private record Request(String id, Long offset, long deadline, String revision) {}
    public record Reply(String id, String text) {}
    private final Set<String> seen = new HashSet<>();
    private final Map<String, Request> pending = new LinkedHashMap<>();
    private final ArrayDeque<Outcome> committed = new ArrayDeque<>();
    public synchronized void request(String id, Long offset, String revision, long now) {
        if (id == null || id.isBlank() || id.length() > 200) throw new IllegalArgumentException("Invalid delegation identity");
        if (seen.contains(id)) return;
        if (seen.size() >= 1024 || pending.size() >= 32) throw new IllegalStateException("Delegation capacity reached");
        seen.add(id); pending.put(id, new Request(id, offset, now + 5000, revision));
    }
    public synchronized void committed(Outcome outcome) {
        if (!"USER".equals(outcome.speaker())) return;
        if (committed.size() == 32) committed.removeFirst(); committed.addLast(outcome);
    }
    public synchronized long nextDeadline() {
        return pending.values().stream().mapToLong(Request::deadline).min().orElse(Long.MAX_VALUE);
    }
    public synchronized List<Reply> resolve(LiveContextSnapshot context, long now) {
        List<Reply> replies = new ArrayList<>();
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Request request = iterator.next();
            Outcome match = committed.stream().filter(value -> request.offset() != null && value.startMs() != null && value.endMs() != null
                    && value.startMs() <= request.offset() && request.offset() <= value.endMs() + 2000).reduce((a,b) -> b).orElse(null);
            if (match == null && now < request.deadline()) continue;
            String status = match == null ? "No complete user segment associated yet; task outcome is unconfirmed."
                    : "Latest nearby user segment " + match.segmentId() + " status=" + match.status()
                        + ". Timing association is approximate; it does not establish a new request.";
            replies.add(new Reply(request.id(), "PROMETHEUS current context revision " + context.revision() + "; state "
                    + String.join(" / ", context.statePath()) + ". " + status
                    + (request.revision().equals(context.revision()) ? "" : " Context changed since delegation; use current guidance, not an earlier result.")
                    + " Delegation did not run any additional action. Only explicit backend announcements confirm task results."));
            iterator.remove();
        }
        return List.copyOf(replies);
    }
}
