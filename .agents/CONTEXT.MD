# PROMETHEUS Context for Codex

PROMETHEUS is an event-driven Java framework for developing multimodal digital
agents with explicit state-machine control, first-class regulation, and
structured behaviour output.

This file exists to orient coding agents before they work in the repository. It
describes the current architecture and boundaries. Workflow rules live in
`.agents/CODEX.md`; project history lives in `PROJECT.md`; user-facing setup and
API documentation live in `README.md`.

## Current Mental Model

PROMETHEUS maps multimodal perception to multimodal behaviour through explicit,
inspectable agent control.

- Inputs are `Event` objects.
- Task control is modeled with `Agent`, `State`, `OuterState`, `Transition`,
  guards, actions, policies, and storage.
- Agent output is a persisted `BehaviourPlan` event with optional `speech`,
  `nonVerbal`, `motion`, and `display` channels.
- `AgentInteractionProfile` declares which observation event types an agent
  accepts and which behaviour modalities it can emit.
- Valerian is the bundled cockpit for creating, sensing, speaking with,
  inspecting, and rendering PROMETHEUS agents.

The important abstraction is not chat. Conversation is one event source among
many. A facial expression sample, social grouping report, hand sign, weather
context, system tick, internal regulation signal, and user utterance all move
through the same event-driven runtime.

## Core Architecture

### State-Machine Task Control

Primary behaviour belongs to explicit state machines.

- States define behavioural regimes and prompts.
- Transitions define when control moves.
- Actions update storage or compute domain-specific outputs.
- Nested `OuterState` control is allowed when it keeps task structure explicit.
- Guards and actions must remain readable and testable.

Do not replace explicit control flow with a single opaque prompt loop.

### Event-Driven Interaction

Events are the public and internal runtime currency.

Current public observation types include:

- `obs.user_utterance`
- `obs.emotion.face`
- `obs.human.presence`
- `obs.social.grouping`
- `obs.social.context`
- `obs.social.situation_change`
- `obs.hand.sign`
- `obs.weather.current`
- `obs.weather.forecast`

Assistant behaviour is persisted as:

- `resp.behaviour_plan`

For HTTP ingestion, `payload` is a string. Structured perception payloads are
JSON encoded into that string.

### Behaviour Plans

Agent responses should be represented as `BehaviourPlan`, not ad-hoc response
objects.

Supported top-level channels:

- `speech`
- `nonVerbal`
- `motion`
- `display`

Clients render only the channels they support. Agents may be speech-only, but
the framework must not assume speech-only interaction.

### Regulation

Regulation is a bounded layer alongside task control.

- Regulation may integrate multimodal context over time.
- Regulation may shape expression or emit explicit internal opportunities and
  interrupts.
- Task control owns goals, commitments, and final authority.
- Safety and policy constraints override adaptive social expression.

Do not let regulation silently replace task transitions or hiddenly change agent
goals.

## Current Main-Branch Agent Boundary

The `main` branch is the reusable framework and baseline Valerian catalog.

Main-branch production definitions currently live under:

- `agentdefs/core`
- `agentdefs/usecases/healthcare`

The current public keys are:

- `core.facial_expression_sensitivity`
- `core.multimodal_behaviour`
- `core.rock_scissor_paper`
- `core.role_clarification_guessing_game`
- `core.social_context_sensitivity`
- `usecases.healthcare.guessing_game`
- `usecases.healthcare.guessing_game_user_guess`
- `usecases.healthcare.healthcare_conversation`
- `usecases.healthcare.smart_goal_coaching`
- `usecases.healthcare.therapy_appointment_reminder`
- `usecases.healthcare.therapy_appointment_reminder_intro`

Main should stay generic. Event-specific, location-specific, experiment-specific,
or customer-specific agents belong on application branches or in separate
deployment-specific modules. Do not reintroduce obsolete `basic.*` or
`multimodal.*` production catalogs.

The main baseline persona is Valerian, a digital agent made in the ZHAW SIRA Lab
for demo purposes and as a starting point for developing other agents. Main
baseline agents should not rely on GIGI, TDSR, Davos, hotel, or robot-tour
persona assumptions.

## Valerian Boundary

Valerian is a cockpit and demo client, not the framework core.

It uses the scoped demo API and should remain a client of public backend
contracts:

- access-code scoped agent creation
- `AgentInteractionProfile`
- `acknowledge` event ingestion
- behaviour SSE streams
- monitor SSE streams
- Realtime speech call orchestration

Keep reusable semantics in the backend model/application layers. Keep browser UI
logic in `src/main/resources/public/valerian` or
`src/main/resources/public/valerian-admin`.

## Public API Contracts to Preserve

External perception clients and behaviour renderers depend on these contracts:

- `POST /demo/agents/{agentId}/acknowledge`
- `GET /demo/agents/{agentId}/behaviour/stream`
- `POST /demo/agents/{agentId}/behaviour/generate`
- `GET /demo/agents/{agentId}/info`
- `GET /demo/agents/{agentId}/monitor/stream`
- Global equivalents without `/demo` for trusted tools.

Scoped browser SSE may pass `accessCode` as a query parameter because
`EventSource` cannot set custom headers. Non-browser clients should prefer the
`X-Prometheus-Access-Code` header.

When adding or changing observation or behaviour contracts:

1. Update `AgentInteractionProfile` usage.
2. Update prompt event adapters or behaviour renderers as needed.
3. Update Valerian only if the cockpit should expose the capability.
4. Update README API documentation.
5. Add focused contract tests.

## Engineering Boundaries

- Prefer existing framework concepts over new parallel abstractions.
- Keep controllers thin; validation and delegation only.
- Keep domain logic in model/application packages.
- Keep prompt strings near the agent definitions that own them.
- Keep agent definitions as Spring-discovered `AgentDefinition` beans.
- Keep event payloads inspectable and deterministic where possible.
- Preserve explicit state, transition, storage, and event-history semantics.
- Remove obsolete code instead of adding compatibility layers unless the user
  explicitly requires compatibility.

## Testing Expectations

Use the smallest high-value test set that protects the changed contract.

Common anchors:

- Agent catalog/profile changes:
  `AgentDefinitionRegistryUnitTest`,
  `SeedAgentInteractionProfileContractTest`,
  package-specific prompt contract tests.
- Public API changes:
  `AgentClientCompatibilityWebMvcTest`,
  `ScopedDemoControllerIntegrationTest`,
  `AdminAccessCodeControllerWebMvcTest`.
- Valerian client changes:
  `ValerianClientStaticResourceContractTest`,
  `ValerianAdminClientStaticResourceContractTest`,
  `node --check src/main/resources/public/valerian/script.js`,
  `node --check tests/playwright/valerian-column-expansion.spec.mjs`,
  and Playwright visual smoke tests when layout or camera/rendering behaviour
  changes.
- RPS changes:
  `RpsRulesUnitTest`,
  `DeterministicRpsSignSelectorUnitTest`,
  `RpsRevealPolicyContractTest`.

## What Not To Do

- Do not treat PROMETHEUS as a chatbot-only framework.
- Do not hide control flow inside one giant prompt.
- Do not bypass `Event` and `BehaviourPlan` contracts with ad-hoc DTOs.
- Do not make Valerian-only assumptions part of the model core.
- Do not add new event-specific agents to `main` without an explicit decision.
- Do not preserve dead legacy agents, endpoints, scripts, or tests by default.
