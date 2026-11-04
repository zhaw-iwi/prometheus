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
