package ch.zhaw.prometheus.spi;

public class SpeechSynthesisException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SpeechSynthesisException(String message) {
        super(message);
    }

    public SpeechSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}
