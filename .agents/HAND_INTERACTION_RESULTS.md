# Hand interaction implementation evidence

Started 2026-11-23 from main `025697df`. The owner authorized sequential
implementation, commit/push after each verified milestone, then continuation.
Provider allowance: at most $50 cumulative across all new successful/failed
requests and modalities. Current spend/reservations: **$0 / $0**. No deployment.
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

HI-05 through HI-12: NOT STARTED. Follow `PLAN_HAND_INTERACTION.md`; each completion
must record actual checks, remaining limits and source revision before committing
and pushing.

## HI-05 - Relational geometry and OK ring

Status: **COMPLETE** (2026-11-23), prepared for the authorized commit/push.

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
