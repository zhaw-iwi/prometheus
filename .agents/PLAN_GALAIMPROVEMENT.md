# Gala interaction improvement plan

Created: 2026-10-21. Baseline: `agents` at `38e1fc0`, project Milestone 226.
Status: GI-01 through GI-04 implemented, verified and pushed. GI-05 investigation
requires a response-association decision below; no GI-05 runtime changes made.
Later milestones are not started.

This plan covers the assessment of the latest adult interaction and the two
children's interactions, including the subsequent gesture review. The user authorized consecutive implementation on 2026-10-21: finish each
milestone, verify it, commit and push all milestone changes, then continue to the
next. Halt only for an unforeseen decision requiring the user. Final physical
acceptance is grouped at the end; intermediate automated/provider gates remain.
Saved-instance migrations are not implicitly authorized. Assign project milestone
numbers when implementing.

## Outcome and architecture

Gigi should converse naturally, start only confirmed activities, finish them
clearly, react selectively to the surroundings, and use occasional distinctive
gestures and warm self-irony. Failure recovery should be truthful and bounded.

Retain the existing explicit reception, guessing, RPS and configurable-task
states. PROMETHEUS owns accepted requests, state transitions, validated facts,
game outcomes, task agreements and nonverbal actions. GPT-Live owns conversational
wording, including task responses and authorized proactive comments. Extend
`LiveActivityContext` and the existing delivery/provenance mechanisms; do not
create another controller, speech queue or social-motivation subsystem.

No plan or unit suite can guarantee zero regressions or constrain every generated
utterance. Each milestone therefore has deterministic acceptance tests and a
separate boundary/provider/physical gate where appropriate. A prompt assertion,
scripted journey or append acknowledgement is not proof of actual speech,
physical action, secrecy compliance or natural conversation.

## Evidence and limits

- Adult trial: `prometheus-telemetry-2026-10-21T18-29-26-584Z.json`.
- Children's trials: `luca.json` and `fabi.json`. The last Live session in each
  cumulative export is the relevant session. Database transcripts were retrieved
  after quota recovery during the review.
- Luca: Live invited an RPS sign without a backend game start. Three scissors
  observations had confidence 0.791-0.845; the stored user input contained no
  game-selection request. This is not evidence that lowering confidence would
  fix the problem. The available records cannot recover missing microphone audio.
- Fabi: two completed RPS rounds had matching spoken results. Three distinct
  contributions were merged into one 22-second user segment.
- Adult: the guessing introduction disclosed its target; a natural correct-guess
  variant was not recognized by the local fallback. Task timing/sequence and
  spoken agreement details also diverged from the requested interaction.
- Sensor-driven greetings/mood comments were repetitive. Counts and expression
  estimates fluctuated; stable tracking identity and speaker-to-face/hand binding
  are not established capabilities.
- Expressive gestures were sparse: two in each child's session. The adult record
  included three successive `EXPLAIN` gestures about nine seconds apart. Neither
  Gala nor Generic's dialogue projection supplies recent gesture history.
- Isolated local replays reproduced an invalid decorative gesture clearing a
  valid game, `Ist es ...?` versus `War es ...?` matching inconsistency, and omitted
  incremental `taskVoice` guidance. Task capture still depends on backend wording.
- The verified instruction maximum was 15,854/16,000 UTF-8 bytes. Database quota
  failures persisted after earlier reductions. Clipped physical audio remains
  unresolved; recorded text and healthy transport statistics cannot exclude it.

Use small synthetic fixtures reproducing these timings and conditions. Do not
commit private transcripts, children's recordings, credentials or production
database dumps. Existing ignored review artifacts are evidence, not test inputs
that a clean checkout must depend on.

## Invariants and implementation boundaries

1. State/control authority stays in PROMETHEUS. Native assistant speech, quotes,
   hypothetical requests, noisy input and ordinary sensor refreshes cannot
   authorize a game or task. A generated invitation is not an accepted request.
2. During Gala Live, ordinary backend plans omit `speech`. Proactive speech is
   authorized with facts and purpose, with wording still produced by Live.
   Native speech recording may retain `BehaviourPlan.speech`; do not strip the
   conversation ledger. Delayed game results remain user-request continuations.
3. Preserve scoped access checks, serialized mutations, durable receipt/segment
   claims, epochs, post-commit publication and no automatic replay of uncertain
   actions. At-most-once dispatch is not a guarantee of exactly-once audibility.
4. Stop, revocation, reset, switch and session loss fence late work. Preserve the
   immediate local audio stop on observed zero visitors; social smoothing must
   not silently delay it. Missing sensing is not observed departure.
5. Preserve public HTTP/SSE and canonical behaviour-plan contracts. Keep compact
   provider encoding internal. Text/Continuous, Talk to Me and non-opted-in
   agents retain their existing speech and task contracts.
6. Do not rewrite saved task semantics or custom prompts. Prefer new Gala
   instances for revised policies. Any necessary data/schema change needs an
   explicit compatibility design, reload tests and documented deployment order.
7. Prefer bounded existing storage/projections over extra history reads. Add no
   model request per frame, per transcript fragment or solely for gesture variety.
   Diagnostics must not add database polling or leak dialogue/hidden identities.
8. No automatic detector changes, multi-hand attribution, locomotion, arbitrary
   tools, new workflow language or paid capacity changes are included.

Shared runtime work follows `feature/gptlive` -> `main` -> `agents`; application
prompts, Gala policies/reference and Gala-only tests remain on `agents`. Check
actual worktrees before implementation and preserve unrelated changes. The current authorization includes milestone commits/pushes and the resulting
agents deployment. Preserve the established branch boundaries.

## Milestones and dependencies

Implement one reviewable milestone at a time. Run its focused checks, record
actual results and remaining gates, update affected documentation, commit and push,
then continue. A dependency requires its implementation and relevant automated
checks, not an intermediate live test by the user. GI-12 retains final physical
acceptance; an unresolved provider limitation needing a product decision stops work.

| ID | Deliverable | Prerequisites |
| --- | --- | --- |
| GI-01 | Consistent activity context and bounded instructions | None |
| GI-02 | Reliable user-turn admission and short-reply context | GI-01 |
| GI-03 | Confirmed games and non-destructive recovery | GI-01, GI-02 |
| GI-04 | Faithful executable task agreements | GI-01 |
| GI-05 | Live-owned task wording and bounded response tracking | GI-03, GI-04 |
| GI-06 | Deliberate proactive and sensory reactions | GI-03, GI-05 |
| GI-07 | Concise dialogue and warmer self-irony | GI-05, GI-06 |
| GI-08 | Occasional, distinctive gesture behaviour | GI-03, GI-06 |
| GI-09 | Understandable hand-sign readiness and feedback | GI-03, GI-08 |
| GI-10 | Speech-delivery evidence and targeted cutoff correction | GI-02, GI-05 |
| GI-11 | Sustained database capacity and operational recovery | GI-01 through GI-10 |
| GI-12 | Integrated acceptance and reviewed rollout | GI-01 through GI-11 |

### GI-01 - Consistent activity context and bounded instructions

Status: IMPLEMENTED; 101 unit/integration cases and packaging passed. See results.

**Outcome:** startup and state changes expose the same applicable voice contract,
current facts and activity revision, with adequate instruction headroom.

Scope:

- Consolidate overlapping Gala/shared task instructions; handle added, changed
  and removed dynamic guidance sections, including `taskVoice`. Opening guidance
  remains startup-only and must not be replayed by a general section iterator.
- Project current mode, phase, accepted request, pending outcome and termination
  status from authoritative state/storage. Avoid introducing another mutable
  copy of activity state. Keep reference facts separate from behavioural rules.
- Preserve `USER_REQUEST` versus `PROACTIVE` and source/epoch/revision identity.
  Define bounded expiry/cancellation semantics for pending speaking occasions.
- Establish secrecy guidance before transmitting target facts; do not assume
  chunked provider updates are atomic. GI-03 supplies game-specific acceptance.
- Target at most 14,400 bytes for the worst assembled Gala instructions, reserving
  10% of the existing 16,000-byte ceiling. Never truncate rules or raise the ceiling
  to pass. Test activity facts, continuation and transport chunk limits separately.

**Unit acceptance:** table-driven startup/transition tests cover reception, both
games, task proposal/running/waiting/completion and return. Added/removed guidance
is delivered once, unchanged guidance is not resent, native history churn does
not echo speech, and old epochs cannot apply. Budget tests include both embodiments,
all person cards and maximum accepted task/draft strings, including multibyte text.

**Anchors:** `LiveVoicePolicyAdapter`, `LiveContextDelivery`, `LiveActivityContext`,
`GalaDinnerPolicy`; extend `LiveActivityContextUnitTest`,
`LiveContextDeliveryUnitTest`, `GalaDinnerLiveDeliveryUnitTest` and
`GalaDinnerReferenceUnitTest`. Verify slow/partial acknowledgements and bounded
chunks through the existing delivery harness. No provider compliance claim yet.

### GI-02 - Reliable user-turn admission and short-reply context

Status: IMPLEMENTED; 32 focused unit cases, 28 isolated integration cases and packaging passed.

**Outcome:** supported acoustic boundaries separate distinct turns without
splitting the intended short pause within a question, and missing input remains
explicitly unknown rather than becoming an inferred command.

Scope:

- Trace provider fragments, audio-activity coverage, closure and backend admission
  separately. Determine the cause of the merged-turn fixture before altering
  thresholds. Distinguish missing provider text from locally lost/merged text.
- Use observed acoustic/turn evidence supported by the actual API. Transcript
  arrival gaps, an assistant reply, or a timer alone must not prove a complete
  user request. Retain bounded incomplete closure when evidence is insufficient.
- Bind short answers to the relevant established question/activity revision;
  an unrelated old announcement must not make every later `Okay` ambiguous.
- Preserve duplicate receipt handling, original text, out-of-order/late handling,
  scope/epoch checks and a single durable admission. Do not reconstruct absent
  commands from the assistant's invitation or automatically retry uncertain turns.

**Unit acceptance:** synthetic acoustic/fragment traces cover a question with a
one-second internal pause, three separately completed contributions, overlap,
late fragments, coverage gaps, duplicate IDs, missing text, maximum open time,
and stop before admission. Complete valid turns admit once; uncertain input never
controls state. Recent unambiguous `Ja` works; an unrelated old intent does not
block `Okay`; an answer across an unresolved activity change requires clarification.

**Anchors:** `LiveTranscriptSegmenterUnitTest`,
`LiveTranscriptCaptureServiceUnitTest`; extract a small pure context decision for
short replies if needed. Extend `LiveTranscriptIngressIntegrationTest` and
`LiveTranscriptClaimIntegrationTest` for durable/restart/race guarantees. Re-run
the paused-question and rapid-follow-up physical scripts; unit success cannot
establish recognition quality for children's voices or recover omitted audio.

### GI-03 - Confirmed games and non-destructive recovery

Status: IMPLEMENTED; 57 unit cases, 12 isolated integration cases, focused reruns, provider trials and packaging passed. Physical acceptance remains at GI-12.

**Outcome:** a game invitation refers to a committed current game, results/reveals
survive terminal cleanup, and optional expressive failures do not destroy control.

Scope:

- Use one acknowledged control path tied to the admitted request and epoch.
  Represent pending versus committed start explicitly in Live context. Unknown
  selection offers a choice; a hand sign or assistant sentence never starts RPS.
- Verify the provider's available control/delegation mechanisms before claiming
  an enforced speech barrier. Reuse the existing path where sufficient; do not
  invent a turn-complete event or add a parallel acknowledgement route. If prompt
  guidance still permits premature invitations, record the failed provider gate
  and bring a scoped control-tool/audio-gating design for review rather than
  labelling the problem solved or silently sacrificing full duplex.
- Keep RPS selection/outcome deterministic; accept only eligible post-start signs.
  Preserve result facts under one source identity before returning to reception.
- Preserve guessing secrecy through introductions/partial context updates.
  Correct guesses and genuine give-up reveal once; stop/silence/expiry and
  hypothetical/negated give-up do not reveal. Keep supported yes/no, unknown fact
  and unclear/nonbinary question responses distinct.
- Replace brittle correct-name prefix matching with bounded direct-guess
  recognition; a passing name mention, quote or negation is not a correct guess.
- Normalize unsupported optional expressive labels to safe defaults, following
  the Generic pattern. Malformed control/results remain invalid. Preserve the
  current valid activity on recoverable inference/cosmetic failure; explicit
  cancellation and expiry still end it. Do not automatically replay uncertain work.

**Unit acceptance:** unspecified choice, delayed start, missing input, duplicate
admission, early/stale/duplicate signs, one valid result, reset during start and
stop before result. Test `Ist es Albert Einstein?`, `War es Albert Einstein?`, a
wrong name, a negated name and a hypothetical mention. Test reveal/stop variants,
partial context ordering and outcome retention after cleanup. Invalid decorative
output preserves target/task; invalid control cannot start or mutate an activity.

**Anchors:** `GalaDinnerReceptionUnitTest`, `GalaDinnerLiveDeliveryUnitTest`,
`GalaDinnerReceptionIntegrationTest` and the Gala journey. Real Live trials must
show no early invitation, premature identity disclosure, repeated reveal or
invented RPS verdict. Model-visible cards are not a hard secrecy boundary.

### GI-04 - Faithful executable task agreements

Status: IMPLEMENTED; 91 focused unit cases, 19 isolated integration cases, provider
configuration/voice review and packaging passed. Physical acceptance remains GI-12.

**Outcome:** the accepted task performs the promised action at the promised time,
with the promised count and lifecycle; unsupported sequences are not flattened.

Scope:

- Derive agreement facts from the validated task: trigger, output channels, total
  actions, first-action timing and termination. Proposal/revision never executes.
- Support a clearly represented future-triggered first action for new Gala
  agreements: acceptance arms it without consuming an action. Preserve immediate
  activation as an explicit alternative. Existing saved tasks and Generic defaults
  retain their original immediate-action meaning; validate any additive format.
- Do not build a sequence/workflow engine. A request such as "first rock, then
  happy, then stop" must either fit the existing executable semantics exactly or
  receive a precise clarification/alternative before acceptance.
- Keep explicit proposal acceptance, bounded counts and all requested modalities.
  In Gala, stop/pause/topic change/departure/session close/idle expiry discard the
  agreement. Generic pause/resume remains distinct and unchanged.

**Unit acceptance:** proposal and revision produce no action; accepting a
future-triggered one-action task produces none immediately, one on a fresh cue,
then completes. Immediate one-action tasks execute once on acceptance. Test wrong
cue, duplicate cue, WAIT versus COMPLETE, count limits, rejected compound sequences,
and speech-only versus explicit hand-sign output. Invalid replacements preserve
the prior agreement. Reload fixtures preserve legacy first-action semantics.

**Anchors:** `TaskSpec`, `TaskUpdateAction`, `GalaDinnerTaskAction`;
`GenericMultimodalTaskUnitTest`, `TaskLifecycleUnitTest`, `GalaDinnerTaskUnitTest`,
`GenericMultimodalTaskIntegrationTest`. Keep canonical/compact decoding equivalent.
Provider review must check that spoken proposals retain every material term.

### GI-05 - Live-owned task wording and bounded response tracking

Decision needed (2026-10-21): Live output transcript fragments have no guaranteed
response/occasion identity or completed-turn event. Both GI-04 WebRTC trials
returned no client_event_id on generated speech (0/69 and 0/91 fragments). The
optional field in the API therefore cannot be assumed available. Current ingress
records possible backend intents as AMBIGUOUS, and TaskDecision uses wording to
associate the actual response. Append acknowledgments and measured silence do
not establish which free-form response was spoken or whether it was heard.

Proposed choices, requiring user direction before replacing that gate:

1. Recommended: one bounded semantic check per pending task response, after a
   completed captured segment. Bind it to the occasion, revision and epoch;
   reject short acknowledgments, unrelated/ambiguous text and stale identities.
   Deadline/provider failure ends automatic progression as uncertain, never
   replaying a physical action. This adds model latency/cost and probabilistic
   association; it is not proof of audible delivery. No per-fragment or per-frame
   checks, no extra generation of spoken answers.
2. Explicit visitor confirmation before another automatic task step. This avoids
   semantic inference as the release condition but changes the interaction by
   asking the visitor to continue after each response. It does not prove playback
   either; it is explicit authorization to proceed.

Neither option is implemented. A timer-only release or treating every substantive
caption as the task response would violate the acceptance criteria below.
Evidence and official API references are in GALAIMPROVEMENT_RESULTS.md.

**Outcome:** Gala tasks use the same Live verbal ownership as games, without
waiting indefinitely for backend sentences to appear verbatim in the transcript.

Scope:

- Replace Gala's backend-composed task prose with validated agreement/action facts
  and an identified speaking occasion. Backend plans retain nonverbal/motion
  actions. Exact words deliberately requested by the visitor remain an explicit
  content requirement. Default Generic/Text/Continuous speech stays supported.
- Tie pending response state to request/action identity, revision and epoch.
  Replace sentence matching for the opted-in path with the strongest available
  correlated evidence. Short acknowledgements or unrelated speech do not release
  a pending action boundary. Do not label caption completion as physical playback.
- Define bounded pending/captured/uncertain/cancelled outcomes and one recovery
  path. On deadline expiry, prevent further automatic task steps and expose an
  uncertain outcome; request clarification/new agreement as appropriate. Never
  repeat the prior physical action or assume it failed because its speech is absent.
- Capture terminal facts before cleanup; clear active target/task data afterwards.
  Cancel obsolete outcomes on departure, stop, reset, revocation and new epoch.
  Use existing scheduling/streams rather than an additional polling loop.

**Unit acceptance:** normal paraphrase can establish the permitted response
boundary; `Okay`, unrelated speech, wrong IDs and stale revisions cannot. Missing
or ambiguous completion reaches bounded recovery, never another action. Test
completion before/after state cleanup, lost ACK, duplicates, reconnect and stop
during pending output. Gala's generated plans omit ordinary prose; native speech
is recorded once without re-entering control or TTS. Legacy task behaviour remains.

**Anchors:** `TaskDecision`, `TaskMemory`, `GalaDinnerTaskPolicy`,
`LiveContextDelivery`; `TaskLifecycleUnitTest`, `LiveSpeechExecutionUnitTest`,
`LiveSpeechPersistenceIntegrationTest` and all three representative Live journeys.
Resolve any unavailable provider correlation explicitly; do not replace a brittle
text match with a timer that falsely certifies completed speech.

### GI-06 - Deliberate proactive and sensory reactions

**Outcome:** Gigi can initiate useful comments, but sensor noise cannot create
repeated greetings, mood assertions or interruptions.

Scope:

- Reuse `PROACTIVE` occasions for admitted ambient comments. One initial greeting
  belongs to session opening; stable additional arrivals/departures while others
  remain may warrant a short comment. Count changes do not establish identity.
- Adapt Social Space's fresh-count/duplicate/stale guards; add measured stability
  and cooldown for ambient comments. Preserve the immediate zero-person audio
  closure separately; do not queue a farewell after it.
- Consolidate perception freshness for Live, Gala and task consumers without
  changing unrelated task thresholds. A derived face summary cannot outlive its
  supporting observations. Evaluate face presence/detection quality as well as
  expression confidence; absent/stale data stays unknown.
- Permit tentative expression/apparent-mood comments and answers to direct
  questions. Accept corrections and suppress repeated unsolicited assertions
  about that disputed interpretation within the current encounter.
- User turns, pending results and active/waiting tasks outrank ambient comments.
  Explicit quiet suppresses them until re-engagement. Expire superseded comments;
  never build a backlog. Weather updates alone do not force unsolicited speech.

**Unit acceptance:** initial occupied view, repeated counts, regrouping duplicates,
rapid 1/2-person fluctuation, stable arrival, out-of-order/future/stale readings,
face-label oscillation, expired summaries, visitor correction, quiet/re-engagement,
active task, pending result and departure during dispatch. Eligible changes produce
at most one occasion; raw refreshes produce none; no sensing implies no claim.

**Anchors:** `SocialSpacePresenceDecision`, Gala face/presence actions,
`LiveContextProjection`; extend `SocialSpaceAgentUnitTest`,
`GalaDinnerReceptionUnitTest`, `LiveContextProjectionUnitTest`, delivery tests and
`tests/js/live/reception.test.mjs`. Use fake clocks; physical trials verify cadence.

### GI-07 - Concise dialogue and warmer self-irony

**Outcome:** Gigi is more playful without becoming repetitive, dismissive or vague
about actual failures and task status.

Scope:

- Consolidate one voice policy for short natural replies, at most one question,
  no habitual echoes/preambles and no repeated invitation after refusal.
- Use occasional self-deprecation and callbacks about Gigi's limitations. Recovery
  combines truthful status and a concrete next step; humour is optional. Acting
  bored with visitors is not a default, and never changes task/lifecycle state.
- Unclear speech gets a simple clarification instead of an elaborate personal
  interpretation. Adapt to the conversation/request without inferring age or
  identity from the camera. Avoid jokes at the visitor's expense or during distress.
- Preserve djee-djee pronunciation guidance without reciting phonetic directions,
  event/reference accuracy, requested language and full task-agreement meaning.

**Unit acceptance:** contract tests verify a single applicable policy in every
activity and its budget; structured recovery fixtures preserve the correct reason,
next step and activity state. These tests do not score humour or prove compliance.

**Provider gate:** a fixed small set covers ordinary conversation, correction,
unclear speech, refusal, a technical hiccup, repeated joke requests and a serious
remark. Review actual speech for brevity, variation, no echo, no blame and no
repeated name/introduction. Record examples and failures without adding exact-
sentence assertions for generated prose. Anchors: `GalaDinnerPrompts`, reference
tests, Gala journey and `tests/gptlive/GALA.md`.

### GI-08 - Occasional, distinctive gesture behaviour

**Outcome:** gestures emphasize meaningful moments without accompanying every
utterance or repeatedly performing the same decorative movement.

Scope:

- Share the existing semantic gesture meanings. Supply a bounded recent-gesture
  summary to the existing selection request; do not make another model call.
- Add local cooldown/repetition suppression for decorative gestures. Use `NONE`
  if no suitable distinct gesture is justified; do not rotate labels randomly.
  Track published requests, not alleged physical execution. Reset encounter-local
  history appropriately; reload/replay must not re-execute gestures.
- Keep requested/task-required gestures and RPS `motion.handSign` distinct from
  decorative gestures and exempt from their suppression. Maintain safe label
  normalization from GI-03. Face/gaze may provide subtler expression independently.
- Produce one expressive beat per eligible occasion, never per transcript chunk
  or native-speech recording. Keep timing claims coarse; word alignment is not
  supported. Do not rewrite whole plans merely to obtain gesture variety.

**Unit acceptance:** first justified gesture, same-label repetition, different
label during cooldown, allowed later gesture, `NONE`, explicit gesture request,
RPS sign and agreed task gesture. Duplicate publication/reconnect/late epochs do
not execute again. Invalid decorative output preserves other valid channels and
activity state. Assert no added inference or query per gesture-history update.

**Anchors:** Gala action/plan construction, `TaskMemory`, `ValerianCorePrompts`;
extend Gala/task unit tests and add a small pure cadence-policy unit suite if
extracted. Browser checks distinguish current-plan indicators from execution
acknowledgements. Robot trials verify distinctiveness, timing and actual motion.

### GI-09 - Understandable hand-sign readiness and feedback

**Outcome:** the visitor/operator can distinguish an unstarted game from a hand
that was not detected, rejected or already accepted.

Scope:

- Reuse the existing activity footer, display plans and bounded cue diagnostics
  for not-ready, waiting, low-confidence/stale, accepted and completed states.
  Keep normal guest wording short and technical details in operator diagnostics.
- Make browser emission and backend acceptance thresholds intentionally consistent
  or explicitly distinguish their roles. Do not lower acceptance solely because
  Luca's unstarted game missed valid observations; retain freshness/stability.
- Explain one visible hand and a brief steady pose when useful. Do not claim
  speaker-to-hand attribution. Held-sign retries cannot create another round.
- Preserve actual game state as the sole readiness authority; no new sampling
  frequency, per-frame database writes or automatic game activation.

**Unit acceptance:** boundary confidences, unstable/stale/out-of-order signs,
camera disabled, emission failure, sign before start, valid sign after start,
duplicate/held sign after resolution, new round and cancellation while waiting.
Assert the feedback reason and state agree, and only one result/action is emitted.

**Anchors:** Gala RPS unit tests, `tests/js/live/hand-sensors.test.mjs`,
`tests/js/live/sensors.test.mjs` and the existing footer/display browser tests.
Run one real-camera acceptance path including repositioning and a held sign.

### GI-10 - Speech-delivery evidence and targeted cutoff correction

**Outcome:** the next clipped response can be located more precisely, and any
reproduced application-side cutoff has a targeted regression test.

Scope:

- Correlate response/occasion identity with existing provider output activity,
  transport, local mute/stop and playback observations where available. Record
  bounded reason/timing metadata; no raw audio or transcript in telemetry.
- Distinguish natural output end, intentional interruption, context/state update,
  lifecycle closure, transport failure and unknown end. Do not import Continuous
  PCM assumptions into Live's WebRTC playback path or invent provider signals.
- Verify ordinary context/state updates do not detach/mute output or prematurely
  close capture. Correct only a demonstrated fault; retain immediate intentional
  stop/barge-in and existing media ownership. No speculative buffer/VAD tuning.

**Unit acceptance:** synthetic output plus context update preserves playback;
explicit stop/departure/revocation still closes promptly; delayed callbacks cannot
revive it. Diagnostic fixtures label missing evidence unknown, bound retention
and exclude content. Text/Continuous delivery behaviour remains unchanged.

**Anchors:** `LiveAudioDiagnosticsUnitTest`,
`tests/js/live/audio-diagnostics.test.mjs`, `diagnostics.test.mjs`, `client.test.mjs`
and focused GPT-Live browser smoke. A physical script records a specific clipped
sentence, missing word, time and caption comparison. If no cause is reproduced,
complete the observability work but leave the audible-cutoff fix explicitly OPEN;
create a focused corrective follow-up from the evidence before claiming resolution.

### GI-11 - Sustained database capacity and operational recovery

**Outcome:** the intended reception load has measured database headroom, and
backend failure cannot masquerade as a successfully started activity.

Scope:

- Measure the final changes against the existing query budgets and a repeatable
  mix of capture receipts, sensing, state updates, monitor/SSE and reconnects.
  Include camera activity after Live stops, since Stop Live does not stop sensing.
- Preserve prior reductions, immediate authorization checks and durable writes.
  Remove demonstrated redundant reads only; do not solve quota pressure by
  silently losing transcript durability, lowering sensing cadence or caching
  permissions. Avoid new polling for gesture/occasion/diagnostic state.
- Specify expected concurrent instances and operator pages, measure idle and peak
  load, and compare against the verified account quota. Target at most 75% of that
  quota under sustained intended load. For 36,000 questions/hour this is 27,000/hour;
  the prior roughly 2,000/minute synthetic result does not establish readiness.
- If the target cannot be met safely, record the required capacity or operating
  limit as an unresolved deployment decision. Do not purchase capacity or silently
  disable sensing. Expose bounded operator recovery and no false ready/result state
  during connection/quota failures; avoid automatic retry storms.

**Unit acceptance:** coalescing, bounded queues/retention and failure classification
under a fake clock; transient/quota failure cannot accept a new activity or replay
an uncertain action. Unit mocks do not establish SQL counts or throughput.

**Integration gate:** extend `LiveQueryBudgetIntegrationTest` and the existing
Gala mixed-minute regression in `QueryVolumeIntegrationTest`; run on disposable
local MySQL. Record actual queries, transactions, duration and load assumptions,
plus a sustained soak. Capacity readiness is blocked until the measured headroom
or an explicitly agreed operational/capacity alternative is established.

### GI-12 - Integrated acceptance and reviewed rollout

**Outcome:** improvements work together, regressions are bounded, and release
claims match actual automated/provider/physical evidence.

- Extend the existing representative happy paths, not a second journey framework:
  `GalaDinnerReceptionJourneySmokeIntegrationTest`,
  `GenericMultimodalTaskIntegrationTest` (representative Live journey), and
  `SocialSpaceLiveJourneySmokeIntegrationTest`. Preserve canonical/compact coverage.
- Gala journey: opening -> ordinary/reference question -> unspecified game choice
  -> confirmed guessing start without disclosure -> supported/unknown/unclear
  questions -> correct guess and separate give-up/stop cases -> confirmed RPS
  start/sign/verdict -> agreed task/cue/completion -> reception. Include controlled
  noise, repeated sensor readings and one bounded failure/recovery path.
- Generic journey: proposal -> acceptance -> intended first action -> cue ->
  WAIT/resume or completion, preserving its own lifecycle and default semantics.
  Social Space journey: one stable arrival comment -> no duplicate refresh reply
  -> task priority -> return to eligible ambient behaviour after completion.
- Exercise persistence/reload, slow/lost acknowledgements, stop/reset/revocation
  races and late old-session events. Run affected shared Java/Node/browser suites,
  packaging, and the Text/Continuous/Live-disabled smoke paths after shared changes.
- Update `tests/gptlive/GALA.md` with adult and short/simple-language paths,
  explicit game readiness, future cue, gesture cadence, corrected facial estimate,
  quiet/re-engagement, arrivals and acoustic evidence collection.
- Run a recorded provider matrix with at least three fresh-session repetitions
  of critical game start/secrecy/result/reveal/task paths, including delayed
  backend confirmation. Zero premature starts, disclosures, duplicate outcomes or
  unauthorized task actions is the release criterion for that matrix; three
  successes are evidence, not a statistical guarantee. Review humour/echo/gesture
  quality separately. Do not repeatedly rerun failures until one passes.
- Run headset and intended reception/robot trials with observed final words,
  correct game results and actual gesture execution. Distinguish live-provider
  tests from synthetic journeys in every report. Unrun checks remain NOT RUN.
- Before any separately authorized deployment, document the target commit,
  compatible instance strategy, available previous release and rollback steps.
  Verify migrations first if any; do not mutate existing agreements implicitly.
  Post-deployment health checks do not substitute for a fresh-instance journey.

**Completion gate:** all deterministic contracts and required integration checks
pass; provider/physical/capacity gates are recorded with their real status.
Unresolved audible clipping or failed Live readiness/secrecy trials stay open;
do not declare the whole improvement programme complete based only on unit tests.

## Coverage of the agreed backlog

| Improvement discussed | Milestones |
| --- | --- |
| 1. Confirm activity readiness before invitation | GI-01, GI-02, GI-03, GI-12 |
| 2. Turn capture, missing input and short replies | GI-02, GI-12 |
| 3. Live owns all Gala wording, including tasks | GI-01, GI-05 |
| 4. Consistent instructions and guessing secrecy | GI-01, GI-03, GI-12 |
| 5. Deliberate restrained proactive speech | GI-06 |
| 6. Facial/social interpretation and clarification | GI-06, GI-07 |
| 7. Stronger warm self-irony and truthful recovery | GI-03, GI-07 |
| 8. Occasional distinctive gestures | GI-08 |
| 9. Cosmetic failures preserve activities | GI-03, GI-08 |
| 10. Natural correct guesses and task paraphrases | GI-03, GI-05 |
| 11. Faithful trigger timing, sequence, counts and lifecycle | GI-04, GI-05 |
| 12. Correlated response tracking and bounded recovery | GI-03, GI-05, GI-10 |
| 13. Hand-sign readiness, thresholds and feedback | GI-09 |
| 14. Context simplification, freshness and database headroom | GI-01, GI-06, GI-11 |
| 15. Regression journeys and real speech/robot verification | GI-10, GI-12 |

## Test execution and documentation

Use the smallest meaningful tests at each layer: pure unit tests for validation,
decisions, identity/cadence and fake-clock lifecycle; integration tests for actual
commit/claim/reload/SQL behaviour; browser tests for media and UI lifecycle; real
provider/physical trials for generated speech, acoustic and robot quality.
Do not mirror implementation in tests or equate prompt substring tests with
behavioural acceptance. Add fixtures only for changed contracts and real failures.

For database-backed checks use the existing isolated runner with an explicit local
test configuration, for example:

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinnerReceptionJourneySmokeIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest
```

The runner provisions a disposable local schema and synthetic endpoints. Never
run application tests against credentials in production `application.properties`.
Check current test names/options before execution. Source-only unit suites need
no live database, paid provider or production application.

When implementation begins, create `.agents/GALAIMPROVEMENT_RESULTS.md` recording
per milestone: commit/tree, scope, exact commands, counts/results, instruction and
query budgets where affected, provider/physical evidence, limitations and next
gate. Update relevant README/current PROJECT status and selectively the project
context when behaviour changes. Keep historical audit factual, not a duplicate
roadmap. Existing contradictory older context descriptions must be reconciled
with the implemented ownership model when that milestone lands.

Proceed consecutively from GI-01 through GI-12 under the current authorization.
After each milestone report changes, verification and remaining gates, commit/push
and continue. Do not mark final physical acceptance passed before the user trial.
Halt for an unforeseen decision the user must make, with concrete evidence and options.
