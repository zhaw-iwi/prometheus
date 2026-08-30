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
