# GPT-Live repair evidence

## GLF-01 — Mode contracts and Continuous parity (2026-11-18)

Status: **verified; stopped for review**. GLF-02–05 remain open. This milestone does not
restore native Coordination speech or establish provider/physical acceptance.

Baseline: clean `main` at `e66aa3cd` (the roadmap/handoff commit, after `cff6818a`).
Work stays in the existing checkout. No production configuration, study code,
commit, push, deployment or paid provider call is included.

### Contract and implementation scope

Continuous uses the shared `TranscriptionEventRuntime` and
`ScopedTranscriptIngress`: only finalized, ordered provider items become scoped
`obs.user_utterance` requests with `profile=full_plan`. The ordinary backend owns
the agreement and response. Behaviour SSE supplies persisted IDs to the existing
Speech queue; `ScopedBehaviourSpeechService` resolves the exact canonical text.
PCM/MP3 streaming, output settings, deduplication, intentional startup replay and
microphone gating retain their existing implementation.

Live is a different contract: native ordinary speech must eventually play while
backend work proceeds asynchronously, with meaning-preserving wording variation.
Agreement, preparation, consent, budgets, response identity and outcomes remain
backend-owned. GLF-01 establishes that distinction in acceptance scenarios and
the assertion map below; the November committed-Speech route still runs until
GLF-02 changes it. No existing strict assertion has been weakened.
No Continuous runtime defect was demonstrated by these checks; production
speech routing and task code are unchanged. The repaired gap is executable
Coordination Continuous coverage, plus acceptance-build isolation.

Added `valerian-continuous-smoke.spec.mjs` to the real-app acceptance runner. It
uses the real cockpit transcription adapter, HTTP controllers, state machine,
disposable MySQL, SSE, canonical Speech endpoint and PCM AudioWorklet. The
external transcription SDP/data channel, microphone and model/Speech providers
are synthetic. The Speech provider emits a tone and records its input text;
actual PCM rendering is observed, not physical speaker audibility.

The same scripted proposal is submitted through the Text UI and finalized
Continuous provider events on separate persisted agents. Both paths must:

1. Store identical goal, roles, success, sign, preparation and one-action limit.
2. Reject starting before observation-based preparation; speech alone cannot
   supply that evidence.
3. Retain one preparation sample and accept with zero actions.
4. Reject reusing the preparation sample as an execution cue.
5. Produce one canonical contribution on a fresh cue and no extra contribution
   for a repeated cue; record `LIMIT_REACHED`, not achieved success.
6. Preserve the same agreement, accepted preparation, count, phase, outcome and
   canonical contribution after separate database reads.

Continuous additionally proves partials stay local, repeated terminal receipts
acknowledge once, startup replay precedes microphone opening, each speech request
uses a unique persisted event ID with no text body, every provider input equals
that event's speech, and input is disabled/reopened around actual PCM playback.
Stopping releases the microphone track. The journey runs with the Live feature
both enabled and disabled; it is not a mocked acknowledgement relabelled as
Continuous acceptance.

The runner now isolates every Maven build under its owned
`target/gptlive-acceptance-<id>/build`, using `prometheus.build.directory` for both
tests and the fixture application's classpath. Previously only embodiment runs
were isolated from IDE output. It still creates/removes only a random loopback
schema/account and its own application process. Existing local
`src/main/resources/application-test.properties` was inspected for a loopback URL
without printing credentials; production properties were not used for tests.

### Assertion map and disposition

These are concrete assertion families, not a permission to delete whole suites.
Where a test mixes authority with an obsolete delivery rule, retain the former
and replace only the mode-specific expectation in its named later milestone.

| Group | Existing tests/assertions | Disposition |
| --- | --- | --- |
| Shared | `CoordinationAcceptanceUnitTest`: explicit negation rejects combined start; inference cannot replace accepted terms; preparation is required; repeated starts keep the run/budget | Retain for all modes. |
| Shared | `CoordinationTaskContractUnitTest`, lifecycle/revision/support/social/perception/timing suites: supported task forms, protected terms, fresh evidence, finite limits, grounded sensing and help without extra actions | Retain. GLF-01 runs the acceptance/task-contract and persisted Coordination suites; other suites are mapped, not claimed rerun. |
| Shared | `ExternalSpeechOwnershipUnitTest`, ingress claim/capture tests: scope, session/epoch fencing, duplicate-safe admission, no assistant acknowledgement, no automatic retry of uncertain claims | Retain. Recheck affected consumers in GLF-02/04. |
| Shared | `CoordinationVoiceUnitTest.ordinaryQuestionsDoNotAuthorizeTaskContributions`, `controlOccasionIsStableConsumedOnceAndCannotCrossSessionOrRevision`, obsolete-response rejection | Retain authority and once-only consumption; adapt the speech route in GLF-03. |
| Shared | `CoordinationDeliveryUnitTest.finalSpeechNeedsAssociationAndCurrentPlaybackAcknowledgementBeforeReflection`, uncertainty cases; delayed receipt tests in `playback-cue.test.mjs` | Retain separate association/playback evidence and no replay/spend on uncertainty. Captions alone must never finish Live work. |
| Strict Text/Continuous | `events.test.mjs`: commit ordering, UI-only partials, terminal dedup, stale epochs; `ingress.test.mjs`: serialized scoped FULL_PLAN, one legitimate fallback, rejected input stays rejected | Retain unchanged. The real-app parity test now exercises this adapter against persistence. |
| Strict Text/Continuous | `ScopedBehaviourSpeechServiceUnitTest.resolvesCanonicalPersistedSpeechWithoutRewritingIt` (PCM and MP3); foreign/malformed/empty events rejected; controller contract | Retain exact provider-input equality. This is a TTS input guarantee, not a physical transcription guarantee. |
| Strict Text/Continuous | `playback.test.mjs`: ordered IDs, duplicate/replay suppression, explicit repeatable resume, Stop abort/queue discard/lease release; PCM/progressive cancellation and fallback tests | Retain the existing streaming queue; do not add whole-response approval buffering. |
| Strict Text/Continuous | `valerian-transcription.spec.mjs`: real adapter with mocked APIs, ordered final input, input gating/reopening, held startup lookup after Stop/restart | Retain; add switching to Text while the lookup is held. Label these mocked-API checks separately from the new real-app journey. |
| Strict Text/Continuous | `CoordinationDeliveryUnitTest.publicationModeSilentOutputAndOldGraphsKeepTheirContracts`: Text completion occurs at canonical publication | Retain. Continuous shares this backend contract, not Live's separate final-playback gate. |
| Obsolete for repaired native Live | `ScopedLiveSessionServiceUnitTest`, `CoordinationPolicy.beginLiveConversation`, Live client tests selecting `speechApprovalRequired`/`committedSpeechOutput`, muted native route | Replace ordinary Coordination routing/handshake assertions in GLF-02. Keep ownership, capability, scope and teardown assertions. |
| Obsolete for repaired native Live | `CoordinationSpeechCorrectionUnitTest.supportCorrectionKeepsTheSameCompleteSpokenInstructionAndAuthority`: `Speak these words verbatim`; literal-support/exact candidate checks in approval tests | Replace universal literal wording and unheard correction requirements with the documented meaning cases in GLF-03. Keep response identity, authority and deadlines wherever still consumed. |
| Obsolete for repaired native Live | `approved-speech`, `speech-buffer`, `committed-speech` suites and old browser ES-08 journey: full capture/approval/synthesis/release before any ordinary audio | These prove the old route only. GLF-02 must test native audio while backend work is held, with no competing synthesis. Remove modules/tests only after checking remaining consumers. |
| Obsolete for repaired native Live | Conversation projection/caption assertions excluding all unconfirmed output | GLF-04 must distinguish inspectable partial/uncertain speech from completed delivery and consent evidence; do not expose muted drafts as heard speech. |

### Synthetic meaning rubric for GLF-03/05

`tests/gptlive/gptlivefix-scenarios.json` contains the executable sensor proposal,
labelled equivalent/materially different wording pairs, and dialogue, revision
and interruption journeys. It is synthetic and contains no private interaction.
The labelled pairs are a review corpus, not an implemented semantic classifier.

Equivalent: “show scissors” / “make a scissor sign”; a riddle paraphrase preserving
keys, space bar and no locks. Material differences: wrong sign, swapped roles,
changed count, removed/negated fresh-cue condition, invented preparation, premature
success, or dropping a defining riddle clue. Minor grammar, contractions and
politeness may vary only while every operative fact remains intact.

Review real speech against the backend facts **at that speaking occasion** and
inspect the resulting persisted state separately. Classify missing/ambiguous
capture as uncertain, not equivalent. Keyword presence alone cannot pass a
changed condition or negation. Generated words cannot establish consent, spend
an extra action, verify preparation or prove playback. No blocking classifier
before ordinary native audio is authorized by this rubric. Provider and physical
review remain separate from fixture outcomes.

### Saved flags and mode-switch decision

Current code persists `live.speechApproval` (`LiveSpeechRequest.ENABLED`) and
`live.committedSpeech` (`LiveCommittedSpeech.ENABLED`) at
`CoordinationPolicy.beginLiveConversation` when `coordination.deliveryEnabled`
is set. They survive session release; `voiceInstructions()` can still select the
silent committed-output guidance from those flags. Fresh sessions reassert them.
The two-second user and 2.4-second delivery-aware assistant silence windows are
capture policy, not an intended ordinary-native-playback prerequisite.

Actual backend speech ownership is the volatile session/epoch grant in
`ExternalSpeechOwnership`; flags alone do not grant it. The new persisted
`savedLiveSpeechFlagsDoNotOwnCanonicalOutputAfterSessionRelease` regression starts
the policy's Live lifecycle, reloads both flags, releases ownership and verifies
ordinary proposal/acceptance, one contribution and exact canonical PCM synthesis.
It also inspects both actual inference requests to ensure the Live mute directive
does not reach backend generation. A suspected prompt leak was disproved:
`CoordinationUpdateAction` and `TaskUpdateAction` own separate stored
`PromptPolicy` instructions, rather than using the active state's
`CoordinationPolicy.describe()`. No speculative prompt change was made.
It does not claim a browser Continuous → Live → Continuous handshake test.

GLF-02 must set the repaired output choice at session initialization **before**
context/handshake snapshots, including an agent with old true flags. Retire or
locally neutralize the old route selectors for repaired Coordination; never use
their mere presence to select muted/committed output. Preserve the separate
delivery capability, existing graphs, history, task response records and uncertain
delivery outcomes. Stop must release ownership and fence pending callbacks;
returning to Continuous must use FULL_PLAN and canonical event Speech again.
Test the complete three-mode transition on one saved agent, with late prior-session
work and zero automatic replay of uncertain task contributions. No global
backfill/reset or rewriting of old provenance is part of this decision.

### Verification and retained failures

Final focused suite: **64 Java cases, 50 Node cases, seven browser cases and 12
runner checks passed**, with no failures/errors/skips in the final selections.
The final saved-flag prompt assertion additionally passed its one-case MySQL
rerun. The seven browser cases are two real-app parity runs (Live enabled/disabled)
and five mocked-API ingress/lifecycle checks. No provider or physical acceptance
is claimed.

Exact final commands:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests ScopedBehaviourSpeechServiceUnitTest,BehaviourSpeechControllerWebMvcTest,SpeechArchitectureBrowserClientContractTest,CoordinationAcceptanceUnitTest,CoordinationTaskContractUnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryUnitTest,LiveSmokeConfigurationTest --browser --browser-mode both --browser-grep 'Coordination Text and Continuous|stopped resume lookup|mocked WebRTC emits partial|Stop and synthesis failure'
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'CoordinationAgentIntegrationTest#savedLiveSpeechFlagsDoNotOwnCanonicalOutputAfterSessionRelease'
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py
node --check tests/playwright/valerian-continuous-smoke.spec.mjs
git diff --check
```

Final combined artifacts: `target/gptlive-acceptance-3530917ab6/` contains the
Java/browser logs and all eight fresh JUnit XML reports under
`build/surefire-reports/`. This final journey explicitly requires the welcome
and its nonverbal plan after the cue, and forbids delivering it before the cue.
`target/gptlive-acceptance-84f61c583f/` contains the final prompt probe (one pass).
`target/gptlivefix-glf01-verification.json` records selections, counts and final
source hashes. All owned app processes, schemas and accounts were removed.

- `npm.cmd run test:transcription:unit`: **29 passed**, no skips.
- `npm.cmd run test:speech:unit`: **21 passed**, no skips.
- `python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py`:
  **12 passed**.
- First isolated run `3ae6f9ec0d`: **48 Java passed**, no skips. Browser stopped
  after the successful Text path because the new test used a nonexistent
  `disconnect-agent` selector; corrected to the existing `connect-agent` toggle.
- Second run `d020745b25`: **five lifecycle/ingress browser checks passed**,
  including the new held-lookup Text switch. The new parity journey reached its
  final canonical-ID assertion, then failed because raw `/eventhistory` omits
  IDs. Corrected the fixture to read the existing `/live/history` projection,
  which retains IDs and ordinary canonical speech. No production API changed.
  Both failed browser traces/logs remain under their artifact directories; each
  owned app/schema/account was cleaned up.
- Intermediate corrected run `9e45b40565`: **30 Java and two real-app browser
  cases passed**, no skips. The final run above strengthens the scripted cue
  content from the generic fixture reply to an actual welcome and checks that it
  was absent before the cue. No production inference/prompt change was needed.

### Remaining gates

Native Coordination audio, no-backend-wait proof, semantic native happy path,
interrupted history and native acoustic handling are GLF-02–04. Real-provider
wording/latency, sample plan, baseline comparison and physical microphone/speaker
trials are **NOT RUN** and belong to GLF-05. Other task variants, actual recognition,
Bluetooth/echo/noise and physical robot execution are not proved by this one
scripted sensor journey. Historical ES-08 evidence remains valid for its old
route and does not accept the repaired native contract.
