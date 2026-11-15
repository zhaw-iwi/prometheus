# Online student study system: implementation roadmap

Prepared **15 November 2026**. Status: **OS-01–06 complete; OS-07 next**.
Actual verification is recorded in [RESULTS.md](RESULTS.md). Read [STUDY.md](STUDY.md) for requirements and
[PAPER_BRIEF.md](PAPER_BRIEF.md) for research context. This roadmap owns milestone
sequence, dependencies, acceptance, and implementation evidence.

## 1. Confirmed scope and decisions

The owner confirmed:

- Deliver **all three executable scenarios**: A, the guessing game; B, the
  contextual medication check-in; C, negotiated gesture answers. Implement A first.
- Use **English** for the interface, task materials, questionnaires, and agent.
- Use **text-to-text** interaction, with disclosed controlled simulations for
  scene/sign observations. No participant microphone or camera is required.
- Plan for **asynchronous participation or scheduled waves**, not an assumption
  of 40-50 concurrent sessions.

Retain the brief's one-system design and one assigned scenario per participant.
Scenario variants share coordination control; this plan does not add a comparative
agent condition. Completing A alone is an intermediate result, not delivery of
the complete requested system. Unfinished B/C cannot be silently replaced by
specification-only exercises.

No additional owner answer is needed to write this plan or begin independent
implementation when instructed. These remaining choices have later deadlines:

| Decision | Working basis | Must be resolved before |
| --- | --- | --- |
| Persona and agreement presentation | Concise neutral study persona; plain, deterministic review of saved proposal/accepted terms, consistently available across scenarios | Material/UI freeze in OS-09; record this as part of the studied design |
| Exact task cards, revision/probe timing, session limits and scoring | Draft from STUDY.md; short free-form intentions, one revision, understanding checks, bounded trial | Real-provider scenarios in OS-11, then pilot freeze |
| Hosting, provider/model configuration, allowed spend and quotas | Isolated local development; finite budgets for provider trials; no deployment assumed | Real-provider configuration in OS-11 and capacity target in OS-12 |
| Concurrent wave size and latency targets | Measure a candidate eight-session wave first; this is a test target, not proven capacity | OS-12 acceptance; adjust the operational cap to the measured result |
| Ethics, participant information, recruitment, retention and withdrawal policy | Prepare implementable drafts; keep real collection disabled while required arrangements are unresolved | Any human pilot or main-study recruitment/collection |
| Dates and support arrangements | Pilot first, then frozen main study; planned deadline remains in the brief | Pilot scheduling and participant release |

Do not postpone unrelated UI, persistence, fixture, or task work while these
choices are pending. Do not invent an approval, hosting limit, or successful pilot.

## 2. Engineering and test boundaries

Follow the [repository bootstrap](README.md). Before implementation, check the
branch/worktree and current code. Application branch:
`experiment/hri-2027-coordination`, created from `17244cb1` in the existing checkout.
Preserve existing user work, public contracts, and saved
agents. Keep study-specific prompts, definitions, and UI outside baseline core
behavior; extract only necessary reusable seams.

The study application owns procedural progress, private research records, and
assignment. The existing Coordination runtime owns agreement and task authority.
Task executors implement accepted terms. A page transition, questionnaire response,
or simulated event must not directly change the agent's control state.

Use the smallest test layer that proves the behavior. Smoke and browser tests are
integration/end-to-end checks, not unit tests:

| Level | Purpose | Required environment/evidence |
| --- | --- | --- |
| Unit/contract | Rules, validation, state decisions, scoring, serialization, request identity | Controlled clocks, deterministic model outputs, representative rejected/stale/duplicate cases |
| Local database smoke | Real persistence, HTTP/SSE, reload, rollback, session isolation and atomic evidence | Disposable loopback MySQL schema and restricted account; synthetic providers; actual assertions against stored data |
| Playwright functional/visual | Participant journey, reconnect/error states, rendering, keyboard access, desktop/mobile usability | Real study application and disposable DB for journey tests; deterministic fixtures; screenshots/traces and human visual inspection |
| Real-provider text trial | Whether configured models interpret and communicate the tested tasks faithfully | Same local application and disposable DB, bounded paid inference, retained failures and independent semantic review |
| Sustained workload | Whether the selected deployment/configuration can support the planned waves | Measured text workload, recorder and SSE enabled, realistic histories, explicit limits and headroom |
| Human pilot | Whether people understand the materials and can complete the procedure | Appropriate study arrangements, real participants, observed problems and documented revisions |

Existing [run_acceptance.py](../../../tests/gptlive/run_acceptance.py) provisions
owned random schemas/accounts and rejects remote DB targets. Reuse its isolation
and cleanup mechanisms, adding study fixtures/spec selection where needed. Its
current browser list does not automatically discover a new study journey. The
default [Playwright config](../../../playwright.config.mjs) may reuse a running
application: study acceptance must explicitly bind to the isolated runner's
server. Never point destructive integration fixtures at the developer or deployed
database. Some existing classes named UnitTest also start a database context.

Use synthetic data only in committed fixtures. Disable scheduled ticks unless a
test explicitly controls them. Keep test-only provider/inspection endpoints off
the production classpath. Review visual snapshots rather than blindly updating
baselines. A screenshot, valid JSON, or HTTP 200 alone is not journey acceptance.

## 3. Milestone sequence and status

Update this table as work proceeds and
record actual commands, results, artifacts, failed attempts, and remaining limits
in a companion `RESULTS.md` created during implementation. Do not copy old passing
counts into new evidence. Proposed test names below are contracts to implement,
not claims that those tests exist.

| Milestone | Outcome | Depends on | Status |
| --- | --- | --- | --- |
| OS-01 | Versioned study contract, materials, agent seam and isolated fixtures | Bootstrap | COMPLETE |
| OS-02 | Durable, isolated participant sessions and provisioning | OS-01 | COMPLETE |
| OS-03 | Recoverable text turns with durable request identity | OS-02 | COMPLETE |
| OS-04 | Complete research recording and initial export | OS-03 | COMPLETE |
| OS-05 | Resumable participant text interface | OS-04 | COMPLETE |
| OS-06 | Executable guessing-game coordination | OS-05 | COMPLETE |
| OS-07 | Executable contextual check-in | OS-06 | NOT STARTED |
| OS-08 | Executable negotiated sign answers | OS-07 | NOT STARTED |
| OS-09 | Full assessment, allocation and operator workflow | OS-06, OS-07, OS-08 | NOT STARTED |
| OS-10 | Integrated offline acceptance and analysis rehearsal | OS-09 | NOT STARTED |
| OS-11 | Bounded real-provider semantic acceptance | OS-10 | NOT STARTED |
| OS-12 | Capacity-qualified package and pilot handoff | OS-11 | NOT STARTED |

The sequence is deliberately incremental: recorder coverage precedes any human
pilot; A exercises the complete shared path before B/C extend task semantics.
One milestone may require several implementation/test iterations. After each,
update relevant runtime/API/setup documentation and evidence, then follow the
user's requested review cadence. The owner explicitly authorized committing and
pushing each completed milestone, then continuing to the next, on this branch.
Bounded live OpenAI/local-database happy-path checks are also authorized.
Halt for an unforeseen material owner decision. Deployment and human recruitment
remain separate from this implementation authorization.

## OS-01 — Study contract, materials, and fixtures

**Outcome:** one versioned, testable description of the study and a minimal
shared agent configuration seam, without duplicating Coordination control.

**Deliver:** proposed study packages/client route; material manifest with stable
scenario, question, revision, and probe IDs; English task/form drafts and rubrics;
pilot/main designation and readiness flags; study persona/profile configuration;
synthetic test fixture startup and explicit study-spec selection in the isolated
runner. Do not prefill hidden participant requirements in agent prompts.

**Acceptance and tests:**

- Unit/contract checks reject unknown/inconsistent material IDs, invalid scenario
  parameters, or enabling an unready scenario. Manifests expose their version.
- Profile/prompt tests show study text capabilities and no accidental changes to
  the baseline agent's persona, graph, catalog key, or supported contracts.
- Runner preflight verifies loopback-only DB ownership, selected test/spec counts,
  provider isolation, cleanup, and absence of production fixture endpoints.
- Minimal local-DB smoke provisions and reloads a study definition. Record which
  changes require fresh agent instances or additive schema changes.

## OS-02 — Participant sessions, assignment, and access

**Outcome:** a participant returns to the same isolated study attempt, with
durable procedural progress and no access to another participant's records.

**Deliver:** study session/attempt persistence, versioned consent status and
procedural stages, stable scenario allocation, scoped provisioning, operator-only
administration, and private-form storage separated from agent history. Implement
the selected retention/withdrawal policy when supplied; unresolved collection
requirements keep participant collection disabled.

**Acceptance and tests:**

- Local-DB tests create two scopes, verify cross-scope reads/writes/exports are
  denied, and reload the original assignment after application/context restart.
- Concurrent entry requests cannot create duplicate attempts or duplicate agents;
  failed/revoked provisioning cannot leave a usable orphan instance.
- Direct HTTP calls cannot skip consent/procedural prerequisites, mutate a closed
  attempt, or reveal private answers. Check ordinary underlying endpoints too:
  the study UI must not expose a bearer credential that bypasses study gates.
- A deliberate replacement attempt has a new identity linked to its predecessor;
  neither refresh nor a temporary disconnect erases or reallocates the old attempt.

## OS-03 — Durable text submission and recovery

**Outcome:** uncertain transport cannot silently duplicate an accepted utterance,
task contribution, or acceptance/revision operation.

**Deliver:** study-scoped request identity and result lookup, serialized ordinary
turn submission, explicit pending/committed/rejected/uncertain outcomes, canonical
response-event correlation, and reconnect reconciliation. Reuse runtime services;
scope any required shared contract extension explicitly. Document the boundary
between a received request, a committed effect, and displayed output.

**Acceptance and tests:**

- Unit/DB tests retry the same request identity concurrently and after reload:
  one canonical mutation/result, with a conflicting payload rejected. An uncertain
  provider attempt is not automatically repeated to manufacture a result.
- Fault-injection smoke covers loss before commit and loss after commit but before
  HTTP response. Lookup returns the durable truth; ambiguous work stays explicit.
- Interleaved same-session requests cannot reorder answers or acceptance. Foreign
  sessions cannot query or reuse a request identity to obtain another result.
- Cancellation, revocation, or closed-attempt status fences late work using the
  appropriate runtime authority. Do not promise exactly-once provider billing.

## OS-04 — Recorder, snapshots, and export foundation

**Outcome:** an interrupted session still yields an auditable research record,
including the agreement that was accepted at each relevant boundary.

**Deliver:** implement the [recording contract](STUDY.md#6-recording-allocation-and-operations),
immutable initial/revised agreement snapshots, event/run/revision correlations,
private form/assessment records, generation-versus-display evidence, completeness
markers, and a first machine-readable export with schema/version manifest.

**Acceptance and tests:**

- Local-DB tests accept, revise, and accept again, then restart/reload: both exact
  snapshots survive and link to the correct committed event. Snapshot persistence
  shares the relevant commit or a verified durable handoff, not best-effort polling.
- Inject rollback/recorder interruption: no false accepted snapshot, silent missing
  revision, or apparently complete export. Recovery is attributable and testable.
- Generate more than 12 journal entries and eight outcomes: the study record stays
  complete despite bounded runtime diagnostics. Duplicate display receipts do not
  duplicate model events or establish comprehension.
- Spy on actual inference requests and public APIs: private preferences, secret,
  questionnaires and researcher keys do not leak. Export omits credentials and
  preserves missing, failed, cancelled and withdrawn status according to policy.

## OS-05 — Participant text interface

**Outcome:** a participant can navigate entry, preparation, coordination, and
failure/recovery through a reduced, resumable interface.

**Deliver:** task card and conversation view, deterministic proposal/accepted-term
review, procedural progress, pending/error states, preserved unsent input,
help/finish/withdraw controls, and placeholders backed by real procedural stages
for subsequent assessments. Keep developer settings out of this view.

**Acceptance and tests:**

- Real-app Playwright smoke verifies send/response through HTTP and native SSE,
  one rendered canonical response across reply/stream/replay, and same-session
  recovery after refresh or a lost response. Double-click/Enter does not double-submit.
- Exercise slow inference, 429/5xx, rejected input, and connection loss. Failed or
  pending messages remain distinguishable; typed input is recoverable; withdrawal
  remains reachable without creating a second task request.
- Visual checks at desktop and narrow mobile widths cover task card, long messages,
  proposal review, loading, error and terminal states. Inspect wrapping, overflow,
  contrast and focus; keyboard traversal reaches all participant controls.
- Browser assertions detect microphone/camera requests or Speech/Live traffic and
  fail if present. Render user/model content as text, with an injection fixture.

## OS-06 — Scenario A: bounded guessing game

**Outcome:** a participant can negotiate roles, accept, play, revise, and finish
a Twenty Questions-style activity through the measured text path.

**Deliver:** scenario-owned bounded execution using existing guessing patterns
where appropriate, explicit role/secret/answer/guess/stopping semantics, a truthful
question-versus-contribution budget, revision/probe cards, and a scoring rubric.
The human's private secret must not be accessible to the agent. A first-action
prediction must precede START's immediate first contribution.

**Acceptance and tests:**

- Unit tests cover roles, permitted answers, exact budget boundaries, guess versus
  question accounting, explicit success reporting, pause/resume and early stop.
  Budget exhaustion is distinguishable from guessing correctly.
- Revision tests preserve untouched requirements, reject unintended protected-term
  changes, and require renewed acceptance before the revised task executes.
- Local-DB journey persists roles/counters across reload and proves that secret and
  assessment responses never enter the model context through the study application.
- Playwright completes initial agreement, pre-probe prediction, bounded trial,
  prescribed revision and final assessment entry. Use deterministic model output
  here; evaluate model guessing/communication behavior separately in OS-11.

## OS-07 — Scenario B: contextual check-in

**Outcome:** an accepted fictional check-in follows its dialogue and context
policy, including interruption and explicit resumption.

**Deliver:** explicit task state connecting the conversation and controlled
presence/count observations; agreed privacy/postponement wording and resumption
conditions; a disclosed scene panel and timestamped scripted probe sequence.
Reuse existing event and authority boundaries. Counts do not identify patients;
unknown/stale evidence is not confirmed absence.

**Acceptance and tests:**

- Unit tests cover the initial encounter, both scripted answer branches, accepted
  multiple-person condition, absence, return and permitted resumption. A generated
  response held in flight when context changes cannot release superseded sensitive
  content; already displayed text cannot be recalled and is recorded as displayed.
- Negative cases: no action before acceptance, no confusion between empty-to-occupied
  arrival and another person joining, stale evidence cannot end an encounter, and
  unrelated observations cannot consume a task contribution.
- Local-DB smoke reloads during postponement and after revision; old-scene or
  old-revision work cannot resume the conversation automatically.
- Playwright shows the simulated context and a complete agreed interruption/recovery
  journey. Review fixtures for fictional, nonjudgmental support and no medication
  dose instruction or claimed clinical outcome.

## OS-08 — Scenario C: negotiated sign answers

**Outcome:** signs answer the intended pending question once, according to the
accepted mapping; wait/resume has explicit semantics.

**Deliver:** scenario-owned sign interpretation and pending-question identity;
thumbs-up/down/open-palm simulation controls; preparation demonstrations where
agreed; source/time/revision validation; pause/resume and bounded completion.
Extend a public observation/profile contract only if necessary and update every
affected validator/consumer. Do not repurpose a generic sensor WAIT as a dialogue engine.

**Acceptance and tests:**

- Unit tests cover each negotiated mapping, unknown sign, no pending question,
  duplicate or stale event, wrong question/run/revision, and a held/repeated sign
  that must not answer a later question. Preparation does not count as an answer.
- Open-palm waiting preserves the pending question and budget. Resume follows the
  agreed policy. Changing mappings needs renewed acceptance and cannot silently
  reinterpret a previously consumed sign.
- Local-DB smoke reloads a pending/paused question and proves single consumption
  across duplicate requests and reconnect. A sign cannot accept a draft merely
  because it is configured as a yes answer inside the task.
- Playwright completes negotiation, demonstration, question/answer, wait/resume,
  revision and completion with visible simulation provenance and no camera access.

## OS-09 — Assessments and complete study operations

**Outcome:** all three scenarios support the full research procedure, including
unsuccessful attempts, with usable assessments and exports.

**Deliver:** finalize pre/post forms, UMUX-LITE and separately labeled exploratory
items, scenario understanding questions, locked pre-feedback answers, debrief and
completion receipts. Add operator provisioning/allocation, progress/failure views,
assistance records, versioned export, and analysis/coding templates. Resolve the
neutral persona/review-panel and instrument choices before freezing materials.

**Acceptance and tests:**

- Unit tests cover scoring and missing/not-applicable values, valid stage changes,
  frozen material versions, and readiness-gated allocation. Reopening an attempt
  cannot redraw its task or overwrite a submitted prediction after seeing feedback.
- DB smoke verifies private records, withdrawal/closure, operator authorization,
  failed attempts, and reproducible exports with correct participant/attempt counts.
- Playwright completes one successful journey per scenario plus one no-agreement
  and one technical-failure journey; all can reach the applicable assessment/debrief.
  Snapshot full forms at both target widths, including validation and keyboard use.
- A synthetic coding rehearsal compares independently specified requirements,
  accepted snapshots, participant predictions and behavior. It must distinguish
  accepted-but-wrong, faithful-but-misunderstood, and faithful-but-poorly-executed cases.

## OS-10 — Integrated offline acceptance

**Outcome:** the assembled application meets its deterministic contracts and
preserves affected baseline behavior, with reproducible evidence.

**Deliver:** one explicit study acceptance entry point selecting the actual Java,
JS and browser checks, synthetic data/export fixtures, an evidence manifest, and
operator reproduction instructions. Ensure new fixtures are discoverable by the
runner; an empty selection or unexpected skip must fail acceptance.

**Acceptance and tests:**

- Run complete local-DB journeys for A/B/C with the recorder and real HTTP/SSE;
  include refresh/reconnect, duplicate ingress, rollback, in-flight revision/stop,
  cross-scope rejection, and final export consistency.
- Run the affected core acceptance/revision/persistence, scoped controller, and
  client regressions; broaden to full Java/package checks for shared/runtime/schema
  changes. Do not repeatedly rerun unrelated suites without new justification.
- Inspect Playwright screenshots for all participant stages and failure states;
  retain failing traces. Repeat evidence tests after relevant fixes rather than
  accepting fixture-only workarounds that bypass production semantics.
- Run the analysis rehearsal over the exported dataset, verifying allocation,
  denominators, initial/revised snapshot counts, failures and missingness. No real
  participant records or production credentials enter test artifacts committed to Git.

## OS-11 — Real-provider text acceptance

**Outcome:** configured models complete the bounded study scripts with semantically
faithful agreements and responses under the actual application controls.

**Deliver:** extend the existing coordination-text provider journey pattern for
the study definitions, with capped requests/tokens/duration and a recorded model,
route, prompt and material version. Synthetic participants/events run through the
study API, recorder and disposable local DB. No Live/audio path is needed.

**Acceptance and tests:**

- Predeclare a finite script matrix for A/B/C: initial specification, meaningful
  clarification, acceptance, prescribed revision, bounded trial/probe and completion.
  Include at least one natural-language paraphrase of acceptance or revision per
  scenario; do not require internal operation keywords.
- Inspect initial and revised agreements against script intentions and actual
  responses against committed roles/conditions. Premature action, changed terms,
  false success, or unsupported promises fail semantic acceptance even when all
  schema/HTTP checks pass. Record independent review separately from raw artifacts.
- Retain every attempt, cost/usage and failure. Rerun only after a documented cause
  or change, then rerun the affected offline checks. A model judge is not the sole
  authority; passing scripts remain bounded evidence, not universal reliability.
- Provide a ready-to-run pilot build and known-limit report. This is not yet a
  claim of human usability or capacity at the intended deployment.

## OS-12 — Capacity, release package, and pilot handoff

**Outcome:** the complete system has a measured operating envelope and a concrete
operator handoff; external readiness conditions are explicitly separated.

**Deliver:** study-specific sustained workload runner, configured wave/session
admission cap, deployment/runbook and schema instructions, recovery/export check,
pilot checklist and final status/evidence summary. Record hosting/provider quotas
and numeric latency/time-out criteria before measuring; do not choose thresholds
after seeing results simply to pass.

**Acceptance and tests:**

- Begin with the candidate eight-session wave, covering all scenarios, realistic
  turn pacing, at least a full session's history, recorder writes, assessments,
  SSE, reconnect and export. Sustain the workload for at least 30 minutes including
  a representative active interval; use synthetic providers for reproducible
  infrastructure load and report real-provider latency evidence separately.
- Zero cross-session leakage, duplicate committed effects, missing required
  snapshots, unexpected 5xx failures, or exhausted connection/worker pools in the
  accepted run. Idle SSE releases DB connections. Sessions over the configured cap
  receive bounded admission feedback, not an unbounded queue or lost assignment.
- Report latency distributions, queue time, timeouts, database query volume,
  CPU/memory where available, provider usage estimates and fixed setup/export cost.
  Require measured load to fit actual quotas with explicit headroom (default 20%).
  Local evidence alone cannot certify a different hosting tier. Reduce the wave
  size or address the bottleneck and retest when the target fails.
- Verify restart/recovery and export on the proposed deployment configuration.
  Prepare an environment-specific smoke checklist; run it on a deployed service
  only when deployment is within the user's authorization.

Software handoff requires OS-01 through OS-12 evidence for **all three** scenarios.
Human-study readiness additionally requires the institutional arrangements,
reviewed participant materials, an actual 4-6-person pilot, pilot issue resolution,
and a frozen protocol/build. Mark these external gates PENDING until observed.
If the pilot changes code/materials, update versions and repeat affected checks;
do not mix pilot and main-study data or imply automated tests replaced the pilot.

## 4. Verification entry points and evidence record

Existing commands below are reference patterns, not checks run for this plan.
The selected tests and browser specs must be updated for study acceptance in
OS-01/OS-10. See [README testing](../../../README.md#testing) for prerequisites.

```powershell
# Existing bounded local-DB regression pattern; never use the developer schema.
python tests/gptlive/run_acceptance.py --java-tests CoordinationAgentIntegrationTest,ScopedCreationConcurrencyIntegrationTest

# Existing pure browser-logic test pattern.
node --test tests/js/state-graph/*.test.mjs

# Existing real-app browser pattern on the runner-owned disposable environment.
# The study specs must be added to explicit selection before they can run here.
python tests/gptlive/run_acceptance.py --java-tests none --browser --browser-mode disabled
```

Use [Coordination revision tests](../../../src/test/java/ch/zhaw/prometheus/agentdefs/core/CoordinationRevisionUnitTest.java),
[Coordination integration tests](../../../src/test/java/ch/zhaw/prometheus/application/CoordinationAgentIntegrationTest.java),
[scoped creation concurrency tests](../../../src/test/java/ch/zhaw/prometheus/controllers/ScopedCreationConcurrencyIntegrationTest.java),
[classroom browser smoke](../../../tests/playwright/valerian-classroom-smoke.spec.mjs),
and [capacity fixture](../../../src/test/java/ch/zhaw/prometheus/controllers/ClassroomCapacityIntegrationTest.java)
as patterns. Their existing coverage is not a substitute for the study cases.

For each milestone, RESULTS.md should record: source revision and configuration;
changed components/migrations; exact commands and selected test counts;
passed/failed/skipped outcomes; artifact paths and reviewed screenshots;
provider semantic verdicts where applicable; remaining limits; and next milestone.
Mark a milestone complete only when its stated outcome and required checks pass.
Do not mark the entire roadmap complete merely because a deployable A-only demo
exists or because the paper deadline is approaching.
