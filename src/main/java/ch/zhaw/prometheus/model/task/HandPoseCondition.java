package ch.zhaw.prometheus.model.task;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.FingerState;

/** A bounded conjunction evaluated on a single hand, never across hands or observations. */
public record HandPoseCondition(String handedness, List<Comparison> all) {
    public record Comparison(String finger, String operator, JsonPrimitive value) {}
    private static final Set<String> FINGERS = Set.of("thumb", "index", "middle", "ring", "little");
    public HandPoseCondition { all = List.copyOf(all); }

    public static HandPoseCondition parse(JsonElement raw) {
        var object = raw.getAsJsonObject();
        TaskSpec.exact(object, Set.of("handedness", "all"));
        String side = TaskSpec.string(object, "handedness", 5);
        if (!Set.of("any", "left", "right").contains(side)) throw new IllegalArgumentException();
        var comparisons = object.getAsJsonArray("all");
        if (comparisons.isEmpty() || comparisons.size() > 5) throw new IllegalArgumentException();
        var seen = new HashSet<String>(); var all = new ArrayList<Comparison>();
        for (var item : comparisons) {
            var comparison = item.getAsJsonObject();
            TaskSpec.exact(comparison, Set.of("finger", "operator", "value"));
            String finger = TaskSpec.string(comparison, "finger", 6), op = TaskSpec.string(comparison, "operator", 2);
            var value = comparison.getAsJsonPrimitive("value");
            if (!FINGERS.contains(finger) || !seen.add(finger) || !Set.of("eq", "lt", "gt").contains(op)) throw new IllegalArgumentException();
            if (value.isString()) {
                if (!op.equals("eq") || FingerState.from(value) == null) throw new IllegalArgumentException();
            } else {
                if (!value.isNumber() || !Double.isFinite(value.getAsDouble())
                        || value.getAsDouble() < 0 || value.getAsDouble() > 1) throw new IllegalArgumentException();
                if ((op.equals("lt") && value.getAsDouble() == 0) || (op.equals("gt") && value.getAsDouble() == 1))
                    throw new IllegalArgumentException("Impossible finger bend condition");
                value = new JsonPrimitive(value.getAsDouble());
            }
            all.add(new Comparison(finger, op, value.deepCopy()));
        }
        return new HandPoseCondition(side, all);
    }

    public boolean matches(JsonObject payload) {
        var hands = payload.getAsJsonArray("hands");
        if (hands == null || hands.size() > 2) return false;
        for (var item : hands) {
            if (!item.isJsonObject()) continue;
            var hand = item.getAsJsonObject();
            var side = hand.get("handedness");
            if (!handedness.equals("any") && (side == null || !side.isJsonPrimitive()
                    || !side.getAsJsonPrimitive().isString() || !handedness.equals(side.getAsString()))) continue;
            var fingers = hand.get("fingers");
            var states = hand.get("fingerStates");
            if (fingers != null && fingers.isJsonObject() && all.stream().allMatch(c -> matches(c,
                    fingers.getAsJsonObject().get(c.finger()), states != null && states.isJsonObject()
                            ? states.getAsJsonObject().get(c.finger()) : null)))
                return true;
        }
        return false;
    }
    private static boolean matches(Comparison c, JsonElement raw, JsonElement state) {
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) return false;
        double value = raw.getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value > 1) return false;
        if (c.value().isString()) return FingerState.from(state) != null && c.value().equals(state);
        return switch (c.operator()) {
            case "eq" -> value == c.value().getAsDouble();
            case "lt" -> value < c.value().getAsDouble();
            case "gt" -> value > c.value().getAsDouble();
            default -> false;
        };
    }
    public String description() {
        return (handedness.equals("any") ? "any single hand" : handedness + " hand") + ": "
                + all.stream().map(c -> c.finger() + " " + c.operator() + " " + c.value().getAsString())
                    .collect(java.util.stream.Collectors.joining(" AND "));
    }

    /** Appended at inference time so persisted configuration prompts gain the same validated extension. */
    public static String instructions() {
        return """

                Hand-pose rule extension (overrides the scalar-only rule description for obs.hand.pose):
                Bend is numeric, 0=straight and 1=fully bent. The client also supplies fingerStates:
                below 0.4 is straight, above 0.55 is bent, and 0.4..0.55 inclusive is neutral. Null is unavailable.
                To react to finger states or explicit numeric bend thresholds, use one obs.hand.pose
                rule with exactly eventType, handPose, action, effect, minConfidence, samples, cooldownSeconds.
                Omit field/operator/value on that rule. handPose has exactly handedness (any, left or right)
                and all (1..5 comparisons). Each comparison has exactly finger (thumb/index/middle/ring/little),
                operator (eq/lt/gt), value (number 0..1, or straight/neutral/bent as a JSON string with eq only).
                Each finger appears at most once. For ordinary 'bent', 'straight' or 'neutral' requests use eq
                with that state string; do not ask the user to supply thresholds for these established labels.
                Use numeric comparisons only for an explicitly requested numeric threshold.
                All comparisons must match the SAME hand in the SAME observation; any means either single hand.
                Unspecified fingers are unrestricted. Null/missing/invalid readings cannot satisfy a comparison.
                Use minConfidence 0: handednessConfidence is not confidence in finger bend. Default samples 1
                and cooldownSeconds 3. Repeated qualifying observations may act again after the cooldown.
                Example combining four classified finger conditions in one rule:
                {"eventType":"obs.hand.pose","handPose":{"handedness":"any","all":[
                {"finger":"index","operator":"eq","value":"bent"},{"finger":"ring","operator":"eq","value":"bent"},
                {"finger":"little","operator":"eq","value":"bent"},{"finger":"middle","operator":"eq","value":"straight"}]},
                "action":"Give the agreed response","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}.
                This is one rule, not four independent rules. For a future cue set firstAction ON_CUE.
                Never replace requested bend comparisons with obs.hand.sign or a named sign such as middle_finger;
                pose observations contain no named sign. State rules use supplied classifications; missing labels
                remain unknown, including older numeric-only observations. Neutral is not unknown or unavailable.
                Named-sign rules remain appropriate only for requests using the named-sign detector.
                Coordination preparation checks may likewise use exactly eventType, handPose, minConfidence.
                """;
    }
}
