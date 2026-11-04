# State graph viewer evidence

## GV-01 (2026-11-04, complete)

Added a scoped read-only graph description and active-state IDs in state/monitor
snapshots. IDs retain duplicate labels, cycles and parallel/shared transitions.
Nested regions explicitly represent membership and initial states, including
shared members. Transition priority is list order, conditions are conjunctive,
and empty conditions are unconditional. Inspection never evaluates these rules.
Only safe operation types/labels are exposed; unknown operation classes use their
type name rather than inferred meaning or serialized policy content.

Local verification uses the existing disposable runner and owned MySQL instance
on loopback port 60878; no configured application or production schema is used.

`f9b8a49f89` passed all 15 tests in StateGraphUnitTest, StateGraphIntegrationTest,
AgentNestedOuterStateRoutingUnitTest, StateTransitionUnitTest and
QueryVolumeIntegrationTest. The existing synthetic Coordination minute remains
1,993 questions (quota headroom still false). The loaded monitor snapshot adds
zero SQL statements. No failures/errors/skips; disposable resources were removed.
Command: `python tests/gptlive/run_acceptance.py --database-properties
target/coordination-mysql/admin.properties --java-tests
StateGraphUnitTest,StateGraphIntegrationTest,AgentNestedOuterStateRoutingUnitTest,StateTransitionUnitTest,QueryVolumeIntegrationTest`.
Whitespace checks passed. UI integration follows in GV-02/03.

## GV-02 (2026-11-04, complete)

Added a local SVG renderer with deterministic radial/routing-hub layout,
grouped parallel routes, individually inspectable ordered transitions,
initial/final/current states, nested expansion and shared-region membership.
Zoom, pan, fit, centre-current, keyboard inspection and a searchable list work
in the existing detachable state view. No runtime dependency or CDN was added.
Graph loading is scoped to the connected agent; state names remain a fallback.

`npm.cmd run test:state-graph:unit`: 3/3 passed (projection, identities, cycles,
shared/nested collapse, routing groups and finite/stable geometry).
`python tests/playwright/run_static.py tests/playwright/valerian-state-graph.spec.mjs
tests/playwright/valerian-state-view.spec.mjs --grep-invert 'persisted transition'`:
9/9 passed. The four graph cases assert interactions and six checked-in Chromium
Windows screenshots: small, Coordination light/dark, nested, narrow graph/list.
Baselines were generated and visually reviewed, then verified without updates.
Five neighboring floating/popup/theme/lifecycle cases passed. The static runner
serves only local assets and cannot start an app against configured credentials.

Visual checks caught and fixed overlapping route badges, self-loop placement
and hidden list rows overridden by CSS. Arbitrary dense graphs can still have
crossing arrows; selecting states/routes, zooming and the complete details/list
are intentional inspection aids. GV-03 adds real persisted/SSE acceptance.
