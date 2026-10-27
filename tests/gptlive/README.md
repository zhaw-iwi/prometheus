# GPT-Live acceptance

Run `npm ci` to install the locked test dependencies, including the exact Bootstrap
5.3.3 and Bootstrap Icons 1.11.3 versions used by the cockpit. The acceptance
fixtures serve those UI assets locally through Playwright routing; application
HTTP/SSE and real Bootstrap behaviour remain unchanged. Camera/provider boundaries
remain synthetic, and optional external fonts may use browser fallbacks.

Run from a configured checkout with Java/Maven, Node/Playwright, Python and
`pymysql` available. MySQL must be on localhost. The runner reads administrative
credentials from ignored `src/main/resources/application-test.properties`, or from
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

Core improvements are tracked in
[PLAN_COREAGENTIMPROVEMENT.md](../../.agents/PLAN_COREAGENTIMPROVEMENT.md).
Offline remains the default. To explicitly permit paid OpenAI calls:

```powershell
python tests/gptlive/run_acceptance.py --java-tests none --paid-core live
python tests/gptlive/run_acceptance.py --java-tests none --paid-core face
python tests/gptlive/run_acceptance.py --java-tests none --paid-core social
python tests/gptlive/run_acceptance.py --java-tests none --paid-core space
python tests/gptlive/run_acceptance.py --java-tests none --paid-core task
python tests/gptlive/run_acceptance.py --java-tests none --paid-core space-task
```

This launches the production app against a disposable local schema and uses its
real policies, scoped HTTP, persistence and Live sideband with synthetic visitor
audio. The first baseline covers Live Multimodal: SIRA conversation, a person-count
question grounded in supplied sensing, and a requested paper hand sign persisted
as a behaviour plan. It checks state retention and semantic captions. It does not
use a replacement prompt or scripted provider. Later milestones extend the
journey selection. Face/social/space now cover a grounded perception answer and
an unsolicited comment after a stable change. Face also verifies persisted user
correction and cancellation of the pending comment. Task/space-task cover a stored
proposal, completed invitation, short acceptance, confirmed activation, a silent
paper gesture on a rock cue, completion and harmless acknowledgment. Social Space
also checks that an arrival cannot override the active task.
`OPENAI_API_KEY` takes precedence over the ignored local
`openai.properties`/`application.properties` API key; their database URL is never
used for paid-provider configuration. Configure `openai.model` or `OPENAI_MODEL`
as for an ordinary local app; the runner rejects missing configuration before
billing. Run its isolation checks with `python -B tests/gptlive/test_acceptance_runner.py`.
The runner limits the paid child to seven minutes, each request/wait is bounded,
and failures return nonzero with artifacts under the owned acceptance directory.
No automatic provider retries are performed by the harness. Cached synthetic
speech stays under ignored `target/core-provider-audio`. Never commit artifacts
containing transcripts or credentials. Do not run heavy browser/build jobs during
paid audio trials. Physical playback and robot execution still need manual tests.

For a control-schema failure, `fixtures.live.CoreTaskProviderProbe` is an explicitly
paid diagnostic main in the test classes. It uses the production core task action
and configured gateway for exactly one synthetic proposal, without starting Spring
or a database. Run with `--allow-paid-provider target/<artifact>.json` on the test
classpath. Its artifact contains the synthetic prompt/response, never credentials;
keep it ignored. Optional third argument `--confirmation-cases` adds six bounded
control requests: activation as a question or offer, an unrelated question or
statement, revision and future activation. It verifies the persisted outcome, including deterministic rejection
when a model misclassifies a deferred question. This diagnostic does not replace
the full paid Live journey.

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

The 22 October follow-up baseline uses `GalaLiveFollowupUnitTest`. It covers first
and repeated RPS starts, a paused nonbinary question, post-reveal follow-ups and
three questions interrupted by isolated activity spikes. GI-15 replaces the false
post-reveal recovery characterization with acceptance. GI-16 replaces the merged-
turn baseline with three distinct turns under its isolated-spike fixture; physical
speech accuracy remains unverified. The test also exports current, application-produced
instructions and game context commands to `target/gala-followup-fixture.json`
for provider comparisons. No database, provider or private transcript is used.

### Opt-in real Gala voice comparison

With `OPENAI_API_KEY` already set in the environment and a provider account that
supports `gpt-live-1`, export the fixture with the unit test above, then run:

```powershell
node tests/gptlive/gala_provider_trial.mjs --allow-paid-provider target/gala-followup-fixture.json target/gala-provider-trial.json
```

This incurs Live and synthetic TTS costs. Input clips are cached under ignored
`target/gala-provider-audio`; results and generated fixture files stay under
`target`. The probe uses actual application context commands over WebRTC with
sequential provider ACKs, five seconds of simulated backend delay after each
activity request, and a scheduled 1.2-second pause inside a nonbinary question.
Recorded input start/end times expose browser scheduling/transfer overhead; use
the measured pause, not just its configured value. Sensor timestamps are rebased
for the synthetic replay, preserving their relative age and TTL.

Scenarios: first RPS start/result, replay/result, guessing start, paused question,
give-up and reveal without a helpful follow-up name. There is no app/database in
this probe and no claim of actual robot or headset audibility. Exit zero means
transport completion only: review output captions against input and confirmation
markers for premature invitations/answers/reveals, duplicates, echoes, internal
status wording and silence. Retain every trial, including failures; three clean
fresh sessions are the planned candidate gate, not a statistical guarantee.

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

## Core games and reception

Core RPS: `python tests/gptlive/run_acceptance.py --java-tests CoreRpsRoundUnitTest,CoreRpsPersistenceIntegrationTest --paid-core rps`
uses the actual browser playback observer, two rounds, an old-capture rejection,
deterministic results and semantic caption checks. It is explicitly paid and uses
only the disposable local database. The browser state-view journey also exercises
manual capture and the rendered result, with Live enabled and disabled.

Guessing: `--paid-core user-guess`, `agent-guess` and `care-guess` run real Live
journeys for both role directions and the healthcare variant. The human-guessing
paths check known/unknown/ambiguous questions, give-up/reveal, a fresh second secret
and a correct guess. The agent-guessing path checks a human-confirmed guess and
new round without inventing/storing the human's secret. Pair with
`--java-tests GuessingGameUnitTest,GuessingGamePersistenceIntegrationTest` for
deterministic rules and local reload checks. Captions are reviewed for semantic
failures; provider success is not physical speech verification.

The browser fixture app explicitly enables Social Space reception for newly created
instances. `--browser --live-only --browser-grep 'Social Space opt-in reception|presence reception preserves'`
checks real scoped start/close, two-second dropout grace, new encounter context,
durable history and Stop alongside the existing client regression. Pair with
`--java-tests CoreReceptionUnitTest,CoreReceptionIntegrationTest` for task retention,
autonomous response gates, source freshness/order and immediate access revocation.
Ordinary and paid app runs retain the production default (reception disabled).

For final hardware acceptance and deployment handoff, follow
[CORE_AGENTS.md](CORE_AGENTS.md). The automated journeys use synthetic input and
cannot establish physical audibility, camera accuracy or robot execution.

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
