# Unified activity and telemetry evidence

Plan: [PLAN_TELEMETRY.md](PLAN_TELEMETRY.md). User authorized sequential commits,
pushes and final main -> agents deployment. No physical acoustic claim follows
from synthetic provider/media tests.

## Milestone 205

Completed shared activity contract, scoped SSE delivery, background cue aggregation,
model/queue/persistence/Live context stages, and provider usage/error retention.
No new database reads or model calls. Server history is transient and bounded;
terminal failures omit exception messages. Context ACKs never imply playback.

Passed 37 Java tests using the disposable local MySQL runner:
`AgentActivityServiceUnitTest,AgentActivityIntegrationTest,LiveQueryBudgetIntegrationTest,GenericMultimodalTaskUnitTest,ScopedLiveSessionServiceUnitTest,LiveContextDeliveryUnitTest`.
Evidence: `target/gptlive-acceptance-59552a7c8a/java.log`. Earlier runs caught and
fixed a static lease configuration reference and an integration fixture which
acknowledged without requesting generation. Final run exit 0; schema/user removed.
The stream test verifies scoped access, Thinking before HTTP completion, content
exclusion and terminal cleanup. Unit tests cover overlap, monotonic durations,
reset fencing, ring limits, repeated cue aggregation, safe cumulative usage.
Browser rendering and physical acoustic behavior are not established by this run.
