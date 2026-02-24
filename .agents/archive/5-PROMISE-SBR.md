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