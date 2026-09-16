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
