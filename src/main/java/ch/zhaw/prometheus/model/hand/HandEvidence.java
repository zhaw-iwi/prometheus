package ch.zhaw.prometheus.model.hand;

import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;

/** Bounded consumed camera identities, shared by preparation and task execution. */
public final class HandEvidence {
    public static final String ENABLED = "hand.signs.v1", CONSUMED = "hand.consumed";
    private HandEvidence() {}
    public static boolean enabled(Storage storage) {
        return storage.containsKey(ENABLED) && new JsonPrimitive(true).equals(storage.get(ENABLED));
    }
    public static String identity(Event event) {
        if (Event.TYPE_HAND_SIGNS.equals(event.getType())) return HandSignObservation.parse(event.getPayload()).identity();
        if (Event.TYPE_HAND_POSE.equals(event.getType()) && modernPose(event.getPayload())) return HandPoseObservation.parse(event.getPayload()).frame().identity();
        return null;
    }
    public static boolean modernPose(String payload) {
        try {
            var raw=JsonParser.parseString(payload).getAsJsonObject();
            return raw.has("observationId") || raw.has("schemaVersion") && !new JsonPrimitive(1).equals(raw.get("schemaVersion"));
        } catch(RuntimeException invalid) { return false; }
    }
    public static boolean consumed(Storage storage, Event event) {
        try {
            String id = identity(event);
            if (id == null) return false;
            if (!enabled(storage)) return true;
            if (Event.TYPE_HAND_POSE.equals(event.getType()) && !HandFeatures.poses(storage)) return true;
            if (Event.TYPE_HAND_SIGNS.equals(event.getType()) && HandSignObservation.parse(event.getPayload()).hands().stream()
                    .anyMatch(h -> h.sign() != null && !HandFeatures.signAllowed(h.sign(),HandFeatures.poses(storage)))) return true;
            if (storage.containsKey(CONSUMED)) for (var value : storage.get(CONSUMED).getAsJsonArray())
                if (id.equals(value.getAsString())) return true;
            return false;
        } catch (RuntimeException invalid) { return true; }
    }
    public static void consume(Storage storage, Event event) {
        String id = identity(event); if (id == null) return;
        var values = storage.containsKey(CONSUMED) ? storage.get(CONSUMED).getAsJsonArray().deepCopy() : new JsonArray();
        if (!values.contains(new JsonPrimitive(id))) values.add(id);
        while (values.size() > 128) values.remove(0);
        storage.put(CONSUMED, values);
    }
}
