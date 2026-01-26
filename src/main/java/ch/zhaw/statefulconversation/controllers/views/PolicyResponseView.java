package ch.zhaw.statefulconversation.controllers.views;

import java.util.List;

import ch.zhaw.statefulconversation.model.PolicyResult;
import ch.zhaw.statefulconversation.model.Event;

public class PolicyResponseView {
    private String stateName;
    private String systemPolicy;
    private List<Event> conversation;
    private boolean isActive;
    private boolean starting;

    public PolicyResponseView(PolicyResult policyResult, boolean isActive) {
        this.stateName = policyResult.getStateName();
        this.systemPolicy = policyResult.getSystemPolicy();
        this.conversation = policyResult.getConversation();
        this.isActive = isActive;
        this.starting = policyResult.isStarting();
    }

    public String getStateName() {
        return stateName;
    }

    public String getSystemPolicy() {
        return systemPolicy;
    }

    public List<Event> getConversation() {
        return conversation;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isStarting() {
        return starting;
    }
}
