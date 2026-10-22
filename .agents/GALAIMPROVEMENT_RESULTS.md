# Gala improvement implementation evidence

Roadmap: `.agents/PLAN_GALAIMPROVEMENT.md` on the agents branch. The user authorized
consecutive milestones with a commit/push after each; final user physical trials
are grouped at the end. Synthetic tests do not establish audibility or model compliance.

## GI-13 / Milestone 240 - follow-up baseline (2026-10-22)

Baseline: agents 98316fe. No runtime changes. Five synthetic tests exercise the
actual Gala graph and transcript segmenter, without production data or providers.
First/repeated RPS starts stay unconfirmed inside delayed inference. A 1.2-second
pause keeps a nonbinary question together and preserves the selected person.
Two labelled characterizations reproduce open defects: an invalid post-reveal
operation preserves the outcome but creates false recovery; 20-ms activity spikes
each second merge three separate questions into one complete segment. The synthetic
invalid operation is deliberately chosen; the real trial did not log its value.

The test exports actual application instructions/startup input and first/repeated
RPS, result, guessing and reveal context commands to ignored
target/gala-followup-fixture.json. GI-14 provider comparisons use these contexts,
not a hand-maintained prompt copy. Paused questions require a real-provider check:
correct backend segmentation cannot establish Live's own response timing.

Verification: 47 pure Java cases passed (5 new, 34 Gala reception, 8 segmentation),
zero failures/errors/skips. Command:
`mvnw.cmd -q -Dtest=GalaLiveFollowupUnitTest,GalaDinnerReceptionUnitTest,LiveTranscriptSegmenterUnitTest test`.
Log: target/gi13-unit.log. No application, database or paid provider started.
`git diff --check` passed. Production behaviour and speech are unchanged.

The preceding physical trial supplies the comparison baseline: first-round
invitation was after confirmation, but replay invitation was about 1.0 s before
backend confirmation and 3.0 s before occasion ACK. Two RPS results started in
captions about 2.38/2.14 s after accepted signs. Three questions formed one roughly
16-second user segment. There were 195 thinking chunks and 23 facial chunks expired
before send. These are trace/caption measurements, not exact acoustic alignment,
audibility or database-query counts. Private evidence stays under target/.

Next: GI-14's before/after provider feasibility gate. GI-15/GI-16 will replace the
known-failure assertions when their remedies land. GI-11 fixed-quota capacity and
final provider/physical/acoustic acceptance remain open.

## GI-01 / Milestone 227 - shared context contract (2026-10-21)

Shared changes begin on feature/gptlive. Dynamic guidance additions, replacements
and removals are delivered, including taskVoice; startup-only opening guidance is
excluded. Activity-aware task policies own their voice contract, while legacy
Generic task guidance stays unchanged. Speaking occasions may carry an expiry;
expired unsent commentary becomes a quiet cancellation, including after slow ACKs.
No schema, HTTP/SSE contract, sensor cadence or model-call change.

Passed 30 pure unit cases with:

```powershell
.\mvnw.cmd -q '-Dtest=LiveActivityContextUnitTest,LiveContextDeliveryUnitTest,LiveVoiceAcknowledgementUnitTest,LiveContextProjectionUnitTest' test
```

No Spring application, database or paid provider was started by this unit run.
Log: `target/gi01-unit.log`. Application prompt compaction, all-state budgets and
integrated checks follow on agents before GI-01 is declared complete.

### GI-01 application completion

Shared commits `2911f6c` and `e8141b8` were pushed through feature/gptlive and main
(main `5f1fb03`) and merged into agents. Gala policies now supply phase, revision,
validated agreed/proposed goals and silence status as quiet facts. Selected-person
secrecy guidance precedes target facts. New continuations expire after 30 seconds;
activity cleanup clears their metadata. No saved instances/history are migrated.

The maximum instructions are 12,382 bytes in both embodiments (previously 15,854).
Full maximum-length goals remain in separately bounded activity data; all person
cards fit. Source references/facts were not truncated. Activity-aware policies no
longer receive duplicate legacy task voice. Exact sentence matching remains for
GI-05; this milestone does not claim an enforced model speech/secrecy barrier.

Verification on the merged agents tree:
- 81 focused unit cases passed: LiveActivityContextUnitTest,
  LiveContextDeliveryUnitTest, LiveVoiceAcknowledgementUnitTest,
  LiveContextProjectionUnitTest, GalaDinnerReferenceUnitTest,
  GalaDinnerReceptionUnitTest, GalaDinnerTaskUnitTest, GalaDinnerLiveDeliveryUnitTest.
  Command: `./mvnw.cmd -q -Dtest=<comma-separated names above> test`.
  Log: `target/gi01-agents-unit.log`.
- 20 isolated MySQL integration cases passed, including all three canonical/compact
  Live journeys, Gala reload, query budgets and mixed-load regression:
  `python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinnerReceptionJourneySmokeIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,GalaDinnerReceptionIntegrationTest,LiveQueryBudgetIntegrationTest,QueryVolumeIntegrationTest`.
  Artifacts: `target/gptlive-acceptance-06e9530505`; disposable schema/account removed.
- `./mvnw.cmd -q -DskipTests package` passed; `target/gi01-package.log`.
- Initial checks caught the budget overrun and outdated prompt-wording assertions;
  quiet goal projection and revised contract assertions passed the final rerun.

GI-01 implementation and automated acceptance PASS. Provider/physical checks stay
pending for the later milestones/final trial. Continue with GI-02 after this push.

## GI-02 / Milestone 228 - user-turn admission (2026-10-21)

The capture mailbox polls established acoustic evidence before a new audio chunk
or transcript fragment can reset it. The application-specific user silence hook
(default 800 ms, Gala 2000 ms) is now shared from feature/gptlive; thresholds and
late/incomplete closure rules are unchanged. A pure short-reply rule allows a
later completed native question to supersede an old announcement for activity-owned
speech. Legacy agents retain the conservative announcement rule. Latest speech
must precede the input, match the active state and belong to the current session.
No extra polling, retries, schema changes or reconstructed commands are introduced.

32 focused unit cases passed (capture, segmenter, short-reply context, scoped
session, context projection); log target/gi02-unit.log. The fixture demonstrates
three distinct turns despite a delayed scheduler, while a 1.708-second internal
pause remains joined. Other cases retain gaps/late/duplicates/incomplete limits.
The original Fabi trace contains no retained acoustic coverage, so this race is a
reproduced vulnerability, not a proven explanation of that recording. Missing
provider text cannot be recovered. Integrated admission/restart/query checks and
Gala-specific assertions follow on agents before milestone completion.

Shared integration acceptance: 10 cases passed (LiveTranscriptIngressIntegrationTest, LiveTranscriptClaimIntegrationTest, LiveQueryBudgetIntegrationTest), using run_acceptance.py with the agents application-test.properties. Artifacts: target/gptlive-acceptance-7a5f92d358; disposable schema/account removed. No production database or live provider was used.

### GI-02 application completion

Shared commit c51ea65 was pushed through feature/gptlive and main (5dc6c56),
then merged into agents. A persisted Gala regression admits Okay after an old
announcement and a later completed question, retaining one durable user event
and one inference even when the accepted segment is retried.

18 isolated agents integration cases passed: GalaDinnerReceptionIntegrationTest,
GalaDinnerReceptionJourneySmokeIntegrationTest, GenericMultimodalTaskIntegrationTest,
SocialSpaceLiveJourneySmokeIntegrationTest and QueryVolumeIntegrationTest.
Command: `python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests <comma-separated names above>`.
Artifacts: target/gptlive-acceptance-7ca3204ee4; disposable schema/account removed.
`./mvnw.cmd -q -DskipTests package` passed (target/gi02-package.log).
Together with the shared checks: 32 unit and 28 integration cases passed.

GI-02 implementation and automated acceptance PASS. Acoustic thresholds remain
unchanged. Physical paused-question/rapid-follow-up recognition remains for final
acceptance; there is no claim to recover absent transcription or prove the cause
of the original merged child input. Same-state semantic question interpretation
remains model-owned; established state/timing/session evidence controls admission.

## GI-03 / Milestone 229 - game control and recovery

GI-01 and GI-02 deployment workflows succeeded for agents eca6010 and 3c617f6.
GI-03 strengthens the existing readiness rule without changing delegation or
full-duplex playback. Reception explicitly reports unconfirmed game readiness.
RPS's eligible-sign time now begins after inference, not before its delay.
Direct guesses include "War es ...?" and bounded direct-guess variants; quotes,
negations and incidental mentions remain excluded. A model GIVE_UP additionally
requires a bounded actual reveal request. Confirmed guessing outcomes survive
cleanup as quiet, identified facts for 30 seconds, without another commentary.
Unsupported optional labels normalize to existing defaults. Invalid control or
inference preserves prior storage, then reports a bounded failure; stop still
ends the activity. No schema, provider mode or public API change.

57 focused unit cases passed: GalaDinnerReceptionUnitTest,
GalaDinnerReferenceUnitTest, GalaDinnerLiveDeliveryUnitTest, GalaDinnerTaskUnitTest.
Log: target/gi03-unit.log. 12 isolated integration cases passed:
GalaDinnerReceptionIntegrationTest, GalaDinnerReceptionJourneySmokeIntegrationTest,
QueryVolumeIntegrationTest. Artifacts: target/gptlive-acceptance-0127e8893f;
disposable schema/account removed. Final conditional-give-up and explicit-reveal
guard checks passed in focused reruns (including a genuine "I don't know" reason).

Real provider readiness probes use gpt-live-1/marin over WebRTC, synthetic German
speech and actual exported Gala context/delivery commands. Confirmation is held
for 12 seconds after input audio ends. This isolates frontend behavior, not the
real microphone/backend admission or physical playback. No production DB is used.

- Baseline target/gi03-provider-10-1.json: hand-sign invitation at 10.42-11.39 s,
  before first confirmation at 20.87 s; another invitation follows confirmation.
- Strengthened rule, target/gi03-provider-10-2.json: waits for confirmation.
- Repeat, target/gi03-provider-10-3.json: no early sign invitation, but an unwanted
  waiting acknowledgment remains. This is not a silence/compliance guarantee.
- Repeated visitor request, target/gi03-provider-10-4.json: waits for confirmation.
- Guessing start, target/gi03-provider-1-1.json: waits for confirmed game facts,
  keeps Einstein secret through partial card delivery and introduces once. It
  starts speaking after confirmed facts but before the commentary append.

The full revised-context trial (target/gi03-final-webrtc-results.json) completed:
choice remained open, starts followed confirmed game facts, target stayed secret,
yes/no and unknown-fact answers were correct, give-up revealed Einstein once after
the retained outcome arrived, and RPS spoke the supplied correct verdict. It also
gave an unhelpful waiting acknowledgment to the hypothetical give-up rules question.
An explicit direct-rules instruction was added; the focused provider rerun
(target/gi03-rules-webrtc-results.json) answered the hypothetical directly without
the name and revealed Einstein once only after the actual request and outcome.
Waiting acknowledgments on actual pending control remain a GI-07 quality target.

The conditional-give-up guard rerun passed. The two Gala journeys were rerun with
new persisted-outcome and duplicate-source assertions; both passed in disposable
schema target/gptlive-acceptance-f5a14ec8eb (removed afterward). Latest prompt units
also passed. The full trial does not establish headset audibility or robot motion.

Official API boundaries checked on 2026-10-21:
[delegation](https://developers.openai.com/api/docs/guides/live-delegation) supplies
metadata and does not pause Live speech; application state and transcripts still
own request admission. A direct tool would not enforce an audible barrier.
[Playback controls](https://developers.openai.com/api/docs/guides/voice-server-controls?api=live#control-playback-when-needed)
require client/media control if audio must be blocked. An instruction ACK is not
a playback-release signal. This milestone preserves full duplex and reports
provider trials separately from deterministic control guarantees.

GI-03 implementation and automated/provider acceptance PASS. The full provider
sequence used synthetic audio and exported commands with injected timing; runtime
expiry/durability is established by the offline tests, not by that provider harness.
No audible-barrier guarantee, physical audio quality or robot execution is claimed.
The instruction budget tests pass with room below 14,400 bytes. Packaging passed;
logs target/gi03-package.log and target/gi03-package-final.log. Residual occasional
waiting acknowledgments are explicitly retained for GI-07, not hidden as a pass.

## GI-04 / Milestone 230 - explicit first-action timing (shared foundation)

TaskSpec accepts optional firstAction ON_CUE or IMMEDIATE. Missing fields retain
IMMEDIATE; default serialization remains unchanged. ON_CUE persists explicitly,
arms with zero actions and strips any premature activation action/motion. Its
final permitted cue completes without an extra cue. Existing default counts and
Generic pause/resume behavior remain unchanged. A validated reply-rendering hook
supports application-owned agreement summaries before mutation. No schema change.

28 focused unit cases passed: TaskFirstActionUnitTest, GenericMultimodalTaskUnitTest,
TaskLifecycleUnitTest (target/gi04-unit.log). Four Generic integration cases passed,
including legacy/future timing reload, session recovery and Live task journey:
run_acceptance.py with agents application-test.properties and
--java-tests GenericMultimodalTaskIntegrationTest. Final artifacts:
target/gptlive-acceptance-14efdf36cf; disposable schema/account removed. Initial
new-test failures were a test-fixture mistake: getStorage returns a map copy;
the corrected test updates the owning TaskPolicy storage inside a transaction.
Gala agreement rendering, application checks and provider review follow on agents.

GI-04 compatibility correction: the Generic prompt fingerprint gate caught the
shared schema-declaration wording change. A ConfigurationContract extension now
allows an application-specific task-field declaration while restoring Generic's
original prompt byte for byte (SHA-256 1fdf7e9f17da9b1cc1c5c5b43692e84c5d97c103f46ecfb1d37968c3de1d30c8).
29 focused units passed after this correction; target/gi04-prompt-compat.log.

Gala completion: new future-cue proposals explicitly select ON_CUE; acceptance
arms without a joke, hand movement or action count. Immediate timing remains an
explicit alternative, including legacy saved tasks. Proposal text is derived from
validated trigger/action terms, timing, count and encounter termination, with
no motion before acceptance. One-action immediate proposals do not promise an
additional cue response. Recognized ordered cross-sensor requests clarify without
replacing the draft; the configuration prompt also rejects unrepresentable ordered
workflows and invented trigger fields. This bounded guard is not a general parser.

Verification on agents:

- 91 focused unit cases passed: GalaDinnerTaskUnitTest (23),
  GalaDinnerReferenceUnitTest (5), GalaDinnerReceptionUnitTest (31),
  GalaDinnerLiveDeliveryUnitTest (1), TaskFirstActionUnitTest (4),
  GenericMultimodalTaskUnitTest (17), TaskLifecycleUnitTest (10).
  Log: target/gi04-agents-final-unit.log. A final voice-policy-only change passed
  the 28 affected Gala task/reference cases in target/gi04-voice-contract-unit.log.
- 19 isolated integration cases passed through run_acceptance.py with local
  application-test.properties: GalaDinnerReceptionIntegrationTest,
  GalaDinnerReceptionJourneySmokeIntegrationTest, GenericMultimodalTaskIntegrationTest,
  SocialSpaceLiveJourneySmokeIntegrationTest, QueryVolumeIntegrationTest.
  Artifacts target/gptlive-acceptance-c2354a99ce; disposable schema/account removed.
  Gala reload now verifies ON_CUE and zero actions after acceptance. Shared
  reload covers both future-cue and legacy immediate timing.
- Three real configuration-provider cases (gpt-5.6-luna, extraction route) were
  replayed through the actual action/validation code: future proposal, acceptance,
  immediate alternative with a supported cue. The initial trial rejected an
  oversized generated action and an invented utterance trigger safely. Prompt
  corrections require short natural action descriptions and clarification when
  a supported cue is missing; the affected rerun passed. Files under target:
  gi04-backend-initial-rejected.json, gi04-backend-results.json,
  gi04-backend-validated.json and gi04-backend-replay.log.
- Real gpt-live-1/marin WebRTC speech with synthetic input retained the future
  cue, joke plus rock output, one-action limit, all termination conditions and
  new-agreement requirement. Acceptance produced only an arming response. The
  initial voice trial omitted lifecycle terms; the policy now explicitly permits
  longer task proposals and requires those terms. The affected rerun passed:
  target/gi04-task-webrtc-results.json and gi04-task-audio.webm; earlier omission
  retained in gi04-task-first-omitted-lifecycle.json. An extra short acknowledgment
  remains a GI-07 quality issue. Exported command injection tests speech, not DB
  dispatch or physical robot motion. This is one final voice trial, not the
  three-session acceptance matrix scheduled for GI-12.
- Maximum complete Live instructions: 13,502/16,000 UTF-8 bytes in both
  embodiments and every tested activity, below the 14,400 working limit.
  Packaging passed (target/gi04-package.log); git diff --check passed.

GI-04 is implemented and verified within those limits. The existing task capture
gate still matches backend wording; replacing it belongs to GI-05. No claim is
made that this milestone solves arbitrary Live paraphrase association or proves
physical playback/gesture completion.

## GI-05 investigation - response association decision (2026-10-21, historical)

GI-04 application commit b5e09a5 is pushed to agents; shared changes are also on
feature/gptlive (bca6d49) and main (d318090). GI-03 deployment completed successfully;
GI-04 workflow 39674794714 was in progress when checked.

The two real GI-04 voice trials produced 69 and 91 assistant transcript fragments,
respectively; none carried client_event_id. LiveTranscriptIngressService supplies
all recent candidate backend intent IDs with AMBIGUOUS association. TaskDecision
firstSample currently distinguishes the requested response from acknowledgments
and unrelated speech by matching backend wording. A generic completed caption
cannot safely replace that association when wording becomes Live-owned.

Official API checked on 2026-10-21:
- [Transcript deltas](https://developers.openai.com/api/docs/guides/live-conversations#transcript-deltas)
  have approximate intervals, no response item identity and no turn-done event.
- [Output transcript schema](https://developers.openai.com/api/reference/resources/live/sideband-websocket#session.output_transcript.delta)
  has an optional client_event_id, not a guaranteed spoken-response correlation.
- [Playback controls](https://developers.openai.com/api/docs/guides/voice-server-controls#control-playback-when-needed)
  distinguish input mute, context acknowledgments and actual client playback.

At that point the proposed decision was bounded semantic association versus explicit visitor
confirmation before further task steps; exact scope and tradeoffs are recorded
in PLAN_GALAIMPROVEMENT.md. Neither was implemented during the investigation.
The user subsequently approved semantic association; implementation follows below.

## GI-05 / Milestone 231 - bounded task response association (shared foundation)

The user approved one semantic check per pending task response on 2026-10-22.
TaskResponse stores one occasion/session/epoch/revision with a 45-second deadline.
Native recording claims a completed, fresh, nontrivial candidate atomically with
its durable record. Existing bounded background-action workers run the check
after commit without holding an entity, JDBC transaction or agent lock. Output
identities and verdict are validated; uncertainty never releases another action.
Completion rechecks ownership/current response and requires fresh post-check cues.
Duplicate/reloaded claims do not retry. Context refresh reuses its existing
deadline scheduler for expiry; no extra poller, schema or transcript endpoint.
Legacy tasks without the opt-in response state retain their wording-based gate.

Focused shared units passed (target/gi05-shared-unit.log), including the new
TaskResponseUnitTest and existing task/Live contracts. Ten isolated integration
cases passed: LiveSpeechPersistenceIntegrationTest, GenericMultimodalTaskIntegrationTest,
LiveQueryBudgetIntegrationTest; target/gptlive-acceptance-0c331985c5. Tests verify
one durable check, duplicate native recording, paraphrase association, no further
action, unlocked inference and obsolete-result rejection. The first run exposed
an executor result-key mismatch; the declaration was corrected and the affected
integration set passed. Disposable schema/account removed. Gala opt-in, provider
classification/latency review and full milestone acceptance followed below.

## GI-05 / Milestone 231 - Gala Live-owned task responses (2026-10-22)

Gala now supplies validated agreement/action/recovery facts and one identified
occasion; Live composes all task wording. Backend task plans retain nonverbal and
motion only. Proposal, KEEP and ON_CUE arming strip hand motion; terminal facts
are captured before clearing the agreement. Complete task specifications have an
1,800-byte UTF-8 bound so maximum goals/terms fit activity context. Ordered
sequences, invalid replacements and provider failures retain the valid agreement
where possible. A fresh clarification supersedes the earlier response claim.

One durable asynchronous semantic check gates further task cues; check completion
requires fresh evidence after its boundary. A requested literal short word can
reach the checker, while ordinary short acknowledgments cannot release a task.
Deadline/provider failure returns Gala to reception with one uncertain occasion,
never a physical retry. Stop, closure and obsolete identities fence late results.
No added poller/schema, no per-frame/per-fragment inference, no transcript endpoint.

Verification:

- 92 focused Gala/context units and 40 shared task/speech units passed. Generic's default
  configuration contract remains unchanged. Gala tests cover canonical/compact
  output, future versus immediate first action, last-action facts, no backend
  prose, retained hand motion, silent WAIT, superseded response checks, bounded
  UTF-8 terms, and cleanup on stop/closure/reset/departure/deadline.
- All three representative journeys plus native persistence/query regressions:
  18 isolated integration cases passed (target/gptlive-acceptance-5f10b5a437).
  Four Gala persistence cases including added deadline refresh then passed
  (target/gptlive-acceptance-acf19b72f1): 19 distinct cases across these suites.
  The timeout test confirms exactly one recovery occasion after reload and no
  inference/replay. Disposable schemas/accounts were removed.
- Eight real provider classifications passed: new joke, arming paraphrase,
  immediate joke and literal word matched; acknowledgment/promise, unrelated
  answer, incomplete exact wording and premature action did not. gpt-5.6-luna,
  configured EXTRACTION route, one request/case, no retries. Round trips
  1.175-2.722 seconds, median 1.595 seconds. This small sample measures the check,
  not end-to-end delay or worst-case capacity. It gates the next automatic action,
  not current Live speech. target/gi05-response-results.json.
- Real backend proposal/activation trials: the first future one-action proposal
  incorrectly used COMPLETE; validation rejected it. Prompt now explicitly says
  ACT performs the final action and COMPLETE only ends without acting. All three
  affected cases then validated, with no premature speech/motion or action count.
  target/gi05-backend-first-results.json, gi05-backend-results.json and
  gi05-backend-validation.log retain that evidence.
- Real gpt-live-1/marin WebRTC trials used actual projected context and synthetic
  user audio. Live composed proposal, arming and a final joke after task cleanup,
  without backend prose. Trials exposed omitted lifecycle terms and one premature
  joke during ON_CUE arming. The voice contract now explicitly separates arming
  from action; arming facts prohibit the joke, and response constraints precede
  action descriptions in chunked context. Proposals retain facts for 60 seconds
  because complete terms can take longer than a short game answer. The final
  trial armed without a joke and delivered a new joke plus explicit completion
  only after the cue occasion. target/gi05-task-webrtc-results.json/audio.webm;
  earlier failures are retained as gi05-task-omitted-lifecycle.json and
  gi05-task-premature-joke.json. This exported-command trial does not run the
  DB dispatcher or semantic checker against a physical robot. Short waiting
  acknowledgments, duplicate arming wording and occasional omission of an
  individual ending condition (notably pause) remain GI-07/GI-12 quality items.
- The final local-fence change also passed seven deadline/query integration
  cases (target/gptlive-acceptance-e93ff3433d); it avoids repeating the scope query
  already performed by the caller. No authorization check was removed at entry.
- Maximum complete Live instructions 14,142/16,000 bytes in both embodiments,
  below the 14,400 working limit. Packaging and git diff --check passed.

Speech association is probabilistic and is not proof of audible playback or
physical execution. Physical headset/robot acceptance remains scheduled for
GI-12; provider synthetic audio does not replace it. Brief Live waiting
acknowledgments remain a GI-07 quality item.

## GI-06 / Milestone 232 - fresh evidence and optional ambient delivery foundation

ObservationEvidence shares TTL/source timestamp parsing across Live and tasks;
task TTLs and two-second clock skew remain unchanged. Live requires a source
timestamp, hides unknown raw values, ignores delayed/future replacement frames,
and withdraws the prior facial summary on explicit absence/poor detection.
New optional interruptible proactive continuations use existing in-memory audio,
open-segment and mailbox activity to yield to conversation, including during ACK
waits. Once suppressed, an occasion cannot replay. Required results/tasks retain
their existing default. No new DB query, polling loop or persisted schema.

64 focused units passed (target/gi06-shared-unit.log), including new source-age,
face withdrawal, out-of-order input, pre-transcript audio and dispatch-race cases.
Isolated LiveMultimodalBridgeIntegrationTest, GenericMultimodalTaskIntegrationTest
and LiveQueryBudgetIntegrationTest passed; target/gptlive-acceptance-254e8a85cc.
The initial test fixtures lacked source timestamps; fixtures now supply them and
an explicit undated-sample test protects the unknown outcome. Disposable schema
and account removed. Gala admission/cadence acceptance follows on agents.

## GI-06 / Milestone 232 - Gala ambient admission (2026-10-22)

GalaDinnerSensing separates optional ambient admission from activity control.
Fresh changed counts/expressions need observations at least two seconds apart,
share a 25-second cooldown and expire after at most eight seconds or source
expiry. Initial presence belongs to opening; zero visitors still clears the
encounter immediately. Baselines advance even when speech is suppressed, so
tasks, games, recent user turns, pending outcomes and quiet cannot create a
comment backlog. Gaps reset the baseline. Raw weather creates no occasion.
Explicit expression corrections preserve the activity and suppress unsolicited
interpretations for that encounter. Direct questions remain Live-owned.

Interruptible dispatch uses in-memory audio/open transcript/mailbox activity.
Known activity identity notifications also withdraw superseded occasions during
an ACK wait without another query. Native-record notifications do not falsely
clear that identity. Required task/game results retain their default delivery.

Verification: 146 focused units passed (target/gi06-final-unit.log), including
source expiry/absence, out-of-order/future frames, count/face oscillation, stable
changes, correction, quiet/task/pending-result priority, no backlog, source-bound
occasion lifetime and dispatch supersession. Seventeen isolated integrations
passed across all three representative journeys, Gala persistence, Live bridge
and query budgets (target/gptlive-acceptance-1fc4eda457); disposable resources
removed. Six browser reception tests passed, retaining immediate local audio
closure (target/gi06-reception-node.log). Packaging and git diff --check passed.
Complete Live instructions max 14,331/16,000 bytes (below working limit 14,400),
including maximum task goals and correction context.

No detector threshold, identity tracking, per-frame inference or new database
poller. Physical cadence, noisy-room sensing and generated ambient phrasing
remain for the GI-12 live acceptance round.

## GI-07 / Milestone 233 - concise voice and warmer self-irony (2026-10-22)

One applicable policy across every activity now asks for direct brief replies,
no habitual echo/preamble/waiting acknowledgment, occasional varied self-irony,
respect for refusal, simple unclear-speech clarification and truthful recovery
with a next step. It avoids jokes at the visitor's expense or during distress.
Full task proposals remain an explicit brevity exception. No new inference,
backend speech generator, lifecycle change or database access was added.

Verification: 63 focused Gala unit cases passed (target/gi07-unit.log), including
all activity/embodiment budgets and existing non-destructive recovery contracts.
Two isolated Gala journey variants passed (target/gptlive-acceptance-44a6a33b99);
disposable schema/account removed. Packaging and git diff --check passed.

Actual gpt-live-1/marin WebRTC review used current projected instructions and
synthetic TTS input. Nine fixed turns covered ordinary SIRA information, facial
estimate/correction, unclear speech, refusal, a simulated control failure, two
jokes and bereavement. The jokes varied (WLAN smalltalk; robot left/right), the
unclear turn got a brief clarification, and the serious turn got sympathy rather
than humour. No repeated introduction, visitor blame or question echo was seen.
The ordinary answer included an unnecessary offer; refusal used "Alles klar".
While awaiting a failed game start, Live incorrectly asked the visitor to confirm
readiness. The policy now explicitly assigns automatic confirmation to the backend.
Original evidence remains target/gi07-quality-first-results.json/audio.webm.

A separate task proposal/acceptance/cue trial retained trigger, both output channels,
count and all ending conditions including pause. Arming did not execute the joke;
the cue produced one new joke and completion. Short "Okay" then "Alles klar"
arming acknowledgments still appeared. This is an OPEN wording-quality issue,
not grounds for claiming enforced one-response compliance. Evidence is retained
as target/gi07-task-results.json. GI-12 must still run the repeated critical gate.
These exported-context trials do not establish database dispatch, physical motion
or audible final-word delivery. No saved instances were rewritten.

The focused failure follow-up initially suggested starting after the control
failure (target/gi07-recovery-failure-results.json). Recovery facts now explicitly
prohibit automatic start/retry and require a fresh visitor request plus backend
confirmation. The affected unit contract and another fixed follow-up passed:
Live said it had changed nothing and asked about trying again; no game invitation
or invented target followed. target/gi07-followup-webrtc-results.json/audio.webm.
A stock acknowledgment and an unsupported offer to ask the team remained; voice
guidance now explicitly refers visitors to staff without claiming contact. This
last wording guard has deterministic coverage only, pending the GI-12 provider
matrix. These quality gaps remain recorded, rather than rerunning until clean.
Maximum instructions before this last 39-byte clarification: 14,304/16,000 bytes;
the final full-state budget test remains below the 14,400 working limit.

## GI-08 / Milestone 234 - occasional meaningful gestures (2026-10-22)

Core decorative label meanings are reused verbatim; the existing core nonverbal
prompt text and Generic configuration hash remain unchanged. Gala supplies up to
five published requests from the previous minute to its existing selector, with
no second inference. Publication suppresses decoration within 12 seconds or the
same label within 60 seconds, preserving face/gaze and intentional NONE. Explicit
German/English gesture commands and agreed task actions bypass cadence; RPS hand
signs remain independent. The command exemption only relaxes cadence for an
already selected gesture, never authorizes motion by itself. No random rotation.

History is encounter-local persisted state, updated in existing publication
transactions and cleared at new Live encounters/departure. It records published
requests, not execution. Consuming the pending plan prevents reload/re-generation
from replaying it. No native-transcript gesture generation, poller, sensor write
stream or additional authorization read was added. Persistence writes remain
part of the measured GI-11 budget, rather than being claimed free.

Focused unit coverage includes boundaries, repeated/different labels, NONE,
explicit requests/negation, task gesture and RPS preservation, bounded/corrupt
history, encounter reset, once-only publication and existing invalid-decoration
recovery. Three actual requests still make exactly three model calls. Thirteen
isolated integration cases passed across the three journeys, dispatch and query
budgets (target/gptlive-acceptance-7d8ce2283f); disposable resources removed.
Physical execution, distinctiveness and speech alignment are NOT RUN; the manual
Gala script now asks the operator to distinguish visible plans from robot motion.

Final GI-08 verification: 98 focused unit cases passed (88 in gi08-unit.log and
10 in gi08-lifecycle-unit.log). One real-app browser test passed for sensor/native
speech/gesture-plan coexistence (target/gptlive-acceptance-3ccb6bd599); its feature-off
variant was skipped by that test and is not claimed as legacy coverage. Packaging
and git diff --check passed. Core extraction was committed through feature/gptlive
(be99cd8) and main (8215a21); the agents merge preserved its existing RPS labels.

## GI-09 / Milestone 235 - hand-game readiness and acceptance (2026-10-22)

The existing activity journal/footer distinguishes no round, awaiting one steady
hand, low confidence, unstable camera evidence, stale/pre-start/out-of-order input,
accepted cue, completed round and cancellation. Repeated reasons coalesce through
the existing service. Terminal footer hints expire after ten seconds; no stale
hint claims an active round. A rejected observation changes neither activity nor
hidden sign. Held signs after resolution cannot produce another result.

Browser 0.65 remains a configurable emission threshold; the operator label now
explains that sending is not acceptance. Gala's unchanged 0.70 acceptance threshold
requires a valid confidence, fresh source timestamp after start and, for camera
origin, the already-emitted three-frame stability metadata. Explicit operator
injection has no frame count. The post-start timestamp boundary remains unchanged.
Out-of-order checks reuse loaded event history; no new polling, sampling or
per-frame state records. Confirmed game introduction/display advises one visible
hand held steady without speaker attribution. Other agent acceptance rules remain
unchanged. Shared feedback landed through feature/gptlive 4f8d8f3 and main 6acd9d4.

Pure/unit acceptance covers confidence boundaries, missing evidence, stability,
pre-start/future/stale/out-of-order input, actual readiness, cancellation, held-sign
suppression and a new round. The normal selector still makes just one call to start;
hand rejection/resolution makes none. Camera disabled/emission failure and stable
held-sign retry behaviour retain the existing browser regressions. Real-camera
repositioning/held-sign acceptance is NOT RUN; the manual script covers it for the
user's grouped physical acceptance at GI-12. No saved graphs were migrated.

GI-09 verification: 50 focused Java unit cases passed (target/gi09-unit.log),
29 Node cases passed (target/gi09-node.log), and nine isolated integration cases
passed: Gala journey/query budgets (target/gptlive-acceptance-07ea80fd49) and
Gala persistence/reload (target/gptlive-acceptance-1b8f38af4e). Disposable schemas
and accounts were removed. Packaging and git diff --check passed. The first
integration selection included a nonexistent persistence class; only the five
actual journey/query cases from that run are counted, with the four real
persistence cases run separately under GalaDinnerReceptionIntegrationTest.

## GI-10 / Milestone 236 - Live output evidence (2026-10-22)

The browser records fixed stop causes for operator Stop, lifecycle/departure,
stale presence, lost access, provider/backend session closure, transport/media
failure and startup/input-control errors. Microphone mute stays separate. Silence
still happens synchronously before cleanup or provider responses. Unknown causes
stay unknown; provider session closure is never natural utterance completion.

Existing media/RTP samples include the latest observed context revision,
acknowledged speaking source and output-caption receipt when available. These
are explicitly approximate context associations, expire after 30 seconds and
clear on new input. Repeated status cannot restore a cleared old occasion. Normal
Live replies may have no backend speaking occasion. Existing clocks and retention
bounds remain separate; no text, raw audio, device IDs or free-form error detail.
No new poller, request, database query, PCM buffering or VAD change.

Tests preserve stream/peer/output volume through instruction/thinking/commentary
acknowledgments and activity refresh, retain immediate intentional stop and access
revocation, fence late callbacks, and bound/private-filter metadata. A fault test
also demonstrates that a diagnostic consumer exception cannot interrupt speech or
prevent media release. This is defensive isolation, not a diagnosis of the user's
reported clipping. Thirty Node cases passed on feature and agents (gi10-node.log).
Shared changes landed through feature/gptlive 4dd6870 and main 55ab6b6.

Reported audible final-word clipping remains OPEN. No application-side cutoff was
reproduced in the context/media regressions. Provider utterance end is unavailable
and physical audibility remains unmeasured. The manual script now requests the
exact missing word, time, caption comparison and issue marker for the next physical
trial. Synthetic/browser tests cannot close that physical issue.

GI-10 final verification: 18 Java audio/context-delivery unit cases passed on the
shared branch (gi10-unit.log); one real bridge integration and three real-app
browser cases passed on agents (target/gptlive-acceptance-f6012bfc4c). Two browser
cases ran with Live enabled; the legacy text/TTS case ran with Live disabled and
the Live-only case was correctly skipped there. Disposable resources removed.
Packaging and git diff --check passed. No physical headset/robot trial was run.

## GI-11 / Milestone 237 - measured capacity decision (2026-10-22, INCOMPLETE)

Live access was available. A read-only SHOW CREATE USER CURRENT_USER() check
confirmed MAX_QUERIES_PER_HOUR = 36000; credentials/grant text were not retained
in this report. Target headroom is 75%, or 27000 questions/hour. No production
load test, account change or capacity purchase was performed.

Added an opt-in capacity gate to the existing QueryVolumeIntegrationTest. It
uses the disposable-local-MySQL acceptance runner, one connection for complete
MySQL session Questions accounting, real scoped persistence/HTTP/SSE and offline
providers. Measuring SHOW queries are excluded. It reports SQL statements,
transactions, wall-clock duration, scheduling lateness and per-path call/query
counts. It fails on insufficient headroom, independently of per-request regression
ceilings. The test is skipped unless PROMETHEUS_GALA_CAPACITY=true.

Declared load: one Gala instance, one operator cockpit, two SSE streams with one
controlled reconnect per minute, status at 1 Hz, social batch plus face every
five seconds, and a continuously visible hand every 2.5 seconds in busy/post-stop
phases. Busy speech has four user turns and eight native segments per minute,
six durable receipts each. Guessing/give-up/RPS produce state changes. Capture
workers and the bridge are mocked; an explicit context refresh after each work
tick substitutes for background reads. In the busy phase that is every 2.5 seconds,
more frequent than the real bridge's ordinary five-second coalescing window.
After Stop, the test still sends unchanged face/social samples every five seconds;
the browser normally suppresses unchanged samples once Live is inactive. These
are stress assumptions, not measured production request rates. This does not
establish real-provider throughput or a production upper bound. Actual workers
may add work. No sensing
cadence, authorization check or durable write has been removed to meet the gate.

The 15-second busy harness check completed correctly and failed only its capacity
gate (target/gptlive-acceptance-16426bd287, gala-capacity-short-report.json).
The sustained run used 30 seconds quiet Live, 180 seconds busy Live, then 30
seconds camera after Stop Live. All 36 segments and 216 receipts remained durable;
the agent ended in reception with its old voice ownership released. Maximum
scheduling lateness was 263/113/2 ms across the three phases, so overload did not
artificially reduce the offered cadence. Resources were removed after failure.

| Phase | Seconds | MySQL questions | SQL statements | Transactions | Questions/hour | Without reconnect cost/hour |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| live_idle | 30.001 | 683 | 496 | 59 | 81957 | 73557 |
| live_peak | 180.001 | 9993 | 7377 | 840 | 199859 | 195659 |
| camera_after_stop | 30.001 | 706 | 470 | 76 | 84717 | 76317 |

The busy phase used 9993 questions. Its measured path costs were: social batch
1806; face 1269; hand 1683; durable receipts 1512; segment commits 1461; context
refresh 1152; conversation refresh 648; status polls 216; lease checks 36; SSE
connect/reconnect 210. These sum to the measured total. Receipt persistence alone
cost seven questions per fragment (504/minute at this profile), exceeding the entire 450/minute headroom target before other work.

Prior reductions are still present: b9e693d reduced polling/sensor graph work;
b56a423 added collection batching and scalar/bulk receipt checks. The current
receipt path retains current scope/epoch, duplicate checking and durable insertion
inside its transaction. The old approximately 1996-question mixed-minute regression
was already documented as exceeding capacity; the new test adds hands, streams,
conversation refresh and wall-clock persistence growth. No newly discovered stray
poller accounts for the capacity gap.

The measured busy stress profile alone requires at least 266479 questions/hour
to occupy at most 75% of a quota. The earlier 300000/hour suggestion was an estimate
for this artificial profile, not an established production requirement or purchase
recommendation. Calibrate browser/worker cadence and profile persistence costs
before choosing capacity. Additional active instances/pages or more fragmented
speech need separate allowance. Stop Live does not itself disable camera emission,
but the measured post-stop phase is not representative of an unchanged scene.

Initial decision request (superseded by the user's fixed-quota/local-first direction below): increase database capacity while preserving
current behaviour, or authorize a larger capture/sensing persistence and batching
optimisation milestone. The latter must retain durable receipts, immediate access
checks, game/task correctness and existing sensing cadence. No such tradeoff or
purchase is inferred. GI-11 remains INCOMPLETE; GI-12 has NOT STARTED.

Artifacts: target/gala-capacity-report.json and
 target/gptlive-acceptance-6ae89040e7/java.log. The capacity test failed its one
headroom assertion with zero runtime errors; this is not a passing release gate.
GI-11 benchmark/docs remain uncommitted pending the user's capacity decision.
GI-05 through GI-10 are committed/pushed; latest agents commit 9ce55da. Its GitHub
deployment workflow completed successfully (actions/runs/39736950905). A separate
Heroku CLI release check timed out, so no release-number/health claim is made.

The ordinary query regression run (capacity opt-in disabled) finished with nine
passing cases, one failure and one intentional skip across QueryVolumeIntegrationTest
and LiveQueryBudgetIntegrationTest. The older accelerated Gala mixed-minute case
used 2104 questions against its unchanged 2100 ceiling (1492 prepared statements).
This smaller regression is OPEN as well; the ceiling has not been relaxed. Scalar
receipt/epoch/auth checks, bounded context loading, idle polling, transaction
release and revocation cases passed. Evidence:
target/gptlive-acceptance-2962700bdc/java.log. Disposable resources removed.
The new capacity test compiles and its sustained run completed all durability,
state/ownership and timing checks before failing its deliberate headroom assertion.
No production optimisation is claimed in GI-11 and no GI-11 changes were pushed.

### Matched baseline investigation (2026-10-22)

At the user's request, separated a broader benchmark from a behavioural regression.
Created an isolated detached worktree at pre-GI-01 revision 38e1fc0 and copied the
identical current QueryVolumeIntegrationTest into it. Used the same local non-secret
settings and disposable-local-MySQL runner, offline providers, and identical input
counts/durations. No production load or application change was performed.

| Workload | Pre-improvement 38e1fc0 | Current 9ce55da | Increase |
| --- | ---: | ---: | ---: |
| Original accelerated mixed minute, questions | 1996 | 2104 | 5.41% |
| Quiet Live stress phase, 30 seconds | 637 | 683 | 7.22% |
| Busy stress phase, 180 seconds | 9154 | 9993 | 9.17% |
| Camera-after-stop stress phase, 30 seconds | 700 | 706 | 0.86% |

The original workload reproduced the previously recorded 1996 exactly. Unlike a
wall-clock capacity test it compresses a minute's events into a few seconds, so
time-based stability/cooldown rules can affect comparisons. The busy stress test
used 840 transactions on both revisions; prepared statements rose from 6538 to
7377, matching the 839-question increase. The old agent also fails the quota gate
at approximately 183079 questions/hour under this same stress profile. This is
not evidence that the new improvements created a several-fold increase.

Busy-phase attribution (same route call counts on both revisions):

| Path | Old questions | Current questions | Difference |
| --- | ---: | ---: | ---: |
| Social samples | 1584 | 1806 | +222 |
| Facial samples | 828 | 1269 | +441 |
| Hand samples | 1653 | 1683 | +30 |
| Segment processing | 1315 | 1461 | +146 |
| Fragment receipts | 1512 | 1512 | 0 |
| Context refreshes | 1152 | 1152 | 0 |
| Conversation refreshes | 648 | 648 | 0 |
| Status polls / lease checks / stream setup | 462 | 462 | 0 |

Face/social processing accounts for 663/839 (79%) of the increase. GI-06 stores
stability candidates and baselines; presence/face handling also rewrites unchanged
baseline values. StorageEntry.setValue always changes its write token, so an
unchanged logical value still dirties its row. This is a concrete optimisation
candidate, not proof that all 663 extra statements are unnecessary. Additional
outcome/continuation state also costs writes. Do not globally change write-token
semantics: background actions use them to reject obsolete results. Prefer targeted
Gala no-change handling, with freshness and concurrent-action regression coverage.

Neither benchmark exercises the GI-05 co-created-task semantic association job.
Gesture output is NONE, so GI-08 decorative-gesture history is not exercised.
GI-09/10 diagnostics introduced no extra database poller. Existing earlier query
reductions (b9e693d, b56a423) remain effective. The original 4731-to-1996 reduction
was already documented as insufficient for sustained use on the 36000/hour quota.

Next investigation priorities: calibrate the stress profile against actual browser
deduplication/context scheduling; isolate redundant Gala state writes; then examine
sensor graph loading/commits and durable fragment overhead. Retain scope revocation,
receipt durability, fresh game cues and per-event correctness. The current evidence
does not justify weakening those guarantees or choosing a paid tier yet.

Baseline verification: two selected tests, one pass and one expected capacity
assertion failure, zero errors. All durability/state/ownership checks completed;
the disposable schema/account were removed. Baseline report copied to
target/gala-capacity-baseline-38e1fc0-report.json. Original baseline logs remain in
the isolated prometheus-gala-query-comparison-20261022 worktree under
target/gptlive-acceptance-f9cfd212a1/java.log. Current measurements are the earlier
GI-11 runs documented above. No production fix, threshold change, commit or push
was made during this investigation.

### Fixed-quota follow-up and local-first acceptance (2026-10-22)

The user confirmed that the Heroku account quota cannot be increased and authorised
removing redundant Gala writes, calibrating the benchmark, examining sensing and
fragment costs, then continuing local functional acceptance. Capacity remains an
independent OPEN production gate; local MySQL does not establish Heroku readiness.
No sensing interval, durable transcript contract, authorization check or quota
target was weakened. Larger persistence changes remain a measured follow-up after
local integration acceptance, with no paid-tier alternative.

GalaDinnerMemory.put now avoids assigning an equal JsonPrimitive. This is scoped
to the Gala blocking actions: shared Storage/StorageEntry write-version semantics
and generic agents are unchanged. Fresh observation times, changed counts/baselines,
new occasion IDs and expiry updates continue to write. Two focused unit cases
cover stable fact versions with refreshed TTLs, changed observations/departure,
idempotent reception and distinct identities for repeated requests with equal facts.

Verification before the extended GI-12 journey: 83 focused Gala unit cases,
19 disposable-MySQL cases (plus the intentionally skipped opt-in soak), and 17
real browser-emitter Node cases passed. The original mixed-minute test now uses
2088 Questions / 1476 prepared statements, below its unchanged 2100 ceiling and
down from 2104. Artifacts: target/gi11-state-writes-unit.log,
target/gptlive-acceptance-23851e0f4e/java.log, target/gi11-calibration-js.log.

Benchmark calibration adds an explicit default reception profile while retaining
the original stress profile for matched comparisons. Reception uses stable Live
social/face samples every five seconds, a five-second hand hold after RPS starts
(three samples per minute), five-second context coalescing with urgent game
occasions, and one controlled reconnect at busy +60 seconds. After Stop, one
changed social/face sample at +10 seconds is emitted; unchanged samples and an
absent hand emit nothing. Context scheduling is still approximated with explicit
reads and offline workers; fragment count remains synthetic. Real provider/worker
load and actual reception activity require separate measurement. Complete cycles
of 60--600 seconds are required so the final game outcome can be asserted.

| Profile / phase | Seconds | Questions | Prepared statements | Transactions | Equivalent/hour | Excluding stream setup/reconnect/hour |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Reception / quiet Live | 30 | 668 | 481 | 59 | 80160 | 71760 |
| Reception / busy Live | 180 | 7846 | 5747 | 673 | 156919 | 155519 |
| Reception / camera after Stop | 30 | 65 | 41 | 8 | 7800 | 7800 |
| Original stress / busy Live | 180 | 9900 | 7284 | 840 | 198000 | 193800 |

The matched code saving is 93/9993 (0.93%) in the stress workload. Its social,
face, hand and segment costs fall by 63, 18, 3 and 9 respectively; other paths
are unchanged. The additional 2054-question difference between stress and reception
is entirely workload calibration: fewer hand samples (1386), context reads (528)
and reconnects (140). Do not report it as a production optimisation.

Both opt-in tests completed all 36 segments/216 receipts, final reception and
ownership-release assertions, then failed only the unchanged 27000/hour headroom
gate (zero errors). Maximum scheduling lateness was 79/1/1 ms for reception and
344/247/2 ms for stress. Disposable databases/accounts were removed. Artifacts:
target/gala-capacity-reception-report.json, target/gala-capacity-stress-report.json,
target/gptlive-acceptance-30fdb2ff0f/java.log and
target/gptlive-acceptance-ddf490b494/java.log.

Persistence cost examination used per-route Hibernate operation counters plus
opt-in, synthetic-only SQL logging delimited by CapacityMeter. SQL attribution
contains table/operation counts, not speech or credentials, in
target/gala-sql-attribution.json. Concrete findings:

- Every scoped receipt costs four SQL statements: enabled access/link check,
  persisted execution-epoch check, duplicate receipt lookup and durable insert.
  Three transaction-control Questions bring this to seven. At 72 fragments/minute,
  receipts alone cost 504/minute, already above the 450/minute headroom target.
  Combining authorization/epoch checks is a candidate for preserving immediate
  revocation with fewer reads; a permission cache is not. Durable batching would
  need explicit commit/ACK, crash, ordering, duplicate and failure guarantees.
- Sensor batches already share one loaded persistence context but retain per-event
  validation, loading/saving transactions and commits. In the calibrated busy phase,
  36 three-event social batches use 216 transactions and 1743 Questions; 36 face
  observations use 72 transactions and 1251 Questions. These are not 36 standalone
  inserts. Any batch transaction redesign must retain failure isolation, event
  ordering and immediate scope checks.
- Storage.entries is an unidirectional collection. Trace evidence includes
  `delete from storage_entries where storage_id=?` followed by re-inserting links
  when keys are added/removed. Across all stress phases the face path alone issues
  402 storage-link inserts and 20 collection deletes, among 1264 ORM statements.
  Avoiding equal value assignments does not fix this collection churn. A targeted
  ownership/mapping or stable-state representation change deserves profiling and
  a migration/compatibility review; it is not implemented here.
- Context/history reads still load the agent graph and historical entities even
  for bounded projections. Preserve the prior fetch batching and turn-context
  reductions; investigate narrower reads without caching permissions or silently
  dropping audit history.

These findings justify proceeding with local functional acceptance while keeping
a specific persistence-efficiency task open. They do not support claiming the fixed
quota is adequate, nor deferring its resolution until the actual gala reception.

Local full-suite verification after the write cleanup and acceptance-fixture fixes:
827 Java cases, 826 passed and the opt-in capacity case intentionally skipped;
zero failures/errors. All 122 Live/transcription/speech/performance Node cases
passed. Four real-app browser cases passed (three Live-enabled and one disabled
text/TTS path; two Live-only cases intentionally skipped when disabled).
The initial full run exposed one outdated source-timestamp fixture and two local
connection-limit errors from cached test contexts. Shared test-only fixes on
feature/gptlive (56921b1, 914d036) flowed through main (351d228) into agents
(ad0ae7b); the final complete rerun passed. Production pool settings are unchanged.
Artifacts: target/gptlive-acceptance-7a9877e0c1/java.log (initial failures),
target/gptlive-acceptance-1c71a40826/ (final Java/browser evidence),
target/gi12-node-acceptance.log. Both runners removed their disposable resources.

## GI-12 / Milestone 238 - local integrated acceptance (2026-10-22)

LOCAL AUTOMATED ACCEPTANCE COMPLETE; full release acceptance INCOMPLETE. Under
the user's revised local-first sequence, the failed GI-11 capacity gate did not
block functional integration. GI-11 local work was committed/pushed as 6d68efd;
resource efficiency remains required against the fixed account quota.

Extended the existing canonical/compact Gala journey, retaining its two-visitor
isolation and game/guess/reveal/gesture/task coverage. A malformed control response
now explicitly exercises failure without a ready game, no sensor-triggered retry,
and a fresh explicit request that succeeds. The second visitor's task is ON_CUE:
activation and a nonmatching cue spend no actions; two fresh matching cues produce
two actions with confirmed response association, then return to reception. The
first visitor still covers the legacy IMMEDIATE default. No production speech
policy or task semantics were changed for this acceptance work.

The focused cross-agent acceptance (Gala, Generic, Social Space, context delivery
and Gala delivery) passed all 27 cases. Final all-Java acceptance passed 826/826
executed cases, with only the opt-in capacity soak skipped. All 122 JS cases passed.
Real-app browser acceptance passed three Live-enabled cases and the feature-disabled
text/TTS case, with two intentional Live-only skips when disabled. Packaging and
git diff --check passed. This verifies persistence/reload, ownership/revocation,
late events, quiet/urgent context delivery, legacy speech modes and local lifecycle
contracts. It does not verify that scripted speech was physically audible.

Commands/artifacts:

- Focused: run_acceptance.py with GalaDinnerReceptionJourneySmokeIntegrationTest,
  GenericMultimodalTaskIntegrationTest, SocialSpaceLiveJourneySmokeIntegrationTest,
  LiveContextDeliveryUnitTest,GalaDinnerLiveDeliveryUnitTest. Evidence:
  target/gptlive-acceptance-cbba7bc722/java.log.
- Final: python tests/gptlive/run_acceptance.py --database-properties
  src/main/resources/application-test.properties --java-tests all --browser
  --live-only --browser-grep 'real scoped cockpit|Live Multimodal exposes sensors|Generic activation telemetry'
  with PROMETHEUS_GALA_CAPACITY=false. Evidence: target/gptlive-acceptance-1c71a40826/.
- node --test tests/js/live/*.test.mjs tests/js/transcription/*.test.mjs
  tests/js/speech/*.test.mjs tests/js/performance/*.test.mjs.
- mvnw.cmd -q -DskipTests package (target/gi12-package.log).

Updated the manual script with local build/database identification, a concise
simple-language/child path and separate headset/robot output evidence. The final
three-fresh-session provider matrix has NOT RUN on this combined version. Earlier
provider acknowledgement/wording issues and reported acoustic clipping remain
OPEN; physical reception/robot and final-word tests remain NOT RUN. Capacity
headroom remains FAILED under the explicit local load profile. Do not describe
all twelve milestones or the production reception as fully accepted.

Rollout scope: no schema/configuration migration, instance rewrite or production
database test. Use a fresh Gala instance for final trials. The prior deployed code
baseline is 9ce55da; a rollback should use the corresponding previously verified
Heroku release after checking its release identity, without deleting audit history
or resetting shared branches. Commit/push follows the user's standing instruction;
successful deployment alone cannot close these remaining gates.

## GI-11 / Milestone 239 - persistence efficiency (2026-10-22)

Implemented and verified the next database-efficiency slice. GI-11 capacity is
still INCOMPLETE. No production database/load test, quota increase, sensor cadence
change, permission cache, buffered transcript durability or saved-instance rewrite.

Shared changes: feature/gptlive 453748a and 1305c57 -> main 1cc13d9/dc05c43 ->
agents c566f5d/f588577. Newly admitted Live fragments use one atomic autocommitted
MySQL INSERT ... SELECT, validating enabled scope/link, persisted epoch and absence
of the receipt identity in that statement. Local ownership remains a fence, not
authorization. Outer transactions are suspended; later processing rollback cannot
erase an accepted receipt. Duplicate/obsolete admission gets one additional check
to release obsolete owners. Segment claims/processing remain separate transactions.
The default autocommit pool is required; a non-autocommit connection fails closed.

Storage entries use set semantics on the same join-table columns, changing only
the affected links. Eager SELECT fetching prevents row multiplication across
agent/policy/state collections. Explicit same-value writes still change versions
used to fence background actions. Tests cover legacy join-table shape, reload,
detached merge/removal and bounded link writes with 100 entries. The complete-history
endpoint reads history/events/paths directly; authorization, fresh data, all event
identities/order, state paths and native provenance are preserved.

### Functional verification and discovered regressions

- Focused shared persistence: 13 cases passed after correcting a preliminary
  JPA/JDBC synchronization issue and two new test fixtures. Shared final fetch/history
  follow-up: 16 focused cases passed. Detailed commands/artifacts and limits are in
  GPTLIVE_RESULTS. Scoped and trusted new-receipt admission each measure exactly
  one MySQL Question, including durability and persisted authorization/epoch guards.
- The first agents full run exposed excessive heap use in the core RPS cockpit
  smoke with the preliminary set mapping. SQL evidence showed storage entries
  joining the agent graph and six storage collections in one policy query. The
  explicit SELECT fixes that query shape; a regression rejects such graph joins.
  The owned failing JVM was stopped and the runner removed its disposable schema
  and account. This was a failed run: target/gptlive-acceptance-48520c915b/java.log.
- Final integrated run: 832 Java cases, 831 passed, one intentional opt-in capacity
  skip, zero failures/errors. Counted only XML reports from this run (29 stale
  report files excluded). Four real-app browser cases passed: three Live enabled
  and one feature-disabled Text/Speech case; two Live-only cases skipped when disabled.
  Command: run_acceptance.py --database-properties src/main/resources/application-test.properties
  --java-tests all --browser --live-only --browser-grep
  'real scoped cockpit|Live Multimodal exposes sensors|Generic activation telemetry'.
  Evidence: target/gptlive-acceptance-28fc8a7fff/ and target/gi11-final-java-counts.json.
- The shared full suite before integration has a separate pre-existing static
  client contract failure: its script includes embodiment, while its test expects
  the older request shape. Its 555 passes/one failure are recorded in GPTLIVE_RESULTS;
  the agents branch's matching embodiment contract passes. No unrelated client
  contract was changed to hide that failure.
- Tightened the accelerated mixed-minute ceiling from 2,100 to 1,700. It measured
  1,569 in the full run and 1,571 in the final one-case rerun (minor timing-sensitive
  variation), versus 2,088 before this slice: about 25% less. Evidence:
  target/gptlive-acceptance-42bd00bddc/java.log. The final command selects
  QueryVolumeIntegrationTest#galaMixedMinuteHasBoundedQueriesAndPreservesGameAndCapture.
  Packaging (`mvnw.cmd -q -DskipTests package`, target/gi11-package.log) and
  git diff --check passed. The capacity target was not relaxed.

### Matched reception capacity measurement

Same one-instance/one-cockpit reception workload as Milestone 237: 30 seconds quiet
Live, 180 seconds busy Live, 30 seconds camera after Stop. No profile calibration
change in this comparison. Every game/capture functional assertion passed: all 36
segments COMPLETE with six receipt identities each, final reception and released
ownership. The one capacity case FAILED only the unchanged 27,000/hour assertion.

| Phase | Earlier Questions | New Questions | New hourly equivalent | New transactions |
| --- | ---: | ---: | ---: | ---: |
| Quiet Live, 30 s | 668 | 624 | 74,878 | 58 |
| Busy Live, 180 s | 7,846 | 5,469 | 109,380 | 421 |
| Camera after Stop, 30 s | 65 | 65 | 7,800 | 8 |

Busy load falls **30.3%**, with unchanged calls and 1 ms maximum scheduled lateness.
Excluding the one SSE reconnect gives 107,980/hour. Quiet phase maximum lateness
was 262 ms, after-Stop 1 ms. These are local synthetic hourly equivalents, not
measured production throughput or a bound on actual provider fragmentation.
The stress profile was not rerun in this slice.

| Busy path | Calls | Earlier Questions | New Questions |
| --- | ---: | ---: | ---: |
| Social sample (three observations) | 36 | 1,743 | 1,587 |
| Facial sample | 36 | 1,251 | 885 |
| Hand sample | 9 | 294 | 273 |
| Fragment receipt | 216 | 1,512 | 216 |
| Segment commit | 36 | 1,452 | 1,418 |
| Live context refresh | 39 | 624 | 624 |
| Complete history refresh | 36 | 648 | 144 |
| Status poll | 180 | 216 | 216 |
| Lease check | 36 | 36 | 36 |
| SSE reconnect | 1 | 70 | 70 |

The 2,377 saved Questions comprise 1,296 from receipts, 504 from history and 577
across sensor/segment persistence. Hibernate reports 3,982 prepared statements
and no receipt entity inserts because the writer uses JDBC. Actual Questions
include those 216 durable inserts; zero Hibernate statements is not zero SQL.

Command: PROMETHEUS_GALA_CAPACITY=true, PROFILE=reception, SECONDS=180, then the
disposable runner with --java-tests 'QueryVolumeIntegrationTest#galaReceptionCapacitySoak'
(environment keys use the full PROMETHEUS_GALA_CAPACITY_ prefix as in tests/gptlive/README).
Evidence: target/gptlive-acceptance-479a52651b/java.log,
target/gala-capacity-reception-report.json; prior comparison preserved as
target/gala-capacity-reception-before239.json. Runner resources were removed.

### Remaining capacity work

The 109,380/hour result is still over the fixed 36,000 quota and requires about
75% further reduction to reach 27,000/hour headroom. Sensor turns contribute 2,745
Questions (50.2%); segment processing adds 1,418 (25.9%). Live context is another
624 (11.4%). Receipts are now only 3.9%; further receipt micro-optimizations cannot
close this gap. Quiet Live also exceeds the target independently of speech.

The next design should address repeated graph reads and the read/save transactions
around sensor and segment processing. Preserve per-event failure/commit isolation,
source freshness, provider-wait connection release, durable transcripts and immediate
revocation. Any proposed changed transaction/retention contract needs an explicit
compatibility decision, including recovery behavior and saved-instance implications;
do not silently introduce long-lived runtime caches or merge event commits.
Final provider/physical/clipping gates also remain open. No Heroku readiness claim.


## GI-14 / Milestone 241 - Live speech feasibility (2026-10-22)

Implementation complete; strict provider acceptance FAILED and remains open.
After reviewing the failure, the user explicitly chose "Continue remaining fixes;
retain the limitation". Proceed to GI-15–17 without audio gating or a change to
full duplex. The prompt refactor is guidance, not an enforced speech boundary.

Gala now separates Backchannel, Interruption and Delegation policies, explicitly
listens through a paused nonbinary question, distinguishes replay readiness from
an old result, and treats Okay as acknowledgment rather than goodbye. Live retains
wording; backend admission/occasions and database traffic are unchanged. The
existing compact state/occasion contract is retained. No extra delegate controller.

The opt-in tests/gptlive/gala_provider_trial.mjs exports no credentials and uses
cached synthetic TTS input, gpt-live-1/voice marin over WebRTC, actual application
context commands and sequential append ACKs. It waits five seconds after activity
request audio before sending confirmation. A scheduled 1.2-second within-question
pause measured about 1.63–1.69 seconds including browser/transfer overhead. It does
not run the app/database or reproduce the backend's exact grouping/coalescing.
Times below are caption/command receipt timings, not acoustic alignment.

| Trial | Observations |
| --- | --- |
| Baseline | Waiting/status remarks before each start; Ja to the first question part, then Nein to the second; reveal before outcome update and duplicate after it. |
| Candidate 1 | No pre-update RPS/guess invitation; waits through the question and clarifies. Still narrates return to reception and says Einen Moment before reveal. |
| Candidate 2 | Replay invitation starts about four seconds before the update; claims person selection before start; Ja before second question part, then clarification; duplicate reveal. |
| Candidate 3 | No invitation before initial updates; waits through question but quotes part of it when clarifying. Reveal begins before the outcome update and repeats afterwards. |

No candidate passes all criteria (0/3). Do not call this a consistent improvement
or readiness/reveal fix. The first candidate's initial confirmation batch took
about 11.2 s, versus roughly 2.5–3.6 s for most other batches; this small sample
cannot attribute that provider/ACK delay to the prompt. First RPS backend delay
was deliberately synthetic; these values are not production latency predictions.
All four sessions closed successfully. Raw captions and markers remain private
in target/gi14-baseline-results.json and target/gi14-candidate-{1,2,3}-results.json;
matching fixture snapshots and cached audio stay in target/. No failed sample
was discarded or rerun to obtain a favourable result.

Verification: 72 focused unit cases passed, and the two canonical/compact Gala
journey cases passed on disposable local MySQL, cleaned up by the runner.
Commands: mvnw.cmd -q -Dtest=GalaLiveFollowupUnitTest,GalaDinnerReceptionUnitTest,
GalaDinnerReferenceUnitTest,GalaDinnerTaskUnitTest,GalaDinnerLiveDeliveryUnitTest test
(single comma-separated argument); run_acceptance.py --database-properties
src/main/resources/application-test.properties --java-tests
GalaDinnerReceptionJourneySmokeIntegrationTest. Logs: target/gi14-unit.log and
target/gptlive-acceptance-a7d4cb1329/java.log. Initial oversized prompt revisions
failed the headroom assertion and were shortened; final maximum is 14,399/16,000
bytes in both embodiments/all tested states, below the unchanged 14,400 test cap.
Node syntax and git diff checks pass. Synthetic journeys are not provider acceptance.

Provider references reviewed: https://developers.openai.com/api/docs/guides/live-prompting
and https://developers.openai.com/api/reference/resources/live/sideband-websocket .
The documented acknowledgement delay is not enforced silence; silent context is
not a secrecy boundary. The current browser plays a live remote track directly;
muting it would discard speech rather than safely defer it. No such workaround
was introduced. Final acoustic/robot acceptance and GI-11 capacity remain open.


## GI-15 / Milestone 242 - post-reveal conversation (2026-10-22)

Direct repetition/confirmation of the just-revealed name now returns locally,
without another classifier call, gesture or recovery occasion. It preserves the
outcome identity and original 30-second expiry. The active guessing path and stop
commands still take precedence; after expiry the ordinary classifier runs again.
The existing operation list now supplies both the current-mode prompt alternatives
and validation. Mode-specific rules omit other activities' control vocabulary.
Other follow-up questions receive bounded, already-public completed-round facts;
the classifier still receives no active secret card. There are no new storage
keys, database reads, polling or inference requests. Native speech remains Live's.

Replaced GI-13's false-recovery characterization with acceptance for bare/prefixed
name confirmations. Added expiring-context, scoped operation and invalid-operation
regressions, and extended both persisted canonical/compact journeys with a repeated
name and follow-up question. Invalid control still preserves prior state; genuine
failed starts retain existing recovery. Arbitrary free-form model classification
can still be wrong; this is not a guarantee that every follow-up is understood.

Verification: 73 pure unit cases passed with mvnw.cmd -q
-Dtest=GalaLiveFollowupUnitTest,GalaDinnerReceptionUnitTest,GalaDinnerReferenceUnitTest,GalaDinnerTaskUnitTest test.
Both GalaDinnerReceptionJourneySmokeIntegrationTest cases passed via the disposable
local-MySQL runner with --database-properties src/main/resources/application-test.properties.
The runner removed its schema/account. Logs: target/gi15-unit.log and
target/gptlive-acceptance-ceae4b61d7/java.log. Instruction maximum unchanged at
14,399 bytes; git diff --check passed. No new paid-provider or physical trial in
this milestone. GI-14's explicitly accepted prompt-only speech limitation remains.


## GI-16 / Milestone 243 - isolated input activity (2026-10-22)

Shared opt-in implementation: feature/gptlive 89fa527, main b559eee, agents merge
 e7030ff. Gala reception and task policies opt in for fresh Live sessions; all
reachable states are checked. No default core behaviour change. The shared filter
requires 40 ms consecutive RMS >500 before resetting input quiet; pending onset
blocks a user boundary. Isolated 20-ms spikes no longer merge the three synthetic
questions. The two-second Gala window, assistant timing, raw receipts/diagnostics,
coverage uncertainty and Stop remain intact. Details are in GPTLIVE_RESULTS.

Verification: shared 52 focused units and 22 boundary cases (including 15 isolated
integrations) passed. Agents then passed 24 focused cases with
-Dtest=GalaLiveFollowupUnitTest,GalaDinnerReferenceUnitTest,LivePcmActivityUnitTest,LiveTranscriptCaptureServiceUnitTest.
The two Gala journey cases and QueryVolumeIntegrationTest#galaMixedMinuteHasBoundedQueriesAndPreservesGameAndCapture
passed on disposable local MySQL. Mixed-minute cost is 1,571 Questions, unchanged
from the earlier final sample and below the unchanged 1,700 ceiling. Evidence:
target/gi16-gala-unit.log and target/gptlive-acceptance-89638621c5/java.log.
Disposable schema/account removed; git diff --check passed. No new paid-provider
trial: this filter affects backend capture, not Live's independent acoustic policy.

Remaining limits: RMS is a coarse heuristic. A real very short sound can resemble
a spike; long noise can resemble speech. Above-threshold quiet speech, split packets,
trailing onset, gaps and Stop are synthetic-tested, not physically accepted. Raw
fragments are still durable; late/uncertain speech is not promoted into a valid
control request. Correctly separating actual questions may appropriately create
more backend turns than the defective merged capture; this is not a capacity fix.
GI-11 fixed-quota capacity, physical clipping and GI-14 prompt-only limitations stay open.


## GI-17 / Milestone 244 - sensory delivery and final acceptance (2026-10-22)

Shared implementation: feature/gptlive b519715, main ab432fe, agents integration
d9ae2a2. Shorter context headers and 440-byte payload parts retain the conservative
480-byte append bound. Current face evidence follows social context before derived
summaries. New commits can replace obsolete unsent observations at the existing
five-second refresh boundary, after completing the current multipart observation.
The packing loop respects that same boundary. No change to source timestamps,
expiry, sampling, permission checks or the database refresh interval. Delivery
still checks expiry after each ACK; a slow provider cannot make old data fresh.

Shared focused tests and the seven initial local integrations are recorded in
GPTLIVE_RESULTS. The delayed-ACK regression delivers the representative full
sensory batch in at most five appends, with face evidence sent within six seconds
under simulated two-second ACKs and all facts inside their original 15-second TTL.
This is a synthetic bound for that fixture, not a production latency guarantee.

Final integrated tree d9ae2a2 passed:

- 847 Java cases, zero failures/errors; one opt-in capacity soak skipped. This
  includes both Gala and Social Space journey variants, the five Generic task
  integration cases, capture/durability, revocation/Stop and context delivery.
- Four application/browser cases: three with Live enabled, and the legacy
  feature-disabled path. Two Live-only cases correctly skip when disabled.
- 97 Node cases: Live 47, transcription 29, output speech 21.
- Packaging with tests skipped after the full acceptance run; git diff checks.
- Mixed-minute regression: 1,569 database Questions under the unchanged 1,700
  ceiling. No production database access; disposable local schema/account removed.

Commands: python tests/gptlive/run_acceptance.py --database-properties
src/main/resources/application-test.properties --java-tests all --browser
--live-only --browser-grep 'real scoped cockpit|Live Multimodal exposes sensors|Generic activation telemetry';
npm run test:live:unit, test:transcription:unit and test:speech:unit;
mvnw.cmd -q -DskipTests package. Evidence: target/gptlive-acceptance-2e890a9cd2/
and target/gi17-{live-node,transcription-node,speech-node,package}.log. The first
PowerShell packaging invocation stopped on a JVM stderr warning; running the
same command through cmd with native redirection completed successfully.

Final provider evidence is recorded below. The manual GALA.md script now covers
post-reveal name/fact follow-ups, separate noisy turns with quiet trailing speech,
and fresh/expired social and facial evidence. Physical audibility, real-camera
accuracy, robot gesture execution and GI-11 fixed-quota capacity remain open.
The user accepted continuing with GI-14's prompt-only speech limitation; no
audio gating, changed full duplex or second controller was introduced.

### Final provider matrix

Three fresh gpt-live-1/marin sessions used the final exported application fixture
and the same six synthetic German clips/delayed confirmations as GI-14. All
sessions closed successfully. Transport success is not behavioural acceptance.
The following observations are from provider captions, not recorded acoustics:

| Trial | Observations |
| --- | --- |
| Final 1 | Waiting remarks before game confirmations. Answers Ja before the second half of the nonbinary question, then clarifies. One reveal after the outcome update starts. |
| Final 2 | Waiting remarks on replay and give-up. No premature Ja, but only Einen Moment instead of a substantive clarification in the observation window. One reveal after the outcome update starts. |
| Final 3 | Fillers/waiting remarks, including Warte mal kurz during the question pause. Volunteers that the target is a real historical man at game introduction; then correctly asks for a binary question. One reveal after the outcome update starts. |

Strict acceptance is still 0/3. No game invitation before its update or duplicated
reveal was observed in these final samples; earlier GI-14 violations still stand.
Do not infer consistent readiness, secrecy or complete-question behaviour from
this small sample. First/replay RPS results were captioned correctly in all three.
The question's scheduled 1.2-second gap measured 1.63–1.64 seconds including audio
transfer overhead. Confirmation batches took 1.61–3.56 seconds from first append
to final ACK. These sequential-append probes do not reproduce backend packing,
coalescing or noisy capture and cannot establish their production latency benefit.

Commands: node tests/gptlive/gala_provider_trial.mjs --allow-paid-provider
target/gi17-final-fixture.json target/gi17-final-N-results.json, for N=1,2,3.
Fixture, event/caption logs and audio cache remain ignored under target/. No
failed sample was discarded, and no repeat was added to obtain a favourable
result. This closes the implementation/automated follow-up sequence under the
user's accepted limitation, not the outstanding provider/physical/capacity gates.

## GI-18 / Milestone 245 - active-game acknowledgements (2026-10-22)

Whole-utterance neutral acknowledgements now bypass classification inside GUESS
and RPS. Target, sign, round, start time and pending-occasion identity/expiry remain
unchanged. No extra speech or inference is created. Reception choices, task
acceptance, compound requests, explicit exits and ordinary yes/no retain their
existing paths. A narrow explicit give-up-without-disclosure rule ends quietly;
acknowledgement-prefixed actual give-up can reveal, with negation/hypothetical
guards preserved. Content-free gala_control stages record mode/operation/reason
and input identity without overwriting waiting-cue telemetry. No schema, extra
database read, per-frame inference or default-core change.

Verification:

- 78 unit cases passed: mvnw.cmd -q
  -Dtest=GalaLiveFollowupUnitTest,GalaDinnerReceptionUnitTest,GalaDinnerReferenceUnitTest,GalaDinnerTaskUnitTest test.
  Evidence: target/gi18-unit.log. Instructions remain within the existing cap.
- Both canonical/compact persisted Gala journeys passed through
  tests/gptlive/run_acceptance.py --database-properties
  src/main/resources/application-test.properties --java-tests
  GalaDinnerReceptionJourneySmokeIntegrationTest. Final tree evidence:
  target/gptlive-acceptance-83092b3a57/java.log; disposable schema/account removed.
- Twelve real backend classifications used actual exported application requests:
  11 matched expected operations. "Ich gebe auf, aber verrate den Namen nicht"
  incorrectly produced WAIT (no disclosure, but no exit). This now bypasses the
  classifier and is regression-tested. A further free-form exit paraphrase,
  "Ich will aufhören; den Namen möchte ich nicht wissen", produced RETURN.
  No failed sample was discarded or relabelled. Private evidence:
  target/gi18-control-provider-results.json and
  target/gi18-control-provider-paraphrase.json. No speech-quality claim follows
  from this classifier check; arbitrary paraphrases remain model-dependent.
- git diff --check passed. No production database load or physical trial.

GI-19 follows under the approved consecutive plan. The accepted GI-14 limitation,
physical audio/camera quality and GI-11 capacity remain open.

## GI-19 / Milestone 246 - short replies and stale recovery (2026-10-22)

Shared implementation 18b15e6 plus 4617920 follows feature/gptlive -> main ->
agents. The application-owned acknowledgement hook defaults false; Gala opts in
only where its action guarantees no control. Active-game acknowledgements and
neutral receipt/thanks after a fresh completed round preserve state and outcome
expiry. Affirmative reception choices, task acceptance, bare yes/no, expired
outcomes and compound requests retain normal context checks. Durable receipts,
input observations and transcript reasons remain unchanged.

Unsent recovery retains segment/source-state/occasion and a ten-second deadline.
Later admitted non-bare user input supersedes older recovery; assistant/incomplete
input and routine sensing do not. Recheck follows provider waits, including an
occasion change within the same state. Queue/deduplication remain bounded; no
inference, database poll or new receipt query is introduced. Live is asked to
clarify the intended choice without claiming broken audio or missing words.
Already-sent provider speech cannot be unsent.

Verification:

- Shared 35 units and 13 local boundary/query cases passed; the additional
  occasion race passed in the 26-case delivery/clarification rerun. Commands and
  worktree evidence are in GPTLIVE_RESULTS (a72ecf6032 and gi19-occasion-unit.log).
- Integrated final focused suite: 105 unit cases passed with mvnw.cmd -q
  -Dtest=GalaLiveFollowupUnitTest,GalaDinnerReceptionUnitTest,GalaDinnerReferenceUnitTest,GalaDinnerTaskUnitTest,LiveClarificationsUnitTest,LiveContextDeliveryUnitTest test.
  Evidence: target/gi19-final-unit.log. All-state instruction cap is unchanged.
- Ten isolated integration cases passed: both Gala variants, five Generic cases,
  both Social Space variants and the mixed-minute query case. Command:
  python tests/gptlive/run_acceptance.py --database-properties
  src/main/resources/application-test.properties --java-tests
  GalaDinnerReceptionJourneySmokeIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,QueryVolumeIntegrationTest#galaMixedMinuteHasBoundedQueriesAndPreservesGameAndCapture.
  Evidence: target/gptlive-acceptance-446c408a9f/java.log. Cost 1,571/1,700 Questions;
  schema/account removed. This run preceded only the final shared occasion fence,
  separately covered by the focused integrated rerun.
- git diff --check passed. No production database or physical/device test.

GI-20 follows. Actual spoken clarification and remaining quality limits are part
of GI-22; this milestone does not close GI-14 or fixed-quota capacity gates.

## GI-20 feasibility - audio-policy decision pending (2026-10-22)

No runtime departure change has been made. The proposed immediate local silence
plus session-preserving grace needs an explicit recovery policy: muting is not
pausing the model. Official sources checked through OpenAI Docs:
https://developers.openai.com/api/docs/guides/live-conversations#control-microphone-input
and https://developers.openai.com/api/docs/guides/voice-server-controls?api=live#control-playback-when-needed.
Input mute leaves generation running; client playback must be controlled
separately. An append ACK is not a safe signal to resume model output.

A synthetic counting probe used a real gpt-live-1/marin WebRTC session, browser
microphone-track disable plus provider input mute/unmute, and local output mute
without detaching the stream. This isolates transport/playback feasibility; it
does not run the Gala backend, database, actual camera or robot. The provider
accepted mute after 171 ms and unmute at 1,845 ms; local playback resumed at
1,850 ms. Captions before suspension contained "eins", during mute "zwei", and
after resumption "drei" followed by subsequent numbers. Remote audio continued
during mute (8/18 sampled 100-ms windows above RMS 0.01) and after resumption.
The same session closed normally. There was no queued replay, but a portion of
the ongoing response was hidden; continuity cannot be claimed. This is synthetic
browser/media evidence, not a human physical-audibility assessment.

Ignored evidence: target/gi20-presence-probe.mjs,
target/gi20-presence-fixture.json and target/gi20-presence-results-2.json.
The first probe timed out because its inherited log filter excluded mute ACKs;
that harness error was corrected and retained as target/gi20-presence-results.json.
It is not counted as an application/provider mute failure. Both sessions were
cleaned up; no production database was accessed.

Decision requested: apply the two-second grace to audio as well (recommended),
allowing brief speech in a possibly empty view, or retain immediate silence and
accept interruption on brief detection loss. Either option retains immediate
operator Stop, explicit camera stop, access revocation, reset/switch and failure
fences. The original no-stale-tail guarantee must not be silently weakened.
GI-18 and GI-19 are pushed (agents 1c4be46); GI-20–22 remain unfinished pending
this decision, as required by the plan's unforeseen-trade-off halt condition.


### GI-20 decision and implementation

The user approved two-second grace for audio/input as well as encounter state.
This supersedes the pending decision above and its immediate-mute requirement.
Brief absence preserves conversation; sustained absence closes once. Stop, camera
shutdown, access loss and failures remain immediate. Raw counts are unchanged;
new sensor actions/gestures and ambient comments are suppressed during grace,
without replay on return. Task agreement/budget survives; user stop/reveal remains
available. Non-opted-in agents keep immediate departure. No extra database polling.

Real provider retry using the actual PresenceReception module: 1,519 ms absence,
one session, zero local stops, zero muted output samples. Seven voiced 100-ms
windows during absence and 54 after; captions continued eins -> zwei -> drei,
without skipping the muted word from the earlier probe. This is synthetic media
evidence, not physical audibility. target/gi20-grace-results-2.json retained.
The first grace probe caught native setTimeout invoked with the wrong receiver;
arrow wrappers fix it. Failed evidence retained as gi20-grace-results.json.

Verification: 101 Gala unit cases and three persisted/budget cases passed in
`target/gptlive-acceptance-78d47664b1/java.log`; both canonical/compact journeys.
The actual agents browser presence lifecycle test passed with brief recovery,
sustained departure and new encounter. The earlier journey assertion raced
asynchronous close cleanup; it now waits for the committed cleanup, preserving
immediate local stop. All failures retained (91d817ee6e and gi20-unit.log).
Instructions remain below the 14,400-byte test ceiling even with absent-presence
facts and maximum task goals; task voice wording was compacted with the same
required lifecycle terms. The true application ceiling remains 16,000 bytes.
Mixed-minute Questions remain 1,571/1,700 (1,103 statements, 9,737 entities).
No production database, source TTL change, schema or new polling. Shared changes
were cherry-picked feature -> main to retain main's linear history, then merged
into agents. Browser template mismatch on shared branches remains pre-existing;
this milestone's complete deployment UI passes. GI-21 follows.
`nRunner note: the browser-enabled selection passed two cases; the subsequent disabled-app selection matched no titles and exited 1 (no application failure). Final GI-22 will run the real scoped cockpit title in both modes.
