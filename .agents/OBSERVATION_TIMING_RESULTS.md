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
