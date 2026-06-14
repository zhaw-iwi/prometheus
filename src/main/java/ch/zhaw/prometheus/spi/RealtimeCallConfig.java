package ch.zhaw.prometheus.spi;

public class RealtimeCallConfig {
    private final String instructions;
    private final String voice;
    private final String turnDetection;
    private final String languageCode;

    public RealtimeCallConfig(String instructions, String voice, String turnDetection) {
        this(instructions, voice, turnDetection, null);
    }

    public RealtimeCallConfig(String instructions, String voice, String turnDetection, String languageCode) {
        this.instructions = instructions;
        this.voice = voice;
        this.turnDetection = turnDetection;
        this.languageCode = languageCode;
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
}
