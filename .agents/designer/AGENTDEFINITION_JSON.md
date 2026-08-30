# Agent Definition JSON Contract

## Status and purpose

This is the normative design for schema version 1. Milestone 1 installed its
machine-readable JSON Schema, typed mapping, and fixtures. Later milestones may
extend component-specific validation, but a field change must update every
document and fixture in the same commit.

The document is the complete static definition. Repository lifecycle metadata,
runtime state, access codes, secrets, and client configuration remain outside
it.

## Format rules

- Encoding is UTF-8 JSON.
- The schema uses JSON Schema draft 2020-12.
- The root is an object and rejects unknown properties.
- Definition keys and all local IDs use stable restricted strings, not display
  names.
- Arrays whose order affects execution retain author order.
- Set-like capability/tag arrays are canonicalized to unique values while
  retaining first occurrence order.
- `null` is permitted only where explicitly declared.
- Published documents are canonicalized by the backend before hashing.
- The content hash is SHA-256 over the backend's canonical serialization and
  excludes repository status, timestamps, and activation metadata.

## Root document

```json
{
  "$schema": "/agent-definitions/schema/agent-definition.schema.json",
  "schemaVersion": 1,
  "key": "core.example",
  "revision": 1,
  "metadata": {},
  "interaction": {},
  "lifecycle": {},
  "storage": [],
  "resources": [],
  "states": [],
  "transitions": [],
  "verification": {
    "scenarios": []
  }
}
```

| Field | Required | Contract |
| --- | --- | --- |
| `$schema` | yes | Stable schema resource identifier; informational after backend version selection |
| `schemaVersion` | yes | Integer `1` |
| `key` | yes | Stable dotted public key, unique in the catalog |
| `revision` | yes | Positive integer, unique and increasing for this key |
| `metadata` | yes | Human/catalog metadata |
| `interaction` | yes | Sensing and behaviour capability declarations |
| `lifecycle` | yes | Initial state, creation, initialization, and reset |
| `storage` | yes | Ordered typed storage declarations; may be empty |
| `resources` | yes | Ordered immutable definition resources; may be empty |
| `states` | yes | Nonempty list of state definitions |
| `transitions` | yes | Ordered transition records; may be empty |
| `verification` | no | Author-owned examples/scenarios; not production runtime state |

`revision` is content identity, `schemaVersion` is document-language identity,
and component `version` is component-contract identity. They are never inferred
from one another.

## Metadata

```json
{
  "displayName": "Friendly Example",
  "description": "Demonstrates a small conversational flow.",
  "categoryPath": "core",
  "languageCode": "en",
  "tags": ["valerian", "conversation"]
}
```

- `displayName` and `description` are required nonblank strings.
- `categoryPath` is an explicit dotted catalog path.
- `languageCode` is an ISO-style code used by the current catalog, or `null`
  for deliberately multilingual behavior.
- `tags` is an ordered set of nonblank stable strings.
- Lifecycle status, timestamps, author identity, and source provenance are
  repository metadata, not metadata supplied as trusted content by imports.

## Interaction contract

```json
{
  "supportedObservations": ["obs.user_utterance"],
  "supportedBehaviourModalities": ["speech"],
  "profileTags": ["conversation"]
}
```

Version 1 preserves the existing public identifiers:

### Observation identifiers

- `obs.user_utterance`
- `obs.emotion.face`
- `obs.human.presence`
- `obs.social.grouping`
- `obs.social.context`
- `obs.social.situation_change`
- `obs.hand.sign`
- `obs.weather.current`
- `obs.weather.forecast`

### Behaviour modality identifiers

- `speech`
- `nonVerbal.gesture`
- `nonVerbal.facialExpression`
- `nonVerbal.gaze`
- `nonVerbal.motion`
- `motion.handSign`
- `display`

The schema accepts registered future identifiers through an explicit registry
rule rather than silently accepting arbitrary strings. Version 1 validation
must at least recognize every identifier needed by the main catalog.

## Lifecycle

```json
{
  "initialStateId": "conversation",
  "startOnCreation": true,
  "initializers": [],
  "reset": {
    "storage": "initial",
    "history": "clear"
  }
}
```

- `initialStateId` references a root state. If it is composite, its initial
  child chain resolves the initial active leaf.
- `startOnCreation` preserves whether instance creation invokes normal start
  behavior immediately.
- `initializers` is an ordered list of component envelopes.
- Version 1 reset supports restoring initial storage and clearing history. Add
  another mode only when an existing main agent demonstrates the need.

An initializer may write only declared storage keys. A declaration may have an
inline initial value or be produced by exactly one initializer, not both.

## Storage declarations

```json
{
  "key": "outcome",
  "description": "Structured result exported to the caller.",
  "valueSchema": {
    "type": "object",
    "required": ["completed"],
    "properties": {
      "completed": { "type": "boolean" }
    },
    "additionalProperties": false
  },
  "required": false,
  "visibility": "outcome",
  "reset": "initial"
}
```

Required fields are `key`, `valueSchema`, `required`, `visibility`, and `reset`.
`description`, `initialValue`, and designer examples in the `examples` array
are optional.

`visibility` is `internal` or `outcome`. `reset` is `initial`, `preserve`, or
`remove`; version 1 migrations should select only modes supported by existing
runtime semantics and tests.

`valueSchema` is a constrained embedded JSON Schema. The implementation must
document supported keywords and reject unsupported keywords rather than
pretending to enforce them.

Schema version 1 supports this recursive subset: required `type`; descriptive
`title` and `description`; object `properties`, `required`, and boolean
`additionalProperties`; array `items`, `minItems`, and `maxItems`; string
`minLength` and `maxLength`; numeric `minimum` and `maximum`; and `enum` or
`const`. Unknown keywords are structural errors. Semantic validation enforces
keyword/type context, declared required properties, ordered bounds, initial
values, and component storage-shape compatibility.

## Component envelope

Policies, decisions, actions, initializers, selectors, and deterministic
behaviour strategies use:

```json
{
  "kind": "prometheus.policy.prompt",
  "version": 1,
  "config": {}
}
```

The root schema validates the envelope. The component registry validates
`config` against the schema registered for `(kind, version)`.

Forbidden keys and values include `class`, `className`, `beanName`, scripts,
source code, filesystem paths outside packaged resource references, provider
credentials, and arbitrary URLs used as executable integrations.

Recommended stable namespaces are:

- `prometheus.*` for reusable framework components;
- `core.*` for trusted deterministic main-catalog components;
- a future domain namespace for separately delivered component modules.

Do not encode Java package names in a kind.

## Prompt sections

Prompt-bearing component configuration uses:

```json
{
  "sections": [
    {
      "id": "persona",
      "kind": "persona",
      "content": "You are a concise and friendly conversational agent."
    },
    {
      "id": "objective",
      "kind": "objective",
      "content": "Help the user formulate one concrete next step."
    }
  ]
}
```

- Section IDs are unique within their prompt.
- `kind` is a registered authoring category used for guidance and composition.
- `content` is persisted exactly after line-ending normalization.
- Array order is composition order.
- The default composition separator is two newline characters.
- Empty sections are rejected or removed before publication.

Components keep prompts with different execution roles separate, for example
`responsePrompt`, `starterPrompt`, `decisionPrompt`, `summaryPrompt`,
`nonverbalPlanPrompt`, and `gesturePrompt`. Each prompt uses the same section
shape.

Storage bindings and expected value schemas are explicit component
configuration. Prompt content must not create undeclared storage dependencies
through an undocumented interpolation convention.

## State definitions

### Atomic state

```json
{
  "id": "conversation",
  "name": "Conversation",
  "kind": "atomic",
  "entryMode": "start",
  "oblivious": false,
  "eventSelector": {
    "kind": "prometheus.selector.state-path",
    "version": 1,
    "config": {}
  },
  "policy": {
    "kind": "prometheus.policy.prompt",
    "version": 1,
    "config": {}
  }
}
```

### Composite state

```json
{
  "id": "session",
  "name": "Session",
  "kind": "composite",
  "entryMode": "start",
  "oblivious": false,
  "eventSelector": null,
  "policy": {
    "kind": "prometheus.policy.prompt",
    "version": 1,
    "config": {}
  },
  "childStateIds": ["introduction", "conversation", "done"],
  "initialChildStateId": "introduction"
}
```

### Final state

```json
{
  "id": "done",
  "name": "Done",
  "kind": "final"
}
```

`entryMode` is:

- `start`: entering through a transition invokes the target's starter behavior;
- `reprocess-event`: entering re-acknowledges the triggering event in the
  target, preserving current PROMETHEUS semantics.

Final states contain no policy, selector, oblivious flag, children, or outgoing
transition. Composite children have exactly one parent. Containment is not
inferred from naming.

The first designer creates one atomic state with ID `main` automatically. The
state remains a normal explicit JSON record even while the UI calls it
implicit.

## Transitions

```json
{
  "id": "finish",
  "sourceStateId": "conversation",
  "targetStateId": "done",
  "order": 10,
  "decisions": [
    {
      "kind": "prometheus.decision.prompt",
      "version": 1,
      "config": {}
    }
  ],
  "actions": [
    {
      "kind": "prometheus.action.extract",
      "version": 1,
      "config": {
        "targetStorageKey": "outcome"
      }
    }
  ]
}
```

- Transition IDs are globally unique within the definition.
- Source and target IDs must exist.
- `order` is a nonnegative integer unique within a source state. Gaps are
  allowed so the UI can reorder without renumbering everything.
- Decisions are ANDed in list order and may short-circuit after a rejection.
- An empty decision list is unconditional.
- Actions run in list order only after acceptance.
- Composite-state transitions retain precedence over active descendant
  transitions regardless of numeric order in another source.

## Event selectors

Selectors use registered component envelopes so current and future recursive
selection can evolve without arbitrary expressions. Version 1 must represent:

- any event;
- current active state path;
- event type;
- actor;
- kind;
- state ID;
- nested all/any composition.

Selectors reference stable state IDs. Existing state display names are migrated
to IDs before publication.

## Resources

A resource has a stable ID, a registered resource kind/version, and inline or
packaged immutable content validated by that resource component. Version 1
implements only resource forms required by the main definitions, especially
typed initializer choices and reusable prompt data.

The structural envelope is explicit and contains no deployment path or remote
integration:

```json
{
  "id": "signs",
  "kind": "prometheus.resource.typed-choices",
  "version": 1,
  "config": {
    "values": ["rock", "paper", "scissors"]
  }
}
```

Large future knowledge catalogs may use packaged resource references with a
hash. Paths are catalog-relative, cannot escape their root, and are resolved by
the backend. Remote mutable URLs are deployment integrations and are excluded.

## Verification scenarios

Optional scenarios document author expectations and support preview/regression
tests. A scenario may provide:

- name and description;
- deterministic initial values or initializer seed;
- an ordered input-event script;
- expected state path, storage fragments, and deterministic behaviour
  fragments.

Schema version 1 names these fields `name`, optional `description`, optional
`initializerSeed`, optional `initialStorage`, ordered `events`, and `expected`.
Each event has `type`, optional `actor` and `kind`, and a string `payload`.
Expectations may contain `activeStatePath`, `storage`, and
`behaviourFragments`. These structures are verification data only and never
become runtime instance history.

Scenarios are definition-owned verification data but are not replayed during
production runtime. Model-dependent natural-language exactness is not a valid
deterministic assertion.

## Complete illustrative definition

This example follows the executable Milestone 1 structural contract. Semantic
and component-specific validation is added by the following milestones.

```json
{
  "$schema": "/agent-definitions/schema/agent-definition.schema.json",
  "schemaVersion": 1,
  "key": "core.friendly_example",
  "revision": 1,
  "metadata": {
    "displayName": "Friendly Example",
    "description": "Greets the user and ends when they ask to finish.",
    "categoryPath": "core",
    "languageCode": "en",
    "tags": ["valerian", "conversation"]
  },
  "interaction": {
    "supportedObservations": ["obs.user_utterance"],
    "supportedBehaviourModalities": ["speech"],
    "profileTags": ["conversation"]
  },
  "lifecycle": {
    "initialStateId": "conversation",
    "startOnCreation": true,
    "initializers": [],
    "reset": {
      "storage": "initial",
      "history": "clear"
    }
  },
  "storage": [],
  "resources": [],
  "states": [
    {
      "id": "conversation",
      "name": "Conversation",
      "kind": "atomic",
      "entryMode": "start",
      "oblivious": false,
      "eventSelector": {
        "kind": "prometheus.selector.state-path",
        "version": 1,
        "config": {}
      },
      "policy": {
        "kind": "prometheus.policy.prompt",
        "version": 1,
        "config": {
          "responsePrompt": {
            "sections": [
              {
                "id": "persona",
                "kind": "persona",
                "content": "You are concise, friendly, and curious."
              },
              {
                "id": "objective",
                "kind": "objective",
                "content": "Have a short conversation and respect a request to finish."
              }
            ]
          },
          "starterPrompt": {
            "sections": [
              {
                "id": "greeting",
                "kind": "starter",
                "content": "Greet the user and ask what they would like to discuss."
              }
            ]
          },
          "storageBindings": []
        }
      }
    },
    {
      "id": "done",
      "name": "Done",
      "kind": "final"
    }
  ],
  "transitions": [
    {
      "id": "finish",
      "sourceStateId": "conversation",
      "targetStateId": "done",
      "order": 10,
      "decisions": [
        {
          "kind": "prometheus.decision.prompt",
          "version": 1,
          "config": {
            "decisionPrompt": {
              "sections": [
                {
                  "id": "criterion",
                  "kind": "transition-criterion",
                  "content": "Accept only when the user clearly asks to end the conversation."
                }
              ]
            }
          }
        }
      ],
      "actions": []
    }
  ],
  "verification": {
    "scenarios": []
  }
}
```

## Semantic publication checklist

A revision cannot publish unless:

- its key/revision matches the repository identity;
- all IDs are unique and references resolve;
- the initial state and all composite initial children resolve;
- containment is valid and acyclic;
- all required states are reachable according to the agreed warning/error rule;
- final states and transitions obey graph invariants;
- every component kind/version is registered;
- every component config validates;
- selectors, storage bindings, and resource references resolve;
- consumed observations and emitted modalities are declared;
- prompt sections are valid and within configured limits;
- initial values and initializer outputs match storage schemas;
- compilation succeeds.

## Explicitly forbidden document concerns

- Regulation in schema version 1
- Database IDs, timestamps, optimistic versions, or active status
- Runtime current state, storage values, events, or behaviour history
- Access codes and deployment activation rules
- Model credentials, endpoints, voices, or browser settings
- Java class/bean names or executable scripts
- Java package-derived category data
- Compatibility/fallback fields for the removed Java definition approach
