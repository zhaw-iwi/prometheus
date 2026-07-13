package ch.zhaw.prometheus.application;

public class TalkToMeSpeechUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TalkToMeSpeechUnavailableException() {
        super("the Talk to Me acknowledgement did not produce a speech behaviour");
    }
}
