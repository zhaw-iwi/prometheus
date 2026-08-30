package ch.zhaw.prometheus.definition.compiled;

import ch.zhaw.prometheus.definition.validation.DefinitionValidationResult;

public final class DefinitionCompilationException extends IllegalArgumentException {
    private final transient DefinitionValidationResult validationResult;

    public DefinitionCompilationException(String message, DefinitionValidationResult validationResult) {
        super(message);
        this.validationResult = validationResult;
    }

    public DefinitionCompilationException(String message, DefinitionValidationResult validationResult,
            Throwable cause) {
        super(message, cause);
        this.validationResult = validationResult;
    }

    public DefinitionValidationResult validationResult() {
        return this.validationResult;
    }
}
