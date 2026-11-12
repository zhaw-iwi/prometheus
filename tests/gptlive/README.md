# GPT-Live acceptance

## Coordination committed Speech acceptance

Fresh Coordination Live sessions select committed Speech output (Alloy at normal
speed, using the configured Speech model) and retain real Live user input. The
three ES-08 scenarios run through the production `CommittedSpeechPlayer`, scoped
synthesis/release/played endpoints, real state machine and disposable MySQL.
Native output/captions remain diagnostic only. The earlier native correction
fixtures remain historical evidence, not the current Coordination playback route.

Run `--paid-core coordination --coordination-scenario escalation-recovery`,
`escalation-dialogue` and `escalation-social` separately with the existing runner.
Existing per-run input/backend/session caps remain unchanged. Committed output has
an additional explicit ceiling of 12 Speech calls and 6,000 input characters;
`ProviderCaptureBudgetTest` checks enforcement. `speech-provider.jsonl` records
counts/characters/voice. Every returned PCM clip is retained under
`synthesized-speech/` with SHA-256, committed text and duration in `journey.json`.
These are distinct from native receipts and from `trial.speech_played` callbacks.
Do not mutate the raw journey rubric from PENDING to a semantic verdict.

Optional independent audio-text evidence can be produced locally:

```powershell
# In a separate local environment, outside application dependencies:
python -m pip install faster-whisper==1.2.1 av==16.1.0
python tests/gptlive/review_synthesized_audio.py target/gptlive-acceptance-<id>
```

This downloads `small.en` once and uses CPU int8, beam 5 and no text prompt.
`audio-review.json` retains hashes, expected wording and ASR text, always marked
PENDING_SEMANTIC_REVIEW. Review meaning separately, accounting for ASR errors;
neither local ASR nor a playback callback establishes physical speaker audibility.
The tool's primary documentation is [faster-whisper](https://github.com/SYSTRAN/faster-whisper).

Focused offline checks:

```powershell
python tests/gptlive/run_acceptance.py --java-tests LiveCommittedSpeechServiceUnitTest,CommittedSpeechIntegrationTest,ProviderCaptureBudgetTest --browser --live-only --browser-grep 'approval player captures real WebRTC|Coordination (reaches|preserves)|real scoped cockpit'
```

The browser uses real Web Audio/output observation with a one-second PCM tone
after 750ms of leading silence, and covers normal Reflection plus lost playback acknowledgement without
replay. MySQL checks retain synthesis/release after reload, exclude native drafts,
and establish proposal consent only after observed synthesized playback.

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

## Gala countdown receipt selection

The mixed-response regression retains the complete assistant transcript while
selecting only the native receipts containing the recognized countdown. It covers
input-cleared prefix captions, continuous introductory audio, durable ingress and
reload, API serialization, one playback acknowledgement and a fresh replay round.

```powershell
node --test tests/js/live/playback-cue.test.mjs
python tests/gptlive/run_acceptance.py --java-tests GalaDinnerCountdownReceiptUnitTest,LivePlaybackCueUnitTest,ScopedLiveSessionControllerWebMvcTest,GalaDinnerReceptionIntegrationTest,LiveTranscriptIngressIntegrationTest
```

Supply `--database-properties` for an ignored local administrative configuration
if needed; the runner creates and removes a disposable schema/account. Scripted
providers and synthetic audio keep these checks offline. Whole-segment callers,
ownership, expiry, interruption and muted/stalled-output rejection retain their
contracts. Early countdown recovery and interrupted-cue recovery are separate
pending steps; these checks do not establish physical audibility.

## Retained native approval coverage

The earlier Coordination route used `ApprovedSpeechPlayer` and the session
handshake `speechApproval: true`. Its native capture/approval regressions remain
covered; current Coordination journeys use `CommittedSpeechPlayer` as described
above. For native-output evidence,
`session.output_transcript.delta` remains raw generated evidence, while
`trial.speech_played` alone supplies assertions about delivered speech. Reports
retain buffer timing/RMS/count diagnostics without recording audio. Task semantic
association, release, observed playback and final task completion stay separate.

The browser smoke holds real updates for eleven seconds after synthetic capture.
No speech may appear in conversation until approval and observed playback; only
then may the persisted task reach Reflection. HTTP, transcript capture, semantic
association, database and SSE are real. A separate local WebRTC peer-pair test
checks remote PCM capture, zero audible output on rejection, and one approved
playback. It catches Chromium's remote-stream consumption issue that a synthetic
MediaStream alone cannot expose. The private remote sink is muted and zero-volume;
the original stream is never used as audible output.

```powershell
node --test tests/js/live/approved-speech.test.mjs tests/js/live/speech-buffer.test.mjs tests/js/live/playback-cue.test.mjs tests/js/live/client.test.mjs
python tests/gptlive/run_acceptance.py --java-tests LiveSpeechApprovalServiceUnitTest,CoordinationDeliveryIntegrationTest --browser --browser-mode enabled --live-only --browser-grep 'approval player captures real WebRTC|Coordination reaches Reflection'
```

Supply `--database-properties` with an ignored loopback MySQL administrative
configuration if needed. Interruption, duplicate input, stale requests, muted/
hidden/stalled output, mismatched receipts, missing audio and size/deadline limits
fail conservatively. No uncertain output is automatically replayed. Physical
audibility and real caption/audio skew remain separate acceptance questions;
see `.reng/HUMAN_TEST.md` and the latest escalation results.

The bounded correction regression uses a valid semantic rejection of the first
unheard Coordination task draft, followed by one checked replacement. It asserts
unchanged response identity/deadline and one contribution, no release of the
rejected draft, and Reflection only after observed replacement playback. The
synthetic sideband supplies PCM lead-in, voiced coverage of the transcript's media interval and complete quiet coverage for each
utterance, matching the production capture requirement. A separate real local WebRTC test still
checks the remote capture/audio path.

```powershell
python tests/gptlive/run_acceptance.py --java-tests TaskResponseCorrectionUnitTest,LiveSpeechApprovalServiceUnitTest,CoordinationDeliveryIntegrationTest --browser --browser-mode enabled --live-only --browser-grep 'approval player captures real WebRTC|Coordination (reaches|corrects)'
```

Provider errors, malformed checks, release uncertainty and a second rejection
remain terminal for automatic correction. Unit tests also exercise concurrent
requests, interruptions, late candidates, owner/revision changes and lost approval
windows after persisted release. These offline checks do not complete ES-08;
the capped dialogue, recovery and social journeys still need semantic review.
Proposal reviews and confirmed support require post-commit native generation:
an early acknowledgement must not spend their check/correction. The latest native-output failures
and the deferred alternative are documented in the escalation results and
[speech-delivery decision note](../../.agents/ES08_SPEECH_DELIVERY_DECISION.md).

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

Generated temporary passwords contain mixed character classes for local MySQL
password policies. To use installed Chrome instead of Playwright's bundled
Chromium, set `PROMETHEUS_BROWSER_CHANNEL=chrome`; other channels remain optional.

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
The final-feedback rubric requires factual review of the actual heard question,
answer and feedback together. It permits correct positive or negative evaluation;
positive words alone do not establish correctness. It reads the associated transcript segment and recorded
suffix offset, keeping any later Reflection turn separate; all native captions
remain available for semantic review. Social covers a reviewed count
alternative, matching/nonmatching cues and explicit restart/pause/resume/stop.
Each journey fails on an unmet check and retains native transcripts, task state,
provider evidence and playback diagnostics under its ignored artifact directory.
Capture diagnostics include timed/untimed output-chunk counts. Reflected sideband
media ranges, when supplied, establish coverage across a transcript independently
of arrival jitter; real range gaps remain incomplete. Untimed compatibility/input
uses received-sample coverage. Neither path proves browser or physical playback.
Visitor-turn assertions wait for durable transcript processing, not just quiet
captions. Final completion still requires real browser playback observation and
the actual final HTTP acknowledgement for the current delivery ID and segment.
Both the direct and approval player adapters record that acknowledgement. The
escalation fixtures use Onyx for the synthetic visitor and Willow for Live;
`journey.json` records the synthesis model/voice. Assistant transcripts never
substitute for missing visitor transcripts or establish consent.
The ES-08 heard-response wait allows 65 seconds to observe the existing
45-second verification and 60-second playback limits. It does not extend either
production deadline or the session/child caps below.

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
Review preliminary native drafts as well as heard replies. With approval enabled,
retain rejected drafts as audit evidence and verify they never played; an allowed
task or ordinary correction must preserve identity, budget and deadlines and play at most once.
Heard proposals must not promise detection of someone joining, or a new allowance
for each person/cue, when the executable alternative is a visible-count threshold
with a total limit. A rejected draft is not evidence of a heard promise or delivery.
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
