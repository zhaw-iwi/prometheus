# Gigi On PROMETHEUS: Multimodal Single-State Variants

## Goal

Provide demonstrator agents for controlled, state-machine-driven interaction with Gigi, focused on multimodal extensions of the single-state guessing game:

- `SingleStateMultimodalIn`
- `SingleStateMultimodalOut`
- `SingleStateMultimodalInOut`

All are German-first demos and are configured as test agents under `src/test/java/ch/zhaw/prometheus/agents/multimodal`.

## Shared Persona and Flow Baseline

Across all three agents:

- Gigi persona baseline remains the same (ZHAW IWI social robot persona, Unitree G1 context, concise/warm style, German-first language policy).
- The inner interaction flow is intentionally preserved from `SingleStateGuessingGame`.
- State-machine shape remains single-state specialized flow plus final state:
  - `Questions Based Guesser -> Session Goodbye Final`
- Transition logic and extraction semantics remain unchanged:
  - one specialized-to-final transition
  - extraction to storage key `outcome`
  - `flow_type = "single_state"`
  - `completed = true` for specialized completion, `false` for global quit
- Guessing-game mode hardening remains unchanged:
  - fixed role split
  - no role renegotiation
  - one yes/no question per step
  - explicit final confirmation phrase

## Multimodal Layering Strategy

The multimodal behavior is introduced via an `OuterState` wrapper and policy configuration, while preserving inner prompt logic:

- Outer-state prompt injects multimodal instructions.
- Inner guessing-game prompts keep original verbal control flow.
- For output-enabled variants, nonverbal gesture generation uses existing built-in `PromptPolicy` gesture support.

This gives a clean separation:

- Inner state = fixed task/mode logic.
- Outer state = multimodal operating rules.

## Agent 1: SingleStateMultimodalIn

### What It Can Do

- Keeps the same verbal guessing-game flow as the baseline single-state guesser.
- Consumes visual observation context if available from `/acknowledge`:
  - `obs.emotion.face`
  - `obs.human.presence`
  - `obs.social.grouping`
- Uses these signals as contextual cues for tone and questioning.
- May occasionally reference visual observations explicitly.
- Handles explicit user questions about visible context (for example appearance or number of people) using available events only.

### Input Safety Rules

- Treat visual events as optional, fallible context.
- State uncertainty/confidence when relevant.
- Never invent perception not present in events.
- Prioritize explicit verbal user content if signals conflict.

### Architecture

- `OuterState`: multimodal input grounding instructions.
- Inner state: original single-state guessing-game prompt/policy and unchanged transition/extraction/final structure.

## Agent 2: SingleStateMultimodalOut

### What It Can Do

- Keeps the same verbal guessing-game flow as the baseline single-state guesser.
- Adds nonverbal behavior output using the built-in gesture channel.
- Nonverbal gesture is selected to support assistant speech.

### Output Contract

- Uses existing `PromptPolicy` nonverbal gesture generation (no custom schema).
- Gesture labels remain the built-in set:
  - `OPEN_QUESTION`
  - `EXPLAIN`
  - `UNCERTAIN`
  - `ACKNOWLEDGE`
  - `POLITE`
  - `NONE`

### Architecture

- `OuterState`: multimodal output alignment instructions (speech and nonverbal consistency).
- Inner state: original guessing-game flow + gesture generation enabled via policy.

## Agent 3: SingleStateMultimodalInOut

### What It Can Do

- Combines multimodal input grounding and built-in nonverbal output.
- Preserves the same inner verbal guessing-game behavior and completion semantics.
- Adapts interaction using available visual observations and emits supportive nonverbal gestures.

### Combined Rules

- Input side:
  - consume available visual observations
  - occasional explicit references allowed
  - uncertainty-aware, no hallucinated perception
- Output side:
  - nonverbal gesture complements verbal response
  - same built-in gesture label schema as `MultimodalOut`
- Core guessing-game mode logic remains unchanged.

### Architecture

- `OuterState`: combined multimodal input/output instructions.
- Inner state: original single-state guesser with nonverbal gesture generation enabled.

## Example Usage Pattern

1. Start one of the three multimodal seed tests to persist the agent.
2. Connect text/realtime client for verbal interaction.
3. Optionally connect visual clients and send events via `/acknowledge`.
4. Observe:
   - `MultimodalIn`: visual context reflected in dialogue behavior.
   - `MultimodalOut`: nonverbal gesture values in behaviour plans.
   - `MultimodalInOut`: both effects together.

## Cross-Agent Hardening Summary

Across all three multimodal variants:

- exact preservation of inner single-state guessing-game flow
- multimodal behavior added through outer-state layering, not by altering core mode logic
- clear input reliability constraints (no fabricated perception)
- deterministic completion/quit semantics and strict outcome extraction shape
- reuse of built-in nonverbal gesture output contract for interoperability
