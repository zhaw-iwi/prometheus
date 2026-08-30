package ch.zhaw.prometheus.definition.application;

import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;

public final class BundledDefinitionConflictException extends DefinitionLifecycleException {
    public BundledDefinitionConflictException(String message) {
        super(message);
    }
}
