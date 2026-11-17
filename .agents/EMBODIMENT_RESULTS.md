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

## Selective promotion — 2026-11-17

The owner authorized `main` → `agents` → Heroku promotion of the visualization.
The feature branch descends from the HRI study branch, so only `556fcd5b`,
`29e3e388` and `1f68568b` were cherry-picked onto `main` (starting at `17244cb1`).
No study runtime, agent definition, schema or production setting was imported.
The acceptance runner was adapted to main without study options or fixtures;
`prometheus.build.directory` provides isolated test builds and defaults to the
existing `target` directory. The pinned upstream Three.js bytes are unchanged;
its Git attribute permits the upstream space-before-tab indentation.

Main integration verification:

- All JavaScript tests: **231 passed**, `target/embodiment-main-js-20261117.log`.
- Runner preflight: **12 passed**.
- `python tests/gptlive/run_acceptance.py --embodiment --browser --browser-mode disabled --java-tests ValerianClientStaticResourceContractTest,SpeechArchitectureBrowserClientContractTest,LiveCockpitSmokeIntegrationTest`:
  **21 Java checks and all 52 browser cases passed**. Disposable local MySQL;
  no provider use. Artifacts: `target/gptlive-acceptance-c7e4139140`.
  Source manifest unchanged during the run; owned app/schema/account removed.
- Deployment verifier syntax and `git diff --check` passed. The verifier compares
  served assets to the checked-out commit and exercises WebGL/view switching
  with a synthetic local plan; it blocks all browser writes and does not access
  production agent data or call a provider.

Baseline deployment before promotion: `agents` at `0760ea8c`, GitHub deployment
run `43179868073`, Heroku `valerian` release **v166**, health UP; no Robot view.
The `agents` push workflow owns deployment. Its catalog, creation-time
embodiment/persona controls, Dockerfile and production configuration must survive
the merge.

### Merged agents acceptance and release

Main promotion `2300d8a8` was pushed, then merged into agents as **`8baa1b70`**.
The documentation conflict retained both branches' records. The render conflict
keeps the existing stale-game-round rejection before either view receives a plan.
The runner conflict retains Gala journeys and rejects combining them with the
embodiment mode. No Java, production configuration, catalog or workflow changes
were introduced relative to the previous agents release. Main's earlier poster
documentation is also included by the normal merge.

- **232 JS checks and 13 runner preflight checks passed** on the merged tree.
  Log: `target/embodiment-agents-js-20261117.log`.
- **30 Java/local-MySQL checks and 27 browser checks passed** in
  `target/gptlive-acceptance-74813da151`. Java selection: the three main-port
  suites above plus `AgentEmbodimentUnitTest,AgentDefinitionRegistryUnitTest`.
  Browser selection: `--embodiment --browser --browser-mode disabled
  --browser-grep 'embodiment|detached windows|wider live modal|lifecycle'`.
  Includes all five Robot cases, lifecycle and column expansion coverage.
  This run started before the merge commit; its file manifest identifies the
  tested merged contents and reports no source changes during the run.
- A fresh bounded real OpenAI baseline on `8baa1b70` passed, using **one request /
  5,350 tokens**. The actual persisted reply rendered in Robot, preserved speech
  and latest event, and did not animate history. No page errors; screenshot
  inspected. Artifacts: `target/gptlive-acceptance-c3e9384aab`. This is baseline
  provider/persistence/rendering evidence, not acceptance of the previously
  rejected extended task proposal. Both runs removed owned apps/schemas/accounts.
- Packaging passed using the isolated agents build directory, with all seven
  required embodiment modules/styles/license entries present and no test fixture
  classes or HRI study assets. Log: `target/embodiment-agents-package-20261117.log`.
- Agents push deployed successfully via
  [GitHub Actions run 44028623698](https://github.com/zhaw-iwi/prometheus/actions/runs/44028623698).
  Heroku **valerian v167**, released **2026-11-17 06:06:32 UTC**, succeeded.
  [Deployed cockpit](https://valerian-e613edef27a0.herokuapp.com/valerian/).
- Deployed read-only verification passed: health **UP**, nine served asset hashes
  match `8baa1b70`, real WebGL Robot/Details switching, preserved speech,
  static replay and zero page errors or attempted writes. A synthetic client plan
  exercised the renderer without production login, agent access or provider use.
  Screenshot inspected. Artifacts: `target/embodiment-heroku-v167-verified`.
  The first verifier attempt falsely reported a CSS mismatch because Fetch's
  `text()` removed its UTF-8 BOM. Reading response bytes fixed the verifier;
  the deployed CSS itself was already byte-correct. Initial failed evidence is
  retained in `target/embodiment-heroku-v167`.

The release audit and verifier correction are documentation/test-only follow-up
changes, merged back from main with `[skip ci]`; deployed runtime remains v167.
For future release checks, run `node tests/deployment/verify-embodiment.mjs
https://valerian-e613edef27a0.herokuapp.com target/embodiment-release-check` from
the agents checkout whose runtime files match the deployed release.

To try it, connect an existing agent and select **Robot** above the Behaviour
cards. This browser view choice is independent of the creation-time
embodiment/persona selector. Speech remains above the robot and Latest Behaviour
Event below it. No new agent or database migration is required.

Remaining limits are unchanged: no physical robot, participant recognizability,
or broad device/GPU validation; production was checked with synthetic local
rendering, while live inference was checked on disposable local MySQL.
