package ch.zhaw.prometheus.application;

import java.util.Set;

public class RealtimeCallSettings {
    private static final Set<String> VOICES = Set.of(
            "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "marin", "cedar");
    private static final Set<String> TURN_DETECTION = Set.of("server_vad", "semantic_vad");
    private static final Set<String> VAD_EAGERNESS = Set.of("low", "auto", "medium", "high");
    private static final Set<String> INPUT_NOISE_REDUCTION = Set.of("near_field", "far_field", "off");
    private static final Set<String> REASONING_EFFORT = Set.of("low", "medium", "high");

    private final String voice;
    private final String turnDetection;
    private final boolean generateComplement;
    private final Double vadThreshold;
    private final Integer vadPrefixPaddingMs;
    private final Integer vadSilenceDurationMs;
    private final String vadEagerness;
    private final boolean vadInterruptResponse;
    private final String inputNoiseReduction;
    private final Double outputSpeed;
    private final String reasoningEffort;
    private final Integer maxOutputTokens;
    private final boolean includeInputTranscriptionLogprobs;

    public RealtimeCallSettings(String voice, String turnDetection, boolean generateComplement) {
        this(voice, turnDetection, generateComplement, null, null, null, null, null, null, null, null, null, null,
                null);
    }

    public RealtimeCallSettings(String voice, String turnDetection, boolean generateComplement,
            String vadThreshold, String vadPrefixPaddingMs, String vadSilenceDurationMs, String vadEagerness,
            String vadCreateResponse, String vadInterruptResponse, String inputNoiseReduction, String outputSpeed,
            String reasoningEffort, String maxOutputTokens, String includeInputTranscriptionLogprobs) {
        this.voice = normalizeVoice(voice);
        this.turnDetection = normalizeTurnDetection(turnDetection);
        this.generateComplement = generateComplement;
        this.vadThreshold = parseOptionalDoubleRange(vadThreshold, 0.0, 1.0, "vadThreshold");
        this.vadPrefixPaddingMs = parseOptionalIntegerRange(vadPrefixPaddingMs, 0, 2000,
                "vadPrefixPaddingMs");
        this.vadSilenceDurationMs = parseOptionalIntegerRange(vadSilenceDurationMs, 0, 3000,
                "vadSilenceDurationMs");
        this.vadEagerness = normalizeOptional(vadEagerness, VAD_EAGERNESS, "VAD eagerness");
        rejectEnabledVadCreateResponse(vadCreateResponse);
        this.vadInterruptResponse = Boolean.TRUE.equals(parseOptionalBoolean(vadInterruptResponse,
                "vadInterruptResponse"));
        this.inputNoiseReduction = normalizeOptional(inputNoiseReduction, INPUT_NOISE_REDUCTION,
                "input noise reduction");
        this.outputSpeed = parseOptionalDoubleRange(outputSpeed, 0.25, 1.5, "outputSpeed");
        this.reasoningEffort = normalizeOptional(reasoningEffort, REASONING_EFFORT, "reasoning effort");
        this.maxOutputTokens = parseOptionalIntegerRange(maxOutputTokens, 1, 4096, "maxOutputTokens");
        this.includeInputTranscriptionLogprobs = Boolean.TRUE.equals(parseOptionalBoolean(
                includeInputTranscriptionLogprobs, "includeInputTranscriptionLogprobs"));
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

    public Double getVadThreshold() {
        return this.vadThreshold;
    }

    public Integer getVadPrefixPaddingMs() {
        return this.vadPrefixPaddingMs;
    }

    public Integer getVadSilenceDurationMs() {
        return this.vadSilenceDurationMs;
    }

    public String getVadEagerness() {
        return this.vadEagerness;
    }

    public boolean isVadInterruptResponse() {
        return this.vadInterruptResponse;
    }

    public String getInputNoiseReduction() {
        return this.inputNoiseReduction;
    }

    public Double getOutputSpeed() {
        return this.outputSpeed;
    }

    public String getReasoningEffort() {
        return this.reasoningEffort;
    }

    public Integer getMaxOutputTokens() {
        return this.maxOutputTokens;
    }

    public boolean isIncludeInputTranscriptionLogprobs() {
        return this.includeInputTranscriptionLogprobs;
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

    private static String normalizeOptional(String raw, Set<String> allowed, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("unsupported realtime " + label + ": " + raw);
        }
        return normalized;
    }

    private static Double parseOptionalDoubleRange(String raw, double min, double max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
                throw new IllegalArgumentException("realtime " + label + " out of range: " + raw);
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid realtime " + label + ": " + raw, failure);
        }
    }

    private static Integer parseOptionalIntegerRange(String raw, int min, int max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("realtime " + label + " out of range: " + raw);
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid realtime " + label + ": " + raw, failure);
        }
    }

    private static Boolean parseOptionalBoolean(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("invalid realtime " + label + ": " + raw);
    }

    private static void rejectEnabledVadCreateResponse(String raw) {
        if (Boolean.TRUE.equals(parseOptionalBoolean(raw, "vadCreateResponse"))) {
            throw new IllegalArgumentException(
                    "vadCreateResponse=true is unsupported because PROMETHEUS owns speech generation");
        }
    }
}
