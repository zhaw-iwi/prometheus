package ch.zhaw.prometheus.controllers.views;

public class RecordedSpeechTurnView {
    private final String transcript;
    private final ResponseView response;
    private final String audioContentType;
    private final String audioBase64;

    public RecordedSpeechTurnView(String transcript, ResponseView response, String audioContentType,
            String audioBase64) {
        this.transcript = transcript;
        this.response = response;
        this.audioContentType = audioContentType;
        this.audioBase64 = audioBase64;
    }

    public String getTranscript() {
        return this.transcript;
    }

    public ResponseView getResponse() {
        return this.response;
    }

    public String getAudioContentType() {
        return this.audioContentType;
    }

    public String getAudioBase64() {
        return this.audioBase64;
    }
}
