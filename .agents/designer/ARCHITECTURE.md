# Declarative Agent Architecture

## Purpose

This document defines how an authored JSON specification becomes a persisted,
executable PROMETHEUS agent without retaining the current Java definition path.
It is normative for persistence boundaries, lifecycle, compilation, caching,
runtime execution, component extension, APIs, and migration.

## Architectural boundaries

```text
Authoring boundary                    Runtime boundary

JSON document                         Controller request
    |                                      |
    v                                      v
schema validation                     application service
    |                                      |
semantic validation                       +--> load AgentInstance
    |                                      |
component validation                      +--> resolve CompiledDefinition cache
    |                                      |
publish immutable revision                +--> execute AgentRuntimeEngine
    |                                      |
activate revision                          +--> persist runtime changes
    v                                      +--> publish behaviour/events
definition repository
    |
    v
compile and prewarm cache
```

The definition side is immutable after publication. The instance side is
mutable. No object crosses that boundary with mixed responsibilities.

## Target modules and responsibilities

Names may follow the repository's established package vocabulary, but these
responsibilities must remain separate.

### Definition document model

Typed JSON-mapping records matching `AGENTDEFINITION_JSON.md`. These are data
only. They contain no controller, persistence, model-client, or Spring behavior.

### Structural validator

Validates the core document using the checked-in JSON Schema. It rejects
unknown fields, wrong types, missing required data, malformed component
envelopes, and unsupported schema versions.

### Semantic validator

Validates rules JSON Schema cannot express conveniently:

- unique keys and IDs;
- valid state, storage, resource, and prompt references;
- valid containment with no composite-state cycles or multiple parents;
- a valid initial state and initial child for every composite state;
- transition order and targets;
- reachability and final-state restrictions;
- component availability and component-configuration schemas;
- storage producer/consumer compatibility;
- observation and behaviour capability consistency;
- prompt-section requirements and bounded sizes;
- deterministic lifecycle and initializer rules.

Diagnostics use stable codes, JSON Pointer locations, severity, a concise user
message, and an optional remediation hint. Warnings never substitute for
errors, and the UI must not parse human-readable messages to locate fields.

### Definition compiler

Converts a validated document into immutable Java runtime objects. Compilation:

- indexes states and storage declarations;
- resolves composite containment and active-state paths;
- orders transitions deterministically;
- resolves registered component kinds and versions;
- compiles prompt sections and event selectors;
- creates stateless policy, decision, action, initializer, and behaviour
  implementations;
- produces definition metadata needed by APIs;
- does not create or mutate an agent instance;
- performs no external model calls.

Compilation must be deterministic: the same schema version, component catalog,
and canonical JSON hash produce equivalent runtime definitions.

### Component registry

The registry is the only Java extension point referenced by a specification.
It owns stable identifiers and versioned configuration schemas. Registration
fails at startup for duplicate identifiers or invalid schemas.

The registry supplies both backend compilation and the designer's component
palette. UI hints are descriptive metadata; backend validation remains
authoritative.

### Definition repository and lifecycle service

The repository persists definition identities and revisions. The lifecycle
service controls draft creation, validation, publication, activation, archival,
import, export, and optimistic update conflicts.

Publication is atomic:

1. Load the draft at the expected optimistic version.
2. Perform structural, semantic, and component validation.
3. Canonicalize the document and compute its hash.
4. Compile it successfully.
5. Mark the revision published and immutable.
6. Install the compiled result in the cache.

Publication does not activate unless explicitly requested by a separate action.

### Compiled-definition cache

The cache is keyed by immutable revision ID and guarded by the content hash.
It guarantees one effective compilation per revision under concurrent access.
All active revisions are prewarmed on startup. Revisions pinned by active
instances remain available.

Cache contents are reconstructible and are never the sole copy. A failed
prewarm for an active definition prevents a misleading partially working
startup.

Expose metrics or structured logs for load/compile duration, cache hits/misses,
revision identity, and compilation failures without logging full prompts,
storage values, credentials, or user events.

### Agent runtime engine

The runtime engine receives:

- a compiled definition;
- the mutable instance snapshot;
- the triggering operation/event;
- trusted runtime dependencies such as model gateways and clocks.

It implements the existing PROMETHEUS semantics for start, acknowledge,
generate, reset, outer-state precedence, ordered transitions, ANDed decisions,
ordered actions, event reprocessing, starter generation, oblivious state
history, final states, storage, and behaviour emission.

It returns explicit runtime changes and emitted events/behaviours for the
application service to persist transactionally. Compiled definition objects
must never retain instance state between invocations.

## Persistence model

The exact table and column names should follow project conventions, but the
following aggregate boundaries are required.

### Agent definition identity

One row per stable key, containing only identity and lifecycle pointers:

| Field | Purpose |
| --- | --- |
| `id` | Internal immutable identifier |
| `definition_key` | Unique public key such as `core.talk_to_me` |
| `active_revision_id` | Nullable reference to the revision used for new instances |
| timestamps | Auditing |

Do not derive catalog paths from Java package names.

### Agent definition revision

One row per draft or immutable published revision:

| Field | Purpose |
| --- | --- |
| `id` | Immutable internal revision identifier |
| `definition_id` | Parent identity |
| `revision_number` | Positive, monotonically increasing within the definition |
| `schema_version` | JSON definition-language version |
| `status` | `DRAFT`, `PUBLISHED`, or `ARCHIVED` |
| `specification_json` | Complete canonical definition document in a native JSON column |
| `content_hash` | SHA-256 of the backend's canonical serialization |
| `provenance` | `BUNDLED`, `DESIGNER`, or `IMPORTED` plus optional source detail |
| `optimistic_version` | Draft concurrency control |
| timestamps | Creation, update, publication, archival |

The database must enforce definition/revision uniqueness. Published content and
hash are immutable. Lifecycle metadata is not part of the content hash.

### Agent instance

The runtime aggregate contains only instance-specific information:

| Field | Purpose |
| --- | --- |
| `id` | Existing public runtime identifier |
| `definition_revision_id` | Required immutable provenance |
| `active_state_id` or active leaf/path | Current point in the compiled graph |
| `storage` | Agent-wide JSON values |
| runtime status/version | Active/final state and optimistic locking as needed |
| timestamps | Lifecycle auditing |

The active state is identified by stable design ID, not a persisted `State`
foreign key. Composite ancestry is derived from the immutable definition; if a
path is persisted for efficient recovery, it is runtime state and must be
validated against that definition.

Event history, behaviour history, access-code links, and other runtime records
continue to reference the agent instance. Static name, description, language,
interaction profile, policies, transitions, and initial values come from the
pinned definition revision rather than being copied into each instance.

## Bundled catalog import

Place versioned source definitions in a deterministic classpath location such
as:

```text
src/main/resources/agent-definitions/
  schema/
    agent-definition.schema.json
  main/
    catalog.json
    core/
      talk-to-me.json
      ...
    usecases/healthcare/
      ...
```

The catalog manifest lists all bundled files and prevents classpath-scanning
differences between development, tests, and packaged JARs.

At startup the importer:

1. Loads the manifest in deterministic order.
2. Validates and canonicalizes every document.
3. Inserts a missing bundled revision.
4. Treats an identical existing key/revision/hash as success.
5. Fails on an existing key/revision with a different hash.
6. Activates the latest bundled revision only when the definition has no active
   revision.
7. Compiles/prewarms all active revisions before reporting readiness.

Updating a bundled resource therefore imports an explicit new revision. It does
not overwrite a draft or silently replace an active designer revision.

## Runtime request flow

For `start`, `acknowledge`, `generate`, `reset`, monitoring, and scoped API
operations:

1. Resolve and authorize the runtime instance as today.
2. Load its lightweight persistence aggregate and pinned revision ID.
3. Resolve the compiled definition from the cache.
4. Verify the persisted active state exists in that definition.
5. Execute the operation using the generic runtime engine.
6. Persist state/storage/history changes in the existing transaction boundary.
7. Publish SSE/behaviour notifications only after persistence follows existing
   consistency conventions.

Controllers remain transport adapters. They must not parse definitions,
compile components, or manipulate state graphs.

## Creation and reset

Creating an instance by stable definition key:

1. Resolve the active published revision.
2. Obtain its compiled definition.
3. Run deterministic and registered initializers.
4. Create the lightweight instance with the initial active leaf/path and
   initial storage.
5. Persist the instance and access-code link transactionally.
6. If configured, execute startup behavior through the normal runtime engine.

Reset reuses the pinned revision, not the currently active revision for the
key. It restores initial state and storage according to the definition's reset
rules and preserves existing public reset semantics.

There is no implicit upgrade of an instance to another revision in this
roadmap.

## API surface for the designer

Use admin-token-protected endpoints under the existing administrative boundary.
Exact DTO names may follow code conventions; the behavior must cover:

- list definitions with active revision and lifecycle summaries;
- retrieve a revision and its canonical JSON;
- create a definition or a new draft revision;
- replace a draft with optimistic concurrency;
- validate without publishing;
- publish, activate, archive, import, clone, and export;
- retrieve registered component schemas and UI metadata;
- create and operate a disposable preview;
- return structured validation diagnostics.

Prefer resource-oriented paths under `/admin/agent-definitions`. Keep preview
operations explicitly separate from production agent/access-code lifecycle.

All mutations require `X-Prometheus-Admin-Token`. Never accept credentials,
provider endpoints, or deployment secrets inside a definition document.

## Preview boundary

A preview compiles the current unsaved or saved draft after validation and runs
it in an isolated, disposable instance. It must not:

- activate or publish the draft;
- appear as a production agent available through access codes;
- contaminate production event/behaviour history;
- retain data beyond its explicit lifecycle/TTL;
- make uncontrolled external model calls in automated tests.

The UI displays active state, received events, storage, emitted behaviour,
validation diagnostics, and component/runtime errors. Preview and production
use the same compiler and runtime engine.

## Definition evolution

Schema evolution and component evolution are independent:

- `schemaVersion` selects the document-language contract.
- A component envelope's `version` selects its configuration/runtime contract.
- `revision` identifies content evolution for one agent key.

Only schema version 1 is required. Unsupported schema or component versions are
hard validation failures. Do not add compatibility shims without an explicit
future migration requirement.

## Migration from the current model

Temporary coexistence is permitted only while the migration milestones are in
progress and the branch remains an unfinished implementation. The completed
migration gate requires:

- all twelve main definitions represented by bundled JSON;
- instance creation resolving the active JSON revision;
- all controller flows using the compiled runtime engine;
- current public keys and observable contracts preserved;
- removal of `AgentDefinition` implementations and registry discovery;
- removal of whole-agent factories and Java prompt-holder classes made unused;
- removal of the ad hoc single-state creation contract if it bypasses the
  canonical definition lifecycle;
- removal or refactoring of `@Entity` definition graph classes and subtype
  tables;
- removal of unused dynamic state subclasses and stale tests/documentation;
- an explicit database migration/runbook for old runtime graph data.

Do not keep a hidden fallback loader or feature flag for Java-authored agents.

## Performance expectations

Compilation shifts work to publication/startup and removes definition graph
hydration from normal requests. The hot path must not query or parse the
definition JSON after the cache is warm.

Tests and instrumentation must establish:

- a revision is compiled once under concurrent access;
- startup prewarming covers all active revisions;
- repeated instance operations hit the cache;
- many instances share one immutable definition object;
- compilation failure cannot leave a revision active but unusable;
- no mutable state leaks between instances.

Do not persist compiled binary forms merely to optimize startup. Measure before
introducing cache eviction, background prewarming, or other complexity.
