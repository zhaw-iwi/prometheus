# Valerian embodiment implementation evidence

Scope and acceptance: [PLAN_EMBODIMENT.md](PLAN_EMBODIMENT.md).

## EV-01 — complete, 2026-11-15

Created the roadmap and pure browser plan adapter/timeline. Preserves canonical
input, maps supported expressions/gaze/gestures/signs, bounds style values,
separates user signs and display content, filters declared modalities and uses
neutral fallbacks for unsupported pose values. Fresh visible events alone admit
transient animation; history, switches, native speech and reset are explicit.

Verification: `node --test tests/js/embodiment/*.test.mjs`: seven tests passed.
`git diff --check`: passed. No UI or provider behaviour changed in this milestone.
Robot asset, integration, browser/DB/provider acceptance remain EV-02/EV-03.

## EV-02 — complete, 2026-11-15

Added the alternative Details / Robot middle region, preserving the channel
strip, Speech and Latest Behaviour Event. Default remains Details; preference is
local and shared with detached windows. Authored original shaded 3D geometry,
head/eyes/expression, jointed arms and mirrored five-finger hands with explicit
sign priority. Display text/game fields are readable; complete payload remains
inspectable. Three.js 0.187.0 is pinned and served locally with its MIT license.
No generated artwork or third-party character asset is used at runtime.

Canonical envelope delivery and existing deduplication own event admission.
History and switches show static poses; native speech preserves embodiment;
new plans supersede animation; user text, disconnect/reset and page teardown
settle/clear it. Hidden/reduced-motion views stop frames. WebGL failure/loss
returns to Details. No speech/audio or backend/agent contract changes.

Verification: 11 mapping/rig tests and 15 neighboring hand-sensor tests passed.
Five Playwright tests passed against locally served production assets using real
WebGL and controlled API events (`target/embodiment-visual-final`). Covers view
selection/persistence without mutations, native/replay/disconnect, safe display
text, all three signs, reduced motion, narrow/maximized/detached view and WebGL
failure. Light/dark and hand screenshots were inspected. Initial visual review
led to larger, outward-facing hands and readable display summaries. Earlier
fixture failures (missing session mock, hidden diagnostics button, Euler callback
comparison) were fixed; the hand-sensor fixture now loads the existing profile
resolver used at the new integration boundary.

Real DB/provider and neighboring browser regression remain EV-03. Human sign
recognizability, physical embodiment and broad GPU/device performance are untested.
