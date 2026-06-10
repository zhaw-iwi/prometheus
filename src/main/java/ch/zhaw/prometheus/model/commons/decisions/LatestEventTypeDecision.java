package ch.zhaw.prometheus.model.commons.decisions;

import java.util.List;

import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

@Entity
public class LatestEventTypeDecision extends Decision {
    private String eventType;

    protected LatestEventTypeDecision() {
    }

    public LatestEventTypeDecision(String eventType) {
        super(new NoOpPolicy());
        this.eventType = eventType;
    }

    @Override
    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        if (this.eventType == null || this.eventType.isBlank() || events == null || events.isEmpty()) {
            return false;
        }
        List<Event> source = events.toList();
        Event latest = source.get(source.size() - 1);
        return latest != null && this.eventType.equals(latest.getType());
    }

    @Override
    public String toString() {
        return "LatestEventTypeDecision(" + this.eventType + ")";
    }
}
