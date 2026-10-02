package ch.zhaw.prometheus.model.task;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.event.Event;

/** A bounded executable configuration, never Java code or a replacement system prompt. */
public record TaskSpec(String goal, int maxActions, List<Rule> rules) {
    public record Rule(String eventType, String field, String operator, JsonPrimitive value,
            String action, boolean complete, double minConfidence, int samples, int cooldownSeconds) {}
    private static final Gson JSON = new Gson();
    public static final Map<String, Set<String>> FIELDS = Map.of(
            Event.TYPE_FACE_EMOTION, Set.of("emotion", "valence", "arousal", "facePresent"),
            Event.TYPE_HUMAN_PRESENCE, Set.of("humanCount", "trackedCount"),
            Event.TYPE_SOCIAL_GROUPING, Set.of("humanCount", "groupCount", "singletonCount", "largestGroupSize"),
            Event.TYPE_SOCIAL_CONTEXT, Set.of("humanCount", "groupCount", "singletonCount", "largestGroupSize"),
            Event.TYPE_SOCIAL_SITUATION_CHANGE, Set.of("changeType", "currentHumanCount", "currentGroupCount", "currentLargestGroupSize"),
            Event.TYPE_HAND_SIGN, Set.of("sign"),
            Event.TYPE_WEATHER_CURRENT, Set.of("temperature_c", "condition", "precipitation_mm", "is_day", "cloud_cover"),
            Event.TYPE_WEATHER_FORECAST, Set.of("days.0.condition", "days.0.temperature_min_c", "days.0.temperature_max_c", "days.0.precipitation_mm"));

    public static TaskSpec parse(JsonElement raw) {
        try {
            if (raw == null || !raw.isJsonObject() || JSON.toJson(raw).length() > 1800) throw new IllegalArgumentException();
            var object = raw.getAsJsonObject();
            exact(object, Set.of("goal", "maxActions", "rules"));
            String goal = string(object, "goal", 400);
            int max = integer(object, "maxActions", 1, 50);
            var array = object.getAsJsonArray("rules");
            if (array == null || array.isEmpty() || array.size() > 4) throw new IllegalArgumentException();
            List<Rule> rules = new ArrayList<>();
            for (var item : array) {
                var rule = item.getAsJsonObject();
                exact(rule, Set.of("eventType", "field", "operator", "value", "action", "complete", "minConfidence", "samples", "cooldownSeconds"));
                String type = string(rule, "eventType", 80), field = string(rule, "field", 80), op = string(rule, "operator", 8);
                if (!FIELDS.getOrDefault(type, Set.of()).contains(field) || !Set.of("eq", "lt", "gt").contains(op)) throw new IllegalArgumentException();
                var value = rule.get("value").getAsJsonPrimitive();
                if (value.toString().length() > 100 || (!op.equals("eq") && (!value.isNumber() || !Double.isFinite(value.getAsDouble())))) throw new IllegalArgumentException();
                boolean textual = Set.of("emotion", "changeType", "sign", "condition", "days.0.condition").contains(field);
                boolean bool = Set.of("facePresent", "is_day").contains(field);
                if (textual ? !value.isString() || !op.equals("eq") : bool ? !value.isBoolean() || !op.equals("eq")
                        : !value.isNumber() || !Double.isFinite(value.getAsDouble())) throw new IllegalArgumentException();
                if (!rule.get("minConfidence").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                double confidence = rule.get("minConfidence").getAsDouble();
                if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1 || !rule.get("complete").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                rules.add(new Rule(type, field, op, value.deepCopy(), string(rule, "action", 300), rule.get("complete").getAsBoolean(),
                        confidence, integer(rule, "samples", 1, 3), integer(rule, "cooldownSeconds", 3, 60)));
            }
            return new TaskSpec(goal, max, List.copyOf(rules));
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid task configuration; use supported observations and bounded rules"); }
    }
    public JsonObject json() { return JSON.toJsonTree(this).getAsJsonObject(); }
    static void exact(JsonObject object, Set<String> fields) {
        if (!object.keySet().equals(fields)) throw new IllegalArgumentException("Unexpected task fields");
    }
    static String string(JsonObject object, String key, int max) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > max) throw new IllegalArgumentException("Invalid " + key);
        return value.getAsString();
    }
    private static int integer(JsonObject object, String key, int min, int max) {
        if (!object.get(key).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        var number = object.get(key).getAsBigDecimal();
        int value = number.intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }
}
