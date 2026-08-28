package ch.zhaw.prometheus.spi;

public record LiveTranscriptionSessionInfo(String clientSecret, String model, String webRtcUrl) {
}
