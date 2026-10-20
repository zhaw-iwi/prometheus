package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.time.Instant;
import java.util.List;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.task.TaskMemory;

final class GalaDinnerMemory {
    static final String PREFIX = "gala.";
    static final String MODE = PREFIX + "mode", REPLY = PREFIX + "reply", PERSON = PREFIX + "person",
            SIGN = PREFIX + "sign", ROUND = PREFIX + "round", LAST_INPUT = PREFIX + "last_input",
            STARTED = PREFIX + "started", SEEN = PREFIX + "seen_at",
            COUNT = PREFIX + "count", QUIET = PREFIX + "quiet", SESSION = PREFIX + "session_from",
            FACE = PREFIX + "face", FACE_AT = PREFIX + "face_at", FACE_REACTED = PREFIX + "face_reacted";
    private GalaDinnerMemory() {}
    static String text(Storage storage, String key, String fallback) {
        return storage.containsKey(key) ? storage.get(key).getAsString() : fallback;
    }
    static void put(Storage storage, String key, String value) { storage.put(key, new JsonPrimitive(value)); }
    static void remove(Storage storage, String key) { if (storage.containsKey(key)) storage.remove(key); }
    static String mode(Storage storage) { return text(storage, MODE, "RECEPTION"); }
    static Instant time(Storage storage, String key) { return Instant.parse(text(storage, key, Instant.EPOCH.toString())); }
    static boolean present(Storage storage, Instant now) {
        return Integer.parseInt(text(storage, COUNT, "0")) > 0 && now.isBefore(time(storage, SEEN).plusSeconds(15));
    }
    static void reply(Storage storage, BehaviourPlan plan) {
        remove(storage, REPLY);
        if (plan != null && !plan.isEmpty()) storage.put(REPLY, plan.toJsonObject());
    }
    static void reception(Storage storage) {
        TaskMemory.clear(storage);
        put(storage, MODE, "RECEPTION");
        for (String key : List.of(PERSON, SIGN, STARTED)) remove(storage, key);
    }
    static void clear(Storage storage) {
        TaskMemory.clear(storage);
        for (String key : storage.toMap().keySet()) if (key.startsWith(PREFIX)) storage.remove(key);
    }
}
