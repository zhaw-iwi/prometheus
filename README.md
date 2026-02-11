# Prometheus

Prometheus is an event-driven, regulation-aware agent framework evolving from PROMISE and SBR.

Current focus:

- Event-first runtime semantics with explicit state-machine control
- BehaviourPlan-based outputs with capability-aware rendering path
- Deterministic, testable iteration toward interrupts/policy-gate authority

## Status

- Iteration 1 complete: event-based inputs/history, shared history model, policy externalization
- Iteration 2 complete: `BehaviourPlan` output abstraction
- Iteration 3 complete (core): snapshot/fact pipeline for transition decisions/actions
- Iteration 4 complete (core): runtime tick-based continuous evaluation
- Iteration 5 complete (core slice): regulation runtime + explicit internal regulation events
- Post-Iteration-5 refactor complete: centralized event recording in `Agent` runtime with path-based state routing
- Next step: Iteration 6 (interrupt arbitration + policy gate)

## What Changed Most Recently (Important)

A significant cleanup/refactor was completed after Iteration 5 to remove duplicated event writes and make event ownership explicit.

### 1. Single Writer For Event History

- `Agent` runtime is now the only component that appends to `EventHistory`.
- `State` and `OuterState` no longer append events.
- This removed duplicate opener/response/user events that occurred in nested state flows.

### 2. Event Routing Metadata Is Path-Based

- Events now carry `statePath` (ordered active chain at record time), for example:
  - `["OuterState", "RapportBuilding"]`
- Selection by state uses containment on `statePath`:
  - Inner leaf state sees events from when that leaf was active.
  - Outer state sees all events from its full active subtree.

### 3. Selector Semantics Simplified

- `EventSelector.stateName(...)` now matches by path containment, not single label equality.
- This enables consistent retrieval for nested outer-state hierarchies without special-case selector logic.

### 4. Runtime Path Derivation

- Active path is derived from current machine position:
  - `State#getActiveStatePath()` returns leaf/self path.
  - `OuterState#getActiveStatePath()` prepends itself and recursively appends inner path.

### 5. Cleanup Of Legacy Transfer Pattern

- With shared runtime-owned history, explicit history transfer actions were removed.

### 6. Persisted Selector Configuration

- State/decision/action selectors are now stored as persisted selector specifications (`EventSelectorSpec`) instead of transient runtime-only selector objects.
- Decision/action snapshot aggregator choice is now persisted as `SnapshotAggregatorType`.
- This removes selector/aggregator drift across save/reload cycles.

### 7. Persisted Regulation Runtime

- Agent regulation system is now persisted as a regulation system spec (`RegulationSystemSpec`).
- Built-in systems implement `PersistableRegulationSystem`, allowing runtime state (for example Zurich latent variables) to survive save/reload cycles.
- Agent-level regulation snapshot aggregator choice is persisted as `SnapshotAggregatorType`.

### 8. Runtime Dependency Boundary Cleanup

- `Agent` and `State` no longer store runtime prompt/gateway collaborators.
- Runtime dependencies are passed explicitly per execution cycle via `PolicyRuntime` from the application layer.
- Legacy runtime attachment wiring in entities was removed.

### 9. Prompt Assembly Consistency Across Runtime And `/prompt`

- `/prompt` now uses the same injected `PromptMessageAssembler` instance used by runtime execution paths.
- This guarantees that custom prompt adapters/augmenters are applied consistently in:
  - `start/generate` policy execution, and
  - realtime `/prompt` retrieval.

### 10. Regulation Lifecycle Reset Semantics

- `Agent.reset()` now resets regulation internals in addition to state and event history.
- Built-in regulation systems expose explicit reset behavior:
  - `NoOpRegulationSystem`: no-op reset
  - `ZurichRegulationSystem`: restores initial latent variables and re-arms threshold gating baseline
- Reset now also refreshes persisted regulation spec from the reset runtime state.

## Current Runtime Contracts

- Inputs are `Event` objects with core fields: `type`, `actor`, `kind`, `payload`.
- Canonical facial-emotion observation type: `obs.emotion.face` (`Event.TYPE_FACE_EMOTION`).
- Runtime stamps routing metadata (`statePath`) when recording events.
- Assistant outputs are `resp.behaviour_plan` events.
- Speech content is represented in plan payload (`payload.speech`).
- `BehaviourPlan` is the output abstraction; runtime emits full plans and does not reduce channels server-side.
- Snapshot contracts are available via:
  - `SnapshotAggregator`
  - `ObservationSnapshot`
  - `Fact`
  - selector-based `FactExtractor` helpers
- `Transition` computes snapshots from selected events and passes them into decisions/actions.
- Selector contracts for state/decision/action are persisted as specs and rebuilt deterministically at runtime.
- Internal `sys.tick` events support no-input evaluation cycles.
- Optional runtime scheduler can tick active agents:
  - `prometheus.runtime.tick.enabled`
  - `prometheus.runtime.tick.delay-ms`
- Agents can host a `RegulationSystem` (default is no-op).
- `ZurichRegulationSystem` emits explicit internal events (e.g. `int.regulation.opportunity`).
- `Agent.reset()` clears:
  - active state position (to initial),
  - event history,
  - regulation internal runtime state (if stateful),
  - latest modulation (back to neutral).
- Monitor stream is SSE via `/{agentId}/monitor/stream`.
- Behaviour stream is SSE via `/{agentId}/behaviour/stream`.
- Log stream is SSE via `/logs/stream`.

## Behaviour + Prompt Pipeline

- `Policy.onStart(...)` and `Policy.onRespond(...)` return `BehaviourPlan`.
- `State` executes policy and returns response events; runtime persists them.
- `PromptPolicy` now supports optional nonverbal gesture labeling via `nonVerbalGesturePrompt`:
  - when configured, policy keeps normal speech generation and performs one additional label-selection call,
  - selected label is stored in `BehaviourPlan.nonVerbal.gesture`,
  - allowed labels: `OPEN_QUESTION`, `EXPLAIN`, `UNCERTAIN`, `ACKNOWLEDGE`, `POLITE`, `NONE`.
- Prompt assembly now happens in the policy layer:
  - `PromptMessage` is the provider-agnostic prompt DTO (`role`, `content`),
  - `PromptMessageAssembler` builds prompt message lists from selected event histories,
  - modality adapters map events to prompt-safe content (`BehaviourPlanPromptEventContentAdapter`, `FaceEmotionPromptEventContentAdapter`, `DefaultPayloadPromptEventContentAdapter`),
  - prompt context augmenters inject derived context (`NonverbalSummaryPromptContextAugmenter`).
- `OpenAILanguageModelGateway` is transport-focused:
  - receives assembled `List<PromptMessage>`,
  - maps them to OpenAI payload messages,
  - performs API request/response handling.
- Realtime and backend generation now share the same prompt semantics:
  - `/prompt` returns assembled `promptMessages` (not raw `systemPolicy` + `eventHistory`),
  - realtime client uses `promptMessages` directly as instruction context.
- Prompt content mapping via policy-layer modality adapters:
  - assistant behaviour plans map to `payload.speech` (if present),
  - facial-emotion observations map to concise text (emotion + confidence),
  - raw telemetry payloads are not forwarded verbatim for this modality.

## Emotion Abstraction Layer (Current)

- Raw facial telemetry remains in event history for traceability/debugging.
- Snapshot extraction now performs temporal emotion aggregation in `DefaultObservationSnapshotAggregator`:
  - `face_emotion_total_count`
  - `face_emotion_current`
  - `face_emotion_current_confidence`
  - `face_emotion_majority_last_window`
  - `face_emotion_negative_streak`
  - `face_emotion_valence_trend` (`improving` / `worsening` / `stable`)
  - `face_emotion_valence_volatility`
  - `face_emotion_events_since_change`
- This supports transition/regulation logic on time-aware emotion facts while keeping raw events available.
- Prompt assembly injects a compact nonverbal summary message derived from these temporal facts, for example:
  - `current`, `majority_recent`, `trend`, `negative_streak`, `events_since_change`
- Current backend does not auto-trigger an LLM call per `acknowledge`; response generation is controlled by explicit generation paths such as `POST /{agentId}/behaviour/generate` plus backend-owned scheduler ticks.
- `POST /{agentId}/behaviour/generate` accepts an optional JSON body with `omitModalities` (for example `["speech"]`) to blank selected channels in the generated `BehaviourPlan` before persistence and SSE publishing.
- `POST /{agentId}/behaviour/generate` response semantics:
  - `200 OK`: behaviour generated and emitted
  - `409 Conflict`: no behaviour produced (for example inactive/final agent)
  - `404 Not Found`: agent id unknown
- Generated behaviour events are emitted as full `BehaviourPlan` payloads according to the active agent policy.
- For `POST /agent/singlestate`, optional DTO field `stateNonVerbalGesturePrompt` enables gesture labeling for that created state policy.

## Event Model Notes

- Historical single-state labeling (`stateName`) was replaced by `statePath` semantics.
- Event routing and retrieval now use `statePath` semantics directly.

## Testing Status

Automated coverage currently includes:

- `BehaviourPlan` serialization and emptiness
- `EventSelector` composition/filtering
- `EventSelectorSpec` JSON/spec round-trip and selector rebuilding
- state/transition behavior-plan emission and selector behavior
- snapshot aggregation and selector-based fact extraction
- snapshot-aware transition decisions/actions
- selector spec persistence across agent save/reload
- regulation system state persistence across agent save/reload
- agent tick/no-input progression
- continuous scheduler processing (active agents only)
- Zurich regulation dynamics and regulation-to-transition integration
- regulation reset behavior on agent reset
- OpenAI message mapping from events
- policy-layer prompt assembly (`PromptMessageAssembler`)
- `/prompt` parity with runtime prompt assembly configuration
- facial emotion prompt mapping abstraction (`FaceEmotionPromptEventContentAdapter`)
- prompt policy composition checks (`PromptPolicyUnitTest`)
- facial emotion snapshot fact extraction in default aggregator
- outer/inner path-based event routing + single-write behavior

Web MVC compatibility tests include:

- text chat endpoints
- realtime acknowledge/prompt flow
- monitor endpoints including SSE monitor stream
- static redirect coverage for `/monitor`, `/realtime`, `/visual/facial`, and `/visual/social`

## Multimodal MVP: Visual Facial Client

An initial browser-based visual client is available at:

- `/visual/facial?agentId={UUID}`
- `/visual/nonverbal?agentId={UUID}`
- `/visual/social?agentId={UUID}`

Capabilities in this MVP:

- webcam capture on the client side
- browser-side face + expression inference via `face-api.js`
- derived emotion signal with simple valence/arousal estimation
- throttled/hysteresis-based emission of observation events to avoid flooding
- observation emission only (no behaviour generation trigger)

Event emitted by this client:

- `type = obs.emotion.face`
- `actor = user`
- `kind = observation`
- `payload = JSON string` with emotion, confidence, valence, arousal, expression distribution, and timestamp

## Multimodal MVP: Visual Social Client

An initial browser-based social situation client is available at:

- `/visual/social?agentId={UUID}`

Capabilities in this MVP:

- wide-scene webcam capture
- browser-side person detection
- lightweight ID tracking across frames
- rule-based nearby group inference from person proximity
- throttled/hysteresis-based observation emission to avoid flooding

Events emitted by this client:

- `type = obs.human.presence`
- `type = obs.social.grouping`
- `actor = user`
- `kind = observation`
- `payload = JSON string` with counts such as `humanCount`, `groupCount`, `singletonCount`, `largestGroupSize`, plus timestamp and tracking metadata

What is implemented so far:

- static route and redirect support: `/visual/social` -> `/visual/social/index.html`
- browser-based person detection (`coco-ssd`) in the social visual client
- lightweight frame-to-frame person tracking with stable short-lived IDs
- proximity-based grouping heuristic (group and singleton derivation)
- throttled change-based event emission to avoid flooding
- backend event constants:
  - `Event.TYPE_HUMAN_PRESENCE` (`obs.human.presence`)
  - `Event.TYPE_SOCIAL_GROUPING` (`obs.social.grouping`)
- snapshot aggregation support in `DefaultObservationSnapshotAggregator` for:
  - `human_presence_total_count`
  - `social_grouping_total_count`
  - `social_current_human_count`
  - `social_current_group_count`
  - `social_current_singleton_count`
  - `social_current_largest_group_size`
  - `social_group_count_trend`
- focused automated coverage:
  - redirect coverage for `/visual/social`
  - snapshot fact extraction assertions for social observations

## Multimodal MVP: Visual Nonverbal Client

An initial browser-based nonverbal behaviour renderer is available at:

- `/visual/nonverbal?agentId={UUID}`

Capabilities in this MVP:

- subscribes to `/{agentId}/behaviour/stream` via SSE
- reads `resp.behaviour_plan` events and parses `payload.nonVerbal`
- maps gesture labels to emoji renderer output:
  - `OPEN_QUESTION` -> open-question gesture emoji
  - `EXPLAIN` -> explanatory-sweep gesture emoji
  - `UNCERTAIN` -> uncertainty-shrug gesture emoji
  - `ACKNOWLEDGE` -> acknowledgement-close-hands gesture emoji
  - `POLITE` -> polite-apology gesture emoji
- acts as a passive behaviour renderer (connect/disconnect stream only)

LLM-facing interpretation for this event (current default):

- mapped to concise text such as `User facial emotion: happy (confidence 0.83)` via `FaceEmotionPromptEventContentAdapter`

## Future Work Priorities (Near-Term)

This section tracks practical next steps that build directly on the current multimodal MVP baseline.

### 1. Social Sensing Maturation

- add camera-profile support for wide-angle/fisheye devices (for example Insta360) with configurable dewarp/crop regions
- stabilize multi-person tracking across occlusions and rapid motion (ID persistence + confidence decay)
- refine group inference beyond proximity only (orientation, dwell time, movement coherence)
- introduce optional zone awareness (near/mid/far, left/center/right) in social observation payloads

### 2. Event And Snapshot Contracts For Social Context

- standardize payload schemas for:
  - `obs.human.presence`
  - `obs.social.grouping`
  - future `obs.social.interaction`
- extend snapshot facts for temporal social dynamics, for example:
  - sustained crowding
  - group split/merge frequency
  - interaction persistence windows
- define confidence and hysteresis conventions for social observations similar to facial emotion abstraction

### 3. Prompt And Policy Integration

- add social-event prompt content adapters so LLM prompts consume compact, stable social summaries instead of raw payloads
- add context augmenters for social situation summaries (current counts + trends + recent changes)
- provide policy examples where state transitions respond to social context (for example group-present vs. one-on-one modes)

### 4. Multi-Actor Runtime Preparation

- align social observation events with Iteration 8 goals (actor/group-scoped event semantics)
- add selector patterns and tests for group-level retrieval in nested state paths
- define authority boundaries for social interrupts versus task flow (to align with Iteration 6 policy gate work)

### 5. Validation, Monitoring, And Replay

- add deterministic test traces for social observation sequences (enter/leave, group formation, group split)
- extend monitor UI to visualize social facts and event-rate diagnostics
- add replay fixtures that include multimodal social + facial observations for regression checking

### 6. Safety, Privacy, And Operational Guardrails

- document data-minimization defaults (no raw image persistence in core runtime)
- add configurable rate limits for high-density scenes to avoid event floods
- add runtime toggles for social sensing modules to support graceful degradation on low-resource deployments

Manual seed tests:

- `src/test/java/ch/zhaw/prometheus/agents/VerbalAgent.java`
- `src/test/java/ch/zhaw/prometheus/agents/MultiModalAgent.java`
- intentionally `@Disabled` and run manually for seeding

## Iteration Summary

### Iteration 1 - Event-Based Interaction (done)

- unified `Event` model
- single per-agent shared event history
- policy externalization (`Policy` / `PromptPolicy`)
- responses represented as events
- selector-based history access

### Iteration 2 - BehaviourPlan Output (done)

- added `BehaviourPlan`
- full-plan response payloads without server-side channel reduction
- response events use `resp.behaviour_plan`

### Iteration 3 - Observation Snapshots (done, core)

- snapshot/fact model over selected event histories
- pluggable `SnapshotAggregator` + default implementation
- `Decision` / `Action` snapshot-aware overloads

### Iteration 4 - Continuous Evaluation (done, core)

- scheduler/tick runtime source
- `Agent.tick()` API

### Iteration 5 - Regulation Runtime Integration (done, core slice)

- `RegulationSystem`, `RegulationContext`, `RegulationResult`, `ModulationBundle`
- `RegulationPolicy` + `RegulationEffect`
- `NoOpRegulationSystem` default
- Zurich reference implementation in commons
- explicit internal regulation events:
  - `int.regulation.opportunity`
  - `int.regulation.interrupt.soft`
  - `int.regulation.interrupt.hard`

### Post-Iteration-5 Runtime Cleanup (done)

- centralized event recording in `Agent`
- path-based event routing (`statePath`)
- removal of state/outer-state append side effects
- de-duplication of nested conversation events

## Roadmap (Next)

### Iteration 6 - Interrupts and Policy Gate

- interrupt severity handling
- policy gate for initiative/interrupt arbitration
- cooldown and hysteresis
- deliverable: no spurious offers, safe preemption

### Iteration 7 - SupportProvisioning Modules

- provisioning module abstraction
- execution monitoring
- abort semantics

### Iteration 8 - Multi-Actor and Group Support

- actor/group-scoped events
- group-level selectors and permissions
- display-oriented behaviour plans

### Iteration 9 - Warehouse Safe Passage Agent

- motion regimes as task states
- safety-driven regulation

### Iteration 10 - Capability Negotiation and Realizers

- capability discovery
- channel-specific realizers
- graceful degradation

### Iteration 11 - Monitoring and Replay Tooling

- rich monitoring UI
- event trace replay
- deterministic regression support

## Naming And Identity

- Spring Boot entry point: `PrometheusApplication`
- Maven artifact/name: `prometheus`
- `spring.application.name=prometheus` configured in template/prod properties

