# PROMETHEUS response-latency roadmap

- Branch: `features/needforspeed`
- Baseline: `main` at `177ee34`, after Milestone 161
- Created: 2026-09-16
- Status: implementation in progress; see milestone table and results record.

This roadmap covers the five approaches selected by the user: combining
compatible model requests, task-specific models and reasoning effort,
progressive audio playback, shorter turn-completion delays, and parallel
execution of eligible work. Follow `.agents/CODEX.md` for execution and
`.agents/CONTEXT.MD` for architectural boundaries. Implement one milestone at a
time, report evidence and limitations, then commit and push its task changes.
The user explicitly authorized continuing to the subsequent milestone after
each push; pause only for an unforeseen decision or an unresolvable blocker.

The NFS identifiers below describe this branch's sequence. Assign the next
available project milestone number when a milestone is implemented; do not
mark any of these milestones complete in `PROJECT.md` merely because this
roadmap exists.

## Goal and measurement contract

Reduce the time between sensory input and meaningful presented behaviour while
preserving explicit agent control and multimodal output. The reference journey
is an ordinary SMART-goal coaching exchange in Valerian. Other core and
healthcare definitions protect broader framework behaviour.

Track these distinct measurements:

| Measurement | Start | End |
| --- | --- | --- |
| Voice response latency | Last voiced audio frame of the user's turn | First meaningful assistant audio playback |
| Backend/client response latency | Final transcript submitted, or typed input submitted | Canonical behaviour rendered |
| Speech delivery latency | Canonical behaviour received | First meaningful assistant audio playback |
| Turn-completion latency | Last voiced frame | Final transcript available |

For automated browser checks, a real media `playing` event and advancing
playback time are proxies for sound reaching the listener. Physical-device
latency needs an acoustic smoke test. A loading indicator, mocked `play()` call,
filler, or first provider byte is not the end-to-end success metric.

Target a warm-session median voice response latency of at most 2 seconds for
short ordinary exchanges in a documented reference setup. Report p95, sample
size, errors, and cost as well. This is an experimental performance target,
not a claim about current performance or a universal network/device guarantee.
Keep startup, reconnect, closing/extraction turns, long replies, and queue
backlogs in separate measurements. Improvements must not worsen p95 or quality
on the same reference workload. If the target is missed, report the actual
result and remaining dominant stage; do not declare the target achieved.

## Decisions and working assumptions

No unanswered product decision blocks creating this branch or roadmap. Use
these explicit defaults for implementation and evaluation:

- **Model candidates:** GPT-5.6 Sol for user-facing behaviour; GPT-5.6 Luna for
  decisions and structured extraction. Configure reasoning effort explicitly,
  starting with `none`. Keep GPT-5.2 as the comparison configuration. Actual
  provider/account availability and quality must be checked before promotion.
- **Configuration:** semantic purpose belongs to the policy/runtime; provider
  model IDs, effort, limits, and endpoint details belong to SPI configuration.
  Existing global configuration remains the fallback for unspecified routes.
  An existing installation must not silently change model because it upgrades.
- **Request composition:** deterministic Java code constructs requests and
  validates results. Combine speech/nonverbal output and compatible pure guard
  checks. Java still selects transitions and runs actions. No extra model call
  plans or combines requests.
- **Audio:** first optimize playback of audio for an already persisted complete
  behaviour. Keep the current scoped POST endpoint and credentials in headers.
  Retain buffered playback on browsers that cannot support progressive playback.
- **Turn completion:** evaluate 0.8 seconds of local silence and `low`
  transcription delay as a responsive candidate against 1.5 seconds/`medium`.
  Preserve saved operator choices and a pause-tolerant option. A global change
  for healthcare or multilateral listening requires the pause/noise checks below.
- **Parallel execution:** allow bounded, pure inference over immutable snapshots.
  Never execute state changes or storage actions concurrently for one agent.
  Parallel guard evaluation is in scope; speculative behaviour generation is
  deferred unless a later, separately scoped experiment establishes a need.
- **Compatibility:** preserve published HTTP/SSE shapes, behaviour identity,
  selected histories, profiles, and persisted agent data. Inspect persisted
  policies during compatibility tests; new source definitions alone do not
  represent all existing instances. No database reset is an implicit migration.
- **Scope:** no response openers, provider Fast mode, automatic model escalation,
  raw sensor-event coalescing, general history compaction, new conversational
  endpoint, or direct provider speech-to-speech control in this branch.

Quality, browser support, and acoustic evidence determine which candidates
become defaults. These are milestone exit criteria, not reasons to delay the
initial work or repeatedly request permission for routine implementation.

## Evidence from the current implementation

The initial investigation traced all 12 registered definitions using a fake
gateway and inspected their factories, runtime, gateway, and clients. It did not
measure a live provider or inspect deployed persisted agents.

| Reference path | Current text-model work |
| --- | --- |
| Ordinary SMART-goal and other single-state healthcare turns | Two decisions, speech, nonverbal: four sequential calls |
| Ordinary core facial/social/multimodal conversation | Two decisions, speech, nonverbal: four calls |
| Core role clarification with no successful transition | Four decisions, speech, nonverbal: six calls |
| Core RPS readiness discussion | Three decisions, speech, nonverbal: five calls |
| Initial prompt-based greeting | Speech and nonverbal: two calls |
| SMART-goal outer/inner closing transition | Three/four calls, including extraction and closing speech |
| Healthcare social-change aside | One decision; two more calls if an aside is generated |
| Core facial/social reaction event | Two generation calls |
| RPS hand-sign result and Talk to Me | Zero text-model calls; behaviour is deterministic |

Invalid nonverbal JSON can add one gesture-fallback call. Audio transcription
and Speech synthesis are separate provider operations and are excluded above.
Do not count a WebRTC session as a new session request for every turn.

Other established facts:

- `StaticDecision` uses an LLM with a fixed prompt. It is not a deterministic
  classifier. `LatestEventTypeDecision` is deterministic.
- Normal acknowledgement without a transition returns no behaviour. Typed and
  live-transcription clients then request explicit generation. Preserve this
  published distinction in this branch.
- `PromptPolicy.buildFullPlan` generates speech before requesting nonverbal
  output. Both requests currently use `LanguageModelGateway.complete`.
- `OpenAILanguageModelGateway` uses blocking Chat Completions, one global model,
  and a shared HTTP client. It records request duration but not a correlated
  end-to-end timing breakdown or usage. Do not propose connection reuse as a
  new optimization; a shared client already exists.
- Local and production OpenAI properties specify GPT-5.2. The template is not
  synchronized with that model. Inspect only non-secret settings in reports.
- The transcription defaults are 1.5 seconds of local silence and medium delay.
- The backend exposes streamed Speech audio, but Valerian waits for
  `response.blob()` before playback. Behaviour SSE publishes complete plans;
  it is not token streaming.
- `composeCondensed` serializes selected history; it does not invoke a separate
  summarization call. Current catalog agents use no-op regulation.

The initial investigation passed eight focused Java tests and nine JavaScript
ingress/playback tests. That evidence is a baseline only, not verification of
any implementation milestone in this document.

## Existing patterns and test anchors

Paths below are relative to `src/main/java/ch/zhaw/prometheus` unless specified.

| Responsibility | Existing implementation | Tests to extend or preserve |
| --- | --- | --- |
| State control and history selection | `model/Agent.java`, `State.java`, `OuterState.java`, `Transition.java`, `Decision.java` | `StateTransitionUnitTest`, `StateTransitionSnapshotUnitTest`, `AgentOuterStateRoutingUnitTest`, `AgentNestedOuterStateRoutingUnitTest` |
| Prompt-based behaviour | `model/policy/PromptPolicy.java`, `PromptMessageAssembler.java`, `PolicyRuntime.java` | `PromptPolicyGestureUnitTest`, `PromptPolicyUnitTest`, `PromptMessageAssemblerUnitTest`, `MultimodalBehaviourPlanEmissionUnitTest` |
| Provider execution/configuration | `spi/LanguageModelGateway.java`, `OpenAILanguageModelGateway.java`, `OpenAIProperties.java` | `OpenAILanguageModelGatewayMessageMappingUnitTest`; loopback HTTP pattern in `OpenAISpeechSynthesisGatewayUnitTest` |
| Catalog and prompts | Core/healthcare factories and package-owned prompts | `ValerianCorePromptContractTest`, `HealthcareUseCasePromptContractTest`, registry/profile contracts, `TalkToMePolicyUnitTest`, RPS tests |
| Persistence and scoped publication | `application/AgentApplicationService.java`, `ScopedDemoService.java`, behaviour/monitor broadcasters | `ScopedDemoControllerIntegrationTest`, `TransitionDecisionActionReplayIntegrationTest`, `SseBroadcasterHardeningUnitTest` |
| Canonical audio | `ScopedBehaviourSpeechService.java`, `spi/OpenAISpeechSynthesisGateway.java`, `SpeechAudio.java`, `controllers/SpeechAudioHttpResponse.java` | `ScopedBehaviourSpeechServiceUnitTest`, `BehaviourSpeechControllerWebMvcTest`, `SpeechAudioUnitTest`, Speech gateway tests |
| Browser output | `src/main/resources/public/speech/playback.js`, Valerian `script.js` | `tests/js/speech/playback.test.mjs`, `tests/playwright/valerian-transcription.spec.mjs`, lifecycle and static-client contracts |
| Turn completion and ingress | `application/LiveTranscriptionSettingsNormalizer.java`, settings descriptor, `public/transcription/*` | Normalizer/provider-payload tests; JavaScript local-VAD, settings, ingress, transport, and client tests |

The scripted gateway currently consumes a global sequence of calls. Extend its
test fixtures carefully for typed requests. Concurrent tests need request-ID or
purpose-keyed responses and explicit barriers, not thread-scheduling-dependent
consumption of that global sequence.

## Milestone sequence

| ID | Deliverable | Dependency | Status |
| --- | --- | --- | --- |
| NFS-01 | Correlated timings, reproducible baseline, quality corpus | None | Complete (Milestone 162) |
| NFS-02 | Typed inference purposes and configurable model/effort routing | NFS-01 | Complete (Milestone 163) |
| NFS-03 | One-request speech and nonverbal generation | NFS-02 | Complete (Milestone 164) |
| NFS-04 | Deterministic batching of compatible transition checks | NFS-02, NFS-03 | Implemented (Milestone 165); live quality pending |
| NFS-05 | Progressive canonical Speech playback | NFS-01 | Complete (Milestone 166); device/proxy checks pending |
| NFS-06 | Responsive turn-completion settings with pause protection | NFS-01 | Implemented (Milestone 167); conservative default retained |
| NFS-07 | Bounded parallel evaluation of eligible inference groups | NFS-02, NFS-04 | Not started |
| NFS-08 | Integrated database/browser/quality/latency acceptance | NFS-01 through NFS-07 | Not started |

Use the listed sequence for reviewable changes; dependency independence does
not authorize parallel agent work. Keep each milestone focused and commit/push at its handoff before continuing.

## NFS-01 - Establish timing and behavioural baselines

**Deliverables**

- Add lightweight correlated timings at gateway, application, transcription,
  ingress, SSE receipt, and playback boundaries. Distinguish queue waiting,
  provider time, persistence/publication, first audio byte, and playback start.
- Use monotonic clocks for durations within each process. Correlate spans with
  opaque identifiers; do not subtract unsynchronized browser/server clocks.
  Design correlation without modifying persisted behaviour payloads. Keep
  scoped access validation and browser CORS/header contracts intact.
- Record purpose, effective model/effort, status, request count, and provider
  token usage when available. Do not add transcript, prompt, audio, credentials,
  or access-code contents to performance telemetry. Bound retained diagnostics.
- Turn the temporary call-count investigation into a small reusable deterministic
  fixture covering ordinary, transition, sensory, and deterministic paths.
- Freeze a synthetic quality corpus with expected guard outcomes, branch
  priority, extraction fields, and response requirements: ambiguous agreement,
  refusal versus session exit, coaching completion, both guessing-game roles,
  therapy introduction, and nonverbal context. Include pause-rich audio fixtures.
- Create a results record with baseline configuration, dataset revision,
  timestamps, browser/device/network details, and explicitly `NOT RUN` live rows.

**High-value verification and exit criteria**

- Fake-clock tests prove span association for success, rejection, cancellation,
  and duplicate transcripts; token-usage absence is handled without failure.
- Gateway call-count fixtures reproduce the table above, including nonverbal
  fallback and zero-call deterministic behaviour. These are call-count assertions,
  not elapsed-time tests against a fake provider.
- One controlled browser journey proves the timing chain joins a final
  transcript to its canonical behaviour and audio. Concurrent agent turns must
  not share timing attribution or expose another scope's diagnostics.
- Baseline tooling can run offline. Any live sample is labelled separately;
  its absence does not prevent implementing subsequent offline-tested work.

## NFS-02 - Introduce task-specific model and effort routing

**Deliverables**

- Introduce the smallest typed inference request/options contract needed for
  purpose, messages, expected output shape, correlation, and execution limits.
  Extend the current SPI/runtime instead of adding a parallel model subsystem.
- Distinguish behaviour, decision, extraction, and summary purposes. Do not
  infer purpose by matching English prompt text. Deterministic policies still
  bypass the provider. Update scripted/no-op gateways and test helpers together.
- Resolve routes from configuration with global fallback. Supply an explicit
  optimized comparison configuration using Sol/Luna and `none`; leave an
  existing installation's unspecified routes on its configured global model.
- Build model-appropriate Chat Completions payloads: reasoning effort, schema,
  output limits, and sampling parameters must be compatible with the selected
  model. Recheck official documentation and actual account support at this point.
  Treat an Azure deployment/endpoint as configuration, not an interchangeable
  public model string. Do not claim Azure live verification without performing it.
- Add finite connection/request deadlines and actionable failures. Do not
  introduce unbounded retries, silent model escalation, or request duplication.

**High-value verification and exit criteria**

- Configuration tests cover global fallback, purpose overrides, explicit effort,
  and invalid combinations. Loopback HTTP tests inspect exact non-secret
  request fields for behaviour versus decisions/extraction and a provider error.
- Verify missing, malformed, refused, and truncated structured results cannot
  be silently interpreted as successful decisions or storage values.
- Routing metadata does not change event payloads, state selection, or persisted
  policy loading. Existing authored prompts retain their owning packages.
- Update README configuration/setup guidance and templates without copying local
  credentials. Record whether candidate models were actually exercised live.

## NFS-03 - Generate speech and nonverbal behaviour together

**Deliverables**

- Build one structured request for prompt policies that currently generate
  speech followed by nonverbal output. Compose task, outer, starter, and
  nonverbal instructions deterministically, with a clear speech-field boundary.
- Parse and validate the provider result into the existing `BehaviourPlan`.
  Preserve gesture normalization and unsupported-motion handling. Keep genuine
  speech-only policies, final states, deterministic RPS, and Talk to Me correct;
  do not force a prompt-based multimodal request onto those paths.
- Preserve optional speech/nonverbal/motion/display channels and profile
  declarations. Do not reduce the agent's modalities merely to reduce latency.
- Replace obsolete sequential generation on the converted path. Define a bounded
  failure policy for invalid output; never emit partial JSON, persist an invalid
  plan, or silently publish a different spoken string through synthesis.
- Exercise policies loaded from the database, including existing custom
  nonverbal prompts. Avoid requiring agents to be recreated solely to gain this
  runtime improvement. If stored configuration cannot be interpreted safely,
  document the exact compatibility constraint before changing that path.

**High-value verification and exit criteria**

- A successful combined response produces correct speech/nonverbal channels in
  exactly one generation call. Cover malformed output, an optional channel
  absent, gesture normalization, and one composed outer/starter policy.
- Ordinary SMART-goal and core signal-demo utterances fall from four to three
  text calls; prompt-based startup/reaction generation falls from two to one.
  Deterministic policy generation remains zero calls.
- A scoped local-database smoke test saves, reloads, acknowledges, generates,
  and reloads the exact plan; SSE and speech lookup identify that persisted event.
- Quality checks assess concise speech, channel coordination, and task adherence.
  Update policy/prompt tests to assert the new contract rather than retaining
  obsolete two-call expectations. Update README and the milestone audit.

## NFS-04 - Batch compatible transition checks deterministically

**Deliverables**

- Expose only known pure prompt decisions as typed evaluation candidates from
  the existing state/transition machinery. Unknown custom decisions, actions,
  storage changes, and changed history are execution barriers.
- Construct eligible requests from an immutable view of the active state path,
  each decision's selected history, resolved prompt, and inference options.
  Preserve different inner/outer histories; do not replace them with one generic
  conversation. Evaluate cheap deterministic event-type filters locally first.
- Group compatible candidates into a structured request with stable scoped IDs
  and named booleans. Compatibility includes model/effort, output requirements,
  snapshot identity, and absence of side effects. Apply size/token limits.
- Consume results in the existing outer-before-inner, transition-list, and
  decision-list order. Java chooses the first valid transition and runs its
  actions exactly once. Unselected computed results never cause an action.
- Ineligible work follows normal ordered evaluation. Do not cache decisions
  across turns, agent resets, or storage/history changes. No provider Batch API
  and no time-window aggregation across unrelated users.

**High-value verification and exit criteria**

- Test all-false continuation, outer precedence when multiple guards are true,
  ordered role selection, explicit decision selectors, and an action that changes
  a later request's inputs. Add one nested-outer-state case.
- Test malformed/missing required booleans and an ineligible custom decision;
  neither may publish speculative behaviour or partly execute a transition.
- Ordinary SMART-goal turns use one combined guard request and one combined
  behaviour request: two text-model calls. The compatible role-clarification
  continuation also reaches two; document exceptions instead of forcing a batch.
- Database replay proves selected state, outcome extraction, event ordering,
  and restart/reset behaviour. Run the frozen guard corpus against candidate
  models: structural equivalence does not prove identical model judgments.
- Document the purity/eligibility boundary and extension rules; agent authors
  retain explicit policies, guards, actions, and priorities.

## NFS-05 - Play canonical Speech audio progressively

**Deliverables**

- Introduce progressive browser consumption at the shared speech playback
  boundary and integrate it into Valerian. Feature-detect a supported codec and
  media path; retain a tested buffered path for unsupported browsers. Start with
  the existing MP3 contract if the browser can decode it incrementally.
- Keep fetching the scoped event-ID POST endpoint with the access-code header.
  Maintain exact persisted speech, voice/speed settings, per-agent cross-tab
  output ownership, selected-device routing, and half-duplex input gating.
- Preserve ordered playback, live/replay deduplication, explicit resume, Stop,
  disconnect, logout, agent switch/delete/reset, and navigation cleanup.
- Bound buffered audio and release readers, source buffers, object URLs, and
  provider streams. Verify servlet/proxy buffering does not defeat first-byte
  delivery; change flushing only where supported by measurement.
- Fallback must not duplicate a synthesis request or replay already-spoken audio
  from the beginning. Define recovery before versus after playback starts.
  Midstream failure must settle the queue and reopen input reliably.
- Keep Talk to Me's public contract intact. Test it if shared code changes;
  changing its standalone UI/player is not required to optimize Valerian.

**High-value verification and exit criteria**

- Java loopback-provider tests hold back the response tail and verify the backend
  can deliver an initial audio chunk before EOF, plus cancellation/resource close.
- JavaScript tests cover chunk ordering, bounded buffering, EOF, unsupported
  capability fallback, and Stop/error cleanup using controlled streams.
- Add a Playwright test with a real, valid audio fixture served in controlled
  chunks by a local HTTP server. Hold the final chunk until the test observes
  actual browser playback progress. Do not mock decoding or `play()` in this
  decisive test. Existing `route.fulfill`/mock-media tests remain lifecycle checks.
- Preserve selected sink behaviour, one output owner, replay silence, and queue
  ordering in the existing mocked browser matrix. Visually inspect loading,
  speaking, stopped, and failed states at desktop and mobile sizes.
- Supported-browser playback demonstrably starts before download completion.
  Unsupported browsers finish through the buffered path without duplicate audio.
  Record real speaker/Bluetooth results separately from browser assertions.

## NFS-06 - Reduce turn-completion delay without losing turns

**Deliverables**

- Use the existing typed settings descriptor, normalizer, preference storage,
  local VAD, and shared Valerian/multilateral engine. Do not introduce another
  transcription transport or send partial transcripts to the state machine.
- Compare 0.8-second silence/low delay with 1.5-second silence/medium delay.
  Expose meaningful responsive versus pause-tolerant choices using the existing
  settings UI; preserve manual commit and explicit operator overrides.
- Test absent preferences separately from saved preferences. Do not silently
  replace a saved threshold, language, noise setting, or provider delay.
- Preserve item ordering, duplicate suppression, reconnect epochs, input gating,
  active-track recovery, and shared cross-tab microphone ownership.

**High-value verification and exit criteria**

- Fake-clock local-VAD tests prove the proposed threshold, intervening speech
  resetting silence, minimum/maximum segments, and one commit per segment.
- Settings tests cover defaults/presets, saved choices, invalid values, manual
  mode, and exact provider payload. Ingress tests keep partials and stale finals
  out of acknowledgement. Run the same shared-engine checks for multilateral.
- Playwright verifies controls/effective values, reconnect retention, keyboard
  access, and visible settings layout without relying only on screenshots.
- Replay labelled audio with natural mid-sentence pauses, hesitation, noise, and
  sequential speakers; count premature splits, missing/duplicate turns, and
  transcript errors. The responsive candidate must pass its declared envelope.
- Keep the pause-tolerant configuration available. If acoustic quality is not
  established for healthcare/far-field use, deliver the responsive option but
  retain the conservative default there and explicitly record that limitation.

## NFS-07 - Execute eligible inference work concurrently

**Deliverables**

- Reuse NFS-04 candidate descriptions and immutable snapshots. Provide a bounded
  execution policy for independent groups that cannot or should not share one
  request, and an explicit parallel comparison mode for eligible pure guards.
  A group is either combined or parallelized, never dispatched redundantly.
- Prefer one combined request for compatible small checks unless evidence shows
  a parallel strategy is better. Combined guard evaluation followed by behaviour
  generation remains ordered: the latter depends on the selected state.
- Bound global and per-turn in-flight requests, queue waiting, and deadlines.
  Cancellation, interruption, failure, reset, and shutdown must release capacity.
  Do not share a mutable persistence context or lazy entity graph with workers.
- Apply results in logical priority order even if later checks finish first.
  A slower higher-priority decision cannot be bypassed. Ignore/cancel unneeded
  lower-priority results; account for billed work even when cancellation is late.
- Keep actions, extraction that writes required storage, persistence, publication,
  and per-agent event processing under ordered control. This is not permission
  to run simultaneous state-machine turns against the same agent aggregate.
- Expose strategy and limits through configuration with ordered execution as
  the safe fallback. Do not build a general-purpose asynchronous agent engine.

**High-value verification and exit criteria**

- Use barriers/latches to prove two eligible calls start before either is
  released. Avoid brittle wall-clock assertions or sleeps as proof of concurrency.
- Reverse completion order while preserving outer/transition priority and
  exactly-once action execution. Cover one required failure, timeout/cancellation,
  capacity exhaustion, and ineligible side-effecting work remaining sequential.
- Verify isolation between agents and rejection/discard of results whose
  snapshot became invalid. Database smoke checks confirm no lost events or
  duplicate storage actions after reload.
- Demonstrate the parallel policy on a concrete catalog guard scenario; executor
  plumbing alone does not complete the milestone. Compare separate-sequential,
  combined, and parallel requests using the same labelled inputs.
- Record request/token overhead and p50/p95. Enable parallel production routing
  only where it helps without quality regression; batching may remain the
  default for SMART-goal coaching. No gain is an explicit result, not grounds
  for speculative state mutation or hiding extra calls.

## NFS-08 - Validate the integrated branch and publish evidence

**Deliverables**

- Produce a compact before/after report with exact configuration, corpus
  revision, model/effort, counts, per-stage timing, token usage, errors, and costs.
  Separate one-change-at-a-time comparisons from the complete optimized path.
- Cover SMART-goal ordinary and closing turns, role clarification, therapy
  introduction, facial/social reactions, RPS, and Talk to Me. Include fresh and
  reloaded agents, reset, SSE reconnect, and two-tab playback ownership.
- Run warm and cold samples separately. For the main reference workload, aim
  for at least 50 completed turns per baseline/candidate configuration; disclose
  smaller samples rather than presenting an unstable p95 as conclusive.
- Keep live evaluation opt-in and bounded by run-local provider-request/token
  caps, counting retries and speculative/unused calls. Use synthetic content.
  If caps/credentials/hardware are unavailable, run deterministic checks and
  leave those live rows `NOT RUN`; do not claim the two-second target passed.
- Synchronize README setup, configuration, browser fallback, and test guidance;
  update current `PROJECT.md` status and the completed milestone audit. Update
  `.agents/CONTEXT.MD` only for durable architecture/capability changes.

**Acceptance gates**

- All deterministic contract, branch-priority, scoped-access, exact-speech,
  persistence, reset, deduplication, and cancellation cases pass.
- The frozen critical guard/transition cases have no incorrect exits, role
  changes, or storage actions. Overall labelled classification/extraction
  accuracy is no worse than baseline; report disagreements rather than hiding
  them in aggregate accuracy.
- Human review of the synthetic response sample finds no regression in task
  adherence, concise speech, or multimodal consistency. Exact wording need not
  match the previous model. Record the review and any accepted limitations.
- Progressive playback starts before response EOF on the supported browser;
  fallback, Stop, selected output, replay silence, and lifecycle tests pass.
- Responsive input demonstrates the reduced commit delay and meets the recorded
  turn-integrity checks; acoustic/deployment limits remain explicit.
- Report whether the reference median reaches 2 seconds and whether p95 improves.
  Functional completion and the measured latency target are separate outcomes.
- Remove temporary comparison scaffolding and superseded internal paths once
  no longer needed, while retaining meaningful configuration choices, tests,
  and the browser compatibility fallback. No unrelated cleanup or commit.

## Verification strategy and execution commands

Use the lowest test level that protects each change. Unit tests cover pure
rules, payloads, selection, and scheduling. Database integration proves actual
persistence and scoped contracts. Playwright proves browser journeys and media
behaviour. Screenshots help assess layout; they cannot prove progressive audio
or acoustic latency. Static source-string contracts alone are insufficient.

**Database isolation is required by the existing fixtures.** Some integration
tests call `deleteAll()`, and some classes named `*UnitTest` use `@SpringBootTest`
with the configured database. Inspect the selected tests and point the app/test
process at a dedicated disposable local MySQL schema before running them.
Use Spring datasource overrides and a test-scoped account; never put passwords
in this document or command output. Do not reset the developer's working schema.

Reuse the existing scripted gateway or a local HTTP provider stub so database
and browser regression tests make no paid provider calls. Use unique fixture
agents/access codes and cleanup restricted to the isolated test data. A browser
test using mocked REST/media does not count as a database smoke test.

Current focused offline commands, to adapt as contracts evolve:

```powershell
.\mvnw.cmd "-Dtest=PromptPolicyUnitTest,PromptPolicyGestureUnitTest,StateTransitionUnitTest,StateTransitionSnapshotUnitTest,AgentOuterStateRoutingUnitTest,AgentNestedOuterStateRoutingUnitTest" test
.\mvnw.cmd "-Dtest=OpenAILanguageModelGatewayMessageMappingUnitTest,OpenAISpeechSynthesisGatewayUnitTest,SpeechAudioUnitTest,ScopedBehaviourSpeechServiceUnitTest,BehaviourSpeechControllerWebMvcTest" test
.\mvnw.cmd "-Dtest=LiveTranscriptionSettingsNormalizerTest,LiveTranscriptionProviderPayloadBuilderTest" test
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
```

Add focused test classes for new request routing, batching, parallel execution,
and real progressive playback during their owning milestones. Those tests and
any new helper scripts are planned deliverables, not existing commands.

After verifying the disposable database target, use representative integration
and browser gates, then the full Java suite at the integrated acceptance stage:

```powershell
.\mvnw.cmd "-Dtest=ScopedDemoControllerIntegrationTest,TransitionDecisionActionReplayIntegrationTest,TalkToMeScopedIntegrationTest" test
npm.cmd run test:valerian:lifecycle
npm.cmd run test:valerian:transcription
npm.cmd run test:valerian:visual
npm.cmd run test:talktome:visual
.\mvnw.cmd test
```

The existing Playwright config starts or reuses port 8080. Start a known test
instance with explicit database/provider overrides and use `PROMETHEUS_BASE_URL`
and `PROMETHEUS_SKIP_WEBSERVER=true` when appropriate; do not accidentally reuse
an unrelated development instance. Use `PROMETHEUS_ADMIN_TOKEN` for that test
instance when needed. Run API Workbench checks if shared client/API code changes;
the standalone participation site is outside this branch's scope.

For every milestone, record exact commands actually run, pass/fail totals,
fixture and live-provider boundaries, and remaining `NOT RUN` cases. Finish
with `git diff --check` and a scoped diff review. Do not repeatedly rerun broad
suites after they pass unless another change or unresolved concern warrants it.

## Remaining empirical questions

These need evidence during implementation, not answers before work can begin:

- How much of the reported three-to-six seconds occurs before final transcript,
  inside each inference request, in database/SSE delivery, or in audio buffering?
- Do Sol/Luna at explicit low reasoning effort meet the frozen task-quality
  criteria, and does combining prompts change contextual decision accuracy?
- Which target browsers/codecs permit progressive playback with selected-device
  routing? How much buffering comes from the servlet or deployment proxy?
- Is 0.8-second silence appropriate for older adults and the actual room, or
  should the responsive option remain specific to faster-paced conversations?
- Does any parallel guard strategy beat a single compact decision request after
  accounting for request overhead, cost, and priority waiting?

## Official provider references

Consult current versions before provider payload changes. The initial analysis
checked these sources on 2026-09-16; account-specific access is not established.

- [Latency optimization](https://developers.openai.com/api/docs/guides/latency-optimization)
- [GPT-5.2 and its default reasoning effort](https://developers.openai.com/api/docs/models/gpt-5.2)
- [GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol)
- [GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna)

The branch is ready for review when milestone outcomes, compatibility, quality,
and measured performance are documented truthfully. Creating this roadmap does
not establish any measured latency improvement. The subsequent user instruction
authorizes committing and pushing each implemented milestone.
