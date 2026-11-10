# Coordination escalation and classroom reliability evidence

## ES-02 — Shared state-aware support (2026-11-10)

All nine phases handle direct help/confusion requests through the shared action;
other phrasings can use SUPPORT in the existing interpretation request, including
saved configurators via runtime guidance. The reply is derived from current state,
remaining preparation and applicable rejection facts, not generated claims of
state changes. Help consumes no contribution and changes no task authority.
Explicit existing pause/resume/stop controls remain available.

Live support preserves pending response/occasion IDs and capture/playback deadlines.
During a pending contribution it adds bounded quiet explanation facts without a
new speaking permission; once no contribution is pending, it uses an ordinary
non-task user-response occasion. It is not an acoustic gate or a claim that the
pending response has finished. Reset, next user turn and new Live conversation
fence old help. Control-only background keeps explanation context bounded without
replacing the current task contribution's purpose. No new inference/polling or
schema/saved-graph migration is introduced.

Verification:

- Final focused Java run: 51 cases passed across CoordinationSupportUnitTest,
  CoordinationRecoveryUnitTest, CoordinationAcceptanceUnitTest,
  CoordinationDeliveryUnitTest, CoordinationVoiceUnitTest and
  CoordinationLifecycleUnitTest. Includes nine-phase authority preservation,
  partial preparation, pending Live identity/deadline preservation, expired help,
  session replacement and error fallback. One initial test incorrectly inspected
  the consumed reply-storage slot; corrected to inspect the actual response event.
- `python target/run_coordination_mysql.py CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest`:
  15 cases passed; help survives in history across reload, preserves terms/budget,
  and existing pause/resume and delivery paths pass. Owned schema/account/server
  cleanup confirmed; artifacts `target/gptlive-acceptance-29696e4bf9/`.
- `python tests/playwright/run_static.py tests/playwright/valerian-lifecycle.spec.mjs --grep 'Coordination recovery guidance'`:
  two cases passed, desktop/light and narrow/dark; wrapping and disconnect clearing
  checked. Both screenshots visually inspected. Initial screenshots exposed an
  open test-fixture Diagnostics drawer; the fixture now dismisses it before capture.
- Real OpenAI Live behavioural acceptance remains ES-08; synthetic browser/media
  and unit checks do not establish physical audibility or natural-language quality.

No production writes or provider calls. No additional task engine or state graph.

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
