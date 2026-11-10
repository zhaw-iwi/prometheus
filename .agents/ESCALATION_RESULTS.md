# Coordination escalation and classroom reliability evidence

## ES-01 — Bounded recovery evidence and synthetic cases (2026-11-10)

Implementation on main adds one content-free `coordination.lastFailure` value to
ordinary Storage. It records safe rejection category/operation, source position,
time, phase, run/revision and action count, explicitly marking no committed task
mutation. Provider text, exception details and user wording are excluded. The
record is applicable for 30 minutes only while task identity/progress still match;
successful control updates clear it and reset removes it. Unknown/malformed legacy
records cannot be interpreted as current failure. A failed diagnostic write does
not replace the original recovery reply or claim successful persistence.

Six synthetic classroom-derived cases cover rejected activation after preparation,
perception questions, unsupported riddle-answer promises, extra-person semantics,
unaccepted cues and bounded replacement. They contain no student transcripts or
production IDs. The existing isolated acceptance runner validates the manifest
before database access and copies it into each run's artifacts. Baseline cases are
explicitly not claimed as behavioural acceptance; later milestones implement them.

Verification:

- `mvnw.cmd -q -Dtest=CoordinationRecoveryUnitTest,CoordinationAcceptanceUnitTest,CoordinationLifecycleUnitTest,CoordinationRevisionUnitTest test`: 28 cases passed, including the final rerun after adding action-count scoping.
- `python tests/gptlive/test_acceptance_runner.py`: eight cases passed.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest`:
  eight integration cases passed against the owned loopback MySQL and a fresh
  restricted schema/account. Rejection facts survived reload with unchanged
  proposal, then cleared on accepted progress. Cleanup confirmed. Artifacts:
  ignored `target/gptlive-acceptance-4da9f295d2/` and `target/es01-db.log`.
- `git diff --check`: passed. The default DB invocation was safely rejected for
  lacking a local administration URL; it made no database changes. A first Maven
  invocation was rejected by PowerShell's comma parsing and was rerun with its
  test selector quoted.

No provider call or
production write is part of ES-01. Existing saved graphs use the updated shared
action implementation; no fresh graph or schema change is required for diagnostics.
Conversational escalation remains ES-02 work.
