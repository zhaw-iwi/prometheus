package ch.zhaw.prometheus.spi;

public class SpeechAudio {
    private final byte[] content;
    private final String contentType;

    public SpeechAudio(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("speech audio must not be empty");
        }
        this.content = content.clone();
        this.contentType = contentType == null || contentType.isBlank() ? "audio/mpeg" : contentType;
    }

    public byte[] getContent() {
        return this.content.clone();
    }

    public String getContentType() {
        return this.contentType;
    }
}
