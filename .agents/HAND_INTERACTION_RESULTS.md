# Hand interaction implementation evidence

Started 2026-11-23 from main `025697df`. The owner authorized sequential
implementation, commit/push after each verified milestone, then continuation.
Provider allowance: at most $50 cumulative across all new successful/failed
requests and modalities. Current provider billing: **in progress; invoice total not available**.
Cumulative reservations: **$24** (HI-12 allocation revision 2). No deployment.
Historical GLF/TWC ledgers remain unchanged.

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

## HI-12 - Real provider acceptance (in progress)

Status: **INCOMPLETE; acceptance revision 2 defined on 2026-11-24**. The owner chose
immediate independent native speech and accepted strict silence as unsupported in
GPT-Live. No playback gate, backend speech approval, classification buffer,
task-window muting or replacement TTS will be added. Text/Continuous retain strict
silence. Optional truthful Live speech is permitted; task authority, semantic
correctness, rendering, lifecycle and latency requirements remain mandatory.

This is a prospective scope revision, not a retrospective pass. The original nine
attempts retain their revision-1 verdicts and reservations below. No new paid run
has been made under revision 2. Complete native audio remains required evidence.
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
| L-01 named signs | NOT RUN; revision-1 failures retained below. |
| L-02 custom poses | NOT RUN; revision-1 failures retained below, including unreached immediate custom request. |
| L-03 ring/geometry | NOT RUN |
| L-04 direction | NOT RUN |
| L-05 registered target | NOT RUN |
| L-06 person/item | NOT RUN |
| L-07 input sequence | NOT RUN |
| L-08 output sequence | NOT RUN |
| L-09 conversation/latency and separate comparison | NOT RUN |
| M-01 Text/Continuous strict silence | NOT RUN against the complete revised pose/sequence/task matrix. Prior units/browser evidence remains recorded below. |
| M-02 native ownership, Stop and mode switching | Prior blocked-backend/Stop/switch regressions passed below; final acceptance against revision 2 pending. |

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

Budget: **nine reservations/$24**, not an invoice total. Models are explicitly
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

Remaining acceptance: all revision-2 L-01..09 against final integrated behavior,
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

The redefinition is verified; HI-12 acceptance itself is still incomplete as
shown in the matrix above. M-01 coverage must be completed where missing, followed
by M-02 and the revision-2 Live runs within the existing budget.
