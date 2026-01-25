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