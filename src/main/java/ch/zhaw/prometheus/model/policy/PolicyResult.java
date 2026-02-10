package ch.zhaw.prometheus.model.policy;

import java.util.List;

import ch.zhaw.prometheus.model.State;
public class PolicyResult {
    private final String stateName;
    private final List<PromptMessage> promptMessages;
    private final boolean starting;

    public PolicyResult(State state, List<PromptMessage> promptMessages) {
        this.stateName = state.getName();
        this.promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        this.starting = state.isStarting();
    }

    public String getStateName() {
        return stateName;
    }

    public List<PromptMessage> getPromptMessages() {
        return promptMessages;
    }

    public boolean isStarting() {
        return starting;
    }
}
