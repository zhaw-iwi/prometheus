# Need for speed: evidence record

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
