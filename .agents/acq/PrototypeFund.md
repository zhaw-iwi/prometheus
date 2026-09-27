# PROMETHEUS Trust Layer: Funding Blueprint

## Document Purpose and Status

This is a reusable project blueprint for a future funding application. It
captures the project idea developed while assessing the 2026 Prototype Fund
Switzerland call, but it is intentionally written so that it can be adapted to
other research, innovation, open-source, responsible-AI, digital-health,
public-interest-technology, or technology-governance funding opportunities.

This is not yet a submission-ready proposal. The application domain,
implementation partner, applicant legal entity, team, technical identity model,
budget, and evaluation commitments remain to be selected. Items requiring a
decision are collected in the decision register near the end of the document.

Working project name:

> **PROMETHEUS Trust Layer: Accountable Multimodal Agents under Explicit Human
> Authority**

The name is provisional. "PROMETHEUS Trust Layer" is used in this document to
distinguish the proposed funded module from the existing PROMETHEUS framework.

## Executive Recommendation

The strongest funding proposition is neither a generic multimodal-agent
platform nor a domain-specific chatbot. It is:

> A reusable, open-source accountability and governance layer for multimodal AI
> agents, validated in one sharply bounded public-interest use case.

The proposal should combine:

1. a **horizontal technical contribution** that can be transferred across
   applications, avatars, and physical robots; and
2. a **vertical lighthouse application** with named users, an implementation
   partner, explicit responsibilities, and a realistic evaluation setting.

Healthcare or care support is a strong lighthouse domain only if a credible
partner and access to users are available. A healthcare label by itself does
not strengthen the proposal. Without such a partner, a public-service, civic,
education, or team-coordination setting is likely to be more feasible and less
burdened by clinical, data-protection, and medical-device questions.

## Proposal Copy Bank

### One-Sentence Pitch

PROMETHEUS Trust Layer is an open-source runtime extension that binds every
multimodal perception and agent behaviour to an identifiable actor, explicit
permission, inspectable control decision, and human override, making generative
agents more accountable across apps, avatars, and robots.

### Short Pitch

AI agents increasingly perceive and act beyond text: they listen, interpret
gestures and social context, speak, display information, orient themselves, and
move. Existing accountability mechanisms often remain detached from these
real-time interactions. PROMETHEUS Trust Layer will make the authority and
provenance of agent behaviour explicit. It will record who or what produced an
observation, which permissions and state-machine rules applied, why a behaviour
was allowed, and who can inspect, revoke, or override the agent's mandate. The
open-source layer will be tested in one concrete public-interest use case and
documented for transfer to other embodiments and domains.

### Draft Abstract

Digital agents are moving from turn-based chat interfaces into multimodal and
sometimes embodied systems that perceive speech, people, gestures, facial and
social signals, and environmental context while producing speech, displays,
gestures, orientation, or physical action. This creates an accountability gap:
users and organisations must be able to determine which agent acted, on whose
authority, with which data and permissions, through which control decision,
and with what opportunity for human intervention. PROMETHEUS Trust Layer will
extend the existing open-source PROMETHEUS agent framework with machine-readable
identity, delegation, least-privilege sensing and behaviour permissions,
decision provenance, revocation, override, and a human-readable audit view.
Unlike a passive log, the layer will enforce authority at runtime and connect
each emitted behaviour to the observations, state transitions, policies, and
generative components that produced it. A functional prototype will be
validated in a bounded public-interest scenario and evaluated for technical
correctness, human comprehensibility, governance usefulness, privacy, safety,
and computational sufficiency. The project will publish reusable software,
documentation, a deployment pattern, and practical governance findings for
accountable multimodal agents.

### Draft Problem Statement

Many conversational and agentic systems organise interaction around a text or
speech turn. That abstraction becomes insufficient when an agent receives
independent, asynchronous signals from several people, sensors, services, and
internal processes and can respond through several channels. A spoken phrase,
hand sign, detected person, inferred social situation, scheduled event, or
internal opportunity may change agent state or trigger speech, a gesture, a
display, or physical action. A conventional transcript cannot adequately show:

- which person, sensor, organisation, or service originated an input;
- whether the agent was permitted to use that input in the current context;
- which rule, state transition, model call, or internal signal led to an action;
- whether the output remained within the agent's delegated mandate;
- which human or organisation was responsible for the agent;
- how consent or authority could be withdrawn; or
- how an unsafe or unwanted action could be stopped.

This is especially important for agents acting in shared, public, care,
educational, or physical environments, where users may not initiate every
interaction and where nonverbal actions may carry as much meaning as words.

## The Underlying Interaction Insight

Human interaction has no universally dominant input or output modality. In a
simple rock-scissor-paper interaction, spoken coordination triggers hand signs;
the signs determine an outcome; and the outcome may produce speech, facial
expression, gaze, gesture, or withdrawal. Neither speech nor a chat turn is a
sufficient abstraction for the whole interaction.

PROMETHEUS therefore represents interaction at the level of events, explicit
states, transitions, policies, and structured behaviour. Arbitrary supported
perceptions may arrive independently and asynchronously; the current state
decides whether they update context, change control flow, or produce behaviour.
This makes the runtime a promising foundation for enforceable accountability:
authority and provenance can be attached to the same explicit control points
that already govern interaction.

The rock-scissor-paper example is useful as an accessible demo or presentation
hook. It should not lead a funding application. A proposal should begin with a
concrete societal problem and use the game only to explain why text transcripts
and verbal turn-taking are insufficient.

## Existing PROMETHEUS Foundation

PROMETHEUS is an event-driven Java framework for multimodal digital agents. Its
current architecture is described in the repository [README](../../README.md)
and [project context](../CONTEXT.MD).

Relevant implemented foundations include:

- explicit, persisted state-machine control using agents, states, transitions,
  guards, actions, policies, storage, and event history;
- normalization of external and internal runtime input as inspectable events;
- an `AgentInteractionProfile` declaring accepted observations and emitted
  behaviour modalities;
- persisted `BehaviourPlan` output across optional speech, nonverbal, motion,
  and display channels;
- behaviour and monitoring streams, replay semantics, scoped access, reset,
  diagnostics, and current-state inspection;
- browser-side speech, facial-expression, social-context, hand-sign, and manual
  environmental sensing in the Valerian demonstrator;
- core demonstrations for facial-expression sensitivity, social context,
  multimodal behaviour, role clarity, and rock-scissor-paper;
- healthcare-oriented agent definitions for conversation, SMART-goal coaching,
  guessing games, and therapy appointment reminders; and
- support for different clients and embodiments rather than one fixed robot or
  interface.

PROMETHEUS's distinctive basis is not simply multimodal input/output. It is the
combination of multimodality with explicit task control: task goals,
commitments, transitions, and final authority remain in an inspectable state
machine rather than being silently delegated to a prompt.

### Current Limitations That Must Be Stated Honestly

The proposed project must not present the intended architecture as already
complete. The current status is recorded in [PROJECT.md](../../PROJECT.md).
Important limitations include:

- production agents currently use no-op regulation;
- the concrete regulation prototype responds only to a limited set of events;
- facial emotion, social grouping, and other multimodal evidence are not yet
  integrated into production regulation policies;
- calculated modulation is not yet consumed by prompt or behaviour generation;
- motivation arbitration, soft and hard interruption, safety precedence, and
  regulation diagnostics remain incomplete; and
- current identity and access mechanisms do not yet constitute the proposed
  end-to-end identity, delegation, provenance, and accountability layer.

These gaps are an advantage for funding clarity if handled correctly: the
existing runtime establishes feasibility, while the proposal defines a
distinctive and testable new module. The application must specify exactly what
will be newly built during the funded period.

## Proposed Innovation

### 1. Verifiable Runtime Identity

Give relevant participants and components stable identities or attributable
roles, potentially including:

- the responsible organisation;
- the human operator or delegating principal;
- the interacting user or participant, where identification is necessary and
  proportionate;
- the agent instance and agent definition;
- perception sources such as microphones, cameras, browser clients, external
  services, or derived-signal processors;
- behaviour renderers such as a display, avatar, speaker, or robot adapter;
- model and service providers involved in generation; and
- policy, prompt, and agent-definition versions.

The prototype should avoid identifying people when a pseudonymous role or
session is sufficient. The specific identity technology remains a design
decision. Options could include ordinary organisational identity and signed
claims, verifiable credentials, or decentralized identifiers, but the project
should select the smallest interoperable mechanism justified by the use case.

### 2. Machine-Readable Delegation and Consent

Represent what an agent may do, on whose authority, in which context, for how
long, and under which constraints. Candidate permissions include:

- which observation modalities may be accepted;
- whether raw data may be processed, transmitted, or retained;
- which derived events may be stored;
- which people or roles may access history or explanations;
- which behaviour channels may be emitted;
- whether proactive behaviour is allowed;
- which actions require confirmation;
- which actions are prohibited; and
- when a mandate expires or may be revoked.

The existing `AgentInteractionProfile` can describe capabilities. The funded
work should distinguish **capability** from **authority**: an agent may be able
to sense or emit a modality without being permitted to do so in every session.

### 3. Causal Provenance and Auditability

Link each behaviour to the accountable runtime chain that produced it:

> identity and authority -> observation -> persisted context -> active state ->
> guard/policy/transition -> model or deterministic computation -> behaviour
> plan -> renderer or actuator

The provenance record should make it possible to answer:

- Who or what supplied the relevant information?
- Which version of the agent and policy was active?
- Why was a transition or behaviour permitted?
- Was a generative model involved, and with which bounded responsibility?
- Which modalities were requested and rendered?
- Did the behaviour remain within the delegated mandate?
- Who could intervene, and was an intervention attempted?

Where useful, the design may evaluate an established provenance model such as
W3C PROV rather than inventing an incompatible vocabulary. This is an option,
not yet a commitment.

### 4. Runtime Enforcement, Revocation, and Human Override

The trust layer should be more than a retrospective log. It should:

- reject observations from unauthorized sources;
- prevent behaviour outside the active mandate;
- require confirmation at defined consent gates;
- allow authority to be narrowed or revoked;
- expose a clear pause, stop, or handover mechanism;
- make safety and explicit task policy authoritative over generative output;
  and
- persist the outcome of blocked, overridden, invalidated, or aborted plans.

An abort can be a correct and successful outcome. For example, if an agent
plans to retrieve an object but a human retrieves it first, the plan should be
invalidated and the agent should disengage safely rather than continue merely
because the plan was previously generated.

### 5. Human-Readable Accountability View

Create an operator, participant, or auditor view that does not require reading
raw logs. It should show, at an appropriate level of detail:

- the agent's identity and responsible organisation;
- its current mandate and allowed modalities;
- current and recent state;
- a trace from salient observations to behaviour;
- whether a behaviour was deterministic or model-generated;
- blocked or overridden actions and the reason;
- data retention and external-provider boundaries; and
- controls for consent, revocation, stop, and escalation.

Different audiences may require different views. A participant needs a concise
explanation and control surface; an operator needs live diagnostics; and an
auditor may need a more detailed export.

### 6. Privacy and Computational Sufficiency by Design

Responsible operation should include doing only as much sensing, storage, and
model computation as the task requires. Candidate mechanisms include:

- processing perception locally where feasible;
- retaining derived events instead of raw audio or video when raw data is not
  required;
- activating sensors and models only in relevant states;
- using deterministic rules for decisions that do not require generation;
- choosing smaller or local models for bounded tasks where performance is
  sufficient;
- minimizing prompt context and repeated calls;
- documenting provider and data-flow boundaries; and
- measuring model calls, active sensing time, retained data volume, latency,
  and an appropriate compute or energy proxy.

The proposal should state these as hypotheses and design goals, not claim that
PROMETHEUS is already more sustainable. A funded prototype should compare its
event- and state-driven approach with a defined baseline.

## Objectives and Non-Goals

### Candidate Objectives

1. Define a domain-informed authority and accountability model for multimodal
   agents.
2. Implement identity, delegation, provenance, enforcement, and override as an
   integrated extension of the existing PROMETHEUS runtime.
3. Demonstrate the extension through one bounded, real-world-relevant use case
   and one supported embodiment.
4. Evaluate technical enforcement, explanation comprehensibility, participant
   control, governance usefulness, and computational sufficiency.
5. Publish reusable open-source components, interfaces, documentation, example
   policies, evaluation instruments where possible, and governance learnings.

### Non-Goals for a Short Prototype

- building a general-purpose humanoid robot;
- solving digital identity for every organisation and jurisdiction;
- automating clinical diagnosis or treatment decisions;
- making unrestricted autonomous agents safe by adding an audit log;
- completing the entire PROMETHEUS social-regulation roadmap;
- supporting every sensor, model provider, identity standard, and embodiment;
- claiming that facial expression reliably reveals a person's internal state;
  or
- replacing institutional responsibility with an identity assigned to an AI
  agent.

## Candidate Research and Innovation Questions

- How can authority over multimodal sensing and behaviour be represented so
  that machines can enforce it and people can understand it?
- Can an explicit event and state-machine runtime provide more actionable
  provenance than a conventional conversation transcript or model-call log?
- Which parts of an agent decision chain must be exposed for participants,
  operators, and auditors to judge accountability?
- How can proactive or embodied behaviour remain useful while preserving
  meaningful human control and revocation?
- When should the agent ask for consent, rely on prior delegation, remain
  silent, abort, or escalate to a person?
- Can state-dependent sensor and model activation reduce data collection and
  computational demand without materially reducing task success?
- How should uncertainty and possible bias in inferred signals, especially
  facial or social signals, affect authorization and decision policies?
- Which governance rules can be encoded and tested at runtime, and which must
  remain organisational processes outside the software?

## Lighthouse Use-Case Strategy

The technical contribution should remain reusable, but the application should
commit to one primary setting. Funders should not be asked to finance a generic
platform in search of a problem.

### Option A: Accountable Care-Support Agent

This is the strongest domain match for Alexandre de Spindler's track record and
the existing healthcare agent catalog, provided a real implementation partner
is available.

Possible bounded scenario:

> A non-diagnostic care-support agent provides appointment reminders or bounded
> SMART-goal coaching. The participant controls which speech and nonverbal
> signals the agent may use, which data may be retained, whether the agent may
> initiate interaction, and who may inspect a session. The care organisation's
> mandate and the agent's limits are visible. The user or professional can stop,
> revoke, or hand over the interaction at any time.

Potential stakeholders:

- an older adult, rehabilitation participant, or chronic-care participant;
- a family member or informal caregiver, where appropriate;
- a therapist, care professional, or coach;
- the responsible care organisation;
- the technology operator; and
- an ethics, data-protection, or patient-representation advisor.

Questions made concrete by this domain:

- Who may authorize reminders or coaching?
- Can the participant restrict cameras while retaining speech interaction?
- May social or facial signals alter behaviour, and with what confidence and
  consent?
- What is visible to a caregiver or professional?
- How is the agent prevented from diagnosing, prescribing, or exceeding its
  supportive role?
- When must it stop or hand over to a human?

Recommended scope controls:

- remain non-clinical unless a clinical and regulatory pathway is explicitly
  funded;
- make no therapeutic efficacy claim in the prototype phase;
- do not infer diagnosis, distress, incapacity, or adherence from facial
  expression alone;
- use synthetic or consented data during development;
- define escalation and emergency limitations plainly; and
- involve users and professionals in participatory design before freezing
  policy rules.

### Option B: Accountable Public-Service Agent

This is a strong fallback if a public-administration or civic partner is easier
to secure than a healthcare partner.

Possible bounded scenario:

> A multimodal information or reception agent acts on behalf of a public-facing
> organisation. Visitors can verify who operates it, what information sources
> and sensors it uses, what it is authorized to say or do, and how to reach a
> human. The organisation can audit outputs, restrict modalities, and revoke the
> agent's mandate.

This setting can demonstrate multilingual access, accessibility, public-sector
accountability, transparent information boundaries, and human handover without
making clinical claims.

### Other Viable Scenarios

- **Hospital door assistance:** the agent observes a professional with occupied
  hands, offers assistance, waits for explicit consent, acts or aborts, and
  records whose authority enabled the physical action.
- **Plan invalidation during object assistance:** the agent begins a bounded
  support plan but safely withdraws when a person has already resolved the
  situation.
- **Meeting facilitation:** the agent monitors group-level dynamics and provides
  bounded recommendations under a visible organisational mandate, with strict
  participation, privacy, and retention rules.
- **Education or social-behaviour coaching:** the agent simulates or observes a
  social interaction while teachers, coaches, and learners retain clear control
  over sensing, feedback, and data reuse.
- **Warehouse safe passage:** the runtime enforces identity, safety, and motion
  authority without requiring speech; this is technically strong but less
  aligned with Alexandre's public-interest and care track record unless an
  implementation partner provides a compelling worker-safety case.

### Domain Selection Rule

Choose the use case that offers the strongest combination of:

1. a named implementation partner;
2. access to representative users or credible proxies;
3. an accountable organisational process that can be studied;
4. a scenario narrow enough to implement and test during the grant;
5. meaningful multimodal interaction rather than decorative multimodality;
6. manageable ethics, privacy, and regulatory requirements; and
7. a plausible route to continued use after the prototype.

## Candidate Four-Month Work Plan

The exact schedule must be adapted to the call, partner availability, and
required ethics or data-protection review.

### Work Package 1: Use Case, Governance, and Baseline — Weeks 1-3

- confirm the primary users, responsible organisation, agent mandate, and
  embodiment;
- co-design representative interaction and failure scenarios;
- map actors, responsibilities, consent, delegation, data flows, and redress;
- define prohibited actions, human handover, and success measures;
- select the smallest suitable identity and provenance mechanisms; and
- establish a baseline implementation and resource-use comparison.

Deliverables: use-case specification, responsibility map, threat and misuse
model, data-management outline, architecture decision record, and evaluation
protocol.

### Work Package 2: Identity, Delegation, and Enforcement — Weeks 3-8

- implement attributable identities or roles for the selected actors and
  components;
- implement session- or context-specific authority grants;
- enforce allowed observations and behaviour modalities;
- implement expiry, revocation, confirmation gates, stop, and handover;
- record blocked and invalidated actions; and
- add focused unit, persistence, and contract tests.

Deliverables: working runtime module, policy examples, API contracts, and
automated enforcement tests.

### Work Package 3: Provenance and Accountability Interface — Weeks 6-11

- connect relevant events, state decisions, model calls, behaviour plans, and
  rendering outcomes;
- expose participant, operator, and auditor explanations;
- show active authority, data boundaries, and intervention controls;
- provide exportable evidence without leaking unnecessary personal data; and
- test failure, reconnect, replay, and partial-component scenarios.

Deliverables: trace model, audit interface, explanation views, and documented
failure behaviour.

### Work Package 4: Lighthouse Integration and Evaluation — Weeks 9-15

- integrate the module with one concrete agent, client, and embodiment;
- rehearse normal, unauthorized, uncertain, revoked, overridden, and aborted
  scenarios;
- conduct technical tests and a proportionate participant/operator study;
- measure data and model use against the chosen baseline; and
- collect implementation and governance feedback from the partner.

Deliverables: functional lighthouse prototype, evaluation dataset or summary,
results report, and deployment recommendations.

### Work Package 5: Open-Source Release and Transfer — Weeks 14-16

- clean and document reusable interfaces and example policies;
- publish setup, extension, privacy, security, and governance documentation;
- describe limitations and non-supported uses;
- prepare a demonstration and dissemination materials; and
- define follow-on development, maintenance, and adoption options.

Deliverables: open-source release, technical documentation, governance guide,
demonstrator, final report, and continuation roadmap.

## Evaluation Framework

Exact targets and study sizes must be agreed with the implementation partner
and funder. Candidate evaluation dimensions are:

### Technical Enforcement

- percentage of unauthorized observations correctly rejected;
- percentage of unauthorized behaviours correctly blocked;
- correct handling of permission expiry and revocation;
- correctness of state, event, and behaviour provenance;
- ability to correlate a rendered action with its canonical persisted plan;
- stop or override latency;
- behavior under reconnect, replay, duplicate input, and unavailable service;
- deterministic handling of defined safety and consent gates; and
- proportion of audit records passing completeness checks.

### Human Understanding and Control

- whether participants can identify who operates the agent;
- whether they understand which sensing and behaviour permissions are active;
- whether they can successfully restrict, revoke, stop, or hand over;
- whether explanations answer the user's question without requiring raw-log
  expertise;
- perceived control, trust calibration, and appropriateness; and
- misunderstandings about agent autonomy, responsibility, or data retention.

The aim is calibrated trust and effective control, not maximizing a generic
trust score.

### Task and Interaction Quality

- completion of the bounded task;
- false initiation and missed-assistance rates;
- correct abort or disengagement when a plan is invalidated;
- appropriateness of modality selection;
- interruption burden and time to human handover; and
- partner assessment of workflow fit.

### Responsible-AI and Governance Utility

- whether responsibilities and redress paths are identifiable;
- whether organisational policy can be represented without hiding necessary
  human judgment;
- whether auditors can reconstruct representative decisions;
- documented trade-offs between autonomy, usability, privacy, and control;
- fairness and accessibility issues found during participatory evaluation; and
- concrete policy or process changes recommended to the partner.

### Sustainability and Sufficiency

- model calls per completed task;
- active sensing time by modality;
- raw and derived data retained per session;
- network transfer or provider requests;
- latency and task quality under lower-resource configurations;
- an appropriate compute or energy proxy; and
- evidence of rebound risks, such as increased usage caused by lower per-task
  cost.

## Responsible-AI Case

### Transparency and Explainability

- explicit states and transitions instead of hidden prompt-owned task control;
- visible agent identity, role, mandate, and responsible organisation;
- traceable inputs, decisions, model involvement, and behaviours;
- documented limitations, uncertainty, and data boundaries; and
- audience-specific explanations rather than one opaque log.

### Accountability and Governance

- machine-readable authority paired with named human and organisational
  responsibility;
- policies enforced at runtime rather than existing only in a document;
- revocation, intervention, handover, and redress mechanisms;
- versioned agent definitions, policies, and relevant prompts; and
- evidence suitable for operational review.

### Privacy and Security

- least-privilege sensing and action permissions;
- data minimization and purpose limitation;
- pseudonymous roles when identity is not required;
- local or edge processing where appropriate;
- authenticated observation and behaviour sources;
- explicit retention and access controls; and
- threat modelling for spoofed sensors, unauthorized clients, prompt attacks,
  privilege escalation, and audit-log leakage.

### Safety and Robustness

- state-machine and safety-policy authority over generated content;
- bounded agent responsibilities and prohibited actions;
- confidence- and consent-aware treatment of uncertain perception;
- stop, abort, override, and human-handover semantics;
- deterministic tests for representative failure paths; and
- no assumption that a plausible generative explanation proves causal truth.

### Fairness and Accessibility

- participatory design with affected users rather than evaluation by developers
  alone;
- evaluation across relevant languages, abilities, interaction styles, and
  environmental conditions;
- opt-out or alternative modalities where practical;
- explicit study of uneven sensor and model performance; and
- no high-impact decision based only on inferred facial emotion or social
  behaviour.

### Public Interest and Societal Impact

- human control over agents acting in shared or vulnerable contexts;
- reusable open-source infrastructure rather than a closed one-off demo;
- practical governance knowledge for organisations deploying agents; and
- transferability across public service, care, education, teamwork, and
  embodied interaction.

## Open Source, Intellectual Property, and Sustainability of the Project

The existing repository uses the [MIT License](../../LICENSE), with copyright
held by the Zurich University of Applied Sciences, Institute for Business IT.
This provides a strong basis for open-source reuse, but a future application
must still clarify:

- the applicant's authority to build on and distribute the existing work;
- use of university employment time, infrastructure, and branding;
- ownership and licensing of newly funded code and documentation;
- whether partner-specific adapters or data can be published;
- contribution, review, security, and release governance;
- dependency and model-provider licensing; and
- who will maintain the module after the funding period.

The preferred outcome is an upstream-compatible open-source module rather than
a private fork. Domain data, personal data, credentials, and partner secrets
must not be included in the public release. A public example should use
synthetic or explicitly releasable data.

## Impact and Transferability

The immediate output is a tested module and lighthouse deployment pattern. The
broader contribution is a way to make multimodal agents governable across
embodiments.

Potential transfer mechanisms include:

- stable identity, policy, provenance, and audit interfaces;
- reusable example grants for sensing, speaking, displaying, gesturing, and
  acting;
- documented mappings to organisational responsibility and approval flows;
- adapters for applications, avatars, and robots;
- training and evaluation scenarios for implementers;
- governance and procurement checklists; and
- evidence for standards, policy, or institutional AI-governance discussions.

The proposal should distinguish software scalability from institutional
adoption. Technical reuse is plausible through open interfaces; adoption also
requires domain ownership, integration capacity, governance processes, user
acceptance, and ongoing maintenance.

## Applicant and Team Positioning

### Alexandre de Spindler

Use a project-specific biography and verify the formal title and affiliation
before submission.

Draft positioning:

> Alexandre de Spindler is an applied researcher and designer of interactive
> systems with more than two decades of experience in information systems,
> human-computer interaction, human-AI collaboration, and applied innovation.
> His work focuses on structured, reliable, and socially responsive AI systems
> that participate in human workflows under explicit interaction rules. His
> PROMISE work provides a foundation for state-machine-driven orchestration of
> LLM interactions, while Social Behaviour Regulation and PROMETHEUS extend
> this trajectory toward socially adaptive and multimodal agents. Since 2010,
> he has led or co-led applied projects funded by Innosuisse, DIZH, the EU AAL
> programme, and other Swiss funders, combining participatory design,
> prototyping, real-world evaluation, and implementation-partner collaboration.

Distinctive contribution to this proposal:

- interaction architecture and explicit state-machine control;
- multimodal human-AI interaction and socially responsive behaviour;
- participatory and situated design;
- translation between research prototypes and implementation contexts;
- healthcare, care, education, public administration, and team collaboration
  experience; and
- real-world evaluation of bounded interactive systems.

Do not position Alexandre merely as a generic AI expert. His relevant strength
is designing and validating structured socio-technical interaction systems in
which generative AI has a bounded and accountable role.

### Relevant Prior Work to Select From

Use only the items that substantiate the selected use case and claims.

- **PROMISE: A Framework for Model-Driven Stateful Prompt Orchestration**,
  CAiSE 2024: structured, stateful, controllable LLM interaction.
- **PROMISE: Prompt Orchestration for Conversational Interactions**, 2023:
  software foundation.
- **SBR: Social Behaviour Regulation**, 2024: socially adaptive agent
  behaviour.
- **PROMETHEUS: Multi-Modal Sensing and Behaviour**, 2026: direct conceptual
  foundation for the proposed project.
- **From Conversation to Agent: LLM-Driven Design of Structured Healthcare
  Agents**, ICTH 2025: use when selecting a health application.
- **Development of a Personality-aware AI Companion for the Emotional Support
  of Older People**, 2025: use for older-adult companionship or care support.
- **Closing the Loop for Patients with Chronic Diseases - from Problems to a
  Solution Architecture**, IEEE ICHI 2023: use for chronic-care workflows.
- **GOOSVC: Version Control for Content Creation with Generative AI**, 2025:
  useful for provenance or collaborative-work positioning.
- **A Bounded Coordination-Support Capability for Multi-Party Settings**, 2026:
  useful for team, facilitation, or safety-conscious multi-party settings.

Relevant funded-project hooks include Digital Companion, Sleep Coach, FairCare,
health literacy for young adults with hearing loss, Tele-Assessment, public
administration chatbots, conversational business-process automation, digital
co-facilitation, and virtual-reality conflict-management training. Exact roles,
dates, titles, amounts, and current publication status must be verified before
submission.

### Team Capabilities Still Needed

A competitive interdisciplinary team should cover:

- software and PROMETHEUS runtime engineering;
- digital identity, authorization, security, and threat modelling;
- domain practice and access to the implementation setting;
- participatory design and human-subject evaluation;
- responsible AI, privacy, data protection, or technology governance; and
- open-source documentation, dissemination, and maintenance.

One person may cover several roles, but the application should assign named
responsibilities and credible effort. A fund that values team diversity should
not receive a proposal that appears to be a one-person technical project with
an advisory partner added nominally.

## Implementation-Partner Requirements

The preferred partner should provide more than a letter of support. Seek a
partner able to:

- name the workflow and accountable owner;
- provide access to representative users or staff;
- co-design authority, consent, failure, and handover cases;
- review privacy, safety, and organisational constraints;
- host or simulate the lighthouse evaluation;
- attend milestone reviews; and
- state what would be required for continued deployment.

For care or healthcare, also seek access to patient or participant
representation and professional ethics/data-protection expertise. Decide early
whether formal ethics approval is necessary; its lead time may determine which
evaluation can realistically be promised.

## Budget Template

Adapt all categories to the funder's eligibility rules. Do not assign amounts
until the team, duration, indirect-cost policy, and partner contributions are
known.

Candidate categories:

- personnel for runtime and interface development;
- identity/security architecture and review;
- participatory design and domain-partner effort;
- participant recruitment, accessibility, and compensation;
- evaluation and data analysis;
- robot, avatar, sensor, hosting, or model-service costs directly required by
  the prototype;
- privacy, ethics, or legal review;
- open-source hardening, documentation, and release;
- travel and mandatory programme participation; and
- dissemination and demonstration.

Protect the core build and evaluation effort. Avoid spending a large share on
new robot hardware unless embodiment is itself central to the call; an existing
robot, avatar, or browser client is more credible for a short prototype.

## Risk Register

| Risk | Consequence | Mitigation |
| --- | --- | --- |
| Proposal remains platform-generic | Weak relevance and untestable impact | Commit to one named use case, user, decision, partner, and evaluation setting. |
| Healthcare is used decoratively | Added regulatory burden without stronger evidence | Select care only with a committed partner and bounded non-clinical task. |
| Identity scope becomes infrastructure research | Four-month prototype becomes infeasible | Implement the minimum interoperable identity and delegation mechanism required by the lighthouse use case. |
| Auditability is reduced to logging | No meaningful accountability innovation | Enforce authority at runtime and test rejection, revocation, stop, and override. |
| Generative explanation is mistaken for provenance | Explanations may be plausible but false | Generate explanations from canonical persisted events and decisions, not model recollection. |
| Facial/social sensing is treated as ground truth | Bias, inaccuracy, and possible harm | Preserve uncertainty, require consent, offer alternatives, and forbid high-impact decisions from these signals alone. |
| Regulation roadmap is conflated with funded scope | Overclaiming and delivery risk | Keep explicit state-machine policy authoritative; add only regulation work required by the selected use case. |
| No implementation partner or users | Weak impact and evaluation credibility | Secure a partner and access plan before finalizing the application. |
| University is not an eligible applicant | Formal rejection or contracting failure | Confirm an eligible legal entity and employment/IP arrangements before submission. |
| Existing code ownership is unclear | Release or exploitation conflict | Agree pre-existing/new IP, contribution, licensing, branding, and maintenance responsibilities. |
| External model or identity dependency dominates | Lock-in, cost, or outage risk | Use explicit provider boundaries, record versions, and retain deterministic fallbacks for critical policy decisions. |
| Sustainability remains rhetorical | Weak responsible-and-sustainable-AI case | Define a baseline and measure sensing, storage, model use, and task-quality trade-offs. |
| Human study cannot receive approval in time | Evaluation promises cannot be met | Decide ethics needs early and prepare a staged fallback using expert walkthroughs or low-risk simulated scenarios. |

## Claim and Messaging Guardrails

Use:

- "explicit state-machine control bounds task behaviour";
- "supported perceptions use a shared event pipeline";
- "structured behaviour plans coordinate several optional output channels";
- "the project will implement and evaluate accountability mechanisms";
- "the framework can connect to apps, avatars, and robots"; and
- "the prototype will test whether event- and state-dependent processing
  reduces unnecessary sensing and model use."

Avoid:

- "all modalities and all robots are already supported";
- "PROMETHEUS guarantees safe or predictable generative AI";
- "emotion can be read from a face";
- "the regulation system is complete";
- "the agent is accountable in place of a person or organisation";
- "health outcomes will improve" without a suitable clinical design; and
- "the platform is sustainable" without comparative evidence.

## How to Adapt the Blueprint to a Future Call

Before drafting, extract the call's exact wording for:

- eligible applicant and required legal entity;
- thematic priority or named challenge;
- funding amount, duration, and allowable costs;
- expected technology-readiness or prototype maturity;
- open-source, data, IP, and exploitation requirements;
- required implementation partners and user access;
- evaluation, ethics, sustainability, and governance expectations;
- mandatory events and applicant time commitments;
- selection dimensions and their weights; and
- submission questions, character limits, attachments, and deadline.

Then map the proposal explicitly:

| Common Criterion | PROMETHEUS Trust Layer Evidence | What Must Be Added |
| --- | --- | --- |
| Relevance | Accountability gap for multimodal and embodied agents | Named societal challenge and partner evidence |
| Innovation | Enforceable identity, authority, and provenance integrated with explicit interaction control | Comparison with closest technical and governance alternatives |
| Feasibility | Working open-source runtime, clients, event history, behaviour contracts, and demonstrations | Frozen MVP, named team, schedule, and dependency plan |
| Responsible AI | Explicit control, inspectability, least privilege, override, and human responsibility | Domain co-design, threat model, fairness/privacy evaluation |
| Sustainability | State-dependent sensing and bounded model use offer testable sufficiency mechanisms | Baseline, measurements, and trade-off targets |
| Impact | Reusable layer across apps, avatars, and robots | Lighthouse deployment and credible post-grant owner |
| Open source | Existing MIT-licensed foundation | New-code licence, contribution plan, documentation, and maintenance |
| Team | Alexandre's applied research and human-AI collaboration record | Identity/security specialist, domain partner, evaluator/governance expertise |

## Prototype Fund Switzerland 2026 Assessment Record

Retain this section as provenance for the original opportunity assessment. The
requirements may change in later rounds and must not be reused without checking
the live call.

### Why the Idea Fit

The 2026 edition focused on Responsible and Sustainable AI and sought
functional, open-source prototypes with a concrete societal challenge,
real-world potential, governance learning, and transferability. The closest
challenge was **Agentic AI & Digital Identity: Building Trust & Accountability
in Autonomous Systems**, which asked how agents can be identified,
authenticated, authorized, traced, audited, constrained, and kept under
meaningful human control.

This matched PROMETHEUS particularly well because explicit events, states,
policies, history, and structured behaviours offer concrete enforcement and
provenance points. Applying through the Wildcard would have been less persuasive
because a dedicated challenge already described the core accountability need.

### Material Eligibility and Programme Facts as Reviewed on 1 September 2026

- Up to CHF 50,000 per team.
- Approximately four months of core prototyping.
- Applications in English.
- Applicants had to be adults with a valid Swiss work permit.
- A legal entity with its own bank account was required after approval; examples
  included an association, sole proprietorship, or LLC.
- Public institutions and universities were not eligible applicants.
- Researchers and university employees could participate through an appropriate
  eligible legal entity.
- At least two or three team members had to attend programme events.
- Existing projects were eligible only when the funded work was a distinctive
  extension, module, or new application context with a clearly defined
  four-month scope.
- Open-source elements were required.
- The stated application deadline was 6 September 2026 at midnight.

The university restriction was the material obstacle for a direct ZHAW
application. An external legal entity might have been possible in principle,
but it would have required deliberate decisions about contracting, employment
time, existing ZHAW-owned code, new intellectual property, team composition,
and programme attendance. Those issues should not be solved informally merely
to fit a deadline.

Sources reviewed:

- [Prototype Fund 2026 selection criteria](https://prototypefund.opendata.ch/en/application/selection-criteria/)
- [Prototype Fund 2026 FAQ](https://prototypefund.opendata.ch/en/application/faq-2/)
- [Agentic AI and Digital Identity challenge](https://prototypefund.opendata.ch/en/agentic-ai-digital-identity-building-trust-accountability-and-autonomous-systems/)
- [Wildcard challenge](https://prototypefund.opendata.ch/en/wildcard-responsible-sustainable-ai-beyond-the-challenge-areas/)
- [2026-2027 programme schedule](https://prototypefund.opendata.ch/en/application/timeline/)

## Decision Register

The blueprint can be preserved now without answering these questions. They must
be resolved before converting it into a real application.

### Decisions Required Before Selecting a Funding Opportunity

1. **Project ownership:** Is this a ZHAW project, a project of an external legal
   entity, a joint project, or an open-source community project?
2. **Long-term intention:** Is the desired outcome primarily a reusable research
   infrastructure, an implementation-partner solution, a maintained open-source
   product, a spin-off asset, or a combination with clearly separated owners?
3. **Domain priority:** Is healthcare/care the preferred lighthouse domain, or
   should the use case be selected opportunistically according to partner and
   call fit?
4. **Scope boundary:** Is the trust layer an independent project, or should it
   include a tightly bounded portion of the incomplete regulation system?

### Decisions Required Before Drafting an Application

5. **Primary use case:** What exact task will the agent support, for whom, in
   which setting, and through which embodiment?
6. **Implementation partner:** Which organisation owns the workflow and will
   provide staff, users, governance input, and a test environment?
7. **Applicant and consortium:** Which eligible entity applies, who contracts,
   and who are the named technical, domain, identity/security, evaluation, and
   governance leads?
8. **Pre-existing and new IP:** What may be reused from the MIT-licensed ZHAW
   repository, who owns the funded additions, and where will they be maintained?
9. **Identity model:** Which actors must be identifiable, which can remain
   pseudonymous, and which interoperability standard is justified?
10. **Authority model:** Which observations and behaviours require prior
    delegation, contextual consent, real-time confirmation, or prohibition?
11. **Evaluation access:** Which representative participants can be recruited,
    and is ethics or data-protection approval required?
12. **Embodiment:** Will the prototype use Valerian, an avatar, an existing
    robot, or another client? Avoid committing to new hardware without a strong
    reason.
13. **Data and model boundary:** Which sensing data, external providers, models,
    retention periods, and deployment environment are acceptable?
14. **Sustainability baseline:** Against what system or configuration will data
    collection and computational sufficiency be compared?
15. **Success thresholds:** What measurable technical, human, governance, and
    sustainability results will constitute a successful prototype?
16. **Post-funding owner:** Who will maintain, deploy, support, and finance the
    module after the grant?

### Recommended Decision Order

Resolve these first because they determine almost everything else:

1. applicant/project ownership and IP route;
2. lighthouse domain and implementation partner;
3. exact task, users, and embodiment;
4. team and access to evaluation participants;
5. four-month MVP and explicit non-goals;
6. identity/delegation technology and evaluation design; and
7. budget, dissemination, and post-funding sustainability.

## Readiness Checklist for the Next Opportunity

- [ ] The applicant is eligible and the contracting route is documented.
- [ ] Pre-existing and new IP arrangements are agreed.
- [ ] One primary use case, user group, and accountable organisation are named.
- [ ] The implementation partner has committed meaningful effort and access.
- [ ] The four-month or call-specific funded delta is distinct from existing
      PROMETHEUS capabilities.
- [ ] Objectives, non-goals, work packages, deliverables, and dependencies fit
      the available time and budget.
- [ ] Identity, delegation, provenance, enforcement, and override form one
      coherent MVP.
- [ ] The data, privacy, security, ethics, and human-handover plan is credible.
- [ ] Responsible-AI claims have corresponding implementation or evaluation.
- [ ] Sustainability claims have a baseline and measurement plan.
- [ ] The evaluation setting, participants, metrics, and approval path are
      feasible.
- [ ] The open-source release, documentation, maintenance, and exploitation
      plan is agreed.
- [ ] Alexandre's title, affiliation, project roles, and selected bibliography
      have been formally verified.
- [ ] Application answers use the funder's terminology and character limits.
- [ ] A reviewer unfamiliar with PROMETHEUS can understand the societal problem
      before encountering the architecture.
