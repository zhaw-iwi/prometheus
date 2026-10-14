package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.Optional;

import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import jakarta.persistence.Entity;

/** Compare grouping counts locally: movement and the derived social event cannot double-trigger a reply. */
@Entity
public class SocialSpacePresenceDecision extends Decision {
    protected SocialSpacePresenceDecision() {}

    public SocialSpacePresenceDecision(Storage storage) { super(new TaskPolicy(storage, "")); }

    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        var storage = ((TaskPolicy) getPolicy()).storage();
        if (TaskMemory.active(storage) || "PAUSED".equals(TaskMemory.phase(storage))) return false;
        return change(events, Instant.now()).isPresent();
    }

    static Optional<PresenceChange> change(EventHistory history, Instant now) {
        if (history == null || history.isEmpty()) return Optional.empty();
        var events = history.toList();
        var latest = events.getLast();
        if (!Event.TYPE_SOCIAL_GROUPING.equals(latest.getType()) || !TaskMemory.fresh(latest, now)) return Optional.empty();
        Integer current = count(latest);
        if (current == null) return Optional.empty();
        Integer before = null;
        Instant previousTime = null;
        for (int i = events.size() - 2; i >= 0; i--) {
            var previous = events.get(i);
            if (!Event.TYPE_SOCIAL_GROUPING.equals(previous.getType())) continue;
            Integer candidate = count(previous);
            var time = TaskMemory.observed(previous);
            if (candidate == null || time == null || time.isAfter(now.plusSeconds(2))) continue;
            // A delayed/invalid frame must not replace the most recent known count and cause
            // a second greeting when the next ordinary sample arrives.
            if (previousTime == null || time.isAfter(previousTime)) {
                previousTime = time;
                before = candidate;
            }
        }
        if (previousTime != null && (!TaskMemory.observed(latest).isAfter(previousTime) || before.equals(current)))
            return Optional.empty();
        return before == null && current == 0 ? Optional.empty() : Optional.of(new PresenceChange(before, current));
    }

    private static Integer count(Event event) {
        try {
            var value = JsonParser.parseString(event.getPayload()).getAsJsonObject().get("humanCount");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            int count = value.getAsBigDecimal().intValueExact();
            return count < 0 ? null : count;
        } catch (RuntimeException invalid) { return null; }
    }

    record PresenceChange(Integer previous, int current) {}
}
