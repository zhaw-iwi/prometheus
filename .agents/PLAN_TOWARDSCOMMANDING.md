# Towards reliable direct commands and current task speech

Prepared: 2026-11-19. Status: **TWC-01–05 complete; physical operator checklist NOT RUN**.
Development branch: `main`. Deployment branch: `agents`.

This roadmap covers three requested improvements: reliable direct requests for
supported gestures and hand signs, cancellation of obsolete task announcements,
and distinguishable Valerian instance labels. The owner has authorized sequential
implementation, committing and pushing each completed milestone before continuing.
Results: [TOWARDSCOMMANDING_RESULTS.md](TOWARDSCOMMANDING_RESULTS.md). The owner
additionally authorized $40/eight provider attempts, $10/two retests and one
further HP-03 attempt capped at $5 on November 19. All reservations are consumed.
Deployment still requires a separate instruction.

## Starting evidence

- Output support already exists in canonical `BehaviourPlan` channels:
  `motion.handSign` and `nonVerbal.gesture`. `LiveMultimodal` has explicit
  expressive-request guidance. `DecorativeGestures` exempts explicitly requested
  gestures from decorative spacing, but its request detector only grants that
  exemption; it does not authorize or interpret a command.
- The reviewed November 19 Heroku interaction produced a backend rock-sign plan
  while native speech denied being able to make signs. A generated plan alone
  does not establish that the client rendered it or a physical robot executed it.
- An older proposal announcement was sent through the context bridge after the
  backend accepted activation. The corresponding spoken proposal had already
  begun before activation and continued afterward. Both queued and already-sent
  announcements therefore matter. Current sensory supersession does not cover
  all task narration.
- Valerian's instance selector displays the name even when several instances
  have that same name. The full instance ID is already available to the client.
- The user reported continuous audio in that interaction. Fragmented stored
  assistant text is a separate known issue, not evidence of audible cutoffs.

Use synthetic reproductions of these cases. Do not commit the user's private
telemetry, database extracts, raw provider captures, credentials or access codes.

## Scope and working contracts

Read `.agents/CODEX.md`, `.agents/CONTEXT.MD`, the current-status portion of
`PROJECT.md`, and the relevant implementation/tests before each milestone.
Work in the existing checkout, preserve unrelated changes, and use current
session instructions for commit/push authority. Once implementation is authorized,
the requested cadence is one completed milestone, its evidence/documentation,
then commit and push before the next milestone. A new merge to `agents` and a
Heroku deployment require deployment authorization; the previous release's
authorization does not establish a new deployment instruction.

### Direct action semantics

The initial command vocabulary is deliberately finite:

- Hand signs: `rock`, `scissor`, `paper`; accept ordinary aliases such as
  "scissors" and normalize to the canonical value.
- Gestures: `OPEN_QUESTION`, `EXPLAIN`, `UNCERTAIN`, `ACKNOWLEDGE`, `POLITE`.
  "Question gesture" maps to `OPEN_QUESTION`. `NONE` is absence of a gesture,
  not a successful demonstration.
- One requested action per command. Repeated commands on distinct user turns
  can each execute once; duplicate delivery of the same turn cannot execute again.

| Example | Required interpretation and outcome |
| --- | --- |
| "Which signs can you make?" | Capability answer; no demonstration merely because an output is mentioned. |
| "Make a rock sign." / "Can you show a rock sign?" | Immediate one-shot request; one canonical rock plan. |
| "Show me a question gesture." | One `OPEN_QUESTION` plan. |
| "Can you do a hand sign?" | A demonstration request; choose one supported sign and keep any acknowledgement consistent with that choice. |
| "Show paper, without speaking." | One paper plan; no speech authored or deliberately requested for this action. Native silence also needs provider acceptance. |
| "Don't make a rock sign." | No rock command. |
| "What does 'make a rock sign' mean?" | Explanation; quoted text does not authorize an action. |
| "Whenever I show paper, make rock." | Existing task proposal/acceptance path; no immediate demonstration or implicit activation. |
| "Walk over here." / an unsupported gesture | Explain the limit and offer a supported alternative; no invented output. |
| "Show that gesture." without a clear referent | Clarify; do not guess from unrelated sensor evidence. |
| A sequence or indefinite repetition request | Clarify supported scope or use an existing bounded task where applicable; do not silently truncate into one action. |

Use a shared, opt-in interpretation/validation mechanism for Coordination,
Live Multimodal and the two Generic Multimodal variants. Reuse their existing
inference and policy boundaries where possible; do not add an unconditional
second inference to every conversational turn. Agent profiles remain authoritative:
sharing the mechanism must not grant output capabilities to unrelated agents.

An unrelated one-shot demonstration does not create, revise, accept, finish or
spend the contribution budget of a Coordination task. An utterance that actually
answers the agreed task or changes its rules still belongs to existing task
handling. Read-only capability questions remain available during a task. An
unrelated demonstration can execute between task outputs if it does not conflict
with the current task; a conflict requires an explanation and a choice to pause
or change the task, without changing it automatically. Do not overlap a pending
task output or silently queue a demonstration for surprise execution later.

### Invariants and exclusions

- Preserve explicit Agent/State/Transition authority, canonical Event and
  BehaviourPlan contracts, scoped access, persisted profiles and delivery identity.
- Preserve preparation, consent, fresh cues, finite budgets and conservative
  task-delivery confirmation. Cancellation is not task completion or a refund
  of an already consumed contribution. Uncertain work is never automatically replayed.
- Text/Continuous retain canonical backend speech and their current playback
  ordering/microphone gating. Live retains independent native speech with no
  second ordinary TTS reply or approval-before-audio pipeline.
- Distinguish supported output, accepted request, published plan, observed client
  rendering and physical execution. User-facing speech must not confuse them.
- Preserve decorative gesture spacing for spontaneous gestures; explicit actions
  bypass that spacing only after their own validation.
- No arbitrary code, locomotion, device/settings control, new gestures, timed
  action sequences, general task scheduler or physical actuator protocol.
- No editable nicknames or database migration for labels in this roadmap.
- Transcript regrouping, microphone/echo tuning, broad persona changes, unrelated
  agent behavior and HRI participant flows remain outside scope.

## Milestone overview

| Milestone | User-visible outcome | Dependencies | Status |
| --- | --- | --- | --- |
| TWC-01 | Task speech stops requesting obsolete decisions after state advances. | Existing GLF baseline | COMPLETE; HP-02 passed |
| TWC-02 | Supported direct requests produce one validated action through the shared backend path. | TWC-01 | COMPLETE; HP-01/03 passed |
| TWC-03 | Native Live speech and direct actions agree; Text/Continuous semantics remain intact. | TWC-01, TWC-02 | COMPLETE; provider and mode parity passed |
| TWC-04 | Same-type Valerian instances have stable, distinguishable labels. | Independent; scheduled after TWC-03 | COMPLETE |
| TWC-05 | Integrated local and real-provider journeys demonstrate the requested outcomes. | TWC-01–04; funded provider preflight | COMPLETE; HP-01–04 passed; eleven attempts/$55 reserved |

TWC-01–04 can establish deterministic software acceptance without provider spend.
Their required real-provider acceptance is now recorded under TWC-05. Failed
attempts remain in the results; final passing follow-ups do not erase them.

## TWC-01 — Invalidate obsolete task announcements

**Outcome:** accepting a ready task invalidates its old readiness invitation.
Equivalent protection applies to pause, stop, replacement, restart and return to
conversation. Current task facts and valid task contributions remain deliverable.

Work:

1. Associate task announcements with their source occasion, task/run identity,
   relevant agreement revision, phase/preconditions and Live owner/session epoch.
   Distinguish supersedable invitations/status narration from committed task
   contributions and their delivery evidence. A generic context revision alone
   must not invalidate legitimate work when a sensor update arrives.
2. Revalidate immediately before every queued speaking command and after each
   provider acknowledgement wait. React to committed state changes, not tentative
   transcript fragments. Remove obsolete announcements without dropping newer
   state facts, replaying an old invitation, or blocking unrelated conversation.
3. Inspect the actual provider transport for cancellation/withdrawal support.
   Withdraw obsolete instructions already sent. Use narrowly scoped cancellation
   if supported; otherwise deliver one concise current-state correction when
   needed. Do not promise that already-heard words can be retracted or introduce
   a blanket audio buffer. Retain this distinction in diagnostics and acceptance.
4. Keep late acknowledgements/captures/playback observations bound to their
   original identities. They cannot reinstate an obsolete invitation or confirm
   delivery of a different action. Avoid repeated corrective announcements.
5. Add bounded, content-free diagnostics identifying source, superseding state,
   cancellation stage and outcome. Reuse existing telemetry infrastructure.

Required verification:

- **Unit:** fixed-clock/latch-controlled race tests for START while an earlier
  append awaits acknowledgement; invalidation immediately before send; unrelated
  sensory updates; rapid start/pause/stop; replacement/restart; disconnect/epoch
  changes; late acknowledgement; and a legitimate final contribution that must
  survive its transition into FINISHING. Assert exact sent/suppressed identities,
  not only eventual phase. Extend `LiveContextDeliveryUnitTest` and adjacent tests.
- **Local MySQL smoke:** persist a ready proposal, hold provider acknowledgement,
  accept through the scoped API, reload the agent and release the hold. Assert
  EXECUTION with zero actions, no later old proposal send, correct new-state
  announcement and unchanged agreement. Add a stop/reconnect case without replay.
- **Playwright real-app:** drive acceptance while the old invitation is queued;
  assert current state/history and the synthetic provider's outbound command log.
  Cover already-started speech separately and verify withdrawal/correction does
  not stop unrelated native audio or fabricate task playback confirmation.

**Exit:** all race cases pass deterministically; the synthetic reproduction cannot
send the old proposal after activation. Already-started speech has an explicit,
tested handling policy. Real-provider conversational effectiveness is still pending.

## TWC-02 — Make one-shot output requests a shared backend contract

**Outcome:** the command examples above produce the right single action, or a
clear clarification/unsupported result, without accidental task changes.

Work:

1. Define a small structured interpretation/result for capability questions,
   one-shot actions, task-related requests, ambiguity and unsupported requests.
   Specify canonical action parameters and bounded validation. Do not use a
   keyword match or `DecorativeGestures.requested` as execution authority.
2. Reuse current inference/policy boundaries, then validate vocabulary, declared
   modality, current task/output state and owner before publishing through the
   ordinary BehaviourPlan path. Keep interpretation separate from authorization.
3. Correlate accepted commands with the admitted user event and published output.
   Reuse existing ingress/event identity and persistence; introduce new durable
   metadata only where necessary to survive reload/races. Distinct new requests
   may repeat an action; transport retries, SSE replay and reconnect may not.
4. Integrate the shared mechanism into the four scoped agent definitions without
   copying separate command grammars. Preserve existing task and decorative paths.
   Remove prompt conflicts that restrict a requested sign to accompanying spoken
   words or a game. Explicit silent demonstrations must remain possible.
   Check existing saved instances as well as newly created ones: derive behavior
   from their persisted profiles/policies, without silently resetting agreements
   or rewriting graphs/history. Document any instance that cannot adopt the
   behavior without a separate compatibility decision.
5. Resolve active-task conflicts as specified above. Recheck Stop/reset/owner
   changes after inference and before publication; stale work cannot execute.

Required verification:

- **Unit:** table-driven semantic fixtures for every example above, sign aliases,
  all five gestures, missing/unknown profile modalities, malformed model output,
  repeated distinct turns, duplicate ingress, stop during inference and task
  conflicts. Scripted model results prove validation/routing, not natural-language
  model accuracy; retain that limitation in results.
- **Unit:** an accepted repeat bypasses decorative cooldown; a capability answer
  or rejected command does not. Inspect task agreement/revision/action counters
  before and after standalone demonstrations in conversation, ready, executing,
  paused, finishing and uncertain states.
- **Local MySQL smoke:** scoped creation -> user request -> persisted BehaviourPlan
  -> repository reload -> duplicate retry. Exactly one actionable event for the
  original turn; a fresh turn can produce a second. Check cross-agent/access-code
  isolation and that reset/ownership changes fence old work. Exercise both policy
  families and a saved pre-change instance fixture, extending
  `CoreGesturePersistenceIntegrationTest` and Coordination tests.
- **Playwright real-app:** submit a Text request, observe the canonical SSE output,
  and verify the hand sign/gesture in the cockpit and existing Robot embodiment.
  Repeat deliberately, then reconnect; distinguish new action animation from
  harmless hydration of the last pose. Unsupported requests produce no action.

**Exit:** the shared contract works through persisted production paths across the
scoped definitions, with no parallel output engine and no unintended task mutation.

## TWC-03 — Align Live speech, commands and capability answers

**Outcome:** a supported direct request no longer produces a backend action paired
with a spoken denial. Ordinary native conversation retains its independent path.

Work:

1. Supply consistent, human-readable output capabilities and command guidance to
   native voice and backend interpretation from the same declared capabilities.
   Distinguish capability knowledge from a particular command's acceptance/result.
2. Allow a short natural acknowledgement or requested silence. Unsupported and
   rejected actions must not receive a false success claim. Where command-specific
   facts are needed, associate a bounded result with the correct user turn; do not
   delay every ordinary reply behind backend inference or create duplicate narration.
3. Preserve canonical Text/Continuous speech, silent output, event-ID ordering,
   microphone gating and mode-switch ownership. Ensure native fragments, partial
   captures and repeated callbacks cannot execute a command twice.
4. Apply TWC-01 supersession to obsolete command-related follow-ups where relevant.
   A late result from a stopped/reset/previous session cannot narrate or perform
   the action in the new session.

Required verification:

- **Unit:** capability projection agrees with allowed command values; positive,
  unsupported and silent responses; result-to-turn association; duplicate/partial
  transcript handling; stale result after Stop; absence of ordinary Live TTS.
- **Local MySQL smoke:** one logical request through Text, finalized Continuous
  transcription and native Live transcript ingress produces equivalent action
  semantics. Check persisted action identity and unchanged task budget. Only the
  transport/inference are scripted; controllers, storage and runtime are real.
- **Playwright real-app:** Continuous -> Live -> Continuous on the same instance;
  verify one visible action per distinct request, no reconnect replay, correct
  speech ownership and Stop cleanup. Retain the existing blocked-backend test
  showing ordinary native audio can play before backend inference completes.
- **Playwright visual:** capture rock, paper, scissors and the question gesture in
  cockpit/Robot views. Freeze or sample animation at deterministic points; assert
  actual pose/output state alongside screenshots rather than only a text badge.
  Review light/dark and narrow layouts, using the existing embodiment fixtures.

**Exit:** deterministic mode parity and independence pass; no competing speech
route is introduced. Actual model interpretation, spoken consistency and silence
remain explicit real-provider acceptance items for TWC-05.

## TWC-04 — Give Valerian instances stable labels

**Outcome:** multiple instances with the same name can be distinguished before
selection and remain identifiable after reload.

Work:

1. Preserve the current displayed name/persona formatting and append a short ID,
   for example `Coordination · a3ca2f38`. Always include the suffix so labels do
   not change merely because a duplicate name appears or disappears.
2. Use at least eight ID characters, extending colliding prefixes among the
   current scoped instances until unique. Keep the full ID available in instance
   details and accessible explanatory text; retain the full ID as the option value.
3. Use the same formatting where Valerian identifies the selected instance.
   Preserve selection, creation, reconnect, deletion and ordering behavior. Treat
   names as text. Do not rename the agent, change persona instructions or add a
   nickname field/database migration.

Required verification:

- **Unit:** unique prefixes, deliberately colliding prefixes, missing names,
  Unicode/long names, escaping and stable formatting across list reordering.
- **Local MySQL + Playwright smoke:** create three instances of the same type;
  select each and verify the full-ID request target and selected instance state;
  reload, reconnect and delete one, preserving the remaining identities. Use a
  scripted provider or no inference; this milestone needs no paid call.
- **Visual:** review the selector and selected-instance identity at desktop and
  narrow widths in light/dark themes, including long names. Ensure the identifying
  suffix remains usable and full identity can be reached without relying solely
  on hover. Native select popups need DOM assertions as well as screenshots.

**Exit:** same-name instances are visibly distinguishable; IDs, agent names,
personas and stored behavior remain unchanged.

## TWC-05 — Integrated acceptance and bounded OpenAI Live happy paths

**Outcome:** the requested user journeys pass against the integrated application,
with separate evidence for deterministic software, real providers, visual output
and physical operator observations.

### Harness and spending preflight

Reuse `tests/gptlive/run_acceptance.py`, the disposable loopback-MySQL setup,
`LiveSmokeConfiguration`, native browser fixtures, budget reservation logic and
the current repair journey's audio/telemetry instrumentation. Add a clearly named
commanding scenario/runner selection and its tests. The `commanding-*` options
now implement this selection. Extend the explicit Playwright spec list when adding new real-app specs.
Do not revive the retired approval/committed-Speech Coordination harness.

The preceding GPT-Live repair plan reached its **12-attempt limit and $49.50
cumulative reservations**. Reservations are conservative envelopes, not invoices.
Its plan explicitly permits no further paid trial. Preserve that plan and ledger;
do not reset them or treat the nominal $0.50 difference as a new test allowance.

The owner explicitly authorized this additional envelope on November 19:
**at most $40 reserved across at most eight attempts, $5 reserved before each
attempt**, including failed attempts. Retain a separate ledger for this additional
authorization. The old repair ledger remains unchanged. The technical cost/bounds
preflight below must still pass before a paid call.

Execution update: all eight reservations are now consumed, including failures.
HP-01 passed on Coordination. HP-02 needs a fresh-cue harness retest after the
confirmed arming boundary; HP-03 exposed malformed-command error propagation,
now fixed and verified locally. HP-04 passed an earlier corrected onset analysis
but needs its final matched baseline after guidance changes. No further paid
attempt is authorized by this envelope; never reset or loosen its ledger.
Detailed failures, fixes, timing-method correction and remaining visual evidence
are in the results record.

The owner subsequently approved **$10 total for two retests**, replacing the
earlier $5 question. The separate `commanding-retests-plan.json` and
`commanding-retests-budget.json` allow one HP-02 and one HP-03 reservation at $5
each. Select them with `--commanding-retests`; neither exhausted ledger changes.
Both reservations are now consumed: HP-02 passed; HP-03 failed because the backend
repeated a question gesture for an unsupported walking request. Native speech
correctly declined. Both requested poses and the matched ordinary timing comparison
passed. The latest interpretation fix has local coverage, but provider effectiveness
was pending at that point. The owner then authorized one further HP-03 attempt
capped at $5 through `commanding-hp3-retest-plan.json` and its separate ledger,
selected with `--commanding-hp3-retest`. That final attempt passed supported outputs,
explicit silence flags and unsupported rejection, with complete visual review and
passing matched ordinary timing. Eleven/$55 reservations across all three envelopes
are consumed. No further paid attempt is authorized; see the results record.

Before enabling the new paid runner:

- Verify configured model availability, official prices and conservative worst-case
  cost under the limits. Pin the chosen models/configuration in the run manifest;
  reject an unreviewed model or a bound exceeding the per-attempt reserve.
- Bound each attempt initially to one provider session, six session minutes,
  twelve user turns, 36 backend requests, 80,000 request bytes and 2,048 output
  tokens per request, with a 390-second overall watchdog. Split longer journeys
  instead of silently raising caps. Tighten caps if needed to fit the reserve.
- Account for all billable routes, including stimulus audio generation,
  transcription, synthesis and model-based grading if used. Prefer cached or
  locally recorded synthetic visitor audio; its preparation is not free if a
  provider is used. Native command runs must have zero competing Speech/TTS calls.
- Fail before provider access on missing authorization, exhausted attempts/budget,
  missing bounds, unsupported configuration or an unavailable ledger. Preserve
  reservations across crashes and concurrency; never silently reroll a failure.
- Test the caps, preflight rejection and watchdog cleanup with fake providers.
  Ensure browser/session, JVM and disposable database cleanup also runs on failure.

### Required real-provider journeys

All journeys use the actual scoped Valerian UI, backend interpretation, persisted
local database, SSE and real OpenAI Live transport. Visitor microphone/sensor
stimuli may be synthetic; record that fact. Stubbed model responses or injected
assistant answers do not qualify as provider acceptance.

| Journey | Steps | Required pass evidence |
| --- | --- | --- |
| HP-01: direct demonstrations | On Coordination, ask which signs are supported; request rock; request a question gesture; repeat that gesture in a new turn; request silent paper. | Capability answer is correct; each command has one correlated canonical plan and observed visual output; no task is created; no contradictory denial or execution claim without evidence; silent request produces no associated assistant audio. |
| HP-02: accepted task remains current | Propose one joke after a fresh rock sign; accept promptly; provide a nonmatching cue, then rock; repeat the cue; return to conversation and request a gesture. | Acceptance activates waiting with zero actions; no new obsolete readiness invitation; any already-started invitation is withdrawn/corrected without demanding renewed consent; only the fresh matching cue spends one action; one confirmed contribution, no replay, then a standalone gesture. |
| HP-03: shared capability behavior | On Live Multimodal, ask capability, request scissors and a question gesture, then request one unsupported output. | Supported outputs are correct and visible; unsupported action is not performed; speech agrees with the supported limits; no invented task or actuator control. The two Generic variants additionally require deterministic integration coverage. |
| HP-04: ordinary conversation regression | Use three ordinary turns on Live Multimodal and three on Coordination with matched capture settings and cached stimuli. | Native audio remains independent of backend completion; no second speech route. Compare browser speech-end-to-audio-onset observations with the predeclared baseline and retain raw timing evidence. |

HP-04 may use two separately reserved sessions/attempts because each attempt is
limited to one session. Plan five successful attempts for these four journeys,
leaving up to three bounded diagnostic/retest attempts within the proposed cap.
Reserve before every attempt and investigate each failure before spending again.

Predeclare a meaning rubric before provider runs: ordinary paraphrases are
allowed, but wrong action/sign, wrong role, changed count/condition, repeated
acceptance demands, false capability denials and unsupported completion claims
fail. Inspect the entire relevant captured reply, not a convenient substring.
The assessor must review artifacts; a passing regex or scripted semantic verdict
is insufficient. Retain failed attempts and never relax the rubric to make a run pass.

For HP-04, retain the existing provisional comparison: Coordination median no
more than 1,000 ms and maximum no more than 2,000 ms above its matched baseline
statistics, with three ordinary turns each. No p95 or general performance claim
from this sample. The deterministic blocked-backend test establishes causal
independence; real-provider timing is observational. Separate planned stimulus
time, browser observed audio, server arrival and physical audibility.

### Integrated exit checks

- Run the affected Java, JavaScript and Python suites, real local-MySQL smoke
  journeys, and the new/existing relevant Playwright tests. Text/Continuous direct
  commands must pass through their production ingress and rendering routes;
  static mocks alone do not establish parity.
- Capture and inspect representative successful screenshots as well as failure
  traces. Check gesture/sign rendering, instance labels, current task state,
  light/dark themes, narrow layout and no new browser errors. Review deterministic
  snapshots before accepting baseline changes; never update them automatically
  to conceal a regression.
- Record HP-01–04 with actual request counts, durations, provider/configuration,
  reservations, observed speech meaning, persisted event/turn IDs and visual
  evidence. A lack of complete timing/audio evidence is INCONCLUSIVE, not PASS.
- Add a short operator checklist for real microphone/speaker experience: spoken
  commands, silent gesture, rapid acceptance, Stop during output and picking the
  intended same-type instance. Keep it NOT RUN until actually observed. Browser
  rendering proves a digital embodiment, not a physical robot action.

**Exit:** all mandatory deterministic, persistence, browser/visual and provider
journeys pass on the integrated source revision. Unrun paid acceptance means
software verified/provider pending, not roadmap complete. Report physical status
separately; a hardware trial is not silently implied by software acceptance.

## Verification entry points and evidence

These commands use existing interfaces; extend selections with the new tests as
they are implemented. They are examples to run at the relevant milestones, not
claims that verification has run while drafting this roadmap.

```powershell
# Existing unit regression anchors; no provider calls or database required.
.\mvnw.cmd -q "-Dtest=LiveContextDeliveryUnitTest,AgentCapabilityDescriptionUnitTest,DecorativeGesturesUnitTest,CoordinationTaskContractUnitTest" test
npm.cmd run test:live:unit
npm.cmd run test:embodiment:unit
python -m unittest discover -s tests/gptlive -p 'test_*.py'

# Real local persistence/application with synthetic external providers.
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'CoordinationAgentIntegrationTest,CoreGesturePersistenceIntegrationTest,CapabilityAwarenessSmokeIntegrationTest' --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous'

# Static visual fixtures only; does not substitute for the real-app smoke above.
python tests/playwright/run_static.py tests/playwright/valerian-embodiment.spec.mjs tests/playwright/valerian-lifecycle.spec.mjs
```

Use an ignored local MySQL administration configuration. The runner must verify
loopback access, create a random disposable schema/restricted account, override
all datasource settings and remove only its own resources. Never run integration
tests against `application-valerian.properties` or a production application schema.
Do not invoke the default Playwright server against unchecked application settings.
Do not print credentials or put them in committed command examples.

Keep generated artifacts in ignored `target/towardscommanding-*` or the acceptance
runner's existing per-run directory. During implementation, create
`.agents/TOWARDSCOMMANDING_RESULTS.md` with one row per milestone and columns for
unit, local DB, browser functional, visual review, provider and physical evidence.
Use PASS, FAIL, NOT RUN, INCONCLUSIVE or N/A with reasons. Record exact commands,
source revision/worktree hashes, test counts/skips, artifact locations, first
failure and its resolution. Existing GLF results remain historical evidence.

Before completing each milestone, update relevant README/current-status records,
this roadmap's status and the results file. Report implemented behavior, checks
actually run and remaining acceptance gaps. Do not declare a milestone complete
merely because tests were added or a provider attempt was made.

## Decisions that would require owner input

No additional product decision is needed to begin the deterministic milestones
under the defaults above. Pause only for a material unanticipated choice, such as
expanding command scope, changing active-task authority, requiring a migration,
or sacrificing ordinary native responsiveness to guarantee speech cancellation.
Record the concrete alternatives and recommendation before asking. The known
additional paid-test budget decision was resolved by the owner's explicit $40
authorization, subsequent $10/two-attempt authorization and final $5/one-attempt
HP-03 authorization. All are exhausted; do not exceed their limits or reset their ledgers.
