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

Integrated verification, branch integration and rollout complete. Added a real scoped Generic activation
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

## Branch integration and production verification

Each milestone was committed and pushed on feature/gptlive: `f5e0e70` (205),
`8e84317` (206), `87c253b` (207). Main fast-forwarded to `87c253b`; the conflict-free
agents merge `c1de3a0` preserves deployment personas, embodiment-aware runtime
resolution and the existing Heroku workflow.

Merged agents passed **628 Java tests and 21 browser cases** with disposable
local MySQL and synthetic providers/media. Evidence:
`target/gptlive-acceptance-deb90959aa/`. The first merged full run exhausted the
local MySQL connection limit because many cached Spring test contexts each retained
a pool (34 context-load errors, no assertion failures). The passing rerun used
`JAVA_TOOL_OPTIONS=-Dspring.test.context.cache.maxSize=4` only in the acceptance
process; it restored the previous environment afterwards. Application pool settings,
query ceilings and production configuration were unchanged. All owned schemas,
accounts and applications were removed/stopped by the acceptance runner.

[Deployment workflow](https://github.com/zhaw-iwi/prometheus/actions/runs/37109995197)
completed successfully for `c1de3a0`. Heroku **v106** was released at
2026-10-03T08:32:40Z; web.1 is up. Verification:

- Health 200/UP, Valerian 200, access-code login 200 and catalog 84.
- Saved-agent count is still 25; no schema or agent data mutations were used for
  rollout checks.
- All seven changed HTML/JavaScript/CSS resources match the deployed agents tree.
- A real, authorized production monitor connection delivered the version-1
  activity contract with the correct scope. No inference or voice session was
  started for production verification.
- The 921-line release log sample confirms startup and contains no ERROR, R14,
  R15, H10, database-quota, schema or startup-failure markers. This is a bounded
  observation, not a sustained-load guarantee.

Sanitized local evidence: `target/telemetry207-rollout-heroku.json`,
`target/generic-rollout-telemetry207-deployed.json`, `target/telemetry207-assets.json`
and `target/telemetry207-stream.json`. No credentials were printed or committed.

Remaining limits: physical microphone/speaker audibility and real-provider response
quality were not retested. Provider transcript intervals, context ACKs and output
activity do not establish physical playback completion. Journals are bounded and
transient; server windows may include work predating the page or a local Clear.
Capture settings/build/usage remain explicitly unknown where not reported. The
unrelated untracked `codexpython.md` was preserved.
