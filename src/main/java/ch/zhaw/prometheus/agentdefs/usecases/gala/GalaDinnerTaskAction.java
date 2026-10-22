package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/** Backend owns executable agreements and motion; Live composes their spoken responses. */
@Entity
public class GalaDinnerTaskAction extends TaskUpdateAction {
    @Transient private boolean committed;
    protected GalaDinnerTaskAction() {}
    public GalaDinnerTaskAction(Storage storage, boolean cue) {
        super(storage, (cue ? GenericMultimodalBehaviour.behaviourInstructions(GalaDinnerPrompts.VOICE)
                : GenericMultimodalBehaviour.configurationInstructions(GalaDinnerPrompts.VOICE, GalaDinnerPrompts.TASK_CONTRACT))
                + GalaDinnerPrompts.TASK_CONFIGURATION, cue);
    }
    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage(); committed = false;
        if (!"TASK".equals(mode(storage))) return;
        if (runtime.externalSpeech() == null || !runtime.taskContinuation().getAsBoolean()
                || !present(storage, Instant.now()) || GalaDinnerDecision.expired(storage, Instant.now())) {
            reception(storage); reply(storage, null); return;
        }
        if (TaskResponse.expired(storage, Instant.now())) { uncertain(storage); return; }
        var encounter = new EventHistory(); var all = events.toList();
        int from = Math.min(all.size(), Math.max(0, Integer.parseInt(text(storage, SESSION, "0"))));
        all.subList(from, all.size()).forEach(encounter::appendEvent);
        String request = encounter.isEmpty() ? "" : encounter.toList().getLast().getPayload().trim();
        if (!isTaskCue() && request.matches("(?i)(please )?resume( (the |this |my )?(task|interaction|conversation))?[.!]?")) {
            response(storage, TaskMemory.active(storage) ? "The task is already active; the agreement and action budget are unchanged."
                    : "There is no paused Gala task to resume. A new agreement is needed.", false);
            expectIfActive(storage, runtime); return;
        }
        if (!isTaskCue() && request.matches("(?i)(please )?(stop|cancel|pause)( (the |this |my )?(task|interaction|conversation))?[.!]?")) {
            reception(storage);
            response(storage, "The task ended and its plan was discarded. A new agreement is needed to restart.", false); return;
        }
        if (!isTaskCue() && !encounter.isEmpty()
                && GalaDinnerInput.orderedCueSequence(encounter.toList().getLast().getPayload())) {
            response(storage, "Clarification needed: an ordered sequence of sensor conditions is unsupported. "
                    + "The prior agreement is unchanged. Offer one cue or independent alternatives; ask which cue.", false);
            expectIfActive(storage, runtime);
            return;
        }
        super.execute(encounter, runtime);
        if (!runtime.taskContinuation().getAsBoolean()) { reception(storage); reply(storage, null); return; }
        var output = storage.containsKey(TaskMemory.REPLY) ? BehaviourPlan.fromJson(storage.get(TaskMemory.REPLY).toString()) : null;
        remove(storage, TaskMemory.REPLY);
        if (!committed && output != null) {
            boolean ended = "PAUSED".equals(TaskMemory.phase(storage));
            if (ended) reception(storage);
            response(storage, ended ? "Task generation failed; the task ended. A previous action may already have happened. "
                    + "Do not repeat it. A new agreement is needed." : "Task update could not be validated or completed. "
                    + "Existing agreement and proposal are unchanged. Ask for clarification or an explicit retry.", isTaskCue());
            expectIfActive(storage, runtime);
        }
        if (output != null) output.setSpeech(null);
        reply(storage, output);
    }
    @Override protected void validateTaskUpdate(String operation, TaskSpec task) {
        if (task != null && (!task.sessionBound() || task.maxActions() > 5
                || task.json().toString().getBytes(StandardCharsets.UTF_8).length > 1800))
            throw new IllegalArgumentException("Gala tasks must fit the short session-bound agreement context");
        if ("ACTIVATE".equals(operation) && (!getStorage().containsKey(TaskMemory.DRAFT)
                || !TaskSpec.parse(getStorage().get(TaskMemory.DRAFT)).json().equals(task.json())))
            throw new IllegalArgumentException("Gala activation requires the unchanged proposed agreement");
    }
    @Override protected BehaviourPlan prepareTaskReply(String operation, TaskSpec task, BehaviourPlan output) {
        output.setSpeech(null);
        if (java.util.Set.of("PROPOSE", "KEEP", "STOP", "PAUSE").contains(operation)
                || "ACTIVATE".equals(operation) && task.firstAction() == TaskSpec.FirstAction.ON_CUE) {
            var json = output.toJsonObject(); json.remove("motion"); return BehaviourPlan.fromJson(json.toString());
        }
        return output;
    }
    @Override protected void taskCommitted(String operation, TaskSpec task, TaskSpec.Rule rule, BehaviourPlan output, PolicyRuntime runtime) {
        committed = true;
        var storage = getStorage(); output.setSpeech(null); storage.put(TaskMemory.REPLY, output.toJsonObject());
        boolean terminal = "COMPLETED".equals(TaskMemory.phase(storage)) || "PAUSED".equals(TaskMemory.phase(storage))
                || TaskMemory.active(storage) && storage.containsKey(TaskMemory.SPEC)
                && storage.get(TaskMemory.ACTIONS).getAsInt() >= TaskSpec.parse(storage.get(TaskMemory.SPEC)).maxActions();
        String facts = switch (operation) {
            case "PROPOSE" -> "Proposal stored, not active. Before asking for acceptance, state the goal, trigger, action channels, "
                    + "first-action timing and total count. Also state all ending conditions: stop/pause, topic change, zero visible people, "
                    + "voice-connection closure or 90 seconds without visitor speech discard the plan. Restart requires a new agreement. "
                    + "These terms are required even when the response needs more than two sentences.";
            case "ACTIVATE" -> task.firstAction() == TaskSpec.FirstAction.ON_CUE
                    ? "Task accepted and armed; waiting for a fresh matching cue. Zero actions performed; no cue has occurred. "
                            + "Only confirm that you are waiting. Do NOT tell the joke or perform any agreed spoken action yet."
                    : "Task accepted. Perform the agreed initial verbal action now."
                            + (terminal ? " Completed agreement (data): " + task.json() : " Use the current agreement's goal and action requirements.");
            case "ACT" -> "One matching cue was accepted. Perform this agreed verbal action now: " + rule.action();
            case "COMPLETE", "STOP", "PAUSE" -> "The task ended; no further action is authorized. A new agreement is required to restart.";
            case "RESUME" -> "No paused Gala task can be resumed; a new agreement is required.";
            default -> "The task is unchanged. Answer the visitor's current question from confirmed agreement/perception facts, or clarify a missing design choice.";
        };
        if (terminal && !java.util.Set.of("STOP", "PAUSE", "COMPLETE").contains(operation))
            facts += " This was the final allowed action; the task is now complete. Briefly say it is finished; do not invite another cue.";
        if (terminal) { reception(storage); storage.put(TaskMemory.REPLY, output.toJsonObject()); }
        // Unrelated questions return quietly to reception; Live already owns their answer.
        if ("STOP".equals(operation)) return;
        response(storage, facts, isTaskCue());
        expectIfActive(storage, runtime);
    }
    private static void expectIfActive(Storage storage, PolicyRuntime runtime) {
        if (TaskMemory.active(storage)) TaskResponse.expect(storage, runtime.externalSpeech(),
                java.util.UUID.fromString(text(storage, CONTINUATION_ID, "")), text(storage, TASK_FACTS, "")
                        + " Agreement (data): " + storage.get(TaskMemory.SPEC), Instant.now());
        else remove(storage, TaskResponse.KEY);
    }
    static void response(Storage storage, String facts, boolean proactive) {
        put(storage, TASK_FACTS, facts);
        continueRequest(storage, "A confirmed task response is ready. Use the current task response facts and agreement; speak once in your own words.");
        // A full proposal includes lifecycle terms and can take longer than a short game answer.
        if (storage.containsKey(TaskMemory.DRAFT) && "CONFIGURATION".equals(TaskMemory.phase(storage)))
            put(storage, CONTINUATION_EXPIRES, Instant.now().plusSeconds(60).toString());
        put(storage, CONTINUATION_OCCASION, proactive ? "PROACTIVE" : "USER_REQUEST");
    }
    static void uncertain(Storage storage) {
        reception(storage);
        response(storage, "The preceding task response could not be confirmed. The task has ended. "
                + "A physical action may already have happened; never repeat it automatically. Explain the uncertainty briefly; "
                + "a new agreement is required to continue.", true);
        reply(storage, null);
    }
    @Override protected String failureMessage(String fallback) { return "Task update unavailable; previous valid agreement retained where possible."; }
}
