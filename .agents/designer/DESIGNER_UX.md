# Valerian Designer V2 UX Specification

## Product intent

`/valerian-design/` helps domain experts turn an interaction scenario into a
sound PROMETHEUS agent without first learning JSON Schema, Java, component
envelopes, or state-machine terminology. The primary formative-testing audience
includes healthcare-professional and Wirtschaftsinformatik students.

The Designer is a lossless projection over one canonical schema-version-1 JSON
document. Guided forms, storyboard, optional flow overview, accessible list,
canonical JSON, prompt previews, and runtime preview are views of that same
document. There is no persisted V2 DSL or parallel V1 editor.

Delivery status: the six-step V2 experience, complete Advanced audit, all-
twelve parity contract, and concise in-product concept guide are implemented.
`CATALOG_PARITY.md` records executable corpus coverage and
`USABILITY_PROTOCOL.md` defines formative evaluation without telemetry.

The central authoring sentence is:

```text
In [Always | situation],
when [the situation begins | an event happens],
if [optional conditions],
then [respond | perform an installed operation | update or record data],
and [stay | move to another situation | finish].
```

## Information architecture

### Catalog

The catalog provides search and category/status filters; stable key, display
name, language, provenance, active revision, and draft status; and lifecycle
actions permitted for the selected revision. Active publication and editable
draft status must never be conflated. Import creates a new draft and never
overwrites a conflicting key/revision.

Lifecycle-changing actions require confirmation naming the exact key and
revision. A referenced published revision may be archived when non-active but
is not physically deleted.

### Editor

The editor contains a compact identity/revision/status header, dirty indicator,
explicit Save draft action, the six-step navigation, one active panel, and a
keyboard-reachable validation summary. Step navigation neither saves nor
publishes. Browser navigation with unsaved changes warns the author.

The exact steps are:

| Step | Title | Caption | Outcome |
| ---: | --- | --- | --- |
| 1 | Brief | Set purpose and conduct. | Identity, audience, goals, agent-wide guidance, boundaries |
| 2 | Capabilities | Choose what it can notice and do. | Inputs, output modalities, response strategies, installed operations |
| 3 | Interaction | Design ordinary behavior and exceptions. | Main interaction, optional situations, scoped rules, stay/move/finish |
| 4 | Data & outcome | Define context and results. | Starting context, working/learned data, outcome reports |
| 5 | Try | Test concrete examples. | Given/When/Expect scenarios and safe execution explanation |
| 6 | Review | Validate and publish. | Reverse summary, diagnostics, Advanced views, lifecycle actions |

The stepper is direct navigation rather than a completion meter and follows
`STEPPER.md` for desktop chevrons, mobile stacking, focus, validation targeting,
and ARIA semantics.

## Step 1: Brief

Brief asks what the agent is called, why it exists, whom it serves, where it is
used, how it should conduct itself, and which boundaries matter. A generated
key suggestion requires explicit confirmation before first save; a published
key is stable. Schema version, hashes, provenance, and repository identifiers
belong in Technical details.

Guidance is one ordered concept with a scope. Agent-wide guidance may cover
identity/role, objective/outcome, audience/setting, language/style, boundaries
and referral, uncertainty/perception limitations, multimodal coordination,
flexible process guidance, examples/counterexamples, and completion. Situation
guidance uses the same prompt-section shape while its situation is active.

Known guidance intents receive domain labels. Imported unknown section IDs or
kinds remain ordered, editable, and lossless as Additional guidance. Merely
opening or viewing an example changes nothing; **Use as starting point** copies
it into the document and marks the draft dirty. Safety/referral guidance must
not be described as guaranteed runtime enforcement.

## Step 2: Capabilities

Capabilities answer:

1. What can the agent notice?
2. How can it express itself?
3. Which installed deterministic operations can it perform?

Observation and expression cards use plain labels, examples, uncertainty/help,
and usage indicators. Concrete schema identifiers remain available in a
Technical details disclosure. Selecting a capability declares availability; it
does not create a rule, situation, transition, or modality mapping.

Response strategies and deterministic operations are rendered from backend
component authoring descriptors. Prompt response is the guided Main strategy;
Exact text is Advanced-only and cannot be adopted or configured as a Designer
strategy. Existing exact-text definitions remain visible and lossless, while
direct authored-text speech belongs to `/talktome/`. The RPS components form
one guided installed-operation card with meaningful settings and owned working
data. Raw kind/version/config values are not card titles.
Unused capabilities produce a warning and link to the place they can be used;
they are never silently removed.

## Step 3: Interaction

Every canonical definition has an initial active leaf. V2 calls its domain
projection **Main interaction** and normally hides the technical state ID. One
situation shows one Main interaction card and no graph.

A situation has a name/purpose, ordered local guidance, an ordinary response
strategy, optional behavior on entry, and ordered scoped rules. Effective
agent-wide plus local guidance may be shown read-only without duplicating
editable content.

The default workspace is an accessible storyboard/list:

- **Always** holds applicable outer-scope rules.
- Situation cards hold scoped rules.
- Event and situation-entry triggers are available; schema version 1 has no
  continuously evaluated `while` trigger or generic timer.
- Semantic conditions use a plain-language criterion with optional positive
  and negative examples.
- Conditions remain ANDed and effects remain ordered.
- Move-earlier/move-later controls express first-match priority; authors do not
  type numeric order values.
- **Stay** reuses the situation, **Continue differently** selects or creates an
  atomic situation, and **Finish** lazily creates or reuses a final state.

Ordinary response policy remains distinct from exceptional transition effects.
Global, self, cross-situation, cycle, and finish rules all edit the canonical
transition array directly. There is no separate reactions collection or graph
model to synchronize.

An optional derived Flow overview appears only when useful. Every operation
remains available through the keyboard/screen-reader list. Composite
containment, stable IDs, selectors, entry/history settings, component envelopes,
and unusual imported topology are preserved and exposed under Advanced rather
than silently normalized.

## Step 4: Data & outcome

Definition-owned values are grouped by lifecycle role:

| Role | Meaning |
| --- | --- |
| Starting context | Fixed or initialized information available when an instance begins |
| Working data | Internal values used by installed operations |
| Learned information | Values recorded during interaction |
| Outcome report | Caller-visible structured results |

Guided data fields cover catalog-proven primitive, enumerated, object, and list
shapes; required/optional behavior; reset behavior; fixed starting values; and
typed resource-choice initialization. Full schemas remain inspectable under
Advanced. RPS owns and explains its internal round data rather than asking the
author to wire four schemas manually.

The outcome builder creates a strict object schema, ordered extraction guidance,
and finish-rule attachment. Existing extraction prompts that cannot be safely
reverse-mapped open losslessly as a Custom outcome report. Conversion to guided
fields is explicit, previews the canonical diff, and can be cancelled without a
mutation. Rename/delete is blocked while policies, conditions, effects,
initializers, or resources still reference the value.

## Step 5: Try

Try edits `verification.scenarios` in Given / When / Expect language. A scenario
contains a name/description, optional deterministic seed and initial storage,
ordered input events, and schema-supported expected state/data/behavior
properties. Event templates derive from selected observation capabilities;
Advanced event JSON remains available.

Execution uses the production parser, validator, compiler, component registry,
and runtime through the disposable preview boundary. The result explains only
available trace facts: submitted event, considered/accepted rules, situation
before/after, storage changes, emitted modalities, and expectation pass/fail.
It never fabricates model reasoning.

Scenario execution shares preview bounds, expiry, cleanup, admin-token
protection, persistence isolation, and fakeable runtime dependencies. Automated
tests never call real model, Speech, transcription, sensing, or knowledge
services.

## Step 6: Review

Review coordinates:

1. A plain-language reverse explanation of Brief, capabilities, situations,
   ordinary behavior, rules, data, outcomes, and scenarios.
2. Authoritative diagnostics grouped by the six V2 steps, errors before
   warnings, with direct field/card/rule targeting.
3. Read-only backend-composed prompt previews.
4. Advanced canonical JSON and complete derived flow graph/list.
5. Disposable free-form preview and explicit lifecycle actions.

Save draft remains available for incomplete semantic content. Publish is
enabled only after backend publication-readiness validation and successful
compilation of the exact saved document. Activation is separate and states
that it affects newly created instances only. Export, clone, and non-active
archive retain the existing lifecycle confirmation semantics.

## Reference corpus

The twelve bundled production definitions are the fixtures; this table records
their required domain projection without duplicating their JSON.

| Definition | V2 reference design |
| --- | --- |
| `core.talk_to_me` | One Main interaction with existing Exact text visible in Interaction and Advanced; no graph or data needed |
| `core.facial_expression_sensitivity` | Agent-wide sensing guidance, one task situation, facial stay rule, global/task finish and outcome |
| `core.multimodal_behaviour` | One task situation with coordinated ordinary multimodal response; sensing may remain context |
| `core.social_context_sensitivity` | One task situation with social-context and situation-change stay rules |
| `core.role_clarification_guessing_game` | Clarification, Valerian-guesses, and User-guesses situations with semantic branches and finish/outcome |
| `core.rock_scissor_paper` | Prepare, Reveal, and Result situations; installed choose/evaluate/reveal/result operation; cycle and finish |
| `usecases.healthcare.guessing_game` | One flexible game situation, social-aside stay rule, semantic completion, structured outcome |
| `usecases.healthcare.guessing_game_user_guess` | Same topology with reversed role guidance and its outcome |
| `usecases.healthcare.healthcare_conversation` | One open conversation, optional social aside, explicit stop, topic/trust outcome |
| `usecases.healthcare.smart_goal_coaching` | One coaching situation, semantic completion, SMART-goal outcome |
| `usecases.healthcare.therapy_appointment_reminder` | One reminder situation, typed starting context, social aside, completion/outcome |
| `usecases.healthcare.therapy_appointment_reminder_intro` | Introduction and Reminder situations, start branch, typed context, social aside, completion/outcome |

These designs pressure-test four topology families:

1. **Single ordinary interaction** — one guided prompt response or a preserved
   existing exact-text response, with no required graph.
2. **One durable situation with orthogonal rules** — sensing stay rules and
   optional structured completion/outcome.
3. **Branching phased interaction** — role/intro branches that change how later
   events are handled.
4. **Deterministic operation cycle** — RPS phases, operation-owned data, cycles,
   and a finish rule.

All twelve must open, summarize, validate, compile, and round-trip with no
semantic loss. Reproducing current behavior precedes any agent redesign.

## Canonical projection and Advanced views

The frontend retains the complete source `AgentDefinitionV1` and applies
focused immutable transformations. A no-edit open/navigation/export leaves the
canonical document semantically identical, including unknown prompt sections,
component envelopes, schema details, resources, initializers, transitions, and
verification fields. IDs and rule orders generated for new content are stable
and collision-free.

Advanced JSON is an alternate editor for the same document. Apply parses
locally and asks the backend for structural validation before replacing form
state. Parse/schema failure preserves the last valid guided projection and
reports a useful location. Repository metadata cannot be forged through JSON.

## Validation and concurrency

Direct step navigation is never blocked. Local checks cover simple syntax and
required input; backend diagnostics remain authoritative on Save, Validate,
Try, Preview, and Publish. Diagnostics retain stable codes and JSON Pointers,
focus the exact V2 target, and never expose stack traces.

Draft replacement sends the optimistic version. A conflict preserves both
documents and offers reload plus export/copy of local JSON; there is no opaque
automatic graph merge. Save, publish, activate, archive, and import remain
deliberate operations.

## Visual, responsive, and accessibility language

Use calm neutral surfaces, restrained teal accent, generous whitespace, short
labels, and one primary action per context. Cards suit capabilities, situations,
rules, and data. Desktop, narrow desktop, tablet, and mobile must remain usable;
the stepper stacks on mobile. Light/dark themes follow the existing Valerian
preference where practical.

All fields have persistent labels and described help. Cards use native
checkbox/radio semantics. Storyboard and graph operations have keyboard
equivalents. Focus follows visible order; dialogs trap and restore focus;
errors use text/icon/state beyond color; motion respects reduced-motion; touch
targets and contrast meet WCAG AA expectations.

## Frontend boundary and exclusions

The source-owned TypeScript/Vite frontend is built deterministically and served
by Spring at `/valerian-design/`. Generated bundles are not source-controlled.
The Designer uses same-origin APIs and `X-Prometheus-Admin-Token` and contains no
credential/provider configuration.

V2 excludes regulation, AI generation, generic scripts/tools/timers, continuous
`while` triggers, parallel states, collaborative editing, deployment
orchestration, product telemetry, participant-data persistence, and migration
of the `agents` branch.
