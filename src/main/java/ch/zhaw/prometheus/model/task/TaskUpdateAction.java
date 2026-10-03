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
        TaskMemory.trace(getStorage());
        try { update(events, runtime); }
        finally { TaskMemory.trace(getStorage()); }
    }
    private void update(EventHistory events, PolicyRuntime runtime) {
        Storage storage = getStorage(); Instant now = Instant.now();
        if (cancelled(storage, runtime)) return;
        if (!taskCue && !events.isEmpty() && events.toList().getLast().getPayload().trim().matches(
                "(?i)(please )?(stop|cancel|pause|resume)( (the |this |my )?(task|interaction|conversation))?[.!]?")) {
            String command = events.toList().getLast().getPayload().trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("^please ", "");
            String reply;
            if (command.startsWith("resume")) {
                if (!"PAUSED".equals(TaskMemory.phase(storage)) || !storage.containsKey(TaskMemory.SPEC)) {
                    reply = "There is no paused task to resume. We can configure a new agreement.";
                } else {
                    try {
                        TaskSpec.parse(storage.get(TaskMemory.SPEC)).executable();
                        TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.revise(storage);
                        TaskMemory.put(storage, TaskMemory.AFTER, now.toString());
                        TaskMemory.bindSession(storage, runtime.externalSpeech());
                        reply = "The task is resumed. I am waiting for a fresh matching cue.";
                    } catch (IllegalArgumentException invalid) {
                        reply = "The saved agreement needs a rule correction before resuming. Its description is preserved; please ask me to revise it.";
                    }
                }
            } else {
                boolean pause = command.startsWith("pause");
                if (pause && !TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage))) reply = "There is no active task to pause.";
                else {
                    TaskMemory.put(storage, TaskMemory.PHASE, pause ? "PAUSED" : "COMPLETED"); TaskMemory.revise(storage);
                    reply = pause ? "The task is paused. Ask me to resume when you are ready." : "The task is stopped.";
                }
            }
            storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(reply).toJsonObject());
            return;
        }
        if (taskCue) {
            var rule = TaskDecision.matching(storage, events, runtime, now).orElse(null);
            if (rule == null) return;
            var spec = TaskSpec.parse(storage.get(TaskMemory.SPEC));
            if (rule.effect() == TaskSpec.Effect.WAIT) {
                TaskMemory.put(storage, TaskMemory.PHASE, "WAITING"); TaskMemory.revise(storage);
                ch.zhaw.prometheus.logging.ActivityTrace.cue("task_waiting", events.toList().getLast().getId());
                return; // A temporary condition never spends an action or calls a model.
            }
            int count = Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0"));
            boolean completed = rule.complete() || count >= spec.maxActions();
            String instruction = completed ? "The task is now complete. Briefly acknowledge completion; do not perform another task step."
                    : "Perform exactly this agreed action now: " + rule.action();
            String prompt = getPolicy().describe() + "\n" + TaskMemory.description(storage) + "\n" + instruction
                    + "\nReturn only the canonical JSON behaviour plan (speech, nonVerbal, optional motion.handSign). No task configuration fields.";
            ch.zhaw.prometheus.model.behaviour.BehaviourPlan plan;
            boolean generated = false;
            try {
                var raw = runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.BEHAVIOUR,
                        TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT));
                if (cancelled(storage, runtime)) return;
                generated = true;
                plan = TaskMemory.plan(JsonParser.parseString(raw));
            } catch (RuntimeException failure) {
                if (cancelled(storage, runtime)) return;
                ch.zhaw.prometheus.logging.ActivityTrace.failed(generated ? "task_behaviour_invalid" : "task_provider_failed");
                // Another camera sample must not turn a provider failure into an unbounded retry loop.
                TaskMemory.put(storage, TaskMemory.PHASE, "PAUSED"); TaskMemory.revise(storage);
                storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(
                        "I've paused the task because I couldn't generate its next response. Ask me to resume when you're ready.").toJsonObject());
                org.slf4j.LoggerFactory.getLogger(TaskUpdateAction.class).warn("Generic task paused after behaviour generation failed");
                return;
            }
            storage.put(TaskMemory.REPLY, plan.toJsonObject());
            if ("WAITING".equals(TaskMemory.phase(storage))) { TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.revise(storage); }
            storage.put(TaskMemory.ACTIONS, new JsonPrimitive(count + (completed ? 0 : 1)));
            TaskMemory.put(storage, TaskMemory.AFTER, Instant.now().plusSeconds(rule.cooldownSeconds()).toString());
            if (completed) { TaskMemory.put(storage, TaskMemory.PHASE, "COMPLETED"); TaskMemory.revise(storage); }
            return;
        }
        String prompt = getPolicy().describe() + "\n" + TaskMemory.description(storage)
                + "\nSupported trigger fields: " + TaskSpec.FIELDS;
        var request = new InferenceRequest(InferencePurpose.EXTRACTION,
                TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT);
        String raw;
        try { raw = runtime.languageModelGateway().infer(request); }
        catch (RuntimeException failure) {
            if (cancelled(storage, runtime)) return;
            ch.zhaw.prometheus.logging.ActivityTrace.failed("task_provider_failed");
            storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(
                    "I couldn't process that update. I've kept the existing agreement and proposal; please ask me to try again.").toJsonObject());
            return;
        }
        if (cancelled(storage, runtime)) return;
        JsonObject update;
        TaskSpec task;
        ch.zhaw.prometheus.model.behaviour.BehaviourPlan reply;
        String operation;
        String validationStage = "envelope";
        try {
            update = JsonParser.parseString(raw).getAsJsonObject();
            TaskSpec.exact(update, Set.of("operation", "task", "reply"));
            validationStage = "operation";
            operation = TaskSpec.string(update, "operation", 12);
            if (!Set.of("KEEP", "PROPOSE", "ACTIVATE", "STOP", "PAUSE", "RESUME").contains(operation)) throw new IllegalArgumentException();
            validationStage = "task";
            task = update.get("task").isJsonNull() ? null : TaskSpec.parse(update.get("task")).executable();
            if (operation.equals("ACTIVATE") && task == null && storage.containsKey(TaskMemory.DRAFT)) task = TaskSpec.parse(storage.get(TaskMemory.DRAFT)).executable();
            if ((operation.equals("PROPOSE") || operation.equals("ACTIVATE")) && task == null) throw new IllegalArgumentException();
            if (Set.of("KEEP", "STOP", "PAUSE", "RESUME").contains(operation) && task != null) throw new IllegalArgumentException();
            if (operation.equals("PAUSE") && !TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage))) throw new IllegalArgumentException();
            if (operation.equals("RESUME")) {
                if (!"PAUSED".equals(TaskMemory.phase(storage)) || !storage.containsKey(TaskMemory.SPEC)) throw new IllegalArgumentException();
                TaskSpec.parse(storage.get(TaskMemory.SPEC)).executable();
            }
            validationStage = "reply";
            reply = TaskMemory.plan(update.get("reply"));
        } catch (RuntimeException invalid) {
            ch.zhaw.prometheus.logging.ActivityTrace.failed("task_configuration_invalid");
            // Do not publish an activation claim or corrupt the previous agreement on malformed output.
            org.slf4j.LoggerFactory.getLogger(TaskUpdateAction.class).warn(
                    "Generic task update rejected trace={} request={} stage={} reason={}",
                    request.traceId(), request.requestId(), validationStage, validationReason(invalid));
            String recovery = storage.containsKey(TaskMemory.DRAFT)
                    ? "I couldn't validate my response. I've kept our proposed plan unchanged; you don't need to repeat it. Please ask me to try again."
                    : storage.containsKey(TaskMemory.SPEC)
                    ? "I couldn't validate that update. I've kept the existing task unchanged. Please ask me to try again."
                    : "I couldn't validate my proposed configuration. Your request is still in our conversation; please ask me to try again.";
            storage.put(TaskMemory.REPLY, ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(
                    recovery).toJsonObject());
            return;
        }
        try (var activity = ch.zhaw.prometheus.logging.ActivityTrace.stage("updating_task")) {
        switch (operation) {
            case "PROPOSE" -> { storage.put(TaskMemory.DRAFT, task.json()); TaskMemory.put(storage, TaskMemory.PHASE, "CONFIGURATION"); TaskMemory.revise(storage); }
            case "ACTIVATE" -> {
                storage.put(TaskMemory.SPEC, task.json());
                if (storage.containsKey(TaskMemory.DRAFT)) storage.remove(TaskMemory.DRAFT);
                storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
                TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.revise(storage);
            }
            case "STOP" -> { TaskMemory.put(storage, TaskMemory.PHASE, "COMPLETED"); TaskMemory.revise(storage); }
            case "PAUSE" -> { TaskMemory.put(storage, TaskMemory.PHASE, "PAUSED"); TaskMemory.revise(storage); }
            case "RESUME" -> { TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.revise(storage); }
            default -> { /* Conversation/perception questions retain the existing task. */ }
        }
        ch.zhaw.prometheus.logging.ActivityTrace.cue(operation.equals("ACTIVATE") ? "waiting_for_cue" :
                TaskMemory.phase(storage).equals("COMPLETED") ? "task_completed" : "task_configuration", null);
        }
        storage.put(TaskMemory.REPLY, reply.toJsonObject());
        TaskMemory.bindSession(storage, runtime.externalSpeech());
        // Only observations captured after this response/cooldown can advance the task.
        if (operation.equals("ACTIVATE") || operation.equals("RESUME"))
            TaskMemory.put(storage, TaskMemory.AFTER, Instant.now().plusSeconds(3).toString());
    }
    private boolean cancelled(Storage storage, PolicyRuntime runtime) {
        if (runtime.taskContinuation().getAsBoolean() || (taskCue && !TaskMemory.sessionBound(storage))) return false;
        TaskMemory.pauseForSession(storage, "live_session_ended");
        if (storage.containsKey(TaskMemory.REPLY)) storage.remove(TaskMemory.REPLY);
        ch.zhaw.prometheus.logging.ActivityTrace.cue("session_work_discarded", null);
        return true;
    }
    private static String validationReason(RuntimeException invalid) {
        // Only fixed codes are logged: Gson/other exception messages may contain private output.
        return switch (String.valueOf(invalid.getMessage())) {
            case "Invalid gesture" -> "gesture_shape";
            case "Unsupported expression" -> "expression";
            case "Unsupported gaze" -> "gaze";
            case "Invalid output intensity" -> "intensity";
            case "Unsupported task motion" -> "motion";
            case "Unsupported task output modality" -> "output_modality";
            case "Unsupported nonverbal field" -> "nonverbal_field";
            default -> "invalid_value";
        };
    }
}
