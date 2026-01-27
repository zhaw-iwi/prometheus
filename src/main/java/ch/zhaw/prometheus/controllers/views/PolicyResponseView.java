package ch.zhaw.prometheus.controllers.views;

import java.util.List;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyResult;

public class PolicyResponseView {
    private String stateName;
    private String systemPolicy;
    private List<Event> eventHistory;
    private boolean isActive;
    private boolean starting;

    public PolicyResponseView(PolicyResult policyResult, boolean isActive) {
        this.stateName = policyResult.getStateName();
        this.systemPolicy = policyResult.getSystemPolicy();
        this.eventHistory = policyResult.getEventHistory();
        this.isActive = isActive;
        this.starting = policyResult.isStarting();
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

    public boolean isActive() {
        return isActive;
    }

    public boolean isStarting() {
        return starting;
    }
}
