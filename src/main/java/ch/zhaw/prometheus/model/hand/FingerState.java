package ch.zhaw.prometheus.model.hand;

import com.google.gson.JsonElement;

/** Client-supplied classification: straight below 0.4, bent above 0.55, otherwise neutral. Missing data is unknown. */
public enum FingerState {
    STRAIGHT("straight"), NEUTRAL("neutral"), BENT("bent");
    private final String label;
    FingerState(String label) { this.label = label; }
    public String label() { return label; }
    public static FingerState from(JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) return null;
        return switch (raw.getAsString()) {
            case "straight" -> STRAIGHT;
            case "neutral" -> NEUTRAL;
            case "bent" -> BENT;
            default -> null;
        };
    }
}
