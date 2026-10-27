package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.*;

/** Conversation remains ordinary; observation self-loops publish only admitted reactions. */
@Entity
public class SensoryPolicy extends PromptPolicy implements ExternalVoicePolicy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage sensoryStorage;
    protected SensoryPolicy() {}
    public SensoryPolicy(String prompt, String starter, Storage storage) {
        super(prompt, starter, DEFAULT_SUMMARISE_PROMPT); sensoryStorage = storage;
        setNonVerbalPlanPrompt(ValerianCorePrompts.NONVERBAL_PLAN);
        setNonVerbalGesturePrompt(DEFAULT_NONVERBAL_GESTURE_PROMPT);
    }
    @Override public Policy withOuterPolicy(Policy outer) {
        if (outer == null) return this;
        return new SensoryPolicy(outer.describe() + "\n" + describe(), starterPrompt(), sensoryStorage);
    }
    @Override public boolean supportsLiveState(State state) { return state.getClass() == State.class; }
    @Override public String voiceInstructions() {
        return ValerianCorePrompts.OUTER_STATE + "\n" + describe()
                + "\nAnswer direct questions normally. Automatic sensor comments require a current backend speaking occasion. "
                + "Fresh readings alone do not ask for another comment. Do not repeat questions or waiting acknowledgements.";
    }
    @Override public boolean livePendingAcknowledgements() { return false; }
    @Override public LiveActivityContext liveActivityContext() {
        var pending = SensoryRemarks.context(sensoryStorage, Instant.now());
        return pending == null ? new LiveActivityContext("Sensory demonstration active. No automatic comment is currently requested. "
                + "Answer the user's direct question using selected fresh evidence; explicit user statements override interpretations.", null) : pending;
    }
    @Override public String describe() {
        return super.describe() + (sensoryStorage != null && sensoryStorage.containsKey(SensoryRemarks.CORRECTION)
                ? "\nThe user corrected or declined facial interpretation. Do not contradict them or volunteer facial comments. Direct questions may be answered cautiously." : "");
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        if (!events.isEmpty() && (Event.TYPE_FACE_EMOTION.equals(events.toList().getLast().getType())
                || Event.TYPE_SOCIAL_CONTEXT.equals(events.toList().getLast().getType()))) {
            if (!sensoryStorage.containsKey(TaskMemory.REPLY)) return null;
            var plan = TaskMemory.plan(sensoryStorage.get(TaskMemory.REPLY)); sensoryStorage.remove(TaskMemory.REPLY); return plan;
        }
        return super.onStart(state, events, assembler, gateway, profile);
    }
    @Override public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        SensoryRemarks.userTurn(sensoryStorage, events);
        return super.onRespond(state, events, assembler, gateway, profile);
    }
    @Override public BehaviourPlan nonSpeechResponse(EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) {
        SensoryRemarks.userTurn(sensoryStorage, events); return super.nonSpeechResponse(events, assembler, gateway);
    }
    @Override public void reset() { SensoryRemarks.reset(sensoryStorage); if (sensoryStorage.containsKey(TaskMemory.REPLY)) sensoryStorage.remove(TaskMemory.REPLY); }
}
