# Extended backlog for interaction demonstrators

This document tracks the available demonstrators for human agent interaction and the work items required to prepare a demonstration day.

The demonstrators are grouped by plan level and ordered from most mature and simplest to least mature and most expressive.

PROMISE is a verbal interaction agent development framework  
PROMETHEUS is a multimodal interaction agent development framework  
Both frameworks support bilateral interactions (one human and one agent) and multilateral interactions (one agent with multiple humans)

## Legend and dimensions

### Modalities

Input modalities
Text
Speech
Visual detections such as camera based facial detection

Output modalities
Text
Speech
Multimodal output such as non verbal behaviour

### Quick rating scale

Maturity
High means runnable and stable
Medium means runnable with known TODOs
Low means experimental and likely incomplete

Expressiveness
Low means mostly verbal or simple scripted behaviour
Medium means verbal plus one additional modality
High means multimodal in and multimodal out with regulation or richer behaviour planning

Complexity
Low means few components and simple pipeline
Medium means framework plus clients plus orchestration
High means multimodal event ingest plus behaviour planning plus streaming

## Demonstrator inventory

| Plan | Demonstrator | Framework | Interaction | Input modalities | Output modalities | Complexity | Expressiveness | Maturity | Primary owner | Key notes and TODOs |
|---|---|---|---|---|---|---|---|---|---|---|
| D | Rock Scissor Paper | Pure Python | Bilateral | Speech or Text plus Hand | Speech plus Non verbal | Low | Low to Medium | High | Team | Requires hand visibility and clear protocol |
| D | Give Me Five and getting shot | Pure Python | Bilateral | Hand and Motion cues | Non verbal plus optional Speech | Low | Medium | High | Team | Physical staging and timing sensitive |
| D | Fist Bump and Hand Greeting | Pure Python | Bilateral | Hand gesture | Non verbal | Low | Low to Medium | High | Team | Physical setup dependency |
| D | Speech to speech hard coded prompt | Pure Python | Bilateral | Speech | Speech | Low | Low | Medium | Marc | TODO persona and short responses only |
| C | PROMISE speech to speech two states | PROMISE | Bilateral | Speech | Speech | Medium | Medium | Medium | Alex and Marc | TODO gigi agent creation and config |
| C | PROMISE multilateral reports with read out loud on command | PROMISE plus Python Realtime outside PROMISE | Multilateral | Speech multi speaker | On screen reports plus Speech on command | Medium to High | Medium to High | Medium | Team | Split architecture and integration points |
| B | Multi modal behaviour with gesture selection | Python Realtime API | Bilateral | Speech or Text | Speech plus Gesture selection | Medium | High | Low to Medium | Team | Prompt driven gesture mapping needs validation |
| B | Multi modal input camera facial detection speak detections | Python plus camera detection | Bilateral | Visual detections plus optional Speech | Speech | Medium | High | Low to Medium | Team | Detection reliability and environment constraints |
| A.2 | PROMETHEUS verbal plus multimodal output | PROMETHEUS | Bilateral | Text or Speech events | BehaviourPlan speech plus Non verbal plus optional Motion or Display | High | High | Low to Medium | Alex and Marc | Needs stable streaming and renderer setup |
| A.2 | PROMETHEUS verbal plus multimodal input | PROMETHEUS | Bilateral | Multimodal events | Verbal and optional BehaviourPlan | High | High | Low to Medium | Alex and Marc | Requires robust event ingest pipeline |
| A.1 | SocialInitiativeMvpAgent multimodal in plus out | PROMETHEUS | Bilateral | Multimodal events including Speech | BehaviourPlan multimodal output including Speech | Very High | Very High | Low | Team | Critical TODO realtime client must speak BehaviourPlan speech from SSE |

### Plan D

Pure Python implementations  
Most mature and simplest  
Typically offline or hard coded logic  
Does not require PROMISE or PROMETHEUS

#### D1 Rock Scissor Paper

Type  
Verbal and Non verbal  
Offline

Input  
Speech or text depending on implementation  
Hand or gesture required in the physical setup

Output  
Speech  
Non verbal cues as applicable

Maturity  
High

Expressiveness  
Low to medium

Owner  
Team

Notes and risks  
Requires hand visibility and a clear interaction protocol

#### D2 Give Me Five and getting shot

Type  
Non verbal plus verbal  
Offline

Input  
Hand based or motion based cues

Output  
Non verbal reaction  
Optional verbal reaction

Maturity  
High

Expressiveness  
Medium

Owner  
Team

Notes and risks  
Requires physical staging and consistent timing

#### D3 Fist Bump and Hand Greeting

Type  
Non verbal

Input  
Hand based gesture

Output  
Non verbal acknowledgement

Maturity  
High

Expressiveness  
Low to medium

Owner  
Team

Notes and risks  
Physical setup dependency

#### D4 Speech to speech with hard coded prompt

Type  
Verbal  
Hard coded prompt

Input  
Speech

Output  
Speech

Maturity  
Medium

Expressiveness  
Low

Owner  
Marc

Known TODOs  
Persona  
Short responses only

---

### Plan C

PROMISE based demonstrators and multilateral branch items  
More complex than Plan D because of orchestration and clients

#### C1 PROMISE Speech to speech two states

Framework  
PROMISE

Interaction  
Bilateral

Input  
Speech via realtime client

Output  
Speech

Maturity  
Medium

Expressiveness  
Medium

Owner  
Alex for agent creation  
Marc for testing

Known TODOs  
Gigi agent creation and configuration

Dependencies  
PROMISE realtime client and prompt bundle flow

#### C2 PROMISE Multilateral with reports on screen and read out loud on command

Framework  
PROMISE multilateral branch plus external realtime Python pipeline

Interaction  
Multilateral

Input  
Speech from multiple speakers

Output  
On screen reports  
Speech read out loud on command via python realtime outside of PROMISE

Maturity  
Medium

Expressiveness  
Medium to high

Owner  
Team

Notes and risks  
Split architecture  
PROMISE for orchestration and storage  
Python realtime outside of PROMISE for speaking the reports

---

### Plan B

Python realtime oriented multimodal prototypes  
More expressive than Plan C but typically less mature

#### B1 Multi Modal Behaviour with gesture selection

Type  
Verbal prompt extended with request to choose a gesture  
Python Realtime API

Input  
Speech or text prompt content

Output  
Speech plus selected gesture metadata or behaviour instruction

Maturity  
Low to medium

Expressiveness  
High relative to Plan D and C

Owner  
Team

Notes and risks  
Gesture selection is prompt driven  
Needs verification of consistent gesture mapping and rendering

#### B2 Multi Modal Input with camera facial detection and speak out detections

Type  
Camera facial detection plus speech output

Input  
Visual detections from camera such as facial signals  
Optional speech

Output  
Speech describing what was detected

Maturity  
Low to medium

Expressiveness  
High

Owner  
Team

Notes and risks  
Detection reliability  
Privacy and environment constraints  
Needs stable framing and lighting

---

### Plan A.2

PROMETHEUS based multimodal agents  
Higher complexity and richer control  
Typically less mature than Plan C and D

#### A2 1 PROMETHEUS Verbal plus multimodal output

Framework  
PROMETHEUS

Input  
Likely text or speech event ingest

Output  
BehaviourPlan with speech plus non verbal and optionally motion or display  
Streaming via behaviour SSE and renderer clients

Maturity  
Low to medium

Expressiveness  
High

Owner  
Alex for agents  
Marc for testing

Dependencies  
PROMETHEUS behaviour streaming  
Nonverbal renderer and any visual clients as needed

#### A2 2 PROMETHEUS Verbal plus multimodal input

Framework  
PROMETHEUS

Input  
Event based multimodal observations  
Examples include user utterance events and visual or social observation events

Output  
Primarily verbal but can also yield behaviour plans depending on configuration

Maturity  
Low to medium

Expressiveness  
High

Owner  
Alex for agents  
Marc for testing

Dependencies  
Reliable event ingest pipeline  
Client support for capturing and sending multimodal events

---

### Plan A.1

Most expressive and least mature  
Full multimodal in plus multimodal out in PROMETHEUS

#### A1 SocialInitiativeMvpAgent

Framework  
PROMETHEUS

Input  
Multimodal input via events  
Speech plus additional observation signals

Output  
Multimodal BehaviourPlan  
Speech plus non verbal and other modalities  
Requires behaviour streaming integration for realtime speech output

Maturity  
Low

Expressiveness  
Very high

Owner  
Team

Primary risk  
Realtime speech pipeline integration with behaviour plans is incomplete and must be finished for demo day

---

## Preparation backlog

This section is a working backlog for Alex and Marc.  
Items are grouped by task and include concrete acceptance criteria.

### Status labels

Todo  
In progress  
Blocked  
Done

### Task 1 Verify plans D to B runnability and TODOs

Owners  
Marc and Alex

Goal  
Ensure demonstrators from Plan D through Plan B run reliably and have a crisp demo script

Scope  
Plan D all items  
Plan C all items  
Plan B all items

Work items
Todo Inventory each demonstrator and record exact run steps  
Todo Run each demonstrator end to end and note failures  
Todo Fix small breakages and update scripts  
Todo Identify remaining TODOs and decide if they will be done or deferred  
Todo Confirm audio devices and camera permissions on demo hardware

Acceptance criteria
Each demonstrator has a short runbook with commands and URLs  
Each demonstrator can be started within a few minutes on the target machine  
Known issues are documented with a workaround or a decision to exclude

Related backlog item
Todo PROMISE reset from client should also reset pipeline and read out starter if present

Definition of done for reset item
Reset action in client triggers backend reset and clears realtime pipeline state  
If a starter prompt exists it is spoken immediately after reset

---

### Task 2 Create and test two digital agents

Owners  
Alex creates  
Marc tests

Goal  
Have two specific agents ready for demo with stable prompts and state models

Work items
Todo Alex defines agent goals and interaction flow for two agents  
Todo Alex seeds or creates agents in the chosen framework  
Todo Marc runs both agents through the main demo script and reports issues  
Todo Iterate prompts for robustness to transcription variance and short user replies

Acceptance criteria
Two agents are listed and loadable by agentId  
Both complete their intended flow in a short scripted interaction  
Monitor view shows expected state transitions and storage updates

---

### Task 3 Realtime speech for Plan A.1 behaviour plan integration

Owners  
Alex and Marc

Goal  
When A.1 transitions to starting state it triggers generate and a verbal behaviour is streamed via SSE and spoken by the realtime pipeline client

Current description
Transition to starting state invokes generate which results in a verbal behaviour sent via SSE  
Realtime pipeline clients must
a receive the behaviour plan from SSE  
b check if behaviour plan contains speech behaviour and possibly remove TEXT prefix  
c make pipeline speak out the speech behaviour

Work items
Todo Confirm which endpoint and SSE channel emits the behaviour plan  
Todo Implement SSE subscription in realtime pipeline clients  
Todo Parse BehaviourPlan payload and detect speech field  
Todo Normalize speech content and remove any TEXT prefix if present  
Todo Trigger speech output in the realtime pipeline  
Todo Add logging and visible debug overlay for received behaviour plans  
Todo Add a minimal fallback when no speech behaviour exists such as do nothing

Acceptance criteria
Starting Plan A.1 produces audible speech without manual intervention  
Logs show received behaviour plan and extracted speech content  
No duplicate speaking and no speaking of raw JSON

Dependencies
PROMETHEUS behaviour generate endpoint and behaviour stream  
Realtime client capability to speak programmatically

---

### Task 4 Validate multi modal behaviour prototype

Owners  
Team

Goal  
Understand and stabilize the Plan B1 multi modal behaviour demonstrator and align it with A.2 expectations

Work items
Todo Run the gesture selection demo and capture examples  
Todo Verify gesture mapping and renderer or display behaviour  
Todo Decide whether to present gesture output visually or only as spoken explanation  
Todo Compare to A.2 multimodal out design and identify gaps

Acceptance criteria
Demo can reliably produce a gesture choice and a corresponding behaviour output  
A short demo script exists with expected outcomes

---

### Task 5 Prepare Plan A.2 agents and multimodal out

Owners  
Alex first for agent creation  
Marc for testing

Goal  
Have at least one A.2 agent that demonstrates multimodal output and optionally one that demonstrates multimodal input

Work items
Todo Alex creates initial verbal agents in PROMETHEUS as a baseline  
Todo Extend one agent to output multimodal behaviour plans  
Todo Verify rendering via nonverbal renderer and any visual clients  
Todo Compare A.2 multimodal out to Task 4 behaviour prototype and reuse patterns where possible  
Todo Optional add a controlled multimodal input event source and show adaptation

Acceptance criteria
At least one A.2 agent outputs speech plus a visible nonverbal behaviour  
Monitor client shows behaviour events and state transitions  
Demo script is stable under basic transcription variability

---

## Demo day checklist draft

Todo Decide which plans will be presented and in what order  
Todo Prepare one consistent narrative from Plan D to Plan A.1  
Todo Ensure all required URLs and agentIds are printed in a one page run sheet  
Todo Verify microphone and camera permissions on demo setup  
Todo Prepare fallback paths if A.1 integration is not stable
