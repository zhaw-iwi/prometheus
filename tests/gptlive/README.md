# GPT-Live acceptance

For a fresh checkout on another device, use the
[hand-interaction continuation guide](../../.agents/HANDOFF_HAND_INTERACTION_2026-11-24.md).
It restores the existing budget history without resetting spending and identifies
which local credentials, audio and database prerequisites must be recreated.

## Current hand acceptance allocation

Allocation 15 permits up to four individually selected open journeys and raises the continuation that began at 88 attempts/$186 to $120.
Each selection reserves $5 across backend inference and native Live. The cumulative
ceiling is 112 attempts/$306. See the latest hand interaction handoff for the current ledger.
The L-02 report records command delivery and individual native caption/audio
observations under each direct turn's `commandTiming`. Clock overlap and missing
evidence leave ordering undetermined. The local PCM, outgoing RTP counters
and provider transcript give separate evidence about acceptance wording.

`controlTimings` in the journey report joins the saved review, consent-repair and arming occasion
IDs to their actual context sends and acknowledgements, caption fragments, and
audible browser activity. Review windows begin at backend creation. Repair and arming
windows include the preceding user turn so an early confirmation stays visible.
Each window ends at withdrawal, expiry or the next turn. Missing timestamps and
overlapping browser clock brackets leave the ordering uncertain. The same
`controlTimings(report)` export can inspect an older full `journey.json` offline.

## Hand acceptance third extension (2026-11-24, historical)

The owner approved an additional $100 reservation allocation. Allocation 9
preserves all **78 attempts/$136** and permits at most **twelve new $5 attempts**
(90 total/$196 reserved), below the $200 cumulative ceiling. Earlier allocations
and their reservation amounts remain intact; new entries use
`hand-interaction-2026-11-24-usd100-extension`. Current isolated tests use
`OPENAI_MODEL=gpt-6.2-sol` with low reasoning and `gpt-live-1.1`, without changing
production model settings. At most ten backend calls give a conservative bound
of $4.6572 per attempt; all other technical caps and acceptance revision 2 remain. Missing/shortened/corrupted ledgers fail closed. No refunds or automatic
paid retries. Run targeted offline reproductions before selecting paid cases.

Checkpoint: **88 attempts/$186 reserved**, with **$14 unreserved** and two $5
selections still available under allocation 9. No more requests are queued.
The ledger must retain all 88 entries; shortened history rejects before spending.
Final paired L-09 and M-01/M-02 pass; L-01..08 remain failed or inconclusive.
See the evidence ledger for native contradictions, overlong review capture,
truncated acceptance captions and the unqualified-OK interpretation failure.
Do not reset the ledger or treat earlier passes as final acceptance.

## Hand acceptance second extension (2026-11-24)

The owner approved another $30 after the earlier $20 extension. Allocation 6
preserves **36 attempts/$70 reservations** and permits at most thirty further $1
attempts, up to **66 attempts/$100 cumulatively**. Actual billing is unavailable.
Each attempt retains the 14-request limit, priced models and other technical
caps, with a conservative bound of $0.9587424. Missing, shortened or altered
reservation amounts fail closed. Prior entries retain their authorizations;
new entries use `hand-interaction-2026-11-24-usd30-extension`. No refunds.
This allocation is now exhausted: **66 attempts/$100 reserved**, original 36/$70
entries unchanged. Further paid selections reject; the allocation is not renewed
by rerunning tests or deleting local artifacts.
Acceptance revision 2 is unchanged. The harness waits for the consumed preparation
request before demonstrating the cue, uses "I accept the current proposal", and
requires one predeclared input-sequence clarification: first leave the response
unspecified, then supply Ready once and human judgement. Term checks stay strict.
The clarification answer waits for the completed native question. Spoken Ready
journeys also require one fresh complete native Ready event after the execution
cue; an empty plan or completion narration cannot stand in for that response.
This assertion supplements the required complete audio and semantic review.
Shortened local audio retains all required conditions and roles. Direction and
registered-target fixtures send independent empty-scene captures, like the camera
pipeline, to establish fresh coordinates during inference without supplying a cue.
Bounded observation timings record capture/request gaps without changing timestamps.
Each independent capture pump includes HTTP time in its 500 ms interval and
permits only one in-flight request per sensor kind.
HI-12 remains incomplete. See [the evidence ledger](../../.agents/HAND_INTERACTION_RESULTS.md).

## Native conversation independence (GLF-02)

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests CoordinationAgentIntegrationTest,CoordinationAcceptanceUnitTest,CoordinationTaskContractUnitTest --browser --browser-mode enabled --browser-grep 'native Coordination audio|Coordination Text and Continuous'
```

The native fixture holds backend inference while measuring PCM from the actual
HTML audio element, then verifies nonverbal SSE rendering, exactly-once transcript
ingress and no competing synthesis/release request. It checks Stop while inference
is pending and Continuous -> Live -> Continuous on the same persisted agent. This
is synthetic transport/browser-route evidence, not provider speed or room audio.

## Native Coordination meaning and delivery (GLF-03)

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests 'Coordination*Test,TaskResponse*Test,Sensory*Test' --browser --browser-mode enabled --browser-grep 'native Coordination|Coordination Text and Continuous'
```

The persisted native journey checks preparation, acceptance, fresh/duplicate cues,
one contribution, scoped observed playback, reflection, help and revision. The
predeclared meaning fixtures use scripted verdicts to verify state/identity
handling; they do not measure model judgement. Native browser cases require
capture plus observed playback for Reflection and retain uncertainty after a
lost callback. Real provider meaning/speed and physical trials remain separate.

## Continuous Coordination parity (GLF-01)

The [repair roadmap](../../.agents/PLAN_GPTLIVEFIX.md) separates strict
Text/Continuous speech from native Live wording. GLF-02 restores native Coordination playback; older approval/synthesis
journeys below are historical and must not be used as native acceptance.

Run the new parity journey and affected contracts with an ignored local MySQL
administration file (the named application schema is never used):

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests ScopedBehaviourSpeechServiceUnitTest,BehaviourSpeechControllerWebMvcTest,SpeechArchitectureBrowserClientContractTest,CoordinationAcceptanceUnitTest,CoordinationTaskContractUnitTest,CoordinationAgentIntegrationTest,CoordinationDeliveryUnitTest,LiveSmokeConfigurationTest --browser --browser-mode both --browser-grep 'Coordination Text and Continuous|stopped resume lookup|mocked WebRTC emits partial|Stop and synthesis failure'
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
python -m unittest discover -s tests/gptlive -p test_acceptance_runner.py
```

`valerian-continuous-smoke.spec.mjs` drives both the Text UI and the production
Continuous transcription adapter against real controllers, state machines,
MySQL, SSE and canonical event-ID Speech. Scripted inference makes the proposed
agreement identical. It checks preparation, early-start rejection, zero-action
acceptance, one fresh-cue contribution, duplicate input/cue exclusion, exact
canonical TTS input, real PCM rendering and microphone gating/reopening. It runs
with the Live flag on and off. The neighbouring lifecycle cases use mocked HTTP
and are reported separately. Saved Live flags after ownership release have a
persisted Java regression.

Only external providers, SDP/data-channel events and microphone capture are
synthetic in the new real-app journey. The Speech provider returns a tone;
neither it nor PCM renderer events demonstrate spoken quality or physical
audibility. The runner isolates all Maven builds under each run's `build/`
directory, then cleans up its application, random schema and restricted account.
Run logs, JUnit XML and browser artifacts remain under
`target/gptlive-acceptance-<id>/`.

`gptlivefix-scenarios.json` supplies the sensor proposal and the meaning rubric
for later native dialogue, revision and uncertainty journeys. Labelled meaning
examples are review fixtures, not a semantic classifier. Actual verification,
retained failures, test dispositions and saved-flag handling are in
[GPTLIVEFIX_RESULTS.md](../../.agents/GPTLIVEFIX_RESULTS.md).

## Paid repair acceptance (GLF-05)

`gptlivefix_journey.mjs` uses the actual Valerian cockpit, direct native audio,
real OpenAI providers and disposable MySQL. Only the visitor microphone/sensor
stimuli are synthetic. `repair-baseline` measures three ordinary turns on
LiveMultimodal; `repair-live`, `repair-continuous` and `repair-text` exercise the
same Coordination proposal, preparation, early acceptance rejection, help,
acceptance, fresh/duplicate cue, one contribution, reflection and revision.

The committed-Speech and native-approval players, endpoints and dedicated tests
were retired in GLF-05. `--paid-core coordination` rejects before provisioning or
billing. Existing evidence remains in `.agents/ESCALATION_RESULTS.md` and Git
history; it is not evidence for the current native route. Other core agents keep
their independent native harness. The old escalation helper is a historical
fixture, not a selectable paid journey.

The predeclared plan is `gptlivefix-paid-plan.json`: initially eight attempts and
$40 cumulative reservations. Dated amendments permit twelve attempts and at most
$49.50 while retaining every earlier reservation. The Text-only retest reserved
$1 and permits neither sessions nor Speech; the transport follow-up reserved $2.
The final native role-guidance validation reserves $1.50, requires cached visitor
audio and disallows competing Speech before provider access. No further paid
trial is allowed under this plan.
Other audio attempts reserve $5. These are conservative envelopes, not invoices.
The ledger is `target/gptlivefix-paid-budget.json`; never reset it to reroll. The
plan bounds requests, tokens, session duration and input/output Speech. A different
backend model is rejected until its price and limits have been reviewed. Retained
failures, amendments and exact outcomes are in `.agents/GPTLIVEFIX_RESULTS.md`.

```powershell
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core repair-baseline
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core repair-live
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core repair-continuous
python tests/gptlive/run_acceptance.py --database-properties src/main/resources/application-test.properties --java-tests none --paid-core repair-text
```

Run only with current paid authorization. Inspect every failure before another
attempt. The latency target is native Coordination median within 1 second of
baseline, maximum within 2 seconds, three ordinary turns per mode. No p95 is
claimed. Browser speech-end, input-caption and actual audio-onset observations
stay separate from server clocks. The deterministic backend barrier establishes
independence; paid timings are observational. See `.agents/GPTLIVEFIX_RESULTS.md`
for outcomes and `.agents/GPTLIVEFIX_OPERATOR_CHECKLIST.md` for physical checks.

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

The former `--paid-core coordination --coordination-scenario interaction`
runner was retired with the committed player. Use the GLF-05 repair journey above
for current native provider acceptance; the persisted shared fixture remains.

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

### Historical Coordination escalation (ES-08)

The retired approval/committed-Speech journeys are preserved in Git history and
[ESCALATION_RESULTS.md](../../.agents/ESCALATION_RESULTS.md). They cannot be run
through the current paid runner and do not qualify the repaired native route.
The labelled semantic fixtures and offline authority checks remain useful;
`escalation_journeys.mjs` now contains only their review helpers. Current provider
and physical acceptance use the GLF-05 entries above.

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
drift, exact words and instruction-like captured text. The 26 cases also include
a complete ready-proposal review and negative activation, cue, role and preparation
contrasts. It uses the same configuration
precedence and private key setup as the probe above, with no database or voice session.

```powershell
.\mvnw.cmd -q -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/response-probe-classpath.txt'
$responseProbeClasspath = 'target/test-classes;target/classes;' + (Get-Content target/response-probe-classpath.txt -Raw).Trim()
java -cp $responseProbeClasspath fixtures.live.TaskResponseProviderProbe --allow-paid-provider target/response-verifier.json --repeat=2
```

Omit `--repeat=2` for one pass. Maximums are 52 requests, 512 completion tokens per
request, 12,000 input bytes per request, 30 seconds per request and four minutes
overall. A 60,000 reported-token threshold prevents subsequent requests (the last
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
