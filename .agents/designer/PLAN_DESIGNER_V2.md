# PROMETHEUS Designer V2 Implementation Roadmap

## Status and objective

This is the active implementation roadmap for the second Designer iteration on
branch `features/designer`.

Designer V2 replaces the existing guided Designer directly. It is not a second
route, feature-flagged alternative, or compatibility mode. Implement V2 as the
Designer that should have existed from the beginning, and delete V1 panels,
view models, tests, styles, help, and documentation as soon as their V2
replacement is in place. Do not retain unused code merely to ease comparison.

The primary audience is a domain expert who understands an interaction
scenario but does not need to understand PROMETHEUS, JSON Schema, Java,
component envelopes, or state-machine terminology. Formative testing will use
students from domain-oriented courses, including healthcare-professional and
Wirtschaftsinformatik courses.

The objective is to let these authors design useful multimodal agents by
describing purpose, conduct, capabilities, ordinary interaction, exceptional
rules, optional phases, data, outcomes, and examples. The canonical schema
version 1 JSON definition, compiled runtime, revision lifecycle, and registered
component architecture remain authoritative and unchanged unless a milestone
explicitly identifies a small supporting API or metadata addition.

## Normative precedence

Read the existing Designer documents for the settled runtime, persistence,
JSON, lifecycle, security, and testing architecture. Where this document
conflicts with V1 user-interface wording, this document wins.

The normative Designer documents have been reconciled with V2. The old V1
frontend milestone descriptions and verification records remain only in
`PLAN_DESIGNER.md`, the explicitly historical portion of `TESTING.md`, and
clearly labeled delivery history in `PROJECT.md`/Designer README. They do not
define a maintained alternate UI.

It does not supersede the canonical JSON architecture, revision lifecycle,
runtime semantics, component trust boundary, admin-token model, database
safety rules, preview isolation, accessibility baseline, or the exclusion of
regulation and the `agents` branch.

Milestone V2.1 reconciled the normative documents. `PLAN_DESIGNER.md` remains
the historical record of completed declarative architecture, migration, and V1
delivery work; its UI sequence is explicitly superseded by this file.

## Settled product decisions

- The primary workflow is for domain experts. Technical authors use derived
  Advanced views; they do not define the primary information architecture.
- V2 replaces V1 at `/valerian-design/`. There is one Designer route and one
  maintained frontend implementation.
- JSON remains the only whole-agent definition language. V2 is a projection
  over one canonical `AgentDefinitionV1`, never a second persisted DSL.
- All twelve bundled definitions must open, be understandable, validate, and
  round-trip without semantic loss.
- Reproduce existing agent semantics before improving individual agents.
  Redesigning a bundled agent is a separate, explicit revision task.
- One implicit Main interaction is the default. Authors add another situation
  only when future events must be handled differently after a durable phase
  change.
- Sensing and behaviour are capabilities. They control flow only when an
  interaction rule binds an observation or operation to a continuation.
- A graph is an optional derived overview and technical editor, not a required
  step. A one-situation agent never needs to see it.
- Templates and recipes are expanded into inspectable, editable canonical
  content when adopted. Published definitions do not retain hidden live
  dependencies on authoring templates.
- Examples are inert until explicitly adopted.
- AI generation is out of scope for V2. A future assistant may propose an
  explainable diff, but it must not become the source of truth.
- Schema version 1 has no regulation engine. Guidance about safety, referral,
  uncertainty, or boundaries must not be presented as guaranteed enforcement.
- No Java classes, bean names, executable scripts, expression languages,
  credentials, or provider configuration enter a definition.
- Trusted deterministic behavior remains available only through registered,
  typed component kinds. RPS receives a guided capability card instead of raw
  component configuration. Exact text remains registered but Advanced-only in
  Designer; direct authored-text speech belongs to `/talktome/`.
- V2 introduces no generic timer, continuously evaluated `while` trigger,
  arbitrary tool runner, or parallel-state abstraction. Persistent context may
  be inspected as a condition when an event occurs. Add new runtime mechanisms
  only in a future roadmap justified by concrete use cases.

## Domain model shown to authors

### Brief and guidance

The Brief describes why the agent exists and how it should conduct itself.
Guidance is one concept with a scope, not a collection of unrelated prompt
editors.

Agent-wide guidance may include:

- identity and role;
- objective and desired outcome;
- audience and setting;
- interaction style and language;
- boundaries, referral, and escalation guidance;
- handling uncertainty and perception limitations;
- multimodal coordination;
- flexible process guidance;
- examples and counterexamples;
- what completion means.

Situation guidance uses the same typed, ordered prompt-section mechanism but
applies only while that situation is active. Imported unknown prompt section
IDs or kinds remain ordered and editable as Additional guidance. Do not discard
or silently reclassify them.

### Capabilities

Capabilities answer three questions:

1. What can the agent notice?
2. How can it express itself?
3. Which installed deterministic operations can it perform?

Observation and output cards use domain labels, examples, uncertainty notes,
and usage indicators. Registered operations use backend component metadata and
typed fields. Kind/version and raw configuration live in a technical
disclosure, not the card title.

Selecting a capability declares availability; it does not automatically create
a reaction, state, or modality mapping. The UI warns about unused capabilities
and links to the place where they can be used.

### Situations and ordinary behavior

Every definition has an initial active leaf in canonical JSON. V2 calls the
domain projection of that leaf the Main interaction. The technical state ID is
generated and normally hidden.

A situation contains:

- a domain name and short purpose;
- ordered situation guidance;
- an ordinary response strategy for inputs not intercepted by a rule;
- optional behavior when the situation begins;
- its scoped interaction rules.

New prompt-oriented definitions should use an outer composite for agent-wide
guidance and a `main` atomic child for situation guidance. Create a final child
lazily when the author first chooses Finish. Exact-text or other definitions
that do not need outer prompt guidance may remain a single atomic state.

Imported definitions are not restructured automatically. If an imported
single atomic state owns all prompt sections, show those sections as situation
guidance. Moving guidance to agent-wide scope is an explicit semantic edit.

### Interaction rules

The central authoring sentence is:

```text
In [Always | situation],
when [the situation begins | an event happens],
if [optional conditions],
then [respond | perform an installed operation | update/record data],
and [stay | move to another situation | finish].
```

Rules map to canonical ordered transitions and their decisions/actions.

- `Always` maps to an applicable outer-state transition.
- A situation-scoped rule maps to a transition from that situation.
- An event trigger normally generates a latest-event-type decision.
- A semantic criterion generates a prompt decision with a plain-language
  criterion plus optional positive and negative examples.
- Installed conditions/actions remain typed registered components.
- Stay is a self-transition when a transition is needed.
- Move selects an existing situation or creates a new atomic situation inline.
- Finish targets a final state created or reused by the projection.
- Source priority is represented by rule order and accessible move-earlier/
  move-later controls, never by requiring authors to type numeric priorities.
- Multiple decisions remain ANDed and actions remain ordered exactly as the
  runtime specifies.

Do not create a second reaction collection. The cards, storyboard, graph,
accessible list, JSON, and preview all read and edit the same transitions.

### Data and outcomes

Present definition-owned data by lifecycle role:

| Domain role | Canonical representation | Examples |
| --- | --- | --- |
| Starting context | storage plus initial value or registered initializer/resource | therapy appointment context |
| Working data | internal storage used by registered operations | RPS sign, round, result |
| Learned information | storage written during interaction | a selected preference or counter |
| Outcome report | outcome-visible storage plus extraction/update action | SMART goal or interaction result |

The guided editor must support the patterns demonstrated by the twelve bundled
definitions: optional/required values, primitive and object/list schemas,
enumerated choices, reset visibility, fixed initial values, typed choice
resources, deterministic choice initialization, operation-owned working data,
and extraction into outcome storage.

For new common outcome reports, provide a field builder and generate a strict
output schema plus ordered extraction guidance. Existing extraction prompts
whose report shape cannot be safely reverse-mapped must open losslessly as a
Custom outcome report with separate instruction, structure/example, and rules
sections. Converting a custom report into guided fields is explicit and shows
the resulting diff; never infer and rewrite it silently.

Capability packs such as RPS may own their internal storage declarations and
bindings. The domain author sees the installed operation and meaningful
settings, not four low-level storage schemas.

### Scenarios and explanation

Examples become executable verification scenarios when adopted into the
definition. The Try step edits the existing `verification.scenarios` contract:

- name and domain description;
- deterministic initializer seed when needed;
- optional initial storage;
- ordered input events;
- expected active state path, storage, accepted transition, and observable
  behavior properties supported by the schema/runtime.

The authoring form uses Given / When / Expect language. Advanced event JSON is
available but is not the primary path.

Running a scenario uses the production compiler and runtime in an isolated
preview boundary. The result explains:

- which event was submitted;
- which rules were considered and which transition was accepted at a safe
  diagnostic level;
- the active situation before and after;
- storage changes;
- emitted behavior modalities;
- why the expectation passed or failed.

Automated tests use deterministic component/model fakes. A scenario must not
call real OpenAI/Azure, Speech, transcription, browser sensors, or another
external service during automated verification.

## V2 information architecture

Retain the existing catalog, revision status, dirty-state, save, conflict,
publication, activation, import/export, clone, archive, and admin-token
contracts. Replace the editor stepper with these exact domain-facing steps:

| Step | Title | Caption | Main outcome |
| ---: | --- | --- | --- |
| 1 | Brief | Set purpose and conduct. | Identity, audience, goals, agent-wide guidance, boundaries |
| 2 | Capabilities | Choose what it can notice and do. | Inputs, output modalities, installed response/operation strategies |
| 3 | Interaction | Design ordinary behavior and exceptions. | Main interaction, optional situations, scoped rules, stay/move/finish |
| 4 | Data & outcome | Define context and results. | Starting context, working/learned data, outcome report |
| 5 | Try | Test concrete examples. | Verification scenarios, disposable execution, explanation trace |
| 6 | Review | Validate and publish. | Summary, diagnostics, technical views, lifecycle actions |

The stepper remains direct navigation rather than a completion meter. Preserve
the accessible button/tab semantics, dynamic z-order, validation focus,
desktop chevrons, and stacked mobile design from `STEPPER.md`, with the new
labels and panels.

### Interaction workspace

The default Interaction view is a storyboard/list of domain situations and
their rules. It must remain fully operable by keyboard and screen reader.

- With one situation, show one Main interaction card and no graph.
- `Continue differently` creates or selects another situation.
- `Finish` is an outcome, not a node the domain author has to create first.
- Global rules appear in a clearly labeled Always section.
- Situation cards show effective agent-wide plus local guidance without
  duplicating editable content.
- The optional Flow overview derives nodes and edges from the same document.
- Technical inspectors may expose composite containment, stable IDs, selector,
  entry mode, oblivious history, component kinds, and raw envelopes only under
  Advanced.

### Review and technical views

Review retains:

- a plain-language reverse explanation of the complete agent;
- authoritative diagnostics grouped by the six V2 steps;
- backend-composed, read-only prompt previews;
- the canonical JSON alternate editor with safe apply/recovery;
- the optional derived flow graph/list;
- publication and lifecycle actions with existing confirmation semantics.

Advanced JSON is not V1 compatibility. It is the expert/audit representation
of the same in-memory canonical document.

## Canonical projection requirements

Implement a typed frontend-only V2 projection or equivalent focused view model.
It must never be sent to or stored by the backend.

The projection must:

- retain the complete source `AgentDefinitionV1` and apply focused immutable
  transformations rather than rebuilding unrecognized content from a lossy
  form;
- map metadata and interaction capabilities without changing order or values
  on a no-edit round trip;
- distinguish outer agent-wide guidance from atomic situation guidance using
  the actual state path;
- preserve all prompt section IDs, kinds, order, and content;
- represent ordinary state policies separately from transition effects;
- map global, self, finish, and cross-situation transitions without creating a
  duplicate rule model;
- preserve decision/action order and unknown registered component envelopes;
- map storage/resources/initializers by domain lifecycle role without
  discarding schema details or examples;
- preserve verification scenarios;
- generate collision-free stable IDs and source priorities for new elements;
- keep diagnostic JSON Pointers resolvable to V2 step, card, and field targets;
- serialize deterministically through the existing canonical lifecycle path;
- leave an unchanged imported document semantically identical after open,
  navigation, and export;
- require explicit adoption/conversion for any transformation that cannot be
  reversed without changing semantics.

Do not put V2-only markers into canonical JSON. Projection state such as
collapsed cards, selected step, graph positions, or recipe provenance is local
UI state and must not affect the content hash.

## Twelve-agent coverage contract

The production definitions are the authoritative fixtures. Do not copy them
into a parallel test catalog.

| Agent | Required V2 representation |
| --- | --- |
| `core.talk_to_me` | One Main interaction whose registered Exact text behavior remains lossless and visible in Interaction/Advanced, but is not offered as a guided strategy; no graph or data required |
| `core.facial_expression_sensitivity` | Agent-wide sensing conduct, one task situation, facial-expression stay rule, global/task finish and outcome |
| `core.multimodal_behaviour` | One task situation whose ordinary strategy coordinates all selected output modalities; sensing remains context unless used by a rule |
| `core.social_context_sensitivity` | One task situation with social-context and situation-change stay rules |
| `core.role_clarification_guessing_game` | Role clarification, Valerian guesses, and User guesses situations with two semantic branches and finish/outcome rules |
| `core.rock_scissor_paper` | Prepare, Reveal, and Result situations; registered choose/evaluate/reveal/result operations; internal round data; cycle and finish rules |
| `usecases.healthcare.guessing_game` | One flexible game situation, social aside stay rule, semantic completion, structured outcome |
| `usecases.healthcare.guessing_game_user_guess` | Same topology with reversed role guidance and its outcome fields |
| `usecases.healthcare.healthcare_conversation` | One open-conversation situation, optional social aside, explicit stop, structured topic/trust outcome |
| `usecases.healthcare.smart_goal_coaching` | One flexible coaching situation, semantic completion, structured SMART-goal outcome |
| `usecases.healthcare.therapy_appointment_reminder` | One reminder situation, typed starting therapy context, social aside, completion, outcome |
| `usecases.healthcare.therapy_appointment_reminder_intro` | Introduction and Reminder situations, explicit start branch, typed starting context, social aside, completion, outcome |

Catalog pressure-test invariants:

- all twelve open without an unsupported-document error;
- a no-edit export is canonically equivalent to the loaded revision;
- the plain summary explains the actual active topology and component roles;
- the ten prompt-oriented definitions need no raw component editor for common
  edits;
- Talk to Me and RPS use installed guided capability cards;
- no imported long prompt, custom outcome, resource, schema, transition, or
  verification field disappears;
- representative edits validate and compile through the backend;
- current stable keys and observable runtime behavior remain unchanged.

## Support for formative student testing

The finished V2 must be suitable for moderated prototype sessions. Provide a
small, repository-owned usability protocol rather than product analytics or
surveillance.

The protocol should contain comparable tasks such as:

1. Create a one-situation conversational or sensing agent.
2. Add an observation that influences a response but does not change phase.
3. Add a second situation and a semantic rule that moves to it.
4. Define completion and an outcome report.
5. Run a Given / When / Expect scenario and explain the result.

Record manually, outside agent definitions:

- task completion and semantic correctness;
- whether the participant predicts stay/move/finish correctly;
- event-versus-context confusion;
- assistance requests and Advanced-view use;
- confidence in explaining the resulting agent;
- terminology problems and qualitative observations.

Do not add telemetry, participant data persistence, or research-consent flows
to the product under this roadmap.

## Engineering and verification rules

- Complete milestones in order and do not begin a later milestone while the
  current one has unfinished implementation, cleanup, tests, or documentation.
- Before each milestone, inspect the relevant implementation and neighboring
  tests and report a concise mental model plus the exact patterns that guide
  the change.
- Use the smallest high-value test set that proves the milestone, then run
  relevant regressions.
- Update affected Designer docs, README, and `PROJECT.md` current state in the
  same milestone as behavior changes.
- Review the entire diff and `git status`; remove dead V1 code and unintended
  artifacts.
- Commit and push every complete milestone before starting the next.
- Preserve unrelated user changes. Never reset or recreate
  `features/designer`.
- Never stage credentials, `application.properties`, provider secrets,
  database dumps, Playwright traces, screenshots not intentionally maintained,
  generated bundles, or unrelated changes.
- Automated tests never call real model, Speech, transcription, sensing, or
  other external services.
- Do not claim browser or visual verification without running Playwright and
  inspecting its output.
- Do not claim MySQL verification without running the guarded dedicated-schema
  smoke. Never clean or drop the normal configured database.
- If V2 does not change persistence schema or lifecycle behavior, do not invent
  a migration. Still rerun the existing final guarded MySQL lifecycle smoke at
  the acceptance gate.

Baseline commands, adjusted only when repository scripts intentionally change:

```powershell
npm run designer:typecheck
npm run designer:test
npm run designer:build
npm run designer:verify
npm run test:designer:visual
npm run test:designer:h2
./mvnw.cmd test
```

Guarded local MySQL commands require the exact opt-in contract documented in
`TESTING.md`. Do not echo credentials:

```powershell
$env:PROMETHEUS_DESIGNER_DB_SMOKE='true'
$env:PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA='prometheus_designer_smoke_local'
./mvnw.cmd -Plocal-db-smoke "-Dtest=LocalMysqlSmokeTest" test
npm run test:designer:live
```

## Milestone status

| Milestone | Status |
| --- | --- |
| V2.1. Normative specification, reference corpus, and component descriptors | Complete |
| V2.2. Lossless V2 projection and direct V1 shell removal | Complete |
| V2.3. Brief and Capabilities | Complete |
| V2.4. Unified Interaction workspace | Complete |
| V2.5. Data and outcome authoring | Complete |
| V2.6. Executable Try scenarios and explanations | Complete |
| V2.7. Review, Advanced views, and lifecycle completion | Complete |
| V2.8. Catalog parity, Playwright, student-test readiness, and final cleanup | Complete |

## Milestone V2.1 - Normative specification, reference corpus, and component descriptors

**Status: Complete.** The current Designer documents now define the six V2
domains and record the twelve reference designs/four topology families. The
registered component API exposes schema-annotated safe defaults/examples plus
authoring role, exposure, capability grouping, and explicit non-guided reasons;
catalog coverage prevents a production component from lacking a V2 disposition.

### Outcome

Make the V2 semantics unambiguous in documentation and expose only the
component metadata needed to render guided response, condition, operation,
data, and outcome cards.

### Implementation

- Update `DECISIONS.md`, `DESIGNER_UX.md`, `STEPPER.md`, `TESTING.md`,
  `PLAN_DESIGNER.md`, README, and `PROJECT.md` so the repository describes one
  V2 Designer and the old UI sequence is historical only.
- Document the twelve reference designs and four topology families from the
  coverage contract above without copying production JSON.
- Extend component UI metadata only as necessary to identify a safe guided
  authoring role and exposure level for response strategies, conditions,
  responses, deterministic operations, initializers/resources, and outcome
  extraction.
- Add titles/descriptions/defaults/examples to config schemas where the V2
  forms need them. Keep backend validation authoritative.
- Mark low-level helper components such as generated event-type decisions as
  generated/internal where appropriate; keep them visible in Advanced.
- Do not add runtime behavior or a generic dynamic-form framework not required
  by the twelve definitions.

### Tests

- Component-registry unit tests for deterministic metadata, unique guided
  roles, safe defaults/examples, and absence of implementation names.
- Web MVC/API mapping tests for the extended component descriptors and admin
  token rejection.
- Coverage contract proving all component kinds used by the twelve definitions
  have either a guided representation or an explicit Advanced-only reason.

### Verification

- Run focused component registry/controller tests and the existing Java suite.
- Inspect the component API response for RPS, exact text, prompt decision,
  prompt response, extraction, typed choices, and resource-choice initializer.
- Search current documentation for contradictory V1 step requirements.

### Completion gate

- Normative documents agree on V2 terminology and precedence.
- Component descriptors are sufficient for later guided panels without a
  client-side table of Java/runtime implementation knowledge.
- Commit and push before V2.2.

## Milestone V2.2 - Lossless V2 projection and direct V1 shell removal

**Status: Complete.** The only Designer shell now uses the exact six V2 steps
over a complete canonical-document projection. All twelve bundled revisions
round-trip without edits; V2 pointer targets and collision-free generators are
covered. The V1 form, panels, graph, tests, styles, and React Flow dependency
are removed. Later guided panels are explicit safe read-only projections until
their owning milestones.

### Outcome

Replace the V1 editor shell and form model with the six V2 steps and a
lossless, frontend-only projection over canonical JSON.

### Implementation

- Introduce typed V2 projection/query/transformation modules with focused
  immutable edits over the complete source document.
- Implement mapping for identity, scoped guidance, capabilities, situations,
  ordinary policies, global/situation rules, data roles, outcomes, and
  verification scenarios.
- Implement collision-free ID/order generation and V2 diagnostic targeting.
- Replace the stepper labels/panels with Brief, Capabilities, Interaction,
  Data & outcome, Try, and Review while preserving existing navigation,
  accessibility, save, dirty state, conflict, and backend validation behavior.
- Create a new-definition baseline with outer prompt context plus `main` atomic
  situation; keep exact-text/special strategies representable without forcing
  that topology.
- Delete the V1 `AuthoringForm`, V1 prompt-field catalog, Purpose/Sensing/
  Behaviour panels, Reactions panel, State flow panel, and their tests/styles
  when replaced. Move genuinely reused primitives into V2-named modules; do
  not leave forwarding wrappers or a V1 feature flag.
- It is acceptable for later V2 panels to show a clearly labeled incomplete
  state during this milestone, but the application must
  build and existing documents must remain safe from mutation.

### Tests

- Pure projection tests for all twelve production definitions.
- No-edit definition -> projection -> definition canonical equivalence.
- Preservation of unknown prompt sections, component envelopes, schemas,
  resources, initializers, transitions, and scenarios.
- Default-document topology and stable generated IDs.
- New exact six-step labels, direct/next/back navigation, ARIA state, bounds,
  mobile stacking, and diagnostic focus.
- Dirty-state test proving navigation alone does not mutate a document.
- Source/contract search proving removed V1 modules and labels are absent from
  production code and current tests.

### Verification

- Run frontend typecheck, focused Vitest suite, production build, and Spring
  static-resource contract tests.
- Run mocked Playwright for desktop and 390-pixel mobile shell; inspect
  screenshots and browser errors.
- Open representative prompt, exact-text, RPS, and healthcare definitions and
  export without editing.

### Completion gate

- `/valerian-design/` has one V2 shell and no dormant V1 implementation.
- All twelve definitions survive no-edit round-trip.
- Commit and push before V2.3.

## Milestone V2.3 - Brief and Capabilities

**Status: Complete.** Brief now edits canonical identity and agent-scoped
ordered guidance, preserves imported Additional guidance losslessly, requires
explicit stable-key/example adoption, and shows backend-composed prompts
read-only. Capabilities declares notice/expression availability without
creating flow, explains uncertainty and usage, renders backend-described
response strategies plus deterministic operation groups, and configures Exact
text without exposing raw envelopes. Focused mocked desktop/mobile and isolated
H2 create/reload gates cover the milestone without provider or normal-database
access.

### Outcome

Let a domain expert define purpose/conduct and choose sensing, expression, and
installed operations without component or state-machine jargon.

### Implementation

- Build Brief identity fields and scoped ordered guidance cards.
- Support known guidance intents and lossless Additional guidance for unknown
  imported section IDs/kinds.
- Preserve explicit example adoption and read-only composed-prompt preview.
- Combine observation and behaviour palettes into Capabilities with grouping,
  plain labels, uncertainty/help text, selected-use indicators, and warnings.
- Render registered response strategies and deterministic operations from
  backend authoring descriptors. Exact text remains Advanced-only and must not
  be selectable/configurable as a guided strategy.
- Hide kind/version/config behind Technical details.
- Keep stable key confirmation, optimistic save/conflict recovery, dirty state,
  and backend diagnostics.

### Tests

- Brief edits change only intended metadata/prompt sections.
- Agent-wide versus situation guidance remains distinct.
- Unknown imported prompt sections preserve ID/kind/order/content.
- Viewing an example is inert; adopting it is explicit and marks dirty.
- Capability selection does not create rules or situations.
- Usage warnings and links are correct.
- Strategy compatibility, exclusion of Advanced exact text, and registered RPS
  card metadata.
- Save/reload and optimistic conflict integration through existing APIs.

### Verification

- Run focused frontend tests, backend prompt/component/API tests, typecheck,
  build, and relevant Java regressions.
- Use isolated H2 to create/reload a prompt agent and open/run an existing
  exact-text definition without exposing it as a strategy choice.
- Run and inspect mocked Playwright desktop/mobile Brief and Capabilities
  states, including keyboard focus and long guidance.

### Completion gate

- A domain expert can create a useful one-situation definition through Brief
  and Capabilities without seeing raw JSON or runtime kind names.
- Commit and push before V2.4.

## Milestone V2.4 - Unified Interaction workspace

**Status: Complete.** Interaction now presents one storyboard over canonical
states and ordered transitions: implicit Main, durable situations, inherited
and local guidance, Always/situation rule cards, event and semantic conditions,
ordered registered effects, and stay/move/finish. Inline situation/final
creation, reference-protected deletion, exact diagnostic targets, keyboard
priority controls, and a compact derived flow preserve advanced imported
topologies without reintroducing a graph editor. Focused unit/browser contracts
cover the five reference topologies, and isolated H2 authoring validates stay,
branch, healthcare, and registered RPS cycles without JSON editing or provider
calls.

### Outcome

Replace separate Reactions and State flow authoring with one domain workspace
for ordinary behavior, scoped rules, and optional situations.

### Implementation

- Render the implicit Main interaction and its ordinary response strategy.
- Provide ordered situation guidance and optional entry behavior.
- Add the Always section and situation-scoped rule cards using the central
  authoring sentence.
- Support event/entry triggers, semantic and registered conditions, prompt or
  registered effects, stay/move/finish, and accessible priority reordering.
- Make the prompt-decision criterion an ordinary-language field with optional
  positive/negative examples; generate the component envelope internally.
- Create a situation inline from Continue differently and create/reuse a final
  state from Finish.
- Show effective inherited guidance read-only within each situation.
- Add a derived compact flow overview only after the list/storyboard works.
  Reuse React Flow only if it materially improves overview; every operation
  remains available in the accessible list.
- Preserve composite/advanced topologies on import even when Guided mode does
  not expose every technical containment operation.

### Tests

- Global, self, cross-situation, cycle, and finish transition projection.
- Event trigger generation and semantic prompt-decision content.
- ANDed decision/action order and first-match source priority.
- Stay, select existing target, create target, and lazy final creation.
- Situation deletion/reference protection and collision-free IDs.
- Agent-wide/situation policy composition and effective-guidance display.
- Keyboard/list parity and derived graph equivalence.
- Diagnostic pointer focus to the exact rule/situation/condition/effect.
- Catalog topology contracts for facial sensitivity, social sensitivity, role
  clarification, RPS, and therapy reminder with introduction.

### Verification

- Run frontend suites, backend validation/compiler/runtime contracts,
  typecheck, build, and static-resource tests.
- Against isolated H2, author and validate one orthogonal sensing stay rule,
  one role branch, one two-situation healthcare flow, and the RPS cycle without
  JSON editing.
- Run mocked Playwright for one-state, branching, cycle, final, error, desktop,
  mobile, and keyboard scenarios; inspect visual output.

### Completion gate

- There is one transition/rule editing path, not synchronized reaction and
  graph models.
- Common flows require no stable IDs, numeric priorities, or component JSON.
- Commit and push before V2.5.

## Milestone V2.5 - Data and outcome authoring

**Status: Complete.** Data & outcome now groups the canonical storage document
by lifecycle purpose, supports common and strict structured schemas plus fixed
or registered typed-choice initialization, keeps RPS internals operation-owned,
and authors strict outcome fields attached to finish rules. Imported extraction
contracts remain lossless Custom reports until an explicit previewed conversion
is applied. Reference protection and exact diagnostic routing preserve bindings;
frontend, backend, mocked-browser, and dedicated H2 gates cover the twelve-agent
catalog and the therapy/SMART persistence paths.

### Outcome

Let authors configure the starting context and results used by the healthcare
agents while keeping operation-owned working data understandable and safe.

### Implementation

- Group storage by Starting context, Working data, Learned information, and
  Outcome report.
- Provide guided schema fields for common primitive, enum, object, and list
  shapes demonstrated by the catalog, with an Advanced schema disclosure.
- Support fixed initial values and the typed-choice/resource-choice pattern
  used by therapy reminders.
- Let installed capability packs declare and explain operation-owned working
  data without requiring manual schema/binding edits.
- Add the guided outcome field builder, extraction rules, storage target, and
  attachment to finish rules.
- Support imported Custom outcome reports losslessly; make conversion to guided
  fields explicit and preview the canonical diff.
- Validate destructive edits and references before deleting data used by
  policies, decisions, actions, initializers, or resources.

### Tests

- Data-role classification and no-edit preservation for all twelve agents.
- Therapy typed choices, deterministic seed/initializer binding, required
  storage, and reset semantics.
- RPS internal storage generated/owned by its capability pack and preserved.
- Guided outcome fields generate deterministic schema and extraction sections.
- All existing healthcare/custom outcomes preserve their structure/rules and
  target bindings.
- Explicit conversion versus cancel/no-op behavior.
- Reference-protected rename/delete and backend diagnostic mapping.

### Verification

- Run frontend data tests plus backend schema, semantic, component,
  initializer, extraction, RPS, and catalog tests.
- Use isolated H2 to save/reload/validate therapy context and SMART outcome
  edits.
- Run mocked Playwright for empty/common/custom/capability-owned data and
  outcome states on desktop/mobile; inspect output.

### Completion gate

- The two therapy agents and all structured-outcome agents can be understood
  and edited without whole-definition JSON.
- RPS remains deterministic without exposing its internal implementation.
- Commit and push before V2.6.

## Milestone V2.6 - Executable Try scenarios and explanations

**Status: Complete.** Try now edits canonical Given / When / Expect scenarios,
runs unsaved documents through one bounded disposable production runtime, and
reports per-expectation evidence plus safe path, transition, data-change, and
modality traces. Seeded initializers, initial storage, exact text, RPS, stay,
branch, finish, failures, cleanup, token protection, and persistence isolation
are covered without provider calls or the configured database.

### Outcome

Make examples executable early and explain what the agent did, rather than
leaving all runtime testing to Review.

### Implementation

- Build Given / When / Expect scenario authoring over
  `verification.scenarios`.
- Add event templates based on selected observation capabilities and retain an
  Advanced JSON event editor.
- Add an isolated scenario execution operation using the production parser,
  validator, compiler, component registry, and runtime.
- Support deterministic initializer seed, initial storage, ordered events, and
  schema-supported expectations.
- Return per-expectation pass/fail plus active path, accepted transitions,
  storage changes, and emitted modality summary.
- Reuse preview resource limits, token protection, safe diagnostics, fakeable
  runtime dependencies, expiry/cleanup, and persistence isolation.
- Provide Why did this happen? and Why did this not happen? explanations only
  from available deterministic trace data; do not fabricate model reasoning.

### Tests

- Scenario form/document round-trip and unknown-field preservation.
- Deterministic exact-text, RPS round, stay, branch, finish, storage, and
  failing-expectation scenarios.
- Invalid scenario diagnostics and field focus.
- Unsaved draft execution, isolation, cleanup, resource bounds, and admin-token
  rejection.
- Fake prompt component behavior with no external call.
- Controller/application integration through the real compiler/runtime and
  disposable persistence boundary.

### Verification

- Run focused frontend, controller, application, runtime, and preview tests.
- Exercise deterministic scenarios against a running isolated-H2 application
  and confirm no production agent/revision/history row is created.
- Run Playwright through author, run, pass, fail, reset, and cleanup states;
  inspect trace presentation and browser errors.

### Completion gate

- A domain author can explain the observed stay/move/finish and data changes
  from the UI.
- Automated verification used only deterministic fakes.
- Commit and push before V2.7.

## Milestone V2.7 - Review, Advanced views, and lifecycle completion

**Status: Complete.** Review now reverse-explains the authored agent in the V2
domains, groups authoritative diagnostics across all six steps, and keeps
free-form preview and revision lifecycle actions alongside a collapsed,
lossless Advanced audit. That audit derives the complete flow diagram/list,
state containment/entry/history settings, registered envelopes and pointers,
raw schemas/lifecycle, backend-composed prompts, and safe canonical JSON from
the same document. Focused component, mocked-browser, and isolated-H2 coverage
protects recovery, conflicts, authorization, preview cleanup, and the complete
two-revision publish/activate/export/clone/archive journey.

### Outcome

Complete the V2 journey from reverse explanation and diagnostics through
technical audit, preview, publication, activation, import/export, clone, and
archive.

### Implementation

- Replace V1 review summaries/diagnostic grouping with the six V2 domains.
- Provide a complete plain-language reverse explanation of capabilities,
  guidance scopes, ordinary behavior, rules, situations, data, and outcomes.
- Retain canonical JSON safe apply/recovery and backend-composed prompts.
- Place the full flow graph/list, component envelopes, IDs, selectors,
  containment, entry/history settings, and raw schemas under clearly labeled
  Advanced views of the same document.
- Keep disposable free-form preview alongside scenario execution without
  duplicating transcript state.
- Preserve current lifecycle confirmation, immutability, activation for new
  instances, conflict, import/export, clone, and archive contracts.
- Remove every remaining V1 label, help paragraph, selector, CSS rule, test,
  and screenshot assumption. Remove dependencies used only by deleted V1 UI.

### Tests

- V2 diagnostic grouping and focus across all six steps.
- Reverse-summary correctness for the four representative topology families.
- JSON failure preserves projection; valid apply remaps it losslessly.
- Advanced graph/list/JSON equivalence with Guided edits.
- Prompt preview pointers remain valid after scoped guidance edits.
- Complete preview and lifecycle unit/component/API contracts.
- Repository search contract for obsolete V1 modules and exact old step
  sequence.

### Verification

- Run the complete frontend and relevant backend Designer suites.
- Against isolated H2, create, save, validate, try, free-preview, publish,
  activate, export, clone, and archive a unique V2 definition.
- Run mocked Playwright for diagnostics, technical views, preview, lifecycle,
  conflict, authorization, network error, light desktop, and dark mobile;
  inspect all captures.

### Completion gate

- V2 is functionally complete and contains no V1 implementation or parallel
  authoring path.
- Commit and push before V2.8.

## Milestone V2.8 - Catalog parity, Playwright, student-test readiness, and final cleanup

**Status: Complete.** All twelve production definitions now have executable
open/summary/no-edit/export/validation/compilation evidence, and the four
from-scratch journeys plus the guided RPS cycle cover the topology families.
The shipped concept guide explains the remaining cross-domain distinctions;
the repository owns a catalog matrix and moderated healthcare/Wirtschafts-
informatik student protocol without telemetry. Frontend, Java, mocked visual,
isolated-H2, shared PROMETHEUS, Participate, packaged-JAR/MySQL, and separate
migration/runtime MySQL gates passed with deterministic fakes and verified
dedicated-schema cleanup. The local multi-stage Docker build and non-root
runtime smoke subsequently passed against a disposable MySQL 8.4 container;
the checked-in CI build continues to enforce the same image gate.

### Outcome

Prove all twelve definitions, packaged deployment, responsive/accessibility
quality, safe lifecycle behavior, and readiness for formative student testing.

### Implementation

- Finish the twelve-agent coverage matrix and four representative from-scratch
  authoring journeys.
- Add the repository-owned moderated usability protocol and facilitator notes.
- Complete concise in-product help for situation versus context, ordinary
  behavior versus rule, stay/move/finish, guidance scope, data roles, and the
  limits of prompt-based safety guidance.
- Update README, API documentation, every Designer document, and `PROJECT.md`
  current status to the delivered V2 truth.
- Audit dead code, dependencies, generated artifacts, security, accessibility,
  and scope. Do not migrate the `agents` branch or add regulation.

### Tests

- All twelve open, summarize, no-edit round-trip, backend validate, and compile.
- Representative guided edits for all four topology families.
- Complete frontend typecheck/unit/build suite and Java regression suite.
- Existing PROMETHEUS and Participate Playwright regressions.
- V2 mocked visual suite covering catalog states, six panels, representative
  agents, errors, focus, light desktop, dark 390-pixel mobile, and no overflow.
- V2 live packaged-JAR lifecycle using deterministic exact-text behavior and
  the guarded dedicated MySQL schema.
- Existing `LocalMysqlSmokeTest` rerun against a separate verified dedicated
  schema, with confirmed cleanup.
- Packaged JAR/static-resource smoke and container/CI build when the local
  environment supports it; report an unavailable Docker daemon accurately.

### Verification

- Actually inspect Playwright screenshots/traces for clipping, overlap,
  ordering, focus, graph/storyboard readability, dialog behavior, contrast,
  accidental technical leakage, and secrets.
- Confirm browser console/page errors are empty.
- Confirm all automated provider/sensor boundaries used deterministic fakes.
- Confirm the MySQL target passed the safety guards and the normal configured
  database was untouched.
- Review the full branch diff since V2.1 and final `git status`.

### Completion gate

- Every final acceptance item below has evidence.
- Mark all V2 milestones complete, commit, push, confirm the remote commit, and
  leave a clean worktree.
- Report delivered behavior, exact verification run, skipped environments,
  and remaining risks, then stop for user review.

## Final acceptance checklist

- `/valerian-design/` exposes only Brief, Capabilities, Interaction, Data &
  outcome, Try, and Review.
- The primary workflow uses domain language and does not require JSON, stable
  IDs, component kinds/versions, numeric priorities, or a graph.
- One implicit Main interaction is enough for simple agents; situations are
  added contextually only for durable phase changes.
- Capabilities remain orthogonal until used by ordinary guidance or a rule.
- Global and situation guidance are distinct, inherited, and inspectable.
- There is one canonical rule/transition model with event/entry trigger,
  optional conditions, ordered effects, and stay/move/finish.
- Data is presented as starting, working, learned, or outcome data.
- Given / When / Expect scenarios are editable, executable, and explained.
- All twelve bundled definitions round-trip and compile with preserved stable
  keys and observable contracts.
- RPS is a guided registered capability, not a script or raw Java/runtime
  configuration. Exact text is registered but Advanced-only in Designer and
  remains available through the dedicated Talk to Me UI.
- The graph, component envelopes, and canonical JSON are derived Advanced
  views of the same document.
- No V1 panel, form model, test, style, feature flag, route, or current help
  remains.
- Canonical JSON, revision lifecycle, compiled cache/runtime, preview
  isolation, admin token, database safety, and no-regulation boundaries remain
  intact.
- Frontend, Java, integration, Playwright visual/live, packaged application,
  and guarded MySQL evidence is reported truthfully.
- The repository contains a practical moderated protocol for healthcare and
  Wirtschaftsinformatik student testing without product telemetry.
