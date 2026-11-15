> **Camera follow-up:** the four-session/two-waves result below qualifies the v2
> simulated-observation workload only. It does not qualify v3 continuous browser
> detection or its observation traffic. Keep human collection closed until the
> camera workload and participant hardware have been assessed.

# OS-12 capacity protocol

Predeclared on 2026-11-15, before the first study capacity probe. Results belong
in RESULTS.md. A compressed probe can diagnose errors; it cannot qualify capacity.

## Candidate and acceptance criteria

- One Java process, scheduled ticks disabled, Hikari maximum 10 connections,
  candidate admission maximum 8, 120-second presence leases renewed every 30
  seconds by visible participant pages. No queued admission. A ninth active
  participant receives HTTP 429 without losing the assignment or private draft.
- At least 1,800 seconds of measured workload: eight complete A/B/C procedures,
  staggered across 24 minutes of interaction, then assessments and export. Each
  has two agreements, the prescribed interruption/revision, private predictions,
  canonical recorder/display receipts, full-history reads, SSE and reconnection.
  Setup and final export are measured separately. Synthetic inference waits one
  second; actual provider results and latency are reported separately in OS-11.
- Zero unexpected 5xx, request timeouts, cross-session leakage, duplicated
  committed effects, missing required snapshots, or exhausted pools. At an idle
  checkpoint all SSE connections remain open while JDBC active/waiting are zero.
- HTTP p95 at most 2 seconds for reads/forms/presence and 5 seconds for turns;
  maximum 30 seconds. Report distributions separately for setup/export. Report
  queue time when instrumented, otherwise explicitly mark it unmeasured rather
  than using total latency as a queue measurement. Sample pool and JVM resources.
- Database reference allowance: 36,000 queries/hour from README's hosting
  capacity documentation. Required headroom: 20%, hence 28,800/hour. Conservatively
  project two complete 30-minute waves per hour, including both setup and export.
  Also report active-interval rate. Reduce admission or optimize/retest if this
  bound fails. Do not change thresholds after observing a failure.
- MySQL GLOBAL Questions includes transaction and other local client traffic;
  run without another test workload and report this conservative limitation.
  Record local account query/connection limits and server connection maximum.
  An unlimited local account is not evidence of the remote hosting allowance.
- Eight-session qualification applies to this scripted workload and one process.
  Human pacing, longer histories and remote hosting remain pilot/deployment checks.
  Estimate provider demand from OS-11 calls/tokens; actual organization/model
  quotas must be checked by the operator before opening a human wave.

## Admission and interaction bounds

Default interaction bounds are 50 new ingress requests and 45 minutes after agent
provisioning, including simulated scene/sign requests. Durable receipt retries
remain readable and do not consume another turn. Reaching a bound blocks new
work with an explanatory 429; Finish interaction and withdrawal remain available,
and assessments preserve a timeout or other selected ending without claiming task
success. The operator can replace a failed attempt with a linked fresh attempt.

`max-active=0` disables admission only for synthetic regression fixtures with
many deliberately unfinished attempts. It prevents human provisioning and is
never a capacity qualification configuration. Production leases are in-memory:
restart clears them and returning pages must reacquire; saved study state is
durable. Hidden/offline pages lose their lease after 120 seconds. No distributed
admission or multi-process deployment is supported.

## External gates

Remote deployment and provider quotas, ethics/institutional arrangements,
participant information/retention policy, actual 4-6-person pilot, support rota,
and frozen main-study protocol remain PENDING. Automated capacity and provider
acceptance do not satisfy these gates.

## Reduced candidate after the initial measurements

The first complete eight-session probe projected 56,188 queries/hour. Direct
shared-storage reads reduced the next probe to 43,518/hour, still above 28,800
before full-length presence renewals. The next candidate is four sessions with
all three scenarios, using `--study-capacity 1800 --study-wave 4`. All original
numeric criteria, durations, complete histories and 20% headroom remain unchanged.
The production default is reduced to four; qualification is recorded in RESULTS.md.

Accepted run `b63e461b75` sustained four sessions for 1,800.263 seconds, with
24,024 projected queries/hour (33.27% headroom), all latency/error criteria met,
and production-JAR recovery/export passed. This qualifies the local scripted
operating envelope only: one process, four concurrent sessions, at most two such
waves/hour. The operator controls intake; admission alone does not cap starts/hour.
External readiness gates above remain pending. See RESULTS.md for retained failures,
resource measurements, exact commands and limitations.
