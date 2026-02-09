# Prometheus

Prometheus is an event-driven, regulation-aware agent framework evolving from PROMISE and SBR.

Current focus:

- Event-based inputs and event history
- Explicit state-machine task control
- Testable, incremental roadmap toward multimodal BehaviourPlans and regulation

Status

- Iteration 1 complete: Events, shared history, policies, and response-as-event workflows
- Iteration 2 complete: BehaviourPlan output abstraction with speech-only rendering
- Iteration 3 next: Observation snapshots

## Current Runtime Contracts

- Inputs are `Event` objects (`type`, `actor`, `kind`, `content`, `payload`, `stateName`).
- Assistant outputs are `resp.behaviour_plan` events.
- Assistant speech is represented in BehaviourPlan payload (`payload.speech`), with `content` available as rendered preview/fallback.
- `BehaviourPlan` is the output abstraction; `SpeechOnlyRenderer` is the default renderer.
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
  - `LMOpenAI` event-to-chat-message mapping
- Web MVC compatibility tests:
  - chat endpoints
  - realtime acknowledge/prompt flow
  - monitor endpoints including SSE monitor stream
- Manual seed tests:
  - `src/test/java/ch/zhaw/prometheus/agents`
  - intentionally `@Disabled` and run manually when seeding agents

## Iteration 3 Entry Criteria (next)

Iteration 3 should introduce snapshot/fact abstractions without breaking current event-first flow:

- Add snapshot model(s) derived from event history with explicit provenance and confidence.
- Add snapshot extraction/aggregation service behind interface(s).
- Keep decisions/actions able to use raw event selectors while enabling snapshot access.
- Add deterministic unit tests for snapshot construction and decision consumption.
- Keep current client contracts stable (`resp.behaviour_plan`, monitor SSE).

Non-goals for Iteration 3:

- No scheduler/tick runtime (Iteration 4).
- No regulation runtime integration (Iteration 5).
- No policy-gate/interrupt authority model changes (Iteration 6).

## Roadmap (Iterative Development)

Each iteration ends with something runnable and testable.

Iteration 2 - BehaviourPlan Output (done)

- Introduce BehaviourPlan as the output abstraction (as event payload)
- Replace text response events with BehaviourPlan events (speech + optional non-verbal)
- Add a simple speech-only renderer
- Deliverable: same conversational agent, BehaviourPlan-driven responses

Iteration 3 - Observation Snapshots (next)

- Snapshot aggregation over events into explicit snapshot/fact artifacts
- Fact extraction helpers and confidence handling
- Decisions/actions use snapshots in addition to raw events
- Deliverable: guards/decisions based on facts, not raw events

Iteration 4 - Continuous Evaluation

- Scheduler or tick events
- Periodic evaluation hooks
- Deliverable: agents react even without new input

Iteration 5 - Regulation Runtime Integration

- RegulationSystem interface consumes events and emits modulation + control events
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
