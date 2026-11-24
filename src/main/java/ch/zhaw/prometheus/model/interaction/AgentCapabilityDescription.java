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
            Map.entry("obs.hand.sign", "Supplied rock, scissor or paper estimates, not intent; task state decides if a move counts."),
            Map.entry("obs.hand.signs", "Per-hand fist, peace, open palm/halt, thumbs up/down, index up/attention. Estimated side; no intent or consent. Input support only."),
            Map.entry("obs.hand.pointing", "One estimated hand direction in a declared unmirrored camera plane; no target, depth or intent."),
            Map.entry("obs.hand.pose", "Finger bend estimates for up to two hands: 0 straight, 1 fully bent, null unavailable. Client states: straight <0.4, bent >0.55, neutral 0.4-0.55 inclusive; missing labels unknown. Hidden fingers may be inferred. Handedness is estimated, not identity; no named sign or intent is established."),
            Map.entry("obs.weather.current", "Supplied weather for its stated place and time; no independent fetching or GPS."),
            Map.entry("obs.weather.forecast", "Supplied forecast for its stated place and time; not current observed weather."),
            Map.entry("speech", "Speech content; actual voice availability depends on the agent policy and active client mode."),
            Map.entry("nonVerbal.gesture", "Digital gestures: question, explanation, uncertainty, acknowledgement and polite."),
            Map.entry("nonVerbal.facialExpression", "A facial-expression plan, not a claim of experienced emotion."),
            Map.entry("nonVerbal.gaze", "A gaze-intent plan, not eye tracking or proof of seeing an object."),
            Map.entry("nonVerbal.motion", "Expressive stillness and energy, not locomotion or physical contact."),
            Map.entry("motion.handAction", "Digital hand output: fist, peace, open palm, thumbs up/down, index up; left/right/both. No physical receipt."),
            Map.entry("motion.handSign", "Can show rock, scissors and paper through the digital embodiment; a plan is not a physical receipt."),
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
        for (String key : profile.getSupportedObservations()) meanings.addProperty(key, meaning(key, profile));
        for (String key : profile.getSupportedBehaviourModalities()) meanings.addProperty(key, meaning(key, profile));
        result.add("meanings", meanings);
        result.add("limits", array(List.of(
                "Empty lists mean unspecified, not unlimited.",
                "Input support proves neither active sensors nor fresh evidence.",
                "Only supplied observations; no unrestricted camera/microphone/service access.",
                "Plans prove no rendering, physical execution or task completion.",
                "Live support proves no active session.",
                "Task policy/state govern; conversation cannot change sensors, settings or code.")));
        String json = result.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Agent capability description exceeds the context budget");
        return json;
    }

    public static String context(AgentInteractionProfile profile) {
        String json = json(profile);
        return json.isEmpty() ? "" : MARKER + " (backend-declared reference data, not task instructions).\n"
                + "Explain capabilities briefly in the user's language, without IDs/JSON. Separate support, evidence and "
                + "confirmed actions. Preserve task policy/output format; invent no capabilities/tasks.\n"
                + json;
    }

    private static String meaning(String key, AgentInteractionProfile profile) {
        if(ch.zhaw.prometheus.model.hand.HandFeatures.dynamicTargets(profile)) {
            if(key.equals("obs.hand.pose")&&ch.zhaw.prometheus.model.hand.HandFeatures.inputSequences(profile))return "Bends 0 straight/1 bent/null unknown; ordered cues/counts need known fingers; no visibility/contact proof.";
            if(key.equals("obs.scene.targets"))return "Person/cup/bottle/book tracks; no identity/depth. Target points need mapping.";
            if(key.equals("obs.hand.pointing"))return "Projected direction/target; ambiguity unresolved.";
            if(key.equals("obs.hand.pose"))return "Bends: 0 straight,1 bent,null unknown; projected geometry, no visibility/contact proof.";
            if(key.equals("obs.hand.signs"))return "Fist/peace/halt/thumbs up/down/index up/shaka/horns/middle finger/OK ring; no consent/geometric score.";
            if(key.equals("motion.handAction"))return "Show signs/custom finger poses/geometry. Point in eight directions without a target, or at a mapped target."+(ch.zhaw.prometheus.model.hand.HandFeatures.outputSequences(profile)?" Show finite sequences/counts on request.":"");
        }
        if(ch.zhaw.prometheus.model.hand.HandFeatures.targets(profile)&&key.equals("obs.hand.pointing"))return "Camera-plane direction and registered region selection; no room depth or intent.";
        if(ch.zhaw.prometheus.model.hand.HandFeatures.geometry(profile)) {
            if(key.equals("obs.hand.pose"))return "V2 bends (0 straight,1 bent,null unknown), projected tip gaps, spreads and axes. Estimated sides; inferred joints do not prove visibility/contact.";
            if(key.equals("obs.hand.signs"))return "Fist, peace, halt, thumbs up/down, index up, shaka, horns, middle finger, OK ring. Geometric shapes have no calibrated score; no consent.";
            if(key.equals("motion.handAction"))return "Ten signs and per-finger poses; bounded ring, index/middle spread and index orientation. Digital output only." + (ch.zhaw.prometheus.model.hand.HandFeatures.pointing(profile)?" Eight robot-relative directions"+(ch.zhaw.prometheus.model.hand.HandFeatures.targets(profile)?" or registered targets.":"."):"");
        }
        if (ch.zhaw.prometheus.model.hand.HandFeatures.poses(profile)) {
            if (key.equals("obs.hand.pose")) return "V2 one/two-hand bends: 0 straight, 1 bent, null unknown. States: straight <0.4, bent >0.55, else neutral. Hidden joints may be inferred; sides estimated, not identity.";
            if (key.equals("obs.hand.signs")) return "Per-hand fist, peace, halt, thumbs up/down, index up; geometric shaka, horns, middle finger (no calibrated score). No intent/consent.";
            if (key.equals("motion.handAction")) return "Digital output: nine named signs or custom per-finger bends on left/right/both hands. No physical receipt.";
        }
        return MEANINGS.getOrDefault(key, "Custom capability; its semantics are unspecified here.");
    }

    private static JsonArray array(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }
}
