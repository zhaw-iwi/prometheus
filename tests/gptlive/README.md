# Offline GPT-Live acceptance

Run `npm ci` to install the locked test dependencies, including the exact Bootstrap
5.3.3 and Bootstrap Icons 1.11.3 versions used by the cockpit. The acceptance
fixtures serve those UI assets locally through Playwright routing; application
HTTP/SSE and real Bootstrap behaviour remain unchanged. Camera/provider boundaries
remain synthetic, and optional external fonts may use browser fallbacks.

Run from a configured checkout with Java/Maven, Node/Playwright, Python and
`pymysql` available. MySQL must be on localhost. The runner reads administrative
credentials from ignored `src/main/resources/application.properties`, or from
`GPTLIVE_MYSQL_ADMIN_URL`, `GPTLIVE_MYSQL_ADMIN_USER` and
`GPTLIVE_MYSQL_ADMIN_PASSWORD`. Do not put credentials in command arguments.

```powershell
python tests/gptlive/run_acceptance.py --browser
python tests/gptlive/run_acceptance.py --java-tests all
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
npm.cmd run test:live:unit
```

Each invocation creates a random `prometheus_gptlive_<id>` schema and restricted
account. It overrides provider credentials/URLs and disables scheduled agent
ticks. The configured application database is never a test target. Cleanup stops
only the owned app process and drops only that generated schema/account. Logs,
traces and screenshots stay under `target/gptlive-acceptance-<id>`.

The default Java smoke uses real scoped controllers, JPA, transactions, runtime,
capture workers and SSE. `--browser` launches the application on a free loopback
port using only the test provider fixtures, then runs the browser smoke and
focused regressions. It restarts with Live disabled and verifies text plus scoped
TTS. `--java-tests none --browser` skips Java tests while still compiling the app
and fixtures. `--java-tests` also accepts a comma-separated focused selection.
Use `--live-only` with `--browser` to rerun Live coverage after a small Live-only
change without repeating already verified legacy browser cases.

`valerian-gptlive-smoke.spec.mjs` stubs physical media/WebRTC and provider output
only; it does not intercept PROMETHEUS HTTP or EventSource. The existing
`valerian-gptlive.spec.mjs` is a separate UI suite with HTTP fixtures. Both use
assertions before screenshots. Test-only `/__live-fixture/*` routes exist only in
the fixture app; the production artifact excludes all fixtures.

Synthetic provider/media success establishes offline behavior, not real voice
quality, acoustic latency, echo cancellation or Bluetooth support. Follow the
physical contract in [PLAN_GPTLIVE.md](../../.agents/PLAN_GPTLIVE.md) and record
each OS/device/noise result separately in the results record. Do not mark unrun
trials as passing.

## Representative task-agent journeys

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GenericMultimodalTaskIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest,GalaDinnerReceptionJourneySmokeIntegrationTest
```

Each agent's happy path runs once with canonical provider output and once with
compact output. Both must publish canonical plans and preserve task agreements,
action counts, speech capture and duplicate suppression. Generic covers proposal,
activation, a fresh cue and stop/reset. Social Space adds arrival/departure,
name address and task priority over ambient reactions. Gala covers two separate
visitors, games and co-created tasks with no inherited conversation/agreement.
The full runtime and MySQL persistence are real; provider voice/model responses
are scripted. The tests never contact a paid provider.

The existing browser smoke `Generic activation telemetry` additionally checks
the real cockpit's pending work, cue waiting, failure and reset. It is skipped
when Live is disabled. A separate bounded text-provider comparison is recorded
in [NEEDFORSPEED_RESULTS.md](../../.agents/NEEDFORSPEED_RESULTS.md); that comparison
is not an acoustic GPT-Live trial.

For a repeatable physical Gala conversation based on the first live trial, use
[GALA.md](GALA.md).

## Opt-in Gala database capacity gate

Use disposable local MySQL, never the production database:

```powershell
$env:PROMETHEUS_GALA_CAPACITY = 'true'
$env:PROMETHEUS_GALA_CAPACITY_SECONDS = '180'
$env:PROMETHEUS_GALA_CAPACITY_PROFILE = 'reception'
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'QueryVolumeIntegrationTest#galaReceptionCapacitySoak'
```

It runs quiet Live, sustained speech/sensing, and camera-after-Stop phases for one
instance/cockpit. Use complete game cycles: 60 to 600 seconds, in multiples of 60.
The default `reception` profile follows five-second stable Live sensing and context
coalescing, with urgent game occasions bypassing that window. A hand is held for
five seconds after each confirmed RPS start (three samples). Two SSE streams stay
open, with one controlled reconnect at busy +60 seconds. After Stop there is one
changed face/social sample at +10 seconds; unchanged readings are suppressed and
no hand is visible. These are explicit scenario assumptions, not all possible
camera activity. Speech remains four user and eight native segments per minute,
with six durable fragments each.

Set the profile to `stress` to reproduce the original continuously held hand,
per-work-tick context refresh and per-minute/phase reconnect profile, including
unchanged post-stop readings. Keep these results separate from code savings.
Reports are `target/gala-capacity-reception-report.json` and
`target/gala-capacity-stress-report.json`; route costs include Hibernate operation
counts. Optional local SQL attribution uses `PROMETHEUS_GALA_SQL_DIAGNOSTICS=true`
and the `org.hibernate.SQL` DEBUG logger; inputs/providers are synthetic.
Providers/capture workers remain offline fixtures; explicit context reads
approximate scheduling. Neither profile is an observed production rate or a
production upper bound. Real worker/fragmentation measurements remain necessary.
The gate intentionally fails above 27,000 questions/hour (75% of the
verified 36,000/hour account allowance). A passing regression ceiling is not capacity
readiness. The account quota is fixed; do not raise the target to pass. Reset the
opt-in environment variable to `false` for ordinary local functional acceptance.
Local functional testing can proceed while the separately reported capacity gate
fails; it does not establish Heroku reception readiness.
