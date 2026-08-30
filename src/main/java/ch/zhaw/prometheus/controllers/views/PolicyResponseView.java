package ch.zhaw.prometheus.controllers.views;

import java.util.List;

import ch.zhaw.prometheus.model.policy.PromptMessage;

public class PolicyResponseView {
    private String stateName;
    private List<PromptMessage> promptMessages;
    private boolean isActive;
    private boolean starting;

    public PolicyResponseView(String stateName, List<PromptMessage> promptMessages, boolean isActive,
            boolean starting) {
        this.stateName = stateName;
        this.promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        this.isActive = isActive;
        this.starting = starting;
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
