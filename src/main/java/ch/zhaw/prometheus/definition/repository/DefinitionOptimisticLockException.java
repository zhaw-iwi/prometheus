package ch.zhaw.prometheus.definition.repository;

public final class DefinitionOptimisticLockException extends DefinitionLifecycleException {
    public DefinitionOptimisticLockException(String message) {
        super(message);
    }

    public DefinitionOptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
