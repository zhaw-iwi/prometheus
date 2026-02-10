package ch.zhaw.prometheus.controllers.views;

import java.util.List;

import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PolicyResult;

public class PolicyResponseView {
    private String stateName;
    private List<PromptMessage> promptMessages;
    private boolean isActive;
    private boolean starting;

    public PolicyResponseView(PolicyResult policyResult, boolean isActive) {
        this.stateName = policyResult.getStateName();
        this.promptMessages = policyResult.getPromptMessages();
        this.isActive = isActive;
        this.starting = policyResult.isStarting();
    }

    public String getStateName() {
        return stateName;
    }

    public List<PromptMessage> getPromptMessages() {
        return promptMessages;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isStarting() {
        return starting;
    }
}
