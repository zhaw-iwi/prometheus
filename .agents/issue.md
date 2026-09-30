# Capability-awareness handoff

Status: CA-01 through CA-03 pushed to main; CA-04 acceptance under investigation.

2026-09-30 browser acceptance: all 463 Java cases and 67 Node cases passed. The
new real-app capability assertions and eight-context classroom smoke passed.
The full Live-enabled browser run had 32 passes and 13 failures in existing UI
checks (accordion visibility, empty-input send buttons, selected speech device,
PCM/timing expectations and lifecycle state). Production browser code has not
changed in this feature. Investigating fixture/environment versus baseline issues
before marking CA-04 complete or deploying. The runner stopped its owned app and
removed its disposable database/account; Live-disabled browser checks were not
reached in that run.

Local evidence: target/gptlive-acceptance-7b9b093169/browser-true.log and traces.
This machine's target artifacts are not committed. The repeatable runner and tests
are in the repository; use a disposable localhost database, never deployment DB.

Unexpected items resolved:
- Capability assembler wrapping initially disabled guard batching because an
  optimization checked exact class identity. Delegated guard eligibility now
  preserves the original contract; all 45 focused cases passed, including counts.
- Local properties target deployment MySQL, and PyMySQL was absent. Installed the
  test dependency and started a separate loopback MySQL fixture; no deployment
  data was changed. The normal acceptance runner provisions disposable schemas.

Continue with CA-04 and branch integration as described in
PLAN_CAPABILITY_AWARENESS.md. Actual verification is recorded in
CAPABILITY_AWARENESS_RESULTS.md. Commits are pushed to main after each milestone.
