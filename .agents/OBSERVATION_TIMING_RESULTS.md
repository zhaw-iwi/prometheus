# Observation timing implementation and evidence

Plan: PLAN_OBSERVATION_TIMING.md. Started 2026-11-04 on main at 6d44a58.
The user authorized sequential implementation and a commit/push per milestone.

## OT-01: contract and baseline

Defined capture-time preservation, bounded calibration, scope invalidation,
legacy compatibility and acceptance gates. Added a sanitized scissors fixture
and two pure Java tests characterizing future rejection, corrected eligibility,
expiry and exact boundaries. The reconstructed capture time is test data;
the source/receipt mismatch follows the recorded trial. No runtime behavior
changes in this milestone. Passed `mvnw.cmd -q -Dtest=ObservationTimingUnitTest
test`: two pure Java tests, zero failures/errors. No database or provider access.
`git diff --check` passed. Runtime repair remains OT-02 through OT-05.

## OT-02: bounded calibration

Added public no-store `/runtime/clock` with no injected application/database
services, and an isolated browser clock module. Capture uses a conservative
server-time bound, with measured RTT, explicit expiry/drift limits, timeout,
wall/monotonic discontinuity handling and old-request fencing. Failed refresh
does not renew a still-valid sample. Sensor integration follows in OT-03.

Passed seven Node tests (`node --test tests/js/live/observation-clock.test.mjs`)
and three Java tests (`mvnw.cmd -q
-Dtest=ObservationTimingUnitTest,RuntimeClockControllerWebMvcTest test`). Java
uses fixed clocks and standalone MVC; no database/provider access. Tests include
asymmetric delay, positive/negative wall offsets, every validity boundary,
timeout despite ignored abort, bad responses and superseded requests.

## OT-03: capture and lifecycle

The Cockpit starts/stops calibration with its agent connection and invalidates
timing while hidden. Periodic refresh/retry is bounded and sends no database
queries. Detector input is timestamped before asynchronous inference; a reading
from an obsolete connection cannot be emitted. Hand/face/social manual paths
share the same helper. Social batches share a capture time while their local
cadence uses local elapsed wall time, not calibrated payload dates. Weather is
unchanged. Unavailable timing is visible beside emission controls and prevents
sending; local presence handling remains active.

Passed all 88 Live Node tests (`node --test tests/js/live/*.test.mjs`), syntax
checking and 15 Java checks (`ValerianClientStaticResourceContractTest` and
`RuntimeClockControllerWebMvcTest`). Added delayed-detector, delayed-submit,
batch, disconnect/visibility, expiry/recovery and stale-request tests. The first
Java run exposed the expected changed helper-signature assertion and a pre-existing
`States` label assertion left behind by the state-graph work. Updated that test
to the existing `State names` UI; no graph behavior changed. The corrected run
passed. No database/provider or physical trial in this milestone.

## OT-04: evidence and diagnostics

ObservationEvidence now classifies fresh/expired/future/unknown source age.
Tasks and Live reuse that classification with their existing two-second/zero
legacy future allowances, preserving TTL and source selection. Task diagnostics
distinguish future, expired and missing timestamps; Live explains future evidence
without showing its unusable value. Telemetry exports a content-free clock journal
bounded to 32 first/latest records with explicit loss and defensive copies.

Passed 41 Java tests across ObservationTimingUnitTest, GenericMultimodalTaskUnitTest,
LiveContextProjectionUnitTest and AgentActivityServiceUnitTest. Passed 118 Node
tests across Live and performance suites. New tests cover consumer agreement on
calibrated timestamps, exact expiry, reason propagation, privacy and journal
bounds/clear. An initial Node expectation contained an encoding-corrupted dash
from the editing shell; corrected the fixture and reran successfully. No database,
model call, physical test or TTL/tolerance widening was needed.

## OT-05: Coordination boundaries

Added a fixed-clock journey using the sanitized scissors fixture and existing
Coordination runtime helpers. Both Text and Live start with zero actions, reject
a pre-acceptance capture delivered later and the original future timestamp,
then execute one correctly dated scissors contribution. Duplicate and later cues
cannot repeat the exhausted action. Live additionally rejects cues before arming
speech association and waits for associated final speech plus the matching playback
acknowledgement before reflection. Text still publishes the canonical spoken plan.
No production control behavior was changed.

Passed 45 pure Java tests across CoordinationObservationTimingUnitTest,
CoordinationAgentUnitTest, CoordinationAcceptanceUnitTest,
CoordinationDeliveryUnitTest, CoordinationCaptureUnitTest and
CoordinationVoiceUnitTest. Reran the two new cases after adding a duplicate cue
while finishing; both passed. Clocks and inference are deterministic; response
association verdicts/playback acknowledgements are simulated. This verifies control
boundaries, not physical audibility or real-provider wording. Database and browser
journeys follow in OT-06.

## OT-06: integration, regressions and handoff

Added Chromium journeys with browser wall time one minute ahead and behind the
server. They use the actual `/runtime/clock`, production detector callback,
connection lifecycle and JSON export; detector results and agent ingress are
scripted. Both show calibrated scissors timestamps no later than receipt, reject
captures from a prior connection and export bounded clock diagnostics without
requiring a conversation turn. The persistence test reloads raw/corrected trials
from disposable MySQL, preserves their payloads/source IDs and verifies task/Live
agreement on capture age and exact expiry. No historical timestamps are rewritten.

Browser acceptance caught two implementation issues: native timer functions
needed their window receiver, and JSON export needed enabling when only clock
diagnostics existed. Both are fixed and protected by the real-browser journeys.
Clock status changes also refresh the telemetry panel. The existing stable-sensor
cadence fixture now advances wall and monotonic time together: changing Date alone
correctly invalidates the new calibration. Physical instructions in
`.reng/HUMAN_TEST.md` now require timing readiness and identify the new evidence.

Verification performed:

- 13 Java checks passed in disposable MySQL artifact `109d97ca4a`:
  `CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest,RuntimeClockControllerWebMvcTest`
  through `python tests/gptlive/run_acceptance.py --database-properties
  target/coordination-mysql/admin.properties --java-tests <selection>`. The
  integration tests use scripted inference, including real response association
  and persistence boundaries; no paid provider calls.
- 68 pure Java checks passed with `mvnw.cmd -q
  -Dtest=ObservationTimingUnitTest,GenericMultimodalTaskUnitTest,TaskResponseUnitTest,LiveContextProjectionUnitTest,CoreRpsRoundUnitTest,ValerianClientStaticResourceContractTest
  test`.
- All 172 Node checks passed with `node --test tests/js/live/*.test.mjs
  tests/js/performance/*.test.mjs tests/js/speech/*.test.mjs
  tests/js/transcription/*.test.mjs tests/js/state-graph/*.test.mjs` after the final
  production changes.
- The isolated runner with `--java-tests none --browser --browser-mode enabled
  --browser-grep 'observation clock'` passed both offset journeys in `8ad455e3cc`.
- Static browser regression passed 18 checks (six unchanged graph screenshots
  plus lifecycle/weather/popup behavior), with the two real-clock journeys
  intentionally skipped: `python tests/playwright/run_static.py
  tests/playwright/valerian-state-graph.spec.mjs tests/playwright/valerian-state-view.spec.mjs
  tests/playwright/valerian-lifecycle.spec.mjs --grep-invert 'persisted transition'`.
- Broader isolated browser run `bbc85ec621` passed eight checks: Coordination
  delivery, durable Live/scoped RPS, multimodal output, Generic task activity,
  Social Space reception, presence reception and two Continuous transcription
  journeys. The stable-camera test failed because its old Date-only jumps
  invalidate calibration; after correction, `python tests/playwright/run_static.py
  tests/playwright/valerian-gptlive.spec.mjs --grep 'stable camera readings'`
  passed. The broader selection was `--browser-mode both --browser-grep
  'real scoped cockpit|Generic activation|Live Multimodal|Social Space opt-in|stable camera readings|presence reception preserves|mocked WebRTC emits|manual turn commits|Coordination finishing'`;
  the initial enabled failure prevented its disabled phase, which is checked
  separately below.
- The Live-disabled real scoped speech journey passed separately in `4b3eb67afb`
  with `--java-tests none --browser --browser-mode disabled --live-only
  --browser-grep 'real scoped cockpit'`. Across these focused runs, 30 browser
  checks passed; two real-clock cases were deliberately skipped in static mode.
- `mvnw.cmd -q -DskipTests package`, JavaScript syntax checks and
  `git diff --check` passed.

Earlier attempts are retained locally for diagnosis. The default database config
was absent, so the runner refused to start; the ignored existing loopback-only
configuration was then used. `100a97a4ee` exposed a test assumption: combining raw
future and corrected trials in one history makes the existing newest-source
selection prefer the raw timestamp once its date becomes valid. The corrected
test uses separate trials and leaves that legacy selection unchanged.
`109d97ca4a` then failed its browser phase at the unbound native timers;
`6916d9de70` passed delivery/hand reporting but exposed the clock-only export gate.
The corrected offset run passed both cases.

All owned app processes and disposable schemas/accounts were cleaned; zero test
schemas/accounts remained before stopping the owned loopback MySQL instance.
OT-01 through OT-06 are complete. Each milestone was committed and pushed to main
under the user's sequential authorization.

At OT-06 completion, deployment and the bounded human scissors run remained open.
No Heroku deployment, branch promotion, production test writes or physical
audio/device test was performed within those implementation milestones. Real-provider wording,
camera recognition accuracy, other browsers/devices and the existing database
quota constraint remain outside this automated evidence. Rejected task updates
and the existing Stop/graph discrepancy are unchanged separate issues.

## Authorized deployment and readiness (2026-11-04)

The owner then authorized merging into agents and triggering redeployment.
Merge `b7ce3a2` integrates main `335e3d3` onto agents `fd89437` without conflicts,
preserving the deployment catalog, embodiment controls, configuration and workflow.
Merged-tree checks passed 148 pure Java tests (clock/freshness, Coordination
delivery, Live projection, static contracts and Gala sensing/tasks/hand/reception),
173 Node tests and three browser journeys (connection lifecycle, actual hand
emission and stable Live camera refresh). Packaging and whitespace checks passed.
The integration record is also included in the agents merge.

GitHub [deployment run 41841418570](https://github.com/zhaw-iwi/prometheus/actions/runs/41841418570)
succeeded for that exact merge. Heroku release **v161**, created at
2026-11-04 13:49:25 UTC, succeeded; `web.1` was up at 13:50:02 UTC.
Read-only HTTP checks at approximately 13:50 UTC verified:

- `/actuator/health`: HTTP 200, `UP`;
- `/runtime/clock`: HTTP 200, current integer `serverTimeMs`, `Cache-Control: no-store`;
- seven deployed assets exactly matched the agents checkout after newline
  normalization: Cockpit index/script, the three observation timing modules and
  performance panel/activity modules.

The deployed Cockpit is ready for `.reng/HUMAN_TEST.md`: hard-refresh the page,
create a new Coordination Agent with Cockpit embodiment, connect and wait for
**Sensor timing ready**. The physical scissors/audio trial remains unverified.
No production task/test writes or paid provider calls were used for these release
checks. Local HTTP evidence is in ignored `target/observation-release-evidence.json`.
