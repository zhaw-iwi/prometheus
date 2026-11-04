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
