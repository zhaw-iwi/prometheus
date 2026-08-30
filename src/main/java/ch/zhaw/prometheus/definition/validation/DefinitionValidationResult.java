package ch.zhaw.prometheus.definition.validation;

import java.util.Comparator;
import java.util.List;

public record DefinitionValidationResult(List<ValidationDiagnostic> diagnostics) {
    public DefinitionValidationResult {
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream()
                .sorted(Comparator.comparing(ValidationDiagnostic::severity)
                        .thenComparing(ValidationDiagnostic::pointer)
                        .thenComparing(diagnostic -> diagnostic.code().name()))
                .toList();
    }

    public boolean isValid() {
        return errors().isEmpty();
    }

    public List<ValidationDiagnostic> errors() {
        return this.diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
                .toList();
    }

    public List<ValidationDiagnostic> warnings() {
        return this.diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING)
                .toList();
    }
}
