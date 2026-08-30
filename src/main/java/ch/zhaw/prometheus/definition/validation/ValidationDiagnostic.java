package ch.zhaw.prometheus.definition.validation;

public record ValidationDiagnostic(
        SemanticDiagnosticCode code,
        DiagnosticSeverity severity,
        String pointer,
        String message,
        String hint) {

    public static ValidationDiagnostic of(SemanticDiagnosticCode code, String pointer, String message,
            String hint) {
        return new ValidationDiagnostic(code, code.severity(), pointer, message, hint);
    }
}
