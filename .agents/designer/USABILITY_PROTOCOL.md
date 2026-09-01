# Designer V2 Moderated Usability Protocol

## Purpose and participants

Use this protocol for formative testing of `/valerian-design/` with
healthcare-professional and Wirtschaftsinformatik students. It tests whether a
domain expert can form a correct mental model, author a small agent, verify it,
and understand lifecycle consequences without learning JSON or state-machine
implementation vocabulary.

Recruit four to eight participants from each audience for an initial round.
Treat results as qualitative design evidence, not a performance ranking of
participants. Do not add product telemetry for this study.

## Safe test setup

- Use a disposable H2 environment or a verified dedicated
  `prometheus_designer_smoke_*` schema. Never use the normally configured
  database.
- Use only invented scenarios and synthetic data. Do not enter patient,
  participant, credential, or provider data.
- Use a test admin token and deterministic behavior fakes. Do not configure or
  call model, Speech, transcription, sensor, or other external services.
- Reset the browser session and test definitions between participants.
- Obtain the institutionally required consent before recording. Prefer notes;
  if audio or screen recording is approved, store it outside the repository
  under the study's retention rules.

## Session outline (50-60 minutes)

1. Welcome, consent, and think-aloud practice - 5 minutes.
2. Orientation without feature instruction - 5 minutes.
3. Four authoring and verification tasks - 30 to 35 minutes.
4. Review/lifecycle questions - 5 minutes.
5. Short debrief - 5 to 10 minutes.

Say: "We are testing the Designer, not you. Please say what you expect before
you act. I may ask what you are looking for, but I will not tell you which
control to choose unless you are blocked."

## Tasks

Give tasks one at a time. The scenario wording may be adapted to the
participant's field, but keep the underlying acceptance points unchanged.

### Task 1: Create the brief and ordinary interaction

Create a new agent that helps a visitor prepare one question for a
professional. Give it a name, purpose, language, one agent-wide boundary, the
ability to notice a person's words, and speech output. Ask the participant to
explain what selecting a capability changed.

Success evidence: the stable key is deliberately confirmed; guidance is
agent-wide; capabilities do not create rules or situations; Main remains the
only situation.

### Task 2: Add one durable phase and an exception

After the visitor has prepared a question, the agent should enter a hand-off
phase. In that phase it should use local guidance and finish when the visitor
confirms. Ask why a situation is appropriate here and what would instead
belong in starting context.

Success evidence: one situation is added for a durable handling change; local
guidance stays distinct from inherited guidance; one rule moves and one rule
finishes; ordinary behavior is not confused with a rule effect.

### Task 3: Define data and an outcome

Add one invented starting value or learned value and a caller-visible outcome
field. Ask the participant to distinguish starting context, working data,
learned information, and an outcome report.

Success evidence: the chosen roles match the explanation; no real personal
data is entered; the participant understands that working data supports the
agent while an outcome report leaves it.

### Task 4: Verify and review

Create a Given / When / Expect scenario, run it, inspect the safe trace, review
the reverse explanation, and find validation help. Ask what Save, Publish, and
Activate mean, and what prompt-based safety guidance can and cannot guarantee.
Do not publish unless the session uses a disposable definition and the
facilitator has planned that lifecycle action.

Success evidence: the participant can explain pass/fail evidence without
assuming hidden reasoning; recognizes that preview is disposable; distinguishes
save, immutable publication, and activation for new instances; and does not
treat prompt guidance as enforcement.

## Moderator behavior

- Use neutral prompts: "What are you expecting?", "What would you look for?",
  and "What does that label mean to you?"
- After 60 seconds of visible blockage, offer a graded hint and record it:
  first name the relevant step, then the relevant section, then the control.
- Do not teach JSON, stable IDs, component kinds, numeric order, or graph
  terminology during the tasks.
- Note whether Advanced is opened voluntarily and why. Advanced use is not a
  success requirement.
- Stop if the participant wants to stop, appears distressed, or begins entering
  sensitive data. Remove any accidental sensitive entry immediately from the
  disposable environment and the notes.

## Observation sheet

Record one row per task outside production data stores:

| Field | Notes |
| --- | --- |
| Participant code and audience | Use a study code, never a name in repository notes |
| Task and completion | Completed, completed with hint, or not completed |
| First interpretation | What the participant expected before acting |
| Route and hesitation | Steps visited, backtracking, and pauses |
| Terminology | Labels understood or misinterpreted |
| Errors and recovery | Visible message, attempted recovery, outcome |
| Confidence | Participant rating from 1 (uncertain) to 5 (confident) |
| Quote | Optional short de-identified quote if consent permits |

Classify findings after the session: **critical** blocks completion or risks
unsafe lifecycle/data behavior; **major** requires moderator intervention;
**moderate** causes repeated hesitation or a wrong mental model; **minor** is a
localized wording or presentation issue. Record the affected step, reproducible
path, evidence, and proposed follow-up. Do not turn this protocol into product
telemetry.

## Debrief questions

1. How would you explain Main and a situation to a colleague?
2. When would you use ordinary behavior instead of an interaction rule?
3. What is the difference between agent-wide and situation guidance?
4. Which data role was least clear?
5. What did the Try evidence prove, and what did it not prove?
6. What do Publish and Activate change?
7. Which words or controls felt too technical?
8. What would you need before trusting this tool in coursework or a
   healthcare-design exercise?

## Round completion

The product team reviews critical and major findings before the next testing
round. Keep a de-identified issue log with links to ordinary repository issues;
do not commit recordings, raw consent data, credentials, database exports,
screenshots containing participant data, or free-text notes that could identify
a participant.
