# Roadmap – Iterative Development from a PROMISE Clone

This roadmap assumes:
- A fresh repository cloned from PROMISE
- The SBR extension package is integrated early
- Code-level backward compatibility is not required

Each iteration ends with **something runnable and testable**.

---

## Iteration 1 – Event-Based Input

**Goal**  
Replace utterances with a unified Event model.

**Key Changes**
- Introduce `Event` as the core input type
- Replace utterance history with event history
- Map text input to `obs.user_utterance` events

**Deliverable**
- Conversational agent works end-to-end using events

---

## Iteration 2 – BehaviourPlan Output

**Goal**  
Generalise output beyond text.

**Key Changes**
- Introduce `BehaviourPlan`
- Add simple speech-only renderer

**Deliverable**
- Same conversational agent, new output abstraction

---

## Iteration 3 – Observation Snapshots

**Goal**  
Support stable decision-making from noisy events.

**Key Changes**
- Snapshot aggregation
- Fact extraction helpers

**Deliverable**
- Guards and decisions based on facts, not raw events

---

## Iteration 4 – Continuous Evaluation

**Goal**  
Enable deliberation without turn-taking.

**Key Changes**
- Scheduler or tick events
- Periodic evaluation hooks

**Deliverable**
- Agents react even without new input

---

## Iteration 5 – Regulation Runtime Integration

**Goal**  
Reintroduce SBR as a native runtime component.

**Key Changes**
- RegulationSystem interface
- Tanks with decay
- Modulation bundles
- Internal control events

**Deliverable**
- Door Assist initiative triggered by regulation

---

## Iteration 6 – Interrupts and Policy Gate

**Goal**  
Stabilise authority boundaries.

**Key Changes**
- Interrupt severity handling
- PolicyGate for initiative and interrupts
- Cooldown and hysteresis

**Deliverable**
- No spurious offers, safe preemption

---

## Iteration 7 – SupportProvisioning Modules

**Goal**  
Support action execution and invalidation.

**Key Changes**
- ProvisioningModule abstraction
- Execution monitoring
- Abort semantics

**Deliverable**
- Phone retrieval with graceful abort

---

## Iteration 8 – Multi-Actor and Group Support

**Goal**  
Enable meeting monitoring scenarios.

**Key Changes**
- Actor- and group-scoped events
- Display-oriented BehaviourPlans

**Deliverable**
- Meeting monitor agent with dashboards

---

## Iteration 9 – Warehouse Safe Passage Agent

**Goal**  
Validate non-conversational, safety-critical control.

**Key Changes**
- Motion regimes as task states
- Safety-driven regulation

**Deliverable**
- Warehouse navigation controller in simulation

---

## Iteration 10 – Capability Negotiation and Realizers

**Goal**  
Finalize multimodal support.

**Key Changes**
- Capability discovery
- Channel-specific realizers
- Graceful degradation

**Deliverable**
- Same agent runs as chat, robot, or display system

---

## Iteration 11 – Monitoring and Replay Tooling

**Goal**  
Make the framework engineerable.

**Key Changes**
- Rich monitoring UI
- Event trace replay
- Deterministic testing

**Deliverable**
- Full observability and regression testing

---

## End State

A unified framework that:
- Evolves naturally from PROMISE
- Integrates SBR cleanly
- Supports conversational, embodied, and ambient agents
- Is testable, inspectable, and extensible