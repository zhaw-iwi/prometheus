package ch.zhaw.prometheus.model.policy;

import java.util.Locale;

public enum OutputProfile {
    FULL_PLAN,
    REALTIME_SPEECH,
    BACKEND_COMPLEMENT;

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
            case "REALTIME_SPEECH" -> REALTIME_SPEECH;
            case "BACKEND_COMPLEMENT" -> BACKEND_COMPLEMENT;
            default -> null;
        };
    }
}
