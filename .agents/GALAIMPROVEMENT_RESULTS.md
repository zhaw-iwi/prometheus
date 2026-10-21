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

### GI-01 application completion

Shared commits `2911f6c` and `e8141b8` were pushed through feature/gptlive and main
(main `5f1fb03`) and merged into agents. Gala policies now supply phase, revision,
validated agreed/proposed goals and silence status as quiet facts. Selected-person
secrecy guidance precedes target facts. New continuations expire after 30 seconds;
activity cleanup clears their metadata. No saved instances/history are migrated.

The maximum instructions are 12,382 bytes in both embodiments (previously 15,854).
Full maximum-length goals remain in separately bounded activity data; all person
cards fit. Source references/facts were not truncated. Activity-aware policies no
longer receive duplicate legacy task voice. Exact sentence matching remains for
GI-05; this milestone does not claim an enforced model speech/secrecy barrier.

Verification on the merged agents tree:
- 81 focused unit cases passed: LiveActivityContextUnitTest,
  LiveContextDeliveryUnitTest, LiveVoiceAcknowledgementUnitTest,
  LiveContextProjectionUnitTest, GalaDinnerReferenceUnitTest,
  GalaDinnerReceptionUnitTest, GalaDinnerTaskUnitTest, GalaDinnerLiveDeliveryUnitTest.
  Command: `./mvnw.cmd -q -Dtest=<comma-separated names above> test`.
  Log: `target/gi01-agents-unit.log`.
- 20 isolated MySQL integration cases passed, including all three canonical/compact
  Live journeys, Gala reload, query budgets and mixed-load regression:
  `python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GalaDinnerReceptionJourneySmokeIntegrationTest,GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,GalaDinnerReceptionIntegrationTest,LiveQueryBudgetIntegrationTest,QueryVolumeIntegrationTest`.
  Artifacts: `target/gptlive-acceptance-06e9530505`; disposable schema/account removed.
- `./mvnw.cmd -q -DskipTests package` passed; `target/gi01-package.log`.
- Initial checks caught the budget overrun and outdated prompt-wording assertions;
  quiet goal projection and revised contract assertions passed the final rerun.

GI-01 implementation and automated acceptance PASS. Provider/physical checks stay
pending for the later milestones/final trial. Continue with GI-02 after this push.
