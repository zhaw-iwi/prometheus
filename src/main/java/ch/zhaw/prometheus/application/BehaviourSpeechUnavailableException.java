package ch.zhaw.prometheus.application;

public class BehaviourSpeechUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BehaviourSpeechUnavailableException(String message) {
        super(message);
    }
}
