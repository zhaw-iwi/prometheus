# PROMETHEUS Context for Codex

This is the project-specific orientation for work on PROMETHEUS. Reusable
engineering and milestone practices live in `.agents/CODEX.md`. Current status
is summarized at the top of `PROJECT.md`; the rest of that file is a historical
audit and should be searched only when a task needs earlier decisions. Setup and
public API details live in `README.md`.

## Purpose and Intended Outcome

PROMETHEUS is a Java framework for developing multimodal digital agents. It maps
multimodal perception to multimodal behaviour through explicit, inspectable
agent control.

The framework is intended to support agents that:

- receive speech, facial, social, gestural, environmental, system, and internal
  signals through one event-driven runtime;
- control tasks with explicit state machines rather than an opaque chat loop;
- emit structured behaviour across speech, nonverbal, motion, and display
  channels;
- integrate social context over time through a bounded regulation and internal
  motivation layer;
- remain inspectable, testable, and usable by different clients and embodiments.

Conversation is one event source among many. PROMETHEUS must not be designed as
a chatbot-only framework.

## Core Model

### Events and control

- `Event` is the public and internal runtime currency.
- `Agent`, `State`, `OuterState`, `Transition`, guards, actions, policies,
  storage, and event history provide explicit task control.
- States define behavioural regimes; transitions decide when control moves;
  actions update storage or compute domain results.
- Nested `OuterState` control is appropriate when it keeps task structure
  explicit.
- Task goals, commitments, transitions, and final authority belong to the state
  machine.

Do not replace explicit task control with a single prompt loop or let a prompt
silently own state.

### Behaviour

Agent output is persisted as a `resp.behaviour_plan` event containing a
`BehaviourPlan`. Supported top-level channels are:

- `speech`;
- `nonVerbal`;
- `motion`;
- `display`.

Channels are optional and clients render only the modalities they support. Do
not introduce ad-hoc response objects that bypass the behaviour-plan contract.

### Interaction profiles

`AgentInteractionProfile` declares which observation event types an agent
accepts and which behaviour modalities it can emit. Its `externalRealtimeSpeech`
boolean declares compatibility with external voice, defaults to false, and is
explicitly supplied by each definition through `applyDefinitionMetadata`. The
Live gateway also validates reachable state/policy implementations. Profiles are part of the
agent/client contract and must be updated when capabilities change.

### Event families

Current external observation types include user utterances, facial emotion,
human presence, social grouping, rich social context, hand signs, and current
or forecast weather. Their constants and exact names are authoritative in
`model/event/Event.java` and documented for clients in `README.md`.

`obs.social.situation_change` is derived by the backend from social grouping;
it is not a raw client-sensed observation. `sys.tick` is a system event.
`int.regulation.*` events are internal regulation signals.

HTTP event payloads are strings. Structured perception payloads are JSON encoded
inside that string so event history stays inspectable.

## Current Capabilities

PROMETHEUS currently provides:

- persisted explicit state-machine agents with policies, transitions, actions,
  storage, event history, reset, and scheduled evaluation;
- multimodal behaviour generation and behaviour persistence;
- typed inference requests with configurable task-purpose model/effort routes,
  finite provider deadlines, strict structured-result validation and content-free
  correlated latency diagnostics, including a Valerian Interaction Timing drawer
  with browser/server JSON and comparison CSV exports (details in README/results);
- agent interaction profiles and Spring-discovered `AgentDefinition` beans;
- scoped access-code APIs and trusted global APIs, including streamed Speech
  synthesis from exact persisted behaviour-event identity;
- behaviour and monitor SSE streams with heartbeat, cursor, and disconnect
  resilience; behaviour delivery explicitly distinguishes live publication
  from initial/history/reconnect replay without changing persisted payloads;
- an access-code-scoped, typed `gpt-live-transcribe` session contract with
  Arabic, German, and English agent-aware language defaults, strict settings
  validation, and ephemeral browser credentials;
- one shared Valerian/multilateral browser transcription engine with
  cross-tab microphone ownership, explicit local/manual turn commits,
  item-ordered final transcripts, bounded fresh-session reconnect after
  transport or active-track loss, navigation-safe teardown, live device
  refresh, operator capture settings, and privacy-safe preference persistence;
- content-free browser timing for commit send/acknowledgement, first/last
  transcript delta and final receipt, preserving arrival times across ordered
  final release; these are browser observations, not provider-side clocks;
- serialized, duplicate-safe live-transcript ingress through the ordinary
  scoped `obs.user_utterance` / `FULL_PLAN` acknowledgement boundary, with
  canonical behaviour rendering left to the existing SSE stream;
- ordered Valerian Speech synthesis and selected-device playback driven by
  persisted behaviour IDs, with live-delivery deduplication, ordinary replay
  suppression, output enabled only by Start Transcription, explicit latest-assistant
  playback on that start, and pending output invalidated when the session stops,
  ID/envelope replay correlation, per-agent cross-tab output ownership,
  explicit Stop, and microphone gating across output;
- an explicit Valerian cockpit lifecycle in which operational columns stay
  empty until connection, hydrate only from the connected agent, and clear on
  switch, failed connection, disconnect, logout, or re-entry;
- persisted creation-time agent embodiments, with `COCKPIT` selecting the
  Valerian persona and `ROBOT` selecting Gigi; the selected persona is exposed
  in agent metadata and consistently resolves legacy identity references in
  system prompts;
- browser-side facial emotion, social context, hand-sign, and manual weather
  sensing through Valerian;
- deterministic social-situation-change derivation;
- on the `agents` branch, an Arabic-only Aisha vertical slice that bounds one
  structured model call with deterministic catalog candidates and emits
  approved-answer Speech plus semantic gestures;
- a regulation foundation with persisted specifications, snapshot context,
  modulation values, internal events, reset behavior, and tests;
- Valerian Cockpit, Valerian Access Management, the public Talk to Me exact-text
  speech client, API Workbench, and separate multilateral displays.

The current registered main-branch agent catalog is authoritative in
`agentdefs/core`, `agentdefs/usecases/healthcare`, and `README.md`. Do not copy
catalog keys into new context documents.

## Capability Awareness

Definitions opt in through `AgentDefinition.capabilityAwareness()` (default false).
Core definitions opt in explicitly; other definitions, including those reusing
core factories, remain out. The setting persists in the instance interaction
profile. Older saved instances default to false; enabling a definition affects
new instances only. Never backfill or delete existing history implicitly.

A bounded deterministic description of declared input/output channels and their
limits accompanies ordinary behaviour inference and eligible Live guidance. Agent
runtime binding covers creation, nested/state-entry/final behaviour, speculation,
reset and embodiment generation independently of state history selectors. Live
context has no sensory TTL; existing guidance revisions handle state updates.
Shared assemblers stay immutable and existing guard batching remains available.
No initialization event, additional inference, tasks, sensor control or Live
eligibility is introduced. Talk to Me remains deterministic exact-text output.

See README for developer setup and `.agents/CAPABILITY_AWARENESS_RESULTS.md` for
verification and rollout evidence. Capability support does not establish current
sensor input, rendered output or confirmed action completion.

## GPT-Live Experiment

Changes prompted by GPT-Live usage start on `feature/gptlive`, then merge into
`main`, and only then from `main` into `agents` for the Heroku deployment. Preserve
ordinary Text/Continuous contracts and the deployment-only catalog/persona layer.

The database query reduction follow-up (2026-10-01) explicitly starts on `main`
at the user's request. Local verification uses `application-test.properties`.
After review, the user authorized commit/push and normal main-to-feature/gptlive,
then main-to-agents integration, followed by Heroku verification. Pushing agents
triggers deployment. Integration and Heroku v101 verification are complete;
see `.agents/GPTLIVE_RESULTS.md`. This overrides the earlier branch order for
this follow-up.

Live polling combines scoped status and a revision-conditional durable transcript
ledger. Scalar authorization still checks every request; no permission cache is
introduced. Ordinary acknowledge/generate turns retain entities only for one
serialized turn, or up to four sensor observations with individual commits and
failure isolation. Short loading transactions release JDBC before inference;
existing outer transaction owners retain their semantics. Valerian batches one
social sample and uses monitor snapshots for state/storage while the stream is
ready, with ordinary reads as fallback. Sensor cadence, freshness, thresholds,
event ordering and derived social changes remain unchanged. See README and the
query-reduction evidence in `.agents/GPTLIVE_RESULTS.md`.

On `feature/gptlive`, GL-01 adds an experimental, disabled-by-default Live API
session gateway. GL-02 seeds it from an immutable,
bounded active-state context with source IDs/times and explicit sensory freshness.
Eligibility now requires an explicit persisted external-realtime-speech capability
and supported policies throughout the reachable graph. Main conversational
definitions opt in individually; Talk to Me and unadapted application definitions
remain out. Older saved profiles default to no Live support; recreate selected
instances deliberately, without deleting existing history. See README for details.
GL-03 adds provider-neutral speech ownership, non-speech-only ordinary backend
generation and trusted native recording without acknowledgement. State-entry
plans retain narration intent; additive event provenance distinguishes intent
from native realization. Shared conversation projection excludes intent speech
while raw history preserves the original plan. GL-04 adds bounded per-speaker
capture and durable receipt/segment claims. Complete user segments acknowledge
once; ambiguous short answers, late fragments and interrupted input do not act.
Failed/pending claims are retained without automatic replay across restarts.
GL-05 publishes committed state/sensory updates and narration asynchronously;
client delegation reports existing work without a second acknowledgement. Lost
append ACKs fail visibly without blind retry. GL-06 replaces the temporary probe
with a third cockpit tab, shared media leases, device/voice controls and receipt-
reconciled captions/history. Conversation projection is opt-in on behaviour SSE;
the raw contract remains available. GL-07 fences pending input on Stop and scope
revocation, checks browser/sideband liveness, preserves scoped close diagnostics,
and adds content-free Live traces to the timing drawer/JSON. Reset/switch callbacks
cannot rehydrate an obsolete session. GL-08 adds a repeatable isolated-MySQL
acceptance runner with real cockpit controllers, persistence and SSE, synthetic
providers/media, and a feature-disabled text/TTS trial. Full Java, all three Node
suites and focused browser checks passed. Live voice and Windows/Linux built-in
and Bluetooth acoustic trials remain NOT RUN. The local template keeps the flag
off; the production profile now enables it, with environment overrides supported.
It is separate from current
transcription/TTS. Read
`.agents/PLAN_GPTLIVE.md` for the authorized implementation roadmap and
`.agents/GPTLIVE_RESULTS.md` for evidence and pending physical trials. Backend
sideband owns provider credentials; diagnostic state is bounded and excludes
audio/text content. See README for scoped session contracts and configuration.

The dedicated Live Multimodal core definition separates external voice guidance
from embodiment generation through persisted `EmbodimentPolicy`. It accepts all
current observations and exposes all declared behaviour modalities. Backend plans
omit speech even without Live ownership; this agent has no text/TTS speech fallback.
Raw sensor frames update context; user ingress, explicit generation and derived
social changes generate embodiment. Location remains weather-provided context.
Ordinary agents retain their combined speech/nonverbal request path. See README
for setup, additive policy columns and limits; strict silence is not enforced.

During Live, unchanged eligible face/social readings refresh every five seconds
while detectors continue observing. Stopped sensing still expires after 15 seconds;
manual samples and ordinary text/TTS deduplication are unchanged. Live delivery
uses compact same-type replacements, with social facts before derived summaries;
only truly deselected types are withdrawn. Unsent chunks are checked again for
expiry after provider waits. Context workers coalesce commit reads over five
seconds, with freshness/delegation deadlines and a bounded fallback; per-append
ACK waits remain sequential. Durable fragment admission uses scalar scope/epoch checks,
and eager event state paths load together. See README and GPTLIVE_RESULTS for the
query-budget regression evidence and remaining provider/production limits.

On the agents branch, the main conversational definitions, including Generic
Multimodal Behaviour, remain opted in;
all other registered definitions (including scored RPS and Talk to Me) stay out.
The existing per-agent embodiment assembler resolves authored Live voice and
nonverbal prompts to Valerian/Gigi without rewriting observation/dialogue content.
The merged runtime retains both persona selection and external speech ownership,
including reset. The production profile now enables the separate Live flag;
`PROMETHEUS_LIVE_ENABLED=false` can override it without changing agent capabilities.

## Conversational Task Configuration

`GenericMultimodalBehaviour` is a separate core definition with the existing
Multimodal Behaviour observation/output set. Its explicit task phases, transitions
and blocking actions negotiate and persist bounded reaction rules in ordinary
Storage. User-turn inference produces a validated task operation plus a canonical
behaviour plan; sensor guards run locally and call inference only for a qualifying
reaction. Reset clears task configuration. Existing definitions retain their
policies. Live explicitly supports the task state/policy and speaks committed
announcements; current perception is also supplied directly to task-turn inference.
See README and `.agents/GENERIC_MULTIMODAL_RESULTS.md` for bounds, schema additions,
verification and pending physical/provider trials. This work starts on
`feature/gptlive`; the user authorized integration into `main`, then `agents`
and Heroku redeployment. Rollout evidence is maintained in the results document.

## Regulation: Implemented Foundation and Missing System

Regulation is intended to become a bounded internal motivation system alongside
task control. It may integrate multimodal social context over time, shape
expression, and emit explicit opportunities or interrupts. It must never
silently replace task transitions or change agent goals. Safety and explicit
task policy retain authority.

The current implementation is only a foundation:

- Agents default to `NoOpRegulationSystem`; no production agent definition
  currently enables the concrete Zurich system.
- `ZurichRegulationSystem` persists dependency, enterprise, and autonomy
  variables, but its active policies currently react only to system ticks and
  user utterances.
- The current implementation emits affiliation opportunities from a dependency
  threshold. Soft and hard interrupt event constants exist, but no concrete
  production policy emits them.
- A `ModulationBundle` is calculated and retained as the agent's latest
  modulation, but it is not yet consumed by prompt assembly or mapped into a
  `BehaviourPlan`.
- Facial emotion, social context, grouping, situation changes, and other
  multimodal evidence are not yet integrated into concrete regulation policies.
- Motivation arbitration, task-commitment handling, safety precedence, and
  observable regulation diagnostics are not yet complete.

Extend the existing `model/regulation` abstractions and their integration in
`Agent`; do not create a parallel motivation subsystem. Keep updates
deterministic, persisted where required, inspectable, and covered by focused
tests.

## Architecture and Repository Boundaries

### Framework and agent definitions

The `main` branch is the reusable framework and baseline Valerian catalog.
Production definitions are Spring-managed `AgentDefinition` beans under:

- `agentdefs/core`;
- `agentdefs/usecases/healthcare`.

Keep prompts near the package that owns the definition. Main-branch baseline
agents use the Valerian/SIRA Lab framing and must not acquire GIGI, TDSR, Davos,
hotel, robot-tour, venue, experiment, or customer assumptions. Such agents
belong in application modules, deployment repositories, or explicitly chosen
application branches.

Do not reintroduce the removed `basic.*` or `multimodal.*` production catalogs.

The `agents` branch deliberately adds event- and experiment-specific
definitions through `ApplicationAgentDefinitionConfiguration`. Keep those
application packages separate from the reusable main catalog, preserve their
existing package/key families, and use the current catalog table in `README.md`
instead of copying a second key list into this context file.

### Application and HTTP layers

Keep controllers focused on transport validation and delegation. Put reusable
semantics in model or application packages. Preserve the event, behaviour-plan,
interaction-profile, storage, and event-history contracts.

Published scoped client contracts include agent creation and info, including
the persisted `COCKPIT`/`ROBOT` embodiment and derived persona name,
`acknowledge`, explicit behaviour generation, behaviour SSE, monitor SSE,
typed live-transcription capabilities/session issuance, and Speech synthesis
from persisted behaviour identity. Exact paths and request shapes are maintained in
`README.md` and exercised by controller contract tests. Browser SSE may use an
access code query parameter because `EventSource` cannot set custom headers;
other clients should prefer the access-code header.

When an observation or behaviour contract changes, update its event/profile
definition, prompt adapter or renderer, relevant client exposure, contract
tests, and README documentation together.

The shared `application.yaml` disables Open EntityManager in View. Scoped creation generates
before its save transaction, then revalidates access and commits the agent/link
atomically; initial publications follow commit. HTTP streams
must not retain persistence contexts or JDBC connections; load the required data
within bounded database work. Preserve eager agent loading and persisted event
identity. Classroom concurrency evidence is in `.agents/CONNECTION_LIFECYCLE_RESULTS.md`.

### Clients and auxiliary sites

Valerian is a cockpit and demo client, not framework core. Keep its browser
logic under `src/main/resources/public/valerian` or `valerian-admin` and consume
public backend contracts rather than importing client assumptions into the
model. Talk to Me is a reduced public client under
`src/main/resources/public/talktome`; it uses explicit access-code-scoped agent
lifecycle and backend-authoritative output-only OpenAI Speech synthesis without
microphone capture or browser-authored speech responses. Its synthesis endpoint,
application service, and agent-tag policy remain Talk-to-Me-specific and reject
agents without the `utility.talk_to_me` profile tag before acknowledgement.
Provider configuration, voice/speed validation, and streamed audio response
mechanics are shared with the scoped canonical behaviour-speech endpoint.
Valerian and the multilateral listener share the transcription-first browser
transport and serialized scoped `FULL_PLAN` ingress. Valerian adds a separate
live-behaviour Speech playback queue; acknowledgement and replay are visual-only,
and input is gated while the selected output device renders agent speech. Its
sensing, interaction, and behaviour columns are connection-scoped: access,
selection, creation, disconnect, and logout do not retain agent-derived UI
state, while connection hydrates the selected agent's persisted history.
Transcription is intentionally not diarization: sequential voices share the
same user-input channel without speaker identity, and overlapping speech may be
merged, split, or missed. Real-room, wireless, Bluetooth, background-throttling,
and acoustic performance remain deployment smoke-test concerns even when the
mocked browser resilience matrix passes.

API Workbench is a developer client for the same contracts. Multilateral pages
are separate meeting/listening displays. The `.web/` SIRA page and participation
site are standalone web deployments; they share this repository but are not
part of the Spring runtime or agent framework architecture.

## Maturity and Compatibility Policy

PROMETHEUS remains prototype-oriented internally. Prefer a clear replacement
and remove obsolete internal code instead of layering compatibility adapters,
unless the milestone explicitly requires compatibility.

Published HTTP/SSE contracts, persisted data, and external client behavior are
real boundaries. Do not break them incidentally under the clean-slate rule.
Scope contract changes explicitly, update all consumers and documentation, and
prefer clear reset instructions over complex migrations only when the affected
deployment is confirmed to be prototype data.

## Where to Look

- `model`: agents, states, events, policies, behaviour, regulation, social
  derivation, and domain rules.
- `application`: runtime orchestration, scoped access, transcription, Speech,
  and publishing.
- `controllers`: HTTP/SSE boundaries and request/response contracts.
- `agentdefs`: current production definitions and their prompts.
- `src/main/resources/public`: Valerian, access management, Talk to Me, API
  Workbench, and multilateral clients.
- `.web`: standalone public and participation sites.
- `src/test/java`: unit, integration, prompt, profile, controller, and static
  resource contracts.
- `tests/playwright`: critical browser journeys and visual behavior.
- `README.md`: current setup, catalog, API, client, and deployment reference.
- top of `PROJECT.md`: current implementation status and latest milestone.
- historical part of `PROJECT.md`: search on demand; never treat an old
  milestone as current behavior without checking code.

Representative high-value test anchors include registry/profile and package
prompt contracts for agent changes; Web MVC and scoped integration contracts for
API changes; static resource plus Playwright checks for browser changes; domain
unit tests for RPS and social derivation; and
`AgentRegulationIntegrationUnitTest`, `RegulationSystemPersistenceUnitTest`, and
`ZurichRegulationSystemUnitTest` for regulation work. Always inspect neighboring
tests for the actual task rather than relying only on this list.

## Do Not

- Treat PROMETHEUS as a speech-only or chatbot-only framework.
- Hide task control, commitments, or regulation authority inside prompts.
- Bypass `Event`, `BehaviourPlan`, or `AgentInteractionProfile` contracts.
- Put Valerian-only behavior into framework core.
- Add deployment-specific agents to main without an explicit architectural
  decision.
- Build a second social motivation or regulation abstraction beside the current
  one.
- Use the complete milestone history as the current specification.

### Need for Speed speech delivery

Valerian's canonical event-ID Speech playback selects bounded PCM AudioWorklet
delivery after browser/device preparation, or progressive MP3 MediaSource delivery
with a same-response buffered fallback. The Continuous output settings expose
Automatic/MP3 for comparison; existing clients default to MP3 unless they request PCM.
The shared queue still owns ordering, leases and input gating. The server flushes
stream chunks; the persisted BehaviourPlan remains the authority for spoken text.
Timing exports label the format, preparation, buffer interruptions and distinct
PCM-renderer versus media-element playback markers; neither is physical audibility.
PCM JSON detail also records bounded, content-free body reads, backpressure,
numbered block delivery and renderer starvation/resume positions, with separate
browser/audio clocks and explicit truncation. The existing timing export captures
it automatically; buffer policy is unchanged. README documents interpretation.
Tracked live speech also captures server provider-body reads and output
write/flush/close durations, correlated by synthesis ID and cumulative byte counts.
A scoped, bounded, short-lived in-memory trace is fetched after download/teardown
into the same JSON export without delaying playback. Server clocks stay separate;
read timings cannot isolate provider compute from upstream transport, and flush
completion cannot establish browser receipt. No durable diagnostic jobs are used.
Live performance and device/proxy evidence are tracked separately in
.agents/NEEDFORSPEED_RESULTS.md.

### Need for Speed inference execution

Typed inference purpose and route configuration live at the policy/SPI boundary.
All PromptPolicy generation, including final-state speech-only output, requests
a JSON BehaviourPlan. Speech-only output needs only the speech field; deterministic
policies continue constructing canonical plans without inference.
Speech and nonverbal prompt output can share one request using compact provider
JSON that is expanded into the canonical BehaviourPlan before publication.
Custom/partial nonverbal objects retain their fields; no compact keys enter the
public or persisted plan contract. Eligible pure guard
requests can be combined or evaluated through an opt-in bounded executor. The
state machine still consumes results in priority order and owns all actions.
Workers never receive entities or persistence contexts. The application serializes
runtime mutations per agent inside one process; scheduled ticks and scoped
deletion use that same boundary. See README for limits and multi-instance scope.

Transition actions default to bounded in-memory background execution in the
application host, with explicit blocking dependencies. Workers consume immutable
prepared work; completion updates fresh storage under the agent lock after commit.
Reset epochs and storage write tokens reject obsolete/conflicting results. Jobs
do not survive restart; completion/failure is correlated in server logs. Legacy
summary/outcome actions migrate on reload; other unconfigured legacy actions keep
their ordering. Embedded runtimes without an execution host remain synchronous.

Eligible user-utterance behaviour can overlap transition decisions by default.
An application-owned bounded, expiring cache bridges acknowledge/generate reloads;
workers only receive immutable inference requests. No transition and matching
committed input, epoch, effective prompt and route are required for reuse. All
transitions invalidate, including self-loops; fresh generation never waits for
discarded work. No-op regulation and known pure state/policy/assembler/guard paths
are required; custom and sensory paths remain sequential. Cancellation can leave
billed work running. Timing exports mark cross-request speculative evidence;
completed late/discarded work may only be visible in server logs. README documents
limits and the configuration switch for sequential comparisons.

New persisted events carry a nullable internal history position so reloading
timestamp ties preserves append order. Legacy rows retain their timestamp order
without backfill; public IDs/payloads/timestamps are unchanged. The additive
MySQL column and coordinated writer upgrade are documented in README.
