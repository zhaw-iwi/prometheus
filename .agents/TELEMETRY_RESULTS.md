# Unified activity and telemetry evidence

Plan: [PLAN_TELEMETRY.md](PLAN_TELEMETRY.md). User authorized sequential commits,
pushes and final main -> agents deployment. No physical acoustic claim follows
from synthetic provider/media tests.

## Milestone 205

Completed shared activity contract, scoped SSE delivery, background cue aggregation,
model/queue/persistence/Live context stages, and provider usage/error retention.
Collection is designed to reuse existing reads and model calls; the full path
query check found one duplicate scope validation, corrected in 207 below.
Server history is transient and bounded;
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

## Milestone 207

Integrated verification complete; branch integration and rollout pending. Added a real scoped Generic activation
scenario with a held synthetic inference, overlapping serialized work, cue waiting,
provider usage, failure and reset. Test-only controls remain under the isolated
fixture classpath; no provider calls or fixture endpoints are introduced in production.
Live configuration exports include the existing segmentation limits and optional
Heroku commit identity (unknown when not supplied by the platform).

The first full Java run exposed an extra ownership/scope read in the telemetry
binding. Binding now reuses the already-resolved runtime ownership rather than
validating it twice. Existing query limits were preserved. Updated static contract
checks for the consolidated footer and the pooled-SSE consumer to dispatch past
additive activity/heartbeat frames. The final full Java rerun passed all 491 tests, including unchanged query budgets
and eight cockpit SSE/pool isolation. The broad browser run passed 50 cases; the new activation test omitted its
required transcript session parameter. After fixing that fixture request, both
enabled cases and the disabled smoke passed in `target/gptlive-acceptance-2401c670ff/`.
All 52 distinct browser cases have passing evidence. Evidence:
`target/gptlive-acceptance-7f68617481/` (Java exit 0).

Read-only production preflight: health UP, access-code login 200, catalog 84,
saved agents 25. Credentials remained local; no agent or schema was modified.
Evidence: `target/generic-rollout-telemetry207-preflight.json`.

Final feature check after adding independent inference outcomes: 15 Java tests
(provider HTTP success/failure, activity concurrency/HTTP/SSE and query volume),
2 enabled browser smokes and 1 disabled smoke passed. Evidence:
`target/gptlive-acceptance-53abbb754f/`; all exits 0, isolated cleanup confirmed.
No database schema or inference/task behavior changes are required by Telemetry.
