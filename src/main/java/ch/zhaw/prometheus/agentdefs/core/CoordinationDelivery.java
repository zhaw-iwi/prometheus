package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;

/** Final Live contribution: association plus client playback evidence, never a claim of physical audibility. */
final class CoordinationDelivery {
    static final String ENABLED = "coordination.deliveryEnabled", KEY = "coordination.delivery",
            PLAYED = "sys.coordination.played", FAILED = "sys.coordination.response_failed", ENDED = "sys.coordination.session_ended";
    private CoordinationDelivery() {}
    static boolean enabled(Storage storage) { return storage.containsKey(ENABLED) && storage.get(ENABLED).getAsBoolean(); }
    static boolean finishing(Storage storage) { return "FINISHING".equals(TaskMemory.phase(storage)); }
    static void await(Storage storage, BehaviourPlan plan, PolicyRuntime runtime) {
        if (enabled(storage) && runtime.externalSpeech() != null && "COMPLETED".equals(TaskMemory.phase(storage))
                && plan.getSpeech() != null && !plan.getSpeech().isBlank()) CoordinationMemory.phase(storage, "FINISHING");
    }
    static void attach(Storage storage) {
        if (!finishing(storage) || !storage.containsKey(TaskResponse.KEY)) return;
        var response = storage.get(TaskResponse.KEY).getAsJsonObject(); var delivery = new JsonObject();
        for (String key : List.of("id", "session", "epoch", "revision")) delivery.add(key, response.get(key).deepCopy());
        delivery.addProperty("deadline", Instant.parse(response.get("created").getAsString()).plusSeconds(60).toString());
        delivery.addProperty("status", "PENDING"); storage.put(KEY, delivery);
    }
    static LivePlaybackCue cue(Storage storage) {
        if (!finishing(storage) || !storage.containsKey(KEY)) return null;
        var delivery = storage.get(KEY).getAsJsonObject();
        if (!TaskMemory.text(storage, TaskMemory.REVISION, "0").equals(delivery.get("revision").getAsString())) return null;
        var response = storage.containsKey(TaskResponse.KEY) ? storage.get(TaskResponse.KEY).getAsJsonObject() : null;
        String segment = response != null && "CAPTURED".equals(TaskResponse.status(storage)) && delivery.get("id").equals(response.get("id"))
                && delivery.get("session").equals(response.get("session")) && delivery.get("epoch").equals(response.get("epoch"))
                ? response.get("segment").getAsString() : null;
        // Keep an expired cue visible so the existing refresh path wakes ordinary timeout evaluation.
        return new LivePlaybackCue(UUID.fromString(delivery.get("id").getAsString()), segment, Instant.parse(delivery.get("deadline").getAsString()));
    }
    static LiveActivityContext withPlayback(Storage storage, LiveActivityContext context) {
        var cue = cue(storage); if (cue == null) return context;
        Instant refresh = context.refreshAt();
        if (refresh == null || cue.expiresAt().isBefore(refresh)) refresh = cue.expiresAt();
        return new LiveActivityContext(context.facts(), context.continuation(), refresh, cue);
    }
    static boolean matches(Storage storage, Event event, PolicyRuntime runtime, Instant now) {
        if (!enabled(storage) || !Event.KIND_SYSTEM.equals(event.getKind())) return false;
        if (ENDED.equals(event.getType()) || FAILED.equals(event.getType()))
            return finishing(storage) || TaskMemory.active(storage);
        var cue = cue(storage);
        if (cue == null) return false;
        if (Event.TYPE_SYSTEM_TICK.equals(event.getType())) return cue.expired(now);
        if (!PLAYED.equals(event.getType()) || cue.segmentId() == null || cue.expired(now)
                || !cue.id().toString().equals(event.getPayload()) || !runtime.taskContinuation().getAsBoolean()) return false;
        var delivery = storage.get(KEY).getAsJsonObject(); var owner = runtime.externalSpeech();
        return owner != null && owner.sessionId().toString().equals(delivery.get("session").getAsString())
                && owner.epoch().toString().equals(delivery.get("epoch").getAsString());
    }
    static void uncertain(Storage storage, String reason, Instant now) {
        if (!finishing(storage)) return;
        var delivery = storage.get(KEY).getAsJsonObject().deepCopy();
        delivery.addProperty("status", "UNCERTAIN"); delivery.addProperty("reason", reason); delivery.addProperty("ended", now.toString());
        storage.put(KEY, delivery); TaskResponse.uncertain(storage);
        CoordinationMemory.phase(storage, "UNCERTAIN"); CoordinationMemory.revise(storage); CoreTaskVoice.clear(storage);
    }
    static void apply(Storage storage, Event event, PolicyRuntime runtime, Instant now) {
        if (!matches(storage, event, runtime, now)) return;
        String before = CoordinationMemory.stage(storage);
        if (PLAYED.equals(event.getType())) {
            var delivery = storage.get(KEY).getAsJsonObject().deepCopy();
            delivery.addProperty("status", "PLAYBACK_CONFIRMED"); delivery.addProperty("ended", now.toString());
            delivery.addProperty("segment", cue(storage).segmentId()); storage.put(KEY, delivery);
            CoordinationMemory.phase(storage, "COMPLETED"); CoordinationMemory.revise(storage); CoreTaskVoice.clear(storage);
        } else if (finishing(storage)) uncertain(storage, Event.TYPE_SYSTEM_TICK.equals(event.getType()) ? "playback_deadline"
                : ENDED.equals(event.getType()) ? "live_session_ended" : "response_uncertain", now);
        else if (ENDED.equals(event.getType())) TaskMemory.pauseForSession(storage, "live_session_ended");
        else TaskMemory.pauseForResponse(storage, "response_uncertain");
        storage.put(TaskMemory.REPLY, new JsonObject());
        CoordinationMemory.record(storage, "DELIVERY_" + TaskMemory.phase(storage), before, event, now);
    }
}
