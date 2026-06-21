# PROJECT.md

## Short project summary
PROMETHEUS is an event-driven Java framework for explicit state-machine agent control with first-class regulation and multimodal behaviour plans.

## Milestones checklist
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
- Replaced the compressed route capsule with a concrete station list covering Bürgenstock, Paradeplatz, Rinspeed, ETH Zürich, Rheinfall, Quantum Basel, Emmentaler Schaukäserei, EPFL Lausanne, Furka/Tremola/Gotthard, SUPSI Lugano, Swiss Miniature, Migros Appenzell, and ZHAW Winterthur.
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
  `Du kannst Deutsch, Französisch, Italienisch und Englisch. Antworte in der Sprache, in der Du angesprochen wirst.`
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