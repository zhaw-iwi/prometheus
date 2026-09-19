package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.spi.InferencePurpose;
import ch.zhaw.prometheus.spi.InferenceRequest;
import ch.zhaw.prometheus.spi.InferenceResult;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Deterministic composition into the existing behaviour contract; never chooses task transitions. */
final class BehaviourPlanInference {
    private BehaviourPlanInference() {}

    static BehaviourPlan generate(List<PromptMessage> speechMessages, String nonverbalPrompt,
            boolean gestureOnly, LanguageModelGateway gateway) {
        String raw = gateway.infer(request(speechMessages, nonverbalPrompt, gestureOnly));
        return parse(raw, nonverbalPrompt != null && !nonverbalPrompt.isBlank());
    }

    static InferenceRequest request(List<PromptMessage> speechMessages, String nonverbalPrompt, boolean gestureOnly) {
        var messages = new ArrayList<>(speechMessages);
        boolean nonverbalRequired = nonverbalPrompt != null && !nonverbalPrompt.isBlank();
        if (nonverbalRequired) messages.add(PromptMessage.system("""
                Output one JSON behaviour plan, not raw speech. No Markdown or explanations.
                The following instructions describe canonical fields; the final encoding rule
                changes their JSON representation only.
                The previous conversation/task/starter instructions govern the speech field.
                Put the exact user-facing spoken response in the string field "speech".
                Generate coordinated nonverbal behaviour in the object field "nonVerbal".
                Use that same speech string wherever the nonverbal instructions refer to assistant speech.
                Apply the following authored nonverbal instructions. If they describe a nonverbal
                object or a single gesture label, put that result inside nonVerbal.
                If they describe an envelope with nonVerbal and motion/display, put those channels
                at the matching top level of this behaviour plan; do not nest a second envelope.
                In either case, preserve the speech field governed by the conversation instructions:
                <nonverbal-instructions>
                """ + nonverbalPrompt + "\n</nonverbal-instructions>\n"
                + (gestureOnly ? "Put the selected label in nonVerbal.gesture.\n" : "")
                + "The plan contains speech and nonverbal behaviour. Only include optional motion/display objects when the task explicitly requires them."
                + " Do not invent new capabilities. Return one complete valid JSON object."
                + CompactBehaviourPlan.INSTRUCTIONS));
        else messages.add(PromptMessage.system("""
                Return one minified JSON behaviour plan with only the string field "speech".
                Put the exact user-facing spoken response, governed by the previous conversation,
                task and starter instructions, inside speech. Preserve punctuation and whitespace
                inside the spoken text. No other behaviour modalities are configured for this policy;
                omit nv, nonVerbal, motion and display. No Markdown, explanations or raw text outside JSON.
                """));
        return new InferenceRequest(InferencePurpose.BEHAVIOUR, messages, InferenceRequest.Output.JSON_OBJECT);
    }

    static BehaviourPlan parse(String raw) {
        return parse(raw, true);
    }

    private static BehaviourPlan parse(String raw, boolean nonverbalRequired) {
        JsonElement parsed = InferenceResult.json(raw);
        if (!parsed.isJsonObject()) throw invalid();
        JsonObject object = parsed.getAsJsonObject();
        String stage = object.has("nv") ? "behaviour_decode_compact" : "behaviour_decode_canonical";
        return LatencyTrace.measure(stage, () -> parseCanonical(CompactBehaviourPlan.expand(object), nonverbalRequired));
    }

    private static BehaviourPlan parseCanonical(JsonObject object, boolean nonverbalRequired) {
        if (!Set.of("speech", "nonVerbal", "motion", "display").containsAll(object.keySet())) throw invalid();
        JsonElement speech = object.get("speech");
        if (speech == null || !speech.isJsonPrimitive() || !speech.getAsJsonPrimitive().isString()
                || speech.getAsString().isBlank()) throw invalid();
        JsonElement nonverbal = object.get("nonVerbal");
        if (nonverbal == null) {
            if (nonverbalRequired) throw invalid();
            return new BehaviourPlan(speech.getAsString(), null, motionChannel(object), channel(object, "display"));
        }
        if (!nonverbal.isJsonObject()) throw invalid();
        JsonObject normalized = nonverbal.getAsJsonObject().deepCopy();
        JsonElement gesture = normalized.get("gesture");
        if (gesture != null && !gesture.isJsonNull()
                && (!gesture.isJsonPrimitive() || !gesture.getAsJsonPrimitive().isString())) throw invalid();
        normalized.addProperty("gesture", normalizeGesture(gesture == null || gesture.isJsonNull() ? null : gesture.getAsString()));
        if (normalized.has("motion") && normalized.get("motion").isJsonObject()) {
            normalized.getAsJsonObject("motion").remove("move");
            normalized.getAsJsonObject("motion").remove("turn");
        }
        return new BehaviourPlan(speech.getAsString(), normalized, motionChannel(object), channel(object, "display"));
    }

    private static JsonElement motionChannel(JsonObject object) {
        JsonElement value = channel(object, "motion");
        if (value == null) return null;
        JsonObject motion = value.getAsJsonObject();
        motion.remove("move");
        motion.remove("turn");
        JsonElement sign = motion.remove("handSign");
        if (sign != null && !sign.isJsonNull()) {
            if (!sign.isJsonPrimitive() || !sign.getAsJsonPrimitive().isString()) throw invalid();
            String normalized = sign.getAsString().trim().replace("\"", "").replace("'", "").toLowerCase(Locale.ROOT);
            if (normalized.equals("scissors")) normalized = "scissor";
            if (Set.of("rock", "paper", "scissor").contains(normalized)) motion.addProperty("handSign", normalized);
        }
        return motion.isEmpty() ? null : motion;
    }

    private static JsonElement channel(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonObject()) throw invalid();
        return value.deepCopy();
    }

    private static String normalizeGesture(String value) {
        if (value == null) return "NONE";
        String normalized = value.trim().replace("\"", "").replace("'", "").toUpperCase(Locale.ROOT)
                .replace("-", "_").replace(" ", "_");
        return Set.of("OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE", "NONE").contains(normalized)
                ? normalized : "NONE";
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Inference returned an invalid combined behaviour plan; no behaviour published");
    }
}
