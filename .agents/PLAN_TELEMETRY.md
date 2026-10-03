# Unified activity and telemetry

Authorized 2026-10-03: implement sequentially on `feature/gptlive`, commit and
push each milestone, continue automatically, then merge to main and main to
agents and verify Heroku. Preserve unrelated files and deployment personas.

Mental model: one content-free activity contract supplies the interaction footer
and diagnostic export. Existing transcription, playback and Live observations
remain the source of truth; backend progress adds missing in-flight evidence.
Task control, inference counts and database query budgets must remain unchanged.

## Milestone 205 — Shared activity reporting

- Bounded in-memory operations/stages, correlations, monotonic durations and
  explicit terminal outcomes; emit over the existing scoped monitor stream.
- Model, processing, persistence and Live announcement stages; queue boundaries,
  source/segment identities, safe inference metadata and token usage.
- Aggregate task cue waiting reasons; preserve existing guard decisions.
- Preserve safe Live provider errors/close reasons, cumulative usage/context
  utilisation, configuration and collection coverage. Never retain raw audio,
  transcript text, prompts, credentials, device IDs or provider error messages.
- Unit/concurrency/privacy tests plus local MySQL HTTP/SSE and query-budget smoke.

## Milestone 206 — Unified footer and Telemetry

- Rename Interaction Timing to Telemetry; retain compatible export fields.
- One status footer for Text, Continuous and GPT-Live, fed by one client model.
  Consolidate redundant processing/playback/context summaries. Keep device
  controls and their necessary local feedback.
- Spinner for active work, elapsed time, static intentional waiting, honest
  unknown/error states, reduced motion and accessible announcements.
- Add bounded operation history/configuration/coverage to JSON and readable
  Telemetry details; add timestamped issue markers with fixed categories.
- Client unit and Playwright desktop/mobile light/dark visual/lifecycle checks.

## Milestone 207 — Integrated acceptance and deployment

- Real scoped application/MySQL/SSE/browser trials with controlled inference and
  synthetic media: delayed activation, release, failure, overlap, cue waiting,
  reset/switch/disconnect and feature-disabled compatibility.
- Full appropriate regression, privacy/bounds/query-budget evidence and docs.
- Commit/push, main integration, agents integration with persona checks, deploy
  and verify release, health/login, client assets and bounded startup logs.

## Evidence rules

Use `python tests/gptlive/run_acceptance.py --database-properties
src/main/resources/application-test.properties ...`; never start Maven/app with
the ignored production properties. Synthetic success does not prove audibility.
Provider transcript intervals and append acknowledgements are not playback
completion. Keep clocks separate and mark approximate associations. Collection
adds no model requests or database polling. No automatic retry of uncertain
actions. No deployment until all three milestones pass.

OpenAI Live contracts were checked during design using official documentation:
https://developers.openai.com/api/docs/guides/live-conversations

Status: 205-207 complete, committed/pushed, integrated through main to agents, and verified on Heroku v106. Results: TELEMETRY_RESULTS.md.
