package ch.zhaw.prometheus.definition.repository;

public class DefinitionLifecycleException extends RuntimeException {
    public DefinitionLifecycleException(String message) {
        super(message);
    }

    public DefinitionLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }
}
