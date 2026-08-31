# Designer Test Strategy

## Goal

Tests establish that one versioned JSON definition can be validated, persisted,
compiled once, executed through existing controller flows, authored safely, and
published from the designer without retaining a legacy path. Designer V2 adds a
lossless domain projection over all twelve production definitions; the former
V1 panel sequence is historical evidence, not a current test contract.

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
- component descriptors provide a deterministic V2 authoring role, exposure,
  safe capability grouping/defaults/examples, and an explicit Advanced reason
  for every non-guided component used by the bundled catalog.

Follow nearby patterns in `AdminAccessCodeControllerWebMvcTest`,
`ScopedDemoControllerIntegrationTest`, `TalkToMeScopedIntegrationTest`, and
static-resource contract tests.

Milestone 10 implements these API contracts in
`DesignerDefinitionControllerWebMvcTest`, including token rejection across the
entire endpoint surface, canonical reads/export, every lifecycle mutation,
server-owned import provenance, structured schema/semantic diagnostics,
not-found/conflict/immutability rules, and a safe deterministic component
catalog. `DefinitionLifecycleServiceUnitTest` exercises clone, validate,
publish/cache installation, sorted catalog access, and canonical export/import
through the real parser/compiler/lifecycle with in-memory persistence;
`JpaPersistenceAdapterTest` covers the database-backed ordered catalog query.

Milestone 11 adds `DesignerPreviewServiceUnitTest` for production-engine state,
storage, event/behaviour trace, session isolation, deterministic fake-model
failure rollback, safe diagnostics, TTL/close, and resource bounds.
`DesignerPreviewControllerWebMvcTest` covers the complete token-protected HTTP
surface and stable error mapping, including rejection of a saved non-draft.
`DesignerPreviewPersistenceIsolationIntegrationTest` runs a saved draft through
the full Spring/Flyway/H2 repository stack and proves that definition/revision
counts and production agents remain unchanged without a model-provider call.

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

Implemented contract:

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

Implemented command (PowerShell):

```powershell
$env:PROMETHEUS_DESIGNER_DB_SMOKE='true'
$env:PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA='prometheus_designer_smoke_local'
.\mvnw.cmd -Plocal-db-smoke "-Dtest=LocalMysqlSmokeTest" test
```

The ordinary Surefire configuration excludes the `local-db-smoke` tag. The
profile includes it, but the test still refuses to run without the exact enable
flag and a `prometheus_designer_smoke_*` schema different from the normal
configured database. It reads credentials without logging them, rebuilds only
the verified target, and verifies target removal after success.

## Frontend tests

Use TypeScript type checking, linting if introduced, and focused component/unit
tests for pure V2 projection transformations:

- all-twelve no-edit canonical round trip and preservation of unknown content;
- scoped agent-wide/situation guidance and ordinary-policy mapping;
- capability selection remaining orthogonal to rules and situations;
- global/situation rule mapping, order, stay/move/finish, and collision-free IDs;
- data-role/outcome classification without schema/resource loss;
- Given / When / Expect scenario mapping and deterministic result explanation;
- six-step navigation, validation targeting, optimistic conflicts, and dirty
  state;
- example adoption versus mere viewing;
- Advanced JSON/flow/list equivalence with Guided edits.

Backend schema/semantic validation remains authoritative. Do not duplicate the
entire validator in TypeScript.

Add a Spring static-resource contract test for `/valerian-design/`, its trailing
slash behavior, expected built assets, admin-token header, and absence of inline
credentials.

Milestone V2.1 extends `ComponentRegistryUnitTest`,
`DesignerDefinitionControllerWebMvcTest`, and
`BundledDefinitionCatalogUnitTest`. Together they prove deterministic schema
annotations and authoring metadata, authenticated API projection, no
implementation-name leakage, and guided-or-explicit-Advanced coverage for
every component actually used by the twelve bundled definitions.

Milestone V2.2 uses the production manifest itself for twelve-definition
projection/round-trip tests. Focused Vitest coverage also protects unknown
prompt sections and envelopes, schema/resource/initializer/scenario retention,
the outer-context-plus-Main baseline, immutable transforms, stable IDs/orders,
the exact six-step ARIA/navigation contract, V2 pointer targeting, and
navigation-without-dirty-state. The mocked Playwright gate opens and exports
representative prompt, exact-text, RPS, and healthcare revisions unchanged and
inspects light desktop plus dark 390-pixel layouts without provider calls.
`ValerianDesignerStaticResourceContractTest` asserts the V2 labels in the
generated bundle; the milestone source search separately proves that V1 module
names and step IDs no longer occur in production or current frontend tests.

Milestone V2.3 expands the focused frontend suite to 41 tests. Pure contracts
cover stable-key confirmation, metadata validity, agent-versus-situation scope,
unknown section identity/order retention, explicit example adoption,
capability declaration without flow mutation, configured-use detection,
response compatibility, Exact text adoption, and RPS grouping/owned data.
Existing editor integration retains save/reload and optimistic-conflict
recovery. Eight mocked Playwright scenarios exercise long Brief guidance,
example inertness/adoption, capability usage warnings and Interaction links,
Exact text settings, grouped RPS metadata, keyboard-visible focus, light
desktop, and dark 390-pixel mobile without provider calls. The separate
`playwright.designer-h2.config.mjs` gate starts Spring with only an explicit
in-memory H2 datasource and creates/reloads one prompt draft and one exact-text
draft through the guided UI. It neither reads the normal datasource credentials
nor calls a model, Speech, transcription, or sensing provider.

Milestone V2.4 expands the focused frontend suite to 49 tests. Pure interaction
contracts cover global/self/cross/cycle/final projection, source-local priority,
ANDed prompt conditions and examples, ordered effects, collision-free
situation/final creation, deletion protection, and scoped entry/response
guidance. Component tests exercise the single rule-card path, keyboard reorder,
derived flow, and exact condition/effect diagnostic targets. Ten mocked
Playwright scenarios now include unified authoring, branch/cycle/final no-edit
projection, validation-error focus, desktop, dark 390-pixel mobile, and visible
keyboard focus. The H2 gate uses the explicit
`jdbc:h2:mem:prometheus_designer_v24` datasource to create/save/compile-validate
orthogonal stay rules, a semantic role branch, a two-situation healthcare
flow, and a cloned registered RPS cycle without canonical JSON editing. It does
not read the normal datasource, call providers, or claim MySQL verification.

Milestone V2.5 expands the focused frontend suite to 60 tests. Data model and
component coverage classifies and preserves all twelve documents, edits therapy
typed choices and synchronized strict object schemas, keeps RPS storage owned,
generates deterministic outcome schema/extraction sections, preserves Custom
healthcare extraction, previews explicit conversion without mutation, protects
references, and maps related backend pointers to exact cards. Twelve mocked
Playwright scenarios cover empty, common therapy, custom SMART, operation-owned
RPS, light desktop, and dark 390-pixel layouts; the inspected captures have no
horizontal overflow or browser errors. The H2 gate now uses only
`jdbc:h2:mem:prometheus_designer_v25` and its seven scenarios additionally
save, reload, and compile-validate therapy context and SMART outcome edits. The
milestone backend slice passes 100 schema, semantic, component, compiler,
runtime, catalog, JSON, controller, and static-resource tests without providers
or the normal configured database.

### Historical Designer V1 delivery evidence

The Milestones 12-16 notes below record the removed/replaced frontend's original
acceptance evidence. They do not prescribe current V2 labels, panels, or
information architecture.

Milestone 12 establishes this layer with `npm run designer:verify` (TypeScript,
Vitest, and the Vite production build) and
`ValerianDesignerStaticResourceContractTest` plus the redirect contract. Maven
uses its pinned frontend toolchain and runs the same verification during
`generate-resources`; generated assets live only in `target/generated-resources`.
The `designer:test` script canonicalizes its working directory through
`designer/scripts/run-vitest.mjs` before launching Vitest. This is required on
Windows when Maven or an IDE preserves a lower-case drive letter from `-f`;
Vitest 4.1.x otherwise resolves its runner and test modules into different
contexts.
The initial 12 frontend tests cover token/catalog API mapping, hash routes,
loading/error/empty/populated catalog states, the six exact step labels,
direct/next/back navigation, ARIA state, bounds, and validation-target focus.
Manual local inspection covered the populated desktop catalog, desktop chevron
stepper, and 390-pixel stacked mobile stepper with no horizontal overflow or
browser console errors.

Milestone 13 expands the frontend suite to 24 tests. Pure mapping contracts
cover JSON/form round trips without losing untouched graph/resource content,
the explicit `main` state, stable prompt-section order, capability propagation,
and strategy compatibility. Component tests distinguish viewing from adopting
examples and exercise the first three panels. Editor/API tests cover create and
update request mapping, backend diagnostics, dirty/before-unload/internal
navigation warnings, save success, and explicit optimistic-conflict recovery.
A real local Spring application on disposable H2 was then driven through the UI
to create `designer.m13_browser_draft`; a full page reload returned the locked
key, purpose objective, user-utterance capability, and speech modality with a
clean saved state and no browser console errors. The normal configured database
and external providers were not used.

Milestone 14 expands the frontend suite to 36 tests. Pure transformations cover
reaction order/reference preservation, explicit capability synchronization,
default-to-multi-state conversion, state/transition add/edit/delete/reorder,
composite containment and initial-child order, cycles, and self-transitions.
Component contracts cover guided reaction mapping, graph/list equivalence,
keyboard operations, schema-derived component fields, and exact diagnostic
navigation. A real Spring application using an explicitly overridden
in-memory H2 datasource and H2 Hibernate dialect was then driven through the
UI, with no JSON editing or external-service calls, to create and reload
`designer.m14_healthcare_verified` and `designer.m14_rps_cycle`. Backend
validation returned zero diagnostics for the four-state/three-transition
healthcare composite and two-state/four-transition RPS cycle. Inspected
Playwright graph and keyboard-list screenshots showed containment, initial
state/child, priorities, the RPS cycle/self-transition, no horizontal overflow,
and no browser console/page errors. The normal configured database was never
opened.

Milestone 15 expands the frontend suite to 45 tests. Review contracts cover
six-step diagnostic grouping/focus, local JSON parse recovery plus backend-
validated apply, backend-composed prompt display, preview transcript and
cleanup, exact-document publication readiness, lifecycle confirmations,
activation semantics, import conflict preservation, export, clone, and archive.
Controller/service tests separately cover the authenticated prompt-composition
and full compile-readiness endpoints. A live Spring application on isolated H2
completed the create/save/validate/preview/publish/activate/export/clone/archive
journey with deterministic exact-text behaviour and no provider call.

## Playwright visual and lifecycle tests

Follow `playwright.config.mjs` and the existing Valerian/API Workbench/Talk to Me
patterns. Split deterministic mocked visual coverage from the smaller live
Spring/MySQL lifecycle smoke where useful.

### Visual coverage

At minimum verify:

- catalog empty/loading/error/populated states;
- all six V2 stepper panels and direct navigation;
- desktop chevrons and mobile stacked stepper;
- Brief scoped guidance and Capabilities cards, including Exact text/RPS;
- one Main interaction, optional situations, global/scoped rules, and
  stay/move/finish without a required graph;
- Starting context, Working data, Learned information, and Outcome report;
- Given / When / Expect author/run/pass/fail explanations;
- validation summary linking to an exact V2 field/card/rule;
- example adoption, composed-prompt preview, and Advanced JSON/flow/list;
- Review/free-preview/publication confirmation states;
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

Milestone 16 implements these browser gates in
`playwright.designer-visual.config.mjs` and
`playwright.designer-live.config.mjs`. The five-test mocked suite records stable
PNG evidence for locked/loading/error/empty/populated catalogs, all six panels,
prompt adoption, composite graph and keyboard list, linked diagnostics, JSON
failure/success, composed prompts, preview/behaviour, publication, desktop
light mode, and 390-pixel dark mode. Assertions cover overflow, stacked stepper,
focus outline, dialogs, downloads, and browser errors. Inspected captures had no
clipping, overlap, low-contrast controls, or accidental secret values.

The one-test live suite requires `PROMETHEUS_DESIGNER_DB_SMOKE=true` and an
explicit `prometheus_designer_smoke_*` schema. It builds the production JAR,
creates only that verified schema, starts the JAR with a test admin-token
override, and drives a unique exact-text draft through import, full readiness,
preview event/generate, publish, activate, export equality, close, and verified
schema removal. The broader `LocalMysqlSmokeTest` was also rerun on MySQL 9.4,
including migration/runtime restart and cleanup. The 22 PROMETHEUS Playwright
regressions passed against isolated H2, and the six Participate regressions
passed with their separate test server/database configuration. No OpenAI,
Azure, Speech, transcription provider, or physical sensor service was called.
The packaged JAR served `/valerian-design/`; a local container build was
attempted but could not start because no Docker daemon was available, while the
checked-in CI gate performs that build.

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

Milestone 8 enforces this gate with `LegacyRuntimeRemovalContractTest`, the
exact-key and representative execution contracts in
`BundledDefinitionCatalogUnitTest`, controller/scoped/access integration tests,
Flyway final-schema assertions, JPA reload coverage, and the complete Java
suite. The production and current-documentation searches contain no whole-agent
Java authoring path or dormant fallback. Real MySQL remains a separate
Milestone 9 requirement.

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
