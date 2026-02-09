# Prometheus

Prometheus is an event-driven, regulation-aware agent framework evolving from PROMISE and SBR.

Current focus:

- Event-based inputs and event history
- Explicit state-machine task control
- Testable, incremental roadmap toward multimodal BehaviourPlans and regulation

Status

- Iteration 1 complete: Events, shared history, policies, and response-as-event workflows
- Iteration 2 complete: BehaviourPlan output abstraction with speech-only rendering
- Iteration 3 complete (core): Observation snapshots and fact-based transition hooks
- Iteration 4 complete (core): Runtime tick-driven continuous evaluation
- Next step: Iteration 5 (Regulation Runtime Integration)

## Current Runtime Contracts

- Inputs are `Event` objects (`type`, `actor`, `kind`, `content`, `payload`, `stateName`).
- Assistant outputs are `resp.behaviour_plan` events.
- Assistant speech is represented in BehaviourPlan payload (`payload.speech`), with `content` available as rendered preview/fallback.
- `BehaviourPlan` is the output abstraction; `SpeechOnlyRenderer` is the default renderer.
- Snapshot contracts are available via `SnapshotAggregator`, `ObservationSnapshot`, `Fact`, and selector-based `FactExtractor` helpers.
- `Transition` now builds snapshots from selected events and passes them into decisions/actions.
- Internal `sys.tick` events are supported for no-input evaluation cycles.
- Optional runtime scheduler can tick active agents (`prometheus.runtime.tick.enabled`, `prometheus.runtime.tick.delay-ms`).
- Manual one-cycle no-input evaluation endpoint is available at `POST /{agentId}/tick`.
- Prompt execution uses OpenAI chat message mapping from events (`role` + `content`) in `LMOpenAI`.
- Monitor client state/storage/active updates are SSE-driven via `/{agentId}/monitor/stream`.
- Log monitoring is SSE-driven via `/logs/stream`.

## Naming And Identity

- Spring Boot entry point is `PrometheusApplication`.
- Maven artifact/name is `prometheus`.
- `spring.application.name=prometheus` is configured in template/prod properties.

## Iteration 1 - Event-Based Interaction (done)

- Unified `Event` model (type/actor/kind/content/payload/stateName).
- Single per-agent event history with state-scoped filtering.
- States read/write shared history; per-state histories removed.
- Policies externalized (`Policy` / `PromptPolicy`); decisions/actions/states use policies.
- Responses are modeled as events and appended to the shared event history.
- Event selectors introduced for history selection (state/actor/kind).
- Prompt-facing APIs and views use policy terminology (`PolicyResult`, `PolicyResponseView`, `getTotalPolicy`).

## Iteration 2 - BehaviourPlan Output (done)

- Added `BehaviourPlan` abstraction and default `SpeechOnlyRenderer`.
- `Policy.onStart(...)` and `Policy.onRespond(...)` return `BehaviourPlan`.
- `State` emits assistant `resp.behaviour_plan` events with serialized plan payload.
- Frontend rendering uses payload-first speech extraction.
- LM adapter maps events to OpenAI chat messages with explicit role/content mapping.

## Testing Status

- Unit tests:
  - `BehaviourPlan` serialization/emptiness
  - `EventSelector` composition/filtering
  - `State`/`Transition` behaviour-plan emission and selector semantics
  - Snapshot aggregation facts and selector-based fact extraction
  - Snapshot-aware transition decisions/actions
  - Agent tick/no-input progression and tick-triggered transitions
  - Continuous scheduler cycle processing (active agents only)
  - `LMOpenAI` event-to-chat-message mapping
- Web MVC compatibility tests:
  - chat endpoints
  - realtime acknowledge/prompt flow
  - monitor endpoints including SSE monitor stream
- Manual seed tests:
  - `src/test/java/ch/zhaw/prometheus/agents`
  - intentionally `@Disabled` and run manually when seeding agents

## Iteration 3 Snapshot Design (implemented)

Iteration 3 is implemented with an additive design that keeps the event-first flow intact:

- Snapshot model from selected `EventHistory` with explicit `Fact` objects (`key`, `value`, `confidence`, `provenance`).
- Pluggable `SnapshotAggregator` interface and default implementation (`DefaultObservationSnapshotAggregator`).
- Reusable selector-first fact helpers (`FactExtractors`) so developers can define snapshot content using `EventSelector` composition.
- `Decision` and `Action` now support snapshot-aware overloads while keeping existing raw-event methods.
- `Transition` computes snapshots from the same selected history already used for decisions/actions.
- Client/runtime contracts remain stable (`resp.behaviour_plan`, monitor SSE).

Non-goals for Iteration 3:

- No scheduler/tick runtime (Iteration 4).
- No regulation runtime integration (Iteration 5).
- No policy-gate/interrupt authority model changes (Iteration 6).

## Iteration 4 Continuous Evaluation (implemented core)

Implemented Iteration 4 core:

- Runtime-level scheduler/tick source outside state machine and regulation.
- `Agent.tick()` API to run one no-input evaluation cycle through the existing event pipeline.
- `POST /{agentId}/tick` endpoint for deterministic/manual triggering.
- Tick-driven transitions and behaviours are supported via normal decisions/actions and snapshot hooks.
- Deterministic tests cover no-input progression and scheduler processing.

Authority split for Iteration 4/5:

- Runtime provides time (`tick` events).
- Regulation (Iteration 5) consumes ticks and observations to update motivational dynamics.
- State machine remains control authority and reacts to explicit internal events emitted by regulation.

## Next Step - Iteration 5 (Regulation Runtime Integration)

Planned Iteration 5 focus:

- Introduce `RegulationSystem` as runtime component consuming observations/ticks.
- Integrate Zurich-model-like latent motivation dynamics (deficit/excess over time).
- Emit explicit internal control events and modulation bundles for state-machine consumption.
- Keep authority boundaries explicit: regulation suggests, state machine decides.

## Roadmap (Iterative Development)

Each iteration ends with something runnable and testable.

Iteration 2 - BehaviourPlan Output (done)

- Introduce BehaviourPlan as the output abstraction (as event payload)
- Replace text response events with BehaviourPlan events (speech + optional non-verbal)
- Add a simple speech-only renderer
- Deliverable: same conversational agent, BehaviourPlan-driven responses

Iteration 3 - Observation Snapshots (done, core)

- Snapshot aggregation over events into explicit snapshot/fact artifacts
- Fact extraction helpers and confidence handling
- Decisions/actions use snapshots in addition to raw events
- Deliverable: guards/decisions based on facts, not raw events

Iteration 4 - Continuous Evaluation

- Runtime scheduler/tick events (framework-level, not state-machine-specific)
- Periodic evaluation hooks via explicit internal events
- Deliverable: agents react even without new input

Iteration 5 - Regulation Runtime Integration

- RegulationSystem interface consumes observations/ticks and emits modulation + control events
- Tanks with decay
- Modulation bundles
- Internal control events
- Deliverable: Door Assist initiative triggered by regulation

Iteration 6 - Interrupts and Policy Gate

- Interrupt severity handling
- PolicyGate for initiative and interrupts
- Cooldown and hysteresis
- Deliverable: no spurious offers, safe preemption

Iteration 7 - SupportProvisioning Modules

- ProvisioningModule abstraction
- Execution monitoring
- Abort semantics
- Deliverable: phone retrieval with graceful abort

Iteration 8 - Multi-Actor and Group Support

- Actor- and group-scoped events (beyond single-actor)
- Group-level selectors and permissions
- Display-oriented BehaviourPlans
- Deliverable: meeting monitor agent with dashboards

Iteration 9 - Warehouse Safe Passage Agent

- Motion regimes as task states
- Safety-driven regulation
- Deliverable: warehouse navigation controller in simulation

Iteration 10 - Capability Negotiation and Realizers

- Capability discovery
- Channel-specific realizers
- Graceful degradation
- Deliverable: same agent runs as chat, robot, or display system

Iteration 11 - Monitoring and Replay Tooling

- Rich monitoring UI
- Event trace replay (enabled by event-first design)
- Deterministic testing
- Deliverable: full observability and regression testing
