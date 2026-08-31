package ch.zhaw.prometheus.definition.component;

/** Domain-facing placement of a registered component in Designer V2. */
public enum ComponentAuthoringRole {
    RESPONSE_STRATEGY,
    RULE_TRIGGER,
    RULE_CONDITION,
    RULE_RESPONSE,
    DETERMINISTIC_OPERATION,
    DATA_UPDATE,
    DATA_INITIALIZER,
    DATA_RESOURCE,
    OUTCOME_EXTRACTION,
    TECHNICAL_SELECTOR
}
