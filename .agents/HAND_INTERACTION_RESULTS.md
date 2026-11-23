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

## HI-03 ? Six named signs as immediate and task output

Status: **COMPLETE** (2026-11-23). Commit recorded in the next milestone.

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

## Subsequent milestones

HI-04 through HI-12: NOT STARTED. Follow `PLAN_HAND_INTERACTION.md`; each completion
must record its actual checks, remaining limits and source revision here before
committing and pushing.
