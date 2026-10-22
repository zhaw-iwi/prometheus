package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.Map;
import com.google.gson.*;

/** Source ages shared by speech context and task consumers; caller chooses allowed clock skew. */
public final class ObservationEvidence {
    private ObservationEvidence() {}
    private static final Map<String, Long> TTL = Map.of(
            Event.TYPE_FACE_EMOTION, 15L, Event.TYPE_HUMAN_PRESENCE, 15L, Event.TYPE_SOCIAL_GROUPING, 15L,
            Event.TYPE_SOCIAL_CONTEXT, 15L, Event.TYPE_SOCIAL_SITUATION_CHANGE, 30L, Event.TYPE_HAND_SIGN, 15L,
            Event.TYPE_WEATHER_CURRENT, 600L, Event.TYPE_WEATHER_FORECAST, 21600L);
    public static boolean supported(String type) { return TTL.containsKey(type); }
    public static long ttl(String type) { return TTL.getOrDefault(type, 15L); }
    public static Instant observed(Event event) {
        try { return observed(JsonParser.parseString(event.getPayload()).getAsJsonObject()); }
        catch (RuntimeException invalid) { return null; }
    }
    public static Instant observed(JsonObject data) {
        try {
            var value = data.has("observed_at") ? data.get("observed_at") : data.get("ts");
            return value == null ? null : Instant.parse(value.getAsString());
        } catch (RuntimeException invalid) { return null; }
    }
    public static boolean fresh(Instant observed, Instant now, long ttl, long skewSeconds) {
        return observed != null && !observed.isAfter(now.plusSeconds(skewSeconds)) && now.isBefore(observed.plusSeconds(ttl));
    }
    /** Optional legacy presence/detection fields may be absent; explicit absence/poor detection is not an expression. */
    public static boolean usableFace(JsonObject data, double minimumExpressionConfidence) {
        try {
            if (data.has("facePresent") && (!data.get("facePresent").isJsonPrimitive()
                    || !data.get("facePresent").getAsJsonPrimitive().isBoolean() || !data.get("facePresent").getAsBoolean())) return false;
            if (data.has("faceDetectionConfidence") && !confidence(data.get("faceDetectionConfidence"), .5)) return false;
            return confidence(data.get("confidence"), minimumExpressionConfidence);
        } catch (RuntimeException invalid) { return false; }
    }
    private static boolean confidence(JsonElement value, double minimum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        double n = value.getAsDouble(); return Double.isFinite(n) && n >= minimum && n <= 1;
    }
}
