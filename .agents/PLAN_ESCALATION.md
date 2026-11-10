# Coordination escalation and classroom reliability

Prepared: 2026-11-10. Status: ES-01 complete; ES-02 through ES-08 pending.
Actual verification and limitations: `ESCALATION_RESULTS.md`.

## Goal and working model

Make the Coordination Agent receptive to difficulty throughout an interaction:
confusion, repeated unsuccessful attempts, lack of understanding and reports that
the system did something unexpected. Explain from actual evidence, provide a
useful next step, and offer appropriate existing recovery controls. Preserve the
procedural flow that already works: explicit agreements, preparation, acceptance,
fresh cues, bounded contributions, delivery confirmation and recorded outcomes.

Recovery is a shared capability of the existing state machine, not a second task
engine or an independent prompt-owned lifecycle. Every Coordination phase already
routes user utterances through `CoordinationUpdateAction`; `CoordinationMemory`
supplies authoritative facts and `CoordinationPolicy` supplies ordinary/Live
guidance. Extend these seams rather than duplicating recovery states or attaching
a new classifier/model call to every input.

On 2026-11-10 the owner authorized sequential implementation of this roadmap,
committing and pushing all milestone work after verification, then immediately
continuing to the next milestone. Halt for an unforeseen owner decision. Work on
the current `main` checkout. The roadmap's bounded provider checks are part of
milestone verification; this does not authorize production provisioning, branch
promotion or deployment.

## Evidence and priorities

The owner confirmed MScF1–MScF6 as the student codes for the 2026-11-09 course.
The `coord` interaction was excluded. Five histories survive; MScF4 has no linked
agent, so its interaction cannot be assessed. The local private review and scoped
evidence are in ignored `target/course-review-20261109/`; they are not a portable
test fixture and must not be committed.

Observed, independently useful regression targets:

- MScF1: hand-triggered jokes/riddles and explicit replacement worked, but riddles
  revealed their answers and invited replies that repeatedly received generic
  failures. The agent also directed the person to answer in chat. A saved
  "until stop" agreement had a finite bound that the proposal did not explain.
- MScF2: perception exploration was useful; stale facial/hand evidence correctly
  became unavailable. A scissors proposal stayed inactive without acceptance.
  Preserve these behaviours rather than treating every non-action as a defect.
- MScF3: a one-joke task completed at its bound and return preserved history.
  Later, rock preparation reached READY, but natural start attempts failed and
  recovery requested already-satisfied evidence. The saved "another person
  enters" rule used `arrival`, whose detector meaning is zero-to-present rather
  than an additional person entering an occupied scene.
- MScF5: only early exploration survives, close to the common recording cutoff.
  Do not infer disengagement or a task failure from the short record.
- MScF6: a plain hand-perception question failed despite a fresh high-confidence
  scissors observation. The actual rejected operation/provider result is unknown.
- Seven generic update failures survive across three instances. They establish
  poor recovery, but do not establish seven identical technical root causes.
- Heroku logs explicitly establish database `max_questions=36000` exhaustion,
  HTTP 500 failures and later 30-second login timeouts. Four surviving histories
  stop around 15:47:53 Zurich; the retained logs start later, so the precise outage
  onset and per-code query attribution are not established.

Prioritize truthful recovery, activation and supported-task semantics, then the
classroom infrastructure gate. Preserve uncertainty where the original evidence
cannot establish a cause. Build synthetic examples with different wording and
content; never copy student transcripts, production IDs or credentials into tests.

## Default decisions and boundaries

These defaults allow planning to proceed without a blocking owner decision:

1. **Scope:** Coordination-owned behaviour first. Shared fixes must preserve
   Generic, Social Space, healthcare and deployment-specific agents, including
   existing Text/Continuous and Live contracts.
2. **Help and authority:** an explanation request preserves phase, agreement,
   preparation, run identity and contribution budget. It does not silently pause,
   accept, resume, restart or replay work. A clear request to suspend/stop uses the
   existing guarded operation. An ambiguous complaint explains and offers a
   pause; it does not infer consent from frustration alone. A mere help request
   is not a guaranteed quiet period while an independently authorized task runs.
3. **Controls:** resume, restart, return, pause and stop are the existing distinct
   operations. No automatic retry of a rejected mutation or uncertain delivery.
   Unknown causes are reported honestly, not translated into invented diagnoses.
4. **Task capability:** first repair negotiation and execution of supported
   dialogue and independent sensor-reaction tasks. Do not introduce an arbitrary
   game/workflow engine or silently convert a cue task to dialogue. When a desired
   sensor-triggered answer/feedback loop cannot be represented, explain that and
   offer an explicitly reviewed dialogue alternative. Rich cue-to-dialogue rounds
   are a possible later scope decision, not a prerequisite for this roadmap.
5. **Recovery delivery:** explanation is delivered through existing behaviour
   plans and Live response authority. It must not consume task contributions,
   associate with a pending task response, extend task-delivery deadlines or
   falsely confirm playback. User interruption/Stop retains its current meaning.
6. **Infrastructure:** conversational recovery cannot operate when ingress or the
   database is unavailable. The client must provide an honest local fallback.
   Measure capacity before deciding whether to optimize further or buy headroom.
7. **Persistence:** ordinary bounded Storage/facts, no private raw model-output
   archive, permission cache, new polling loop or unbounded incident history.
   Saved agents are not silently rewritten. Each milestone states compatibility,
   fresh-instance requirements and any explicitly scoped additive schema change.

Owner choices needed **before later execution**, not before writing this plan:

- Whether to expand ES-05 into a new bounded cue-to-dialogue protocol. Default:
  keep the supported-task scope above and offer an honest alternative.
- Which measured database capacity envelope to provision for classroom rollout.
  No plan/add-on change or production load test is implied here.
- Approval and limits for paid OpenAI runs, and publication/deployment when ready.
  Synthetic local checks can be prepared without those external actions.

## Verification contract

### Deterministic tests

Use controlled clocks, synthetic sensor events and scripted provider outcomes.
Test the changed success path, representative failure and authority boundaries.
Parameterize shared rules over all nine phases: CONVERSATION, COORDINATION,
PREPARATION, READY, EXECUTION, PAUSED, FINISHING, UNCERTAIN and REFLECTION.
Separate pure response/fact tests from runtime transitions; do not multiply a full
end-to-end journey across every phase when a unit matrix protects the invariant.

Existing anchors include `CoordinationAcceptanceUnitTest`,
`CoordinationRevisionUnitTest`, `CoordinationLifecycleUnitTest`,
`CoordinationDeliveryUnitTest`, `CoordinationObservationTimingUnitTest`,
`CoordinationAgentIntegrationTest`, `CoordinationDeliveryIntegrationTest`,
`SocialSituationChangeDetectorUnitTest` and `QueryVolumeIntegrationTest`.
Add focused escalation tests rather than creating a parallel test harness.

### Disposable local database smoke

Extend `tests/gptlive/run_acceptance.py` and its existing real local application,
HTTP/SSE, persistence/reload and restricted-account setup. Read local test
configuration from `application-test.properties` or explicit local-only runner
configuration. Never target `application.properties`, Heroku or a development
schema containing real data. The runner must continue rejecting remote hosts and
cleaning up only its generated schema, account and owned process.

For offline smoke, external providers/media are scripted. Verify committed state,
run/revision, evidence, budgets and history after reload, not just response wording.
Mocked tests must report that they do not establish real model interpretation.

### Playwright functional and visual checks

Reuse the existing Cockpit, activity footer, monitor/behaviour streams and browser
acceptance/static runners. Cover ordinary recovery, error fallback, reconnect and
agent switching. Inspect screenshots of desktop and narrow layouts, with light
and dark themes where affected. Screenshots must show no overlap, clipped error
guidance or inaccessible controls; functional assertions must establish that the
display agrees with backend state. Review intentional snapshot changes rather
than automatically accepting them.

### Real OpenAI Live happy paths

Extend the existing `--paid-core coordination` journey and scenario selection;
do not invent a second provider transport. The current runner supports scenarios
such as `interaction`, `recognition`, `dialogue` and `revision`; any new scenario
flags mentioned by later implementation must first be added and tested.

Use the actual local application, disposable MySQL and real OpenAI Live sessions,
with synthetic visitor audio and deterministic sensor inputs. Verify both native
captured wording and authoritative backend state, including actual browser
playback observation where required. Do not substitute text inference, captions
alone or a direct playback acknowledgement for a real Live happy path.

Before each paid run, declare one scenario, finite turn/inference/token limits,
one Live session and a maximum duration no greater than the existing 420-second
journey timeout. Extend the harness if it cannot enforce the declared limits.
Set a request/usage ceiling appropriate to the short scenario before execution;
the owner's monetary limit takes precedence. Baseline is one execution per
scenario, with no automatic rerun-until-pass. Record every failed attempt, reason
for another run, usage where available and cleanup result. A run blocked by
credentials/quota is BLOCKED/NOT RUN, never a passed test.

A deterministic assertion can establish authorization and counts; it cannot
guarantee semantic quality. Review actual captured responses against a short
scenario rubric. Provider success is bounded evidence, not universal reliability.
Physical audibility, camera accuracy, classroom experience and robot behaviour
still require a separately recorded operator trial.

## Milestones

### ES-01 — Reproducible classroom cases and bounded recovery evidence

**Depends on:** none.

**Deliverable:** synthetic fixtures for rejected start after preparation,
perception-question failure, riddle/answer mismatch, incorrect extra-person rule,
unaccepted cue and successful one-shot/replacement control. Extend existing
Coordination diagnostics with the minimal bounded, content-free last-relevant
failure facts needed for recovery: safe category/operation, time, source/run/
revision identity, whether mutation committed, and current applicability. Distinguish
inference/provider failure, invalid result, prohibited operation and protected-term
rejection where the code actually knows the difference.

**Testable outcomes:** a rejected update leaves agreement/preparation/budget
unchanged and exposes a truthful recoverable fact after reload. Subsequent
successful progress cannot be described as the old unresolved failure. Old-run,
old-revision and reset evidence cannot diagnose the current task incorrectly.
Evidence expiry/clearing and bounded size are explicit. Missing legacy facts mean
unknown, not a fabricated historical cause. Error-report persistence failure cannot
replace the original error with a claim that diagnostics were saved.

**Checks:** focused unit cases for categories, sanitization, bounds and lifecycle;
disposable-MySQL fail/reload/succeed smoke; harness unit tests for new fixture and
artifact handling. Preserve the successful acceptance, replacement and one-action
fixtures as baseline regression checks. No paid run is needed yet.

### ES-02 — State-aware escalation through the shared user-turn path

**Depends on:** ES-01.

**Deliverable:** recognise requests for explanation, struggle, repeated unsuccessful
attempts and unexpected outcomes within the existing turn interpretation. Reuse
one authoritative recovery-facts assembler for ordinary and Live responses. Give
a brief account of current progress, relevant known failure or uncertainty, and
one useful next step with applicable controls. The explanation fallback remains
available when generation/validation of the normal update fails; avoid depending
on a second model request merely to explain a failed first request.

**Testable outcomes:** help in every phase preserves task authority and contribution
count. READY says preparation is satisfied and acceptance remains; PREPARATION
names only missing checks; PAUSED offers resume; FINISHING describes pending
delivery; UNCERTAIN offers deliberate recovery without replay; REFLECTION retains
the recorded outcome. Clear pause/stop/restart requests execute only their existing
guarded semantics. A help turn neither steals a pending Live contribution's
identity nor marks it delivered; overlap, late completion and interruption keep
their established fences. Successful sensor work outside the help turn retains
its own budget and timing semantics.

**Checks:** nine-phase unit matrix, representative compound requests and unknown
cause; disposable-MySQL escalation/reload/pause/resume smoke; focused Live delivery
overlap regression; Cockpit desktop/narrow recovery screenshots and assertions.
Prepare a paid Live recovery scenario for ES-08, with no extra per-sensor inference
or polling introduced.

### ES-03 — Robust acceptance, preparation explanations and natural controls

**Depends on:** ES-02.

**Deliverable:** improve natural start/assent interpretation in context, including
English variants and German wording used in the course. Use deterministic handling
only when intent, unchanged terms and prerequisites are unambiguous; use existing
inference/clarification elsewhere. Make known starts/rejections explain the actual
remaining boundary. Do not require another demonstration once preparation is valid,
or require a second acceptance after a successful explicit acceptance.

**Testable outcomes:** after a completed invitation and satisfied checks,
"let's start", a minor start-phrase typo and clear German start instructions
activate the unchanged proposal once. Contextual short assent is accepted only
when the invitation supports it. "Ready/Bereit" outside that context remains an
acknowledgement or gets a precise clarification. Negation, questions, future plans,
quoted instructions, expired invitations, revised proposals and missing checks
never start work. ON_CUE activation still spends zero actions and requires a
fresh post-acceptance cue. Repeated starts do not create extra runs or actions.

**Checks:** focused acceptance/voice unit cases including both valid and unsafe
phrasing; real local DB smoke with reload between demonstration and acceptance;
Playwright status/invitation recovery case. Reuse the existing full one-joke
interaction as a regression, then exercise real Live wording in ES-08.

### ES-04 — Reliable ordinary questions and actionable error replies

**Depends on:** ES-01 and ES-02.

**Deliverable:** keep perception/capability questions available throughout task
states, including after a rejected update. Ground answers in existing fresh
observations and distinguish missing, expired, uncertain and usable evidence.
Prevent invalid optional expression or an inappropriate generated operation from
turning an otherwise answerable question into a misleading task-control reply.
Maintain strict executable-agreement, speech and motion validation; any safe reply
fallback must be narrowly specified and cannot apply a partial invalid mutation.

**Testable outcomes:** a current scissors question with fresh confidence around
0.87 names the supplied sign without consuming a task action. Missing/stale data
does not produce a current recognition claim. A malformed provider reply or
provider failure preserves state and yields an honest recovery response. A normal
question during a pending task does not authorize a new contribution or confuse
task-speech association. No claim of direct image access or certain inner emotion.

**Checks:** deterministic question/age/invalid-output cases, including the existing
stale-face success; local DB question/failure/reload smoke; small Playwright case
showing sensed evidence, reply and unchanged task status. Paid Live perception
questions join the recovery scenario in ES-08 rather than another separate run.

### ES-05 — Honest task negotiation, useful riddles and visible limits

**Depends on:** ES-03 and ES-04.

**Deliverable:** match the proposed human/agent roles and response instructions to
the executable task form. A single sensor reaction must not promise an answer/
evaluation loop that the implementation cannot honour. When asked for such a game,
offer an explicit supported dialogue alternative or a clearly described single
cue-triggered output, without silently removing the cue. Explain finite contribution
bounds for "until stop" and clarify limits when needed. Preserve all unaffected
terms during content revision and require explicit replacement for material changes.

For supported dialogue riddles, ask the question without its solution, allow the
person to answer, then provide relevant feedback as an authorized contribution.
Make the contribution budget account for both question and feedback and negotiate
enough budget to finish a round. Avoid repeating the same riddle within a short
run when prior questions are available; do not add an unbounded question bank,
scored game engine or unsupported arbitrary workflow.

**Testable outcomes:** the user either accepts a supported, clearly bounded form
and completes a question/answer/feedback exchange, or is told precisely what is
unsupported before execution. No "answer here" invitation is emitted by an
output-only sensor action that cannot handle that answer. A question does not
reveal its answer, ordinary/recovery questions do not spend contributions, and
terminal outcomes are not equated with the person solving the riddle. Explicit
replacement suspends old rules; unrelated scissors after a rock-only replacement
does not trigger work. The finite cap is visible and reload preserves it.

**Checks:** deterministic negotiation/operation/budget/revision cases; disposable
DB dialogue round and sensor-to-dialogue replacement smoke; Playwright proposed
terms/limits and final-outcome checks. Paid Live dialogue in ES-08 reviews actual
question, answer handling and feedback, not merely the presence of keywords.

**Scope checkpoint:** if satisfying this milestone requires a new mixed cue/
dialogue protocol, stop and present the proposed bounded contract to the owner.
The default deliverable remains honest supported alternatives; do not quietly
expand into a game engine or remove a user's material cue requirement.

### ES-06 — Correct and inspectable social-cue semantics

**Depends on:** ES-01 and ES-03.

**Deliverable:** map natural social conditions to what the actual detector and
rule model can establish. Explicitly distinguish first arrival into an empty
scene, an additional visible person, crowd thresholds and grouping changes.
Provide authoritative event-value semantics to task interpretation rather than
expecting the model to infer them from field names.

If a condition cannot be represented faithfully by the existing independent
single-field rules, explain the limitation and negotiate a specific measurable
alternative. Do not substitute a fixed count threshold for a general increase
without agreement. Any genuine need for relative-change semantics must be scoped
as a deliberate extension of the existing detector/rule contract, with all
consumers and saved-data compatibility reviewed.

**Testable outcomes:** "another person enters while I am already here" can never
be silently accepted as `changeType=arrival`. Test zero-to-one, one-to-two,
two-to-three, departure, unchanged scene and grouping-only changes, including
detector category precedence. The executable rule and explanation agree; a
faithfully negotiated alternative triggers once and unrelated changes do not.
No inference of identity or proof that a physical person actually entered.

**Checks:** detector and Coordination compilation/validation unit cases; disposable
DB agreed-condition/reload/trigger smoke; a browser case presenting the agreed
condition and observed change. Real Live cue wording joins ES-08's sensor journey.

### ES-07 — Infrastructure fallback and a measured six-user capacity gate

**Depends on:** ES-01; may be implemented independently of ES-03 through ES-06
after its scope is approved. Run measurements against the final integrated tree.

**Deliverable:** explain transport/database unavailability locally in the Cockpit
when the agent cannot respond. Preserve visible accepted history and distinguish
last-known task state from current confirmed state. Offer bounded reconnect or
operator guidance without automatically resending task mutations, restarting work
or claiming a successful stop. Reuse existing sensing backoff/lease handling.

Measure a deterministic six-client classroom workload on disposable local MySQL:
scoped login/create, concurrent sensor delivery, monitor/behaviour SSE, ordinary
questions, accepted cue tasks, recovery, reload/reconnect and cleanup. Include
the production-relevant enabled worker/tick paths in a controlled phase, or
identify and measure excluded paths separately. Attribute global query deltas to
the isolated workload, including direct JDBC and transaction Questions; event
counts and Hibernate-only counts are not substitutes. Report warm idle, active
sensing, task/recovery bursts, total throughput, latency and projected hourly
Questions separately. Add only bounded, measured persistence savings that preserve
authorization, freshness, durability and healthy sensor cadence.

**Testable outcomes:** simulated 500/503, timeout, SSE loss and reconnection give
truthful visible guidance with no duplicate submission or false task-state claim.
Cross-agent switches cannot revive old recovery messages. The local six-user run
produces reproducible raw measurements, an explicit operating envelope and a
pass/fail capacity conclusion for the proposed deployment allowance, with at
least 20% headroom reserved for measured variability/other traffic. A plan at
36,000/hour would require at most 28,800/hour for the total chosen workload;
this is a release criterion, not a claim that the current architecture can meet it.
Short-run hourly extrapolation must be labelled; sustained acceptance must cover
the intended 60–70 minute classroom window before claiming classroom readiness.

If the current quota cannot meet that envelope, record the capacity gate as unmet
and present measured provisioning/optimization choices. Successful functional
tests must not be reported as a capacity pass. No remote load test or paid add-on
upgrade is part of this milestone without explicit owner instruction.

**Checks:** focused error/reconnect/backoff unit tests; real local DB six-client
smoke and query regression with controlled providers; desktop/narrow Playwright
visuals for unavailable/last-known/recovered states. Verify normal ingress and
recovery add no unbounded polling or repeated model calls. Document how to retain
sanitized incident evidence before reset/deletion; enabling external log drains
or adding a durable research archive is a separate rollout decision.

### ES-08 — Integrated OpenAI Live acceptance and operator handoff

**Depends on:** ES-02 through ES-07 for the final acceptance tree. A capacity
failure does not prevent local functional investigation, but it blocks a claim
that the deployment is ready for another six-user class.

**Deliverable:** one integrated evidence record and three short real Live happy
paths, using the extended existing runner and its bounded paid-run controls:

1. **Preparation, struggle, recovery, acceptance and one-shot delivery:** synthetic
   rock/scissors evidence, a help request before acceptance, clear truthful
   guidance, natural explicit start, zero actions while arming, one fresh cue,
   matching speech capture/playback, Reflection and history-preserving return.
   Include an ordinary fresh-sign question without a task-budget change.
2. **Dialogue and a supported revision:** negotiate a bounded riddle dialogue,
   accept, hear a question without its answer, answer and receive relevant
   feedback; ask for help/change and review the proposal before renewed acceptance.
   Check contribution limits, run identity and ordinary questions throughout.
3. **Social condition and recovery controls:** negotiate a condition actually
   expressible by the detector, distinguish first arrival from another person,
   execute a matching cue, then exercise an explicit pause/resume or deliberate
   restart where appropriate. Check that explanations never claim a transition
   or physical outcome not present in the backend.

For these Live happy paths, use real OpenAI transport and local real runtime/DB;
synthetic sensors/audio make the stimulus reproducible. Keep fault/late-delivery
matrices deterministic offline rather than spending paid runs on every branch.
An initial known-invalid update for scenario 1 may be injected only as a clearly
labelled application failure fixture; subsequent Live comprehension and recovery
must be real, and the fixture cannot stand in for model-failure diagnosis.

**Testable outcomes:** all deterministic milestone gates pass on the integrated
tree; each paid journey's captured response agrees with persisted control,
perception and budgets; no premature action, duplicate contribution, answer leak,
automatic replay, false recovery confirmation or task-response misassociation.
Record rubric judgement, state assertions, usage, failures and NOT RUN limitations.
Any failed critical check leaves that scenario unmet even if other attempts pass.

**Checks:** focused combined Java/Node regression first, then broader regression
proportional to shared runtime changes; real local DB smoke, reviewed Playwright
functional/visual checks, packaging and whitespace checks. Run the three bounded
paid journeys only when authorized. Provide an operator script covering real
camera/microphone/speaker, ordinary/challenging/after-change trials and explicit
stop/capture teardown. Do not label physical acceptance complete from synthetic
media or playback acknowledgements alone.

## Documentation, compatibility and completion

At each implementation milestone, create/update `.agents/ESCALATION_RESULTS.md`
with the implementation scope, actual commands, test outcomes, retained failure
evidence, compatibility/fresh-instance requirements and remaining limits. Update
README for changed user guidance/contracts/configuration, the top/current state of
PROJECT.md for milestone status, and CONTEXT.MD only for stable architectural
changes. Search historical milestones only when an earlier decision is needed.
This planning-only change does not assert an implemented capability in those files.

Preserve explicit public Event/BehaviourPlan/profile contracts, scoped access and
saved history. Reuse existing monitor/SSE and canonical response delivery. Bound
new storage values to the existing limits; do not deploy a larger format without
an explicit compatibility/schema decision. Fresh graph requirements must be
stated instead of claiming saved instances were upgraded automatically.

A milestone is complete only when its stated implementation and required checks
have passing evidence, documentation is synchronized and remaining uncertainty is
explicit. Functional completion, real-provider behavioural acceptance, physical
acceptance and deployment capacity are separate claims. Report what changed and
what was actually verified, then stop for owner review/commit unless further work
has already been authorized.
