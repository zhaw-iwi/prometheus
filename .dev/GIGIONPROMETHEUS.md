# Gigi On PROMETHEUS

## Goal

Provide demonstrator agents for controlled, state-machine-driven verbal interaction with the Gigi persona:

- `FourStatesCircular`
- `FourStatesLinear`
- `SingleStateMicroCoaching`
- `SingleStateGuessingGame`
- `SingleStateCoCreation`

All are German-first conversational demos and are configured as test agents under `src/test/java/ch/zhaw/prometheus/agents`.

## Shared Persona Baseline

Across agents, Gigi is modeled as:

- social robot persona of ZHAW IWI
- embodied context: Unitree G1 / digital clients
- concise, warm, concrete interaction style
- German by default, with explicit language switch only on user request
- explicit, inspectable transition logic via PROMETHEUS states and transitions
- hardened mode control:
  - specialized modes are fixed while active
  - no role renegotiation in guessing mode
  - no implicit mode switching inside specialized states
- deterministic end-of-session handling:
  - clear global quit intent is handled as session-level end
  - specialized completion remains separate from global quit semantics

## Agent 1: FourStatesCircular

### What It Can Do

- Start in a base menu with 4 options:
  1) Ratespiel  
  2) Persuasions-Mikro-Coach  
  3) Story-Co-Creation  
  4) Gesamte Interaktion beenden
- Run any of the three specialized interactions.
- Return to base menu after each completed specialized interaction.
- End whole session only via outer global end-intent detection.
- On outer transition to final, extract structured outcome JSON to storage key `outcome`.
- Recognize global quit both in natural language and menu-style forms (e.g., `4`, `Option 4`, `Nummer 4`).

### Interaction Architecture

- Outer state:
  - global Gigi persona
  - global end-of-session decision
- Inner state machine:
  - Base Menu
  - Questions Based Guesser
  - Persuasion Micro Coach
  - Story Co Creation
  - Session Goodbye Final
- Circular flow:
  - `Base -> Specialized -> Base` (repeatable), and `Outer -> Final` on global end intent.

### Example Interaction

1. Gigi: Begruessung + Menue.
2. User: "Story-Co-Creation."
3. Gigi/User: Co-creation until a clear ending is confirmed.
4. Transition: Story -> Base.
5. User: "Ich moechte die Interaktion beenden."
6. Transition: Outer -> Session Final.
7. Action on outer-final transition writes `outcome` JSON with `flow_type = "circular"` and possibly multiple `outcomes` entries.

## Agent 2: FourStatesLinear

### What It Can Do

- Start in same base menu as circular.
- Run exactly one specialized interaction path per run (unless globally ended earlier).
- After specialized interaction completion, transition to inner final summary/goodbye (no return to menu).
- Keep outer global end-intent transition active.
- On specialized->inner-final transition, extract structured outcome JSON to storage key `outcome`.
- On outer global-quit transition, also extract structured outcome JSON to storage key `outcome`.
- Distinguish normal specialized completion vs global quit:
  - normal completion path: `Base -> Specialized -> Activity Summary Final`
  - global quit path: `Outer -> Session Goodbye Final`

### Interaction Architecture

- Outer state:
  - global Gigi persona
  - global end-of-session decision
- Inner state machine:
  - Base Menu
  - Questions Based Guesser
  - Persuasion Micro Coach
  - Story Co Creation
  - Activity Summary Final (inner final)
  - Session Goodbye Final (outer final target)
- Linear flow:
  - `Base -> one Specialized -> Activity Summary Final`
  - `Outer -> Session Goodbye Final` on clear global quit intent.

### Example Interaction

1. Gigi: Begruessung + Menue.
2. User: "Mikro-Coach."
3. Gigi/User: short coaching to concrete action + commitment.
4. Transition: Coach -> Activity Summary Final.
5. Action on this transition writes `outcome` JSON with `flow_type = "linear"` and one `outcomes` entry (`interaction_type = "micro_coaching"`).
6. Gigi: concise final summary and goodbye from inner final.

### Example Early-Exit Path (Linear)

1. Gigi: Begruessung + Menue.
2. User: "Option 4".
3. Transition: Outer -> Session Goodbye Final.
4. Action on outer-final transition writes `outcome` JSON for global-quit path.
5. Gigi: kurze, warme Abschlussausgabe aus dem Session-Final.

## Agent 3: Single-State Variants

### Common Single-State Concept

All single-state variants share the same architecture:

- no menu and no mode selection
- one specialized interaction state as entry state
- one transition from specialized state to a final goodbye state
- transition decision aligned with the specialized interaction goal and global quit handling
- transition action:
  - structured extraction to storage key `outcome`
- `flow_type` in extraction JSON is always `single_state`
- `completed` semantics in extraction:
  - `completed = true` only for specialized-goal completion
  - `completed = false` when transition occurred due to global quit intent
- specialized state is mode-locked while active (no implicit mode change)

### Agent 3.1: SingleStateMicroCoaching

#### What It Can Do

- Start directly in Persuasions-Mikro-Coach mode.
- Complete one micro-coaching run to concrete micro-action + commitment.
- Transition to final summary/goodbye on either:
  - coaching completion intent, or
  - clear global quit/end intent.

#### Specific Interaction Goal

- `interaction_type = "micro_coaching"` in extracted outcome.
- Final prompt handles both:
  - completed coaching summary
  - neutral early-exit summary on global quit

#### Example Interaction

1. Gigi: kurzer Coaching-Einstieg.
2. User: nennt gewuenschte Veraenderung.
3. Gigi: klaert Motivation/Barriere/Ausloeser und schlaegt Mikro-Schritt vor.
4. User: bestaetigt Commitment.
5. Transition: Coach -> Final + `outcome` extraction.
6. Gigi: kurze Ergebnis-Zusammenfassung und Verabschiedung.

### Agent 3.2: SingleStateGuessingGame

#### What It Can Do

- Start directly in Ja/Nein-Ratespiel mode.
- Ask discriminating yes/no questions and make a final guess.
- Transition to final summary/goodbye on either:
  - guess confirmation, or
  - clear global quit/end intent.
- Guessing roles are fixed:
  - user thinks of the item
  - assistant asks yes/no questions and gives final guess
  - no role renegotiation

#### Specific Interaction Goal

- `interaction_type = "guessing_game"` in extracted outcome.
- Final prompt handles both:
  - completed guessing summary
  - neutral early-exit summary on global quit

#### Example Interaction

1. Gigi: "Denke an eine Sache ..."
2. User: "Bereit."
3. Gigi/User: Ja/Nein-Fragen bis finaler Tipp.
4. User: bestaetigt den Tipp.
5. Transition: Guesser -> Final + `outcome` extraction.
6. Gigi: kurze Spielzusammenfassung und Verabschiedung.

### Agent 3.3: SingleStateCoCreation

#### What It Can Do

- Start directly in Story-Co-Creation mode.
- Co-create a short story from genre + figure to a clear ending.
- Transition to final summary/goodbye on either:
  - completion confirmation, or
  - clear global quit/end intent.

#### Specific Interaction Goal

- `interaction_type = "story_co_creation"` in extracted outcome.
- Final prompt handles both:
  - completed story summary
  - neutral early-exit summary on global quit

#### Example Interaction

1. Gigi: fragt nach Genre und Figur.
2. User/Gigi: entwickeln die Story bis zu einem Ende.
3. User: bestaetigt, dass die Geschichte abgeschlossen ist.
4. Transition: Story -> Final + `outcome` extraction.
5. Gigi: kurze Story-Zusammenfassung und Verabschiedung.

## Cross-Agent Hardening Summary

Across all five agents, current hardening goals are:

- prompt-level enforcement of active-mode discipline
- clear separation of specialized completion vs global quit
- deterministic transition intent checks with explicit false-cases for ambiguity
- strict outcome extraction JSON contracts with concrete enum values
- concise final-state behavior that closes the session and asks for a new session if user continues
