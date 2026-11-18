# Restore distinct Continuous and GPT-Live interaction contracts

Prepared: 2026-11-18. Status: **GLF-01 committed; GLF-02 verified;
GLF-03-05 open**. Development branch: `main`.
GLF-01 commit: `f7c1033c`; evidence is in `GPTLIVEFIX_RESULTS.md`.
The owner now authorizes sequential milestones, committing and pushing each,
and bounded real OpenAI happy-path tests costing less than $50 total. Halt for
an unforeseen owner decision. No deployment has been performed.

## Owner direction and working model

The owner wants two deliberately different speech modes:

| Contract | Text / Continuous | GPT-Live |
| --- | --- | --- |
| Primary objective | Backend-controlled reliability | Fast, natural conversation |
| Speech path | Continuous: `gpt-live-transcribe` -> finalized user event -> backend behaviour plan -> existing Speech/TTS | Native Live speech, with backend transcript processing and nonverbal behaviour alongside it |
| Wording | Backend-authored canonical text supplied to TTS | Equivalent paraphrasing and minor verbal variation are acceptable |
| Task authority | Backend agreements, preparation, transitions, actions and outcomes | The same backend authority; native speech does not itself execute or complete a task |
| Acceptance | Strong parity between Text and Continuous | Representative happy paths succeed without requiring identical words |

Restore the original division of responsibility for ordinary Live conversation.
Do not make immediate native replies wait for backend inference, complete audio
capture, semantic approval, separate TTS or playback-release HTTP requests.
PROMETHEUS still processes user transcripts, supplies confirmed task/sensory
context and emits nonverbal behaviour. Deliberate backend speaking occasions
remain supported without a competing ordinary TTS reply.

This owner direction supersedes the **desired GPT-Live delivery design** in
`ES08_SPEECH_DELIVERY_DECISION.md` and the November 11-12 approval/synthesis
sections of `PLAN_ESCALATION.md`. Those records explain the current code and
earlier failures; retain them as history. Their literal-output and approval-before-
audio expectations must not silently become requirements for this new Live mode.
Their backend authority, scope, identity and duplicate-prevention protections
remain relevant. Runtime documentation must continue to distinguish planned
changes from implemented behaviour until each milestone is verified.

The owner subsequently authorized implementation through all remaining milestones,
with a commit and push after each. This overrides the handoff's original
one-milestone stop. Paid smoke tests must stay below $50 cumulatively, with finite
request, token, synthesis and session caps declared before billing. Historical
permissions do not independently authorize deployment or unbounded trials.

## Bootstrap and evidence

Before implementation:

1. Read `.agents/CODEX.md` and `.agents/CONTEXT.MD`.
2. Read `PROJECT.md` from its beginning through `Current milestone state`,
   stopping before `Historical milestones checklist`. Search older records only
   for a specific relevant decision.
3. Check branch, worktree and user changes. Work in the existing `main` checkout;
   do not create routine extra worktrees or revert unrelated work.
4. Read the relevant README speech/Continuous/GPT-Live/Coordination sections,
   implementation and neighboring tests. State a concise mental model and the
   patterns/files/tests guiding the selected milestone before editing.

### Diagnosis established on 2026-11-18

- `a3b3947` (October 28) sets Coordination's user-silence interval to 2 seconds.
- `02f25dd` (November 11) introduces approval before audible native playback.
  `2424691` also extends Coordination assistant sentence assembly to 2.4 seconds.
- `0801746` (November 12) replaces audible native output with backend-authored,
  fully buffered Speech synthesis. It includes ordinary Coordination replies.
  Native output remains generated/captured but inaudible on this route.
- The supplied interaction on Heroku `valerian` v169 recorded six user turns.
  Last received user transcript to completed synthesis took 5.9-7.7 seconds;
  backend inference accounted for about 1.8-2.3 seconds. These are server
  intervals, not physical speech-end-to-audibility measurements.
- Seven synthesized outputs existed, including an unsolicited observation;
  three had confirmed playback. Unconfirmed output is deliberately excluded
  from projected conversation. The stored text is not necessarily lost.
- Two retained browser traces show interruption 0.85 and 1.80 seconds after
  speech release. The approval player's microphone worklet triggers after two
  20-ms blocks above an RMS threshold. It cannot distinguish human speech,
  noise and residual echo. The recording does not establish which caused these
  particular interruptions.
- Thirty existing playback unit tests passed. The four deployed Live client
  assets matched committed main/agents versions. Those checks do not establish
  physical audio quality. The robot-mouth change did not introduce this path.

Optional local evidence, which may be absent in another checkout:

- `C:/Users/despi/Downloads/prometheus-telemetry-2026-11-18T14-09-12-895Z.json`
- Ignored `target/live-review-20261118/assessment.json`, scoped database extracts
  and deployed-asset hashes. The browser ring dropped earlier events; retained
  Heroku logs started after the interaction. Do not infer complete acoustic
  evidence or an earlier server failure from those limits.

The summary above is sufficient to start. Do not commit private transcripts,
database extracts, credentials or raw captures as fixtures. Reproduce relevant
cases with synthetic data. `application.properties` may contain production
credentials: it is not a test database configuration. Use the existing disposable
loopback-MySQL acceptance runner for integration tests.

## Boundaries shared by all milestones

- Preserve explicit Agent/State/Transition control, canonical Event and
  BehaviourPlan contracts, interaction profiles and existing service boundaries.
- Preserve agreements, verified preparation, fresh-cue requirements, finite
  action budgets and authoritative outcomes. Speech cannot manufacture consent,
  perform another contribution or establish a successful physical action.
- Preserve scoped access, session/epoch/response identity, serialized duplicate-
  safe user ingress, Stop, reset, disconnect and ownership isolation.
- Keep Text and Continuous's strict backend semantics and existing output
  settings, ordered event-ID playback, microphone gating and replay rules.
  Preserve their explicit latest-assistant playback on transcription start;
  do not introduce automatic replay of uncertain task work.
- A paraphrase may change wording, not roles, a riddle's defining clues, the
  agreed condition, a numerical limit, negation or a task outcome. Accepted and
  rejected examples must be written down before tuning Live checks.
- Distinguish generated/captured speech, observed playback, interruption and
  physical audibility. Recording partial speech does not prove full delivery.
- Reuse the current Continuous pipeline. Do not transplant the entire Live
  approval/synthesis subsystem into it or create a second task/voice engine.
- Inspect persisted Coordination flags and mode-switch behaviour. A previously
  used agent must not retain the wrong speech ownership when changing modes.
  Make any compatibility handling explicit, local and tested; do not backfill
  saved graphs or rewrite histories without a separately justified scope.
- Keep other agents and the HRI participant interface/protocol unchanged.
  No database-capacity campaign, new provider, broad prompt overhaul or robot
  rendering work is included.

## Milestones

Implement in order. Each milestone must be reviewable, with the smallest
high-value test set that proves its outcome. An intermediate milestone is not
release-ready merely because its focused tests pass.

### GLF-01 — Establish mode contracts and Continuous parity

**Outcome:** the owner-directed distinction is executable and Continuous's
Coordination behaviour is protected before changing Live.

Work:

- Map the existing speech and Coordination tests to three groups: shared
  invariants, strict Text/Continuous expectations, and obsolete Live delivery
  expectations. Record concrete assertions and their disposition in the results
  file; do not globally weaken tests or relabel a mocked backend test as a
  Continuous browser journey.
- Verify one representative Coordination path through finalized transcription,
  the ordinary `FULL_PLAN` acknowledgement, persisted behaviour and canonical
  Speech output. Fix a demonstrated Continuous gap within this scope.
- Define synthetic scenarios and the Live meaning rubric reused in GLF-03/05.
  Document how old saved speech flags and mode switching will be handled.

Minimum verification:

- Unit/contract checks: ordered, exactly-once final ingress; no partial input;
  TTS receives canonical event speech; duplicate/replayed SSE cannot double-play;
  Stop and mode changes fence pending output.
- A disposable-MySQL test comparing equivalent typed and finalized-transcript
  input for agreement, preparation, acceptance and action count, with scripted
  inference to avoid requiring identical independent model generations.
- A real-app Continuous browser case that actually drives the transcription
  adapter and canonical output route, including microphone gating/reopening.

**Exit:** the contract/test map and representative Continuous path pass. Existing
strict assertions remain meaningful. Record missing coverage explicitly.

### GLF-02 — Restore native Live conversation

**Outcome:** ordinary native replies are independent of backend response latency.

Work:

- Select the native audible path for the repaired Live mode. Remove mandatory
  approval/buffering/synthesis from ordinary conversation; simply unmuting audio
  alongside the existing TTS player is not a valid implementation.
- Restore backend non-speech generation for ordinary Live user turns while
  retaining asynchronous user ingress, context updates and deliberate backend
  narration. No second ordinary spoken answer should be generated for playback.
- Resolve the mode/session handshake and persisted-flag compatibility identified
  in GLF-01. Remove newly obsolete branches/tests where there are no remaining
  consumers; preserve independently supported paths and historical evidence.

Minimum verification:

- A controlled browser/transport test holds backend processing behind a barrier
  while native audio begins playing. It must observe the audible route and
  subsequent transcript/backend progress, not merely assert a configuration flag.
- Verify no competing synthesis/release requests for that ordinary reply,
  exactly-once user ingress and continued nonverbal rendering.
- Test Stop or session replacement while backend work is pending, plus a
  Continuous -> Live -> Continuous switch on the same saved agent.

**Exit:** these checks pass together with affected Continuous regressions. Real
provider response speed is still unverified until GLF-05.

### GLF-03 — Make Coordination Live acceptance semantic

**Outcome:** native conversation and backend-controlled tasks work together,
without whole-response approval becoming an ordinary speech prerequisite.

Work:

- Reconcile voice guidance, response association, backend speaking occasions and
  final-delivery handling with native speech. Keep task authority in the existing
  state machine. Do not add a blocking model/classification request before every
  ordinary Live reply or recreate the old buffered approval path under a new name.
- Permit meaning-preserving verbal variation. Use deterministic fixtures for
  known paraphrases and independent review for real model output; literal text
  equality is not the default Live acceptance rubric.
- Keep task contribution and playback evidence scoped. Generated words or a
  loosely related transcript alone cannot prove an action was delivered, spend
  an extra contribution or finish a pending task. A correction after uncertain
  delivery must not automatically replay the task.

Minimum verification:

- Unit cases include equivalent phrasing (for example, "show scissors" versus
  "make a scissor sign") and materially different content (wrong sign, changed
  role/count, reversed condition or premature success). Keep the latter failures.
- A persisted Live journey covers ordinary exploration -> proposal -> required
  preparation -> acceptance -> fresh cue -> one contribution -> reflection.
  Verify agreement and budget invariants; repeat a cue to prove no duplicate action.
- Include one revision/help path and one interrupted or stale response that
  cannot falsely complete the task. Preserve corresponding strict Continuous
  expectations and unaffected native agents.

**Exit:** the representative native happy path passes under the documented
meaning rubric, with task authority and delivery uncertainty preserved.

### GLF-04 — Make speech recording and interruption dependable

**Outcome:** the conversation remains inspectable and interruption is intentional
and observable rather than an unexplained disappearance of speech.

Work:

- Define how complete, partial/interrupted and delivery-uncertain native speech
  appear in history, captions and diagnostics. Retain available speech once with
  accurate provenance; do not expose muted drafts as heard conversation or use
  partial output as full task-delivery/consent evidence.
- Fix capture/ledger/projection/lifecycle defects demonstrated by the new native
  path. Stop/reconnect/reload must preserve recorded evidence without duplicate
  bubbles, repeated acknowledgement or automatic task replay.
- Review actual native interruption ownership after GLF-02. Remove obsolete
  approval-player coupling where appropriate; do not blindly retune its RMS
  constant if that player is no longer on the native route. Exercise genuine
  interruption and plausible echo/noise separately without muting the microphone
  for all Live playback, which would remove the intended full-duplex experience.
- Add bounded diagnostics for actionable interruption/capture outcomes and timing
  correlation where existing telemetry is insufficient. Retain separate clock
  domains and dropped-event counts; do not claim word-level audibility from RMS.

Minimum verification:

- Segmenter/projection/client cases for complete output, interrupted prefixes,
  late/duplicate captions, overlapping input, scope change and reload.
- Browser/media cases for genuine interruption, controlled non-speech/echo
  fixtures, Stop and reconnect. Confirm no double playback or false completion.
- Check that uncertain speech is inspectable while backend consent/action
  evidence remains conservative. Recheck Continuous's microphone gating.

**Exit:** software checks pass with meaningful diagnostic evidence. Synthetic
audio does not close the physical microphone/speaker acceptance requirement.

### GLF-05 — Integrated acceptance and release handoff

**Outcome:** the implemented distinction works through complete interactions,
with measured Live speed and an honest statement of remaining limits.

Work and required evidence:

- Run affected shared Java/JS/browser regressions and disposable-MySQL journeys
  on the final source. Exercise the same representative Coordination scenarios
  in Text, Continuous and Live: greeting/capability exchange, proposal/preparation/
  acceptance, cue-triggered contribution, help/revision and bounded completion.
- Run bounded real-provider Continuous and native Live journeys under current
  session authorization. Extend the existing harness to use the actual repaired
  route; the old committed-Speech ES-08 harness is not native-Live evidence.
  Declare finite call/token/session-duration limits before running and retain
  failures/usage. Do not repeatedly reroll unchanged failures to obtain a pass.
- Establish the sample count and latency acceptance target before trials, using
  a native-conversation baseline on the same setup. Record per-turn observations,
  median/range and slow cases; report p95 only with a sufficient declared sample.
  Separate measured speech end, transcript receipt, backend work and browser
  audio onset. The invariant is no backend wait on ordinary native playback;
  provider/network speed needs measured evidence, not an arbitrary unit-test SLA.
- Review real Live meaning and the resulting persisted task state. Wording
  variation may pass; changing the agreed task or merely satisfying fixture
  keywords may not. Verify conversation capture as well as the spoken exchange.
- Complete an operator microphone/speaker trial with the actual target setup:
  ordinary exchange, intentional interruption, quiet/noisy input, output echo,
  Stop and reconnect. Record devices and settings. If a person must perform it,
  prepare a short executable checklist and leave those rows **NOT RUN** until
  evidence arrives. Do not claim full acceptance from automated checks alone.

**Exit:** publish a per-mode acceptance matrix, actual commands/results, measured
latency and retained failures/limits. Mark software completion separately from
provider and physical acceptance if an external trial remains outstanding.

When publication is authorized after integrated acceptance, commit/push on main,
then merge into `agents` and `experiment/hri-2027-coordination`. Preserve the
agents catalog/persona/deployment settings and experiment-specific code/protocol.
The agents push triggers Heroku deployment: verify its actual workflow/release
and served client assets. The experiment merge is not a deployment or new study
qualification. Do not perform production mutations as a release smoke by default.

## Implementation and test anchors

Use these as starting points, then follow the current call graph; names are not
a mandate to preserve obsolete implementation structure.

- **Continuous:** `src/main/resources/public/transcription/` (especially
  `ingress.js`), `public/speech/`, `public/valerian/script.js`;
  `application/ScopedBehaviourSpeechService.java`, `SpeechTurnSelector.java`.
  Tests: `tests/js/transcription/`, `tests/js/speech/`,
  `tests/playwright/valerian-transcription.spec.mjs`,
  `ScopedBehaviourSpeechServiceUnitTest`, `SpeechArchitectureBrowserClientContractTest`.
- **Live session/ownership:** `public/live/client.js`, `approved-speech.js`,
  `committed-speech.js`, `speech-capture-worklet.js`, `playback-cue.js`;
  `application/ScopedLiveSessionService.java`, `ExternalSpeechOwnership.java`,
  `LiveContextBridgeService.java`, `LiveCommittedSpeechService.java`,
  `LiveSpeechApprovalService.java`, and `application/live/`.
  Tests: `tests/js/live/`, `ScopedLiveSessionServiceUnitTest`,
  `ExternalSpeechOwnershipUnitTest`, `LiveContextDeliveryUnitTest`,
  `tests/playwright/valerian-gptlive-smoke.spec.mjs`.
- **Coordination:** `agentdefs/core/CoordinationPolicy.java`,
  `CoordinationUpdateAction.java`, `CoordinationSpeechApproval.java`,
  `CoordinationDeliveryAction.java`, `CoreTaskVoice.java`, `model/task/TaskResponse.java`.
  Tests: `CoordinationVoiceUnitTest`, `CoordinationAcceptanceUnitTest`,
  `CoordinationTaskContractUnitTest`, `CoordinationDeliveryUnitTest`,
  `CoordinationAgentIntegrationTest`, `CoordinationDeliveryIntegrationTest`.
- **Recording:** `application/LiveTranscriptCaptureService.java`,
  `LiveTranscriptIngressService.java`, `ExternalSpeechRecordingService.java`,
  `application/live/LiveTranscriptSegmenter.java`,
  `model/event/ConversationProjection.java`, `SpeechProvenance.java`,
  `public/live/captions.js`. Tests: `LiveTranscriptCaptureServiceUnitTest`,
  `LiveTranscriptSegmenterUnitTest`, `LiveTranscriptIngressIntegrationTest`,
  `LiveTranscriptClaimIntegrationTest`, and neighboring client/caption tests.
- **Acceptance:** `tests/gptlive/run_acceptance.py`,
  `tests/gptlive/coordination_text_journey.mjs`, `escalation_journeys.mjs`,
  `core_backend_journey.mjs`, `tests/gptlive/README.md`.
  Java paths above are relative to `src/main/java/ch/zhaw/prometheus/`;
  `public/` paths are relative to `src/main/resources/`.

Useful existing commands (choose focused selections for the milestone):

```powershell
python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
npm.cmd run test:live:unit
python tests/gptlive/run_acceptance.py --database-properties <ignored-loopback-config> --java-tests <focused-classes> --browser --browser-mode enabled --browser-grep <focused-titles>
```

The angle-bracket arguments are placeholders, not runnable literal commands.
Verify that selected tests actually ran without skips. Keep owned Maven output
separate from IDE compilation, using the existing build-directory mechanism.
Do not run database-backed tests against the ordinary production-configured
`application.properties`. Do not copy existing provider keys into logs or fixtures.

## Documentation, progress and next-session instruction

Create `.agents/GPTLIVEFIX_RESULTS.md` during GLF-01. After each milestone, record
scope, source revision/dirty state, changed behaviour, exact verification, artifact
paths, failures and unverified areas. Update this checklist and relevant README,
`.agents/CONTEXT.MD` and `PROJECT.md` current-state descriptions as behaviour
changes. Preserve earlier audit records and explain supersession with dated notes.

- [x] GLF-01: mode contracts and Continuous parity — verified 2026-11-18; results in `GPTLIVEFIX_RESULTS.md`
- [x] GLF-02: native Live conversation — verified 2026-11-18; results in `GPTLIVEFIX_RESULTS.md`
- [ ] GLF-03: semantic Coordination Live happy path
- [ ] GLF-04: recording and interruption
- [ ] GLF-05: integrated provider/physical acceptance and release handoff

Suggested instruction for a new session:

> Follow the repository bootstrap, then read `.agents/PLAN_GPTLIVEFIX.md`.
> Implement the next incomplete milestone, currently GLF-03. Preserve Text
> and Continuous guarantees while restoring native Live speed under the plan's
> relaxed wording contract. Use the smallest meaningful test set, update the
> results/current documentation, and report what passed and what remains
> unverified. Stop for review after the milestone unless I explicitly authorize
> continuing through more milestones. Commit, push, paid trials and deployment
> follow my current session instructions; do not inherit them from older plans.
