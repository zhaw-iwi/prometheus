package ch.zhaw.prometheus.definition.validation;

/**
 * Stable schema-version-1 semantic diagnostic codes. Human-readable messages
 * may improve without changing these machine contracts.
 */
public enum SemanticDiagnosticCode {
    DUPLICATE_STATE_ID(DiagnosticSeverity.ERROR),
    DUPLICATE_TRANSITION_ID(DiagnosticSeverity.ERROR),
    DUPLICATE_STORAGE_KEY(DiagnosticSeverity.ERROR),
    DUPLICATE_RESOURCE_ID(DiagnosticSeverity.ERROR),
    MISSING_INITIAL_STATE(DiagnosticSeverity.ERROR),
    INITIAL_STATE_NOT_ROOT(DiagnosticSeverity.ERROR),
    MISSING_CHILD_STATE(DiagnosticSeverity.ERROR),
    INVALID_INITIAL_CHILD(DiagnosticSeverity.ERROR),
    MULTIPLE_STATE_PARENTS(DiagnosticSeverity.ERROR),
    CONTAINMENT_CYCLE(DiagnosticSeverity.ERROR),
    MISSING_TRANSITION_SOURCE(DiagnosticSeverity.ERROR),
    MISSING_TRANSITION_TARGET(DiagnosticSeverity.ERROR),
    FINAL_STATE_OUTGOING_TRANSITION(DiagnosticSeverity.ERROR),
    DUPLICATE_TRANSITION_ORDER(DiagnosticSeverity.ERROR),
    UNREACHABLE_STATE(DiagnosticSeverity.WARNING),
    UNUSED_OBSERVATION(DiagnosticSeverity.WARNING),
    UNUSED_BEHAVIOUR_MODALITY(DiagnosticSeverity.WARNING),
    UNDECLARED_OBSERVATION(DiagnosticSeverity.ERROR),
    UNDECLARED_BEHAVIOUR_MODALITY(DiagnosticSeverity.ERROR),
    MISSING_STORAGE_BINDING(DiagnosticSeverity.ERROR),
    INCOMPATIBLE_STORAGE_SCHEMA(DiagnosticSeverity.ERROR),
    INVALID_STORAGE_INITIAL_VALUE(DiagnosticSeverity.ERROR),
    STORAGE_SCHEMA_KEYWORD_MISMATCH(DiagnosticSeverity.ERROR),
    STORAGE_SCHEMA_REQUIRED_PROPERTY_UNDECLARED(DiagnosticSeverity.ERROR),
    STORAGE_SCHEMA_INVALID_BOUNDS(DiagnosticSeverity.ERROR),
    MULTIPLE_STORAGE_INITIALIZERS(DiagnosticSeverity.ERROR),
    REQUIRED_STORAGE_UNINITIALIZED(DiagnosticSeverity.ERROR),
    MISSING_SCENARIO_STORAGE(DiagnosticSeverity.ERROR),
    INVALID_SCENARIO_STORAGE_VALUE(DiagnosticSeverity.ERROR),
    MISSING_SCENARIO_STATE(DiagnosticSeverity.ERROR),
    UNDECLARED_SCENARIO_OBSERVATION(DiagnosticSeverity.ERROR),
    MISSING_RESOURCE_REFERENCE(DiagnosticSeverity.ERROR),
    RESOURCE_COMPONENT_MISMATCH(DiagnosticSeverity.ERROR),
    MISSING_STATE_REFERENCE(DiagnosticSeverity.ERROR),
    INVALID_PROMPT_STRUCTURE(DiagnosticSeverity.ERROR),
    DUPLICATE_PROMPT_SECTION_ID(DiagnosticSeverity.ERROR),
    BLANK_PROMPT_SECTION(DiagnosticSeverity.ERROR),
    PROMPT_SECTION_TOO_LARGE(DiagnosticSeverity.ERROR),
    PROMPT_TOO_LARGE(DiagnosticSeverity.ERROR),
    DEFINITION_PROMPTS_TOO_LARGE(DiagnosticSeverity.ERROR),
    UNKNOWN_COMPONENT(DiagnosticSeverity.ERROR),
    INVALID_COMPONENT_CONFIG(DiagnosticSeverity.ERROR),
    COMPONENT_CATEGORY_MISMATCH(DiagnosticSeverity.ERROR),
    COMPONENT_COMPILATION_FAILED(DiagnosticSeverity.ERROR);

    private final DiagnosticSeverity severity;

    SemanticDiagnosticCode(DiagnosticSeverity severity) {
        this.severity = severity;
    }

    public DiagnosticSeverity severity() {
        return this.severity;
    }
}
