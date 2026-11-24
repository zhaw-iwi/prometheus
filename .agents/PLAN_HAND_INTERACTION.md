# Coordination hand interaction: signs, finger poses and pointing

Prepared: 2026-11-23. Baseline: main `025697df`.
Status: **HI-01 through HI-11 COMPLETE; HI-12 INCOMPLETE**.
The current paid allocation is exhausted; required Live retests remain open.
HI-12 acceptance revision 2: owner decision of 2026-11-24 preserves native Live speech independence.
Development branch: `main`; deployment branch: `agents`.

The owner authorized sequential implementation on 2026-11-23: complete and verify
one milestone, commit and push it to main, then continue to the next. The new
OpenAI allowance is a cumulative hard cap of $50 including failures, backend
inference, native audio, synthesis and grading. Use a separate bounded ledger;
previous exhausted authorizations do not carry forward. No deployment or physical
robot acceptance is authorized or implied. See `HAND_INTERACTION_RESULTS.md` for
actual evidence and remaining limits.

## Goal and working model

Users can negotiate what a detected hand signal should cause the Coordination
Agent to do, or request an immediate demonstration from the robot. Both routes
use the same bounded vocabulary and explicit backend authority.

There are three interpretation layers, usable independently or together:

1. **Named signs:** recognisable shapes such as thumbs up, peace or an OK ring.
2. **Finger poses:** explicit conditions or requested configurations of individual
   fingers on one or two hands.
3. **Pointing:** a pointing pose with a direction, optionally resolved to a region,
   person or item in an identified scene.

**Sequences** add ordered steps and timing to these layers. They are a bounded
extension of Coordination task execution, not a fourth sensor or a general-purpose
workflow language. Recognition and execution have separate capabilities and
separate evidence: a detected human pose, a published robot plan, an observed
avatar rendering and a physical actuator completion are different facts.

| User request | Required route |
| --- | --- |
| Which signs can you recognise or show? | Capability answer distinguishing input from output; no demonstration. |
| Show thumbs up. | One immediate validated demonstration; no task negotiation. |
| Extend your left index and little finger, keeping the others bent. | One explicit pose through the same direct-command path. |
| Whenever I show thumbs up, show peace. | Propose, prepare if requested, accept, then wait for fresh cues. |
| When my right index is stretched and the others are bent, tell one joke. | Persist the precise pose condition and finite response budget. |
| When I point toward the display, explain the slide. | Agree the reference frame and target, then resolve fresh pointing evidence. |
| Point to your left. / Point at the selected cup. | One directional or target-resolved command; clarify an unresolved target. |
| When I show one, then two, then three, show an OK ring. | Bounded input sequence with persisted progress and one completion action. |
| Count from one to five on your fingers. | One finite output sequence, with explicit progress and cancellation. |

The reference implementation is the existing Valerian browser Robot embodiment.
Define reusable output and execution contracts for other embodiments. Do not
claim that an unspecified physical robot can articulate fingers or reach spatial
targets. Hardware acceptance requires a named adapter, its declared capabilities,
calibration and an actual operator trial; it is tracked separately from software
acceptance and is not silently omitted from a physical deployment claim.

## Starting evidence and implementation anchors

- Marc's combined MediaPipe detector measures up to two hands and publishes
  `obs.hand.sign` and `obs.hand.pose` separately. It currently selects one RPS sign
  across both hands. Finger bend is numeric 0..1, with straight below 0.4, bent
  above 0.55 and neutral in the inclusive middle range; unavailable is null.
- Coordination explicitly excludes hand poses in its persisted profile and
  rejects pose preparation/reaction agreements. Generic/Social Space already
  support bounded same-hand pose conditions. Remove the Coordination exclusion
  deliberately, with profile, agreement, prompt and lifecycle coverage together.
- Direct commands currently accept three RPS signs and five expressive gestures.
  They already validate capability, task conflicts and admitted-turn identity,
  preserve agreements, and distinguish published output from physical execution.
- The browser robot has articulated fingers, but only three named hand poses are
  mapped. Per-finger output, directional pointing and target reaching are new work.
- Social sensing has local person boxes and temporary tracks. Published social
  observations omit spatial positions; other detector object classes are filtered
  out. There is no public target registry or calibrated shared scene frame.
- Current hand world landmarks are hand-relative; they are not room coordinates.
  Hidden fingers can be inferred. Neither handedness nor an array index identifies
  a person or establishes a persistent hand track.
- Main's current Coordination Live instructions use 15,997 of 16,000 bytes.
  Extend compact capability/selected-task descriptions; do not append the whole
  new vocabulary and grammar to every Live session or raise the bound silently.
- Baseline review ran 44 focused Java and 36 JavaScript tests successfully against
  this exact source revision. This is regression evidence, not acceptance of any
  feature in this roadmap. No new camera, provider or physical trial was run.

Start with these existing files and adjacent tests; inspect their current callers
before editing rather than creating a parallel hand-control subsystem:

| Responsibility | Implementation anchors | Existing test anchors |
| --- | --- | --- |
| Geometry and camera admission | `public/valerian/hand/finger-pose.js`, hand/social functions in `public/valerian/script.js`, `ObservationEvidence` | `tests/js/hand/finger-pose.test.mjs`, `tests/js/live/hand-sensors.test.mjs`, `valerian-finger-pose.spec.mjs` |
| Pose rules and Coordination | `HandPoseCondition`, `TaskSpec`, `TaskDecision`, `CoordinationAgreement`, `CoordinationAgent`, `CoordinationUpdateAction`, `CoordinationPerception` | `HandPoseTaskUnitTest`, `CoordinationHandPoseUnitTest`, `CoordinationObservationTimingUnitTest`, `CoordinationAgentIntegrationTest` |
| Immediate output and capabilities | `DirectCommands`, `BehaviourPlan`, `AgentInteractionProfile`, `AgentCapabilityDescription` | `DirectCommandsUnitTest`, `DirectCommandAgentsUnitTest`, `DirectCommandIntegrationTest`, profile contracts |
| Visual execution | `public/valerian/embodiment/model.js`, `robot.js`, cockpit lifecycle and SSE consumers | `tests/js/embodiment/model.test.mjs`, `rig.test.mjs`, `valerian-embodiment.spec.mjs`, `valerian-commanding.spec.mjs` |
| Live authority and acceptance | `LiveVoicePolicyAdapter`, `LiveTaskResponseService`, `TaskResponse`, existing transcript/playback lifecycle | Coordination delivery/voice tests, native Live browser specs, `tests/gptlive/run_acceptance.py`, `LiveSmokeConfiguration`, commanding journeys/preflight |

Java paths above are under `src/main/java/ch/zhaw/prometheus`; browser paths are
under `src/main/resources`. Test class names are existing anchors, not promises
that new behavior is already covered. Add focused neighboring suites where needed.

External reference: [MediaPipe Gesture Recognizer output and built-in classes](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer).
Pin the actual SDK/model artifacts used by acceptance; documentation about a newer
model is not evidence that the repository's configured model behaves identically.

## Shared design decisions and boundaries

### Vocabulary and geometry

The initial complete named-sign catalog is ten physical shapes:

| Canonical name proposed for the new contract | User-facing aliases | First implementation |
| --- | --- | --- |
| `fist` | fist; rock in RPS | HI-02 detection, HI-03 output |
| `peace` | peace, V-sign; scissors in RPS | HI-02 / HI-03 |
| `open_palm` | open palm, halt; paper in RPS | HI-02 / HI-03 |
| `thumbs_up` | thumbs up, positive/OK sign when clarified | HI-02 / HI-03 |
| `thumbs_down` | thumbs down, negative/not-OK sign when clarified | HI-02 / HI-03 |
| `index_up` | index finger up, attention | HI-02 / HI-03 |
| `shaka` | hang loose; thumb and little finger extended | HI-04 |
| `horns` | rock 'n' roll; index and little extended, thumb folded | HI-04 |
| `middle_finger` | middle finger extended, other fingers folded | HI-04 |
| `ok_ring` | OK circle, diver's OK; thumb/index ring | HI-05 |

Physical names are distinct from task meanings. A thumbs-up observation is not
automatically consent, and an open palm is not a global Stop command. Existing
explicit Stop controls retain priority. Clarify an ambiguous request for "OK"
when its intended shape matters. Do not confuse horns with the thumb-extended
I-love-you shape. Additional catalog signs are outside this initial scope;
number displays are explicit poses/sequences rather than competing sign aliases.

Retain continuous bend and straight/neutral/bent/unknown on input. User wording
"stretched" maps to straight. Do not describe inferred landmarks as proven visible
fingers. Unknown does not match bent, straight or neutral. Do not manufacture a
finger-confidence score from handedness confidence. Named classifier scores,
geometric validity and temporal stability remain distinct evidence.

Named signs and custom poses consume the same frame evidence and common geometry.
Rules distinguish either hand, a specified side or two distinct hands. Do not
combine fingers from different hands to satisfy a same-hand condition, count a
hand twice or assume two hands belong to one person. Freeze mirroring/subject-side
semantics in HI-01. Conditions on an unspecified finger are unconstrained; direct
pose commands resolve unspecified fingers to documented relaxed defaults and
carry a complete, deterministic target pose to the renderer.

Orientation, finger spread and thumb/index proximity are additional primitives.
A geometric proximity estimate does not prove physical contact. HI-05 must define
which combinations are expressible and reject unsupported shapes explicitly;
"arbitrary pose" means compositions of the supported primitives, not every
anatomically possible configuration or an automatic gesture-learning system.

### Authority, persistence and output

- Use existing Event ingress, Storage, state transitions, scoped APIs and canonical
  BehaviourPlan publication. Keep task semantics in model/application code and
  sensing/rendering in clients. Do not let prompts execute arbitrary predicates.
- Retain preparation, explicit acceptance, original capture times, fresh cues,
  run/revision/owner fencing, finite contributions and separate playback evidence.
  Pose demonstrations during preparation spend no task action and cannot also
  become an execution cue. One physical observation published as sign and pose
  must not double-spend a task contribution.
- An unrelated direct request neither creates nor mutates a task. Preserve current
  conflict handling while task output is pending. A new user turn can deliberately
  repeat a command; ingress retry, reconnect, SSE replay or late completion cannot.
- Keep Text/Continuous canonical speech and native Live audio independent as in
  GLF/TWC. Do not revive the retired approval/committed-Speech players or introduce
  an extra ordinary TTS reply. Silent demonstrations remain explicitly supported.
- Extend `motion` with validated pose/pointing/sequence intents and declare their
  capabilities. Avoid model-authored actuator coordinates. An embodiment adapter
  translates semantic output into its own geometry and reports unsupported work.
- Track perception support, requested output support and connected embodiment
  execution support separately. A broad motion capability cannot authorize every
  new sign, pose relation or spatial frame. Reject before claiming execution when
  the active embodiment cannot perform the requested action.
- Preserve old `rock`/`scissor`/`paper` events, RPS outcomes and saved agreements.
  HI-01 chooses an additive/versioned mapping and tests both old and new consumers;
  no silent rename of persisted data. New definitions opt in explicitly. Saved
  agents need a visible compatibility check and explicit upgrade/recreation path,
  without deleting history or silently widening an existing agreement.

### Pointing and scene grounding

Direction is an estimate from a declared frame, not a target identity. Begin with
left/right/up/down and diagonals in the unmirrored camera plane, plus equivalent
robot/virtual-scene output directions. Forward/back and precise 3D targets require
a supported calibrated frame; do not infer depth from a 2D overlap.

Represent a pointing observation using bounded direction evidence, source hand,
frame ID/version, source time and a resolved/ambiguous/unresolved target result.
Candidate event names such as `obs.hand.pointing` and scene-target observation
contracts are proposals to freeze in HI-01, not existing APIs. Keep raw camera
images and full landmarks local under the existing sensing model.

Regions and targets have scoped IDs, type, display label, coordinate frame,
position/extent, timestamp/expiry and provenance (configured, detected or virtual).
Configured regions survive reload; live tracks and target resolutions expire.
Person tracking is not identity recognition. A name such as Marc requires an
explicit association; an automatically reused detector ID cannot inherit it.

Do not treat a projected finger ray crossing a person's box as proof of intent.
Use bounded candidates and an explicit ambiguity result. Distinguish the pointing
person from a possible target; if source association is uncertain, retain only
the supported direction. Resolve "my left", "your left", "that one" and "again"
from explicit current context. Never reinterpret an old capture in a new frame.

For object/person output, revalidate target identity, freshness and frame before
dispatch. A brief pointing command uses a resolved position snapshot, not endless
target pursuit. A physical adapter needs a calibrated scene-to-robot transform
and workspace validation. A digital avatar may demonstrate a direction or point
at targets in its registered virtual scene; that is not physical room pointing.

### Bounded sequences

Initial proposed limits, to encode and test in HI-01: 2 hands, 10 finger entries,
12 sequence steps, 120 seconds total input-sequence duration, 5 seconds maximum
between input steps by default, and 300 ms stable evidence per input step.
Output sequences have at most 12 steps, 100..5,000 ms hold per step and at most
60 seconds total; the ordinary default hold is 500 ms. These limits support a
0..10 counting journey. Wider bounds require an explicit contract change.

Fresh distinct samples establish dwell; one cached frame cannot satisfy elapsed
time. Unknown/lost evidence resets dwell and pauses advancement, without extending
the original deadline. A wrong stable step resets the partial input sequence;
expiry ends that attempt. Identical consecutive steps require an intervening
nonmatching release. Define input association loss and reacquisition explicitly;
never continue across an uncertain hand/person switch.

Static tasks retain their saved repeat/cooldown semantics. New agreements disclose
whether a held pose repeats after cooldown or must be released and re-entered.
Sequence completion fires once; repeated delivery of its last sample does not
restart it. One completed input sequence spends one agreed contribution; partial
steps spend none. An output sequence is one bounded command/contribution with
identified steps, not twelve unrelated inferred commands.

Persist task progress and execution identity. On reload/reconnect, partial input
progress is inspectable but requires explicit restart; uncertain output never
automatically resumes or replays. Stop/reset/supersession cancels unsent steps and
fences late callbacks. A renderer completion receipt is scoped to event, sequence,
step, owner and epoch; it is not native audio evidence or a physical execution
receipt. Extend existing execution boundaries only where such evidence is absent.

## Milestone overview

All statuses below are **NOT STARTED**. U = focused unit/contract tests; DB = real
local MySQL/HTTP/SSE smoke; V = Playwright behavioral and reviewed visual checks;
L = required real OpenAI Live journey, executed under HI-12 acceptance. Earlier
bounded provider checkpoints may run once their local prerequisites pass, with
the same predeclared rubric and recorded source/configuration.

| ID | Concrete deliverable | Depends on | Required evidence |
| --- | --- | --- | --- |
| HI-01 | Versioned contracts, bounds, compatibility and baseline fixtures | Current main | U, DB baseline; existing V baseline |
| HI-02 | Six named signs recognised and usable as Coordination triggers | HI-01 | U, DB, V; L-01 |
| HI-03 | Six named signs demonstrated through immediate and task output | HI-02 | U, DB, V; L-01 |
| HI-04 | Custom one/two-hand poses and three derived signs, input and output | HI-03 | U, DB, V; L-02 |
| HI-05 | Orientation/spread/proximity primitives and OK ring, input and output | HI-04 | U, DB, V; L-03 |
| HI-06 | Directional pointing triggers and immediate/task pointing output | HI-05 | U, DB, V; L-04 |
| HI-07 | Configured scene regions/targets and explicit calibration | HI-06 | U, DB, V; L-05 |
| HI-08 | Bounded dynamic person/item target resolution and output | HI-07 | U, DB, V; L-06 |
| HI-09 | Ordered input sequences and finger counting | HI-08 | U, DB, V; L-07 |
| HI-10 | Finite output sequences and robot finger counting | HI-09 | U, DB, V; L-08 |
| HI-11 | Integrated mode, lifecycle, UI and resource regression acceptance | HI-10 | U, DB, V; L-09 |
| HI-12 | Mode-appropriate acceptance, evidence and operator handoff | HI-01..11 | Revision-2 L-01..09; M-01/M-02; final affected U/DB/V |

Milestones distinguish SOFTWARE VERIFIED from PROVIDER VERIFIED. Passing mocked
tests does not mark the linked Live journey complete. Overall completion requires
the full mandatory provider matrix as well as deterministic and visual evidence.

## HI-01 — Freeze contracts and preserve the current baseline

**Outcome:** one documented, validated vocabulary and evidence model describes
every later capability without changing current agent behavior prematurely.

Work: freeze bounded DTO/schema examples for per-hand named detections, pose
conditions/outputs, pointing/targets and sequences; capability negotiation;
legacy RPS mapping; timing/units/frame conventions; event/command identities;
storage size and input limits. Specify meaningful rejection reasons and avoid
duplicate competing grammars. Add the new acceptance manifest/fixture structure
to the existing runner. Correct stale README claims about current Coordination
hand-pose support without advertising unfinished features.

**Tests:** U round-trip/bounds/unknown fields, old RPS fixtures, naming and frame
semantics; DB create/reload the existing scissors agreement and its event history
unchanged; reuse the current scissors-to-one-contribution browser baseline.
Record current event/request/query counts and Live instruction size. New runner
selection must fail closed rather than accidentally invoke a historical paid mode.

**Exit:** contracts and expected examples are reviewable; old agents still work;
each later milestone and Live journey maps to an explicit fixture/expected result.

## HI-02 — Six named signs as coordinated triggers

**Outcome:** users can prepare and agree a task triggered by fist, peace, open
palm, thumbs up/down or index up. Capability answers distinguish these shapes.

Work: expose available MediaPipe classes; retain per-hand evidence and side
selection instead of a global winning sign for new tasks. Keep the legacy RPS
selection path unchanged. Add bounded stability/admission, named-sign predicates,
profile opt-in and Coordination negotiation/preparation. Ambiguous or unsupported
hand selection must be visible, not guessed. Show local detection versus delivery
versus accepted task cue separately in the hand report.

**Tests:** U table covers every shape, side, two competing hands, low scores,
unknown/stale/malformed input and sign/pose deduplication; DB proposal -> wrong
demonstration -> correct preparation -> acceptance -> fresh cue -> one action,
with reload and a duplicate observation; V actual emitter with simulated landmark
results, detector/status display and the old RPS route. Begin the actual-detector
clip corpus defined below for these six signs. L-01 later verifies spoken negotiation
and the resulting action through the real provider.

**Exit:** a thumbs-up demonstration establishes readiness only; a later accepted
fresh cue triggers exactly one agreed response. Existing scissors tasks pass.

## HI-03 — Named signs as immediate and task output

**Outcome:** the robot visibly demonstrates each of the six signs on request,
and an accepted sensor task can produce the same canonical output.

Work: extend shared command interpretation/validation and motion capability
selection; implement named poses in the browser rig, including wrist orientation
for thumbs up/down and index up. Preserve silent commands, task conflicts,
duplicate fencing and truthful native capability guidance. Define hold/release
behavior and static history preview separately from fresh execution.

**Tests:** U all six signs/aliases, wrong capabilities, negation, quoted requests,
left/right/both effectors and unchanged task budget; DB one actionable event for
one admitted command across reload/retry, plus a distinct intentional repeat;
V reviewed front/side pose contact sheet, light/dark/narrow layouts, visible
thumb direction, replay without repeated action and supported/unsupported output.

**Exit:** in Text/Continuous, "show thumbs up silently" renders once without task creation; "when I
show thumbs up, show peace" executes only after acceptance and a fresh cue.

## HI-04 — Custom finger patterns and two-hand composition

**Outcome:** users can specify and demonstrate supported finger patterns, including
shaka, horns and middle finger, and request those poses directly from the robot.

Work: reuse HandPoseCondition for Coordination checks and reactions; extend it
explicitly for two distinct hands and partial input masks. Add structured
per-finger output and complete renderer targets. Derive the three named shapes
from shared geometry; document thumb constraints and do not relabel arbitrary
patterns as known signs. Make side/unknown readings inspectable during preparation.

**Tests:** U all three shapes, every finger, neutral/null, conflicting constraints,
numeric compatibility, split conditions across hands, swapped array order and
two-hand uniqueness; DB custom agreement/checks survive reload and unrelated
direct output preserves its terms; V left/right/two-hand pose inspection, including
unavailable readings and actual rendering of all ten independently set fingers.
Extend actual-detector clips to the three shapes and custom one/two-hand patterns.

**Exit:** a custom two-hand cue can trigger one response; the same explicit pose
can be requested as a standalone demonstration. Unknown fingers never satisfy it.

## HI-05 — Relational geometry and OK ring

**Outcome:** a supported combination of bend, spread, orientation and fingertip
proximity can be a cue or requested pose; OK ring works in both directions.

Work: add normalized geometry primitives with documented frames and validity;
calibrate threshold fixtures, hysteresis/stability and uncertainty. Share named
OK-ring semantics with custom conditions. Extend the avatar's thumb opposition,
finger positioning and wrist orientation as needed. Reject impossible or
unsupported output combinations rather than silently dropping a constraint.

**Tests:** U rotations/scales/mirroring, near-but-not-ring negatives, missing
landmarks, threshold edges and conflicting output geometry; DB persist/reload
relational conditions without changing legacy numeric terms; V close-up ring
rendering from multiple views plus near-miss recognition and ambiguity feedback.
Actual-detector clips must distinguish ring, thumbs up and the declared near misses.

**Exit:** thumbs up and OK ring are distinct supported requests/cues; an ambiguous
"OK" is clarified before committing the shape. Complete ten-sign catalog passes.

## HI-06 — Directional pointing in a declared frame

**Outcome:** broad directional pointing works as a task cue and robot command.

Work: estimate a stable projected direction from the pointing pose; carry source
hand, capture time, frame and validity. Implement left/right/up/down/diagonal
predicates and semantic output. Resolve user/robot/image-relative language using
the declared convention. Couple arm direction and finger extension in the avatar;
optional gaze can follow the same target. This milestone does not claim a person
or object has been selected or infer unsupported forward/back depth.

**Tests:** U frame transforms, mirrored preview, near-zero/foreshortened direction,
boundary jitter and attention-versus-pointing task interpretation; DB frame/version
survives agreement reload and a stale-frame cue is rejected; V mirrored and
unmirrored input with labelled axes and robot-left/right visual comparison, plus
actual-detector clips for the supported directions and unresolved orientations.

**Exit:** "when I point to my left, point to your right" has an unambiguous agreed
frame and fires once. A direct "point left" uses the disclosed robot frame.

## HI-07 — Configured regions and registered scene targets

**Outcome:** users can coordinate pointing toward a named region or registered
target, and command the robot to point toward that same grounded destination.

Work: add scoped setup for named camera regions and virtual targets, explicit
frame registration/calibration, candidate selection and target expiry. Provide a
browser scene preview with labelled targets so visual pointing can be verified.
Camera regions only become robot-space targets through a declared transform;
unregistered room locations remain unsupported. Bind agreements to stable target
IDs and frame versions, with explicit handling of target edits/deletion.

**Tests:** U unique/ambiguous/no candidates, overlapping regions, invalid transforms
and expired references; DB scoped target configuration and agreement reload,
cross-scope rejection and deleted/recalibrated target invalidation; V configure
a display/doorway region, inspect selection, and verify avatar ray/arm direction
against a registered virtual target instead of only checking a JSON field.

**Exit:** "when I point at the display, explain it" and "point at the registered
display" work with evidence of the configured mapping; ambiguity asks for repair.

## HI-08 — Dynamic people and item targets

**Outcome:** pointing can resolve one fresh tracked person or supported detected
item, and commands can refer to that target through the same scoped registry.

Work: extend the current detector pipeline to a bounded initial target set:
people, cups, bottles and books. Retain image positions and temporary track
identity without biometric identification. Associate source hand/person where
supported; otherwise keep the source unresolved. Combine pointing direction with
fresh target candidates and explicitly reject ambiguous projected intersections.
Use 2D visible-scene selection for camera input and registered virtual/adapter
positions for output; do not claim monocular evidence solves arbitrary room depth.
Allow an explicit selected-target reference and user-assigned labels.

**Tests:** U crossing tracks, source-person exclusion, ID reuse, two aligned
targets, moving/occluded targets, unsupported classes and frame mismatch; DB
target binding/reload cannot revive expired tracks or transfer labels to a reused
ID, and inference delayed past target expiry cannot dispatch pointing; V replay
controlled image/video fixtures through the actual detector plus deterministic
geometry fixtures, inspect overlays, clarify ambiguity and observe output toward
the selected virtual counterpart. Synthetic payload tests alone do not establish
real detector accuracy; record the fixture detection results separately.

**Exit:** pointing at one clearly separated cup selects it; an ambiguous cup/person
case requests clarification; a vanished target cannot receive stale output.

## HI-09 — Input sequences and finger counting

**Outcome:** ordered signs, poses or pointing cues can trigger one agreed action;
counting 0..10 is expressible with one or two hands.

Work: add a bounded typed step sequence to Coordination agreements and persisted
progress to ordinary Storage. Select either exact finger configurations or an
explicit extended-finger-count predicate; define the chosen counting convention
during negotiation. An unknown finger cannot be counted as folded to manufacture
a total. Use source-time dwell, release, gap and total-deadline rules above.
Emit stable changes promptly with bounded rate/backpressure; the existing
periodic pose interval must not silently drop intermediate counts. Surface gaps
and reset progress conservatively rather than inventing missed steps.

**Tests:** U fixed-clock 0..10/1->2->3, duplicate samples, out-of-order steps,
missing/unknown fingers, held poses, wrong-step reset, hand switches and deadlines;
DB partial progress reload, explicit restart, pause/Stop, revision replacement and
one contribution only after the last step; V visible progress, wrong-step recovery,
lost sensing, and emitter-level ordered delivery at the proposed bounded cadence.

**Exit:** only an accepted complete 1->2->3 sequence causes its response; holding
three, duplicate delivery, reload or a missing intermediate step cannot repeat it.

## HI-10 — Output sequences and robot counting

**Outcome:** a direct request or task contribution can execute one finite sequence
of signs, poses and pointing actions, including counting on the robot's fingers.

Work: extend direct commands with a validated finite sequence; preserve one-shot
semantics for a single pose. Use the existing application execution/lifecycle
boundaries and one modality-specific renderer executor, not a general scheduler.
Persist command/step identity and publish canonical plans with bounded timing.
Add scoped execution feedback if needed, distinguish start/complete/interrupted
from unknown, and cancel on Stop/switch/reset/supersession. Subsequent task progress
cannot use an unconfirmed final sequence step as completion. Recheck target
freshness before each pointing step; disappearance aborts with an explicit result.

**Tests:** U step order/duration/limits, malformed mixed steps, silent sequence,
one command versus multiple task contributions and cancellation races; DB reload
mid-sequence preserves uncertainty without replay, late acknowledgements cannot
advance another command, and scope/owner checks hold; V virtual-clock assertions
plus sampled frames for every counting step, cancellation between steps, replay
as static history, and no hidden-tab catch-up burst of obsolete motions.

**Exit:** in Text/Continuous, "count one to five silently" executes exactly five ordered poses;
Stop after two prevents steps three through five. A task-triggered sequence uses
the same output contract and its single agreed contribution budget.

## HI-11 — Integrated parity, UI and resource acceptance

**Outcome:** all layers work together through the actual scoped application,
without regressions in conversation, saved tasks, RPS, sensing or output lifecycle.

Work: finish capability/status UI, accessible explanations, hand/target/sequence
inspection and saved-instance adoption guidance. Keep model prompts compact;
measure the worst supported agreement against existing request/instruction bounds.
Maintain bounded observation/event volume and content-free diagnostics. Compare
fixed-fixture event, database-query and ordinary-turn latency baselines; stable
geometry must not produce inference on every frame or uncontrolled database writes.
Do not treat this work as resolving the separately deferred production capacity
gate. Establish and record explicit per-scenario local resource ceilings in HI-01.

**Tests:** focused full affected U suites and harness preflight tests; DB Text,
Continuous and native Live journeys with actual scoped ingress/SSE/reload, legacy
instances, reset, owner loss and simultaneous sensor event types; V light/dark,
narrow layouts, keyboard use, camera denied/model unavailable, WebGL fallback,
reduced motion, disconnect/reconnect and visible stale/unsupported states.
Review successful screenshots/contact sheets, not only failure screenshots.

**Exit:** all ten signs, explicit poses, directional/target pointing and both
sequence directions have local acceptance evidence. Ordinary Live audio remains
independent of backend work and no duplicate Speech route is introduced.

## HI-12 — Mode-appropriate acceptance and final handoff

**Outcome:** real spoken interaction succeeds through the actual application,
local persistence and browser rendering for every new capability family, preserving
the existing speech-ownership architecture. Strict silent output is verified in
Text and Continuous; GPT-Live supports the same hand actions without a silence guarantee.

### Accepted mode boundary (revision 2, 2026-11-24)

- Text/Continuous: the backend owns verbal and nonverbal behavior. Explicit silent
  commands/tasks must omit speech and produce no synthesis or audible output.
- GPT-Live: Live owns verbal responses and their immediate streaming; the backend
  owns task authority and all other behavior modalities. Ordinary native audio
  must continue while backend interpretation is blocked.
- Strict "silently do X" is unsupported in GPT-Live for this roadmap, including
  silent sensor-triggered actions. Users needing strict silence choose Text or
  Continuous. No playback gate, buffering pending classification, sentence approval,
  task-window muting, or replacement TTS is introduced to satisfy acceptance.
- Live journeys request the hand behavior without promising silence. Optional
  truthful acknowledgements/follow-up speech are allowed. False cancellation,
  denial or completion claims, changed terms and unauthorized task progress still
  fail. Capture and review complete native audio; do not hide inconvenient output.
- This is a prospective owner-approved scope revision. Preserve the nine original
  attempts and their verdicts under revision 1; none becomes a pass by relabeling.

Reuse the existing native Live runner, real scoped cockpit, disposable local MySQL,
SSE, capture/playback evidence and commanding preflight patterns. Add a distinct
hand-interaction run manifest, scenario selection and ledger; do not reuse or reset
the exhausted GLF/TWC authorizations. Existing command-line examples do not select
these new journeys until the runner is extended and its selection is tested.

The execution plan must specify a current authorized money cap, maximum attempts,
per-attempt reservation and conservative costs for all billable routes before
provider access. The subsequent owner instruction authorizes at most $50 total;
HI-01 originally allocated twelve $4 reservations and a separate persistent ledger.
HI-12 allocation revision 2 retains the first three $4 reservations unchanged and
uses at most eighteen further $2 reservations: 21 attempts and $48 reserved total.
Official verified prices and unchanged per-attempt bounds yield a conservative
$1.9153 ceiling. This changes only future allocations; no failed reservation is
refunded, reset or reused, and no per-attempt provider limit is raised.
Allocation revision 3 (2026-11-24) uses the remaining $2 buffer for one further
attempt: at most 22 attempts/$50 reserved, preserving every prior entry. Current
official prices were rechecked unchanged; the $1.9153 per-attempt ceiling and all
technical limits remain unchanged. This stays within the owner's original cap.
Plan one initial attempt per journey; L-09 needs separate Coordination
and comparison-agent sessions. Diagnostic repeats count as attempts. Preserve all
failed reservations and artifacts, including on crash. Current `paid_budget.py`
also has a repair-specific hard ceiling; do not silently raise it to fit this plan.
Isolate/generalize budgeting with regression protection if a new envelope needs it.

Initial technical bounds per attempt: one Live session, at most six minutes,
twelve user turns, 36 backend model requests, 80,000 request bytes and 2,048 output
tokens per backend request; 390-second overall watchdog. Define and enforce
native audio/token bounds in the new preflight as well. Verify official current
prices and configured models before costing; split an oversized journey instead
of silently raising its bounds. Reuse cached/local visitor audio where possible.
Do not call provider-based grading or audio preparation outside the same budget.

### Required happy-path matrix

Use real OpenAI Live audio/transcription and the configured real backend inference
routes. Sensor/scene inputs may be controlled fixtures, labelled as such. Do not
inject assistant transcripts, agreement interpretations or verdicts and then call
the run real-provider acceptance. Every journey has a nonmatching/ambiguity or
duplicate check as well as a success path; these are not free automatic retries.

| ID | Real spoken journey | Required evidence |
| --- | --- | --- |
| L-01 Named signs | Ask capabilities; request thumbs up; negotiate "on thumbs up, show peace once"; prepare/accept; send wrong then fresh correct sign; deliberately repeat a direct command. | Correct spoken scope; one action per authorized turn/cue; no premature task start, duplicate contribution or competing speech; visible correct poses. |
| L-02 Custom poses | Specify a custom two-hand pattern; demonstrate it during preparation; accept one response on its next occurrence; ask robot to reproduce it. | All finger/side constraints preserved; split/missing readings do not match; readiness spends no action; one response and correct ten-finger output; any native speech remains consistent with confirmed facts. |
| L-03 Ring and geometry | Ask for "OK", resolve shape if ambiguous; demonstrate an OK ring and a near miss; agree one ring-triggered response; request the ring from the robot. | Thumb-up/ring distinction, all geometric terms retained, near miss rejected, one accepted cue, rendered ring and consistent speech. |
| L-04 Direction | Negotiate "when I point to my left, point to your right"; accept and exercise wrong/correct directions; request an immediate left-pointing demonstration. | Correct disclosed frames, no mirroring reversal, one action, correct visible arm/finger direction and no task mutation by the unrelated direct request. |
| L-05 Registered target | Identify the configured display; agree a response to pointing there; accept and trigger it; request pointing at that display. | Same scoped target/frame across agreement and output, correct selected region, supported virtual mapping and visible target-directed output. |
| L-06 Person/item | Select one of two ambiguous cups, negotiate a pointing-triggered response and trigger it; directly request pointing at a clearly selected person; remove that target before repeating. | Clarification precedes action, stable target binding, fresh cup/person resolution, no invented personal identity, and no action at a stale target. |
| L-07 Input sequence | Agree "one, then two, then three -> one acknowledgement"; accept; show an incomplete/wrong sequence then a valid one; hold the last pose. | Spoken terms retain order/limits; inspectable progress; only full sequence spends one action; holding the final pose does not repeat it. |
| L-08 Output sequence | Request counting one to five; request a second bounded sequence and Stop after step two; agree one pose-triggered output sequence and execute it. | Correct ordered visual steps, timely cancellation, no late/reconnected replay, one task contribution with scoped completion evidence; any native speech remains consistent with confirmed facts. |
| L-09 Mode/conversation regression | Three matched ordinary turns on Coordination and the existing Live comparison agent; one supported new direct request; inspect Text/Continuous parity through the local real-app suite. | Native speech remains independent, correct capabilities, no extra TTS, no false completion claim; compare browser audio onset with the declared baseline and retain evidence. |

Also require two deterministic real-application checks with disposable local MySQL:

| ID | Mode check | Required evidence |
| --- | --- | --- |
| M-01 Strict silence | In both Text and Continuous, request a silent custom pose and finite count sequence; prepare/accept and trigger one silent hand task. | Correct visible output and one action per request/cue; canonical plans omit speech; no synthesis request or audio playback for the action, including completion. A spoken control response verifies the Continuous speech route is functioning. |
| M-02 Ownership | Run the existing native-audio/blocked-backend, Stop and mode-switch regression. | Native audio plays while backend inference is held; no approval/replacement-TTS request; Stop immediately releases audio; Text/Continuous regain canonical backend speech after switching. |

M-01 uses real ingress, persistence, SSE and rendering with labelled provider/media
fixtures; it is not real-provider speech recognition or physical audibility evidence.
Extend neighboring commanding/hand/sequence browser tests only where coverage is
missing. M-02 is anchored in `valerian-gptlive-native.spec.mjs`; retain its blocked
backend assertion. These checks add no paid-provider attempts.

For each journey predeclare the semantic rubric before running: wrong sign, hand,
direction, target, step order, task terms or action count fails; repeated consent
demands, premature execution, unsupported completion claims and false capability
denials fail. Ordinary paraphrases are allowed. Review the complete relevant
captured speech/audio and visible output, not a convenient substring or a regex
alone. Missing evidence is INCONCLUSIVE, not PASS. A failed journey remains in the
record after a corrected retest.

Strict silence in Text/Continuous needs both canonical output and browser
synthesis/playback evidence, not only an empty speech field. Native Live audio is
retained for semantic and latency review, with no zero-audio requirement.
Spatial/pose correctness needs inspected rendered output, not only
canonical JSON. Controlled sensor injection proves interpretation/control, not
camera accuracy; include the HI-08 actual-detector fixture results and a separate
operator checklist for real-room sensing and physical audio/actuation.

### Final exit and evidence

- All revision-2 L-01..09 and M-01/M-02 pass with recorded source revision, model/configuration, stimuli,
  actual/reserved usage, timing, admitted turn/event/action IDs and reviewed visual
  and speech evidence. Run the mandatory matrix against the final integrated
  behavior; after a relevant fix, repeat every affected journey, preserving failures.
  L-09 uses a separate comparison recorded under the same acceptance revision.
- The smallest complete affected unit/contract, isolated DB and Playwright suites
  pass after the final changes. Include packaging and whitespace checks. Do not
  rerun unrelated suites repeatedly without a change or unresolved concern.
- Create `.agents/HAND_INTERACTION_RESULTS.md` during implementation with a row
  for every milestone/journey: NOT RUN, PASS, FAIL or INCONCLUSIVE, exact commands,
  environment/source, artifact locations and limits. Keep generated/private
  captures under ignored output, not committed fixtures or result prose.
- Update README contracts/setup/catalog, `.agents/CONTEXT.MD`, PROJECT current
  status and its focused historical audit. Document new/saved instance behavior,
  additive schema requirements, detector/model dependencies, frames, output
  capabilities and unsupported hardware. Fix superseded hand-pose statements.
- Provide a concise operator checklist: ten signs; each hand/two hands; occlusion;
  mirrored view; pointing directions; configured/dynamic targets; ambiguity and
  disappearance; input/output counting; Stop; real microphone/speaker and chosen
  physical adapter if applicable. Keep unperformed trials explicitly NOT RUN.
- No production database mutation, branch integration or deployment is part of
  local acceptance. Stop for review with software/provider/physical status stated
  separately. All required provider tests pending means roadmap incomplete.

## Verification rules applying to every milestone

Use the lowest meaningful level first: pure geometry/rules in JS/Java units,
persisted contracts and scope at real local MySQL/HTTP boundaries, and actual
browser ingress/rendering in Playwright. Fixed clocks, synthetic timestamps,
controlled frames and fake provider responses make ordinary regression tests
deterministic. Test the primary success, representative rejection and every
nontrivial new branch; avoid redundant tests that merely repeat implementation.

Use `tests/gptlive/run_acceptance.py` to provision a random loopback schema and
restricted account from `application-test.properties`; never run deletion fixtures
against the developer or production schema. Disable scheduled ticks unless the
case tests them. Use isolated build/artifact directories, JDK 21, owned-process
cleanup and deterministic provider fixtures for DB/browser regression. Extend
the runner's explicit browser spec selection so new specs actually execute.

Browser tests must use the real hand emitter and canonical SSE/renderer wherever
those boundaries are under test. Landmark/provider fakes isolate dependencies;
directly calling a rendering helper is not an end-to-end assertion. Keep synthetic
image/landmark checks, actual model inference, real-provider semantic acceptance
and physical operator evidence labelled separately. Maintain performance bounds
without lowering sensing correctness or allowing stale target/sequence evidence.

For recognition acceptance, maintain a small labelled image/video corpus using
consented or appropriately licensed material, separate from private user captures.
Exercise the pinned real detector, not just supplied landmarks. Cover each new
named shape, representative custom/two-hand patterns, pointing directions and
supported target classes; include near misses, partial/off-frame hands, mirroring,
competing hands/targets and ordered counting clips. Declare supported views,
expected match/no-match windows and stable-emission deadlines before running.
Each positive must produce the expected stable result in its window; negative or
unresolved windows must produce no actionable match. Record misses, false matches
and timing explicitly. A small passing corpus establishes that corpus only,
not general real-room accuracy; unexpected failures cannot be hidden by relabelling
the examples after a run. UI tests may replay the same clips deterministically.


## HI-12 acceptance decision history

On 2026-11-23, nine attempts reserved $24 and complete audio review failed the
original native-silence requirement. Work paused for an ownership decision after
independent capture-freshness repairs were verified and pushed in `ae493087`.
On 2026-11-24, the owner explicitly chose immediate independent Live speech and
accepted strict silence as unsupported in that mode. Revision 2 above replaces
the zero-native-audio gate without changing task authority, speech ownership or
the $50 authorization. Refresh local visitor clips and run preflight before
resuming explicit paid selections. HI-12 remains incomplete until the revised
matrix passes; all previous failures/reservations remain in the results.


On 2026-11-24, revision-2 execution verified L-01, L-02 and the paired L-09, plus
M-01/M-02. L-03..08 remain failed or inconclusive; the exact causes, local fixes and
required retests are in `HAND_INTERACTION_RESULTS.md`. All 22 reservations/$50 of
allocation 3 are used (not a measured invoice total). The runner refuses further
paid selections. HI-12 is not complete, and this checkpoint does not change its
exit criteria or authorize another allocation. Preserve the ledger and evidence.
