# Gala improvement implementation evidence

Roadmap: `.agents/PLAN_GALAIMPROVEMENT.md` on the agents branch. The user authorized
consecutive milestones with a commit/push after each; final user physical trials
are grouped at the end. Synthetic tests do not establish audibility or model compliance.

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
