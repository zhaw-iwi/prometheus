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
    private static final Set<String> OPERATIONS = Set.of("KEEP", "PROPOSE", "REPLACE", "PROPOSE_AND_START", "START", "STEP", "COMPLETE", "PAUSE", "RESUME", "STOP", "DISCARD", "REFLECT", "RETURN_TO_CONVERSATION", "RESTART", "STATUS");
    private static final class UnexpectedTerms extends IllegalArgumentException {}
    protected CoordinationUpdateAction() {}
    public CoordinationUpdateAction(Storage storage, String instructions) {
        super(new PromptPolicy(instructions, null, null), storage, TaskMemory.REPLY); blocking();
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage();
        if (events.isEmpty() || !runtime.taskContinuation().getAsBoolean()) return;
        var event = events.toList().getLast(); String text = event.getPayload(); Instant now = Instant.now();
        String revision = TaskMemory.text(storage, TaskMemory.REVISION, "0");
        // Acoustic completion is not semantic completeness. These clear fragments cannot authorize a change.
        if (CoreTaskVoice.normalize(text).matches("(a|an|the)( dry)?|(please )?(tell me|make it|change it to|can you|i want)( a| an| the)?")) {
            answer("Please finish that request; the agreement and state are unchanged.", runtime); return;
        }
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
            if (command.matches("(please )?(go |get |take us )?back to (the )?(beginning|initial|first|conversation)( state)?")
                    || command.matches("(please )?return to (general )?conversation")) {
                operation = "RETURN_TO_CONVERSATION"; reply = BehaviourPlan.speechOnly("Returned to Conversation.");
            } else if (command.matches("(please )?(start|let s start)( all)? over( again)?")) {
                answer("Do you mean return to Conversation, or restart the same agreed task? The state is unchanged.", runtime); return;
            } else if (command.matches("(what is (our|your|the) (current )?state|where are we|which state are we in)")) {
                operation = "STATUS"; reply = BehaviourPlan.speechOnly(CoordinationMemory.controls(storage));
            } else if (command.matches("(please )?(stop|cancel|pause|resume)( (the |this |my )?(task|interaction|conversation))?")) {
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
                if (!runtime.taskContinuation().getAsBoolean() || !revision.equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"))) return;
                inferred = true;
                var update = JsonParser.parseString(raw).getAsJsonObject();
                CoordinationAgreement.exact(update, Set.of("operation", "agreement", "reply"));
                operation = update.get("operation").getAsString();
                proposed = update.get("agreement").isJsonNull() ? null : CoordinationAgreement.parse(update.get("agreement"));
                reply = controlReply(operation, update.get("reply"));
            }
            validate(operation, proposed, assent, invitation, text);
            if (runtime.externalSpeech() != null && reply.getSpeech() != null && reply.getSpeech().length() > 350)
                throw new IllegalArgumentException("Coordination speech intent too long");
        } catch (RuntimeException failure) {
            if (!runtime.taskContinuation().getAsBoolean()) return;
            ch.zhaw.prometheus.logging.ActivityTrace.failed(inferred ? "coordination_update_invalid" : "coordination_update_failed");
            org.slf4j.LoggerFactory.getLogger(CoordinationUpdateAction.class).warn("Coordination update rejected; agreement and preparation preserved");
            answer(failure instanceof UnexpectedTerms
                    ? "That edit also changes agreed triggers, limits, roles or preparation. I have preserved the agreement. Please say explicitly if you want to replace those terms."
                    : "I couldn't apply that update. Our agreement and preparation are preserved; we can clarify the missing requirement or retry.", runtime);
            return;
        }
        if (!runtime.taskContinuation().getAsBoolean()) return;
        commit(operation, proposed, reply, event, now, runtime);
    }
    private void validate(String operation, CoordinationAgreement proposed, boolean assent, String invitation, String text) {
        var storage = getStorage(); var accepted = CoordinationMemory.agreement(storage, CoordinationMemory.AGREEMENT);
        if (!OPERATIONS.contains(operation) || (Set.of("PROPOSE", "REPLACE", "PROPOSE_AND_START").contains(operation) != (proposed != null))) throw new IllegalArgumentException();
        var previous = CoordinationMemory.agreement(storage, storage.containsKey(CoordinationMemory.DRAFT) ? CoordinationMemory.DRAFT : CoordinationMemory.AGREEMENT);
        if (operation.equals("PROPOSE") && previous != null && previous.complete()
                && !"CONVERSATION".equals(CoordinationMemory.stage(storage)) && !previous.sameTerms(proposed)) throw new UnexpectedTerms();
        if (operation.equals("REPLACE") && !CoreTaskVoice.normalize(text).matches(
                ".*\\b(replace|replacement|instead|switch|new task|different task)\\b.*|.*\\bchange\\b.*\\b(trigger|limit|role|roles|checks|preparation|timing|session|condition|success|criterion|confidence|cooldown|samples|responsibilities)\\b.*")) throw new UnexpectedTerms();
        if (assent && (!Set.of("KEEP", "START").contains(operation)
                || operation.equals("START") && CoreTaskVoice.deferredQuestion(invitation))) throw new IllegalArgumentException();
        switch (operation) {
            case "PROPOSE_AND_START" -> {
                if (accepted != null || storage.containsKey(CoordinationMemory.DRAFT) || !proposed.complete() || !proposed.checks().isEmpty())
                    throw new IllegalArgumentException("Immediate agreement requires complete new terms and no preparation");
            }
            case "START" -> { if (!CoordinationMemory.ready(storage)) throw new IllegalArgumentException("Preparation incomplete"); }
            case "STEP" -> {
                if (!TaskMemory.active(storage) || accepted == null || accepted.reactions() != null || storage.containsKey(CoordinationMemory.DRAFT))
                    throw new IllegalArgumentException("No active dialogue task");
            }
            case "COMPLETE" -> { if (!TaskMemory.active(storage)) throw new IllegalArgumentException(); }
            case "PAUSE" -> { if (!TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage))) throw new IllegalArgumentException(); }
            case "RESUME" -> { if (!"PAUSED".equals(TaskMemory.phase(storage)) || accepted == null || storage.containsKey(CoordinationMemory.DRAFT)) throw new IllegalArgumentException(); }
            case "DISCARD" -> { if (!storage.containsKey(CoordinationMemory.DRAFT)) throw new IllegalArgumentException(); }
            case "RESTART" -> { if (accepted == null || storage.containsKey(CoordinationMemory.DRAFT)) throw new IllegalArgumentException("No accepted task to restart"); }
            default -> { }
        }
    }
    private void commit(String operation, CoordinationAgreement proposed, BehaviourPlan reply, Event event, Instant now, PolicyRuntime runtime) {
        var storage = getStorage(); boolean live = runtime.externalSpeech() != null;
        if (operation.equals("PROPOSE_AND_START")) {
            storage.put(CoordinationMemory.DRAFT, proposed.json()); CoordinationMemory.resetPreparation(storage, now);
            operation = "START";
        }
        if (operation.equals("STATUS")) { answer(CoordinationMemory.controls(storage), runtime); return; }
        if (operation.equals("STOP") && !TaskMemory.active(storage) && !"PAUSED".equals(TaskMemory.phase(storage))
                && !storage.containsKey(CoordinationMemory.DRAFT)) {
            answer("No task is running. " + CoordinationMemory.controls(storage) + " The earlier outcome is preserved.", runtime); return;
        }
        if (operation.equals("RESTART")) {
            CoordinationMemory.archive(storage);
            var restart = CoordinationMemory.agreement(storage, CoordinationMemory.AGREEMENT);
            storage.put(CoordinationMemory.PREVIOUS_PHASE, new JsonPrimitive(TaskMemory.phase(storage)));
            storage.put(CoordinationMemory.DRAFT, restart.json()); CoordinationMemory.resetPreparation(storage, now);
            CoordinationMemory.phase(storage, "CONFIGURATION");
            if (CoordinationMemory.ready(storage)) operation = "START";
            else { operation = "PROPOSE"; proposed = restart; }
        }
        boolean action = operation.equals("STEP"), gate = false;
        switch (operation) {
            case "PROPOSE", "REPLACE" -> {
                if (!storage.containsKey(CoordinationMemory.DRAFT)) storage.put(CoordinationMemory.PREVIOUS_PHASE, new JsonPrimitive(TaskMemory.phase(storage)));
                storage.put(CoordinationMemory.DRAFT, proposed.json()); CoordinationMemory.phase(storage, "CONFIGURATION");
                CoordinationMemory.resetPreparation(storage, now);
            }
            case "START" -> {
                CoordinationMemory.archive(storage);
                storage.put(CoordinationMemory.RUN, new JsonPrimitive(java.util.UUID.randomUUID().toString()));
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
                if (!storage.containsKey(CoordinationMemory.OUTCOME)) storage.put(CoordinationMemory.OUTCOME, new JsonPrimitive(operation.equals("STOP") ? "CANCELLED" : "USER_REPORTED_COMPLETION"));
                CoordinationMemory.remove(storage, CoordinationMemory.DRAFT, CoordinationMemory.PREVIOUS_PHASE);
            }
            case "RETURN_TO_CONVERSATION" -> {
                if (TaskMemory.active(storage) || "PAUSED".equals(TaskMemory.phase(storage)))
                    storage.put(CoordinationMemory.OUTCOME, new JsonPrimitive("RETURNED_TO_CONVERSATION"));
                CoordinationMemory.archive(storage); CoordinationMemory.phase(storage, "CONFIGURATION");
                CoordinationMemory.remove(storage, CoordinationMemory.DRAFT, CoordinationMemory.PREVIOUS_PHASE,
                        CoordinationMemory.EVIDENCE, CoordinationMemory.PREPARED_AFTER, CoordinationMemory.LAST_CUE,
                        TaskMemory.SPEC, TaskMemory.ACTIONS, TaskMemory.AFTER, TaskMemory.SESSION, TaskMemory.PAUSE_REASON);
                SensoryRemarks.cancel(storage);
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
        if (Set.of("PROPOSE", "REPLACE").contains(operation) && CoordinationMemory.ready(storage)) storage.put(CoreTaskVoice.INVITATION, new JsonPrimitive(now.toString()));
        if (Set.of("START", "RESUME").contains(operation)) {
            storage.put(TaskMemory.SESSION, new JsonPrimitive(live ? runtime.externalSpeech().sessionId().toString() : ""));
            storage.put(TaskMemory.AFTER, new JsonPrimitive(now.plusSeconds(3).toString()));
        }
        // A task begun in Text becomes associated with Live when a user continues it there.
        if (live && TaskMemory.active(storage)) storage.put(TaskMemory.SESSION, new JsonPrimitive(runtime.externalSpeech().sessionId().toString()));
        if (!operation.equals("KEEP")) CoordinationMemory.record(storage, operation, event, now);
        reply = switch (operation) {
            case "PROPOSE" -> BehaviourPlan.speechOnly(CoordinationMemory.proposalPrompt(storage));
            case "REPLACE" -> BehaviourPlan.speechOnly("This is a replacement proposal; the earlier task is not executing. " + CoordinationMemory.proposalPrompt(storage));
            case "RETURN_TO_CONVERSATION" -> BehaviourPlan.speechOnly("We are now in Conversation. Task execution has ended; our earlier conversation and recorded outcomes are preserved.");
            case "STOP", "PAUSE", "RESUME", "DISCARD", "COMPLETE" -> BehaviourPlan.speechOnly(CoordinationMemory.controls(storage)
                    + " Recorded outcome: " + TaskMemory.text(storage, CoordinationMemory.OUTCOME, "not established") + ".");
            default -> reply;
        };
        String speech = reply.getSpeech();
        if (!action && !operation.equals("KEEP")) {
            var json = reply.toJsonObject(); json.remove("motion"); reply = BehaviourPlan.fromJson(json.toString());
        }
        if (live) reply.setSpeech(null);
        storage.put(TaskMemory.REPLY, reply.toJsonObject());
        if (action || DecorativeGestures.requested(singleEvent(event))) storage.put(DecorativeGestures.REQUIRED, new JsonPrimitive(true));
        if (operation.equals("KEEP") || operation.equals("REFLECT")) { CoreTaskVoice.clearAnswered(storage); return; }
        String facts = switch (operation) {
            case "PROPOSE" -> "Proposal saved, not active. Explain the goal, roles, success criterion and limits without performing the task. "
                    + (CoordinationMemory.ready(storage) ? CoordinationMemory.acceptancePrompt(storage) : "Clarify remaining questions or request missing preparation. Do not ask to start until preparation is complete.");
            case "REPLACE" -> "A replacement proposal was saved and the earlier task is not executing. Explain the changed terms without performing them. " + CoordinationMemory.proposalPrompt(storage);
            case "START" -> action ? "Task accepted. Give its first agreed contribution now. " + intent(speech)
                    : "Task accepted and armed. Wait for a new cue; do not perform its future action now.";
            case "STEP" -> "One agreed dialogue contribution is authorized. " + intent(speech);
            case "COMPLETE" -> "The user's completion report ended the task. Reflect on available evidence; this is not independently measured success.";
            case "STOP" -> "No further task actions are authorized. The recorded outcome is " + TaskMemory.text(storage, CoordinationMemory.OUTCOME, "not established") + ".";
            case "RETURN_TO_CONVERSATION" -> "We are now in Conversation. Previous history and recorded outcomes are preserved. No task action is authorized.";
            case "PAUSE" -> "The task is paused; agreement and budget are preserved. Explicit resume is required.";
            case "RESUME" -> "The task is resumed with its existing agreement and remaining budget. No extra task action was performed.";
            case "DISCARD" -> "The draft was discarded. The previous agreement retains its recorded outcome; only an unfinished, paused task can resume.";
            default -> throw new IllegalStateException("Unsupported coordination continuation");
        };
        if (action && "COMPLETED".equals(TaskMemory.phase(storage))) facts += " The action budget is exhausted; do not claim that this proves success.";
        CoreTaskVoice.response(storage, facts, false, gate, runtime, CoordinationMemory.facts(storage) + "\n" + facts);
    }
    private static String intent(String speech) { return speech == null ? "No spoken task action was requested." : "Required contribution: " + speech.substring(0, Math.min(350, speech.length())); }
    private static BehaviourPlan controlReply(String operation, JsonElement raw) {
        try { return TaskMemory.plan(raw); }
        catch (IllegalArgumentException invalid) {
            // Optional expression is not part of a proposal's executable agreement. Keep motion and speech strict.
            if (!Set.of("PROPOSE", "REPLACE", "STATUS", "RETURN_TO_CONVERSATION", "STOP", "PAUSE", "RESUME", "DISCARD", "COMPLETE").contains(operation)
                    || raw == null || !raw.isJsonObject() || raw.toString().length() > 1800 || !raw.getAsJsonObject().has("nonVerbal")) throw invalid;
            var withoutExpression = raw.getAsJsonObject().deepCopy(); withoutExpression.remove("nonVerbal");
            var plan = TaskMemory.plan(withoutExpression);
            org.slf4j.LoggerFactory.getLogger(CoordinationUpdateAction.class).warn("Coordination control reply discarded invalid optional expression");
            return plan;
        }
    }
    private static EventHistory singleEvent(Event event) { var history = new EventHistory(); history.appendEvent(event); return history; }
    private void answer(String text, PolicyRuntime runtime) {
        getStorage().put(TaskMemory.REPLY, runtime.externalSpeech() == null ? BehaviourPlan.speechOnly(text).toJsonObject() : new JsonObject());
        if (runtime.externalSpeech() != null) CoreTaskVoice.response(getStorage(), text, false, false, runtime, CoordinationMemory.facts(getStorage()));
    }
}
