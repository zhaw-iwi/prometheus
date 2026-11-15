# Study implementation evidence

This records software verification, not human-study findings. Local artifacts
under `target/` are ignored and contain synthetic data. Each milestone is committed
and pushed separately on `experiment/hri-2027-coordination`, as authorized by the
owner on 15 November 2026. Source base: `17244cb1`.

## OS-01 — Foundation (15 November 2026)

Implemented versioned English task/form/rubric materials for `guess`, `context`
and `signs`, all explicitly unready; a neutral `study.coordination` definition
using the existing control graph; a closed text-only landing page; and study
selection/evidence checks in the disposable local MySQL acceptance runner.
No schema changes. Fresh agents are needed to use the new definition. Pilot/main
attempt designation and persistence are OS-02 work; human collection remains closed.

Actual verification:

| Command/check | Result and artifact |
| --- | --- |
| `python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py` | 13 passed, including study selection and remote-DB rejection |
| `python tests/gptlive/run_acceptance.py --study --java-tests StudyMaterialsTest,StudyDefinitionIntegrationTest,CoordinationAgentUnitTest,CoordinationRevisionUnitTest,AgentDefinitionRegistryUnitTest --browser` | 37 Java checks passed, zero skips; initial browser run failed because nested static route returned 404. Retained in `target/gptlive-acceptance-c6653bc41e` |
| `python tests/gptlive/run_acceptance.py --study --java-tests none --browser` | After adding the route, one browser check passed, zero skips. Screenshots at 390 and 1440 pixels visually reviewed: readable, no overflow. `target/gptlive-acceptance-47a14c1e88` |
| `python tests/gptlive/run_acceptance.py --study --java-tests none --paid-core coordination-text` | Three-turn real OpenAI journey passed against disposable local MySQL; `target/gptlive-acceptance-754156fee1` |
| `node --check tests/hri/provider_journey.mjs`, `git diff --check` | Passed |

Independent semantic review of the real-provider transcript: **PASS for the
foundation smoke only**. The first turn preserved roles, success criterion and
limit two without execution; acceptance produced one welcome sentence and a
stored agreement; pause preserved that agreement and contribution count. Success
was correctly not established. This is one synthetic journey, not a reliability
estimate or scenario acceptance. The raw journey intentionally retains its
`semanticReview: PENDING` field; this paragraph is the separate review evidence.

Known observations for later milestones: canonical replies can contain unused
nonverbal fields although the study client renders text only; pause wording still
exposes runtime terminology. Participant sessions, durable submissions, recorder,
task execution, forms and operational qualification are not yet implemented.

## OS-02 — Sessions and access (15 November 2026)

Implemented persisted SYNTHETIC/PILOT/MAIN attempt identity, stable assignment,
consent, staged private forms, hashed participant credentials, atomic agent
provisioning, withdrawal/revocation/replacement, and operator-only provisioning.
Study-owned agents are hidden from legacy lists and blocked on direct legacy
routes. The two new tables are additive. Human collection remains disabled.

Actual verification:

- Initial selected Java run in `target/gptlive-acceptance-96fe388a3d` failed:
  the restart guard used the wrong schema prefix, a test attempted Gson reflection
  on Instant, and MVC slices lacked the new repository dependency. Failed XML/logs
  are retained. Six session tests and 13 baseline scoped tests passed in that run.
- After fixes,
  `python tests/gptlive/run_acceptance.py --study --java-tests StudySessionsIntegrationTest,StudyRestartIntegrationTest,ScopedDemoControllerIntegrationTest,*WebMvcTest`
  passed **76 checks, zero failures/errors/skips**, in
  `target/gptlive-acceptance-429d785256`. This includes actual independent
  application-context shutdown/restart with stable assignment, forms and agent
  identity; simultaneous provisioning; rollback after an injected post-save
  failure; private-answer separation; closed/gated sessions; and legacy endpoint
  isolation. The owned MySQL schema/account were removed after each run.
- No inference code changed in this milestone; provisioning is deterministic and
  tests assert no model calls. The OS-01 real-provider evidence remains scoped to
  that foundation journey. Full participant turn handling follows in OS-03.

Remaining limits: no participant text client or export yet; operator-selected
assignment precedes balanced allocation in OS-09; retention policy and human
information remain unresolved, so no human collection is enabled.
Runner preflight was rerun: 13 passed. `git diff --check` also passed.

## OS-03 — Durable text ingress (15 November 2026)

Added request receipts, canonical event IDs/results, replay and cursor checks,
pending/uncertain reconciliation, explicit submission cancellation and late-commit
fencing. A small shared AgentApplicationService hook permits an application-owned
transaction after inference; task authority stays in the existing agent. Monitor
publication follows commit. The study conversation projection contains committed
agreements and receipt-linked turns, excluding private forms.

Actual verification:

| Run | Evidence |
| --- | --- |
| `--study --java-tests StudyTurnsIntegrationTest,StudyRestartIntegrationTest,StudySessionsIntegrationTest,CoordinationAgentUnitTest` | Initial run `c146675b24`: 29 passed, one rollback-test spy setup error. After correction, `d4cb769fc4`: **30 passed**, no skips, including provider-outside-transaction assertion, concurrent retry/ordering, injected rollback, post-commit response loss, cancellation/revocation during inference, expired receipts and real application restart |
| `--study --java-tests StudyTurnsIntegrationTest,AgentApplicationServiceUnitTest --paid-core coordination-text` | `0589524542`: **six study checks passed** (the additional named class does not exist; service coverage is the separate run below). Real OpenAI three-turn session path passed receipt/state assertions, including repeated IDs. Two provider calls, 7,488 cumulative tokens recorded |
| `--study --java-tests StudySessionsIntegrationTest,AgentApplicationService*UnitTest,AgentTurnSerialiserUnitTest,LiveTranscriptIngressIntegrationTest,LiveTranscriptClaimIntegrationTest` | Mixed-fixture run `6bac9f8f51` exposed existing aggregate-history JSON serialization failure for an unrelated EventSelector policy. Adjusted the list-isolation assertion to the controller boundary, retaining HTTP denial and ordinary-list checks. Rerun `96091b165d`: **28 passed**, zero failures/errors/skips |

Commands above use `python tests/gptlive/run_acceptance.py`; artifact directories
are `target/gptlive-acceptance-<run>`. Failed logs/XML are retained. Disposable
MySQL schemas/accounts were cleaned up by the runner.

Independent semantic review of `0589524542`: task-control and durable-replay
criteria **PASS**; strict wording fidelity **FAIL**. Proposal did not execute;
acceptance preserved roles, success and limit two; one contribution was counted;
pause preserved the accepted terms. However, the requested single welcome sentence
was rendered as “Welcome! We’re glad to have you here.”, two sentences. This
non-deterministic wording deviation is retained for later semantic qualification,
not hidden by a successful HTTP/JSON result. Raw `semanticReview: PENDING` remains
unchanged; this paragraph records the independent assessment. No human-study
finding or general instruction-fidelity claim follows from this smoke.

Limitations: one study application process, scheduled ticks disabled; no claim of
exactly-once provider billing or client display. Full immutable research snapshots
and export are OS-04; participant UI and all three task executors remain pending.

Final targeted rerun after adding rollback-publication coverage:
`--study --java-tests StudyTurnsIntegrationTest`, artifact `1eb58f1427`, **six
passed**, including no monitor publication for a rolled-back mutation. Runner
preflight: 13 passed. Node syntax and `git diff --check` passed.

## OS-04 — Research recorder and export (15 November 2026)

Added append-only `study_record` evidence at procedural/agent commit boundaries,
immutable acceptance snapshots, version/configuration metadata, full canonical
event coverage, separate client rendering reports and private-answer export rows.
Operator-only JSON export compares recordings against canonical state and reports
missing records, uncertainty and procedure/display completeness independently.

Actual verification:

- `--study --java-tests StudySessionsIntegrationTest,StudyTurnsIntegrationTest`:
  **14 passed**, artifact `3433a81b0a`, after inserting recorder boundaries.
- `--study --java-tests StudyRecorderIntegrationTest,StudyRestartIntegrationTest`:
  **six passed**, artifact `c7b973524b`. Tests retain exact initial/revised
  agreements, exceed 12 journal entries/eight outcomes, inject post-insert recorder
  failure, check display scope/deduplication and detect missing evidence.
- First complete-study run `83e1fbc365` had **23 passes and one failed assertion**:
  OS-03's broad “no broadcaster calls” check raced with scheduled activity metrics.
  Narrowed it to agent-state publication; metadata activity is a separate channel.
  Failure XML/logs retained; no provider trial was reached in that run.
- `python tests/gptlive/run_acceptance.py --study --paid-core coordination-text`:
  **24 Java checks passed**, zero failures/errors/skips; real OpenAI journey/export
  also passed. Artifact `target/gptlive-acceptance-4325eed8f0`. Two provider calls,
  7,449 cumulative tokens. Owned MySQL schema/account cleaned up.

Independent semantic review of `4325eed8f0`: **PASS for this bounded smoke**.
The proposal preserved roles/success/limit without executing; explicit acceptance
produced one short welcome sentence and one counted contribution; pause preserved
the accepted terms. Export retained exactly that agreement with source/run/revision,
three committed requests and four idempotent synthetic display reports (including
the starter). The export correctly reported procedure incomplete and excluded the
participant token. Display evidence here was supplied by the test script, not an
actual browser or proof of reading. The OS-03 wording deviation remains part of
the evidence; this successful run does not establish a reliability rate.

No human data collected. Source metadata records the tested base commit with a
dirty-checkout flag. Research materials remain draft and collection closed.
Participant UI, full instruments, task executors and operational qualification
remain subsequent work.
Final runner preflight: 13 passed. Node syntax and `git diff --check` passed.

## OS-05 — Resumable text interface (15 November 2026)

Added invitation entry, staged private forms, canonical conversation/term review,
native SSE reconciliation, refresh-safe drafts/requests, help/finish/withdraw and
mobile layouts. Finish durably enters assessment without asserting task success.
Cancellation before request arrival now creates a persisted rejection tombstone.
Receipt timestamps use database-compatible microsecond precision. Study control
wording is concise while baseline controls and agreement authority are preserved.

Actual verification (runner: `python tests/gptlive/run_acceptance.py`):

| Run/artifact suffix | Result |
| --- | --- |
| `62f87bd03f`, targeted Java + initial browser | 18 Java and one initial browser check passed |
| `5cfb24b6ab`, expanded browser | Seven passed, reconnect failed: current SSE cursor delayed response headers until heartbeat. Added immediate connection comment |
| `325ba009be`, browser rerun | Seven passed, offline error text failed: raw fetch error overwrote the friendly message. Fixed shared error presentation |
| `91938ac843`, browser | All eight passed; visual review prompted mobile collapsing panels and removal of transient smooth-scroll snapshots |
| `1ed3160c81`, all study Java | 25 passed, one cancellation replay assertion failed on nanosecond/microsecond timestamp round-trip. Normalized receipt times |
| `34ed98e80f`, `--study --browser` | **26 Java and eight browser checks passed**, zero failures/errors/skips/retries |
| `e2ed7ad847`, session/turn/shared Coordination and revision Java + browser | **43 Java and eight browser checks passed**, including final participant Pause wording |
| `8b234132c0`, paid text foundation | Three committed/replayed turns and export passed; two provider calls, 7,483 cumulative tokens |
| `ee485f0a6f`, final material checks + paid text foundation | **Three Java checks passed**; three committed/replayed turns and export passed; two provider calls, 7,401 cumulative tokens |

Artifacts are under ignored `target/gptlive-acceptance-<suffix>`. Disposable local
MySQL schemas/accounts were cleaned up. An attempted combined browser/paid command
was rejected at preflight before DB/API use, as intended; runs were separated.
Runner preflight: **13 passed**. JavaScript syntax and `git diff --check` passed.

Browser evidence covers preparation, acceptance, pause, refresh, lost HTTP reply,
429/503/400 recovery, held inference/withdrawal fencing, offline reconnect and long
untrusted text. It asserts no media/Speech/Live traffic, canonical single rendering,
keyboard controls and preserved drafts. Screenshots inspected at 1440px and 390px:
proposal, agreement, long-message, pending, error and terminal views. Final mobile
agreement, error-429, pending desktop and finished mobile were inspected in
`34ed98e80f`; the final run additionally asserts the concise pause text.

Independent semantic review: `8b234132c0` passes proposal/acceptance/pause and
recording criteria but **fails strict one-sentence fidelity** (“Welcome! We're glad
you're here.”). Final `ee485f0a6f` **passes this bounded text smoke**, with “Welcome!”,
unchanged roles/success/limit, one contribution and a truthful paused response.
Both exports retain one exact agreement, three requests, four synthetic display
reports and explicit incomplete procedure. Nonverbal hints can remain in canonical
behavior plans but are not rendered or executed by this text interface. Display
reports in paid trials are script-generated, unlike the separate browser checks.
Raw semantic-review fields remain PENDING; this paragraph is the independent review.
Retained failures preclude treating the passing trial as a reliability estimate.

No human data collected. Assessment remains a procedural placeholder; all three
task executors and later operational qualification remain pending. Human collection
is disabled and scenarios remain unready.
