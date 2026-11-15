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
