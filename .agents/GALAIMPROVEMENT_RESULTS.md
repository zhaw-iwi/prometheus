# Gala improvement implementation evidence

Roadmap: `.agents/PLAN_GALAIMPROVEMENT.md` on the agents branch. The user authorized
consecutive milestones with a commit/push after each; final user physical trials
are grouped at the end. Synthetic tests do not establish audibility or model compliance.

## GI-01 / Milestone 227 - shared context contract (2026-10-21)

Shared changes begin on feature/gptlive. Dynamic guidance additions, replacements
and removals are delivered, including taskVoice; startup-only opening guidance is
excluded. Activity-aware task policies own their voice contract, while legacy
Generic task guidance stays unchanged. Speaking occasions may carry an expiry;
expired unsent commentary becomes a quiet cancellation, including after slow ACKs.
No schema, HTTP/SSE contract, sensor cadence or model-call change.

Passed 30 pure unit cases with:

```powershell
.\mvnw.cmd -q '-Dtest=LiveActivityContextUnitTest,LiveContextDeliveryUnitTest,LiveVoiceAcknowledgementUnitTest,LiveContextProjectionUnitTest' test
```

No Spring application, database or paid provider was started by this unit run.
Log: `target/gi01-unit.log`. Application prompt compaction, all-state budgets and
integrated checks follow on agents before GI-01 is declared complete.

## GI-02 / Milestone 228 - user-turn admission (2026-10-21)

The capture mailbox polls established acoustic evidence before a new audio chunk
or transcript fragment can reset it. The application-specific user silence hook
(default 800 ms, Gala 2000 ms) is now shared from feature/gptlive; thresholds and
late/incomplete closure rules are unchanged. A pure short-reply rule allows a
later completed native question to supersede an old announcement for activity-owned
speech. Legacy agents retain the conservative announcement rule. Latest speech
must precede the input, match the active state and belong to the current session.
No extra polling, retries, schema changes or reconstructed commands are introduced.

32 focused unit cases passed (capture, segmenter, short-reply context, scoped
session, context projection); log target/gi02-unit.log. The fixture demonstrates
three distinct turns despite a delayed scheduler, while a 1.708-second internal
pause remains joined. Other cases retain gaps/late/duplicates/incomplete limits.
The original Fabi trace contains no retained acoustic coverage, so this race is a
reproduced vulnerability, not a proven explanation of that recording. Missing
provider text cannot be recovered. Integrated admission/restart/query checks and
Gala-specific assertions follow on agents before milestone completion.

Shared integration acceptance: 10 cases passed (LiveTranscriptIngressIntegrationTest, LiveTranscriptClaimIntegrationTest, LiveQueryBudgetIntegrationTest), using run_acceptance.py with the agents application-test.properties. Artifacts: target/gptlive-acceptance-7a5f92d358; disposable schema/account removed. No production database or live provider was used.

## GI-04 / Milestone 230 - explicit first-action timing (shared foundation)

TaskSpec accepts optional firstAction ON_CUE or IMMEDIATE. Missing fields retain
IMMEDIATE; default serialization remains unchanged. ON_CUE persists explicitly,
arms with zero actions and strips any premature activation action/motion. Its
final permitted cue completes without an extra cue. Existing default counts and
Generic pause/resume behavior remain unchanged. A validated reply-rendering hook
supports application-owned agreement summaries before mutation. No schema change.

28 focused unit cases passed: TaskFirstActionUnitTest, GenericMultimodalTaskUnitTest,
TaskLifecycleUnitTest (target/gi04-unit.log). Four Generic integration cases passed,
including legacy/future timing reload, session recovery and Live task journey:
run_acceptance.py with agents application-test.properties and
--java-tests GenericMultimodalTaskIntegrationTest. Final artifacts:
target/gptlive-acceptance-14efdf36cf; disposable schema/account removed. Initial
new-test failures were a test-fixture mistake: getStorage returns a map copy;
the corrected test updates the owning TaskPolicy storage inside a transaction.
Gala agreement rendering, application checks and provider review follow on agents.

GI-04 compatibility correction: the Generic prompt fingerprint gate caught the
shared schema-declaration wording change. A ConfigurationContract extension now
allows an application-specific task-field declaration while restoring Generic's
original prompt byte for byte (SHA-256 1fdf7e9f17da9b1cc1c5c5b43692e84c5d97c103f46ecfb1d37968c3de1d30c8).
29 focused units passed after this correction; target/gi04-prompt-compat.log.

## GI-05 / Milestone 231 - bounded task response association (shared foundation)

The user approved one semantic check per pending task response on 2026-10-22.
TaskResponse stores one occasion/session/epoch/revision with a 45-second deadline.
Native recording claims a completed, fresh, nontrivial candidate atomically with
its durable record. Existing bounded background-action workers run the check
after commit without holding an entity, JDBC transaction or agent lock. Output
identities and verdict are validated; uncertainty never releases another action.
Completion rechecks ownership/current response and requires fresh post-check cues.
Duplicate/reloaded claims do not retry. Context refresh reuses its existing
deadline scheduler for expiry; no extra poller, schema or transcript endpoint.
Legacy tasks without the opt-in response state retain their wording-based gate.

Focused shared units passed (target/gi05-shared-unit.log), including the new
TaskResponseUnitTest and existing task/Live contracts. Ten isolated integration
cases passed: LiveSpeechPersistenceIntegrationTest, GenericMultimodalTaskIntegrationTest,
LiveQueryBudgetIntegrationTest; target/gptlive-acceptance-0c331985c5. Tests verify
one durable check, duplicate native recording, paraphrase association, no further
action, unlocked inference and obsolete-result rejection. The first run exposed
an executor result-key mismatch; the declaration was corrected and the affected
integration set passed. Disposable schema/account removed. Gala opt-in, provider
classification/latency review and full milestone acceptance remain in progress.
