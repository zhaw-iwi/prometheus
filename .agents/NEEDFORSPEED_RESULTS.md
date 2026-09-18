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

## Server speech delivery timing (Milestone 183, 2026-09-18)

The user's 13:56 trial for fe3cd0bb-66de-4497-b215-34c909477f41 recorded 14
replies, each with a confirmed interruption after exactly 4,800 consumed samples
(200 ms). The first browser delivery burst was 9,600 bytes. Confirmed gaps ranged
from 61 to 627 ms (median 163 ms); no producer backpressure preceded them, and
received/consumed sample totals matched. This places the immediate problem at
early delivery starvation, but does not locate the pause between provider,
server and browser. Original trial logs remain untouched.

Added optional server instrumentation at SpeechAudio's existing read/write/flush
loop. Each synthesis receives a fresh ID and an independent monotonic timeline
starting after provider headers, including async dispatch, provider-body reads,
output writes/flushes, stream closure, cumulative bytes and unfinished/error phase.
Neither audio bytes nor exception messages enter the trace. The ordinary streaming
loop, provider request, PCM 60 ms prefill/refill and two-second queue are unchanged.

Valerian enables delivery timing automatically on tracked live canonical speech
requests (PCM or MP3). A header identifies the trace; one scoped background GET
after download/teardown attaches it to the existing turn's speechDelivery field.
Playback and microphone reopening never wait for that GET. The timing drawer
shows retrieval/stream status and maximum recorded waits; JSON retains operations,
CSV records coverage. A timeout, missing/expired trace or invalid snapshot remains
explicit and does not become a speech failure. Late retrieval cannot recreate
cleared turns or attach to another agent. Startup replay remains outside turn logs.

The service checks current access-code/agent visibility before looking up the
agent/event/synthesis identity. Storage is in memory only: at most 128 traces,
ten-minute expiry with pruning on access, and 256 operations per trace preserving
the first/latest halves with dropped counts. A restart or another instance can
make detail unavailable; no durable job/store is introduced. Trace status can be
streaming after early teardown; the single fetch does not promise final completion.

Interpretation: long server reads locate application waiting on the provider
response, including upstream transport/client buffering. Long writes/flushes
locate waiting toward servlet output. Prompt writes with later browser delivery
point downstream. Match cumulative byte positions despite differing read sizes,
compare intervals within each clock, and never subtract server and browser times.
Flush completion does not establish browser receipt, OpenAI generation time or
physical audibility. No live cause or latency improvement is claimed yet.

Verification: 60 focused Java cases passed, including deterministic read/write/
flush separation, failure phase, retention/expiry, scoped HTTP contracts, CORS,
legacy speech paths, and real provider-to-Tomcat progressive PCM/MP3 delivery with
withheld tails. Another 13 scoped controller integration cases passed on disposable
local MySQL schema prometheus_delivery_d95e2bfb78 with a restricted task account;
both schema/account were removed. All providers were mocked or loopback fixtures.
Passed 69 Node performance/speech/transcription cases and four Playwright cases
(PCM/export and timing drawer at 1440/390px). Desktop/mobile delivery-detail
screenshots were inspected. The Java set is focused regression, not the full suite.

Commands actually run:

- mvnw.cmd -q "-Dtest=*Speech*UnitTest,*Speech*WebMvcTest,SpeechProgressiveHttpIntegrationTest,SpeechArchitecture*ContractTest,PrometheusCorsConfigurationWebMvcTest,ValerianClientStaticResourceContractTest" test
- python target/run-speech-delivery-db.py (isolated ScopedDemoControllerIntegrationTest)
- node --test tests/js/performance/*.test.mjs tests/js/speech/*.test.mjs tests/js/transcription/*.test.mjs
- npx playwright test tests/playwright/valerian-transcription.spec.mjs --grep "PCM format choice|timing drawer" --output=target/speech-delivery-browser-results

Browser fixtures used the local static server with PROMETHEUS_SKIP_WEBSERVER=true.
Logs/screenshots are under target/speech-delivery-*. No live paid synthesis was
performed. Next trial: refresh Heroku, retain Ultra Responsive/Automatic and the
unchanged buffer, clear timing, speak normally, wait for the final reply and for
Server audio delivery's retrieval status to be received, then export JSON. This
measures the same playback policy with the additional server evidence.

## PCM interruption diagnostics (Milestone 182, 2026-09-18)

The user's 13:08 trial for fc140c0e-eda6-43f2-b420-b1f0d3bce211 contains eight
ordinary turns and one closing turn, all Ultra Responsive/minimal and PCM. Ordinary
speech-end-to-renderer-start averaged 4.028 s; first-byte latency averaged 0.672 s.
Every reply recorded one interruption (88-269 ms). The previous Ultra/MP3 trial
averaged 4.305 s overall and 1.116 s to first bytes, but different workloads and
playback markers prevent a controlled causal comparison. Original logs untouched.

Added automatic PCM detail to the existing content-free turn collector/export.
Body-read byte counts, read waits, inter-read gaps, outstanding producer frames,
numbered sample-block posting/receipt and backpressure waits reveal delivery order.
Renderer observations include precise audio frames, consumed source positions,
occupancy, provisional starvation, confirmed resume/gap, and final drain. EOF,
stop/failure and browser-reported preparation latency remain distinguishable.
Browser receipt times and AudioContext clocks are documented separately; readings
cannot isolate provider compute from transport or certify physical audibility.

Delivery and renderer lists retain 256/64 entries respectively, preserving startup
and the latest half with explicit dropped counts. Only whitelisted numeric fields
and fixed event types survive collection; no audio, transcript, credentials, URL,
device ID or free-form error is retained. Joining before/after acknowledgement
uses the existing agent/event identity. Delivery capture does not rerender the UI
per chunk. The drawer displays interruption positions; CSV records coverage counts,
and JSON carries the full retained trace and its interpretation. Playback policy,
models and transcription settings are unchanged.

Verification: 66 Node tests passed across performance, speech and transcription.
Six Playwright cases passed: four native PCM cases (including held-response gap,
exact starvation/resume positions, completion, Stop and malformed tail), and two
desktop/mobile cockpit cases with PCM capture, JSON download and MP3 comparison.
Passed 22 Java static/browser architecture contract cases. Sources/providers were
local synthetic fixtures; no live inference or database access was needed. The
desktop/mobile detail screenshots were inspected, with two cockpit cases rerun
after the panel grouping refinement. Logs/screenshots: target/pcm-diagnostics-*.

Commands: node --test tests/js/performance/*.test.mjs tests/js/speech/*.test.mjs
tests/js/transcription/*.test.mjs; mvnw.cmd -q
"-Dtest=ValerianClientStaticResourceContractTest,SpeechArchitectureBrowserClientContractTest,SpeechArchitectureSourceContractTest" test;
npx playwright test tests/playwright/pcm-speech.spec.mjs
tests/playwright/valerian-transcription.spec.mjs --grep "native PCM|PCM format choice".
Playwright used the local static server and PROMETHEUS_SKIP_WEBSERVER=true, with
output directed into target/pcm-diagnostics-browser-results.

Next trial: refresh Heroku, select Ultra Responsive and Automatic, clear the timing
log, speak normally, wait for the last reply to finish, then export JSON. Detail is
automatic. Mark any audible gaps in the feedback; the browser trace alone does not
establish what reached the listener. The interruption cause and any future buffer
adjustment remain unverified; this milestone deliberately preserves the 60 ms
prefill/refill so the next trial measures the same playback policy.

## Progressive PCM speech and format comparison (Milestone 181, 2026-09-18)

Implemented PCM alongside the existing MP3 path. The canonical behaviour-speech
endpoint accepts format=mp3|pcm, defaults to MP3, and preserves exact persisted
speech and scoped event identity. PCM responses declare 24 kHz mono signed 16-bit
little-endian samples. Unknown formats fail before provider access; Talk to Me
retains MP3. Existing gateway implementations can retain their MP3-only SPI method.

Continuous now exposes the existing Speech Output Settings (previously hidden in
Sensing), with persisted Automatic/MP3 selection. Automatic prepares a 24 kHz
AudioContext, selected speaker and AudioWorklet before choosing the provider format.
Unsupported capability or failed preparation selects MP3 before synthesis; there
is no second request or spoken-prefix replay after streaming starts. A selected
speaker that cannot be applied is never silently replaced during playback.

PCM uses a two-second bounded renderer queue, 60 ms initial/refill target, and
producer backpressure. The decoder handles split 16-bit samples and rejects a
truncated final sample. Buffer starvation followed by further speech counts as an
interruption; initial waiting and silence after the final audio do not. Stop closes
the stream and renderer. EOF drains reported output latency (100 ms fallback when
unavailable) before teardown/input reopening. Setup deadline is two seconds;
network/playback stalls are bounded at 30 seconds; utterances are capped at 16 MiB.
The pre-existing queue owns gating, output leases and live/startup delivery rules.
PCM honors the media control's stored volume/mute and uses Stop Playback rather
than a native file seek control while active.

JSON/CSV include effective/requested format, fallback reason, preparation duration,
buffer target/initial audio amount, interruption count and inserted silence. PCM
playback marks receipt of the renderer's first-consumed-samples notification;
MP3 uses HTMLMediaElement playing. Neither measures physical audibility. Compare
format-specific markers with preparation and end-to-end timings, and listen for
gaps/clipping. PCM transfers more data; no provider or production speedup is claimed.

Verification:

- 37 Java cases: mvnw.cmd -q "-Dtest=*Speech*UnitTest,*Speech*WebMvcTest,SpeechProgressiveHttpIntegrationTest,SpeechArchitecture*ContractTest" test.
  Controlled providers cover format/default validation, canonical text, MIME
  metadata, failed access and early-byte delivery through Tomcat before EOF.
- 16 database cases: ScopedDemoControllerIntegrationTest and TalkToMeScopedIntegrationTest,
  run with disposable local MySQL schema prometheus_pcm_4ddd0755a2 and a restricted
  account. Both removed on completion. Inference/speech mocked, fallback URLs on
  loopback and dummy provider credentials. MP3 and reloaded-startup PCM routes pass.
- 63 Node cases: node --test tests/js/performance/*.test.mjs tests/js/transcription/*.test.mjs tests/js/speech/*.test.mjs.
  Includes controlled PCM decoding, ring wrap, prefill, bounded backpressure,
  interruption counting, cancellation, setup/device failure, truncation, oversize,
  stream timeout, output interruption and content-free timing export.
- 16 Playwright cases: three native PCM and three native MP3 tests with withheld
  HTTP tails, plus ten cockpit tests covering selected speaker/voice/speed/format,
  persisted MP3 comparison, input gating, reset/startup, output controls, reconnect
  and timing exports. Native worklet tests consume real synthetic tone samples;
  cockpit tests mock media/provider boundaries. Desktop/mobile screenshots inspected.
- Final output-drain and compact-header refinements: eight PCM Node and five PCM
  browser cases passed. No live provider, physical speaker or Bluetooth test.

Ignored evidence: target/pcm-java.log, target/pcm-database.log,
target/pcm-client.log, target/pcm-native-browser.log, target/pcm-native-final.log,
target/pcm-cockpit-browser.log, target/pcm-unit-final.log, target/pcm-final-browser.log.
Screenshots: target/playwright-results and target/pcm-final-browser-results.
The original user timing exports in test-results are preserved.

Trial: refresh Heroku, choose Ultra Responsive and keep speech voice/speed fixed.
Under Continuous → Speech Output Settings, compare Automatic with MP3 using similar
utterances; export each session and confirm its effective format. Separate startup
from ordinary/closing turns. Automatic may legitimately report MP3 on unsupported
browsers or speaker configurations. Official rationale and browser mechanics:
https://developers.openai.com/api/docs/guides/text-to-speech#streaming-realtime-audio
and https://www.w3.org/TR/webaudio-1.1/.

## Minimal Ultra Responsive and transcription timing (Milestone 180, 2026-09-17)

Ultra Responsive now selects 0.5-second local silence and minimal provider delay.
Saved values are preserved: an older 0.5/low selection becomes Custom; reselect
Ultra Responsive while stopped after refreshing the cockpit to use 0.5/minimal.
The default remains Pause tolerant. Session start and reconnect send the chosen
settings unchanged; the shared multilateral control uses the same preset.

The existing committed stage remains the local VAD boundary. New commit_sent
records successful local send (also for manual turns); commit_acknowledged is
the browser receipt of input_audio_buffer.committed. transcript_first_delta and
transcript_last_delta record accepted non-empty deltas, including those received
before the acknowledgement. final_transcript records actual completion receipt,
before ordered release/ingress waiting. All times use the browser monotonic clock.
Duplicate acknowledgements cannot consume the following pending commit; duplicate
or late deltas/finals cannot move earlier timestamps. Timing maps remain bounded
and clear at epoch changes. No transcript content enters timing records.

The panel, CSV and offline summarizer add send-to-acknowledgement,
acknowledgement-to-final, first-to-last-delta and last-delta-to-final intervals;
JSON includes the raw stages. Timeline offsets can be negative for pre-speech-end
deltas. Missing stages in old recordings or provider event sequences stay unknown.
These are browser-observed intervals including network/provider effects, not a
measurement of isolated provider processing. Overlapping intervals are not additive.

Verification passed:

- node --test tests/js/performance/*.test.mjs tests/js/transcription/*.test.mjs
  tests/js/speech/*.test.mjs: 54 cases, zero failures/skips. Controlled clocks cover
  pre-commit deltas, duplicate commits, out-of-order finals, missing receipts,
  epoch reset, manual commits, bounds, JSON/CSV/offline metrics and content exclusion.
- Three Playwright cases selected by conversation pace|interaction timing drawer
  in valerian-transcription.spec.mjs: desktop/mobile exports and keyboard preset
  selection, effective session payload, reconnect and manual mode. Static loopback
  assets with routed provider fixtures; 1440/390px screenshots inspected.
- Logs: target/transcription-precision-unit.log and
  target/transcription-precision-browser.log. Visual artifacts are under
  target/playwright-results; user timing exports in test-results are untouched.

No Java/database changes or live provider/acoustic tests. Tomorrow's export should
establish whether final receipt lags substantially behind the last partial and how
much time elapses before commit acknowledgement. No new latency gain is claimed.

## Ultra Responsive preset and next experiments (Milestone 179, 2026-09-17)

Follow-up trial (16:28 export): agent 7b57e224-3a60-4e9f-bef5-cb69313daf4b
completed six ordinary turns and one closing turn. All ordinary candidates were
reused and all playback was progressive, with no recorded errors or truncated
server timings. Ordinary averages compared with the preceding responsive/low run:

| Consecutive browser interval | Responsive / low | Ultra / user-reported minimal |
| --- | ---: | ---: |
| Silence detection | 0.825 s | 0.519 s |
| Commit to final transcript | 0.698 s | 0.698 s |
| Transcript submission to speech request | 1.537 s | 1.868 s |
| Speech request to first bytes | 0.950 s | 1.116 s |
| First bytes to playback | 0.173 s | 0.104 s |
| Total speech end to playback | 4.183 s | 4.305 s |

The shorter silence wait saved 306 ms, offset by slower model/speech requests in
this session. Closing took 5.934 s, with a 1.553 s decision and 1.822 s final reply;
the earlier 3.923 s closing-decision outlier did not recur. These small separate
conversations cannot establish a causal effect of minimal or transcript accuracy.

The export exposed a diagnostics bug: safeConfiguration accepted only low/medium/
high and silently omitted minimal/xhigh. The actual settings descriptor, session
payload and backend already support all five levels. Fixed the export filter
without changing provider settings or execution. The historical export cannot
independently confirm minimal; retain the user's reported selection and leave the
original file untouched. A capture-to-JSON/CSV regression reproduced the omission,
then all 12 performance Node tests passed after the fix, including privacy checks.
Logs: target/transcription-delay-export-before.log and
target/transcription-delay-export-after.log (ignored). No Java, browser rendering,
live API or acoustic test was needed for this metadata-only correction.

Added Ultra Responsive (0.5-second local silence, low transcription delay) to the
shared Valerian/multilateral settings panel. Presets still change only silence
and delay, retain unrelated preferences and reconnect settings, and are hidden
for manual turns. Pause tolerant remains the default. Relative to Responsive,
the configured silence interval is 300 ms shorter; live benefit and segmentation
quality remain to be measured. Closing-decision execution was left unchanged.

Passed ten focused Node settings/local-VAD cases and the existing Playwright
conversation-pace case (keyboard choice, effective session payload, reconnect,
manual mode), with desktop and 390px screenshots inspected. Browser testing used
loopback static assets and routed provider fixtures. Logs: ignored
target/ultra-responsive-unit.log and target/ultra-responsive-browser.log. No Java
or provider changes; no live audio/API benchmark was run.

The user's two new exports contain 13 unique turns (the six pause-tolerant turns
are repeated in the later export). All eleven ordinary turns reused speculative
behaviour. Ordinary speech-end-to-playback means: pause tolerant 5.08 s (five
turns), responsive 4.18 s (six), versus 5.63 s in the earlier Luna/compact responsive
trial. These are separate small conversations, not a controlled A/B comparison.
The two closing turns queued background actions with no blocking extraction in
their response spans. Eventual action completion is not established by the export.

Follow-up ideas only, not implemented here:

- Compare MP3 with true streamed PCM/WAV playback. The current gateway fixes MP3
  and the browser buffers other formats, so changing only response_format would
  remove progressive playback. Preserve selected output devices, cancellation,
  input gating and authoritative persisted speech. OpenAI recommends WAV/PCM for
  fastest response times, but the improvement in this browser/Heroku path needs
  measurement: https://developers.openai.com/api/docs/guides/text-to-speech.
- Compare existing transcription delay minimal versus low with the same silence
  interval and representative speech. All five delay levels are already exposed
  by the backend descriptor and passed to the provider. The documentation describes
  earlier partial text and an accuracy tradeoff, not guaranteed finalisation timing:
  https://developers.openai.com/api/docs/guides/realtime-transcription.
- Add commit-acknowledgement and first/last-delta timestamps to separate transport,
  partial availability and finalisation. Consider bounded read-only previews from
  partial text only if measurements show enough stable text arrives early; final
  transcripts must still control persistence, transitions and publication.

## Speculative conversational behaviour (Milestone 178, 2026-09-17)

Agents integration: merged feature commit 61dac83. The complete merged Java suite
passed **473 tests across 103 classes**, zero failures/errors/skips, with disposable
local MySQL schema prometheus_async_59cc207162 and controlled providers. The schema
and restricted account were removed; log: target/behaviour-speculation-agents.log.
Only documentation needed merge conflict resolution. Application definitions and
deployment workflow remain on agents; main is unchanged. Push to agents triggers
the authorized Heroku testing deployment.

Default-enabled OpenAI speculation overlaps eligible current-state behaviour with
transition decisions. An application-owned cache survives the separate HTTP
acknowledge/generate boundary and entity reload; workers receive only immutable
requests. Reuse requires committed input identity, reset epoch, effective prompt,
output contract and model route to match. All transitions (outer, inner, self and
final) invalidate immediately, and fresh generation never waits for stale work.
Required blocking actions still complete before new-state prompt composition.

Eligibility is conservative: user utterance, no-op regulation, ordinary
State/OuterState, PromptPolicy, the default assembler and known pure guards with
a model decision. Custom extension paths and sensory/deterministic paths remain
unchanged. Deterministic Talk to Me does not even consult gateway capabilities.
Defaults: two concurrent speculative requests, 64 cached candidates, 30-second TTL,
no worker queue. Saturation skips speculation without blocking required work.
Set PROMETHEUS_BEHAVIOUR_SPECULATION_ENABLED=false for a sequential comparison.

Candidate output uses the ordinary decoder and publication boundary only when
selected. A required failed/malformed candidate fails without retry or publication.
Obsolete candidates never publish, including when cancellation is ignored. Reset,
deletion, newer input, rollback and expiry discard cached work. No persisted future
or durable job is introduced; Heroku restart drops unfinished work as requested.

Full feature suite: **359 Java tests / 86 classes, zero failures/errors/skips**,
`.\mvnw.cmd -q test`, disposable localhost MySQL schema
prometheus_async_ff4e24ffe4 with a restricted account, mocked/loopback providers and
fail-closed external URLs. Schema/account removed. Log:
`target/behaviour-speculation-full.log` (ignored). The first full run exposed two
Talk to Me zero-gateway assertions; moving capability lookup after policy
eligibility fixed them, and the complete suite then passed.

Focused database coverage additionally passed 27 cases. New latch tests establish
actual decision/behaviour overlap, reuse after persistence reload, outer/inner/self
and final transition discard without waiting, required-action prompt dependencies,
reset, new input, rollback, regulation/sensory silence and malformed output without
publication. Unit tests cover worker saturation with foreground/other-agent
progress, ignored cancellation, expiry, nested policy composition, custom extension
barriers and required versus discarded failures. Existing scoped deletion, ingress,
replay, deterministic and action suites passed in the full run.

Client verification: **50 tests passed** with
`node --test tests/js/performance/*.test.mjs tests/js/transcription/*.test.mjs tests/js/speech/*.test.mjs`.
**Two Playwright cases passed** with the `interaction timing drawer` filter in
`tests/playwright/valerian-transcription.spec.mjs`, against a loopback static server
and routed fixtures at 1440px and 390px. JSON export scope/origin, explanatory text,
privacy and existing panel behavior passed; screenshots inspected. Logs:
`target/behaviour-speculation-client.log` and `target/behaviour-speculation-browser.log`.

Playwright now writes to target/playwright-results, keeping generated artifacts
separate from user timing exports. Its first run cleared the old default output
directory; all three user exports were restored from Downloads and their SHA
hashes verified. They remained intact after the rerun using the new output path.

Timing headers/export preserve completed speculative inference evidence with
scope=speculative and originTrace. Work may begin in an earlier request, so its
duration is not additive to HTTP latency; started/completed markers share a
request ID for CSV deduplication. Still-running discarded requests can complete
after response headers and require correlated server logs for usage. Cancellation
does not guarantee avoided provider computation or billing. No live latency,
response-quality or cost improvement is claimed by these offline tests.

## Background transition actions (Milestone 177, 2026-09-17)

Implemented the user's in-memory durability choice: no job table, restart replay
or automatic retries. New actions default to background execution; `.blocking()`
preserves required dependencies. All built-in action types can prepare immutable
work. RPS/gather/choice definitions explicitly block; summary/outcome actions run
in the background. Legacy persisted summary/outcome and summary-state actions
migrate on reload, while other unconfigured legacy actions keep prior ordering.

Turn-local admission reserves bounded capacity before persistence. Work starts
only after successful commit, FIFO per agent, with parallel progress for other
agents. Fresh transactional patches use the existing agent lock; reset epochs,
missing-agent checks and storage write tokens reject obsolete/conflicting output.
Accepted background writes rebase later queued writes without accepting newer
foreground changes. No worker receives an entity or persistence context.

Full feature Java suite passed 344 tests across 83 classes with zero failures,
errors or skips: `.\mvnw.cmd -q test`. Providers were mocked/loopback, and MySQL
used only disposable schema prometheus_async_a570e7ed1c plus a restricted account;
both were removed. Log: ignored `target/background-actions-full.log`.
After refining the queue deadline to begin at commit and adding a deterministic
clock test, 25 focused cases passed:
`.\mvnw.cmd -q "-Dtest=BackgroundAction*UnitTest,StateTransition*UnitTest,AgentTurnSerialiserUnitTest,*Rps*Test,CatalogInferenceCountUnitTest" test`.
Log: ignored `target/background-actions-unit-final.log`.

New tests prove farewell publication while extraction is unfinished, eventual
persisted result, explicit blocking, FIFO writes, other-agent progress, rollback,
reset/delete rejection, foreground conflict rejection, bounded admission, failure,
queue expiry and shutdown even when a worker ignores interruption. Existing
HTTP/SSE replay now explicitly declares its extracted-goal dependency blocking.

No client code changed, so no browser test was needed. Live provider latency and
quality remain unmeasured. Background completion after HTTP headers is recorded
in correlated server logs, not retroactively added to the browser timing export.
Limits, additive nullable columns and single-process writer scope are in README.
Speculative behaviour generation is not enabled by this milestone.

Merged feature commits f8d9c1b and 13ba6ed into agents. The complete merged Java
suite passed 459 cases across 100 classes, with zero failures/errors/skips, on
disposable schema prometheus_async_5f32ae018b (removed with its restricted account).
Log: ignored `target/background-actions-agents-full.log`. The first merged run
exposed three old replay fixtures: raw speech output and extraction preceding
farewell. Their data was updated on the feature branch and merged; application
definitions/workflows remain on agents. Source matches the verified feature
branch. Main remains unchanged; the agents push deploys Heroku for testing.


## Unified behaviour generation (Milestone 176, 2026-09-17)

Removed the raw speech branch from PromptPolicy. Every prompt-driven generation
now uses typed BEHAVIOUR / JSON_OBJECT inference and BehaviourPlanInference.
Speech-only policies request only `{"speech":"..."}`; configured nonverbal
policies retain the compact encoding and required-nonverbal validation. All six
Final constructors inherit the shared path without changing stored instructions
or inventing nonverbal defaults. Deterministic policies still use zero model
calls. Decisions/extraction/summarisation retain separate purposes.

Passed 95 focused Java cases covering policy/codec/application behavior, all
baseline catalog paths, final constructors, nested transitions, guard handling,
typed provider HTTP and malformed output with no partial publication:
`.\mvnw.cmd -q "-Dtest=PromptPolicy*UnitTest,CompactBehaviourPlanUnitTest,CatalogInferenceCountUnitTest,AgentApplicationServicePromptUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,*PromptContractTest,StateTransitionUnitTest,AgentOuterStateRoutingUnitTest,AgentNestedOuterStateRoutingUnitTest,GuardEvaluationUnitTest,ParallelGuardEvaluationUnitTest,OpenAILanguageModelGateway*UnitTest" test`.
Log: ignored `target/unified-behaviour-regression.log`.

Passed 14 additional cases in ScopedDemoControllerIntegrationTest (13) and
TransitionDecisionActionReplayIntegrationTest (1), using the disposable local
MySQL schema prometheus_unified_639c9f4fa0 and a restricted account. Both were
removed afterwards; developer data was untouched. The extended closing/reset
case reloads persisted agents, requires JSON farewell inference, checks canonical
speech-only history and verifies no raw completion call. The replay exercises
real HTTP/SSE and storage transitions. All providers are fixtures, no live model
or browser/acoustic measurement. Log: `target/unified-behaviour-database.log`.

This is contract harmonization, not a claimed speedup: JSON adds a small envelope
to speech-only replies. No background action or speculative generation is enabled
yet. Their dependency audit and proposed acceptance milestones are recorded in
`.agents/PLAN_TRANSITION_EXECUTION.md`; action restart durability awaits the user.

Integrated feature commit faa790c into agents, resolving documentation only.
Changed shared implementation/tests match the verified feature commit. All 94
cases across the eleven merged-branch `*PromptContractTest` classes passed,
including the application-specific catalog, with no failures/errors/skips:
`.\mvnw.cmd -q "-Dtest=*PromptContractTest" test`.
Log: ignored `target/unified-behaviour-agents-contracts.log`. Main remains
unchanged; pushing agents triggers the authorized Heroku testing deployment.

## Compact behaviour output (Milestone 175, 2026-09-17)

Implemented the format investigated in Milestone 174. Combined PromptPolicy
requests now ask for minified provider-only `speech`/`nv` JSON. Inside `nv`, `g`
is the gesture; `f`, `z` and `m` are exact face, gaze and motion pairs. Java
expands them before current canonical validation and publication. The `x` object
preserves full-name custom, partial, extended or null nonverbal values, including
posture/prosody/proxemics. Aliases and x may not define the same canonical field.
Top-level motion/display keep their full shape; nv.m remains distinct from
top-level motion. Existing gesture/hand-sign normalization and move/turn removal
continue after expansion. Speech whitespace, punctuation and multilingual text
are preserved. No speech shortening, expression presets or inferred defaults
were added.

This applies automatically to existing combined-generation policies on their
next turn, including their persisted custom prompts. The final encoding rule
changes representation only; authored task/nonverbal instructions remain in the
request. Speech-only and deterministic policies keep their paths. Canonical
provider output is still accepted to support authored prompts/custom gateways;
there is no repair request or second generation. Persisted/event/API/client
BehaviourPlan fields remain canonical.

The existing timing header/export now includes a content-free decode stage:
`behaviour_decode_compact` or `behaviour_decode_canonical`, with success/error.
These are nested inside behaviour generation and must not be added to its total.
Use these stages to check format adoption, and the inference span's actual
completion tokens/model/effort for the next Heroku comparison. Trace bounds and
missing/truncated evidence rules still apply.

Offline evidence: the same synthetic example from Milestone 174 has 232 minified
canonical characters versus 136 compact characters (41% fewer); the test compares
the entire expanded JSON tree, not only speech. Character savings are not token
or latency savings. Extra encoding instructions increase input size, and model
adherence/output length/quality and net latency require live measurement.

Passed 93 Java cases with no failures/errors/skips:
`.\mvnw.cmd -q "-Dtest=CompactBehaviourPlanUnitTest,PromptPolicy*UnitTest,PromptMessageAssemblerUnitTest,PromptEventContentAdapterUnitTest,OutputProfileUnitTest,CatalogInferenceCountUnitTest,MultimodalBehaviourPlanEmissionUnitTest,BehaviourPlanUnitTest,ValerianCorePromptContractTest,HealthcareUseCasePromptContractTest,InferenceRoutingUnitTest,OpenAILanguageModelGateway*UnitTest,LatencyTraceUnitTest" test`.

Coverage includes 37 compact-codec/policy cases, all twelve baseline catalog
definitions using compact responses, canonical compatibility, exact speech and
custom values, event/history serialization, strict failure without publication,
normalization, route preservation and real loopback HTTP from prompt to canonical
event plus privacy-safe timing export. Log: ignored
`target/compact-behaviour-regression.log`. No database, browser or real provider
was used; persistence/client schemas and assets were unchanged. Heroku model
adherence, output-token savings and acoustic/end-to-end latency remain unverified.

Integrated feature commit 1ee4f0f into agents. Only documentation conflicted;
all changed source and test files exactly match the tested feature commit.
On the merged branch, all 94 cases in the eleven `*PromptContractTest` classes
passed with no failures/errors/skips, including the application-specific agents
and their existing canonical provider fixtures. Command:
`.\mvnw.cmd -q "-Dtest=*PromptContractTest" test`.
Log: ignored `target/compact-behaviour-agents-contracts.log`. Main is unchanged;
the agents push triggers the authorized Heroku testing deployment.

## Luna behaviour trial and output-size investigation (Milestone 174, 2026-09-17)

The requested Heroku test configuration now routes behaviour and nonverbal to
gpt-5.6-luna at none, joining decision/extraction/summary. Separate purpose routes,
global GPT-5.2 fallback and the opt-in Sol/Luna template are retained. The mixed
model routing test still checks Sol behaviour alongside Luna decisions. This
change does not shorten speech, alter authored prompts or change model output
parsing, persisted plans, client contracts or speech synthesis settings.

The two user-provided 2026-09-17 timing exports contain 15 ordinary turns and two
closing turns. For ordinary turns, Luna decisions average 1,358 input / 19 output
tokens and 0.908 seconds; Sol behaviour averages 3,223 input / 82.6 output tokens
and 2.617 seconds. Luna's two closing extractions produce 120/139 output tokens
and take 1.753/2.671 seconds. These compare different workloads; they do not
isolate model speed. Exports contain usage metadata, not the response text, so
the actual speech-versus-JSON token split cannot be recovered from them.

Output investigation:

- PromptPolicy resolves nested outer/task instructions; PromptMessageAssembler
  supplies selected events as role-labelled messages plus context augmenters.
  BehaviourPlanInference adds authored nonverbal instructions and one JSON
  envelope requirement. Policies with no nonverbal prompt use plain speech.
- Combined output contains speech/nonVerbal, with motion/display only where
  explicitly required. Healthcare NONVERBAL_PLAN asks for gesture, face type
  and intensity, gaze direction and focus, and stillness/energy. It already
  omits top-level motion for ordinary coaching and forbids display. There is no
  universal requirement to emit every possible modality on every request.
- A compact provider-only encoding could shorten field names and encode fixed
  pairs as arrays, followed by deterministic expansion before current plan
  validation/publication. Synthetic example (not a recorded user utterance):

  Canonical (232 characters, already minified):
  `{"speech":"What small step feels manageable?","nonVerbal":{"gesture":"NONE","facialExpression":{"type":"gentleSmile","intensity":0.3},"gaze":{"direction":"toward_user","focus":"older_adult"},"motion":{"stillness":0.9,"energy":0.1}}}`

  Candidate (136 characters):
  `{"speech":"What small step feels manageable?","nv":{"g":"NONE","f":["gentleSmile",0.3],"z":["toward_user","older_adult"],"m":[0.9,0.1]}}`

  This preserves all values in the example and reduces characters by 41%, not
  a measured token or latency reduction. Tokenization, model adherence, extra
  mapping instructions and custom modality shapes need evaluation. A generic
  adapter must preserve authored capabilities, optional/partial modalities,
  posture/prosody/proxemics, top-level hand signs and display. It belongs at the
  policy inference boundary; no shorthand should reach storage or clients.
- Replacing full modality values with named expression presets could reduce
  output further, but constrains independent expression and requires per-agent
  authored presets. Omitting neutral/unchanged fields requires explicit reset
  semantics; it must not silently retain a previous gesture or facial state.
- Minified model output and omission of unnecessary null fields are smaller
  candidates. Server-side minification after receipt cannot reduce generation
  latency. A lower output-token cap risks truncating JSON and is not a substitute
  for a smaller representation. Existing healthcare speech is already brief.

Next evidence: repeat the same Heroku agent/preset with Luna and the unchanged
output format, comparing actual output tokens, latency and conversation quality.
Assess a compact codec separately with round-trip preservation and malformed
output tests alongside PromptPolicyGestureUnitTest, then provider measurements;
no compact codec is enabled by this milestone. Provider latency guidance:
https://developers.openai.com/api/docs/guides/latency-optimization

Verification: all nine cases in InferenceRoutingUnitTest (4) and
OpenAILanguageModelGatewayHttpUnitTest (5) passed with no failures or skips:
`.\mvnw.cmd -q "-Dtest=InferenceRoutingUnitTest,OpenAILanguageModelGatewayHttpUnitTest" test`.
The production-profile test loads the checked-in routes; the existing mixed
Sol/Luna test proves independent purpose overrides and global fallback remain.
HTTP tests use loopback fixtures, with no database or live model requests.
Log: ignored `target/luna-behaviour-routing-tests.log`. No UI or persistence
changes require browser/database tests. Live Luna behaviour quality, token
savings and latency improvement have not been measured.

Integrated feature commit 29444f1 into agents for the authorized Heroku test
deployment. Only documentation conflicted; the production properties and route
test match the tested feature commit exactly. Main remains unchanged.

## Interaction timing follow-up (Milestone 173, 2026-09-17)

The right-hand Agent & Diagnostics drawer now has an Interaction Timing tab.
It shows per-turn browser durations and a millisecond timeline, separate HTTP
requests and server spans, settings and actual playback mode. JSON exports
contain the detailed evidence; CSV provides comparison rows. Both exclude
conversation text, prompts, credentials, audio and device identifiers. Recording
is bounded to 128 turns in memory; the latest 20 are displayed. Reset/disconnect
retain recordings, explicit Clear/reload removes them, and startup/restart
assistant replay cannot alter a previous turn. Hidden panels do not rerender.

The old Transcript Sending badge covered acknowledgement/model/generation work
and cockpit refresh. It now says Processing turn. Acknowledgement, optional
fallback generation and refresh have separate boundaries. Speech HTTP requests
are correlated by event identity even when SSE starts playback before the
acknowledgement response binds the event to its turn.

Server spans now accompany JSON and streamed Speech responses in the bounded
X-Prometheus-Timing header (base64 JSON, version 1, maximum 6,000 characters and
64 spans, explicit truncation). The header includes request-relative durations,
model purpose/route/effort, provider dispatch counts and reported token usage.
Pure guard workers explicitly share only the parent measurement collector.
There is no global trace cache, extra endpoint, provider call or audio buffering.
The existing offline reporter accepts the drawer export without a server log
and reports missing/truncated header coverage; cost remains unmeasured.

Verification on features/needforspeed:

- 37 Java cases: LatencyTraceUnitTest (3), provider HTTP routing/timing (5),
  GuardInferenceExecutorUnitTest (4), real HTTP JSON/streaming boundaries (2),
  CORS (4), and the existing Valerian/Speech static resource contracts (19).
- 49 Node cases across performance, transcription and speech. New checks cover
  early-SSE audio correlation, missing/invalid clocks, content exclusion,
  cancellation, bounded parsing and reporting directly from a cockpit export.
- 26 Playwright cases: transcription/cockpit (22), lifecycle (1) and native
  progressive MP3 playback (3). The two new drawer cases at 1440/390 pixels
  download JSON/CSV, inspect routes and settings, check export privacy, preserve
  evidence through restart replay/reset and clear it explicitly. Desktop and
  narrow screenshots were inspected; no drawer horizontal overflow.

All passed without failures or skips. Logs are in ignored
target/interaction-timing-{java,node,browser}.log; screenshots are under
test-results/valerian-transcription-int-*/interaction-timing-*.png.
Providers/WebRTC/cockpit audio are controlled fixtures; the streaming tests use
real HTTP and native Chromium MP3 decoding. No database changes/tests were
needed. No real Heroku turn, ASR quality or physical-device latency was measured.

Interpretation: last_voice estimates speech end; audio_playing is a browser
event. Browser and server clocks remain separate. Server spans may overlap or
nest, and missing/truncated/in-flight measurements are unknown. Failed ASR items
that never reach transcript ingress do not create records. These diagnostics
enable investigation of the reported six seconds; they do not establish its
cause or certify the two-second target.

Integrated feature commit a7e3a89 into agents for the authorized Heroku testing
deployment. Only documentation conflicted. All changed source/test files match
the tested feature commit exactly; main is unchanged.

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
