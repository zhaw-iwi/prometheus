# Coordination consolidation and reassessment — 2026-11-11

## Scope and provenance

Consolidate the office's uncommitted ES-08 work with the home-session checkpoint,
then reassess the original roadmap. This pass does not attempt the remaining
speech-assembly repair or declare ES-08 accepted. Capacity remains deferred under
the owner's November 11 scope update. No deployment or `agents` promotion.

The common base was `be3109e9` (ES-07). GitHub `main` contained one subsequent
commit, `02df87b` (52 changed files), while this device retained 17 modified and
two untracked files. There were no divergent committed histories to merge.
Local `main` was fast-forwarded to the home checkpoint after preserving the office
work, then the useful differences were reconciled individually.

Recovery copies on this device:

- `.git/codex-consolidation-20261111/`: byte-for-byte copies of all 19 files,
  SHA-256/size manifest, binary patch, original status and three-way comparisons.
- Retained stash named `Office ES-08 work preserved before home consolidation
  2026-11-11`, also protected by local branch `backup/office-es08-20261111`.
- Existing ignored credentials and raw test artifacts were left in place. Other
  worktrees and deployment branches were not changed. The backup branch is local;
  the reconciled result is the `main` branch content intended for GitHub.

## Reconciliation decisions

| Area | Consolidated result |
| --- | --- |
| `CoordinationUpdateAction` and `CoordinationCaptureUnitTest` | Retain office handling of clear unfinished answers through SUPPORT, preserving a pending response and its deadline without inference or contribution spending. Keep all home guards/current-turn context. |
| Proposal/arming guidance in `CoordinationUpdateAction` and `CoordinationPolicy` | Keep home READY and atomic-delivery improvements; retain office guidance against inventing a supplied line or treating the need for a fresh cue as failed recognition. Positive arming guidance replaces the stylistic "Do not say done" instruction. |
| `TaskResponse` | Keep the newer home verifier, contribution reference, receipt-aware suffix capture and queued offsets. The office's earlier arming-prompt variant does not replace that implementation; positive arming facts above remove its observed conflicting stylistic instruction. |
| `CoordinationTaskContract` | Keep home final-feedback and explicit current-turn/remaining-budget guidance. The office final-feedback sentence is redundant. |
| `core_backend_journey.mjs`, `escalation_journeys.mjs` and provider recorder | Keep the home modular journeys and stronger enforced budgets. Port office unfinished-answer coverage and identified-final-contribution review into that harness, including the home persisted speech offset. Keep all captions for wider semantic review. No unused older audio manifest or parallel runner is installed. |
| Runner preflight and static/sensor tests | Home already contains equivalent text-transport rejection and connection-helper/static-path fixes. Preserve those; helper load-order differences add no behavior. |
| Browser fake provider | Verification exposed routing by an obsolete verifier prompt sentence. Recognize the structured check payload instead; two regressions exercise the actual production request. The production verifier remains unchanged. |
| Operator checklist | Preserve the office `ESCALATION_OPERATOR.md`, update its current status/environment guidance, and link it from the runner documentation. |
| `CONTEXT.MD` and README | Retain the office stable recovery summary and unfinished-answer explanation alongside the home architecture and current runner instructions. Older scenario names and looser budgets are superseded. |
| Evidence and status documents | Keep the home results/handoff authoritative for its investigation. Preserve the distinct office attempts in `ESCALATION_OFFICE_2026-11-10.md`, explicitly historical. Update the plan/current status with this consolidated assessment. Neither session's failures are erased. |

The restored nine-case Capture test class was run against the home implementation
before merging the guard: **five failures, zero errors**. The failures reproduced
unfinished answers reaching inference and missing/protected Live support behavior.
The original failure log remains at
`target/consolidation-20261111/capture-before-merge.log`.

The full merge regression also caught office arming facts exceeding the home's
340-byte atomic-update limit. The explanation was shortened within that existing
bound. A real-app browser smoke then exposed the stale fake-provider routing
described above. Both original failures are retained in the results; passing the
older tests on either device alone did not establish that these changes combined.

Three Node regression cases protect the merged feedback-review boundary: select
the identified complete assistant segment, preserve a question actually appended
inside it, honor the recorded suffix offset, and reject missing/incomplete or
unassociated evidence. A later separate Reflection question is not part of the
final task contribution. These fixture checks do not establish Live reliability.

## Original goal versus achieved behavior

The goal was a slim escalation path throughout Coordination's existing lifecycle,
plus the highest-value classroom corrections. The explicit state machine remains
the authority; help is a shared user-turn capability, not a second task engine.

| Roadmap area | What is implemented/evidenced | Remaining limit |
| --- | --- | --- |
| ES-01/02 diagnostics and recovery | Bounded content-free failure facts; phase-aware help; guarded controls; authority/budget/response preservation. The office unfinished-answer guard is now retained too. | Explanations cannot recover unknown historical causes or operate through a failed backend connection. Native wording remains fallible. |
| ES-03 acceptance | Natural starts, contextual assent, preparation evidence and fresh execution cues remain distinct. Home audit adds explicit no-start protection. | End-to-end native arming/proposal delivery still needs integrated acceptance. |
| ES-04 ordinary questions | Fresh perception, truthful missing/stale evidence and narrow safe reply fallback; agreed-trigger questions are no longer intercepted as current perception. | Synthetic tests do not establish camera accuracy or physical input quality. |
| ES-05 supported tasks | Dialogue versus output-only cues, visible finite limits, two-contribution question/feedback and explicit replacement. Current-turn context avoids answering older questions during feedback. | Factual grading is not validated; split native feedback can still fail association. No general game or assessment engine exists. |
| ES-06 social conditions | First arrival and additional-person requests are distinguished; executable count alternatives require review and acceptance. | Preliminary native speech can still promise joining/arrival before giving the correct stored count terms. |
| ES-07 unavailable service | Local last-known/uncertain guidance and read-only recovery, without automatic mutation replay. Six-client functional probe passed. | The measured old quota envelope failed. Capacity/environment selection is deferred, not solved. |
| ES-08 integrated acceptance | Real-app/MySQL/Live harness, stronger verifier evaluation, atomic short delivery, receipt-aware capture, broader unit/persistence/browser coverage and operator instructions. | **Incomplete.** No accepted full three-journey result on the consolidated tree; physical trials remain NOT RUN. |

The home checkpoint reports 871 passing Java cases, 187 Node cases, 11 preflight
checks and a final 36/36 labelled verifier evaluation. Its ten latest Live attempts
did **not** produce a complete passing journey. One riddle round and a recovery
flow have useful positive evidence, with fixture failures and real behavioral
failures distinguished in `ESCALATION_RESULTS.md`. Home raw `target/` artifacts
did not transfer through Git; those results are reported evidence, not independently
reinspected raw runs on this device.

The office record adds a separate factual failure: backend feedback credited a
piano with the Enter-key clue, and native speech repeated it. A delivery verifier
can correctly match that speech to a wrong committed response. The later verifier
evaluation therefore does not close factual assessment quality. This remains a
semantic acceptance concern, without silently expanding scope to curated answers
or a new grading engine.

## Verification of this consolidated tree

Verified: **876 full-suite Java cases passed** (one capacity opt-in skip), then
**two new browser-provider fixture cases**, **190 Node tests**, **11 runner
preflight checks**, **three real-app/local-MySQL browser cases**, journey syntax,
Maven packaging and whitespace checks. Production Java was unchanged after the
full passing run; the subsequent Java change repaired only the test provider.
Conversation/state screenshots were inspected. Owned process/schema cleanup was
confirmed. Original red runs and exact commands remain in `ESCALATION_RESULTS.md`.
Logs for this pass are under
`target/consolidation-20261111/`; disposable application runs have their own
`target/gptlive-acceptance-*` directories. No paid provider run is part of this
consolidation. The newly merged Live fixture and prompt combination needs real
Live verification during ES-08 continuation.

## Focused continuation

1. Reproduce the home two-segment feedback failure deterministically, then design
   bounded assembly of one authorized spoken contribution. Preserve the original
   deadline, ownership, revision, interruption/stop and playback fences. Do not
   make AMBIGUOUS successful or replay an uncertain spent contribution.
2. Correct misleading preliminary proposal speech at its actual delivery source.
   Stored terms being correct is insufficient when spoken negotiation promises a
   different trigger. Prompt-only waiting and acknowledgment cancellation have
   already shown limits.
3. Run each of the three bounded real Live journeys after a causal correction,
   with actual transcript/admitted-input review and persisted-state assertions.
   Include unfinished-answer recovery, revised agreement acceptance, social
   controls and factual feedback review. Keep all failed attempts. A paid pass
   on an earlier implementation does not accept this consolidated tree.
4. Record physical microphone/speaker/camera trials separately. Revisit deployment
   environment and sustained classroom capacity when the owner resumes that scope.

No owner decision was needed to consolidate. A new mixed cue/dialogue protocol,
validated-content assessment engine, expanded authority/deadlines, or deployment
change would need an explicit scope decision. This pass stops after consolidation
and reassessment so the next implementation scope can be chosen deliberately.
