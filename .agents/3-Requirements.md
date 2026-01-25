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