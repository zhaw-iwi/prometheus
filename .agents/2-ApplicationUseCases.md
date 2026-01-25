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