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
        if (!isTaskCue() && !encounter.isEmpty()
                && GalaDinnerInput.orderedCueSequence(encounter.toList().getLast().getPayload())) {
            reply(storage, BehaviourPlan.speechOnly("Eine feste Reihenfolge mehrerer Signale kann ich in dieser Aufgabe nicht verfolgen. "
                    + "Ich kann auf ein einzelnes Signal oder unabhängig auf mehrere reagieren. "
                    + "Die bisherige Vereinbarung bleibt unverändert. Welches Signal möchten Sie verwenden?"));
            return;
        }
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
        if ("ACTIVATE".equals(operation) && task.firstAction() == TaskSpec.FirstAction.ON_CUE)
            return BehaviourPlan.speechOnly("Die Aufgabe ist aktiviert. Ich warte auf das vereinbarte frische Signal; "
                    + "noch wurde keine Aktion ausgeführt.");
        if (!"PROPOSE".equals(operation)) return response;
        String count = task.firstAction() == TaskSpec.FirstAction.ON_CUE
                ? "Erste Aktion erst beim passenden Signal, höchstens " + task.maxActions()
                    + (task.maxActions() == 1 ? " Aktion insgesamt. " : " Aktionen insgesamt. ")
                : task.maxActions() == 1
                ? "Nach Ihrer Zustimmung eine Aktion sofort; danach ist die Aufgabe beendet. "
                : "Nach Ihrer Zustimmung eine Aktion sofort, dann höchstens " + (task.maxActions() - 1)
                    + " Reaktionen auf passende Signale (" + task.maxActions() + " Aktionen insgesamt). ";
        var plan = response.toJsonObject();
        String goal = task.goal().stripTrailing().replaceFirst("[.!?]+$", "");
        plan.addProperty("speech", "Vorschlag (noch nicht aktiv): " + goal + ". " + count + terms(task)
                + "Stopp/Pause, Themenwechsel, leeres Kamerabild, Ende der Sprachverbindung oder 90 Sekunden ohne Ihre Sprache "
                + "beenden die Aufgabe und verwerfen den Plan. Neustart nur mit neuer Vereinbarung. "
                + "Möchten Sie diesen Vorschlag aktivieren?");
        // A proposal never executes its hand action before acceptance.
        plan.remove("motion");
        return BehaviourPlan.fromJson(plan.toString());
    }

    private static String terms(TaskSpec task) {
        var text = new StringBuilder();
        for (var rule : task.rules()) {
            String cue = switch (rule.field()) {
                case "sign" -> "Handzeichen";
                case "emotion" -> "geschätzter Gesichtsausdruck";
                case "valence" -> "geschätzte Stimmung";
                case "arousal" -> "geschätzte Aktivierung";
                case "humanCount", "trackedCount", "currentHumanCount" -> "Personenzahl im Kamerabild";
                case "groupCount", "currentGroupCount" -> "Gruppenzahl im Kamerabild";
                case "singletonCount" -> "Zahl einzeln stehender Personen";
                case "largestGroupSize", "currentLargestGroupSize" -> "Grösse der grössten Gruppe";
                case "changeType" -> "Gruppenänderung";
                case "facePresent" -> "sichtbares Gesicht";
                case "condition" -> "Wetter";
                case "temperature_c" -> "Temperatur";
                case "precipitation_mm" -> "Niederschlag in Millimetern";
                case "is_day" -> "Tageslicht";
                case "cloud_cover" -> "Bewölkung";
                case "days.0.condition" -> "Wetter am ersten Vorhersagetag";
                case "days.0.temperature_min_c" -> "Tiefsttemperatur am ersten Vorhersagetag";
                case "days.0.temperature_max_c" -> "Höchsttemperatur am ersten Vorhersagetag";
                case "days.0.precipitation_mm" -> "Niederschlag am ersten Vorhersagetag";
                default -> throw new IllegalArgumentException("Unsupported agreement description");
            };
            String value = switch (rule.value().getAsString()) {
                case "rock" -> "Stein"; case "paper" -> "Papier"; case "scissor" -> "Schere";
                case "happy" -> "fröhlich"; case "sad" -> "traurig"; default -> rule.value().getAsString();
            };
            String relation = switch (rule.operator()) { case "lt" -> " unter "; case "gt" -> " über "; default -> " "; };
            String action = switch (rule.effect()) {
                case WAIT -> "abwarten, ohne Aktion"; case COMPLETE -> "Aufgabe beenden"; case ACT -> rule.action();
            };
            action = action.replace("motion.handSign=paper", "Papier-Handzeichen zeigen")
                    .replace("motion.handSign=rock", "Stein-Handzeichen zeigen").replace("motion.handSign=scissor", "Schere-Handzeichen zeigen")
                    .stripTrailing().replaceFirst("[.!?]+$", "");
            if (task.firstAction() == TaskSpec.FirstAction.IMMEDIATE && task.maxActions() == 1)
                text.append("Einmalige Aktion: ").append(action).append(". ");
            else text.append("Bei ").append(cue).append(relation).append(value).append(": ").append(action).append(". ");
        }
        return text.toString();
    }

    @Override protected String failureMessage(String fallback) {
        return "Das konnte ich gerade nicht umsetzen. "
                + (getStorage().containsKey(TaskMemory.DRAFT) ? "Unser Vorschlag bleibt unverändert. "
                    : getStorage().containsKey(TaskMemory.SPEC) ? "Unsere Vereinbarung bleibt unverändert. " : "")
                + "Bitte versuchen Sie es noch einmal.";
    }
}
