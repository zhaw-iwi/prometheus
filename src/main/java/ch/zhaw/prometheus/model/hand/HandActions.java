package ch.zhaw.prometheus.model.hand;

import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Versioned output contract, separate from camera evidence and legacy RPS. */
public final class HandActions {
    public static final String MODALITY = "motion.handAction";
    public static final Set<String> EFFECTORS = Set.of("left_hand", "right_hand", "both_hands");
    private HandActions() {}
    public static boolean enabled(AgentInteractionProfile profile) {
        return profile != null && profile.getSupportedBehaviourModalities().contains(MODALITY);
    }
    public static String sign(String value) {
        String label = value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (label) {
            case "halt" -> "open_palm";
            case "attention" -> "index_up";
            case "hang_loose" -> "shaka";
            case "rock_n_roll", "rock_n'_roll", "rock_and_roll" -> "horns";
            default -> label;
        };
    }
    public static JsonObject named(String sign, String effector) {
        var action = new JsonObject(); action.addProperty("kind", "sign");
        action.addProperty("sign", sign); action.addProperty("effector", effector);
        var envelope = new JsonObject(); envelope.addProperty("schemaVersion", 1); envelope.add("action", action);
        validate(envelope); return envelope;
    }
    public static JsonObject pose(JsonObject action) {
        var envelope=new JsonObject();envelope.addProperty("schemaVersion",1);envelope.add("action",action.deepCopy());
        validate(envelope);return envelope;
    }
    public static void validate(JsonElement raw) {
        try {
            var envelope = raw.getAsJsonObject();
            if (!envelope.keySet().equals(Set.of("schemaVersion", "action"))) throw new IllegalArgumentException();
            var version = envelope.getAsJsonPrimitive("schemaVersion");
            if (!version.isNumber() || version.getAsDouble() != 1) throw new IllegalArgumentException();
            var action = envelope.getAsJsonObject("action");
            if ("pose".equals(text(action,"kind"))) {
                if(!action.keySet().equals(Set.of("kind","hands")))throw new IllegalArgumentException();
                var hands=action.getAsJsonArray("hands");if(hands.isEmpty()||hands.size()>2)throw new IllegalArgumentException();
                var sides=new HashSet<String>();
                for(var item:hands) {
                    var hand=item.getAsJsonObject();
                    if(!hand.keySet().equals(Set.of("side","fingers"))||!Set.of("left","right").contains(text(hand,"side"))||!sides.add(text(hand,"side")))throw new IllegalArgumentException();
                    var fingers=hand.getAsJsonObject("fingers");if(!fingers.keySet().equals(HandPoseObservation.FINGERS))throw new IllegalArgumentException();
                    fingers.asMap().values().forEach(HandSignObservation::number);
                }
            } else if (!action.keySet().equals(Set.of("kind", "sign", "effector")) || !"sign".equals(text(action, "kind"))
                    || !HandSignObservation.SIGNS.contains(text(action, "sign")) || !EFFECTORS.contains(text(action, "effector")))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Unsupported hand action", invalid); }
    }
    public static void requireCapability(BehaviourPlan plan, AgentInteractionProfile profile) {
        if (plan.getMotion() == null || !plan.getMotion().isJsonObject() || !plan.getMotion().getAsJsonObject().has("handAction")) return;
        if(!enabled(profile))throw new IllegalArgumentException("Named hand output is not declared for this instance");
        var action=plan.getMotion().getAsJsonObject().getAsJsonObject("handAction").getAsJsonObject("action");
        if("pose".equals(text(action,"kind")) ? !HandFeatures.poses(profile) : !HandFeatures.signAllowed(text(action,"sign"),HandFeatures.poses(profile)))
            throw new IllegalArgumentException("This instance does not declare pose/derived output");
    }
    private static String text(JsonObject object, String name) {
        var value = object.getAsJsonPrimitive(name);
        if (!value.isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    public static String guidance(AgentInteractionProfile profile) {
        if (!enabled(profile)) return "";
        return "\nNamed hand output overrides legacy-only motion guidance: motion is exactly {\"handAction\":{\"schemaVersion\":1,"
                + "\"action\":{\"kind\":\"sign\",\"sign\":\"fist|peace|open_palm|thumbs_up|thumbs_down|index_up\","
                + "\"effector\":\"left_hand|right_hand|both_hands\"}}}. Default right_hand. Hold 500 ms then release. "
                + "Halt means open_palm; attention means index_up. Clarify ambiguous OK (thumbs up or diver ring); ring is unavailable. "
                + "Use this same output for immediate demonstrations and agreed task actions, preserving requested silence. "
                + "Plans describe digital output, never proof of physical execution. Do not mix handAction with legacy handSign.\n"
                + (HandFeatures.poses(profile) ? "Also supported: shaka (hang loose), horns (rock n roll), middle_finger, and custom finger poses. "
                    + "For custom poses, handAction.action is {\"kind\":\"pose\",\"hands\":[{\"side\":\"left|right\",\"fingers\":{\"thumb\":0.5,\"index\":0,\"middle\":0.5,\"ring\":0.5,\"little\":0.5}}]}. "
                    + "Use 1..2 distinct sides and ALL five numeric bends per output hand: 0=stretched, 1=bent, 0.5=neutral/default for unspecified fingers. "
                    + "Only requested hands move. Never invent orientation/spread/proximity constraints; those are unavailable.\n" : "");
    }
}
