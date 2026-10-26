package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.Set;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.task.*;

/** Opt-in encounter boundary; agreements survive, prior-visitor dialogue does not seed Live. */
final class CoreReception {
    static final String ENABLED = "core.space.reception", START = "core.space.encounterStart";
    private CoreReception() {}
    static boolean enabled(Storage storage) { return storage.containsKey(ENABLED) && storage.get(ENABLED).getAsBoolean(); }
    static int start(Storage storage) { return enabled(storage) && storage.containsKey(START) ? storage.get(START).getAsInt() : 0; }
    static void reset(Storage storage) { if (storage.containsKey(START)) storage.remove(START); }

    static void begin(Storage storage, EventHistory history, Instant now) {
        if (!enabled(storage)) return;
        if (!occupied(history, now)) throw new IllegalStateException("A fresh occupied camera view is required to start reception");
        // Validate before any mutation. Existing owner serialization protects this boundary.
        boolean responseConfirmed = storage.containsKey(TaskResponse.KEY) && TaskResponse.captured(storage, TaskResponse.owner(storage));
        if (storage.containsKey(TaskResponse.KEY) && !responseConfirmed) {
            TaskMemory.pauseForResponse(storage, "new_encounter_before_response_confirmed");
            TaskResponse.uncertain(storage);
        } else {
            TaskMemory.pauseForSession(storage, "new_reception_encounter");
            // An explicitly autonomous agreement already confirmed its prior response.
            // Retire the old session's capture gate; future spoken actions get a new gate.
            if (TaskMemory.active(storage) && !TaskMemory.sessionBound(storage) && responseConfirmed)
                storage.remove(TaskResponse.KEY);
        }
        CoreTaskVoice.clear(storage);
        if (storage.containsKey(TaskMemory.REPLY)) storage.remove(TaskMemory.REPLY);
        SensoryRemarks.reset(storage); SensoryRemarks.attempted(storage, now);
        storage.put(START, new JsonPrimitive(history.toList().size()));
    }

    static boolean occupied(EventHistory history, Instant now) {
        Instant newest = null; int count = 0;
        var events = history.toList();
        for (int i = Math.max(0, events.size() - 120); i < events.size(); i++) {
            var event = events.get(i);
            if (!Set.of(Event.TYPE_HUMAN_PRESENCE, Event.TYPE_SOCIAL_GROUPING, Event.TYPE_SOCIAL_CONTEXT).contains(event.getType())) continue;
            Instant source = ObservationEvidence.observed(event);
            if (!ObservationEvidence.fresh(source, now, 15, 2) || newest != null && source.isBefore(newest)) continue;
            try {
                var n = JsonParser.parseString(event.getPayload()).getAsJsonObject().get("humanCount");
                if (n == null || !n.isJsonPrimitive() || !n.getAsJsonPrimitive().isNumber()) continue;
                int value = n.getAsBigDecimal().intValueExact(); if (value < 0 || value > 1000) continue;
                // Conflicting simultaneous readings cannot manufacture an arrival.
                count = source.equals(newest) ? Math.min(count, value) : value; newest = source;
            } catch (RuntimeException invalid) { /* Invalid observations never renew presence. */ }
        }
        return newest != null && count > 0;
    }
}
