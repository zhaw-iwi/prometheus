package ch.zhaw.prometheus.definition.instance;

public final class DeclarativeAgentOptimisticLockException extends RuntimeException {
    public DeclarativeAgentOptimisticLockException(String message) {
        super(message);
    }

    public DeclarativeAgentOptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
