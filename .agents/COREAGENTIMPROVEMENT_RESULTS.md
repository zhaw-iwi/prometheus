# Core-agent improvement evidence

## Current status

CA-01 complete and pushed as 55738757. CA-02 complete and pushed as d30196ff.
CA-03 implementation/local verification complete; paid sensory trials pending.
CA-04 through CA-08 not implemented.
Baseline: main 19d2d51c, clean feature/gptlive fast-forwarded on 2026-10-26.
No main, agents or standalone Valerian runtime changes in this series yet.

## CA-01 — baseline and acceptance harness

Added the tracked eight-milestone plan and paid `--paid-core live` runner. It uses
the real production app, scoped APIs, MySQL, control inference and Live WebRTC;
only visitor audio/sensing is synthetic. Default database properties now select
the local test configuration. Loopback enforcement, unique schema/account,
provider preflight, subprocess deadlines/tree cleanup and failure artifacts bound
the run. Ordinary offline acceptance remains available.

Actually verified (2026-10-26):

- 45 Java tests: GenericMultimodalTaskUnitTest, SocialSpaceAgentUnitTest,
  LiveMultimodalUnitTest, ValerianCorePromptContractTest,
  HealthcareUseCasePromptContractTest, GenericMultimodalTaskIntegrationTest,
  SocialSpaceAgentIntegrationTest, SocialSpaceLiveJourneySmokeIntegrationTest.
  Disposable MySQL artifact: `target/gptlive-acceptance-3de3428350`.
- Three Python boundary checks: reject remote database, missing model before paid
  calls, and mixing paid/mocked providers. Node syntax and git diff checks pass.
- Paid baseline passes: `target/gptlive-acceptance-38526dd0ae`. Actual configured
  control model `gpt-5.6-luna`, effort none; Live `gpt-live-1`, Willow; synthetic
  visitor `gpt-4o-mini-tts`, Alloy. SIRA answer, supplied one-person evidence,
  persisted paper hand sign, retained state and attached session all checked.
  Each invocation stopped its app and removed its owned database/account.

All unsuccessful trials are retained, not counted as passes:

- `b8a32b383c`, `401d8771df`: new worktree lacked ignored openai.properties;
  backend route validation failed before control inference. Temporary content-free
  exception instrumentation identified this and was removed. Preflight now rejects
  missing model/key before billing. No production runtime fix was needed.
- `39453c92bc`: test incorrectly required speech for a gesture-only request. The
  existing agent policy requires silence and persisted the paper sign correctly.
  Corrected the test to check execution intent, then ran the successful trial.

Baseline limitation: synthetic speech occasionally splits into short durable user
segments and can cause repeated embodiment plans. Preserve this evidence for
CA-04/08; no capture timing changed. Physical audio/robot execution and full browser
visual regression are unverified at this milestone. No main/agents deployment.

## CA-02 — decorative gesture cadence

New Generic/Social Space and Live Multimodal instances share DecorativeGestures:
12-second spacing, 60-second same-label suppression, five published requests of
history, explicit request/agreed-action exemptions, and reset cleanup. Publication
only replaces the decorative label with NONE; other channels are retained.
Generic's configuration prompt fingerprint and legacy factory default remain.
Saved instances are not silently upgraded. EmbodimentPolicy adds one nullable
storage relationship; README records managed-schema requirements.

Verified so far:

- 43 Java checks (helper boundaries, explicit/task exemptions, existing core
  units and Generic/Social Live journeys) on disposable MySQL
  `target/gptlive-acceptance-1ba8d68547`. The new persistence case covers all three
  agents across reload, repeat suppression and explicit reset.
- Paid Live Multimodal journey passes with the cadence code:
  `target/gptlive-acceptance-4194b9bc66`. SIRA answer, current person count,
  requested paper sign, retained state and attached session verified.
- Shared reset/policy regression passes (`target/ca02-reset-regression.log`),
  covering oblivious history clearing, transitions, regulation reset, nested/outer
  routing and PromptPolicy. Physical movement remains unverified.

## CA-03 — meaningful sensory remarks

New Social Space instances opt into stable 2s counts, 4s conversation priority
and 15s remark cooldown. Face/social demos use 1s stability, 2s conversation
priority and 8s cooldown. Source order/15s freshness remains independent of receipt
refresh. A suppressed crossing is dropped, not queued. Rich social signatures
ignore tracker IDs and confidence jitter but retain confident movement/attention.
Explicit facial corrections/opt-outs suppress automatic interpretation until
resumed/reset; ordinary direct questions remain available.

Live uses expiring, interruptible PROACTIVE occasions and owns wording; these
sensor turns make no language-model call in the backend. Different/invalid newer
evidence or a user turn cancels a pending comment. Text/Continuous generate a
validated plan, discarded if its source expires or continuation is cancelled.
Existing saved Social Space and generic Multimodal Behaviour semantics are retained;
new opt-in policy classes/nullable storage association require a new instance.

- 41 Java/local MySQL checks pass in `target/gptlive-acceptance-9ebcf402e6`:
  helper rules, new agent graphs, existing saved-instance contracts, legacy
  canonical/compact Social Space journeys, Generic task regressions, gesture and
  sensory storage reload. Core sensory persistence covers all three definitions.
- Initial integrated run `b848ece2c7` failed due test fixtures mutating the read-only
  Agent storage projection and counting gateway configuration reads as inference.
  Corrected the fixtures/verification without loosening behavioural assertions.
- Eight focused helper/agent checks also cover conversation priority, source expiry
  and the quiet context after an occasion expires (`target/ca03-final-units.log`).
- Paid face `40e3471bf4`, social `dad7800a17` and Social Space `249b50d7f1`
  journeys pass: ordinary questions, direct current sensing, one stable-change
  comment, retained state and attached session. Face also persists a correction
  and withdraws its pending comment.
- Social trial `42e873c98a` failed an overly narrow test wording check: Live said
  "a second pops in", correctly describing the supplied second visitor. Added
  that semantic alternative and reran; no production behaviour changed for it.

Conversation priority uses committed dialogue timestamps. A just-starting user
utterance can overlap an already admitted remark before its transcript arrives;
the face trial exhibited this at the first question. This is no hard audio gate.
No physical audio/movement or UI rendering change is claimed. All paid runs used
disposable local databases and cleaned up their app/schema/account.

## CA-04 — task facts and contextual short replies

New core instances opt into Live wording, confirmed task facts and identified
speaking occasions; ordinary KEEP answers remain quiet to avoid duplicate replies.
Text/Continuous and saved opt-out contracts retain backend speech. Bare assent
requires a current completed invitation and the unchanged proposal; harmless
acknowledgements preserve control without another model request. Spoken actions
use the existing bounded TaskResponse check; silent gesture actions never wait for
captions. Explicit autonomous tasks can proceed after voice ownership ends.

During invitation testing, found that EventHistory.select discarded source
createdDate. Preserving it in detached selections fixes invitation timing and
CA-03 conversation priority. The earlier CA-03 helper tests bypassed selection;
the added selection regression explicitly protects that missing path.

Initial tests exposed old-contract assertions that needed explicit legacy opt-out,
one test using EventSelector as a functional interface, and a wall-clock unit
fixture with a one-nanosecond future start. Corrected fixtures; retained artifacts.
Integrated attempt `0cc03a255a`: 50 checks passed, one clock-fixture error (no
provider requests). `601f1f2cf1` passed 60 checks with one obsolete new-instance
voice assertion; updated it to check Live ownership and absence of legacy narration.
`b6592ed9f3` passes all 61 selected Java/local-MySQL checks, including new persisted
Generic/Social Space invitation acceptance, legacy scripted journeys, autonomous
continuation, timestamped selection and reset.

Paid attempt `1dcabaca6f` failed: invitation recognition missed "Want me to start
it?", and a split request ending in "finishes" produced a replacement proposal
without ON_CUE timing. Added the invitation form, runtime firstAction guidance
outside the unchanged configuration prompt, preservation guidance for fragments,
and rejection of IMMEDIATE for an explicit waiting request. The trial also showed
unsolicited waiting acknowledgements despite silent-wait guidance; prompt-only
speech coordination remains a limitation, not a claimed fix. No global capture
interval or media interruption changed.

`5b489b3846` passed the new task checks; three lexical-boundary tests still assumed
the old voice contract and now explicitly exercise legacy opt-out. `5920679f60`
passes all 43 selected local checks, but its paid journey rejected the proposal.
An explicitly paid, single-request synthetic control probe reproduced the cause:
the model used COMPLETE for "show once and finish". COMPLETE performs no action,
so ON_CUE correctly rejected it. Added guidance to use ACT plus maxActions=1 and
a rejection regression; `target/ca04-control-probe.json` retains the failed raw
synthetic response. The fixture starts no app/database and requires paid opt-in.
Post-fix synthetic control probe passes (`target/ca04-control-probe-fixed.json`);
36 final focused tests pass (`target/ca04-final-units.log`).

Paid Generic `0541648618` passes: ordinary
conversation/perception, ON_CUE one-action proposal, current completed invitation,
short acceptance, semantically confirmed activation, fresh rock cue, persisted
paper motion, completed phase and harmless acknowledgment. Social Space
`af78a01d8c` passed state assertions, but transcript review found contradictory
"activation is still pending" wording and a late arrival greeting before completion.
Do not count that as full behavioural acceptance. "Yes, please" split into Yes
and a polite tail; KEEP then cleared the still-owed activation facts. Polite tails
now preserve pending confirmations without inference, as do ordinary KEEP updates.
Strengthened smoke assertions to reject pending/repeated-acceptance wording and
an obsolete greeting during the result. `66028ea493` passes the 10 focused
unit/persistence checks, but its paid run exposed a conservative invitation
recognition miss for "Want me to start that?". Added the demonstrative-pronoun
case and regression; no task was activated by the unrecognized invitation.
`a421ebdc9a` again passes the 10 local checks but its paid invitation was
"Want me to start this when you say so?". That conflates accepting/arming now
with future activation, and remains deliberately unrecognized. Clarified this
distinction in the proposal facts and voice guidance; added a rejection test.
`d1b6c72c83` passes nine focused checks. The paid provider used the unambiguous
but shortened "Activate it now?", exposing another conservative recognition miss.
Made the modal invitation prefix optional and added the exact completed-segment
regression; conditional future invitations and unrelated questions remain rejected.
`b4e03658d0` passes nine focused checks but reveals an invitation timing defect:
the full speech segment began before the final proposal fragment committed,
whereas its final "Start it?" question arrived afterward. The invitation check
now uses existing timestamped speech parts to require that the question itself
starts after the current proposal; older/overlapping questions remain rejected.
No capture interval or additional provider check is introduced. `4ca54483f0`
passes 11 unit/persistence checks; its paid invitation adds the explicit
"and start waiting now" clause. Added that bounded conjunct to recognition and
the regression cases, preserving rejection of future activation and changes to
the proposal. `fc96ea1719` again passes focused checks but misses a natural
"activate that waiting rule now" invitation. These accumulating lexical misses
showed that a wording whitelist conflicts with Live wording ownership.

The final approach records a current completed question, then reuses the existing
control inference to interpret assent. Deterministic validation permits only KEEP
or activation of the unchanged draft; it additionally rejects deferred wording
without explicit current activation. No extra invitation inference is added.
`target/ca04-contextual-units.log` exposed a Mockito restubbing fixture error;
the corrected fixture and all 38 selected unit regressions pass in
`target/ca04-contextual-final-units.log`. Synthetic paid probe
`target/ca04-confirmation-probe.json` correctly handled activation/unrelated/revision
but misclassified future activation. The added deterministic guard rejects that
same response; all four persisted outcomes now pass in
`target/ca04-confirmation-probe-guarded.json` (five bounded provider requests total,
including one proposal). This does not claim infallible model intent recognition.
`92917226ba` stopped before provider execution: the question unit fixture could
capture and propose in the same clock tick. It now pins the proposal boundary
explicitly; production strict ordering is unchanged. `d3ebd9cc27` passes all 12
selected unit/local-persistence checks and the full paid Social Space journey:
proposal, short acceptance of "activate that waiting rule now", captured substantive
activation, no ambient arrival remark during the task, one fresh rock/paper action,
completion and harmless acknowledgment. The recorded response association points
to "It's active now and waiting for that rock sign", not its preceding "Okay".
Three Python runner-boundary checks and JavaScript syntax also pass. Generic recheck
`e1675bc70d` failed before the task: Live invented "Service Innovation and Robotics"
as SIRA's expansion. The compact Generic policy omitted the correct definition
already present in the shared core prompt. It now includes that same shared name
and brief lab facts; the configuration prompt is untouched. Added instruction/budget
and negative provider-output checks. `cac7126402` passes core prompt/unit checks
and answers the lab question correctly, but offers activation in a statement
rather than a question. The completed-context admission now supports statements
as well as questions; meaning is still checked by ordinary task control, with
unchanged-draft/deferral validation. All 38 focused regressions pass in
`target/ca04-prompt-units.log`; six paid confirmation cases pass in
`target/ca04-confirmation-offers.json` (one proposal plus six control requests).
Final Generic Live recheck `da1e9c2233` passes: correct lab/perception answers,
proposal/lifecycle explanation, short acceptance, captured activation, one fresh
rock cue, paper motion, completion and harmless acknowledgment. Together with
Social Space `d3ebd9cc27`, the required task journeys pass. `cac7126402` also
passed the core prompt/unit checks, including the unchanged configuration prompt
and Live instruction budget. CA-04 is complete; CA-05 is next.
All owned apps/databases/accounts were
removed. No Heroku database was used. Live sometimes adds acknowledgments or
technical lifecycle wording despite guidance; physical speech/movement and hard
audio-turn coordination are not established by these tests.
