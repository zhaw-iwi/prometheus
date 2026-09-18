package ch.zhaw.prometheus.spi;

import java.util.Locale;

/** Formats explicitly supported by the scoped speech delivery contract. */
public enum SpeechAudioFormat {
    MP3("mp3", "audio/mpeg"),
    PCM("pcm", "audio/pcm;rate=24000;channels=1;encoding=s16le");

    private final String wireValue;
    private final String contentType;

    SpeechAudioFormat(String wireValue, String contentType) {
        this.wireValue = wireValue;
        this.contentType = contentType;
    }

    public String wireValue() { return this.wireValue; }
    public String contentType() { return this.contentType; }

    public static SpeechAudioFormat parse(String value) {
        if (value == null || value.isBlank()) return MP3;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mp3" -> MP3;
            case "pcm" -> PCM;
            default -> throw new IllegalArgumentException("unsupported speech audio format");
        };
    }
}
