# PROMETHEUS Designer Documentation

This directory is the implementation contract for the versioned JSON runtime
and the domain-expert Designer V2 at `/valerian-design/`. Designer V2 replaces
the completed V1 guided frontend directly; it is not a second authoring path.

The target architecture has one definition language and one runtime path:

```text
JSON definition revision
        -> schema and semantic validation
        -> immutable compiled definition cached by revision
        -> generic runtime engine
        -> mutable agent instance state persisted separately
```

There is no permanent Java whole-agent authoring alternative. Java remains the
trusted implementation language for the engine and registered component kinds,
not for assembling complete agents.

## Required reading order

Before implementing a milestone, read:

1. `../CODEX.md`
2. `../CONTEXT.MD`
3. `../../PROJECT.md` from the beginning through `Current milestone state`
4. `DECISIONS.md`
5. `ARCHITECTURE.md`
6. `AGENTMETAMODEL.md`
7. `AGENTDEFINITION_JSON.md`
8. `DESIGNER_UX.md`
9. `STEPPER.md`
10. `TESTING.md`
11. `PLAN_DESIGNER.md`
12. `PLAN_DESIGNER_V2.md`

Read the historical milestones in `PROJECT.md` only through selective searches
when an earlier decision is relevant.

## Document authority

When documents appear to conflict, use this order:

1. The current user instruction
2. `.agents/CODEX.md`, `.agents/CONTEXT.MD`, and the current milestone in
   `PROJECT.md`
3. `DECISIONS.md`
4. `PLAN_DESIGNER_V2.md` for the active frontend product contract
5. `ARCHITECTURE.md` and `AGENTDEFINITION_JSON.md`
6. `AGENTMETAMODEL.md`, `DESIGNER_UX.md`, `TESTING.md`, and `STEPPER.md`
7. `PLAN_DESIGNER.md` as the declarative/V1 historical roadmap

Do not silently resolve a genuine product or architecture contradiction. Record
the evidence and ask only when the documents do not provide a safe answer.

## Scope

The work has three ordered outcomes:

1. Introduce the declarative definition, persistence, compilation, and runtime
   architecture.
2. Migrate all twelve definitions on `main`, then delete the complete legacy
   Java definition path and unused persistence code.
3. Maintain the designer backend and replace the V1 guided UI with the
   lossless V2 Brief, Capabilities, Interaction, Data & outcome, Try, and
   Review experience at `/valerian-design/`.

The `agents` branch informed the metamodel, especially its knowledge-backed,
scene-scoped, multilingual, and deterministic patterns. Its definitions are not
migrated in this roadmap.

Regulation is excluded from schema version 1 and Designer V2. It is not a
hidden Advanced field.

## Terms

- **Definition**: the stable identity represented by a unique key.
- **Revision**: one immutable published version, or one mutable unpublished
  draft, of a definition.
- **Specification JSON**: the serialized definition document stored in the
  revision and used for import/export.
- **Compiled definition**: immutable Java runtime objects produced from a
  validated specification and cached in memory.
- **Instance**: mutable runtime state pinned to one published definition
  revision.
- **Component**: a registered policy, decision, action, initializer, selector,
  or behaviour strategy referenced by stable kind and version.
- **Activation**: selecting the published revision used when a new instance is
  created for a definition key.
- **Bundled definition**: a source-controlled JSON seed shipped with the
  application.

## Completion standard

A milestone is complete only when its listed production behavior, tests,
documentation, and cleanup are finished. Passing tests do not compensate for
dead legacy code, an undocumented public contract, unsafe database behavior, or
an unverified UI.

## Implementation history and current V2 state

The following Milestones 1-16 record the completed declarative architecture,
runtime/database cutover, backend, and Designer V1 delivery. Their old panel
names are historical evidence only; `PLAN_DESIGNER_V2.md`, `DESIGNER_UX.md`,
and `STEPPER.md` define the maintained frontend.

Milestones 1-16 use NetworkNT `json-schema-validator` 2.0.7, the maintained
Jackson 2 line of the Apache-2.0-licensed validator. The executable schema,
typed document records, canonical serializer, structured semantic diagnostics,
deterministic prompt composer, registered component catalog, immutable compiler,
and revision/hash-guarded single-flight cache are database-, provider-,
persistence-, and Spring-free. Compiled graphs share no mutable instance state.
The generic runtime now executes explicit per-instance state/storage/history
snapshots with trusted injected component dependencies and observable change
sets. All twelve main definitions are bundled as validated revision-1 JSON with
exact prompt/profile parity, resource-backed deterministic therapy context,
exact-text output, and deterministic RPS components. Definition identities and
revisions persist canonical JSON in native JSON columns behind repository
ports, with lifecycle/optimistic rules and explicit activation. The
deterministic startup importer is idempotent, preserves a designer-selected
active revision, and prewarms all active published revisions. Lightweight
instances pin state, storage, initial storage, and history to one revision;
creation/reset contracts retain that pin.

Milestone 8 completed the production cutover. Global and scoped controllers,
access-code type listing, monitoring, Talk to Me, and lifecycle operations now
resolve active JSON revisions through the compiled cache and generic runtime.
The Java whole-agent path, copied static-graph persistence, obsolete
repositories, and ad hoc single-state endpoint are deleted. Flyway version 2
removes the named legacy tables and rebuilds scoped links to declarative
instances; Hibernate validates the final schema. Milestone 9 adds a
default-excluded, double-opt-in local-MySQL smoke harness with a strict dedicated
schema-name guard. It exercises the foreign-key-connected legacy cutover,
bundled import/idempotence/prewarm, deterministic RPS publication/activation/export,
revision-pinned runtime persistence across restart, access-code preservation,
archived-revision execution, final-schema inspection, and verified target
cleanup without provider calls. Database preservation and removal scope are
recorded in `DATABASE_TRANSITION.md`. Milestone 10 exposes the full definition
catalog/draft/import/clone/validate/publish/activate/archive/export lifecycle at
`/admin/agent-definitions`, guarded by the existing admin token. It preserves
canonical documents and server-owned provenance/lifecycle metadata, maps
optimistic and lifecycle conflicts consistently, returns structured schema and
semantic diagnostics, and projects the deterministic typed-component palette
without implementation class or bean names. A full isolated-H2 Spring request
smoke verified the 12-definition catalog, canonical export/validation, all 23
component descriptors, and unauthorized rejection without mutating a bundled
revision or the normal configured database. Milestone 11 adds admin-token-
protected disposable previews for current unsaved JSON and saved drafts. Each
bounded, idle-expiring in-memory session compiles through the production
compiler and runs the production engine/components while retaining its mutable
state, storage, history, transcript, and safe diagnostics outside every
definition, agent, access-code, and history repository. Close/expiry discards
the session. A full Spring/H2 request smoke exercised a saved exact-text draft,
confirmed deterministic output and close-to-404 behavior, and observed zero
global, admin access-code, and scoped agents while the preview was open.
Milestone 12 adds the source-owned React/TypeScript/Vite application at
`/valerian-design/`. Maven installs pinned Node/npm releases, verifies the
frontend, and emits its production bundle only under generated build resources;
no compiled bundle is hand-maintained in source. The shell reuses the existing
admin-token session convention, lists the definition catalog, routes new and
existing revisions, and implements the exact accessible six-step desktop/mobile
stepper from `STEPPER.md`. Focused Vitest contracts cover routing, API states,
catalog states, navigation, ARIA/bounds, and validation targeting; Spring
contracts prove redirects, generated static assets, the admin header, and the
absence of credentials/development API URLs.
Milestone 13 turns the first three steps into schema-version-1 authoring forms
over one complete in-memory document. Purpose controls identity, category,
language, tags, stable-key confirmation, and ordered typed prompt sections;
Sensing declares observation capabilities with current-use indicators;
Behaviour declares modalities, filters registered policy strategies by their
catalog schema/capabilities, and renders a read-only capability summary. The
generated document always contains the explicit `main` atomic state. Backend
validation remains authoritative, while explicit save, dirty/navigation state,
structured diagnostics, and two-choice optimistic-conflict recovery use the
existing lifecycle API. Versioned UI examples remain inert until explicitly
adopted. A live isolated-H2 browser workflow created and reloaded a draft with
its key, prompt, sensing, and speech selections intact and clean.
Milestone 14 makes Reactions a guided projection of the same ordered transition
records used by the runtime, including declared observations, source/target,
conditions, actions, prompt response guidance, priority, catalog-schema-driven
advanced fields, and explicit capability synchronization. State flow renders
the same state/transition arrays through React Flow and an equivalent keyboard
table. Atomic, composite, and final states; containment and initial children;
entry, selector, policy, decisions/actions; cycles; and self-transitions remain
canonical JSON only. Backend pointers mark and focus exact graph elements. A
live isolated-H2 browser workflow authored and backend-validated a four-state
healthcare composite and a two-state RPS cycle with self-transition without JSON
editing; inspected graph/list screenshots had no console errors or horizontal
overflow.

Milestone 15 completes the Review journey over that same document. Validation
diagnostics are grouped by all six guided steps and retain pointer-based focus.
The advanced JSON textarea applies only after local parsing and backend schema
validation, preserving the last valid guided form on failure. Prompt previews
come from the backend `PromptComposer`; they are never separately editable.
Disposable preview UI uses the Milestone 11 session boundary for templates or
advanced event JSON, active path, storage diffs, behaviour/transition trace,
reset, expiry handling, and close-on-discard/unmount cleanup. Confirmed
publish, activation, export, clone, and non-active archive operations are
available only from Review, while canonical import lives in the catalog and
preserves content on identity conflict. Navigation and ordinary draft save do
not invoke a lifecycle transition.

Milestone 16 closes the roadmap with deterministic Playwright evidence for
catalog states, every guided panel, graph/list equivalence, diagnostic focus,
safe JSON synchronization, prompt composition, disposable preview, publication,
light desktop, dark mobile, responsive overflow, and keyboard-visible focus.
The broad visual suite runs against mocked same-origin APIs and a Vite server.
The separately opted-in live suite builds and starts the packaged JAR, provisions
only a guarded `prometheus_designer_smoke_*` MySQL schema, imports a unique
exact-text definition, validates/previews/publishes/activates/exports it, and
verifies schema removal. Existing PROMETHEUS browser regressions now start on
isolated H2 by default. Maven, a multi-stage non-root runtime image, and CI all
build the same source-owned frontend; `.dockerignore` excludes local datasource
properties, environment files, dumps, generated assets, and browser traces.

Designer V2 Milestone V2.1 establishes the domain semantics and twelve-agent
reference corpus. Registered component descriptors now expose stable authoring
roles, guided/Advanced/generated exposure, optional capability grouping, and an
explicit rationale for non-guided components. Config schemas carry safe titles,
descriptions, defaults, and examples. The frontend shell replacement begins in
V2.2; no alternate route or compatibility mode is permitted.

Designer V2 Milestone V2.2 replaces that shell directly. A typed frontend-only
projection retains the complete canonical document while mapping identity, all
ordinary-policy prompt fields and scoped guidance, capabilities, situations,
policies, rules, data roles/outcomes, registered envelopes, and verification
scenarios. It supplies focused immutable transforms, stable ID/order generation,
and V2 diagnostic targets. The six V2 steps preserve save, dirty, conflict,
validation, JSON, and lifecycle behavior; later guided panels are visibly
read-only until their milestones. All twelve production revisions pass no-edit
round-trip coverage. The V1 authoring modules/tests/styles and React Flow
dependency are deleted, with no wrapper, route, flag, or compatibility mode.

Designer V2 Milestone V2.3 makes Brief and Capabilities the first complete
domain workspaces. Brief edits canonical identity plus ordered prompt sections
at agent scope, keeps situation guidance separate, presents unrecognized
section IDs/kinds as editable Additional guidance without normalization, and
requires explicit stable-key confirmation and example adoption. Its composed
prompt preview remains read-only and backend-owned. Capabilities declares
observations and output modalities without generating flow, adds uncertainty,
usage indicators, and Interaction links, and renders response strategies and
deterministic operation groups from backend authoring descriptors. Exact text
has typed domain settings; the RPS pack is one operation card with its inputs,
outputs, and owned data. Kind/version/config are disclosed only under Technical
details. Focused H2 browser coverage creates and reloads prompt and exact-text
drafts without touching the configured database or external providers.

## Bundled main catalog inventory

The deterministic manifest at `../../src/main/resources/agent-definitions/catalog/main/manifest.json`
maps these stable keys to revision-1 resources:

| Key | Resource below `catalog/main/` |
| --- | --- |
| `core.facial_expression_sensitivity` | `core/facial_expression_sensitivity/revision-1.json` |
| `core.multimodal_behaviour` | `core/multimodal_behaviour/revision-1.json` |
| `core.rock_scissor_paper` | `core/rock_scissor_paper/revision-1.json` |
| `core.role_clarification_guessing_game` | `core/role_clarification_guessing_game/revision-1.json` |
| `core.social_context_sensitivity` | `core/social_context_sensitivity/revision-1.json` |
| `core.talk_to_me` | `core/talk_to_me/revision-1.json` |
| `usecases.healthcare.guessing_game` | `usecases/healthcare/guessing_game/revision-1.json` |
| `usecases.healthcare.guessing_game_user_guess` | `usecases/healthcare/guessing_game_user_guess/revision-1.json` |
| `usecases.healthcare.healthcare_conversation` | `usecases/healthcare/healthcare_conversation/revision-1.json` |
| `usecases.healthcare.smart_goal_coaching` | `usecases/healthcare/smart_goal_coaching/revision-1.json` |
| `usecases.healthcare.therapy_appointment_reminder` | `usecases/healthcare/therapy_appointment_reminder/revision-1.json` |
| `usecases.healthcare.therapy_appointment_reminder_intro` | `usecases/healthcare/therapy_appointment_reminder_intro/revision-1.json` |
