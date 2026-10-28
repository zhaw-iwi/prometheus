package ch.zhaw.prometheus.model.coordination;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.*;

/** Agreements and their evidence live in ordinary inspectable, persisted Storage. */
public final class CoordinationMemory {
    public static final String DRAFT = "coordination.draft", AGREEMENT = "coordination.agreement",
            PREPARED_AFTER = "coordination.preparedAfter", EVIDENCE = "coordination.evidence",
            ACCEPTED_EVIDENCE = "coordination.acceptedEvidence", PREVIOUS_PHASE = "coordination.previousPhase",
            OUTCOME = "coordination.outcome", LOG = "coordination.log", REVIEW = "coordination.review", LAST_CUE = "coordination.lastCue",
            RUN = "coordination.run", OUTCOMES = "coordination.outcomes";
    private CoordinationMemory() {}
    public static CoordinationAgreement agreement(Storage storage, String key) {
        return storage.containsKey(key) ? CoordinationAgreement.parse(storage.get(key)) : null;
    }
    public static String stage(Storage storage) {
        if ("PAUSED".equals(TaskMemory.phase(storage))) return "PAUSED";
        if ("COMPLETED".equals(TaskMemory.phase(storage))) return "REFLECTION";
        if (TaskMemory.active(storage)) return "EXECUTION";
        var draft = agreement(storage, DRAFT);
        if (draft == null) return "CONVERSATION";
        if (!draft.complete()) return "COORDINATION";
        return ready(storage) ? "READY" : "PREPARATION";
    }
    public static boolean ready(Storage storage) {
        var draft = agreement(storage, DRAFT);
        return draft != null && draft.complete() && evidence(storage).size() == draft.checks().size();
    }
    public static JsonObject evidence(Storage storage) {
        return storage.containsKey(EVIDENCE) ? storage.get(EVIDENCE).getAsJsonObject().deepCopy() : new JsonObject();
    }
    public static void resetPreparation(Storage storage, Instant now) {
        storage.put(EVIDENCE, new JsonObject());
        storage.put(PREPARED_AFTER, new JsonPrimitive(now.toString()));
    }
    /** Only a fresh observation after this proposal/encounter can discharge a check. No verbal bypass. */
    public static boolean observe(Storage storage, Event event, Instant now) {
        if (!"PREPARATION".equals(stage(storage))) return false;
        var observed = TaskMemory.observed(event);
        Instant boundary = Instant.parse(TaskMemory.text(storage, PREPARED_AFTER, now.toString()));
        if (observed == null || !observed.isAfter(boundary)) return false;
        var draft = agreement(storage, DRAFT); var evidence = evidence(storage); boolean changed = false;
        for (int i = 0; i < draft.checks().size(); i++) {
            String key = Integer.toString(i);
            if (evidence.has(key) || !TaskDecision.matchesObservation(draft.checks().get(i).rule(), event, now)) continue;
            var proof = new JsonObject(); proof.addProperty("observed", observed.toString());
            proof.addProperty("eventId", event.getId() == null ? event.getType() + ":" + observed : event.getId().toString());
            evidence.add(key, proof); changed = true;
        }
        if (changed) storage.put(EVIDENCE, evidence);
        return changed;
    }
    public static void revise(Storage storage) {
        storage.put(TaskMemory.REVISION, new JsonPrimitive(Integer.parseInt(TaskMemory.text(storage, TaskMemory.REVISION, "0")) + 1));
    }
    public static void phase(Storage storage, String phase) { storage.put(TaskMemory.PHASE, new JsonPrimitive(phase)); }
    public static void remove(Storage storage, String... keys) { for (String key : keys) if (storage.containsKey(key)) storage.remove(key); }
    /** Retain completed run outcomes independently of the next task's mutable control. */
    public static void archive(Storage storage) {
        if (!storage.containsKey(OUTCOME)) return;
        var entries = storage.containsKey(OUTCOMES) ? storage.get(OUTCOMES).getAsJsonArray().deepCopy() : new JsonArray();
        String run = TaskMemory.text(storage, RUN, "legacy");
        for (var item : entries) if (item.getAsJsonObject().get("run").getAsString().equals(run)) return;
        var entry = new JsonObject(); entry.addProperty("run", run);
        entry.addProperty("outcome", TaskMemory.text(storage, OUTCOME, "unknown"));
        entry.addProperty("actions", TaskMemory.text(storage, TaskMemory.ACTIONS, "0"));
        entries.add(entry); while (entries.size() > 8) entries.remove(0); storage.put(OUTCOMES, entries);
    }
    public static String controls(Storage storage) {
        return "Initial state: CONVERSATION. Current state: " + stage(storage) + ". " + switch (stage(storage)) {
            case "CONVERSATION" -> "Explore or propose a task.";
            case "COORDINATION" -> "Clarify missing terms; execution is unavailable.";
            case "PREPARATION" -> "Demonstrate the remaining checks; execution is unavailable.";
            case "READY" -> "Awaiting your acceptance. Start accepts these terms; then an ON_CUE task needs a fresh cue.";
            case "EXECUTION" -> "Pause, stop, revise, or report completion.";
            case "PAUSED" -> "Explicitly resume, revise or stop.";
            default -> "Reflect, revise or explicitly restart the same task.";
        } + " Return to conversation is always available and preserves history. Ambiguous start over needs clarification.";
    }
    public static void record(Storage storage, String operation, Event event, Instant now) {
        var log = storage.containsKey(LOG) ? storage.get(LOG).getAsJsonArray().deepCopy() : new JsonArray();
        var entry = new JsonObject(); entry.addProperty("operation", operation); entry.addProperty("at", now.toString());
        entry.addProperty("revision", TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        entry.addProperty("actions", TaskMemory.text(storage, TaskMemory.ACTIONS, "0"));
        entry.addProperty("source", event == null || event.getId() == null ? "unpersisted" : event.getId().toString());
        if (event != null) {
            entry.addProperty("eventType", event.getType());
            var observed = TaskMemory.observed(event);
            if (observed != null) entry.addProperty("observed", observed.toString());
        }
        if (Set.of("SENSOR_RUNNING", "SENSOR_COMPLETED").contains(operation) && storage.containsKey(LAST_CUE))
            entry.add("cue", storage.get(LAST_CUE).deepCopy());
        log.add(entry); while (log.size() > 12) log.remove(0); storage.put(LOG, log);
    }
    public static String facts(Storage storage) {
        String prefix = controls(storage) + " Coordination stage: " + stage(storage) + "; actions: " + TaskMemory.text(storage, TaskMemory.ACTIONS, "0")
                + "; outcome: " + TaskMemory.text(storage, OUTCOME, "not established") + ". ";
        String key = storage.containsKey(DRAFT) ? DRAFT : AGREEMENT;
        var agreement = agreement(storage, key);
        if (agreement == null || "CONVERSATION".equals(stage(storage))) return prefix + "No active task. Explore capabilities and possible useful activities.";
        String cue = !key.equals(DRAFT) && storage.containsKey(LAST_CUE)
                ? "Last accepted cue: rule " + storage.get(LAST_CUE).getAsJsonObject().get("rule").getAsInt()
                    + "; output requests do not prove physical execution. " : "";
        return prefix + cue + (key.equals(DRAFT) ? "PROPOSAL, NOT ACTIVE. Recognised preparation checks: " + evidence(storage).keySet()
                + ". " : "Accepted agreement: ") + agreement.json();
    }
    public static void clear(Storage storage) { remove(storage, DRAFT, AGREEMENT, PREPARED_AFTER, EVIDENCE, ACCEPTED_EVIDENCE, PREVIOUS_PHASE, OUTCOME, LOG, REVIEW, LAST_CUE, RUN, OUTCOMES); }
}
