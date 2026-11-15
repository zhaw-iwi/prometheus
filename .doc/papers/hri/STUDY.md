# Online coordination study: implementation handoff

Prepared **15 November 2026**, following the text-interaction study review.
Read [PAPER_BRIEF.md](PAPER_BRIEF.md) alongside this document. The brief owns the
motivation, contribution, scenario rationale, research questions, analysis, and
claim limits. This document specifies what to prepare and implement for the
online study. It is not evidence of implementation, recruitment, ethics approval,
or collected results.

## 1. Direction and scope

The owner's latest direction is **text input and text output** for the student
experiment. This resolves the brief's previously open primary interaction mode.
Microphone capture, speech synthesis, camera access, and physical robot control
are outside the default participant journey. Multimodal situations can be
represented by explicitly disclosed, controlled observations; this evaluates
coordination and response logic, not perception accuracy or embodied interaction.

Implementation direction, including the owner's roadmap decisions:

- One shared coordination implementation, with scenario-specific configuration
  and bounded executors where needed. Scenario variants are not automatically
  experimental treatment conditions or comparative baselines.
- Deliver all three executable scenarios, with the guessing game implemented
  first. The two other scenarios need additional execution logic before they can
  be enabled as executable study cases; they are required parts of the full system.
- One participant, one assigned task, one isolated agent instance; one initial
  agreement, one prescribed revision, and a bounded trial. The sample, proposed
  duration, and allocation rationale remain in the brief.
- English is confirmed for the interface, materials, questionnaires, and agent.
  A concise neutral study persona remains the working default to pilot. The
  current Valerian character humor is a deliberate design choice, not a neutral constant.
- Participation will be asynchronous or in scheduled waves. Measure the supported
  concurrency cap; 40-50 participants is not a requirement for simultaneous access.
- Run a 4-6-person pilot before freezing materials and deployment. Keep pilot
  records distinguishable from main-study data.

The owner anticipates a separate branch. Suggested name:
`experiment/hri-2027-coordination`. No branch is created by this documentation
change. When instructed to implement, check branch/worktree status and use the
existing checkout, preserving uncommitted work. Put study-specific definitions,
prompts, and client behavior on the application branch; preserve the reusable
core. Commit, publish, deploy, or recruit only under the applicable user instruction.

Before implementation, follow the [workspace bootstrap](README.md). Give a
focused mental model and milestone scope. Routine implementation choices can be
resolved within these boundaries; unresolved study choices must remain visibly
provisional rather than being described as approved methods.

## 2. Responsibilities and architecture

Keep three responsibilities distinct:

| Component | Owns | Must preserve |
| --- | --- | --- |
| Study application | Participant/session identity, assignment, procedural stages, private forms, assessments, recorder/export, operator preparation | Private answers and researcher answer keys stay outside agent prompts/history |
| Coordination agent/runtime | Interpretation, draft and accepted agreements, preparation, guarded transitions, revision, pause/resume, task authority | Existing Event, BehaviourPlan, interaction-profile, Storage, and history contracts |
| Task execution | Bounded activity behavior and, where implemented, pending questions and contextual interruption | Execution follows the accepted revision; no hidden rewriting of roles or conditions |

The procedural stage (for example, post-questionnaire) is not an agent task
state. Advancing the study UI cannot mark a task successful, accept a proposal,
or authorize execution. Conversely, reaching Reflection must not bypass the
remaining assessment or automatically classify a session as successful.

Reuse scoped HTTP/SSE application services. A new study client can live under
`src/main/resources/public/hri-study/` using the repository's existing frontend
patterns; this is a proposed location, not an existing component. Keep additional
study persistence/controllers in an application-owned package. Extract a small
shared factory/configuration seam only where the current definition needs it;
do not copy the entire Coordination implementation for each scenario.

Study variants may define available task capabilities and a consistent persona.
They must not receive a participant's hidden preferences, game secret, requested
revision card, or expected answer through their initial prompts. The participant
must communicate the relevant requirements through the interaction.

## 3. Participant journey

Implement explicit, resumable procedural stages with stable identifiers. Freeze
their time limits and exact wording after the pilot.

| Stage | Required behavior and recording |
| --- | --- |
| Entry and information | Resolve a pseudonymous session; show reviewed information/consent and the text-only setup. Do not silently request media permissions |
| Pre-interaction form | Capture concise background information; check that the participant can use the interface and understands the fictional setting |
| Task preparation | Show the assigned task card; capture initial free-form intention before a detailed checklist, then any private preferences. Keep these records out of the model context |
| Coordination | Let the participant specify the task in their own words. Record proposals, clarifications, acceptance, and the committed agreement snapshot |
| Revision and trial | Present one standardized revision at the defined point; capture the revised agreement and unchanged terms. Run only the authorized bounded trial/probe |
| Assessment | Capture understanding, usability, experience, and open comments before a researcher-provided corrective explanation |
| Completion/debrief | Persist completion and export status, provide a receipt, explain the simulation where applicable, and close the session without deleting its record |

Every relevant stage needs a path for task failure, technical failure, timeout,
and voluntary withdrawal. A participant who cannot reach agreement should still
be able to report the problem and complete the applicable assessment. Preserve
failure/withdrawal categories according to the approved data policy; do not
silently discard them or relabel them as success.

Keep practice separate from measurement. A brief neutral interface exercise may
teach sending a message and finding help, but should not teach the required task
specification or reveal assessment answers. Record researcher assistance during
the measured session.

## 4. Task materials and scenario readiness

Use the scenario definitions in [brief section 6](PAPER_BRIEF.md#6-proposed-student-study).
For each enabled scenario, prepare a versioned bundle: participant card, private
choice form, revision card, probe script, comprehension questions, and researcher
rubric. Separate mandatory requirements, participant preferences, and acceptable
negotiated alternatives. Do not provide JSON fields or scripted utterances to
participants. The main study should support ordinary language, not require users
to learn internal operation names such as REPLACE.

| Scenario | Implementation requirement before an executable study |
| --- | --- |
| A: Twenty Questions-style game | OS-06 implements a dedicated question/guess counter, private secret and explicit stopping outcomes. Pilot role fidelity, question quality and participant understanding; see IMPLEMENTATION.md and RESULTS.md for current evidence |
| B: contextual medication check-in | OS-07 implements a fictional question/response encounter with controlled scene interruption and explicit resumption. Pilot the disclosed simulation and wording; distinguish person count from identity or proof of privacy. Current contract/evidence: IMPLEMENTATION.md and RESULTS.md |
| C: negotiated hand-sign answers | OS-08 implements scoped simulated thumbs/open-palm answers, optional demonstrations, wait/resume and renewed acceptance for mapping changes. Pilot the mapping and pending-question explanation; this is not visual sign recognition. Current contract/evidence: IMPLEMENTATION.md and RESULTS.md |

For B/C, use visibly labeled simulated scene/sign events, such as a small scene
panel or symbol buttons. Emit them through the event boundary with source,
timestamp, and simulation provenance; do not mutate task state directly. A typed
claim that a sign was shown is not equivalent to a recognition observation or a
passed preparation check. Control probe order, relative timing, and accepted
revision; do not secretly improvise events for individual participants.

Include one planned change request and one bounded interruption/recovery or
contextual probe per applicable task. Do not bundle unrelated changes. Choose
their exact content during piloting and record which dimensions differ across
scenarios; raw task success differences are not modality effects.

**Readiness rule:** completing A is the first milestone, not completion of a
three-scenario executable system. B/C stay disabled for executable allocation
until their control logic and materials pass acceptance. The owner has confirmed
all three as executable deliverables. A specification-only or A-only release
would require an explicit scope change, not a hidden implementation shortcut.

## 5. Text interface and delivery reliability

Use a reduced participant view: task card, conversation, plain procedural status,
processing/error feedback, and accessible help/finish/withdrawal controls. Keep
agent catalogs, provider settings, raw storage, state graphs, and debug exports
in the operator interface. If a readable agreement panel is included, derive it
from committed fields and treat its availability as part of the studied design.
An LLM summary must not be the sole representation used for assessment.

Required text behavior:

- Distinguish local composition, pending submission, backend acceptance, response
  generation, and response rendering. The current cockpit clears and displays
  input before backend confirmation; reuse its transport, but provide explicit
  pending/failure semantics in the study view.
- Serialize ordinary turns and guard double clicks. Preserve recoverable text.
  On an uncertain request result, reconcile against durable session/event identity;
  do not blindly resend an utterance or request another generated contribution.
  Client-side disabling alone is not durable deduplication. If request identity
  needs a new contract, scope and test it explicitly; ordinary text ingress must
  not be assumed to inherit Live transcript receipt guarantees.
- Render canonical persisted behavior once across the HTTP reply, live SSE, and
  history replay. Reconnect to the same agent/session and restore the procedural
  stage without resetting the task, duplicating output, or reallocating the task.
- Keep help and withdrawal reachable during errors. Ending participation must
  prevent further study-triggered actions and handle in-flight work using existing
  runtime authority boundaries. Report uncertain outcomes instead of replaying.
- Keep text as the exposed output modality; declare or describe study-client
  capabilities truthfully. Do not promise rendered gaze, gestures, or camera
  evidence when those channels are absent. Disable irrelevant autonomous sensing
  and reception in study configuration, preserving the baseline agents.
- Use ordinary text rendering for participant/model strings and keyboard-accessible
  controls. Test the chosen supported desktop/mobile layouts and slow responses.

**Prediction timing:** START currently accepts a dialogue proposal and generates
its first contribution in the same turn. A prediction about that first action
must therefore be collected before START, or through an explicitly designed and
tested study boundary. Predictions about later probes must precede those probes.
Do not measure a revealed behavior and call the answer a prediction. Keep normal
conversation visibility consistent; do not introduce an answer key mid-assessment.

## 6. Recording, allocation, and operations

Implement the record specified in [brief section 7](PAPER_BRIEF.md#7-study-artifacts-and-recording-contract)
as a durable study dataset. The bounded runtime journal and browser timing export
are diagnostics, not a complete recorder.

Additional implementation requirements:

- Persist session identity, allocation, pilot/main designation, scenario mode,
  material versions, language/persona, model/settings, and source revision. A page
  refresh must not draw a new assignment. Keep scenario variants distinct from
  any future randomized agent treatment.
- Capture immutable initial/revised agreement snapshots at committed mutation
  boundaries with run/revision and source-event identity. Periodically polling
  mutable Storage can miss intermediate agreements. Preserve the snapshot before
  any later participant review causes a correction.
- Link server-generated events and client-rendered messages without equating
  generation, rendering, reading, or comprehension. Distinguish server and browser
  timing; record disconnects, delays, missing display evidence, and incomplete data.
- Store private forms and assessment answers separately from agent history and
  inference context. Link by pseudonymous ID for authorized analysis. Freeze and
  version question IDs, answer options, missing/not-applicable values, and rubrics.
- Provide an operator export containing machine-readable session/event records
  (for example JSON/JSONL), accepted agreement versions, and analysis-ready tabular
  assessments. Include a manifest and schema version, explicit completeness checks,
  and record counts; validate it on synthetic sessions. Never manufacture missing
  data or substitute the latest agreement for a missing earlier snapshot.
- Scope access per participant and keep administration/export separate. Do not
  ship admin credentials to browsers or put bearer codes in exported research data.
  Server authorization must enforce isolation; hiding UI controls is insufficient.
- Apply the brief's consent, retention, withdrawal, and access arrangements. Keep
  identifiable consent/recruitment records separate. Only cleared materials and
  synthetic fixtures belong in Git. Collection must not begin with invented ethics
  approval or unresolved participant-information placeholders.

Provide operator procedures for provisioning, session recovery, export, failure
triage, and closure. Do not require students to create agents or choose from the
developer catalog. A reused participant link resumes its session; a deliberate
replacement attempt receives an explicit linked attempt identity, not an erased
history.

Measure capacity using the actual text study journey, realistic turn pacing,
recording, concurrent inference, and reconnects. Existing eight-client browser
coverage and the six-client sensor-heavy capacity probe are not proof that 50
students can interact concurrently. Record deployment-specific limits, provider
rate limits/cost, latency, database load, and failures, then choose a supported
concurrency cap or waves. Freeze the main-study configuration; record deviations.

## 7. Pre/post instruments and understanding assessments

Keep the pre-form short: prior chatbot use, robot interaction experience, prior
PROMETHEUS exposure, and comfort with the study language. Collect only justified
demographic/technical information. Do not ask participants to disclose real health
or medication details. Expectations measured before use and experience measured
after use are different constructs; their difference is not a causal improvement
estimate.

For the post-form, prepare:

| Instrument | Proposed content | Implementation/analysis boundary |
| --- | --- | --- |
| UMUX-LITE | The two published usability items, with their original seven-point agreement response format | Preserve original wording/scoring and cite the source. Record missing responses explicitly; do not call a modified item or translation validated, or silently relabel a score as SUS |
| Exploratory coordination ratings | Clarity of agreement; ability to correct misunderstanding; control over changes; effort required | Four separate, task-specific items. Pilot wording, use consistent anchored scales, and report individually; no invented validated composite |
| Open feedback | What, if anything, did the agent misunderstand? Describe a moment when its behavior differed from expectations | Optional free text, retained with the session and available for independent coding |

UMUX-LITE source: James R. Lewis, Brian S. Utesch, and Deborah E. Maher (2013),
[UMUX-LITE: When There's No Time for the SUS](https://doi.org/10.1145/2470654.2481287).
It measures perceived usability; it does not establish instruction fidelity.
The [reference guide](REFERENCES.md#h-evaluation-of-common-ground-and-human-ai-collaboration)
connects the understanding assessments to the earlier grounding literature.

Prepare a few scenario-specific understanding questions, for example:

- Who chooses the secret, and who asks the questions?
- What will happen when the agreed limit is reached?
- After your requested change, which earlier requirements still apply?
- What do you expect the agent to do when you pause?

Choose free responses or fixed options with an explicit uncertain/don't-know
option during piloting. Store answers before feedback and score them against the
agreement actually in force, while separately checking whether that agreement
faithfully represents the participant's intentions. Distinguish recall of visible
terms from prediction/application of those terms. Apply the same assessment
timing and access to conversation/plan information across comparable sessions.

The primary outcome, independent coding, failure accounting, and analysis limits
remain in [brief sections 6.6-6.7](PAPER_BRIEF.md#66-outcomes-and-independent-assessment).
Implement exports so two researchers can assess fidelity without relying on the
agent's own judgment. Do not replace unsuccessful sessions with successful retries
in the denominator. Game-solving quality and execution quality remain separate
from agreement quality.

## 8. Implementation sequence and acceptance

The detailed sequence and acceptance gates are authoritative in
[PLAN.md](PLAN.md). The earlier five workstreams below now map to its milestones;
maintain implementation status/evidence there rather than in two parallel plans.
Complete focused milestones end to end with the smallest meaningful test set and
required documentation updates. Stop at each requested handoff for review; this
document does not authorize publishing or recruitment.

| Milestone | Deliverable | Acceptance evidence |
| --- | --- | --- |
| S1: study definition and materials (OS-01) | Branch/package boundaries, versioned scenario/material manifest, shared study persona/capabilities and provisional forms | Prompt/profile contracts; explicit readiness flags |
| S2: complete text journey (OS-02/03/05/09) | Provisioning, durable turns, UI, procedural stages, assessments and failure/withdrawal paths | Scoped DB/browser journeys and recovery checks |
| S3: recording and analysis handoff (OS-04/09/10) | Durable snapshots/answers/events, exports, operator procedures and coding materials | Complete records across reload/export; private-data separation; denominator rehearsal |
| S4: task readiness (OS-06/07/08) | A, B and C execution with controlled simulation/probes | Scenario-specific unit, DB and browser acceptance |
| S5: operational acceptance (OS-10/11/12) | Integrated regression, provider trials, measured capacity and pilot handoff | Retained evidence and failures; all three scenarios; explicit external readiness gates |

Build S2 and S3 together enough that the first pilot has complete recording.
Do not collect pilot sessions and discover afterward that only the last mutable
agreement was retained. Real-provider verification is distinct from mocked tests;
report exactly what was run. Human piloting and institutional review require the
appropriate arrangements; they cannot be replaced by synthetic participants.

## 9. Code and test starting points

Use the broader [repository map](PAPER_BRIEF.md#10-repository-reading-map-and-evidence)
for orientation. The most relevant implementation patterns for this work are:

- [CoordinationAgent](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/CoordinationAgent.java)
  and its neighboring update, policy, cue, and preparation classes: preserve the
  common graph and operation guards across study variants.
- [CoordinationAgreement](../../../src/main/java/ch/zhaw/prometheus/model/coordination/CoordinationAgreement.java)
  and [CoordinationMemory](../../../src/main/java/ch/zhaw/prometheus/model/coordination/CoordinationMemory.java):
  bounded terms, protected revisions, snapshot identity, and journal limitations.
- [ScopedDemoController](../../../src/main/java/ch/zhaw/prometheus/controllers/ScopedDemoController.java),
  [ScopedDemoService](../../../src/main/java/ch/zhaw/prometheus/application/ScopedDemoService.java),
  and [Valerian's client](../../../src/main/resources/public/valerian/script.js):
  scoped events/history/storage, canonical responses, SSE, and reconnect behavior.
- [CoordinationAgentIntegrationTest](../../../src/test/java/ch/zhaw/prometheus/application/CoordinationAgentIntegrationTest.java),
  [CoordinationRevisionUnitTest](../../../src/test/java/ch/zhaw/prometheus/agentdefs/core/CoordinationRevisionUnitTest.java),
  and [CoordinationAcceptanceUnitTest](../../../src/test/java/ch/zhaw/prometheus/agentdefs/core/CoordinationAcceptanceUnitTest.java):
  authoritative acceptance, reload, revision, and bounded task outcomes.
- [Classroom browser test](../../../tests/playwright/valerian-classroom-smoke.spec.mjs)
  and [ClassroomCapacityIntegrationTest](../../../src/test/java/ch/zhaw/prometheus/controllers/ClassroomCapacityIntegrationTest.java):
  scoped isolation/reconnect patterns and measured capacity, with their existing
  fixture/workload limits. Adapt the workload for this study rather than declaring
  these existing checks sufficient.

## 10. Handoff and open decisions

Before main-study collection, resolve and version: readiness of all three executable
scenarios; task cards/revisions/probes; persona; agreement-panel presentation;
assessment timing/items/scoring; allocation and session limits; failure handling;
institutional review/consent/withdrawal arrangements; hosting and concurrency.
Most are protocol choices, not reasons to block independent implementation of
the scoped UI, recorder, and synthetic verification.

The system is ready for the agreed study scope when every enabled scenario has
passed the full text journey, isolation and recovery checks, complete export and
independent coding rehearsal, and the documented operational workload. Report
unverified provider, participant, or deployment behavior honestly. Update this
document with implementation status and evidence links, and reconcile protocol
changes with the brief. Stop for owner review without committing unless instructed.

## Implemented assessment/procedure draft

`hri-text-v2-draft` freezes the software study materials. See
[INSTRUMENTS.md](INSTRUMENTS.md) for exact instrument source/scoring, open-reference
understanding, adaptive one-revision timing and coding worksheet. Balanced task
allocation and separate participant/attempt denominators are implemented; failed,
withdrawn and replacement attempts remain in exports. This software freeze does
not authorize human collection or replace the required pilot.
