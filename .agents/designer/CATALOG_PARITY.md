# Designer V2 Catalog Parity

## Status

Complete for the twelve schema-version-1 definitions in the production `main`
catalog. This matrix records coverage; the production JSON files remain the
only definition fixtures and are not duplicated here.

Every row passes the same five contracts:

1. the frontend projection opens the complete canonical document;
2. Review produces all five authored-domain explanations;
3. no-edit guided projection and export preserve the document exactly;
4. the authenticated backend validation and publication-readiness boundaries
   accept and compile the document; and
5. the Java catalog starts the compiled definition with a deterministic fake
   instead of an external provider.

## Twelve-definition matrix

| Definition | Topology family | Guided content that must remain visible |
| --- | --- | --- |
| `core.facial_expression_sensitivity` | Orthogonal rules | Facial observation, task stay rule, finish path, outcome |
| `core.multimodal_behaviour` | Single ordinary interaction | Coordinated prompt response and expression modalities |
| `core.rock_scissor_paper` | Deterministic operation cycle | Prepare/reveal/result situations, cycle, finish, owned working data |
| `core.role_clarification_guessing_game` | Branching phased interaction | Clarification branch, two durable role situations, outcome |
| `core.social_context_sensitivity` | Orthogonal rules | Social/situation observations and independent stay rules |
| `core.talk_to_me` | Single ordinary interaction | One Main interaction and guided Exact text behavior |
| `usecases.healthcare.guessing_game` | Orthogonal rules | Flexible game guidance, social aside, completion, outcome |
| `usecases.healthcare.guessing_game_user_guess` | Orthogonal rules | Reversed game roles, social aside, completion, outcome |
| `usecases.healthcare.healthcare_conversation` | Orthogonal rules | Open conversation, social aside, explicit stop, outcome |
| `usecases.healthcare.smart_goal_coaching` | Orthogonal rules | Coaching guidance, semantic completion, custom SMART outcome |
| `usecases.healthcare.therapy_appointment_reminder` | Orthogonal rules | Typed starting context, reminder, social aside, outcome |
| `usecases.healthcare.therapy_appointment_reminder_intro` | Branching phased interaction | Introduction/reminder situations, typed context, finish/outcome |

## Guided authoring journeys

The isolated-H2 Playwright suite owns four from-scratch journeys:

| Journey | Evidence |
| --- | --- |
| Simple ordinary interaction | Creates and reloads new prompt and Exact text agents from Brief and Capabilities |
| Orthogonal observations | Creates two independent stay rules without a situation change |
| Meaning-based branch | Creates two destination situations and two conditioned branches |
| Healthcare phase change | Creates preparation and hand-off situations, local guidance, a move rule, and a finish rule |

The same suite covers a guided edit for every topology family. The deterministic
operation family deliberately starts by cloning bundled RPS: registered RPS
content is edited through its guided cards and remains inspectable, while the
Designer does not invent a generic script or arbitrary operation builder.

## Executable evidence

- `designer/src/v2/projection.test.ts`: all-twelve projection and no-edit
  canonical equality.
- `designer/src/review/reviewModel.test.ts`: all-twelve plain-language reverse
  explanation.
- `tests/playwright/valerian-designer-visual.spec.mjs`: all-twelve open,
  summarize, exact no-edit JSON, and exported JSON equality.
- `tests/playwright/valerian-designer-h2.spec.mjs`: all-twelve live Review,
  authenticated validation, and publication-readiness compilation, plus the
  four from-scratch journeys.
- `BundledDefinitionCatalogUnitTest`: schema, semantic, compiled-runtime, safe
  component coverage, and exact deterministic behavior.
