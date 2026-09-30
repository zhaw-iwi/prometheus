# Capability awareness

Authorized 2026-09-30: implement on main, commit and push each completed milestone,
then merge main into feature/gptlive and main into agents for deployment. Preserve
normal ancestry so later feature/gptlive -> main -> agents work remains possible.

## Scope and decisions

- A definition opts in with `capabilityAwareness()`; default false. Persist the
  flag in the instance interaction profile. Explicitly opt in core definitions;
  shared factories must not grant it to use-case or deployment definitions.
- Derive bounded reference JSON from the instance profile without inference.
  Supply it automatically to behaviour inference and eligible Live sessions,
  independent of state selectors, history, sensor freshness or client controls.
- Legacy saved instances remain opted out; create new instances deliberately.
  Preserve history. No data backfill, new tasks, sensor controls or Live opt-in.
- Talk to Me keeps exact-text deterministic output, even though its flag is true.
- No initialization event: the persisted profile remains authoritative.
- Test with synthetic providers and disposable localhost MySQL, never the
  configured deployment database. Deployment database checks are read-only.

## Milestones and acceptance

1. CA-01 / 197: declaration, persistence JSON and deterministic description.
   Unit checks for catalog opt-ins, legacy defaults, independent Live flag,
   exact declared channels, unknown capabilities and bounded full-core payloads.
2. CA-02 / 198: automatic ordinary behaviour context. Cover initial/state-entry,
   nested/final states, explicit generation, speculation and embodiment paths;
   preserve opt-outs, deterministic policies and inference counts.
3. CA-03 / 199: stable Live context at startup and state updates/reconnect.
   Cover history selection, expiry, deduplication, instruction budgets and opt-outs.
4. CA-04 / 200: scoped persistence/reset smoke on disposable MySQL, full Java
   regression, existing Node suites and focused real-app Playwright acceptance.
   Synchronize README, CONTEXT, PROJECT and evidence. Provider answer quality and
   physical acoustics remain separate manual trials.
5. CA-05: merge validated main into feature/gptlive, then agents. Enable the
   agents-only core scored RPS definition; verify all other definitions stay off,
   persona coexistence, branch ancestry and deployment health/release where possible.

## Handoff

Evidence and completed checks live in CAPABILITY_AWARENESS_RESULTS.md. Any unresolved
unexpected issue must be summarized in `.agents/issue.md` and committed/pushed so
work can resume from another machine. Do not expose local/provider/database secrets.
