# Codex Engineering Guide

This file defines reusable software-engineering and execution practices for
Codex. It is intentionally project-neutral. Product architecture, maturity,
compatibility requirements, repository structure, and current status belong in
the project-specific context named by the session bootstrap prompt.

## 1. Establish Context Before Acting

At the start of a task:

1. Read the project-specific context and current-status sources identified by
   the repository's bootstrap instructions.
2. Inspect the active branch, worktree, relevant implementation, configuration,
   and neighboring tests.
3. Read historical records selectively when the task depends on an earlier
   decision. Do not load a complete audit trail by default.
4. Resolve conflicts using this order:
   - the user's current goal and explicit constraints;
   - running code, tests, schemas, and configuration;
   - current project documentation;
   - historical documentation.

If documentation disagrees with the implementation, treat the implementation
as the current behavior and correct relevant documentation in the same unit of
work.

## 2. Scope and Change Safety

- Make the smallest coherent change that completes the requested outcome.
- Preserve user changes and unrelated work in a dirty worktree.
- Do not revert, delete, migrate, publish, or commit outside the authorized
  scope.
- State assumptions that materially affect architecture, compatibility, data,
  security, or user-visible behavior.
- Ask for direction when a missing decision would substantially change the
  result or require new authority.
- Follow the project's compatibility and migration policy. Do not assume either
  clean-slate freedom or backward compatibility without project context.

## 3. Engineering Principles

### Modularity and separation

- Keep components small and focused.
- Separate UI, API, application, domain, persistence, and infrastructure
  responsibilities.
- Validate and translate at boundaries; keep core behavior in the appropriate
  domain or application layer.
- Expose the smallest stable interface needed by callers.

### Clarity and orthogonality

- Prefer existing project concepts and patterns over parallel abstractions.
- Prefer explicit control flow and intent-revealing names over cleverness.
- Keep functions cohesive and remove duplication when a shared concept is
  genuinely stable.
- Avoid hidden coupling, global side effects, and alternative implementations
  of the same responsibility.

### Determinism and observability

- Control randomness, clocks, and external dependencies in tests.
- Keep generated artifacts reproducible or clearly identify them as
  non-deterministic.
- Fail with actionable messages at the boundary where a problem can be
  understood.
- Add logs or diagnostics where failures would otherwise be difficult to
  investigate, without leaking secrets or sensitive data.

### Security and data safety

- Validate public inputs and reject unsafe values.
- Do not commit credentials, tokens, personal data, or generated secrets.
- Prevent path traversal, injection, arbitrary code execution, and accidental
  privilege expansion.
- Follow the project's existing error contract. Introduce or change a public
  error shape only as an explicit contract decision with tests.

## 4. Milestone Workflow

When a repository uses milestone-based development, complete one focused
milestone at a time:

1. Define the goal, boundaries, and concrete deliverables.
2. Inspect the most relevant implementation and tests before editing.
3. Implement the change end to end, removing code made obsolete by that change
   when project policy permits it.
4. Add or update the smallest high-value test set.
5. Run verification proportional to the change and fix failures.
6. Update current project documentation and the engineering audit trail where
   required.
7. Report the outcome, verification, and remaining risks, then stop for review.

Do not create commits or publish changes unless the user explicitly requests
it.

## 5. Testing

Prefer the lowest test level that protects the changed behavior:

- unit tests for pure rules and branching logic;
- integration or contract tests for persistence and system boundaries;
- end-to-end tests for critical user journeys or rendering behavior.

A minimal high-value test set normally covers:

- the primary success path;
- one representative failure or rejection path;
- each non-trivial branch introduced or changed.

Tests must be deterministic and isolated. Use controlled clocks, seeds, mocks,
fixtures, and temporary storage where appropriate. Avoid network dependencies
unless they are an explicit part of the requested verification.

Run the focused suite first. Expand to broader regression coverage when the
change affects shared infrastructure, public contracts, persistence, or several
consumers. Report commands that were actually run; never imply unrun checks
passed.

## 6. Documentation

Update documentation in the same milestone when behavior changes affect:

- setup, configuration, or deployment;
- public APIs, schemas, or command-line interfaces;
- user-visible behavior;
- architecture or extension points;
- known limitations or current project status.

Keep entry-point documentation practical and current. Keep architectural
context concise and stable. Keep historical records as an audit trail rather
than treating them as the current specification. Avoid copying volatile lists
or implementation details into several documents when one authoritative source
and a pointer are sufficient.

## 7. Code Quality and Cleanup

- Match the repository's language, formatting, dependency, and naming
  conventions.
- Remove unused imports, dead helpers, and commented-out code created or exposed
  by the change.
- Do not preserve obsolete behavior unless project context or milestone scope
  requires it.
- Do not broaden a cleanup beyond the requested area merely because adjacent
  code could be improved.
- Keep dependencies minimal and justify additions.

## 8. Completion Handoff

Before declaring a milestone complete, confirm that:

- the requested behavior is implemented;
- relevant tests cover it and the reported verification passed;
- required documentation is synchronized;
- unrelated work remains untouched;
- remaining risks, assumptions, and unverified areas are explicit.

Stop after the handoff so the user can review and commit.
