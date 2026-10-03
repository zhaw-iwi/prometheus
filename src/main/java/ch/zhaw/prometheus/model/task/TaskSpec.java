package ch.zhaw.prometheus.model.task;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.event.Event;

/** A bounded executable configuration, never Java code or a replacement system prompt. */
public record TaskSpec(String goal, int maxActions, List<Rule> rules, boolean sessionBound) {
    public TaskSpec(String goal, int maxActions, List<Rule> rules) { this(goal, maxActions, rules, true); }
    public enum Effect { ACT, WAIT, COMPLETE }
    public record Rule(String eventType, String field, String operator, JsonPrimitive value,
            String action, Effect effect, double minConfidence, int samples, int cooldownSeconds) {
        public boolean complete() { return effect == Effect.COMPLETE; }
    }
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
            exact(object, object.has("sessionBound") ? Set.of("goal", "maxActions", "rules", "sessionBound") : Set.of("goal", "maxActions", "rules"));
            if (object.has("sessionBound") && (!object.get("sessionBound").isJsonPrimitive()
                    || !object.get("sessionBound").getAsJsonPrimitive().isBoolean())) throw new IllegalArgumentException();
            boolean sessionBound = !object.has("sessionBound") || object.get("sessionBound").getAsBoolean();
            String goal = string(object, "goal", 400);
            int max = integer(object, "maxActions", 1, 50);
            var array = object.getAsJsonArray("rules");
            if (array == null || array.isEmpty() || array.size() > 4) throw new IllegalArgumentException();
            List<Rule> rules = new ArrayList<>();
            for (var item : array) {
                var rule = item.getAsJsonObject();
                boolean legacy = !rule.has("effect");
                exact(rule, Set.of("eventType", "field", "operator", "value", "action", legacy ? "complete" : "effect", "minConfidence", "samples", "cooldownSeconds"));
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
                if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException();
                if (legacy && !rule.get("complete").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                Effect effect = legacy ? (rule.get("complete").getAsBoolean() ? Effect.COMPLETE : Effect.ACT)
                        : Effect.valueOf(string(rule, "effect", 12));
                rules.add(new Rule(type, field, op, value.deepCopy(), string(rule, "action", 300), effect,
                        confidence, integer(rule, "samples", 1, 3), integer(rule, "cooldownSeconds", 3, 60)));
            }
            return new TaskSpec(goal, max, List.copyOf(rules), sessionBound);
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid task configuration; use supported observations and bounded rules"); }
    }
    public JsonObject json() { return JSON.toJsonTree(this).getAsJsonObject(); }
    /** Validate new agreements separately so legacy stored data remains inspectable. */
    public TaskSpec executable() {
        var conditions = new HashSet<String>();
        for (var rule : rules) {
            if (!conditions.add(rule.eventType() + "/" + rule.field() + "/" + rule.operator() + "/" + rule.value()))
                throw new IllegalArgumentException("Conflicting task conditions");
            // Average confidence of detected people says nothing about an empty observed scene.
            if (rule.eventType().equals(Event.TYPE_HUMAN_PRESENCE) && Set.of("humanCount", "trackedCount").contains(rule.field())
                    && rule.operator().equals("eq") && rule.value().getAsDouble() == 0 && rule.minConfidence() != 0)
                throw new IllegalArgumentException("Absence requires observation confidence semantics");
        }
        if (rules.stream().anyMatch(r -> r.effect() == Effect.WAIT) && rules.stream().noneMatch(r -> r.effect() == Effect.ACT))
            throw new IllegalArgumentException("Waiting requires a resumable action rule");
        return this;
    }
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
