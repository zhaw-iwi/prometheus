# Coordination workflow refinement

Prepared: 2026-10-28. Status: CR-01 through CR-08 implemented and checked; CR-09 next.
Evidence: COORDINATION_RESULTS.md.

This plan covers the observed Coordination trial and its subsequent ordinary
conversation. The owner has authorized consecutive implementation, the specified
local and paid provider checks, a commit and push after each milestone, and final
integration through main into agents to deploy Heroku valerian. Record each
milestone's evidence before proceeding. Physical listening/camera/robot acceptance
remains an operator check.

## Objective and evidence

Make the persisted coordination lifecycle, conversational explanation and delivered
response agree. Preserve explicit state-machine authority, the shared Valerian
persona, ordinary conversation, sensor validation and existing client contracts.

The 2026-10-28 trial established these concrete problems:

- Preparation completed, but Ready did not clearly communicate the remaining
  acceptance step. Further hand signs could not activate the task.
- Changing a joke to a dry one-liner replaced the hand-sign task with a
  one-contribution dialogue task. Starting it immediately exhausted its budget.
- Live produced task content before acceptance and repeated it after activation.
- Unfinished phrases such as "A dry" / "one-liner" were admitted as separate
  complete user turns and produced separate agreement revisions.
- Reflection began when the final contribution was authorized, before its speech
  had finished. Budget exhaustion, delivery and successful outcome were conflated
  in the visible lifecycle.
- Returning to the beginning was interpreted as STOP. The reply claimed
  Conversation while the backend remained in Reflection. "Start all over again"
  also claimed a fresh start without a transition.
- STOP changed an already finished task's outcome to CANCELLED.
- Three assistant captures were incomplete with audio_coverage_gap. A separate
  context-delivery warning coincided with operator Stop. These are diagnostic
  findings, not proof of physical audio loss or of the cause of the state errors.

The supplied export ends at 15:08:09 Zurich time; the later return/restart requests
are confirmed by database history, not by that export. Private snapshots under
`target/coordination-trial-20261028` are local evidence and must not be committed.
Create minimal synthetic regression fixtures rather than committing transcripts,
credentials or production identifiers. The deployed baseline is agents `f76dda7`
(Heroku v157); shared main is `38b32d8`. Recheck these before implementation.

## Architecture and working boundaries

- Extend Coordination actions, routing, ordinary Storage and policy facts. Do not
  add arbitrary state jumps, a second task engine or prompt-owned lifecycle.
- State awareness is an authoritative description of current state, initial state,
  task status, supported controls and their prerequisites. It grants no permission
  to skip agreement, preparation or execution guards.
- Returning to Conversation preserves history and earlier completed outcomes.
  It is a conversational lifecycle operation, not Agent.reset(), which clears
  history. Restarting the current task is a different explicit operation.
- Preserve Text/Continuous contracts and Generic/Social Space/Gala semantics.
  Shared capture or delivery changes require neighboring regressions; changes to
  Coordination policy behavior should be opt-in or definition-owned.
- Existing saved agents are not silently rewritten. Each milestone must state
  whether a fresh instance is required and document any additive schema change.
- Shared implementation follows feature/gptlive -> main -> agents when publication
  is authorized. Bring current main into the feature work first; preserve the
  deployment-only persona/embodiment and application catalog on agents.

## Verification levels and release gates

**Unit tests:** deterministic rules, controlled clocks and provider outputs;
success, representative rejection, stale/duplicate evidence and changed branches.

**Local smoke/integration:** real scoped application services, persistence, reload
and relevant HTTP/SSE/browser paths, with mocked external providers and synthetic
sensor data. Use the existing acceptance runner's random local MySQL schema and
restricted account. The development/Heroku database is never a test target.

**Paid OpenAI smoke:** the real local application and disposable database, real
text inference and/or the actual Live session as relevant, synthetic spoken input
and deterministic sensor events. Check both persisted control and actual captured
responses. Establish finite run duration, request/usage limits and cleanup before
execution; keep unsuccessful attempts in the evidence record. A timeout,
contradiction or premature task response fails the scenario. Do not rerun until a
pass without documenting the failure and the reason for another attempt.

Unit and integration tests enforce deterministic contracts. Provider smoke tests
provide bounded behavioral evidence, not a guarantee about every future phrasing.
Captions, received media and playback acknowledgements do not prove physical
audibility, camera accuracy or robot motion; retain a final operator check.

## A. Evidence and test infrastructure

### CR-01 — Reproduce the trial and extend the acceptance harness

**Components:** `tests/gptlive/run_acceptance.py`, `core_backend_journey.mjs`,
Coordination unit/integration fixtures and test-only provider probes.

**Deliverable:** add Coordination selection and reusable scenarios for sensor
tasks, preparation, revision, dialogue tasks and lifecycle commands. Record the
requested operation, agreement/run revision, actual state, response identity,
transcript and evidence boundary in test artifacts. Retain content-free production
telemetry; full synthetic provider requests/results belong only in local artifacts.

**Acceptance:** reproduce unclear acceptance, accidental sensor-to-dialogue
revision, premature/duplicate speech, fragmented input, early Reflection and false
return/restart confirmation as independently attributable regression cases.
Known failures are recorded as failing baseline behavior, not successful acceptance.

**Checks:** runner unit tests verify local-only database ownership, bounded runs
and cleanup; a local create/converse/reload smoke succeeds; one paid Coordination
creation/identity/conversation smoke establishes provider connectivity and the
artifact pipeline. Later milestones turn their respective baseline failures green.

## B. Coordination domain and lifecycle

### CR-02 — State-aware lifecycle commands and truthful control replies

**Components:** `CoordinationAgent`, `CoordinationUpdateAction`,
`CoordinationMemory`, `CoordinationPolicy`, existing routing and policy context.

**Deliverable:** expose current/initial state, meaningful next actions and
prerequisites through the existing ordinary/Live policy facts. Add an explicit
return-to-conversation operation. Build bounded facts from already resolved
runtime state; state inspection requires no extra model call or database polling.
Separate explicit restart of the same task from
returning to general conversation; ask one clarification for ambiguous "start over".
Restart creates a fresh execution identity/budget and applies the current
preparation and first-action requirements. Returning clears pending task authority,
draft/cue/response work and fences late results while retaining history and prior
completed outcomes. Repeated STOP/return is idempotent; STOP after completion must
not reclassify the old outcome as cancellation.

**Acceptance:** "Go back to the beginning state" actually reaches Conversation
and reloads there. "Where are we?" agrees with persisted control. A request to
enter Execution cannot bypass prerequisites. Ordinary control confirmations are
derived from the committed result, including an honest rejection when applicable.

**Checks:** unit matrix across conversation, draft, preparation, ready, running,
paused and reflection; late-result, repeated-command and outcome-preservation
cases, plus full Live instruction/fact byte-budget checks. Extend
`CoordinationAgentIntegrationTest` for real scoped requests,
history-preserving return/restart and reload. Paid text-provider smoke covers
state questions, explicit return and restart wording; Live confirmation is also
checked in CR-06 and the final combined journey.

### CR-03 — Unambiguous preparation, acceptance and activation

**Components:** Coordination preparation/update actions and policies,
`CoreTaskVoice`, task-status projection and `public/performance/activity.js`.

**Deliverable:** describe Ready consistently as "Awaiting your acceptance".
The final recognition check explains that the task is not active, explicitly
asks whether to start, and says that an ON_CUE task then needs a fresh sign.
Honor an already explicit start request for unchanged, fully specified terms when
all prerequisites are satisfied; avoid an unnecessary second confirmation.
Preparation evidence alone, a hypothetical request or a casual acknowledgement
never authorizes execution. This milestone does not add implicit deferred consent
across later agreement revisions or preparation.

**Acceptance:** demonstration -> acceptance request -> explicit acceptance ->
armed/waiting -> fresh cue -> one action is visibly and durably distinguishable.
No action occurs during preparation or while awaiting acceptance. A clear start
request with all prerequisites already satisfied activates once.

**Checks:** unit tests for incomplete preparation, stale/duplicate signs, current
invitation/short replies and explicit authorization; local DB smoke with reload
between preparation and acceptance; footer unit test and one focused browser
check. Paid Live smoke verifies an explicit acceptance invitation (a direct question
or an instruction such as "say start to accept"), truthful waiting
guidance and absence of task content before authorization.

### CR-04 — Revisions preserve unchanged agreement terms

**Components:** `CoordinationAgreement`, `CoordinationUpdateAction`,
`CoordinationMemory`, model request/result validation and agreement facts.

**Deliverable:** bind changes to the current agreement revision and distinguish
editing an existing activity from replacing it. A style/content edit preserves
trigger modality/condition, timing, limits, lifecycle, roles and preparation terms
unless their change was requested. Validate the proposed difference before
mutation; unexpected material changes require clarification. A replacement is
allowed when explicitly requested and must be explained before acceptance.
Revisions still suspend execution and require acceptance; preparation freshness
and invalidation remain explicit and cannot be bypassed by carrying old evidence.

**Acceptance:** "Make it a dry one-liner" changes the response content while the
rock trigger, ON_CUE timing and one-action limit remain. It does not turn into a
dialogue task. Explicitly changing to a dialogue task works as a deliberate new
agreement; old results cannot mutate the revised agreement.

**Checks:** unit tests for permitted edits, unexpected semantic changes, explicit
replacement, malformed output and stale revision; local DB revise/accept/reload
smoke. Paid text inference and Live happy paths assert the stored agreement fields
and the spoken explanation, including preservation of the sensor trigger.

## C. Shared speech/application pipeline

### CR-05 — Prevent fragmented speech from causing premature task updates

**Components:** `LiveTranscriptSegmenter`, `LiveTranscriptCaptureService`,
user-silence policy/configuration, transcript ingress and existing capture tests.

**Deliverable:** reproduce the observed split phrases with controlled fragment
arrival and PCM activity before choosing the correction. Use existing bounded
capture/turn-completion controls; preserve raw receipts, ordering and duplicate
claims. Configure Coordination appropriately where policy settings suffice.
Incomplete/ambiguous input must not commit a lifecycle change merely because a
short partial phrase was admitted. Do not fix this by indiscriminately joining
distinct turns, waiting indefinitely or hiding inputs from history.

**Acceptance:** the controlled "A dry ... one-liner" and "tell me a ... joke"
fixtures produce one intended update for one turn, or an explicit clarification
when the evidence cannot establish completeness. Truly separate turns remain
separate; interruption, late fragments, Stop and revocation keep their guarantees.

**Checks:** fake-clock/audio unit cases around the configured silence and lateness
boundaries; real ingress/local DB smoke asserts one committed operation and
durable receipts; paid Live smoke uses synthetic phrases with reproducible pauses.
Record latency impact and any remaining provider/acoustic uncertainty. Failure of
the exact baseline reproduction must be resolved or explicitly remain open.

### CR-06 — Keep speech aligned with committed task authority

**Components:** Coordination policy/update/cue actions, `CoreTaskVoice`,
`LiveActivityContext`, Live context delivery and response association.

**Deliverable:** distinguish task specification, acceptance, authorized contribution
and ordinary conversation in both inference and Live guidance. Use one identified,
revision-bound speaking occasion for each committed task response. State-changing
confirmations follow committed control; proposal edits do not themselves request
the future task content. Retain genuine ordinary questions, persona and requested
capability examples without treating them as task activation.

**Acceptance:** no joke is delivered merely because a joke task is being described
or revised. After acceptance and the qualifying cue, the task response occurs once.
No duplicate arises from repeated context refresh, harmless acknowledgements or
reconnect. "Back in Conversation" matches the committed state in Text and Live.

**Checks:** unit tests for speaking identity, revision/epoch invalidation, delayed
model results and duplicate delivery; local Live/scoped smoke checks persisted
control and publications together. Paid Live journeys assert both the expected
response and absence of premature/duplicate task content in bounded observation
windows. Prompt changes alone are not evidence of enforcement: actual violating
speech fails acceptance even if backend counters remain correct.

### CR-07 — Separate final contribution, delivery and reflection

**Components:** Coordination completion/routing, `TaskResponse`, the existing
`LivePlaybackCue` contract, capture hooks and scoped playback acknowledgement.

**Deliverable:** stop further task actions as soon as the action budget is spent,
but keep a final Live contribution in an explicit finishing/delivery-pending state.
Reuse current response association and playback-cue evidence before entering
Reflection. Silence-only/gesture-only contributions must not wait for nonexistent
speech. Timeouts, incomplete capture, interrupted/muted playback, Stop and lost
ownership end in a bounded, honest recovery state without replaying uncertain work.

**Mode contract:** in Live, wait for the associated response and existing client
playback-completion evidence. In Text, canonical response publication is the
observable delivery boundary. Continuous retains its existing canonical publication
and separate client playback status; neither backend completion nor that status
claims physical audibility. This milestone does not add a second TTS transport or
silently reinterpret the existing Continuous API as a playback guarantee.

**Acceptance:** a one-contribution Live task has a visible finishing phase and does
not enter Reflection while its final response is still pending. Outcome values
continue distinguishing budget exhaustion, user-reported success, cancellation,
completion cue and uncertain delivery. Captions alone do not establish playback.

**Checks:** controlled-clock unit tests for final spoken/silent contributions,
duplicate/stale acknowledgements, timeout and interruption; local DB reload while
finishing plus a browser smoke with delayed playback acknowledgement. Paid Live
smoke verifies state/response/cue ordering without equating it to hardware audibility.

## D. Diagnostics and cockpit feedback

### CR-08 — Explain lifecycle progress and classify capture/teardown evidence

**Components:** existing activity journal/monitor SSE, capture/context diagnostics,
task-status projection, Valerian activity footer and telemetry export.

**Deliverable:** expose awaiting acceptance, armed/waiting, finishing, reflection
and uncertain delivery consistently. Correlate control operations with source
events, agreement/run revisions, before/after state and explicit outcome where
available; remove avoidable "unpersisted" gaps in committed operation evidence.
Keep journals bounded and content-free, with truncation/coverage visible.
Reproduce genuine and apparent audio_coverage_gap cases before changing capture
classification. Distinguish expected context-delivery teardown after operator Stop
from failure while the session remains active; retain underlying diagnostic facts.

**Acceptance:** an exported timeline explains why a task waited, changed state or
ended. A deliberate Stop is not presented as an unexplained mid-task failure.
Actual gaps remain uncertain and never establish successful delivery. The three
observed capture gaps receive an evidence-backed classification or an explicit
unresolved diagnosis, not an unsupported claim that audio clipping is fixed.

**Checks:** Java/Node unit cases for correlation, privacy, bounds and stop/error
ordering; local HTTP/SSE smoke plus focused Playwright footer/export checks. Inspect
the paid CR-06/07 artifacts rather than paying for a redundant identical journey.
Physical missing-word diagnosis can use the separate
`PLAN_CUTOFFINSPECTION.md`; that recorder project is not implicitly included here.

## E. Combined acceptance and handoff

### CR-09 — Verify complete journeys and document rollout

**Components:** Coordination and neighboring regression suites, acceptance runner,
operator handoff, README, context/current status and results documentation.

**Deliverable:** run three independent, bounded real-provider journeys against the
local application and disposable MySQL, with clean instances:

1. Rock -> recognition preparation -> explicit acceptance -> one joke -> confirmed
   delivery -> reflection -> change to a dry one-liner while retaining the rock
   trigger -> renewed acceptance -> fresh rock -> one revised response.
2. Scissor -> joke plus current facial-expression estimate -> completion -> return
   to Conversation -> query the actual state -> configure another task. Include an
   ordinary Text variant for the false-confirmation regression.
3. One-contribution dialogue task -> finishing/reflection -> explicit same-task
   restart -> new execution -> return to Conversation. Check ambiguous "start over"
   clarification and preservation of the earlier transcript/outcome.

The synthetic sensor events and spoken inputs are controlled; backend inference
and Live speech use the real OpenAI APIs. Check state, agreement fields, budget,
response identity, actual wording and duplicate/early-output absence together.

**Checks:** all new focused suites pass; run broader Java/Node regression because
shared capture, task and Live delivery boundaries changed. Run applicable local
HTTP/SSE and browser journeys for Coordination, Generic/Social Space and existing
playback consumers. On agents integration, retain Gala/catalog and both embodiment
modes. Preserve all failed provider attempts and any remaining limits.

**Documentation:** maintain `COORDINATION_RESULTS.md` with each milestone's actual
commands/evidence; update README, `.agents/CONTEXT.MD`, PROJECT current status and
audit, and a Coordination operator journey. State saved-instance/schema requirements.
Published deployment, when separately authorized, follows feature -> main -> agents
and verifies CI/release, health, catalog, additive schema and preserved saved agents.
Finish with one manual listening/camera/robot trial; automated smoke evidence is not
a substitute for it.

## Sequence and completion rule

Implement CR-01 through CR-09 in order. Domain controls precede speech changes;
capture correctness precedes combined Live acceptance; final-delivery handling
reuses the identity/control work from CR-06. Each milestone reports its changed
behavior, passing checks, failed attempts and remaining limits before review.
No milestone is complete merely because it compiles or its model stub returns the
desired operation. No release is accepted while the required combined journeys
still exhibit the observed workflow contradictions.
