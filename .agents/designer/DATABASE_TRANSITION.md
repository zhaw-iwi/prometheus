# Declarative Database Transition Runbook

This runbook is the database safety contract for the ordered declarative-agent
cutover. It complements `ARCHITECTURE.md`; it does not authorize running a
clean, drop, or destructive migration against the normally configured database.

## Milestone 7 additive state

Flyway migration `V1__create_declarative_agent_aggregates.sql` has no destructive
target. It creates only:

- `agent_definition`, one row per stable public definition key;
- `agent_definition_revision`, one native-JSON row per draft/published/archived
  revision, with canonical hash and optimistic version;
- `declarative_agent_instance`, lightweight runtime state pinned by foreign key
  to one revision.

The application baselines a pre-Flyway non-empty schema at version `0`, then
applies version `1`. A new empty schema applies version `1` directly. Startup
imports the ordered bundled manifest idempotently and prewarms active published
revisions. An existing active revision is never replaced by the seed importer.

Milestone 7 deliberately left the old runtime tables and Java-authored catalog
in use. Milestone 8 ended that coexistence through the version-2 cutover below.

## Data classification for the Phase II gate

Preserve:

- every `access_code` identity, code, and enabled flag;
- `access_code_allowed_agent_type` assignments for the twelve stable public
  keys, without changing the key strings;
- `agent_definition` and `agent_definition_revision` rows and hashes;
- any `declarative_agent_instance` created after the declarative cutover.

Disposable by the settled roadmap:

- legacy `agent` rows and their copied static graphs;
- legacy state, transition, decision, action, policy, storage, and event-history
  rows;
- existing `access_code_agent` links, because they point to discarded legacy
  `agent` rows.

If an allowed-agent-type row contains a key outside the twelve-key catalog,
Milestone 8 must report it during its preflight. It must not silently reinterpret
or map that key.

## Milestone 8 final cutover

Flyway migration `V2__cut_over_to_declarative_runtime.sql` is the explicit
destructive migration. It encodes the following named targets in dependency
order:

1. Remove rows from `access_code_agent`, preserving `access_code` and
   `access_code_allowed_agent_type`.
2. Remove legacy collection/join tables:
   `event_state_path`, `action_storage_keys_from`, `transition_actions`,
   `transition_decisions`, `state_transitions`, and `storage_entries`.
3. Remove legacy aggregate tables in foreign-key-safe order:
   `event`, `event_history`, `agent`, `state`, `transition`, `decision`,
   `action`, `policy`, `storage_entry`, and `storage`.
4. Replace the old `access_code_agent.agent_id` relationship with the final
   declarative-instance link required by the unchanged scoped API. Do not retain
   a compatibility foreign key to legacy `agent`.
5. Delete the matching legacy entities, repositories, whole-agent factories,
   endpoints/DTO paths, tests, and documentation, then switch Hibernate from
   schema mutation to schema validation.

The production application now performs every scoped/global lifecycle operation
through revision-pinned declarative instances. Startup reports preserved
allowed-agent-type keys that do not resolve to an active definition; it does
not reinterpret them. Version 2 has executed under Flyway and Hibernate
validation against disposable H2 databases in MySQL mode and against a guarded
dedicated local MySQL schema. The MySQL smoke seeded every named legacy table
with representative foreign keys, preserved access-code configuration,
restarted Spring, inspected the final schema, and verified removal of its
dedicated target afterward.

No wildcard table selection, database-wide clean, or broad schema drop is
permitted. The final migration must name each confirmed table and constraint.

## Verification environments

- Ordinary unit/service tests use in-memory repository adapters.
- Flyway ordering, DDL syntax, Hibernate validation, and native-JSON round trips
  use a disposable H2 database in MySQL mode.
- Real MySQL migration verification uses the Milestone 9 `local-db-smoke`
  profile and requires the documented explicit opt-in plus a verified dedicated
  test database/schema.
- The database URL in `application.properties` is never a smoke-test target and
  must never be cleaned or dropped.
