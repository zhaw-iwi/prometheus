# Coordination Agent implementation and evidence

Date: 2026-10-27. Work starts on `main` at `56a3f05`; no commit, integration or
deployment is part of this milestone.

## Scope and implementation

The owner requested a new `CoordinationAgent.java`, eventually replacing both
Generic variants, with their persona, knowledge, humour and perception behaviour
retained. The new definition is `core.coordination`. Existing definitions, saved
graphs and history remain untouched.

The agent has explicit conversation, clarification, preparation, ready, execution,
paused and reflection states. A bounded validated agreement stores the goal,
human/agent roles, success criterion, outstanding questions, optional recognition
checks and either dialogue execution or the existing `TaskSpec`. Model output is
validated before mutation. A replacement suspends previous execution; acceptance
starts the unchanged complete proposal only after required evidence. Discarding
a replacement preserves an unfinished earlier agreement for explicit resume;
completed agreements cannot be revived this way. Accepted preparation evidence
stays with its accepted agreement while a replacement is being discussed.

Preparation reuses `TaskDecision`'s field/confidence/freshness semantics. It accepts
only observations newer than the current proposal/encounter and records each
required check once. Progress is persisted without inference. The last check
invites acceptance and never executes a task action. These are recognition
demonstrations; they do not certify current sensor availability or a person's
identity. Revisions and new Live conversations invalidate pending preparation.

Sensor execution reuses the existing task guards, behaviour validation, cooldown,
response capture, session fencing and failure recovery. Dialogue tasks have their
own bounded contributions without a fabricated sensor rule. Cancellation, action
limits, completion cues and user-reported completion are recorded separately.
Reflection retains a bounded operation log and a labelled user report alongside
durable event history. Model interpretation and subjective success are not
deterministically proven by these records.

The same scoped APIs, behaviour plans, monitor stream and cockpit are used.
The activity footer distinguishes clarification/preparation from ready acceptance.
The new `CoordinationDecision` adds nullable `decision.coordination_condition`
(`varchar(255)`); other new entity types use inherited mappings. Deploy the current
code and additive schema before creating instances. No automatic migration or
replacement of saved instances occurs.

## Adopted behaviour and limits

- Reuses the full Social Space voice, `ValerianPersona`, `CoreAgentReference` and
  shared humour guidance. SIRA/IWI, Alexandre, Marc, Jasmin and Theresa remain in
  both ordinary and Live policy context, as does the fictional Laureline layer.
- Keeps the full Generic observation/speech/nonverbal/hand-pose profile, capability
  awareness, name-addressed conversation and concise spoken style.
- Reuses stable arrival/departure admission, quiet time, cooldown, source expiry,
  task priority and interruptible Live remarks. Other observations inform direct
  questions and agreed task cues. Generic/Social Space did not automatically
  comment on every facial/weather/hand reading; the new agent preserves that
  distinction instead of enabling unsolicited reactions to every modality.
- Supports the existing optional presence-controlled reception through the new
  `prometheus.core.coordination.reception` setting (default false).
- No scored RPS/guessing activity, arbitrary sequence generation, automatic device
  settings, weather fetching, person identification, locomotion or physical
  completion feedback is added. These require separately scoped executors and
  evidence contracts. Preparation currently supports at most three independent
  observation demonstrations; full prerequisite/arbitration languages remain open.
- Agreement JSON is limited to 3,000 UTF-8 bytes. Dialogue contributions and sensor
  actions are bounded; natural-language intent, requested content and character
  delivery still need real-provider/operator acceptance.

## Verification

Tests use controlled model output; no paid provider or physical-device trial has
been run.

- Full Java regression: **673 tests, zero failures/errors**, using the existing
  `tests/gptlive/run_acceptance.py --java-tests all` runner against an owned local
  MySQL instance, a random schema and a restricted test account. Maven used a
  temporary copy of `pom.xml` whose only change was the output directory
  `target/coordination-regression`, avoiding stale classes from another branch.
  Log: `target/gptlive-acceptance-ddcdd22691/java.log`.
- Final focused rerun: **58 tests, zero failures/errors/skips**, after the
  completed-task discard fence, accepted evidence retention, matched-rule
  explanation, sensor role/constraint projection and UTF-8 boundary check.
  Selection: `CoordinationAgentUnitTest`, `CoordinationAgentIntegrationTest`,
  `AgentDefinitionRegistryUnitTest`, `CoreTaskVoiceUnitTest`,
  `GenericMultimodalTaskUnitTest`, `CoreReceptionUnitTest`,
  `GenericMultimodalTaskIntegrationTest`, `CoreTaskVoicePersistenceIntegrationTest`,
  `SocialSpaceAgentIntegrationTest`.
  Log: `target/gptlive-acceptance-d4b9e1e7fb/java.log`.
- `node --test tests/js/performance/activity.test.mjs`: **8 passed**.
- `.\mvnw.cmd --file .coordination-regression.pom.xml -q -DskipTests package`:
  passed after the final focused rerun. The temporary root POM was removed after
  verification; its exact copy is retained in
  `target/coordination-regression/build-pom.xml` with the build artifacts.
- `git diff --check`: passed. Temporary database schemas/accounts and the owned
  MySQL process were removed/stopped. The existing application database and
  compiled output directories were not modified by cleanup.

The new tests cover partial dialogue agreements, inability to start incomplete
preparation, stale/future/low-confidence/duplicate observations, invalid output
atomicity, pause/resume/completion, discarded revisions, budget exhaustion,
reset, shared knowledge/persona in every state, stable social comments, completed
Live invitation checks, response capture and harmless acknowledgements, Text-to-
Live session binding, explicit autonomous sensor tasks, context byte limits,
scoped creation and persistence/reload. Dialogue and preparation use the real
application/persistence boundary in the integration tests; provider output is mocked.

The initial compile exposed an incorrect EventHistory method name, and the first
test compile exposed a package-private timestamp setter in a fixture; both were
corrected before the passing unit run. The configured application database was
rejected by the local-only acceptance runner. Template local credentials also
failed authentication. Persistence checks therefore use an independently owned
temporary MySQL instance with disposable schemas and restricted accounts; the
configured application database is never a test target.

The first persistence run failed one new session-close assertion because the
fixture wrote to `Agent.getStorage()`'s copied map. It now writes the policy's
persisted Storage and verifies the saved association before session close; the
corrected integration checks pass. Review also found and corrected pending Live
response replacement by polite acknowledgements and accidental revival of a
completed agreement after discarding a replacement. Dedicated assertions cover
both. Automatic review rejected recursive compiled-directory cleanup; isolated
Maven output supplied fresh classes without deleting those directories.

## Remaining acceptance and rollout

No real-provider, classroom, physical audio/camera/robot, production-load or
deployment acceptance is claimed. Natural-language interpretation, conversational
quality, character delivery, sensor acquisition and actual rendered/audible output
remain operator checks. Preparation is deliberately bounded; arbitrary workflows
and the dedicated game executors are not part of this version. The existing
Generic variants remain available until comparative acceptance supports retirement.

To repeat the focused integration checks with normal build output, supply a
local administration properties file to the existing runner, for example:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests CoordinationAgentUnitTest,CoordinationAgentIntegrationTest,CoreTaskVoiceUnitTest,GenericMultimodalTaskIntegrationTest
node --test tests/js/performance/activity.test.mjs
```

The runner creates its own random schema/account; the configured schema must
never be used directly by the integration tests.

## Release integration (2026-10-28)

The owner authorized committing and pushing main, then merging main into agents
and pushing agents to trigger the existing Heroku deployment workflow.
Coordination implementation is committed as `293c987`. Merge `585d399` also retains
upstream main's Live playback-readiness fix `3454fcb`; the only merge conflict was
the current-status list in PROJECT, resolved by retaining both entries.

Main release verification used fresh Maven output in
`target/coordination-release-main`, the existing acceptance runner and the owned
loopback MySQL instance. All **71 Java tests passed, with no failures/errors/skips**:
the previous nine-class focused selection plus
`LiveTranscriptCaptureServiceUnitTest` and `LiveAgentContextRefreshUnitTest`.
Log: `target/gptlive-acceptance-9e10643aae/java.log`. The disposable schema was
removed by the runner. All **8 activity-footer Node tests passed** and packaging
with the isolated build POM passed. No paid provider or physical-device trial
was run. Deployment-branch integration and release checks are recorded separately
after the merge; these main checks do not establish production acceptance.

## CR-01: refinement baseline and harness (2026-10-28)

Consecutive CR-01 through CR-09 implementation and per-milestone publication are
authorized by the owner. Work uses feature/gptlive based on current main; final
integration follows main -> agents. The refinement plan is
`PLAN_COORDINATION_REFINEMENT.md`.

Added bounded Coordination Live/Text selections to the existing disposable-local-
database runner, a synthetic regression manifest and three characterization tests.
Those tests reproduce terminal outcome replacement, sensor-contract loss on a
style revision, and Reflection before final Live capture. They document defects,
not passing acceptance of the intended behavior. The manifest assigns the other
observed contradictions to their acceptance milestones; no production transcript
or identifiers are included. No application behavior or schema changed.

Verification: runner unittest discovery passed **5 tests**. The disposable MySQL
selection CoordinationAgentUnitTest, CoordinationAgentIntegrationTest and
CoordinationRefinementBaselineUnitTest passed **17 tests**, no failures/errors/skips
(`target/gptlive-acceptance-8de3943141/java.log`). The paid selection
`--java-tests none --paid-core coordination` passed its real Live identity/lab and
current-perception conversation (`target/gptlive-acceptance-a85cc3c6a3/journey.json`).
The runner stopped both owned applications and removed their schemas/accounts.
Provider journeys have a 420-second process deadline; the Text journey additionally
bounds itself to 300 seconds and 12 turns. Future scenario names are reserved and
fail explicitly until implemented. This baseline makes no physical-device claim.

## CR-02: authoritative lifecycle controls (2026-10-28)

Added RETURN_TO_CONVERSATION, RESTART and STATUS to Coordination's existing update
protocol. Committed state supplies control replies and current/initial state plus
prerequisites. Return clears task authority and pending response work, retaining
history and up to eight completed run summaries. Restart creates a fresh run UUID
and budget; required preparation is refreshed and cannot be bypassed. Ambiguous
start-over asks which operation is intended. Terminal Stop preserves the previous
outcome. Model results from an earlier revision cannot commit.

Passed **36 Java tests** (CoordinationAgentUnitTest, CoordinationLifecycleUnitTest,
CoordinationRefinementBaselineUnitTest, CoordinationAgentIntegrationTest and
CoreTaskVoiceUnitTest) using disposable MySQL; artifact `gptlive-acceptance-7639ce4869`.
The seven-phase return matrix, terminal idempotence, stale revision, guarded restart
and persisted history/outcomes are covered. Paid `coordination-text` scenario
`lifecycle` passed (`gptlive-acceptance-d8f93fd58d/journey.json`), including provider-
interpreted state query, restart and return phrasings. Both owned schemas/accounts
were removed. New instances are required for updated saved prompts; no schema
change. Live wording and final delivery remain CR-06/07 acceptance work.

## CR-03: acceptance clarity (2026-10-28)

Ready is displayed as awaiting acceptance. Preparation and ready-proposal replies
end with an explicit current acceptance question, after explaining the fresh-cue
requirement. This ordering matters because short assent is associated with the
last completed sentence. An explicitly requested, complete new activity can start
in one turn without preparation; partial terms/checks and casual acknowledgements
cannot bypass consent. Added a one-shot ACT example after provider evidence showed
invalid COMPLETE rules and mismatched duplicate goal fields; validators remain strict.

Passed **27 Java tests**, **8 activity Node tests**, **6 runner tests**, and the
focused desktop footer Playwright check. Java artifacts: `gptlive-acceptance-dbad383537`
and (after prompt correction) `gptlive-acceptance-539304687f`. Browser initial run
passed the enabled fixture but failed because the same title did not exist in
the disabled selection. Added explicit `--browser-mode`; enabled rerun passed
in `gptlive-acceptance-69a9c5a39d`.

Paid Live attempts retained locally:

- `eeaeb50468`: rejected generated proposal; no raw provider artifact yet.
- `232d3c6dd7`: added test-classpath-only provider request/result capture and a
  60-inference limit. The long task request fragmented, briefly revising the earlier
  camera question; final proposal had unequal goal fields and a COMPLETE-only
  ON_CUE task. Rejected correctly. Fragment admission remains CR-05 work.
- `539304687f`: corrected prompt and concise independent input; received input
  audio but no transcript, so no inference occurred. No acceptance claim.
- `86e9b13e17`: startup probe incorrectly waited for an audio event in the browser
  log, which deliberately filters audio events; fixed to use server status.

Each failed attempt stopped its owned application and removed its schema/account.
The harness now starts independent Coordination scenarios without ambient greeting
events and waits for server unmute/context readiness. Synthetic provider artifacts
remain under ignored target; the recorder is absent from production classpaths.

Two further attempts: `f70ebf72e1` reached Ready and asked the valid paraphrase
"Want to accept it and move on?" but the test regex was too narrow; expanded it.
`f7663e0982` passed 27 Java checks, but Live's proposal asked an unnecessary
confidence-tuning question; made ordinary recognition defaults explicit.
Final paid recognition journey **passed** in `gptlive-acceptance-38f18f7f5b`:
preparation -> explicit acceptance question -> start -> captured armed response,
zero actions -> fresh rock -> one dry joke/action. Inspection of its pre-cue
captions found no delivered joke. Fragmented wording and an extraneous failed-update
clarification remain visible and are assigned to CR-05/06, not claimed fixed here.
No additive schema change. New instances receive updated prompts/state labels.

## CR-04: preserve revision terms (2026-10-28)

Content edits compare protected agreement fields before mutation. A dry one-liner
cannot remove sensor rules, change timing/count/session behavior, human commitments,
success criteria or preparation. Unexpected material changes preserve the existing
agreement and request clarification. Explicit REPLACE proposals can change those
terms and still require acceptance. Current revision fencing also rejects late
model results. Updated older tests that intentionally replaced dialogue/sensor
tasks to express that intent through REPLACE rather than an ordinary content edit.

The declared 3,000-byte agreement contract exceeded Storage's varchar(2048).
Widened `storage_entry.entry_value` to varchar(8192), an additive schema change.
A real MySQL test persists/reloads a valid agreement larger than 2,048 characters.
Other consumers retain their existing validation limits; this does not make
unbounded payloads acceptable.

First Java run (`4b898bc192`) caught one remaining old test switching task types
through PROPOSE; corrected its explicit replacement fixture. All **46 Java tests**
then passed (`83fdafb783`), followed by the paid Text `revision` journey in the same
artifact directory: complete original sensor task, edit only response style,
assert protected fields, accept with zero initial actions, then explicitly replace
with a dialogue proposal. The first Live run (`fe8756ba2f`) failed an overly narrow
check requiring fresh-cue guidance in the final preparation sentence alone; the
recorded proposal already explained the fresh cue and the final sentence asked to
start. The check now examines both parts of the current proposal/preparation
dialogue. All owned applications/schemas/accounts from those runs were cleaned up.

Follow-up checks caught and fixed discarding restart preparation reviving a
completed run. Control replies now discard invalid optional nonverbal expression
while still validating speech, motion and the agreement. The provider had returned
the literal enum alternatives as a gaze focus; a dedicated regression covers it.
The final combined selection passed **49 Java tests** in `3538181c80`, including
the one remaining characterization of premature Live reflection (CR-07).

Further paid Live artifacts: `4983c38b0f` asked "Wanna try it?", a valid current
invitation omitted from the test vocabulary; `72ec228444` missed the browser
startup event despite attachment; `161e40e16f` returned the invalid optional gaze
label; `eef23aacff` passed 27 focused Java tests but omitted fresh-cue guidance
in its Live proposal/preparation wording. That wording remains a CR-06/final
acceptance concern. The CR-04 Live scenario now establishes/completes the original
task through real Text inference, then exercises revision/acceptance/cue response
in Live; CR-09 retains the complete Live journey. `a80bef530c` failed before any
revision was admitted: context delivery closed and the peer failed. The trace had
unmute confirmation before server session-start, so the probe now waits for server
startup as well as connected peer/data channel before unmuting. These failures are
not accepted as successful workflow tests; their owned resources were cleaned up.

`bf1269032d` failed unmute with HTTP 502; context append preceded provider
session.started. The bridge now waits for both socket attachment and provider
startup, tested in both arrival orders and against duplicate/late startup events.
All **43 neighboring Java tests** passed in `a1e6a818b5`. Its paid attempt received
only a usage event before sideband disconnect, with no session.started or captured
user turn. This is a provider startup failure, not a revision acceptance pass.
The bounded Live revision check remains outstanding for CR-05/06/09 combined
verification; no production rollout is justified by the Text result alone.

## CR-05: bounded user-turn capture (2026-10-28)

Coordination opts into the existing 2,000-ms user silence policy. Assistant timing,
other agents, receipt identity, 30-second maximum open turns, late fragments and
coverage-gap uncertainty are unchanged. Actual 24-kHz PCM tests reproduce the
"A dry ... one-liner" and "tell me a ... joke" pauses at 1.2/1.7 seconds and keep
subsequent turns distinct. Clearly unfinished requests cannot invoke inference or
mutate task authority; the answer requests completion. This English guard covers
the observed fragments, not arbitrary semantic completeness in every language.

Passed **32 Java checks** in `1b0b5b1156`, including real local MySQL ingress with
two durable receipts, one revision and idempotent duplicate admission. The final
PCM case was strengthened to assert that the subsequent distinct turn is COMPLETE;
all **12 segmenter tests** passed again in `82e8943eb5`. The paid capture scenario
now composes two real synthetic speech clips with a reproducible 1.2-second pause.
Latency increases by up to 1.2 seconds; no physical/acoustic guarantee is claimed.

Paid Live `capture` **passed** (`82e8943eb5`): one admitted paused request, unchanged
protected sensor terms, fresh preparation, explicit acceptance question including
fresh-cue guidance, captured armed response with zero actions, then one response
on fresh rock. This also closes CR-04's outstanding Live revision check. It does
not establish CR-06's no-premature/duplicate-speech contract: captions include
"No wet" during the partial input and unnecessary backend-waiting commentary.
Owned application, schema and account were removed.

## CR-06: speaking authority (2026-10-28)

Speaking occasions carry session, epoch, creation time and agreement revision.
Coordination consumes complete current control captures and withdraws a task
occasion once semantic response association starts. Reconnect clears old occasions
and pauses an unfinished pending response. Dialogue contributions now use the
same association gate as sensor actions; a new step cannot replace pending speech.
Shared Generic semantics retain their existing contract. Native guidance separates
description, acceptance and execution and requests silent waiting for control facts.

First run (`5ddd5eaed7`) passed 64/65 checks; the old dialogue test still asserted
that no response association existed. Updated it to the new PENDING contract.
All **66 Java checks** then passed in `18a0bf275c`, including a persisted/reloaded
consumed Coordination permission and neighboring Generic/context delivery checks.
Provider output remains probabilistic: permission validation is deterministic,
but prompt instructions alone do not establish absence of unauthorized native speech.

Paid `delivery` in `18a0bf275c` passed the pre-acceptance no-joke window but failed
association of "it's live now; I'll act on the next fresh rock": the verifier
returned AMBIGUOUS, and the task correctly paused at zero actions. The current
requested response is now labeled ahead of agreement background, and association
guidance explicitly distinguishes arming from eventual exact-word task content.
This is a false-negative recovery, not a successful speech acceptance run.

Paid `delivery` **passed** in `8145a53c4c`: five-second windows before acceptance
and before the fresh cue contain no agreed joke; exactly one joke follows the cue;
an eight-second acknowledgement/refresh window contains no repeat. Live return and
state query agree with persisted Conversation. The final spoken return also says
"Okay, resetting" before "back in conversation": added guidance that return is
history-preserving, not reset. This smoke does not prove every acknowledgement or
paraphrase is ideal, nor physical audibility. Three additional deterministic cases
cover stale revision, expiry and association-in-progress. A rejected pending STEP
now preserves the original speaking permission as well as its association record.

Final CR-06 rerun: **18 Java tests** passed in `2a4f198975` after the pending-STEP
permission preservation change.

## CR-07: final delivery before reflection (2026-10-28)

New graphs opt into FINISHING/UNCERTAIN routing. A final spoken dialogue, immediate
reaction or cue response spends its budget once, then waits for TaskResponse
association and the existing scoped LivePlaybackCue acknowledgement. No further
action is admitted. Text/Continuous publication and silent output keep their
existing boundary. Capture expires at 45 seconds; playback at 60 seconds. Explicit
Stop, incomplete capture, session loss or timeout preserves LIMIT_REACHED while
recording uncertain delivery. An explicit restart archives that evidence and
creates a new budget. A fresh-session hook routes abandoned finishing work through
ordinary recovery before claiming ownership. Existing saved graphs lack the
opt-in marker and are not silently rewritten. No new database column is required.

Initial `457b94b95b` found two fake-capture tests comparing a synthetic timestamp
one nanosecond after creation against the same real-clock tick. Corrected their
controlled claim/finish times. **75 Java tests passed** in `226ed02a62`, including
real local DB reload, delayed/wrong-owner/wrong-segment/duplicate acknowledgement,
incomplete capture and fresh-session recovery, plus Generic and Live neighbors.
The existing playback and footer Node suites passed **13 tests**; the footer matrix
now also includes the new phases. Synthetic browser provider startup now emits
session.started to match the startup ordering contract introduced in CR-04.

The real-app browser check **passed** in `959c244731`: held acknowledgement after
capture leaves the footer on Finishing, then the scoped acknowledgement changes
it to Reflection. All 13 Node checks passed again with the expanded footer matrix.
Paid attempt `64574326b8` stopped at the acceptance wording assertion: it said
"Say START to accept then a fresh rock will cue the joke" rather than a question.
That is actionable acceptance guidance, so the assertion now accepts this explicit
instruction as well as a direct question; a bare "ready" still fails. This run did
not reach the final-response acceptance gate. Owned resources were cleaned up.

`8f1333c76b` also stopped before delivery: real proposal extraction left a question
about the recognition demonstration, so the harness's sign could not prepare it.
The scenario now checks completeness first and permits one ordinary, explicit
clarification of the already supplied demonstration/roles/defaults. It still fails
if questions remain; it does not bypass preparation or mutate stored data directly.

`8ddc8fb565` failed that setup assertion: extraction introduced another optional
question about saying ready versus waiting silently. Guidance now states that
recognition confirmation/acceptance mechanics are fixed by the backend and are
not missing user preferences. To isolate CR-07, its paid `delivery` scenario now
uses a complete real Text proposal explicitly requesting no preparation checks,
then real Live acceptance/cue/association/playback. CR-09 still requires the full
recognition and revision journey; the earlier CR-06 preparation/no-speech evidence
remains separately recorded. No failed setup is counted as delivery acceptance.

`f5adf7af63` reached FINISHING but Live appended "How'd it land?" to the exact
requested sentence. Association rejected the extra wording and routed to UNCERTAIN,
preserving LIMIT_REACHED. Final-contribution guidance now forbids added reflection
questions/asides and explicitly preserves exact wording; the verifier remains strict.
Fresh Live claims also create a new acceptance boundary for complete drafts with no
checks, so an old invitation cannot be mistaken for a current one.

Final `d29858158d` passed **21 Java tests and the paid Live delivery journey**:
FINISHING persisted while response/cue were pending, native response association
preceded the browser's accepted playback acknowledgement, and only then did the
agent enter Reflection. Pre-acceptance/pre-cue and post-acknowledgement windows
contained no premature/repeated joke. Return and state query both confirmed
Conversation. This demonstrates received-media evidence, not hardware audibility.

## CR-08: explain control and capture evidence (2026-10-28)

Control journals now retain stable source history positions through detached event
selection, before/after stage, run/revision, actions and explicit outcome. The
ordinary application turn binds its persisted input ID to the same activity
operation after save, using loaded history without another database read. The
storage journal is bounded by 12 entries and 6000 UTF-8 bytes with a dropped counter;
monitor/export metadata is explicitly allowlisted. Sensor outcomes are recorded
after setting the outcome. Footer copy explains acceptance, armed/fresh-cue waiting,
finishing and uncertain delivery without implying physical execution.

Live diagnostics retain the original trace and distinguish observed
session_closed_during_delivery from active context_delivery_unconfirmed. A failed
delivery near operator Stop is labeled as unconfirmed, with no cross-clock causal
claim. Controlled capture cases distinguish a delayed chunk containing full sample
coverage from a genuine coverage gap. Existing silence, interruption and late-input
rules remain conservative. Each of the three original assistant audio_coverage_gap
segments remains **incomplete capture with unresolved physical cause**: the stored
metadata cannot establish whether speech was clipped at the speaker. No capture
classification was relaxed and no audio-clipping fix is claimed.

Verification: `c41b32fdb3` passed the focused Coordination, activity, event-order,
capture and context Java suites plus one real-app browser/SSE footer and exported
timeline smoke. The two Node suites passed **16 tests**. Follow-up `aa5f0b5f1a`
found a test setup mistake: unchanged context generated no command, so its teardown
latch could never open. Supplying an actual synthetic narration exercises the
intended close-during-acknowledgement path. `b738112776` then passed **39 capture and
context tests**, including that teardown case and full-sample versus missing-sample
coverage. All disposable resources were cleaned up.

## CR-09: combined acceptance and release (2026-10-28 to 2026-10-29)

Extended the paid harness with full rock preparation/acceptance/delivery/revision/
renewed acceptance/delivery, scissors plus an uncertain facial-expression estimate
and return/new-task, and one-contribution dialogue/restart/history-preserving return.
The Text harness also covers the scissors/return/new-task variant. Sensor and audio
inputs remain synthetic; real inference and Live output are checked together with
persisted authority, association and received-media playback evidence.

All **147 Node tests across 23 files** and **six runner tests** passed. The first
Python summary print hit Windows console encoding after Node had exited zero; the
saved log confirms all 147 passed (the tests were not rerun to change that result).
Full Java run `3162b7a68a` ran **739 tests**, with five timeout errors in three older
Live integration fixtures: Generic (two variants), Social Space (two variants) and
the multimodal bridge. Their provider stubs never emitted session.started, now
required by CR-04 startup ordering. Updated those fixtures to emit the actual
provider event; production readiness guards remain unchanged. The paid scenario
did not run after the failed Java gate. Disposable resources were removed.

`9108cfc9ba` passed the full **739-test Java regression** after those fixture
corrections. Final diagnostics review added a five-second same-browser-clock
proximity bound: a much later operator Stop cannot reclassify an earlier active
failure. The seven diagnostics Node tests passed after that addition; underlying
server timestamps/trace and explicit non-causal assessment remain exported.

The paid combined **rock/revision journey passed in `9108cfc9ba`**. Both runs
required fresh preparation/acceptance/cues and ended only after PLAYBACK_CONFIRMED;
the revision retained protected terms and each run spent exactly one action.
Five-second pre-acceptance/pre-cue windows contained no joke, and the final
acknowledgement did not repeat it. Live still used short conversational lead-ins
(for example "let me get that ready"); this is not evidence that all provider
phrasing is ideal or that prompt instructions impose a hard acoustic speech gate.

Paid scissors attempt `246f1b16a9` stopped before opening Live: inference generated
a 207-character agentRole, exceeding the existing 200-character contract. The
entire update was correctly rejected with no draft. Added guidance to keep roles
brief and place exact script details in rule.action; limits remain unchanged.
The harness now reports a rejected proposal explicitly instead of dereferencing an
absent draft. No subsequent scenarios ran after this failed gate.

`bc9c230b94` passed 12 focused Java checks and the live scissors response, scoped
playback, return and state query. It failed the final new-task limit assertion:
"give one welcome sentence" produced a dialogue budget of six, not one. That
sentence did not explicitly state a contribution budget. The synthetic follow-up
now says "Limit one contribution"; the full journey must still pass from scratch.

`7302db2737` failed the facial-estimate assertion: speech said warm-neutral/attentive
despite the synthetic happy reading. Inspection found that PROPOSE had already
stored that invented estimate as the rule action, replacing the dynamic request.
Guidance now requires current-perception actions to retain an execution-time
instruction, never a prewritten example estimate; cue guidance uses fresh evidence
or says unknown. The harness also refreshes its synthetic face once per second
during output, avoiding expiry of a one-off 15-second observation. Neither change
weakens the happy/positive and uncertainty assertions.

`289838624a` passed 24 focused Java tests but failed arming: native speech mixed
English/Bengali and an unsolicited expression estimate into the confirmation.
Association returned AMBIGUOUS and safely paused at zero actions. Coordination
arming now supplies only authoritative control/trigger facts in its activity
context, excluding the future action script until a fresh cue creates the next
occasion. A deterministic test checks both the waiting context and restoration
of the action after a qualifying cue. The semantic verifier remains strict.

`5100e17dae` passed 34 focused Java checks. Its native arming response was now
appropriate ("I'm waiting for your sign"), but association returned AMBIGUOUS and
paused. The verifier was still receiving the entire future agreement, including
unrequested exact content. Arming association now receives only the current control
facts; guidance recognizes present-tense waiting for the already agreed cue as a
valid paraphrase without requiring a recital of its label. Bare acknowledgements,
future promises and premature task content remain insufficient.

`3efb16cd32` passed the focused Java gate and arming/cue path. It stopped on an
overly narrow output matcher: the uncertain estimate said "focused or amused"
while the positive-expression matcher allowed happy/smile/cheer/positive only.
"Amused" is a legitimate positive-expression paraphrase, so it is now included;
the Live assertion additionally requires explicit uncertainty. The earlier
warm-neutral/attentive failure remains excluded. This attempt is retained as a
failed test run and is not counted as complete acceptance.

`b759c470b3` again stopped at proposal validation: the role was 231 characters.
Rather than rely on repeated compression hints, role fields now allow 300
characters, matching goals/success; the entire 3000-byte agreement limit is unchanged.
This explicit bounded contract adjustment accepts the observed useful role text.
Boundary tests reject 301 characters and the local DB case reloads 300-character
roles. Earlier comments about the unchanged 200-character limit describe those
earlier attempts, not the final contract.

`cf06c3bc8f` passed 57 focused Java tests but exposed an association race. An
unrelated native utterance was correctly classified UNRELATED, yet the shared
boolean result handling made the final response uncertain before the subsequent
correct joke and happy estimate could be associated. Coordination now opts into
one additional candidate after a verified UNRELATED result, within the original
response identity/deadline. At most one later segment/start pair is retained while
the first check runs; its text remains in existing event history. This adds no
action or speech retry. Invalid/ambiguous results and exhausted allowance still
fail conservatively; Generic retains its previous single-candidate policy.
Deterministic unit and local DB tests cover the in-flight race, reload, duplicate
and stale identities, fixed deadline, allowance exhaustion and scoped playback.

`5544d7c203` passed **45 focused Java tests**, including the persisted in-flight
association race and Generic regression. The complete paid **scissors/multimodal
Live journey passed**: accepted activation, no premature joke, one exact joke plus
an explicitly uncertain positive-expression estimate, PLAYBACK_CONFIRMED, actual
return to Conversation, truthful state query and a fresh one-contribution proposal.
The native arming and contribution were both MATCH in this run; the new unrelated
candidate branch is verified deterministically rather than claimed as exercised by
this successful paid attempt. Disposable resources were removed.

`08c73766ed` passed the complete paid **dialogue/restart Live journey**. The first
and restarted one-contribution runs each ended with PLAYBACK_CONFIRMED and one
spent action. Ambiguous "start all over again" requested clarification without
changing the run. Explicit restart created a distinct run, and return/state-query
checks confirmed Conversation while preserving earlier history and outcomes.
The application and disposable database resources were cleaned up.

Final rock rerun `49d5026810` failed at arming: Live said "I'm checking where we
stand" rather than confirming waiting. The verifier correctly returned UNRELATED;
no action was spent. The harness's 40-second wait ended before the response's own
45-second deadline. Coordination arming now places its immediate confirmation/cue
before the general control menu, so the purpose fits in the first context append
chunk instead of following metadata. This ordering is a concrete improvement,
not proof that chunk order caused the provider's wording. The harness now permits
the actual capture deadline plus ingress time and fails immediately on UNCERTAIN;
it still requires CAPTURED and never treats timeout or unrelated speech as success.

`ed1a298e40` passed seven voice tests, arming and the first rock delivery, but its
revision wording did not match the harness's narrow dry/rock/check expression:
Live said the proposal stayed the same except for its line and was still inactive.
The persisted draft retained protected terms and reset preparation. The matcher
now accepts that valid confirmation. More importantly, the final rock scenario now
requests a distinct exact dry line ("My calendar is full of empty promises") and
asserts that the action actually changes, the new line is absent before the fresh
cue, and only the new line is delivered once. Earlier style-only revisions could
retain the same computer joke and were a weaker content-change check. This failed
attempt also contained an unsolicited aside after the first joke; native wording
remains provider-controlled and this is retained as a delivery-quality limitation.

`cc5230f64c` passed the first task but timed out waiting for a revised draft. The
synthetic revision PCM was only **2.6 seconds** long: TTS generated the quoted joke
alone, omitting the request and preservation constraint. The admitted user input
was exactly that joke, and KEEP in Reflection was appropriate. The harness now
composes separately synthesized request/line/constraint clips (as with the earlier
hesitation fixture), rejects implausibly short input and checks that the captured
utterance includes both revision intent and preservation terms before awaiting a
draft. This is a fixture correction, not a production transcription change.

`fdd26a59fc` used the corrected 12.4-second revision fixture but failed before
acceptance: Live explained acceptance and recognition without saying that a fresh
cue must follow acceptance. The strict clarity assertion remains. Current response
facts now precede agreement background for every Coordination occasion, including
the explicit acceptance/fresh-cue instruction produced by preparation; the earlier
ordering fix had applied only to arming. No guards or verification requirements
were relaxed.

`4d30e18ffa` passed nine focused tests and reached renewed activation with the
distinct revision and protected terms intact, but the strict negative window
caught Live quoting the new line inside a future-tense proposal. It also asked for
acceptance while preparation was pending (backend guards still blocked activation).
Guidance now explicitly forbids quoting supplied task lines during negotiation,
including "after you accept, I will say...", and names required fresh recognition
evidence before acceptance. A focused case covers those pending-preparation facts.
One unexpected provider input fragment (`onds\":3`) also appeared in this attempt;
it produced KEEP without changing the agreement. Its source is not established;
it is retained as a provider/capture limitation, not claimed repaired.

`4578b37bac` passed 15 focused tests and the first exact contribution without an
aside, then stopped at an overly literal synthetic-input check. The retained user
input was "Only the response to a different dry one-liner..." with the complete
line and preservation constraint, while the opening word "Change" appeared as
assistant speech. The input check now also accepts the retained revision meaning;
the quote-only truncated fixture still fails. This provider word/speaker anomaly
is recorded, not repaired or treated as perfect transcription.

`f3b60624a4` failed association of the correctly captured first computer joke.
The verifier returned AMBIGUOUS. Its prompt mixed ON_CUE activation guidance with
subsequent cue execution, and the transcript omitted punctuation from the exact
line. Coordination now persists an explicit ARMING/CONTRIBUTION response purpose
from its committing action and supplies that to the verifier. Guidance distinguishes
subsequent execution from initial arming and ignores punctuation/capitalization
without tolerating missing spoken words. Reload/in-flight tests protect the typed
purpose; legacy and neighboring responses retain UNSPECIFIED inference. These
remove concrete ambiguities; the provider's internal reason is not known.

`ff7a6efd59` passed **34 focused Java tests**, both exact contributions and their
semantic association, and the revision/no-preview/preparation/acceptance checks.
The second delivery did not receive browser playback confirmation: its gate had
no retained voiced sample, although media time progressed and capture was complete.
The old 40-second harness wait ended before the 60-second delivery boundary.
The harness now waits through that actual deadline (failing promptly on UNCERTAIN)
and retains at most 1400 test-only samples of RMS, playability, media progress,
timing gaps and cue identity. No audio is recorded, no production threshold is
changed and captions remain insufficient. The missing playback evidence's cause
is unresolved pending those diagnostics.

On the typed-purpose revision, `c444f26158` failed the scissors Live delivery:
the native utterance began with a perception comment before the response boundary
and continued into the correct joke/estimate. Complete capture therefore could
not satisfy the fresh-start fence and expired conservatively; no replay occurred.
This is retained as a native early-speech failure, not a successful contribution.
The independent **dialogue/restart Live journey passed in `7f98940544`**, including
both playback confirmations, ambiguity handling, distinct run and truthful return.
Paid **Text multimodal passed in `c406058c9d`** and **Text lifecycle passed in
`c8d7340820`**, covering the ordinary false-confirmation regression, new-task
configuration, protected outcomes and restart/return distinction. All runners
removed their disposable application/database resources.

Broad browser regression `dd5e930905` passed **64 enabled-mode cases** and **7
disabled-mode cases**, with four intentional Live-only skips when disabled. This
includes real local HTTP/SSE, Coordination finishing/export, neighboring Live
playback, lifecycle, transcription, state views and responsive UI regressions.
The owned applications and schema/account were removed.

Full Java regression `ecdb759309` passed **749 tests**, with no failures, errors
or skips, on disposable local MySQL after the association/purpose changes.

To address `c444f26158` without weakening capture fences, new Coordination sensor
tasks now defer unsolicited observations in the Live context while arming or
quietly waiting after a captured contribution. Backend history, sensors and cue
matching are untouched. An accepted contribution or an ordinary user turn restores
the selected facts with their original source times; expired evidence stays
unknown. This uses an opt-in ExternalVoicePolicy projection hook; other agents and
legacy Coordination graphs retain their existing context. Unit cases cover
withdrawal/restoration without history mutation, unchanged freshness, cue delivery
and direct perception questions without spending another action.

The pre-deferral diagnostic rock run `5af5046bc0` completed the first delivery but
rejected the revision because the provider added an unsupported `valueType` field
to a rule. The unchanged agreement remained in Reflection and the response reported
a snag. The strict parser remains unchanged; configuration guidance clarifies that
JSON values already carry their type and schema metadata must not be emitted.
This attempt did not reach the second playback boundary and therefore cannot
resolve the earlier missing-playback evidence.

`9127599b78` passed **59 focused Java tests** for context deferral/restoration,
Coordination, context delivery and Generic. The paid scissors attempt received
36,960 voiced input samples after confirmed unmute/context readiness but the
provider emitted no transcript or reply; its capture ledger stayed empty and the
task remained an unaccepted proposal. Deferral had not yet activated. This is a
failed silent-session attempt. A fresh-session retry follows the full regression;
no speech boundary or lifecycle check is relaxed.

Final Java regression `dad8836c62` passed **750 tests**, without failures, errors
or skips, after context deferral and its neighboring projection tests. Remote
main/agents remained at the inspected baseline; no unrelated branch changes were
introduced during the work.

Fresh session `fc8c45a920` reached the scissors contribution but Live copied a
neutral-face example the proposal model had inserted despite its instructions.
The committed backend plan correctly used the current happy observation. Sensor
Live response facts now use that current committed contribution, with hypothetical
agreement examples excluded from the active speech context and verifier reference.
`d9c6ed4a16` failed the correct first literal joke when the semantic model returned
unsupported MISMATCH. Complete explicit `Say exactly:` literals that also match
the committed plan now support deterministic word matching; punctuation/case are
ignored, words, identity, owner, deadline and playback remain strict. Additional
content and nonliteral actions retain the semantic path. Unit/local DB cases cover
literal eligibility, changed/missing words, immutable claims and zero-inference
association followed by mandatory scoped playback. Cue guidance also requires
actual speech rather than bracketed instructions in the backend plan.

`0d201e7c3f` ran 37 focused tests with two failures in the new current-content
cases: the shared Live publisher clears plan.speech before returning. The new
reference now saves that value before calling the publisher. The dependent paid
run `76203e8779` was stopped by targeting only its owned Node process; the runner
retains cleanup ownership. It is not acceptance evidence. Focused tests are rerun
before further paid checks.

Focused rerun `c7d183428e` passed all **37 tests** after saving the committed speech
before the shared publisher consumes it. This includes the literal, current-content,
delivery, database reload and neighboring Generic cases. The runner stopped its
owned process and removed the disposable database/account.

Paid Live multimodal rerun `8bf0468b26` passed the complete scissors journey on
the corrected source: no early joke, current happy-expression estimate with
explicit uncertainty, one authorized contribution, scoped playback confirmation,
return to Conversation, truthful state answer and a new one-contribution dialogue
proposal. Native wording still included harmless waiting questions and a partial
question before the state answer; this is not evidence of perfectly controlled
turn-taking or physical audibility. Its synthetic evidence and provider records
remain under the ignored acceptance artifact directory.

Paid Live recognition/revision `ce9728ec53` passed: recognition preparation,
explicit acceptance, fresh rock and the first exact computer line, confirmed
delivery, a distinct calendar-line revision preserving all protected terms,
fresh preparation and acceptance, fresh rock and the revised line once, confirmed
delivery, and no repetition of either line after acknowledgement. Neither agreed
line was spoken during the guarded pre-acceptance/pre-cue windows. Live still made
unsolicited conversational asides, including a "patient calendar" waiting remark;
the successful task-content checks do not establish a hard acoustic gate or
perfect silence. Together with dialogue/restart `7f98940544` and Text multimodal /
lifecycle `c406058c9d` / `c8d7340820`, all required automated combined journeys now
have passing evidence. A final full Java regression follows the last shared
response-association change before publication.

Final shared-source regression `7f0a7dd30f` passed **752 Java tests in 159 suites**,
with zero failures, errors or skips. Command:
`python tests/gptlive/run_acceptance.py --database-properties <local-admin-properties> --java-tests all`.
The focused and paid reruns use the same runner with `--java-tests` selection or
`--java-tests none --paid-core coordination --coordination-scenario multimodal`
and `recognition`. All runs used disposable loopback MySQL schemas/accounts;
the runner removed them and stopped its owned applications. No production trial
agents or test writes were created. The successful Node, browser, Text and Live
journeys above form the automated CR-09 gate; failed attempts remain part of this
record. Physical listening, camera classification, robot motion and the original
capture-gap cause remain unverified. Create a fresh Coordination instance after
rollout; saved graphs/prompts remain intact. Deployment integration retains the
agents-specific catalog/persona/embodiments and receives separate local checks.

Shared packaging passed with `mvnw.cmd -q -DskipTests package`; local log:
`target/coordination-refinement-package.log`. Whitespace verification passed.
