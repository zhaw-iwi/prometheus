package ch.zhaw.prometheus.model.task;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.hand.FingerState;
import ch.zhaw.prometheus.model.hand.HandRelation;

/** Bounded same-hand comparisons, optionally composed over two distinct hands in one capture. */
public record HandPoseCondition(String handedness, List<Comparison> all, Integer schemaVersion, List<HandPoseCondition> hands, List<HandRelation> relations) {
    public record Comparison(String finger, String operator, JsonPrimitive value) {}
    private static final Set<String> FINGERS = Set.of("thumb", "index", "middle", "ring", "little");
    public HandPoseCondition(String handedness, List<Comparison> all) { this(handedness,all,null,null,null); }
    public HandPoseCondition { all = all == null ? null : List.copyOf(all); hands = hands == null ? null : List.copyOf(hands); relations=relations==null?null:List.copyOf(relations); }
    public boolean versioned() { return schemaVersion != null; }
    public boolean relational() { return relations!=null&&!relations.isEmpty() || hands!=null&&hands.stream().anyMatch(HandPoseCondition::relational); }

    public static HandPoseCondition parse(JsonElement raw) {
        return parse(raw,false);
    }
    private static HandPoseCondition parse(JsonElement raw,boolean allowRelations) {
        var object = raw.getAsJsonObject();
        if(object.has("schemaVersion")) {
            TaskSpec.exact(object,Set.of("schemaVersion","hands"));
            ch.zhaw.prometheus.model.hand.HandSignObservation.integer(object.get("schemaVersion"),1,1);
            var array=object.getAsJsonArray("hands");if(array.isEmpty()||array.size()>2)throw new IllegalArgumentException();
            var hands=new ArrayList<HandPoseCondition>();var sides=new HashSet<String>();
            for(var item:array) {
                if(!item.isJsonObject()||item.getAsJsonObject().has("schemaVersion"))throw new IllegalArgumentException();
                var condition=parse(item,true);
                if(!condition.handedness().equals("any")&&!sides.add(condition.handedness()))throw new IllegalArgumentException("Conflicting hand selection");
                hands.add(condition);
            }
            return new HandPoseCondition(null,null,1,hands,null);
        }
        TaskSpec.exact(object, allowRelations&&object.has("relations")?Set.of("handedness", "all","relations"):Set.of("handedness", "all"));
        String side = TaskSpec.string(object, "handedness", 5);
        if (!Set.of("any", "left", "right").contains(side)) throw new IllegalArgumentException();
        var comparisons = object.getAsJsonArray("all");
        if (comparisons.size() > 5 || comparisons.isEmpty()&&(!allowRelations||!object.has("relations")||object.getAsJsonArray("relations").isEmpty())) throw new IllegalArgumentException();
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
        return new HandPoseCondition(side, all,null,null,object.has("relations")?HandRelation.parse(object.get("relations")):null);
    }

    public boolean matches(JsonObject payload) {
        if(versioned()) {
            var observed=ch.zhaw.prometheus.model.hand.HandPoseObservation.parse(payload.toString()).frame().hands();
            return assign(observed,0,new HashSet<>());
        }
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
                            ? states.getAsJsonObject().get(c.finger()) : null))
                    && (relations==null||relations.stream().allMatch(r->r.matches(hand.getAsJsonObject("geometry")))))
                return true;
        }
        return false;
    }
    private boolean assign(JsonArray observed,int next,Set<String> used) {
        if(next==hands.size())return true;
        var condition=hands.get(next);
        if(!condition.handedness().equals("any")) {
            int count=0;
            for(var item:observed)if(condition.handedness().equals(item.getAsJsonObject().get("handedness").getAsString()))count++;
            if(count!=1)return false;
        }
        for(var item:observed) {
            String id=item.getAsJsonObject().get("id").getAsString();if(used.contains(id))continue;
            var one=new JsonObject();var array=new JsonArray();array.add(item);one.add("hands",array);
            if(!condition.matches(one))continue;
            used.add(id);if(assign(observed,next+1,used))return true;used.remove(id);
        }
        return false;
    }
    public String key() {
        return versioned()?"v1/"+hands.stream().map(HandPoseCondition::key).sorted().toList()
                :handedness+"/"+all.stream().sorted(Comparator.comparing(Comparison::finger)).toList()
                    +(relations==null?"":relations.stream().map(Object::toString).sorted().toList());
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
        if(versioned())return hands.stream().map(HandPoseCondition::description).collect(java.util.stream.Collectors.joining(" AND a distinct "));
        return (handedness.equals("any") ? "any single hand" : handedness + " hand") + ": "
                + java.util.stream.Stream.concat(
                    all.stream().map(c -> c.finger() + " " + c.operator() + " " + c.value().getAsString()),
                    relations==null?java.util.stream.Stream.empty():relations.stream().map(r->r.description()))
                    .collect(java.util.stream.Collectors.joining(" AND "));
    }

    public static String coordinationInstructions() {
        return "\nPose v2 coordination: obs.hand.pose rules use handPose exactly {\"schemaVersion\":1,\"hands\":[{\"handedness\":\"any|left|right\","
                + "\"all\":[{\"finger\":\"thumb|index|middle|ring|little\",\"operator\":\"eq|lt|gt\",\"value\":\"straight|neutral|bent\"}]}]}. "
                + "Use 1..2 distinct hands and 1..5 distinct finger comparisons per hand. Partial input masks leave other fingers unconstrained. "
                + "Stretched means straight; bend means bent. eq accepts a state or explicit numeric 0..1; lt/gt only numbers. "
                + "All hands and fingers must match ONE frame; unknown is never neutral. minConfidence must be 0 (bend is not a confidence score). "
                + "Other rule fields are action, effect, samples and cooldownSeconds. Checks have exactly eventType, handPose, minConfidence. "
                + "Use named obs.hand.signs for shaka, horns and middle_finger with minConfidence 0. Their geometric extension/opposed-thumb "
                + "classification differs from mean joint bend; do not translate those names into numeric pose masks. Geometry has no calibrated score.\n";
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
