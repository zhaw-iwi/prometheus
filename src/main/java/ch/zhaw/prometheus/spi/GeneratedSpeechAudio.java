package ch.zhaw.prometheus.spi;

public class GeneratedSpeechAudio {
    private final byte[] bytes;
    private final String contentType;

    public GeneratedSpeechAudio(byte[] bytes, String contentType) {
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        this.contentType = contentType;
    }

    public byte[] getBytes() {
        return this.bytes.clone();
    }

    public String getContentType() {
        return this.contentType;
    }
}
