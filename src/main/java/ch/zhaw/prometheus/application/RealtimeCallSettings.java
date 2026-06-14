package ch.zhaw.prometheus.application;

import java.util.Set;

public class RealtimeCallSettings {
    private static final Set<String> VOICES = Set.of(
            "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "marin", "cedar");
    private static final Set<String> TURN_DETECTION = Set.of("server_vad", "semantic_vad", "none");

    private final String voice;
    private final String turnDetection;
    private final boolean generateComplement;

    public RealtimeCallSettings(String voice, String turnDetection, boolean generateComplement) {
        this.voice = normalizeVoice(voice);
        this.turnDetection = normalizeTurnDetection(turnDetection);
        this.generateComplement = generateComplement;
    }

    public String getVoice() {
        return this.voice;
    }

    public String getTurnDetection() {
        return this.turnDetection;
    }

    public boolean isGenerateComplement() {
        return this.generateComplement;
    }

    private static String normalizeVoice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (!VOICES.contains(normalized)) {
            throw new IllegalArgumentException("unsupported realtime voice: " + raw);
        }
        return normalized;
    }

    private static String normalizeTurnDetection(String raw) {
        if (raw == null || raw.isBlank()) {
            return "server_vad";
        }
        String normalized = raw.trim().toLowerCase();
        if (!TURN_DETECTION.contains(normalized)) {
            throw new IllegalArgumentException("unsupported realtime turn detection: " + raw);
        }
        return normalized;
    }
}
