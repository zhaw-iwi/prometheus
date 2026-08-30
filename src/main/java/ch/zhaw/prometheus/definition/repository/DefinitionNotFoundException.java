package ch.zhaw.prometheus.definition.repository;

public final class DefinitionNotFoundException extends DefinitionLifecycleException {
    public DefinitionNotFoundException(String message) {
        super(message);
    }
}
