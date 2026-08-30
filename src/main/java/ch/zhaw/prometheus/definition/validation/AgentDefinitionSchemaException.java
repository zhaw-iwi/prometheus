package ch.zhaw.prometheus.definition.validation;

import java.util.List;

public final class AgentDefinitionSchemaException extends IllegalArgumentException {
    private final List<SchemaViolation> violations;

    public AgentDefinitionSchemaException(List<SchemaViolation> violations) {
        super("Agent definition failed structural validation with " + violations.size() + " violation(s)");
        this.violations = List.copyOf(violations);
    }

    public List<SchemaViolation> violations() {
        return this.violations;
    }
}
