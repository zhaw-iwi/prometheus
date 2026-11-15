# Executable study scenarios and coding notes

Read STUDY.md and PAPER_BRIEF.md for the research rationale. These are draft
implementation/protocol notes, not frozen participant materials or validated scales.
Human collection remains disabled. Full instruments and material freeze are OS-09.

## A — Guessing game (OS-06)

Use the participant's privately chosen everyday object. Only their conversation
and answers enter the agent context; the private object/preference and predictions
remain researcher records. The supported executor gives secret ownership and
answers to the human, and questions/guesses to the assistant. It supports YES/NO
and optional UNKNOWN, a negotiated 1–20 question total, and guesses consuming that
same total. Different roles or unlimited questions require negotiating a supported
alternative; this is not the standalone reverse-role guessing agent.

The acceptance fixture uses an initial limit of five. It proceeds as follows:

1. Specify roles, answer choices, budget, confirmation and stopping semantics;
   review the saved proposal without executing it.
2. Privately predict the next behavior, then explicitly accept. START asks the
   first question immediately. A prediction is tied to that proposal revision.
3. Pause and explicitly resume. Refresh while paused; the pending question remains.
4. Request three questions **in total**, preserving the other commitments. Review
   and predict again before renewed acceptance. Already asked questions stay spent;
   the existing pending question is continued, not charged again.
5. Answer through completion or Stop. A final question still permits its answer.
   YES to a specific pending GUESS records human-confirmed success. Reaching the
   budget without that confirmation records exhaustion. Finish enters assessment.

The browser fixture confirms a third-question guess; the real-provider script
rejects all guesses and reaches exhaustion. Both are valid coordination outcomes.
This validates process/control behavior, not guessing accuracy or therapeutic benefit.

Draft participant revision/probe cards live in `materials-v1.json` and appear after
initial acceptance. Before OS-09 freeze, make revision instructions adaptive when
the participant already chose limit three; a prescribed no-op is not a meaningful
revision. Define the exact timing of cards for the human protocol and check their
wording in the pilot. No timing/understanding result is inferred from synthetic users.

### Coding rubric

Code each applicable facet separately: **1** supported by the record, **0** a
contradiction/omission against an explicitly communicated requirement, **NA** not
elicited or no evaluable agreement. Retain excerpts/event IDs and missingness;
do not convert unavailable evidence to success or combine these into a validated scale.

| Facet | Evidence and pass criterion |
| --- | --- |
| Role fidelity | Communicated secret owner, questioner, answerer and guesser match both the accepted text and typed activity |
| Answer/budget fidelity | Agreed answer choices and numeric question limit match the typed contract; guesses count; the final answer is permitted |
| Revision fidelity | Requested changes appear in the revised snapshot, with untouched commitments preserved and renewed acceptance preceding execution |
| Interruption/recovery | Pause spends no question, preserves the pending ID/counter, and explicit resume continues it; refresh does not duplicate an answer |
| Outcome accuracy | Reported success requires human confirmation of a pending guess; exhaustion, cancellation and technical interruption stay distinct |

Compare three separate sources: private intention/preference, what the participant
actually communicated, and the accepted specification. An unspoken private preference
is not evidence that the agent ignored an instruction. Compare predictions with the
subsequent recorded behavior separately from specification fidelity. A faithful
agreement can be misunderstood; a well-understood agreement can still omit a need.
Question usefulness and natural wording need human semantic review alongside the
deterministic budget checks. Generated text, rendering receipts and comprehension
are separate evidence categories.

Scenario B/C executor-specific notes follow with OS-07/08. No executable context or
sign capability is claimed by the A milestone.
