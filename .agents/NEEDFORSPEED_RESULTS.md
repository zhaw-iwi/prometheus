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
