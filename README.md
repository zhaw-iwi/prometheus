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

- `TransferEventHistoryAction` remains as an explicit no-op for transition compatibility.
- With shared runtime-owned history, explicit history transfer is no longer required.

## Current Runtime Contracts

- Inputs are `Event` objects with core fields: `type`, `actor`, `kind`, `payload`.
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
- Internal `sys.tick` events support no-input evaluation cycles.
- Optional runtime scheduler can tick active agents:
  - `prometheus.runtime.tick.enabled`
  - `prometheus.runtime.tick.delay-ms`
- Manual one-cycle no-input endpoint:
  - `POST /{agentId}/tick`
- Agents can host a `RegulationSystem` (default is no-op).
- `ZurichRegulationSystem` emits explicit internal events (e.g. `int.regulation.opportunity`).
- Monitor stream is SSE via `/{agentId}/monitor/stream`.
- Log stream is SSE via `/logs/stream`.

## Behaviour + Prompt Pipeline

- `Policy.onStart(...)` and `Policy.onRespond(...)` return `BehaviourPlan`.
- `State` executes policy and returns response events; runtime persists them.
- Prompt execution uses OpenAI chat mapping from event stream (`role` + `content`) in `LMOpenAI`.

## Event Model Notes

- Historical single-state labeling (`stateName`) was replaced by `statePath` semantics.
- `Event#getStateName()` still resolves to the leaf item of `statePath` for compatibility in call sites still using leaf semantics.
- New code should prefer path semantics for retrieval/routing decisions.

## Testing Status

Automated coverage currently includes:

- `BehaviourPlan` serialization and emptiness
- `EventSelector` composition/filtering
- state/transition behavior-plan emission and selector behavior
- snapshot aggregation and selector-based fact extraction
- snapshot-aware transition decisions/actions
- agent tick/no-input progression
- continuous scheduler processing (active agents only)
- Zurich regulation dynamics and regulation-to-transition integration
- OpenAI message mapping from events
- outer/inner path-based event routing + single-write behavior

Web MVC compatibility tests include:

- text chat endpoints
- realtime acknowledge/prompt flow
- monitor endpoints including SSE monitor stream

Manual seed tests:

- `src/test/java/ch/zhaw/prometheus/agents/OpenHealthCoaching.java`
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
- `POST /{agentId}/tick` endpoint

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
