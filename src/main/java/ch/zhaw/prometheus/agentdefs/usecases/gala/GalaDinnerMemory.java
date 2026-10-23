package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.time.Instant;
import java.util.List;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.task.TaskMemory;

final class GalaDinnerMemory {
    static final long DEPARTURE_GRACE_MS = 2000;
    static final String PREFIX = "gala.";
    static final String MODE = PREFIX + "mode", REPLY = PREFIX + "reply", PERSON = PREFIX + "person",
            SIGN = PREFIX + "sign", LAST_INPUT = PREFIX + "last_input",
            EXIT_REQUEST = PREFIX + "exit_request", CAPTURE_ID = PREFIX + "rps_capture_id",
            STARTED = PREFIX + "started", SEEN = PREFIX + "seen_at", ABSENT_SINCE = PREFIX + "absent_since",
            CONTINUATION_ID = PREFIX + "continuation_id", CONTINUATION_FACTS = PREFIX + "continuation_facts",
            CONTINUATION_EXPIRES = PREFIX + "continuation_expires",
            CONTINUATION_OCCASION = PREFIX + "continuation_occasion", TASK_FACTS = PREFIX + "task_facts",
            CONTINUATION_AMBIENT = PREFIX + "continuation_ambient",
            OUTCOME = PREFIX + "outcome", OUTCOME_ID = PREFIX + "outcome_id", OUTCOME_EXPIRES = PREFIX + "outcome_expires",
            COUNT = PREFIX + "count", QUIET = PREFIX + "quiet", SESSION = PREFIX + "session_from",
            FACE = PREFIX + "face", FACE_AT = PREFIX + "face_at", FACE_REACTED = PREFIX + "face_reacted";
    private GalaDinnerMemory() {}
    static String text(Storage storage, String key, String fallback) {
        return storage.containsKey(key) ? storage.get(key).getAsString() : fallback;
    }
    // Gala actions are blocking. Unchanged facts need no new storage write version;
    // timestamps and occasion IDs still change normally. Shared Storage semantics stay intact.
    static void put(Storage storage, String key, String value) {
        var next = new JsonPrimitive(value);
        if (!storage.containsKey(key) || !next.equals(storage.get(key))) storage.put(key, next);
    }
    static void remove(Storage storage, String key) { if (storage.containsKey(key)) storage.remove(key); }
    static String mode(Storage storage) { return text(storage, MODE, "RECEPTION"); }
    static Instant time(Storage storage, String key) { return Instant.parse(text(storage, key, Instant.EPOCH.toString())); }
    static String recentOutcome(Storage storage, Instant now) {
        return storage.containsKey(OUTCOME) && now.isBefore(time(storage, OUTCOME_EXPIRES))
                ? text(storage, OUTCOME, "") : "";
    }
    static boolean present(Storage storage, Instant now) {
        return Integer.parseInt(text(storage, COUNT, "0")) > 0 && now.isBefore(time(storage, SEEN).plusSeconds(15));
    }
    static boolean departureDue(Storage storage, Instant now) {
        return "0".equals(text(storage, COUNT, "0")) && storage.containsKey(ABSENT_SINCE)
                && !now.isBefore(time(storage, ABSENT_SINCE).plusMillis(DEPARTURE_GRACE_MS));
    }
    static boolean conversationPresent(Storage storage, Instant now) {
        return present(storage, now) || storage.containsKey(ABSENT_SINCE) && !departureDue(storage, now)
                && now.isBefore(time(storage, SEEN).plusSeconds(15));
    }
    static void endEncounter(Storage storage, int historySize) {
        reception(storage); GalaDinnerSensing.clear(storage);
        for (String key : List.of(ABSENT_SINCE, LAST_INPUT, QUIET, FACE, FACE_AT, FACE_REACTED,
                GalaDinnerGestures.HISTORY, REPLY, GalaDinnerGestures.REQUIRED, GalaDinnerDialogue.FACE_TOPIC_AT)) remove(storage, key);
        put(storage, SESSION, Integer.toString(historySize));
    }
    static void reply(Storage storage, BehaviourPlan plan) {
        reply(storage, plan, false);
    }
    static void reply(Storage storage, BehaviourPlan plan, boolean requiredGesture) {
        remove(storage, REPLY);
        remove(storage, GalaDinnerGestures.REQUIRED);
        if (plan != null && !plan.isEmpty()) {
            storage.put(REPLY, plan.toJsonObject());
            if (requiredGesture) put(storage, GalaDinnerGestures.REQUIRED, "true");
        }
    }
    static void reception(Storage storage) {
        if (storage.containsKey(EXIT_REQUEST)) ch.zhaw.prometheus.logging.ActivityTrace.cue("confirmation_resolved", null);
        TaskMemory.clear(storage);
        put(storage, MODE, "RECEPTION");
        GalaDinnerRounds.clear(storage);
        for (String key : List.of(PERSON, SIGN, STARTED, CAPTURE_ID, EXIT_REQUEST, CONTINUATION_ID, CONTINUATION_FACTS, CONTINUATION_EXPIRES,
                OUTCOME, OUTCOME_ID, OUTCOME_EXPIRES, CONTINUATION_OCCASION, CONTINUATION_AMBIENT, TASK_FACTS)) remove(storage, key);
        GalaDinnerSensing.activityEnded(storage);
    }
    static void continueRequest(Storage storage, String facts) {
        remove(storage, CONTINUATION_AMBIENT);
        put(storage, CONTINUATION_ID, java.util.UUID.randomUUID().toString());
        put(storage, CONTINUATION_FACTS, facts);
        put(storage, CONTINUATION_EXPIRES, Instant.now().plusSeconds(30).toString());
        put(storage, CONTINUATION_OCCASION, "USER_REQUEST");
    }
    static void clear(Storage storage) {
        TaskMemory.clear(storage);
        for (String key : storage.toMap().keySet()) if (key.startsWith(PREFIX)) storage.remove(key);
    }
}
