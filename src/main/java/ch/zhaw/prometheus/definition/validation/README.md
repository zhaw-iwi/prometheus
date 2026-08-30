# Definition validation contracts

`AgentDefinitionSchemaValidator` rejects malformed schema-version-1 documents.
`DefinitionSemanticValidator` then returns ordered `ValidationDiagnostic`
records; it never accesses Spring, persistence, a model provider, or runtime
instance state. Errors block publication. Warnings identify suspicious but
executable designs.

`SemanticDiagnosticCode` is the machine-contract authority. Its stable codes
are grouped below; messages and remediation hints may improve independently.

- Identity: `DUPLICATE_STATE_ID`, `DUPLICATE_TRANSITION_ID`,
  `DUPLICATE_STORAGE_KEY`, `DUPLICATE_RESOURCE_ID`.
- Graph: `MISSING_INITIAL_STATE`, `INITIAL_STATE_NOT_ROOT`,
  `MISSING_CHILD_STATE`, `INVALID_INITIAL_CHILD`,
  `MULTIPLE_STATE_PARENTS`, `CONTAINMENT_CYCLE`,
  `MISSING_TRANSITION_SOURCE`, `MISSING_TRANSITION_TARGET`,
  `FINAL_STATE_OUTGOING_TRANSITION`, `DUPLICATE_TRANSITION_ORDER`, and the
  warning `UNREACHABLE_STATE`.
- Capabilities: `UNDECLARED_OBSERVATION`,
  `UNDECLARED_BEHAVIOUR_MODALITY`, plus `UNUSED_OBSERVATION` and
  `UNUSED_BEHAVIOUR_MODALITY` warnings.
- Storage/resources: `MISSING_STORAGE_BINDING`,
  `INCOMPATIBLE_STORAGE_SCHEMA`, `INVALID_STORAGE_INITIAL_VALUE`,
  `STORAGE_SCHEMA_KEYWORD_MISMATCH`,
  `STORAGE_SCHEMA_REQUIRED_PROPERTY_UNDECLARED`,
  `STORAGE_SCHEMA_INVALID_BOUNDS`, `MULTIPLE_STORAGE_INITIALIZERS`,
  `REQUIRED_STORAGE_UNINITIALIZED`, `MISSING_RESOURCE_REFERENCE`, and
  `MISSING_STATE_REFERENCE`.
- Prompts: `INVALID_PROMPT_STRUCTURE`, `DUPLICATE_PROMPT_SECTION_ID`,
  `BLANK_PROMPT_SECTION`, `PROMPT_SECTION_TOO_LARGE`, `PROMPT_TOO_LARGE`, and
  `DEFINITION_PROMPTS_TOO_LARGE`.

Every diagnostic supplies an RFC 6901 JSON Pointer to its definition location.
Component-specific requirements enter through `ComponentSemanticsResolver`;
the registered component catalog introduced by the compiler milestone is the
authoritative implementation of that boundary.
