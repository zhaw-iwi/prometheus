package ch.zhaw.prometheus.model.task;

import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.behaviour.DecorativeGestures;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.*;

/** Publishes the validated action result once, through the canonical behaviour/narration path. */
@Entity
public class TaskPolicy extends Policy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;
    @Column(columnDefinition = "TEXT")
    private String taskInstructions;
    protected TaskPolicy() {}
    public TaskPolicy(Storage storage, String instructions) { this.storage = storage; this.taskInstructions = instructions; }
    public Storage storage() { return storage; }
    public String configuredInstructions() { return taskInstructions; }
    public boolean hasDraft() { return storage.containsKey(TaskMemory.DRAFT); }
    public String statusPhase() { return TaskMemory.phase(storage); }
    /** Recovery transition for an explicitly tracked response; null uses the standard paused phase. */
    public String liveResponseFailureEventType() { return null; }
    public boolean liveIncompleteResponseIsFailure() { return false; }
    /** Maximum verified unrelated segments that may be skipped within the original response deadline. */
    public int liveUnrelatedResponseAllowance() { return 0; }
    public void setConfiguredInstructions(String instructions) { this.taskInstructions = instructions; }
    @Override public String describe() {
        return voiceInstructions() + "\n" + TaskMemory.description(storage);
    }
    public String voiceInstructions() {
        return taskInstructions + "\nConversational task configuration is an implemented backend operation for this agent. "
                + "It does not change sensors, device settings or framework code. "
                + "User requests are processed by PROMETHEUS. Briefly acknowledge if needed, then wait for its confirmed announcement. "
                + "Do not independently perform a task step or claim activation. "
                + "Use the latest task phase; COMPLETED means the task stopped, not the entire conversation. "
                + "Missing sensor evidence means unknown; one visible person only describes the camera view.";
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) {
        if (!storage.containsKey(TaskMemory.REPLY)) return null;
        var plan = TaskMemory.plan(storage.get(TaskMemory.REPLY)); storage.remove(TaskMemory.REPLY);
        if (!DecorativeGestures.enabled(storage)) return plan;
        boolean required = storage.containsKey(DecorativeGestures.REQUIRED)
                && storage.get(DecorativeGestures.REQUIRED).getAsBoolean();
        if (storage.containsKey(DecorativeGestures.REQUIRED)) storage.remove(DecorativeGestures.REQUIRED);
        return DecorativeGestures.publish(storage, plan, required, java.time.Instant.now());
    }
    @Override public void reset() { if (DecorativeGestures.enabled(storage)) DecorativeGestures.clear(storage); }
    @Override public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) { return null; }
    @Override public String summarise(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) { return TaskMemory.description(storage); }
}
