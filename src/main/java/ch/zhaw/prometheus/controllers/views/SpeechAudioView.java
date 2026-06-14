package ch.zhaw.prometheus.controllers.views;

public class SpeechAudioView {
    private final String speech;
    private final String audioContentType;
    private final String audioBase64;

    public SpeechAudioView(String speech, String audioContentType, String audioBase64) {
        this.speech = speech;
        this.audioContentType = audioContentType;
        this.audioBase64 = audioBase64;
    }

    public String getSpeech() {
        return this.speech;
    }

    public String getAudioContentType() {
        return this.audioContentType;
    }

    public String getAudioBase64() {
        return this.audioBase64;
    }
}
