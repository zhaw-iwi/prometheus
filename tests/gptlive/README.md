# GPT-Live acceptance

## Playback cue receipt selection

Shared playback cues can select a contiguous span of a complete assistant
transcript by native receipt ID. Trusted speech parts preserve those IDs; empty
selections retain whole-segment evidence. The client requires each selected
receipt, matching playable audio and subsequent quiet, while allowing retained
prefix receipts from the same segment to establish audio continuity. Selections
are bounded to 32 distinct IDs; unrelated, missing and interrupted evidence is
rejected. This shared contract does not interpret application countdown words.

```sh
node --test tests/js/live/playback-cue.test.mjs
./mvnw -Dtest=LivePlaybackCueUnitTest,ScopedLiveSessionControllerWebMvcTest test
```

For durable-ingress coverage, run `LiveTranscriptIngressIntegrationTest` through
`run_acceptance.py` with an ignored local `--database-properties` configuration.
The runner provisions only a disposable loopback schema/account. These synthetic
checks do not establish physical audibility. Reload the cockpit after updating.

## Late playback cue regression

The short-response regression exercises the production playback observer with
synthetic audio and native caption identities, holding back real updates until
eleven seconds after playback. The browser must acknowledge the associated
segment itself and the persisted Coordination task must enter Reflection.
Application HTTP, transcript capture, semantic association and monitor SSE are real;
provider/media boundaries are synthetic. No manual playback POST substitutes for
the observer in this journey.

```powershell
node --test tests/js/live/playback-cue.test.mjs tests/js/live/client.test.mjs
python tests/gptlive/run_acceptance.py --java-tests LiveContextDeliveryUnitTest,ScopedLiveSessionServiceUnitTest,CoordinationDeliveryIntegrationTest --browser --browser-mode enabled --live-only --browser-grep 'Coordination reaches Reflection'
```

Supply `--database-properties` with an ignored loopback MySQL administrative
configuration if needed. Unit coverage includes late initial cues, delayed
association, unrelated/overlapping speech, missing receipts, interruption,
duplicate captions, muted/stalled output and stream replacement. Physical audibility
and real caption/audio skew still require `.reng/HUMAN_TEST.md` after deployment.

## Observation timing

The sanitized `coordination/observation-timing.json` test resource characterizes
the November 4 future-dated scissors failure. `CoordinationObservationTimingUnitTest`
uses fixed clocks for zero-action acceptance, old/future cue rejection, one action
and Live playback gating. Run it without a database:

```powershell
.\mvnw.cmd -q -Dtest=ObservationTimingUnitTest,CoordinationObservationTimingUnitTest,RuntimeClockControllerWebMvcTest test
node --test tests/js/live/observation-clock.test.mjs tests/js/live/observation-lifecycle.test.mjs
```

For persistence/reload and Chromium wall offsets of plus/minus one minute:

```powershell
python tests/gptlive/run_acceptance.py --java-tests CoordinationAgentIntegrationTest,CoordinationDeliveryIntegrationTest,RuntimeClockControllerWebMvcTest --browser --browser-mode enabled --browser-grep 'observation clock|camera hand report|Coordination reaches Reflection'
```

Supply `--database-properties` for an ignored local administrative configuration
when the default is absent. The runner rejects remote MySQL hosts and removes its
random schema/account. The offset journeys use the real clock endpoint and actual
Cockpit hand callback/export, with synthetic detector results and mocked agent
ingress. They are intentionally skipped by the static-only runner. Persistence
and final delivery are verified separately with scripted providers. See
`.agents/OBSERVATION_TIMING_RESULTS.md` for actual commands/results and
`.reng/HUMAN_TEST.md` for the remaining physical test.

## State graph viewer

For browser-only graph, visual and popup/lifecycle checks, serve only static
assets (no configured application database or provider access):

```powershell
npm.cmd run test:state-graph:unit
python tests/playwright/run_static.py tests/playwright/valerian-state-graph.spec.mjs tests/playwright/valerian-state-view.spec.mjs tests/playwright/valerian-lifecycle.spec.mjs --grep-invert 'persisted transition'
```

Six reviewed Chromium/Windows screenshot baselines live beside the graph spec.
Regenerate them only for intentional visual changes, using `--update-snapshots`
on the static runner, and inspect the images before accepting them. Other
platforms require separately reviewed baselines.

For persisted graph IDs, real HTTP/monitor SSE, popup and reset acceptance:

```powershell
python tests/gptlive/run_acceptance.py --java-tests StateGraphUnitTest,StateGraphIntegrationTest --browser --browser-mode both --live-only --browser-grep 'state view tracks a persisted transition'
```

Use `--database-properties` with an ignored local administrative properties file
if needed. This journey creates disposable agents with scripted providers and
checks graph request counts; no live OpenAI call is required. See
`.agents/STATE_GRAPH_RESULTS.md` for the exact verification performed.

## Recorded scissors interaction

The shared fixture `src/test/resources/coordination/scissors-interaction.json`
captures the intended 2026-10-29 Cockpit journey: persona, capabilities, grounded
scissor recognition, a one-joke proposal, explicit acceptance, one fresh cue,
confirmed delivery, Reflection, positive feedback and return to Conversation.
It contains synthetic task content rather than private conversation history.

Run the persisted journey and the mixed-workload query regression offline:

```powershell
python tests/gptlive/run_acceptance.py --java-tests CoordinationDeliveryIntegrationTest,CoordinationAcceptanceUnitTest,QueryVolumeIntegrationTest
npm.cmd run test:live:unit
```

The database runner always uses a disposable local schema. The journey uses real
runtime, persistence, selected observation context and response/playback fences
with a scripted inference provider. It does not measure native spoken compliance.
`target/coordination-query-report.json` reports MySQL Questions including transaction
commands, by path, and separately reports whether projected quota headroom is met.
Its regression ceiling is not a production capacity guarantee. The synthetic
minute excludes real provider/capture workers, SSE reconnections and scheduled
ticks; measure those before making a production capacity decision.

For an explicitly paid real-provider trial, the existing isolated runner now
supports this full journey with synthetic visitor audio and sensor inputs:

```powershell
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario interaction
```

It checks persisted terms, zero actions on acceptance, one action on the fresh
cue, matching captured speech plus real browser playback settling, Reflection and
the archived delivery on return. It never acknowledges playback from captions
alone. Inspect its retained captions for accurate persona/capabilities and absence
of premature task content; lexical assertions are not a complete semantic judge.
The real-provider scenario is prepared but has not been run for this follow-up.

For physical acceptance, use the Heroku-served Cockpit after an authorized deploy,
create a fresh Coordination instance, start Live and enable hand observations.
Use the fixture's seven user turns in order. Show scissors for the perception
question, then lower the hand during proposal/acceptance. Expect an answer naming
the fresh sign, no sample joke during setup and no task action on acceptance.
After arming, show a fresh sign once. Expect one joke, then Reflection only after
its delivery acknowledgement. Say "Okay. That worked", verify the report remains
separate from playback evidence, and return to Conversation with history intact.
Export telemetry and note the served build, client visibility and audio device.
Repeat with an early user interruption as a separate uncertainty case; never
treat a replay or an unconfirmed delivery as a successful completion.

Run `npm ci` to install the locked test dependencies, including the exact Bootstrap
5.3.3 and Bootstrap Icons 1.11.3 versions used by the cockpit. The acceptance
fixtures serve those UI assets locally through Playwright routing; application
HTTP/SSE and real Bootstrap behaviour remain unchanged. Camera/provider boundaries
remain synthetic, and optional external fonts may use browser fallbacks.

Run from a configured checkout with Java/Maven, Node/Playwright, Python and
`pymysql[rsa]` available (install with `python -m pip install 'pymysql[rsa]'`).
MySQL must be on localhost. The runner reads administrative
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

## Core task journeys with canonical and compact responses

### Integrated Coordination escalation (ES-08)

These three scenarios use the existing real local application, disposable MySQL,
real OpenAI Live transport and the production browser playback observer. Visitor
audio is synthesized and sensor inputs are synthetic. They extend the existing
journey; `coordination-text` cannot satisfy these checks.

After configuring local MySQL administration and provider credentials as described
above, select one explicitly paid scenario:

```powershell
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-recovery
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-dialogue
python tests/gptlive/run_acceptance.py --java-tests none --paid-core coordination --coordination-scenario escalation-social
```

Recovery covers recognition, difficulty before/after preparation, fresh perception,
natural acceptance, one cue and confirmed final delivery, then return. Dialogue
covers a riddle question/answer/feedback, help and ordinary questions, followed by
an unfinished-answer completion request, explicit replacement and renewed acceptance.
The final-feedback rubric reads the associated transcript segment and recorded
suffix offset, keeping any later Reflection turn separate; all native captions
remain available for semantic review. Social covers a reviewed count
alternative, matching/nonmatching cues and explicit restart/pause/resume/stop.
Each journey fails on an unmet check and retains native transcripts, task state,
provider evidence and playback diagnostics under its ignored artifact directory.
Visitor-turn assertions wait for durable transcript processing, not just quiet
captions. Final completion still requires real browser playback observation.

Per invocation: at most 12 scripted visitor turns, 24 backend inference requests,
2,048 completion tokens per request, 80,000 UTF-8 input bytes per inference, one
Live session with a 360-second lifetime, and a 420-second child-process deadline.
Further inference stops once reported cumulative usage reaches 200,000 tokens;
the last request can cross that threshold, so it is not a hard monetary cap.
Synthetic speech is limited to 12 clips/6,000 characters and cached between runs.
The test-only provider recorder enforces request/input/session limits without
altering production transport. Provider usage may be unavailable after failures;
Live seconds, backend tokens and synthesis calls are separate measures.

Review actual wording against the rubric in `journey.json`: truthful state and
perception, visible supported terms, finite budgets, no premature/duplicate
contribution or answer leak. An automated pass does not complete semantic review.
Never automatically rerun until success: retain every attempt and state the
specific correction that justifies another run. Integrated evidence and unresolved
checks are in [ESCALATION_RESULTS.md](../../.agents/ESCALATION_RESULTS.md); physical
operator trials are in [CORE_AGENTS.md](CORE_AGENTS.md) and the
[escalation operator checklist](../../.agents/ESCALATION_OPERATOR.md). Physical audibility,
camera accuracy and robot movement remain separate gates. Database capacity is
deferred for the current behavioural milestone by the owner's 2026-11-11 scope update.

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

The same entry point accepts `--coordination-cases` for three real OpenAI **text**
smoke scenarios: recognition/help/natural acceptance/one-shot cue, a supplied
riddle question and feedback followed by explicit replacement, and a reviewed
visible-count alternative with pause/resume. Each uses a fresh production
Coordination graph, real actions, canonical replies and storage assertions.
No Spring application or database is started. This diagnostic does not complete
ES-08's Realtime audio/persistence/physical acceptance requirements.

Set `OPENAI_API_KEY` in the process environment (or use ignored `openai.key`
configuration). It loads checked-in `openai-prod.properties` routes, then local
`openai.properties` and `application.properties` OpenAI overrides; it never uses
their database credentials. Models/effort and provider token usage are retained
in the artifact. There are at most 14 requests, 2,048 completion tokens per request,
30 seconds per request and a five-minute scenario budget; there are no automatic
provider retries. Normal Maven tests never invoke this paid main.

```powershell
.\mvnw.cmd -q -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/coordination-provider-classpath.txt'
$probeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/coordination-provider-classpath.txt -Raw).Trim()
java -cp $probeClasspath fixtures.live.CoreTaskProviderProbe --allow-paid-provider target/coordination-provider-smoke.json --coordination-cases
```

Failures return nonzero and retain the synthetic requests, raw model output,
response/state snapshots and usage in the ignored artifact. Review wording as
well as assertions; passing a few cases is not a paraphrase-robustness guarantee.

For speech-association failures, `fixtures.live.TaskResponseProviderProbe` evaluates
the production `TaskResponse.Check` against the labelled synthetic cases in
`src/test/resources/coordination/response-verification-cases.json`. It includes
question paraphrases, full/partial/contradictory feedback, arming, count-condition
drift, exact words and instruction-like captured text. It uses the same configuration
precedence and private key setup as the probe above, with no database or voice session.

```powershell
.\mvnw.cmd -q -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/response-probe-classpath.txt'
$responseProbeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/response-probe-classpath.txt -Raw).Trim()
java -cp $responseProbeClasspath fixtures.live.TaskResponseProviderProbe --allow-paid-provider target/response-verifier.json --repeat=2
```

Omit `--repeat=2` for one pass. Maximums are 36 requests, 512 completion tokens per
request, 12,000 input bytes per request, 30 seconds per request and four minutes
overall. A 30,000 reported-token threshold prevents subsequent requests (the last
request can cross it). There are no provider retries. Exact-word matches use the
production fast path and are identified separately. Errors stop spending and retain
the partial report; classification failures retain every result and return nonzero.
Malformed output or a mismatched response identity fails the evaluation even when
production conservatively maps it to AMBIGUOUS and that is the expected label.
This evaluates delivery content only, not acoustic timing, playback or task success.

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
