package ch.zhaw.prometheus.spi;

public class RealtimeCallConfig {
    private final String instructions;
    private final String voice;
    private final String turnDetection;

    public RealtimeCallConfig(String instructions, String voice, String turnDetection) {
        this.instructions = instructions;
        this.voice = voice;
        this.turnDetection = turnDetection;
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
}
