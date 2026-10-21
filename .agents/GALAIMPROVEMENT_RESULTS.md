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
