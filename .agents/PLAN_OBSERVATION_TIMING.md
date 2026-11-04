# Observation timing: OT-01 through OT-06

Authorized 2026-11-04: implement consecutively on main, verify and commit/push
each milestone before continuing. Halt for unforeseen material decisions.
The branch starts clean at 6d44a58. Deployment and physical acceptance are
separate from this shared implementation; no production test writes are needed.

Status: OT-01 through OT-06 completed with automated evidence in
OBSERVATION_TIMING_RESULTS.md. Authorized agents merge `b7ce3a2` is deployed as
Heroku v161; health/clock/assets passed readiness checks. Physical acceptance
remains open.

## Contract

The November 4 scissors trial persisted valid hand observations approximately
2.64 seconds ahead of Heroku. The two-second task tolerance rejected them;
no joke executed. A sanitized fixture records representative timing and the
rule, without session identifiers or private transcripts. Its capture instant
is a controlled reconstruction, not a measurement of the physical video frame.

- Public camera payloads retain `ts`, expressed on the server clock. Capture it
  at detector observation, before asynchronous emission. Related social events
  share it. Retries and recalibration never restamp an old observation.
- A public, no-store `GET /runtime/clock` exposes only `serverTimeMs`, requires
  no database or model access, and grants no agent permissions.
- The browser measures request/response with a monotonic clock. At receipt,
  server time is at least the sampled server timestamp; use this conservative
  lower bound, not a midpoint that could date a sample into the future.
- Maximum accepted round trip: 1000 ms. Request timeout: 2000 ms. Calibration
  lifetime: 300000 ms; refresh after 240000 ms. A 1 ms rounding margin plus
  100 ppm drift allowance makes inferred capture times older, never newer.
  These are explicit operating bounds, not proof of arbitrary clock stability.
- Resample on connection and periodically while connected. Hidden/pagehide,
  reconnect/scope change, monotonic rollback or a wall/monotonic discrepancy
  over 1000 ms invalidate the mapping; returning visible reacquires it.
  In-flight old-scope calibration responses cannot restore invalidated state.
- Failed refresh may retain an unexpired valid calibration; expired or
  invalidated timing blocks new camera emissions with a visible reason.
  Capture/Stop/local presence handling continues. No stale observation queue.
- Preserve provider weather times, existing source TTLs, external client
  contracts and saved history. Legacy future tolerances remain explicit per
  consumer; this work does not reinterpret old samples or widen tolerances.
- Share freshness classification (fresh/expired/future/unknown) in
  ObservationEvidence and expose bounded, content-free timing diagnostics.

## Milestones and gates

1. **OT-01: contract and baseline.** Sanitized fixture and deterministic Java
   characterization of the recorded failure, correction and TTL boundaries.
2. **OT-02: calibration.** No-database HTTP endpoint and independently clocked
   browser unit tests: offsets in either direction, asymmetric delay, bounds,
   malformed data, expiry, drift and invalidated requests.
3. **OT-03: capture and lifecycle.** Shared sensor stamping and scope lifecycle;
   actual-emitter tests for hand/face/social, delayed submissions, batches,
   reconnect/resume and unavailable timing. Weather retains source dates.
4. **OT-04: evidence and diagnostics.** Shared classification for tasks and Live,
   reason-specific telemetry; units protect TTLs, legacy behavior and privacy.
5. **OT-05: Coordination boundaries.** Zero-action acceptance, pre-acceptance
   rejection, one fresh scissors action, duplicate suppression, final playback
   gating and ordinary Text behavior with deterministic providers.
6. **OT-06: integration and handoff.** Offset browser journey, disposable-MySQL
   persistence/reload, proportional shared regression, documentation and physical
   test procedure. Simulated delivery is not proof of physical audibility.

Every milestone updates OBSERVATION_TIMING_RESULTS.md and PROJECT.md with actual
commands, outcomes and limitations. Rejected model updates and the existing
Stop/graph-label discrepancy are separate work. No private trial dumps or
credentials enter commits. Tests never use the configured production database.
