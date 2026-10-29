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
        return super.inferenceGuidance(runtime) + "\nUse fresh supplied observations for any requested perception estimate. "
                + "Do not invent an example expression in the speech intent. If evidence is absent or expired, say it is unknown. "
                + "The speech field contains actual words to speak, never bracketed directions such as [Say exactly: ...]. "
                + "Accepted coordination roles and constraints (data): " + CoordinationMemory.facts(getStorage());
    }
    @Override protected void taskCommitted(String operation, TaskSpec task, TaskSpec.Rule rule, BehaviourPlan plan, PolicyRuntime runtime) {
        String spoken = plan.getSpeech(); // The shared Live publisher consumes this field before publishing nonverbal output.
        if ("ACT".equals(operation)) {
            if (TaskMemory.active(getStorage()) && Integer.parseInt(TaskMemory.text(getStorage(), TaskMemory.ACTIONS, "0")) >= task.maxActions()) {
                CoordinationMemory.phase(getStorage(), "COMPLETED"); CoordinationMemory.revise(getStorage());
            }
            CoordinationDelivery.await(getStorage(), plan, runtime);
        }
        super.taskCommitted(operation, task, rule, plan, runtime);
        if ("ACT".equals(operation)) {
            TaskResponse.purpose(getStorage(), TaskResponse.Purpose.CONTRIBUTION);
            if (runtime.externalSpeech() != null && spoken != null && !spoken.isBlank()
                    && getStorage().containsKey(CoreTaskVoice.RESPONSE) && getStorage().containsKey(TaskResponse.KEY)) {
                String intent = spoken.substring(0, Math.min(350, spoken.length()));
                String facts = "A fresh cue authorized this current contribution. Required spoken content: " + intent
                        + " Use this committed result, not an example from the agreement. Say it once; do not add a reflection or aside.";
                var response = getStorage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
                response.addProperty("facts", facts); response.addProperty("contributionOnly", true); getStorage().put(CoreTaskVoice.RESPONSE, response);
                TaskResponse.contribution(getStorage(), facts, literalSpeech(rule, spoken));
            }
        }
        CoordinationDelivery.attach(getStorage());
        if (rule != null) {
            var cue = new JsonObject(); cue.addProperty("rule", task.rules().indexOf(rule) + 1);
            cue.addProperty("eventType", rule.eventType()); cue.addProperty("effect", rule.effect().name());
            getStorage().put(CoordinationMemory.LAST_CUE, cue);
        }
    }
    private static String literalSpeech(TaskSpec.Rule rule, String spoken) {
        if (rule == null || spoken == null) return null;
        var match = java.util.regex.Pattern.compile("(?i)^say exactly:\\s*(?:\"([^\"\\r\\n]+)\"[.!]?|([^\"\\r\\n]+))$").matcher(rule.action().strip());
        if (!match.matches()) return null;
        String literal = match.group(1) == null ? match.group(2).strip() : match.group(1);
        return TaskResponse.sameWords(literal, spoken) ? literal : null;
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
