# Need for speed: evidence record

## Agents integration for Heroku testing (2026-09-17)

Integrated features/needforspeed through f9fcffe into agents from d0d3618 using
an ordinary merge. The shared PromptPolicy conflict was resolved with combined
generation and the feature branch's port of the existing hand-sign contract.
No application definitions or deployment workflow were moved back to the feature
branch. The agents workflow still deploys the testing app valerian on push.

Updated eight application-test fixtures for combined speech/nonverbal output and
immediate teardown of replay test servers. Existing expected response/state/storage
assertions remain; no production task prompts or catalog behavior were weakened.
The full merged Java suite passed 398 tests across 96 classes with zero failures,
errors or skips and clean process exit. The shared Node suites passed 45 tests.
Commands: .\\mvnw.cmd -q test, then node --test tests/js/performance/*.test.mjs
tests/js/speech/*.test.mjs tests/js/transcription/*.test.mjs.
MySQL used only the disposable prometheus_nfs_merge_b9643a17 schema and restricted
account; providers used mocks/loopback and a dummy key. The production-profile
route test also respects the fail-closed endpoint override used by these suites.
Deployment is explicitly authorized for the user's Heroku trial. Live provider
quality, account access and the two-second performance target remain trial gates.

## Speech activation follow-up (Milestone 172, 2026-09-17)

Reset publishes a live starter behaviour. The cockpit previously sent every
live speech event to its output queue regardless of whether the operator had
started transcription. It now gates audible output on the active transcription
session while continuing to display all eligible chat/behaviour events.

Session generation checks also prevent delayed startup lookups or queue work
from speaking after Stop Transcription/reset/disconnect or interfering with a
subsequent start. Stop Speech remains an output cancellation within the active
session, including during initial assistant replay.

Four new browser cases failed before the fix with an unwanted Speech POST:
reset SSE arriving before/after the HTTP response, and a delayed resume lookup
returning after Stop with/without a new start. These now pass. Saved cedar voice,
1.25 speed and room-speaker output are checked on both startup replay and live
replies. Both paths use the existing shared controls; no duplicate reset-only
configuration or browser text-to-speech path was found.

Verification on features/needforspeed:

- 24 Playwright cases: valerian-transcription.spec.mjs (20),
  valerian-lifecycle.spec.mjs (1), progressive-speech.spec.mjs (3).
- 11 Node cases: `node --test tests/js/speech/*.test.mjs`.
- 19 Java cases: SpeechArchitectureBrowserClientContractTest (5) and
  ValerianClientStaticResourceContractTest (14).

All passed with zero failures/skips. Exact logs are in ignored
target/speech-activation-*.log. Static local assets and controlled API/WebRTC/media
fixtures cover the cockpit; native Chromium covers MP3 playback before EOF,
Stop and buffered fallback. No database or live provider was used. Actual
Heroku acoustic behavior and response latency still require the user's trial.

Integrated feature commit 39c5b70 into agents for the authorized Heroku test
deployment. Only documentation conflicted. The merged Valerian script, markup,
speech/transcription modules and regression spec exactly match the tested
feature-branch versions; no test rerun was needed for the documentation merge.

## Transcription resume follow-up (Milestone 171, 2026-09-17)

Heroku testing exposed an existing resume lookup bug: the current-state policy
view copies events without IDs, so the latest-assistant endpoint discarded the
eligible result and returned 204. The endpoint now uses the scoped canonical
history also used by chat and synthesis. This preserves persisted IDs and
includes earlier-state speech. It returns no speech for empty history or a
latest user utterance; non-speech events do not count as utterances.

Two new persisted-agent/HTTP regression tests failed with 204 before the fix and
passed afterwards. Coverage includes a newly created SMART greeting, exact
canonical Speech synthesis, repeated lookup, access scoping, a resumed reply
outside the current-state selector, a non-speech tail, and silence after a user
utterance or empty history. Providers are mocked and the local MySQL schema is
disposable; the developer database is untouched.

Verification on features/needforspeed:

- 27 Java cases: ScopedBehaviourSpeechServiceUnitTest,
  ScopedDemoControllerIntegrationTest, BehaviourSpeechControllerWebMvcTest,
  SpeechProgressiveHttpIntegrationTest and EventHistoryOrderPersistenceTest.
- 11 Node cases: `node --test tests/js/speech/*.test.mjs`.
- 18 Playwright cases: valerian-transcription.spec.mjs and
  progressive-speech.spec.mjs against static local assets. The extended resume
  case confirms repeated Start Transcription replays before opening input.
  Cockpit API/WebRTC/media are mocked; the three native MP3 cases exercise real
  Chromium playback with a withheld HTTP tail, Stop and buffered fallback.

All passed, with zero failures/skips. Logs are in ignored target/resume-*.log.
No live provider latency, physical acoustic output or Heroku proxy streaming
measurement was performed. README records which improvements are automatic,
which cockpit settings apply, and why parallel guards remain an opt-in trial.

Merged feature commit 889f170 into agents for the authorized Heroku test
deployment. Repeated the same 27 focused Java cases successfully on the merged
branch. Only documentation conflicted; the canonical history fix and tests
merged unchanged.

## Heroku testing follow-up (Milestone 170, 2026-09-17)

The user explicitly designated Heroku as the test environment and requested
deployment with Sol/Luna at none. The feature branch now sets all five purpose
routes in openai-prod.properties: behaviour/nonverbal use gpt-5.6-sol;
decision/extraction/summary use gpt-5.6-luna. Global GPT-5.2 remains the fallback.
This supersedes the earlier opt-in-only deployment decision, not its historical
measurement results. Official model pages reconfirm both models support Chat
Completions, structured outputs and none effort; account-specific access and
response quality still require the Heroku trial.

Prepared the reusable hand-sign compatibility fix on the feature branch before
integrating into agents. Combined generation recognizes authored nonverbal
envelopes, preserves top-level motion, normalizes scissors to scissor, and strips
unsupported move/turn fields. The agents branch's existing hand-sign regression
case is ported to the one-request contract; optional other channels stay intact.

Passed 23 focused Java cases with InferenceRoutingUnitTest,
OpenAILanguageModelGatewayHttpUnitTest, PromptPolicyGestureUnitTest,
CatalogInferenceCountUnitTest and LiveTranscriptionSettingsNormalizerTest.
The production-profile context test loads the actual property file without a
database/provider and resolves all five routes; provider payload checks use
loopback HTTP. This is configuration verification, not live quality evidence.
Cockpit local VAD accepts 1.0 seconds as a custom setting while stopped; changing
it does not change the separate provider-delay setting.

## Integrated result (NFS-08 / Milestone 169, 2026-09-16)

All five approaches are implemented on `features/needforspeed`. Deterministic
acceptance passed; live quality and the two-second voice target are **unverified**.
This branch has not been deployed or merged. The rollout choices are:

| Approach | Implemented comparison | Evidence and current choice |
| --- | --- | --- |
| Combine generation | Separate speech/nonverbal -> one structured plan | One generation request; malformed plans fail once without partial publication |
| Combine compatible guards | Ordered checks -> one validated boolean group | Default on this branch; Java preserves priority/actions; live contextual accuracy NOT RUN |
| Purpose/model/effort routes | Global GPT-5.2 fallback -> opt-in Sol/Luna at none | HTTP/configuration tests pass; existing local model settings unchanged; live candidate access/quality NOT RUN |
| Progressive audio | Wait for complete Blob -> MP3 MediaSource | Real Chromium playback advances before withheld response EOF; same-response fallback and Stop pass |
| Turn completion | 1500ms/medium -> optional 800ms/low | Offline commit delay falls 700ms, but natural-pause integrity fails; 1500ms/medium remains default |
| Parallel eligible guards | Ordered/combined -> parallel/combined_parallel | Bounded workers and ordered application pass; extra speculative requests measured; parallel remains opt-in |

The one-change-at-a-time request counts are preserved in NFS-03/04/07 below.
The complete combined path gives these counts, excluding transcription and Speech:

| Workload | Baseline main 177ee34 | Integrated combined path |
| --- | --- | --- |
| Ordinary healthcare/core conversation | 4 | 2 |
| Role clarification continuation | 6 | 2 |
| RPS readiness conversation | 5 | 2 |
| Prompt startup / direct facial-social reaction | 2 | 1 |
| SMART outer / inner close, including extraction | 3 / 4 | 3 / 3 |
| Deterministic RPS result / Talk to Me | 0 | 0 |
| Live voice-response p50/p95 and per-stage breakdown | NOT RUN | NOT RUN |
| Live text/transcription/Speech usage, cost and error rate | NOT RUN | NOT RUN |
| Frozen labelled guard/extraction accuracy and human response review | NOT RUN | NOT RUN |

Zero live samples were collected. No finite live-run budget or physical acoustic
fixture was established, so the roadmap's offline-only acceptance path was used.
The frozen quality cases are labels for future provider/human evaluation, not
evidence that a model passed them. Deterministic guard tests prove selection,
history, priority, failure and action contracts using controlled answers.
No p50/p95 from mocks is presented as a provider speedup. Pricing and currency
are consequently not applicable to this run. Multiple speakers, real room noise,
Bluetooth/selected physical outputs, Azure, other browser engines and deployment
proxy buffering remain NOT RUN. The longer synthetic hesitation also fails the
conservative VAD setting; use longer custom timing/manual turns when needed.

### Final verification and corrections

- Full Java suite: **281 tests, 79 classes, zero failures/errors/skips**.
  Final run exited successfully without the earlier Surefire shutdown timeout.
- Shared performance/speech/transcription JavaScript suites: **45 passed**.
- Integrated Playwright matrix: **31 passed**, including real native MP3 media
  progression/cancellation, scoped Talk to Me DB persistence, Valerian lifecycle,
  replay/ownership/reconnect, settings, columns and API Workbench. Most Valerian
  API/WebRTC/media fixtures are mocked; only the native progressive tests exercise
  actual decoding, and the Java/ Talk to Me cases establish database contracts.
- Inspected desktop/mobile playback and pace-control artifacts. Corpus hashes
  below remain unchanged; Git attributes now preserve fixture JSON LF and binary
  audio on Windows checkouts.
- Integrated tests caught a deterministic Talk to Me gateway-options interaction;
  guard preparation now bypasses the gateway when there are no eligible checks.
- Reload tests exposed ambiguous timestamp ordering. New events get an internal
  append position; an isolated MySQL test forces identical timestamps and verifies
  order after reload/removal, plus unchanged legacy IDs/dates and public JSON.
  Nullable `event.history_position` is an additive schema change. No backfill or
  developer-database change was performed; README documents writer upgrades.
- The SSE replay test now uses immediate test-server shutdown, avoiding a graceful
  drain of long-lived fixture subscriptions. Production shutdown is unchanged.
- Removed the superseded unbounded audio-Blob timing helper. Added an offline
  report utility that separates missing/invalid timing and unknown provider usage,
  and a reusable loopback provider for database/browser smoke tests.

Environment: Windows; Java 24.0.2 targeting Java 21; Node 24.13.1; Playwright 1.61.1;
Chromium 149.0.7827.55; local MySQL 9.4, disposable schema
`prometheus_nfs_f71ad024` and restricted account. Browser test app bound to
127.0.0.1:8087 with synthetic provider on 8091, dummy credentials, scheduler off,
global GPT-5.2 label and combined guards. Provider name in a stub request does
not establish execution of that model. No user content or paid provider traffic.
Test output and screenshots are local ignored artifacts under `target/` and
`test-results/`; the durable evidence is this record and the regression tests.
After verification, the temporary servers were stopped and the disposable schema,
restricted account and temporary credential file were removed.

Commands actually run after verifying the isolated datasource and overriding
provider URLs with loopback fixtures/fail-closed port 9 (secrets omitted):

```powershell
.\mvnw.cmd -q test
node --test tests/js/performance/*.test.mjs tests/js/speech/*.test.mjs tests/js/transcription/*.test.mjs
# Separate processes: provider stub and Spring test app with isolated env settings.
node tests/needforspeed/provider-stub.mjs
.\mvnw.cmd -q spring-boot:run
$env:PROMETHEUS_BASE_URL='http://127.0.0.1:8087'
$env:PROMETHEUS_SKIP_WEBSERVER='true'
# PROMETHEUS_ADMIN_TOKEN matches the synthetic app's token.
npx.cmd playwright test --config=playwright.config.mjs tests/playwright/progressive-speech.spec.mjs tests/playwright/valerian-transcription.spec.mjs tests/playwright/valerian-lifecycle.spec.mjs tests/playwright/valerian-column-expansion.spec.mjs tests/playwright/talktome.spec.mjs tests/playwright/apiworkbench.spec.mjs
```

Future live acceptance must use a bounded synthetic run (including speculative
requests/cancelled usage), fixed workload/configuration and at least 50 warm
reference turns per configuration, with cold/closing/reconnect turns separate.
Export timings as described in README and run `tests/needforspeed/summarize.mjs`.
Record individual critical-case disagreements and human response review before
promoting model/guard changes. A provider benchmark and acoustic check must
establish the actual median/p95; the request-count reduction alone cannot.

## Baseline (NFS-01 / Milestone 162, 2026-09-16)

Baseline source: main `177ee34`. Text endpoint: Chat Completions; configured
model GPT-5.2; no explicit reasoning effort. Speech: gpt-4o-mini-tts, MP3,
buffered browser playback. Local silence 1.5 seconds, transcription delay medium.
No runtime defaults changed in NFS-01.

Environment: Windows, Java 24.0.2 (project targets Java 21), local Chromium via
repository Playwright. Browser assets served by Python on 127.0.0.1:8086;
API, WebRTC, microphone and audio playback mocked. No database was used for
these unit/browser checks. Speaker, network-to-provider and deployment proxy:
NOT RUN. In-app browser tool failed during bootstrap with missing sandboxPolicy;
repository Playwright was used instead.

| Evidence | Result |
| --- | --- |
| Ordinary healthcare and core conversation | 4 sequential text calls |
| Role clarification continuation | 6 text calls |
| RPS readiness conversation | 5 text calls |
| Prompt greeting or facial/social reaction | 2 text calls |
| SMART outer / inner closing | 3 / 4 text calls |
| Invalid nonverbal fallback on ordinary SMART | 5 text calls |
| Talk to Me / non-reactive weather | 0 text calls |
| Live median / p95 voice response latency | NOT RUN |
| Live tokens, cost, error rate | NOT RUN |
| Live classification / response quality | NOT RUN |
| Physical acoustic and Bluetooth latency | NOT RUN |

Counts are asserted by CatalogInferenceCountUnitTest against all twelve catalog
factories with a scripted gateway. They are not fake-provider latency estimates.
The controlled browser test joins a finalized transcript, HTTP trace header,
canonical SSE event, first fetched byte and mocked media playing event. This
proves instrumentation wiring; it does not prove audible or progressive playback.

Commands run:

```powershell
.\mvnw.cmd -q "-Dtest=LatencyTraceUnitTest,CatalogInferenceCountUnitTest,PrometheusCorsConfigurationWebMvcTest,PromptPolicyGestureUnitTest,OpenAILanguageModelGatewayMessageMappingUnitTest,OpenAILanguageModelGatewayHttpUnitTest,OpenAISpeechSynthesisGatewayUnitTest" test
node --test tests/js/performance/*.test.mjs tests/js/transcription/*.test.mjs tests/js/speech/*.test.mjs
# Static server in public/, no provider/database; env overrides avoid the dev app.
$env:PROMETHEUS_BASE_URL='http://127.0.0.1:8086'
$env:PROMETHEUS_SKIP_WEBSERVER='true'
npx.cmd playwright test --config=playwright.config.mjs tests/playwright/valerian-transcription.spec.mjs --grep 'mocked WebRTC emits'
```

JavaScript: 33 tests passed. Browser: 1 scenario passed after fixing a dropped
timing field in the final-transcript callback. Java: 17 focused tests and 19 shared browser/static contracts passed; the HTTP
gateway test passed again after final logging adjustments. Syntax and diff
checks passed.

## Frozen synthetic corpus

`tests/fixtures/needforspeed/quality-v1.json` contains fourteen labelled cases,
including contextual yes, first refusal, explicit stop, goal commitment and
completion, both guessing roles, intro progression, facial and social context.
Guard labels describe semantics; Java retains outer/transition/decision priority.
Expected extraction predicates distinguish exact fields from grounded free text.
Human response review and candidate-provider execution remain NOT RUN.

Two local synthetic WAVs contain the same sentence with a 400 ms or 1100 ms
inserted pause. `pauses-v1.json` documents provenance and expected natural turns.
They are reproducible frozen inputs, not recordings of users. They do not
establish suitability for older adults, noisy rooms or sequential speakers.

SHA-256 (do not alter the corpus to make a candidate pass):

- `pause-hesitation.wav`: `9a72500f72cfc296c9c1d509db0e1e51e5a15edc1319694fecaa8b76816a7033`
- `pause-short.wav`: `2efc876d8f87c51b99ad17e96df6e0dfc5e51962d4160fd7c1c158bc846752f2`
- `pauses-v1.json`: `4192eed92fa9678f6bb987a870c4e4bca45149607d2e5b53e7e9fba612e372dc`
- `quality-v1.json`: `9884bbe0cd1bd886888d06bde666ceb706227c81d6b66b23c46eaa46d31f6924`

## Collecting comparable evidence

Use a fresh synthetic agent or an isolated database copy, fixed labelled inputs,
model/effort/strategy and voice/speed settings. Keep warm, cold, transition and
ordinary turns separate. Export only `PrometheusTimings.snapshot(agentId)` from
Valerian; join server `latency trace=...` lines by opaque trace ID. Browser stages
use performance.now(); server durations use nanoTime(). Never subtract clocks
across processes. Voice latency is audio_playing minus last_voice; report missing
last_voice for manual/unmatched commits rather than inventing it. A media playing
event is a proxy, so device latency still requires an acoustic measurement.

Report sample count, p50/p95, errors and absent stages, provider request/token
counts, pricing date and currency if costs are calculated, browser/device/network,
and corpus hashes. Run at least 50 reference turns per live configuration if
credentials and a finite run-local request/token cap are available. Retain NOT RUN
for missing live or hardware evidence. The two-second target has not been tested.


## NFS-02 / Milestone 163

22 focused Java tests passed, followed by 7 routing/HTTP tests after final
validation changes. No inference call-count reduction yet. Per-purpose routes
and explicit effort are opt-in; local OpenAI credentials/configuration unchanged.
Sol/Luna Chat Completions and none effort verified from official model docs;
actual account/model access, quality, tokens/cost and latency: NOT RUN.

Payload comparison caveat: reasoning-family requests now omit optional sampling
parameters. The original GPT-5.2 decision payload supplied temperature zero.
Use the baseline revision for a faithful original-payload live comparison rather
than assuming the new fallback configuration is byte-identical to that baseline.

Commands: `.\mvnw.cmd -q "-Dtest=InferenceRoutingUnitTest,OpenAILanguageModelGatewayHttpUnitTest,OpenAILanguageModelGatewayMessageMappingUnitTest,PromptPolicyGestureUnitTest,CatalogInferenceCountUnitTest,StateTransitionUnitTest,StateTransitionSnapshotUnitTest,AgentOuterStateRoutingUnitTest,AgentNestedOuterStateRoutingUnitTest" test`
and the focused rerun with `-Dtest=InferenceRoutingUnitTest,OpenAILanguageModelGatewayHttpUnitTest`.


## NFS-03 / Milestone 164

| Workload | Baseline calls | Combined generation calls |
| --- | --- | --- |
| Ordinary healthcare/core conversation | 4 | 3 |
| Role clarification continuation | 6 | 5 |
| RPS readiness conversation | 5 | 4 |
| Prompt greeting / direct sensory reaction | 2 | 1 |
| SMART outer / inner close | 3 / 4 | 3 / 4 |
| Malformed nonverbal result | Up to 3 generation calls | One request, explicit failure |
| Talk to Me | 0 | 0 |

11 policy/catalog tests, 10 scoped database integration cases and 31 neighboring
contract tests passed. Database target was the newly created local disposable
schema prometheus_nfs_f71ad024 with a restricted account, never the developer
schema. All providers were mocked. Stored custom task/nonverbal instructions
survived flush/clear/reload; one combined request published the same event ID
used by exact-speech synthesis. No persisted schema change or agent recreation.
Live latency, tokens/cost, model response quality and acoustic evidence: NOT RUN.

Exact suites and outcomes are recorded under Milestone 164 in PROJECT.md.

## NFS-04 / Milestone 165

Ordinary catalog conversation: 2 text calls (one guard group, one behaviour),
versus baseline 4 for healthcare, 6 for role clarification and 5 for RPS readiness.
SMART outer/inner close: 3/3 including required extraction and final speech.
Talk to Me remains 0. Guard groups may evaluate lower-priority checks that ordered
short circuiting would skip; compare tokens as well as requests in live runs.

18 focused Java cases and 12 isolated-database cases passed. The initial role
fixture missed the second outer predicate; corrected to all five catalog checks.
No production schema or local model configuration changed. DB suites completed
assertions but emitted a Surefire process-shutdown timeout. Live guard corpus,
response review, latency, provider cost, and model access remain NOT RUN.

## NFS-05 / Milestone 166

Real Chromium MP3 playback advances past 0.2 seconds before the local HTTP server
releases its final bytes. Browser download completion is still absent at that
point. Native cancellation closes the held response; unsupported MSE buffers one
response and plays it. A separate real Tomcat + loopback-provider check confirms
backend first-byte delivery while provider EOF is blocked by a latch.

Passed 11 Node tests, 29 Java cases (streaming + speech contracts), 17 browser
cases, and one visual rerun. Desktop/mobile playback status controls inspected.
The 73,351-byte MP3 fixture is two repetitions of the frozen pause-short.wav,
encoded locally with lameenc 1.8.4/LAME at mono 64 kbps. It is synthetic fixture
speech, not a live-provider result. MP3 SHA-256:
3c16bf65f43ce6444d1f2f157e2e88ed8a60b3e1f18f96f0374873f076828459.

No production-provider p50/p95, physical audio, reverse-proxy or non-Chromium
results were collected. The two-second end-to-end target remains unverified.

## NFS-06 / Milestone 167

| Input | Labelled turns | 0.8s commits | 1.5s commits |
| --- | --- | --- | --- |
| Frozen short pause | 1 | 2 | 1 |
| Frozen long hesitation | 1 | 2 | 2 |
| Short pause + deterministic low noise | 1 | 2 | 1 |
| Two short-pause utterances, 2.5s gap | 2 | 4 | 2 |

Each commit waited exactly the configured 800/1500ms from the last voiced
sample. No missing segments; extra segments are premature splits, not duplicate
provider events. These fixtures include synthesizer phrase-end silence in
addition to the labelled inserted pause. Sampling uses 1024 PCM samples every
50ms at the WAV's rate; it is an offline VAD approximation, not browser acoustic
or ASR evidence. The added noise is deterministic and quiet; sequential utterances
use the same synthetic voice and do not establish multiple-speaker behavior.

Responsive fails this natural-pause corpus. It remains an explicit option for
steadier speech; the default stays 1.5s/medium. Longer custom timing/manual turns
remain available. ASR errors, real healthcare/far-field speech, provider low-delay
quality/latency and acoustic end-to-end latency: NOT RUN.

Verified 28 Node, 13 Java and 15 browser cases, including keyboard/mobile settings,
reconnect retention and the shared multilateral path. Reproduce the replay with
node tests/needforspeed/replay-vad.mjs target/nfs06-vad.json.

## NFS-07 / Milestone 168

| Ordinary continuation | Current ordered | Combined | Parallel |
| --- | --- | --- | --- |
| SMART coaching | 3 | 2 | 3 |
| Role clarification | 5 | 2 | 6 |
| RPS readiness | 4 | 2 | 5 |

Counts include combined behaviour generation; the baseline revision had separate
speech/nonverbal requests. Parallel tests use a barrier to ensure all eligible
speculative checks actually dispatch, including normally short-circuited guards.
Combined groups and independent calls are never dispatched redundantly.

32 focused unit cases, 12 local DB cases and 3 domain tick cases passed. The
concurrent DB test retained both same-agent inputs, allowed another agent to
proceed while inference was blocked, extracted closing storage once and reset
correctly after reload. Database target remained prometheus_nfs_f71ad024.

No live model-quality, provider p50/p95 or token/cost comparison. Parallel mode
remains opt-in; combined is the default. Cancellation can stop the local wait
without eliminating already billed provider work. Missing cancelled-request
usage must be counted as unknown, never zero. Runtime mutation serialization is
in-process; multiple backend instances need an additional coordination design.
