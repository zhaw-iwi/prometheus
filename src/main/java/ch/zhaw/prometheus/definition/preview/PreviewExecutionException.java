package ch.zhaw.prometheus.definition.preview;

public final class PreviewExecutionException extends RuntimeException {
    public PreviewExecutionException(Throwable cause) {
        super("Preview execution failed", cause);
    }
}
