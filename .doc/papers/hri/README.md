# HRI 2027 paper workspace

Start with [PAPER_BRIEF.md](PAPER_BRIEF.md). It captures the research direction
developed with the project owner on 15 November 2026: motivation, contribution,
proposed student study, implementation boundaries, and the next decisions.

[STUDY.md](STUDY.md) is the implementation handoff for the online text-to-text
study system: participant flow, task readiness, recording, assessments, recovery,
operations, and acceptance. Read it alongside the brief before study-system work.

[PLAN.md](PLAN.md) is the implementation roadmap, with dependencies, testable
milestones, verification levels, and readiness gates. The confirmed target is all
three executable scenarios in English, using text and asynchronous/scheduled waves.

[REFERENCES.md](REFERENCES.md) records the papers discussed, their intended use,
source locations, reading status, and venue requirements. Its literature map
connects multimodality, design/framework precedents, joint coordination,
generative interaction/execution, evaluation, and scenario context to the paper's
claims. It includes a compact citation plan and distinguishes published work,
preprints, owner-supplied manuscripts, and engineering sources. The supplied PDFs
remain outside the repository; public links are provided where known.

Implementation is under way on `experiment/hri-2027-coordination`.
[RESULTS.md](RESULTS.md) records engineering evidence and remaining limitations.
[IMPLEMENTATION.md](IMPLEMENTATION.md) describes the current API and persistence.
[SCENARIOS.md](SCENARIOS.md) records executable scenario boundaries and coding notes.
No manuscript, human-study results, or completed ethics process is claimed.

Before project work, follow the repository bootstrap:

1. Read [CODEX.md](../../../.agents/CODEX.md) and
   [CONTEXT.MD](../../../.agents/CONTEXT.MD).
2. Read [PROJECT.md](../../../PROJECT.md) from the beginning through
   **Current milestone state**, stopping before **Historical milestones checklist**.
3. Check the active branch/worktree and inspect the relevant code and tests listed
   in the brief. Historical evidence is not a substitute for current code.
4. Give a concise mental model and focused scope before making changes. Preserve
   unrelated work and verify the changes. The owner authorized committing/pushing
   each completed milestone and continuing, halting for unforeseen owner decisions.

The immediate next work is the study-system implementation scoped in PLAN.md,
alongside finalizing the protocol and annotated paper draft. Research procedures
marked as proposals must not be described as completed methods or findings.

Implementation instrument and coding freeze: [INSTRUMENTS.md](INSTRUMENTS.md).
Operator/API details: [IMPLEMENTATION.md](IMPLEMENTATION.md).

[ACCEPTANCE.md](ACCEPTANCE.md) provides the complete offline acceptance command,
source/material manifest and configuration-free package workflow.

[PROVIDER_MATRIX.md](PROVIDER_MATRIX.md) declares the finite A/B/C semantic trials
and review criteria. A technical script pass does not replace the separate review.
