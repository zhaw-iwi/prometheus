# CONTEXT.md  
PROMETHEUS – Event-Driven, Regulation-Aware Agent Framework

## 1. Vision and Motivation

PROMETHEUS is a next-generation agent control framework that generalizes the original PROMISE framework and its Social Behaviour Regulation extension into a unified, event-driven architecture.

The original PROMISE framework demonstrated that explicit state-machine-based control enables the engineering of complex, controllable conversational agents using language models. The SBR extension showed that adaptive social behaviour can be layered on top of task-oriented behaviour using regulation models such as the Zurich Model of Social Motivation.

However, both frameworks were biased toward:

- Verbal input and output  
- Turn-based conversational interaction  
- Reactive behaviour triggered primarily by user utterances  

Modern agent applications increasingly require:

- Proactive and deliberative behaviour  
- Multimodal perception and action  
- Continuous evaluation without turn boundaries  
- Socially appropriate disengagement and yielding  
- Agents that may never speak at all  

PROMETHEUS addresses these needs by providing a unified framework that supports conversational agents, embodied robots, and ambient intelligence systems under a single conceptual model.

## 2. Core Design Objectives

PROMETHEUS is designed to achieve the following objectives:

1. Preserve explicit, inspectable task control  
2. Generalize interaction from turns to events  
3. Treat regulation as a first-class component  
4. Support multimodal behaviour expression  
5. Maintain explicit authority boundaries between control and regulation  
6. Enable deterministic testing, trace replay, and regression validation  
7. Support safe, safety-critical and socially adaptive systems  

The framework must allow developers to build agents that:

- Perform useful tasks  
- Adapt socially and contextually  
- Take initiative when appropriate  
- Withdraw or remain silent when necessary  
- Remain predictable and testable  

## 3. Conceptual Model

### 3.1 Explicit Task Control

Primary behaviour is modeled as explicit state machines.

- States represent behavioural regimes  
- Transitions are triggered by events  
- Guards and actions remain inspectable and testable  
- Nested and supervisory state machines are supported  

This preserves the designability and controllability that characterized PROMISE.

### 3.2 Event-Driven Interaction

All interaction is driven by a unified event model.

Events may represent:

- User utterances  
- Detected emotions or gaze  
- Physical world observations  
- Internal regulation signals  
- Timers or periodic ticks  
- System-level signals  

Conversation turns are a special case of events, not the primary abstraction.

### 3.3 Regulation as a First-Class Component

Regulation systems operate alongside task control.

A regulation system:

- Integrates multimodal signals over time  
- Maintains latent internal state such as tanks or drives  
- Outputs:
  - Modulation bundles that shape behaviour expression  
  - Internal control events such as opportunities and interrupts  

Regulation shapes task execution but does not replace task control.

### 3.4 Behaviour as Multi-Channel Intent

Agent output is expressed as a BehaviourPlan rather than raw text.

A BehaviourPlan may include:

- Speech intent  
- Non-verbal signals such as gesture or gaze  
- Motion intent  
- Display or advisory output  

Rendering depends on available capabilities.  
A purely verbal agent is a valid degenerate case.

### 3.5 Explicit Authority Boundaries

Task control and regulation remain separate layers.

- Task control owns goals and commitments  
- Regulation shapes expression and may suggest initiative  
- Hard constraints and policies override social adaptation  
- Interrupts have severity levels and explicit semantics  

All coupling must be explicit and traceable.

## 4. What PROMETHEUS Is

PROMETHEUS is:

- A general agent control framework  
- Suitable for conversational AI  
- Suitable for embodied robots  
- Suitable for ambient or advisory agents  
- Suitable for safety-critical social navigation scenarios  
- Designed for engineering, testing, and iterative development  

## 5. What PROMETHEUS Is Not

PROMETHEUS is not:

- A chatbot framework  
- A reinforcement learning framework  
- A full autonomy or robotics stack  
- Tied to a single psychological theory  
- Limited to language-based interaction  

## 6. Canonical Use Cases and Scenarios

The following scenarios define acceptance-level capability targets.  
The framework must support all of them without changing core abstractions.

### 6.1 Conversational Health Check-In

A digital agent conducts daily patient check-ins.

Characteristics:

- Purely verbal  
- Turn-based  
- State-machine-driven prompts and transitions  
- Optional escalation  

Purpose:

- Ensures backward compatibility with PROMISE-style agents  
- Defines the minimal viable configuration  

### 6.2 Door Assist in Hospital Room

A robot observes a healthcare professional approaching a closed door with hands full and offers assistance.

Characteristics:

- Proactive initiative  
- Explicit consent gate  
- Multimodal perception  
- Physical action execution  
- Socially appropriate disengagement  

Tests:

- Regulation-driven initiative  
- Interrupt handling  
- Multi-channel BehaviourPlans  

### 6.3 Phone Retrieval with Plan Invalidation

A robot retrieves a dropped phone.  
A relative picks it up first, invalidating the plan.

Characteristics:

- Continuous monitoring  
- Plan invalidation  
- Graceful withdrawal  

Tests:

- Execution monitoring  
- Abort semantics  
- Social disengagement  

### 6.4 Warehouse Robot Safe Passage

A mobile robot encounters a worker in a warehouse aisle.

Characteristics:

- Continuous perception  
- Safety-critical motion control  
- No conversation required  
- Hard interrupts  

Tests:

- Event-driven control without turn-taking  
- Regulation-based risk assessment  
- Motion regimes as states  

### 6.5 Meeting Monitoring and Facilitation Support

An ambient agent monitors group meeting dynamics and provides facilitation support.

Characteristics:

- Multi-actor perception  
- Advisory and display output  
- No direct conversational ownership  

Tests:

- Group-level regulation  
- Non-intrusive behaviour  
- Continuous evaluation  

### Scenario Coverage Matrix

| Scenario | Speech | Motion | Regulation | Initiative | Abort |
|----------|--------|--------|------------|------------|-------|
| Check-in | Yes | No | Yes | No | Yes |
| Door Assist | Yes | Yes | Yes | Yes | Yes |
| Phone Retrieval | Optional | Yes | Yes | Yes | Yes |
| Warehouse | No | Yes | Yes | Yes | Yes |
| Meeting Monitor | No | No | Yes | Yes | Soft |

## 7. High-Level Requirements

### 7.1 Architectural Requirements

- Event-driven architecture  
- Explicit state machine task control  
- Regulation as first-class component  
- Explicit coupling between task and regulation  
- Support for verbal-only agents  

### 7.2 Event and Perception Requirements

- Unified event schema with provenance and confidence  
- Snapshot aggregation of events  
- Pluggable perception providers  
- Support evaluation without new user input  

### 7.3 Behaviour Output Requirements

- Multi-channel BehaviourPlans  
- Capability-dependent rendering  
- Optional non-verbal behaviour  

### 7.4 Regulation Requirements

- Latent internal state with decay and thresholds  
- Output modulation bundles and control events  
- Interchangeable regulation implementations  
- Composable regulation systems  

### 7.5 Initiative and Interrupt Requirements

- Severity levels for interrupts  
- Hard interrupts must preempt any task state  
- Initiative must be gated and rate-limited  

### 7.6 Execution and Planning Requirements

- Reusable provisioning modules  
- Continuous execution monitoring  
- Cancellable execution  
- Disengagement as first-class outcome  

### 7.7 Compatibility Requirements

- Preserve explicit state and transition semantics  
- Preserve prompt orchestration patterns  
- Preserve traceability of decisions and actions  

### 7.8 Developer Experience Requirements

- Minimal boilerplate for simple agents  
- Declarative configuration preferred  
- Deterministic simulation and replay support  
- Rich monitoring and introspection  

### 7.9 Safety and Policy Requirements

- Explicit policy constraints  
- Hard constraints override adaptation  

### 7.10 Non-Functional Requirements

- Deterministic behaviour where possible  
- Support for real-time scenarios  
- Extensibility without modifying the core engine  

## 8. Acceptance Criterion

PROMETHEUS is considered architecturally complete when all canonical scenarios can be implemented using:

- The same event model  
- The same state machine abstraction  
- Interchangeable regulation systems  
- Explicit authority boundaries  

without changing core abstractions.