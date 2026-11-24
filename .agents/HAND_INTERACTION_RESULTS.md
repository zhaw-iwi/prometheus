# Hand interaction implementation evidence

Started 2026-11-23 from main `025697df`. The owner authorized sequential
implementation, commit/push after each verified milestone, then continuation.
Provider allowance: original $50 plus owner-approved $20, $30 and $100 extensions on
2026-11-24, including successful/failed requests and all modalities.
Actual provider billing: **invoice total not available**.
Cumulative reservations entering the third extension: **$100 / 66 attempts**.
The cumulative ceiling is **$200**. The checkpoint ends at **88 attempts/$186**,
including **$86 reserved in this extension**. **$14 remains unreserved**; allocation
9 permits two further $5 selections (90 total/$196 reserved). No requests are
queued. HI-12 remains **INCOMPLETE** after the final sweep below. No deployment.
Historical GLF/TWC ledgers remain unchanged.

Portable continuation: [handoff and setup](HANDOFF_HAND_INTERACTION_2026-11-24.md),
[exact budget checkpoint](checkpoints/hand-interaction-2026-11-24/ledger.json), and
[curated evidence](checkpoints/hand-interaction-2026-11-24/evidence.json).
Raw recordings/logs remain local; these files preserve continuation state without
committing credentials or treating earlier results as newly rerun acceptance.

## HI-01 — Contracts and compatibility baseline

Status: **COMPLETE** (2026-11-23), committed and pushed to main as `111ed6f3`.

Added a strict JSON Schema bundle and nine positive examples for per-hand signs,
poses, conditions, sequences, scene targets, pointing, capabilities and scoped
execution evidence. The contract guide fixes legacy RPS compatibility, coordinate
frames, unknown values, bounds, and the distinction between structural and runtime
semantic validation. New capability support remains disabled until implemented.

The existing acceptance runner now has an explicit contracts/baseline selector.
All nine future Live selectors reject before database or provider access; their
manifest maps fixtures to milestones and expected outcomes. The new budget is
separate, initially twelve $4 reservations within the $50 cap; price verification
and enforced provider limits remain prerequisites to enabling paid journeys.
Corrected README's stale claim that Coordination already accepts numeric poses.

Verification actually run:

- Seven Python contract/selection tests; nine positive schemas round-trip,
  malformed/unknown fields and boundaries reject, and all nine unimplemented
  paid selectors fail closed. Fourteen existing acceptance-runner tests pass.
- Thirty-six existing JavaScript finger/sensor tests pass, including preserved
  RPS capture, unknown fingers, same-frame timestamping and delayed-ack fencing.
- Sixty-six Java tests pass through the disposable loopback MySQL runner:
  CoordinationAgent 16, CoordinationHandPose 4, CoordinationObservationTiming 2,
  CoordinationAgentIntegration 18, LiveQueryBudgetIntegration 4, HandPoseTask 11,
  TaskHandConfidence 11. New saved-scissors fixture preserves agreement, raw
  `scissor` value, event IDs/positions/payloads and zero actions after acceptance.
- A focused rerun of the updated saved-scissors test passes and records 15,921
  Live instruction characters against the existing 16,000-character ceiling.
- Two real-app Playwright tests pass: Text/Continuous agreement/preparation/fresh
  scissors cue, and native Live completion only after observed playback. The
  three captured interaction panels were visually reviewed: readable controls,
  one contribution, correct mode and native reflection status. Providers and
  hardware are synthetic in these tests; this is not real OpenAI acceptance.
- `git diff --check` passes.

Measured baseline: five persisted events for proposal/acceptance, zero task actions
until a fresh cue; context queries 8 for both 5 and 205 history events (ceiling 25);
receipt is one autocommitted SQL statement with zero entity loads and zero ORM
prepared statements (scoped ORM ceiling 4). Text and Continuous each persist 13 events from three user turns and four hand
submissions, spending one action; synthesis requests are 0 and 6 respectively.
Native Live persists four events, one action, one playback request and zero
synthesis requests. Counts are retained in the final browser artifacts below. New hand envelope/state/rate
ceilings are specified in the manifest; enforcing those belongs to the milestones
that introduce the corresponding runtime boundaries.

Local artifacts (ignored): `target/gptlive-acceptance-cc8a765ca1` (66 Java tests,
initial browser run/screenshots); `target/gptlive-acceptance-4d2ef69b77` (focused
instruction/persistence measurement); `target/gptlive-acceptance-fd77e1af2e`
(final browser rerun with persisted counts and screenshots, both passed). Every run
used a new schema/user and removed both on exit. The owned loopback MySQL process
uses `target/hand-interaction-mysql`; the configured remote application database
and pre-existing local MySQL service were not used.

Remaining limits: no new runtime hand capabilities, real-detector corpus,
physical robot/microphone checks or paid provider journey has been accepted.
Live instruction headroom is only 79 characters in this measured baseline;
future capability guidance must be compacted rather than silently truncated.

## HI-02 — Six named signs as coordinated triggers

Status: **COMPLETE** (2026-11-23), committed and pushed to main as `31b053ff`.

New Coordination instances declare strict versioned `obs.hand.signs` input for
fist, peace, open palm, thumbs up/down and index up. Shared typed conditions select
any/left/right hand, preserve confidence and reject ambiguous sided evidence.
Preparation and execution share persisted consumed-frame identities: readiness
never spends an action; acceptance needs a fresh cue. Saved profiles retain their
old declarations. Legacy RPS and numeric-pose consumers remain compatible.

The camera retains per-hand classes, waits for three ordered frames spanning
300 ms, and distinguishes local recognition, delivery and task acceptance. Input
is bounded to two hands, 16 KiB and five new frames per second by server receipt
time. Invalid or undeclared payloads cannot enter history. Named output belongs
to HI-03 and is not advertised yet.

Verification actually run:

- Focused Java suites passed after fixing a nullable-field assumption in shared
  social-contract validation. Final named-sign suite: 7 tests, covering all six
  shapes, sides/ambiguity, confidence, stale/malformed evidence, preparation,
  deduplication, admission rate and undeclared Generic input.
- Disposable local-MySQL runs: 23 integration/query-budget cases in
  `target/gptlive-acceptance-b98df7a660`; 25 named/integration cases in
  `target/gptlive-acceptance-f6181ad6f9`; 16 boundary/capability cases in
  `target/gptlive-acceptance-a73ee3c3d2`; 42 final compatibility cases in
  `target/gptlive-acceptance-36585b01f3`. These overlap and are not a unique total.
  Reload preserves consumed observations; malformed/undeclared ingress leaves
  history unchanged. Saved-scissors Live instructions measure 15,832 UTF-8 bytes
  against 16,000. Context query count remains 8.
- Forty JavaScript hand/sensor tests and seven Python contract tests passed.
- Four real-app Playwright cases passed in `f6181ad6f9`: named input through
  agreement/preparation/acceptance/fresh cue, legacy finger/RPS, Text/Continuous,
  and native Live playback. Named-input light/dark 1440 px and light 390 px
  screenshots were visually reviewed. Providers and camera SDK results in these
  browser journeys are synthetic; database, emitter, HTTP, SSE and task logic
  are real. One fresh cue spends one action; retries spend none.
- Real MediaPipe 0.10.35 CPU inference on hash-pinned public upstream fixtures:
  nine cases / 63 frames pass at the explicitly configured **0.60** threshold,
  with stable decisions at 300 ms. Cases include all six shapes, a mirrored
  thumb, an off-frame crop and an empty scene. Script and provenance are in
  `tests/hand-interaction`; results/images are under
  `target/hand-interaction-detector/run-1795448708530`.
- Maven packaging and `git diff --check` passed.

Detector limits: the unchanged product default is **0.65**. At that threshold
the initial corpus passed 8/9: its back-of-hand palm scored about 0.61 and was
correctly withheld. Three exploratory alternative palm views also missed their
positive expectations; these failures remain in ignored detector artifacts.
The calibrated nine-case run is a reproducible smoke test, not general camera
accuracy, demographic coverage or physical-robot acceptance. Recorded moving
hands, broader lighting/occlusion and paid L-01 remain unverified. No provider
calls or reservations were made. All disposable database users/schemas were
removed; no remote application database was used.

## HI-03 - Six named signs as immediate and task output

Status: **COMPLETE** (2026-11-23). Commit `bd678342` pushed to main.

New Coordination profiles declare `motion.handAction`. The shared command handler
and task validator accept the six implemented named signs with left/right/both
hands. Existing RPS commands keep their old payload. Named aliases include halt
and attention; ambiguous OK is clarified by interpretation guidance. Unsupported
profiles and malformed/future actions cannot emit named output. Silent commands
create no task and do not spend its budget; pending task output blocks a separate
command. Persisted turn identity fences retries while a new repeat remains valid.

The production browser rig articulates the correct fingers on either hand and
uses explicit wrist orientation for thumbs up/down and index up. Fresh actions
enter for 350 ms, hold 500 ms and release for 350 ms; redraws stop after release.
History/hidden/reduced-motion views are static previews. Details and Robot views
show the named pose; rendering is never presented as physical execution evidence.
An accepted fresh thumbs-up cue can produce one silent two-hand peace action.

Verification actually run:

- **153 Java unit cases** pass in `target/hand-interaction-mysql/hi03-unit-final.log`:
  shared direct commands and all four agent variants, six signs/three effectors,
  aliases, malformed/future actions, capability and pending-task rejection,
  silence/budget preservation, interpreted negations/quoted requests, fresh
  preparation/cue behavior, compact plans and legacy numeric-pose/task behavior.
  Interpretation tests inject provider decisions; they do not prove natural
  language accuracy with a real model.
- **29 unique integration cases** pass across the isolated MySQL runs:
  CoordinationAgentIntegration 20 and LiveQueryBudgetIntegration 4 in
  `target/gptlive-acceptance-aa72732df1`; DirectCommandIntegration 5 in
  `target/gptlive-acceptance-a381f5b463`. The first run's extra retry assertion
  incorrectly targeted a Live Multimodal generate call with no current user turn;
  its correct rejection was retained, and the assertion was scoped to the new
  Coordination path. All five command cases then pass. Named commands survive
  reload, re-generation does not republish, and a distinct repeated user turn
  publishes once more. The saved-scissors recheck also passes. Live instructions
  measure **15,955 UTF-8 bytes** against 16,000; context queries remain 8.
- **63 JavaScript tests** pass (embodiment, named/legacy hands and camera sensors).
  Geometric checks verify finger articulation, world-space thumb/index direction,
  capability rejection, 500 ms hold, release and non-executing history previews.
  Seven Python contract tests pass.
- **Four final Playwright cases** pass in
  `target/gptlive-acceptance-536ae05cd2`: existing Coordination/Live Multimodal
  native mode parity; six actual command/UI/DB outputs, history deduplication,
  one accepted cue output and unsupported Live-profile rejection; production rig
  contact sheet. Twelve front/side views and light/dark/narrow layouts were
  reviewed. The corrected contact-sheet labels and final light view were checked
  again. Initial passing browser evidence is in `a381f5b463`.
  The named journey records six direct commands, seven total named plans (one
  task output), eight user turns, three hand observations and one task action.
- Maven packaging and `git diff --check` pass. All source files touched are valid
  UTF-8; a test-only contact-sheet encoding error was corrected and re-rendered.

Remaining limits: provider interpretation is synthetic in these tests. Real L-01
is deferred to HI-12, and no physical actuation or execution receipt is claimed.
Custom poses, extra three derived signs, ring and pointing remain unavailable.
Instruction headroom is 45 bytes in the measured case; later capability additions
must compact guidance, not truncate it. HI-02 detector limitations still apply.
All disposable schemas/accounts were removed. Paid spend/reservations: **$0/$0**.

## HI-04 - Composed finger poses and three derived signs

Status: **COMPLETE** (2026-11-23). Commit `0cd01f24` pushed to main.

New Coordination instances explicitly opt in with `hand.poses.v2`; saved named
profiles keep six shapes and Generic agents keep v1 numeric poses. Strict v2
pose ingress shares frame identity with named input, validates ten independent
readings, and counts companion representations once against rate/action limits.
Preparation and task rules use bounded partial masks on one or two distinct
hands from the same capture. Direct `HAND_POSE` and canonical task output carry
complete left/right finger targets, with neutral defaults for unspecified
requests. The rig independently articulates all ten fingers and preserves the
existing bounded hold/release and history-preview lifecycle.

Shaka, horns and middle finger work as named triggers and output. Real detector
failures exposed why averaging every joint cannot describe every named shape:
a thumb may be straight at its joints but opposed across the palm; one folded
finger joint can be averaged away. Named extension uses proximal/distal angles
and palm-normalized thumb opposition, requiring known landmarks and timed
stability. This does not change the low-level curvature values, thresholds or
saved numeric predicates. Derived confidence is null, labelled geometric, with
no fabricated classifier percentage. Named requests use named predicates rather
than silently replacing them with numeric masks.

Verification actually run:

- **167 Java unit cases** pass in `target/hand-interaction-mysql/hi04-unit-confirmed.log`:
  composed/legacy poses, named signals, all four command agents, strict output,
  capability gates, preparation, unknown readings, distinct assignment, shared
  evidence and perception. A focused 16-case perception recheck also passes after
  excluding unscored basic classifier signs from factual answers. An earlier
  PowerShell redirection reported exit 1 for Mockito's stderr warning although
  all selected JUnit reports passed; the subprocess rerun reports Maven exit 0.
- **26 local MySQL integration cases** pass in `target/gptlive-acceptance-b8b96c4c65`
  (21 Coordination, five direct commands). Custom draft/checks and accepted terms
  survive reload; a separate direct custom output leaves the action count/terms
  intact. Missing-hand and reused preparation evidence do not act; a fresh
  two-hand cue publishes one silent pose; its retry cannot repeat it.
  The later `17a4bb59ba` run also passes 23 selected unit/integration checks.
  The final `cfbd82df84` run passes four additional query/receipt cases plus
  the saved-scissors check: **30 unique integration cases** across the runs. Live
  instructions measure **15,993/16,000 UTF-8 bytes**, retaining explicit native
  silence and automatic-remark authority. Context queries stay at 8 for 5/205
  events; receipt entity loads stay zero. Later additions must compact guidance.
- **70 JavaScript cases** pass in `hi04-js-final.log`, including all ten independent
  joints, neutral/unknown readings, side/capability gates, shared emitter capture
  identity, timed stability and rotation/scale/reflection invariance of derived
  signs. **Seven Python contract tests** pass. Packaging and whitespace checks pass.
- **18 actual-detector cases / 126 frames** pass in
  `target/hand-interaction-detector/run-1795451252597`: the previous nine cases,
  three new shapes and their mirrors, I-love-you versus horns, a partial one-hand
  mask and a two-hand mask. All expected stable results arrive within 450 ms.
  `composed-corpus.json` records hash-pinned public HaGRID overview provenance,
  crops/transforms, the 0.60 operator setting and 1000 ms admission deadline.
  Product default remains 0.65. The earlier three-shape failures are preserved in
  `run-1795450811113` and diagnostics `run-1795451005539`; an intermediate geometric
  attempt in `run-1795451054820` missed shaka/middle finger. The final criterion
  evaluates emitted stable classifications, allowing unstable model warm-up but
  forbidding any stable wrong result. This criterion change is explicit; prior
  every-raw-frame failures were not relabelled. These are static-image fixtures,
  not recorded motion or broad demographic/real-room accuracy.
- Browser evidence is retained in `17a4bb59ba`, `3c13e0eb17`, `62c4e41aa9`
  and the final narrow-layout/direct-pose run `3b5feb0ea3`. The complete two-hand cue journey and both
  contact sheets pass in `62c4e41aa9`. Eighteen named and six custom front/side
  views, the unavailable reading, and light/dark/narrow input panels were reviewed.
  Nine direct named outputs, silence, task preservation, history deduplication,
  unsupported profiles and the custom rig pass. Initial custom journey failures
  were a case-sensitive test label and a fixture-only missing test class at
  runtime; the provider fixture is now self-contained. Visual review caught
  Chromium's WebGL context ceiling in an 18-cell contact sheet: two blank cells
  escaped the old canvas-count assertion. The revised harness snapshots/disposes
  each rendering and explicitly rejects empty pixels. Initial artifacts remain.
  Four unique browser cases now pass. The final composed journey additionally
  requests a silent standalone two-hand pose, verifies the exact ten values,
  preserves the accepted agreement/action count, and checks its history preview.
  The 390 px output and corrected non-wrapping Unavailable label were reviewed.
  Camera/provider outputs in browser tests are fixtures; HTTP, MySQL and the
  application pipeline are real. Both task and direct custom plans are silent.

Commands: the local runner used `--java-tests` for the listed classes and
`--browser --browser-mode enabled --browser-grep` for composed/named output
journeys; exact invocations/artifacts are retained in the named logs. Units ran
via Maven `-Dtest=... test`, Node `--test tests/js/hand/*.test.mjs
tests/js/embodiment/*.test.mjs tests/js/live/hand-sensors.test.mjs`, and Python
`-B -m unittest discover -s tests/gptlive -p test_hand_interaction.py`. The detector
command is `node tests/hand-interaction/detector-smoke.mjs
--manifest=tests/hand-interaction/composed-corpus.json`. Final Maven package and
`git diff --check` pass.

Remaining limits: small calibrated detector corpus only; inferred landmarks do
not prove visibility or contact. Relational poses/ring/pointing/sequences and
real-provider L-02 remain pending. No physical actuation or receipt is claimed.
Paid OpenAI spend/reservations: **$0/$0**. All disposable accounts/schemas are
cleaned by the runner; the configured remote application database was not used.

## Subsequent milestones

See the per-milestone sections below for current status. Follow `PLAN_HAND_INTERACTION.md`; each completion
must record actual checks, remaining limits and source revision before committing
and pushing.

## HI-05 - Relational geometry and OK ring

Status: **COMPLETE** (2026-11-23), committed and pushed as `d03af037`.

New Coordination profiles opt in to `hand.geometry.v1` independently of saved
instances. Pose v2 optionally carries strict normalized geometry; unknown fingers
cannot manufacture measured pairs/axes. Versioned conditions combine same-hand
bend, up to six relations, and distinct hands, or explicitly use a relation-only
mask. Legacy numeric curvature and Generic v1 remain unchanged. Input/output
capabilities remain separate. Overall existing task-size limits still apply.

Image-plane gaps use aspect-corrected separation divided by max projected palm
width/length. Spread is a proximal angle divided by pi; axes are projected
camera directions. Null axes/pairs do not match. The ring uses thumb/index gap
enter<.18 and hold<.22, index curvature>0.25, three extended fingers and three
frames/300 ms. Its confidence is null. It is distinct from thumbs up; ambiguous
OK is clarified through the existing command path before a shape is committed.

The digital subset supports thumb/index ring proximity with explicit .7/.735
bends, straight index/middle spread below 54 degrees, and straight index-axis
orientation in eight view directions. Contradictory/unsupported terms reject.
The rig's thumb opposition closes the ring, a wrist presentation reveals its
opening, and mirrored finger lengths now agree. The sensing disclosure persists
through camera/history refresh. These are estimates and digital rendering,
not physical contact, calibrated room geometry or execution receipts.

Actual verification:

- **171 Java unit cases**, all passing, in
  `target/gptlive-acceptance-81ae849245/build/surefire-reports`: RelationalHand,
  ComposedHandPose, CoordinationAgent/NamedSign/HandPose/Perception, NamedHandOutput,
  DirectCommands/DirectCommandAgents, CompactBehaviourPlan, HandPoseTask and
  GenericMultimodalTask. The initial focused run caught the obsolete ring-reject
  assertion and a 16,038-byte voice prompt; the next measured 16,001. Compacting
  duplicated policy wording retained all authority/silence guidance. The final
  focused creation test in `hi05-prompt-size.log` measures ten reachable states,
  maximum **15,967/16,000 UTF-8 bytes**. No limit was raised or persona truncated.
- **Six local MySQL checks** pass in `target/gptlive-acceptance-d3d142108d`:
  composed and relational preparation/reload/direct-output journeys plus four
  existing query/receipt budgets. Accepted terms survive; unrelated direct output
  spends no task action; reused preparation evidence cannot act, a fresh cue
  acts once. Both isolated runs removed their owned schema/account and app.
- **76 JavaScript cases** pass (`hi05-js-final.log`), including normalized gap,
  mirror/scale/aspect invariance, missing landmarks, ring near misses/hysteresis,
  both rendered tip distances, both hands' eight orientations, composed spread,
  and unsupported conflicts. **Eight Python schema/runner tests** pass. The
  strict schema permits relation-only masks but rejects empty/unknown contracts.
  `mvnw.cmd -q -DskipTests package` and `git diff --check` pass.
- **Four unique browser cases** pass across `d3d142108d` and final `81ae849245`:
  production emitter -> persisted relational preparation -> explicit acceptance
  -> nonmatching/matching cue -> silent ring; ambiguous direct OK produces no
  action; unrelated silent relational pose preserves terms; ten direct signs;
  twenty named front/side views and four ring closeups. Real HTTP/MySQL/SSE are
  exercised; SDK outputs and inference interpretations are controlled fixtures.
  Initial screenshots exposed a numerically closed but visually hidden ring and
  a disclosure that collapsed during refresh. Both were fixed and the successful
  ring/contact sheets, light/dark/390px details and 390px output were reviewed.
  Earlier visual evidence remains under `d3d142108d`; exploratory yaw views are
  retained in ignored `target/hand-interaction-detector/ring-probe.png`.
- **20 actual model cases / 140 frames** pass in
  `target/hand-interaction-detector/run-1795452739676`, using
  `geometry-corpus.json`: all ten signs, reflected views, partial/two-hand masks,
  thumbs-up/open-palm/index-up/ILY near misses. Ring stabilizes at 300 ms; all
  cases meet the declared 1000 ms deadline. Explicit threshold remains .60;
  product default stays .65. Initial ring exploration `run-1795451888215` failed;
  `run-1795452243019` passed after using the documented projected normalization.
  The model's hand-world depth did not establish fingertip contact; it is not
  substituted for projected geometry. Fixtures are public, hash-pinned static
  photos, not moving-user clips or broad accuracy evidence.

Remaining limits: real-provider interpretation/native speech is deferred to
HI-12; physical hardware, moving/occluded-user accuracy, general anatomical IK,
metric depth and target pointing are not accepted here. Output geometry is the
explicit supported subset above. Paid spend/reservations remain **$0/$0**.

## HI-06 - Directional pointing in a declared frame

Status: **COMPLETE** (2026-11-23), committed and pushed as `98922ef8`.

The first pointing-v1 emitter completes the initial schema with source side,
validity and aspect-corrected ray evidence. A single pointing hand stabilizes over
three frames/300 ms; sector deadbands, foreshortening, two hands and missing
geometry withhold direction. Fresh proposal evidence binds the exact camera frame.
Restart/aspect changes invalidate its revision. Sign/pose/point share capture
consumption and ingress limits. Preview mirroring affects display only.

New Coordination profiles opt into pointing independently of pose/geometry tags.
Eight robot-own directions align the arm and index in direct and task output,
with the existing bounded hold/release and static history preview. An explicit
facing-camera convention grounds user-left; no target or depth is fabricated.
Older saved profiles retain their contract. Geometry output's view directions
remain distinct and documented. Native guidance retains silence/authority while
compacting duplicate wording; ten reachable instruction states max 15976 bytes.

Actual verification:

- 175 focused Java cases pass in `hi06-units-final-fixed.log` (HI-05 regression
  set plus DirectionalPointing). The initial broader run found absent pointing
  storage flags throwing during old Generic activation; optional flags now
  return false, including missing geometry flags on pose-only saved instances.
  Earlier focused failures exposed old baseline expectations, fixture mismatches,
  a 16081-byte prompt and a same-clock-instant test cue; all retained logs explain
  the corrections. The timing test waits past its actual freshness fence.
- `target/gptlive-acceptance-f6bfd7282d` passes 21 Java cases (20 unit and one
  MySQL journey), compilation and two Playwright cases. The app/schema/account
  were removed. Preparation, reload, acceptance, stale-frame/reused-capture
  rejection, one fresh contribution and task-neutral silent direct output pass.
- 81 JavaScript cases and eight Python schema/runner cases pass in the HI-06
  final logs. Coverage includes all eight directions on both articulated hands,
  missing/ambiguous/foreshortened input, frame revision, release, history, and
  actual script emitter capture sharing, display mirroring and reset fencing.
  Maven package and `git diff --check` pass.
- Browser evidence uses real local HTTP/MySQL/SSE with controlled detector and
  inference fixtures. Reviewed sixteen front/side pointing views, mirrored and
  unmirrored camera evidence, narrow-screen direction labels and left/right
  output. The scene fits the extended arm and labels robot-own directions.
- Actual MediaPipe: 11 direction cases/77 frames pass in
  `target/hand-interaction-detector/run-1795453946034`; the eight directions,
  palm/empty negatives and a seven-frame translated/rotated photo stabilize at
  300 ms. Geometry regression: 20 cases/140 frames pass in `run-1795453955097`.
  A rotated index pose revealed contradictory canned thumbs-down classification;
  known geometric index extension now suppresses that label for opted-in profiles.
  These are hash-pinned static photos and artificial pixel motion, not recorded
  moving users or proof of general robustness.

Remaining limits: targets/calibration, metric depth, real-provider/native speech,
recorded moving/occluded-user acceptance and physical execution remain unverified.
Paid OpenAI spend/reservations remain **$0/$0**. No configured remote database use.

## HI-07 - Configured regions and registered virtual destinations

Status: **COMPLETE** (2026-11-23), committed and pushed as `c7a271a8`.

New `hand.targets.v1` instances use the existing scoped API and ordinary Storage
for a maximum of sixteen registered regions/virtual destinations. IDs include the
agent namespace; optimistic revisions and exact frame identity bind all references.
An explicit bounded affine transform maps camera regions into virtual scene units.
The backend recomputes projected selection (one/none/multiple), never trusting a
client's claimed target or a nearest-depth guess. Up to four candidates explain
ambiguity. Setup changes pause active tasks and clear preparation while preserving
accepted terms. Expiry/deletion/recreation cannot revive obsolete references.

Direct and task output ground a target against current registry/camera evidence
after inference. Model-supplied snapshots cannot override it. The backend adds a
bounded resolved position; the renderer aims its index ray at the orange virtual
destination and labels it. Positions are limited to x=-4..4/y=-1..5/z=-3..3 and
at least 1.8 virtual units from the selected shoulder. This is the reference
avatar's supported workspace, not room calibration or physical actuator evidence.

Actual verification:

- **181 Java unit cases** pass (`hi07-units-final.log`): the HI-06 regression set
  plus six HandScene cases. Coverage includes scoped/revisioned references,
  intersection ambiguity, behind-ray negatives, stale frames, malformed/bounded
  registration, degenerate transforms, deletion/recreation/expiry, target-condition
  round trips, mapped and unmapped output, current-snapshot replacement, virtual
  output without a camera, and too-near destination rejection.
- **Two local MySQL journeys** pass in `target/gptlive-acceptance-bdf3deebcb`
  (28 Java cases including 26 units). Registered setup and accepted agreement
  reload unchanged, cross-scope setup is denied, forged client selection is
  recomputed, fresh preparation/cue acts once, silent direct output spends no task
  action, and deletion produces repair. The earlier scoped journey also passes
  in `0411945376`; the first `929f474801` exposed noncanonical serialization of
  target conditions. Fixed canonical JSON now round-trips strictly.
- **84 JavaScript cases** and **nine Python contract/runner cases** pass in the
  HI-07 final logs. The rig test checks the index ray against actual destinations
  for both hands and nonzero depth, not just plan JSON or the shoulder direction.
  `hi07-aim.log` retains the focused aiming check. Maven package and diff checks
  pass. Native instruction checks across ten states max **15974/16000 bytes**.
- **Two browser journeys** pass in `bdf3deebcb`: the HI-06 directional regression
  and configured Display -> preparation -> acceptance -> fresh cue -> explanation
  -> silent target point; overlapping Doorway yields ambiguity, reload preserves
  setup/history, and re-registration cannot redirect the old command. Final
  configured/ambiguous setup, 390px controls, and target output were reviewed.
  The earlier successful browser `892e3fa9a9` exposed a small visible index offset;
  aiming now accounts for the index's offset from the arm centre. Initial browser
  `3345d8838b` retained the same serialization failure as the first DB run.
- Backend prompt tests found that current pointing frame evidence had been omitted
  by the legacy perception-type list. Opted-in named/pointing evidence is now
  included without widening Generic's supported task observations. This is
  deterministic interpretation-context evidence, not a real-provider result.

No new detector model is introduced in HI-07; the browser uses controlled SDK
outputs and fake inference over real local HTTP/MySQL/SSE. HI-06's separate actual
model evidence remains the directional baseline. Dynamic people/items, source
association, broad moving-user robustness, room depth and native Live L-05 remain
pending. Paid spend/reservations **$0/$0**. All owned disposable apps, schemas and
accounts were cleaned; the configured remote database was not used.


## HI-08 - Dynamic people and item targets

Status: **COMPLETE** (2026-11-23), committed and pushed as `9f359195`;
source-association follow-up pushed as `ceaa12ec`, described below.

Reuses the existing single COCO-SSD inference and preserves social person
tracking. A separate bounded tracker retains person/cup/bottle/book boxes;
ambiguous assignments, crossings, occlusion, frame changes and long gaps retire
IDs. Server generations prevent label transfer even if a client reuses an ID.
Scoped snapshots expire after three seconds; exact retries are idempotent and
oversized, out-of-order, stale, malformed or undeclared input rejects.

Same-frame pointing combines configured regions and fresh detected boxes. An
estimated wrist inside exactly one person box identifies only a projected source
to exclude; ambiguity remains unresolved and identity/depth are never inferred.
The server recomputes source/selection and publishes the canonical current
resolution to the UI. Explicit labels and registered virtual counterparts support
input selection and immediate/task output. Binding edits pause active tasks;
dispatch rechecks target/counterpart expiry after inference. History is static.
Slow inference (>=1.8 seconds) is withheld with visible device feedback rather
than submitted as fresh; normal target emission is at most twice per second.

Actual verification:

- **291 Java unit cases** pass (`hi08-units-confirmed.log`). Six new target tests
  cover strict atomic admission/retries, generation and label retirement, profile
  gates, source exclusion, aligned targets, frame/time pairing, explicit mapping,
  stale dispatch and changed counterpart rejection. Existing Coordination, hand,
  direct-command and capability regressions pass. The first broader run exposed
  an optional social-guidance Live context at 16040 bytes; concise capability
  wording fixes it without truncating persona or raising the limit. Ten standard
  states now max **15908/16000 bytes**; the optional context test also passes.
- **Three local MySQL journeys**, plus fifty focused units, pass in
  `target/gptlive-acceptance-389b0fb44e` (53 Java cases). Dynamic bindings reload,
  cross-scope access is denied, duplicate snapshots add no event, a 3.1-second
  delayed interpretation produces CLARIFY without pointing, and reused raw IDs
  cannot inherit labels. Registered-target and legacy scissors regressions pass.
  The earlier DB run `33928ddb0d` passed thirteen Java cases; its browser selector
  initially omitted the new spec, now included in the existing runner.
- **88 JavaScript** and **ten Python contract/runner cases** pass in
  `hi08-js-final.log` and `hi08-python-final.log`. Coverage includes tracker
  movement/crossings/retirement, invalid classes/boxes, geometry and shared scoped
  emission/timing/cadence gates. Maven package and whitespace checks pass.
- **Two real-app Playwright journeys** pass in `389b0fb44e`: detected Cup -> label
  and virtual counterpart -> proposal -> preparation -> explicit acceptance ->
  one fresh cue -> silent direct point, aligned Bottle ambiguity, disappearance,
  repair and label retirement; plus the configured Display regression. Controlled
  SDK and inference fixtures use actual HTTP/MySQL/SSE. The first dynamic browser
  `deffae0cc9` exposed the missing live resolution display; `1521eebc9a` then
  exposed a test that opened Robot only after output (correct static preview,
  not replay). The test now opens the visible renderer before command delivery.
  Final dynamic visual/history rerun `0e50a34002` passes: selected/ambiguous
  panels, expired-label controls at 390px, and static history point toward the
  registered orange counterpart were reviewed. Fresh execution reaches released;
  reload shows preview without replay. A released neutral-pose screenshot from
  the earlier run is not claimed as evidence of the pointing pose.
- **Actual pinned COCO-SSD/tfjs model**: eight cases/56 frames in
  `target/hand-interaction-detector/objects/run-1795455995256` pass their declared
  class-output expectations. Images, model and archive hashes are recorded in
  `scene-corpus.json`; downloaded public photos remain ignored local artifacts.
  Overlays were reviewed. **Semantic review FAIL for the original two-bottles
  case**: a milk carton and jar were classified as bottles. This failure is
  explicitly retained, not relabelled as accuracy success. The other reviewed
  examples contain people, cups, a wine bottle and a book/bottle; no broad accuracy
  is claimed. Small pixel translation is not recorded-user tracking evidence.
  Software-WebGL inference measured **2488..3279 ms (median 2759)**, exceeding
  online freshness. Offline replay uses artificial timestamps and is **not**
  real-time device acceptance. Actual device timing and real-room robustness
  remain unverified; the runtime fails closed for slow/stale captures.

No provider, room-depth, physical actuator or general object-recognition acceptance
is claimed. Paid spend/reservations remain **$0/$0**. All owned disposable apps,
schemas and accounts are cleaned by the runner; remote MySQL was not used.


HI-08 source-boundary follow-up: review against the roadmap caught an overly
permissive path when source association was unresolved. Dynamic person/item
selection now requires exactly one projected source person; otherwise it returns
`unresolved/source_unknown` and keeps only direction. Registered-region behavior
is unchanged. The backend prompt no longer incorrectly says person/item targets
are unsupported. Unit tests cover unknown/multiple sources and aligned candidates
with a known source. `target/gptlive-acceptance-a8c43f5104` passes the focused Java
set and both configured/dynamic browser journeys, with an explicit source person
in the latter. This corrects the first implementation's boundary; it does not
change the retained detector accuracy/timing limitations. Paid spend remains $0.


## HI-09 - Ordered input sequences and counting

Status: **COMPLETE** (2026-11-23), committed and pushed as `3aa0676d`.

Added typed sign/pose/point/count sequences (1..12 steps) on checks and ON_CUE
rules, with independent preparation/run epochs and bounded durable progress.
Counts require all five/ten participating fingers to be known straight or bent;
neutral/unknown cannot manufacture zero. Dwell uses distinct ordered source
captures and shared ordinals; missing captures reset dwell. Stable wrong steps,
release between identical steps, original deadlines, source/hand changes, process
restart and explicit resume/re-preparation are enforced. One final capture can
spend one contribution; preparation and held final poses cannot replay it.

Added prompt/capability opt-ins, conditional bounded sequence agreement sizes,
fast bounded capture delivery while sequences are active, and a count/progress
panel that clears stale sensing and explains explicit restart. Saved profiles
keep their existing contract. No new wire-level completion event is trusted.

Verification actually run:

- Focused Java package run `hi09-package-confirmed.log`: 33 tests pass, including
  eleven counts, mixed sign/pose/point order, unknown/neutral, duplicates/gaps,
  held/repeated steps, deadlines, camera/hand/process restart and Coordination
  preparation/execution/control. Standard native instructions max 15,913 bytes;
  existing optional-context bound also passes. Earlier failures were test fixture
  mistakes (confidence and omitted pointing opt-in), retained in logs.
- 36 JavaScript count/emitter tests pass; the broader earlier ordinal run had 62
  passing hand/sensor cases. Eleven Python schema/runner tests pass. Malformed
  capture ordinals reject; unimplemented paid selectors still fail closed.
- Disposable MySQL `target/gptlive-acceptance-49202c9efb`: one real persistence
  journey passes, reloading partial progress and exercising camera restart,
  explicit re-preparation, acceptance, pause/resume, exactly one complete action,
  held final pose, replacement, Stop and reset.
- Actual scoped cockpit `target/gptlive-acceptance-73b5b5991b`: one browser journey
  passes with controlled SDK landmarks and actual emitted frames/SSE/storage.
  Wrong-step recovery, unknown hands, 1->2->3, camera pause/resume and no duplicate
  final contribution verified. Reviewed one-of-three, paused-source-390 and
  completed-390 screenshots: readable progress/count/status without clipping.
- Maven package and diff whitespace checks pass. No physical, real-room counting,
  or provider acceptance claimed. Paid spend/reservations remain **$0/$0**.


## HI-10 - Finite output sequences and robot counting

Status: **COMPLETE** (2026-11-23), committed and pushed as `124593a8`.

Added a capability-gated sequence of 1..12 supported sign/pose/point actions to
handAction and HAND_SEQUENCE direct interpretations. One sequence is one command
or task contribution. Hold 100..5000 ms; 350 ms entry/release per step; complete
sequence <=60 seconds, envelope <=3500 UTF-8 bytes, task plan <=4000 bytes. No
nested/indefinite output. Existing single outputs and saved profiles are unchanged.

HandExecution uses ordinary Storage and the existing serialized scoped application
boundary: canonical persisted event, server command/epoch/process identity, one
renderer owner, ordered timed receipts, five-second start and duration+15s end
windows. Target output is grounded before each step. Stop, new input, supersession,
changed scene, hidden/reduced/disconnected views and render gaps interrupt without
replay. Reload is a static preview; missing receipts become unknown. Final tasks
join required native speech and hand receipts in either order; no automatic retry
or extra action budget. The earlier unimplemented draft receipt schema was refined
to server-owned evidence plus a bounded event/owner/operation/step request.

Verification actually run:

- Final focused Java/local MySQL run `target/gptlive-acceptance-b2374e535f`: 121
  selected Hand/OutputSequence/NamedOutput/DirectCommands/Coordination delivery,
  voice and agent units plus two sequence persistence journeys pass. Database
  tests cover cross-scope denial, exclusive owner, persisted partial progress,
  Stop, late receipts, reset, one cue/one sequence, and final playback gating.
- 65 JS embodiment/rig/sensor tests pass, including virtual-clock step order,
  finger articulation, Stop after two, delayed authorization, hidden/reduced
  views, render gaps, target failure, connection loss and legacy behavior.
- Twelve Python schema/preflight tests pass. Native instruction maximum in the
  ten standard states is 15,903 bytes; optional social context also passes.
- `target/gptlive-acceptance-0ab1e53b1a`: 32 Java checks and one scoped browser
  journey pass. `target/gptlive-acceptance-20a544d139`: updated browser journey
  passes with synchronized GPU captures. Reviewed count-1 through count-5: thumb,
  then index/middle/ring/little visibly extend cumulatively. Reviewed narrow static
  history; Stop and final task receipt assertions pass. Earlier unsynchronized
  page captures sometimes showed an older frame; they are not pose accuracy proof.
- Retained initial failures: a test used an invalid spatial-frame literal;
  instruction growth exceeded optional Live context by 47 bytes (fixed by compact
  capability wording); HTTP acknowledgements lacked the persisted event ID (now
  shared with SSE); legacy VM tests exposed unnecessary crypto allocation (now
  only allocated for a sequence). Corrected focused checks, Maven package and diff whitespace validation pass.

No paid/provider, physical actuation or real-room accuracy claim. Spend/reservations
remain **$0/$0**. All runner-owned app/schema/account resources were cleaned;
remote MySQL and historical paid ledgers were untouched.


## HI-11 - Integrated modes, UI and resource acceptance

Status: **COMPLETE** (2026-11-23), committed and pushed as `47a3de28`.

Closed boundary gaps exposed by integration: modern hand representations reject
stale captures or changed reuse of an identity and ignore identical retries before
persistence. The five-capture/second limit permits one sign, pose and point
representation per capture. Active `hand.*` state plus current draft/agreement,
task spec and last command result has a 64 KiB UTF-8 ceiling. Rejected updates do
not commit partial scene/task changes. General retained event history is separate.

`event.payload` and `storage_entry.entry_value` are now TEXT to support the already
declared 16 KiB ingress and 32 KiB agreement contracts. README provides explicit
widening SQL for an existing installation; no remote migration was performed.
A 32,342-byte agreement (12-step/two-hand/all-finger conditions, three preparation
checks and maximal UTF-8 role fields) round-trips through local MySQL. Full terms
stay in Storage/backend inference. Live uses an explicit navigation projection
when those details cannot fit; it refers the person to cockpit task details rather
than inventing omitted terms. Normal spoken reviews retain their terms. Facts stay
within 4,000 bytes and the existing 16,000-byte native instruction limit remains.
The cockpit now explains saved-instance adoption, direct output versus triggers,
preparation, target mapping and the boundary between digital and physical evidence.

Verification actually run:

- `target/gptlive-acceptance-6f447e6284`: 318 selected Java cases, initially four
  errors in old retry fixtures that changed source timestamps on the same capture
  ID. The other 314 passed, including all 268 selected units, 17 native delivery
  integrations and four query-budget integrations. Corrected fixtures reuse the
  exact capture. `9118af9aa5` reruns all 29 Coordination integrations successfully.
- `6941b7b083`: five focused checks pass, including the large-agreement database
  round trip and rejection/rollback of a scene edit at the 64 KiB state boundary.
  The initial `d2d7647f62` boundary attempt exposed the 8,192-character Storage
  column; that failure is retained. Initial oversized/incorrect sequence test
  fixtures are also retained in `target/hand-interaction-mysql/hi11-*.log`.
- Final large-context/voice/admission regression: 26 units pass. Another 24
  RPS/registry/profile tests pass. 95 JavaScript hand/rig/sequence tests and 12
  Python schema/preflight tests pass. Package and diff-whitespace checks pass.
- Fixed five-sample legacy/new hand-ingress comparison: maximum elapsed 53/51 ms,
  14/14 ORM statements, one stored event per representation, no inference outside
  an active task and zero entity/collection writes on an identical retry. Limits
  declared before measurement were <=1,000 ms and <=12 additional statements.
  Existing Live context stays at 8 queries for both 5 and 205 history events
  (ceiling 25); native receipt remains one SQL statement, zero loaded entities
  and zero ORM statements (scoped ceiling 4). Pool ceiling remains 10. These small
  local fixtures are not a production-capacity or tail-latency claim.
- `9118af9aa5`: 20 of 21 browser cases pass, covering Text/Continuous, native audio
  independent of blocked backend work, final playback/uncertainty, Stop/reconnect,
  queued/in-flight withdrawal, geometry, composed poses, direction, both sequence
  directions, reduced motion, WebGL fallback, light/dark/narrow/keyboard and
  camera/model failures. Its named-output retry fixture also recreated the source
  timestamp; the original failure is retained and the fixture is corrected.
- `f425f5eb33`: both configured and dynamic target journeys pass. `1b2873e1cb`:
  model-failure visibility and screenshot rerun passes. `274eb4afc1`: corrected
  ten-sign direct/task/output retry journey passes, completing all 23 selected
  distinct browser cases across these runs. Reviewed successful dark
  and narrow finger reports, expanded dark camera/report, visible model/camera
  errors, selected-cup explanation, narrow configured scene and synchronized
  count-three rendering. Previous milestones retain all ten-sign and pose views.

No paid/provider or physical trial in this milestone. Spend/reservations remain
**$0/$0**. Real-detector misses/false positives and slow software-WebGL limits from
HI-05/08 remain visible; this does not establish general real-room accuracy.
All runner-owned schemas/accounts/apps are cleaned on exit; the owned loopback
MySQL remains available for HI-12. Remote MySQL and old paid ledgers are untouched.

## HI-12 - Real provider acceptance (incomplete; third extension active)

Status: **INCOMPLETE; third owner-authorized extension active on 2026-11-24**. The owner chose
immediate independent native speech and accepted strict silence as unsupported in
GPT-Live. No playback gate, backend speech approval, classification buffer,
task-window muting or replacement TTS will be added. Text/Continuous retain strict
silence. Optional truthful Live speech is permitted; task authority, semantic
correctness, rendering, lifecycle and latency requirements remain mandatory.

This is a prospective scope revision, not a retrospective pass. The original nine
attempts retain their revision-1 verdicts and reservations below. Revision-2
attempts and final mode checks are recorded in the continuation below. Complete
native audio remains required evidence.
The original limitation is consistent with [official suppression guidance](https://developers.openai.com/api/docs/guides/live-delegation#suppress-automatic-acknowledgments-while-tools-run).

The hand runner uses the real scoped cockpit, native Live, backend inference and
disposable loopback MySQL. Sensor inputs are controlled fixtures, not camera
accuracy evidence. Visitor audio is local Windows SAPI. Every attempt retains
source hashes, complete captions and received audio, provider usage, terms,
command/action identities, receipts and synchronized rendered images. Complete
received audio has also been transcribed locally for review; this is not physical
listening or provider-based grading. No physical microphone/speaker/adapter trial.

| Journey | Revision-2 acceptance |
| --- | --- |
| L-01 named signs | Latest PASS `1be4bc1e10`: two direct right thumbs-up signs, preparation/acceptance, one fresh-cue right peace and duplicate exclusion; full audio and all three images reviewed. |
| L-02 custom poses | Latest FAIL `a370692218`: exact task and direct ten-finger output render correctly, but native speech falsely refuses the independent direct command because of the completed task limit. Earlier PASS `e50790242f` remains historical. |
| L-03 ring/geometry | Latest FAIL `54d6fa0265`: exact combined geometry, near-miss rejection and both rings pass; native announces an OK demonstration while backend correctly asks for clarification. Earlier PASS `0439ea6bbc` remains historical. |
| L-04 direction | Latest FAIL `3377e48acd`: correct task robot-right and direct robot-left output; native falsely refuses the independent direct point. Earlier PASS `69a4fe99eb` remains historical. |
| L-05 registered target | PASS `1c7bb666d7`: exact target/frame, preparation/acceptance, one confirmed Ready and reviewed Display point. Later shared context/capture changes have no complete L-05 retest. |
| L-06 person/item | Still no complete PASS. Latest `b032f0bb3e` INCONCLUSIVE: exact cup proposal/preparation/review, but no completed acceptance ingress; person output not reached. Earlier backend/visual passes fail native semantics. Selected-label context repair has unit coverage but no complete person/item provider acceptance. |
| L-07 input sequence | Latest FAIL `365cccb3ed`: clarification adds an unwanted robot sequence and wrong judgement role; empty generated output now safely pauses at zero actions. Earlier fully specified PASS `c656de937d` does not cover the required clarification roundtrip. |
| L-08 output sequence | Latest FAIL `9f985668a2`: direct counts/Stop pass, but model returns START with a forbidden non-null agreement; task activation rejected. Earlier PASS `df8f86dd8f` remains historical. |
| L-09 conversation/latency and separate comparison | Latest PASS: comparison `2d8916425b`, Coordination `025f92b3fd`; complete audio review and correct attention sign. Median/max differences +263.842/+1856.325 ms pass the unchanged +1000/+2000 ms thresholds; three turns, no p95 claim. |
| M-01 Text/Continuous strict silence | PASS `31875ac4e1`, both modes; all three silent action windows and positive Continuous speech control. |
| M-02 native ownership, Stop and mode switching | PASS `31875ac4e1`; backend-held native audio, immediate Stop and mode switching; confirmed/uncertain final delivery also passes. |

Passing entries describe their recorded source manifests, not a guarantee across
provider runs. The latest regression results above prevent final-source acceptance.
M-01/M-02 include the fresh-sentence and empty-plan repairs; later selected-label
enrichment is covered by projection/delivery units and does not affect their
unselected scene paths. The final checkpoint below is authoritative; earlier
checkpoints retain their historical scope and verdicts.

All attempt directories below are under ignored `target/gptlive-acceptance-<id>`.

| Attempt | Journey | Result | Artifact / finding |
| --- | --- | --- | --- |
| 1 | L-01 | INCONCLUSIVE | `f9a4ec68cc`: automated checks passed, but complete review showed test-turn overlap and an unobserved pending final announcement. Correct thumbs-up image reviewed; not accepted. |
| 2 | L-01 | INCONCLUSIVE | `9cadb18321`: unused detector CDN script blocked startup; no native session/inference. Reservation retained. |
| 3 | L-01 | FAIL | `5e84ef459e`: model returned an object for HAND_SIGN instead of its required string; safely rejected, no motion. |
| 4 | L-01 | FAIL | `677242b6fd`: correct signs/action, but cancellation narration and post-action reflection; duplicate fixture also exceeded freshness before resubmission. |
| 5 | L-01 | INCONCLUSIVE | `4f5711a196`: correct direct thumbs up; harness incorrectly required optional speech. Task not reached. |
| 6 | L-01 | FAIL | `f2629c37ea`: preserved terms and one peace action; false cancellation wording and unsolicited reflection in complete received audio. |
| 7 | L-02 | INCONCLUSIVE | `b7183252c0`: login clicked before asynchronous initialization; no native session/inference. Reservation retained. |
| 8 | L-02 | FAIL | `42da9a7a9b`: all ten constraints retained but scalar rule fields used instead of handPose; safely rejected before proposal/action. |
| 9 | L-02 | FAIL | `ce594e8b5b`: corrected rule format, preparation/acceptance and one correct custom action; unsolicited reflection violates silence. Synchronized image reviewed: left index/right thumb stretched, other eight bent. |

Revision-1 checkpoint budget: **nine reservations/$24**, not an invoice total. Models are explicitly
`gpt-5.6-luna` and `gpt-live-1`; developer default `gpt-5.2` is unchanged. Verified
2026-11-23 prices and unchanged bounds give a conservative $1.9153 per-attempt
ceiling. Allocation revision 2 retains the first three $4 reservations and permits
at most eighteen later $2 reservations: 21 attempts/$48 within the owner's $50.
No failed, crashed or zero-request attempt is refunded. Historical GLF/TWC ledgers
are untouched. The retained ledger is `target/hand-interaction-budget.json`.
Observed usage across these artifacts: 29 backend requests, 225,150 prompt tokens,
6,428 completion tokens, and 864 seconds of recorded RTC lifetime. RTC lifetime
omits provider setup/closure and is not a billing measurement. No paid synthesis
or grading. Twelve further reservations remain, subject to the unchanged bounds.

Corrections implemented and locally verified:

- Clarify the HAND_SIGN string/sibling-effector shape and the versioned handPose
  rule, explicitly omitting scalar field/operator/value.
- Suppress an extra completion speaking occasion for a hand-only final action;
  preserve legacy nonverbal completion. Withdraw obsolete occasions silently;
  retain full context with an explicit quiet prefix on each nonsensory thinking
  chunk. Command-result resets preserve prior agreements. These improve guidance
  but have not guaranteed provider silence.
- Wait for native preparation review and arming; allow an absent optional visual
  acknowledgement; retain eight seconds after completion; replay duplicates before
  expiry. Wait for cockpit initialization and avoid unused detector downloads.
- Keep complete received audio even when playback is muted. The committed local
  transcription helper records package versions, weight/audio hashes and every
  segment; it does not assign a verdict. It was run on attempt 9 successfully.
- Independent scene captures now commit promptly during inference. One bounded
  latest capture per agent preserves source time and generations; a fresh scalar
  read bypasses the turn's ORM cache before output grounding. Epoch/scope fences,
  retry checks, disappearance, deletion and the 64 KiB active-state gate remain.
  Only the owning decision thread may assimilate data; speculative/background
  workers cannot mutate its aggregate. Passive scene data never spends an action.
- Camera detection continues while other sensor deliveries wait (one in flight per
  kind). Canonical capture receipts keep the target selector fresh. Intermediate
  scene frames coalesce; applied captures remain in ordinary Storage/history.
  README documents the added capture table. No remote migration was performed.

Verification actually run:

- 143 final focused Java units and provider-budget tests pass; package succeeds.
  Commands and output: `target/hand-interaction-mysql/hi12-final-units.log` and
  `hi12-final-package.log`. Earlier 57 task/narration and 69 context checks are
  retained; the final selection includes their relevant coverage.
- 43 Python preflight/contract/budget tests and four Node/browser capture tests
  pass (`python -B -m unittest discover -s tests/gptlive -p 'test_*.py'` and
  `node --test tests/gptlive/test_hand_capture.mjs tests/gptlive/test_hand_media.mjs`).
- `0dce408ced`: 41 Java checks pass, selecting AgentTurnSerialiserUnitTest,
  CoordinationAgentIntegrationTest and LiveQueryBudgetIntegrationTest. Includes
  target continuity across a blocked transaction, disappearance, scope, retries,
  epoch/reset and cascading capture deletion. Matched legacy/new hand ingress is
  38/47 ms maximum and 16/16 statements in the fixed five-sample fixture.
- `7aadbc4658`: all three affected capture integrations pass again after the final
  gateway/receipt changes. Three browser cases pass: camera/target selection with
  3.8 seconds blocked inference and still-fresh labels; independent native audio
  across Continuous switches; Stop while inference remains blocked. The new
  fresh-target screenshot was visually reviewed. `0dce408ced` also passed both
  Coordination/comparison-agent Text direct-command cases and the original target
  journey. Five distinct browser journeys pass across the runs.
- Retained failures: `7c68996c96` proves original capture queue blockage;
  `51a1bd4622` and `6d4ca59da9` expose the stale ORM read after capture decoupling.
  A scalar latest-capture projection fixes that; both continuance and disappearance
  passed in `d403814ce9` and the later regression runs. No TTL was increased.
- `git diff --check` passes. All runner-owned applications/schemas/accounts were
  cleaned. The owned loopback MySQL remains for continuation; remote MySQL and the
  pre-existing local service are untouched.

Acceptance pending at the revision-1 checkpoint: all revision-2 L-01..09 against final integrated behavior,
including same-revision L-09 comparison and latency limits; M-01/M-02; full semantic/audio/visual review;
operator trials listed in `tests/hand-interaction/README.md` (all NOT RUN).
The real-detector failures and software-WebGL limits from HI-08 remain known.

### Revision-2 definition verification (2026-11-24)

Updated the roadmap, executable manifest, paid rubric, Live stimuli/scenario
assertions and current documentation to the owner's mode boundary. Reports carry
acceptance revision 2; preflight rejects old revisions and an L-09 comparison from
another revision. Complete audio capture, semantic review, action/pose validation,
budget enforcement and all revision-1 artifacts remain intact. No production
speech/playback code changed.

Actually run for this definition/harness update:

- `python -B -m unittest discover -s tests/gptlive -p 'test_*.py'`: 44 pass.
- `node --test tests/gptlive/test_hand_capture.mjs tests/gptlive/test_hand_media.mjs
  tests/gptlive/test_hand_scenarios.mjs`: six pass. Includes retained received audio
  with muted test playback, optional native speech in the pose harness and rejection
  of the wrong finger pose. These are harness tests, not application acceptance.
- `node --check` on `hand_journey.mjs` and `hand_scenarios.mjs`: pass.
- `python -B tests/gptlive/run_acceptance.py --hand-interaction contracts`: nine
  examples pass with no database/provider access.
- `python -B tests/gptlive/prepare_hand_audio.py`: regenerated 27 local SAPI clips;
  hashes/text pass `hand_paid.validate_audio()`. Provider calls: zero.
- Budget validation and `git diff --check` pass. The ledger still contains nine
  attempts/$24 reserved; no new paid request, database run or physical trial.

At this definition checkpoint, acceptance remained incomplete. The execution
continuation below records the subsequent M-01/M-02 checks and revision-2 Live
runs; the current matrix above reflects their final outcomes.


### Revision-2 execution continuation (2026-11-24)

All artifacts retain the source revision plus per-file start/end hashes. Successful
canonical L-01/L-02 behavior is unchanged by the later lossless model adapter;
identity and strict parsing regressions cover that path. No native speech ownership,
playback gate, muting, replacement TTS, TTL, task limit or consent rule was changed.

| Attempt | Journey | Result | Artifact / finding |
| --- | --- | --- | --- |
| 10 | L-01 | PASS | `748a94c65d`: capabilities, two authorized direct thumbs-up requests, prepared/accepted one-cue peace task; wrong and duplicate cues spend no extra action. Full captions/local ASR and sign images reviewed. |
| 11 | L-02 | INCONCLUSIVE | `44873a36db`: startup/input automation stalled with a large PCM number array; transport stopped before transcript, inference or reply. No backend provider call. Reservation retained. |
| 12 | L-02 | FAIL | `1a1f603484`: correct ten-finger draft/preparation, but provider transcribed the acceptance as only "Start the". Backend correctly kept configuration; native speech falsely claimed starting. Full local ASR of the visitor clip confirmed the original full sentence. |
| 13 | L-02 | PASS | `e50790242f`: all ten constraints retained; separate-hand negatives fail; prepared/accepted fresh combined cue acts once, then a direct custom pose leaves agreement/count unchanged. Task/direct images and complete captions/local ASR reviewed. Truthful completion follow-up is permitted. |
| 14 | L-03 | FAIL | `c4878a6175`: ambiguity clarified and correct direct ring rendered. Complete geometric condition appeared in scalar rule fields; strict parser rejected the draft before task start. |
| 15 | L-03 | INCONCLUSIVE | `93175570b5`: backend correctly returned CLARIFY for ambiguous OK, with no motion. No native reply/voiced output before timeout; full local ASR empty. Explicit ring/task and the parser correction were not reached. |
| 16 | L-04 | FAIL | `f84796cf1a`: correct image-right/robot-right terms, but handPoint used the same scalar wrapper. No saved draft/action; complete speech truthfully reported the failed update. |

Corrections and evidence:

- Model-only typed-check/rule conversion copies the full condition from an exact
  field/eq/object-value wrapper into its typed member. Event/field must agree;
  unknown fields, mixed encodings and all strict validation remain. Public/stored
  parsing is unchanged. Target availability and fresh camera frame checks still
  run after conversion. This repairs syntax, never invents or changes task terms.
- `CoordinationModelAgreementUnitTest` covers complete geometry, signs, directions,
  target references and ordered sequences; no accepted agreement/action before
  preparation/consent, unavailable target rejection and conflicting/invalid terms.
  All 61 focused Java cases pass after the final extension. An initial new fixture
  omitted the current camera frame; the corrected fixture supplies it, while the
  unregistered-target variant deliberately remains rejected.
- PCM now crosses automation as base64; a real headless-browser harness test
  verifies the complete 45-second PCM checksum, responsive polling and unchanged
  audio budget. Eight Node/browser harness tests and 44 Python tests pass.
- Future acceptance uses the shorter "Start now." clip. All 27 clips were generated
  locally; previous artifacts keep their original text/hashes. No paid synthesis.
- `b2a8a67df2`: 38 local MySQL checks and four mode browser cases pass. Text silent
  windows have zero synthesis/PCM/media playback. Continuous positive control
  plays PCM; pose/count windows preserve synthesis=2/PCM=1, task/completion preserves
  synthesis=5/PCM=3. Native blocked-backend, Stop and mode switches pass. Third-count
  images reviewed: right thumb/index/middle straight, ring/little bent; the Text
  and Continuous files are identical. A blank image-viewer display was not a bad
  capture. Final peace screenshot is after release, not active geometry evidence.

Allocation revision 3 preserves all earlier reservations and allocates the unused
$2 buffer: at most 22 attempts/$50, with the unchanged $1.9153 conservative ceiling.
Official model prices were rechecked unchanged on 2026-11-24. At that checkpoint the ledger held
16 attempts/$38 reserved; final usage is recorded below. Actual invoice total remains unavailable. Failures are
not refunded and no earlier rubric failure is relabeled as a pass.

Production verification after the reaction adapter extension: `e3e93e20be`
passes the same 38 Java/local MySQL and four browser mode checks. Third-count
Text/Continuous images were reviewed/byte-compared; the full mode evidence is
retained in `mode-review.json`. Packaging succeeds (`hi12-final-package.log`).
The person/item fixture now runs independent bounded scene and pointing feeds,
matching the camera pipeline. A blocked-pointing harness test proves scenes keep
refreshing, only one delivery per kind runs, and cleanup drains pending requests.
No provider interpretation, task result or verdict is injected by this fixture.

Further revision-2 attempts:

| Attempt | Journey | Result | Artifact / finding |
| --- | --- | --- | --- |
| 17 | L-05 | FAIL | `614a1d51c8`: exact Display reference/terms, but preparation and reaction both used scalar wrappers. Preparation parsing rejected the proposal; no activation, cue or direct output. Complete audio truthfully reported failure; two extra short "mm" input transcripts produced no task. |
| 18 | L-06 | FAIL | `0f27412f5a`: ambiguous cup clarification and scoped selection succeeded. Provider transcripts omitted words; the proposal reversed the demonstrator role, questioned the action budget and left checks/rules empty. Native paraphrase also confused roles, then reported no active task. Person output and disappeared-target rejection were unreached. |

The adapter now covers preparation checks and rules equally, with all 61 focused
Java cases passing again. The unregistered-target case supplies a current frame,
so rejection specifically retains target grounding. No semantic repair is applied
to the changed roles or empty rules in L-06; that run remains failed.

| Attempt | Journey | Result | Artifact / finding |
| --- | --- | --- | --- |
| 19 | L-07 | INCONCLUSIVE | `701b969927`: correct complete proposal; 1-3 gives wrong_step/no evidence, 1-2-3 completes preparation without actions. Full native review is consistent, but its combined segment is INCOMPLETE/activity_or_transcript_timeout, leaving the review unconsumed. Harness did not bypass this to accept. Active repeat/hold not reached. |
| 20 | L-08 | INCONCLUSIVE | `8956a39114`: correct five ordered poses/500ms holds accepted. Session closes context_delivery_unconfirmed, causing voice_owner_lost interruption at step 0. No ordered visual frames; explicit Stop, triggered count and reload not reached. Empty native speech is allowed and is not the failure. |

These are not silence failures. The remaining native capture/context-delivery
boundaries are retained; no timeout, acknowledgement or consent check was relaxed
to force acceptance. The independent deterministic sequence and lifecycle evidence
from HI-09..11 remains valid but does not substitute for a successful paid journey.

Final check/rule adapter verification: all 61 focused Java units, 44 Python tests,
eight Node/browser harness tests and nine contract examples pass; packaging passes.
`bac97ece5d` passes 38 MySQL cases, Text silence and both native ownership cases.
Its Continuous test fails at startup because the test dereferenced the fake mic
before initialization. The readiness poll now waits for the optional object;
the affected case is rerun separately. No production speech code changed.

The corrected Continuous readiness test passes in `71e9fa1fbd` (35.2 s), along
with three local LiveCockpit smoke cases. All silent synthesis/PCM counters remain
unchanged from their positive control; the final count PNG is byte-identical to
the reviewed correct Text frame. Together with `bac97ece5d`, all four required
mode cases pass on the final production code. `71e9fa1fbd/mode-review.json` records
the source hashes and per-case artifact mapping. The earlier startup failure remains
retained, not hidden by an automatic retry.

| Attempt | Journey | Result | Artifact / finding |
| --- | --- | --- | --- |
| 21 | L-09 comparison | PASS | `10320f2bcf`: three matched ordinary clips, complete captions/local ASR and retained native onset telemetry. Repeated/truncated opening phrases and split input transcripts remain visible. |
| 22 | L-09 Coordination | PASS | `79739a10c7`: same models/revision/clips; appropriate ordinary responses, no extra TTS or unauthorized task. Correct immediate right index-up sign inspected in `ordinarynew.png`, with no agreement/action-budget mutation. |

Latency uses the predeclared first native audio after input starts, relative to the
visitor's speech end. Negative values mean speech began before the visitor finished;
these are real onsets, not missing values. With only three matched clips per agent,
comparison median/max are -556.14/-456.00 ms, Coordination -605.14/-462.80 ms.
Differences -49.00/-6.80 ms pass the +1000/+2000 ms allowances. This is a small
matched regression check, not p95 evidence or a general latency promise. Both full
recordings retain the repeated opening fragments; no output was hidden.

### Original $50 bounded checkpoint

HI-12 remains **INCOMPLETE**: revision-2 L-01, L-02, L-09 and M-01/M-02 pass;
L-03..08 require successful complete retests. The syntax failures in L-03..05 have
local regression coverage after correction. L-06 still needs reliable transcription
and unchanged role/preparation semantics; L-07 needs confirmed complete native review;
L-08 needs successful context delivery and the whole visual/Stop/reload journey.
None is replaced by deterministic fixtures or relabeled under a weaker rubric.

The ledger contains **22 reservations/$50**, exhausting allocation 3. All original
entries remain. Aggregate recorded usage is **68 backend requests, 492,765 prompt
and 17,688 completion tokens, plus 2,052.05 seconds of RTC lifetime**. This excludes
provider setup/closure and is not an invoice measurement. No paid synthesis or
grading occurred. `target/hi12-final-usage.json` retains the calculation. Every paid
source manifest has an empty `sourceChangedDuringRun`. Calling
`hand_paid.available_budget('L-03')` now rejects before any provider/database access;
no further reservation was attempted. Additional paid attempts require an explicit
extension of this recorded allowance; do not reset/refund the existing ledger.

Final commands actually run:

- `mvnw.cmd -q -Dtest=CoordinationModelAgreementUnitTest,CoordinationHandPoseUnitTest,CoordinationAgentUnitTest,CoordinationNamedSignUnitTest,HandPoseTaskUnitTest,CoreTaskVoiceUnitTest,DirectionalPointingUnitTest,CoordinationSequenceUnitTest test`: 61 pass; output `target/hand-interaction-mysql/hi12-model-boundary.log`.
- `python -B -m unittest discover -s tests/gptlive -p 'test_*.py'`: 44 pass.
- `node --test tests/gptlive/test_hand_capture.mjs tests/gptlive/test_hand_media.mjs tests/gptlive/test_hand_scenarios.mjs tests/gptlive/test_hand_audio_input.mjs tests/gptlive/test_hand_observation_pump.mjs`: eight pass.
- `python -B tests/gptlive/run_acceptance.py --hand-interaction contracts`: nine examples pass with no provider/database access.
- Isolated runner with `--java-tests 'CoordinationAgentIntegrationTest,LiveQueryBudgetIntegrationTest' --browser --browser-mode enabled --browser-grep 'strict hand silence|native Coordination audio precedes blocked backend' --live-only`: `bac97ece5d` passes 38 Java cases and three browser cases; startup-only Continuous failure retained above.
- Affected rerun with `--browser --browser-mode enabled --browser-grep 'strict hand silence in Continuous' --live-only`: `71e9fa1fbd` passes that browser case and three LiveCockpitSmokeIntegrationTest cases. Both use `--database-properties target/hand-interaction-mysql/application-test.properties`; no remote database.
- `mvnw.cmd -q -DskipTests package`, JavaScript syntax checks and `git diff --check`: pass.
- Each paid selection uses the same loopback properties, explicit `OPENAI_MODEL=gpt-5.6-luna`, `--hand-interaction L-0N --paid-core hand-live`; attempt 21 additionally uses `--hand-comparison`. All new audio was reviewed with `python -B tests/gptlive/review_hand_audio.py <artifact>`; no physical listening or provider grading.

All runner-owned apps, schemas and accounts were cleaned. The owned loopback MySQL
remains available; the pre-existing local service and remote database are untouched.
No deployment, agents-branch integration, physical microphone/speaker/robot trial or
new real-camera accuracy claim. HI-08 detector false positives and software-WebGL
latency limits remain known. Source: `da50fa8c` plus the changes in this checkpoint;
per-file manifests identify the exact code used for each attempt.

### Owner-authorized $20 extension checkpoint (2026-11-24)

Status: **INCOMPLETE; all 36 attempts/$70 reservations exhausted**. Source baseline
was `432da24e`; each attempt preserves its own source hashes and an empty
`sourceChangedDuringRun`. The owner explicitly authorized another $20; the original
authorization and all earlier reservations remain intact. No further paid attempt
is authorized by this checkpoint. Commit/push is within the owner's existing
instruction; no deployment or agents-branch integration.

This extension established complete L-04 directional-pointing and L-08
output-sequence passes. It also exposed and repaired three concrete defects:

- `LiveCommandResult` dereferenced the intentionally null scalar `value` on accepted
  HAND_POSE, POINT and HAND_SEQUENCE receipts. It now emits only the bounded command
  kind for those structured plans, retaining full data in Storage and all quiet,
  freshness, ownership and deduplication rules. This explains the earlier L-08
  `8956a39114` context-delivery failure; that old verdict is unchanged.
- `HandPosePromptEventContentAdapter` omitted relational geometry even after the
  typed predicate matched. It now projects validated pair gaps/spreads and known
  camera-plane axes using fixed labels and bounded numeric formatting. Invalid or
  unsupported geometry stays unknown; valid bend data survives. This is projected
  evidence, not physical contact/depth. The fix has unit coverage but no complete
  successful L-03 provider retest.
- `LiveTranscriptSegmenter` treated a whitespace-only provider input caption as a
  new human interruption. The exact observed sequence was reproduced with three
  failing unit cases before the fix. Padding no longer interrupts assistant
  capture or hides a subsequent meaningful turn. Real textual and acoustic
  interruption, required silence, coverage and uncertainty safeguards remain.
  This shared capture fix has final offline coverage only; L-07 stays INCONCLUSIVE.

Model guidance now distinguishes direct-command and task sequence envelopes,
human cue constraints from robot output poses, and an observed target reference
from its registered virtual destination. Incomplete-draft questions are supplied
in current-turn context with guidance to resolve them via PROPOSE. Existing
REPLACE consent, START preparation and target-freshness validation are unchanged.
A real Coordination graph regression verifies clarification leaves the task in
PREPARATION without activation or an action; no successful paid clarification
roundtrip has yet exercised this guidance.

The harness waits for the preparation request to be consumed before supplying
the demonstration and triggering its full review. This prevented the earlier
merged response from exceeding capture duration. The acceptance clip changed
from "Start now" to "I accept the current proposal" after the provider transcribed
only "now". One explicit input-sequence clarification clip is available. All 28
visitor clips were generated locally with Windows SAPI; no provider synthesis or
grading. Terms, zero-action acceptance and fresh-cue assertions were not relaxed.

Every attempt below retains `journey.json`, available provider/capture records,
`semantic-review.json` and full received-audio ASR review under
`target/gptlive-acceptance-<id>`. A startup failure has no received audio. All
failures and inconclusive results retain their full reservation.

| Attempt | Journey | Reserved | Verdict | Artifact and finding |
| --- | --- | --- | --- | --- |
| 23 | L-08 | $2 | FAIL | `6c837da9c7`: direct five counts and Stop passed; task model put correct three-step data in invalid motion.kind/value. Safely paused, zero actions. |
| 24 | L-08 | $2 | FAIL | `1de8df8db8`: direct sequence/Stop passed; model put three robot output poses into three right-hand input conditions. Proposal rejected. |
| 25 | L-07 | $2 | FAIL | `2150b1f2d2`: correct count conditions, unnecessary role/additional-acknowledgement questions; preparation not reached. |
| 26 | L-03 | $2 | FAIL | `9f54d8a270`: backend clarified ambiguous OK but native speech prematurely announced a demonstration. Correct explicit ring inspected. Valid proposal/preparation/acceptance then one task action with unknown-gap speech and no ring because geometry was absent from model projection. |
| 27 | L-04 | $2 | PASS | `69a4fe99eb`: camera image-right/visitor-left input, robot-right task output, wrong/duplicate exclusion, one action, then immediate robot-left/left-arm point without task changes. Full audio and synchronized images reviewed. An unnecessary optional follow-up is retained. |
| 28 | L-05 | $2 | INCONCLUSIVE | `5c32ba4dfe`: exact registered target checks/rules and complete preparation/review. Provider heard only "now"; backend correctly kept CONFIGURATION. Direct target point not reached. Early native readiness/consent wording is not counted as successful acceptance. |
| 29 | L-06 | $1 | FAIL | `bbee299898`: ambiguous cups clarified, selected cup prepared/accepted, fresh cue produced one Ready. Direct person point used reply.motion, the destination reference and invalid fields; no execution, stale check not reached. |
| 30 | L-03 | $1 | INCONCLUSIVE | `02a7f15ac2`: backend CLARIFY, no native clarification during the 35-second wait; explicit ring/task not reached. |
| 31 | L-03 | $1 | INCONCLUSIVE | `35527547ca`: startup instructions exceeded 16,000 bytes (16,077). Launched before a same-batch failing unit result was inspected; no provider capture. Reservation retained. Guidance shortened and instruction-budget regressions passed before further calls. |
| 32 | L-03 | $1 | INCONCLUSIVE | `e0ae02e89c`: shorter guidance passed startup; backend CLARIFY again had no native reply within 35 seconds. |
| 33 | L-08 | $1 | PASS | `df8f86dd8f`: ordered five direct counts at 500 ms; repeated count at 1,000 ms stopped at step two. Correct human-pose preparation/review/acceptance, canonical three-step task at 500 ms, scoped COMPLETED receipt, one action, duplicate exclusion and no reload replay. All ten pose images match the corresponding inspected images from attempt 23 byte-for-byte. Full speech is consistent; repeated acceptance wording and partial captured input "I accept the current" are retained. |
| 34 | L-07 | $1 | FAIL | `821fb2969c`: one clarification answered, but model used REPLACE instead of PROPOSE. Existing consent guard preserved the incomplete draft. |
| 35 | L-07 | $1 | INCONCLUSIVE | `7eae6b1f68`: correct one-two-three cue, wrong-order zero, completed preparation/review and acceptance, then exactly one fresh-sequence Ready. A blank input caption falsely interrupted capture before required silence, leaving delivery UNCERTAIN. Fixed offline after this run. |
| 36 | L-06 | $1 | FAIL | `af9b9aed24`: ambiguity clarified, then selected-cup proposal rejected with operation_constraints after 24.016 seconds of inference. Exact caught reason is not retained; possible expired pointing-frame evidence needs a focused reproduction. Person and stale-target checks not reached. Native Ready during negotiation followed by an accurate failure explanation. |

For attempt 35, whole-file local ASR hallucinated repeated material beyond the
103.32-second recording. That tail is explicitly excluded from speech evidence.
Both raw reviews remain; supplemental ASR over 94..103.32 seconds recognizes Ready
at 101.12..102.0, corroborating provider captions and audio activity. The actual
blank input delta spans provider 101000..101200 ms, interrupting the assistant
100200..100400 ms before the required 2400 ms silence. Review is transcription,
caption and waveform evidence, not physical listening. No failure is relabeled.

Budget and usage:

- Allocation 4 preserved 22 attempts/$50 and initially permitted ten $2 extension
  reservations. After six, allocation 5 preserved all 28 attempts/$62 and reduced
  future backend calls from 36 to 14, reserving eight $1 attempts within the same
  $70 ceiling. Other caps and models stayed unchanged. The conservative bound is
  `14 * ((80000+2048)*0.2*2.5 + 2048*1.2)/1000000 + 7*0.05 = $0.9587424`.
  Official [backend model pricing](https://developers.openai.com/api/docs/models/gpt-5.6-luna)
  and [Live pricing](https://developers.openai.com/api/docs/models/gpt-live-1)
  were rechecked on 2026-11-24. No reservation is refunded, including startup failures.
- `target/hi12-extension-audit.json` records **113 backend requests, 849,775 prompt
  tokens, 37,333 completion tokens and 3,689.546 seconds of client RTC lifetime**
  across all 36 attempts. The extension alone accounts for 45 requests, 357,010
  prompt tokens and 19,645 completion tokens. RTC lifetime includes startup and
  excludes other provider phases; it is not billable duration or an invoice.
- Prefix comparisons against `target/hi12-extension-ledger-before.json` and
  `target/hi12-allocation5-ledger-before.json` confirm preservation of earlier
  entries. Missing/shortened ledgers, changed historical reservation amounts and
  exhausted allowances reject before provider access. The original authorization
  remains; entries 23+ also record the explicit extension authorization.

Final verification actually run:

- **232 unit tests in 20 suites pass**, covering Live command/context/capture,
  geometry projection, Coordination proposal/consent, typed poses and sequences,
  output encoding, observed targets, direct commands and provider budgets.
  Suite counts: `target/hi12-extension-final-verification.json`; output:
  `target/hand-interaction-mysql/hi12-extension-final-unit.log`;
  pre-fix blank-input reproduction: `hi12-blank-input-repro.log` (three failures).
- **45 Python tests pass** with `python -B -m unittest discover -s tests/gptlive -p 'test_*.py'`;
  three Node scenario/observation-pump tests pass; nine hand contract examples pass
  without provider/database access. Final budget test also rejects lowering a
  historical reservation while keeping its sum internally consistent.
- **46 isolated local-MySQL integration tests and six Playwright cases pass** in
  `7742045c5f`: CoordinationAgentIntegrationTest (34), DirectCommandIntegrationTest
  (5), LiveQueryBudgetIntegrationTest (4), LiveTranscriptIngressIntegrationTest (3).
  Browser cases cover M-01 in both modes, M-02 Stop/mode switching under blocked
  backend inference, and confirmed/uncertain native final delivery. Reviewed
  screenshots show the distinct delivery states and right-hand counts one/two/
  three. Text and Continuous count images are byte-identical. Final peace-panel
  images are captured after release and show the neutral rig with its last label;
  they are not held-pose visual evidence.
- The first integration run `9d3336d96b` had one intermittent German contextual
  "Bereit" acceptance failure (READY instead of EXECUTION): 37/38 passed. Its
  focused rerun `b5191e1c6a` passed, as did the final full 46-case run. The original
  failure remains recorded; no production change was made to hide it.
- Final `mvnw.cmd -q -DskipTests package` passes; output
  `target/hand-interaction-mysql/hi12-extension-final-package.log`. JavaScript syntax
  and `git diff --check` pass. All DB/browser runs explicitly use
  `--database-properties target/hand-interaction-mysql/application-test.properties`.
  Runner-owned apps, schemas and accounts were cleaned; no remote DB access.

Remaining acceptance: L-03 needs an actual native ambiguity clarification and a
complete relational-geometry task after the projection fix; L-05 needs complete
acceptance and direct registered-target output; L-06 needs successful direct
selected-person output and stale-target rejection, plus diagnosis of the slow
proposal failure; L-07 needs complete native capture after the blank-caption fix
and a successful clarification roundtrip. Recheck affected Live capture journeys
on the final source as well. Do not weaken freshness, consent or native speech
ownership to satisfy these checks. No further paid calls without another explicit
allowance. Physical microphone/speaker/robot trials remain **NOT_RUN**; previous
camera-accuracy and software-WebGL limits remain. HI-12 is not complete.


## HI-12 second extension - frame freshness and interpretation repairs (2026-11-24)

Continued from main `e8e4e8e3` under the owner's additional $30 authorization.
Allocation 6 preserves every prior reservation and permits thirty further $1
attempts, at most 66 attempts/$100. The original 36-entry/$70 prefix is unchanged
against `target/hi12-second-extension-ledger-before.json`. Models, per-attempt
limits and $0.9587424 conservative bound are unchanged. Actual invoice unavailable.

Repairs and their boundaries:

- A fresh validated passive scene capture can establish the current camera frame
  after a serialized pointing event ages during inference. It cannot supply a
  cue, revive a stale target or mask a newer changed frame. Three-second scene
  expiry remains unchanged. Fixed-clock tests cover empty scenes, equal-time
  conflicts, newer frames, expiry and undeclared capability; local MySQL tests
  exercise proposals after slow inference and reject disappeared targets.
- Delegated thinking replies no longer prohibit the pending native answer. Other
  context stays quiet; no new speaking occasion or playback gate is introduced.
- Geometry guidance preserves the AND of requested finger states and relations,
  numeric thresholds and partial masks, without inventing thumb/index bends.
  Task guidance preserves human demonstration/judgement and robot output roles.
- Spatial guidance separates relative left/right conventions from named target
  frames and distinguishes proposing a target condition from demonstrating it.
  Direct clarification previously described every unresolved request as a
  sign/gesture, even a stale target. Current-turn feedback and Text/Continuous
  clarification now cover actions and targets. Immediate-demo guidance no longer
  promises that pointing needs no setup. Native capabilities remain in their
  existing dedicated section instead of being duplicated in command guidance.
- Input clips were shortened without changing required conditions. Direction and
  registered-target fixtures now provide the independent empty-scene capture that
  the camera pipeline emits. Person/item fixtures begin with their actual scene.
  Observation request/capture timings are retained with a 2048-entry bound.
  L-07 now explicitly leaves the response unspecified, then supplies Ready once,
  human judgement and one action, to require a clarification roundtrip before
  preparation/acceptance. Prior audio manifests and verdicts remain recorded.

| Attempt | Journey | Verdict | Evidence |
| --- | --- | --- | --- |
| 37 | L-03 | FAIL | `f5715b95e5`: native ambiguity clarification and direct ring work; model incorrectly says geometry cannot combine with three finger states. |
| 38 | L-03 | INCONCLUSIVE | `b7c6795d8c`: exact combined condition and preparation work; provider invents a Hmm user caption outside injected turns, interrupting review. No acceptance bypass. |
| 39 | L-07 | FAIL | `02ee4732ec`: model assigns human judgement to robot and adds acknowledgement; clarification revises via PROPOSE but retains unsupported roles. |
| 40 | L-06 | FAIL | `ee802faca7`: garbled input, empty rules and native pointing/demo claims; no task saved. |
| 41 | L-07 | PASS | `c656de937d`: exact sequence, wrong-order zero action, full preparation, fresh accepted sequence produces one confirmed Ready; held three does not repeat. |
| 42 | L-03 | FAIL | `5a33601196`: provider input inserts Silent context only; model invents neutral bends and nests action fields inside pose. Strict validation rejects. |
| 43 | L-05 | FAIL | `85faa34416`: correct target but unnecessary orientation question; operation_constraints after 15.914-second inference with no independent scene capture. |
| 44 | L-06 | FAIL | `80ef62496b`: provider captures only At that; backend refuses to invent intent, native denies visible cups. Unnecessary initial empty scene removed. |
| 45 | L-03 | PASS | `0439ea6bbc`: exact <0.18 plus three stretched fingers; no added bends, correct clarification, preparation, acceptance, fresh/duplicate cue behavior and right ring. Native refers to the specified cue rather than repeating its number. |
| 46 | L-05 | INCONCLUSIVE | `48251cb19b`: one correct Ready, complete capture and semantic match. Browser sampling_gap at 120682 ms discards evidence before association at 121005 ms; completion withheld. Guard unchanged. |
| 47 | L-06 | FAIL | `a60bc0a23e`: all backend/visual assertions pass, including selected-person point and disappeared-target rejection. Native incorrectly clarifies a ring and claims continued pointing at stale Visitor. |
| 48 | L-06 | INCONCLUSIVE | `5d48c6a22a`: correct native ambiguity question and exact selected-cup proposal; target generations change during capture gaps, invalidating selection. No activation. |

All thirty second-extension attempts reserve $1 each. Complete received audio was transcribed
locally and reviewed with full captions/backend evidence; physical audibility is
NOT_RUN. Every completed paid source manifest reports no tracked changes during
its run. Images reviewed include both rings in `0439ea6bbc`, the selected-person
point in `a60bc0a23e`, and the completed sequence cockpit in `c656de937d`.

Offline verification: the first integrated run passed 249 Java tests in 24 suites,
45 Python tests, 25 Node tests and nine schema examples. The 48-case isolated
MySQL run `8ecedbcba1` and all six browser checks passed; all six unique images
were inspected. Final coverage reaches 342 Java tests in 32 suites, 45 Python
tests, 27 Node tests, nine schema examples and packaging. The 48-case isolated
MySQL and six-browser rerun `31875ac4e1` passes, including the fresh-sentence and
empty-plan repairs. The later selected-label change has three new unit tests;
it changes no schema or DB path. After restricting pose guidance to capable
profiles, 59 command/capability tests and packaging pass again. Counts are retained in
`target/hi12-second-final-verification.json`. Initial reproductions retain two
frame/delegation failures and one clarification-scope failure. An intermediate
clarification guidance change exceeded the 16000-byte Live budget (three unit
errors); removing duplicated capability text restored passing startup coverage.
A first slow-frame DB reproduction had one failed proposal check; its cause was
not conclusively established. A focused rerun and the later 48-case run pass. Do not erase these failed evidence records.


Further second-extension findings:

- Partial sensor proposals with an unknown response keep the known cue in checks
  and use null reactions; an empty executable action is invalid. Clarifications
  may fill goal, roles and success because incomplete drafts have no protected
  terms. A regression preserves the sequence and forbids activation before
  clarification and preparation. The happy-path fixture waits for the completed
  spoken clarification before answering it.
- A generated empty ACT plan now follows the existing generation-failure pause
  path, before action spending or completion. Text and Live reproductions first
  failed (COMPLETED instead of PAUSED), then passed; subsequent camera samples
  cannot trigger automatic retries. Valid silent hand output remains covered.
- Unresolved-command feedback is factual data rather than an instruction to ask
  another question. This does not create a new speaking occasion. Repetitive
  provider speech remains an acceptance failure.
- Ready journeys now require one fresh complete native Ready event after the
  execution cue, in addition to task and playback checks. The regression rejects
  empty/reflection-only output, old/incomplete/synthesized speech and duplicates.
  Full recording review remains required; caption matching alone is not a pass.
- Capture pumps now count HTTP time toward their 500 ms interval, preserving one
  in-flight request per kind. In `f1a219b06f`, scene captures have no >3-second gap
  (maximum 1104 ms) while serialized pointing requests reach 12031 ms. No TTL,
  target generation, capture or playback fence was relaxed.

| Attempt | Journey | Verdict | Evidence |
| --- | --- | --- | --- |
| 49 | L-06 | FAIL | `f1a219b06f`: fresh target and exact preparation; native falsely denies pointing recognition. Acceptance is clipped to accept the; backend correctly leaves it inactive. |
| 50 | L-07 | FAIL | `6626bcb6f9`: model asks the missing-response question but supplies an empty executable action; strict validation rejects the partial draft. |
| 51 | L-07 | FAIL | `e872738bb9`: correct sequence control and one spent action, but clarification keeps obsolete roles/success; behavior model returns an empty plan instead of Ready, then completion is narrated. The semantic MATCH in this run covers arming only. |
| 52 | L-05 | PASS | `1c7bb666d7`: complete registered-target task, one confirmed Ready, duplicate rejection and correct inspected Display point. Initial unnecessary repeat request is followed by a correct proposal/review. |
| 53 | L-06 | FAIL | `fcd9c3805d`: confirmed cup task and correct inspected Visitor point. Native denies the selected person label in a repeated-question loop; stale-target repeat has no durable user transcript. |
| 54 | L-07 | FAIL | `ca9e25ca21`: completed clarification and full recognition, but the model retains recognition-only roles/success after adding Ready. Native review repeats those obsolete terms; an unexpected incomplete user turn interrupts review. No acceptance/action. |

All completed attempts retain complete captions, local audio review and separate
semantic verdicts. `target/hi12-second-extension-audit.json` records usage and the
unchanged original 36-entry prefix. The latest source manifests have no tracked
changes during paid runs. No physical or production acceptance is implied.

### Final second-extension checkpoint (2026-11-24)

HI-12 remains **INCOMPLETE**. All 30 new $1 reservations are consumed: 66 attempts,
$100 cumulatively, zero remaining under this authorization. The original 36/$70
entries are byte-for-byte equivalent as parsed ledger data. No failed reservation
is refunded. The offline budget preflight rejects another selection without any
provider access. The recorded cumulative usage is 235 backend requests, 1,813,121
prompt tokens, 77,645 completion tokens and 7,484.092 seconds of RTC lifetime.
RTC lifetime excludes setup/closure and is not a billed-duration measurement;
the actual invoice total remains unavailable. There was no paid synthesis or grading.
The second extension alone records six PASS, nineteen FAIL and five INCONCLUSIVE
attempts, 122 backend requests, 963,346 prompt tokens, 40,312 completion tokens
and 3,794.546 seconds of RTC lifetime. Passing attempts are not a full matrix.

Additional repairs, following concrete reproductions:

- A completed preparation/support or proposal-review sentence can consume its
  existing occasion when it begins after that occasion within an older native
  segment. Ordered, nonfuture trusted parts must reproduce the captured speech
  exactly, and the whole final sentence must begin after the boundary. Eleven
  positive/negative cases cover old/split text, mismatches, incomplete capture,
  expiry, owner changes and missing parts. Task-contribution semantic/arming
  checks are unchanged; this consumes no action and supplies no acceptance.
- Fresh scene context now includes the selected user label, target type and
  whether its explicit virtual output mapping is still current. It enriches the
  existing observation with the same source/time/expiry; no new observation or
  speaking occasion. Three tests cover quiet delivery, changed labels/mappings,
  expiry, disappearance and reappearance. A later generation cannot borrow an old
  selection, or an old source a new label. Labels never establish identity.
- Explicit supported poses remain immediate commands after task reflection and
  need no sensor reading. This guidance is restricted to capable profiles; legacy
  profiles do not advertise pose support. Backend behavior passes, but native
  speech still sometimes applies the previous task limit to a new direct request.

| Attempt | Journey | Verdict | Evidence |
| --- | --- | --- | --- |
| 55 | L-01 | FAIL | `5b458f57a9`: direct thumbs-up images correct; proposal goal reverses acceptance roles. Correct preparation sentence starts after the occasion inside an older segment and is not consumed; led to the trusted-part fix. |
| 56 | L-02 | FAIL | `5a56867a38`: exact ten-finger task and rendered pose; subsequent explicit pose interpreted as KEEP/restart, with an incorrect fresh-reading dependency. |
| 57 | L-06 | INCONCLUSIVE | `1f8d34f3c4`: correct ambiguity/proposal, but selected cup generation 2 becomes stale against current generation 6; rejected before saving. Selected-person context not reached. |
| 58 | L-07 | FAIL | `365cccb3ed`: initial clarification works, but the next model response adds robot sequence repetition despite You only say Ready and assigns wrong judgement. Empty behavior now pauses at zero actions; no false completion. |
| 59 | L-01 | PASS | `1be4bc1e10`: exact cue/roles/peace response, preparation/acceptance, fresh/duplicate checks; both direct thumbs-up images and task peace reviewed. Initial terse acknowledgement is followed by explicit inactive-proposal review before acceptance. |
| 60 | L-02 | FAIL | `a370692218`: automated backend/visual journey passes, including independent direct pose after completion. Full native audio falsely refuses that command because of the old task limit. |
| 61 | L-06 | INCONCLUSIVE | `b032f0bb3e`: exact cup task/preparation and correct native review; acceptance never produces a completed durable user turn. Remains inactive; person/stale-person checks not reached. |
| 62 | L-03 | FAIL | `54d6fa0265`: exact geometry, near-miss, preparation/acceptance, both correct rings. Native claims an OK demonstration when backend correctly returns CLARIFY with no action. |
| 63 | L-04 | FAIL | `3377e48acd`: correct frames, one robot-right task point and independent robot-left direct point, both visually inspected. Native falsely refuses the direct point as outside the old cue. |
| 64 | L-08 | FAIL | `9f985668a2`: five correct direct counts plus two repeat steps/Stop, all images reviewed. START returns an echoed agreement instead of null and is rejected; task counting/reload not reached. |
| 65 | L-09 comparison | PASS | `2d8916425b`: three relevant replies; complete audio reviewed. Speech-end onset median -493.342 ms, maximum -290.200 ms. |
| 66 | L-09 Coordination | PASS | `025f92b3fd`: relevant conversation and correct inspected attention sign. Median/max differences +263.842/+1856.325 ms pass unchanged limits. Negative onset values mean native overlap; three samples do not establish p95. |

Final verification and evidence boundaries:

- 342 Java units across 32 suites pass (`target/hand-interaction-mysql/hi12-final-selection-verified-unit.log`);
  the final capability-scoping edit then passes 59 command/capability tests
  (`target/hi12-final-command-profile-unit.log`). Final packaging passes
  (`target/hi12-final-package.log`). Failed reproductions remain retained.
- 48 isolated local-MySQL tests and six Playwright cases pass in `31875ac4e1`.
  Eight silent-output images match previously inspected bytes; both new confirmed
  and uncertain-delivery images were inspected directly. The post-release peace
  screenshot is not evidence of a held peace pose. Later selected-label enrichment
  has unit coverage; no final DB rerun is claimed for that change.
- 45 Python tests, 27 Node tests and nine schema examples pass. The new native
  Ready assertion prevents empty/reflection-only completion from passing the
  automated speech journeys. Full semantic review still rejects provider errors
  even when automated backend/visual assertions pass.
- Every new paid attempt has complete local received-audio review, a separate
  semantic verdict and an empty sourceChangedDuringRun list. Current SAPI clips
  replaced earlier local PCM; earlier stimulus text/hash manifests remain, not
  every prior PCM byte. New screenshots listed in reviews were inspected.
- Branch is main, based on `e8e4e8e3`; origin/main was unchanged at final fetch.
  Disposable schemas/accounts and owned runner applications were cleaned up.
  Existing local MySQL remains available. No deployment, agents merge, remote
  database migration or physical microphone/speaker/robot trial was performed.

Remaining work is provider reliability and complete final-source acceptance:
truthful native handling of independent commands and ambiguity, a faithful
partial-sequence clarification, reliable acceptance capture/START shape, and a
complete selected-person/disappeared-target run. L-05 needs a final shared-context
regression despite its earlier complete pass. The latest L-01/L-09 and offline
checks do not establish those missing outcomes. Preserve native speech ownership,
consent, target freshness and capture/delivery guards; do not weaken the rubric
or add playback control to turn these failures into passes. No further paid
requests under the exhausted authorization.

### Third extension started (2026-11-24)

Continued from main `c14fb24f` under the additional $100 authorization. The original
66 entries/$100 are preserved in `target/hi12-third-extension-ledger-before.json`.
Allocation 7 permits at most 100 further $1 attempts, 166/$200 cumulatively, with
the same models and technical caps. Official pricing was rechecked; the bound is
still $0.9587424. Prior matrix rows describe their source snapshots and await the
affected regressions below.

Offline reproductions expose completed-task context that fails to distinguish new
immediate requests, contradictory protected-term guidance on incomplete drafts,
and imperative acceptance falling through to inference. A pre-existing optional
correction path also exceeds the 16000-byte instruction ceiling by 32 bytes.
The focused repair preserves task limits, agreement/history, consent and delivery
guards; it narrows context and removes redundant instructions. Exact regression
evidence and provider outcomes will be recorded at the next checkpoint.

### Third-extension progress before final sensor-context regressions

Models and reservation history: attempts 67–71 use GPT-5.6 Luna/GPT-Live-1;
72–74 use Luna/GPT-Live-1.1; 75 onward use GPT-6.2 Sol low/GPT-Live-1.1.
Production configuration is unchanged. Allocation 8 kept the 14-call cap and
reserved $7 (bound $6.38008); allocation 9 lowers future calls to ten, reserving
$5 (bound $4.6572). It preserves all 78 entries/$136 and allows twelve further
attempts under the same $200 authorization. Snapshots before reallocations are
`target/hi12-stronger-model-ledger-before.json` (74/$108) and
`target/hi12-ten-call-ledger-before.json` (78/$136). Actual invoice total unavailable.

Every completed run below retained full received audio, local ASR and a manual
semantic verdict; tracked source hashes were unchanged during each run. All
artifacts are under `target/gptlive-acceptance-<suffix>`.

| Attempt | Journey | Artifact suffix | Verdict and evidence |
| --- | --- | --- | --- |
| 67 | L-02 | `09ab30ea7e` | FAIL: model nests reply/command in agreement; strict envelope rejects before preparation/action. |
| 68 | L-02 | `4b5c018fe0` | INCONCLUSIVE: caption arrived before observed playback; old harness aborted too early. |
| 69 | L-02 | `eb5f99bdf4` | PASS at this source: exact ten-finger trigger/output, negative split hands, completed-task independent demo. Both images inspected. Multilingual local ASR confirms English audio despite corrupt caption fragments. |
| 70 | L-03 | `530317c2ad` | FAIL: correct ambiguity/ring/geometry/preparation, but input acceptance truncates; backend remains inactive while native voice claims agreement. Ring image inspected. |
| 71 | L-07 | `b0f9b750c1` | FAIL: partial clarification drops human judgement; spurious input during silence withdraws proposal review. |
| 72 | L-07 | `29d470fdad` | FAIL: clarified roles, preparation and complete acceptance succeed; native arming adds joke/invitation, so unchanged semantic guard pauses with zero actions. |
| 73 | L-07 | `1fd1fdac00` | INCONCLUSIVE: spurious provider input during silence withdraws clarification occasion; no later workflow tested. |
| 74 | L-04 | `9db0b1f5e2` | FAIL: model stores preparation as unresolved question with no executable pointing rule; native prematurely asks acceptance. |
| 75 | L-07 | `4321568d25` | FAIL: correct clarified agreement rejected because unused explanatory speech exceeds Live 350-character limit; prompt only applied that limit to contributions. |
| 76 | L-07 | `2c2aef8c60` | INCONCLUSIVE: correct draft; delayed HTTP consumed the driver's dwell loop, leaving only one count-two capture. Recognizer correctly refuses preparation. |
| 77 | L-07 | `68e5245fe6` | PASS: explicit partial-response clarification, exact agreement, wrong-sequence rejection, preparation/consent, one confirmed native Ready and no held-pose repeat. Full audio reviewed; captions include nonlexical fillers, not additional task content. Human judgement remains in saved terms. |
| 78 | L-06 | `a51295a427` | FAIL: backend cup clarification, accepted cue/Ready, and rendered selected-person point are correct; native repeatedly claims unauthorized cup alignment, substitutes a cue and continues unsolicited remarks. Stale-person repeat not reached. Person image inspected. |

Offline fixes and verification so far:

- Completed agreements are history, with independent immediate requests separately
  validated; exact imperative acceptance follows existing preparation/consent
  guards. Incomplete-draft clarification replaces placeholder roles/success while
  complete agreements retain protected terms. Reproduced failures before fixes.
  91 focused Java tests pass (`hi12-third-fixes-unit.log`); follow-up 45 Java tests
  pass after the remaining static revision conflict was removed.
- A native reply wait requires caption plus actual browser playback. Six harness
  tests pass after dwell coverage now requires distinct captures spanning the
  hold, including a 900ms acknowledgement (`hi12-third-dwell-harness-final.log`).
- Native control confirmations yield after their requested response. The optional
  correction/startup path stays below 16000 UTF-8 bytes; 29 voice/context tests
  pass (`hi12-third-control-unit-final.log`).
- Live proposal narration is omitted; all remaining Live speech intents have the
  existing 350-character limit. Reproduced the inconsistent prompt before repair;
  49 Coordination tests pass (`hi12-third-proposal-limit-unit.log`).
- Every split sensor append now retains quiet-reference framing. A new regression
  failed before repair; 51 context-delivery/delegation/projection tests pass after
  compacting redundant wire wording, retaining original source/expiry and the
  five-ACK sensory performance ceiling (`hi12-third-sensor-context-unit-verified.log`).
- Allocation-9 validation and all 45 Python acceptance tests pass
  (`hi12-third-ten-call-harness.log`). Final DB/browser/package checks and all
  affected paid regressions remain to be completed. HI-12 is not complete.

### Third-extension final sweep and checkpoint (2026-11-24)

**HI-12 INCOMPLETE.** Attempts 79–88 use the final implementation/harness source,
GPT-6.2 Sol with low reasoning, GPT-Live-1.1 and allocation-9 limits. Every run
retains full native captions, local full-recording ASR, provider traces and a
separate semantic review. All 22 attempts in this extension have unchanged tracked
source hashes during execution. Documentation was finalized afterward.

| Attempt | Journey | Artifact suffix | Reviewed outcome |
| --- | --- | --- | --- |
| 79 | L-06 | `2c23a3e667` | FAIL: cup ambiguity clarifies without action and selected-cup preparation succeeds; native adds an unrequested user phrase, then its long review/backchannels remain incomplete. Acceptance, person output and stale-person repeat are not reached. |
| 80 | L-03 | `c2e83f885a` | FAIL: native asks thumbs up versus ring, but backend interprets unqualified “okay sign” as ACTION/ok_ring and publishes before clarification. Later geometry/task checks are not reached. |
| 81 | L-04 | `c721900e89` | INCONCLUSIVE: correct frames, direction, roles, preparation and one-action terms; overlong native review cannot complete capture. Acceptance/output checks are not reached. |
| 82 | L-05 | `23aab80b46` | FAIL: backend binds the registered Display correctly; native says the target is ambiguous, offers setup again and continues unrelated remarks. Driver cannot reach quiet; task/output checks remain unverified. |
| 83 | L-01 | `24ce59f607` | INCONCLUSIVE: correct capabilities and two independent right thumbs-up outputs, both images inspected. Correct proposal/preparation; native review is incomplete, so accepted cue/peace output is not reached. |
| 84 | L-02 | `e68cc8c407` | FAIL despite automatic pass: exact ten-finger conditions, negative split/missing hands, one accepted task output, and independent completed-task output all pass. Both images match. Native asks the user to demonstrate again for the fully specified direct request. |
| 85 | L-07 | `8a4fed2881` | INCONCLUSIVE: explicit missing-response clarification, exact saved sequence/roles, wrong-sequence rejection and completed preparation/review pass. Provider captions omit “proposal” from the full acceptance stimulus; backend KEEP leaves zero actions. Execution/held-pose checks are not reached. |
| 86 | L-08 | `31f5963ace` | INCONCLUSIVE: one-through-five output and Stop after step two pass; all seven active images inspected. Prepared task terms are correct, but the same acceptance-caption truncation prevents arming. Task count and reload checks are not reached. |
| 87 | L-09 comparison | `c1d1fadee0` | PASS: accurate ordinary identity/activity/planning replies and independent native playback; complete audio reviewed. |
| 88 | L-09 Coordination | `000517e728` | PASS: accurate matched ordinary replies, bounded native onset comparison and one correct right-index attention command. Active image and complete audio reviewed. |

The paired three-turn onset smoke comparison has Coordination minus baseline
median **-471.30 ms** and maximum **-94.90 ms**, within the predeclared +1000/+2000 ms
ceilings. Negative values retain native speech beginning before the visitor
finishes. This is a three-turn smoke comparison, not a statistical latency claim.
Minor native restarts remain in the evidence. The earlier complete L-02 (69) and
clarified L-07 (77) passes remain recorded; they do not override these final results.

Final offline verification on this implementation:

- **363 Java tests / 28 suites**, zero failures/errors: Coordination, direct
  commands, Live context/delegation/capture/segmenter, capability projection and
  TaskResponse. `target/hi12-third-final-unit.log` and
  `target/hi12-third-final-unit-summary.json` retain the selection/results.
- **48 local-MySQL tests / four suites** and **six Playwright cases** pass in
  `target/gptlive-acceptance-f557375817`: Coordination persistence, direct commands,
  context-query budget and Live transcript ingress; Text/Continuous silent pose,
  finite sequence and prepared hand task; native speech before blocked backend,
  Stop/mode switching, confirmed final playback and lost-receipt uncertainty.
  Six active count images and both native status panels were visually reviewed.
  The final peace screenshot is after release, not proof of an active pose.
  Providers/media are fixtures in these browser checks. M-01/M-02 pass.
- **45 Python tests**, **16 Node harness tests**, **nine schema examples**, Maven
  packaging with tests skipped after the above runs, and `git diff --check` pass.
  Logs: `hi12-third-final-python.log`, `hi12-third-final-node.log`,
  `hi12-third-final-db-browser.log`, `hi12-third-final-package.log` under `target`.
- All runner-owned applications, random local schemas and restricted accounts
  were cleaned. The pre-existing local MySQL remains running. No remote database,
  deployment, agents merge, physical camera/robot or room-audibility trial.

Budget reconciliation: **88 attempts/$186 reserved cumulatively**, **22/$86 in
this extension**. The original 66/$100 and later 74/$108 and 78/$136 snapshots
match the final ledger prefixes exactly. No refunds or resets. **$14 remains
unreserved**; current caps allow two additional $5 selections, but none are queued.
Actual invoice total is unavailable. Production model configuration is unchanged.

After the paid sweep, the ledger minimum was advanced from 78 to 88 so shortened
history cannot reopen used reservations. A regression reproduced acceptance of an
87-entry ledger before this repair and rejects it afterward without mutation.
All 45 Python tests pass again (`hi12-third-ledger-floor-verified.log`). This
preflight-only change does not alter the implementation exercised by paid runs.

Remaining acceptance work is substantive: reliable shape clarification before
output; native wording consistent with current task/command facts; bounded,
complete proposal reviews; complete provider capture of explicit acceptance; and
full person/item stale-target coverage. Consent, capture, semantic delivery and
target expiry guards remain intact. Further unchanged retries would not establish
reliability. Keep HI-12 open and retain the accepted speech-ownership architecture.
