# Designer Architecture Decisions

These decisions are settled for the roadmap. An implementation agent should not
reopen them because another approach is possible.

Delivery note: the declarative runtime and domain-expert Designer V2 implement
these decisions. Future changes must preserve the single canonical document,
registered-component boundary, revision lifecycle, and no-regulation scope
unless a separately approved roadmap changes them.

## D1. JSON is the only whole-agent definition language

Every complete agent definition is represented by a versioned JSON document.
The backend validates, persists, compiles, imports, and exports that document.
The designer edits the same representation.

After migration there must be no `AgentDefinition` implementation per agent,
no Java factory that assembles a whole state graph, and no endpoint that creates
an ad hoc code-shaped definition outside this model.

## D2. Persist definitions once and instances separately

An immutable published revision stores the complete canonical specification in
a native database JSON column plus indexed lifecycle metadata and a content
hash. An agent instance references one revision and stores only mutable runtime
data such as its active state, storage, history, lifecycle state, and regulation
state if a future schema supports regulation.

Do not copy the complete state graph into every instance. Do not normalize the
same graph into `StateDefinition`, `TransitionDefinition`, and policy subtype
tables. That would create a second persistence schema parallel to the JSON
Schema and would preserve unnecessary JPA coupling.

## D3. State-machine concepts remain; their JPA role does not

States, composite states, finals, transitions, policies, decisions, actions,
selectors, and initializers remain explicit runtime concepts. The compiler
creates immutable Java objects for them.

The current persistence entities are not the target definition model. Static
definition objects must not be Hibernate-managed. Mutable instance state and
event/behaviour history remain persisted through JPA repositories or an
equally explicit persistence boundary.

## D4. JSON is not parsed on the request hot path

Published revisions are compiled before activation and prewarmed at application
startup. Compiled definitions are cached by revision ID and content hash. A
controller invocation loads the mutable instance, obtains its compiled
definition from memory, executes the engine, and persists runtime changes.

A cache miss may reload and compile the stored JSON. Concurrent misses for one
revision must produce one effective compilation. Compiled definitions are
thread-safe and contain no instance-specific mutable state.

Do not persist Java-serialized compiled objects. They are a disposable cache
that must remain reconstructible from the JSON revision.

## D5. Definition lifecycle is revisioned and explicit

- Drafts are mutable and use optimistic concurrency control.
- Published revisions are immutable.
- Editing a published revision creates a new draft revision.
- Existing instances remain pinned to their original revision.
- A published revision referenced by an instance may be archived but not
  physically deleted.
- Publication validates and compiles; activation is a separate operation.
- New instances resolve the active published revision for the stable key.
- Schema version, definition revision, and content hash are distinct values.

## D6. Bundled definitions use the same repository path

The twelve `main` definitions become JSON resources. A deterministic boot
importer validates them and ensures their immutable revisions exist in the
definition repository. It must be idempotent and must fail clearly if the same
key/revision has different content.

On an empty catalog, the latest bundled published revision becomes active. An
import must not silently replace an explicitly activated designer revision in
an existing catalog. Bundled provenance remains visible, and the designer may
create a new draft revision from a bundled revision.

The running application reads definitions from the repository/cache, not
directly from classpath files. Classpath JSON is a seed and deployment artifact.

## D7. Java extension is below the whole-agent level

Trusted Java components are registered by stable kind and component version.
Each exposes:

- the category: policy, decision, action, initializer, selector, or behaviour;
- a configuration JSON Schema;
- UI labels, descriptions, defaults, and examples;
- a compiler/factory into a stateless runtime component;
- compatibility metadata and tests.

Specifications contain only `kind`, `version`, and `config`. They must not
contain Java class names, Spring bean names, source code, expression-language
scripts, credentials, or executable uploads.

Talk to Me and RPS remain supported through registered deterministic components,
not through whole-agent Java definitions. The component boundary also permits
later Aisha, Migros, TDSR, and SHHD capabilities without changing the core
document structure.

## D8. Preserve public behavior, not internal compatibility

The migration preserves the twelve stable agent keys, display metadata,
language/profile declarations, access-code assignment behavior, documented
controller contracts, prompt intent, deterministic outputs, and state-machine
semantics.

No compatibility is required for Java definition APIs, internal constructors,
old definition tables, or persisted runtime instances containing the old graph.
Existing runtime agents and their histories may be removed by the migration.
Preserve access-code records and allowed stable agent keys where safely
possible; remove links to discarded runtime instances.

The migration is complete only after obsolete entities, repositories,
controllers/DTOs, factories, prompts, tests, tables, and documentation are
deleted or replaced.

## D9. Use explicit schema migrations and safe database tests

Introduce explicit Flyway migrations and move runtime configuration from
Hibernate schema mutation to validation. The intended final schema must be
reviewable in source control.

Any one-time destructive transition must be documented and precisely scoped.
Automated tests and implementation commands must never drop or clean the normal
database named in `application.properties`. Local database smoke tests use the
same configured server credentials only with an explicitly enabled, dedicated
test schema/database and deterministic cleanup of their own data.

Never print or commit database or provider credentials.

## D10. Designer V2 is one domain-expert administrative tool

The designer is served at `/valerian-design/`, replaces the V1 guided frontend
directly, and reuses `X-Prometheus-Admin-Token`. There is no second route,
feature flag, compatibility mode, or parallel V1/V2 form model.

The editor is organized as Brief, Capabilities, Interaction, Data & outcome,
Try, and Review. It starts with one implicit Main interaction. Situations are
introduced only for durable phase changes; capabilities do not create flow by
themselves. One ordered rule projection covers event/entry triggers, optional
conditions, ordered effects, and stay/move/finish. A graph is an optional
derived Advanced view, never a required authoring step. `DESIGNER_UX.md` and
`STEPPER.md` define the interaction, responsive, validation, and accessibility
contracts.

The frontend may use TypeScript, Vite, and an established graph-editing library.
The build and deployment path must remain deterministic and documented.

## D11. Structured prompt elements are first-class

Prompt-bearing components use ordered, typed prompt sections rather than one
undifferentiated text area. The backend composes sections deterministically.
Examples guide the author but never become specification content unless the
author explicitly adopts them.

The designer provides a read-only composed-prompt preview. The advanced JSON
view edits the canonical specification, not an independently editable compiled
prompt.

Agent-wide and situation guidance are scopes of this same ordered concept.
Imported unknown section IDs and kinds remain ordered and lossless as
Additional guidance; moving guidance between scopes is an explicit semantic
edit.

## D12. Regulation and `agents`-branch migration are out of scope

Schema version 1 contains no regulation section. The runtime's current unused
regulation behavior may be removed when it becomes dead after migration. A
future schema version may reintroduce a properly integrated regulation
component.

The `agents` branch is an architectural input, not an implementation target.
Do not modify or migrate its definitions during this roadmap.

## D13. Tests progress from pure contracts to visual confidence

The implementation begins with JSON Schema, semantic validation, compilation,
and runtime unit tests; then adds application/controller integration tests,
opt-in local MySQL smoke tests, frontend component tests, and Playwright visual
and lifecycle tests. External OpenAI/Azure, Speech, and transcription calls are
always replaced with deterministic fakes in automated tests.

Designer V2 additionally treats all twelve bundled documents as the lossless
projection corpus and executes Given / When / Expect scenarios only through the
isolated production compiler/runtime boundary. Product telemetry is not a test
substitute and is excluded.

## D14. The designer frontend is source-built and Spring-served

The designer source lives under `designer/` and uses React, TypeScript, Vite,
and React Flow only for the optional derived flow overview. Exact versions are
locked in `package-lock.json`. Maven owns a pinned Node/npm toolchain, runs
the frontend verification during `generate-resources`, and places the production
bundle under `target/generated-resources/public/valerian-design` for Spring to
serve. Generated JavaScript and CSS are not source-controlled or edited by
hand.

The application uses same-origin relative API paths and the existing
`X-Prometheus-Admin-Token` session-storage convention. Source and bundle must
contain no credential, provider secret, or development-only API host.
