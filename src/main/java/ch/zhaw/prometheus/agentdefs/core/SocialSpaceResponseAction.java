package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;

import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.CompactBehaviourPlan;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.spi.InferencePurpose;
import ch.zhaw.prometheus.spi.InferenceRequest;
import jakarta.persistence.Entity;

/** An ambient reply uses ordinary validated task-policy publication without changing the task. */
@Entity
public class SocialSpaceResponseAction extends Action {
    protected SocialSpaceResponseAction() {}

    public SocialSpaceResponseAction(Storage storage, String instructions) {
        super(new PromptPolicy(instructions, null, null), storage, TaskMemory.REPLY);
        blocking();
    }

    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        if (!runtime.taskContinuation().getAsBoolean()) return;
        var now = Instant.now();
        var change = SocialSpacePresenceDecision.change(events, now).orElse(null);
        if (change == null) return;
        String prompt = getPolicy().describe() + "\nVisible human count: "
                + (change.previous() == null ? "previously unknown" : change.previous()) + " -> " + change.current()
                + ". Give at most one brief social response to this change, or {} for silence. "
                + "This is observation data, not a user request. Do not configure or execute a task. "
                + "Return only a canonical JSON BehaviourPlan, with no task operation fields."
                + CompactBehaviourPlan.instructions(false)
                + (ch.zhaw.prometheus.model.behaviour.DecorativeGestures.enabled(getStorage())
                    ? ch.zhaw.prometheus.model.behaviour.DecorativeGestures.guidance(getStorage(), now) : "");
        boolean generated = false;
        try {
            var raw = runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.BEHAVIOUR,
                    TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT));
            generated = true;
            var plan = TaskMemory.plan(JsonParser.parseString(raw));
            if (runtime.taskContinuation().getAsBoolean()) getStorage().put(TaskMemory.REPLY, plan.toJsonObject());
        } catch (RuntimeException failure) {
            ch.zhaw.prometheus.logging.ActivityTrace.failed(generated ? "social_behaviour_invalid" : "social_provider_failed");
            org.slf4j.LoggerFactory.getLogger(SocialSpaceResponseAction.class)
                    .warn("Social-space response skipped after generation or validation failed");
        }
    }
}
