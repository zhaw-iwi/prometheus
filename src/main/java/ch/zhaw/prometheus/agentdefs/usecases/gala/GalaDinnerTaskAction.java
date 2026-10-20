package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;

import java.time.Instant;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskSpec;
import ch.zhaw.prometheus.model.task.TaskUpdateAction;
import jakarta.persistence.Entity;

/** Reuses the task engine, with a short, confirmed agreement owned by this encounter. */
@Entity
public class GalaDinnerTaskAction extends TaskUpdateAction {
    protected GalaDinnerTaskAction() {}
    public GalaDinnerTaskAction(Storage storage, boolean cue) {
        super(storage, (cue ? GenericMultimodalBehaviour.behaviourInstructions(GalaDinnerPrompts.VOICE)
                : GenericMultimodalBehaviour.configurationInstructions(GalaDinnerPrompts.VOICE, GalaDinnerPrompts.TASK_CONTRACT))
                + GalaDinnerPrompts.TASK_CONFIGURATION, cue);
    }

    @Override public void execute(EventHistory events, PolicyRuntime runtime) {
        var storage = getStorage();
        if (!"TASK".equals(mode(storage))) return;
        if (runtime.externalSpeech() == null || !runtime.taskContinuation().getAsBoolean()
                || !present(storage, Instant.now()) || GalaDinnerDecision.expired(storage, Instant.now())) {
            reception(storage);
            reply(storage, null);
            return;
        }
        if (!isTaskCue() && !events.isEmpty() && events.toList().getLast().getPayload().trim().matches(
                "(?i)(please )?resume( (the |this |my )?(task|interaction|conversation))?[.!]?")) {
            reply(storage, BehaviourPlan.speechOnly(TaskMemory.active(storage)
                    ? "Unsere Aufgabe ist bereits aktiv."
                    : "Hier gibt es keine pausierte Aufgabe. Wir können einen neuen Vorschlag vereinbaren."));
            return;
        }
        // A proposal from a previous visitor cannot enter this configuration or its cue history.
        var encounter = new EventHistory();
        var all = events.toList();
        int from = Math.min(all.size(), Math.max(0, Integer.parseInt(text(storage, SESSION, "0"))));
        all.subList(from, all.size()).forEach(encounter::appendEvent);
        super.execute(encounter, runtime);
        if (!runtime.taskContinuation().getAsBoolean()) {
            reception(storage);
            reply(storage, null);
            return;
        }
        var response = storage.containsKey(TaskMemory.REPLY)
                ? BehaviourPlan.fromJson(storage.get(TaskMemory.REPLY).toString()) : null;
        remove(storage, TaskMemory.REPLY);
        boolean limit = TaskMemory.active(storage) && storage.containsKey(TaskMemory.SPEC)
                && Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0"))
                    >= TaskSpec.parse(storage.get(TaskMemory.SPEC)).maxActions();
        if (limit || "COMPLETED".equals(TaskMemory.phase(storage)) || "PAUSED".equals(TaskMemory.phase(storage))) {
            if ("PAUSED".equals(TaskMemory.phase(storage)))
                response = BehaviourPlan.speechOnly("Die Aufgabe ist beendet. Ich bin wieder für Ihre Fragen da.");
            else if (limit && response != null)
                response.setSpeech((response.getSpeech() == null ? "" : response.getSpeech() + " ")
                        + "Damit ist unsere Aufgabe abgeschlossen.");
            reception(storage);
        }
        reply(storage, response);
    }

    @Override protected void validateTaskUpdate(String operation, TaskSpec task) {
        if (task != null && (!task.sessionBound() || task.maxActions() > 5))
            throw new IllegalArgumentException("Gala tasks must be short and session-bound");
        if ("ACTIVATE".equals(operation) && (!getStorage().containsKey(TaskMemory.DRAFT)
                || !TaskSpec.parse(getStorage().get(TaskMemory.DRAFT)).json().equals(task.json())))
            throw new IllegalArgumentException("Gala activation requires the unchanged proposed agreement");
    }

    @Override protected BehaviourPlan prepareTaskReply(String operation, TaskSpec task, BehaviourPlan response) {
        if (!"PROPOSE".equals(operation)) return response;
        String count = task.maxActions() == 1
                ? "Nach Ihrer Zustimmung eine Aktion sofort; danach ist die Aufgabe beendet. "
                    + "Für eine spätere Sensorreaktion brauchen wir mindestens zwei Aktionen insgesamt. "
                : "Nach Ihrer Zustimmung eine Aktion sofort, dann höchstens " + (task.maxActions() - 1)
                    + " Reaktionen auf passende Signale (" + task.maxActions() + " Aktionen insgesamt). ";
        var plan = response.toJsonObject();
        String goal = task.goal().stripTrailing().replaceFirst("[.!?]+$", "");
        plan.addProperty("speech", "Vorschlag (noch nicht aktiv): " + goal + ". " + count
                + "Stopp/Pause, ein leeres Kamerabild, Ende der Sprachverbindung oder 90 Sekunden ohne Ihre Sprache "
                + "beenden die Aufgabe und verwerfen den Plan. Ein Neustart braucht eine neue Vereinbarung. "
                + "Möchten Sie diesen Vorschlag aktivieren?");
        // A proposal never executes its hand action before acceptance.
        plan.remove("motion");
        return BehaviourPlan.fromJson(plan.toString());
    }

    @Override protected String failureMessage(String fallback) {
        return "Das konnte ich gerade nicht umsetzen. "
                + (getStorage().containsKey(TaskMemory.DRAFT) ? "Unser Vorschlag bleibt unverändert. "
                    : getStorage().containsKey(TaskMemory.SPEC) ? "Unsere Vereinbarung bleibt unverändert. " : "")
                + "Bitte versuchen Sie es noch einmal.";
    }
}
