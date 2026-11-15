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
