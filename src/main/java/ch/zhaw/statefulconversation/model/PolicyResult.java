package ch.zhaw.statefulconversation.model;

import java.util.List;

public class PolicyResult {
    private final String stateName;
    private final String systemPolicy;
    private final List<Event> conversation;
    private final boolean starting;

    public PolicyResult(State state, String systemPolicy, List<Event> conversation) {
        this.stateName = state.getName();
        this.systemPolicy = systemPolicy;
        this.conversation = conversation;
        this.starting = state.isStarting();
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

    public boolean isStarting() {
        return starting;
    }
}
