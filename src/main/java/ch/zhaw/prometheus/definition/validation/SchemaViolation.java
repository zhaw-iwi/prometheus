package ch.zhaw.prometheus.definition.validation;

public record SchemaViolation(String pointer, String keyword, String message) {
}
