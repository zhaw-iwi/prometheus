# State graph viewer

Started 2026-11-04 on main. The owner authorized GV-01 through GV-03 in sequence,
with a commit and push after each milestone. Halt for an unforeseen owner decision.
This scope does not include deployment or execution-history playback.

The saved State/Transition graph is authoritative. The viewer is read-only and
uses the cockpit's connection and monitor stream. Graph inspection must not run
guards, actions or inference, expose prompts/storage, or add recurring DB reads.

1. GV-01: scoped saved-graph description, stable node/edge identities, initial and
   nested regions, ordered conditions/actions, active IDs in existing monitoring.
   Gate: unit cases for cycles, parallel/shared transitions, duplicate names and
   nested/shared regions; local MySQL reload/access isolation and query regression.
2. GV-02: stable directed SVG graph, zoom/pan/fit/current, edge inspection, nested
   expansion, grouped parallel edges and searchable list. No CDN dependency.
   Gate: focused JavaScript tests and reviewed Playwright visual/interaction cases
   for small, routing-heavy and nested graphs, light/dark and narrow layouts.
3. GV-03: real HTTP/SSE updates, floating/popup/docking lifecycle, scope changes,
   disconnect/reconnect, resource/query checks, docs and operator handoff.
   Gate: disposable-MySQL browser journey, neighboring browser regression, package
   and whitespace checks. No paid providers or production test writes are needed.

Evidence and limitations: STATE_GRAPH_RESULTS.md.
