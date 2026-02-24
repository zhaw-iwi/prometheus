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