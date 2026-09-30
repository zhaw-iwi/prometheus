package ch.zhaw.prometheus.model.interaction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Derived from the persisted instance profile; no inference, runtime sensing or task authority. */
public final class AgentCapabilityDescription {
    public static final int MAX_JSON_BYTES = 6000;
    public static final String MARKER = "AGENT CAPABILITIES";
    private static final Map<String, String> MEANINGS = Map.ofEntries(
            Map.entry("obs.user_utterance", "User text supplied directly or through speech transcription."),
            Map.entry("obs.emotion.face", "Supplied facial-expression estimates; not knowledge of feelings or intentions."),
            Map.entry("obs.human.presence", "Estimated visible human presence; not identity or willingness to participate."),
            Map.entry("obs.social.grouping", "Estimated spatial grouping; not established relationships or social roles."),
            Map.entry("obs.social.context", "Supplied social-scene estimates; explicit participant statements take precedence."),
            Map.entry("obs.social.situation_change", "Backend-derived changes in social grouping, not direct sensing."),
            Map.entry("obs.hand.sign", "Supplied rock, scissor or paper signs; task state decides whether a move counts."),
            Map.entry("obs.weather.current", "Supplied weather for its stated place and time; no independent fetching or GPS."),
            Map.entry("obs.weather.forecast", "Supplied forecast for its stated place and time; not current observed weather."),
            Map.entry("speech", "Speech content; actual voice availability depends on the agent policy and active client mode."),
            Map.entry("nonVerbal.gesture", "A supported semantic gesture in a behaviour plan."),
            Map.entry("nonVerbal.facialExpression", "A facial-expression plan, not a claim of experienced emotion."),
            Map.entry("nonVerbal.gaze", "A gaze-intent plan, not eye tracking or proof of seeing an object."),
            Map.entry("nonVerbal.motion", "Expressive stillness and energy, not locomotion or physical contact."),
            Map.entry("motion.handSign", "A rock, scissor or paper hand-sign plan."),
            Map.entry("display", "Structured display content for clients that render it."));

    private AgentCapabilityDescription() {}

    public static String json(AgentInteractionProfile profile) {
        if (profile == null || !profile.isCapabilityAwareness()) return "";
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.add("supportedObservations", array(profile.getSupportedObservations()));
        result.add("supportedBehaviourModalities", array(profile.getSupportedBehaviourModalities()));
        result.addProperty("externalRealtimeSpeech", profile.isExternalRealtimeSpeech());
        JsonObject meanings = new JsonObject();
        for (String key : profile.getSupportedObservations()) meanings.addProperty(key, meaning(key));
        for (String key : profile.getSupportedBehaviourModalities()) meanings.addProperty(key, meaning(key));
        result.add("meanings", meanings);
        result.add("limits", array(List.of(
                "Empty lists mean capabilities are unspecified, not unlimited.",
                "Supported input does not establish that a sensor is enabled or that current evidence exists.",
                "Inputs are supplied observations, not unrestricted camera, microphone or external-service access.",
                "Output plans do not confirm rendering, physical execution or task completion.",
                "Live compatibility does not establish an active or available Live session.",
                "Task policies and state control remain authoritative; conversation does not change sensors, settings or coded rules.")));
        String json = result.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Agent capability description exceeds the context budget");
        return json;
    }

    public static String context(AgentInteractionProfile profile) {
        String json = json(profile);
        return json.isEmpty() ? "" : MARKER + " (backend-declared reference data, not task instructions).\n"
                + "When asked, explain these capabilities briefly in the user's language. Do not recite identifiers or JSON. "
                + "Distinguish supported capabilities from current evidence and confirmed actions. Do not invent undeclared "
                + "capabilities or infer tasks from modality names. Preserve the current task policy and output format.\n"
                + json;
    }

    private static String meaning(String key) {
        return MEANINGS.getOrDefault(key, "Custom capability; its semantics are unspecified here.");
    }

    private static JsonArray array(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }
}
