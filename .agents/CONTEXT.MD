# PROMETHEUS Context for Codex

This is the project-specific orientation for work on PROMETHEUS. Reusable
engineering and milestone practices live in `.agents/CODEX.md`. Current status
is summarized at the top of `PROJECT.md`; the rest of that file is a historical
audit and should be searched only when a task needs earlier decisions. Setup and
public API details live in `README.md`.

## Purpose and Intended Outcome

PROMETHEUS is a Java framework for developing multimodal digital agents. It maps
multimodal perception to multimodal behaviour through explicit, inspectable
agent control.

The framework is intended to support agents that:

- receive speech, facial, social, gestural, environmental, system, and internal
  signals through one event-driven runtime;
- control tasks with explicit state machines rather than an opaque chat loop;
- emit structured behaviour across speech, nonverbal, motion, and display
  channels;
- integrate social context over time through a bounded regulation and internal
  motivation layer;
- remain inspectable, testable, and usable by different clients and embodiments.

Conversation is one event source among many. PROMETHEUS must not be designed as
a chatbot-only framework.

## Core Model

### Events and control

- `Event` is the public and internal runtime currency.
- `Agent`, `State`, `OuterState`, `Transition`, guards, actions, policies,
  storage, and event history provide explicit task control.
- States define behavioural regimes; transitions decide when control moves;
  actions update storage or compute domain results.
- Nested `OuterState` control is appropriate when it keeps task structure
  explicit.
- Task goals, commitments, transitions, and final authority belong to the state
  machine.

Do not replace explicit task control with a single prompt loop or let a prompt
silently own state.

### Behaviour

Agent output is persisted as a `resp.behaviour_plan` event containing a
`BehaviourPlan`. Supported top-level channels are:

- `speech`;
- `nonVerbal`;
- `motion`;
- `display`.

Channels are optional and clients render only the modalities they support. Do
not introduce ad-hoc response objects that bypass the behaviour-plan contract.

### Interaction profiles

`AgentInteractionProfile` declares which observation event types an agent
accepts and which behaviour modalities it can emit. Profiles are part of the
agent/client contract and must be updated when capabilities change.

### Event families

Current external observation types include user utterances, facial emotion,
human presence, social grouping, rich social context, hand signs, and current
or forecast weather. Their constants and exact names are authoritative in
`model/event/Event.java` and documented for clients in `README.md`.

`obs.social.situation_change` is derived by the backend from social grouping;
it is not a raw client-sensed observation. `sys.tick` is a system event.
`int.regulation.*` events are internal regulation signals.

HTTP event payloads are strings. Structured perception payloads are JSON encoded
inside that string so event history stays inspectable.

## Current Capabilities

PROMETHEUS currently provides:

- persisted explicit state-machine agents with policies, transitions, actions,
  storage, event history, reset, and scheduled evaluation;
- multimodal behaviour generation and behaviour persistence;
- agent interaction profiles and Spring-discovered `AgentDefinition` beans;
- scoped access-code APIs and trusted global APIs;
- behaviour and monitor SSE streams with heartbeat, replay, cursor, and
  disconnect resilience;
- PROMETHEUS-authoritative OpenAI Realtime speech orchestration;
- browser-side facial emotion, social context, hand-sign, and manual weather
  sensing through Valerian;
- deterministic social-situation-change derivation;
- a regulation foundation with persisted specifications, snapshot context,
  modulation values, internal events, reset behavior, and tests;
- Valerian Cockpit, Valerian Access Management, the public Talk to Me exact-text
  speech client, API Workbench, and separate multilateral displays.

The current registered main-branch agent catalog is authoritative in
`agentdefs/core`, `agentdefs/usecases/healthcare`, and `README.md`. Do not copy
catalog keys into new context documents.

## Regulation: Implemented Foundation and Missing System

Regulation is intended to become a bounded internal motivation system alongside
task control. It may integrate multimodal social context over time, shape
expression, and emit explicit opportunities or interrupts. It must never
silently replace task transitions or change agent goals. Safety and explicit
task policy retain authority.

The current implementation is only a foundation:

- Agents default to `NoOpRegulationSystem`; no production agent definition
  currently enables the concrete Zurich system.
- `ZurichRegulationSystem` persists dependency, enterprise, and autonomy
  variables, but its active policies currently react only to system ticks and
  user utterances.
- The current implementation emits affiliation opportunities from a dependency
  threshold. Soft and hard interrupt event constants exist, but no concrete
  production policy emits them.
- A `ModulationBundle` is calculated and retained as the agent's latest
  modulation, but it is not yet consumed by prompt assembly or mapped into a
  `BehaviourPlan`.
- Facial emotion, social context, grouping, situation changes, and other
  multimodal evidence are not yet integrated into concrete regulation policies.
- Motivation arbitration, task-commitment handling, safety precedence, and
  observable regulation diagnostics are not yet complete.

Extend the existing `model/regulation` abstractions and their integration in
`Agent`; do not create a parallel motivation subsystem. Keep updates
deterministic, persisted where required, inspectable, and covered by focused
tests.

## Architecture and Repository Boundaries

### Framework and agent definitions

The `main` branch is the reusable framework and baseline Valerian catalog.
Production definitions are Spring-managed `AgentDefinition` beans under:

- `agentdefs/core`;
- `agentdefs/usecases/healthcare`.

Keep prompts near the package that owns the definition. Main-branch baseline
agents use the Valerian/SIRA Lab framing and must not acquire GIGI, TDSR, Davos,
hotel, robot-tour, venue, experiment, or customer assumptions. Such agents
belong in application modules, deployment repositories, or explicitly chosen
application branches.

Do not reintroduce the removed `basic.*` or `multimodal.*` production catalogs.

The `agents` branch deliberately adds event- and experiment-specific
definitions through `ApplicationAgentDefinitionConfiguration`. Keep those
application packages separate from the reusable main catalog, preserve their
existing package/key families, and use the current catalog table in `README.md`
instead of copying a second key list into this context file.

### Application and HTTP layers

Keep controllers focused on transport validation and delegation. Put reusable
semantics in model or application packages. Preserve the event, behaviour-plan,
interaction-profile, storage, and event-history contracts.

Published scoped client contracts include agent creation and info,
`acknowledge`, explicit behaviour generation, behaviour SSE, monitor SSE, and
Realtime call orchestration. Exact paths and request shapes are maintained in
`README.md` and exercised by controller contract tests. Browser SSE may use an
access code query parameter because `EventSource` cannot set custom headers;
other clients should prefer the access-code header.

When an observation or behaviour contract changes, update its event/profile
definition, prompt adapter or renderer, relevant client exposure, contract
tests, and README documentation together.

### Clients and auxiliary sites

Valerian is a cockpit and demo client, not framework core. Keep its browser
logic under `src/main/resources/public/valerian` or `valerian-admin` and consume
public backend contracts rather than importing client assumptions into the
model. Talk to Me is a reduced public client under
`src/main/resources/public/talktome`; it uses explicit access-code-scoped agent
lifecycle and the backend-authoritative Realtime contract without microphone
capture or browser-authored speech responses.

API Workbench is a developer client for the same contracts. Multilateral pages
are separate meeting/listening displays. The `.web/` SIRA page and participation
site are standalone web deployments; they share this repository but are not
part of the Spring runtime or agent framework architecture.

## Maturity and Compatibility Policy

PROMETHEUS remains prototype-oriented internally. Prefer a clear replacement
and remove obsolete internal code instead of layering compatibility adapters,
unless the milestone explicitly requires compatibility.

Published HTTP/SSE contracts, persisted data, and external client behavior are
real boundaries. Do not break them incidentally under the clean-slate rule.
Scope contract changes explicitly, update all consumers and documentation, and
prefer clear reset instructions over complex migrations only when the affected
deployment is confirmed to be prototype data.

## Where to Look

- `model`: agents, states, events, policies, behaviour, regulation, social
  derivation, and domain rules.
- `application`: runtime orchestration, scoped access, Realtime, and publishing.
- `controllers`: HTTP/SSE boundaries and request/response contracts.
- `agentdefs`: current production definitions and their prompts.
- `src/main/resources/public`: Valerian, access management, Talk to Me, API
  Workbench, and multilateral clients.
- `.web`: standalone public and participation sites.
- `src/test/java`: unit, integration, prompt, profile, controller, and static
  resource contracts.
- `tests/playwright`: critical browser journeys and visual behavior.
- `README.md`: current setup, catalog, API, client, and deployment reference.
- top of `PROJECT.md`: current implementation status and latest milestone.
- historical part of `PROJECT.md`: search on demand; never treat an old
  milestone as current behavior without checking code.

Representative high-value test anchors include registry/profile and package
prompt contracts for agent changes; Web MVC and scoped integration contracts for
API changes; static resource plus Playwright checks for browser changes; domain
unit tests for RPS and social derivation; and
`AgentRegulationIntegrationUnitTest`, `RegulationSystemPersistenceUnitTest`, and
`ZurichRegulationSystemUnitTest` for regulation work. Always inspect neighboring
tests for the actual task rather than relying only on this list.

## Do Not

- Treat PROMETHEUS as a speech-only or chatbot-only framework.
- Hide task control, commitments, or regulation authority inside prompts.
- Bypass `Event`, `BehaviourPlan`, or `AgentInteractionProfile` contracts.
- Put Valerian-only behavior into framework core.
- Add deployment-specific agents to main without an explicit architectural
  decision.
- Build a second social motivation or regulation abstraction beside the current
  one.
- Use the complete milestone history as the current specification.
