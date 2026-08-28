package ch.zhaw.prometheus.model.policy;

import java.util.Locale;

public enum OutputProfile {
    FULL_PLAN;

    public static OutputProfile fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL_PLAN;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "FULL_PLAN" -> FULL_PLAN;
            default -> null;
        };
    }
}
