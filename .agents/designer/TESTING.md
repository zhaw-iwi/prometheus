# Designer Test Strategy

## Goal

Tests establish that one versioned JSON definition can be validated, persisted,
compiled once, executed through existing controller flows, authored safely, and
published from the designer without retaining a legacy path.

The suite grows in this order:

```text
pure unit contracts
    -> application/controller integration
    -> opt-in local MySQL smoke
    -> frontend component/build contracts
    -> Playwright visual and lifecycle tests
```

Each milestone adds the smallest tests that prove its acceptance contract. Do
not create broad low-value snapshot suites or repeat the same behavior at every
layer.

## Test invariants

- Automated tests make no real OpenAI/Azure, Speech, transcription, browser
  sensor, or external knowledge call.
- Time, randomness, IDs, and model outputs are injectable/deterministic where
  assertions depend on them.
- Tests never print or commit secrets from local property files.
- Default tests never create, drop, truncate, or clean the normal local
  database.
- Published revisions and compiled definitions are immutable.
- No test relies on execution order or data left by a previous test.
- Visual tests use stable test selectors and deterministic API/runtime data.

## Fixture organization

Create focused fixtures under test resources:

```text
src/test/resources/agent-definitions/
  valid/
    minimal-single-state.json
    composite-flow.json
    deterministic-components.json
  invalid/
    missing-state-reference.json
    containment-cycle.json
    unknown-component.json
    undeclared-modality.json
    invalid-storage-binding.json
```

Bundled main definitions are production resources and also contract fixtures.
Do not maintain separate test copies of the twelve documents.

Invalid fixtures have one primary defect so diagnostic assertions remain clear.
Tests assert stable diagnostic code and JSON Pointer, not full prose wording.

## Backend unit tests

### JSON Schema

Prove:

- the minimal definition passes;
- every required root section is enforced;
- unknown fields are rejected;
- schema/revision/component versions have the correct types/ranges;
- component envelopes cannot contain executable/class-name escape hatches;
- core observation and modality identifiers are accepted;
- malformed state variants and storage schemas fail at precise paths.

### Semantic validation

Use small programmatic documents or focused fixtures to prove:

- stable IDs are unique and references resolve;
- cycles/self-transitions are accepted;
- containment cycles and multiple parents are rejected;
- composite initial children resolve;
- final states cannot have policies/outgoing transitions;
- transition order is deterministic and unique per source;
- declared capabilities match component consumption/emission;
- storage bindings and initializer values match declared schemas;
- unknown component kinds/versions fail;
- unreachable/unused elements receive the agreed error or warning severity.

### Prompt composition

Prove exact section order, separator, line-ending normalization, empty-section
handling, component-specific prompt roles, and storage binding preservation.
Reuse important assertions from `PromptPolicyUnitTest` and the current prompt
contract tests rather than snapshotting every long prompt wholesale.

### Compilation and registry

Prove:

- each supported component schema compiles valid config and rejects invalid
  config;
- duplicate registry identifiers fail startup;
- state/transition references become immutable runtime references;
- compiled collections cannot be mutated;
- equivalent input produces equivalent compiled structure;
- no instance storage/history is held in a compiled definition;
- deterministic RPS and exact-text components preserve existing behavior.

### Runtime engine

Adapt the current state-machine tests to prove start, acknowledge, generate,
reset, final inactivity, first-match transition order, ANDed decisions, action
order, outer-before-inner evaluation, event reprocessing, starter generation,
oblivious history, and active state paths using compiled definitions rather than
JPA graph entities.

### Cache and performance contracts

Use a counting compiler/repository to prove:

- concurrent resolution compiles one effective object per revision;
- repeated operations do not reload or parse JSON;
- a new revision receives a distinct cache entry;
- publication installs only a successfully compiled entry;
- active revisions prewarm at startup;
- multiple instances share the compiled definition without sharing mutable
  state.

Avoid brittle wall-clock thresholds in ordinary unit tests. Record compilation
and warm-dispatch timings in an opt-in benchmark or smoke report if performance
measurement becomes necessary.

## Application and controller integration tests

Use in-memory/fake repository adapters where a real database is not the subject
of the test. Exercise the real application services, compiler, cache, runtime,
and controller serialization.

Required contracts include:

- bundled catalog import is deterministic and idempotent;
- creation by stable key resolves the active revision;
- an existing instance stays pinned after another revision is activated;
- reset uses the pinned revision;
- draft save uses optimistic concurrency;
- publication validates and compiles atomically;
- activation rejects draft/invalid/archived revisions;
- archive/delete rules protect referenced revisions;
- import/export canonical round trip;
- structured validation diagnostics reach the API unchanged;
- all designer endpoints reject missing/invalid admin tokens;
- existing scoped lifecycle and access-code flows continue to work;
- preview cannot enter production agent/access-code listings.

Follow nearby patterns in `AdminAccessCodeControllerWebMvcTest`,
`ScopedDemoControllerIntegrationTest`, `TalkToMeScopedIntegrationTest`, and
static-resource contract tests.

## Main-catalog parity tests

The migration gate covers exactly these keys:

- `core.facial_expression_sensitivity`
- `core.multimodal_behaviour`
- `core.rock_scissor_paper`
- `core.role_clarification_guessing_game`
- `core.social_context_sensitivity`
- `core.talk_to_me`
- `usecases.healthcare.guessing_game`
- `usecases.healthcare.guessing_game_user_guess`
- `usecases.healthcare.healthcare_conversation`
- `usecases.healthcare.smart_goal_coaching`
- `usecases.healthcare.therapy_appointment_reminder`
- `usecases.healthcare.therapy_appointment_reminder_intro`

One catalog contract loads production JSON and asserts unique keys, metadata,
language, profiles, valid compilation, and initial state. Focused tests cover
topology/prompt contracts for the representative complex definitions. Existing
Talk to Me and RPS deterministic tests remain exact.

Do not compare ORM row shape or Java class names. Those are intentionally
replaced.

## Opt-in local MySQL smoke tests

The repository currently obtains MySQL credentials from
`src/main/resources/application.properties`. Smoke tooling may reuse those
credentials to reach the server, but it must require an explicit dedicated
database/schema name and opt-in flag. It must refuse to run when the target is
the configured normal application database.

Recommended contract:

- JUnit tag: `local-db-smoke`;
- explicit enable flag such as `PROMETHEUS_DESIGNER_DB_SMOKE=true`;
- explicit dedicated database/schema override;
- no credential values in commands, logs, reports, screenshots, or commits;
- create/migrate only the dedicated target;
- deterministic cleanup limited to test-owned rows or that verified target;
- default `mvn test` excludes destructive schema setup.

The smoke sequence proves:

1. Flyway creates the intended clean schema and Hibernate validation passes.
2. All twelve bundled revisions import and the catalog is idempotent on a
   second startup/import.
3. A draft saves, publishes, activates, exports, and reloads exactly.
4. An instance pins a revision, starts, handles a deterministic event, persists
   state/storage/history, and reloads correctly in a new transaction/context.
5. Activating a new revision does not alter the existing instance.
6. Access-code allowed keys survive the definition migration contract.
7. Archived referenced revisions remain executable by pinned instances.
8. No obsolete definition-graph tables remain in a clean final schema.

Use deterministic Talk to Me/RPS paths so the smoke never needs a provider key.

## Frontend tests

Use TypeScript type checking, linting if introduced, and focused component/unit
tests for pure state transformations:

- JSON/form round trip;
- stepper index and direct navigation;
- validation diagnostic grouping and field/node targeting;
- reaction-to-state assignment;
- graph ordering/containment transformations;
- optimistic conflict state;
- example adoption versus mere viewing;
- dirty-state and unsaved-navigation behavior.

Backend schema/semantic validation remains authoritative. Do not duplicate the
entire validator in TypeScript.

Add a Spring static-resource contract test for `/valerian-design/`, its trailing
slash behavior, expected built assets, admin-token header, and absence of inline
credentials.

## Playwright visual and lifecycle tests

Follow `playwright.config.mjs` and the existing Valerian/API Workbench/Talk to Me
patterns. Split deterministic mocked visual coverage from the smaller live
Spring/MySQL lifecycle smoke where useful.

### Visual coverage

At minimum verify:

- catalog empty/loading/error/populated states;
- all six stepper panels and direct navigation;
- desktop chevrons and mobile stacked stepper;
- Purpose, Sensing, Behaviour, and Reactions forms;
- simple implicit-state view and multi-state graph workspace;
- validation summary linking to a field and graph element;
- prompt example adoption and composed-prompt preview;
- advanced JSON parse error and successful synchronized update;
- Review/Preview/Publish confirmation states;
- light desktop and dark mobile layouts;
- keyboard-visible focus for stepper and primary actions.

Use deterministic mocked API data for broad visual states so screenshots cannot
depend on local catalog history.

### Live lifecycle coverage

Against the running Spring application and dedicated local test database:

1. Authenticate with the configured admin-token override.
2. Create/import a uniquely keyed draft fixture.
3. Validate, publish, and activate it.
4. Start a disposable preview and exercise a deterministic reaction.
5. Confirm state/storage/behaviour rendering.
6. Export the canonical definition.
7. Archive/clean only the test-owned definition where lifecycle rules allow.

This test replaces external model/speech boundaries with deterministic fakes.
It must never modify the twelve bundled definitions.

## Structural legacy-removal checks

At the migration gate, verify through compilation, targeted source assertions,
and repository search that there is:

- no production `AgentDefinition` interface/implementation or whole-agent
  factory;
- no Spring discovery of agent definition beans;
- no JPA entity hierarchy for static states/transitions/policies/decisions/
  actions;
- no ad hoc single-state creation endpoint bypassing revisions;
- no Java prompt-holder used only by removed definitions;
- no documentation instructing developers to author agents in Java;
- no old definition-graph tables in the clean Flyway schema;
- no migration adapter or fallback feature flag left enabled or dormant.

Do not rely on a source-string assertion as the sole evidence; compile and
execute all twelve JSON definitions through the production path.

## Verification reporting

For every milestone report:

- exact tests and build commands run;
- whether local MySQL or a browser was actually used;
- skipped suites and why;
- external services replaced by fakes;
- remaining risks or unverified environments;
- clean `git status` after the milestone commit.

Never claim visual, database, packaging, or provider verification from unit
tests alone.
