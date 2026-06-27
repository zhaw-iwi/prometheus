package ch.zhaw.prometheus.spi;

public class RealtimeCallConfig {
    private final String instructions;
    private final String voice;
    private final String turnDetection;
    private final String languageCode;
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

    public RealtimeCallConfig(String instructions, String voice, String turnDetection) {
        this(instructions, voice, turnDetection, null);
    }

    public RealtimeCallConfig(String instructions, String voice, String turnDetection, String languageCode) {
        this(instructions, voice, turnDetection, languageCode, null, null, null, null, false, null, null, null, null,
                false);
    }

    public RealtimeCallConfig(String instructions, String voice, String turnDetection, String languageCode,
            Double vadThreshold, Integer vadPrefixPaddingMs, Integer vadSilenceDurationMs, String vadEagerness,
            boolean vadInterruptResponse, String inputNoiseReduction, Double outputSpeed, String reasoningEffort,
            Integer maxOutputTokens, boolean includeInputTranscriptionLogprobs) {
        this.instructions = instructions;
        this.voice = voice;
        this.turnDetection = turnDetection;
        this.languageCode = languageCode;
        this.vadThreshold = vadThreshold;
        this.vadPrefixPaddingMs = vadPrefixPaddingMs;
        this.vadSilenceDurationMs = vadSilenceDurationMs;
        this.vadEagerness = vadEagerness;
        this.vadInterruptResponse = vadInterruptResponse;
        this.inputNoiseReduction = inputNoiseReduction;
        this.outputSpeed = outputSpeed;
        this.reasoningEffort = reasoningEffort;
        this.maxOutputTokens = maxOutputTokens;
        this.includeInputTranscriptionLogprobs = includeInputTranscriptionLogprobs;
    }

    public String getInstructions() {
        return this.instructions;
    }

    public String getVoice() {
        return this.voice;
    }

    public String getTurnDetection() {
        return this.turnDetection;
    }

    public String getLanguageCode() {
        return this.languageCode;
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
}
