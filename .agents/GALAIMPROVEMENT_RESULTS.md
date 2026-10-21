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
