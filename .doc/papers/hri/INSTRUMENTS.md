# Procedure and coding freeze: hri-text-v2-draft

This is the software/pilot draft, not ethics approval or a validated coordination
scale. Exact item wording, IDs and sections live in
`src/main/resources/hri-study/materials-v2.json`; each invitation freezes that full
manifest and the participant information. Existing attempts never adopt later
materials. Use a new version/build after a pilot change.

The neutral Coordination Assistant, deterministic saved proposal/accepted-terms
panel and disclosed text simulations are part of the studied design. Three tasks
are allocated in persisted random blocks of three, separately for each dataset
and material version. A replacement keeps participant identity and task, has a new
attempt ID and does not consume another allocation. Report both denominators.

## Procedure

Information/consent → background → initial intention → private choices →
coordination → prediction before each acceptance → bounded trial and one revision
→ open-reference understanding → experience questionnaire → debrief → receipt.
No agreement and technical/time endings use the same applicable assessments;
withdrawal closes immediately and remains in the dataset. An ending is not task
success. Intention, secret, predictions and assessments never enter model context.

Issue the probe/revision instructions after the first accepted agreement. Before
answering to completion: A pauses/resumes its first pending question, then changes
the total limit to three (five if the first accepted limit was already three);
B advances the one–two–one scene script, revises the no-answer wording, explicitly
resumes under one visible person and answers; C answers its first question, waits
on the second with open palm, resumes and swaps yes/no mappings before answering.
Demonstrations and private prediction precede each applicable acceptance. The
revision card is fixed from the first acceptance and disappears after a second.
Do not manufacture compliance: retain early endings, deviations and assistance.
The task card and terms remain visible during understanding; this measures
interpretation with reference access, not unaided recall. Answers lock before
debrief; optional missing responses are explicit. An operator cannot edit answers.

## Questionnaire

UMUX-LITE uses the original two capability/ease items and seven agreement levels.
Store both responses; raw score = `(capabilities + ease - 2) × 100 / 12`.
Also export the separately named original regression adjustment `0.65 × raw + 22.9`.
Do not call either a measured SUS score, impute missing answers, or pool the four
exploratory coordination ratings into this scale. Missing either item means both
scores are missing. Source: Lewis, Utesch and Maher (2013),
[UMUX-LITE: When There's No Time for the SUS](https://doi.org/10.1145/2470654.2481287).
Formula/anchors checked against the original paper; wording remains in the manifest.

The clarity, correction, control and effort items are individual exploratory
ratings. Report item distributions and missingness; avoid a validated-scale claim.
Two optional free-text questions capture difficulties and expectations. Pre-items
describe familiarity rather than eligibility or an experimental manipulation.

## Human coding worksheet

One row per independently identifiable requirement and acceptance revision:

| Field | Coding rule |
| --- | --- |
| participant/attempt/task/material/revision | Export identifiers; keep replacement links |
| private intention | Private form excerpt; never assume it was communicated |
| communicated requirement + event ID | Actual participant utterance, or UNSTATED |
| accepted term + snapshot ordinal | Exact immutable agreement, or NO_AGREEMENT |
| instruction fidelity | 1 faithful, 0 conflicts/omits an explicitly communicated requirement, NA no assessable requirement/agreement |
| prediction + submission time | Private prediction before that acceptance; missing stays missing |
| understanding | 1 consistent with accepted terms, 0 inconsistent, NA not assessable; assess each item separately |
| execution evidence + event IDs | Generated, committed and displayed are distinct; no physical-action inference |
| execution fidelity | 1 matches accepted terms, 0 violates them, NA no relevant opportunity/evidence |
| intervention/deviation | Pause/revision/probe observed, assistance category, failure/early ending |
| coder/rationale/adjudication | Independent coding then adjudication; preserve disagreements |

Do not score failure to infer an unspoken preference as instruction infidelity.
Do not score an accepted but incorrect interpretation as faithful merely because
the participant accepted it. A faithful agreement can still be misunderstood or
poorly executed. `tests/hri/coding.test.mjs` rehearses these distinctions with
independently authored synthetic labels; it is not an automatic free-text judge.
Use SCENARIOS.md for task-specific rubrics and PAPER_BRIEF.md for inference limits.
