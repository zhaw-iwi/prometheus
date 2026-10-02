package ch.zhaw.prometheus.model.task;

import java.time.Instant;
import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;
import jakarta.persistence.Entity;

/** Blocking task update: validate completely before changing configuration or publishing an answer. */
@Entity
public class TaskUpdateAction extends Action {
    private boolean taskCue;
    protected TaskUpdateAction() {}
    public TaskUpdateAction(Storage storage, String instructions, boolean cue) {
        super(new PromptPolicy(instructions, null, null), storage, TaskMemory.SPEC); taskCue = cue; blocking();
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        Storage storage = getStorage(); Instant now = Instant.now();
        if (!taskCue && !events.isEmpty() && events.toList().getLast().getPayload().trim().matches(
                "(?i)(please )?(stop|cancel|pause)( (the |this |my )?(task|jokes|game|interaction|conversation)| telling jokes)?[.!]?")) {
            TaskMemory.put(storage, TaskMemory.PHASE, "COMPLETED"); TaskMemory.revise(storage);
            storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly("The task is stopped.").toJsonObject());
            return;
        }
        if (taskCue) {
            var rule = TaskDecision.matching(storage, events, runtime, now).orElse(null);
            if (rule == null) return;
            var spec = TaskSpec.parse(storage.get(TaskMemory.SPEC));
            int count = Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0"));
            boolean completed = rule.complete() || count >= spec.maxActions();
            String instruction = completed ? "The task is now complete. Briefly acknowledge completion; do not perform another task step."
                    : "Perform exactly this agreed action now: " + rule.action();
            String prompt = getPolicy().describe() + "\n" + TaskMemory.description(storage) + "\n" + instruction
                    + "\nReturn only the canonical JSON behaviour plan (speech, nonVerbal, optional motion.handSign). No task configuration fields.";
            ch.zhaw.prometheus.model.behaviour.BehaviourPlan plan;
            try {
                var raw = runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.BEHAVIOUR,
                        TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT));
                plan = TaskMemory.plan(JsonParser.parseString(raw));
            } catch (RuntimeException failure) {
                // Another camera sample must not turn a provider failure into an unbounded retry loop.
                storage.put(TaskMemory.DRAFT, spec.json()); TaskMemory.put(storage, TaskMemory.PHASE, "CONFIGURATION"); TaskMemory.revise(storage);
                storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(
                        "I've paused the task because I couldn't generate its next response. Ask me to resume when you're ready.").toJsonObject());
                org.slf4j.LoggerFactory.getLogger(TaskUpdateAction.class).warn("Generic task paused after behaviour generation failed");
                return;
            }
            storage.put(TaskMemory.REPLY, plan.toJsonObject());
            storage.put(TaskMemory.ACTIONS, new JsonPrimitive(count + (completed ? 0 : 1)));
            TaskMemory.put(storage, TaskMemory.AFTER, Instant.now().plusSeconds(rule.cooldownSeconds()).toString());
            if (completed) { TaskMemory.put(storage, TaskMemory.PHASE, "COMPLETED"); TaskMemory.revise(storage); }
            return;
        }
        String prompt = getPolicy().describe() + "\n" + TaskMemory.description(storage)
                + "\nSupported trigger fields: " + TaskSpec.FIELDS;
        String raw = runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.EXTRACTION,
                TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT));
        JsonObject update;
        TaskSpec task;
        ch.zhaw.prometheus.model.behaviour.BehaviourPlan reply;
        String operation;
        try {
            update = JsonParser.parseString(raw).getAsJsonObject();
            TaskSpec.exact(update, Set.of("operation", "task", "reply"));
            operation = TaskSpec.string(update, "operation", 12);
            if (!Set.of("KEEP", "PROPOSE", "ACTIVATE", "STOP").contains(operation)) throw new IllegalArgumentException();
            task = update.get("task").isJsonNull() ? null : TaskSpec.parse(update.get("task"));
            if (operation.equals("ACTIVATE") && task == null && storage.containsKey(TaskMemory.DRAFT)) task = TaskSpec.parse(storage.get(TaskMemory.DRAFT));
            if ((operation.equals("PROPOSE") || operation.equals("ACTIVATE")) && task == null) throw new IllegalArgumentException();
            if ((operation.equals("KEEP") || operation.equals("STOP")) && task != null) throw new IllegalArgumentException();
            reply = TaskMemory.plan(update.get("reply"));
        } catch (RuntimeException invalid) {
            // Do not publish an activation claim or corrupt the previous agreement on malformed output.
            storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(
                    "I couldn't validate that task configuration. Please restate the intended action and when it should happen.").toJsonObject());
            return;
        }
        switch (operation) {
            case "PROPOSE" -> { storage.put(TaskMemory.DRAFT, task.json()); TaskMemory.put(storage, TaskMemory.PHASE, "CONFIGURATION"); TaskMemory.revise(storage); }
            case "ACTIVATE" -> {
                storage.put(TaskMemory.SPEC, task.json());
                if (storage.containsKey(TaskMemory.DRAFT)) storage.remove(TaskMemory.DRAFT);
                storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
                TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.revise(storage);
            }
            case "STOP" -> { TaskMemory.put(storage, TaskMemory.PHASE, "COMPLETED"); TaskMemory.revise(storage); }
            default -> { /* Conversation/perception questions retain the existing task. */ }
        }
        storage.put(TaskMemory.REPLY, reply.toJsonObject());
        // Only observations captured after this response/cooldown can advance the task.
        TaskMemory.put(storage, TaskMemory.AFTER, Instant.now().plusSeconds(3).toString());
    }
}
