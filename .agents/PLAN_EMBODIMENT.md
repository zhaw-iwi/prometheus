# Valerian alternative embodiment view

EV-01 through EV-03 are complete. Verification, retained failures and remaining
limits are recorded in [EMBODIMENT_RESULTS.md](EMBODIMENT_RESULTS.md).

Started 2026-11-15 on `feature/valerian-embodiment`, based on the current study
branch. The owner requested implementation, a roadmap with testable milestones,
and previously authorized milestone commits/pushes and local-DB/live-provider
smokes. No deployment, study protocol change or participant recruitment.

## Contract

- Keep the channel strip, Speech at the top and Latest Behaviour Event at the
  bottom. A persistent Details / Robot switch changes only the middle region.
  Details remains the initial default and retains existing DOM IDs and behaviour.
- Render canonical plans through the existing deduplicated envelope path; no
  additional model calls, polling, database writes, sensor inputs or task decisions.
- An original, dimensional, softly shaded robot has an expressive head/eyes,
  articulated arms and five fingers. Author its mesh/joint hierarchy locally in
  reproducible JavaScript using a pinned, locally served Three.js dependency.
  This avoids an unlicensed external character asset. The earlier generated
  concept is visual guidance, not an animation asset or promised pixel match.
- Map supported gestures, expressions, gaze, energy/stillness and RPS hand signs.
  Explicit signs own the affected hand; user signs never become robot actions.
  Display payloads occupy an integrated readable display area, with arbitrary
  fields inspectable as text. Unknown pose labels remain visible with neutral
  fallback. No extension of agent prompts, output schema or supported sign set.
- Speech stays in the existing card and player, with no new lip-sync/audio path.
  Eyes/head express symbolic gaze, not physical eye tracking. Energy is movement
  styling, not motivation or a new emotion inference.
- Fresh acknowledged/live plans can animate once. History/reconnect and view
  switches display static poses, never reenact an old action. New plans supersede
  current animation; native speech-only updates preserve current embodiment.
  Disconnect/reset clears it. Hidden/reduced-motion views stop animation.
- Support ordinary, maximized and detached columns; light/dark and narrow layouts,
  keyboard controls, textual alternatives and WebGL failure fallback to Details.
  The HRI study UI and its text-only output remain unchanged.

## Milestones and acceptance

1. **EV-01 — mapping and lifecycle contract.** This roadmap, a pure plan-to-pose
   adapter and deterministic animation admission/timing. Unit tests cover complete
   and partial plans, bounds, unknown labels, sign precedence/ownership, display
   data, profile filtering, native preservation, replay, visibility and reset.
2. **EV-02 — robot and cockpit integration.** Local authored 3D character, renderer,
   Details / Robot switch and canonical event/lifecycle wiring. Browser tests use
   real WebGL rendering with controlled events, covering switch persistence,
   unchanged speech/latest event, all mapped channels, hidden/replayed plans,
   reduced motion, missing WebGL, resize/maximize/detach and disconnection. Inspect
   screenshots in light/dark, desktop/narrow and representative hand poses; iterate
   on visual defects. Units alone cannot certify appearance or legibility.
3. **EV-03 — integrated acceptance and handoff.** Run focused neighboring JS/browser
   and Java static contracts, a disposable local-MySQL application smoke and a
   bounded live OpenAI text journey. Inspect the real persisted plan in Robot view.
   Preserve provider failures/usage and distinguish generated output from browser
   rendering. Update README, context, PROJECT and EMBODIMENT_RESULTS. Commit/push.

After each milestone, record actual verification and commit/push before continuing.
Stop for an unforeseen substantive owner decision. Do not claim physical robot,
human recognizability, broad hardware performance or HRI-study validation.

## Implementation anchors

`public/valerian/script.js`: renderBehaviourPlan, handleBehaviourEnvelope,
resetCockpitColumns, existing replay/dedup and interaction profiles.
`public/valerian/index.html`: behaviour-visual-grid and existing card IDs.
New client modules live under `public/valerian/embodiment/`; framework unchanged.
Tests: `tests/js/embodiment`, `tests/playwright/valerian-embodiment.spec.mjs`,
existing valerian-lifecycle/column-expansion/transcription and hand-sensors tests.
The disposable-MySQL runner is `tests/gptlive/run_acceptance.py`.
