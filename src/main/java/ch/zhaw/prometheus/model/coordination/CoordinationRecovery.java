package ch.zhaw.prometheus.model.coordination;

import java.time.Instant;
import java.util.Set;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** One content-free rejected update, scoped to the unchanged task and current phase. */
public final class CoordinationRecovery {
    public static final String KEY = "coordination.lastFailure";
    public static final long TTL_SECONDS = 1800;
    private static final Set<String> CATEGORIES = Set.of("inference_failed", "update_shape", "agreement_shape",
            "reply_shape", "operation_constraints", "speech_intent_length", "protected_terms");
    private static final Set<String> OPERATIONS = Set.of("KEEP", "SUPPORT", "PROPOSE", "REPLACE", "PROPOSE_AND_START", "START",
            "STEP", "COMPLETE", "PAUSE", "RESUME", "STOP", "DISCARD", "REFLECT", "RETURN_TO_CONVERSATION",
            "RESTART", "STATUS");
    private CoordinationRecovery() {}

    public static void rejected(Storage storage, String category, String operation, Event event, Instant now) {
        var value = new JsonObject();
        value.addProperty("category", CATEGORIES.contains(category) ? category : "unknown");
        value.addProperty("operation", OPERATIONS.contains(operation) ? operation : "UNKNOWN");
        value.addProperty("at", now.toString());
        value.addProperty("run", TaskMemory.text(storage, CoordinationMemory.RUN, "none"));
        value.addProperty("revision", TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        value.addProperty("phase", CoordinationMemory.stage(storage));
        value.addProperty("actions", TaskMemory.text(storage, TaskMemory.ACTIONS, "0"));
        value.addProperty("mutationCommitted", false);
        if (event != null && event.sourcePosition() != null) value.addProperty("sourcePosition", event.sourcePosition());
        storage.put(KEY, value);
    }

    /** Legacy, expired or obsolete facts cannot diagnose the current interaction. No polling or inference. */
    public static JsonObject current(Storage storage, Instant now) {
        if (!storage.containsKey(KEY)) return null;
        try {
            var value = storage.get(KEY).getAsJsonObject();
            Instant at = Instant.parse(value.get("at").getAsString());
            if (at.isAfter(now) || !now.isBefore(at.plusSeconds(TTL_SECONDS))
                    || !value.get("run").getAsString().equals(TaskMemory.text(storage, CoordinationMemory.RUN, "none"))
                    || !value.get("revision").getAsString().equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"))
                    || !value.get("actions").getAsString().equals(TaskMemory.text(storage, TaskMemory.ACTIONS, "0"))
                    || !value.get("phase").getAsString().equals(CoordinationMemory.stage(storage))) return null;
            return value.deepCopy();
        } catch (RuntimeException invalid) { return null; }
    }

    public static void clear(Storage storage) { CoordinationMemory.remove(storage, KEY); }
}
