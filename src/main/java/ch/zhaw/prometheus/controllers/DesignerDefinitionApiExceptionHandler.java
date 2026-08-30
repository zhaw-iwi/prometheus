package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ch.zhaw.prometheus.controllers.DesignerDefinitionController.DefinitionDiagnosticView;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompilationException;
import ch.zhaw.prometheus.definition.document.AgentDefinitionFormatException;
import ch.zhaw.prometheus.definition.repository.DefinitionLifecycleException;
import ch.zhaw.prometheus.definition.repository.DefinitionNotFoundException;
import ch.zhaw.prometheus.definition.repository.DefinitionOptimisticLockException;
import ch.zhaw.prometheus.definition.validation.AgentDefinitionSchemaException;

@RestControllerAdvice(assignableTypes = DesignerDefinitionController.class)
public class DesignerDefinitionApiExceptionHandler {

    @ExceptionHandler(DefinitionNotFoundException.class)
    ResponseEntity<DefinitionApiError> notFound() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested definition or revision does not exist");
    }

    @ExceptionHandler(DefinitionOptimisticLockException.class)
    ResponseEntity<DefinitionApiError> optimisticConflict() {
        return error(HttpStatus.CONFLICT, "OPTIMISTIC_CONFLICT",
                "The definition changed after it was loaded");
    }

    @ExceptionHandler(DefinitionCompilationException.class)
    ResponseEntity<DefinitionApiError> compilation(DefinitionCompilationException failure) {
        List<DefinitionDiagnosticView> diagnostics = failure.validationResult().diagnostics().stream()
                .map(DesignerDefinitionController::diagnostic).toList();
        return new ResponseEntity<>(new DefinitionApiError("VALIDATION_FAILED",
                "The definition cannot be published", diagnostics), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(AgentDefinitionSchemaException.class)
    ResponseEntity<DefinitionApiError> schema(AgentDefinitionSchemaException failure) {
        List<DefinitionDiagnosticView> diagnostics = failure.violations().stream()
                .map(violation -> new DefinitionDiagnosticView("SCHEMA_" + normalized(violation.keyword()), "ERROR",
                        violation.pointer(), violation.message(), "Correct the definition structure"))
                .toList();
        return new ResponseEntity<>(new DefinitionApiError("SCHEMA_VALIDATION_FAILED",
                "The definition does not match schema version 1", diagnostics), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(DefinitionLifecycleException.class)
    ResponseEntity<DefinitionApiError> lifecycleConflict() {
        return error(HttpStatus.CONFLICT, "LIFECYCLE_CONFLICT",
                "The requested lifecycle operation is not allowed");
    }

    @ExceptionHandler({ AgentDefinitionFormatException.class, IllegalArgumentException.class,
            HttpMessageNotReadableException.class })
    ResponseEntity<DefinitionApiError> malformedRequest() {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed or incomplete");
    }

    private static ResponseEntity<DefinitionApiError> error(HttpStatus status, String code, String message) {
        return new ResponseEntity<>(new DefinitionApiError(code, message, List.of()), status);
    }

    private static String normalized(String keyword) {
        return keyword == null ? "INVALID"
                : keyword.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    public record DefinitionApiError(String code, String message, List<DefinitionDiagnosticView> diagnostics) {
    }
}
