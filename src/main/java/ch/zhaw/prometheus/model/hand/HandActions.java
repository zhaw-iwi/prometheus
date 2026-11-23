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
            default -> label;
        };
    }
    public static JsonObject named(String sign, String effector) {
        var action = new JsonObject(); action.addProperty("kind", "sign");
        action.addProperty("sign", sign); action.addProperty("effector", effector);
        var envelope = new JsonObject(); envelope.addProperty("schemaVersion", 1); envelope.add("action", action);
        validate(envelope); return envelope;
    }
    public static void validate(JsonElement raw) {
        try {
            var envelope = raw.getAsJsonObject();
            if (!envelope.keySet().equals(Set.of("schemaVersion", "action"))) throw new IllegalArgumentException();
            var version = envelope.getAsJsonPrimitive("schemaVersion");
            if (!version.isNumber() || version.getAsDouble() != 1) throw new IllegalArgumentException();
            var action = envelope.getAsJsonObject("action");
            if (!action.keySet().equals(Set.of("kind", "sign", "effector")) || !"sign".equals(text(action, "kind"))
                    || !HandSignObservation.SIGNS.contains(text(action, "sign")) || !EFFECTORS.contains(text(action, "effector")))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Unsupported hand action", invalid); }
    }
    public static void requireCapability(BehaviourPlan plan, AgentInteractionProfile profile) {
        if (plan.getMotion() != null && plan.getMotion().isJsonObject() && plan.getMotion().getAsJsonObject().has("handAction")
                && !enabled(profile)) throw new IllegalArgumentException("Named hand output is not declared for this instance");
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
                + "Plans describe digital output, never proof of physical execution. Do not mix handAction with legacy handSign.\n";
    }
}
