package ch.zhaw.prometheus.spi;

public class LiveTranscriptionProviderException extends RuntimeException {

    public LiveTranscriptionProviderException(String message) {
        super(message);
    }

    public LiveTranscriptionProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
