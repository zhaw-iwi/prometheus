# PROMETHEUS Designer Documentation

This directory is the implementation contract for replacing Java-authored agent
definitions with versioned JSON specifications and then building the
`/valerian-design/` authoring experience.

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

Read the historical milestones in `PROJECT.md` only through selective searches
when an earlier decision is relevant.

## Document authority

When documents appear to conflict, use this order:

1. The current user instruction
2. `.agents/CODEX.md`, `.agents/CONTEXT.MD`, and the current milestone in
   `PROJECT.md`
3. `DECISIONS.md`
4. `ARCHITECTURE.md` and `AGENTDEFINITION_JSON.md`
5. `AGENTMETAMODEL.md`, `DESIGNER_UX.md`, `TESTING.md`, and `STEPPER.md`
6. `PLAN_DESIGNER.md`

Do not silently resolve a genuine product or architecture contradiction. Record
the evidence and ask only when the documents do not provide a safe answer.

## Scope

The work has three ordered outcomes:

1. Introduce the declarative definition, persistence, compilation, and runtime
   architecture.
2. Migrate all twelve definitions on `main`, then delete the complete legacy
   Java definition path and unused persistence code.
3. Add the designer backend and the guided UI at `/valerian-design/`.

The `agents` branch informed the metamodel, especially its knowledge-backed,
scene-scoped, multilingual, and deterministic patterns. Its definitions are not
migrated in this roadmap.

Regulation is excluded from schema version 1 and from the first designer. It is
not a hidden advanced field.

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

## Current implementation note

Milestones 1-11 use NetworkNT `json-schema-validator` 2.0.7, the maintained
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
