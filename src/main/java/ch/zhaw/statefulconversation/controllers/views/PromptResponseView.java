package ch.zhaw.statefulconversation.controllers.views;

import java.util.List;

import ch.zhaw.statefulconversation.model.PromptResult;
import ch.zhaw.statefulconversation.model.Event;

public class PromptResponseView {
    private String stateName;
    private String systemPrompt;
    private List<Event> conversation;
    private boolean isActive;
    private boolean starting;

    public PromptResponseView(PromptResult promptResult, boolean isActive) {
        this.stateName = promptResult.getStateName();
        this.systemPrompt = promptResult.getSystemPrompt();
        this.conversation = promptResult.getConversation();
        this.isActive = isActive;
        this.starting = promptResult.isStarting();
    }

    public String getStateName() {
        return stateName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
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
