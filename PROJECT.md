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
  - `src/main/resources/scripts/realtime-speech-backend-complement-replay-script.json`
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
  - `src/main/resources/scripts/social-initiative-mvp-replay-script.json`
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
