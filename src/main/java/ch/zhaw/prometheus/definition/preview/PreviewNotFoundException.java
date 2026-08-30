package ch.zhaw.prometheus.definition.preview;

public final class PreviewNotFoundException extends RuntimeException {
    public PreviewNotFoundException() {
        super("Preview session does not exist or has expired");
    }
}
