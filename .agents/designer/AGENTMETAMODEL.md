# PROMETHEUS Agent Metamodel

An agent is an executable, revisioned blueprint rather than a prompt:

```text
agent definition = identity + interaction contract + lifecycle + memory
                 + state graph + policies + decisions + actions + behaviour
                 + optional definition resources
```

The metamodel describes static reusable intent. Runtime state, deployment
credentials, access codes, browser configuration, and event history are not
part of the definition.

`AGENTDEFINITION_JSON.md` gives the version 1 serialized contract.
`ARCHITECTURE.md` explains how the contract is persisted and executed.

## Catalog examined

The inventory considered both `main` and the `agents` branch. The latter is
`main` plus 41 commits, not an unrelated historical fork.

| Catalog | Count | Patterns that influence the metamodel |
| --- | ---: | --- |
| `main/core` | 6 | Signal-sensitive demos, multimodal output, deterministic RPS, role clarification, exact-text output |
| `main/usecases.healthcare` | 6 | Prompt-based care flows, composite context, one/two-state variants, preselected context |
| `agents/elderlycare` | 4 | German care variants |
| `agents/tdsr.aisha` | 1 | Catalog-grounded Q&A, validation, and fallback |
| `agents/tdsr.core` | 25 | Five tasks in German, English, French, Italian, and multilingual modes |
| `agents/tdsr.davos` | 6 | Care-center and open event/venue agents |
| `agents/tdsr.lab` | 5 | Lab equivalents of core demonstrations |
| `agents/tdsr.migros` | 3 | General station conversation and scene-scoped scripts |
| `agents/tdsr.shhd` | 25 | Five scenes in five language modes |
| **Total on `agents`** | **81** | 69 additions beyond `main` |

Only the twelve `main` definitions are migrated in the current roadmap. Branch
patterns establish extension boundaries but do not justify speculative
implementation of every specialized component.

## Static, runtime, and deployment concerns

### Definition data

The JSON revision owns:

- identity and catalog metadata;
- supported sensing and behaviour capabilities;
- lifecycle and initializers;
- storage declarations and initial values;
- states, containment, policies, transitions, decisions, and actions;
- ordered prompt elements;
- references to definition-owned resources;
- authoring verification scenarios when supported.

### Runtime instance data

The persisted instance owns:

- its pinned definition revision ID;
- current active state or active state path;
- mutable storage values;
- event and behaviour history;
- lifecycle status and runtime concurrency information.

### Deployment data

The following remain outside an agent definition:

- access-code assignment and catalog activation;
- OpenAI/Azure credentials and global endpoints;
- browser sensing implementation;
- TTS voice, speed, and physical speaker choice;
- client rendering details;
- CORS and deployment configuration;
- designer authentication credentials.

An agent declares what it can receive and emit. A deployment decides how those
capabilities are connected.

## 1. Identity and catalog metadata

A definition specifies:

- a stable unique key such as `core.rock_scissor_paper`;
- display name and description;
- explicit category path;
- optional fixed language code, with `null` for multilingual behavior;
- searchable profile tags;
- schema version and definition revision.

Repository lifecycle data adds draft/published/archived status, provenance,
timestamps, optimistic version, and active revision. Author/owner fields may be
added when PROMETHEUS has a real identity model; they must not pretend that an
admin token identifies a person.

Category is data. It is read from revision JSON and must never be derived from a
Java package or implementation type.

## 2. Sensing contract

The interaction contract declares observations the agent accepts. Existing
agents use:

- user utterance;
- facial emotion;
- human presence;
- social grouping;
- rich social context;
- derived social-situation change;
- hand sign;
- current weather;
- forecast weather.

Capability declaration and reaction logic are deliberately distinct:

1. Sensing declares that an event type is accepted and meaningful.
2. Policies/transitions declare what happens when such an event is observed in
   a particular state.

A selected capability may intentionally have no dedicated reaction because a
prompt consumes it as context. That is a designer warning at most, not always a
validation error.

Event selectors support any event, type, actor, kind, state ID, and nested
AND/OR expressions. Selection and snapshot aggregation are explicit where a
component needs historical context.

## 3. Behaviour contract

The interaction profile declares modalities an agent may emit. Existing output
forms include:

- speech;
- nonverbal gesture;
- facial expression;
- gaze;
- nonverbal motion;
- top-level hand-sign motion;
- display content.

The behaviour contract is separate from generation strategy. A modality may be
produced by a model prompt, deterministic mapping, catalog-constrained answer,
or fallback component.

A policy or action cannot emit an undeclared modality. The designer should
offer to add the missing capability, while backend validation remains
authoritative.

## 4. Lifecycle and initialization

A definition specifies:

- initial/root state ID;
- whether creation immediately starts the agent;
- startup/entry behavior;
- storage initializers;
- reset rules.

Observed initialization strategies include:

- empty or constant storage;
- random selection from a fixed typed collection;
- packaged resource values;
- values accumulated during interaction;
- model-extracted outcome values.

Initializers are registered components with typed configuration. Random
initializers accept an injectable randomness source so tests can be
deterministic.

Reset always uses the instance's pinned revision. It does not upgrade the
instance to the definition's currently active revision.

## 5. Storage

Agent-wide JSON storage is a typed contract rather than an unstructured bag.
Each declaration includes:

- stable key;
- JSON value schema;
- optional initial value or initializer;
- required/optional semantics;
- reset behavior;
- visibility: internal working value or exported outcome;
- optional description and designer example.

Policies, decisions, and actions declare storage inputs and outputs. Semantic
validation checks that referenced keys exist and that expected shapes are
compatible with declared schemas.

Outcome extraction is a first-class pattern. Current agents commonly record
completion, selected role, interaction type, discussed topics, sensing usage,
and result summaries.

## 6. State graph

The graph primitives correspond to the current
[`State`](../../src/main/java/ch/zhaw/prometheus/model/State.java),
[`OuterState`](../../src/main/java/ch/zhaw/prometheus/model/OuterState.java),
[`Final`](../../src/main/java/ch/zhaw/prometheus/model/Final.java), and
[`Transition`](../../src/main/java/ch/zhaw/prometheus/model/Transition.java)
semantics without retaining their persistence role.

A state specifies:

- stable design ID and human-readable name;
- kind: atomic, composite, or final;
- optional policy component;
- event-history selector;
- whether entering it starts/generates or reprocesses the triggering event;
- whether it is oblivious and clears prior state events;
- for composites, ordered child IDs and one initial child ID.

A transition specifies:

- stable ID;
- source and target state IDs;
- deterministic order within its source;
- zero or more decisions;
- zero or more ordered actions.

### Required graph semantics

- Cycles and self-transitions are valid.
- Composite containment and transition edges are different relationships.
- Containment is acyclic and every child has at most one parent.
- Composite/outer transitions are evaluated before active inner-state
  transitions.
- Transitions are evaluated in declared order; the first accepted transition
  wins.
- Decisions on one transition are ANDed.
- No decisions means unconditional acceptance when that transition is
  evaluated.
- Actions run in declared order after acceptance.
- Entry configured as starter generation emits the target state's starter.
- Entry configured as event reprocessing acknowledges the triggering event in
  the target state.
- Final states are inactive and have no policy or outgoing transitions.
- State IDs, not display names, are used by references and persisted runtime
  state.

Observed graph shapes include a single conversational state, composite context
with task and final children, introduction then task, role-clarification
branching, repeated RPS rounds, sensor-driven self-transitions, and
scene-relevance gates.

## 7. Policies

A policy determines response behavior in a state. The common prompt policy
currently represented by
[`PromptPolicy`](../../src/main/java/ch/zhaw/prometheus/model/policy/PromptPolicy.java)
supports:

- main response prompt;
- starter prompt;
- summarization prompt;
- optional nonverbal-plan prompt;
- optional gesture-only prompt;
- storage inputs with expected shapes;
- composition with enclosing composite-state policy;
- output profile selection.

Other observed strategies are no-op, deterministic exact text, deterministic
RPS reveal/result, catalog-grounded response with validation/fallback,
scene-scoped suppression, and language/scene composition.

The version 1 component catalog implements only what the main definitions need,
but uses a component envelope that supports later strategies.

## 8. Prompt elements

Prompt-bearing configuration uses ordered sections with stable IDs, kinds, and
content. Recurrent kinds found across the catalogs include:

- persona and embodiment;
- purpose and intended outcome;
- organization, setting, or use-case context;
- participant roles;
- language and pronunciation;
- tone, warmth, humor, brevity, and question frequency;
- grounded knowledge;
- uncertainty and non-invention rules;
- safety and delegation boundaries;
- sensing interpretation and proactive behavior;
- modality and gesture guidance;
- state-specific response instructions;
- starter instructions;
- transition criteria and positive/negative examples;
- relevance/scope gates;
- outcome extraction schema;
- completion and goodbye behavior.

The compiler concatenates nonblank sections in declared order using one
documented separator and preserves exact content. Component-specific prompts
retain their separate roles; they are not all collapsed into one global prompt.

Designer examples and placeholders are UI metadata. They are persisted only
after an explicit author action.

## 9. Decisions

Observed decision types include:

- latest event has a specified type;
- model decision from a static prompt;
- model decision using stored values;
- scene relevance;
- registered domain decision.

A decision configuration declares its selector/snapshot behavior, required
storage inputs, prompt elements where applicable, and result/failure contract.
Decision components return an explicit deterministic decision result to the
transition engine.

Typical purposes are completion, clarified role, readiness, another round,
hand-sign arrival, social-interjection opportunity, and scene relevance.

## 10. Actions

Observed actions include:

- extract structured JSON into storage;
- summarize into storage;
- remove a selected item from a stored list;
- select a deterministic RPS sign;
- evaluate an RPS round;
- registered domain storage mutation.

An action declares:

- component kind and version;
- input event selection;
- storage inputs and expected schemas;
- prompt sections or deterministic configuration;
- storage output and expected schema;
- failure behavior.

Actions cannot modify the immutable definition or access arbitrary Spring
services. Runtime dependencies are explicitly supplied by the trusted engine.

## 11. Definition resources

Resources are immutable data owned or referenced by a revision, for example:

- prompt fragment libraries;
- typed initializer choices;
- approved knowledge catalogs;
- aliases and protected facts;
- provenance and time-sensitivity metadata;
- deterministic fallback content.

Aisha demonstrates future needs for versioned knowledge entries, retrieval
thresholds, constrained model output, validation, fallback, and speech/context
limits. Schema version 1 need not implement its retrieval policy, but resources
and component envelopes must not prevent that later component.

Credentials, mutable external databases, deployment paths, and arbitrary files
are not embedded definition resources.

## 12. Component envelope

Every extensible runtime element uses the same conceptual form:

```json
{
  "kind": "prometheus.policy.prompt",
  "version": 1,
  "config": {}
}
```

The core schema validates the envelope. The registered component's own schema
validates `config`. Compilation resolves the pair `(kind, version)`; it never
reflects a class name supplied by JSON.

The registry should expose at least:

- stable machine identity;
- category and user-facing label;
- description and compatibility notes;
- configuration schema;
- defaults and examples;
- emitted/consumed capabilities;
- compiler/factory;
- deprecation metadata when eventually needed.

## 13. Interaction mapping

The designer presents the relationship in plain language:

```text
When <selected sensing event>
while <state is active>
if <all decisions accept>
then <run actions and produce behaviour>
and optionally <move to another state>.
```

This mapping does not introduce a second execution model. It is a guided view
over state policies and transitions. A new agent starts with one implicit
atomic state so authors can create useful mappings before learning graph
concepts. Adding state flow makes the implicit state explicit and assigns the
same mappings to states.

## 14. Validation layers

Validation is cumulative:

1. JSON parsing and core JSON Schema.
2. Component configuration schemas.
3. Semantic references and graph invariants.
4. Capability/storage compatibility.
5. Compilation.
6. Optional author verification scenarios and preview.

Warnings cover suspicious but valid designs such as unused sensing capability,
unreachable non-final state where policy allows warning, missing descriptive
metadata, or unusually large prompts. Errors cover anything that prevents safe
and deterministic execution.

## Excluded from version 1

- Regulation and modulation
- Arbitrary Java/script execution
- Whole-agent Java definitions
- Instance upgrades between revisions
- Collaborative author identity and approval workflow
- Deployment orchestration
- Migration of the `agents` branch
- Client-specific sensor, rendering, voice, and credential settings

## Current test anchors

- `BundledDefinitionCatalogUnitTest` for the exact twelve keys, capability
  declarations, prompt parity, compilation, and representative execution;
- `AgentRuntimeEngineUnitTest` for nested graph behavior and reset/history
  semantics;
- `PromptComposerUnitTest` and `LanguageModelRuntimeGatewayUnitTest` for prompt
  composition and the provider boundary;
- `DeclarativeAgentInstanceServiceUnitTest` and `JpaPersistenceAdapterTest` for
  revision pins and mutable runtime persistence;
- `DeterministicRuntimeComponentsUnitTest` and RPS rules/sign tests for trusted
  provider-free component behavior;
- `LegacyRuntimeRemovalContractTest` for the no-fallback structural gate;
- Aisha and localized prompt contracts on `agents` as selective design
  references only.
