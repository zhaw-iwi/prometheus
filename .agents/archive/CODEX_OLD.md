# CODEX.md – Engineering Guide for the New Framework

This repository is an iterative re-engineering of PROMISE plus the SBR extension into a new, event-driven, regulation-aware agent framework.

The goal is to preserve what made PROMISE effective for engineering controllable agents, while generalizing beyond turn-based text interaction to support multimodal perception and multimodal behaviour across conversational, embodied, and ambient agent applications.

---

## How CODEX Should Work in This Repository

### Engineering Priorities

All implementation work must prioritize strong software engineering design in **Java** with **Spring Boot**:

- Reusability through clean abstractions and stable interfaces
- Encapsulation of responsibilities, avoiding shared mutable state across components
- Modularity so features can be added or removed without cascading changes
- Extensibility via pluggable components, not hard-coded branching
- Orthogonality so concerns stay separated and composition stays explicit
- Testability and traceability, especially for control decisions and interrupts
- Maintainable API design with clear DTO boundaries and consistent naming

Prefer designs that minimize coupling and maximize clarity, even if slightly more code is required.

---

## Implementation Principles

### 1. Preserve Explicit Control

The **task control layer** remains an explicit, inspectable state machine:
- states, transitions, guards, actions
- nested and supervisory machines are allowed
- all control effects must be observable and traceable

No hidden control logic may be embedded solely in prompt text.

---

### 2. Generalize Interaction to Events

All inputs and internal signals are expressed as **Events**:
- external observations such as text, vision, audio, system signals
- internal events such as interrupts, opportunities, timers, ticks

Conversation turns are just one event type.

---

### 3. Generalize Outputs to Behaviour Plans

All outputs are expressed as **BehaviourPlans**:
- speech intent
- non-verbal intent
- motion intent
- display and advisory intent

Rendering is capability-dependent and must degrade gracefully.

---

### 4. Keep Regulation First-Class and Bounded

Regulation systems:
- fuse events into latent state over time
- output modulation bundles that shape expression
- emit explicit internal control events such as opportunities and interrupts

Regulation must not silently override task control.
All coupling must be explicit through defined interfaces and priority rules.

---

### 5. Design for Iteration and Regression Testing

Every iteration must end with:
- a runnable example agent
- a deterministic test or trace replay that verifies state transitions and outputs

Prefer adding trace replay early and using it as the main regression mechanism.

---

## Repository Workflow for CODEX

When implementing roadmap iterations:

- Introduce new abstractions behind interfaces
- Provide default implementations that preserve current behaviour
- Migrate existing code incrementally, avoiding large rewrites per iteration
- Add unit tests and trace-based tests whenever a new abstraction is introduced
- Keep public REST endpoints stable where possible, but code-level compatibility is not required

All modifications should move the codebase closer to the target architecture described in the documentation files included in this repository.

---

## Definition of Done for Each Change

A change is complete only if:

- It follows the stated engineering priorities
- It is modular and does not hardwire special cases
- It has at least one focused automated test
- It does not reduce observability of control decisions
- It does not break the current iteration’s runnable examples

---

## Next Sections

The remainder of this document is assembled from the following files:
- Overall design idea and vision
- Use cases and canonical scenarios
- High-level requirements
- Iterative roadmap starting at Iteration 1
- Notes about existing PROMISE and SBR codebases

CODEX should treat these documents as the source of truth when implementing the framework.


# New Framework – Overall Idea and Design Vision

## Motivation

The original PROMISE framework demonstrated that **explicit state-machine-based control** is an effective way to engineer complex, controllable conversational agents using language models.  
The Social Behaviour Regulation (SBR) extension further showed that **adaptive social behaviour** can be layered on top of task-oriented behaviour using regulation models such as the Zurich Model of Social Motivation.

However, both frameworks are currently biased toward:
- verbal input and output,
- turn-based conversational interaction,
- reactive behaviour triggered primarily by user utterances.

Many emerging agent scenarios require:
- proactive and deliberative behaviour,
- multimodal perception and action,
- continuous evaluation without turn boundaries,
- socially appropriate disengagement and yielding,
- agents that may never speak at all.

This new framework generalises PROMISE into an **event-driven, regulation-aware agent control framework** that supports conversational agents, embodied robots, and ambient intelligence systems under a single conceptual model.

---

## Core Design Principles

### 1. Explicit Task Control

Primary agent behaviour is modelled as **explicit state machines**:
- states represent behavioural regimes,
- transitions are triggered by events,
- guards and actions remain inspectable and testable,
- nested and supervisory state machines are supported.

This preserves PROMISE’s main strength: **designability and controllability**.

---

### 2. Event-Driven Interaction

All interaction is driven by a **unified event model**.

Events may represent:
- user utterances,
- detected emotions or gaze,
- physical world observations,
- internal regulation signals,
- timers or periodic ticks.

Conversation turns become a **special case** of events rather than the core abstraction.

---

### 3. Regulation as a First-Class Component

One or more **regulation systems** operate alongside task control.

A regulation system:
- integrates multimodal signals over time,
- maintains latent internal variables (tanks, traits, drives),
- outputs:
  - modulation bundles (how behaviour is expressed),
  - internal control events (opportunities, interrupts).

Regulation **does not replace** task control.  
It **shapes and constrains** task execution.

---

### 4. Behaviour as Multi-Channel Intent

Agent output is expressed as a **BehaviourPlan**, not raw text.

A BehaviourPlan may include:
- speech intent,
- non-verbal signals (gesture, gaze, posture),
- motion intent,
- display or advisory output.

Actual rendering depends on available capabilities.  
A verbal-only agent is a valid degenerate case.

---

### 5. Explicit Coupling and Authority Boundaries

The interaction between task control and regulation is **explicit and bounded**:
- regulation may suggest initiative,
- regulation may emit interrupts with severity levels,
- hard constraints and policies always dominate soft adaptation.

This avoids implicit, hard-to-debug behaviour changes.

---

## What This Framework Is

- A **general agent control framework**, not a chatbot framework
- Suitable for:
  - conversational AI,
  - embodied robots,
  - ambient and advisory agents,
  - safety-critical social navigation
- Designed for **engineering**, testing, and iteration

---

## What This Framework Is Not

- Not an end-to-end planner or autonomy stack
- Not a reinforcement learning framework
- Not tied to any single psychological model
- Not limited to language-based interaction

---

## Key Outcome

A single framework that allows developers to engineer agents that:
- do useful tasks,
- adapt socially and contextually,
- act, wait, withdraw, or remain silent when appropriate,
- and remain predictable and testable.


# Application Use Cases and Canonical Scenarios

This document describes **canonical application scenarios** that guide the design and serve as acceptance tests for the framework.

Each scenario stresses different aspects of agent behaviour while sharing the same underlying abstractions.

---

## 1. Conversational Health Check-In (Baseline)

**Description**  
A digital agent conducts a daily check-in with a patient, assessing wellbeing and therapy adherence.

**Key Properties**
- Purely verbal interaction
- Turn-based
- Uses state machine prompts and transitions
- Optional escalation to healthcare professionals

**Why It Matters**
- Ensures backward compatibility with PROMISE-style conversational agents
- Defines the minimal viable agent

---

## 2. Door Assist in Hospital Room

**Description**  
A robot observes a healthcare professional approaching a closed door with hands full.  
It offers to open the door, waits for consent, executes the action, or aborts gracefully.

**Key Properties**
- Proactive initiative
- Explicit consent gate
- Multimodal perception
- Physical action execution
- Socially appropriate disengagement

**What It Tests**
- Regulation-driven initiative
- Interrupt handling
- SupportProvisioning modules
- Multi-channel BehaviourPlans

---

## 3. Phone Retrieval with Plan Invalidation

**Description**  
A robot notices a patient dropping their phone and moves to retrieve it.  
A relative picks up the phone first, invalidating the plan.

**Key Properties**
- No explicit consent
- Continuous execution monitoring
- Plan invalidation
- Graceful withdrawal without embarrassment

**What It Tests**
- Execution monitoring
- Abort as a successful outcome
- Social disengagement semantics

---

## 4. Warehouse Robot Safe Passage

**Description**  
A mobile robot navigates a warehouse aisle and encounters a human worker.  
Based on human activity and intent prediction, it decides to slow, yield, stop, or proceed.

**Key Properties**
- Continuous perception
- Safety-critical
- No conversation required
- Motion and signaling output only

**What It Tests**
- Event-driven control without turn-taking
- Regulation-based risk assessment
- Discrete motion regimes
- Hard interrupts for safety

---

## 5. Meeting Monitoring and Facilitation Support

**Description**  
A digital agent monitors a group meeting and detects suboptimal dynamics such as dominance or stagnation.  
It updates shared summaries and provides private recommendations to a facilitator.

**Key Properties**
- Multi-actor perception
- No direct interaction ownership
- Advisory and ambient output
- No speech required

**What It Tests**
- Group-level regulation
- Non-intrusive agents
- Display-oriented BehaviourPlans
- Continuous evaluation

---

## Canonical Scenario Set

The framework must support all scenarios **without changing core abstractions**.

| Scenario | Speech | Motion | Regulation | Initiative | Abort |
|--------|--------|--------|------------|------------|-------|
| Check-in | yes | no | yes | no | yes |
| Door Assist | yes | yes | yes | yes | yes |
| Phone Retrieval | optional | yes | yes | yes | yes |
| Warehouse | no | yes | yes | yes | yes |
| Meeting Monitor | no | no | yes | yes | soft only |

---

## Acceptance Criterion

If the framework can implement all scenarios using:
- the same event model,
- the same state machine abstraction,
- interchangeable regulation systems,

then the framework is considered complete.


# High-Level Requirements for the New Framework

## 1. Architectural Requirements

R1. The framework must be event-driven, not turn-driven.  
R2. Task behaviour must be modelled as explicit state machines.  
R3. Regulation systems must be first-class components.  
R4. Task control and regulation must be separable and explicitly coupled.  
R5. Verbal-only agents must remain fully supported.

---

## 2. Event and Perception Requirements

R6. Unified event schema with provenance and confidence.  
R7. Support aggregation of events into stable observation snapshots.  
R8. Pluggable perception providers (text, vision, audio, system signals).  
R9. Support evaluation without new user input (ticks, thresholds).

---

## 3. Behaviour Output Requirements

R10. Behaviour must be expressed as multi-channel BehaviourPlans.  
R11. Rendering must depend on available capabilities.  
R12. Non-verbal behaviour must be modular and optional.

---

## 4. Regulation Requirements

R13. Regulation systems must maintain latent state with decay and thresholds.  
R14. Regulation must output both modulation bundles and control events.  
R15. Regulation implementations must be interchangeable.  
R16. Multiple regulation systems must be composable.

---

## 5. Initiative and Interrupt Requirements

R17. Interrupts must support severity levels.  
R18. Hard interrupts must preempt any task state.  
R19. Initiative must be gated and rate-limited.

---

## 6. Execution and Planning Requirements

R20. SupportProvisioning modules must be reusable and selectable.  
R21. Execution must be continuously monitored and cancellable.  
R22. Disengagement must be a first-class outcome.

---

## 7. PROMISE Compatibility Requirements

R23. State and transition prompt orchestration must remain supported.  
R24. Decisions and actions must generalise to event-based evaluation.  
R25. All transitions must be traceable to events and prompts.

---

## 8. Developer Experience Requirements

R26. Minimal boilerplate for simple conversational agents.  
R27. Declarative configuration preferred over imperative logic.  
R28. Deterministic simulation and replay support.  
R29. Rich monitoring and introspection tooling.

---

## 9. Safety and Policy Requirements

R30. Explicit policy constraints must be expressible.  
R31. Hard constraints must override social adaptation.

---

## 10. Non-Functional Requirements

R32. Deterministic behaviour where possible.  
R33. Support real-time and low-latency scenarios.  
R34. Extensible without modifying core engine.


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


# What CODEX Should Know About PROMISE and SBR Before Implementing the New Framework

This document summarizes **what is essential to understand about the existing PROMISE framework and the Social Behaviour Regulation (SBR) extension** so that CODEX can work effectively when implementing the new roadmap.

CODEX will have full access to the PROMISE and SBR codebases.  
This document therefore does **not** restate the code, but explains **how the frameworks think**, where assumptions are baked in, and where extensions or refactorings must be handled carefully.

---

## 1. PROMISE – Conceptual and Architectural Summary

### 1.1 What PROMISE Fundamentally Is

PROMISE is an **explicit state-machine-based interaction framework** for language-model-driven agents.

Key characteristics:

- The **state machine is the primary control structure**
- States represent **interaction regimes**
- Transitions represent **explicit decision points**
- Language models are used *inside* states and transitions, not as controllers
- Control logic is **inspectable, testable, and developer-authored**

PROMISE is *not* a dialogue manager in the classical sense.  
It is closer to a **programming model for interactions**, where LLMs are used as semantic evaluators and generators.

---

### 1.2 Core PROMISE Concepts CODEX Must Respect

#### Agent
- Wraps a state machine
- Owns:
  - current state
  - conversation history
  - storage
- Provides lifecycle methods:
  - `start()`
  - `respond(...)`
  - `rerespond()`
  - `reset()`

In the new framework, `respond()` will generalize to **event ingestion**, but the lifecycle concept remains valid.

---

#### State
- Has:
  - a **state prompt** (role + behavior)
  - a **starter prompt**
  - a list of outgoing transitions
- Is responsible for **producing agent output** when active

Important:
- PROMISE currently assumes **textual utterances** as input
- States append prompts hierarchically (outer states prepend prompts)

This assumption must be relaxed, but the **hierarchical prompt composition mechanism is a core asset** and should be preserved.

---

#### Transition
- Has:
  - one or more **Decision** objects (triggers and guards)
  - zero or more **Action** objects
  - a target state
- Decisions and actions are **LLM-driven**

Important:
- Transitions are evaluated **after user input**
- Decisions operate on **conversation history**

In the new framework:
- Decisions must generalize to operate on **events and snapshots**
- Transition evaluation must not depend solely on user utterances

---

#### Decision
- Encapsulates a prompt that asks the LLM to decide something
- Returns boolean or categorical outcomes

Important:
- Decisions are **pure evaluation**, no side effects
- CODEX should preserve this separation

---

#### Action
- Encapsulates an LLM prompt that extracts or produces structured data
- Often writes to `Storage`

Important:
- Actions already resemble **actuators**
- This is a natural extension point for non-verbal and control actions

---

#### Storage
- Key-value store scoped to the agent
- Used across states and transitions

Important:
- Storage already supports **cross-state memory**
- In the new framework, storage will also hold:
  - snapshots
  - regulation state
  - active provisioning context

---

### 1.3 PROMISE Assumptions That Must Be Broken Carefully

PROMISE currently assumes:

- Input == user utterance (string)
- Output == assistant utterance (string)
- History == ordered list of utterances
- Evaluation == happens on `respond()`

These assumptions must be generalized to:

- Input == Event
- Output == BehaviourPlan
- History == Event stream
- Evaluation == event-driven and tick-driven

However:

**The explicit state machine model, transition semantics, and prompt orchestration must remain intact.**

---

## 2. SBR – Social Behaviour Regulation Extension

### 2.1 What SBR Adds to PROMISE

SBR introduces a **parallel processing layer** that runs alongside PROMISE state execution.

Key ideas:

- Behaviour regulation is **orthogonal** to task behaviour
- Regulation is based on:
  - detection
  - internal mediation (tanks)
  - behaviour modulation

SBR is not a controller.  
It **modulates how states behave**, not which state is active.

---

### 2.2 Core SBR Concepts CODEX Must Understand

#### RegulationSystem
- Interface implemented by:
  - ZurichModelImpl
  - BigFiveImpl
  - CoachingImpl
- Main method:
  - `process(Utterances): String`

This method:
- Analyzes conversation history
- Updates internal variables
- Returns **prompt extensions** to append to the state prompt

In the new framework:
- `process` must generalize from utterances to **events**
- Output must generalize from **prompt extensions** to:
  - modulation bundles
  - internal control events

---

#### Tanks (Zurich Model)
- Internal continuous variables
- Represent motivational deficits or excesses
- Have inertia and thresholds

Important:
- Tanks already provide:
  - temporal smoothing
  - resistance to noise
  - multimodal fusion potential

CODEX should **reuse tanks**, not replace them.

---

#### PromptsProvider
- Centralizes:
  - detection prompts
  - behavior modulation prompts
- Allows swapping prompt variants

Important:
- This abstraction is valuable and should be preserved
- But prompt output should no longer be the only actuator

---

### 2.3 What SBR Does *Not* Do

- Does not control state transitions
- Does not model execution or plans
- Does not handle multimodal output beyond text
- Does not support interrupts explicitly

These are **intentional limitations**, not flaws.

---

### 2.4 How SBR Must Evolve

In the new framework, SBR must:

- Consume **events**, not utterances
- Maintain tanks continuously, not only per turn
- Emit:
  - ModulationBundles (for expression)
  - Internal control events (for initiative and interrupts)
- Be composable with other regulation systems

But:

**SBR must never silently hijack task control.**  
All control influence must be explicit and observable.

---

## 3. How PROMISE and SBR Fit Together Conceptually

PROMISE answers:
> What is the agent doing?

SBR answers:
> How should the agent do it right now?

The new framework must preserve this division.

### Correct Authority Model

- Task state machines:
  - Own goals
  - Own commitments
  - Own safety and policy constraints

- Regulation systems:
  - Shape expression
  - Suggest initiative
  - Trigger interrupts with severity

CODEX must **not merge these layers implicitly**.

---

## 4. Practical Guidance for CODEX During Implementation

### 4.1 What to Preserve

- Explicit state machines
- Transition-based control
- Prompt orchestration
- Storage semantics
- Regulation tank logic
- PromptsProvider abstraction

---

### 4.2 What to Generalize

- Utterances → Events
- Text responses → BehaviourPlans
- Conversation history → Event stream
- Turn-based evaluation → event-driven + tick-driven

---

### 4.3 What to Introduce Carefully

- Internal control events (opportunity, interrupt)
- Severity semantics
- Snapshot aggregation
- Provisioning modules
- Capability-aware output rendering

---

### 4.4 What Not to Do

- Do not let regulation decide states directly
- Do not hide interrupts inside prompt text
- Do not make speech mandatory
- Do not collapse continuous dynamics into discrete states prematurely
- Do not remove inspectability of decisions

---

## 5. Mental Model CODEX Should Use

> PROMISE is a **program**.  
> SBR is a **continuous modifier**.  
> Events are the **currency**.  
> BehaviourPlans are **intent**, not rendering.  
> Regulation suggests; task control decides.

If CODEX follows this mental model, it will be effective in implementing the roadmap without fighting the existing frameworks.

---

## 6. Summary

For CODEX to be effective:

- Understand PROMISE as a **state-machine programming framework**, not a chatbot engine
- Understand SBR as a **parallel regulation layer**, not a controller
- Preserve explicit control, observability, and testability
- Generalize interaction from text to events
- Make all coupling explicit

This knowledge, combined with full code access, is sufficient for CODEX to implement the new framework iteratively and correctly.