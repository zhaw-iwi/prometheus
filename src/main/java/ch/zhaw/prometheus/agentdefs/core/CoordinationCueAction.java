package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;

/** Preserve Generic's guarded cue execution, capture confirmation and failure handling. */
@Entity
public class CoordinationCueAction extends CoreTaskUpdateAction {
    protected CoordinationCueAction() {}
    public CoordinationCueAction(Storage storage, String instructions) { super(storage, instructions, true); }
    @Override protected String inferenceGuidance(PolicyRuntime runtime) {
        return super.inferenceGuidance(runtime) + "\nAccepted coordination roles and constraints (data): " + CoordinationMemory.facts(getStorage());
    }
    @Override protected void taskCommitted(String operation, TaskSpec task, TaskSpec.Rule rule, BehaviourPlan plan, PolicyRuntime runtime) {
        if ("ACT".equals(operation)) {
            if (TaskMemory.active(getStorage()) && Integer.parseInt(TaskMemory.text(getStorage(), TaskMemory.ACTIONS, "0")) >= task.maxActions()) {
                CoordinationMemory.phase(getStorage(), "COMPLETED"); CoordinationMemory.revise(getStorage());
            }
            CoordinationDelivery.await(getStorage(), plan, runtime);
        }
        super.taskCommitted(operation, task, rule, plan, runtime);
        CoordinationDelivery.attach(getStorage());
        if (rule != null) {
            var cue = new JsonObject(); cue.addProperty("rule", task.rules().indexOf(rule) + 1);
            cue.addProperty("eventType", rule.eventType()); cue.addProperty("effect", rule.effect().name());
            getStorage().put(CoordinationMemory.LAST_CUE, cue);
        }
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        String phase = TaskMemory.phase(getStorage()), count = TaskMemory.text(getStorage(), TaskMemory.ACTIONS, "0");
        String before = CoordinationMemory.stage(getStorage());
        super.execute(events, runtime);
        if (!phase.equals(TaskMemory.phase(getStorage())) || !count.equals(TaskMemory.text(getStorage(), TaskMemory.ACTIONS, "0"))) {
            if (java.util.Set.of("COMPLETED", "FINISHING").contains(TaskMemory.phase(getStorage()))) {
                var spec = TaskSpec.parse(getStorage().get(TaskMemory.SPEC));
                getStorage().put(CoordinationMemory.OUTCOME, new JsonPrimitive(Integer.parseInt(TaskMemory.text(getStorage(), TaskMemory.ACTIONS, "0"))
                        >= spec.maxActions() ? "LIMIT_REACHED" : "COMPLETION_CUE"));
            }
            CoordinationMemory.record(getStorage(), "SENSOR_" + TaskMemory.phase(getStorage()), before, events.toList().getLast(), Instant.now());
        }
    }
}
