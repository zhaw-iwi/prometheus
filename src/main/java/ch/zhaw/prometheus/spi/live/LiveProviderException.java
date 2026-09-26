package ch.zhaw.prometheus.spi.live;

public class LiveProviderException extends RuntimeException {
    public LiveProviderException(String message) { super(message); }
    public LiveProviderException(String message, Throwable cause) { super(message, cause); }
}
