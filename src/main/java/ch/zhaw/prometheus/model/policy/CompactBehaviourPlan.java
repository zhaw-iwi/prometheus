package ch.zhaw.prometheus.model.policy;

import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Provider-only encoding. Canonical plans remain the persistence and client contract. */
final class CompactBehaviourPlan {
    private CompactBehaviourPlan() {}

    static final String INSTRUCTIONS = """

            For the final response, use this compact JSON encoding instead of the full field
            names/layout above. This changes representation only, never speech or behaviour choices.
            Return {"speech":"exact spoken response","nv":{...}} with no formatting whitespace.
            Preserve whitespace and punctuation inside all strings, including speech.
            Within nv, encode the requested nonVerbal fields as follows:
            - g: gesture string, keeping the same semantic label.
            - f: [type,intensity] for facialExpression with exactly these two fields.
            - z: [direction,focus] for gaze with exactly these two fields.
            - m: [stillness,energy] for nonVerbal.motion with exactly these two fields.
            Tuple labels are strings; intensity, stillness and energy are numbers from 0 to 1.
            - x: an object holding all other requested nonVerbal fields under their original
              full names, including posture, prosody, proxemics and custom fields. Also use x
              for any partial, null or extended object that does not fit a tuple exactly.
              Preserve every field/value inside x; do not abbreviate or reinterpret its contents.
            Include a field only when called for by the authored instructions. Do not remove
            requested neutral/default values, invent missing values or shorten the spoken text.
            Never encode the same nonVerbal field both via a short key and inside x. Omit empty x.
            Keep optional top-level motion/display objects under their original full names and
            with their full contents; nv.m is not top-level motion. Do not output nonVerbal
            alongside nv. Return one complete JSON object, no Markdown or explanations.
            """;

    static JsonObject expand(JsonObject object) {
        // Authored/persisted prompts and custom gateways may still return the canonical shape.
        if (!object.has("nv")) return object;
        if (!Set.of("speech", "nv", "motion", "display").containsAll(object.keySet())) throw invalid();
        JsonElement value = object.get("nv");
        if (!value.isJsonObject()) throw invalid();
        JsonObject compact = value.getAsJsonObject();
        if (!Set.of("g", "f", "z", "m", "x").containsAll(compact.keySet())) throw invalid();

        JsonObject nonverbal = new JsonObject();
        if (compact.has("x")) {
            if (!compact.get("x").isJsonObject()) throw invalid();
            nonverbal = compact.getAsJsonObject("x").deepCopy();
        }
        if (compact.has("g")) add(nonverbal, "gesture", compact.get("g").deepCopy());
        expandPair(compact, nonverbal, "f", "facialExpression", "type", "intensity", false, true);
        expandPair(compact, nonverbal, "z", "gaze", "direction", "focus", false, false);
        expandPair(compact, nonverbal, "m", "motion", "stillness", "energy", true, true);

        JsonObject expanded = object.deepCopy();
        expanded.remove("nv");
        expanded.add("nonVerbal", nonverbal);
        return expanded;
    }

    private static void expandPair(JsonObject compact, JsonObject nonverbal, String alias, String field,
            String first, String second, boolean firstNumber, boolean secondNumber) {
        if (!compact.has(alias)) return;
        JsonElement value = compact.get(alias);
        if (!value.isJsonArray()) throw invalid();
        JsonArray pair = value.getAsJsonArray();
        if (pair.size() != 2) throw invalid();
        validateValue(pair.get(0), firstNumber);
        validateValue(pair.get(1), secondNumber);
        JsonObject expanded = new JsonObject();
        expanded.add(first, pair.get(0).deepCopy());
        expanded.add(second, pair.get(1).deepCopy());
        add(nonverbal, field, expanded);
    }

    private static void validateValue(JsonElement value, boolean number) {
        if (!value.isJsonPrimitive()) throw invalid();
        if (number) {
            if (!value.getAsJsonPrimitive().isNumber()) throw invalid();
            double numeric = value.getAsDouble();
            if (!Double.isFinite(numeric) || numeric < 0 || numeric > 1) throw invalid();
        } else if (!value.getAsJsonPrimitive().isString()) {
            throw invalid();
        }
    }

    private static void add(JsonObject object, String field, JsonElement value) {
        if (object.has(field)) throw invalid();
        object.add(field, value);
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Inference returned an invalid compact behaviour plan; no behaviour published");
    }
}
