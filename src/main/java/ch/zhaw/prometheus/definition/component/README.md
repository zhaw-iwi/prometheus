# Declarative component SPI

Whole-agent definitions may select only registered `(kind, version)` pairs.
`AgentComponentDefinition` is the trusted Java extension point: every entry
declares its category, a Draft 2020-12 configuration schema, designer metadata,
semantic dependencies, and a factory for an immutable compiled component.

To add a component:

1. Choose a stable, non-Java kind name and a positive component-contract
   version. Increment the version when persisted configuration semantics change.
2. Implement an immutable `CompiledComponent` category interface. It must not
   contain mutable instance state, JPA entities, repositories, Spring services,
   provider credentials, executable source, or bean/class names from JSON.
3. Register a strict configuration schema (`additionalProperties: false`), safe
   UI label/default/example metadata, semantic capability/storage/reference
   declarations, and a deterministic factory.
4. Add registry-schema, diagnostic, compilation, and runtime-behaviour tests.
   Random components receive a caller-owned `RandomGenerator`; prompt components
   normalize line endings and preserve section order.

`BuiltInComponentCatalog` is the schema-version-1 framework catalog. Duplicate
registrations and invalid configuration schemas fail at registry construction.
Unknown kind/version, wrong-category placement, and invalid configuration are
definition diagnostics before a factory runs. `DefinitionCompiler` then resolves
state/transition/resource references into one shareable immutable graph.

`CompiledDefinitionCache` is keyed by immutable database revision ID and guards
that identity with the canonical SHA-256 hash. It single-flights concurrent
compilation, removes failures, supports prewarming, and exposes content-free
observer callbacks/counters. Mutable instance state and history never enter this
cache.

The prompt-catalog migration also registers `prometheus.action.prompt-behaviour`
for final-transition output and lets `prometheus.initializer.random-choice`
reference `prometheus.resource.typed-choices`. Resource references are resolved
to immutable values at compilation; runtime randomness still comes only from
the caller-owned `RandomGenerator`. Prompt storage interpolation is limited to
explicit typed `storageBindings`.

## Schema-version-1 catalog

Every entry exposes a strict configuration schema and safe schema-valid UI
default/example metadata. The complete built-in palette is:

| Category | Kinds |
| --- | --- |
| Policy | `prometheus.policy.no-op`, `prometheus.policy.prompt`, `prometheus.policy.exact-text`, `prometheus.policy.rps-reveal`, `prometheus.policy.rps-result` |
| Selector | `prometheus.selector.any`, `prometheus.selector.state-path`, `prometheus.selector.event-type`, `prometheus.selector.actor`, `prometheus.selector.event-kind`, `prometheus.selector.state-id`, `prometheus.selector.all`, `prometheus.selector.any-of` |
| Decision | `prometheus.decision.latest-event-type`, `prometheus.decision.prompt` |
| Action | `prometheus.action.extract`, `prometheus.action.increment`, `prometheus.action.prompt-behaviour`, `prometheus.action.rps-select-sign`, `prometheus.action.rps-evaluate-round` |
| Initializer | `prometheus.initializer.constant`, `prometheus.initializer.random-choice` |
| Resource | `prometheus.resource.typed-choices` |

Exact-text and RPS kinds are provider-free. Their JSON contains only typed event
and storage references; implementation names, beans, and executable content are
not configuration. The RPS actions use the deterministic sign/rules domain,
while reveal/result output shares the provider-free renderer used by the
generic runtime.
