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
