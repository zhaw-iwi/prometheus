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

## EV-03 — complete, 2026-11-15

Added an explicit `--embodiment` mode to the existing disposable-MySQL runner:
isolated Maven builds, source manifests, focused browser regression and optional
real-provider output inspection. It rejects unrelated study/provider modes. The
paid text smoke is capped at 24 requests / 200,000 tokens and zero Live sessions;
the browser only reads the actual scoped instance and changes the local view.
All owned apps, schemas and accounts were removed by the runner after each trial.

Final adapter review preserved existing German/plural sign aliases and the
precedence of valid display signs over motion signs. Invalid display signs no
longer hide a valid motion sign. Camel-case facial labels from actual inference
are normalized; unknown labels cannot resolve inherited JavaScript properties.
Regression tests failed before these corrections and pass after them.

Actual verification:

- `d953a6b13f`: 21 Java checks passed against disposable local MySQL (two
  LiveCockpitSmokeIntegrationTest cases and 19 static/speech client contracts).
  Broader browser run: 51 passed, one failed. The pre-existing expansion fixture
  called `socialContextPayload` without its now-required capture timestamp. The
  fixture now supplies/asserts that timestamp; production sensing was unchanged.
- `e6b2d213da`: six focused browser cases passed, including the corrected expansion
  fixture and all five Robot cases. Across these two runs all 52 selected browser
  cases have passing evidence, including legacy transcription and lifecycle.
- Final JS regression: **237 passed**, including 13 mapping/rig cases, in
  `target/embodiment-js-final.log`. Runner preflight: **18 passed**.
- Final five Robot browser cases passed again after label hardening; artifacts
  `target/embodiment-final`. Real WebGL renders inspected for light/dark, all three
  hand poses, narrow/maximized/detached layouts. The retained documentation preview
  is `.doc/figures/Valerian/valerian-embodiment.png`.
- `ae60ca74f1`, extended real OpenAI multimodal Coordination journey: **FAILED**
  before the browser check. Its task proposal did not meet the existing response
  requirements and no draft was accepted. The fixture then attempted to read the
  missing draft. Two provider calls / **11,049 tokens**. No agent guard, prompt or
  acceptance criterion was weakened; this is retained as an unresolved task-
  negotiation limitation, not an embodiment pass.
- `1b3bea5aa2`, separate baseline real OpenAI journey: **PASSED**, one provider call /
  **5,351 tokens**. The actual saved reply contained ACKNOWLEDGE, warmNeutral,
  toward_user gaze and energy/stillness. The real browser loaded that history,
  rendered Robot, preserved exact speech/latest event, did not animate replay,
  switched back to Details and reported zero page errors. Screenshot inspected;
  synthetic identity/introduction wording was consistent with the agent's role.
  This validates the renderer/provider/persistence path, not the failed extended
  task journey or physical robot execution. Total paid use: three calls /
  **16,400 tokens** across both trials.
- Every isolated run's manifest reports `sourceChangedDuringRun: []`.
- Configuration-free `study-release` packaging passed; archive inspection verified
  all seven robot modules/styles/license entries and exclusion of local config and
  fixture classes. Log: `target/embodiment-package-final.log`. The initial shell
  invocation failed to quote the dotted Maven property; the corrected invocation
  passed. `git diff --check` passed.

Remaining limits: authored gestures are illustrative; participant interpretation,
clinical suitability, broad device/GPU performance and physical robot behaviour
have not been evaluated. Arbitrary face/gaze/effector labels use the documented
neutral fallback; no output hand-sign vocabulary or study protocol was extended.
The richer coordination task proposal failure remains outside this view change.
