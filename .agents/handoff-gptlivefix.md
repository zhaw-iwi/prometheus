# Handoff prompt: implement the GPT-Live repair roadmap

Use the following as the task instructions for a new Codex session. Prepared on
2026-11-18; verify repository state before assuming the snapshot below is current.

## Your assignment

Implement `.agents/PLAN_GPTLIVEFIX.md`, following its five milestones and their
testable outcomes. Restore a useful distinction between reliable, backend-owned
Continuous interaction and fast native GPT-Live conversation. Preserve the
Coordination Agent's backend task authority while accepting meaning-preserving
variation in Live speech.

Read and understand the existing architecture before editing. Do the selected
milestone end to end, including appropriate verification and documentation. Start
with GLF-01 unless the roadmap/results show it has already been completed. Work
on `main`, in the existing checkout, preserving all unrelated user work.

The default handoff is one verified milestone followed by a review stop, as
specified in the roadmap and `.agents/CODEX.md`. If the owner explicitly asks you
to complete several milestones or the whole roadmap without intermediate stops,
follow that instruction. Do not repeatedly ask about routine implementation
choices already settled by this brief. Commit/push, paid trials and deployment
follow the current session's authorization; do not inherit execution permissions
from historical plans. This handoff itself does not request immediate publication.

## Why this work exists

The owner tested the Coordination Agent on Heroku and reported three problems:

1. Verbal replies in GPT-Live became noticeably slow, despite Live originally
   being introduced for immediate spoken responses.
2. Some responses were absent from the recorded conversation.
3. Some spoken responses stopped after approximately one word.

Text interaction was working well. The owner expected GPT-Live to speak directly,
with its transcripts entering PROMETHEUS while the backend generated nonverbal
behaviour in parallel. Deliberate backend-triggered speech, such as a response to
a visual cue, should still be possible.

Investigation found that Coordination development had changed this design. To
make task wording and delivery more controlled, ordinary Coordination replies
had also been routed through backend-authored, buffered TTS. Native Live audio
was muted. This was an intentional earlier design decision, not a regression
introduced by the recent robot visualization.

The owner now explicitly chooses different standards for the two speech modes.
The strict backend-mediated path belongs in Continuous. GPT-Live should maximize
response speed and tolerate slight verbal deviations while completing meaningful
happy-path interactions. Unit tests should encode that distinction instead of
forcing both modes to satisfy the same delivery and exact-word expectations.

## Desired contracts and limits

| Concern | Text and Continuous | GPT-Live |
| --- | --- | --- |
| Control | Backend interprets input and produces the canonical response | Backend owns tasks; native Live owns immediate conversational wording |
| Audio | Continuous uses `gpt-live-transcribe` -> finalized transcript -> ordinary backend acknowledgement/plan -> Speech/TTS | Native provider audio reaches the user while backend work proceeds asynchronously |
| Words | Canonical backend speech is the TTS input | Paraphrases and minor word variation are acceptable when meaning is preserved |
| Ordinary reply latency | Includes backend processing and synthesis | Must not wait for backend inference, a complete captured utterance, semantic approval or a separate synthesis/release request |
| Task guarantees | Explicit agreements, preparation, acceptance, fresh cues, finite actions and authoritative outcomes | The same backend guarantees, with less demanding spoken-word fidelity |
| Recording | Canonical events and ordered playback retain their existing contracts | Actual captured speech, interruptions and uncertainty must remain inspectable |

Continuous already has the intended pipeline, including event-scoped output,
ordered/deduplicated playback, microphone gating, selected devices/voice/speed,
streamed PCM and MP3 fallback. Preserve these capabilities. Carry appropriate
Coordination reliability expectations into this existing path; do not copy the
whole Live approval/synthesis subsystem into it or require complete audio
buffering merely because ES-08 used it.

Examples of acceptable Live variation include "show scissors" versus "make a
scissor sign". Changing the agreed sign, role, count, condition, negation, defining
riddle clue or task outcome is a material semantic difference. Do not solve the
problem by accepting arbitrary speech, removing state-machine tests or claiming
that any assistant transcript proves successful delivery.

Native speech may describe or paraphrase backend-confirmed work. It must not
itself spend another contribution, change an agreement, invent verified
preparation or make the backend report success. Preserve scope/session/epoch
identity, duplicate-safe ingress, Stop/reset/disconnect, response association
and conservative handling of uncertain task completion. Accepted wording
flexibility is not permission to replay uncertain actions automatically.

The owner accepts that Live cannot offer the same verbal precision as Text and
Continuous. Do not reintroduce a blocking approval/classification step before
every ordinary reply to recover that precision. Keep the distinction between
speech generation, captured text, observed playback and physical audibility.

## Repository and deployment snapshot

At handoff preparation:

- The active branch is `main`, at `cff6818`, synchronized with `origin/main`.
- No GPT-Live repair has been implemented. All five GLF milestones are open.
- `.agents/PLAN_GPTLIVEFIX.md` is a newly written, uncommitted roadmap. This
  handoff is also new. Preserve these documents when reviewing the worktree.
- `agents` is the deployment branch with its own catalog/persona configuration.
  Its last reviewed audit commit was `9f3bb41`; Heroku `valerian` was on v169.
- The previous work merged main into `experiment/hri-2027-coordination` as
  `8d4b431` and pushed it. That branch contains study-specific runtime/UI/protocol
  work. Do not import the study implementation into main as part of this repair.
- Earlier session work added the exact production CORS origin
  `http://localhost:5173`, plus the Valerian Robot view and friendly mouth baseline.
  Preserve them. They are outside this repair and do not explain the Live path.

The eventual publication direction is main -> agents and main ->
experiment/hri-2027-coordination after integrated acceptance and owner-authorized
publication. Pushing agents triggers Heroku deployment; the experiment branch
does not. Preserve branch-specific configuration and do not equate an experiment
merge with study qualification. Recheck all branch/release facts when needed.

## Required reading and inspection order

1. Read `.agents/CODEX.md` for engineering and milestone practice, then
   `.agents/CONTEXT.MD` for purpose, architecture, current capabilities and limits.
2. Read `PROJECT.md` from the beginning through `Current milestone state` and
   stop before `Historical milestones checklist`. Search historical records
   selectively rather than loading the audit end to end.
3. Read `.agents/PLAN_GPTLIVEFIX.md` completely. It is the authoritative roadmap
   for this work. Read `.agents/GPTLIVEFIX_RESULTS.md` if a preceding session has
   created it; use actual evidence and code to determine the next incomplete step.
4. Inspect the README sections covering Continuous/transcription ingress,
   output-only Speech, Experimental GPT-Live, provenance/conversation history,
   and Coordination speech/delivery. They describe the implementation to change;
   some will need updating as the new design becomes real.
5. Read `.agents/PLAN_GPTLIVE.md`, especially the original proposed division of
   responsibility and operator experience. Search `.agents/GPTLIVE_RESULTS.md`
   for GL-03 external speech ownership, GL-04 transcript capture, GL-05 context/
   delegation and the later announcement-latency/audio-diagnostics follow-up.
6. Read `.agents/ES08_SPEECH_DELIVERY_DECISION.md`. Read the current summary and
   relevant November 11-12 approval, native-output failures and committed-Speech
   sections of `.agents/ESCALATION_RESULTS.md`; use headings/search, not a full
   audit dump. Consult `.agents/PLAN_ESCALATION.md` for the reasoning behind
   existing guards. Understand the failures before revising their remedies.
7. Read `tests/gptlive/README.md` and `tests/gptlive/CORE_AGENTS.md` for acceptance
   setup. Inspect `.agents/TRANSCRIBE_SMOKE_RESULTS.md` and `.reng/HUMAN_TEST.md`
   when preparing physical tests. Consult `.agents/TELEMETRY_RESULTS.md` and
   current telemetry code when extending measurement.
8. Inspect the implementation and neighboring tests listed below, following the
   actual call graph for the selected milestone. State a concise mental model,
   focused scope and guiding test patterns before making changes.

The current owner direction in this handoff/roadmap supersedes the desired Live
output architecture of the old ES-08 decision. Keep its evidence and history.
Do not let an older "must approve before playback", exact-word requirement,
pending-choice note, feature-branch instruction or authorization override the
new mode distinction and the current session instructions.

## Commit history to examine

Use `git log`, `git show --stat`, focused `git show <commit> -- <paths>` and
before/after comparisons. Read relevant changes rather than reverting a large
commit or assuming its entire implementation is obsolete.

| Commit | Relevance |
| --- | --- |
| `293c987` | Initial Coordination agreements, preparation and task lifecycle; establishes the architecture to preserve. |
| `a3b3947` | October 28: Coordination user silence becomes 2 seconds to keep hesitant input together. |
| `61ddf13` | October 28: binds Coordination speech to current committed response authority while ordinary native conversation remains permitted. |
| `c663ca6` | Separates final Coordination delivery from reflection. Inspect the authority/evidence boundary before changing completion behaviour. |
| `3dc1912` | Repairs late playback confirmation; retain the underlying delayed-evidence regression concern. |
| `2424691` | November 11: bounded assistant sentence assembly; Coordination assistant silence becomes 2.4 seconds for delivery-aware instances. |
| `02f25dd` | November 11: approval before audible native speech, buffered capture and playback-confirmed conversation. This removes immediate playback. |
| `0801746` | November 12: committed Coordination Speech output, native muting, synthesis/release/played flow, heard-only history and microphone interruption handling. Includes ordinary replies. |

For original ownership/capture design, find the GL-03/04/05 implementation commits
through their files and result records. Do not assume the early implementation
can simply be restored wholesale: later identity, persistence, context, sensing
and lifecycle fixes must survive.

## Evidence from the reported interaction

The owner supplied:

`C:/Users/despi/Downloads/prometheus-telemetry-2026-11-18T14-09-12-895Z.json`

The investigation used Heroku CLI, read-only scoped database queries and local
code/history inspection. Local evidence is under ignored
`target/live-review-20261118/`, including `assessment.json`, `segments.json`,
`receipts.json`, `events.json` and `deployed-assets.json`. These files may be absent
on another machine. The findings below permit work to begin without production
access or the private export.

Established findings:

- The deployed four relevant client files matched committed main/agents:
  `live/client.js`, `committed-speech.js`, `approved-speech.js` and
  `speech-capture-worklet.js`.
- All six user utterances were captured and acknowledged. There were seven
  synthesized outputs, including an unsolicited observation; three had playback
  confirmation. Raw native assistant output also existed but was inaudible on
  this route and excluded from delivered conversation.
- Measured from the final received user transcript fragment to synthesis being
  ready, the six delays were approximately **5.99, 7.73, 6.70, 6.58, 5.89 and
  6.50 seconds**. Input settling contributed about 2 seconds and backend inference
  about 1.8-2.3 seconds. Synthesis/discovery/delivery contributed further waiting.
  These intervals precede playback and exclude any earlier provider transcription
  delay. They are not a physical end-of-user-speech-to-first-audible-word measure.
- Two retained hand-response traces show interruption **0.849 and 1.804 seconds
  after release**. The approval player's input worklet can interrupt on two
  consecutive 20-ms blocks above RMS `500 / 32768`. It detects amplitude, not
  human intent or speech identity. Echo/noise are plausible contributors, but
  the evidence does not distinguish them from actual human speech.
- `ConversationProjection` hides speech whose `playbackConfirmed` is false.
  An interrupted response can therefore disappear from the conversation even
  though opening words were heard and the full intended text exists in storage.
  Do not equate this with a demonstrated loss of provider transcription.
- The retained WebRTC statistics showed no received-packet loss. The reported
  `context_injection_incomplete` error occurred during local teardown after Stop;
  it does not establish the cause of earlier delays or cutoffs.
- The browser retained 128 diagnostic entries and dropped 189 earlier entries.
  Heroku's retained log window started after the interaction. Audio content and
  physical audibility were not collected, so earlier cutoffs and acoustic causes
  cannot be reconstructed conclusively.

The following existing tests were actually rerun during diagnosis, with **30
passing cases**:

```powershell
node --test tests/js/live/committed-speech.test.mjs tests/js/live/approved-speech.test.mjs tests/js/live/playback-cue.test.mjs
```

Their passing result confirms the old software rules, not a satisfactory physical
conversation. No repair, provider replay or production mutation was performed.
Convert relevant observations into synthetic regression fixtures; do not commit
the private interaction, real access codes, credentials or raw production exports.

If further production diagnosis is relevant, the app is `valerian` at
`https://valerian-e613edef27a0.herokuapp.com`; the owner identified
`src/main/resources/application.properties` as the database credential source.
Load secrets without printing them, use scoped read-only access and keep private
artifacts ignored. This is never the configuration for automated integration tests.

## Code and tests that should guide implementation

Backend paths below are under `src/main/java/ch/zhaw/prometheus/`; browser paths
are under `src/main/resources/public/`. Confirm names and current consumers before
editing. The roadmap contains additional anchors.

**Continuous and canonical output**

- `transcription/ingress.js`, `transcription/client.js`, `transcription/events.js`,
  `speech/playback.js`, `speech/pcm.js`, `speech/progressive.js`, and their wiring
  in `valerian/script.js`.
- `application/ScopedBehaviourSpeechService.java`, `SpeechTurnSelector.java`,
  `controllers/BehaviourSpeechController.java` and the ordinary acknowledgement/
  behaviour publication path.
- Tests in `tests/js/transcription/`, `tests/js/speech/`,
  `tests/playwright/valerian-transcription.spec.mjs`,
  `ScopedBehaviourSpeechServiceUnitTest`, `BehaviourSpeechControllerWebMvcTest`
  and the Speech architecture contract tests.

**Live output, ownership and context**

- `live/client.js`: session handshake, player selection, remote audio routing,
  transcript polling/filtering, captions/history callbacks and shutdown.
- `live/committed-speech.js`, `approved-speech.js`, `speech-capture-worklet.js`,
  `speech-buffer.js`, `playback-cue.js`: existing complete-buffer approval,
  interruption and completion mechanics. Determine which remain needed after
  native output is restored.
- `application/ScopedLiveSessionService.java`, `ExternalSpeechOwnership.java`,
  `LiveContextBridgeService.java`, `LiveCommittedSpeechService.java`,
  `LiveSpeechApprovalService.java`, `LiveTaskResponseService.java`,
  `application/live/LiveVoicePolicyAdapter.java`, `LiveContextDelivery.java`,
  and `spi/live/OpenAILiveSessionGateway.java`.
- Tests in `tests/js/live/`, `ScopedLiveSessionServiceUnitTest`,
  `ExternalSpeechOwnershipUnitTest`, `LiveContextDeliveryUnitTest`,
  `LiveCommittedSpeechServiceUnitTest`, `LiveSpeechApprovalServiceUnitTest`,
  and `tests/playwright/valerian-gptlive-smoke.spec.mjs`.

**Coordination authority and mode-specific wording**

- `agentdefs/core/CoordinationPolicy.java`, `CoordinationUpdateAction.java`,
  `CoordinationSpeechApproval.java`, `CoordinationDeliveryAction.java`,
  `CoordinationCueAction.java`, `CoordinationPreparationAction.java`,
  `CoreTaskVoice.java`, and `model/task/TaskResponse.java`.
- Inspect persisted `LiveSpeechRequest.ENABLED` / `LiveCommittedSpeech.ENABLED`,
  session-start initialization, `liveUserSilenceMs`, speech ownership and
  `liveSpeechCaptured`. Verify a saved agent can move between modes correctly.
- Preserve `CoordinationAcceptanceUnitTest`, `CoordinationTaskContractUnitTest`,
  lifecycle/revision/support/observation timing tests and their task guarantees.
  Reassess mode-specific assertions in `CoordinationVoiceUnitTest`,
  `CoordinationSpeechCorrectionUnitTest`, `CoordinationDeliveryUnitTest` and
  `CoordinationDeliveryIntegrationTest`. Use `CoordinationAgentIntegrationTest`
  for persisted state-machine behaviour.

**Recording and honest diagnostics**

- `application/LiveTranscriptCaptureService.java`, `LiveTranscriptIngressService.java`,
  `ExternalSpeechRecordingService.java`, `application/live/LiveTranscriptSegmenter.java`,
  `model/event/ConversationProjection.java`, `SpeechProvenance.java`,
  `live/captions.js`, `live/diagnostics.js` and `live/audio-diagnostics.js`.
- Inspect receipt/segment durability, completeness, playback provenance and
  projected history together. Partial inspectable text must not accidentally
  become full delivery or consent evidence in prompts/task handling.
- Relevant tests include `LiveTranscriptCaptureServiceUnitTest`,
  `LiveTranscriptSegmenterUnitTest`, `LiveTranscriptIngressIntegrationTest`,
  `LiveTranscriptClaimIntegrationTest` and the Live client/diagnostic suites.

**Acceptance infrastructure**

- `tests/gptlive/run_acceptance.py`, `test_acceptance_runner.py`,
  `coordination_text_journey.mjs`, `core_backend_journey.mjs`,
  `escalation_journeys.mjs`, and test-only provider fixtures.
- The current ES-08 journey drives `CommittedSpeechPlayer`. Adapt it to test the
  repaired native path rather than claiming an old synthesized-output pass as
  Live acceptance. A mocked text request alone is not Continuous acceptance.

## Execution and verification expectations

Follow the detailed scopes and exit gates in `PLAN_GPTLIVEFIX.md`:

1. **GLF-01:** establish the mode/test contract and demonstrate Continuous parity
   through the real transcription adapter and canonical output route.
2. **GLF-02:** restore native conversational speech. Prove it can start while
   backend work is deliberately held pending, with no competing TTS and continued
   exactly-once transcript processing/nonverbal behaviour.
3. **GLF-03:** complete a semantic Coordination Live happy path, retaining task
   invariants, delivery uncertainty and one representative revision/recovery path.
4. **GLF-04:** repair recording/interruption/lifecycle issues on the actual native
   route; make partial or uncertain output inspectable without false completion.
5. **GLF-05:** integrated per-mode regression, bounded real-provider journeys,
   measured latency and a physical microphone/speaker trial, then release handoff.

Use the smallest meaningful set of unit, integration and browser checks for each
change. Unit tests protect deterministic rules; they cannot prove real provider
wording, acoustic echo handling or perceived response speed. Do not weaken all
tests until the suite passes: map each changed assertion to the explicit new
contract and retain shared/Continuous guarantees. Do not add tests that merely
repeat implementation constants.

Use disposable loopback MySQL through the acceptance runner. This Windows
checkout may lack `application-test.properties`; prepare/use an ignored local
administration config rather than falling back to production. Existing local
MySQL helper patterns may be under ignored `target/`, but inspect them before
reuse and own the processes/schemas/accounts you create. Keep Maven build output
isolated from IDE compilation using `prometheus.build.directory`. Use `npm.cmd`
on PowerShell when `npm.ps1` is blocked; do not change execution policy.

For provider trials, follow current authorization and declare finite request,
token and session-duration bounds. Retain failed evidence and investigate a
concrete cause before rerunning. Define the latency target/sample plan before
trials, compare with native conversation on the same setup, and keep server,
browser and provider clocks distinct. Report actual counts and distributions
without claiming physical audibility from browser events.

Prepare an operator checklist when physical actions are needed. Keep unperformed
rows marked NOT RUN and distinguish software, provider and physical acceptance.
Do not mark the complete roadmap accepted just because unit tests pass or a
single favorable provider attempt succeeds.

During implementation, create/update `.agents/GPTLIVEFIX_RESULTS.md`, the roadmap
checklist, relevant README sections, `.agents/CONTEXT.MD` and `PROJECT.md` current
status. Keep historical evidence with dated supersession notes. Report what
changed, why, exact verification, retained failures and remaining limits at each
milestone handoff. Leave a reviewable working tree and follow the owner's current
instructions for continuing or publishing.

Begin by checking the worktree and reading the bootstrap/roadmap. Then explain
your concise mental model and the GLF-01 implementation/test scope, and proceed
with the authorized milestone rather than stopping at a proposed plan.
