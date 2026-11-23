# Hand interaction implementation evidence

Started 2026-11-23 from main `025697df`. The owner authorized sequential
implementation, commit/push after each verified milestone, then continuation.
Provider allowance: at most $50 cumulative across all new successful/failed
requests and modalities. Current spend/reservations: **$0 / $0**. No deployment.
Historical GLF/TWC ledgers remain unchanged.

## HI-01 — Contracts and compatibility baseline

Status: **COMPLETE** (2026-11-23), ready for the authorized commit/push.

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

## Subsequent milestones

HI-02 through HI-12: NOT STARTED. Follow `PLAN_HAND_INTERACTION.md`; each completion
must record its actual checks, remaining limits and source revision here before
committing and pushing.
