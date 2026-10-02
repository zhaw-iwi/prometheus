package ch.zhaw.prometheus.model.task;

import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
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
    @Override public String describe() {
        return taskInstructions + "\n" + TaskMemory.description(storage) + "\n"
                + "Conversational task configuration is an implemented backend operation for this agent. "
                + "It does not change sensors, device settings or framework code. "
                + "User requests are processed by PROMETHEUS. Briefly acknowledge if needed, then wait for its confirmed announcement. "
                + "Do not independently perform a task step, tell an additional joke, or claim activation. "
                + "Use the latest task phase; COMPLETED means the task stopped, not the entire conversation. "
                + "Missing sensor evidence means unknown; one visible person only describes the camera view.";
    }
    @Override public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) {
        if (!storage.containsKey(TaskMemory.REPLY)) return null;
        var plan = TaskMemory.plan(storage.get(TaskMemory.REPLY)); storage.remove(TaskMemory.REPLY); return plan;
    }
    @Override public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) { return null; }
    @Override public String summarise(State state, EventHistory events, PromptMessageAssembler assembler, LanguageModelGateway gateway) { return TaskMemory.description(storage); }
}
