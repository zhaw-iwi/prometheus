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

## B — Contextual fictional check-in (OS-07)

Negotiate one fictional yes/no question, exact YES acknowledgment and brief,
nonjudgmental NO support. No diagnosis, dosage recommendation, real medication data
or adherence verification is involved. Counts describe simulated visible people;
they cannot identify a patient or prove privacy. Scene state persists until changed.

1. Specify words and the one-person start, multiple-person postponement, explicit
   resumption and departure-ending rules. Privately predict, then accept.
2. Use the planned scene control for **1 → 2 → 1**, before answering. Its steps
   are participant-paced and timestamped, not a fixed-delay automatic stimulus.
   The first scene asks the question; two people pause it; return stays paused.
3. Request shorter, neutral NO support while preserving the other terms. Predict
   and accept the revised proposal. Use a fresh one-person scene and Resume.
4. Answer yes/no, or Stop/Finish with a recorded failure reason. The same pending
   question remains throughout postponement/revision. Neither Resume nor revision
   spends another question. Absence during an unfinished question ends the encounter.

The browser fixture additionally changes scene during held inference and proves
the superseded output is not rendered. The live fixture uses manual scene controls
for the same sequence. Canonical generation, actual/synthetic display reports,
withholding and late reports remain separate evidence. Earlier displayed text
cannot be recalled by a later privacy change.

Code applicable facets as 1/0/NA using the rubric above: exact question/answer
wording; count-policy fidelity; no pre-acceptance question; postponement without
private repetition; explicit resumption with preserved question; revision fidelity;
and truthful departure/self-report outcomes. Score actual ordered-probe completion
and missingness separately. Unknown/stale evidence must not be scored as absence.
This is controlled context-conditioned interaction, not sensor accuracy or clinical
privacy effectiveness.

## C — Negotiated simulated sign answers (OS-08)

Agree on a short ordered set of fictional, nonclinical preference questions,
thumbs-up/down as YES/NO (either mapping), open palm as WAIT, explicit text Resume
and completion after all answers or Stop. The executor supports 1–8 distinct
questions, with optional demonstrations of all three signs before acceptance.
The acceptance fixture uses three preference questions and demonstrations.

1. Specify questions, mappings, scope and control rules. Demonstrate the agreed
   signs using the disclosed simulation buttons. Verbal claims cannot satisfy
   preparation. Save a private prediction, review and accept.
2. Answer the first question with a fresh button click. Each sign is bound to its
   pending question/run/revision, with a separate observation identity.
3. Signal open palm on the next question. Refresh if desired; wait retains the
   question and spent count. Type Resume before another answer can count.
4. Request the opposite YES/NO mapping with all other terms unchanged. Demonstrate
   again if agreed, predict and accept. The pending question and earlier answers
   remain, while only fresh observations can use the new mapping.
5. Answer remaining questions or Stop/Finish. Recording a preference is not a
   clinical outcome, and demonstration/WAIT never counts as an answer.

Code mapping/scope fidelity, preparation before acceptance, exactly one answer per
pending question, wait/resume identity, revised acceptance before use, untouched
commitments and outcome accuracy separately as 1/0/NA. Earlier answers must retain
their original meaning after a mapping swap. Compare private preferences, expressed
requirements, snapshots and predictions separately, following A's rubric. This
validates negotiated symbolic interaction, not visual gesture recognition.

The v2 timing/card and assessment freeze is in [INSTRUMENTS.md](INSTRUMENTS.md).
A first accepted guessing limit of three gets a change-to-five card; other first
limits get change-to-three. The card is fixed from that first acceptance and is
removed after a second acceptance. Record deviations rather than forcing a
successful trial. Full protocol sessions reject a different/generic executor.
