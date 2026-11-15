> **15 November implementation clarification:** the participant study uses typed
> dialogue **and real camera sensing**, with a participant-arranged consenting adult
> helper for the contextual task. Earlier simulation examples below remain useful
> for deterministic software validation, not a substitute for the participant condition.
> See [STUDY.md](STUDY.md) and [CAMERA.md](CAMERA.md). Recognition quality and helper
> feasibility must be evaluated in the pilot; no causal comparison is added.

# HRI 2027 paper brief: PROMETHEUS and human-robot coordination

Prepared: **15 November 2026**. Target: **HRI 2027 Late-Breaking Reports (LBR)**.
This document preserves the research discussion so a new Codex session can work
without access to the conversation. It is a planning document, not a manuscript
or evidence that the proposed study has taken place.

Repository inspected on clean `main`, commit
`17244cb1f55d7d90199f839fa53f623203229655`, with one worktree. Recheck these facts
at the start of the next session. Scope of this documentation change: create the
paper handoff; no runtime changes, recruitment, data collection, or submission.

## 1. Working direction and decision status

The intended contribution is a **framework approach that makes coordination
design principles executable**, demonstrated and examined through a demanding
Coordination Agent. PROMETHEUS may be described as a **stateful interaction
harness for generative multimodal agents**, with a focus on social robotics.

Working title, not final:

> PROMETHEUS: A Stateful Interaction Harness for Generative Human-Robot Coordination

Alternative if the design-to-implementation connection becomes the stronger story:

> From Design Principles to Executable Coordination: A Framework Approach to
> Generative Human-Robot Interaction

Directions established with the owner:

- Retain useful robotics and coordination **before, during, and after execution**
  as the outermost motivation. Multimodality develops that motivation.
- Retain the poster's principles as the design rationale; connect them to
  framework primitives and implemented coordination patterns.
- Explain flexible interaction enabled by generative models together with the
  explicit control needed to constrain their effects.
- Use the availability of **40-50 students** for online interaction studies.
- Develop three healthcare-inspired scenarios: a guessing game, a contextual
  medication check-in, and negotiated hand signs for yes/no/wait.
- Record participants' specifications, the interactions, and the accepted task
  specifications at the transition to execution.
- Treat a robot execution layer inspired by Michal Johnson's work as a possible
  downstream integration, not an existing PROMETHEUS capability.

Recommended protocol choices, **not yet frozen or approved as a study protocol**:

- One system configuration; balanced random assignment to one of three scenarios.
- Target 48 students, approximately 16 per scenario; 20-25 minutes per session.
- Private intention capture, free negotiation, initial acceptance, one requested
  revision, understanding checks, and a short bounded execution probe.
- Agreement fidelity as the primary outcome; descriptive and qualitative analysis.
- Synthetic verification as supporting engineering evidence, not the main study.

Follow-up: the owner selected text-to-text as the primary online interaction
mode, confirmed all three executable scenarios with the guessing game implemented
first, English throughout, and asynchronous participation or scheduled waves.
[STUDY.md](STUDY.md) records the requirements; [PLAN.md](PLAN.md) defines the
implementation milestones and testable outcomes. Read both alongside this brief
before study-system work.

Critical open matters: ethics/recruitment arrangements, participant perspective,
camera-based observation presentation with typed dialogue, feasible implementation of the two new coordination patterns,
the operational rubric, recording infrastructure, and concurrent capacity.
Section 12 orders these decisions and the next work.

## 2. Venue and publication boundaries

The [official LBR call](https://humanrobotinteraction.org/2027/late-breaking-reports/)
was checked on 15 November 2026. Recheck before submission.

- Deadline: **8 December 2026, 23:59 Anywhere on Earth**. Aim to finish on 7 December.
- **2-4 pages excluding references**, no appendices; ACM `sigconf`, double column,
  US letter. Four pages is the working target.
- Double-blind review; optional video at most two minutes.
- Initial author list is final. Reviewers from the author team owe up to three
  LBR reviews; review deadline 4 January 2027.
- Notification 13 January; camera-ready 25 January 2027.
- Accepted work appears in the companion proceedings; the call describes this
  as non-archival and without an APC. In-person presentation uses an A0 portrait
  poster; the existing booth poster is landscape and branded.
- Anonymize text, interfaces, video, artifacts, metadata, institutional/project
  identifiers where identifying, and repository links according to the call.

HRI is the primary community: interaction design, systems, and coordination.
The full-paper Design/Systems descriptions are useful positioning guidance, not
separate LBR submission tracks. A future robotics paper would require stronger
embodied execution evidence; an agentic-AI paper could emphasize protocols,
architectural comparisons, and formal or benchmark evidence. The current LBR
should not attempt all these contributions.

## 3. Motivation and introductory argument

### 3.1 Outermost motivation

Anchor from the [existing poster](../../posters/human-robot-coordination/POSTER.md):

> Many everyday tasks need coordination before, during and after execution.

Usefulness does not depend on task complexity alone. A technically simple
conversation can require demanding coordination when the purpose changes,
someone interrupts, or another person becomes visible. The relevant class of
tasks requires participants to establish and maintain goals, roles, conditions,
and expectations throughout the activity.

The introduction should progress through:

1. Robot capabilities become useful when connected to people's goals and situations.
2. A class of activities requires ongoing human-robot coordination.
3. Coordination involves language, nonverbal behavior, and changing social context.
4. Generative flexibility must be combined with inspectable state and control.
5. PROMETHEUS and the Coordination Agent operationalize this combination; the
   proposed study examines resulting agreements and interaction breakdowns.

### 3.2 Candidate opening text

> Making robot capabilities useful in everyday situations requires connecting
> them to people's goals and circumstances. A significant class of human-robot
> activities involves ongoing coordination: people and robots must establish what
> they will do together, coordinate their contributions as the activity unfolds,
> and assess the outcome to guide subsequent action. Coordination therefore
> extends before, during, and after execution.
>
> Such coordination takes place through language, nonverbal behavior, and
> observations of the surrounding situation. Goals may be clarified through
> dialogue and gestures, execution conditions may change as other people enter
> the scene, and the robot's behavior must help its human partner understand what
> it is doing. Supporting these interactions requires flexibility while
> maintaining the commitments that govern the shared activity.
>
> Generative models support flexible interpretation and expression. We investigate
> how explicit interaction state and control can constrain how generated
> interpretations and responses change task commitments and behavior.

These are draft formulations, not established empirical findings. Add the
appropriate literature support and narrow claims to the eventual evaluation.

### 3.3 Multimodality and mutual understanding

Distinguish three roles for multimodality:

| Role | Example | Required interaction semantics |
| --- | --- | --- |
| Establishing task meaning | An utterance combined with pointing, gaze, or a demonstrated sign | Bind cues to references; retain uncertainty and seek clarification |
| Determining whether execution is appropriate | Presence changes during a conversation | Relate observations to agreed conditions and interruption policies |
| Performing and communicating the activity | Speech with gaze, expression, and gesture | Produce behavior consistent with the activity and understandable to its partner |

The gaze papers support action anticipation, context dependence, and mutual
legibility. They do not demonstrate that gaze alone establishes task agreement.
Distinguish **observed cue**, **interpreted meaning**, and **permitted consequence**.
Agreement need not mean verbal reconfirmation for every action: an accepted
bounded activity may authorize subsequent action on defined nonverbal cues.

A candidate refinement of poster principle 5 is:

> Support mutual understanding through multimodal interaction: interpret human
> cues in context, make the agent's intended behavior legible, and clarify
> uncertainty when it affects the agreed activity.

Gaze tracking and general pointing/reference resolution are motivation and future
extensions here, not implemented capabilities to claim in the study. Multimodal
interaction can combine specialized perception, symbolic observations, language
models, and separate renderers; it does not require one foundation model to
process every modality directly.

### 3.4 Literature foundation and the claim it supports

Use the [reference map](REFERENCES.md#how-the-literature-supports-the-paper) to
connect each part of the argument to evidence. The short format still needs a
clear foundation and an explicit relationship to prior systems.

| Argument in the paper | Literature to consult | Consequence for our framing |
| --- | --- | --- |
| Tasks require continuing coordination of goals, roles, and action | Clark and Brennan; Klein et al.; Hoffman and Breazeal (reference guide G) | Grounding, joint activity, and shared plans are established ideas; identify our particular operationalization |
| Multimodal cues support interpretation and mutual legibility | Belardinelli; Duarte et al.; the additional gaze and expressive-behavior sources (A2-A3, F) | Explain input, context, and output separately; cues do not constitute agreement by themselves |
| Design principles and executable frameworks are legitimate contribution forms | Cila et al.; Robot Behavior Toolkit; IrisTK; RISE and other precedents (B) | Show principles, mechanisms, and evidence together; examples establish precedents, not how common a contribution type is |
| Generative interaction needs an explicit account of state and control | PROMISE; dialogue-management survey; FurChat; adaptive HRI framework (A1, C) | Distinguish inherited orchestration and existing multimodal/interruptible interaction from the proposed contribution |
| Accepted plans require separate execution grounding | Johnson thesis/manuscript; SayCan (A4-A5, C) | Describe a possible handoff and its evidence boundary; the current study does not demonstrate general robotic execution |
| Agreement fidelity differs from perceived understanding and performance | Chai et al.; common-ground benchmark by Poelitz et al. (G4, H) | Measure the accepted artifact, participant understanding, and behavior separately |

The health sources support fictional scenario design, not clinical effectiveness.
Industry harness descriptions explain terminology; the scholarly argument should
rest primarily on the research above. Keep published papers, preprints, the BSc
thesis, and the unverified ERF manuscript explicitly distinguished.

## 4. Contribution and relationship to earlier work

### 4.1 Three contribution layers

| Layer | Contribution | Claim boundary |
| --- | --- | --- |
| Design principles | Desired interaction qualities and design rationale | Informed by iterative implementation/scenario testing; not seven empirically validated laws |
| PROMETHEUS primitives | Events, explicit states/transitions, guards/actions, policies, storage/history, profiles, structured behavior | General engineering constructs; many have precedents in HRI frameworks |
| Coordination patterns | Proposal/acceptance, preparation, persistent commitments, bounded execution, pause, uncertainty, revision | Implemented compositions in the Coordination Agent; not all are independently composable public framework primitives |

Preserve the poster's seven guidelines: communicate capabilities; make workflow
navigable; make agreement explicit; keep commitments in state; ground meaning
together; support recovery; reflect and revise. Map each to mechanisms and
evidence rather than treating implementation alone as validation of the guideline.

Candidate contribution statement:

> Building on stateful prompt orchestration, we present PROMETHEUS as an
> interaction harness for generative multimodal agents. A Coordination Agent
> demonstrates how explicit interaction state connects human instructions and
> nonverbal observations to persistent task commitments and controlled behavior.
> A preliminary user study examines whether people can establish and revise
> faithful task specifications and understand the behavior they authorize.

Use future tense while the study is planned. The final abstract must describe
only methods actually performed and findings supported by collected evidence.

### 4.2 Harness positioning and PROMISE lineage

Operational definition for this paper:

> A stateful interaction harness connects generative models to multimodal
> observations, persistent interaction state, explicit control rules, and
> structured behavior.

Existing harnesses already include memory, execution controls, and human approval.
Human involvement by itself is not novelty. The proposed emphasis is continuing
human participation in defining, interpreting, performing, and revising an
activity, including nonverbal contributions and social-context changes.

PROMISE already provides state-specific prompts, transition decisions/actions,
storage, reusable states, nested conversations, and history-based continuation.
It already motivates flexibility together with control. Cite it as inherited
work, and identify PROMETHEUS's extension into multimodal event processing,
explicit commitments, and observable behavior delivery. State machines and
multimodal authoring are not new in HRI; compare the specific composition to
related systems. The novelty assessment remains to be completed.

The [related-system sources](REFERENCES.md#c-generative-interaction-harness-terminology-and-execution)
include LLM-based expressive dialogue and interruptible adaptive HRI. Inspect
those systems for their actual representation of agreements, revision authority,
contextual interruption, and execution evidence. Do not infer missing features
from an abstract. Likewise, the joint-activity literature already treats people
as continuing participants; human involvement alone cannot distinguish the work.

### 4.3 What the preliminary paper can establish

- Feasibility of expressing and executing the covered coordination patterns.
- Fidelity and comprehensibility of agreements produced with students in the
  selected scenarios; interaction effort and observed breakdowns.
- Lessons for designing the connection between generation, state, observations,
  and behavior, with reproducible definitions and scenario materials.

It cannot yet establish broad framework validity, authoring efficiency for other
designers, therapeutic efficacy, medication-adherence improvement, general
patient usability, physical robot success, or superiority over another system.
Students interacting with one application do not constitute a developer study.
One revision does not establish long-term adaptation or learning from reflection.

## 5. Current implementation mental model

The authoritative orientation is [CONTEXT.MD](../../../.agents/CONTEXT.MD),
[README](../../../README.md), and the current section of
[PROJECT.md](../../../PROJECT.md). The following is a dated research-facing summary.

- PROMETHEUS is a Java 21/Spring event-driven framework. States and transitions
  own task control; policies may call models, and actions update persisted state.
- `BehaviourPlan` is a structured output payload (speech, nonverbal, motion,
  display), not a general dependent task plan. A client renders supported channels.
- `AgentInteractionProfile` declares observation and output capabilities.
- `CoordinationAgent` routes nine phases: CONVERSATION, COORDINATION, PREPARATION,
  READY, EXECUTION, PAUSED, FINISHING, UNCERTAIN, REFLECTION. The current
  coordination lifecycle is flat, even though the framework supports nested states.
- `CoordinationAgreement` contains `goal`, `humanRole`, `agentRole`, `success`,
  unresolved `questions`, preparation `checks`, `dialogueLimit`, and optional
  sensor `reactions`. Draft and accepted agreements are stored separately.
- Preparation checks need fresh observations after the proposal. Preparation
  evidence does not authorize execution. An ON_CUE start waits with zero actions
  for a fresh execution cue; dialogue START may emit its first contribution.
- Content revisions preserve protected terms; changes to those terms require
  an explicit replacement. Revision suspends prior execution; acceptance and
  run/revision ownership govern subsequent behavior.
- Dialogue budgets count agent contributions, not necessarily game questions or
  rounds. Ordinary support/questions need not consume a contribution. Define the
  study's game limit in terms the implementation actually enforces.
- Sensor `TaskSpec` supports bounded independent ACT/WAIT/COMPLETE rules. It is
  not a general workflow or an arbitrary combination of temporal/AND conditions.
- Final speech delivery has separate association/playback evidence. Uncertainty
  does not authorize automatic replay. User-reported success, generated output,
  observed playback, and physical execution are different evidence types.
- Return-to-conversation preserves history; restart begins a new run/budget.
  Reflection records evidence and a user's account; it is not automatic learning.
- The production regulation layer is no-op; broader social motivation/arbitration
  is unfinished. Do not attribute the proposed contextual interruption pattern to
  an already implemented general regulation system.

### Readiness of the proposed scenarios

| Requirement | Current footing | Work or verification still needed |
| --- | --- | --- |
| Negotiate bounded conversational roles | Agreement fields and dialogue execution exist; separate guessing definitions exist | Freeze neutral study prompts, exact budget/outcome semantics, and test role fidelity |
| Presence-conditioned medication dialogue | Presence/count events, state control, and independent sensor rules exist | Compose context monitoring with ongoing dialogue and output interruption; define priorities and encounter semantics |
| Thumbs up/down and open palm | Camera mapping currently emits rock/scissor/paper; open palm maps to paper | Add thumb labels to the relevant perception/client contracts and tests |
| Gestures as answers to dialogue questions | Observations/preparation exist; sensor WAIT suspends reactions | Bind one accepted fresh response to one pending question; pause/resume and stale/held-signal handling |
| Full research trace | Persisted state/events and diagnostics exist | Durable study export with immutable agreement snapshots; bounded journals are insufficient |
| 40-50 remote users | Scoped instances and clients exist | Pilot devices, access isolation, capacity, failure recovery, scheduling, and data handling |
| General physical robot executor | Future integration concept | Typed skills, plan/parameter validation, cancellation/progress, and independent outcome evidence |

The two substantive candidate reusable additions are **contextual interruptions
of dialogue** and **question-scoped multimodal answers**. They are not prompt-only
changes and are not implemented by writing this brief.

## 6. Proposed student study

### 6.1 Research question and design

Main empirical question:

> How successfully can people establish and revise task agreements with a
> generative multimodal agent, and how faithfully do the resulting specifications
> represent their intentions and expectations?

Secondary questions: Do participants understand what behavior is authorized?
Which clarification, revision, and grounding breakdowns occur? What interaction
effort is required? Which abstractions would address recurring failures?

Recommended design: one configuration, three scenario assignments, 40-50 students
(target 48), balanced random assignment, one scenario per participant. These are
complementary diagnostic cases, not three controlled treatments. They differ in
demands; raw success differences cannot isolate the effect of a modality.

Use fictional healthcare situations and one consistent participant perspective.
The recommended default is a person configuring support for themselves in a
simulated care setting. Caregiver configuration for someone else introduces an
additional authority/consent problem and should not be mixed in silently.

### 6.2 Scenario A: cognitive activation guessing game

Use a **Twenty Questions-style yes/no guessing game**. It is not Simon Says.
Calling it a cognitive activation activity describes its intended setting, not
a demonstrated therapeutic effect.

Candidate brief, to refine and pilot: arrange a short activity in which the
participant privately chooses an everyday object and the agent tries to identify
it through yes/no questions. The participant negotiates roles and preferences.
Do not hand them the JSON schema or prescribe their exact utterances.

Researcher rubric should cover:

- Who chooses and knows the secret; who asks; who answers; who guesses.
- Permitted answer types (including whether unknown/sometimes is allowed).
- Question/guess limits and whether a guess spends the same budget.
- Category/topic, pace, success confirmation, and voluntary stopping.
- Preservation of these terms through an agreed content/style revision.

Record the participant's secret privately for analysis, never pass it to the
agent. Separate coordination fidelity from the quality of the guessing strategy.
Existing `model/guessing` code and healthcare guessing definitions are relevant
patterns, not evidence that Coordination already incorporates that whole engine.
The standalone `RoleClarificationGuessingGame` deliberately exaggerates confusion
for a humorous demo: do not reuse that behavior as neutral study behavior.

### 6.3 Scenario B: contextual medication check-in

Candidate brief: agree a short, fictional medication-routine check-in which is
initiated on the designated participant's appearance, adapts to their answer,
ends on disappearance, and offers postponement if another person becomes visible.
Negotiate the observable interpretation of these requirements.

Intended policy, requiring implementation and pilot validation:

1. Establish a known simulated encounter and check whether it is an appropriate
   moment for the agreed conversation before disclosing sensitive content.
2. Ask about the fictional routine. Acknowledge reported completion or offer a
   brief agreed form of support after understanding a reported difficulty.
3. On a valid absence observation, end the encounter as interrupted, not as
   successful task completion; define what a later return permits.
4. On the accepted multiple-visible-person condition, suspend sensitive output
   and use a neutral offer to postpone. Define resumption explicitly.

The original idea included a persuasion impulse after non-intake. Prefer bounded,
nonjudgmental, previously agreed support with a right to decline. A negative answer
is not sufficient grounds for an unconditional instruction to take medication.
Use fictional facts and reviewed content; do not gather personal medication
histories or give dose/treatment advice in this coordination study.

Counts do not identify patients or prove privacy. The existing arrival event
means empty-to-occupied, not an additional person entering an occupied view.
An explicitly accepted threshold such as at least two visible people is a
different condition and must not be silently substituted. Missing/stale camera
data is not a confirmed absence. Controlled, disclosed observation sequences may
be preferable to requiring additional people in students' rooms; that validates
interaction logic, not detector accuracy.

### 6.4 Scenario C: negotiated gesture answers

Candidate brief: configure a short check-in in which the participant uses thumbs
up for yes, thumbs down for no, and an open palm for wait. The meanings and their
scope are established through coordination, with recognition demonstrations.

The accepted specification must make clear:

- Which signal means which answer/control, and in which activity it applies.
- Which pending question a gesture answers; one response is consumed at most once.
- Preparation demonstrations never count as answers or blanket acceptance.
- Holding a sign must not answer several successive questions. A later question
  requires a fresh intentional response, not merely another frame of a held sign.
- Wait preserves the unanswered question and does not mean no or completion.
- The resume mechanism, ambiguous recognition behavior, and treatment of
  conflicting verbal/gestural answers are explicitly defined.
- Task-answer gestures do not inadvertently accept changed task terms.

The client currently maps Closed_Fist/Open_Palm/Victory to rock/paper/scissor.
Thumb recognition and pending-question semantics are gaps. A fallback using
existing signs or supplied events changes the study condition and must be
disclosed; do not describe it as an evaluated thumb-recognition interaction.

### 6.5 Common session procedure

Pilot a few participants outside the main analysis sample. Then freeze tasks,
prompts, implementation/model configuration, coding rubric, and recording.
Tentative main-session duration: 20-25 minutes, adjusted after the pilot.

1. Consent, fictional scenario, short device/access check, and relevant background
   (e.g. prior chatbot/robot experience). Avoid teaching the agreement schema.
2. Private capture of the assigned essential requirements and one or two chosen
   preferences. The agent learns them only through the interaction. Distinguish
   mandated requirements from optional preferences in the researcher rubric.
3. Free negotiation in the participant's own words, with a fixed pilot-informed
   time limit and the option to report failure rather than accept a bad proposal.
4. Automatically archive the initial accepted contract and state at acceptance.
   START can immediately produce a dialogue contribution: snapshot at the commit
   boundary rather than relying on a later screenshot or mutable state query.
5. Give one private, standardized revision instruction, phrased by the participant.
   Recommended default: change content/style while retaining other commitments.
   Match its intended difficulty across scenarios; do not add an unplanned
   factorial experiment with multiple revision types.
6. Capture the revised acceptance. Before showing the stored specification, ask
   brief behavior predictions tied to the scenario (roles, trigger, bound,
   next action, or pause behavior). This is an independent understanding measure.
7. Perform a short bounded execution probe with known inputs. Store the agreement
   revision in force, observed inputs, and actual output/delivery evidence.
8. Show a readable agreement and ask what differs from the participant's intention.
   Collect brief clarity/effort/control ratings and an open comment. Keep this
   retrospective review separate from earlier predictions and pre-review fidelity.

If an initial agreement is never reached, retain that outcome and the attempted
interaction within the consent policy. Do not force acceptance or quietly replace
the case. Define a consistent route for incomplete sessions in the protocol.

Standardize typed versus spoken interaction unless modality variation is itself
an explicit study question. Text plus genuine gesture observations is multimodal;
speech adds recognition/delivery confounds which need measurement. No decision
on the study input mode has yet been made. Record any researcher assistance or
Wizard-of-Oz action and distinguish it from autonomous system behavior.

### 6.6 Outcomes and independent assessment

Recommended primary outcome: proportion of assigned, consented main-study
participants who reach a **faithful final accepted agreement** within the defined
session. Freeze the exact endpoint and rubric before data collection.

| Measure | Evidence and operationalization |
| --- | --- |
| Agreement fidelity | Essential requirements and intended preferences compared with the accepted specification, including explicitly negotiated changes |
| Revision fidelity | Requested change incorporated; unaffected commitments preserved; correct acceptance boundary |
| Understanding | Participant predictions compared with the contract actually in force |
| Behavioral consistency | Bounded execution follows the accepted terms and responds correctly to the probe |
| Effort | Time, turns, clarifications, repairs, abandonment, and exploratory subjective ratings |
| Breakdowns | Role confusion, omitted constraints, silent substitution, false acceptance, ambiguous cues, stale evidence, misleading output, or technical failure |

The distinction between acceptance and understanding is informed by Chai et al.
(2014); the common-ground benchmark of Poelitz et al. (2026) offers a related
methodological lead for private information and repair. See the
[coordination sources](REFERENCES.md#g-common-ground-joint-activity-and-human-robot-coordination)
and [evaluation sources](REFERENCES.md#h-evaluation-of-common-ground-and-human-ai-collaboration).
These motivate our measures; they do not validate the custom rubric or make this
study a replication. Agreement fidelity is a narrower construct than common
ground, which must not be claimed solely from the stored specification.

Suggested fidelity coding per requirement: satisfied, omitted, contradicted,
unresolved, or explicitly replaced by an accepted alternative. Define examples
and adjudication rules during the pilot. A supported alternative may be faithfully
agreed while failing to fulfill the original task; report these separately.

Two researchers should independently code agreement fidelity against the brief,
private preferences, transcript, and negotiated changes. Agreement with the
agent's own summary is not independent ground truth. Assess initial and revised
contracts separately, retain disagreements, and report agreement before resolving
them. If resources require partial double-coding, predefine the sample and method.
An LLM judge may assist exploration but cannot be the sole fidelity criterion.

Code the process for user/agent clarification, capability negotiation, correction,
acceptance, revision, and help seeking. Use anonymized traces to explain recurrent
problems and connect them to framework mechanisms. Codebooks and custom ratings
must not be presented as validated instruments without supporting validation.

Reaching EXECUTION, valid JSON, participant acceptance, task success, perceived
control, and instruction fidelity are distinct measures. Particularly informative
cases include an accepted but incorrect agreement, a correct agreement the user
misunderstands, and correct internal state with misleading communication.

### 6.7 Analysis and claim limits

- Report recruitment/allocation/completion/failure counts with explicit
  denominators. Separate no-agreement, semantic, recognition, delivery, and
  infrastructure failures. Handle withdrawn data according to consent.
- Report proportions with uncertainty intervals, time/turn distributions, and
  descriptive results per scenario. Do not treat N=48 as a power justification
  for arbitrary hypothesis tests or small task-specific effects.
- The participant is the unit of analysis. Turns, questions, snapshots, and two
  agreement versions are nested observations, not additional participants.
- Initial/revised comparisons describe responses to a particular challenge;
  they do not establish the causal benefit of the framework.
- One configuration cannot show superiority over another approach. Student
  role-play supports preliminary interaction findings, not patient/clinical
  generalization, therapeutic efficacy, or professional designer productivity.
- Bounded online tasks do not establish long-horizon physical robot performance.
  Consistent output channels do not by themselves establish human action legibility.

### 6.8 Ethics, data, and practical delivery

Confirm the institutional review/approval or applicable exemption route before
recruitment/data collection. Student participation must be voluntary, separate
from grading, and have an equivalent alternative if course credit is involved.
Consider recruitment/consent arrangements that reduce instructor pressure.
Use fictional health circumstances and collect only data needed for the study.
Define retention, withdrawal, access, pseudonymization, and any release procedure.
Raw microphone/camera recordings require a justified analysis purpose and consent;
derived observations alone do not establish actual gesture/recognition accuracy.

HRI requires explicit reporting of sampling, environment, data collection and
analysis, consent, ethics status, autonomy, and any Wizard-of-Oz setup. See the
[human participant requirements](https://humanrobotinteraction.org/2027/studies-with-human-participants/).
Keep a record of generative-AI assistance in research design, coding, data or
analysis so the methods disclosure follows the current venue policy accurately.

Schedule in waves after capacity checks; 50 available students do not imply 50
simultaneous supported sessions. Use scoped participant instances and avoid
shared mutable task state. Freeze deployment/configuration for the main study;
record and explain any change rather than pooling versions silently.

## 7. Study artifacts and recording contract

Prepare these before the main study:

- Participant information/consent and a consistent fictional role description.
- Three task cards, private preference forms, revision cards, execution probes,
  and an independent requirement/rating rubric.
- A neutral onboarding flow, technical-failure route, and post-task questionnaire.
- A durable study recorder/export and an analysis script or documented procedure.

Recommended per-session record:

| Record | Purpose |
| --- | --- |
| Pseudonymous participant/session ID, assigned task, allocation record | Traceability and denominators without names in analysis data |
| Code revision, prompt/task-card versions, model/settings, capabilities, language, client/browser | Reproducibility and attribution of failures |
| Private requirements/preferences (and secret for task A), requested revision | Ground truth outside model context |
| User inputs and agent behavior actually presented, with timing/delivery status | Interaction analysis; distinguish generated from delivered output |
| Relevant observation events with timestamps, source, freshness/confidence, actual versus supplied provenance | Grounding and cue analysis |
| Immutable initial/revised accepted agreements, run/revision IDs, acceptance event, preparation evidence | Correct specification at the handoff, not a later mutable draft |
| State/control transitions, pending-question/context events where implemented, failures | Explain behavior and interruption handling |
| Participant predictions, review corrections, ratings, comments, researcher interventions | Independent understanding and experience evidence |

`CoordinationMemory` currently retains a bounded journal (at most 12 entries and
6,000 bytes) and the last eight outcomes. Timing exports are also bounded and
are not the full study transcript. Export reliably during the session; do not
assume runtime persistence alone provides a complete research dataset. Never
commit participant data, credentials, raw provider traces, or consent forms with
identities to this repository. Store only appropriately cleared study materials
and anonymized artifacts intended for release.

## 8. Synthetic verification and optional comparisons

Synthetic interactions verify specified behavior under covered conditions; they
do not prove general correctness or human understanding. Reuse the existing
focused tests and add only meaningful coverage for implemented study extensions.

High-value cases include: no execution before acceptance; preparation versus
execution cues; preservation of unchanged terms; stale/duplicate/held gesture
rejection; wait preserving a pending question; context interruption precedence;
unknown observations versus absence; and no automatic replay after uncertainty.
For the new patterns, test primary success, one representative failure, and the
distinct control branches before a small browser/provider pilot.

Earlier in the discussion, a two-configuration comparison was proposed:
conversation-managed agreements versus state-enforced agreements, with the same
model, available actions, information, speech path, and low-level safeguards.
An indicative 24 cases x 3 repetitions x 2 configurations (144 episodes) was
considered. **This is an optional earlier proposal, not the default workload now
that a student study is planned.** A credible comparison can support a mechanism
claim, but an intentionally weak baseline would not. Removing enforcement for an
experiment should occur in an isolated harness, not in deployed physical control.

A later fuller study could distinguish ordinary conversation, explicit agreement
protocol managed in context, and the same protocol with authoritative state.
Keep model/executor/skills/budgets comparable and use independent outcome truth.
Do not claim comparative improvement from the proposed single-configuration study.

## 9. Accepted agreement and downstream robot execution

Keep three artifacts distinct:

1. **Accepted task specification:** what participants agreed to do, under which
   conditions and limits, and with which responsibilities.
2. **Validated executor request/plan:** resolved skills, arguments, object/location
   references, preconditions, permitted adaptations, and outcome checks.
3. **Behavior/output and execution evidence:** what was requested, delivered,
   observed, completed, cancelled, or left uncertain.

Current agreements are plausible inputs to a later planner, not general
robot-executable plans. `BehaviourPlan` is also not that plan. A model prompt alone
does not ensure preservation of the agreed conditions when translating to skills.

The promising idea from Michal Johnson's thesis is a planner over bounded,
deterministic skills with checks and retries hidden behind skill interfaces.
A future integration should preserve agreement/run/revision identity, capability
limits, pause/cancel authority, progress and terminal events, and independent
postconditions. It must distinguish controller completion from actual task
success and must not replay uncertain physical actions automatically. ROS 2
actions can provide goal/feedback/result/cancel transport semantics; a cancellation
request is not proof that execution has stopped.

For this LBR, an optional dry-run translation/contract inspection of supported
agreements could test loss of terms at the handoff. Implementing a full robot
executor is outside the recommended December scope. The long-horizon paper's
architecture dimensions are useful for comparison, but a nested state graph is
not automatically a multi-agent or long-horizon robotic planner.

## 10. Repository reading map and evidence

Paths below are relative to this brief. Use code and neighboring tests as the
current specification; older milestone reports may describe superseded behavior.

### Orientation and documentation

- [Engineering practices](../../../.agents/CODEX.md),
  [project context](../../../.agents/CONTEXT.MD),
  [current milestone state](../../../PROJECT.md#current-milestone-state).
- [README](../../../README.md): read Why/What/How, Current Agent Catalog, Testing,
  Connecting External Clients, Task-specific text inference, Response latency
  diagnostics, and Coordination Agent. Search headings rather than reading the
  whole file or historical audit indiscriminately.
- [Poster](../../posters/human-robot-coordination/POSTER.md),
  [transcript](../../posters/human-robot-coordination/TRANSCRIPT.md),
  [poster design notes](../../posters/human-robot-coordination/DESIGN_NOTES.md).
  These establish motivation and a demonstration; they are not a user study or
  word-for-word acoustic verification. The story in the transcript is fictional.
- [Coordination plan](../../../.agents/COORDINATION.md),
  [coordination results](../../../.agents/COORDINATION_RESULTS.md),
  [latest ES-08 evidence](../../../.agents/ESCALATION_RESULTS.md),
  [Speech delivery decision](../../../.agents/ES08_SPEECH_DELIVERY_DECISION.md).

### Framework and coordination implementation

- [CoordinationAgent](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/CoordinationAgent.java):
  prompts, supported operations, phase construction, shared event routing.
- [CoordinationUpdateAction](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/CoordinationUpdateAction.java)
  and [CoordinationPolicy](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/CoordinationPolicy.java):
  interpretation, validation/commit, context and speech behavior.
- [CoordinationAgreement](../../../src/main/java/ch/zhaw/prometheus/model/coordination/CoordinationAgreement.java),
  [CoordinationMemory](../../../src/main/java/ch/zhaw/prometheus/model/coordination/CoordinationMemory.java),
  [CoordinationSocialContract](../../../src/main/java/ch/zhaw/prometheus/model/coordination/CoordinationSocialContract.java):
  schema, protected terms, state/evidence, journal bounds, detector semantics.
- [CoordinationDelivery](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/CoordinationDelivery.java):
  final-delivery ownership and uncertainty boundaries; follow its action/tests.
- [TaskSpec](../../../src/main/java/ch/zhaw/prometheus/model/task/TaskSpec.java),
  [TaskDecision](../../../src/main/java/ch/zhaw/prometheus/model/task/TaskDecision.java),
  [TaskMemory](../../../src/main/java/ch/zhaw/prometheus/model/task/TaskMemory.java):
  independent sensor rules, fresh observations, budgets, and output admission.
- [State](../../../src/main/java/ch/zhaw/prometheus/model/State.java),
  [OuterState](../../../src/main/java/ch/zhaw/prometheus/model/OuterState.java),
  [Transition](../../../src/main/java/ch/zhaw/prometheus/model/Transition.java),
  [Storage](../../../src/main/java/ch/zhaw/prometheus/model/Storage.java),
  [Event](../../../src/main/java/ch/zhaw/prometheus/model/event/Event.java),
  [AgentInteractionProfile](../../../src/main/java/ch/zhaw/prometheus/model/interaction/AgentInteractionProfile.java),
  [BehaviourPlan](../../../src/main/java/ch/zhaw/prometheus/model/behaviour/BehaviourPlan.java):
  preserve these framework contracts; do not create a parallel prompt-owned runtime.
- [Valerian client](../../../src/main/resources/public/valerian/script.js):
  hand mapping, observation admission, participant interaction and output handling.
- [RoleClarificationGuessingGame](../../../src/main/java/ch/zhaw/prometheus/agentdefs/core/RoleClarificationGuessingGame.java),
  [healthcare robot-guesses definition](../../../src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare/SingleStateGuessingGame.java),
  [healthcare user-guesses definition](../../../src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare/SingleStateGuessingGameUserGuess.java),
  [GuessingMemory](../../../src/main/java/ch/zhaw/prometheus/model/guessing/GuessingMemory.java):
  existing role/secret patterns, separate from the Coordination Agent.

### Tests and acceptance tools

Under [core agent tests](../../../src/test/java/ch/zhaw/prometheus/agentdefs/core):
`CoordinationAgentUnitTest`, `CoordinationAcceptanceUnitTest`,
`CoordinationRevisionUnitTest`, `CoordinationLifecycleUnitTest`,
`CoordinationDeliveryUnitTest`, `CoordinationSocialContractUnitTest`,
`CoordinationTaskContractUnitTest`, `CoordinationPerceptionUnitTest`,
`CoordinationObservationTimingUnitTest`, `CoordinationSupportUnitTest`,
`CoordinationRecoveryUnitTest`, and capture/voice/speech-correction tests.

Also inspect:

- [CoordinationAgentIntegrationTest](../../../src/test/java/ch/zhaw/prometheus/application/CoordinationAgentIntegrationTest.java)
  and [CoordinationDeliveryIntegrationTest](../../../src/test/java/ch/zhaw/prometheus/application/CoordinationDeliveryIntegrationTest.java).
- [Synthetic escalation cases](../../../src/test/resources/coordination/escalation-cases.json).
- [Browser hand-sensor tests](../../../tests/js/live/hand-sensors.test.mjs),
  [playback-cue tests](../../../tests/js/live/playback-cue.test.mjs),
  [speech playback tests](../../../tests/js/speech/playback.test.mjs),
  [client resource contracts](../../../src/test/java/ch/zhaw/prometheus/controllers/ValerianClientStaticResourceContractTest.java),
  and relevant [Playwright journeys](../../../tests/playwright).
- [Acceptance README](../../../tests/gptlive/README.md),
  [runner](../../../tests/gptlive/run_acceptance.py),
  [escalation journeys](../../../tests/gptlive/escalation_journeys.mjs).
  Read current setup before running database/provider tests. Do not copy old paid
  commands or private configuration into a new environment without checking scope.

As historical evidence, the top ES-08 record reports 1,015 Java passes with one
intentional capacity skip, 218 Node passes, preflight/browser checks, and three
reviewed synthetic real-provider journeys. These were **not rerun for this brief**.
That record explicitly leaves physical microphone/camera/speaker, classroom,
pedagogical, and sustained capacity validation unrun/deferred. Ignored `target/`
artifacts may exist only on the original machine; verify availability and source
revision before using them as paper evidence.

## 11. Four-page manuscript plan

Provisional allocation, excluding references:

| Content | Approximate space | Main job |
| --- | --- | --- |
| Motivation, prior work, contribution | 0.75 page | Useful tasks, continuous coordination, multimodality, PROMISE lineage and specific gap |
| Framework and coordination patterns | 1 page | Compact architecture figure and principle-to-mechanism mapping; distinguish existing/new capabilities |
| Study methods and results | 1.5 pages | Three scenarios, protocol, independent rubric, denominators, results table and representative failure |
| Discussion and limitations | 0.75 page | Transferable systems lessons, evidence boundaries, future robot-execution handoff |

Use the [citation plan](REFERENCES.md#i-citation-plan-and-remaining-reading) to
select a compact foundation across coordination, multimodality, frameworks,
generative interaction, and evaluation. References are outside the stated page
limit; the constraint is the space to make a precise argument. Retain all the
discussion sources in the guide, but cite each in the manuscript only where it
supports a claim or a substantive comparison.

Prefer a figure showing observations -> interpretation/proposal -> explicit
state/control -> behavior -> delivery/outcome evidence, with human acceptance and
revision visible. Do not imply every event passes through an LLM. Include an
example accepted contract or trace only if it illuminates a result. Do not fill
limited space with the whole class hierarchy or all seven guidelines at length.

Keep results as explicit placeholders until analysis exists. Do not invent
participant quotes, ethics identifiers, statistical findings, robot deployments,
or evidence that reviewers would understand as collected observations.

## 12. Next-session actions and unresolved decisions

Read this brief, [REFERENCES.md](REFERENCES.md), and the bootstrap documents in
the order given in [README.md](README.md). Then inspect current code/status and
give a concise mental model and focused scope before proposing implementation.

Resolve or present concrete defaults for these decisions first:

1. **Feasible evaluation boundary:** implement and verify all three agreed
   executable scenarios, including contextual interruption and question-scoped
   gesture answers. If timing prevents that, report the gap for an explicit scope
   decision; do not silently substitute specification-only tasks or claim completion.
2. **Participant role and presentation:** self-configured fictional care interaction
   versus caregiver scenario; persona and presentation of supplied observations.
   English text-to-text is the selected primary mode (see STUDY.md).
   Keep presentation consistent or record deviations.
3. **Institutional and study arrangements:** ethics route, voluntary recruitment,
   teaching/grading separation, dates, data handling, raters, and author team.
4. **Operational protocol:** mandatory requirements, optional preferences,
   revision type, time/budget limits, probe timing, failed-session route, and rubric.
5. **Study deployment:** recorder, independent participant instances, browser/device
   support, tested capacity, model/prompt freeze, and anonymizable artifacts.

Suggested sequence (planning dates, not completed milestones):

- **16-20 November:** feasibility decisions, task cards/rubric, any narrowly scoped
  implementation, recording, ethics arrangements, and pilot preparation.
- **21-27 November:** pilot, freeze, and main data collection only when ready.
- **28 November-3 December:** independent coding, analysis, manuscript and figures.
- **4-7 December:** coauthor review, anonymization, requirements check, optional video.
- **8 December:** submission buffer, subject to the owner's submission instruction.

Useful next deliverables are an annotated manuscript outline and a concrete study
protocol/task-card package. For implementation, keep focused milestones and
required documentation, preserve the existing architecture and user changes,
verify the smallest high-value test set, then stop for review and commit by the
owner. Research infrastructure should not be hidden in production prompts or
added as deployment-specific framework behavior without a scoped design decision.
