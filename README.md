# Prometheus

Prometheus is an event-driven, regulation-aware agent framework evolving from PROMISE and SBR.

Current focus:
- Event-based inputs and event history
- Explicit state-machine task control
- Testable, incremental roadmap toward multimodal BehaviourPlans and regulation

Status
- Iteration 1 complete: Event model replaces utterances for input and history
- Iteration 2 next: BehaviourPlan output abstraction

## Roadmap (Iterative Development)

Each iteration ends with something runnable and testable.

Iteration 1 - Event-Based Input (complete)
- Introduce Event as the core input type
- Replace utterance history with event history
- Map text input to obs.user_utterance events
- Deliverable: conversational agent works end-to-end using events

Iteration 2 - BehaviourPlan Output (next)
- Introduce BehaviourPlan as the output abstraction
- Add a simple speech-only renderer
- Deliverable: same conversational agent, new output abstraction

Iteration 3 - Observation Snapshots
- Snapshot aggregation over events
- Fact extraction helpers
- Deliverable: guards/decisions based on facts, not raw events

Iteration 4 - Continuous Evaluation
- Scheduler or tick events
- Periodic evaluation hooks
- Deliverable: agents react even without new input

Iteration 5 - Regulation Runtime Integration
- RegulationSystem interface
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
- Actor- and group-scoped events
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
- Event trace replay
- Deterministic testing
- Deliverable: full observability and regression testing
