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

## Milestone 206

Shared client activity model and one interaction-card footer replace duplicate
processing/playback/context summaries. Telemetry retains compatible turn fields
and CSV, adds backend history/coverage/issue markers, Live capture settings,
cumulative usage/context utilisation and safe errors. Reduced motion stops the
spinner; live announcements change only when the label changes. Elapsed text is
not repeatedly announced. Connection and microphone control feedback remains.

Passed 47 performance/Live JavaScript tests and 50 transcription/Speech tests.
Browser acceptance used disposable local MySQL. The broad rerun passed 47 cases;
two stale empty-state text assertions were updated. The focused final run passed
8 enabled and 1 disabled case (including both repaired exports and the normal
no-behaviour fallback). All 51 distinct browser cases have passing evidence.
Final rerun: `target/gptlive-acceptance-6d6852d5b8/`, both exits 0 and cleanup confirmed. The first run
caught one stale reference to the removed Live context element (six affected
cases); it was removed before the full rerun. Light/mobile and dark/mobile footer
and Telemetry screenshots were inspected; the footer fits without overflow and
has readable contrast. Screenshots and final logs are under
`target/gptlive-acceptance-2b38b2a425/`. Physical acoustics remain unverified.
