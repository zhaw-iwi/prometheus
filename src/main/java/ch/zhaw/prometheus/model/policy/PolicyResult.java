package ch.zhaw.prometheus.model.policy;

import java.util.List;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.Event;

public class PolicyResult {
    private final String stateName;
    private final String systemPolicy;
    private final List<Event> eventHistory;
    private final boolean starting;

    public PolicyResult(State state, String systemPolicy, List<Event> eventHistory) {
        this.stateName = state.getName();
        this.systemPolicy = systemPolicy;
        this.eventHistory = eventHistory;
        this.starting = state.isStarting();
    }

    public String getStateName() {
        return stateName;
    }

    public String getSystemPolicy() {
        return systemPolicy;
    }

    public List<Event> getEventHistory() {
        return eventHistory;
    }

    public boolean isStarting() {
        return starting;
    }
}
