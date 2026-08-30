# PROMETHEUS Designer Implementation Roadmap

## Objective

Replace Java-authored whole-agent definitions with a single versioned JSON
definition architecture, migrate every agent on `main`, remove all legacy
definition/persistence paths, and deliver a guided designer at
`/valerian-design/`.

Implementation branch: `features/designer`, created from `main` at
`d5b8583d3ebd898a4c29baa187b1e1cd38851133`.

## Execution rules

- Complete milestones in order.
- Keep each milestone focused and independently reviewable.
- Temporary old/new coexistence is permitted only inside the unfinished
  migration phase. It is not a supported compatibility mode.
- Run the milestone's smallest high-value test set plus relevant regressions.
- Update affected README, API documentation, this roadmap, and `PROJECT.md`
  current milestone state in the same milestone when public behavior changes.
- Review the complete diff and ensure no secrets/artifacts are staged.
- Commit and push every completed milestone before starting the next one.
- Do not migrate or modify definitions on the `agents` branch.
- Do not stop for routine engineering choices covered by the designer docs.
- Stop only for an unforeseen product/architecture/data-safety decision that
  these documents cannot resolve, or an external blocker that prevents the
  milestone's required verification/commit/push after reasonable diagnosis.

## Milestone status

| Milestone | Status |
| --- | --- |
| 1. Executable JSON contract | Complete |
| 2. Semantic validation and prompt composition | Complete |
| 3. Component registry, compiler, and cache | Complete |
| 4. Immutable runtime state-machine engine | Complete |
| 5. Prompt-based main catalog in JSON | Complete |
| 6. Deterministic main components and complete JSON catalog | Complete |
| 7. Definition repository, revisions, and seed import | Complete |
| 8. Runtime/application cutover and legacy deletion | Complete |
| 9. Final MySQL migration smoke gate | Complete |
| 10. Designer lifecycle API | Complete |
| 11. Disposable preview API | Complete |
| 12. Designer frontend foundation and stepper | Complete |
| 13. Purpose, Sensing, and Behaviour authoring | Complete |
| 14. Reactions and State flow authoring | Complete |
| 15. Review, JSON, preview, and publication UX | Planned |
| 16. Playwright, packaging, and final acceptance | Planned |

## Phase I - Declarative foundation

This phase builds the new path using fixtures while the production catalog
continues to work. No milestone in this phase claims that legacy has been
removed.

### Milestone 1 - Executable JSON contract

**Outcome**

Install the machine-readable schema and typed document mapping for schema
version 1.

**Implementation**

- Add `src/main/resources/agent-definitions/schema/agent-definition.schema.json`
  using JSON Schema draft 2020-12.
- Add typed Java data records for the root, metadata, interaction, lifecycle,
  storage, state variants, transitions, component envelopes, prompt sections,
  resources, and verification scenarios.
- Select one maintained JSON Schema validator compatible with the project's
  Jackson/Spring stack after checking its current stable documentation and
  license.
- Reject unknown properties and unsupported schema versions.
- Implement deterministic parse/serialize behavior and canonical content
  hashing.
- Add focused valid/invalid fixtures described in `TESTING.md`.
- Keep document records persistence- and Spring-free.

**Tests**

- Schema unit tests for the minimal valid definition and each state variant.
- One focused invalid fixture per major structural category.
- Parse/serialize/canonical-hash determinism test.
- Security contract rejecting class/bean/script escape-hatch fields.

**Verification**

- Run the new schema/document tests.
- Run the existing fast Java unit tests most closely related to interaction
  profiles and selectors.
- Confirm fixture and production-resource JSON can be parsed without accessing
  a database or provider.

**Documentation/commit gate**

- Reconcile any field adjustment across all designer documents.
- Mark Milestone 1 complete here.
- Commit a focused JSON contract change and push the branch.

### Milestone 2 - Semantic validation and prompt composition

**Outcome**

Produce structured diagnostics for graph, capability, storage, prompt, and
reference correctness before compilation.

**Implementation**

- Implement semantic validation with stable diagnostic code, severity, JSON
  Pointer, message, and remediation hint.
- Validate ID uniqueness/references, initial state/children, containment,
  reachability, final-state constraints, transition order, capabilities,
  storage schemas/bindings, resource references, and prompt sections.
- Implement deterministic prompt composition for every prompt role.
- Define the supported embedded `valueSchema` keyword subset.
- Separate warnings from publication-blocking errors.

**Tests**

- Accept graph cycles and self-transitions.
- Reject containment cycles, multiple parents, missing initial children,
  dangling targets, final outgoing edges, duplicate transition order,
  undeclared emissions, and invalid storage dependencies.
- Assert diagnostic code and pointer rather than complete prose.
- Exact prompt-section order/separator/normalization tests.
- Adapt representative assertions from current prompt/profile contract tests.

**Verification**

- Run schema and semantic-validator unit suites together.
- Confirm validation performs no database, Spring context, or external calls.

**Documentation/commit gate**

- Document all stable diagnostic codes near the implementation.
- Update metamodel/JSON docs if semantic evidence changes a rule.
- Commit and push before Milestone 3.

### Milestone 3 - Component registry, compiler, and cache

**Outcome**

Compile a valid document into an immutable runtime definition and resolve it
once per revision through a concurrency-safe cache.

**Implementation**

- Add the registered component SPI with kind, version, category, config schema,
  UI metadata, and compiler/factory.
- Implement baseline generic components required by fixtures: no-op and prompt
  policies, fundamental selectors, latest-event/prompt decisions, extraction
  action, and constant/random initializer as justified by main definitions.
- Build immutable compiled state, containment, transition, prompt, storage, and
  interaction-profile structures.
- Add a cache keyed by revision ID and guarded by hash.
- Deduplicate concurrent compilation and support startup prewarm.
- Expose safe compile/cache instrumentation.

**Tests**

- Component registration uniqueness and config-schema tests.
- Unknown kind/version and invalid config diagnostics.
- Compiler reference resolution and immutability tests.
- Equivalent document compilation contract.
- Concurrent cache resolution compiles once.
- Multiple revisions do not alias; failed compilation is not cached as active.
- Many synthetic instances can share a compiled definition without mutable
  state leakage.

**Verification**

- Run Milestones 1-3 unit suites.
- Run relevant existing policy/decision/action tests.
- Confirm no JPA annotation or repository dependency exists in compiled model
  packages.

**Documentation/commit gate**

- Document component authoring and registration beside the SPI.
- Commit and push before Milestone 4.

### Milestone 4 - Immutable runtime state-machine engine

**Outcome**

Execute start, acknowledge, generate, and reset from a compiled definition and
an explicit mutable instance snapshot without using JPA state-graph entities.

**Implementation**

- Extract/refactor current state-machine semantics into a generic runtime
  engine.
- Represent active leaf/path, storage, and history as instance state.
- Preserve composite-before-inner transitions, first accepted transition,
  ANDed decisions, ordered actions, starter/reprocess entry, oblivious state
  behavior, and final inactivity.
- Return explicit state/storage/history/behaviour changes to the caller.
- Supply runtime dependencies through a trusted context.
- Keep the current production path operating until the catalog cutover.

**Tests**

- Port/adapt `StateTransitionUnitTest` and outer-state tests to compiled
  definitions.
- Cover start, acknowledge, generate, reset, cycles, self-transition, final,
  selectors, entry modes, action order, and event-history behavior.
- Prove two instances using one definition evolve independently.
- Use deterministic fake model runtime; no database or network.

**Verification**

- Run new runtime tests plus current state/policy regressions.
- Compare representative old/new state traces in tests while the old code still
  exists; do not retain the comparison adapter after Phase II.

**Documentation/commit gate**

- Record any intentionally corrected runtime semantic difference explicitly;
  otherwise preserve existing behavior.
- Commit and push before migrating definitions.

## Phase II - Main catalog migration and legacy removal

All twelve `main` agents move to JSON. The phase ends with one production path,
a lightweight runtime instance, explicit schema migrations, and no legacy code.

### Milestone 5 - Prompt-based main catalog in JSON

**Status: Complete.** Ten revision-1 JSON definitions and their deterministic
manifest are bundled under `agent-definitions/catalog/main`. Exact current
prompt/profile contracts are retained. Typed resource-backed random therapy
context and final-transition prompt behaviour use registered components; the
production Spring-discovered path remains intentionally unchanged until the
Phase II cutover.

**Outcome**

Represent and compile the ten prompt-oriented main definitions without Java
whole-agent assembly.

**Scope**

- Core facial-expression sensitivity
- Core multimodal behaviour
- Core role-clarification guessing game
- Core social-context sensitivity
- All six healthcare definitions

**Implementation**

- Create bundled JSON revisions under the deterministic main catalog layout.
- Split current long prompts into ordered prompt sections without altering
  content or intent.
- Add any genuinely reusable component/config schema missing from Milestones
  1-4.
- Represent therapy-reminder typed random context through a deterministic
  initializer component/resource.
- Create the catalog manifest.

**Tests**

- Production JSON passes schema, semantic validation, and compilation.
- Catalog key/metadata/language/profile parity.
- Representative topology and prompt-composition contracts for single,
  composite, two-state, sensing self-transition, and role-branching agents.
- Deterministic initializer test using injected randomness.
- Existing public prompt/profile contracts adapted to load JSON.

**Verification**

- Run all definition/validation/compiler/runtime tests and focused current
  agent-definition contracts.
- Inspect composed prompts against source prompts for accidental loss or
  reordering.

**Documentation/commit gate**

- Update catalog migration notes but do not yet tell users the production path
  is fully cut over.
- Commit and push before Milestone 6.

### Milestone 6 - Deterministic main components and complete JSON catalog

**Status: Complete.** Talk to Me and RPS are bundled as revision-1 JSON. Five
strict deterministic component kinds preserve exact text, RPS selection,
evaluation, reveal, and result behavior without provider calls. All twelve
definitions compile and start through the declarative catalog harness; reusable
exact-text validation and English RPS rendering live outside whole-agent
assembly. Production discovery remains on the temporary Java path until the
ordered cutover.

**Outcome**

Represent Talk to Me and RPS through trusted registered components so all twelve
main definitions compile and execute through the declarative harness.

**Implementation**

- Move exact-text behavior behind a stable deterministic policy/behaviour kind.
- Move RPS sign selection, reveal, evaluation, result, display, and motion
  behavior behind stable component kinds and typed config.
- Create the two bundled JSON revisions.
- Relocate reusable domain implementations out of `agentdefs`; do not delete
  old production discovery until cutover.
- Complete the component palette metadata for all main-catalog components.

**Tests**

- Exact Talk to Me text/plan contract.
- Deterministic RPS selection, reveal, evaluation, result, storage, display,
  motion, repetition, and final paths.
- All twelve production JSON files validate, compile, and start in a catalog
  harness.
- Assert no JSON contains class/bean names or scripts.

**Verification**

- Run all main-agent prompt/profile/runtime contracts.
- Run the full Java regression suite if the local configured test environment
  permits; report any database-bound exclusions accurately.

**Documentation/commit gate**

- Record the complete component catalog and JSON file inventory.
- Commit and push before persistence cutover.

### Milestone 7 - Definition repository, revisions, and seed import

**Outcome**

Persist definition identities/revisions as canonical JSON, implement lifecycle
rules, and import the bundled catalog idempotently.

**Implementation**

- Introduce Flyway and source-controlled schema migrations.
- Add definition identity/revision persistence, native JSON mapping, hashes,
  statuses, provenance, optimistic version, and active revision pointer.
- Add repository ports/adapters and lifecycle application services needed for
  import, publication, and activation.
- Implement deterministic manifest import and startup prewarm.
- Add the lightweight instance persistence shape pinned to a revision, without
  yet exposing the designer API.
- Precisely plan migration/removal of old instance graph tables and links.
- Preserve access-code records/allowed stable keys where safe.

**Tests**

- Repository/service contract tests with in-memory adapters.
- Revision/status/optimistic-lock/publication/activation rules.
- Canonical hash and immutable-published-update rejection.
- Bundled import first-run, second-run idempotence, conflicting-hash failure,
  and no silent override of an active designer revision.
- Creation/reset service tests pinning and retaining revision identity.
- Flyway migration syntax/ordering checked without touching the normal DB.

**Verification**

- Run service integration tests with real compiler/cache.
- Review every migration for exact destructive targets.
- Do not run a clean/drop against `application.properties` database.

**Documentation/commit gate**

- Add a database transition/runbook documenting disposable runtime data and
  preserved access-code data.
- Commit and push before application cutover.

### Milestone 8 - Runtime/application cutover and legacy deletion

**Outcome**

Every production creation/controller flow uses active JSON revisions and the
compiled runtime engine; no legacy definition or static graph persistence path
remains.

**Implementation**

- Cut scoped/global creation, monitoring, start, acknowledge, generate, reset,
  delete, and access-code type listing to the new services.
- Refactor `Agent` into the lightweight mutable instance aggregate.
- Remove JPA definition roles from static state/transition/policy/decision/
  action classes or replace/delete them as appropriate.
- Remove all `AgentDefinition` implementations, registry discovery, whole-agent
  factories, unused prompt holders, unused dynamic state subclasses, old
  persistence repositories, and migration adapters.
- Remove the ad hoc single-state creation endpoint/DTO if it bypasses canonical
  revision creation.
- Remove obsolete tables in the explicit final migration and set Hibernate to
  schema validation rather than mutation.
- Update README catalog, setup, testing, API, repository layout, and agent
  development instructions to JSON/component authoring.

**Tests**

- Existing controller/scoped/access-code compatibility tests adapted to JSON.
- Creation uses active revision; existing instance remains pinned after
  activation change.
- Runtime persist/reload tests for active state, storage, and history.
- Catalog contract for exactly the twelve stable keys.
- Structural legacy-removal checks in `TESTING.md`.
- Full Java regression suite.

**Verification**

- Search the production tree and documentation for all removed interfaces,
  factories, endpoints, tables, and Java-authoring instructions.
- Inspect dependency injection to ensure no dormant fallback path exists.
- Confirm no unused Java/code/resources remain merely for compatibility.

**Documentation/commit gate**

- Update `PROJECT.md` current milestone state and README truthfully.
- This is the no-legacy architecture gate. Do not mark complete with known dual
  paths or dead definition code.
- Commit and push before database smoke.

### Milestone 9 - Final MySQL migration smoke gate

**Outcome**

Verify the final schema, bundled import, revision lifecycle, and runtime
persistence against a real local MySQL server using a dedicated test target.

**Implementation**

- Add the opt-in `local-db-smoke` test/profile/tooling from `TESTING.md`.
- Require explicit enablement and dedicated database/schema name.
- Add refusal guards for the normal configured application database.
- Use deterministic Talk to Me/RPS behavior only.

**Tests**

- Execute all eight local MySQL smoke assertions listed in `TESTING.md`.
- Start/restart or create a fresh application context to prove reload/prewarm.
- Verify clean final schema contains no old definition graph tables.

**Verification**

- Actually run the smoke against the local database; do not mark this milestone
  complete from mocked/in-memory tests.
- Run the full Java suite afterward.
- Report target identity safely without credentials.

**Documentation/commit gate**

- Document the exact safe smoke command using environment placeholders.
- Record what data the migration discards/preserves.
- Commit and push. Phase II is then complete.

## Phase III - Designer backend

### Milestone 10 - Designer lifecycle API

**Outcome**

Expose admin-token-protected definition CRUD/lifecycle, validation,
import/export, clone, and component-catalog APIs.

**Implementation**

- Add resource-oriented endpoints under `/admin/agent-definitions`.
- Support list/get/create draft/update with optimistic version/validate/publish/
  activate/archive/clone/import/export.
- Return structured diagnostics and consistent error/status responses.
- Expose component schemas, defaults, examples, capabilities, and UI hints.
- Ensure imported lifecycle/provenance fields cannot forge server metadata.
- Extend CORS allowed header behavior only if needed; same-origin designer is
  primary.

**Tests**

- Web MVC authentication for every endpoint class.
- Happy paths plus malformed JSON, validation error, unknown key/revision,
  optimistic conflict, immutable update, invalid activation, protected archive,
  and import conflict.
- Canonical export/import round trip.
- Component catalog deterministic ordering and no implementation class names.
- Application integration through real lifecycle/compiler/cache with fake
  persistence where database behavior is not under test.

**Verification**

- Run controller/application suites and Java regression suite.
- Exercise representative requests against a running local app when practical,
  without modifying bundled revisions.

**Documentation/commit gate**

- Add the designer admin API to README/API documentation.
- Commit and push before preview.

### Milestone 11 - Disposable preview API

**Outcome**

Validate/compile a draft and exercise it in an isolated preview using the same
runtime engine as production.

**Implementation**

- Add create, event/generate/reset/inspect, and close preview operations.
- Keep preview instances out of production agent/access-code listings and
  histories.
- Add explicit TTL/cleanup and resource bounds.
- Return active path, storage, event/behaviour transcript, and safe execution
  diagnostics.
- Use current unsaved JSON as well as a saved draft when authorized.

**Tests**

- Invalid draft cannot preview; valid draft need not publish.
- Preview follows production runtime semantics.
- Isolation from production repositories and between preview sessions.
- TTL/close cleanup and unknown/expired session responses.
- Deterministic event, state, storage, and behaviour trace.
- Model gateway replaced by a fake.

**Verification**

- Run API/application tests and one manual deterministic local preview.
- Confirm no preview records appear in existing admin/scoped agent listings.

**Documentation/commit gate**

- Document preview lifetime and non-production semantics.
- Commit and push before frontend work.

## Phase IV - Guided `/valerian-design/` UI

### Milestone 12 - Frontend foundation and stepper

**Outcome**

Serve a production-buildable TypeScript designer shell with admin-token entry,
catalog screen, editor routing/state, and the accessible six-step component.

**Implementation**

- Establish a minimal TypeScript/Vite frontend source tree and deterministic
  build into Spring static resources.
- Select frontend/graph dependencies using current maintained releases,
  compatible licenses, and locked versions.
- Integrate build into CI/deployment without hand-edited generated bundles.
- Serve `/valerian-design/` with correct trailing-slash/static behavior.
- Reuse the existing admin-token header/storage convention.
- Build loading/error/empty/populated catalog states and create/open routing.
- Implement the six-step component exactly as `STEPPER.md` requires, using
  dynamic z-order and stable test selectors.

**Tests**

- Type check/build.
- Focused stepper navigation, ARIA state, bounds, and validation-target tests.
- Catalog API state tests.
- Spring static-resource/redirect/admin-header contract test.
- JavaScript bundle contains no credentials or development-only API URL.

**Verification**

- Run frontend tests/build, static resource tests, and syntax checks.
- Inspect desktop and mobile stepper manually in the local browser; visual
  automation comes in Milestone 16.

**Documentation/commit gate**

- Update README requirements/build commands and repository structure.
- Commit source, intentional build artifacts according to the chosen build
  policy, lockfile, and backend serving changes; push before Milestone 13.

### Milestone 13 - Purpose, Sensing, and Behaviour authoring

**Outcome**

Create and save a useful single-state draft through the first three guided
steps without editing JSON.

**Implementation**

- Purpose identity/language/category and structured prompt sections.
- Sensing capability cards, interpretation guidance, and usage indicators.
- Behaviour modality cards, compatible strategy selection, prompt guidance,
  and read-only summary.
- Schema/component-metadata-driven labels, help, defaults, and examples.
- Explicit example adoption, dirty state, Save draft, optimistic conflict, and
  unsaved navigation warning.
- Generate the explicit default `main` atomic state in canonical JSON.

**Tests**

- Form-to-JSON and JSON-to-form round trips.
- Capability selection and strategy compatibility.
- Example viewing does not mutate; adoption does.
- Prompt section ordering and composed-preview request mapping.
- Save success, backend validation display, and optimistic conflict recovery.
- No duplicate frontend semantic validator.

**Verification**

- Run frontend and relevant backend API tests.
- Manually create/reload a draft against the local application.

**Documentation/commit gate**

- Update contextual help/example catalog documentation.
- Commit and push before reactions/state flow.

### Milestone 14 - Reactions and State flow authoring

**Outcome**

Connect sensing to behaviour and evolve the default state into a validated
multi-state/composite graph visually and accessibly.

**Implementation**

- Guided reaction cards for observation, state, decisions, actions, response,
  target, and order.
- Component-specific advanced configuration from schemas.
- Visual graph for atomic/composite/final states, containment, initial child,
  transition edges, cycles, self-transitions, and inspectors.
- Accessible list/table alternative with equivalent operations.
- One canonical document model shared by cards, graph, and forms.
- Diagnostic-to-node/edge/field navigation.

**Tests**

- Reaction transformations preserve order and references.
- Adding an undeclared capability offers an explicit synchronized update.
- Default-state to multi-state conversion preserves reactions.
- Add/edit/delete/reorder state and transition operations.
- Composite containment and initial child editing.
- Self-transition/cycle preservation.
- Graph/list equivalence and keyboard-accessible operations.
- Backend errors mark and focus the correct graph element.

**Verification**

- Run frontend tests/type check/build and backend validation contracts.
- Manually reproduce a representative healthcare composite flow and RPS cycle
  without editing JSON.

**Documentation/commit gate**

- Document graph controls and keyboard alternative in user help.
- Commit and push before final review integration.

### Milestone 15 - Review, JSON, preview, and publication UX

**Outcome**

Complete the author journey from draft review through validation, preview,
publication, activation, import/export, clone, and archive.

**Implementation**

- Plain-language summary and grouped actionable diagnostics.
- Advanced canonical JSON alternate editor with safe apply/recovery.
- Read-only composed prompt previews.
- Disposable preview transcript, event templates/advanced event JSON, active
  state, storage diffs, behaviour, reset, and close.
- Publish/activate/archive confirmations with exact revision consequences.
- Import/export and clone flows.
- Loading, expired preview, authorization, network, and conflict recovery.

**Tests**

- JSON parse error preserves form state; valid edit round trips.
- Diagnostic grouping/link/focus across all six steps.
- Preview request/response state and cleanup.
- Publish disabled on errors and enabled only after backend success.
- Activation explains new-instance-only behavior.
- Import conflict, export, clone, and archive states.
- No lifecycle action occurs on step navigation or ordinary save.

**Verification**

- Run complete frontend/backend designer suites.
- Manually create, preview, publish, activate, export, and archive a uniquely
  keyed test definition against the local application.

**Documentation/commit gate**

- Add concise in-app help and README designer usage.
- Commit and push before browser acceptance.

### Milestone 16 - Playwright, packaging, and final acceptance

**Outcome**

Prove visual quality, responsive/accessibility behavior, live lifecycle,
packaging, and final architectural cleanup.

**Implementation**

- Add deterministic mocked Playwright visual coverage from `TESTING.md`.
- Add the smaller live Spring/dedicated-MySQL designer lifecycle scenario.
- Cover light desktop and dark mobile, all six steps, graph/list, diagnostics,
  prompt/JSON/preview/publication states, and keyboard focus.
- Update Docker/deployment/CI build stages for the frontend.
- Finish README, API, repository structure, local setup, testing, and
  `PROJECT.md` current milestone documentation.
- Perform final dead-code, dependency, generated-artifact, security, and scope
  audit.

**Tests**

- Complete Java regression suite.
- Local MySQL smoke suite.
- Frontend unit/type/build suite.
- Existing Playwright regressions.
- New designer mocked visual suite.
- New designer live lifecycle suite.
- Packaged JAR/container static-resource smoke as supported by the repository.

**Verification**

- Actually inspect Playwright screenshots/traces for clipping, overlap, focus,
  responsive graph/stepper, contrast, and accidental secrets.
- Start the packaged application and open `/valerian-design/`.
- Verify `git status`, dependency lock, generated asset policy, and no
  `agents`-branch migration.
- Confirm no test claim relies on unrun provider-dependent behavior.

**Documentation/commit gate**

- Mark every roadmap milestone complete only with recorded verification.
- Report remaining risks and unverified environments.
- Commit and push the final milestone, leave a clean worktree, and stop for user
  review.

## Final acceptance checklist

- One whole-agent authoring format: JSON schema version 1.
- Twelve main definitions are bundled JSON revisions with stable public keys.
- No Java whole-agent definition/factory/registry/fallback remains.
- Static state graphs are not JPA entities and are not duplicated per instance.
- Instances pin immutable revisions and persist only runtime state/history.
- JSON is compiled/prewarmed and absent from the warm controller hot path.
- Published revision lifecycle and activation semantics are enforced.
- Flyway owns the clean final schema; Hibernate validates it.
- Local MySQL smoke is real, opt-in, dedicated, and safe.
- Designer API uses the existing admin token.
- `/valerian-design/` implements the six-step guided experience.
- Prompt examples require explicit adoption.
- One implicit/default state supports bottom-up authoring.
- Graph and accessible list editing produce the same canonical document.
- Regulation and `agents`-branch migration remain absent.
- Java, frontend, Playwright, packaging, README, and `PROJECT.md` truthfully
  reflect the delivered architecture.
