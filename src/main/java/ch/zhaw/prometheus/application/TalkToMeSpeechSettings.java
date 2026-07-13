package ch.zhaw.prometheus.application;

import java.util.Locale;
import java.util.Set;

public class TalkToMeSpeechSettings {
    private static final String DEFAULT_VOICE = "alloy";
    private static final double DEFAULT_SPEED = 1.0;
    private static final Set<String> VOICES = Set.of(
            "alloy", "ash", "ballad", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer",
            "verse", "marin", "cedar");

    private final String voice;
    private final double speed;

    public TalkToMeSpeechSettings(String voice, String speed) {
        this.voice = normalizeVoice(voice);
        this.speed = parseSpeed(speed);
    }

    public String getVoice() {
        return this.voice;
    }

    public double getSpeed() {
        return this.speed;
    }

    private static String normalizeVoice(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_VOICE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!VOICES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported speech voice: " + raw);
        }
        return normalized;
    }

    private static double parseSpeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SPEED;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed) || parsed < 0.25 || parsed > 4.0) {
                throw new IllegalArgumentException("speech speed out of range: " + raw);
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid speech speed: " + raw, failure);
        }
    }
}
