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

## GV-03 (2026-11-04, complete)

Graph hydration no longer delays connection, is aborted on scope change and has
a 15-second timeout with a visible state-name fallback. Newer monitor IDs take
precedence over a late graph snapshot. Cockpit reset explicitly reloads the graph.
The popup retains one cockpit-owned monitor connection and the same interactive
DOM. Inspection does not add polling or read the full graph on monitor updates.

Final verification:

- `npm.cmd run test:state-graph:unit`: 4/4 passed. Real-agent visual review exposed
  overlapping neighbours around a small central node; minimum hub spacing and a
  non-overlap regression now protect this. Final states are not chosen as hubs.
- `python tests/playwright/run_static.py tests/playwright/valerian-state-view.spec.mjs
  tests/playwright/valerian-state-graph.spec.mjs tests/playwright/valerian-lifecycle.spec.mjs
  --grep-invert 'persisted transition'`: 18/18 passed, including six unchanged
  screenshot baselines, popup/theme/search/zoom, agent switch, disconnect/reconnect,
  stale streams/responses, late snapshots and graph-failure fallback.
- `python tests/gptlive/run_acceptance.py --database-properties
  target/coordination-mysql/admin.properties --java-tests none --browser
  --browser-mode both --live-only --browser-grep 'state view tracks a persisted transition'`:
  artifact `1225ff0efa`, 1/1 passed with Live enabled and 1/1 disabled. Uses real
  local MySQL, scoped HTTP and SSE with scripted providers. Asserts persisted
  active IDs, stable positions, manual RPS capture/result, reset, popup/docking,
  one graph read during transitions/popup and a second only on cockpit reset.
  No additional state/detail reads during the monitored journey; one monitor
  connection before explicit reset. The real popup screenshot was reviewed.
- `mvnw.cmd -q -DskipTests package`: exit 0; log `target/state-graph-package.log`.
  Syntax and `git diff --check` checks passed. GV-01's 15 Java/MySQL tests and
  unchanged query-volume measurement remain the backend evidence.

Earlier real-browser runs `909d20260c` and `4eec60d50b` stopped at test UI
navigation: the enlarged floating panel covered Send, then Reset was in an
inactive drawer tab. The journey now uses the normal Enter-to-send path and
selects the Agent tab before reset. Their disposable resources were cleaned.
The initial mocked lifecycle run similarly needed to close the drawer before
clicking logout; the corrected case and final suite passed.

No production database writes or paid provider calls were made for these
milestones. Disposable app/schema/accounts were removed; zero remaining test
schemas/accounts were verified before stopping the owned local MySQL server. Browser acceptance is
Chromium on Windows; other browser/OS rendering and physical devices are not
verified. Dense crossing routes still use selection/zoom/list inspection, and
arrows describe saved definitions rather than executed history or live guard
results. The existing database quota constraint is unchanged. No deployment is
included in GV-01 through GV-03; subsequent authorized deployment integration is
recorded below.

## Deployment integration (2026-11-04)

The owner authorized merging and pushing the completed viewer to `agents` for
Heroku redeployment, while another session continues Coordination work on main.
This merge pins main commit `6d44a58` onto `agents` at `c2c8663`; newer main work
is excluded. Work used the existing clean agents checkout and an independently
initialized MySQL data directory/port. The other checkout was not switched,
stashed, edited or committed. Three documentation conflicts retain both graph
guidance and deployment-specific catalog/embodiment documentation; runtime code
merged without conflict. Production configuration and workflow are unchanged.

Merged verification: 74/74 Java tests in StateGraphUnitTest,
StateGraphIntegrationTest, AgentNestedOuterStateRoutingUnitTest,
StateTransitionUnitTest, AgentDefinitionRegistryUnitTest, AgentEmbodimentUnitTest,
ScopedDemoControllerIntegrationTest and GalaDinnerReceptionUnitTest. All passed
without failures/errors/skips. The disposable runner artifact is `4195947093` in
the agents checkout. Its persisted HTTP/SSE browser journey passed once with Live
enabled and once disabled. Four graph JavaScript tests and all eighteen static
browser cases/six unchanged visual baselines passed on the merged tree.
Packaging (`mvnw.cmd -q -DskipTests package`) and whitespace checks passed.
The static asset server logged harmless aborted requests during navigation;
all browser assertions passed. The owned MySQL server stopped after verification
that disposable schemas/accounts were removed. No production test writes or
paid inference calls were made. Pushing this merge triggers the existing
`deployment.yml` workflow; its result and Heroku health must be verified after push.
