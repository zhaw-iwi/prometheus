package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;
import jakarta.persistence.Entity;

/** Interpret one user turn, validate the whole update, then commit it under the existing agent-turn boundary. */
@Entity
public class CoordinationUpdateAction extends Action {
    private static final Set<String> OPERATIONS = Set.of("KEEP", "PROPOSE", "START", "STEP", "COMPLETE", "PAUSE", "RESUME", "STOP", "DISCARD", "REFLECT");
    protected CoordinationUpdateAction() {}
    public CoordinationUpdateAction(Storage storage, String instructions) {
        super(new PromptPolicy(instructions, null, null), storage, TaskMemory.REPLY); blocking();
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage();
        if (events.isEmpty() || !runtime.taskContinuation().getAsBoolean()) return;
        var event = events.toList().getLast(); String text = event.getPayload(); Instant now = Instant.now();
        SensoryRemarks.userTurn(storage, events);
        if (runtime.externalSpeech() == null) CoordinationMemory.remove(storage, TaskResponse.KEY, CoreTaskVoice.RESPONSE);
        String invitation = CoreTaskVoice.prompt(storage, events, runtime.externalSpeech(), CoordinationMemory.DRAFT);
        boolean assent = CoreTaskVoice.assent(text);
        if (CoreTaskVoice.acknowledgement(text) || assent && invitation == null) {
            CoreTaskVoice.clearAnswered(storage);
            if (runtime.externalSpeech() != null && !(assent && storage.containsKey(CoordinationMemory.DRAFT))) {
                // A polite tail must not replace an in-flight spoken action or its capture boundary.
                storage.put(TaskMemory.REPLY, new JsonObject()); return;
            }
            answer(assent && storage.containsKey(CoordinationMemory.DRAFT)
                    ? "Please say explicitly whether to start the current proposal; its preparation must be ready first." : "Understood.", runtime);
            return;
        }
        String operation; CoordinationAgreement proposed = null; BehaviourPlan reply;
        boolean inferred = false;
        try {
            String command = CoreTaskVoice.normalize(text);
            if (command.matches("(please )?(stop|cancel|pause|resume)( (the |this |my )?(task|interaction|conversation))?")) {
                operation = command.replaceFirst("^please ", "").split(" ")[0].toUpperCase(java.util.Locale.ROOT);
                if (operation.equals("CANCEL")) operation = "STOP";
                reply = BehaviourPlan.speechOnly(switch (operation) {
                    case "PAUSE" -> "The task is paused; our agreement and progress are preserved.";
                    case "RESUME" -> "The task is resumed. We can continue from the agreed progress.";
                    default -> "The task is stopped. We can reflect on it or plan something else.";
                });
            } else {
                String prompt = getPolicy().describe() + "\n" + CoordinationMemory.facts(storage)
                        + "\nPrevious accepted agreement (data): " + (storage.containsKey(CoordinationMemory.AGREEMENT) ? storage.get(CoordinationMemory.AGREEMENT) : "none")
                        + "\nRecent committed operations (requests/plans, not physical evidence): " + (storage.containsKey(CoordinationMemory.LOG) ? storage.get(CoordinationMemory.LOG) : "none")
                        + "\nLatest user reflection (their report, not a measured outcome): " + (storage.containsKey(CoordinationMemory.REVIEW) ? storage.get(CoordinationMemory.REVIEW) : "none")
                        + "\nSupported sensor fields: " + TaskSpec.FIELDS
                        + (runtime.externalSpeech() == null ? "" : "\nLive owns spoken wording. In reply.speech describe the required contribution in at most 350 characters; do not supply a long finished script. Use supported nonverbal/motion normally.")
                        + (assent ? "\nInterpret this short reply ONLY against the completed utterance: " + new JsonPrimitive(invitation)
                                + ". It is not automatically consent. Only KEEP or START of the unchanged saved proposal is permitted. A question about future activation or changing a proposal does not authorize starting now." : "")
                        + DecorativeGestures.guidance(storage, now);
                String raw = runtime.languageModelGateway().infer(new InferenceRequest(InferencePurpose.EXTRACTION,
                        TaskMemory.messages(events, prompt, runtime.promptMessageAssembler(), now), InferenceRequest.Output.JSON_OBJECT));
                if (!runtime.taskContinuation().getAsBoolean()) return;
                inferred = true;
                var update = JsonParser.parseString(raw).getAsJsonObject();
                CoordinationAgreement.exact(update, Set.of("operation", "agreement", "reply"));
                operation = update.get("operation").getAsString();
                proposed = update.get("agreement").isJsonNull() ? null : CoordinationAgreement.parse(update.get("agreement"));
                reply = TaskMemory.plan(update.get("reply"));
            }
            validate(operation, proposed, assent, invitation);
            if (runtime.externalSpeech() != null && reply.getSpeech() != null && reply.getSpeech().length() > 350)
                throw new IllegalArgumentException("Coordination speech intent too long");
        } catch (RuntimeException failure) {
            if (!runtime.taskContinuation().getAsBoolean()) return;
            ch.zhaw.prometheus.logging.ActivityTrace.failed(inferred ? "coordination_update_invalid" : "coordination_update_failed");
            org.slf4j.LoggerFactory.getLogger(CoordinationUpdateAction.class).warn("Coordination update rejected; agreement and preparation preserved");
            answer("I couldn't apply that update. Our agreement and preparation are preserved; we can clarify the missing requirement or retry.", runtime);
            return;
        }
        if (!runtime.taskContinuation().getAsBoolean()) return;
        commit(operation, proposed, reply, event, now, runtime);
    }
    private void validate(String operation, CoordinationAgreement proposed, boolean assent, String invitation) {
        var storage = getStorage(); var accepted = CoordinationMemory.agreement(storage, CoordinationMemory.AGREEMENT);
        if (!OPERATIONS.contains(operation) || (operation.equals("PROPOSE") != (proposed != null))) throw new IllegalArgumentException();
        if (assent && (!Set.of("KEEP", "START").contains(operation)
                || operation.equals("START") && CoreTaskVoice.deferredQuestion(invitation))) throw new IllegalArgumentException();
        switch (operation) {
            case "START" -> { if (!CoordinationMemory.ready(storage)) throw new IllegalArgumentException("Preparation incomplete"); }
            case "STEP" -> {
                if (!TaskMemory.active(storage) || accepted == null || accepted.reactions() != null || storage.containsKey(CoordinationMemory.DRAFT))
                    throw new IllegalArgumentException("No active dialogue task");
            }
            case "COMPLETE" -> { if (!TaskMemory.active(storage)) throw new IllegalArgumentException(); }
            case "PAUSE" -> { if (!TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage))) throw new IllegalArgumentException(); }
            case "RESUME" -> { if (!"PAUSED".equals(TaskMemory.phase(storage)) || accepted == null || storage.containsKey(CoordinationMemory.DRAFT)) throw new IllegalArgumentException(); }
            case "DISCARD" -> { if (!storage.containsKey(CoordinationMemory.DRAFT)) throw new IllegalArgumentException(); }
            default -> { }
        }
    }
    private void commit(String operation, CoordinationAgreement proposed, BehaviourPlan reply, Event event, Instant now, PolicyRuntime runtime) {
        var storage = getStorage(); boolean live = runtime.externalSpeech() != null;
        boolean action = operation.equals("STEP"), gate = false;
        switch (operation) {
            case "PROPOSE" -> {
                if (!storage.containsKey(CoordinationMemory.DRAFT)) storage.put(CoordinationMemory.PREVIOUS_PHASE, new JsonPrimitive(TaskMemory.phase(storage)));
                storage.put(CoordinationMemory.DRAFT, proposed.json()); CoordinationMemory.phase(storage, "CONFIGURATION");
                CoordinationMemory.resetPreparation(storage, now);
            }
            case "START" -> {
                var accepted = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT);
                storage.put(CoordinationMemory.AGREEMENT, accepted.json());
                storage.put(CoordinationMemory.ACCEPTED_EVIDENCE, CoordinationMemory.evidence(storage));
                CoordinationMemory.remove(storage, CoordinationMemory.DRAFT, CoordinationMemory.PREVIOUS_PHASE, CoordinationMemory.OUTCOME,
                        CoordinationMemory.REVIEW, CoordinationMemory.LAST_CUE, TaskMemory.PAUSE_REASON);
                if (accepted.reactions() == null) CoordinationMemory.remove(storage, TaskMemory.SPEC);
                else storage.put(TaskMemory.SPEC, accepted.reactions().json());
                action = accepted.reactions() == null || accepted.reactions().firstAction() == TaskSpec.FirstAction.IMMEDIATE;
                storage.put(TaskMemory.ACTIONS, new JsonPrimitive(action ? 1 : 0)); CoordinationMemory.phase(storage, "RUNNING");
                gate = accepted.reactions() != null;
                if (!action) reply = BehaviourPlan.speechOnly("The task is active and waiting for a fresh matching cue. No task action has been performed yet.");
            }
            case "STEP" -> storage.put(TaskMemory.ACTIONS, new JsonPrimitive(Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0")) + 1));
            case "PAUSE" -> CoordinationMemory.phase(storage, "PAUSED");
            case "RESUME" -> { CoordinationMemory.phase(storage, "RUNNING"); gate = storage.containsKey(TaskMemory.SPEC); CoordinationMemory.remove(storage, TaskMemory.PAUSE_REASON); }
            case "STOP", "COMPLETE" -> {
                CoordinationMemory.phase(storage, "COMPLETED");
                storage.put(CoordinationMemory.OUTCOME, new JsonPrimitive(operation.equals("STOP") ? "CANCELLED" : "USER_REPORTED_COMPLETION"));
                CoordinationMemory.remove(storage, CoordinationMemory.DRAFT, CoordinationMemory.PREVIOUS_PHASE);
            }
            case "DISCARD" -> {
                String previous = TaskMemory.text(storage, CoordinationMemory.PREVIOUS_PHASE, "CONFIGURATION");
                CoordinationMemory.remove(storage, CoordinationMemory.DRAFT);
                CoordinationMemory.phase(storage, !storage.containsKey(CoordinationMemory.AGREEMENT) ? "CONFIGURATION"
                        : previous.equals("COMPLETED") ? "COMPLETED" : "PAUSED");
                CoordinationMemory.remove(storage, CoordinationMemory.PREVIOUS_PHASE);
                CoordinationMemory.resetPreparation(storage, now);
            }
            case "REFLECT" -> {
                var report = new JsonObject(); report.addProperty("userReport", event.getPayload().substring(0, Math.min(600, event.getPayload().length())));
                report.addProperty("at", now.toString()); storage.put(CoordinationMemory.REVIEW, report);
            }
            default -> { }
        }
        var accepted = CoordinationMemory.agreement(storage, CoordinationMemory.AGREEMENT);
        if (action && accepted != null && accepted.reactions() == null
                && Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0")) >= accepted.dialogueLimit()) {
            CoordinationMemory.phase(storage, "COMPLETED"); storage.put(CoordinationMemory.OUTCOME, new JsonPrimitive("LIMIT_REACHED"));
        }
        if (!operation.equals("KEEP") && !operation.equals("REFLECT")) {
            CoordinationMemory.revise(storage);
            CoordinationMemory.remove(storage, CoreTaskVoice.INVITATION, CoreTaskVoice.SPOKEN_PROMPT, CoreTaskVoice.RESPONSE, TaskResponse.KEY);
        }
        if (operation.equals("PROPOSE") && CoordinationMemory.ready(storage)) storage.put(CoreTaskVoice.INVITATION, new JsonPrimitive(now.toString()));
        if (Set.of("START", "RESUME").contains(operation)) {
            storage.put(TaskMemory.SESSION, new JsonPrimitive(live ? runtime.externalSpeech().sessionId().toString() : ""));
            storage.put(TaskMemory.AFTER, new JsonPrimitive(now.plusSeconds(3).toString()));
        }
        // A task begun in Text becomes associated with Live when a user continues it there.
        if (live && TaskMemory.active(storage)) storage.put(TaskMemory.SESSION, new JsonPrimitive(runtime.externalSpeech().sessionId().toString()));
        if (!operation.equals("KEEP")) CoordinationMemory.record(storage, operation, event, now);
        String speech = reply.getSpeech();
        if (!action && !operation.equals("KEEP")) {
            var json = reply.toJsonObject(); json.remove("motion"); reply = BehaviourPlan.fromJson(json.toString());
        }
        if (live) reply.setSpeech(null);
        storage.put(TaskMemory.REPLY, reply.toJsonObject());
        if (action || DecorativeGestures.requested(singleEvent(event))) storage.put(DecorativeGestures.REQUIRED, new JsonPrimitive(true));
        if (operation.equals("KEEP") || operation.equals("REFLECT")) { CoreTaskVoice.clearAnswered(storage); return; }
        String facts = switch (operation) {
            case "PROPOSE" -> "Proposal saved, not active. Explain the proposed goal, roles, success criterion, limits and lifecycle. Clarify remaining questions or request missing preparation. If ready, ask to start now.";
            case "START" -> action ? "Task accepted. Give its first agreed contribution now. " + intent(speech)
                    : "Task accepted and armed. Wait for a new cue; do not perform its future action now.";
            case "STEP" -> "One agreed dialogue contribution is authorized. " + intent(speech);
            case "COMPLETE" -> "The user's completion report ended the task. Reflect on available evidence; this is not independently measured success.";
            case "STOP" -> "The task was cancelled. No further task actions are authorized. Conversation and reflection remain available.";
            case "PAUSE" -> "The task is paused; agreement and budget are preserved. Explicit resume is required.";
            case "RESUME" -> "The task is resumed with its existing agreement and remaining budget. No extra task action was performed.";
            case "DISCARD" -> "The draft was discarded. The previous agreement retains its recorded outcome; only an unfinished, paused task can resume.";
            default -> throw new IllegalStateException("Unsupported coordination continuation");
        };
        if (action && "COMPLETED".equals(TaskMemory.phase(storage))) facts += " The action budget is exhausted; do not claim that this proves success.";
        CoreTaskVoice.response(storage, facts, false, gate, runtime, CoordinationMemory.facts(storage) + "\n" + facts);
    }
    private static String intent(String speech) { return speech == null ? "No spoken task action was requested." : "Required contribution: " + speech.substring(0, Math.min(350, speech.length())); }
    private static EventHistory singleEvent(Event event) { var history = new EventHistory(); history.appendEvent(event); return history; }
    private void answer(String text, PolicyRuntime runtime) {
        getStorage().put(TaskMemory.REPLY, runtime.externalSpeech() == null ? BehaviourPlan.speechOnly(text).toJsonObject() : new JsonObject());
        if (runtime.externalSpeech() != null) CoreTaskVoice.response(getStorage(), text, false, false, runtime, CoordinationMemory.facts(getStorage()));
    }
}
