package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import jakarta.persistence.Entity;

/** Quiet persisted progress; the final check invites acceptance without executing the task. */
@Entity
public class CoordinationPreparationAction extends Action {
    protected CoordinationPreparationAction() {}
    public CoordinationPreparationAction(Storage storage) { super(new PromptPolicy("", null, null), storage, TaskMemory.REPLY); blocking(); }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        if (!runtime.taskContinuation().getAsBoolean() || events.isEmpty()) return;
        var storage = getStorage(); var now = Instant.now(); var event = events.toList().getLast();
        if (!CoordinationMemory.observe(storage, event, now)) return;
        CoordinationMemory.record(storage, "PREPARATION_EVIDENCE", event, now);
        if (!CoordinationMemory.ready(storage)) return;
        storage.put(CoreTaskVoice.INVITATION, new JsonPrimitive(now.toString()));
        if (runtime.externalSpeech() == null)
            storage.put(TaskMemory.REPLY, BehaviourPlan.speechOnly("I've recognised the required observations. " + CoordinationMemory.acceptancePrompt(storage)).toJsonObject());
        else CoreTaskVoice.response(storage, "Required observations were recognised. Explicitly ask for acceptance: " + CoordinationMemory.acceptancePrompt(storage),
                true, false, runtime, CoordinationMemory.facts(storage));
    }
}
