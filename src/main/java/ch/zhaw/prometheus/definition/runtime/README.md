# Immutable-definition runtime

`AgentRuntimeEngine` executes one immutable `CompiledAgentDefinition` against a
small mutable `AgentRuntimeInstance`. The instance pins the database revision ID
and contains only its active leaf, storage, history, and started flag. Static
states, transitions, prompts, selectors, and components remain shared.

Runtime rules preserve the current state-machine contract:

- history paths and active paths use stable state IDs from root to leaf;
- composite transitions are checked before descendant transitions;
- transitions for one source are checked by numeric order and stop at the first
  accepted transition;
- decisions are ANDed with short-circuiting and actions execute in list order;
- `start` entry generates starter behaviour, while `reprocess-event` applies the
  same already-recorded event in the target without recording it twice;
- oblivious entry removes only events whose path contains that state;
- a final leaf is inactive and cannot generate or transition;
- reset clears history, restores `initial` storage, retains `preserve` storage,
  removes `remove` storage, and returns to the compiled initial leaf.

An event-reprocess chain is capped at 1,024 accepted transitions. The legacy
recursive path would eventually overflow on an unconditional reprocess cycle;
the declarative engine instead fails explicitly and deterministically. Ordinary
cyclic state machines remain supported because each acknowledged event starts a
new bounded dispatch.

Every operation returns before/after snapshots plus appended/removed events,
storage changes, accepted transition IDs, and optional behaviour. Callers own
persistence and publication. `AgentRuntimeContext` supplies the trusted typed
component executor and injected `RandomGenerator`; neither the engine nor its
tests call providers, repositories, Spring, or JPA.
