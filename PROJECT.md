# PROJECT.md

## Short project summary
PROMETHEUS is an event-driven Java framework for explicit state-machine agent
control, a developing regulation layer, and multimodal behaviour plans.

## How to use this file

This opening section is the current engineering status. Coding agents should
read it at session startup, then inspect relevant code, tests, and README
sections for their task.

The milestone checklist and records below are a historical audit trail. Do not
read them end to end by default. Search or open individual milestones only when
an earlier decision is relevant, and verify historical claims against current
code.

## Current implementation status

### Product and architecture

- PROMETHEUS is a Java 21/Spring Boot framework for mapping multimodal `Event`
  inputs to persisted multimodal `BehaviourPlan` outputs.
- Explicit `Agent`/`State`/`OuterState`/`Transition` control owns tasks,
  commitments, and final authority.
- `AgentInteractionProfile` declares accepted observations and emitted
  modalities.
- The reusable main-branch catalog consists of Spring-discovered definitions
  under `agentdefs/core` and `agentdefs/usecases/healthcare`.
- Valerian, Valerian Access Management, the public Talk to Me speech client,
  API Workbench, and the multilateral displays consume public backend
  contracts. Standalone sites under `.web` are auxiliary deployments outside
  the Spring agent runtime.

### Implemented runtime capabilities

- Persisted state machines, storage, event history, prompt policies, explicit
  acknowledge/generate semantics, and scheduled evaluation.
- Speech, nonverbal, motion, and display behaviour-plan channels.
- Scoped access-code and trusted global APIs, resilient behaviour/monitor SSE,
  and PROMETHEUS-authoritative Realtime speech orchestration.
- Explicit access-code-scoped Talk to Me instances for deterministic exact-text
  output-only Speech synthesis with user-managed create/select/delete lifecycle.
- Browser sensing for facial emotion, social context, and hand signs, plus
  manual environmental inputs and deterministic social-situation derivation.
- A persisted regulation foundation with snapshot context, modulation values,
  internal opportunities, reset behavior, and focused tests.

### Known capability gap

The intended social regulation/internal motivation system is not complete.
Production agents currently use no-op regulation. The Zurich prototype responds
only to ticks and user utterances, emits an affiliation opportunity from a
dependency threshold, and does not yet integrate multimodal social evidence.
Its modulation bundle is retained on the agent but is not consumed by prompt or
behaviour generation. Soft/hard interrupts, task arbitration, safety precedence,
and regulation diagnostics remain future work.

### Current milestone state

- Last completed milestone: Milestone 142, participate phase date label.
- The approved standalone participation phase-management scope is complete; no
  follow-up milestone is currently selected.
- The regulation gap above is a major framework direction, but it should become
  a milestone only after its intended motivation model and acceptance criteria
  are explicitly scoped.

## Historical milestones checklist

- [x] Milestone 1: Output-profile-aware prompt and generation flow for realtime compatibility
- [x] Milestone 2: Realtime multimodal seed agent and complement replay coverage
- [x] Milestone 3: Multifacial client with per-user face-emotion attribution
- [x] Milestone 4: Two-state social-initiative MVP seed agent template
- [x] Milestone 5: Scripted integration replay for social-initiative MVP flow
- [x] Milestone 6: Remove legacy respond flow and align runtime on acknowledge + generate semantics
- [x] Milestone 7: SSE hardening for broadcaster boundaries and browser stream lifecycle
- [x] Milestone 8: SSE resilience follow-up with backoff, bounded client buffers, and broadcaster diagnostics
- [x] Milestone 9: Migrate Gigi verbal seed agents from PROMISE package shape to PROMETHEUS API
- [x] Milestone 10: Extend single-state Gigi transitions with global quit intent handling
- [x] Milestone 11: Reduce SSE disconnect exception amplification during client refresh/reconnect
- [x] Milestone 12: Align final-state generation semantics and acknowledge response contract
- [x] Milestone 13: Keep client active badges synchronized on terminal transitions
- [x] Milestone 14: Add Gigi single-state multimodal In/Out/InOut guessing-game seed agents
- [x] Milestone 15: Add FourStatesCircular scripted REST+SSE integration replay (all options, quit last)
- [x] Milestone 16: Add FourStatesLinear scripted REST+SSE integration replay (all options with resets, quit last)
- [x] Milestone 17: Add scripted REST+SSE integration replays for all three single-state Gigi agents
- [x] Milestone 18: Structured nonverbal plan generation and richer multimodal seed-agent output
- [x] Milestone 19: Re-scope multimodal In/Out demos (In as micro-coaching, Out with deterministic multi-channel nonverbal policy)
- [x] Milestone 20: Stabilize elderly-care seed agents and align omitted-modality history/SSE semantics
- [x] Milestone 21: Production-grade SSE lifecycle foundation with heartbeats, cursors, and replay
- [x] Milestone 22: GIGI TDSR demonstrator roadmap
- [x] Milestone 23: TDSR talking-with-gestures seed agent
- [x] Milestone 24: TDSR social situation change events
- [x] Milestone 25: TDSR social context sensitivity seed agent
- [x] Milestone 26: TDSR Schere-Stein-Papier core game and motion contract
- [x] Milestone 27: TDSR RPS web behaviour and manual sensing client
- [x] Milestone 28: TDSR RPS client-side hand detection
- [x] Milestone 29: Unified GIGI TDSR demo cockpit
- [x] Milestone 30: OpenAI Realtime GA migration across PROMETHEUS clients
- [x] Milestone 31: Realtime demo hardening
- [x] Milestone 32: GIGI demo independent sensing modes
- [x] Milestone 33: GIGI demo UI layout and explicit agent connection
- [x] Milestone 34: GIGI demo sensing accordion and behaviour rows
- [x] Milestone 35: GIGI demo explicit connect/disconnect lifecycle
- [x] Milestone 36: Agent interaction profile declaration
- [x] Milestone 37: GIGI demo consumes interaction profiles
- [x] Milestone 38: Seed agent interaction profile coverage
- [x] Milestone 39: GIGI demo empty visual sensing state
- [x] Milestone 40: GIGI demo text transcript history hydration
- [x] Milestone 41: GIGI demo unified camera observation emission gate
- [x] Milestone 42: GIGI demo agent profile drawer summary
- [x] Milestone 43: GIGI demo duplicate behaviour render suppression
- [x] Milestone 44: GIGI demo diagnostics drawer storage, log, and state polish
- [x] Milestone 45: GIGI demo mirrored camera overlay alignment
- [x] Milestone 46: Valerian cockpit rename and PROMETHEUS-facing branding
- [x] Milestone 47: Feature branch and agent definition catalog
- [x] Milestone 48: Access code persistence and admin API
- [x] Milestone 49: Scoped demo API
- [x] Milestone 50: Valerian user UI
- [x] Milestone 51: Admin UI
- [x] Milestone 52: End-to-end rehearsal and compatibility pass
- [x] Milestone 53: PROMETHEUS-authoritative Realtime speech sideband orchestration
- [x] Milestone 54: Realtime speech duplicate-response fix
- [x] Milestone 55: Agent language codes for Realtime transcription hints
- [x] Milestone 56: Realtime transcript ingress gating and ASR hallucination hardening
- [x] Milestone 57: Push-to-talk Realtime turn-boundary hardening
- [x] Milestone 58: Valerian speech sensing readout
- [x] Milestone 59: Valerian agent drawer language metadata and tab order
- [x] Milestone 60: Move Valerian speech sensing into Realtime tab
- [x] Milestone 61: Valerian explicit no-gesture display
- [x] Milestone 62: Push-to-talk audio drain before commit
- [x] Milestone 63: Split continuous and push-to-talk speech clients
- [x] Milestone 64: Out-of-band exact Realtime speech and dual speech-sensing readouts
- [x] Milestone 65: Backend-owned recorded push-to-talk speech turns
- [x] Milestone 66: Push-to-talk starter speech playback
- [x] Milestone 67: Current-state latest-utterance speech replay
- [x] Milestone 68: Remove push-to-talk speech clients
- [x] Milestone 69: Realtime speaks published backend behaviour speech
- [x] Milestone 70: Property-driven CORS for external Valerian cockpit clients
- [x] Milestone 71: GIGI TDSR prompt storyline grounding and relevance guards
- [x] Milestone 72: General GIGI TDSR tour conversation agent
- [x] Milestone 73: Valerian Admin assignment replacement fix
- [x] Milestone 74: GIGI TDSR UTF-8 German prompt polish
- [x] Milestone 75: Manual weather sensing for GIGI TDSR tour conversation
- [x] Milestone 76: Weather-location grounding for GIGI TDSR tour conversation
- [x] Milestone 77: Shared weather-location context for all GIGI TDSR agents
- [x] Milestone 78: Sparse open-question gestures for GIGI TDSR agents
- [x] Milestone 79: GIGI TDSR physical behaviour vocabulary alignment
- [x] Milestone 80: Warmer GIGI TDSR tour conversation persona and route grounding
- [x] Milestone 81: Shared GIGI TDSR persona continuity across task agents
- [x] Milestone 82: GIGI TDSR tour conversation with sparse social context sensitivity
- [x] Milestone 83: GIGI TDSR German core package reorganization
- [x] Milestone 84: Multilingual TDSR core agents and package-shaped keys
- [x] Milestone 85: Babylon multilingual TDSR core agents
- [x] Milestone 86: Elderly-care package and key rename
- [x] Milestone 87: Valerian Admin package-based agent type tree
- [x] Milestone 88: German TDSR SHHD scene agents
- [x] Milestone 89: Multilingual and Babylon TDSR SHHD scene agents
- [x] Milestone 90: Compact shared TDSR core outcome extraction prompts
- [x] Milestone 91: Valerian Admin access-code presets
- [x] Milestone 92: Valerian Admin package accordion manual collapse fix
- [x] Milestone 93: Valerian speech audio device selection
- [x] Milestone 94: Valerian Realtime ICE diagnostics
- [x] Milestone 95: TDSR tour and SHHD concise micro-humor prompt accent
- [x] Milestone 96: Valerian camera device selection
- [x] Milestone 97: Valerian shared light/dark cockpit themes
- [x] Milestone 98: PROMETHEUS Realtime tuning backend contract
- [x] Milestone 99: Valerian cockpit Realtime audio tuning
- [x] Milestone 100: Framework/application agent repository split
- [x] Milestone 101: Remove cockpit VAD create-response control
- [x] Milestone 102: Valerian maximizable cockpit columns
- [x] Milestone 103: Valerian visual behaviour board
- [x] Milestone 104: Valerian real-time facial emotion report
- [x] Milestone 105: Valerian social context report
- [x] Milestone 106: Valerian social movement states
- [x] Milestone 107: Valerian social attentiveness signal
- [x] Milestone 108: Valerian social context observation contract
- [x] Milestone 109: Valerian sensing column completeness pass
- [x] Milestone 110: Valerian facial emotion camera-loop diagnostics
- [x] Milestone 111: Valerian facial emotion overlay diagnostics
- [x] Milestone 112: Valerian shared visual detector TFJS compatibility
- [x] Milestone 113: Valerian compact cockpit headers
- [x] Milestone 114: Valerian main-branch core and healthcare agent catalog
- [x] Milestone 115: GitHub README current-state cleanup and client API guide
- [x] Milestone 116: Codex context guide cleanup and branch publication
- [x] Milestone 117: Valerian detached column window foundation
- [x] Milestone 118: Valerian cross-window camera and microphone ownership
- [x] Milestone 119: Remove replaceable legacy static clients
- [x] Milestone 120: API Workbench shell and endpoint catalog
- [x] Milestone 121: API Workbench live scoped lifecycle execution
- [x] Milestone 122: API Workbench SSE and observation publishing
- [x] Milestone 123: README bundled client and API Workbench documentation
- [x] Milestone 124: README multilateral note and API Workbench response width
- [x] Milestone 125: API Workbench short URL redirect
- [x] Milestone 126: Standalone German SIRA/PROMETHEUS public page
- [x] Milestone 127: Participate landing page and registration wizard frontend
- [x] Milestone 128: Participate PHP/MySQL registration backend
- [x] Milestone 129: Participate admin registration table
- [x] Milestone 130: Agent-facing context and bootstrap optimization
- [x] Milestone 133: Public Talk to Me exact-text speech client
- [x] Milestone 134: Talk to Me lifecycle layout and Realtime completion diagnostics
- [x] Milestone 135: Talk to Me exact-text Speech renderer
- [x] Milestone 136: Talk to Me backend isolation
- [x] Milestone 139: Participate phase and assignment foundation
- [x] Milestone 140: Participate admin phase and assignment management
- [x] Milestone 141: Participate recovery, phase views, and results interest
- [x] Milestone 142: Participate phase date label

## Milestone 1
### Date
2026-02-24

### Goal
Add modality/output-profile-aware policy execution so realtime clients can receive speech-only prompt contracts while backend generation can produce complementary non-speech behaviour.

### What changed
- Added `OutputProfile` (`FULL_PLAN`, `REALTIME_SPEECH`, `BACKEND_COMPLEMENT`).
- Threaded output profile through runtime and state/policy prompt bundle generation.
- Extended `PromptPolicy` with profile-specific output contracts and generation behavior:
  - `FULL_PLAN`: existing speech + optional nonverbal gesture.
  - `REALTIME_SPEECH`: speech only.
  - `BACKEND_COMPLEMENT`: nonverbal-only complement derived from latest assistant speech in history.
- Updated endpoints:
  - `GET /{agentID}/prompt?profile=...`
  - `POST /{agentID}/behaviour/generate` accepts `outputProfile`.
- Added validation for unknown profile values (`400 Bad Request`).

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`

### How to test
- Targeted tests run:
  - `.\mvnw.cmd "-Dtest=PromptPolicyUnitTest,PromptPolicyGestureUnitTest,AgentApplicationServicePromptUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- `BACKEND_COMPLEMENT` currently derives nonverbal output from latest assistant speech and does not yet generate `motion`/`display` channels.
- `BACKEND_COMPLEMENT` emits no behaviour event when no prior assistant speech exists (intentional: avoid synthetic `NONE` events).
- Existing API behavior remains default-compatible via `FULL_PLAN` when no profile is provided.

### Next steps
1. Add explicit assistant speech event type for cleaner realtime acknowledge semantics.
2. Extend complement generation to support `motion` and `display`.
3. Add integration test covering realtime speech + backend complement end-to-end.

## Milestone 2
### Date
2026-02-24

### Goal
Add a concrete agent template and deterministic integration replay that demonstrate realtime speech interaction combined with backend nonverbal complement behaviour under multimodal input.

### What changed
- Added manual seed agent template:
  - `src/test/java/ch/zhaw/prometheus/agents/RealtimeMultimodalAgent.java`
- Added deterministic scripted gateway fixture:
  - `src/test/resources/scripts/realtime-speech-backend-complement-replay-script.json`
- Added integration replay test that validates:
  - realtime prompt fetched with `profile=REALTIME_SPEECH` includes speech-only contract
  - prompt context includes both verbal and facial-emotion observations
  - assistant realtime speech acknowledged via `/acknowledge`
  - backend complement generation via `outputProfile=BACKEND_COMPLEMENT` emits nonverbal-only behaviour
  - `src/test/java/ch/zhaw/prometheus/integration/RealtimeSpeechBackendComplementReplayIntegrationTest.java`
- Updated README seed-agent template list to include `RealtimeMultimodalAgent`.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`

### How to test
- Replay-focused tests run:
  - `.\mvnw.cmd "-Dtest=RealtimeSpeechBackendComplementReplayIntegrationTest,MultimodalScriptReplayIntegrationTest" test`

### Known issues and decisions
- Realtime assistant acknowledgements currently reuse `resp.behaviour_plan` payload conventions for compatibility.
- Complement flow remains nonverbal-only in this milestone.

### Next steps
1. Introduce explicit assistant speech event type for realtime acknowledgements.
2. Expand complement generation coverage for `motion`/`display`.
3. Add scripted assertions for mixed-profile behaviour generation in longer conversations.

## Milestone 3
### Date
2026-02-25

### Goal
Add a multi-user variant of the visual facial client that captures a user-provided name and sends it with each face-emotion observation so the agent can distinguish users.

### What changed
- Added new static client endpoint and assets:
  - Route: `GET /visual/multifacial` redirects to `public/visual/multifacial/index.html`
  - Files:
    - `src/main/resources/public/visual/multifacial/index.html`
    - `src/main/resources/public/visual/multifacial/script.js`
- New client UI includes a `User Name` field with local persistence (`localStorage`) and includes `userName` in emitted `obs.emotion.face` payloads.
- Updated face-emotion prompt adapter to include user identity when present (e.g., `User Alice facial emotion: happy ...`) so the LLM prompt context carries per-user attribution.
- Updated and added tests:
  - redirect coverage for `/visual/multifacial`
  - adapter and assembler coverage for `userName`-aware face-emotion prompt content.
- Updated README with the new multifacial client endpoint.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/visual/multifacial/?agentId=<uuid>`

### How to test
- Targeted tests run:
  - `.\mvnw.cmd "-Dtest=StaticRedirectControllerWebMvcTest,PromptEventContentAdapterUnitTest,PromptMessageAssemblerUnitTest" test`

### Known issues and decisions
- The multifacial client is a single-camera source; multiple simultaneous users are represented by the entered `userName`, not by automatic multi-face tracking.
- Snapshot aggregation for face-emotion facts remains global and is not yet split by user identity.

### Next steps
1. Extend the visual pipeline to track multiple faces in-frame and auto-assign stable identities.
2. Add per-user face-emotion facts in snapshot aggregation.
3. Add an integration replay script with alternating `userName` observations.

## Milestone 4
### Date
2026-02-25

### Goal
Add a concrete seed-agent template for a two-state MVP that alternates between direct conversation handling and proactive social-situation assessment for room scenarios with changing participants.

### What changed
- Added new manual seed agent template:
  - `src/test/java/ch/zhaw/prometheus/agents/SocialInitiativeMvpAgent.java`
- The template introduces a two-state structure:
  - `ConversationHandling` for direct user-request responses.
  - `SocialSituationAssessment` for proactive social-context-based utterances.
- Added prompt-based transitions:
  - conversation -> social assessment on room social-signal change.
  - social assessment -> conversation on explicit user request.
- Added extraction action that maintains a cumulative `SocialContext` JSON object in storage for use by social-assessment prompts.
- Updated README template list to include `SocialInitiativeMvpAgent`.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`
3. Seed this agent by running:
   - `src/test/java/ch/zhaw/prometheus/agents/SocialInitiativeMvpAgent.java`
   - remove `@Disabled("Manual seed test")` before running.

### How to test
- Targeted compatibility tests run:
  - `.\mvnw.cmd "-Dtest=PromptPolicyUnitTest,PromptPolicyGestureUnitTest,AgentApplicationServicePromptUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- This milestone adds a seed template only; no new runtime endpoint or scheduler behavior was changed.
- Cooldown and anti-repeat greeting logic are currently prompt/policy-level expectations and are not yet enforced by deterministic storage guards.

### Next steps
1. Add deterministic cooldown guard decisions/actions driven by storage timestamps and user-level greeting memory.
2. Add replay integration tests for entry/exit room scenarios with named and unnamed users.
3. Improve transition guards for noisy visual-event streams to avoid unnecessary bouncing.

## Milestone 5
### Date
2026-02-25

### Goal
Add deterministic scripted integration coverage for the new two-state social-initiative MVP agent behavior across endpoint flow, state transitions, storage updates, and behaviour SSE emission.

### What changed
- Added new scripted gateway fixture:
  - `src/test/resources/scripts/social-initiative-mvp-replay-script.json`
- Added new end-to-end replay integration test:
  - `src/test/java/ch/zhaw/prometheus/integration/SocialInitiativeMvpReplayIntegrationTest.java`
- Replay scenario validates:
  - startup behaviour emission in `ConversationHandling`
  - transition to `SocialSituationAssessment` after visual face-emotion observation
  - `SocialContext` storage update containing named user data
  - proactive social greeting generation
  - transition back to `ConversationHandling` on direct user request
  - subsequent conversation response generation

### How to run
1. Configure properties as in `README.md`.
2. Run targeted replay test:
   - `.\mvnw.cmd "-Dtest=SocialInitiativeMvpReplayIntegrationTest" test`

### How to test
- Executed:
  - `.\mvnw.cmd "-Dtest=SocialInitiativeMvpReplayIntegrationTest" test`

### Known issues and decisions
- Scripted backend already supports per-test script selection through `prometheus.gateway.script`; no backend refactor was required.
- Test coverage currently focuses on state/storage/behaviour flow, not yet on deterministic cooldown timing semantics.

### Next steps
1. Add a second replay script for repeated observations to validate anti-repeat greeting cooldown behavior.
2. Extract shared replay test harness utilities to reduce duplication across replay integration tests.
3. Add optional replay assertions for prompt endpoint profile usage in social-initiative scenarios.

## Milestone 6
### Date
2026-02-27

### Goal
Unify runtime semantics around asynchronous `acknowledge` + `generate`, remove legacy `respond(...)` flow, and ensure starting-state transitions can immediately emit behaviour (including speech) without a manual generate call.

### What changed
- Removed `respond(...)` API from model flow (`Agent`, `State`, `OuterState`) and aligned behavior on:
  - `acknowledge(...)` for event ingestion and transition handling
  - `generate(...)` for explicit behaviour generation
- Updated transition-entry semantics:
  - when `acknowledge(...)` causes a transition into a `starting` state, `start(...)` is invoked immediately
  - for non-starting transitions, `enter()` is called and transition chaining continues on the same event
- Updated tick semantics to use `acknowledge(systemTick)` so transition/start behavior is consistent.
- Removed obsolete `startResponsePending` behavior.
- Updated acknowledge service flow to publish any acknowledge-triggered behaviour to behaviour SSE.
- Updated replay scripts and tests to reflect acknowledge-time starting responses.
- Refined `SocialInitiativeMvpAgent` seed template with explicit Gigi persona and capability framing:
  - socially intelligent InIT robot context
  - multimodal sensing/behaviour capabilities
  - bilateral and multilateral interaction framing
  - explicit handling guidance for identity/capability questions.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`

### How to test
- Full regression:
  - `.\mvnw.cmd test`
- Social initiative replay:
  - `.\mvnw.cmd "-Dtest=SocialInitiativeMvpReplayIntegrationTest" test`

### Known issues and decisions
- Cooldown/anti-repeat initiative logic is still policy-driven and not yet enforced by a deterministic runtime guard.
- Visual event quality and confidence thresholds still strongly affect transition decisions.

### Next steps
1. Add deterministic, semantic anti-repeat guards for proactive social utterances.
2. Add replay coverage for repeated noisy visual events and expected stable-state behavior.
3. Expand seed-agent documentation with concrete end-user test scripts per client combination.

## Milestone 7
### Date
2026-02-27

### Goal
Harden all current SSE client-server interactions so disconnects and transport abort races do not break main HTTP flows, scheduler ticks, or logging side effects.

### What changed
- Hardened SSE broadcaster send boundaries:
  - `AgentBehaviourBroadcaster`, `AgentMonitorBroadcaster`, and `LogStreamBroadcaster` now catch `Throwable` on emitter sends and remove failed emitters without rethrowing.
  - Initial stream handshake sends no longer call `completeWithError(...)` on first-send failure; failed emitters are only unsubscribed.
  - Agent-scoped broadcaster maps now remove empty emitter lists during unsubscribe.
- Hardened business/runtime publish call sites:
  - `AgentApplicationService` now wraps monitor/behaviour broadcaster publishes in defensive `try/catch (Throwable)` boundaries.
  - `ContinuousEvaluationScheduler` now isolates broadcaster failures from tick-cycle success paths.
  - `SseLogAppender` now wraps `broadcaster.publish(...)` in `try/catch (Throwable)` to prevent logging-path SSE failures from leaking into request execution.
- Hardened frontend SSE lifecycle for all current EventSource clients under `src/main/resources/public`:
  - Added `beforeunload` and `pagehide` cleanup handlers.
  - Cleanup closes each EventSource and clears reconnect timers.
  - Added one bounded reconnect timer per stream on `onerror` in:
    - `public/script.js`
    - `public/monitor/script.js` (logs/monitor/behaviour streams)
    - `public/nonverbal/script.js`
    - `public/realtime/script.js` (behaviour stream)
- Added targeted regression tests for resilience against broadcaster-thrown `Throwable`:
  - `AgentApplicationServiceGenerateOptionsUnitTest`
  - `ContinuousEvaluationSchedulerUnitTest`

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`

### How to test
- Targeted resilience tests run:
  - `.\mvnw.cmd "-Dtest=AgentApplicationServiceGenerateOptionsUnitTest,ContinuousEvaluationSchedulerUnitTest,AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- SSE broadcasters intentionally swallow send-path failures at boundary level to preserve primary business and scheduler behavior.
- Reconnect cadence is bounded to one timer per stream with fixed delay; exponential backoff is not introduced in this milestone.

### Next steps
1. Add broadcaster-focused unit tests for first-send handshake failure removal semantics per broadcaster implementation.
2. Add structured metrics counters for SSE subscribe/disconnect/send-failure events for observability.
3. Consider configurable reconnect backoff parameters for frontend streams if deployment conditions require it.

## Milestone 8
### Date
2026-02-28

### Goal
Reduce SSE failure amplification and improve operational diagnosability by adding client reconnect backoff, bounded monitor buffers, defensive parse handling, and broadcaster-level failure diagnostics.

### What changed
- Frontend reconnect policy hardening for all active SSE clients:
  - Replaced fixed-delay reconnect with bounded exponential backoff + jitter while keeping one reconnect timer per stream.
  - Backoff is reset when stream `open` succeeds.
  - Updated files:
    - `src/main/resources/public/script.js`
    - `src/main/resources/public/monitor/script.js`
    - `src/main/resources/public/nonverbal/script.js`
    - `src/main/resources/public/realtime/script.js`
- Monitor client resilience hardening:
  - Added defensive JSON parse handling for log and snapshot stream events.
  - Added bounded in-memory buffers for logs and behaviour entries to avoid unbounded growth.
  - Added short-window dedupe for repeated app-level disconnect messages.
  - Updated file:
    - `src/main/resources/public/monitor/script.js`
- Backend SSE observability at swallow boundaries:
  - Added debug diagnostics (first failure and periodic counts) for broadcaster send failures while preserving non-throwing semantics.
  - Added debug logs for service/scheduler publish catch boundaries to trace suppressed SSE failures without breaking primary flows.
  - Updated files:
    - `src/main/java/ch/zhaw/prometheus/logging/AgentBehaviourBroadcaster.java`
    - `src/main/java/ch/zhaw/prometheus/logging/AgentMonitorBroadcaster.java`
    - `src/main/java/ch/zhaw/prometheus/logging/LogStreamBroadcaster.java`
    - `src/main/java/ch/zhaw/prometheus/application/AgentApplicationService.java`
    - `src/main/java/ch/zhaw/prometheus/runtime/ContinuousEvaluationScheduler.java`
- Added broadcaster-focused hardening tests:
  - New test file verifies failed emitter send paths unsubscribe emitters and do not rethrow across behaviour, monitor, and log broadcasters.
  - `src/test/java/ch/zhaw/prometheus/logging/SseBroadcasterHardeningUnitTest.java`

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - PowerShell: `.\mvnw.cmd spring-boot:run`

### How to test
- Targeted SSE hardening tests run:
  - `.\mvnw.cmd "-Dtest=SseBroadcasterHardeningUnitTest,AgentApplicationServiceGenerateOptionsUnitTest,ContinuousEvaluationSchedulerUnitTest,AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- Backoff parameters are currently static constants in frontend scripts and are not yet centrally configurable.
- Broadcaster diagnostics are debug-level logs and periodic counters; no external metrics export was introduced in this milestone.

### Next steps
1. Add explicit handshake-failure-path tests for `subscribe(...)` initial-send failures per broadcaster.
2. Consider exporting SSE failure and disconnect counters via metrics endpoint for dashboarding.
3. Evaluate optional per-client reconnect policy tuning from server-provided config.

## Milestone 9
### Date
2026-03-01

### Goal
Adapt the five new `gigi` seed tests to compile and run on PROMETHEUS while keeping their original verbal-only interaction design and behavior patterns.

### What changed
- Migrated package/import usage in all `gigi` seed classes from legacy `statefulconversation` namespaces to `ch.zhaw.prometheus`.
- Replaced deprecated `TransferUtterancesAction` usage with explicit PROMETHEUS transitions that preserve the same state-machine flows.
- Updated state construction to current `State(name, Policy, transitions)` API using `PromptPolicy` with the original prompts/starter prompts.
- Updated agent startup to current runtime contract:
  - inject `PromptMessageAssembler` and `LanguageModelGateway`
  - call `agent.start(new PolicyRuntime(...))`
- Added persisted-agent assertions (`assertNotNull(saved.getId())`) in the five tests.
- Updated `README.md` seed-template list to include the five new `gigi` templates.

### How to run
1. Configure properties as in `README.md`.
2. Run a selected `gigi` seed test class from `src/test/java/ch/zhaw/prometheus/agents`.

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`

### Known issues and decisions
- The migration intentionally keeps these agents verbal in/out only at prompt-policy level; no multimodal behaviour prompts were added.
- Seed tests still depend on repository runtime configuration (database + gateway mode) when fully executed; this milestone validated API compatibility via test compile.

### Next steps
1. Add deterministic scripted replay integration tests for each `gigi` flow (single-state, linear, circular).
2. If needed, mark all seed-only tests consistently with `@Disabled("Manual seed test")` and document execution expectations.
3. Add one REST-based creation path for the same flows if reusable provisioning beyond seed tests is required.

## Milestone 10
### Date
2026-03-01

### Goal
Allow the three single-state Gigi agents to transition to final state on either specialized completion or explicit global quit intent, while preserving topology and strict outcome JSON shape.

### What changed
- Updated decision prompts in:
  - `SingleStateMicroCoaching` (`PROMPT_COACH_TO_FINAL`)
  - `SingleStateGuessingGame` (`PROMPT_TO_FINAL`)
  - `SingleStateCoCreation` (`PROMPT_TO_FINAL`)
- Added explicit global quit intent detection examples (German), while keeping false for ambiguous/non-committal messages.
- Updated extraction prompts to keep the same JSON structure and field names while changing:
  - `completed` from fixed `true` to `true|false`
  - rules for mapping `completed=true` (specialized completion) vs `completed=false` (global quit).
- Updated final prompts in all three agents to handle both paths:
  - regular completed specialized interaction summary
  - neutral early-exit summary on global quit
  - concise goodbye and reminder that a new session is needed for further messages.
- No state-machine topology changes were made (`specialized -> final` remains unchanged).

### How to run
1. Configure properties as in `README.md`.
2. Run selected seed test classes under:
   - `src/test/java/ch/zhaw/prometheus/agents`

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`

### Known issues and decisions
- This milestone updates prompt contracts only; runtime transition/action wiring was intentionally left unchanged.
- Existing encoding artifacts in legacy prompt text were left as-is outside changed prompt blocks.

### Next steps
1. Add replay/integration tests that assert `completed=false` for global-quit transitions.
2. Add replay/integration tests that assert `completed=true` for specialized completion transitions.
3. Optionally centralize reusable global quit-intent prompt fragments across Gigi agents.

## Milestone 11
### Date
2026-03-01

### Goal
Reduce backend exception noise and request-path impact when SSE clients disconnect abruptly (for example during hard refresh loops), especially for log/monitor/behaviour streams.

### What changed
- Scoped SSE log broadcasting away from the global root logger:
  - `SseLogAppender` now applies to `ch.zhaw.prometheus` logger only.
  - Root logger remains console-only.
  - File: `src/main/resources/logback-spring.xml`
- Hardened broadcaster cleanup on failed sends:
  - On send failure, failed emitters are now unsubscribed and explicitly completed.
  - Updated files:
    - `src/main/java/ch/zhaw/prometheus/logging/LogStreamBroadcaster.java`
    - `src/main/java/ch/zhaw/prometheus/logging/AgentMonitorBroadcaster.java`
    - `src/main/java/ch/zhaw/prometheus/logging/AgentBehaviourBroadcaster.java`
- Existing service/scheduler catch boundaries remained unchanged and continue isolating SSE failures from business flow.

### How to run
1. Start app:
   - `.\mvnw.cmd spring-boot:run`
2. Open monitor/realtime clients and reproduce refresh/reconnect patterns.

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`
- SSE hardening unit test executed:
  - `mvn "-Dtest=SseBroadcasterHardeningUnitTest" test`

### Known issues and decisions
- A one-off connection-aborted IOException can still occur during abrupt browser disconnect races, but this milestone reduces repeated amplification and broad logger fan-out.
- Log stream now intentionally carries application logger events (`ch.zhaw.prometheus`) rather than every framework/container logger.

### Next steps
1. Add integration test that simulates abrupt SSE disconnect during active acknowledge/generate requests.
2. Optionally classify and suppress expected broken-pipe style disconnect exceptions at container logging level.
3. Consider bounded emitter count instrumentation per stream for operational visibility.

## Milestone 12
### Date
2026-03-01

### Goal
Allow behaviour generation in final states and make acknowledge responses explicit for clients, while improving text-client handling of terminal flows and AJAX errors.

### What changed
- Updated `Agent.generate(...)` to allow generation whenever a current state exists, including final states.
  - File: `src/main/java/ch/zhaw/prometheus/model/Agent.java`
- Updated acknowledge application/controller contract:
  - `AgentApplicationService.acknowledge(...)` now returns `Optional<ResponseView>` instead of `boolean`.
  - `AgentControllerRealtime.acknowledge(...)` now returns `ResponseEntity<ResponseView>`.
  - Files:
    - `src/main/java/ch/zhaw/prometheus/application/AgentApplicationService.java`
    - `src/main/java/ch/zhaw/prometheus/controllers/AgentControllerRealtime.java`
- Updated text client flow:
  - Reads acknowledge response `active` and `responseEvent`.
  - Skips follow-up `generate` when acknowledge already produced a response or agent is inactive.
  - Treats `generate` `409` as non-fatal terminal/no-op behavior.
  - Replaced `alert(errMsg)` with formatted status/text output.
  - File: `src/main/resources/public/script.js`
- Updated controller compatibility test fixture and assertions to match new acknowledge response contract.
  - File: `src/test/java/ch/zhaw/prometheus/controllers/AgentClientCompatibilityWebMvcTest.java`
- Updated README API notes for acknowledge response and final-state generate semantics.

### How to run
1. Start app:
   - `.\mvnw.cmd spring-boot:run`
2. Use text/realtime clients and test terminal transitions plus post-acknowledge generation behavior.

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`
- Controller compatibility tests executed:
  - `mvn "-Dtest=AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- `behaviour/generate` can still return `409` when no behaviour is produced by policy (true no-op), but final-state generation is no longer blocked solely by `active=false`.

### Next steps
1. Add integration test that covers acknowledge-triggered final transition with `responseEvent` returned and no follow-up generate call.
2. Add API docs/examples for client-side handling of acknowledge `responseEvent` vs explicit generate.
3. Consider adding an explicit response field indicating whether a state transition occurred during acknowledge.

## Milestone 13
### Date
2026-03-01

### Goal
Ensure client active-status badges switch to inactive immediately when an agent reaches a final state, including clients without direct user input loops.

### What changed
- Realtime client:
  - Updated acknowledge handling to parse response JSON and apply `active` immediately.
  - If acknowledge returns `active=false`, skips follow-up side-behaviour generation.
  - Updated assistant transcript append acknowledge path to also refresh active badge.
  - File: `src/main/resources/public/realtime/script.js`
- Nonverbal client:
  - Added monitor snapshot SSE subscription (`/{agentId}/monitor/stream`) to update active badge from snapshot `active`.
  - Added cleanup for monitor stream on page unload.
  - File: `src/main/resources/public/nonverbal/script.js`

### How to run
1. Start app:
   - `.\mvnw.cmd spring-boot:run`
2. Open realtime and nonverbal clients and drive an agent to final state.

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`

### Known issues and decisions
- Active badge updates in nonverbal client are now snapshot-driven and independent of whether a new behaviour event is emitted after a terminal transition.

### Next steps
1. Add browser-level integration checks for badge transitions in text/realtime/nonverbal clients.
2. Consider sharing a small common SSE utility for status synchronization across clients.
3. Optionally surface final-state transitions as a dedicated lightweight SSE signal.

## Milestone 14
### Date
2026-03-01

### Goal
Add three new Gigi single-state guessing-game seed agents under `gigi.multimodal` that preserve the exact existing verbal flow while introducing multimodal input instructions, built-in nonverbal output, and a combined variant.

### What changed
- Added new seed agent classes:
  - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`
  - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalOut.java`
  - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalInOut.java`
- Preserved the inner single-state guessing-game prompts and transition/extraction/final flow from `SingleStateGuessingGame` in all three variants.
- Wrapped each variant with an `OuterState` that adds multimodality-specific instructions without changing inner game logic.
- Implemented variant behavior:
  - `MultimodalIn`: multimodal visual input grounding for `obs.emotion.face`, `obs.human.presence`, and `obs.social.grouping` with uncertainty-aware handling.
  - `MultimodalOut`: built-in nonverbal gesture output enabled via `PromptPolicy#setNonVerbalGesturePrompt(...)`.
  - `MultimodalInOut`: combined multimodal input grounding and built-in nonverbal gesture output.
- Updated `README.md` seed template list to include the three new multimodal Gigi variants.

### How to run
1. Configure properties as in `README.md`.
2. Run one of the seed tests:
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalOut.java`
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalInOut.java`

### How to test
- Compile validation executed:
  - `mvn -DskipTests test-compile`

### Known issues and decisions
- `MultimodalOut` and `MultimodalInOut` currently use the existing built-in gesture label schema only (`nonVerbal.gesture`); no custom motion/display schema was introduced.
- Multimodal input handling is prompt-guided and depends on incoming visual events being acknowledged to the agent.

### Next steps
1. Add scripted integration replay tests that exercise each variant with combinations of visual client event streams.
2. Add assertion coverage for emitted `nonVerbal.gesture` values in Out and InOut variants.
3. Consider extracting shared guessing-game prompt constants into a reusable helper to reduce duplication across Gigi variants.

## Milestone 15
### Date
2026-03-01

### Goal
Add deterministic REST endpoint + SSE replay coverage for the `FourStatesCircular` agent flow where the user executes base menu options 1, 2, and 3, then ends via option 4 as the last step.

### What changed
- Added scripted Mock-LLM replay fixture:
  - `src/test/resources/scripts/four-states-circular-all-options-replay-script.json`
- Added integration replay test:
  - `src/test/java/ch/zhaw/prometheus/integration/FourStatesCircularReplayIntegrationTest.java`
- The replay test validates:
  - start and acknowledge flows through REST endpoints
  - behaviour plan publication via behaviour SSE
  - state progression through outer/inner state reporting
  - final storage extraction under key `outcome`

### How to run
1. Run:
   - `.\mvnw.cmd "-Dtest=FourStatesCircularReplayIntegrationTest" test`

### How to test
- Executed:
  - `.\mvnw.cmd "-Dtest=FourStatesCircularReplayIntegrationTest" test`

### Known issues and decisions
- State assertions in outer-state scenarios intentionally accept both top-level `name` and `innerName` because `/state` reports outer + inner state metadata.

### Next steps
1. Add three additional circular scripts for early global-quit from each specialized state (guesser/coach/story).
2. Add analogous scripted replay coverage for `FourStatesLinear`.
3. Consider extracting a shared script-replay test harness to reduce duplication across integration replay tests.

## Milestone 16
### Date
2026-03-01

### Goal
Add deterministic REST endpoint + SSE replay coverage for the `FourStatesLinear` agent across all four base options, with option 4 tested last.

### What changed
- Added scripted Mock-LLM replay fixture:
  - `src/test/resources/scripts/four-states-linear-all-options-replay-script.json`
- Added integration replay test:
  - `src/test/java/ch/zhaw/prometheus/integration/FourStatesLinearReplayIntegrationTest.java`
- Replay design decision:
  - Because linear flow ends after one specialized interaction, the script uses `reset` between runs to cover options 1, 2, 3 and then 4 (quit last) in one deterministic replay.
- The test validates:
  - start/acknowledge/reset via REST endpoints
  - behaviour plan publication via behaviour SSE
  - state progression (outer + inner state reporting)
  - storage extraction under key `outcome` for specialized completion and global quit paths

### How to run
1. Run:
   - `.\mvnw.cmd "-Dtest=FourStatesLinearReplayIntegrationTest" test`

### How to test
- Executed:
  - `.\mvnw.cmd "-Dtest=FourStatesLinearReplayIntegrationTest" test`

### Known issues and decisions
- State assertions accept both outer `name` and `innerName` from `/state` because linear agent routing is wrapped in an outer state.

### Next steps
1. Add additional linear scripts for early global-quit while inside each specialized state.
2. Add a combined replay-suite command/test grouping for circular + linear scripts.
3. Extract shared replay helper utilities to reduce duplicate endpoint/SSE assertion code.

## Milestone 17
### Date
2026-03-01

### Goal
Add deterministic REST endpoint + SSE replay coverage for all three single-state Gigi variants (`guessing_game`, `micro_coaching`, `story_co_creation`) with one script and one integration test per agent.

### What changed
- Added scripts:
  - `src/test/resources/scripts/single-state-guessing-game-replay-script.json`
  - `src/test/resources/scripts/single-state-micro-coaching-replay-script.json`
  - `src/test/resources/scripts/single-state-co-creation-replay-script.json`
- Added integration tests:
  - `src/test/java/ch/zhaw/prometheus/integration/SingleStateGuessingGameReplayIntegrationTest.java`
  - `src/test/java/ch/zhaw/prometheus/integration/SingleStateMicroCoachingReplayIntegrationTest.java`
  - `src/test/java/ch/zhaw/prometheus/integration/SingleStateCoCreationReplayIntegrationTest.java`
- Replay coverage design:
  - each script validates two paths in one run via `reset`:
    - specialized completion path (`completed=true`)
    - explicit global quit path (`completed=false`)
- Each integration test validates:
  - REST `start`, `acknowledge`, and `reset` endpoint flow
  - behaviour SSE emissions and payload matching
  - state endpoint expectations
  - storage `outcome` extraction content

### How to run
1. Run:
   - `.\mvnw.cmd "-Dtest=SingleStateGuessingGameReplayIntegrationTest,SingleStateMicroCoachingReplayIntegrationTest,SingleStateCoCreationReplayIntegrationTest" test`

### How to test
- Executed:
  - `.\mvnw.cmd "-Dtest=SingleStateGuessingGameReplayIntegrationTest,SingleStateMicroCoachingReplayIntegrationTest,SingleStateCoCreationReplayIntegrationTest" test`

### Known issues and decisions
- These replays use deterministic scripted gateway outputs and focus on endpoint/SSE/state/storage contract behavior rather than natural-language robustness.

### Next steps
1. Add additional scripts that stress ambiguous user utterances and ensure no premature transitions.
2. Consider extracting a shared single-state replay harness to reduce test duplication.
3. Add combined CI target that runs all new gigi replay tests (single-state + linear + circular).

## Milestone 18
### Date
2026-03-02

### Goal
Extend nonverbal generation from gesture-only labels to structured nonverbal plans, and enable richer nonverbal output for multimodal single-state seed agents.

### What changed
- Extended `PromptPolicy` with structured nonverbal-plan support:
  - added `DEFAULT_NONVERBAL_PLAN_PROMPT`
  - added `setNonVerbalPlanPrompt(...)` / `getNonVerbalPlanPrompt()`
  - `FULL_PLAN` and `BACKEND_COMPLEMENT` now resolve nonverbal output through:
    1. structured plan prompt (when configured)
    2. gesture-only prompt fallback (existing behavior)
- Added normalization rule for structured nonverbal outputs:
  - if `gesture` is missing or invalid, it is normalized to `NONE`
  - supports both direct nonverbal object output and nested `{ "nonVerbal": { ... } }`
- Added unit coverage in:
  - `src/test/java/ch/zhaw/prometheus/model/policy/PromptPolicyGestureUnitTest.java`
  - verifies structured output usage and fallback-to-gesture behavior on invalid structured JSON
- Updated multimodal seed agents to emit richer nonverbal outputs:
  - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`
  - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalInOut.java`
  - both now configure `PromptPolicy#setNonVerbalPlanPrompt(...)` (with gesture fallback)
- Updated README developer guidance for multimodal nonverbal prompt configuration.

### How to run
1. Seed one of the updated multimodal agents:
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalInOut.java`
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open nonverbal client:
   - `http://localhost:8080/nonverbal/?agentId=<uuid>`

### How to test
- Executed:
  - `mvn -DskipTests test-compile`
- Recommended targeted tests:
  - `mvn "-Dtest=PromptPolicyGestureUnitTest" test`

### Known issues and decisions
- Structured nonverbal generation is still prompt-driven and not schema-validated server-side beyond basic JSON object handling and gesture normalization.
- Rich nonverbal output depends on model compliance; invalid structured output falls back to gesture-only behavior when gesture prompt is configured.

### Next steps
1. Add explicit server-side JSON-schema validation for structured `nonVerbal` payloads.
2. Add integration replay coverage that asserts presence of nonverbal subfields beyond `gesture`.
3. Consider enabling structured nonverbal plan prompts for additional agents beyond multimodal In/InOut.

## Milestone 19
### Date
2026-03-03

### Goal
Adapt the two single-state multimodal demo seed agents to match demo intent: make `SingleStateMultimodalIn` a supportive micro-coaching flow grounded in visual inputs, and make `SingleStateMultimodalOut` produce more balanced, deterministic multi-channel nonverbal output (not gesture-only behavior).

### What changed
- Updated `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`:
  - Replaced inner interaction prompt from guessing game to supportive single-state micro-coaching.
  - Kept single-state topology and existing multimodal input wiring via `OuterState`.
  - Added explicit visual-cue coaching adaptation guidance (emotion/presence/grouping) with fallback behavior when no visual signals exist.
  - Updated transition decision prompt to micro-coaching completion semantics.
  - Updated outcome extraction type from `guessing_game` to `micro_coaching`.
  - Updated state label and agent description to reflect micro-coaching purpose.
- Updated `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalOut.java`:
  - Added structured `PROMPT_NONVERBAL_PLAN` with deterministic intent-category mapping.
  - Enabled nonverbal plan generation via `PromptPolicy#setNonVerbalPlanPrompt(...)` (keeping gesture fallback prompt).
  - Strengthened outer-state instructions to require gesture plus additional nonverbal channels on each turn.
  - Kept single-state guessing-game flow and transition topology unchanged.

### How to run
1. Configure properties as in `README.md`.
2. Run one of:
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java`
   - `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalOut.java`

### How to test
- Executed:
  - `mvn -q "-Dtest=SingleStateMultimodalIn,SingleStateMultimodalOut" test`
- Result:
  - Prompt/runtime behavior exercised successfully up to persistence, including nonverbal-plan generation.
  - Test run failed on local DB schema mismatch unrelated to this milestone (`Field 'start_response_pending' doesn't have a default value` during `agent` insert).

### Known issues and decisions
- This milestone intentionally limits changes to prompt and seed-test agent definitions only; no runtime/model/schema code changes were made.
- Gesture rendering remains constrained by existing nonverbal client gesture token UI map.

### Next steps
1. Decide whether to expand nonverbal client gesture token mapping if additional gesture emojis are required beyond current canonical gesture set.
2. Add scripted integration replay assertions that verify `SingleStateMultimodalOut` emits populated non-gesture nonverbal fields.
3. Resolve local DB schema drift before relying on these seed tests for pass/fail gating.

## Milestone 20
### Date
2026-06-10

### Goal
Stabilize the current implementation before expanding the framework by adopting the new GIGI elderly-care seed agents into PROMETHEUS and removing the event-history/SSE divergence for omitted behaviour modalities.

### What changed
- Added and migrated the elderly-care seed-agent package under:
  - `src/test/java/ch/zhaw/prometheus/agents/elderlycare`
- Adapted the former PROMISE-shaped seed classes to PROMETHEUS runtime APIs:
  - prompt text is persisted through `PromptPolicy`
  - seed startup uses `PolicyRuntime`, `PromptMessageAssembler`, and `LanguageModelGateway`
  - final states use shared event-history selection instead of predecessor-framework utterance-transfer actions
  - prompt contract checks now assert PROMETHEUS persistence fields
- Changed `PromptPolicy` prompt columns to `TEXT` so shared elderly-care role and persuasion prompts fit without oversized `VARCHAR` rows.
- Fixed omitted-modality consistency:
  - `EventHistory.appendEvent(...)` now returns the stored event copy
  - `Agent.start(...)`, `generate(...)`, and acknowledge transition responses return the recorded event instance
  - service-level modality omission now mutates the same event seen through persisted history and behaviour SSE
- Added regression coverage proving generated behaviour with omitted speech is stored and published as the same recorded event.
- Updated README seed-template documentation to list the elderly-care agents.

### How to run
1. Configure properties as in `README.md`.
2. Run one of the elderly-care seed classes manually from:
   - `src/test/java/ch/zhaw/prometheus/agents/elderlycare`

### How to test
- Executed:
  - `.\mvnw.cmd -q test-compile`
  - `.\mvnw.cmd -q "-Dtest=PflegezentrumDemoPromptContractTest,AgentApplicationServiceGenerateOptionsUnitTest" test`
  - `.\mvnw.cmd -q clean test`

### Known issues and decisions
- The elderly-care agents are seed/test templates, not dedicated creation endpoints.
- The migrated prompt text still contains the existing source encoding artifacts; this milestone kept prompt content behaviorally stable.
- The `TEXT` column change is compatible with generated test schemas, but persistent local databases may need schema migration or recreation.

### Next steps
1. Add deterministic REST+SSE replay scripts for the four elderly-care seed agents.
2. Clean up prompt source encoding once there is replay coverage to protect behavior.
3. Consider extracting shared GIGI elderly-care prompt fragments after the adopted agents have stabilized in PROMETHEUS.

## Milestone 21
### Date
2026-06-10

### Goal
Harden the current SSE mechanism toward production-grade lifecycle behavior without replacing the existing Spring MVC SSE architecture or introducing a distributed broker.

### What changed
- Added finite emitter lifetimes and scheduled heartbeat comments to all SSE broadcasters:
  - `AgentBehaviourBroadcaster`
  - `AgentMonitorBroadcaster`
  - `LogStreamBroadcaster`
- Behaviour SSE events now carry an SSE frame id derived from the persisted event id when available.
- Behaviour stream subscriptions accept reconnect cursors through either:
  - `Last-Event-ID`
  - `?lastEventId=<id>`
- Behaviour stream replay now selects missed `resp.behaviour_plan` events from event history after the provided cursor; missing or unknown cursors fall back to the latest behaviour event.
- Browser clients that rebuild `EventSource` now remember behaviour `lastEventId` and pass it as a reconnect query parameter:
  - text client
  - monitor client
  - realtime client
  - nonverbal renderer
- Nonverbal renderer now reconnects its monitor-status stream instead of allowing active-state badges to go stale after a monitor stream failure.
- Event ids remain hidden from normal JSON API/event-history responses while still being usable as SSE frame cursors.
- Added focused regression coverage for:
  - heartbeat send failure cleanup across behaviour, monitor, and log broadcasters
  - behaviour replay selection after known, missing, and unknown cursors
  - behaviour stream cursor forwarding through the web controller

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Use any SSE-backed client:
   - `http://localhost:8080/?agentId=<uuid>`
   - `http://localhost:8080/monitor/?agentId=<uuid>`
   - `http://localhost:8080/realtime/?agentId=<uuid>`
   - `http://localhost:8080/nonverbal/?agentId=<uuid>`

### How to test
- Executed:
  - `.\mvnw.cmd -q test-compile`
  - `.\mvnw.cmd -q "-Dtest=SseBroadcasterHardeningUnitTest,AgentClientCompatibilityWebMvcTest,AgentApplicationServiceGenerateOptionsUnitTest,ContinuousEvaluationSchedulerUnitTest" test`

### Known issues and decisions
- This milestone keeps emitters in memory and remains single-JVM/sticky-session oriented; horizontal fan-out still needs a broker/outbox strategy.
- Sends are still performed inline at broadcaster boundaries, though protected by catch boundaries; a queued async fan-out layer remains future work for slow-client isolation.
- Monitor and log streams use heartbeats and cleanup but do not implement historical replay.
- `/logs/stream` remains a development/admin-oriented endpoint and still needs explicit authorization before production exposure.

### Next steps
1. Add an async bounded fan-out layer with slow-client drop policy.
2. Add metrics for emitter counts, send failures, heartbeats, replay counts, and dropped events.
3. Decide on sticky sessions versus broker/outbox semantics for horizontal deployment.
4. Add browser-level reconnect tests for cursor replay and nonverbal monitor status recovery.

## Milestone 22
### Date
2026-06-10

### Goal
Create a detailed implementation roadmap for the GIGI TDSR demonstrator agents before adding new agents or clients.

### What changed
- Added `.agents/TDSR.md` with:
  - locked design decisions for the TDSR demo agents
  - detailed descriptions for social context sensitivity, talking with gestures, and Schere-Stein-Papier
  - proposed event and behaviour payload contracts
  - proposed package and client locations
  - a staged roadmap with milestone deliverables, tests, and risks
- Recorded the decision that all new demo seed agents should live under:
  - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr`
- Recorded the decision that all new demo agents use GIGI and German language.

### How to run
- No runtime behavior changed in this planning milestone.

### How to test
- Documentation-only change; no automated tests required.

### Known issues and decisions
- The roadmap proposes `obs.social.situation_change`, `obs.hand.sign`, and `motion.handSign` contracts, but they are not implemented yet.
- The roadmap recommends implementing the gesture guessing-game agent first, then social computed events and social context agent, then Schere-Stein-Papier core/game client/sensing milestones.

### Next steps
1. Implement the TDSR talking-with-gestures seed agent with prompt contract and scripted replay coverage.
2. Add deterministic social situation change computation and tests.
3. Add the social context sensitivity seed agent and replay coverage.
4. Implement Schere-Stein-Papier core game logic, web client, and client-side hand detection in staged milestones.

## Milestone 23
### Date
2026-06-10

### Goal
Implement TDSR Milestone 1 by adding a new German GIGI seed agent that demonstrates speech plus structured nonverbal gesture output in a yes/no guessing game.

### What changed
- Added new TDSR seed-agent package:
  - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr`
- Added `GuessingGameWithGestures`, a new GIGI TDSR seed agent that:
  - preserves an explicit single-state yes/no guessing-game flow
  - speaks German
  - configures `PromptPolicy#setNonVerbalPlanPrompt(...)`
  - keeps `PromptPolicy#setNonVerbalGesturePrompt(...)` as fallback
  - emits structured `nonVerbal` behaviour plans for gesture-oriented demo turns
  - transitions to final only on explicit user stop intent, not merely on a correct guess confirmation
- Added fixture wiring:
  - `AgentFixtures.gigiTdsrGuessingGameWithGestures()`
- Added deterministic replay fixture:
  - `src/test/resources/scripts/gigi-tdsr-guessing-game-with-gestures-replay-script.json`
- Added tests:
  - `GigiTdsrPromptContractTest`
  - `GigiTdsrGuessingGameWithGesturesReplayIntegrationTest`
- Updated README seed-agent list.
- Updated `.agents/TDSR.md` to mark TDSR Milestone 1 as implemented.

### How to run
1. Configure properties as in `README.md`.
2. Run the seed class manually:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/GuessingGameWithGestures.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Use the text client and optional nonverbal renderer:
   - `http://localhost:8080/?agentId=<uuid>`
   - `http://localhost:8080/nonverbal/?agentId=<uuid>`

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest,GigiTdsrGuessingGameWithGesturesReplayIntegrationTest" test`

### Known issues and decisions
- The new seed agent intentionally lives beside, not inside, the existing elderly-care package.
- The replay proves structured `nonVerbal` payloads are emitted through behaviour SSE; the existing nonverbal renderer may still visualize only part of the richer payload.
- The interaction finalizes only on explicit stop intent. A correct final guess keeps the interaction state active so GIGI can ask whether to play another round or stop.
- The targeted Maven run passed, but Surefire printed its existing fork-shutdown warning after the Spring SSE test closed; surefire reports show zero failures and zero errors.

### Next steps
1. Implement deterministic `obs.social.situation_change` computation and unit tests.
2. Add the TDSR social context sensitivity seed agent and replay coverage.
3. Implement Schere-Stein-Papier core game logic and motion payload contract.

## Milestone 24
### Date
2026-06-10

### Goal
Implement TDSR Milestone 2 by adding deterministic computed social situation change events from visual social grouping observations.

### What changed
- Added event constant:
  - `Event.TYPE_SOCIAL_SITUATION_CHANGE = "obs.social.situation_change"`
- Added pure detector:
  - `src/main/java/ch/zhaw/prometheus/model/social/SocialSituationChangeDetector.java`
- The detector converts raw `obs.social.grouping` history into reusable computed events with change types:
  - `arrival`
  - `departure`
  - `crowd_detected`
  - `now_alone`
  - `single_person_nearby`
  - `group_size_changed`
- Integrated social situation change detection into `AgentApplicationService.acknowledge(...)`:
  - raw `obs.social.grouping` is acknowledged first
  - the computed `obs.social.situation_change` event is then acknowledged through the normal agent path
  - both raw and computed events are persisted in event history before the service saves/publishes monitor state
- The detector uses the agreed default crowd threshold of `humanCount >= 3`.
- The detector suppresses duplicate computed events for repeated unchanged social grouping states.
- The detector forwards latest `obs.human.presence` confidence when available.
- Added tests:
  - `SocialSituationChangeDetectorUnitTest`
  - `AgentApplicationServiceSocialSituationChangeUnitTest`
- Updated README event documentation.
- Updated `.agents/TDSR.md` to mark TDSR Milestone 2 as implemented.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open the visual social client:
   - `http://localhost:8080/visual/social/?agentId=<uuid>`
3. Enable event emission. Raw social grouping observations may now produce computed `obs.social.situation_change` events in the agent event history.

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=SocialSituationChangeDetectorUnitTest,AgentApplicationServiceSocialSituationChangeUnitTest" test`

### Known issues and decisions
- Computed social situation changes are triggered from `obs.social.grouping`, not from every `obs.human.presence` event, to avoid duplicate reactions from the social client's paired raw emissions.
- `crowd_detected` has priority over generic `arrival` when a grouping observation reaches the crowd threshold.
- This milestone adds backend event computation only. No seed agent reacts to `obs.social.situation_change` yet.
- Duplicate suppression is state-change based, not wall-clock based; explicit time-based cooldown policy can be added if needed by the social context seed agent.

### Next steps
1. Add the TDSR social context sensitivity seed agent that reacts to `obs.social.situation_change`.
2. Add scripted REST+SSE replay coverage for arrival, crowd, departure, conversation, and explicit exit.
3. Add clearer prompt content adaptation for `obs.social.situation_change` if the replay shows raw JSON is too noisy for prompts.

## Milestone 25
### Date
2026-06-10

### Goal
Implement TDSR Milestone 3 by adding a German GIGI seed agent that reacts to computed social situation changes while still supporting ordinary conversation.

### What changed
- Added TDSR seed agent:
  - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/SocialContextSensitivity.java`
- The agent uses a single active state plus a final state:
  - reacts to `obs.social.situation_change` events through a self-transition
  - handles normal German conversation in the same state
  - transitions to final only when the user explicitly wants to end the conversation
- Added reusable deterministic transition helper:
  - `src/main/java/ch/zhaw/prometheus/model/commons/decisions/LatestEventTypeDecision.java`
- Added prompt adapter for readable computed social-event context:
  - `src/main/java/ch/zhaw/prometheus/model/policy/SocialSituationChangePromptEventContentAdapter.java`
- Wired the new adapter into `PromptMessageAssembler`.
- Added fixture wiring:
  - `AgentFixtures.gigiTdsrSocialContextSensitivity()`
- Added deterministic replay fixture:
  - `src/test/resources/scripts/gigi-tdsr-social-context-sensitivity-replay-script.json`
- Added tests:
  - `LatestEventTypeDecisionUnitTest`
  - `PromptEventContentAdapterUnitTest`
  - `GigiTdsrPromptContractTest`
  - `GigiTdsrSocialContextSensitivityReplayIntegrationTest`
- Updated README seed-agent list.
- Updated `.agents/TDSR.md` to mark TDSR Milestone 3 as implemented.

### How to run
1. Configure properties as in `README.md`.
2. Run the seed class manually:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/SocialContextSensitivity.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Use the text client with the visual social client:
   - `http://localhost:8080/?agentId=<uuid>`
   - `http://localhost:8080/visual/social/?agentId=<uuid>`
5. Enable social event emission in the visual social client. Raw social grouping observations may produce computed `obs.social.situation_change` events, which the agent can react to without a user utterance.

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=LatestEventTypeDecisionUnitTest,PromptEventContentAdapterUnitTest,GigiTdsrPromptContractTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest" test`

### Known issues and decisions
- The agent reacts to computed `obs.social.situation_change` events produced from raw `obs.social.grouping`; it does not consume camera frames directly.
- The active state self-transition keeps the agent available for both proactive social reactions and ordinary user conversation.
- The prompt-based final transition is guarded by the latest event being `obs.user_utterance`, so visual social events cannot ask the LLM to end the interaction.
- Duplicate steady-state suppression remains owned by the Milestone 24 detector. This milestone does not add a separate wall-clock cooldown.
- The visual social client emits perception events only. Use the text, monitor, or nonverbal clients to observe generated agent behaviour.
- The targeted Maven run passed, but Surefire printed its existing fork-shutdown warning after the Spring SSE replay test closed; surefire reports show zero failures and zero errors.

### Next steps
1. Implement TDSR Milestone 4: Schere-Stein-Papier core game logic and motion payload contract.
2. Add the RPS web behaviour/manual sensing client.
3. Add client-side hand-sign detection for RPS once the manual event path is stable.

## Milestone 26
### Date
2026-06-10

### Goal
Implement TDSR Milestone 4 by adding deterministic Schere-Stein-Papier game logic and a top-level `motion.handSign` behaviour contract without adding the browser RPS client yet.

### What changed
- Added event constant:
  - `Event.TYPE_HAND_SIGN = "obs.hand.sign"`
- Added RPS core domain helpers under:
  - `src/main/java/ch/zhaw/prometheus/model/rps`
- The RPS helpers provide:
  - canonical signs: `rock`, `scissor`, `paper`
  - deterministic sign selection cycle: `rock`, `scissor`, `paper`
  - deterministic winner calculation for all sign combinations
  - clear invalid-sign errors
- Added deterministic RPS actions:
  - `RpsSelectAgentSignAction`
  - `RpsEvaluateRoundAction`
- Added deterministic RPS behaviour policies:
  - `RpsRevealPolicy` emits speech `Schere, Stein, Papier` plus top-level `motion.handSign`
  - `RpsResultPolicy` emits the round winner and a compact game-status display payload
- Added TDSR seed agent:
  - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
- The seed agent uses explicit states:
  - `GIGI TDSR RPS Spielstart`
  - `GIGI TDSR RPS Zeichen zeigen`
  - `GIGI TDSR RPS Rundenergebnis`
  - `GIGI TDSR RPS Abschluss`
- Added fixture wiring:
  - `AgentFixtures.gigiTdsrRockScissorPaper()`
- Added deterministic replay fixture:
  - `src/test/resources/scripts/gigi-tdsr-rock-scissor-paper-replay-script.json`
- Extended the test replay expectation shape to support `motion` and `display` assertions.
- Added tests:
  - `RpsRulesUnitTest`
  - `DeterministicRpsSignSelectorUnitTest`
  - `GigiTdsrPromptContractTest`
  - `GigiTdsrRockScissorPaperReplayIntegrationTest`
- Updated README seed-agent and event/behaviour contract documentation.
- Updated `.agents/TDSR.md` to mark TDSR Milestone 4 as implemented.

### How to run
1. Configure properties as in `README.md`.
2. Run the seed class manually:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Use the text client for readiness/play-again/exit utterances:
   - `http://localhost:8080/?agentId=<uuid>`
5. Until the RPS web client exists, send manual normalized hand-sign observations through `/acknowledge`:
   - `type`: `obs.hand.sign`
   - `payload.sign`: `rock`, `scissor`, or `paper`

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=RpsRulesUnitTest,DeterministicRpsSignSelectorUnitTest,GigiTdsrPromptContractTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`

### Known issues and decisions
- This milestone intentionally adds no RPS web client. The replay uses manually supplied `obs.hand.sign` events.
- GIGI's sign selection is deterministic, not random, so scripted replay and regression tests remain stable.
- Winner calculation and round storage are deterministic actions, not prompt outputs.
- `RpsRevealPolicy` is the first seed-specific deterministic policy that emits top-level `motion` directly; this preserves the existing `BehaviourPlan` modality model instead of adding a new behaviour modality.
- `rps_rounds` is stored in the existing `StorageEntry` value column and is suitable for short demo sessions; very long sessions may need a larger or normalized persistence shape later.
- The targeted Maven run passed, but Surefire printed its existing fork-shutdown warning after the Spring SSE replay test closed; surefire reports show zero failures and zero errors.

### Next steps
1. Implement TDSR Milestone 5: RPS web behaviour and manual sensing client.
2. Add browser rendering for `motion.handSign` and manual buttons that emit normalized `obs.hand.sign`.
3. Add client-side hand-sign detection after the manual RPS event path is stable.

## Milestone 27
### Date
2026-06-10

### Goal
Implement TDSR Milestone 5 by adding a browser-only Schere-Stein-Papier client that renders GIGI's motion sign and lets the user submit manual hand-sign observations.

### What changed
- Added the RPS static client:
  - `src/main/resources/public/rps/index.html`
  - `src/main/resources/public/rps/script.js`
- Added `/rps` and `/rps/` redirects to the static RPS client while preserving query parameters.
- The RPS client:
  - loads agent metadata and event history
  - subscribes to behaviour SSE with cursor-based reconnect
  - renders `BehaviourPlan.speech`, `display` game state, and top-level `motion.handSign`
  - submits manual `obs.hand.sign` events for `rock`, `scissor`, and `paper`
  - handles acknowledge-time `responseEvent` payloads from state transitions
- Added static client contract coverage:
  - `RpsClientStaticResourceContractTest`
- Extended redirect coverage in:
  - `StaticRedirectControllerWebMvcTest`
- Updated README client documentation and `.agents/TDSR.md`.

### How to run
1. Configure properties as in `README.md`.
2. Run the seed class manually:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open the RPS client:
   - `http://localhost:8080/rps/?agentId=<uuid>`
5. Use `Start`, `Bereit`, one of `Schere` / `Stein` / `Papier`, and `Noch einmal` or `Beenden` to drive the game.

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=StaticRedirectControllerWebMvcTest,RpsClientStaticResourceContractTest,RpsRulesUnitTest,DeterministicRpsSignSelectorUnitTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`
- Local HTTP smoke against the running Spring Boot app:
  - `http://localhost:8080/rps/?agentId=11111111-1111-1111-1111-111111111111`
  - verified `200 OK` after redirect and presence of the RPS script, manual rock button, agent sign label, and user sign label.

### Known issues and decisions
- The RPS client intentionally uses manual buttons only. Camera-based hand-sign detection remains TDSR Milestone 6.
- Manual sign observations use the same normalized `obs.hand.sign` contract the later detector must emit.
- The client renders the existing top-level `motion` modality and does not introduce a new behaviour modality.
- The in-app browser target was unavailable in this session, so browser interaction was not executed. Local HTTP smoke and static client contract tests covered the client surface that can be verified without a browser.
- End-to-end game play still requires seeding a real `RockScissorPaper` agent.
- The targeted Maven run passed, but Surefire printed its existing fork-shutdown warning after the Spring SSE replay test closed; surefire reports show zero failures and zero errors.

### Next steps
1. Implement TDSR Milestone 6: client-side hand-sign detection with manual fallback retained.
2. Add browser/manual verification instructions for camera confidence thresholds.
3. Consider a seeded demo shortcut once the RPS client-side detector is stable.

## Milestone 28
### Date
2026-06-10

### Goal
Implement TDSR Milestone 6 by adding browser-side camera hand-sign detection to the RPS client while preserving the manual sign buttons.

### What changed
- Extended the RPS static client:
  - `src/main/resources/public/rps/index.html`
  - `src/main/resources/public/rps/script.js`
- Added a Handkamera panel with:
  - local camera preview
  - hand landmark overlay canvas
  - camera start/stop controls
  - confidence threshold slider
  - auto-send toggle
  - camera detection, confidence, and stability metrics
- Integrated MediaPipe Gesture Recognizer through dynamic browser import so manual controls still work if the model or camera path is unavailable.
- Mapped MediaPipe canned gestures to normalized RPS signs:
  - `Closed_Fist -> rock`
  - `Victory -> scissor`
  - `Open_Palm -> paper`
- Camera-detected signs emit the same `obs.hand.sign` event contract as manual signs, with:
  - `source`: `rps.web.camera`
  - `detectionMode`: `client_camera`
  - `confidence`
  - `cannedGesture`
  - `stabilityFrames`
- Added confidence and stability gates before auto-emitting camera observations.
- Reset the camera auto-send gate on game start, user readiness/play-again utterances, and each new GIGI `motion.handSign` reveal.
- Extended `RpsClientStaticResourceContractTest` to cover camera controls, MediaPipe integration hooks, gesture mapping, and camera payload metadata.
- Updated README and `.agents/TDSR.md`.

### How to run
1. Configure properties as in `README.md`.
2. Run the seed class manually:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open the RPS client:
   - `http://localhost:8080/rps/?agentId=<uuid>`
5. Use manual buttons as before, or start `Handkamera`, enable `Auto senden`, and show:
   - closed fist for `rock`
   - victory sign for `scissor`
   - open palm for `paper`

### How to test
- Executed:
  - `node --check src/main/resources/public/rps/script.js`
  - `.\mvnw.cmd -q "-Dtest=RpsClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,RpsRulesUnitTest,DeterministicRpsSignSelectorUnitTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`
- Local HTTP smoke against the running Spring Boot app:
  - `http://localhost:8080/rps/?agentId=11111111-1111-1111-1111-111111111111`
  - verified `200 OK` after redirect and presence of camera controls, camera confidence slider, camera auto-send toggle, and manual sign controls.

### Known issues and decisions
- Camera inference is browser-side and depends on camera permission, lighting, pose, MediaPipe model availability, and CDN access.
- The MediaPipe model recognizes canned gestures, not a custom RPS-trained model. The current mapping uses the closest canned static gestures.
- The manual buttons remain the reliable fallback path and emit the same normalized event contract.
- Dynamic import keeps the manual client usable if MediaPipe loading fails.
- The in-app browser target was unavailable in this session, so interactive camera verification was not executed. Local HTTP smoke and static client contract tests covered the non-camera hardware surface.
- The targeted Maven run passed, but Surefire printed its existing fork-shutdown warning after the Spring SSE replay test closed; surefire reports show zero failures and zero errors.

### Next steps
1. Manually verify camera classification on the target demo machine with real lighting and camera angle.
2. Tune the confidence threshold and stability frame count if live demos produce false positives.
3. Consider a custom gesture model or server-side image interpretation only if MediaPipe canned gestures are not reliable enough.

## Milestone 29
### Date
2026-06-12

### Goal
Add a unified browser client for GIGI TDSR agent testing and demos so users can interact through text or realtime speech, run visual sensing, use manual scenario inputs, and inspect multimodal behaviour from one page.

### What changed
- Added the GIGI Demo Cockpit static client:
  - `src/main/resources/public/gigi-demo/index.html`
  - `src/main/resources/public/gigi-demo/script.js`
- Added redirect aliases:
  - `/gigi-demo`
  - `/gigi`
  - `/tdsr`
- The cockpit:
  - discovers existing agents from `GET /agent`
  - accepts direct `?agentId=<uuid>` and persists the selected agent locally
  - starts/resets agents and subscribes to behaviour/monitor SSE
  - supports text interaction through `obs.user_utterance`
  - supports realtime speech-to-speech through the existing Realtime session and prompt profile flow
  - exposes realtime voice, temperature, continuous listening, push-to-talk, and backend complement controls
  - combines camera controls for face emotion, social grouping, and RPS hand-sign detection
  - preserves manual fallback controls for conversation shortcuts, social grouping samples, and RPS signs
  - renders `BehaviourPlan.speech`, `nonVerbal`, `motion.handSign`, and `display`
  - includes a diagnostics drawer for activity and storage snapshots
- Added static contract coverage:
  - `src/test/java/ch/zhaw/prometheus/controllers/GigiDemoClientStaticResourceContractTest.java`
- Extended redirect coverage:
  - `src/test/java/ch/zhaw/prometheus/controllers/StaticRedirectControllerWebMvcTest.java`
- Updated README client documentation and `.agents/TDSR.md`.

### How to run
1. Configure properties as in `README.md`.
2. Seed one of the GIGI TDSR agents:
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/GuessingGameWithGestures.java`
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/SocialContextSensitivity.java`
   - `src/test/java/ch/zhaw/prometheus/agents/gigitdsr/RockScissorPaper.java`
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/?agentId=<uuid>`
   - or `http://localhost:8080/tdsr/?agentId=<uuid>`

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest" test`
  - local static HTTP smoke against `http://127.0.0.1:8095/gigi-demo/index.html?agentId=11111111-1111-1111-1111-111111111111`, verifying `200 OK` and presence of the cockpit, realtime, and hand-sensing controls

### Known issues and decisions
- The cockpit selects existing persisted agents; it does not yet create or seed TDSR demo agents from the browser.
- Browser-side sensing still depends on camera permission, local hardware, lighting, pose, and CDN/model availability.
- Manual scenario buttons intentionally remain first-class fallbacks so demos can proceed without reliable camera sensing.
- The cockpit reuses existing event contracts and does not add new event types, behaviour modalities, or backend agent semantics.
- The in-app browser backend was unavailable in this session, so interactive browser/camera validation still needs to be run on a live app with seeded agents and camera access.

### Next steps
1. Add a one-click GIGI TDSR demo-agent registry/seed endpoint if browser-driven demo setup becomes a requirement.
2. Run a live rehearsal with the cockpit on the target demo machine and tune camera thresholds.
3. Consider extracting shared browser sensing utilities if future clients duplicate the cockpit detector code.

## Milestone 30
### Date
2026-06-12

### Goal
Migrate PROMETHEUS realtime speech-to-speech and listening clients from the retired OpenAI Realtime beta contract to the GA client-secret and WebRTC calls contract.

### What changed
- Migrated backend Realtime configuration and session creation:
  - replaced `openai.realtimeSessionUrl` / `openai.realtimeUrl` with `openai.realtimeClientSecretUrl` / `openai.realtimeCallsUrl`
  - changed the default realtime model to `gpt-realtime`
  - updated `RealtimeSessionClient` to call `/v1/realtime/client_secrets`
  - changed the session request payload to the GA nested `session` shape with `type=realtime`, `output_modalities`, and `audio.input.transcription`
  - changed response parsing to read the top-level client secret `value`
  - returned `realtimeCallsUrl` through `RealtimeSessionInfo` and `RealtimeSessionView`
- Migrated browser WebRTC setup to post SDP to `sessionInfo.realtimeCallsUrl` in:
  - `src/main/resources/public/realtime/script.js`
  - `src/main/resources/public/gigi-demo/script.js`
  - `src/main/resources/public/multilateral/listen/script.js`
- Updated browser Realtime event/config payloads:
  - speech clients now use `output_modalities: ["audio"]`
  - assistant transcript handling now listens for `response.output_audio_transcript.delta` and `response.output_audio_transcript.done`
  - session updates now use nested `audio.input.turn_detection` and `audio.output.voice`
  - removed the obsolete realtime temperature controls from `/realtime` and `/gigi-demo`
  - multilateral listening now sends a GA non-responding realtime session update with nested audio turn detection
- Added regression coverage:
  - `RealtimeSessionClientTest`
  - `RealtimeBrowserClientContractTest`
  - updated `GigiDemoClientStaticResourceContractTest`
- Updated README and OpenAI property templates for the GA property names.

### How to run
1. Configure properties as in `README.md`, using:
   - `openai.realtimeModel=gpt-realtime`
   - `openai.realtimeClientSecretUrl=https://api.openai.com/v1/realtime/client_secrets`
   - `openai.realtimeCallsUrl=https://api.openai.com/v1/realtime/calls`
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open one of the realtime-backed clients:
   - `http://localhost:8080/realtime/?agentId=<uuid>`
   - `http://localhost:8080/gigi-demo/?agentId=<uuid>`
   - `http://localhost:8080/multilateral/listen/?agentId=<uuid>`

### How to test
- Executed:
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSessionClientTest,RealtimeBrowserClientContractTest,GigiDemoClientStaticResourceContractTest" test`

### Known issues and decisions
- Realtime session creation remains OpenAI-only; Azure OpenAI is still rejected by `RealtimeSessionClient`.
- This milestone intentionally removes the browser temperature controls instead of carrying a stale beta-shaped setting.
- `/multilateral/listen` still uses a non-responding realtime session. A later milestone can migrate it to a dedicated GA transcription session if that becomes important.
- Local ignored `src/main/resources/openai.properties` files that still contain old beta property names must be updated manually.
- Browser microphone/WebRTC validation still requires a live app, an OpenAI key, and browser permissions.

### Next steps
1. Run a live browser rehearsal for `/realtime`, `/gigi-demo`, and `/multilateral/listen` with real microphone input.
2. Consider adding `marin` and `cedar` voice options and a server-side `OpenAI-Safety-Identifier`.
3. Decide whether multilateral listening should use a dedicated Realtime transcription session.

## Milestone 31
### Date
2026-06-12

### Goal
Harden the GA Realtime demos after the migration by moving `/multilateral/listen` to a true transcription session, tightening speech response orchestration, and updating demo configuration/options.

### What changed
- Added backend Realtime transcription-session support:
  - new endpoint: `POST /realtime/transcription/session`
  - default transcription model: `openai.realtimeTranscriptionModel=gpt-realtime-whisper`
  - optional transcription hints: `openai.realtimeTranscriptionLanguage`, `openai.realtimeTranscriptionDelay`
  - optional `openai.realtimeSafetyIdentifier`, sent as `OpenAI-Safety-Identifier` when minting client secrets
- Updated `/multilateral/listen` to request a true GA transcription session instead of a voice-agent session instructed not to respond.
- Added periodic `input_audio_buffer.commit` events for `/multilateral/listen` when using `gpt-realtime-whisper`, which omits turn detection.
- Hardened transcript event handling to read GA `conversation.item.input_audio_transcription.delta` deltas correctly.
- Added `cedar` and `marin` voice options to `/realtime` and `/gigi-demo`.
- Tuned realtime speech orchestration around `acknowledge.responseEvent`:
  - if acknowledge returns speech, clients speak that exact stored response and update Realtime instructions
  - clients skip the extra model-generated response for that same user transcript
  - `/realtime` also remembers the stored speech to avoid an SSE-triggered duplicate
- Removed the stale `renderTemperatureValue()` startup call from `/gigi-demo` after Milestone 30 removed temperature controls.
- Added/updated regression coverage:
  - `RealtimeSessionClientTest`
  - `RealtimeControllerWebMvcTest`
  - `RealtimeBrowserClientContractTest`
  - `GigiDemoClientStaticResourceContractTest`
- Updated README and OpenAI property templates for the new transcription and safety properties.

### How to run
1. Configure properties as in `README.md`, using:
   - `openai.realtimeModel=gpt-realtime`
   - `openai.realtimeTranscriptionModel=gpt-realtime-whisper`
   - `openai.realtimeClientSecretUrl=https://api.openai.com/v1/realtime/client_secrets`
   - `openai.realtimeCallsUrl=https://api.openai.com/v1/realtime/calls`
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/realtime/?agentId=<uuid>`
   - `http://localhost:8080/gigi-demo/?agentId=<uuid>`
   - `http://localhost:8080/multilateral/listen/?agentId=<uuid>`

### How to test
- Executed:
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSessionClientTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,GigiDemoClientStaticResourceContractTest" test`
  - app-backed HTTP smoke against the running Spring Boot app at `http://127.0.0.1:8080`, verifying `200 OK` and key controls/configuration markers for `/realtime`, `/gigi-demo`, and `/multilateral/listen`

### Known issues and decisions
- Realtime client-secret creation remains OpenAI-only; Azure OpenAI is still rejected by `RealtimeSessionClient`.
- `gpt-realtime-whisper` does not use VAD in this setup, so `/multilateral/listen` chunks audio with a fixed commit interval. The interval may need live tuning for the demo room.
- `openai.realtimeSafetyIdentifier` is a configured value because PROMETHEUS does not yet have authenticated per-user identity in these demo endpoints.
- Shared realtime JS helpers were not extracted in this milestone; the current duplication is tolerable and the helper boundary should be chosen after live rehearsal pressure is clearer.
- The in-app browser backend was unavailable in this session (`agent.browsers.list()` returned no browsers), so interactive browser automation was not executed.
- Browser microphone/WebRTC validation still requires a live app, an OpenAI key, seeded agents, and browser permissions.

### Next steps
1. Run a live rehearsal with seeded GIGI TDSR agents and real microphone input.
2. Tune `/multilateral/listen` commit interval and transcription delay against the target demo room.
3. Consider extracting shared Realtime browser helpers if the three clients continue changing together.

## Milestone 32
### Date
2026-06-12

### Goal
Make GIGI demo visual sensing modes independently toggleable at runtime so face emotion, social grouping, and hand-sign sensing can run in any combination or one at a time.

### What changed
- Updated `src/main/resources/public/gigi-demo/script.js` so sensing mode checkboxes are handled by a dedicated runtime mode-change path.
- New mode behavior:
  - newly enabled modes load their model while the camera is already running
  - disabled modes are skipped immediately by the camera loop
  - stale mode-specific metrics and duplicate gates are cleared when a mode is disabled
  - camera status returns to `Camera Live` after live model loading completes
- Replaced the shared visual sensing event throttle with per-mode timestamps:
  - face emotion uses `lastEmotionEmitAt`
  - social grouping uses `lastSocialEmitAt`
  - hand sign keeps its existing hand-sign duplicate gate
- Preserved the single camera loop and overlay composition while making detector enablement explicit through `isSensorModeEnabled(...)`.
- Extended `GigiDemoClientStaticResourceContractTest` to assert runtime sensing mode handling and independent emit gates.
- Updated README with the GIGI demo combined-sensing behavior.

### How to run
1. Configure properties as in `README.md`.
2. Seed a GIGI TDSR agent.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/?agentId=<uuid>`
5. Start the camera, then turn face emotion, social grouping, and hand-sign sensing on or off in any combination.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright scripted smoke against the live local app with mocked agent/profile responses verifying:
    - RPS profile shows hand-sign sensing plus speech, motion/sign, and display rows while hiding emotion/social/gesture rows
    - social-context profile shows social sensing plus speech while hiding hand/emotion/display rows
    - speech-only profile hides visual sensing controls and non-speech behaviour rows
    - empty profile fallback keeps the full cockpit visible
    - no local page HTTP responses with status `>=400`

### Known issues and decisions
- This milestone keeps the existing browser-side detector libraries and event contracts; it changes runtime coordination only.
- Detector execution is still sequential within one camera loop, not parallel worker execution.
- Live camera quality still depends on browser permission, lighting, model/CDN availability, and local hardware.
- Hand-sign camera events still require `Auto-send hand sign`; manual hand-sign buttons remain available independently of camera mode.

### Next steps
1. Rehearse live combined sensing on the target demo machine and tune thresholds if needed.
2. Consider exposing per-mode emit interval controls if demos need different rates for emotion versus social context.
3. Consider extracting shared sensing helpers if another client needs the same runtime mode behavior.

## Milestone 33
### Date
2026-06-12

### Goal
Improve the GIGI demo cockpit UI layout and remove implicit stale agent connections by making agent selection explicit.

### What changed
- Moved agent selection, Agent ID entry, connection, start, reset, and agent metadata controls into a new Agent tab in the existing drawer.
- Kept diagnostics in the same drawer under a separate Diagnostics tab.
- Removed implicit page-load agent selection:
  - no localStorage Agent ID is read or written
  - no first GIGI agent is auto-selected from `/agent`
  - only an explicit `?agentId=` URL or drawer selection/paste populates the Agent ID
- Hardened connection behavior so `/info` must succeed before event history, storage, behaviour SSE, or monitor SSE are opened.
- Split the center column into Text and Realtime Speech tabs.
- Moved sensing and sensed input-signal metrics to the left column.
- Moved rendered `BehaviourPlan` output to the right column.
- Reframed the old Scenario card as concrete manual event shortcuts:
  - conversation shortcuts in the Text tab
  - manual hand-sign and social-sample inputs in the Sensing card
- Extended `GigiDemoClientStaticResourceContractTest` to cover drawer tabs, interaction tabs, explicit agent selection, validated stream connection, and the manual event shortcut layout.
- Updated README with the new cockpit layout and connection behavior.

### How to run
1. Configure properties as in `README.md`.
2. Seed a GIGI TDSR agent.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/`
   - or `http://localhost:8080/gigi-demo/?agentId=<uuid>`
5. Use the Agent drawer tab to select or paste an agent, connect, then start the agent when needed.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`

### Known issues and decisions
- `Connect` and `Start Agent` remain separate because they affect different layers: connecting validates the selected agent and opens browser streams; starting invokes the backend runtime `/{agentID}/start` endpoint.
- The cockpit still supports explicit URL agent IDs for rehearsals and shared links.
- Manual event shortcuts remain TDSR-demo-oriented, not generic framework-wide controls.
- Live browser/camera/microphone verification still depends on a running app, seeded agents, browser permissions, and local hardware.

### Next steps
1. Rehearse the new layout with seeded GIGI agents and confirm the drawer interaction is ergonomic during demos.
2. Consider adding lightweight seeded-demo affordances if selecting agents from the drawer remains too manual.
3. Extract client-side layout or realtime helpers only if additional cockpit variants reuse the same patterns.

## Milestone 34
### Date
2026-06-12

### Goal
Clean up the GIGI demo cockpit sensing and behaviour panels so the left column is shorter, sensed signals are grouped with sensing controls, and behaviour output is easier to scan.

### What changed
- Reworked the Sensing card into a single accordion with these sections:
  - Detectors
  - Configuration
  - Manual Emotion
  - Manual Social
  - Manual Hand Sign
  - Signals Sensed
- Added manual facial emotion buttons that emit the existing `obs.emotion.face` contract with `source=visual.facial.manual` and `detectionMode=manual`.
- Moved sensed signal readouts into the accordion and rendered each signal as a full-width row.
- Kept accordion headers expandable before agent connection while leaving action controls disabled until a valid agent is connected.
- Renamed the visible camera hand-sign readout from `Camera Sign` to `Hand Sign`.
- Removed the generic Latest Event row from the sensing panel.
- Added a behaviour-scoped `Latest Behaviour Event` row to the Behaviour card.
- Changed the Behaviour card from compact grids to full-width rows for:
  - speech
  - gesture
  - face
  - gaze
  - motion
  - GIGI sign
  - user sign
  - round
  - winner
  - display
  - latest behaviour event
- Updated `GigiDemoClientStaticResourceContractTest` to cover the new accordion sections, manual emotion emission, renamed hand-sign readout, full-width behaviour rows, and removal of stale generic event/grid hooks.
- Updated README with the new cockpit panel organization.

### How to run
1. Configure properties as in `README.md`.
2. Seed a GIGI TDSR agent.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/`
   - or `http://localhost:8080/gigi-demo/?agentId=<uuid>`
5. Use the sensing accordion to configure detectors, trigger manual emotion/social/hand observations, and inspect sensed signals.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`

### Known issues and decisions
- This milestone intentionally keeps the cockpit TDSR-demo-oriented and does not introduce agent-declared sensing/behaviour capabilities.
- Observation events now surface primarily through the Activity log and specific sensed-signal rows; only `resp.behaviour_plan` events update the Behaviour card's latest event row.
- Manual emotion payload values use deterministic representative valence/arousal defaults, not model-derived affect estimation.
- Live browser/camera/microphone verification still depends on a running app, seeded agents, browser permissions, and local hardware.

### Next steps
1. Discuss an optional agent interaction profile or capability declaration for supported sensing signals and behaviour modalities.
2. Rehearse the accordion layout during a real GIGI demo and adjust default-expanded sections if needed.
3. Consider making manual event shortcuts profile-driven once agent capability declarations exist.

## Milestone 35
### Date
2026-06-12

### Goal
Remove ambiguity from the GIGI demo Agent drawer by separating agent selection from connection and making the Connect button a visible connect/disconnect control.

### What changed
- Added a separate selected-agent state in the GIGI demo client.
- Changed Agent drawer behavior:
  - choosing an agent in the dropdown only fills/selects the Agent ID
  - editing the Agent ID field only updates the selected Agent ID
  - `Connect` validates the selected ID and opens info/history/storage/SSE streams
  - once connected, the button changes to `Disconnect`
  - `Disconnect` closes streams, stops realtime/camera if running, resets active agent state, and preserves the selected ID for reconnect
- Added a visible connection-state row in the Agent drawer.
- Kept explicit `?agentId=<uuid>` URLs auto-connecting for rehearsal/shared links.
- Preserved the separation between `Connect` and `Start Agent`: connecting affects browser streams; starting calls the backend runtime start endpoint.
- Updated `GigiDemoClientStaticResourceContractTest` to assert the selected-vs-connected model and guard against dropdown auto-connect regressions.
- Updated README with the clarified Agent drawer lifecycle.

### How to run
1. Configure properties as in `README.md`.
2. Seed a GIGI TDSR agent.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/`
5. Open the Agent drawer tab, choose an agent, click `Connect`, then use `Start Agent` only when the backend runtime should start.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright scripted smoke outside the repo verifying:
    - dropdown selection updates selected ID and does not open behaviour/monitor streams
    - `Connect` opens behaviour and monitor streams and enables interaction controls
    - `Disconnect` preserves the selected ID and disables interaction controls again
    - no page HTTP responses with status `>=400`

### Known issues and decisions
- The cockpit still auto-connects when an explicit `?agentId=<uuid>` is present because shared rehearsal links should remain one-step.
- Dropdown browsing no longer opens SSE streams or validates the agent.
- `Disconnect` is a client-side stream/session disconnect, not a backend agent reset or stop operation.
- The agent capability declaration milestone remains separate and should build on this clearer connection lifecycle.

### Next steps
1. Implement the agent interaction profile/capability declaration as a framework-level feature.
2. Use the declared profile in the GIGI demo to hide irrelevant controls and behaviour rows.
3. Consider a visual connected badge in the page header if live demos need stronger state signalling.

## Milestone 36
### Date
2026-06-12

### Goal
Add a framework-level agent interaction profile so agents can declare expected observation signals and supported behaviour modalities through persisted metadata instead of frontend heuristics or runtime storage.

### What changed
- Added `AgentInteractionProfile` with stable string identifiers for supported observations, supported behaviour modalities, and optional profile tags.
- Added reusable GIGI/TDSR profile factories in `AgentInteractionProfiles`.
- Persisted the profile on the `Agent` aggregate as a typed JSON value stored in the database-backed agent row.
- Exposed the profile through `AgentInfoView`, including `GET /agent`, `GET /agent/{id}`, and `GET /{agentID}/info`.
- Kept agents without an explicit profile compatible by exposing an empty profile rather than `null`.
- Annotated the seeded GIGI TDSR agents:
  - gesture guessing game: user utterance input plus speech and nonverbal gesture/face/gaze output
  - social context sensitivity: user utterance, human presence, grouping, and social situation change input plus speech output
  - Schere-Stein-Papier: user utterance and hand-sign input plus speech, `motion.handSign`, and display output
- Updated README to document the profile as agent metadata, not runtime `Storage`.

### How to run
1. Configure properties as in `README.md`.
2. Seed or create agents.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Inspect an agent profile:
   - `GET http://localhost:8080/{agentID}/info`
   - `GET http://localhost:8080/agent`

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfileUnitTest,AgentApplicationServicePromptUnitTest,AgentClientCompatibilityWebMvcTest,AgentInteractionProfilePersistenceUnitTest" test`

### Known issues and decisions
- The profile is persisted as a typed JSON column on `Agent`, not as runtime storage and not as normalized child tables yet.
- This is a deliberate prototype-stage compromise: the metadata is database-backed and type-checked in Java, while the vocabulary can still evolve without schema churn.
- The GIGI demo still renders known TDSR controls and behaviour rows; it does not yet hide controls based on the profile.
- Existing agents created before this milestone expose an empty profile until reseeded or updated.

### Next steps
1. Use `interactionProfile` in the GIGI demo to hide irrelevant sensing controls and behaviour rows after agent connection.
2. Consider creation/update API support for setting interaction profiles outside seed tests.
3. Normalize profile tables later if SQL querying by supported signal/modality becomes a real requirement.

## Milestone 37
### Date
2026-06-12

### Goal
Make the GIGI demo consume `AgentInfoView.interactionProfile` so sensing controls and behaviour rows reflect the selected agent's declared observations and behaviour modalities.

### What changed
- Added profile visibility metadata to GIGI demo sensing sections, detector/config controls, sensed-signal rows, and behaviour rows.
- Added frontend profile consumption after `/{agentID}/info` loads:
  - `supportedObservations` controls sensing accordion visibility.
  - `supportedBehaviourModalities` controls behaviour row visibility.
  - missing or empty profiles keep the full cockpit visible as a conservative fallback for older agents.
- Turned off unsupported sensor modes when a connected profile hides those observations, preventing hidden detectors from continuing to emit unsupported events.
- Kept profile handling explicit and did not infer capabilities from agent names or descriptions.
- Updated `GigiDemoClientStaticResourceContractTest` to guard the profile-driven UI hooks.
- Updated README with the profile-driven cockpit behavior.

### How to run
1. Configure properties as in `README.md`.
2. Seed GIGI TDSR agents with interaction profiles.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/gigi-demo/`
5. Connect different profiled agents and verify the sensing accordion and behaviour card adapt to the selected profile.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`

### Known issues and decisions
- Empty profiles are treated as "unknown" and show the full cockpit rather than hiding everything.
- Profile matching supports exact tokens and prefix relationships, so a declared `motion.handSign` can satisfy generic `motion` rows.
- The demo still uses static markup with profile attributes; it does not dynamically create controls from profile definitions yet.

### Next steps
1. Run live rehearsal with seeded GIGI agents and adjust any row mappings that feel too broad or too narrow.
2. Consider profile-aware defaults for expanded accordion sections after more demo use.
3. Consider creation/update API support for setting interaction profiles outside seed tests.

## Milestone 38
### Date
2026-06-12

### Goal
Retroactively add interaction profile declarations to existing seed/test agent templates so profile-driven clients do not have to fall back to generic cockpit controls for known agents.

### What changed
- Added common `AgentInteractionProfiles` factories:
  - `speechOnly()`
  - `speechWithNonverbal()`
  - `multimodalInput()`
  - `multimodalOutput()`
  - `multimodalInputOutput()`
- Added `nonVerbal.motion` as a declared modality because existing nonverbal prompts can emit nested motion intent.
- Annotated the basic single-state and four-state seed agents as `speechOnly`.
- Annotated the Pflegezentrum seed agents as `speechOnly`.
- Annotated multimodal seed agents according to their actual prompt behavior:
  - `SingleStateMultimodalIn`: visual observations plus speech and nonverbal output
  - `SingleStateMultimodalOut`: speech and nonverbal output
  - `SingleStateMultimodalInOut`: visual observations plus speech and nonverbal output
- Kept specialized TDSR declarations and expanded the gesture guessing-game declaration to include `nonVerbal.motion`.
- Added seed-profile contract coverage:
  - reusable fixtures expose expected profiles
  - all seed-agent source files contain an explicit `setInteractionProfile(...)`
  - common profile factories expose the expected observation and modality lists
- Updated README to point developers to the common profile factories.

### How to run
1. Configure properties as in `README.md`.
2. Rerun seed tests for any agents that should have persisted profiles in the local database.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Inspect profiles through:
   - `GET http://localhost:8080/agent`
   - `GET http://localhost:8080/{agentID}/info`

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest" test`

### Known issues and decisions
- Already-persisted database rows are not automatically backfilled; reseed or update agents to persist the new profile metadata.
- The static source contract intentionally covers manual seed classes that do not expose reusable fixture methods.
- `SingleStateMultimodalIn` is profiled as input plus output because the current code sets nonverbal output prompts despite the class name.

### Next steps
1. Add profile creation/update API support if profiles need to be edited without reseeding.
2. Consider reusable fixture methods for the package-local multimodal and Pflegezentrum seed agents if more tests need direct object assertions.
3. Rehearse the GIGI cockpit against reseeded agents and tune any profile-to-row mapping that feels too broad.

## Milestone 39
### Date
2026-06-12

### Goal
Hide all visual sensing controls in the GIGI demo when the connected agent profile declares no visual observations, and show a clear empty state instead.

### What changed
- Added a no-visual-sensing empty state to the GIGI demo sensing card.
- Updated profile-driven visibility to toggle both `hidden` and Bootstrap `d-none`, so flex/grid utility classes cannot keep hidden profile sections visible.
- Hid the sensing accordion, camera viewer, and Start/Stop camera controls when the profile has no visual observations.
- Kept the conservative fallback behavior: missing or empty profiles still show the full cockpit for older agents.
- Stopped an active camera session if a newly applied profile removes visual sensing support.
- Extended `GigiDemoClientStaticResourceContractTest` to guard the new empty-state hooks and Bootstrap-safe visibility behavior.
- Updated README with the no-visual-sensing cockpit behavior.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect a speech-only or otherwise nonvisual profiled agent and verify the sensing card shows the no-visual-sensing message instead of camera controls.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright smoke against the running app at `http://127.0.0.1:8080/gigi-demo/`, verifying:
    - fallback/empty profiles keep camera controls, camera viewer, and sensing accordion visible
    - speech-only profiles hide Start/Stop, camera viewer, and sensing accordion while showing the no-visual-sensing message
    - hand-sign visual profiles keep visual sensing visible and hide the empty state

### Known issues and decisions
- Empty profiles still mean "unknown capabilities" and intentionally keep the full cockpit visible.
- The empty state appears only for explicit profiles that declare no visual observations.
- The sensing card remains visible as the container for the empty-state message.

### Next steps
1. Rehearse with reseeded speech-only and visual GIGI agents to confirm the profile declarations match demo expectations.
2. Consider making the empty-state text more agent-specific if profile metadata later includes display labels or descriptions.
3. Add profile creation/update API support if profiles need to be changed without reseeding.

## Milestone 40
### Date
2026-06-12

### Goal
Hydrate the GIGI demo Text interaction transcript from existing agent event history when an agent is connected or reopened.

### What changed
- Updated `loadEventHistory()` so historical assistant `resp.behaviour_plan` events append their `speech` content to the Text tab transcript.
- Added historical `obs.user_utterance` rendering so prior user utterances appear as user bubbles.
- Preserved behaviour panel hydration from the same event history: the latest behaviour event and modality previews still update from historical behaviour plans.
- Kept behaviour duplicate suppression through `seenBehaviourKeys`, so behaviour stream replay does not duplicate historical assistant speech after connect.
- Extended `GigiDemoClientStaticResourceContractTest` to guard user and assistant transcript hydration from event history.
- Updated README with the reopened-agent transcript behavior.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect an agent that already has event history and verify the Text tab shows prior user utterances and assistant speech.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright smoke against the running app at `http://127.0.0.1:8080/gigi-demo/`, with mocked agent info/history verifying:
    - starter assistant speech from history appears in the Text tab
    - historical user utterances appear as user bubbles
    - later assistant behaviour-plan speech appears as assistant bubbles in order

### Known issues and decisions
- Only `obs.user_utterance` and `resp.behaviour_plan.speech` hydrate the text transcript; visual observations remain in the sensing/behaviour panels.
- The Text tab still begins with the local `Connected.` system message before the historical conversation.
- Historical assistant speech is rendered immediately rather than replayed incrementally.

### Next steps
1. Rehearse against real persisted agents that were started during seeding and against agents reopened after a prior demo session.
2. Consider adding timestamps to transcript bubbles if event-history review becomes important during demos.
3. Consider a transcript clear/reload distinction if users need to hide local view state without losing history.

## Milestone 41
### Date
2026-06-12

### Goal
Simplify GIGI demo camera observation emission so hand-sign detections use the same global emit gate as face emotion and social grouping detections.

### What changed
- Removed the separate `Auto-send hand sign` checkbox from the sensing configuration.
- Renamed the global sensing switch from `Emit events` to `Emit camera observations`.
- Updated camera hand-sign emission so a detected hand sign is sent when:
  - hand-sign detector is enabled
  - `Emit camera observations` is enabled
  - confidence threshold passes
  - stable-frame threshold passes
  - duplicate cooldown passes
- Kept manual hand-sign buttons independent of camera observation emission.
- Extended `GigiDemoClientStaticResourceContractTest` to assert that the auto-send hand-sign gate is gone and camera hand signs use the global emit gate.
- Updated README with the unified camera observation emission behavior.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect an RPS-profiled agent, start the camera, enable Hand sign and `Emit camera observations`, then hold a stable rock/scissor/paper gesture.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright smoke against the running app at `http://127.0.0.1:8080/gigi-demo/`, verifying the old hand auto-send checkbox is absent and the unified emit label is visible.

### Known issues and decisions
- This milestone intentionally removes the previous preview-only camera hand-sign mode from the GIGI demo.
- Confidence, stable-frame, and duplicate cooldown gates still protect against accidental repeated hand-sign submissions.
- Manual hand-sign buttons still submit immediately because they represent explicit operator input rather than camera observations.

### Next steps
1. Rehearse the RPS flow with live camera input and adjust confidence/stability thresholds if signs are still too noisy or too hard to send.
2. Consider per-detector emission toggles only if a real demo need emerges for previewing one enabled detector while emitting another.
3. Consider surfacing the stable-frame requirement in diagnostics if users struggle to understand why a visible hand sign was not emitted.

## Milestone 42
### Date
2026-06-12

### Goal
Show the selected agent's declared interaction profile in the GIGI demo Agent drawer alongside the existing Agent ID, name, and description.

### What changed
- Added an Interaction Profile section to the Agent drawer info panel.
- Rendered three profile lists:
  - supported observations
  - supported behaviour modalities
  - profile tags
- Kept empty profile lists readable with `-` placeholders.
- Used DOM text assignment for profile tokens rather than injecting raw HTML.
- Extended `GigiDemoClientStaticResourceContractTest` to guard the new drawer markup and rendering hooks.
- Updated README with the expanded Agent drawer metadata.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect a profiled agent and inspect the Agent drawer.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright smoke against the running app at `http://127.0.0.1:8080/gigi-demo/`, with mocked agent info verifying observation, behaviour, and tag tokens render in the drawer.

### Known issues and decisions
- The drawer renders stable profile identifiers directly, not friendly labels, because these identifiers are the current framework contract.
- Empty profiles still display `-` rather than hiding the section, so older or unprofiled agents are explicit.
- Profile editing remains out of scope; the drawer is read-only.

### Next steps
1. Consider adding human-readable labels only after the profile vocabulary stabilizes.
2. Consider a copy-to-clipboard affordance if profile identifiers are frequently used during debugging.
3. Add profile creation/update API support if profiles need to be changed without reseeding.

## Milestone 43
### Date
2026-06-12

### Goal
Prevent the GIGI demo from rendering the same assistant behaviour twice when a response is received once through the HTTP response body and again through the behaviour SSE stream.

### What changed
- Added short-lived payload-level duplicate suppression for live behaviour events.
- Kept persisted-event-key suppression for behaviour events with `createdDate`.
- Kept event-history hydration independent of the live duplicate window so repeated historical utterances still render.
- Reset behaviour de-duplication state when connecting, disconnecting, or resetting an agent.
- Extended `GigiDemoClientStaticResourceContractTest` to guard the immediate HTTP/SSE duplicate suppression path.
- Updated README with the duplicate-render suppression behavior.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect an RPS-profiled agent and play a round; each assistant utterance should appear once in the transcript.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Playwright smoke against the running app at `http://127.0.0.1:8080/gigi-demo/`, simulating an immediate HTTP `responseEvent` followed by the same persisted SSE behaviour event and verifying only one assistant bubble/log entry renders.

### Known issues and decisions
- This is a UI de-duplication fix. Backend event storage still records behaviour through the normal event-history append path.
- The duplicate window is intentionally short so a legitimately repeated utterance in a later turn can still render.
- Without the specific live agent ID, the exact database history from the reported run was not inspected.

### Next steps
1. Re-test the RPS flow with the same agent and confirm the transcript no longer doubles each assistant utterance.
2. If duplicate persisted events are ever observed in `/eventhistory`, add backend-level replay assertions for that agent flow.
3. Consider exposing event IDs in diagnostics if future demo debugging needs to distinguish HTTP responses from SSE delivery.

## Milestone 44
### Date
2026-06-12

### Goal
Improve the GIGI demo Diagnostics drawer so storage, activity log, and state information are easier to inspect during live agent rehearsals.

### What changed
- Replaced the raw storage JSON dump with a list-group storage accordion:
  - each storage key is an expandable row
  - values are rendered with JSON pretty-printing when possible
  - each row has a clipboard icon button that copies the rendered value
- Added Activity log controls:
  - clear local log view
  - toggle wrapped lines versus one-line horizontal scrolling
  - toggle timestamp visibility
- Added a state diagnostics section showing:
  - current innermost state
  - available states
  - a `current` badge for the active outer state and active inner-state chain
- Reused the existing monitor snapshot contract for live state/storage updates and added initial REST refreshes through `/{agentID}/state`, `/{agentID}/states`, and `/{agentID}/storage`.
- Extended `GigiDemoClientStaticResourceContractTest` to guard diagnostics drawer markup and render/update hooks.
- Updated README with the expanded Diagnostics drawer behavior.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect an agent, open the drawer Diagnostics tab, and inspect Activity, Current State, States, and Storage.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`
  - Temporary Chrome/CDP smoke against a mocked GIGI demo server, verifying:
    - current state renders as the innermost state
    - active states receive `current` badges
    - storage values render as pretty-printed JSON
    - activity log wrap, timestamp, and clear controls work

### Known issues and decisions
- Clearing the Activity log only clears the local diagnostics view; it does not clear event history or backend logs.
- Storage copy buttons copy the formatted value shown in the drawer, not the raw database cell.
- The state list uses the existing monitor-client convention: the current outer state and active inner-state chain are badged as `current`.

### Next steps
1. Rehearse the drawer against a live RPS agent and confirm the state/storage updates are useful during hand-sign rounds.
2. Consider adding event IDs or response-source tags to diagnostics if future duplicate-delivery investigations need more detail.
3. Consider sharing diagnostics rendering helpers with `/monitor` if further code duplication appears.

## Milestone 45
### Date
2026-06-12

### Goal
Align GIGI demo face and social-grouping overlay boxes with the mirrored camera self-view.

### What changed
- Confirmed the displayed video is mirrored with CSS while detector coordinates are produced in raw camera-frame coordinates.
- Added `mirroredOverlayBox(...)` to convert detector-frame boxes into display-frame boxes before drawing.
- Updated face-emotion and social-grouping box drawing to use mirrored display coordinates.
- Kept hand landmarks unchanged because they were already mirrored manually with `(1 - p.x)`.
- Extended `GigiDemoClientStaticResourceContractTest` to guard the mirrored overlay helper and prevent raw unmirrored box drawing from returning.
- Updated README to document that camera overlay boxes align with the mirrored self-view.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/gigi-demo/`
4. Connect an agent with visual observations, start the camera, enable social grouping or face emotion, and verify boxes align with the person in the mirrored preview.

### How to test
- Executed:
  - `node --check src/main/resources/public/gigi-demo/script.js`
  - `.\mvnw.cmd -q "-Dtest=GigiDemoClientStaticResourceContractTest" test`

### Known issues and decisions
- The fix mirrors overlay geometry at draw time instead of mirroring the entire canvas, so future canvas labels or text would remain readable.
- The coordinate mapping still assumes the camera feed and preview use the same 4:3 aspect ratio, which matches the current requested camera constraints and preview shell.

### Next steps
1. Verify with the live camera that social grouping and face boxes align in the mirrored self-view.
2. If future cameras produce a different aspect ratio, add object-fit crop/offset handling to the overlay transform.

## Milestone 46
### Date
2026-06-13

### Goal
Rename the unified demo cockpit package to Valerian and make its user-facing branding PROMETHEUS-centered.

### What changed
- Renamed the static cockpit files:
  - `src/main/resources/public/valerian/index.html`
  - `src/main/resources/public/valerian/script.js`
- Replaced the old cockpit route with:
  - `GET /valerian`
  - `GET /valerian/`
- Removed the previous legacy cockpit aliases from the redirect controller.
- Updated user-facing cockpit copy:
  - page title: `Prometheus Demo Cockpit`
  - default subtitle: `PROMETHEUS demo console`
  - behaviour hand-sign label: `Agent Sign`
- Removed old cockpit-specific agent boosting/special labeling from the agent dropdown; agents now sort by name.
- Renamed the static cockpit contract test to `ValerianClientStaticResourceContractTest`.
- Updated realtime browser contract coverage to read the Valerian cockpit resources.
- Updated README client documentation and event-contract examples to use `/valerian`.

### How to run
1. Configure properties as in `README.md`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/valerian/`
   - or `http://localhost:8080/valerian/?agentId=<uuid>`

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,RealtimeBrowserClientContractTest" test`

### Known issues and decisions
- Historical milestone entries and existing seed-agent package names still contain earlier demo naming because they are part of the audit trail or active Java test package names.
- The old cockpit route aliases were removed rather than preserved, following the repository clean-slate rule.
- The RPS-specific client remains separate and was not renamed in this milestone.
- A browser smoke was attempted, but the in-app browser was unavailable in this session.

### Next steps
1. Run a live browser rehearsal against `http://localhost:8080/valerian/`.
2. Consider whether the RPS-specific client should also receive generic PROMETHEUS branding in a separate milestone.

## Milestone 47
### Date
2026-06-14

### Goal
Create the feature branch for scoped Valerian access-code work and move reusable agent creation out of test sources into a production agent definition catalog.

### What changed
- Created and worked on feature branch:
  - `feature/valerian-access-codes`
- Added production agent definition infrastructure:
  - `AgentDefinition`
  - `AgentCreationContext`
  - `AgentCreationResult`
  - `AgentDefinitionRegistry`
- Added production agent definitions under:
  - `src/main/java/ch/zhaw/prometheus/agentdefs/basic`
  - `src/main/java/ch/zhaw/prometheus/agentdefs/multimodal`
  - `src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare`
  - `src/main/java/ch/zhaw/prometheus/agentdefs/gigitdsr`
- Registered all current agent types with stable definition keys.
- Preserved existing agent names, descriptions, prompts, states, profiles, and startup behavior.
- Kept startup as developer-written creation code: current migrated definitions call `Agent.start(...)` inside `createInstance(...)` because their previous seed tests did so.
- Reduced the former seed classes in `src/test/java/ch/zhaw/prometheus/agents` to thin wrappers around production definitions for manual database seeding.
- Updated prompt-contract, profile-contract, fixture, and replay references to use production definitions.
- Added `seed-wrapper-start-script.json` so manual seed wrappers can be smoke-tested with a scripted gateway instead of live model calls.
- Updated README guidance so new agents are implemented under `src/main/java/ch/zhaw/prometheus/agentdefs` and registered through `AgentDefinitionRegistry`.

### How to run
1. Stay on the feature branch:
   - `git switch feature/valerian-access-codes`
2. Configure properties as in `README.md`.
3. Start app:
   - `.\mvnw.cmd spring-boot:run`
4. Seed a registered agent by running its thin wrapper test under `src/test/java/ch/zhaw/prometheus/agents`.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=ch.zhaw.prometheus.agents.SingleStateMicroCoaching,ch.zhaw.prometheus.agents.SingleStateGuessingGame,ch.zhaw.prometheus.agents.SingleStateCoCreation,ch.zhaw.prometheus.agents.FourStatesLinear,ch.zhaw.prometheus.agents.FourStatesCircular,ch.zhaw.prometheus.agents.multimodal.SingleStateMultimodalIn,ch.zhaw.prometheus.agents.multimodal.SingleStateMultimodalOut,ch.zhaw.prometheus.agents.multimodal.SingleStateMultimodalInOut,ch.zhaw.prometheus.agents.elderlycare.SingleStateTherapyAppointmentReminder,ch.zhaw.prometheus.agents.elderlycare.SingleStateGuessingGame,ch.zhaw.prometheus.agents.elderlycare.SingleStateGuessingGameUserGuess,ch.zhaw.prometheus.agents.elderlycare.SingleStateSmartGoalCoaching,ch.zhaw.prometheus.agents.gigitdsr.GuessingGameWithGestures,ch.zhaw.prometheus.agents.gigitdsr.SocialContextSensitivity,ch.zhaw.prometheus.agents.gigitdsr.RockScissorPaper" "-Dprometheus.gateway.mode=scripted" "-Dprometheus.gateway.script=classpath:scripts/seed-wrapper-start-script.json" "-DforkCount=1" "-DreuseForks=false" test`
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,PflegezentrumDemoPromptContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=SingleStateMicroCoachingReplayIntegrationTest,SingleStateGuessingGameReplayIntegrationTest,SingleStateCoCreationReplayIntegrationTest,FourStatesLinearReplayIntegrationTest,FourStatesCircularReplayIntegrationTest,GigiTdsrGuessingGameWithGesturesReplayIntegrationTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest,GigiTdsrRockScissorPaperReplayIntegrationTest,SingleStateMicroCoachingRealtimeReplayIntegrationTest,SingleStateGuessingGameRealtimeReplayIntegrationTest,SingleStateCoCreationRealtimeReplayIntegrationTest,FourStatesLinearRealtimeReplayIntegrationTest,FourStatesCircularRealtimeReplayIntegrationTest" test`

### Known issues and decisions
- This milestone intentionally adds no access-code tables, admin UI, scoped endpoints, or Valerian cockpit changes.
- Definition display metadata currently comes from creating an unsaved `Agent`; this keeps the catalog simple until the first UI/API consumer needs cheaper explicit metadata.
- Some demonstrator package names still reflected their earlier GIGI-specific namespace at the time of this milestone; later cleanup milestones may rename them.
- Existing persisted database agents are not migrated or changed; reseed through the wrappers when persisted rows should reflect current definitions.

### Next steps
1. Add access-code persistence and admin-token-protected management endpoints.
2. Add scoped Valerian demo endpoints for available agent types and access-code-bound instances.
3. Update the Valerian UI with access-code login, root management, available type selection, instance creation, and scoped known-agent selection.

## Milestone 48
### Date
2026-06-14

### Goal
Add database-backed access codes and allowed agent-type assignments so a root/admin can configure which registered agent types an access code may instantiate.

### What changed
- Added access-code persistence entities:
  - `AccessCode`
  - `AccessCodeAllowedAgentType`
  - `AccessCodeAgent`
- Added repositories for the new tables:
  - `AccessCodeRepository`
  - `AccessCodeAllowedAgentTypeRepository`
  - `AccessCodeAgentRepository`
- Added `AccessCodeAdminService` for:
  - listing registered production agent types
  - creating access codes
  - enforcing exact case-sensitive code uniqueness
  - enabling/disabling codes
  - replacing allowed agent-type assignments
  - listing agents already associated with an access code
- Added admin endpoints guarded by header `X-Prometheus-Admin-Token`:
  - `GET /admin/agent-types`
  - `POST /admin/access-codes`
  - `GET /admin/access-codes`
  - `PATCH /admin/access-codes/{id}`
  - `PUT /admin/access-codes/{id}/agent-types`
  - `GET /admin/access-codes/{id}/agents`
- Added property placeholders:
  - local/template: `prometheus.admin.token`
  - prod: `prometheus.admin.token=${PROMETHEUS_ADMIN_TOKEN:}`
- Updated README with the admin API contract.

### How to run
1. Configure `prometheus.admin.token` in `src/main/resources/application.properties`.
2. Start app:
   - `.\mvnw.cmd spring-boot:run`
3. Call admin endpoints with:
   - header `X-Prometheus-Admin-Token: <configured token>`

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=AccessCodeAdminServiceIntegrationTest,AdminAccessCodeControllerWebMvcTest" test`

### Known issues and decisions
- Access codes are exactly five ASCII letters or digits.
- Access codes are case-sensitive and are not normalized by the backend.
- The `AccessCode.code` column uses binary collation in generated MySQL DDL to preserve case-sensitive uniqueness.
- This milestone intentionally adds no Valerian UI changes, scoped demo endpoints, or runtime agent creation through access codes.
- Admin token checking is deliberately local and property-driven; Spring Security is still not introduced.

### Next steps
1. Add scoped Valerian/demo API endpoints that validate access codes and expose only allowed agent types and associated agents.
2. Add runtime instance creation from `AgentDefinitionRegistry` and persist `AccessCodeAgent` links.
3. Build the root/admin UI and access-code user flow in the Valerian cockpit.

## Milestone 49
### Date
2026-06-14

### Goal
Add user-facing scoped `/demo` endpoints for Valerian so access-code users can see allowed agent types, create access-code-bound instances, and use runtime operations only for visible agents, without changing existing global endpoints.

### What changed
- Added `ScopedDemoService` for access-code validation, allowed-type filtering, scoped agent listing, runtime agent creation, visibility checks, and scoped deletion.
- Added scoped demo endpoints:
  - `POST /demo/session`
  - `GET /demo/agent-types`
  - `GET /demo/agents`
  - `POST /demo/agents`
  - `DELETE /demo/agents/{agentId}`
  - scoped runtime proxies under `/demo/agents/{agentId}/...` for info, event history, state, states, storage, start, reset, acknowledge, behaviour generation, behaviour stream, monitor stream, and prompt.
- Added `X-Prometheus-Access-Code` as the scoped API header and also accepted `?accessCode=<code>` for browser SSE streams.
- Added production creation support through `AgentApplicationService.persistCreatedAgent(...)` so definition-created agents use the existing monitor and behaviour publication path.
- Extended `AccessCodeAgentRepository` with link lookup, visibility, and agent-reference count helpers.
- Implemented scoped delete semantics: remove the access-code link first and delete the underlying `Agent` only when no links remain.
- Added scoped demo request/view DTOs and dedicated access-denied/type-forbidden exceptions.
- Kept existing global endpoints unchanged.

### How to run
1. Stay on the feature branch:
   - `git switch feature/valerian-access-codes`
2. Configure and start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Use admin endpoints to create an enabled access code and assign agent type keys.
4. Validate the code:
   - `POST /demo/session`
   - body: `{ "accessCode": "af7u1" }`
5. Call scoped endpoints with:
   - header `X-Prometheus-Access-Code: af7u1`
   - or for SSE/browser streams: `?accessCode=af7u1`

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=ScopedDemoControllerIntegrationTest" test`
  - `.\mvnw.cmd -q "-Dtest=AgentClientCompatibilityWebMvcTest" test`

### Known issues and decisions
- No Valerian UI changes are included in this milestone.
- Access codes remain case-sensitive and unnormalized.
- Invalid or disabled access codes return `401`; disallowed agent type creation returns `403`; agents outside a valid code's scope return `404`.
- `POST /demo/session` does not create server-side session state; it validates the code and returns the current scoped agent types and agents.
- Query-parameter access codes are accepted to support `EventSource`; the UI should avoid logging or exposing stream URLs unnecessarily.

### Next steps
1. Update Valerian with access-code login, available agent type selection, scoped instance creation, and scoped known-agent selection.
2. Add root/admin UI flows for code creation, enabled state, and allowed type assignment.
3. Rehearse scoped Valerian flows with the three TDSR agent types assigned to one access code.

## Milestone 50
### Date
2026-06-14

### Goal
Make the Valerian cockpit access-code scoped so regular users can create and use only the agent instances visible through their active access code.

### What changed
- Added an access-code screen before the cockpit and store accepted codes in `sessionStorage`.
- Added `Available Agent Types` in the agent drawer and wired instance creation through `POST /demo/agents`.
- Kept `Known Agents` scoped to `GET /demo/agents` for the active access code.
- Added scoped instance deletion through `DELETE /demo/agents/{agentId}`; the UI only enables delete for agents visible in the active scoped list.
- Routed existing info, event history, state, storage, start, reset, acknowledge, behaviour generation, prompt, behaviour stream, and monitor stream calls through `/demo/agents/{agentId}/...`.
- Kept realtime session creation on `/realtime/session`; the prompt and backend complement calls remain scoped to the selected agent.
- Updated the Valerian static contract test and README client guidance.

### How to run
1. Configure an admin token and create an enabled access code with assigned agent type keys through the admin API.
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/valerian/`
4. Enter the access code, create an instance from `Available Agent Types`, then connect through `Known Agents`.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - Headless Chrome smoke against `http://127.0.0.1:18080/valerian/` with a temporary access code:
    invalid code rejected, valid code showed assigned types, create instance, instance appeared in `Known Agents`, connect succeeded, delete removed it.
  - Smoke screenshot captured at `target/milestone50-smoke/valerian-smoke.png`.

### Known issues and decisions
- The user UI has no root/admin management screen yet; access codes and allowed types are still configured through the admin API.
- Access codes remain case-sensitive and unnormalized.
- Browser SSE streams include `accessCode` as a query parameter because `EventSource` cannot set custom headers.

### Next steps
1. Add the root/admin management UI for access-code creation, enable/disable state, and allowed type assignment.
2. Rehearse a curated demo code with the desired PROMETHEUS agent types.
3. Consider replacing direct Agent ID entry with a read-only selected-instance display once all supported demos use access-code-scoped instances.

## Milestone 51
### Date
2026-06-14

### Goal
Add a small root/admin management page so access codes, enabled state, allowed agent types, and scoped instances can be managed without touching the database manually.

### What changed
- Added the Prometheus Admin Cockpit static client:
  - `src/main/resources/public/valerian-admin/index.html`
  - `src/main/resources/public/valerian-admin/script.js`
- Added route:
  - `GET /valerian-admin`
  - `GET /valerian-admin/`
- The admin page supports:
  - entering an admin token and storing it in `sessionStorage`
  - creating manually typed access codes
  - generating five-character client-side codes that exclude ambiguous characters
  - enabling/disabling access codes
  - assigning registered agent types with checkboxes
  - inspecting agents linked to the selected access code
- The page calls the existing Milestone 48 admin API; no backend API changes were needed beyond the static route.
- Added static contract coverage for the admin client and redirect coverage for `/valerian-admin`.
- Updated README with the new admin cockpit URL and capabilities.

### How to run
1. Configure `prometheus.admin.token` in `src/main/resources/application.properties`.
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open:
   - `http://localhost:8080/valerian-admin/`
4. Enter the admin token, create or generate a code, assign agent types, and use that code in:
   - `http://localhost:8080/valerian/`

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianAdminClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,AdminAccessCodeControllerWebMvcTest" test`
  - Headless Chrome smoke against `http://127.0.0.1:18081/valerian-admin/` and `http://127.0.0.1:18081/valerian/`:
    entered admin token, generated and created an access code, assigned three agent types, and verified Valerian login saw those three types.
  - Smoke screenshots captured at:
    - `target/milestone51-smoke/valerian-admin-smoke.png`
    - `target/milestone51-smoke/valerian-types-smoke.png`

### Known issues and decisions
- Admin authentication remains the configured token header flow from Milestone 48; Spring Security is still not introduced.
- The admin token is kept in `sessionStorage`, not persisted across browser sessions.
- The admin UI sanitizes legacy demo naming from display labels while still sending the registered backend type keys to the API.

### Next steps
1. Add optional filtering/search once the agent type catalog grows.
2. Consider showing per-code usage or last-created timestamps if repeated demos require audit visibility.
3. Rehearse a curated root workflow for the three desired PROMETHEUS demo agent types.

## Milestone 52
### Date
2026-06-14

### Goal
Prove the access-code-scoped Valerian feature works end to end without disrupting existing global PROMETHEUS application workflows.

### What changed
- No production code changes.
- Rehearsed the complete admin and user flow on the feature branch with the real Spring Boot app, local MySQL persistence, and scripted gateway responses.
- Verified the three former `gigitdsr` definitions can be assigned to an access code, instantiated through Valerian, and connected through the scoped UI.
- Verified access-code isolation with a second code assigned to the same agent types but showing no instances created under the first code.
- Verified a disabled access code is rejected by the Valerian access screen.
- Verified existing global `/agent`, `/{agentId}/info`, `/realtime`, `/monitor`, and `/rps` routes still respond successfully for a created agent before cleanup.

### How to run
1. Stay on the feature branch:
   - `git switch feature/valerian-access-codes`
2. Configure an admin token:
   - `prometheus.admin.token=<token>`
3. Start the app:
   - `.\mvnw.cmd spring-boot:run`
4. Open:
   - `http://localhost:8080/valerian-admin/`
   - `http://localhost:8080/valerian/`

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=AdminAccessCodeControllerWebMvcTest,ScopedDemoControllerIntegrationTest,AccessCodeAdminServiceIntegrationTest,AgentClientCompatibilityWebMvcTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,RpsClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,PflegezentrumDemoPromptContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrGuessingGameWithGesturesReplayIntegrationTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest,GigiTdsrRockScissorPaperReplayIntegrationTest,SingleStateMicroCoachingReplayIntegrationTest,SingleStateGuessingGameReplayIntegrationTest,SingleStateCoCreationReplayIntegrationTest,FourStatesLinearReplayIntegrationTest,FourStatesCircularReplayIntegrationTest" test`
- Headless Chrome smoke against `http://127.0.0.1:18082/` with admin token `m52-root`:
  - invalid Valerian code rejected
  - admin created codes `98FH5` and `AQFSQ`
  - admin assigned the three former `gigitdsr` definitions to both codes
  - user logged into Valerian with `98FH5`
  - user created and connected all three assigned instances
  - user logged into Valerian with `AQFSQ` and saw the same types but zero known agents
  - admin disabled `AQFSQ`; Valerian rejected that disabled code
  - global compatibility checks returned `200` for `/agent`, `/{agentId}/info`, `/realtime?agentId=...`, `/monitor?agentId=...`, and `/rps?agentId=...`
  - created smoke agents were deleted through the scoped API after compatibility checks
- Smoke artifacts:
  - `target/milestone52-smoke/smoke-summary.json`
  - `target/milestone52-smoke/admin-assigned.png`
  - `target/milestone52-smoke/valerian-created-and-connected.png`
  - `target/milestone52-smoke/valerian-code-isolation.png`
  - `target/milestone52-smoke/valerian-disabled-code.png`

### Known issues and decisions
- The smoke used installed headless Chrome over CDP because the in-app browser backend was not available in this session.
- Access codes created by the smoke remain in the local database because there is no access-code delete API; the created agent instances were cleaned up.
- Access codes remain case-sensitive and unnormalized.
- Browser SSE streams still use the `accessCode` query parameter due to `EventSource` header limitations.

### Next steps
1. Review and merge `feature/valerian-access-codes`, or run a full suite before merge if broader confidence is needed.
2. Consider adding admin access-code deletion or archival if repeated smoke rehearsals should not leave local database codes behind.
3. Continue hardening with UI search/filtering and operational audit fields once the catalog grows.

## Milestone 53
### Date
2026-06-14

### Goal
Rebuild Realtime speech-to-speech so PROMETHEUS remains authoritative for state, prompt refresh, user-turn acknowledgement, and behaviour persistence while browser clients handle only WebRTC media.

### What changed
- Replaced the speech-to-speech browser client-secret flow with agent-bound raw-SDP call creation:
  - `POST /{agentID}/realtime/call`
  - `POST /demo/agents/{agentId}/realtime/call`
  - `DELETE /realtime/calls/{callId}`
- Added unified OpenAI Realtime call creation through `/v1/realtime/calls` with the current `REALTIME_SPEECH` prompt installed as session `instructions`, default model `gpt-realtime-2`, and turn detection configured with `create_response=false`.
- Added backend Realtime sideband orchestration for WebRTC calls. The sideband monitors Realtime transcript events, acknowledges user speech through `AgentApplicationService`, refreshes PROMETHEUS instructions after state transitions, waits for `session.updated`, then sends `response.create`.
- Added backend persistence for Realtime-generated assistant speech as `resp.behaviour_plan` via `recordRealtimeAssistantSpeech(...)`, so normal behaviour streams remain the canonical assistant-output feed.
- When PROMETHEUS acknowledgement already returns exact speech, the sideband asks Realtime to say that text and avoids persisting a duplicate assistant event.
- Updated the standalone `/realtime` client and the Valerian cockpit Realtime tab to post SDP to PROMETHEUS, track returned call IDs, close calls through the backend, and stop sending browser-side `session.update`, `response.create`, or acknowledgement requests.
- Kept the multilateral listening path on `POST /realtime/transcription/session` because it is transcription-only and still uses OpenAI client secrets.
- Updated OpenAI Realtime configuration defaults in the template and prod properties to `gpt-realtime-2`.
- Updated README Realtime notes and the affected backend/frontend contract tests.

### How to run
1. Configure OpenAI Realtime properties in `src/main/resources/openai.properties`:
   - `openai.openaivsazureopenai=openai`
   - `openai.key=<standard OpenAI API key>`
   - optional `openai.realtimeModel=gpt-realtime-2`
   - optional `openai.realtimeSafetyIdentifier=<stable privacy-preserving user id>`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open one of:
   - standalone speech client: `http://localhost:8080/realtime/?agentId=<uuid>`
   - Valerian cockpit: `http://localhost:8080/valerian/`
4. Connect/start an agent, start Realtime speech, and verify that spoken user turns advance PROMETHEUS state and that assistant speech appears on the normal behaviour stream.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSessionClientTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest,AgentApplicationServiceGenerateOptionsUnitTest" test`

### Known issues and decisions
- This milestone follows the current OpenAI Realtime WebRTC guidance: browser media connects through unified `/v1/realtime/calls`, and PROMETHEUS keeps business logic on a backend sideband connection.
- `instructions` remains the relevant Realtime session field for PROMETHEUS prompts.
- The sideband intentionally waits for `session.updated` before creating a response after prompt refresh; if the update fails, it does not generate from stale PROMETHEUS context.
- Exact backend acknowledgement speech is realized through Realtime audio, so wording is constrained by instructions rather than by a separate deterministic TTS engine.
- Browser voice and turn-detection changes apply on the next Realtime restart.
- The speech-to-speech call path is OpenAI-only for now. Azure Realtime speech remains unsupported by this code path.
- A live browser/WebRTC smoke with real Realtime credentials was not run in this milestone.

### Next steps
1. Run a live Realtime speech smoke with a real OpenAI key and one scoped Valerian agent.
2. Add operational diagnostics for sideband connection state, prompt-update acknowledgements, and Realtime errors.
3. Consider a strict STT -> PROMETHEUS -> TTS mode if exact backend-authored speech becomes more important than low-latency speech-to-speech.

## Milestone 54
### Date
2026-06-14

### Goal
Fix duplicate assistant speech in push-to-talk Realtime sessions while keeping PROMETHEUS authoritative for response generation and state transitions.

### What changed
- Diagnosed the duplicate-response path from a live Valerian test with `gigitdsr.guessing_game_with_gestures`: one user transcript produced one PROMETHEUS user event, but two assistant speech events because the sideband used `BACKEND_COMPLEMENT` for user acknowledgement and then asked Realtime for free speech.
- Changed the sideband user-turn flow:
  - acknowledge user speech with `OutputProfile.REALTIME_SPEECH`
  - if acknowledgement returns no speech, call backend `generate(..., REALTIME_SPEECH)` to mirror the text-client acknowledge-then-generate workflow
  - persist that canonical PROMETHEUS speech before Realtime speaks
  - optionally generate `BACKEND_COMPLEMENT` non-speech behaviour after canonical speech exists
  - send Realtime `response.create` only with exact PROMETHEUS speech
- Removed sideband persistence of assistant transcripts emitted by Realtime; those transcripts are audio-display telemetry, not authoritative PROMETHEUS behaviour.
- Removed sideband free-form response instructions and unused initial response-instruction plumbing.
- Updated standalone Realtime and Valerian push-to-talk clients to clear the Realtime input buffer on press, optionally cancel/clear active output, and commit captured audio on release.
- Audited all browser clients that touch Realtime: `/realtime/` and `/valerian/` are speech-to-speech clients and now share the same backend-owned response contract; `multilateral/listen` remains transcription-only and is covered by a contract assertion that it does not create or cancel assistant responses.
- Updated README Realtime notes and added regression coverage for the sideband profile contract, speech-plus-complement generation, and push-to-talk buffer clearing.

### How to run
1. Start the app with OpenAI Realtime configured:
   - `.\mvnw.cmd spring-boot:run`
2. Open:
   - `http://localhost:8080/valerian/`
3. Use an access code with `gigitdsr.guessing_game_with_gestures`, create/connect an instance, start Realtime, select push-to-talk mode, and answer a few game questions.
4. Expected behaviour: each user answer results in at most one assistant speech turn in the text transcript and behaviour stream.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest,RealtimeSessionClientTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest,AgentApplicationServiceGenerateOptionsUnitTest" test`

### Known issues and decisions
- The sideband now treats Realtime as speech realization for PROMETHEUS-authored text, not as the source of normal assistant content.
- If PROMETHEUS produces no speech for a user turn, the sideband updates Realtime instructions but does not ask Realtime to invent a response.
- Browser push-to-talk still leaves `response.create` to the backend sideband; this intentionally differs from the generic OpenAI client-only push-to-talk recipe.
- A repeat live browser/WebRTC smoke with real credentials should be run after review.

### Next steps
1. Re-test the GIGI TDSR guessing-game push-to-talk flow with the same access-code setup.
2. Add sideband runtime diagnostics for transcript handling, backend speech generation, and exact-speech response creation.
3. Consider adding a lightweight endpoint or monitor event for sideband turn lifecycle debugging.

## Milestone 55
### Date
2026-06-14

### Goal
Add an optional agent-level language code and use it wherever an agent-bound Realtime pipeline is created, so German agents can pass a transcription language hint without relying only on global OpenAI configuration.

### What changed
- Added persisted `Agent.languageCode` metadata with normalization to lower-case language codes.
- Extended `AgentDefinition` with optional `languageCode()` metadata, shared `en`/`de` constants, and a shared metadata application helper.
- Marked all currently registered built-in agent definitions as German with `de` after checking their prompt language; newly created definition-backed instances now inherit that metadata.
- Defaulted custom `/agent/singlestate` creation to `en` when no explicit `languageCode` is supplied, while allowing callers to override it.
- Exposed `languageCode` through `AgentInfoView`, global agent listings, scoped Valerian agent/session responses, and admin assignment listings.
- Wired agent language into Realtime speech-to-speech calls as `audio.input.transcription.language`.
- Added optional `agentId` support to `POST /realtime/transcription/session`; the multilateral listen client now passes its selected agent so transcription-only Realtime sessions can also use the agent language hint.
- Kept `openai.realtimeTranscriptionLanguage` as the fallback for transcription-only sessions without an agent language.
- Moved the Valerian admin "Save Assignments" action above the agent choices and made it full width.
- Updated README Realtime notes and added regression coverage for metadata propagation, per-definition language expectations, custom single-state language defaults, Realtime payloads, controller routing, browser client behaviour, scoped demo views, and the admin layout.

### How to run
1. Start the app with OpenAI Realtime configured:
   - `.\mvnw.cmd spring-boot:run`
2. Open:
   - Valerian cockpit: `http://localhost:8080/valerian/`
   - standalone speech client: `http://localhost:8080/realtime/?agentId=<uuid>`
   - multilateral listen client: `http://localhost:8080/multilateral/listen/?agentId=<uuid>`
3. Use a German built-in agent such as `gigitdsr.guessing_game_with_gestures`, start a Realtime session, and confirm the session payload includes `audio.input.transcription.language=de`.
4. For the cosmetic change, open `http://localhost:8080/valerian-admin/` and verify that "Save Assignments" appears above the agent selection list and spans the form width.

### How to test
- Executed during the implementation:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentApplicationServicePromptUnitTest,RealtimeSessionClientTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ScopedDemoControllerIntegrationTest" test`
- Final verification executed:
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentApplicationServicePromptUnitTest,RealtimeSessionClientTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest" test`
  - `git diff --check`

### Known issues and decisions
- OpenAI's Realtime transcription `language` value is a hint, not a hard guarantee; it should reduce German-to-English drift such as `nein` becoming `nine`, but live audio still needs validation.
- Agent-level language wins over the global `openai.realtimeTranscriptionLanguage` for agent-bound sessions; the global property remains useful for unbound transcription sessions.
- This milestone does not add per-user or per-session language switching. Language remains agent metadata.
- A live WebRTC smoke with real credentials was not run yet after this language-code change.

### Next steps
1. Re-test the GIGI TDSR guessing-game push-to-talk flow and verify German yes/no turns in the diagnostics drawer and text transcript.
2. If `nein` still drifts under noisy conditions, consider a PROMETHEUS-side yes/no normalization guard for German guessing-game agents.
3. Consider adding admin/editor affordances for custom agent language codes if non-built-in agents need runtime configuration.

## Milestone 56
### Date
2026-06-14

### Goal
Stop duplicate and phantom Realtime speech-to-speech transcript completions from entering PROMETHEUS as multiple user turns.

### What changed
- Hardened `RealtimeSidebandService` transcript ingress:
  - tracks `input_audio_buffer.committed` item IDs
  - batches `conversation.item.input_audio_transcription.completed` events briefly before acknowledgement
  - processes at most one accepted transcript per batch
  - ignores duplicate item IDs
  - suppresses the observed caption-style ASR hallucination `Untertitel der Amara.org-Community`
- Kept PROMETHEUS authoritative: only the accepted transcript is acknowledged as `obs.user_utterance`; Realtime still only realizes backend-authored speech.
- Mirrored the same committed-item, batch, duplicate, and hallucination filtering in:
  - standalone `/realtime/`
  - Valerian `/valerian/`
- Added the same exact caption-hallucination suppression to transcription-only `/multilateral/listen` before it displays or acknowledges user transcripts.
- Added `openai.realtimeInputTranscriptionModel` for speech-to-speech input transcription and defaulted it to `gpt-4o-transcribe`.
- Left transcription-only sessions on `openai.realtimeTranscriptionModel=gpt-realtime-whisper`.
- Updated README Realtime notes and configuration documentation.

### How to run
1. Configure OpenAI Realtime properties:
   - `openai.realtimeModel=gpt-realtime-2`
   - `openai.realtimeInputTranscriptionModel=gpt-4o-transcribe`
   - `openai.realtimeTranscriptionModel=gpt-realtime-whisper`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open Valerian:
   - `http://localhost:8080/valerian/`
4. Use a German speech-enabled agent such as `gigitdsr.guessing_game_with_gestures`, start Realtime push-to-talk, and answer several turns.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest,RealtimeSessionClientTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- OpenAI documents Realtime input transcription as asynchronous ASR that can diverge from model interpretation, so PROMETHEUS treats completed transcript events as candidates rather than authoritative turns.
- The batch window adds a short delay before backend acknowledgement so duplicate completions from the same push-to-talk action can be reconciled.
- The caption-hallucination filter is intentionally exact after normalization to avoid suppressing legitimate longer utterances.
- A live browser/WebRTC smoke with real credentials is still needed to confirm the duplicate-answer symptom is gone in Valerian.

### Next steps
1. Re-test the GIGI TDSR guessing-game push-to-talk flow with the same access-code setup that produced duplicate answers.
2. If ASR still produces non-caption hallucinations, enable/use transcription confidence data or add a stricter PROMETHEUS-side acceptance policy for speech input.
3. Consider exposing speech-call transcription model selection in admin/config UI if demos need to trade quality against cost.

## Milestone 57
### Date
2026-06-14

### Goal
Harden the OpenAI Realtime push-to-talk turn boundary so manual sessions behave like the documented WebRTC push-to-talk flow while PROMETHEUS remains authoritative for responses.

### What changed
- Compared the live symptom against the OpenAI Realtime WebRTC push-to-talk guidance:
  - `turn_detection` must be null for manual sessions
  - button press clears the input audio buffer and active output when needed
  - button release commits the buffered audio
  - response creation remains backend-side in PROMETHEUS
- Locked Realtime voice, turn-detection mode, and backend-complement controls while a WebRTC call is live in both speech clients, so the UI cannot enter push-to-talk behavior for a call that was created with server VAD.
- Captured the active turn-detection mode at call creation and used that active mode for push-to-talk UI/keyboard guards instead of reading mutable dropdown state.
- Disabled the local mic track immediately after `getUserMedia` when the active call is push-to-talk, preventing an initial open-mic window during WebRTC setup.
- Changed push-to-talk release ordering in `/realtime/` and `/valerian/` so the client sends `input_audio_buffer.commit` before muting the mic track.
- Changed push-to-talk press ordering so clients send `input_audio_buffer.clear` first, then cancel/clear active output when needed.
- Changed sideband and browser transcript gates so `input_audio_buffer.cleared` clears pending input item tracking but does not discard already completed transcript candidates waiting in the batch window.

### How to run
1. Configure OpenAI Realtime properties:
   - `openai.realtimeModel=gpt-realtime-2`
   - `openai.realtimeInputTranscriptionModel=gpt-4o-transcribe`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open Valerian:
   - `http://localhost:8080/valerian/`
4. Select Push to Talk before starting Realtime, start the call, and test `gigitdsr.guessing_game_with_gestures`.
5. To change between Continuous and Push to Talk, stop Realtime, change the mode, then start it again.

### How to test
- Executed:
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest,RealtimeBrowserClientContractTest" test`

### Known issues and decisions
- PROMETHEUS still intentionally differs from the generic client-only OpenAI recipe at the final step: the browser does not send `response.create`; the sideband sends it only after backend acknowledgement/generation has produced canonical speech.
- The fix is based on static contract coverage and targeted unit tests. A live WebRTC smoke with real credentials is still needed to confirm that push-to-talk speech and text-client history now stay aligned.
- If live PTT still produces unexplained spoken output absent from the behaviour stream, the next diagnostic should add response/item IDs and exact sideband lifecycle events to the Valerian diagnostics drawer.

### Next steps
1. Re-test the GIGI TDSR guessing-game push-to-talk flow in Valerian with diagnostics open.
2. If mismatch remains, add explicit sideband turn-lifecycle diagnostics for accepted transcript item ID, generated backend speech, session update acknowledgement, and exact-speech `response.create`.

## Milestone 58
### Date
2026-06-14

### Goal
Extend the Valerian PROMETHEUS demo cockpit sensing area so speech input is visible next to visual sensing, and make the sensing/output panel icons more semantically clear.

### What changed
- Added a `Speech Sensing` surface to the Valerian sensing panel.
- The speech sensing readout displays the latest accepted Realtime ASR user transcript from the same gated candidate path that updates the text transcript.
- The speech sensing readout is reset when connecting to another agent, disconnecting, or resetting the current agent.
- Marked the speech sensing surface with `data-profile-observations="obs.user_utterance"` so it participates in the existing interaction-profile visibility rules.
- Changed the main sensing icon from a camera-specific icon to a radar-style sensing icon.
- Changed the behaviour output panel icon from eyeglasses to a send/output icon.
- Updated the Valerian static resource contract to pin the panel, icons, profile marker, and ASR binding.
- Updated README cockpit notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect a speech-capable agent, start Realtime, and speak. The left sensing panel should show the accepted user speech in `Speech Sensing`.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- The readout shows accepted completed ASR candidates, not raw partial deltas. This keeps the sensing display aligned with the transcript gate that suppresses duplicates and known hallucinations.
- Historical text utterances do not populate the speech sensing readout on refresh. The panel is for live speech sensing, not general conversation history.
- A live browser smoke with microphone input was not run in this milestone.

### Next steps
1. Re-test Valerian Realtime speech and confirm the speech sensing card updates together with the text transcript.
2. If operators need deeper debugging, add transcript item IDs and ASR event IDs to the diagnostics drawer rather than the main cockpit panel.

## Milestone 59
### Date
2026-06-14

### Goal
Expose agent language metadata in the Valerian agent drawer and make the Agent tab the first/default drawer view.

### What changed
- Added a `Language` field beneath agent name and description in the Agent tab.
- The language field is always rendered and shows `-` when no `languageCode` is present.
- Wired the field from the existing agent info response `languageCode`.
- Reordered the drawer tabs so `Agent` is on the left and `Diagnostics` is on the right.
- Added a drawer-open hook that activates the Agent tab whenever the drawer opens, even if the previous drawer view was Diagnostics.
- Updated the Valerian static resource contract and README.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open `Agent & Diagnostics`. The drawer should open on `Agent`, with `Diagnostics` second, and the Agent panel should show `Language`.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- This milestone only displays the existing language metadata; it does not add editing for language codes.
- No live browser smoke was run.

### Next steps
1. Reopen the drawer after switching to Diagnostics and confirm it returns to the Agent tab.
2. Consider adding a future admin affordance for editing custom-agent language codes if runtime configuration becomes necessary.

## Milestone 60
### Date
2026-06-14

### Goal
Move the Valerian speech sensing readout out of the visual sensing column and into the Realtime Speech interaction tab.

### What changed
- Removed the `Speech Sensing` surface from the left Sensing card so that card is again visual-sensing-only.
- Added the same `Speech Sensing` surface at the bottom of the Realtime Speech tab, below the assistant audio element.
- Kept the existing accepted-ASR transcript wiring and reset behaviour unchanged.
- Updated the Valerian static resource contract to enforce the new placement.
- Updated README cockpit notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open the Realtime Speech tab. The `Speech Sensing` readout should be at the bottom of that tab, not in the left Sensing card.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- The readout still shows accepted completed ASR candidates, not raw partial deltas.
- No live browser smoke was run.

### Next steps
1. Re-test Valerian Realtime speech and confirm the readout updates inside the Realtime Speech tab.

## Milestone 61
### Date
2026-06-14

### Goal
Make Valerian's behaviour output display explicit when a backend behaviour event does not emit a gesture.

### What changed
- Updated the Valerian behaviour renderer so missing `nonVerbal` output sets the gesture row to `NONE`.
- Also defaults a present `nonVerbal` object without `gesture` to `NONE`.
- Added Valerian static resource contract assertions for both display safeguards.
- Updated README behaviour notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Trigger a speech-only behaviour event. The Behaviour Output gesture row should show `NONE` rather than retaining the previous gesture.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- This is a cockpit display fix only. It does not add a final-state goodbye gesture to any agent definition.
- Face, gaze, and motion rows are still only updated when their corresponding values are present in the latest plan.

### Next steps
1. Decide whether GIGI final states should emit an explicit nonverbal goodbye plan, and whether that should reuse `ACKNOWLEDGE` or introduce a new canonical goodbye gesture.

## Milestone 62
### Date
2026-06-14

### Goal
Improve Realtime push-to-talk transcription reliability by avoiding a WebRTC commit race at key-up.

### What changed
- Compared the failing push-to-talk path against current OpenAI Realtime push-to-talk guidance:
  - manual sessions use `audio.input.turn_detection=null`
  - button press clears the input buffer
  - button release commits the input buffer before response creation
- Kept PROMETHEUS authoritative for responses: browser clients still do not send `response.create`.
- Added a short 250 ms audio-drain window after push-to-talk release in both `/valerian/` and `/realtime/`.
- During that drain window, the mic track remains enabled so late WebRTC audio frames can reach the Realtime input buffer before `input_audio_buffer.commit`.
- After the delayed commit is sent, the clients mute the local mic track.
- Added lifecycle handling to flush a pending delayed commit before a new push-to-talk turn and cancel pending delayed commits when a Realtime session stops.
- Clarified `openai.properties.template`:
  - `openai.realtimeInputTranscriptionModel` configures speech-to-speech call input transcription.
  - `openai.realtimeTranscriptionModel` configures transcription-only sessions such as `/multilateral/listen`.
- Updated README Realtime notes and browser contract coverage.

### How to run
1. Configure OpenAI Realtime properties:
   - `openai.realtimeModel=gpt-realtime-2`
   - `openai.realtimeInputTranscriptionModel=gpt-4o-transcribe`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open Valerian:
   - `http://localhost:8080/valerian/`
4. Select Push to Talk before starting Realtime, start the call, hold Space while speaking, release, and confirm the Speech Sensing readout matches the utterance.

### How to test
- Executed:
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,RealtimeSessionClientTest" test`

### Known issues and decisions
- The transcription model defaults were not changed. Official OpenAI docs still distinguish speech-to-speech built-in input transcription from `gpt-realtime-whisper` transcription-only sessions.
- The 250 ms drain window is a pragmatic browser/WebRTC timing fix. A live microphone smoke is still needed to tune it if a specific environment needs a longer delay.
- If push-to-talk still produces correct spoken responses but wrong displayed transcripts, the next deeper fix should evaluate out-of-band transcription or using the Realtime model itself for authoritative transcript generation, but that would be a larger PROMETHEUS workflow decision.

### Next steps
1. Re-test GIGI push-to-talk with short German utterances such as `Bereit`, `Ja`, and `Nein`.
2. If transcripts are still clipped, increase the drain delay in small increments and add diagnostics for commit timestamp, transcript item ID, and completed transcript text.

## Milestone 63
### Date
2026-06-14

### Goal
Redesign browser Realtime speech mode handling so continuous VAD and push-to-talk are separate client paths instead of one shared mode-switching client.

### What changed
- Split Valerian's interaction area into three tabs:
  - `Text`
  - `Continuous`
  - `Push to Talk`
- Removed `Push to Talk` from the Valerian VAD dropdown. The continuous tab now only exposes VAD modes (`server_vad`, `semantic_vad`).
- Added a dedicated Valerian push-to-talk start/stop control, voice selector, backend-complement checkbox, assistant audio element, and speech-sensing readout.
- Split the standalone `/realtime/` client into separate continuous and push-to-talk controls as well.
- Changed browser push-to-talk behavior:
  - a PTT session is created with `turnDetection=none`
  - the WebRTC mic track remains enabled while the manual session is active
  - pressing the button clears the Realtime input buffer and cancels/clears active output if needed
  - releasing the button schedules a delayed `input_audio_buffer.commit`
  - the browser still does not send `response.create`; PROMETHEUS sideband remains authoritative
- Removed the old per-turn mic-track enable/disable mechanism from PTT. This avoids using WebRTC track muting as a turn-boundary mechanism.
- Updated browser/static contract tests and README.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect a speech-capable agent.
4. Use `Continuous` for the known-working VAD path.
5. Use `Push to Talk` for manual turns. Start the PTT session, hold the button or Space while speaking, then release.

### How to test
- Executed:
  - `node --check src/main/resources/public/realtime/script.js`
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- Push-to-talk now controls Realtime input-buffer turn boundaries, not microphone transport privacy. The browser keeps the WebRTC mic track live during an active PTT session so OpenAI receives coherent audio frames; the next press clears any uncommitted background buffer.
- This keeps PROMETHEUS authoritative: browser clients still never create assistant responses.
- A live microphone smoke is still required because static tests cannot verify ASR quality.
- If PTT still shows a mismatch between what Realtime appears to understand and what built-in ASR transcribes, the next design step should be a backend-owned recorded-turn pipeline or out-of-band transcription rather than further WebRTC track-timing tweaks.

### Next steps
1. Live-test GIGI in Valerian `Continuous` and `Push to Talk` tabs back to back with `Bereit`, `Ja`, and `Nein`.
2. If PTT transcript quality remains unacceptable, prototype backend-owned recorded-turn PTT where the browser uploads one utterance and PROMETHEUS owns transcription before response generation.

## Milestone 64
### Date
2026-06-14

### Goal
Restore Valerian speech sensing in both speech tabs and prevent Realtime from answering push-to-talk raw audio outside the PROMETHEUS behaviour path.

### What changed
- Added the live `Speech Sensing` readout to the Valerian `Continuous` tab while keeping the existing readout in `Push to Talk`.
- Updated the shared speech-sensing renderer so the latest accepted Realtime ASR transcript is shown in both speech tabs.
- Compared the sideband response flow with current OpenAI Realtime guidance:
  - WebRTC push-to-talk uses explicit `input_audio_buffer.clear`, `input_audio_buffer.commit`, then `response.create`.
  - `response.create` can be sent out-of-band with `conversation: "none"`.
  - Providing `input: []` gives the response an empty context instead of the default conversation.
- Changed `RealtimeSidebandService` exact-speech responses to send `conversation=none` and empty `input`, so Realtime speech output is only a rendering of PROMETHEUS backend speech rather than a fresh answer to the live audio item.
- Added sideband contract coverage for the exact `response.create` event shape.
- Updated Valerian static resource coverage and README notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect a speech-capable agent.
4. Use `Continuous` or `Push to Talk`; both tabs should show the latest accepted ASR transcript in their `Speech Sensing` card.
5. In Push to Talk, spoken assistant output should match the backend/text-client behaviour event rather than an additional Realtime-generated answer to raw audio.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,RealtimeSessionClientTest" test`

### Known issues and decisions
- This preserves the PROMETHEUS authority boundary: the browser still does not create assistant responses and Realtime only speaks backend-authored text.
- This should address the symptom where the first spoken answer reacts to the actual push-to-talk audio while the text client later shows a different backend response to a bad ASR transcript.
- It does not solve poor push-to-talk ASR transcripts such as `Ja` becoming `Heureusement`, `Hello`, or punctuation. If that persists, the next design step should be a backend-owned recorded-turn PTT path or an out-of-band Realtime-model transcription pass before acknowledging the user event.
- A live microphone smoke was not run in this milestone.

### Next steps
1. Re-test GIGI push-to-talk and verify that spoken output now exactly matches the text-client/backend behaviour.
2. If ASR quality remains unacceptable, prototype backend-owned recorded-turn PTT where PROMETHEUS receives one utterance audio blob, transcribes it, and only then acknowledges `obs.user_utterance`.

## Milestone 65
### Date
2026-06-14

### Goal
Replace WebRTC manual-buffer push-to-talk with a PROMETHEUS-owned recorded speech turn pipeline.

### What changed
- Added request-based OpenAI audio support:
  - `OpenAIAudioClient.transcribe(...)` posts uploaded audio to `/v1/audio/transcriptions`.
  - `OpenAIAudioClient.createSpeech(...)` renders backend speech through `/v1/audio/speech`.
- Added configuration keys:
  - `openai.audioTranscriptionsUrl`
  - `openai.audioSpeechUrl`
  - `openai.recordedSpeechTranscriptionModel`
  - `openai.speechModel`
- Added `RecordedSpeechTurnService`:
  - accepts one uploaded browser-recorded audio turn
  - transcribes it with the agent `languageCode` as language hint when present
  - acknowledges the transcript as `obs.user_utterance` with `OutputProfile.REALTIME_SPEECH`
  - generates canonical backend speech when acknowledgement does not directly produce speech and the agent remains active
  - optionally generates backend complement behaviour with speech omitted
  - returns the transcript, canonical `ResponseView`, and backend TTS audio
- Added endpoints:
  - `POST /{agentID}/speech-turn`
  - `POST /demo/agents/{agentId}/speech-turn`
- Changed Valerian Push to Talk:
  - no Realtime WebRTC call is created
  - no OpenAI data-channel buffer commands are sent
  - the browser records local audio only while the PTT button or Space key is held
  - release uploads one recorded audio blob to PROMETHEUS
  - the returned transcript updates the Speech Sensing card and text conversation
  - returned backend TTS audio is played in the Push to Talk audio element
- Changed the standalone `/realtime/` Push to Talk path to use the same recorded-turn backend endpoint.
- Kept Continuous speech on the existing Realtime WebRTC/VAD path.
- Updated static browser contracts, backend unit/WebMvc coverage, README, and the OpenAI properties template.

### How to run
1. Configure OpenAI properties:
   - `openai.recordedSpeechTranscriptionModel=gpt-4o-transcribe`
   - `openai.speechModel=gpt-4o-mini-tts`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open Valerian:
   - `http://localhost:8080/valerian/`
4. Connect a speech-capable agent.
5. Use `Continuous` for live Realtime VAD.
6. Use `Push to Talk` for backend-owned recorded turns. Start PTT, hold the button or Space while speaking, then release.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q "-Dtest=OpenAIAudioClientTest,RecordedSpeechTurnServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeSidebandServiceContractTest,RealtimeSessionClientTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- This intentionally abandons Realtime manual-buffer PTT for PROMETHEUS clients because the browser no longer leaves a live microphone stream attached to OpenAI while idle.
- Push to Talk is no longer low-latency speech-to-speech. It is a recorded request turn followed by backend transcription, backend behaviour generation, and backend TTS. This is slower but preserves the PROMETHEUS authority boundary.
- Continuous VAD still uses Realtime sideband orchestration and remains the low-latency mode.
- A live microphone smoke with real credentials is still needed to confirm German short-turn ASR quality in the new recorded path.

### Next steps
1. Live-test Valerian Push to Talk with `Bereit`, `Ja`, and `Nein`.
2. If recorded-turn ASR still mishears short German utterances, add a narrow transcript post-processor for agent-declared yes/no interaction states rather than returning to live WebRTC PTT.

## Milestone 66
### Date
2026-06-14

### Goal
Make backend-owned recorded Push to Talk read the latest stored assistant utterance when the PTT session starts.

### What changed
- Added `SpeechAudioView` for backend-rendered speech audio without a new user turn.
- Added recorded-speech service methods that scan agent event history for the latest assistant `BehaviourPlan.speech` and synthesize it with backend TTS.
- Added endpoints:
  - `POST /{agentID}/speech/latest`
  - `POST /demo/agents/{agentId}/speech/latest`
- Updated the Valerian Push to Talk tab so `Start Push to Talk` requests and plays the latest stored assistant speech before the user records a turn.
- Updated the standalone `/realtime/` Push to Talk client with the same start-time playback behavior.
- Updated static browser contracts, service/MVC coverage, scoped integration coverage, and README notes.

### How to run
1. Configure OpenAI request-based audio properties:
   - `openai.recordedSpeechTranscriptionModel=gpt-4o-transcribe`
   - `openai.speechModel=gpt-4o-mini-tts`
2. Start the app:
   - `.\mvnw.cmd spring-boot:run`
3. Open Valerian:
   - `http://localhost:8080/valerian/`
4. Connect an agent that already has a starter assistant utterance in chat, open `Push to Talk`, and press `Start Push to Talk`. The stored assistant utterance should be read aloud before recording any user audio.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=RecordedSpeechTurnServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest" test`

### Known issues and decisions
- The endpoint only renders existing backend-authored assistant speech; it does not publish a new behaviour event or mutate agent state.
- If no assistant speech exists in history, the browser logs that no stored speech is available and leaves Push to Talk ready.
- A live browser smoke with real OpenAI TTS credentials was not run in this milestone.

### Next steps
1. Re-test Valerian Push to Talk after `Start Agent` and confirm the starter utterance is read on `Start Push to Talk`.
2. If browser autoplay blocks the audio after a slow TTS response, add an explicit replay button next to the Push to Talk audio element.

## Milestone 67
### Date
2026-06-14

### Goal
Align continuous and recorded Push to Talk restart playback around the latest utterance in the current state history.

### What changed
- Added a current-state event-history accessor on `Agent`, `AgentApplicationService`, and `ScopedDemoService`.
- Added a shared `SpeechTurnSelector` that walks current-state history backward and returns assistant speech only when the latest utterance is assistant-authored.
- Updated continuous Realtime call orchestration to seed the sideband with this selected speech instead of only checking the final raw event.
- Updated recorded Push to Talk `/speech/latest` to use the same selector, so it no longer replays stale assistant speech after a newer user utterance.
- Documented the restart playback rule in README.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect/start an agent, stop Continuous or Push to Talk after an assistant reply, then start the same speech mode again. If no later user utterance exists in the current state history, the assistant reply should be read aloud.

### How to test
- Executed:
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeCallOrchestrationServiceUnitTest,RecordedSpeechTurnServiceUnitTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest" test`

### Known issues and decisions
- The selector ignores non-speech assistant behaviour events and non-verbal complement events when deciding whether the latest utterance is assistant speech.
- A later `obs.user_utterance` in the current state history blocks replay, even if no response has been generated yet.
- A live browser smoke was not run in this milestone.

### Next steps
1. Re-test Valerian Continuous by stopping and restarting immediately after an assistant reply.
2. If nested outer-state agents need narrower leaf-only replay, add an exact active-state-path selector rather than the current state's existing event selector.

## Milestone 68
### Date
2026-06-14

### Goal
Remove the push-to-talk speech path and leave PROMETHEUS speech clients with continuous Realtime only, selectable between server VAD and semantic VAD.

### What changed
- Removed the backend-owned recorded speech turn pipeline, including request-based transcription/TTS SPI classes, views, services, controller endpoints, configuration properties, and tests.
- Removed Push to Talk tabs, controls, local `MediaRecorder` recording, upload handling, and recorded-audio playback from both `/valerian/` and `/realtime/`.
- Kept continuous Realtime WebRTC speech-to-speech with `server_vad` and `semantic_vad`, including sideband exact backend speech rendering and speech-sensing transcript display.
- Updated browser static-resource contracts, MVC/integration tests, and README to describe the continuous-only speech surface.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. In the Continuous tab, choose `Server VAD` or `Semantic VAD`, then start speech.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=RealtimeCallOrchestrationServiceUnitTest,RealtimeSidebandServiceContractTest,RealtimeSessionClientTest,RealtimeControllerWebMvcTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ScopedDemoControllerIntegrationTest" test`
  - `.\mvnw.cmd -q "-Dtest=TransitionDecisionActionReplayIntegrationTest" test`
- Attempted:
  - `.\mvnw.cmd -q test`
  - The full run stopped on `TransitionDecisionActionReplayIntegrationTest` because MySQL rejected a context-load connection with `Too many connections`; the same test passed when rerun in isolation.

### Known issues and decisions
- Push-to-talk is intentionally removed instead of hidden; the remaining speech clients rely on OpenAI VAD turn detection.
- Request-based audio transcription and TTS configuration keys are no longer part of the application surface.
- A live browser smoke with real OpenAI credentials still needs to verify both VAD modes after this removal.

### Next steps
1. Re-test Valerian Continuous with both `server_vad` and `semantic_vad`.
2. If manual turn capture is needed later, design it as a new milestone with a clear PROMETHEUS authority boundary instead of reviving the removed PTT code.

## Milestone 69
### Date
2026-06-14

### Goal
Make active Realtime speech sessions render canonical backend assistant speech even when the triggering observation is non-speech, such as visually sensed hand signs in the TDSR rock-scissor-paper agent.

### What changed
- Added `AssistantBehaviourPublishedEvent` as an internal application event emitted from the canonical `AgentApplicationService.publishBehaviour(...)` boundary.
- Updated `RealtimeSidebandService` to listen for published assistant behaviour plans for agents with active Realtime calls.
- Realtime now renders exact `BehaviourPlan.speech` through the sideband with `conversation="none"` for any backend-published assistant behaviour event, not only responses caused by Realtime ASR transcripts.
- Removed the direct exact-speech trigger from the Realtime transcript handler so published backend behaviour is the single speech trigger and duplicate audio is avoided.
- Aligned Realtime transcript handling with the text client fallback by generating `REALTIME_SPEECH` after an acknowledgement without a response even when the agent is in an inactive final state.
- Added per-session spoken-event deduplication and ignored speechless backend complement events.
- Documented that Realtime acts as an audio renderer for backend-authored speech across speech and visual input paths.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Start a continuous Realtime session for `gigitdsr.rock_scissor_paper`, then play a round using visual hand-sign sensing.

### How to test
- Executed:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/realtime/script.js`
  - `.\mvnw.cmd -q "-Dtest=AgentApplicationServiceGenerateOptionsUnitTest,RealtimeSidebandServiceContractTest,RealtimeCallOrchestrationServiceUnitTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=RealtimeSidebandServiceContractTest" test`

### Known issues and decisions
- The bridge is backend-only: clients still only send observations and render backend events.
- The sideband ignores published behaviour events when no active Realtime call exists or when the behaviour plan has no speech.
- Final states can still generate short final-policy speech after further user input, matching the text client behaviour, without reactivating or restarting the agent.
- A live browser smoke with OpenAI credentials is still needed to confirm visual hand-sign-triggered speech audio in Valerian.

### Next steps
1. Re-test TDSR rock-scissor-paper with visual hand-sign sensing and an active continuous Realtime session.
2. Watch for duplicate audio on speech-triggered turns; the expected behavior is one spoken rendering per published assistant speech event.

## Milestone 70
### Date
2026-06-19

### Goal
Allow independently hosted browser clients such as the external Valerian cockpit to call PROMETHEUS directly without introducing a proxy backend solely to avoid CORS.

### What changed
- Added property-driven global CORS configuration:
  - `prometheus.cors.allowed-origins`
  - `prometheus.cors.allowed-origin-patterns`
- CORS remains disabled by default unless one of the allowlist properties is configured.
- Allowed browser methods and headers cover the external cockpit flow:
  - JSON scoped demo requests
  - `X-Prometheus-Access-Code`
  - `Last-Event-ID`
  - raw `application/sdp` Realtime call preparation
  - Realtime call cleanup with `DELETE`
- Added Heroku-friendly production property bindings:
  - `PROMETHEUS_CORS_ALLOWED_ORIGINS`
  - `PROMETHEUS_CORS_ALLOWED_ORIGIN_PATTERNS`
- Updated README with local and Heroku CORS examples for external browser clients.

### How to run
1. Configure allowed external browser origins, for example:
   - `prometheus.cors.allowed-origins=http://127.0.0.1:5010,http://localhost:5010`
2. Start PROMETHEUS:
   - `.\mvnw.cmd spring-boot:run`
3. Start the external Valerian cockpit on the allowed origin and point its `PROMETHEUS Host` to the PROMETHEUS backend.

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=PrometheusCorsConfigurationWebMvcTest,ScopedDemoControllerIntegrationTest,RealtimeControllerWebMvcTest" test`

### Known issues and decisions
- A Valerian proxy backend is not required for the current browser client shape; direct browser-to-PROMETHEUS calls remain the preferred low-latency path.
- The allowlist is intentionally opt-in because scoped demo access codes are bearer-style client credentials.
- Robot-server CORS must still be configured separately because the external cockpit calls robot-server directly.
- A real browser smoke from a laptop-hosted cockpit to a Heroku PROMETHEUS app was not run in this milestone.

### Next steps
1. Configure Heroku with the laptop cockpit origin and run a live cross-origin cockpit smoke.
2. Keep the allowlist as narrow as practical for demos; use origin patterns only when laptop hostnames or ports cannot be fixed.

## Milestone 71
### Date
2026-06-20

### Goal
Ground the three GIGI TDSR agent prompts in the Tour de Suisse Robotique storyline while keeping normal demo responses focused and concise.

### What changed
- Added concise TDSR persona context to the active and final prompts for:
  - `gigitdsr.guessing_game_with_gestures`
  - `gigitdsr.social_context_sensitivity`
  - `gigitdsr.rock_scissor_paper`
- Added an explicit relevance guard so agents use the TDSR background only when asked or directly relevant.
- Added demo-capability tie-backs:
  - guessing game: speech, gestures, and short yes/no interaction with changing humans
  - social context: humans entering, leaving, and changing group presence near GIGI
  - rock-scissor-paper: hands, fingers, visual hand-sign detection, and social play
- Normalized the three agent definition files back to the repository's usual 4-space Java style.
- Updated GIGI TDSR prompt contract coverage and README agent-definition notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Create or connect one of the GIGI TDSR agents in Valerian:
   - `http://localhost:8080/valerian/`

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- The TDSR context is intentionally short and guarded to reduce prompt tokens and avoid persona leakage into routine game turns.
- Final prompts include only a minimal capability tie-back; live LLM responses should still be smoke-tested for naturalness before public demos.

### Next steps
1. Live-test the three GIGI TDSR agents with likely visitor questions about GIGI, TDSR, and the current demo capability.
2. If responses still over-explain the tour, reduce the final-prompt tie-back to a single optional clause.

## Milestone 72
### Date
2026-06-20

### Goal
Add a fourth GIGI TDSR agent for free station conversations with visitors, grounded in GIGI's tour persona and usable through Valerian Admin and Valerian.

### What changed
- Added `gigitdsr.tour_conversation`, a single-state German TDSR agent for open public conversation at any station.
- The agent supports user utterances plus speech and occasional nonverbal behaviour; it intentionally declares no visual, hand-sign, or social-situation sensing input.
- Added compact route/persona grounding covering major research, partner, cultural, and public-location stops, with guards against claiming a current station unless the context says so.
- Registered the definition in `AgentDefinitionRegistry`, so Valerian Admin can assign the key to access codes and Valerian can create scoped instances.
- Added the matching interaction-profile factory/tag and a manual seed wrapper under `src/test/java/ch/zhaw/prometheus/agents/gigitdsr`.
- Updated prompt, registry, profile, seed-source, and scoped-demo creation coverage.
- Updated README registered-agent documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign `gigitdsr.tour_conversation` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Enter that access code, create `GIGI TDSR - Tour Conversation`, connect it, and start the agent.

### How to test
- Executed:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest" test`

### Known issues and decisions
- Station knowledge is intentionally compact; the agent can discuss the route, but should not be treated as a full schedule database.
- The route prompt avoids exact station dates to reduce prompt size and lower the risk of stale live statements.
- Live LLM/browser testing is still needed to tune whether the general conversation agent feels concise enough for public station interactions.

### Next steps
1. Live-test likely visitor questions about GIGI, TDSR, specific stations, robot usefulness, and concerns about robots replacing people.
2. If answers are too long, further compress the route capsule or move station details behind a station-specific runtime context.

## Milestone 73
### Date
2026-06-20

### Goal
Fix Valerian Admin access-code agent-type assignment updates so existing assignments can be expanded, reduced, replaced, or cleared without server errors.

### What changed
- Changed `AccessCode.replaceAllowedAgentTypes(...)` from clear-and-reinsert semantics to a diff-style replacement:
  - keep overlapping existing keys
  - remove keys absent from the submitted replacement set
  - add only genuinely new keys
- This avoids unique-constraint failures when an update keeps any previously assigned key, such as `A -> A+B` or `A+B -> A`.
- Expanded database-backed admin-service coverage to exercise:
  - adding to an existing assignment
  - removing from an existing assignment
  - replacing with a disjoint assignment
  - clearing all assigned types
  - rejecting unknown and duplicate keys
- Added authorized MVC coverage for `PUT /admin/access-codes/{id}/agent-types` with an empty `agentTypeKeys` list.
- Tightened Valerian Admin static client coverage around sending only checked checkboxes as the full replacement body.
- Updated README to document complete replacement semantics for the admin assignment endpoint.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Select an access code, add and remove assigned agent types, then save.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AccessCodeAdminServiceIntegrationTest,AdminAccessCodeControllerWebMvcTest,ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- The fix is backend-side and protects all admin clients that use the replacement endpoint.
- The static Valerian Admin test does not simulate real browser checkbox interaction; it verifies the client payload-building contract. A future browser-level test would catch DOM interaction regressions more directly.

### Next steps
1. Re-test Valerian Admin manually by saving `A -> A+B`, `A+B -> A`, `A -> B`, and `B -> []`.
2. Add a browser-level admin cockpit smoke if this UI keeps changing.

## Milestone 74
### Date
2026-06-20

### Goal
Polish the four GIGI TDSR agent prompts so German-facing text uses UTF-8 umlauts and the general tour conversation agent produces shorter, less uniform replies.

### What changed
- Converted avoidable German ASCII spellings in the four TDSR production definitions from forms such as `fuer`, `koennen`, `Gespraech`, and `Haende` to proper UTF-8 German forms.
- Kept technical identifiers, JSON keys, event names, and the English nonverbal-plan prompt text unchanged.
- Tightened the tour conversation style guidance from "mostly one to three sentences" to:
  - usually one or two short sentences
  - three sentences only for direct explanation questions
  - varied answer length across the conversation
- Updated TDSR prompt contract coverage so future prompt edits fail if avoidable German ASCII umlaut spellings return.
- Updated README registered-agent notes to document the UTF-8 prompt decision and short varied response guidance.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Create or connect a GIGI TDSR agent in Valerian:
   - `http://localhost:8080/valerian/`
3. For `gigitdsr.tour_conversation`, ask several visitor-style questions and check that replies vary naturally between very short and moderately short responses.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- Source and Maven builds are configured for UTF-8, so Java text blocks can safely contain umlauts.
- The prompt keeps Swiss-style `ss` spellings such as `ausser` and `heisst`; this milestone specifically targets umlaut transliterations.
- Prompt wording can encourage shorter and more varied replies, but live LLM testing remains necessary because exact response length is probabilistic.

### Next steps
1. Live-test `gigitdsr.tour_conversation` with typical station questions and compare answer length over at least ten turns.
2. If answers remain too long, make the response contract stricter by asking for default one-sentence answers and only optional second sentences.

## Milestone 75
### Date
2026-06-21

### Goal
Add manual weather awareness as a declared non-visual sensing capability for the GIGI TDSR tour conversation agent.

### What changed
- Added weather observation event types:
  - `obs.weather.current`
  - `obs.weather.forecast`
- Added weather observation constants to `AgentInteractionProfile`.
- Declared current and forecast weather support only for `gigitdsr.tour_conversation`.
- Added `WeatherPromptEventContentAdapter` so weather JSON is rendered into compact readable prompt context instead of raw provider payloads.
- Updated the tour conversation prompt to treat weather as manually provided context, only use it when asked or directly relevant, and avoid claiming GIGI physically senses the weather.
- Added a Valerian Weather panel gated by `obs.weather.current obs.weather.forecast`:
  - location input
  - fetch current weather
  - send current weather
  - send short forecast
- The Valerian client resolves locations and weather through Open-Meteo in the browser, normalizes the data into PROMETHEUS weather events, and never emits weather on a timer.
- Updated README observation documentation and tests for the profile, prompt adapter, prompt contract, and static Valerian client contract.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Create or connect `GIGI TDSR - Tour Conversation`.
4. In the Sensing card, enter a location in the Weather panel, fetch current weather, then send current weather or forecast.
5. Ask GIGI a weather-relevant question in the conversation.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfileUnitTest,PromptEventContentAdapterUnitTest,GigiTdsrPromptContractTest,ValerianClientStaticResourceContractTest,SeedAgentInteractionProfileContractTest,ScopedDemoControllerIntegrationTest" test`

### Known issues and decisions
- Weather is intentionally manual context, not a continuous sensor loop, because weather does not change meaningfully during a short interaction.
- The first version uses Open-Meteo directly from the browser because no secret is needed. Production use should still review provider terms, attribution, and reliability needs.
- Weather sends do not automatically trigger speech. They add context to PROMETHEUS so the next relevant user turn can use it.

### Next steps
1. Live-test the Valerian Weather panel with Zurich, Lugano, and a station-specific location.
2. If operators need immediate weather narration, add an explicit `Send and Generate` button instead of changing weather sends into automatic speech triggers.

## Milestone 76
### Date
2026-06-21

### Goal
Let the GIGI TDSR tour conversation agent use the location carried by manual weather events as current-location context.

### What changed
- Extended the Tour Conversation weather prompt minimally so the location named in the latest weather event is treated as operator-provided current location until newer context overrides it.
- Kept the safety boundary explicit: GIGI must not claim that it physically senses weather or determined the location itself.
- Updated the prompt contract test and README weather observation notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Create or connect `GIGI TDSR - Tour Conversation`.
4. Send current weather or a forecast for a location, then ask GIGI where it is.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- This is intentionally prompt-only; no new event type or runtime state was added.
- The latest weather location is operator-provided context, not an independently sensed robot location.

### Next steps
1. Live-test with station locations and check that GIGI answers location questions naturally without overusing weather context.

## Milestone 77
### Date
2026-06-21

### Goal
Extend manual weather and current-location context from the TDSR tour conversation agent to the other GIGI TDSR agents.

### What changed
- Added `obs.weather.current` and `obs.weather.forecast` to the interaction profiles for:
  - `gigitdsr.guessing_game_with_gestures`
  - `gigitdsr.social_context_sensitivity`
  - `gigitdsr.rock_scissor_paper`
- Added concise weather/location prompt guidance to the active prompts of those agents.
- Kept weather and location guarded as operator-provided context that should only be used when asked or directly relevant to the current demo.
- Guarded the guessing-game final transition with `obs.user_utterance` so manual weather events cannot be treated as exit-intent inputs.
- Updated prompt/profile contract tests and README notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Create or connect any `gigitdsr.*` agent.
4. Use the Sensing Weather panel to send current weather or forecast for a location.
5. Ask a location- or weather-relevant question during the demo.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- Weather remains manual context, not a continuous sensor loop.
- The weather event location is treated as team-provided current-location context, not independently sensed robot localization.
- Specialized demo prompts still prioritize their active task unless weather or location is directly relevant.

### Next steps
1. Live-test weather/location questions in the guessing-game, social-context, and Schere-Stein-Papier demos.

## Milestone 78
### Date
2026-06-21

### Goal
Reduce repetitive question-ending speech and repeated `OPEN_QUESTION` gestures in the GIGI TDSR guessing-game and tour-conversation agents.

### What changed
- Updated the guessing-game state prompt to avoid extra open follow-up questions and keep the game to one simple yes/no question per turn.
- Updated the tour-conversation state prompt to use follow-up questions sparingly and allow many answers to end without a question.
- Tightened both agents' structured nonverbal plan prompts so:
  - `OPEN_QUESTION` is not selected merely because speech contains a question
  - `OPEN_QUESTION` is avoided when used recently
  - `NONE` is preferred for many routine or ordinary turns
  - gestures should vary across recent chat history
- Updated README notes and prompt contract tests.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Create or connect `GIGI TDSR - Ratespiel mit Gesten` or `GIGI TDSR - Tour Conversation`.
4. Run several turns and check that not every reply ends with a follow-up question or emits `OPEN_QUESTION`.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- This milestone is prompt-only; there is no deterministic gesture cooldown yet.
- Model compliance should be live-tested because exact gesture frequency remains probabilistic.

### Next steps
1. If live runs still overuse `OPEN_QUESTION`, add a deterministic post-generation cooldown that downgrades repeated `OPEN_QUESTION` to `NONE`.

## Milestone 79
### Date
2026-06-21

### Goal
Align the four GIGI TDSR agents with the current Valerian/G1 physical behaviour vocabulary.

### What changed
- Added `.agents/BEHVAIOURS_GIGI.md` as the reusable reference for GIGI
  `BehaviourPlan` physical outputs.
- Updated the guessing-game and tour-conversation structured nonverbal prompts
  to use only safe semantic `nonVerbal.gesture` labels and to forbid robot-server
  gesture IDs and unsupported locomotion fields.
- Removed `nonVerbal.motion` from the TDSR guessing-game and tour-conversation
  interaction profiles, because those agents now advertise semantic gestures,
  facial expression, and gaze only.
- Kept the social-context agent speech-only for outputs.
- Kept Schere-Stein-Papier on top-level `motion.handSign` with canonical
  `rock`, `scissor`, and `paper` values.
- Added a narrow structured-nonverbal sanitizer that strips `move` and `turn`
  if a language model returns them inside a nonverbal motion object.
- Expanded tests for safe gesture labels, valid `BehaviourPlan` JSON payloads,
  canonical RPS hand signs, and no unsupported locomotion.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Create each `gigitdsr.*` agent and inspect the interaction profile plus
   generated behaviour events.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest,RpsRevealPolicyContractTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest" test`

### Known issues and decisions
- The reusable behaviour reference intentionally keeps the user-requested file
  name `.agents/BEHVAIOURS_GIGI.md`.
- Generic multimodal demo prompts still mention `nonVerbal.motion`; this
  milestone only aligned the four Valerian-facing GIGI TDSR agents.
- Valerian can normalize some aliases, but TDSR agents should emit canonical
  semantic gesture and hand-sign values.

### Next steps
1. Live-test each TDSR agent on Valerian and confirm the G1 receives only mapped
   semantic gestures or canonical RPS hand signs.

## Milestone 80
### Date
2026-06-21

### Goal
Warm up the `gigitdsr.tour_conversation` persona and route grounding without changing its Valerian/Admin creation path, German speech contract, weather-location guardrails, or concise-response discipline.

### What changed
- Updated `TourConversation.PROMPT_STATE` so GIGI is more explicitly sympathetic, lightly humorous, open to people and places, and framed as a learning travel companion.
- Added Frank as GIGI's occasional travel/context reference and design/mobility/technology sparring partner, with a guard to mention him only when fitting.
- Replaced the compressed route capsule with a concrete station list covering B?rgenstock, Paradeplatz, Rinspeed, ETH Z?rich, Rheinfall, Quantum Basel, Emmentaler Schauk?serei, EPFL Lausanne, Furka/Tremola/Gotthard, SUPSI Lugano, Swiss Miniature, Migros Appenzell, and ZHAW Winterthur.
- Strengthened the prompt's humor and learning-companion wording while preserving existing German-only, sparse-follow-up, weather-location, no-Markdown, no-JSON, and current-station guardrails.
- Added a small final-state prompt update so the closing response can acknowledge the Frank/TDSR learning journey without starting a new topic.
- Updated prompt contract coverage and README notes for the revised tour-conversation behavior.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign `gigitdsr.tour_conversation` to an access code if needed.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect `GIGI TDSR - Tour Conversation` and ask station, Frank, robot-skepticism, and casual visitor questions.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- This milestone is prompt-only; exact warmth, humor frequency, and Frank mentions remain probabilistic and should be live-tested.
- The agent remains German-only at prompt and Realtime language-code level; the multilingual wording from the proposed draft was intentionally not adopted.
- The existing weather-location rule remains in place so manually sent weather locations can still serve as operator-provided current-location context.

### Next steps
1. Live-test `gigitdsr.tour_conversation` in Valerian and check that GIGI feels warmer without mentioning Frank or learning phrases too often.

## Milestone 81
### Date
2026-06-21

### Goal
Carry the warmer GIGI TDSR persona continuity from the tour conversation agent into the guessing-game, rock-scissor-paper, and social-context agents without weakening their individual demo focus.

### What changed
- Updated `GuessingGameWithGestures.PROMPT_STATE` with compact Frank/travel/persona continuity, a short route capsule, careful humor guidance, and a task-specific framing of the game as social yes/no interaction practice.
- Updated `RockScissorPaper.PROMPT_START` with the same continuity capsule while explicitly preserving deterministic rules, sign selection, and winner calculation outside the language model.
- Updated `SocialContextSensitivity.PROMPT_STATE` with the continuity capsule and a stronger respectful-social-attention frame for arrivals, departures, and group changes without pressuring people.
- Updated all three final prompts so their closings reference the Frank/TDSR learning journey only briefly and remain bounded by each agent's completed demo task.
- Updated prompt contract coverage and README notes for shared TDSR persona continuity across all four GIGI TDSR agents.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign any of the `gigitdsr.*` task agents to an access code if needed.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the guessing-game, Schere-Stein-Papier, and social-context agents and exercise their normal demo flows.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest" test`

### Known issues and decisions
- This milestone is prompt-only; exact humor frequency, Frank mentions, and learning-language reuse remain probabilistic and need live Valerian/robot testing.
- The task agents intentionally use a compact route capsule instead of the full tour station list so their game and social-event prompts stay focused.
- No registry, interaction-profile, Admin UI, or Valerian client changes were needed because the existing agent keys and modalities are unchanged.

### Next steps
1. Live-test all four GIGI TDSR agents in Valerian and check that they feel like the same GIGI while each still prioritizes its own task.

## Milestone 82
### Date
2026-06-21

### Goal
Add a fifth GIGI TDSR agent that keeps the open tour-conversation flow while adding sparse, non-disruptive social context sensitivity.

### What changed
- Added `gigitdsr.tour_conversation_social_context` as a new production `AgentDefinition`:
  - class: `TourConversationSocialContextSensitivity`
  - display name: `GIGI TDSR - Tour Conversation Social Context`
  - base behavior: tour conversation persona, route grounding, weather/location context, and occasional nonverbal gestures
  - added input capability: human presence, social grouping, and computed social situation changes
- Added a prompt-gated self-transition on `obs.social.situation_change`:
  - routine or low-value changes should be ignored
  - salient changes such as suddenly being alone or a shift from one person to a group may produce a short aside
  - social comments must remain sparse and must not interrupt serious or important answers
- Added a dedicated interaction profile combining tour-conversation and social-context observations/tags.
- Registered the new definition so Valerian Admin can assign it to access codes and Valerian can create scoped instances.
- Added the matching manual seed wrapper, fixtures, registry/profile/scoped-demo coverage, prompt contract assertions, README entry, and source-profile contract coverage.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign `gigitdsr.tour_conversation_social_context` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create `GIGI TDSR - Tour Conversation Social Context`, connect it, start it, and run text plus social camera/manual social inputs.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest" test`

### Known issues and decisions
- This milestone uses a prompt-gated social interjection transition instead of a deterministic cooldown/rate limiter. That keeps the implementation aligned with existing seed-agent patterns, but exact frequency remains model-dependent.
- The original `gigitdsr.tour_conversation` agent is unchanged; users can choose the plain or social-context-aware variant in Valerian Admin.
- The social variant deliberately comments on social changes only as a short aside when appropriate, not as the primary topic of every computed change.

### Next steps
1. Live-test the social tour conversation variant with manual `now_alone`, `arrival`, `crowd_detected`, and group-size-change scenarios and tune the gate if remarks are too frequent or too quiet.

## Milestone 83
### Date
2026-06-21

### Goal
Reorganize the GIGI TDSR German agent implementation package from the old flat `gigitdsr` package into the new `tdsr.core.de` hierarchy.

### What changed
- Moved the five production GIGI TDSR agent definitions to `src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/de`.
- Moved the matching manual seed wrappers and prompt contract test package to `src/test/java/ch/zhaw/prometheus/agents/tdsr/core/de`.
- Updated registry imports, test fixtures, and source-profile contract paths to use the new Java package.
- Preserved the stable Valerian/Admin agent definition keys (`gigitdsr.*`) so existing access-code assignments and scoped demo creation semantics do not change.
- Hardened the GIGI TDSR replay integration test cleanup so access-code-to-agent links are removed before agents.
- Updated README package-layout notes.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign any `gigitdsr.*` agent to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned GIGI TDSR agent and confirm the public key is unchanged.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest,GigiTdsrGuessingGameWithGesturesReplayIntegrationTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`

### Known issues and decisions
- This is a Java/package reorganization only; public agent definition keys intentionally remain under `gigitdsr.*`.
- Historical PROJECT milestone entries still mention the old package paths because those entries describe the repository state at the time they were completed.

### Next steps
1. Continue with the planned `tdsr` subpackage split for additional SHHD agents once this reorganization is reviewed.

## Milestone 84
### Date
2026-06-21

### Goal
Create French, Italian, and English variants of the five TDSR core agents and switch all TDSR core public keys to the package-shaped `tdsr.core.<language>.<agent>` namespace.

### What changed
- Added French definitions under `src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/fr`.
- Added Italian definitions under `src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/it`.
- Added English definitions under `src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/en`.
- Added `LANGUAGE_FRENCH` and `LANGUAGE_ITALIAN` to `AgentDefinition`; English already existed.
- Registered all 20 TDSR core definitions:
  - `tdsr.core.de.*`
  - `tdsr.core.fr.*`
  - `tdsr.core.it.*`
  - `tdsr.core.en.*`
- Renamed the five German public keys from `gigitdsr.*` to `tdsr.core.de.*`.
- Translated model-facing prompts for the localized variants, including state, starter, final, decision, extraction, and social-interjection prompts.
- Kept protocol vocabulary stable across languages: JSON schema fields, event names, gesture labels, change types, and `motion.handSign` values remain language-neutral.
- Added a shared `TdsrCoreAgentFactory` for the non-German variants so state-machine wiring and interaction-profile assignment stay consistent.
- Updated README, registry tests, access-code/scoped-demo tests, source-profile coverage, and localized prompt contract coverage.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign a key such as `tdsr.core.fr.tour_conversation`, `tdsr.core.it.rock_scissor_paper`, or `tdsr.core.en.tour_conversation_social_context` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned agent and start Realtime; the agent language code should be `fr`, `it`, `en`, or `de` according to the key.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,TdsrCoreLocalizedPromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest,AdminAccessCodeControllerWebMvcTest,GigiTdsrGuessingGameWithGesturesReplayIntegrationTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`

### Known issues and decisions
- This milestone intentionally supersedes the previous compatibility decision to keep `gigitdsr.*` public keys.
- Existing access-code assignments that still reference old `gigitdsr.*` keys need to be recreated or migrated to the new `tdsr.core.de.*` keys.
- The localized prompts are translated manually in code and should be live-tested with native speakers for tone, idiom, and robot suitability.
- Nonverbal and hand-sign protocol values remain English-like machine values because Valerian/G1 mappings depend on those labels.

### Next steps
1. Live-test one agent per language in Valerian with Realtime transcription and spoken output.
2. Decide whether the Valerian Admin UI needs language filters or grouping for the expanded TDSR catalog.

## Milestone 85
### Date
2026-06-21

### Goal
Add Babylon variants of the five TDSR core agents that start in English but can answer in German, French, Italian, or English without pinning a Realtime transcription language.

### What changed
- Added five production definitions under `src/main/java/ch/zhaw/prometheus/agentdefs/tdsr/core/babylon`:
  - `tdsr.core.babylon.guessing_game_with_gestures`
  - `tdsr.core.babylon.social_context_sensitivity`
  - `tdsr.core.babylon.rock_scissor_paper`
  - `tdsr.core.babylon.tour_conversation`
  - `tdsr.core.babylon.tour_conversation_social_context`
- Copied the English core agents as the implementation base, kept English starter prompts for the opening turn, and replaced English-only state/final guards with the shared multilingual instruction:
  `Du kannst Deutsch, Franz?sisch, Italienisch und Englisch. Antworte in der Sprache, in der Du angesprochen wirst.`
- Kept the Babylon definitions without a `languageCode()` override so Valerian/Realtime does not receive a fixed language hint for these agents.
- Extended the stop/readiness/play-again guard prompts so they interpret German, French, Italian, and English user intent.
- Registered the Babylon keys and updated registry, source-profile, and Babylon prompt contract coverage.
- Updated README entries for the expanded TDSR core catalog.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign a key such as `tdsr.core.babylon.tour_conversation` or `tdsr.core.babylon.rock_scissor_paper` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned agent and start Realtime; the agent metadata should not contain a fixed language code.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AgentInteractionProfileUnitTest,SeedAgentInteractionProfileContractTest,GigiTdsrPromptContractTest,TdsrCoreLocalizedPromptContractTest,TdsrCoreBabylonPromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest,AdminAccessCodeControllerWebMvcTest,GigiTdsrGuessingGameWithGesturesReplayIntegrationTest,GigiTdsrSocialContextSensitivityReplayIntegrationTest,GigiTdsrRockScissorPaperReplayIntegrationTest" test`

### Known issues and decisions
- The Babylon variants intentionally omit Realtime language-code metadata; actual language switching still depends on live ASR/model behavior and should be tested in Valerian.
- The first generated starter turn remains English by design. Later replies should follow the user's language among German, French, Italian, and English.
- Protocol values remain language-neutral machine values as in the fixed-language variants.

### Next steps
1. Live-test a Babylon tour conversation in Valerian by switching among German, French, Italian, and English across turns.
2. Confirm Realtime session payloads omit the input transcription language for `tdsr.core.babylon.*` agents.

## Milestone 86
### Date
2026-06-21

### Goal
Rename the elderly-care demonstrator namespace from `gigielderlycare` to `elderlycare`.

### What changed
- Moved production elderly-care agent definitions from `src/main/java/ch/zhaw/prometheus/agentdefs/gigielderlycare` to `src/main/java/ch/zhaw/prometheus/agentdefs/elderlycare`.
- Moved the matching manual seed wrappers and prompt contract tests from `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare` to `src/test/java/ch/zhaw/prometheus/agents/elderlycare`.
- Updated Java package declarations, registry imports, source-profile contract paths, prompt contract references, and README catalog entries.
- Renamed the public Valerian/Admin keys from `gigielderlycare.*` to `elderlycare.*`.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign a key such as `elderlycare.therapy_appointment_reminder` or `elderlycare.smart_goal_coaching` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned elderly-care agent.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,PflegezentrumDemoPromptContractTest" test`

### Known issues and decisions
- This is a clean rename. The old `gigielderlycare.*` public keys are not preserved.
- Existing access-code assignments that reference old `gigielderlycare.*` keys need to be recreated or migrated to the new `elderlycare.*` keys.

### Next steps
1. If existing demo databases are reused, update any saved access-code assignments from `gigielderlycare.*` to `elderlycare.*`.

## Milestone 87
### Date
2026-06-21

### Goal
Introduce package-based browsing for Valerian Admin agent type assignments without hard-coding package names in the client.

### What changed
- Added `AgentDefinition.packagePath()` to derive package segments from Java packages below `ch.zhaw.prometheus.agentdefs`.
- Extended `AdminAgentTypeView` with `packagePath` while keeping the existing key, display name, and description fields.
- Included package metadata in both admin and scoped demo agent-type views.
- Replaced the flat Valerian Admin agent-type checkbox list with a dynamic package tree built client-side from `packagePath`.
- Added package expansion state, assigned-count badges, and a package/name/key filter for the assignment panel.
- Updated controller, service, scoped-demo, and static-resource tests plus README API documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Select an access code and browse agent types through the package tree.
4. Save assignments; the payload remains the list of selected agent definition keys.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,AccessCodeAdminServiceIntegrationTest,AdminAccessCodeControllerWebMvcTest,ScopedDemoControllerIntegrationTest,ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- Package hierarchy is derived from backend metadata and is not hard-coded in the Valerian Admin client.
- The assignment storage model is unchanged: only agent definition keys are persisted for access-code assignments.
- `/demo/agent-types` also receives `packagePath` because it shares `AdminAgentTypeView`, although the Valerian user cockpit does not currently use it for browsing.

### Next steps
1. Live-check Valerian Admin with a dense agent catalog and adjust spacing or default expansion rules if operators need a more compact view.
## Milestone 88
### Date
2026-06-21

### Goal
Add the initial German TDSR SHHD scene-agent set under `tdsr.shhd.de` using the existing social tour-conversation architecture.

### What changed
- Added a shared SHHD social-tour factory for single-state agents with one interaction state, the explicit user-stop final transition used by tour conversation, sparse social-context self-transition on `obs.social.situation_change`, manual weather context support, and structured nonverbal gesture prompting.
- Added five German production definitions:
  - `tdsr.shhd.de.epfl_active`
  - `tdsr.shhd.de.furka`
  - `tdsr.shhd.de.interviewing_people`
  - `tdsr.shhd.de.supsi_active`
  - `tdsr.shhd.de.unis_student`
- Condensed the authoritative Word prompt material into German state/final prompts while preserving each agent's intent: EPFL/Qolo social navigation, Furka/Belvedere/Goldfinger mobility and memory, public interviews about robot collaboration and trust, SUPSI teleoperated workcell safety and human support, and university-student motivation behind robotics work.
- Registered the new definitions so Valerian Admin exposes them dynamically under `tdsr / shhd / de`.
- Added prompt/registry/profile/admin-service contract coverage and README catalog entries.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign a key such as `tdsr.shhd.de.epfl_active` or `tdsr.shhd.de.supsi_active` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned SHHD agent and start Realtime; the agent language code should be `de`.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,TdsrShhdGermanPromptContractTest,AccessCodeAdminServiceIntegrationTest" test`

### Known issues and decisions
- The SHHD prompts are condensed from the Word documents in `.agents/tdsr/shhdagents`; those source documents are not runtime inputs.
- The German variants intentionally force German prompt and Realtime language behavior. French, Italian, English, and Babylon variants are planned as separate packages.
- The SUPSI source prompt had duplicated material and mixed autonomous wording with teleoperation constraints; this implementation keeps the safer teleoperation framing.
- Social context sensitivity remains prompt-gated rather than deterministically rate-limited, matching the current TDSR social tour agent.

### Next steps
1. Live-test the five German SHHD agents in Valerian with text, Realtime speech, manual weather, and social-context events.
2. Create the French, Italian, English, and Babylon SHHD variants once the German prompts are reviewed.

## Milestone 89
### Date
2026-06-21

### Goal
Replicate the TDSR core language-package structure for SHHD scene agents by adding English, Italian, French, and Babylon variants of the five SHHD agents.

### What changed
- Added a generic SHHD agent-definition base class and localized SHHD prompt library.
- Decoupled the SHHD social-tour factory from the German prompt helper while preserving the shared TDSR tour nonverbal gesture prompt.
- Added fixed-language SHHD variants under:
  - `tdsr.shhd.en.*`
  - `tdsr.shhd.it.*`
  - `tdsr.shhd.fr.*`
- Added language-open Babylon SHHD variants under `tdsr.shhd.babylon.*`.
- Each new package contains the same five scene agents:
  - `epfl_active`
  - `furka`
  - `interviewing_people`
  - `supsi_active`
  - `unis_student`
- Registered all 20 new definitions so Valerian Admin exposes them dynamically through package metadata.
- Fixed-language variants set Realtime language metadata to `en`, `it`, or `fr`; Babylon variants intentionally leave language metadata unset and instruct GIGI to answer in German, French, Italian, or English according to the user's language.
- Refined localized/Babylon prompts to restore source-prompt scene beats for social-context example reactions, EPFL object/person distinction, SUPSI teleoperation wording, adaptive interview follow-ups, Furka tour continuity, and UnisStudent response strategy.
- Replaced localized SHHD outcome-extraction prompts with one compact shared JSON extraction prompt for all SHHD language variants.
- Added localized/Babylon prompt contract coverage plus registry, source-profile, and admin package-path coverage.
- Updated README catalog notes for the expanded SHHD package structure.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign a key such as `tdsr.shhd.en.epfl_active`, `tdsr.shhd.it.supsi_active`, `tdsr.shhd.fr.interviewing_people`, or `tdsr.shhd.babylon.unis_student` to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned SHHD agent. Fixed-language variants should expose their language code; Babylon variants should not.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,TdsrShhdGermanPromptContractTest,TdsrShhdLocalizedPromptContractTest,AccessCodeAdminServiceIntegrationTest" test`

### Known issues and decisions
- The localized SHHD prompt text is intentionally concise and should be live-tested with native speakers before public deployment.
- Babylon starts in English when no user language is known and then relies on prompt behavior plus unset Realtime language metadata for language switching.
- The SHHD agents still use prompt-gated social-context interjections rather than deterministic rate limiting.

### Next steps
1. Live-test at least one SHHD agent per fixed language and one Babylon SHHD agent in Valerian.
2. Review localized tone and terminology with native speakers, especially for the SUPSI and EPFL technical scenes.

## Milestone 90
### Date
2026-06-22

### Goal
Reduce token duplication in TDSR core outcome extraction prompts without changing spoken behavior or outcome schemas.

### What changed
- Added compact shared outcome extraction helpers in `TdsrCoreAgentFactory` for tour conversation, social tour conversation, social context sensitivity, and guessing game with gestures.
- Replaced duplicated localized and Babylon `PROMPT_OUTCOME_EXTRACTION` text in the core German, English, French, Italian, and Babylon variants with the shared helpers.
- Left `RockScissorPaper` unchanged because it uses deterministic RPS actions/storage and has no static outcome extraction prompt.
- Added German, localized, and Babylon prompt contract coverage to assert the shared compact JSON schemas and prevent old localized extraction prompt text from returning.

### How to run
- No runtime workflow changes. Continue assigning and running `tdsr.core.*` agents through Valerian Admin and Valerian as before.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest,TdsrCoreLocalizedPromptContractTest,TdsrCoreBabylonPromptContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest,TdsrCoreLocalizedPromptContractTest,TdsrCoreBabylonPromptContractTest,AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,TdsrShhdGermanPromptContractTest,TdsrShhdLocalizedPromptContractTest,AccessCodeAdminServiceIntegrationTest" test`

### Known issues and decisions
- The extraction prompts are intentionally English-only technical instructions because they are not spoken to users.
- The public agent keys, state prompts, final prompts, language metadata, and output schemas are unchanged.

### Next steps
1. Consider the same shared-extraction-prompt pattern if other non-TDSR agent families accumulate duplicated technical extraction prompts.

## Milestone 91
### Date
2026-06-22

### Goal
Add backend-defined Valerian Admin presets that create specific access-code and agent-type assignment sets without requiring manual checkbox work in the Admin cockpit.

### What changed
- Added an explicit `shhd_scene_agents` access-code preset catalog in Java with five entries:
  - `shhde` for the five `tdsr.shhd.de.*` scene agents.
  - `shhen` for the five `tdsr.shhd.en.*` scene agents.
  - `shhfr` for the five `tdsr.shhd.fr.*` scene agents.
  - `shhit` for the five `tdsr.shhd.it.*` scene agents.
  - `shhba` for the five `tdsr.shhd.babylon.*` scene agents.
- Added authenticated Admin API endpoints:
  - `GET /admin/access-code-presets`
  - `POST /admin/access-code-presets/{presetKey}/apply`
- Preset application is transactional and strict: all preset codes must be submitted, existing code conflicts abort the whole operation, and selected agent type keys must belong to that preset entry.
- Added a Valerian Admin header preset menu and review modal with one accordion per preset access code. Preset agents are checked by default and can be unchecked before creation.
- Updated README Admin API and Admin cockpit documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Enter the configured admin token.
4. Use the preset button next to the forget-token button, select `SHHD scene access codes`, review the checked agents, and create the preset codes.

### How to test
- Targeted tests run:
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=AccessCodeAdminServiceIntegrationTest,AdminAccessCodeControllerWebMvcTest,ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- Agent specifications remain Java `AgentDefinition` classes registered through `AgentDefinitionRegistry`; presets persist only access codes and allowed agent type keys.
- Preset definitions are explicit maps from access code to agent type keys, not package scans, so the client does not infer package contents.
- The Babylon SHHD access code is `shhba` and maps to `tdsr.shhd.babylon.*` because the Java package is `babylon`, not `ba`.
- If a preset code already exists, operators must adjust existing codes manually before applying the preset; there is still no delete/archive flow for access codes.

### Next steps
1. Live-check the Admin cockpit preset modal against a local database and confirm the created codes appear in Valerian with the expected agent sets.
2. Add more Java preset entries if future rehearsals need repeatable access-code bundles.

## Milestone 92
### Date
2026-06-22

### Goal
Fix Valerian Admin agent-type package accordions so packages with selected agents can still be manually collapsed.

### What changed
- Confirmed that assigned package accordions were being reopened immediately because `renderAgentTypes()` called `expandAssignedPackages(...)` on every render.
- Added `collapsedAgentTypePackages` state to remember packages the operator manually closes.
- Updated assigned-package auto-expansion to respect manual collapses while still auto-opening selected packages when switching access codes, creating a code, applying a preset, or saving assignments.
- Kept filter behavior unchanged: active search text still expands matching package paths for visibility.
- Added static contract coverage for the collapsed-package state and assigned-package expansion call.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Select an access code with assigned SHHD agents and verify a `5/5` package such as `it` can be closed and reopened.

### How to test
- Targeted tests run:
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- This is a Valerian Admin client-state fix only; no backend API or persistence behavior changed.
- Search/filter mode still forces packages open so matching agent types remain visible.

### Next steps
1. Live-check the Admin cockpit package tree with assigned SHHD preset codes and confirm selected packages can be manually collapsed.

## Milestone 93
### Date
2026-06-22

### Goal
Let Valerian cockpit operators explicitly choose the browser microphone and speaker used for scoped PROMETHEUS Realtime speech sessions.

### What changed
- Added Microphone and Speaker selectors to the Continuous Speech tab, plus a Refresh Audio Devices action.
- Added browser media-device enumeration for audioinput and audiooutput devices, with labels unlocked through an operator-initiated microphone permission request when needed.
- Persisted selected microphone and speaker device IDs in localStorage.
- Applied the selected microphone through Realtime getUserMedia audio constraints when starting a speech session.
- Applied the selected speaker to the assistant audio element through HTMLMediaElement.setSinkId(...) when supported by the browser.
- Added clear fallback status text when speaker routing is not supported and browser/system default output is used.
- Kept the existing scoped Realtime endpoint, SDP flow, voice selection, VAD selection, backend complement behavior, and transcript gating unchanged.
- Updated Valerian static contract coverage and README documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect a scoped agent, open the Continuous Speech tab, click Refresh Audio Devices, select the desired microphone and speaker, then start speech.

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfilePersistenceUnitTest,AgentInteractionProfileUnitTest" test`
  - `.\mvnw.cmd -q test` (72 suites, 301 tests, 0 failures, 0 errors, 0 skipped)

### Known issues and decisions
- Microphone changes apply to the next Realtime speech session; restart speech after changing input devices.
- Browser speaker routing depends on setSinkId support and browser permissions. When unsupported, the cockpit keeps using browser/system default output.
- This milestone does not add audio level meters, speaker test tones, STUN/TURN configuration, or robot-side audio.

### Next steps
1. Live-test with the demo laptop's headset, conference microphone, and speaker devices in the target browser.

## Milestone 94
### Date
2026-06-22

### Goal
Expose browser-side Realtime WebRTC ICE/STUN diagnostics in the PROMETHEUS Valerian cockpit without changing backend Realtime or robot-server behavior.

### What changed
- Added a Realtime transport status pill and detail line to the Continuous Speech tab.
- Wired the browser RTCPeerConnection diagnostics for iceconnectionstatechange, connectionstatechange, icegatheringstatechange, and icecandidateerror.
- Logged ICE/peer/gathering transitions to the existing realtime activity log.
- Surfaced explicit operator messages for ICE failure, peer connection failure, disconnection, and ICE candidate errors.
- Kept the scoped Realtime call endpoint, SDP exchange, VAD behavior, audio device selection, and backend complement flow unchanged.
- Updated Valerian static contract coverage and README documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect a scoped agent, open the Continuous Speech tab, start speech, and watch the transport pill/detail while testing normal and failing network conditions.

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `.\mvnw.cmd -q "-Dtest=TransitionDecisionActionReplayIntegrationTest" test`
  - `.\mvnw.cmd -q test` (72 suites, 301 tests, 0 failures, 0 errors, 0 skipped)

### Known issues and decisions
- This is a pure cockpit UI diagnostic change. It does not add STUN/TURN configuration, TURN credentials, or backend-side WebRTC inspection.
- Browser ICE candidate error details depend on what the browser exposes through RTCPeerConnectionIceErrorEvent.
- The failure message intentionally tells operators to restart speech first, then investigate network/STUN/TURN if failures repeat.

### Next steps
1. Live-test the diagnostic while reproducing the laptop/demo ICE failure and compare the cockpit status with browser about:webrtc details.

## Milestone 95
### Date
2026-06-22

### Goal
Tighten the behavioural accent of TDSR tour-conversation and SHHD scene agents so GIGI speaks more briefly while using warmer, good-willed micro-humor more often.

### What changed
- Updated the TDSR core tour-conversation prompts in German, English, French, Italian, and Babylon variants to prefer one very short spoken sentence, often 3-10 words, with two short sentences only when a direct explanation truly needs it.
- Updated the tour-conversation social-context variants through their inherited base prompts and tightened their final prompts to one short goodbye sentence.
- Updated the SHHD German, English, French, Italian, and Babylon shared prompt profiles so all SHHD scene agents use the same concise response contract.
- Added explicit warm micro-humor guidance: light irony, self-irony, playful understatement, and callbacks to earlier turns, while forbidding mocking, hurtful, safety-relevant, or serious-moment jokes.
- Tightened starter and final prompts for affected agents so openings and closings do not drift into multi-sentence monologues.
- Left the guessing-game, rock-scissor-paper, and dedicated social-context-sensitivity agents unchanged.
- Updated prompt contract tests to assert the new concise micro-humor wording and reject the old one/two/rare-three response contract where this milestone applies.
- Updated README notes for the TDSR catalogue.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
3. Assign an affected agent such as `tdsr.core.de.tour_conversation`, `tdsr.core.babylon.tour_conversation`, or any `tdsr.shhd.*` scene agent to an access code.
4. Open Valerian:
   - `http://localhost:8080/valerian/`
5. Create or connect the assigned agent and live-test short turns, explanation questions, and callback-friendly conversational moments.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=GigiTdsrPromptContractTest,TdsrCoreLocalizedPromptContractTest,TdsrCoreBabylonPromptContractTest,TdsrShhdGermanPromptContractTest,TdsrShhdLocalizedPromptContractTest" test`

### Known issues and decisions
- This is a prompt-only behavioural accent change; no runtime, API, interaction-profile, or agent-key semantics changed.
- Exact response length and humor frequency remain probabilistic and need live Valerian/robot testing with GPT-5.2.
- The excluded guessing-game, Schere-Stein-Papier, and dedicated social-context-sensitivity agents keep their existing task-specific prompt cadence.

### Next steps
1. Live-test affected tour and SHHD agents in Valerian with GPT-5.2 and check whether responses stay genuinely short instead of becoming long single sentences.
2. If live runs still drift long, consider a deterministic post-generation speech trimmer or a stricter output-length validator for affected agents.

## Milestone 96
### Date
2026-06-23

### Goal
Let Valerian cockpit operators refresh and select the browser camera used for visual sensing, including USB cameras connected after page load.

### What changed
- Added a Camera selector with an appended refresh icon button and camera-device status line to the Valerian Sensing card.
- Added browser videoinput enumeration with labels unlocked through an operator-initiated camera permission request when needed.
- Persisted the selected camera device ID in localStorage under `prometheus.valerian.cameraDevice`.
- Applied the selected camera through getUserMedia video deviceId constraints when starting visual sensing.
- Restarted the camera stream automatically when the selected camera changes while visual sensing is live.
- Refreshed camera devices on browser devicechange events so newly plugged USB cameras can appear without reloading the cockpit.
- Kept backend APIs, event payload schemas, interaction profiles, detection models, and observation emission semantics unchanged.
- Updated Valerian static contract coverage and README documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect an agent with visual sensing, refresh cameras, select the desired camera, and start visual sensing.
4. Plug in a USB camera, click Refresh if the browser does not fire devicechange immediately, select the new camera, and verify the preview switches.

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- Browser camera labels still depend on camera permission, so operators may need to click Refresh Cameras once to unlock readable labels.
- Device routing is browser-owned; if a selected camera disappears or is blocked by permissions, the cockpit reports the camera start error and leaves the backend untouched.
- This milestone does not add camera resolution controls or robot-side camera selection.

### Next steps
1. Live-test with the demo laptop's built-in camera and a USB camera in the target browser.
2. If operators need more control, add explicit resolution/frame-rate choices beside the camera selector.

## Milestone 97
### Date
2026-06-23

### Goal
Add a persistent light/dark theme switch to both the Valerian cockpit and Valerian Admin cockpit while treating the existing UI as the light mode baseline.

### What changed
- Added icon-only theme toggle buttons to the pre-auth card headers and authenticated header tool rows in both `/valerian/` and `/valerian-admin/`.
- Added page-local dark theme CSS overrides for cockpit panels, forms, cards, status pills, lists, accordions, menus, and modal surfaces while leaving the current design as light mode.
- Persisted the shared theme selection in localStorage under `prometheus.valerian.theme` so Valerian and Valerian Admin stay in sync across page loads.
- Applied the stored theme in a small head script before the main page script runs to avoid a visible light-to-dark flash.
- Updated both static resource contract tests and README documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`
4. Toggle the moon/sun button on either page and verify the chosen theme persists when reloading or moving between the two cockpits.

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- The current light UI remains the default; dark mode is opt-in and browser-local.
- The theme is a browser presentation preference only and does not affect backend API, access-code, agent, sensing, or realtime behavior.
- Live visual contrast should still be checked on the target demo laptop because camera/video lighting and browser font rendering can vary.

### Next steps
1. Live-check both cockpits on the target browser in light and dark mode.
2. If operators use both pages side by side, decide whether a future shared static CSS/JS asset is worth extracting from the two page-local implementations.

## Milestone 98
### Date
2026-06-27

### Goal
Expose the Realtime audio tuning controls needed by Valerian while preserving PROMETHEUS-owned speech generation.

### What changed
- Extended `RealtimeCallSettings` and `RealtimeCallConfig` with optional Realtime tuning fields for server VAD, semantic VAD, interruption, input noise reduction, output speed, reasoning effort, max output tokens, and input transcription logprob inclusion.
- Mapped the tuning fields into both the initial OpenAI `/v1/realtime/calls` session payload and later backend sideband `session.update` messages so instruction refreshes keep the same audio contract.
- Kept Realtime turn detection under PROMETHEUS authority: `create_response=false` is always sent, `vadCreateResponse=true` is rejected with `400 Bad Request`, and `vadInterruptResponse` maps only to OpenAI `interrupt_response`.
- Added explicit `inputNoiseReduction=off` support by sending OpenAI `audio.input.noise_reduction=null`.
- Added global and scoped controller coverage for the query contract plus serialization/orchestration coverage for the OpenAI payloads.
- Updated README Realtime notes with the new query parameters and backend authority rules.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Start a scoped Realtime speech call once Milestone 99 adds the cockpit-side controls.

### How to test
- Targeted tests run:
  - `.\mvnw.cmd -q "-Dtest=RealtimeSessionClientTest,RealtimeSidebandServiceContractTest,RealtimeControllerWebMvcTest,ScopedDemoControllerWebMvcTest,RealtimeCallOrchestrationServiceUnitTest" test`

### Known issues and decisions
- Browser Realtime events such as `input_audio_buffer.speech_started` and browser-side `response.cancel` remain client/OpenAI data-channel concerns in the current WebRTC architecture; this milestone changes backend session configuration only.
- `vadCreateResponse=true` remains unsupported because assistant speech must be generated and persisted by PROMETHEUS before OpenAI speaks it.
- The tuning ranges are backend validation choices aligned with expected OpenAI field ranges, but live values still need rehearsal on the target microphone/speaker setup.

### Next steps
1. Milestone 99: adopt Marc Styger's Valerian cockpit audio tuning UI and wire it to these backend query parameters.

## Milestone 99
### Date
2026-06-27

### Goal
Adopt Marc Styger's Valerian cockpit Realtime audio tuning updates in the bundled PROMETHEUS Valerian cockpit.

### What changed
- Moved routine voice, VAD, backend complement, and advanced Realtime speech controls into an Advanced Speech Settings accordion in the Sensing column.
- Wired the cockpit to send the Milestone 98 backend query parameters for VAD timing/eagerness/interruption, input noise reduction, output speed, reasoning effort, max output tokens, and input transcription logprob inclusion.
- Kept `vadCreateResponse=true` unavailable in the cockpit because PROMETHEUS remains responsible for creating assistant responses before OpenAI speaks them.
- Made continuous speech full-duplex by default, with local barge-in cancellation sending `response.cancel` when OpenAI reports `input_audio_buffer.speech_started` during active assistant audio.
- Added an opt-in half-duplex fallback that mutes existing microphone tracks during assistant playback without stopping or recreating the media stream.
- Added assistant-echo transcript suppression, remote audio playback/track diagnostics, Realtime inbound-audio stats warnings, and active microphone processing logging.
- Updated the Valerian static resource contract test and README documentation.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect an agent, open Sensing > Advanced Speech Settings, tune the speech controls, then start Continuous Speech.

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`

### Known issues and decisions
- Live full-duplex quality still depends on the target browser, microphone, speaker, and room acoustics; the new half-duplex fallback is intentionally operator-controlled.
- Browser-side `response.cancel` remains a data-channel event sent by the cockpit, not a backend REST endpoint.
- The Realtime audio stats diagnostics are advisory and depend on browser getStats fields that vary between browsers.

### Next steps
1. Live-test the bundled cockpit on the demo laptop with the target microphone and speaker setup.
2. Tune default VAD/noise-reduction values only after live rehearsal shows a repeatable need.

## Milestone 100
### Date
2026-06-29

### Goal
Preserve the current application agent catalog on a separate `agents` branch, then make `main` the clean PROMETHEUS framework line without static dependencies on TDSR or elderly-care agent applications.

### What changed
- Created and pushed the `agents` branch from the current Valerian/access-code feature head so the full TDSR and elderly-care application catalog remains available unchanged.
- Fast-forwarded local `main` to the Valerian/access-code/runtime work before cleanup.
- Removed TDSR and elderly-care application agent definitions, prompt docs, RPS-specific model/client code, app replay scripts, and matching app-specific tests from `main`.
- Refactored `AgentDefinitionRegistry` to consume Spring-managed `AgentDefinition` beans, validate and sort them by key, and avoid direct construction or imports of application agents.
- Registered the remaining framework demo definitions as Spring components under `basic.*` and `multimodal.*`.
- Removed SHHD access-code presets from the framework line; `main` now ships with an empty built-in preset catalog.
- Removed app-specific interaction-profile tags/factories while keeping generic observations and modalities such as hand sign, weather, motion hand sign, and display.
- Renamed Valerian hand-sign observation sources from RPS-specific names to Valerian/manual-camera source names.
- Updated README and framework tests to describe bean-based agent registration and the branch/module boundary for application agents.
- Narrowed `RealtimeBrowserClientContractTest` so it still forbids browser-owned response creation while allowing the Valerian Milestone 99 `response.cancel` barge-in behavior.

### How to run
1. Start the framework app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open Valerian Admin:
   - `http://localhost:8080/valerian-admin/`

### How to test
- Targeted checks run:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `.\mvnw.cmd -q "-Dtest=RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest" test`
- Full clean-schema suite run:
  - `.\mvnw.cmd -q "-Dspring.jpa.hibernate.ddl-auto=create-drop" "-Dlogging.level.org=ERROR" "-Dlogging.level.com=ERROR" "-Dlogging.level.ch.zhaw.prometheus=ERROR" "-Dlogging.level.org.hibernate.SQL=ERROR" test`
  - Result from fresh Surefire XML reports: 214 tests, 0 failures, 0 errors.

### Known issues and decisions
- Local `main` is not pushed yet; stop here for review and commit before publishing the cleaned framework line.
- The `agents` branch is the preserved application-agent line. A future extraction can turn that branch into a Maven module or separate repository that contributes `AgentDefinition` beans.
- Framework `main` intentionally has no built-in access-code presets after removing SHHD/TDSR application presets.
- The remaining basic/multimodal demo agents still use their existing Gigi demo persona; this milestone separates application catalogs, not all demo copy.
- A plain `.\mvnw.cmd -q test` initially hit an obsolete local MySQL `agent.start_response_pending` column left from an older schema. Use a clean schema or the `create-drop` override above if the local database still contains removed columns.
- The full clean-schema run exits successfully but still prints the existing Surefire fork-shutdown warning caused by open async/SSE test contexts.

### Next steps
1. Review the `main` cleanup diff and commit it.
2. Push `main` after review.
3. Decide whether the preserved `agents` branch should become a Maven application module, a separate repository, or remain a branch while the framework/application split settles.

## Milestone 101
### Date
2026-07-03

### Goal
Remove the Valerian cockpit's obsolete Realtime VAD create-response control so operators cannot configure a setting that conflicts with PROMETHEUS-owned assistant speech generation.

### What changed
- Removed the `VAD creates` dropdown from the embedded Valerian cockpit advanced speech settings.
- Removed the corresponding browser storage key, settings parsing, and `vadCreateResponse` Realtime call query parameter emission.
- Updated the cockpit static contract test to assert the create-response control and query parameter are absent.
- Kept backend validation that rejects older clients sending `vadCreateResponse=true`, and clarified README wording that browser cockpits do not expose response creation as a user setting.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open Advanced Speech Settings and confirm VAD mode/timing/eagerness and VAD interrupts remain, while VAD create-response is no longer shown.

### How to test
- Focused cockpit/realtime contract suite:
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest,RealtimeControllerWebMvcTest,ScopedDemoControllerWebMvcTest" test`

### Known issues and decisions
- The server-side `vadCreateResponse=true` rejection remains intentionally as a compatibility guard for stale or external clients.
- No runtime Realtime authority semantics changed: PROMETHEUS still sends OpenAI `create_response=false`.

### Next steps
1. Live-test both cockpit surfaces against the current PROMETHEUS backend with continuous speech and barge-in enabled.

## Milestone 102
### Date
2026-07-04

### Goal
Let Valerian cockpit operators expand any of the three cockpit columns into a wider modal viewport without duplicating or resetting the live panel content.

### What changed
- Added icon-only expand controls to the Sensing, Interaction, and Behaviour column headers.
- Added a shared Bootstrap modal that temporarily moves the selected column's existing card into the modal body and restores it to the original column when the modal closes.
- Added per-column placeholders so the three-column layout remains stable while a panel is expanded.
- Kept existing event listeners, media elements, transcript state, accordion/tab state, Realtime/audio controls, and behaviour render targets attached to the same DOM nodes.
- Refreshed the camera overlay sizing after modal open/close so moved visual sensing content can realign to its new viewport.
- Kept the shared expansion modal at body level so Bootstrap backdrops do not cover the modal controls.
- Added a Playwright visual smoke test that runs against the real Spring app, seeds access code `VX102`, expands all three columns, captures modal screenshots, validates wider layout, and verifies restore behavior.
- Updated Valerian static resource contract coverage, Playwright project configuration, and README documentation.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, then use the expand icon in any Sensing, Interaction, or Behaviour column header.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm install`
  - `npx playwright install chromium`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This is a pure cockpit UI change; backend APIs, Realtime orchestration, profile filtering, access-code scoping, and event contracts are unchanged.
- The implementation moves the live DOM node instead of cloning it so existing listeners and state remain intact.
- The Playwright visual test uses the configured local database and admin API; set `PROMETHEUS_ADMIN_TOKEN` when the local admin token differs from `laure`.
- Live camera and Realtime sessions should still be tested on the demo laptop because browser media behavior can vary by device.

### Next steps
1. Live-test expanded Sensing while the camera is running and expanded Interaction while continuous speech is active on the target browser.

## Milestone 103
### Date
2026-07-04

### Goal
Make the Valerian Behaviour column show BehaviourPlan multiplicity and current modality state more visually, including in the maximized Behaviour modal.

### What changed
- Replaced the Behaviour column's raw row list with a visual state board that keeps Speech, Gesture, Face, Gaze, Motion, Display, and latest-event content visible as distinct cards.
- Added active modality chips so operators can quickly see which behaviour channels are present in the latest plan.
- Added a gesture stage with Bootstrap icon rendering for the existing nonverbal gesture vocabulary.
- Added coloured progress-meter visuals for facial intensity, motion energy, and motion stillness, plus sign symbols for agent/user display state.
- Updated the Valerian renderer to reset stale modality state before rendering each new plan, clamp numeric values into 0-100 percent meters, and keep the same visuals when the Behaviour column is moved into the maximized modal.
- Extended Valerian static resource contract coverage and the Playwright visual smoke test to assert the board, icon, active chips, meters, sign visuals, and expanded Behaviour modal rendering.
- Updated README documentation for the new Behaviour board and Playwright visual coverage.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, connect an agent, and inspect the Behaviour column or maximize it with the expand icon.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This is a cockpit UI and renderer change only; backend behaviour-plan contracts, profile filtering, access-code scoping, and Realtime orchestration are unchanged.
- The renderer treats each behaviour plan as the current state snapshot and resets absent modalities to neutral values so stale chips or meters do not remain visible.
- Gesture rendering mirrors the existing nonverbal renderer's vocabulary but uses Bootstrap icons to fit the cockpit tool surface.
- The meters currently cover the numeric fields available in existing plans: face intensity, motion energy, and motion stillness.

### Next steps
1. Live-test the board with framework demo agents that emit sparse and multi-channel behaviour plans.
2. Add additional visual scales if future agents emit more numeric nonverbal fields such as posture openness or prosody intensity.

## Milestone 104
### Date
2026-07-04

### Goal
Add a richer real-time facial emotion sensing report to Valerian so operators can inspect valence, arousal, confidence, expression distribution, and observation emission status directly in the cockpit.

### What changed
- Added a `Facial Emotion Report` panel under `Signals Sensed`, directly after the compact Emotion row and before social grouping readouts.
- Added a valence/arousal affect plane with a live marker whose x-position is valence and y-position is arousal.
- Added numeric readouts and meters for valence, arousal, dominant-emotion confidence, and face-detection confidence.
- Added fixed expression distribution bars for neutral, happy, sad, angry, fearful, disgusted, and surprised scores.
- Updated live camera and manual-emotion rendering to use the same report path, reset stale state when no face is detected or face sensing is disabled, and show whether the current live state was emitted, skipped by threshold/cooldown/stability, or live-only.
- Extended the Valerian static resource contract and Playwright visual smoke test to verify the affect plane, marker position, meters, expression bars, and expanded Sensing modal rendering.
- Updated README documentation for the richer real-time facial emotion report.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, enable Face emotion sensing, start the camera, then open Sensing > Signals Sensed.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This is a Valerian UI-only change; the `obs.emotion.face` payload contract is unchanged and already carries emotion, confidence, valence, arousal, face-detection confidence, and expression scores.
- The affect-plane marker reports live detection state, while the emission status separately describes whether an observation was sent to PROMETHEUS.
- Browser camera detection still depends on target lighting, camera quality, and face-api model behavior.

### Next steps
1. Live-test the report with the target camera and lighting conditions.
2. Consider extending backend prompt adapters or snapshot facts if agents should reason explicitly over arousal as well as emotion and valence.

## Milestone 105
### Date
2026-07-04

### Goal
Rename Valerian's social detector controls to Social context and make the current social sensing state easier to inspect without changing the existing PROMETHEUS observation contracts.

### What changed
- Renamed the Valerian social sensing control language from Social grouping to Social context while keeping the underlying `obs.human.presence` and `obs.social.grouping` event types stable.
- Added a `Social Context Report` panel under `Signals Sensed` with humans, groups, largest group size, singleton count, group/member lists, and tracked-person confidence.
- Updated live camera detections, manual social-context samples, and disabled-sensor reset handling to render through the same report path.
- Extended the Valerian static resource contract and Playwright visual smoke test to verify the social context report in the normal Sensing column and the maximized Sensing modal.
- Updated README documentation for the richer social context readout and visual smoke coverage.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, enable Social context sensing or use Manual Social Context, then open Sensing > Signals Sensed.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This milestone is Valerian UI-only: raw social observations remain `obs.human.presence` and `obs.social.grouping` for backend compatibility.
- Person activity is currently shown as `unknown`; track-derived movement states are planned for Milestone 106.
- The report quality still depends on browser-side COCO-SSD person detection and camera conditions.

### Next steps
1. Milestone 106: derive stationary/moving/approaching/receding movement states from the existing person tracker.
2. Milestone 107: add the first cheap attentiveness heuristic using person visibility, face visibility, near-frontal/centered cues, and confidence.

## Milestone 106
### Date
2026-07-04

### Goal
Derive first-pass client-side movement activities for tracked people in Valerian's Social Context Report without adding GPT calls or changing the raw social observation contracts.

### What changed
- Added tracker-level movement heuristics based on consecutive person box center displacement and bounding-box area change.
- Classified tracked people as `stationary`, `moving`, `approaching`, `receding`, or `unknown` once a track has enough history.
- Added movement confidence and colored activity tokens to the Social Context Report's tracked-person rows.
- Kept new tracks as `unknown` until a second detection frame exists, avoiding false movement labels on first sighting.
- Extended the Valerian static resource contract and Playwright visual smoke test to verify movement-state rendering and the in-browser `updateTracks` movement heuristic.
- Updated README documentation for movement states in the social context report.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, enable Social context sensing, start the camera, and inspect Sensing > Signals Sensed > Social Context Report.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This remains browser-only sensing and UI rendering. The emitted `obs.human.presence` and `obs.social.grouping` payload shapes are unchanged pending a separate contract-hardening decision.
- Movement labels are cheap heuristics, not semantic action recognition. Camera jitter, partial boxes, occlusion, and detector box scale changes can produce noisy labels.
- Approaching/receding is inferred from bounding-box area changes, so lateral movement toward the image edge can be misread under perspective distortion.

### Next steps
1. Milestone 107: add a first cheap attentiveness signal using person visibility, face visibility, near-frontal/centered cues, and confidence.
2. Decide in Milestone 108 how these richer client-side social context signals should be emitted as stable PROMETHEUS observation payloads.

## Milestone 107
### Date
2026-07-04

### Goal
Add a first cheap attentiveness signal to Valerian's Social Context Report using only browser-side person detection geometry and confidence, without GPT calls or social observation payload changes.

### What changed
- Added a per-person attention heuristic derived from person visibility, likely face-region visibility, upright/frontal geometry, center alignment, box scale, and detector confidence.
- Classified tracked people as `attending`, `not_attending`, or `unknown` with a displayed attention confidence.
- Added Social Context Report tokens for attention state, attention confidence, person visibility, likely face visibility, and centered/frontal geometry.
- Kept manual social-context samples explicit as unknown attention while still marking the manually declared person as visible.
- Extended Valerian static resource coverage and the Playwright visual smoke test to verify attentiveness rendering and the in-browser tracker-derived attention result.
- Updated README documentation for the attentiveness signal.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, enable Social context sensing, start the camera, and inspect Sensing > Signals Sensed > Social Context Report.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This is still a cheap client-side heuristic. `faceVisible` means likely face-region visibility inferred from a person box, not a dedicated per-person face detector.
- The emitted `obs.human.presence` and `obs.social.grouping` payload shapes remain unchanged. A later contract decision is still needed before these richer social context signals are sent as stable PROMETHEUS observation fields.
- Centered/frontal geometry is a proxy for attentiveness toward the camera or embodied agent. It can be wrong when the camera is off-axis relative to the robot, when people stand sideways near the center, or when groups overlap.

### Next steps
1. Decide how to evolve the social observation payload contract for humans, groups, movement, and attentiveness.
2. Consider using a lightweight pose or face-landmark model only if the geometric heuristic is too noisy in target deployment conditions.

## Milestone 108
### Date
2026-07-04

### Goal
Promote Valerian's richer social context sensing into a stable optional PROMETHEUS observation contract while preserving the existing `obs.human.presence` and `obs.social.grouping` flows.

### What changed
- Added `obs.social.context` as a first-class event/profile constant.
- Added `obs.social.context` to common visual input interaction profiles so multimodal visual agents can declare the richer social signal.
- Added a prompt adapter that summarizes the social context payload for LLM prompts without exposing camera boxes or frames.
- Updated Valerian profile gating so social controls are visible for agents declaring either the existing social observations or `obs.social.context`.
- Updated Valerian social emission to build a schema-versioned context payload with counts, group member IDs, movement state/confidence, and attention state/confidence/cues.
- Kept old presence/grouping payload shapes unchanged and independently deduplicated them from the richer context event.
- Emitted `obs.social.context` only when the active profile declares it or when the cockpit is in fallback-all mode for unprofiled agents.
- Extended Java profile/prompt tests, Valerian static contract tests, and the Playwright visual smoke test to cover the new contract.
- Updated multimodal seed prompt text that enumerates supported visual observation event types.
- Updated README observation documentation.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Use an agent profile that declares `obs.social.context`, enable Social context sensing, and inspect Sensing > Signals Sensed > Social Context Report.

### How to test
- Focused checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `.\mvnw.cmd -q "-Dtest=AgentInteractionProfileUnitTest,PromptEventContentAdapterUnitTest,ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- Existing social situation-change computation still listens to `obs.social.grouping`, not `obs.social.context`, to avoid changing current agent behavior.
- `obs.social.context` is profile-gated in Valerian. Existing agents that declare only grouping continue receiving only the old social event pair.
- The attention and movement values are still browser-side heuristics and should be treated as uncertain sensing facts, not ground truth.

### Next steps
1. Decide which production agents should declare `obs.social.context` and update their prompts/replay tests accordingly.
2. Live-test the new contract with the target camera and embodied-agent placement.

## Milestone 109
### Date
2026-07-04

### Goal
Complete the Valerian Sensing column coverage so manual inputs and Signals Sensed reports reflect the richer sensing vocabulary added during the recent cockpit milestones.

### What changed
- Added a manual `Disgusted` emotion shortcut so the manual emotion controls match the detector/report expression vocabulary.
- Replaced coarse-only manual social context emission with a detail editor for people count, group sizes, per-person movement state/confidence, attentiveness state/confidence, and attention cues while keeping the existing quick scenario buttons.
- Kept manual social context on the existing `obs.human.presence`, `obs.social.grouping`, and optional `obs.social.context` event path; no backend event contract changed.
- Added a Hand Sign Report under Signals Sensed with sign visual, label, confidence meter, source, detection mode, MediaPipe canned gesture, stability frames, and emission/live status.
- Added a Weather Report under Signals Sensed with location, condition, temperature, precipitation/intensity, wind, light state, and a compact three-day forecast strip.
- Extended Valerian static resource contract coverage and the Playwright visual smoke test for the new sensing controls and report panels.
- Updated README documentation for the completed sensing column coverage.

### How to run
1. Start the main branch app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, open Sensing, and inspect Manual Emotion, Manual Social Context, Weather, and Signals Sensed.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `git diff --check`
  - `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- `obs.social.situation_change` remains out of Signals Sensed because it is a backend-computed event, not a client-sensed signal.
- `obs.user_utterance` remains in the Interaction/Realtime speech UI rather than Signals Sensed.
- Weather remains manually fetched/sent context, not a continuous detector.
- Manual social context details are intended for rehearsal and debugging; camera-derived movement and attention remain heuristic and should not be treated as ground truth.

### Next steps
1. Live-test the full Sensing column with target camera, hand gestures, and representative weather locations.
2. Consider whether specific production agents should declare and use `obs.social.context` now that the cockpit can emit and display the richer signal consistently.

## Milestone 110
### Date
2026-07-05

### Goal
Bring Valerian's facial emotion camera-loop diagnostics from the agents branch back to main without importing application agent definitions.

### What changed
- Made face-api/model load failures visible in the Facial Emotion Report via `Model unavailable` / `Model load failed` states.
- Preserved live-preview behavior: camera detections update the report when `Emit camera observations` is off, while emitted observations keep the existing `obs.emotion.face` contract.
- Added a stable `camera-status` test id for browser checks.
- Extended Playwright with a mocked browser camera and mocked face-api result that exercises `startCamera()`, model loading, `runCameraLoop()`, `detectEmotion()`, and the shared report renderer.

### How to run
1. Start the app:
   - `./mvnw spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, connect an agent/profile that declares `obs.emotion.face`, enable `Face emotion`, start the camera, and inspect Sensing > Signals Sensed > Facial Emotion Report.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `./mvnw -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- This is a Valerian cockpit client change only; backend observation contracts, event processing, and agent definitions are unchanged.
- The deterministic Playwright test proves the Valerian browser wiring when face-api returns a face expression result; it does not prove physical webcam, lighting, or real model behavior.

### Next steps
1. Continue cherry-picking the remaining Valerian overlay/runtime compatibility fixes from agents.

## Milestone 111
### Date
2026-07-05

### Goal
Make Valerian's facial emotion detector visibly diagnosable in the shared camera preview so users can see whether face detection is active before relying on emitted observations.

### What changed
- Drew a labelled face box in the shared camera preview when the facial emotion detector finds a face.
- Added explicit preview overlay states for `No face` and face detection errors.
- Routed face detector exceptions into the Facial Emotion Report without disabling unrelated visual detectors.
- Extended the Playwright visual checks to verify the face overlay and the no-face diagnostic path.
- Updated README documentation for the visible face-detection overlay.

### How to run
1. Start the app:
   - `./mvnw spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, enable `Face emotion`, start the camera, and inspect both the camera preview overlay and Sensing > Signals Sensed > Facial Emotion Report.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `./mvnw -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- The overlay is a client-side diagnostic only. It does not change `obs.emotion.face` payloads or backend processing.
- The preview now distinguishes no face from detector/model failure so live testing can separate camera positioning from runtime problems.

### Next steps
1. Continue cherry-picking the shared visual detector runtime compatibility fix from agents.

## Milestone 112
### Date
2026-07-05

### Goal
Stabilize Valerian's shared visual detector runtime on main so face emotion, social context, and hand-sign sensing can be started across agent reconnects without face-api/COCO TensorFlow.js conflicts.

### What changed
- Pinned the Valerian visual stack to a TFJS runtime version compatible with both `face-api.js@0.22.2` and `coco-ssd@2.2.3`.
- Kept all visual detector scripts loaded in a deterministic order before the cockpit script initializes.
- Added explicit Social Context Report status/error rows when the people detector model is unavailable or detection fails.
- Added camera-preview social context diagnostics for model load and detection errors while keeping face and hand detectors isolated from those failures.
- Added a Playwright regression that simulates the prior face-api runtime error and verifies social context sensing still reports people.
- Updated README documentation for the shared detector runtime dependency and visual diagnostics.

### How to run
1. Start the app:
   - `./mvnw spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enable Face emotion and Social context in different combinations, start the camera, and inspect both the camera preview overlays and Sensing > Signals Sensed reports.

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
  - `./mvnw -q "-Dtest=ValerianClientStaticResourceContractTest" test`
  - `npm run test:valerian:visual`

### Known issues and decisions
- The compatibility fix is client-only; backend event contracts and agent definitions are unchanged.
- This keeps the existing browser-side model set instead of replacing face/social detection with GPT calls.
- Real webcam accuracy still depends on lighting, camera placement, and model limitations.

### Next steps
1. Live-test detector toggling across multiple agent connect/disconnect cycles on the target browser and camera.
2. Revisit the model stack only if future detector additions require a newer shared TFJS runtime.

## Milestone 113
### Date
2026-07-08

### Goal
Compact the Valerian cockpit and admin header areas by removing the large page-title row and using the existing subtitle style for the page labels.

### What changed
- Removed the large legacy page-title elements from the logged-in Valerian cockpit and admin shells.
- Renamed the visible and browser titles to `Valerian Cockpit` and `Valerian Access Management`.
- Kept the existing `.page-subtitle` styling for the remaining compact header labels.
- Stopped the Valerian cockpit script from overwriting the compact header label with the connected agent name; agent metadata remains visible in the Agent drawer.
- Updated static resource contract tests and README client naming.

### How to run
1. Start the app:
   - `./mvnw spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open Valerian Access Management:
   - `http://localhost:8080/valerian-admin/`

### How to test
- Focused cockpit checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `./mvnw -q "-Dtest=ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest" test`

### Known issues and decisions
- This is a static Valerian UI change only; backend routes, access-code behavior, agent metadata, and observation contracts are unchanged.
- The connected agent name is no longer mirrored in the top cockpit header because that header line is now the compact page label.

### Next steps
1. Visually smoke-test both Valerian surfaces in the target browser after the current main-branch merge work settles.

## Milestone 114
### Date
2026-07-08

### Goal
Replace the remaining main-branch `basic.*` and `multimodal.*` framework demo agents with the Valerian baseline catalog: reusable core capability demos and English healthcare use-case demos.

### What changed
- Removed the old `agentdefs/basic` and `agentdefs/multimodal` production definitions, manual seed wrappers, and their deleted-catalog replay scripts/tests.
- Added `src/main/java/ch/zhaw/prometheus/agentdefs/core` with five Valerian Core definitions:
  - `core.facial_expression_sensitivity`
  - `core.multimodal_behaviour`
  - `core.rock_scissor_paper`
  - `core.role_clarification_guessing_game`
  - `core.social_context_sensitivity`
- Added `src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare` with six English Valerian healthcare use-case definitions:
  - `usecases.healthcare.guessing_game`
  - `usecases.healthcare.guessing_game_user_guess`
  - `usecases.healthcare.healthcare_conversation`
  - `usecases.healthcare.smart_goal_coaching`
  - `usecases.healthcare.therapy_appointment_reminder`
  - `usecases.healthcare.therapy_appointment_reminder_intro`
- Adapted the imported lab and Davos/event prompts to the Valerian digital-agent persona:
  - Valerian is a digital agent manifestation from the ZHAW SIRA Lab.
  - PROMETHEUS is described as a digital agent development framework for rapid prototyping and experimental validation of multimodal digital agents.
  - GIGI, TDSR, robot, Davos, hotel, and German-only references were removed from the new main-branch agent packages.
- Restored the RPS model package required by the Valerian Core rock-scissor-paper agent.
- Updated registry, interaction-profile, access-code, scoped-demo, and prompt contract tests for the new catalog.
- Updated README registered-agent documentation and API examples to use the new `core.*` and `usecases.healthcare.*` keys.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Access Management:
   - `http://localhost:8080/valerian-admin/`
3. Create an access code and assign one or more `core.*` or `usecases.healthcare.*` agent types.
4. Open Valerian Cockpit:
   - `http://localhost:8080/valerian/`
5. Enter the access code and create one of the assigned Valerian agents.

### How to test
- Focused backend checks:
  - `.\mvnw.cmd -q -DskipTests compile`
  - `.\mvnw.cmd -q -DskipTests test-compile`
  - `.\mvnw.cmd -q "-Dtest=AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,ValerianCorePromptContractTest,HealthcareUseCasePromptContractTest,AccessCodeAdminServiceIntegrationTest,ScopedDemoControllerIntegrationTest,AdminAccessCodeControllerWebMvcTest,RpsRulesUnitTest,DeterministicRpsSignSelectorUnitTest,RpsRevealPolicyContractTest" test`

### Known issues and decisions
- This intentionally removes the old main-branch `basic.*` and `multimodal.*` public keys. Existing local database access-code assignments that reference those keys need to be recreated with the new keys.
- The healthcare package deliberately has no Davos/package/key naming, even where the source material came from the former Davos event agents.
- Historical milestones in this file still describe earlier GIGI/TDSR and split-branch work; they are retained as project history.

### Next steps
1. Review and commit this catalog replacement on `main`.
2. Merge `main` back into the `agents` branch so the old main-branch `basic.*` and `multimodal.*` definitions disappear there as intended.

## Milestone 115
### Date
2026-07-08

### Goal
Rewrite `README.md` as a GitHub-facing current-state project entry point with a why/what/how introduction focused on multimodal sensing, multimodal behaviour, and digital-agent mapping, while documenting the API contracts external clients need.

### What changed
- Replaced the historical README narrative with a current-state overview of PROMETHEUS, Valerian, the main Valerian agent catalog, setup, testing, and repository structure.
- Reframed the introduction around why turn-based chat is too narrow, what PROMETHEUS provides for multimodal digital agents, and how events, interaction profiles, state machines, and behaviour plans map sensing to behaviour.
- Replaced older `.readme` client screenshots in the README with the new Valerian screenshots under `.doc/figures/Valerian`.
- Added a practical external-client guide for scoped demo sessions, agent creation, `AgentInteractionProfile` discovery, perception event publication, behaviour SSE subscription, behaviour generation, monitor streams, Realtime speech, admin access-code management, and CORS.
- Documented the current main-branch agent catalog only, removing descriptions of obsolete `basic.*` and `multimodal.*` agents and older historical client flows.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian Access Management:
   - `http://localhost:8080/valerian-admin/`
3. Open Valerian Cockpit:
   - `http://localhost:8080/valerian/`

### How to test
- Markdown/link sanity:
  - inspect `README.md`
- Static/client checks:
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
- Documentation-related backend contract checks:
  - `.\mvnw.cmd -q "-Dtest=AgentClientCompatibilityWebMvcTest,ScopedDemoControllerIntegrationTest,AdminAccessCodeControllerWebMvcTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest,AgentDefinitionRegistryUnitTest" test`

### Known issues and decisions
- The new README intentionally describes the current main branch only and leaves historical implementation detail to `PROJECT.md`.
- The screenshot files are expected to be committed together with this README update so GitHub can render them.
- The API guide documents current public request/response shapes but is not an OpenAPI specification.

### Next steps
1. Review the README wording and screenshot ordering in GitHub's Markdown preview.
2. Consider adding an OpenAPI description if external-client integration becomes a primary release target.

## Milestone 116
### Date
2026-07-08

### Goal
Clean up `.agents/CONTEXT.md` so it optimally informs Codex agents working on the current PROMETHEUS repository, then publish the accumulated main-branch documentation/assets and merge them back into the `agents` branch.

### What changed
- Replaced the old long-form architectural requirements document in `.agents/CONTEXT.md` with a concise current-state Codex context guide.
- Focused the context guide on the present PROMETHEUS mental model: event-driven inputs, explicit state-machine control, `AgentInteractionProfile`, `BehaviourPlan`, Valerian boundaries, public API contracts, main-branch agent catalog boundaries, and high-value test anchors.
- Removed obsolete or overly broad scenario/acceptance material that is no longer the best first context for a coding agent; historical detail remains in `PROJECT.md`.
- Prepared the repository state for publication, including the README cleanup, new Valerian screenshots, and the pre-existing audio-tuning documentation archive move.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Open Valerian Access Management:
   - `http://localhost:8080/valerian-admin/`

### How to test
- Documentation/static checks:
  - `git diff --check`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
- Focused README/API/UI contract checks:
  - `.\mvnw.cmd -q "-Dtest=AgentClientCompatibilityWebMvcTest,ScopedDemoControllerIntegrationTest,AdminAccessCodeControllerWebMvcTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest,AgentDefinitionRegistryUnitTest" test`

### Known issues and decisions
- This milestone changes repository documentation and committed assets only; no runtime Java or Valerian client behaviour is intended to change.
- `.agents/CONTEXT.md` is intentionally not a full product requirements document anymore; it is a focused orientation document for coding agents.
- The audio-tuning archive move was already present in the workspace and is included because this milestone explicitly publishes all current main-branch changes.

### Next steps
1. Commit and push `main`.
2. Merge `main` into `agents`, resolve documentation conflicts if any, then push `agents`.

## Milestone 117
### Date
2026-07-08

### Goal
Add the first Valerian detached-window foundation so students can open Sensing, Interaction, or Behaviour from a connected cockpit into separate browser windows while keeping the normal three-column cockpit intact.

### What changed
- Added detached-window toolbar buttons for the Sensing, Interaction, and Behaviour columns.
- Added Valerian detached mode at `/valerian/?mode=detached&panel=<sensing|interaction|behaviour>&agentId=<uuid>`.
- Detached windows inherit the opener's access-code session, reconnect to the same agent through the scoped demo API, update the page subtitle/title, and show only the requested Valerian column.
- Kept the existing in-page column maximization modal unchanged.
- Added static contract coverage for detached controls, query-mode handling, and detached CSS.
- Extended the Playwright Valerian smoke with a deterministic connected-agent detached-window test while preserving the existing database-backed visual smoke coverage.
- Left `README.md` unchanged so screenshots and user-facing docs can be updated after the UI pass is ready.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Enter an access code, create or select an agent, connect it, then use the new window buttons in a column header.

### How to test
- `node --check src/main/resources/public/valerian/script.js`
- `node --check tests/playwright/valerian-column-expansion.spec.mjs`
- `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
- `npm run test:valerian:visual`

### Known issues and decisions
- Detached windows are intentionally opened from the connected cockpit in this milestone; standalone detached-window agent selection is left for a later pass.
- Hardware ownership is not coordinated yet. Camera and microphone controls can still be active in more than one window; this is the explicit scope of the next milestone.
- Detached windows rely on the browser's same-origin session-storage copy from `window.open`, with existing access-code entry as the fallback if opened directly.

### Next steps
1. Add cross-window ownership coordination for camera and microphone controls.

## Milestone 118
### Date
2026-07-08

### Goal
Prevent conflicting camera and microphone use across Valerian cockpit and detached windows, and re-enable cockpit controls when the owning window closes.

### What changed
- Added browser-side cross-window ownership for camera and microphone resources using same-origin `BroadcastChannel` messages plus `localStorage` heartbeat records.
- Camera start now claims camera ownership; stop, page close, and cleanup release it.
- Realtime speech start now claims microphone ownership; stop, page close, and cleanup release it.
- Cockpit and detached windows disable camera controls, detector toggles, and camera configuration while another Valerian window owns the camera.
- Cockpit and detached windows disable realtime start and speech-session controls while another Valerian window owns the microphone.
- Added visible `Camera In Use` and `Mic In Use` statuses while a resource is owned by another window.
- Added a stale-owner TTL fallback so controls recover if a window closes without delivering a clean release event.
- Extended static client contract coverage and Playwright multi-window coverage for claim, disable, close, and re-enable behaviour.
- Left `README.md` unchanged for the later screenshot/documentation pass.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/valerian/`
3. Connect an agent, open Sensing or Interaction in a separate window, start or own the camera/microphone there, and observe the corresponding cockpit controls deactivate until the detached window closes or releases the resource.

### How to test
- `git diff --check`
- `node --check src/main/resources/public/valerian/script.js`
- `node --check tests/playwright/valerian-column-expansion.spec.mjs`
- `.\mvnw.cmd -q "-Dtest=ValerianClientStaticResourceContractTest" test`
- `npm run test:valerian:visual`

### Known issues and decisions
- Ownership is intentionally browser-local and same-origin. It coordinates Valerian windows in one browser profile, not different browsers or machines.
- Ownership is global per resource, not per agent, because camera and microphone hardware cannot be safely shared across different agent windows either.
- The TTL fallback recovers stale ownership after a short delay; normal window close releases immediately.

### Next steps
1. Compare detached Valerian windows against the old special-purpose clients and decide which legacy clients should redirect, be removed, or remain separate.

## Milestone 119
### Date
2026-07-08

### Goal
Remove the replaceable legacy browser clients now covered by the Valerian cockpit and detached Valerian windows, while keeping the multilateral meeting displays separate.

### What changed
- Removed the old root text-interaction client:
  - `src/main/resources/public/index.html`
  - `src/main/resources/public/script.js`
- Removed the old special-purpose browser clients:
  - `src/main/resources/public/monitor`
  - `src/main/resources/public/realtime`
  - `src/main/resources/public/nonverbal`
  - `src/main/resources/public/visual`
- Kept the current shared static surfaces:
  - `src/main/resources/public/valerian`
  - `src/main/resources/public/valerian-admin`
  - `src/main/resources/public/multilateral`
  - shared `src/main/resources/public/style.css`
- Removed static redirects for `/monitor`, `/realtime`, `/nonverbal`, `/visual/facial`, `/visual/multifacial`, `/visual/social`, and `/visual/nonverbal`.
- Added `/` as the canonical redirect to `/valerian/index.html`, preserving query parameters.
- Updated realtime browser contract coverage so it validates the remaining Valerian speech client and the multilateral listener instead of the removed `/public/realtime` client.
- Added contract coverage that the legacy client assets are absent and the current Valerian/Admin/Multilateral static surfaces remain present.
- Left `README.md` unchanged for the later screenshot and user-facing documentation pass.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open Valerian:
   - `http://localhost:8080/`
   - `http://localhost:8080/valerian/`
3. Open Valerian Access Management:
   - `http://localhost:8080/valerian-admin/`
4. Open the multilateral screens when needed:
   - `http://localhost:8080/multilateral/listen/`
   - `http://localhost:8080/multilateral/reports/`

### How to test
- Static/client checks:
  - `git diff --check`
  - `node --check src/main/resources/public/valerian/script.js`
  - `node --check src/main/resources/public/valerian-admin/script.js`
  - `node --check src/main/resources/public/multilateral/listen/script.js`
  - `node --check src/main/resources/public/multilateral/reports/script.js`
  - `node --check tests/playwright/valerian-column-expansion.spec.mjs`
- Focused Java contract checks:
  - `.\mvnw.cmd -q clean "-Dtest=StaticRedirectControllerWebMvcTest,LegacyStaticClientRemovalContractTest,RealtimeBrowserClientContractTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest" test`
- Valerian visual smoke:
  - `npm run test:valerian:visual`

### Known issues and decisions
- Compatibility redirects for the removed legacy clients were intentionally not kept. They now return 404 rather than silently opening Valerian.
- The backend realtime, monitor, acknowledgement, behaviour-stream, and multilateral APIs remain available; only the old browser clients were removed.
- The root path is now a current entry point for Valerian, not a compatibility layer for the deleted text client.
- README updates are deferred until the Valerian UI cleanup/screenshot pass is ready.

### Next steps
1. Use the remaining Valerian and multilateral surfaces for the next README screenshot/documentation pass.

## Milestone 120
### Date
2026-07-08

### Goal
Add the first static PROMETHEUS API Workbench client so developers can discover the current REST/SSE contracts through guided lifecycle steps and ready-to-copy endpoint templates.

### What changed
- Added a new self-contained static client under `src/main/resources/public/apiworkbench/`:
  - `index.html`
  - `script.js`
  - `workbench.css`
- Added a Valerian-style developer workbench layout with:
  - session variables for base URL, access code, agent id, agent definition key, and admin token
  - guided scoped-demo lifecycle steps
  - endpoint catalog filtering by text and group
  - selected endpoint detail view
  - path variable, header, query, and body template rendering
  - resolved URL display
  - ready-to-copy `fetch`, `curl`, and `EventSource` snippets
  - placeholder HTTP and SSE response panes for the next live-execution milestone
- Added endpoint templates for current client-developer contracts:
  - scoped demo lifecycle, observation, behaviour, stream, prompt, and Realtime endpoints
  - admin access-code endpoints
  - trusted global agent endpoint equivalents
- Kept the first milestone static/template-only. It does not execute HTTP calls or open SSE connections yet.
- Added static resource contract coverage for the workbench and updated the static-client removal contract so `apiworkbench` is an intentional current client surface.
- Added a Playwright smoke test for endpoint filtering, lifecycle selection, snippet generation, and mobile viewport visibility.
- Added `npm run test:apiworkbench:visual`.
- Left `README.md` unchanged until the API Workbench can execute live requests and provide meaningful screenshots.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open the API Workbench:
   - `http://localhost:8080/apiworkbench/index.html`

### How to test
- Static/client checks:
  - `git diff --check`
  - `node --check src/main/resources/public/apiworkbench/script.js`
  - `node --check tests/playwright/apiworkbench.spec.mjs`
- Focused Java contract checks:
  - `.\mvnw.cmd -q "-Dtest=ApiWorkbenchStaticResourceContractTest,LegacyStaticClientRemovalContractTest" test`
- Playwright smoke:
  - `npm run test:apiworkbench:visual`

### Known issues and decisions
- The first workbench milestone intentionally does not send live HTTP requests or subscribe to SSE streams. That is the next milestone.
- The workbench is available at `/apiworkbench/index.html`. No backend redirect for `/apiworkbench` was added because this milestone keeps runtime changes inside the new static client.
- Endpoint metadata is currently a static JavaScript catalog so the client can evolve without backend API changes. A backend-provided OpenAPI or metadata endpoint can be considered later.
- README updates are deferred until the workbench has live request/SSE execution and final screenshots.

### Next steps
1. Add guided scoped-demo lifecycle execution: open session, list agent definitions, create/select an agent, inspect `AgentInteractionProfile`, and display HTTP responses.
2. Add behaviour and monitor SSE subscriptions plus event publishing from observation templates.

## Milestone 121
### Date
2026-07-08

### Goal
Make the API Workbench execute non-streaming scoped lifecycle HTTP requests and use successful responses to keep the developer session context synchronized.

### What changed
- Added a `Send` command to the API Workbench request panel for non-SSE endpoints.
- Added request execution against the selected endpoint template using the same resolved URL, headers, query, and body shown in the workbench.
- Added preflight validation for unresolved path/body variables before sending.
- Added JSON-body validation before sending JSON requests.
- Added HTTP response rendering with status, response headers, and parsed JSON/text body.
- Added a request status indicator.
- Added response extraction for scoped lifecycle calls:
  - `POST /demo/session` updates access code, first agent type, and first visible agent when present.
  - `GET /demo/agent-types` updates the agent definition key from the first returned type.
  - `GET /demo/agents` updates the selected agent id from the first returned agent.
  - `POST /demo/agents` and `GET /demo/agents/{agentId}/info` update the selected agent id and render the `AgentInteractionProfile`.
- Added a dedicated profile preview pane for supported observations, behaviour modalities, tags, language, and agent identity.
- Kept SSE endpoints as prepared templates only; live stream connection is left for the next milestone.
- Extended static and Playwright coverage for live request execution, response extraction, and missing-variable failure handling.
- Left `README.md` unchanged until the workbench has live SSE/event publishing and final screenshots.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open the API Workbench:
   - `http://localhost:8080/apiworkbench/index.html`
3. Enter an access code, select a lifecycle step, and use `Send` on non-streaming endpoints.

### How to test
- Static/client checks:
  - `git diff --check`
  - `node --check src/main/resources/public/apiworkbench/script.js`
  - `node --check tests/playwright/apiworkbench.spec.mjs`
- Focused Java contract checks:
  - `.\mvnw.cmd -q "-Dtest=ApiWorkbenchStaticResourceContractTest,LegacyStaticClientRemovalContractTest" test`
- Playwright smoke:
  - `npm run test:apiworkbench:visual`

### Known issues and decisions
- SSE endpoints remain non-executing stream templates in this milestone. The `Send` command is disabled for those endpoints.
- The workbench updates context from the first returned agent type or agent instance when a response contains lists. More explicit selection controls can be added if the response lists become too large for the guided flow.
- The Playwright lifecycle test uses mocked API responses so it remains deterministic and independent from the configured database state.
- README updates remain deferred until the workbench has complete request, event, and stream support.

### Next steps
1. Add live behaviour and monitor SSE subscriptions.
2. Add observation-template helpers for publishing compatible perception and interaction events.

## Milestone 122
### Date
2026-07-08

### Goal
Complete the first practical API Workbench loop for client developers by adding live SSE subscriptions and profile-aware observation publishing helpers.

### What changed
- Added live `EventSource` connection handling for SSE endpoint templates.
- The primary request command now switches between:
  - `Send` for non-streaming HTTP endpoints
  - `Connect` / `Disconnect` for SSE endpoints
- Added SSE event capture for `open`, `message`, `behaviour`, `snapshot`, `heartbeat`, `error`, connect, and disconnect events.
- Added a bounded SSE log in the response pane with endpoint id, event type, timestamp, and parsed JSON/text payload.
- Added automatic stream cleanup on page unload.
- Added observation body templates for:
  - `obs.user_utterance`
  - `obs.emotion.face`
  - `obs.hand.sign`
  - `obs.social.context`
  - `obs.weather.current`
- Added an event-template selector for acknowledge endpoints.
- Made event templates profile-aware after an agent info response loads `AgentInteractionProfile`; supported observation types are preferred when templates are shown.
- Kept the scoped lifecycle, response extraction, and snippet generation behaviour from Milestone 121.
- Extended static contract coverage for EventSource and observation-template wiring.
- Extended Playwright coverage for:
  - mocked SSE stream event capture
  - profile-compatible hand-sign observation template selection
  - acknowledge request body validation for the generated observation event

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open the API Workbench:
   - `http://localhost:8080/apiworkbench/index.html`
3. Use the scoped lifecycle to create/select an agent, connect a behaviour or monitor stream, then publish an observation through the acknowledge endpoint.

### How to test
- Static/client checks:
  - `git diff --check`
  - `node --check src/main/resources/public/apiworkbench/script.js`
  - `node --check tests/playwright/apiworkbench.spec.mjs`
- Focused Java contract checks:
  - `.\mvnw.cmd -q "-Dtest=ApiWorkbenchStaticResourceContractTest,LegacyStaticClientRemovalContractTest" test`
- Playwright smoke:
  - `npm run test:apiworkbench:visual`

### Known issues and decisions
- The workbench still uses a static endpoint and observation-template catalog. This keeps the milestone self-contained and avoids backend metadata changes.
- SSE subscriptions are browser-local and close on page unload. Multiple stream endpoints can be connected from the same page, but the UI is intentionally compact rather than a full stream-management console.
- Observation templates are starter payloads for developers. They demonstrate correct `EventRequest` structure and JSON-string payload handling, not every possible observation shape.
- README updates remain deferred until the final screenshot/documentation pass for the new UI set.

### Next steps
1. Add optional request history and clearer copied-code/export affordances if developers need repeatable test sessions.
2. Add `/apiworkbench` redirect only if the shorter URL is worth a backend route change.
3. Update README with the API Workbench once screenshots are ready.

## Milestone 123
### Date
2026-07-08

### Goal
Update the README client documentation with URL-specific guidance for the current Valerian UI set and the new API Workbench.

### What changed
- Replaced the old bare screenshot stack with a `Bundled Clients` section.
- Added URL and usage guidance for Valerian Access Management.
- Added URL and workflow guidance for Valerian Cockpit:
  - access-code entry
  - heartbeat drawer agent selection/creation/connection/reset/deletion
  - diagnostics tab for events and agent state
  - three-column sensing/interaction/behaviour layout
  - maximised and separate-window column modes
- Added focused descriptions for the shipped social-context, facial-expression, and rock-scissor-paper core agents.
- Generated a Playwright screenshot for the API Workbench at `.doc/figures/Valerian/api-workbench.png`.
- Added API Workbench URL, lifecycle guidance, endpoint-catalog guidance, and snippet/HTTP/SSE/profile viewer guidance.
- Added the API Workbench to the README local surface list, JavaScript checks, Playwright smoke checks, and repository structure summary.

### How to run
1. Start the app:
   - `.\mvnw.cmd spring-boot:run`
2. Open the documented clients:
   - `http://localhost:8080/valerian-admin/`
   - `http://localhost:8080/valerian/`
   - `http://localhost:8080/apiworkbench/index.html`

### How to test
- Documentation/static checks:
  - `git diff --check`
  - `node --check src/main/resources/public/apiworkbench/script.js`
  - `node --check tests/playwright/apiworkbench.spec.mjs`
- Focused Java contract checks:
  - `.\mvnw.cmd -q "-Dtest=ApiWorkbenchStaticResourceContractTest,ValerianClientStaticResourceContractTest,ValerianAdminClientStaticResourceContractTest" test`
- Playwright smoke:
  - `npm run test:apiworkbench:visual`

### Known issues and decisions
- The API Workbench remains documented at `/apiworkbench/index.html`; no `/apiworkbench` redirect has been added.
- The README now describes only the current bundled clients: Valerian Access Management, Valerian Cockpit, API Workbench, and the externally documented API contract.

### Next steps
1. Take updated Valerian Cockpit screenshots again if the column-detach UI changes visually.
2. Add request history/export documentation if the API Workbench grows those features.

## Milestone 124
### Date
2026-07-08

### Goal
Finish the README client-surface pass and make the API Workbench response viewer span the full desktop width.

### What changed
- Added the bundled multilateral listener and reports displays to the README.
- Documented that `http://localhost:8080` redirects to Valerian Cockpit.
- Added `multilateral/` to the README repository structure.
- Changed the API Workbench desktop grid so the response viewer spans the full row instead of stopping after the lifecycle and endpoint columns.
- Added a Playwright regression assertion that the response viewer spans the full desktop grid width.
- Added missing Milestone 123 and 124 entries to the checklist.

### How to test
- `git diff --check`
- `node --check src/main/resources/public/apiworkbench/script.js`
- `node --check tests/playwright/apiworkbench.spec.mjs`
- `npm run test:apiworkbench:visual`

### Known issues and decisions
- The API Workbench response viewer now sits below the full top row on desktop, which is the intended tradeoff for full-width response visibility.
- The README still documents the API Workbench at `/apiworkbench/index.html`; no short redirect route has been added.

### Next steps
1. Merge this main-branch documentation and API Workbench layout polish into `agents`.

## Milestone 125
### Date
2026-07-08

### Goal
Add a short `/apiworkbench` entry point so deployed users do not need to type `/apiworkbench/index.html`.

### What changed
- Added `StaticRedirectController` mappings for `/apiworkbench` and `/apiworkbench/`.
- Preserved query strings when redirecting to `/apiworkbench/index.html`.
- Added MVC redirect coverage for both short API Workbench routes.
- Updated README client URLs to advertise `http://localhost:8080/apiworkbench/`.

### How to test
- `git diff --check`
- `.\mvnw.cmd -q "-Dtest=StaticRedirectControllerWebMvcTest,ApiWorkbenchStaticResourceContractTest" test`

### Known issues and decisions
- The redirect keeps the implementation as a static client under `/apiworkbench/index.html`; the short route is only an entry-point convenience.

### Next steps
1. Merge this main-branch redirect into `agents` for deployed agents-branch builds.

## Milestone 126
### Date
2026-07-09

### Goal
Create a standalone German/English public web page introducing the ZHAW SIRA Lab and positioning PROMETHEUS as the agent development framework for SIRA experiments.

### What changed
- Added `.web/index.html` and `.web/texts.js` as a static page that can be hosted directly.
- Wrote German and English introductory copy for:
  - ZHAW Socially Intelligent and Responsible Agents (SIRA) Lab.
  - PROMETHEUS as a platform for SIRA experiments.
- Added a public GitHub repository link to `https://github.com/zhaw-iwi/prometheus`.
- Added [Alexandre de Spindler](mailto:alexandre.despindler@zhaw.ch) as the main contact with a link to the ZHAW Centre for Information Systems Engineering page.
- Added an selected publication list covering PROMETHEUS, PROMISE, SBR, multimodal interaction, and social behaviour work.
- Matched the visual direction of the Valerian cockpit with Space Grotesk typography, compact 8px panels, orange/teal/blue accents, and a persisted light/dark theme toggle.
- Added a persisted German/English language switch next to the dark-mode toggle.
- Added a canvas-based configuration-space hero visual and reduced mobile canvas detail to avoid text overlap.
- Updated `README.md` with the standalone page location and repository structure entry.

### How to run
1. Open the static page directly:
   - `.web/index.html`
2. Or host the `.web/` directory as static files.

### How to test
- Static checks:
  - `git diff --check`
  - `node --check .web/texts.js`
  - Inline script parse check:
    - `node -e "const fs=require('fs');const vm=require('vm');const html=fs.readFileSync('.web/index.html','utf8');const re=new RegExp('<script>([\\\\s\\\\S]*?)</script>','g');let m,n=0;while((m=re.exec(html))){new vm.Script(m[1],{filename:'inline-'+n+'.js'});n++;}console.log('checked '+n+' inline scripts');"`
- Browser smoke checks with Playwright:
  - Desktop render, dark-mode toggle, German/English toggle, page title, H1, publication count, GitHub links, and contact links.
  - Mobile render at 390x844 with no horizontal overflow.

### Known issues and decisions
- The page intentionally lives under `.web/` and is not bundled into the Spring static client surfaces.
- The page uses CDN-hosted fonts and Bootstrap Icons, matching the existing Valerian style direction.
- The publication list is a selected research-context list, not a formal CV or exhaustive bibliography.

### Next steps
1. Review the public wording and publication selection before deploying the page online.

## Milestone 127
### Date
2026-07-10

### Goal
Create the first standalone German participation site frontend under `.web/participate/` for recruiting participants into the human-AI collaboration study.

### What changed
- Added `.web/participate/index.php` as the deployable root page for the participation site.
- Added local frontend assets:
  - `.web/participate/assets/styles.css`
  - `.web/participate/assets/app.js`
- Matched the SIRA/Valerian visual direction with compact panels, 8px radii, orange/teal accents, a canvas hero, and persisted light/dark theme.
- Added German landing content for the study:
  - introductory study motivation.
  - what participants can expect.
  - key facts for date, place, target group, and thank-you gift.
  - privacy and contact summary.
- Added a `Mitmachen` registration dialog with a three-step chevron wizard:
  - personal details: full name, date of birth, e-mail address.
  - half-day preference: Monday 17 August 2026 morning, Monday 17 August 2026 afternoon, or unavailable-but-interested.
  - review and submit.
- Added a privacy information dialog accessible from the landing page, footer, and review step.
- Added frontend validation for required personal details, valid e-mail address, and slot preference.
- Added local browser recognition for the frontend milestone:
  - submitted data is stored in local storage with a cookie marker.
  - returning users see their registration summary.
  - returning users cannot edit and resubmit through the CTA.
- Added a full-width success alert after local submission that vanishes after five seconds.
- Added dedicated Playwright support for this PHP-rooted static site:
  - `playwright.participate.config.mjs`
  - `tests/playwright/participate.spec.mjs`
  - `npm run test:participate:visual`
- Updated `README.md` with local run and test instructions.

### How to run
1. Start PHP's built-in server:
   - `php -S 127.0.0.1:8091 -t .web/participate`
2. Open:
   - `http://127.0.0.1:8091/`

### How to test
- Static checks:
  - `php -l .web/participate/index.php`
  - `node --check .web/participate/assets/app.js`
  - `node --check tests/playwright/participate.spec.mjs`
  - `node --check playwright.participate.config.mjs`
  - `git diff --check`
- Browser smoke:
  - `npm run test:participate:visual`

### Known issues and decisions
- This milestone is frontend-only. It does not yet write to MySQL or send e-mail.
- Submission currently stores a local summary in the participant's browser to validate the wizard and returning-summary UX. Milestone 128 will replace this with the PHP/MySQL registration API, duplicate e-mail handling, cookies backed by a server token, and confirmation mail.
- The page intentionally lives under `.web/participate/` and does not depend on `.web/index.html` or `.web/texts.js`.
- The study page is German-only for this milestone, but the asset structure leaves room for future localization.

### Next steps
1. Milestone 128: add `.env` loading, MySQL schema/seed files, PHP registration API, duplicate handling, confirmation mail, and database-backed returning-summary lookup.

## Milestone 128
### Date
2026-07-10

### Goal
Replace the participation site's frontend-only submission with a deployable PHP/MySQL registration backend, confirmation mail support, duplicate e-mail handling, and database-backed returning-summary lookup.

### What changed
- Added deployment and test environment templates under `.web/participate/`:
  - `.env.example` for production host credentials and mail settings.
  - `.env.test` for the local MySQL test instance using `root` / `achselle9`.
- Added MySQL deployment files:
  - `.web/participate/database/schema.sql`
  - `.web/participate/database/seed.sql`
- Seeded the first three participation options:
  - Monday 17 August 2026, 09:00 to 13:00, capacity 64.
  - Monday 17 August 2026, 13:00 to 17:00, capacity 64.
  - unavailable-but-interested option with no capacity limit.
- Added PHP backend support in `.web/participate/config/bootstrap.php`:
  - local `.env` loading.
  - PDO/MySQL connection creation.
  - JSON request/response helpers.
  - secure server-token registration cookie helper.
  - PHP `mail()` confirmation sender with logged-mail transport for tests.
- Added participant API endpoints:
  - `.web/participate/api/register.php`
  - `.web/participate/api/registration.php`
- Registration now:
  - validates name, date of birth, e-mail address, and selected slot.
  - enforces one registration per normalized e-mail address.
  - checks slot capacity for the two half-day slots.
  - stores the request in MySQL.
  - sets an HTTP-only cookie containing a random 64-character public token.
  - sends a confirmation mail marked `DO NOT REPLY TO THIS MAIL`.
  - adds comma-separated `ADMIN_NOTIFY_EMAIL` values as BCC recipients on participant mails.
- Duplicate registration rejection now returns a graceful message telling participants to contact `alexandre.despindler@zhaw.ch`.
- Updated the frontend submission flow in `.web/participate/assets/app.js`:
  - posts the wizard values to the PHP API.
  - shows backend validation or duplicate messages in the existing wizard alert.
  - restores returning-user summaries through the server-backed cookie.
  - prevents editing and resubmitting once the browser has an existing registration token.
- Updated the returning summary copy in `.web/participate/index.php` to describe browser-backed registration lookup instead of local-only storage.
- Added `.web/participate/tests/setup_test_db.php` to reset and seed the local MySQL test database and clear logged test mails.
- Updated `playwright.participate.config.mjs` so `npm run test:participate:visual` resets MySQL, starts PHP with `.env.test`, and runs against the real backend.
- Extended `tests/playwright/participate.spec.mjs` to verify:
  - wizard validation and review.
  - backend submission.
  - MySQL-backed summary restoration after reload.
  - logged confirmation mail with BCC.
  - duplicate e-mail rejection.
  - mobile layout.
- Updated `.gitignore` to ignore production `.env` and generated test mail while keeping `.env.example` and `.env.test` tracked.
- Updated `README.md` with deployment setup, local backend setup, and backend-aware test instructions.

### How to run
1. For deployment, create `.web/participate/.env` from `.web/participate/.env.example`.
2. Execute the database files in this order in the deployment MySQL database:
   - `.web/participate/database/schema.sql`
   - `.web/participate/database/seed.sql`
3. For local testing:
   - `$env:PARTICIPATE_ENV_FILE = (Resolve-Path .web/participate/.env.test).Path`
   - `php .web/participate/tests/setup_test_db.php`
   - `php -S 127.0.0.1:8091 -t .web/participate`
4. Open:
   - `http://127.0.0.1:8091/`

### How to test
- Static checks:
  - `php -l .web/participate/index.php`
  - `php -l .web/participate/config/bootstrap.php`
  - `php -l .web/participate/api/register.php`
  - `php -l .web/participate/api/registration.php`
  - `php -l .web/participate/tests/setup_test_db.php`
  - `node --check .web/participate/assets/app.js`
  - `node --check tests/playwright/participate.spec.mjs`
  - `node --check playwright.participate.config.mjs`
- Database setup:
  - `php .web/participate/tests/setup_test_db.php`
- MySQL seed check:
  - `mysql -uroot -pachselle9 -D sira_participate_test -e "SELECT slot_key, capacity, is_active FROM participation_slots ORDER BY sort_order; SELECT COUNT(*) AS registrations FROM participation_registrations;"`
- Browser/backend smoke:
  - `npm run test:participate:visual`

### Verification
- `php -l .web/participate/index.php`: passed.
- `php -l .web/participate/config/bootstrap.php`: passed.
- `php -l .web/participate/api/register.php`: passed.
- `php -l .web/participate/api/registration.php`: passed.
- `php -l .web/participate/tests/setup_test_db.php`: passed.
- `node --check .web/participate/assets/app.js`: passed.
- `node --check tests/playwright/participate.spec.mjs`: passed.
- `node --check playwright.participate.config.mjs`: passed.
- `php .web/participate/tests/setup_test_db.php`: passed and prepared `sira_participate_test`.
- Direct API smoke with PHP server: passed registration insert, cookie-backed summary lookup, logged mail, and duplicate 409 rejection.
- MySQL seed check: passed; both half-day slots have capacity 64 and the unavailable option has `NULL` capacity.
- `npm run test:participate:visual`: passed, 2 tests.

### Known issues and decisions
- Production mail uses PHP `mail()` because no SMTP library or Composer dependency was introduced.
- Test mail uses `MAIL_TRANSPORT=log` and writes `.eml` files under `.web/participate/.tmp/mail`.
- The registration cookie stores only a random public token; the summary itself is fetched from MySQL.
- `.web/participate/.env` is intentionally ignored and must be created on the deployment host.
- The admin view is not part of this milestone.

### Next steps
1. Milestone 129: add the unprotected `/admin/` view with searchable/sortable registration table and CSV export.

## Milestone 129
### Date
2026-07-10

### Goal
Add the participation admin view under `.web/participate/admin/` so registrations can be inspected, searched, sorted, deleted, and exported as CSV.

### What changed
- Added `.web/participate/admin/index.php` as the admin root page.
- Added `.web/participate/admin/admin.css` for admin-specific layout:
  - compact SIRA/participate visual language.
  - summary metric cards.
  - toolbar with search and CSV export.
  - horizontally scrollable dense registration table.
- Added `.web/participate/admin/admin.js` for plain-JavaScript table behavior:
  - persisted light/dark theme toggle using the existing participate theme key.
  - client-side search across every displayed column.
  - sortable headers for every displayed column.
  - per-row deletion through the admin delete endpoint.
  - CSV export from the full loaded registration set, independent of the active search filter.
- Added `.web/participate/admin/delete.php` to delete a registration row from MySQL, freeing the e-mail address and browser token so the participant can register again.
- The admin page queries MySQL server-side through the existing participation backend config and renders:
  - ID.
  - created and updated timestamps.
  - full name.
  - date of birth.
  - e-mail address.
  - slot preference.
  - slot start and end.
  - slot capacity.
  - status.
  - IP address.
  - user agent.
- Extended the participation Playwright suite with an admin smoke test that:
  - creates two registrations through the API.
  - opens `/admin/`.
  - verifies the table contains the registrations.
  - sorts by e-mail.
  - filters by search value.
  - exports CSV while filtered and verifies the CSV still contains all loaded rows.
  - deletes a registration and verifies the same browser can open the signup dialog and submit the same e-mail address again.
- Updated `README.md` with the `/admin/` route, lack of built-in authentication, and admin test coverage.

### How to run
1. Use the same `.web/participate/.env` deployment setup from milestone 128.
2. Open the admin route below the deployed participation root:
   - `/admin/`
   - local example: `http://127.0.0.1:8091/admin/`

### How to test
- Static checks:
  - `php -l .web/participate/admin/delete.php`
  - `php -l .web/participate/admin/index.php`
  - `node --check .web/participate/admin/admin.js`
  - `node --check tests/playwright/participate.spec.mjs`
- Browser/backend smoke:
  - `npm run test:participate:visual`
- Manual visual check:
  - start PHP with `.env.test`.
  - capture `http://127.0.0.1:8091/admin/` at 1440x1000.
  - verify the metrics, toolbar, and wide table do not overlap and remain readable.

### Verification
- `php -l .web/participate/admin/delete.php`: passed.
- `php -l .web/participate/admin/index.php`: passed.
- `node --check .web/participate/admin/admin.js`: passed.
- `node --check tests/playwright/participate.spec.mjs`: passed.
- `npm run test:participate:visual`: passed, 3 tests.
- Manual screenshot check of `/admin/` at 1440x1000: passed; table is readable and horizontally scrolls inside its panel.

### Known issues and decisions
- The admin page intentionally has no built-in authentication, matching the requested UUID/obscured-folder deployment approach.
- The CSV export is generated client-side from the full dataset loaded into the admin page. For very large future datasets, a server-side export endpoint would be more scalable.
- The page omits the registration public token from the visible/exported table to avoid exposing a browser summary token unnecessarily.
- Deletion is hard-delete rather than cancellation because the participant must be able to register again with the same e-mail address and stale browser token.

### Next steps
1. No milestone 130 has been defined yet.

## Milestone 130
### Date
2026-07-13

### Goal
Optimize the coding-agent bootstrap and context documents so new agents receive
accurate PROMETHEUS architecture and current-state guidance without loading the
complete milestone history or inheriting project-specific assumptions from the
reusable engineering guide.

### What changed
- Rewrote `.agents/messageinabottle.txt` as a compact startup sequence that
  reads the generic engineering guide, PROMETHEUS context, and only the current
  status at the top of `PROJECT.md` before inspecting task-relevant code and
  tests.
- Replaced `.agents/CODEX.md` with a shorter project-neutral guide covering
  context discovery, scope safety, software-engineering principles, milestone
  execution, testing, documentation, cleanup, and review handoff.
- Removed generic-guide assumptions about prototype maturity, backward
  compatibility, schema resets, fixed error shapes, repository file layout, and
  mandatory full-history reading. Those decisions now defer to project context.
- Refactored `.agents/CONTEXT.MD` around PROMETHEUS's purpose, explicit
  event/state-machine/behaviour model, implemented capabilities, repository
  boundaries, compatibility policy, and task-oriented source/test routing.
- Documented the regulation maturity boundary accurately:
  - persisted no-op/Zurich foundation and internal opportunity support exist;
  - production agents still use no-op regulation;
  - multimodal social evidence, modulation consumption, interrupts,
    arbitration, safety precedence, and diagnostics remain incomplete.
- Distinguished raw external observations from backend-derived social
  situation changes, system events, and internal regulation events.
- Normalized all bootstrap and contributor references to the tracked
  `.agents/CONTEXT.MD` casing so they work on case-sensitive systems.
- Added this concise current implementation snapshot at the top of `PROJECT.md`
  and explicitly designated the remaining milestones as a selectively searched
  historical audit.
- Updated the README introduction to describe regulation as a developing
  foundation and updated project notes to explain each agent-facing file.

### How to run
1. Give a new coding agent the contents of
   `.agents/messageinabottle.txt`.
2. The agent should read the two focused `.agents` documents and the current
   status at the top of `PROJECT.md`, then inspect only task-relevant sources.

### How to test
- `git diff --check`
- PowerShell documentation contract assertions for:
  - exact `.agents/CONTEXT.MD` path tracking;
  - absence of PROMETHEUS-specific terms in `.agents/CODEX.md`;
  - selective-history wording in the bootstrap and project status;
  - regulation capability/gap statements in `.agents/CONTEXT.MD`;
  - preservation of historical milestone records;
  - synchronized README agent-document guidance.

### Verification
- `git diff --check`: passed.
- PowerShell agent-document contract assertions: passed for tracked path casing,
  project-neutral `CODEX.md`, selective-history startup guidance, regulation
  capability/gap coverage, historical milestone preservation, and synchronized
  README guidance.
- Measured required startup content decreased from 357,251 bytes to 19,986
  bytes, a 94.4% reduction; the new current-status section is 65 lines.
- No runtime tests were run because this milestone changes documentation and
  agent instructions only.

### Known issues and decisions
- Historical milestones remain in `PROJECT.md` to preserve the existing audit
  trail. Startup guidance now prevents loading them end to end; physical archive
  splitting is unnecessary unless the file becomes operationally difficult to
  maintain.
- The current-status section is intentionally curated and must be updated when
  future milestones materially change architecture, capabilities, gaps, or the
  latest milestone.
- This milestone changes agent guidance only. Runtime code, public APIs,
  database schemas, and user-facing application behavior are unchanged.

### Next steps
1. Rehearse `.agents/messageinabottle.txt` with a fresh coding-agent session and
   adjust only if it still causes unnecessary broad file reads.
2. Scope the next regulation milestone separately, with an explicit motivation
   model and acceptance criteria before implementation.

## Milestone 133
### Date
2026-07-13

### Goal
Provide a standalone public Talk to Me client at `/public/talktome` that keeps
PROMETHEUS access-code scoping and backend-authoritative OpenAI Realtime speech,
while giving users explicit ownership of speech-agent creation, connection,
disconnection, and deletion.

### What changed
- Added the Spring-discovered `core.talk_to_me` agent definition with interaction
  profile tag `utility.talk_to_me` and a deterministic policy that copies the
  latest user utterance exactly into a speech-only `BehaviourPlan`.
- Added a 2,000-Unicode-code-point policy boundary and matching browser
  validation. Blank or over-limit inputs do not produce speech.
- Added the standalone Valerian-styled client under
  `src/main/resources/public/talktome` and the stable
  `/public/talktome` forwarding route.
- Kept lifecycle actions explicit: users create, select, connect, disconnect,
  and delete access-code-scoped instances.
- Opened a receive-only WebRTC audio connection without requesting microphone
  access, with Realtime voice, output speed, and browser speaker controls.
- Preserved the authority boundary: the client acknowledges exact text through
  the scoped agent API, while the backend owns Realtime session and response
  commands.
- Added policy, registry/profile, MVC route, static-client contract,
  MySQL-backed scoped integration, and Playwright lifecycle/visual coverage.
- Updated `README.md`, `.agents/CONTEXT.MD`, and current project status.

### How to run
1. Create a five-character access code in Valerian Access Management and assign
   `core.talk_to_me`.
2. Start the application with `./mvnw spring-boot:run` or
   `.\mvnw.cmd spring-boot:run`.
3. Open `http://localhost:8080/public/talktome`, enter the code, create an
   instance, configure the speech connection, and connect.

### How to test
- Focused Java and MySQL-backed tests:
  - `.\mvnw.cmd -q "-Dtest=TalkToMePolicyUnitTest,AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,TalkToMeClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,TalkToMeScopedIntegrationTest,RealtimeSidebandServiceContractTest" test`
- Browser syntax:
  - `node --check src/main/resources/public/talktome/script.js`
  - `node --check tests/playwright/talktome.spec.mjs`
- Browser/database smoke and visual checks:
  - `npm.cmd run test:talktome:visual`

### Verification
- Focused Java tests passed, including `TalkToMeScopedIntegrationTest` against
  the configured MySQL test database.
- JavaScript and Playwright syntax checks passed.
- Talk to Me Playwright lifecycle, persistence, Realtime authority, speaker,
  responsive, and screenshot smoke passed, 1 test.

### Known issues and decisions
- Automated browser coverage fakes OpenAI/WebRTC and physical audio devices;
  audible live output remains an environment-dependent manual check.
- Multiple Talk to Me agents may exist under one access code. The client does
  not silently create, reuse, or delete an instance.
- A Realtime call is ephemeral; the persisted agent is the longer-lived
  user-managed resource.

### Next steps
1. Perform one manual audible smoke with deployment OpenAI credentials and the
   intended speaker hardware before public deployment.
2. Select the next milestone independently; no follow-on Talk to Me scope is
   implied by this milestone.

## Milestone 134
### Date
2026-07-13

### Goal
Polish the public Talk to Me lifecycle controls and make Realtime speech
completion accurate and diagnosable after a reported mid-paragraph audio and
transcript cutoff.

### What changed
- Grouped Create/Delete and Connect/Disconnect into two full-width,
  equal-button rows in the Speech Instance card.
- Moved voice, output speed, and speaker selection between those lifecycle
  rows. Dynamic guidance explains that voice and speed lock for an active call
  while speaker selection and refresh remain available immediately.
- Made `alloy` the initial voice in markup and the fresh-user preference
  fallback. An explicitly stored voice choice continues to take precedence.
- Load the reported “Love is patient…” paragraph into the textarea on every
  page load and provide accessible icon-only actions to restore or clear it.
- Retained the 2,000-Unicode-code-point application boundary. The reported
  320-code-point paragraph is well within it, so no speculative lower limit was
  added.
- Replaced partial transcript deltas with the final transcript event and also
  recover the complete transcript from `response.done` output.
- Interpret Realtime's final response status and `status_details`, distinguishing
  completed, incomplete, cancelled, and failed responses. Output-token and
  content-filter cutoffs now have explicit messages.
- Warn when a completed Realtime transcript differs from the submitted text
  instead of reporting unconditional success.
- Extended the static resource and Playwright coverage for the row and settings
  geometry, disconnected/connected control states and guidance, Alloy default,
  default-text initialization, restoration, clearing, reload behavior, the
  reported paragraph, partial-to-final transcript replacement, transcript
  mismatch, and output-token cutoff handling.
- Updated `README.md` with the default, limit, and completion semantics.

### How to test
- Focused Java and configured MySQL smoke:
  - `.\mvnw.cmd -q "-Dtest=TalkToMePolicyUnitTest,AgentDefinitionRegistryUnitTest,SeedAgentInteractionProfileContractTest,TalkToMeClientStaticResourceContractTest,StaticRedirectControllerWebMvcTest,TalkToMeScopedIntegrationTest,RealtimeSidebandServiceContractTest" test`
- Browser syntax:
  - `node --check src/main/resources/public/talktome/script.js`
  - `node --check tests/playwright/talktome.spec.mjs`
- Browser/database smoke and visual checks:
  - `npm.cmd run test:talktome:visual`

### Verification
- Focused Java tests passed, including `TalkToMeScopedIntegrationTest` against
  the configured MySQL test database.
- JavaScript and Playwright syntax checks passed.
- Talk to Me Playwright lifecycle, persistence, settings-placement and locking,
  voice-default, textarea preset actions, Realtime completion, responsive, and
  screenshot smoke passed, 1 test.
- Light desktop and dark mobile screenshots were inspected; both lifecycle rows
  use the available width evenly and remain legible at mobile width.

### Known issues and decisions
- The automated browser smoke fakes OpenAI/WebRTC and physical speaker output,
  so it cannot reproduce or prove resolution of the reported audible cutoff.
  It does fix the partial-transcript rendering bug and makes a repeated live
  cutoff report its final Realtime status and transcript mismatch.
- The Realtime session does not request a lower output-token cap; reducing the
  textarea limit would not address the observed short-paragraph failure.
- A Realtime transcript describes model output but cannot prove that every audio
  frame reached a particular browser and speaker device.

### Next steps
1. Deploy `main` and repeat the audible test with the same paragraph.
2. If audio still stops, record the displayed completion message and final
   transcript; they now distinguish model truncation from downstream playback.

## Milestone 135
### Date
2026-07-13

### Goal
Replace Talk to Me's conversational Realtime renderer with an output-only
OpenAI Speech renderer so the persisted canonical speech plan is the actual
synthesis input and short exact-text requests are not exposed to conversational
response truncation.

### What changed
- Added `POST /demo/talktome/agents/{agentId}/speech`. The scoped application service
  acknowledges the observation with `REALTIME_SPEECH`, extracts the unchanged
  speech channel from the returned persisted `BehaviourPlan`, and only then
  invokes the synthesis gateway.
- Added an OpenAI Speech gateway for `/v1/audio/speech` with configurable
  `prometheus.talktome.speech.model` and `prometheus.talktome.speech.url`.
  Standard OpenAI requests use
  `gpt-4o-mini-tts` and MP3; voice and speed are validated server-side against
  the supported contract.
- Return uncached audio from the scoped endpoint. Invalid requests/settings are
  400, a policy result without speech is 409, and upstream synthesis failure is
  502.
- Removed Talk to Me's Connect/Disconnect, WebRTC peer connection, Realtime
  data channel, response-status, and generated-transcript lifecycle. Other
  Valerian and Realtime clients are unchanged.
- The browser now uses Speak/Stop directly, buffers the returned audio blob,
  routes it to the selected speaker, and reports completion only on the media
  element's `ended` event. Stop aborts synthesis or stops playback.
- Updated focused gateway, MVC, scoped persistence, static-resource, and
  Playwright coverage plus OpenAI configuration and project documentation.

### How to test
- Focused Java tests:
  - `.\mvnw.cmd -q "-Dtest=TalkToMeSpeechControllerWebMvcTest,TalkToMeScopedIntegrationTest,TalkToMeClientStaticResourceContractTest,OpenAISpeechSynthesisGatewayUnitTest" test`
- Browser syntax:
  - `node --check src/main/resources/public/talktome/script.js`
  - `node --check tests/playwright/talktome.spec.mjs`
- Browser/database smoke and visual checks:
  - `npm.cmd run test:talktome:visual`

### Verification
- Focused Java tests passed, including exact outbound text/options at the local
  OpenAI Speech boundary and exact event/behaviour persistence against the
  configured test database.
- The full Java regression suite passed: 246 tests, 0 failures/errors/skips.
- JavaScript and Playwright syntax checks passed.
- Talk to Me Playwright synthesis mapping, playback, media-ended completion,
  Stop, persistence, speaker, responsive layout, and screenshot smoke passed,
  1 test.

### Known issues and decisions
- The browser intentionally buffers the complete MP3 before playback. This
  favors complete, simple playback for the 2,000-code-point client limit but
  adds startup latency relative to streamed audio.
- The automated gateway and browser tests use local/fake audio boundaries. They
  do not prove audible output through deployment credentials and intended
  speaker hardware.
- The agent observation and canonical response are persisted before the
  external synthesis call. An upstream failure therefore remains auditable, but
  a manual retry creates another observation/response pair.
- Standard OpenAI request mapping is tested. Azure deployment URL behavior is
  configuration-compatible with the existing provider abstraction but was not
  exercised live.

### Next steps
1. Deploy with `prometheus.talktome.speech.model=gpt-4o-mini-tts` and the Speech endpoint.
2. Repeat the supplied-paragraph audible smoke on the target browser/speaker
   and confirm playback reaches the media `ended` completion state.

## Milestone 136
### Date
2026-07-13

### Goal
Make Talk to Me's output-only synthesis backend structurally independent from
Valerian Cockpit and the shared scoped/Realtime application paths.

### What changed
- Moved synthesis HTTP handling from `ScopedDemoController` into the dedicated
  `TalkToMeSpeechController` at
  `POST /demo/talktome/agents/{agentId}/speech`. The shared controller constructor,
  mappings, and MVC slices are restored to their pre-synthesis contract.
- Added `ScopedTalkToMeSpeechService`, which requires the existing
  `utility.talk_to_me` profile tag before acknowledgement, persistence, or an
  external synthesis call. Other scoped agents receive 404 from this endpoint.
- Restored `SpeechTurnSelector` to its Realtime-only implementation. Talk to Me
  now extracts its exact persisted plan within its own application service.
- Restored `OpenAIProperties` to the shared Chat/Realtime contract. Dedicated
  `TalkToMeSpeechProperties` owns
  `prometheus.talktome.speech.model` and
  `prometheus.talktome.speech.url`; the synthesis gateway reuses only the
  existing provider credentials/header behavior.
- Split controller tests along the same boundary and added an application test
  proving a non-Talk-to-Me agent cannot be acknowledged or synthesized through
  the output-only path.

### How to test
- Focused Java isolation and speech tests:
  - `.\mvnw.cmd -q "-Dtest=ScopedTalkToMeSpeechServiceUnitTest,TalkToMeSpeechControllerWebMvcTest,TalkToMeScopedIntegrationTest,TalkToMeClientStaticResourceContractTest,OpenAISpeechSynthesisGatewayUnitTest,ScopedDemoControllerWebMvcTest,PrometheusCorsConfigurationWebMvcTest" test`
- Full Java regression suite:
  - `.\mvnw.cmd -q test`
- Browser syntax and regression checks:
  - `node --check src/main/resources/public/talktome/script.js`
  - `node --check tests/playwright/talktome.spec.mjs`
  - `npm.cmd run test:talktome:visual`
  - `npm.cmd run test:valerian:visual`

### Verification
- Focused isolation and speech tests passed: 17 tests across the seven listed
  suites, with no failures, errors, or skips.
- The full Java regression suite passed: 248 tests across 63 suites, with no
  failures, errors, or skips.
- JavaScript and Playwright syntax checks passed.
- Talk to Me's Playwright persistence, synthesis mapping, playback, Stop,
  speaker, lifecycle, responsive-layout, and screenshot smoke passed: 1 test.
- The unchanged Valerian cockpit Playwright suite passed all detached-window,
  camera/microphone ownership, column-expansion, face-emotion, and resilient
  social-detector checks: 5 tests.

### Known issues and decisions
- The synthesis provider still uses the shared OpenAI provider selection and
  credential/header helpers. Those are read-only dependencies; synthesis no
  longer adds fields or payload behavior to `OpenAIProperties`.
- A valid access code and visible agent remain shared scoped infrastructure by
  design. Talk to Me adds its profile-tag restriction on top of that boundary.
- Live OpenAI credentials and audible speaker hardware remain outside automated
  regression coverage.

## Milestone 139
### Date
2026-08-14

### Goal
Establish the persistence, deployment, deterministic phase rules, and private
Brainkick assignment import foundation for the standalone participation site
before adding its administrative and participant-facing phase workflows.

### What changed
- Added `participation_phase_settings` as the singleton overall/default phase
  setting, initialized to phase 1.
- Added `participation_assignments` as a one-to-one, cascade-deleted extension
  of registrations with the experiment's fixed access code, role, team,
  half-day, time, and room fields.
- Added `participation_participant_state` for nullable per-registration phase
  overrides and the future reversible results-interest value/timestamp.
- Added deterministic phase rules in `config/phases.php`:
  - missing half-day or time data limits a participant to phase 1;
  - complete schedule data with incomplete phase-3 data limits them to phase 2;
  - complete assignments permit phases 1 through 4;
  - phase 4 exposes no assignment data.
- Kept the canonical `database/schema.sql` and `database/seed.sql` synchronized
  with the model.
- Added the MariaDB-compatible, repeatable additive migration
  `database/migrations/20260814_participation_phases.sql`.
- Added `database/generate_brainkick_seed.php`, which validates the private
  seven-column CSV, converts blank/literal `NULL` values to SQL `NULL`, rejects
  duplicate IDs/access codes, orders rows by participant ID, and generates a
  repeatable assignment upsert.
- Generated the requested `database/brainkick_seed.sql` with 57 live
  assignments. It remains on the local deployment workspace but is ignored by
  Git because it contains live access codes.
- Added `database/brainkick_verify.sql` for post-deployment counts and
  incomplete-assignment review.
- Ignored the private live database dump and generated Brainkick seed without
  modifying or deleting either artifact.
- Added pure phase-rule, generator, and disposable MariaDB migration smoke
  tests. The migration test also exercises the private seed when it is present.
- Updated README deployment, generation, verification, testing, and structure
  documentation.

### How to run
- Fresh database:
  - execute `.web/participate/database/schema.sql`;
  - execute `.web/participate/database/seed.sql`.
- Existing live database:
  - execute `.web/participate/database/migrations/20260814_participation_phases.sql`;
  - execute the locally generated `.web/participate/database/brainkick_seed.sql`;
  - execute `.web/participate/database/brainkick_verify.sql` and review the results.
- Regenerate the private data seed when needed:
  - `php .web/participate/database/generate_brainkick_seed.php INPUT.csv .web/participate/database/brainkick_seed.sql`.

### How to test
- `php .web/participate/tests/setup_test_db.php`
- `php .web/participate/tests/phase_rules_test.php`
- `php .web/participate/tests/brainkick_seed_generator_test.php`
- `php .web/participate/tests/migration_smoke_test.php`
- PHP syntax checks across `.web/participate/`
- `npm.cmd run test:participate:visual`
- `git diff --check`

### Verification
- The canonical schema and seed prepared `sira_participate_test` successfully.
- Phase-rule tests passed.
- Brainkick seed generator tests passed.
- The disposable MariaDB migration smoke passed, including applying the
  migration twice, applying the private 57-row seed twice, checking participant
  4's SQL `NULL` fields, constraint enforcement, and cascade deletion.
- The existing participation Playwright suite passed all 3 participant/admin
  tests against the evolved schema.
- PHP syntax and whitespace checks passed.

### Known issues and decisions
- This milestone intentionally adds no phase controls or new participant UI;
  those are the next two milestones.
- The generated Brainkick seed is a deployment artifact, not a versioned source
  artifact, because it contains live access codes. Its generator and structural
  verification are versioned.
- Participant ID is the existing `participation_registrations.id`; the live CSV
  contained 57 unique IDs and all matched the supplied 66-registration dump.
- Registrations without schedule assignments, including participant 4 while
  its time remains `NULL`, stay in phase 1 even when a higher default is later
  selected.

### Next steps
1. Milestone 140: add overall/per-participant phase controls, assignment
   editing, readiness diagnostics, and export fields to the admin site.

## Milestone 140
### Date
2026-08-14

### Goal
Give participation administrators direct control over the overall/default
phase, per-participant overrides, and the experiment's fixed assignment values
while making incomplete-data phase limits explicit and testable.

### What changed
- Added an overall phase control to the participation admin page with a
  confirmation step and an explicit signup-closure warning.
- Added server-side signup closure in `api/register.php` whenever the overall
  phase is not phase 1, including the stable `signup_closed` error code.
- Added phase summary metrics and effective/requested phase diagnostics to the
  registration table.
- Added one modal editor per registration for:
  - nullable individual phase override;
  - half-day and time slot;
  - access code, role, team ID, and room.
- Empty assignment editor values are normalized to SQL `NULL`. Effective phase
  is recalculated from the requested phase and data readiness, so incomplete
  participants remain at the highest safe visible phase.
- Added `admin/update.php` for validated overall-phase and participant updates,
  including registration existence checks, field-length boundaries,
  transactions, and duplicate access-code rejection.
- Preserved individual overrides when the overall phase changes; choosing
  `Standardphase übernehmen` stores a nullable override.
- Extended admin search, sorting, metrics, and full-dataset CSV export with
  effective phase, missing assignment data, all assignment fields, and the
  results-interest value/timestamp prepared in milestone 139.
- Preserved relative admin endpoint paths so the deployed admin directory can
  continue to use its UUID-suffixed name.
- Updated the admin layout for five phase metrics, the phase control, wide data
  table, and responsive assignment modal.
- Expanded the participation Playwright suite with overall/override phase
  changes, incomplete-to-complete assignment progression, duplicate access-code
  rejection, signup closure, CSV fields, and clearing a time slot back to NULL.
- Updated README behavior, deployment, test, and admin documentation.

### How to run
- Prepare the test database and start the standalone PHP site as documented in
  README.
- Open `/admin/` (or the deployment's UUID-suffixed admin directory).
- Use `Gesamtphase speichern` for the default phase.
- Use `Bearbeiten` on a participant row for an override or fixed assignment
  values.

### How to test
- `php .web/participate/tests/setup_test_db.php`
- `php .web/participate/tests/phase_rules_test.php`
- `php .web/participate/tests/brainkick_seed_generator_test.php`
- `php .web/participate/tests/migration_smoke_test.php`
- PHP syntax checks across `.web/participate/`
- `node --check .web/participate/admin/admin.js`
- `node --check tests/playwright/participate.spec.mjs`
- `npm.cmd run test:participate:visual`
- `git diff --check`

### Verification
- PHP syntax checks passed for all participation PHP files.
- JavaScript syntax checks passed for the admin client and Playwright suite.
- Clean-schema setup, phase rules, seed generation, and the MariaDB
  migration/private-seed smoke tests passed.
- The participation Playwright suite passed all 4 participant/admin tests.
- Headless Chromium screenshots of the 1440x1000 admin overview and assignment
  modal were inspected; controls, metrics, table, backdrop, fields, and actions
  were readable without overlap.
- Whitespace checks passed.

### Known issues and decisions
- The participant page still uses the phase-1 presentation in this milestone;
  higher-phase participant rendering and recovery belong to milestone 141.
- The public signup button remains present until milestone 141, but direct
  submissions are already rejected server-side outside phase 1.
- Admin authentication remains intentionally external through the deployment's
  hard-to-guess UUID-suffixed directory.
- The in-app browser connection was unavailable in this environment; the
  project-owned Playwright/Chromium suite and inspected screenshot artifacts
  provided browser coverage instead.

### Next steps
1. Milestone 141: add email/date-of-birth recovery, phase-specific participant
   data, closed-signup presentation, and reversible results-interest handling.

## Milestone 141
### Date
2026-08-14

### Goal
Complete the standalone participation workflow with cross-device recovery,
server-filtered phase 2/3/4 participant views, closed-signup presentation, and
the reversible phase-4 results-information choice.

### What changed
- Added `api/identify.php` so a participant can recover an active registration
  on another device with the normalized e-mail address and exact date of birth.
  Successful recovery installs the existing long-lived public-token cookie on
  that device; failed matching uses a generic response.
- Centralized participant-row loading and public-session construction in
  `config/bootstrap.php` so registration, recovery, and session refresh use the
  same effective-phase and data-filtering contract.
- Extended `api/registration.php` and `api/register.php` with the current
  signup state, effective phase, phase label, filtered assignment, and
  phase-appropriate results-interest state.
- Enforced data minimization in PHP before JSON encoding:
  - phase 2 exposes participant ID, half-day, and time slot only;
  - phase 3 adds access code, role, team ID, and room;
  - phase 4 returns an empty assignment object.
- Added `api/results-interest.php` for an explicit boolean phase-4 choice. It
  preserves any participant phase override and updates the stored last-change
  timestamp on every save.
- Reworked the participant page into four phase views while retaining the
  original signup summary as phase 1. Phase 3 includes a copyable access code;
  phase 4 replaces all assignment data with the requested thank-you message and
  reversible results-information checkbox.
- Added a **Bereits angemeldet?** recovery dialog and kept it available when
  overall signup is closed. The visible signup action is hidden outside phase 1
  for unidentified visitors, matching the server-side registration closure
  added in milestone 140.
- Expanded the Playwright suite to use a genuinely separate browser context for
  recovery, reject a mismatched birth date, verify phase-2 response keys,
  reject premature interest writes, progress through phases 3 and 4, verify
  phase-4 replacement, persist yes then no, recover while signup is closed,
  and confirm the final interest value in the admin table.
- Updated README participant behavior, endpoint, deployment, structure, lint,
  and browser-test documentation.

### How to run
- Deploy the complete `.web/participate/` directory after applying the
  milestone-139 database migration and private Brainkick assignment seed.
- Participants use **Mitmachen** only while the overall phase is 1.
- Returning participants use **Bereits angemeldet?** with their signup e-mail
  address and date of birth on any device.
- Administrators progress the overall phase or a participant override through
  the admin page; the participant view changes on the next request/reload.

### How to test
- `php .web/participate/tests/setup_test_db.php`
- `php .web/participate/tests/phase_rules_test.php`
- `php .web/participate/tests/brainkick_seed_generator_test.php`
- `php .web/participate/tests/migration_smoke_test.php`
- PHP syntax checks across `.web/participate/`
- `node --check .web/participate/assets/app.js`
- `node --check .web/participate/admin/admin.js`
- `node --check tests/playwright/participate.spec.mjs`
- `npm.cmd run test:participate:visual`
- `git diff --check`

### Verification
- PHP and JavaScript syntax checks passed.
- Clean-schema setup, phase-rule, Brainkick generator, and repeatable
  migration/private-seed smoke tests passed.
- All 5 participation Playwright tests passed against MySQL and PHP's built-in
  server, including the separate-device recovery and public JSON filtering
  checks.
- Headless Chromium screenshots of the 1440x1000 phase-3 assignment view and
  phase-4 thank-you/interest view were inspected; content, controls, wrapping,
  spacing, and replacement behavior were correct without overlap.
- Whitespace checks passed.

### Known issues and decisions
- Recovery intentionally uses the approved e-mail/date-of-birth pair and does
  not rotate the existing public token, so already identified devices remain
  valid.
- Invalid recovery matches deliberately return one generic error to avoid
  disclosing whether an e-mail address exists.
- Results interest is writable only when the participant's effective phase is
  4. Saving an unchecked box stores an explicit `false`, distinct from the
  initial unanswered `NULL` state.
- Phase 4 deliberately transmits and displays no phase-3 assignment values.
- No schema change was needed in this milestone; the canonical schema, seed,
  additive migration, and private import from milestone 139 already include
  the results-interest columns.
- Admin authentication remains deployment-managed through the UUID-suffixed
  directory as explicitly accepted for this version.

### Next steps
1. Deploy the migration, private assignment seed, verification query, and then
   the complete `.web/participate/` application as documented in README.

## Milestone 142
### Date
2026-08-14

### Goal
Show the registration's stored slot label as a fourth date entry in the
participant's phase-2 and phase-3 schedule information.

### What changed
- Added `date` to the server-filtered visible assignment payload for phases 2
  and 3, sourced directly from
  `participation_registrations.slot_preference_label`.
- Added a fourth `Datum` card after `Zeitfenster` in the participant UI.
- Preserved the existing phase-4 replacement behavior, which still exposes no
  assignment or date fields.
- Extended phase-rule and Playwright assertions for the exact stored label in
  both phase 2 and phase 3.
- Updated README's phase-2 public data contract.

### How to test
- `php .web/participate/tests/phase_rules_test.php`
- `node --check .web/participate/assets/app.js`
- `node --check tests/playwright/participate.spec.mjs`
- `npm.cmd run test:participate:visual`
- `git diff --check`

### Verification
- PHP and JavaScript syntax checks passed.
- Phase-rule tests passed, including phase-2/3 date presence and phase-4
  replacement.
- All 5 participation Playwright tests passed, including exact API key order
  and rendered date-label assertions in phases 2 and 3.
- The phase-2 participant panel was captured and visually inspected at desktop
  width; all four cards fit without truncation or overlap.

### Known issues and decisions
- `Datum` deliberately displays the complete stored slot label rather than
  parsing or reformatting it, as requested.
- No database migration is required because the source column already exists
  and is non-null in `participation_registrations`.

### Next steps
1. Deploy the updated `.web/participate/` application files; no SQL step is
   required for this milestone.
