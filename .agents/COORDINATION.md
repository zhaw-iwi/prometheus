# Runtime coordination between people and robots

Prepared: 2026-10-27. Status: concept and source handoff, grounded in the Gala
demonstrator. This document preserves the explanatory text and source references
from the discussion and supplies context for a future coding-agent session. It
does not define an approved implementation milestone or claim a general-purpose
coordination system already exists.

## Start here

Read `.agents/CODEX.md`, `.agents/CONTEXT.MD`, and `PROJECT.md` from the beginning
through `Current milestone state`, stopping before `Historical milestones
checklist`. Then check the branch/worktree and the implementation, configuration,
README sections and neighboring tests relevant to the actual request. Search
historical evidence selectively. This document supplements those sources; running
code and tests determine current behavior.

The owner's interest is to make **runtime coordination a first-order topic in
human-robot collaboration**: robots converse with people, suggest activities,
jointly select one, establish how to carry it out, and then act. The motivating
LinkedIn post asks how impressive physical abilities, such as faster running,
translate into everyday usefulness. It describes a gala demonstration and offers
unloading a dishwasher as a future example. The post and the reported successful
gala experience are owner-provided context, not an independently measured study.
The collaboration considered here is between people and a robot; multi-robot
coordination has not been demonstrated by this example.

## Explanatory text

For robots to become useful in everyday life, they need to establish a shared
understanding with people: what shall we do together, what needs clarifying, and
when are we ready to begin? A round of rock-paper-scissors provides a small,
concrete example of that collaboration.

**GIGI, the gala reception agent, connects conversation with perception and
action.** It welcomes visitors, responds to information about people and groups,
facial expressions, hand signs and weather, and can make occasional contextual
remarks. It answers questions about the event and its institutional background,
offers rock-paper-scissors and person guessing, and supports agreeing on simple
tasks in which a sensed cue triggers a specified response. Speech is accompanied
by gestures and other nonverbal behaviour.

The preparation variant makes coordination explicit. After visitors choose
rock-paper-scissors, GIGI asks them to demonstrate the three hand signs individually.
It tracks which signs have actually been recognised and which are still missing.
Only successful recognition of all three allows the interaction to advance to the
countdown and game. Repeated signs or a spoken "I've shown them all" cannot bypass
that check. Successful preparation is remembered for the current conversation.

Technically, PROMETHEUS combines a realtime speech model with structured perception
and explicit state machines. The model handles conversational wording; the
framework maintains activity state, validates observations, controls transitions
and computes game results. Preparation therefore has an enforceable completion
condition tied to sensory evidence. The interaction unfolds at runtime within
deliberately defined capabilities and rules.

The broader pattern is **offer, choose, coordinate, execute**. Applied to household
or workplace assistance, a robot could agree on a task, clarify object locations
and responsibilities, check that it can perceive the relevant objects, and then
begin. "Empty the dishwasher; glasses go here and plates there" would become a
shared activity with clarification and readiness checks. Extending this approach
requires reliable manipulation and safety controls, but the demo illustrates the
interaction structure that could make those physical capabilities easier for
people to use.

## What the implementation establishes

The original `GalaDinnerReception` is the application-specific German GIGI agent.
It combines reception, rock-paper-scissors, person guessing and bounded co-created
tasks. It uses GPT-Live for speech, structured perception events for sensing, and
persisted state/storage for activity control. The framework publishes canonical
multimodal `BehaviourPlan` events; clients and robot integrations render supported
channels. An advertised output modality or a published motion request does not
establish physical execution.

The colleague's `GalaDinnerReceptionRpsPreparation` is a separately registered
variant on `origin/feature/gala-game-lifecycle`. At the pinned revision below:

- Selecting RPS for the first time in a Live conversation enters the explicit
  `Gala rock-scissor-paper preparation` state, with storage mode `RPS_SETUP`.
  No game round or robot sign has been selected during this preparation.
- The introduction asks for rock, paper and scissors individually, in any order.
  The existing hand-observation validator checks the evidence before a distinct
  accepted sign is added to the recognised set. Freshness, confidence and capture
  correlation matter; duplicate signs and verbal completion claims cannot satisfy
  missing prerequisites.
- Progress is persisted and exposed as recognised/remaining signs in display
  output and Live activity facts. Progress updates do not each request another
  spoken response. The speech model receives facts about progress; the backend
  decides whether the readiness condition is satisfied.
- All three recognised signs permit entry into a separate countdown phase. The
  round then has its own identity and speech cue. The branch validates countdown
  evidence before requesting the robot hand gesture and opening game capture.
  Fresh capture identity prevents preparation observations from deciding a round.
- An exit question pauses hand acceptance. Declining the exit preserves preparation
  progress and opens fresh capture. Leaving incomplete preparation discards that
  progress. Completed preparation is retained for the same Live conversation,
  including later rounds and activity changes; a new conversation or reset
  requires it again.
- The original Gala definition keeps its direct game-selection flow; the tutorial
  is a variant feature. Saved graphs are not implicitly migrated into the variant.

This is a recognition/readiness check using an existing detector. It does not
train a new gesture model, prove all later signs will be recognised, or certify
mutual understanding. Completion is conversation-scoped rather than tied to a
verified person's identity. Group membership can change within an encounter.

The generic task engine supplies a related but distinct building block: proposals,
explicit acceptance, cue rules, action limits, lifecycle and first-action timing.
`ON_CUE` activation arms a task and waits for a qualifying observation; it should
not be conflated with a completed preparation protocol. Current core-agent work
also provides Live-owned task wording and persisted round control. Inspect these
existing extensions before designing a new shared abstraction.

## How to elaborate the idea

Treat coordination as an inspectable activity phase with a completion condition.
The useful generalisation is the relationship between a shared activity, its
prerequisites, the evidence needed to establish readiness, and permission to act.
The following are design questions for the next scoped task, not implemented APIs:

1. **Activity and agreement:** What can the robot offer? What has the person
   selected or accepted? Which roles, objects, locations, constraints and limits
   are actually agreed, and which remain ambiguous?
2. **Preparation:** What must be demonstrated, clarified or checked before action?
   Can the person adapt their demonstration while the robot explains what remains
   unclear? Which existing observations can verify each prerequisite?
3. **Readiness:** What counts as sufficient evidence, how fresh must it be, and to
   which person, encounter, agreement revision or activity instance does it apply?
   A claim, acknowledgement, detection and confirmed execution are different facts.
4. **Execution and repair:** What event permits the next step? What happens on
   interruption, changed conditions, rejected evidence, timeout or uncertain
   output? Which progress survives pause/reload, and which changes invalidate it?
5. **Completion:** What evidence supports saying the activity is finished? When
   does the agent return to conversation, and what requires a new agreement?

Separate runtime flexibility from unrestricted invention. A language model can
interpret requests and phrase questions within validated capabilities. Existing
Gala activities and preparation rules are authored; the demo does not generate
arbitrary executable workflows or physical skills from conversation. A future
design must explicitly choose what can be configured at runtime and how it is
validated before changing control or authorising an action.

A useful next step, if requested, is a focused comparison between the Gala
preparation flow and one additional coordination scenario. Use that comparison
to decide whether a small shared concept is justified or whether an explicit
application state remains sufficient. Define success/failure examples and the
required evidence before choosing classes or persistence changes. No particular
scenario, generic protocol language or implementation roadmap is approved here.

## Engineering boundaries and verification

- Preserve `Event`, `AgentInteractionProfile`, `BehaviourPlan`, storage and event
  history contracts. Keep task authority in states, transitions, guards and actions.
  Use the existing model/application layers for reusable behavior and transport
  validation at controller boundaries. Do not hide state authority in prompts.
- Reuse `ExternalVoicePolicy` and `LiveActivityContext` for Live compatibility,
  current facts and identified speaking occasions where appropriate. Quiet fact
  updates must not accidentally authorise repeated speech or actions. Preserve
  ordinary Text/Continuous behavior when shared code changes.
- Keep evidence tied to the current activity/session/revision and apply existing
  freshness, duplicate and stale-result protections. Do not infer physical success
  from a command, transcript or browser playback marker. Recovery must not blindly
  repeat actions whose outcome is uncertain.
- Respect the difference between Gala's encounter-owned tasks and current core
  task lifecycle. Core session-bound tasks can pause while preserving agreements;
  do not copy Gala cleanup semantics into generic tasks without a scoped decision.
- Published contracts and saved data are real compatibility boundaries. State graph
  changes may require a fresh instance; make that explicit rather than rewriting
  existing graphs, prompts or history silently.
- For implementation work, begin with the smallest useful tests: successful
  readiness and execution, incomplete/invalid evidence, duplicate or stale evidence,
  and introduced interruption/reset branches. Add persistence or browser tests when
  those boundaries change. Reuse nearby fixtures and controlled clocks/providers.
- Follow README test isolation: database-backed tests use a disposable local MySQL
  schema and restricted account, not the developer database. Some `*UnitTest`
  classes start Spring/database contexts. Inspect fixtures before running them.
- Report local automated, real-provider and physical-device evidence separately.
  The sources/tests were inspected for this handoff; no tests or new physical
  trials were run to validate this document. Existing results files record prior
  evidence and remaining acoustic, speech-timing and capacity limits. Do not turn
  those records or the owner's demo report into a claim of universal reliability.

## Branch and source provenance

The following are the local revisions inspected when this handoff was prepared;
re-check them before future work:

| Line | Inspected revision | Role |
| --- | --- | --- |
| `main` | `35dae71b` | Reusable framework and baseline catalog, including recent core-agent improvements. |
| `agents` | `a222eb0f` | Main integrated with additional deployment/application agents, including original Gala. |
| `origin/feature/gala-game-lifecycle` | `bae5f095a3d5c64ed61f16d2e9edcb3e358a58c6` | Colleague's preparation variant and associated round/speech-cue implementation. |

At inspection, `main` was an ancestor of `agents`. The preparation variant is not
present in either of those inspected main/agents trees. The pinned branch contains
more changes than the tutorial alone and predates newer core-agent improvements;
do not assume it can be transplanted wholesale without reconciliation.

Maintain the owner's intended relationship: **agents is main plus additional
agents and their application-specific content**. Shared coordination concepts and
this handoff belong in main and flow into agents. Gala identity, event knowledge
and deployment definitions remain application-owned. For changes prompted by
GPT-Live, follow the existing feature/gptlive -> main -> agents workflow unless
the current user request explicitly changes it. Do not merge the entire agents or
Gala feature branch into main just to reuse a concept. Commit, publish and deploy
only when authorised; an agents push can trigger deployment.

Maintain identical copies of this shared document through normal branch ancestry:
commit changes on main and merge main into agents after the required review and
authorisation. Reconcile any identical local agents copy as part of integration.
Two uncommitted files alone do not establish branch-history integration.

Local worktree locations at preparation time:

- Agents: `C:/Data/VSCodeWorkSpace/prometheus`
- Main: `C:/Data/VSCodeWorkSpace/prometheus-core-agents-main-20261020`

The absolute paths below point into the agents worktree unless explicitly noted.
For shared sources in the main worktree, replace that root with the main root.
On another machine, retain the repository-relative suffix and substitute the
actual repository root. A source path must always be interpreted with its revision.

## Source references

### Orientation and public behavior

- `C:/Data/VSCodeWorkSpace/prometheus/.agents/CODEX.md` — engineering workflow,
  test selection and completion handoff.
- `C:/Data/VSCodeWorkSpace/prometheus/.agents/CONTEXT.MD` — purpose, contracts,
  architecture and branch boundaries; see current core-agent additions too.
- `C:/Data/VSCodeWorkSpace/prometheus/PROJECT.md` — current status before the
  historical checklist; search individual historical records only when needed.
- `C:/Data/VSCodeWorkSpace/prometheus/README.md` — `Gala Dinner Reception`,
  `Generic Multimodal Behaviour`, GPT-Live, setup and testing. The pinned feature
  revision contains the preparation-variant and countdown documentation; current
  main does not contain the Gala application section. Heading line numbers move.

### Gala implementation

Read the feature revision for preparation-specific behavior, even when a file of
the same name exists in the current agents checkout:

- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/GalaDinnerReception.java`
  — reception/game/task graph; feature version adds preparation/countdown states.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/GalaDinnerReceptionRpsPreparation.java`
  — **feature only**; separately registered variant entry point.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerRpsPreparation.java`
  — **feature only**; recognised/remaining signs, tutorial facts and display.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerAction.java`
  — selection, validated hand evidence, preparation completion and activity control.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerHandSigns.java`
  — validation of freshness, confidence, capture identity and camera evidence.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerRounds.java`
  — round identity and replay; feature version also owns countdown-phase metadata.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerPolicy.java`
  — Live voice, activity facts, speaking occasions and encounter lifecycle.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerSensing.java`
  — bounded presence, expression and contextual reactions.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerTaskAction.java`
  — negotiated task validation, execution limits and Gala lifecycle.

### Shared implementation to inspect before generalising

- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/model/task/TaskSpec.java`
  — validated task representation, including first-action timing and limits.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/model/task/TaskUpdateAction.java`
  — proposal/update/activation processing; neighboring task classes own guards,
  phases, storage and response confirmation.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/core/GenericMultimodalBehaviour.java`
  — existing configurable task graph.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/agentdefs/core/CoreTaskPolicy.java`
  — newer core Live wording, agreement facts and encounter behavior; inspect
  neighboring `CoreTaskVoice` and `CoreTaskUpdateAction` as needed.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/model/policy/ExternalVoicePolicy.java`
  — policy integration with Live, including lifecycle/capture hooks.
- `C:/Data/VSCodeWorkSpace/prometheus/src/main/java/ch/zhaw/prometheus/model/policy/LiveActivityContext.java`
  — bounded current facts, identified speaking occasions and playback cues.
  Compare revisions: the feature branch carries additional speech-cue support.

### Tests and operational evidence

- `C:/Data/VSCodeWorkSpace/prometheus/src/test/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerRpsPreparationUnitTest.java`
  — **feature only**; all distinct signs, rejected evidence, verbal bypass rejection,
  interrupted preparation, preserved completion and fresh-conversation reset.
- `C:/Data/VSCodeWorkSpace/prometheus/src/test/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerTaskUnitTest.java`
  — proposal, acceptance, first-action timing, sensed cues and completion.
- `C:/Data/VSCodeWorkSpace/prometheus/src/test/java/ch/zhaw/prometheus/application/GalaDinnerReceptionJourneySmokeIntegrationTest.java`
  — integrated conversation/activity/encounter journey; inspect feature changes.
- `C:/Data/VSCodeWorkSpace/prometheus/src/test/java/ch/zhaw/prometheus/application/GalaRoundLifecycleIntegrationTest.java`
  — **feature only**; round lifecycle and cue integration.
- `C:/Data/VSCodeWorkSpace/prometheus/src/test/java/ch/zhaw/prometheus/agentdefs/core/CoreTaskVoiceUnitTest.java`
  — newer shared task/Live control evidence; useful when generalising beyond Gala.
- `C:/Data/VSCodeWorkSpace/prometheus/.agents/GALAIMPROVEMENT_RESULTS.md`
  — original Gala implementation, trials and limitations; historical evidence.
- `C:/Data/VSCodeWorkSpace/prometheus/.agents/COREAGENTIMPROVEMENT_RESULTS.md`
  — recent shared/core implementation and acceptance evidence.
- `C:/Data/VSCodeWorkSpace/prometheus/tests/gptlive/GALA.md`
  — operator journey; use the feature revision for preparation-specific steps.

Feature-only paths above are locations within that revision, not files currently
present in the agents/main worktrees. Read them without switching or disturbing a
worktree, for example:

```powershell
git -C C:/Data/VSCodeWorkSpace/prometheus show bae5f095a3d5c64ed61f16d2e9edcb3e358a58c6:src/main/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerRpsPreparation.java
git -C C:/Data/VSCodeWorkSpace/prometheus show bae5f095a3d5c64ed61f16d2e9edcb3e358a58c6:src/test/java/ch/zhaw/prometheus/agentdefs/usecases/gala/GalaDinnerRpsPreparationUnitTest.java
git -C C:/Data/VSCodeWorkSpace/prometheus show bae5f095a3d5c64ed61f16d2e9edcb3e358a58c6:README.md
```

For implementation/testing on that revision, first inspect current worktrees and
use an appropriate isolated checkout if needed. The read commands above do not
fetch remote changes and the pinned commit is deliberate provenance, not a claim
to be the latest branch head.

## Outlook and next-session handoff

Dishwasher unloading, workplace handover or guided assistance are potential
transfer scenarios, not implemented capabilities. They would add object grounding,
spatial references, physical skills, action feedback and appropriate safety
controls. The transferable question is how people and robots establish and revise
enough shared understanding to make the next action useful and justified.

When tasked to continue, state the chosen scenario, the existing patterns that
apply, the evidence required for readiness, and the smallest coherent deliverable.
Distinguish conceptual elaboration from requested implementation. Preserve main's
reusable architecture and agents' additive application layer, document changed
contracts and remaining limits, report what was actually verified, and stop for
review and commit unless the user has authorised further integration.
