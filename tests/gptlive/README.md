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

## Core task journeys with canonical and compact responses

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests GenericMultimodalTaskIntegrationTest,SocialSpaceAgentIntegrationTest,SocialSpaceLiveJourneySmokeIntegrationTest
```

Generic covers proposal, persisted activation, a matching sensor cue, Live
announcement capture and stop. Social Space adds arrival, name address, ambient
silence during an active task and departure. Both Live journeys run once per
encoding and assert canonical persisted behaviour and unchanged agreements and
action counts. They use real scoped services, JPA and Live ingress/delivery,
with controlled provider responses. They do not establish physical audibility
or robot execution.

The lower-level boundary checks are `TaskBehaviourEncodingUnitTest`,
`CompactBehaviourPlanUnitTest`, `GenericMultimodalTaskUnitTest`,
`TaskLifecycleUnitTest` and `SocialSpaceAgentUnitTest`.

## Detachable state view

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests ValerianClientStaticResourceContractTest,LiveCockpitSmokeIntegrationTest --browser --browser-grep 'state view|Valerian columns follow|Valerian cockpit opens connected|Valerian camera and microphone controls|Valerian cockpit columns expand|real scoped cockpit'
```

`valerian-state-view.spec.mjs` checks floating movement, keyboard controls,
maximise/restore, dock/close, popup blocking, theme, agent changes and owner
teardown, with desktop/mobile screenshots. Its real-app case uses scoped HTTP,
MySQL and native monitor SSE to verify an RPS transition and reset while detached.
Only the inference provider is scripted; the state view adds no model calls.
The runner includes these cases with Live enabled and disabled. Database
fixtures remain isolated; popup behaviour is verified in Playwright Chromium.
