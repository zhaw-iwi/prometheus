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
  - `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare`
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
   - `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare`

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
