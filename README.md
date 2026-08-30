# PROMETHEUS

PROMETHEUS is an event-driven Java framework for building multimodal digital
agents with explicit state-machine control, a developing regulation layer, and
structured behaviour output.

## Why

Many agent systems still treat interaction as turn-based chat: a user says
something and the agent replies. That is too narrow for digital agents that must
work with voice, facial expression, hand signs, group context, weather, and
other signals while deciding when to speak, gesture, stay silent, or yield.

PROMETHEUS focuses on the mapping problem behind multimodal agents: how sensed
events from humans and environments become inspectable agent state, and how that
state becomes coordinated speech, nonverbal behaviour, motion intent, or display
output.

## What

PROMETHEUS provides an engineering framework for digital agents that may remain
screen-based, be embodied as VR avatars, or be connected to physical robots.
Agents are explicit state machines, not opaque chat loops. Their task logic is
implemented with states, transitions, guards, actions, prompts, and storage.

The framework treats multimodality as a first-class contract:

- Perception clients publish observations as `Event` objects.
- Agents declare accepted observations and emitted behaviour modalities through
  an `AgentInteractionProfile`.
- Agent responses are persisted as `BehaviourPlan` events with `speech`,
  `nonVerbal`, `motion`, and `display` channels.
- Streaming clients subscribe to behaviour events and render the channels that
  their target avatar, robot, or UI can support.

## How

Every runtime input is normalized as an event. A user utterance, facial emotion
sample, social grouping report, hand sign, weather context, system tick, and
internal regulation signal use the same event pipeline. The current state
decides whether the event changes control flow, updates storage, or triggers a
new behaviour plan.

The regulation foundation runs alongside task control, can maintain persisted
variables, and can emit internal opportunities back through the state machine.
Direct multimodal motivation, arbitration, and modulation of generated
behaviour are not complete yet. The state machine therefore remains explicit
and authoritative while the regulation layer develops.

## Bundled Clients

### Valerian Designer

URL: `http://localhost:8080/valerian-design/`

Valerian Designer is the administrative authoring surface for declarative agent
definitions. Enter the existing PROMETHEUS admin token to browse the revision
catalog and open the guided Purpose, Sensing, Behaviour, Reactions, State flow,
and Review workflow. The current foundation supplies catalog and editor routing;
the following designer milestones add the authoring controls and publication
lifecycle.

### Valerian Access Management

URL: `http://localhost:8080/valerian-admin/`

![Valerian access management](.doc/figures/Valerian/valerian-cockpit-admin.png)

Use this client to create new access codes and assign the agent types made
available in the scope of each access code. A valid access code must be entered
before using Valerian Cockpit.

### Valerian Cockpit

URL: `http://localhost:8080/valerian/`

Valerian Cockpit is the primary all-in-one client for trying the core agents
that ship with PROMETHEUS. After entering an access code, open the drawer on
the right with the heartbeat button, choose an agent type, create an instance,
connect to it, and reset or delete it when needed. The drawer's diagnostics tab
shows runtime events and agent state.

The cockpit is organised into three columns: sensing, verbal interaction by
text or speech, and behaviour. Each column can be maximised or opened in a
separate window when an experiment needs more screen space.

Speech interaction is transcription-first: Valerian commits explicit browser
audio turns to `gpt-live-transcribe`, sends only finalized transcripts through
the ordinary scoped acknowledgement boundary, and synthesizes speech from the
resulting persisted behaviour event. New `behaviour-live` deliveries can
produce audio; ordinary history and reconnect replay remain visual-only. When
an operator starts transcription, Valerian also requests and speaks the latest
eligible assistant utterance in the agent's current state before opening
microphone input. While speech is loading or playing, microphone input is
disabled and its pending provider state is cleared so the agent cannot
transcribe itself. **Stop Playback** cancels queued/current audio and reopens
input without changing the persisted plan. A per-agent browser lease selects
one audible Valerian window, and playback uses the speaker, voice, and speed
selected in the speech settings.

#### Cockpit lifecycle contract

The sensing, interaction, and behaviour columns represent the currently
connected agent only. An empty column contains no agent-derived history,
starter message, sensing value, or behaviour value; operator lifecycle notices
belong in diagnostics rather than the conversation.

| Lifecycle state or action | Cockpit columns |
| --- | --- |
| Access screen, accepted access code, selected agent type, or newly created instance | Empty. Selecting or creating an instance does not imply a connection. |
| Connect to a new instance | Cleared first, then initialized from that agent's current starter behaviour and subsequent events. |
| Connect to a previously used instance | Cleared first, then hydrated in persisted order with its conversation and the latest facial, social, hand-sign, weather, and behaviour values. |
| Switch instance, failed connection, or disconnect | Empty. Disconnect retains the selected instance so it can be reconnected explicitly. |
| Reset a connected instance | Cleared first, then initialized from the reset agent's new starter behaviour. |
| Log out and access the cockpit again | Empty, with access, selection, diagnostics, and agent URL identity removed. |

An explicit `agentId`/`agent` URL is the intentional exception: it identifies a
specific agent for direct-link and detached-column use and therefore attempts
that connection on load.

#### Social Context Sensitivity

![Valerian social context sensing](.doc/figures/Valerian/valerian-cockpit-social.png)

This core agent demonstrates social-context sensing.

- Sensing: visual detection of human presence, groups of humans, and whether
  people are attentive toward the agent.
- Interaction: the agent comments on the social situation; users can also enter
  utterances or use transcription-first speech interaction.
- Behaviour: the full behaviour spectrum, including speech, gesture, facial
  expression, gaze, motion, hand signs, and display output where supported.

#### Facial Expression Sensitivity

![Valerian facial expression sensing](.doc/figures/Valerian/valerian-cockpit-facial.png)

This core agent demonstrates facial-expression sensing.

- Sensing: visual detection of faces and emotion, valence, and arousal.
- Interaction: the agent comments on the social situation; users can also enter
  utterances or use transcription-first speech interaction.
- Behaviour: the full behaviour spectrum, including speech, gesture, facial
  expression, gaze, motion, hand signs, and display output where supported.

#### Rock-Scissor-Paper

![Valerian rock-scissor-paper hand-sign interaction](.doc/figures/Valerian/valerian-cockpit-rsp.png)

This core agent demonstrates a hand-sign game loop.

- Sensing: visual detection of the user's hand sign: rock, scissor, or paper.
- Interaction: ready, draw a sign, and receive the evaluation of who won; text-
  and speech-based interaction remain available.
- Behaviour: the full behaviour spectrum, plus the additional hand sign drawn
  by the agent.

### Talk to Me

URL: `http://localhost:8080/public/talktome`

Talk to Me is a reduced, public-facing Valerian-style output-only speech client.
An administrator first assigns `core.talk_to_me` to an access code. The user
then enters that code and explicitly creates, selects, and deletes their own
scoped speech instances.

Enter up to 2,000 Unicode code points and choose **Speak** to persist and speak
that exact text without language-model rewriting. The client exposes the OpenAI
Speech voices, output speed, browser speaker selection, and speaker refresh;
`alloy` is the fresh-user voice default while an explicitly saved choice is
retained. Voice and speed are locked only while a synthesis/playback request is
active. Speaker selection remains available and applies immediately. The client
does not request microphone access.

The supplied sample is loaded into the speech field on every page load. The
icon controls above the field restore that sample or clear the field; they
become available with the textarea after selecting an instance.

The browser sends one scoped speech request. PROMETHEUS first persists the
canonical `BehaviourPlan`, then sends that plan's unchanged speech channel to
OpenAI's output-only Speech API. The browser buffers the returned MP3 before
playback and reports completion only when the audio element emits `ended`.
**Stop** aborts an in-flight request or stops current playback. The
2,000-code-point boundary remains an application policy.

### API Workbench

URL: `http://localhost:8080/apiworkbench/`

![PROMETHEUS API Workbench](.doc/figures/Valerian/api-workbench.png)

The API Workbench is a guided developer client for learning and testing the
PROMETHEUS REST and SSE API. Start with the lifecycle column: open a scoped
session, list allowed agent definitions, create or select an agent, inspect its
interaction profile, subscribe to behaviour or monitor streams, and publish
observation events. The endpoint catalog can also be used directly to inspect
resolved URLs, path variables, headers, query parameters, JSON request bodies,
copyable `fetch`, `curl`, and `EventSource` snippets, HTTP responses, SSE
events, and the active agent profile.

### Multilateral Displays

URLs:

- Listener display: `http://localhost:8080/multilateral/listen/`
- Reports display: `http://localhost:8080/multilateral/reports/`

The multilateral screens are separate meeting/group displays. They are not part
of the Valerian cockpit workflow, but remain bundled for situations where a
larger audience should see live listening state or generated meeting reports.

## Current Agent Catalog

Production agent definitions are canonical JSON revisions stored in the
database. The twelve revision-1 seeds live under
`src/main/resources/agent-definitions/catalog/main`; startup imports missing
identities idempotently, activates the initial published revision, and prewarms
the immutable compiled runtime cache. New instances resolve the active revision
once and retain that revision pin. Talk to Me exact text and RPS rules/output
use trusted registered deterministic component kinds; JSON never names Java
classes or Spring beans.

The main branch ships the Valerian baseline catalog:

| Key | Purpose |
| --- | --- |
| `core.facial_expression_sensitivity` | Core demo for facial-expression observations. |
| `core.multimodal_behaviour` | Core demo for coordinated multimodal output. |
| `core.rock_scissor_paper` | Core hand-sign rock-scissor-paper demo. |
| `core.role_clarification_guessing_game` | Core guessing game focused on agent/user role clarity. |
| `core.social_context_sensitivity` | Core demo for social grouping and rich social context. |
| `core.talk_to_me` | Deterministic exact-text output-only speech utility. |
| `usecases.healthcare.guessing_game` | Healthcare guessing game where Valerian guesses. |
| `usecases.healthcare.guessing_game_user_guess` | Healthcare guessing game where the user guesses. |
| `usecases.healthcare.healthcare_conversation` | Open healthcare conversation use case. |
| `usecases.healthcare.smart_goal_coaching` | Healthcare SMART-goal coaching use case. |
| `usecases.healthcare.therapy_appointment_reminder` | Single-state therapy appointment reminder. |
| `usecases.healthcare.therapy_appointment_reminder_intro` | Two-state therapy appointment reminder with introduction. |

Event- or experiment-specific agents should live in application branches,
separate modules, or deployment-specific code rather than being added to the
main baseline catalog.

## Requirements

- Java 21 or newer.
- MySQL.
- Maven Wrapper from this repository.
- Maven downloads the pinned Node.js/npm toolchain used to build and test
  Valerian Designer. A compatible system Node.js is needed only for direct
  frontend commands and Playwright tests.
- OpenAI or Azure OpenAI configuration for prompt generation, live
  transcription, and output-only Speech synthesis.

## Local Setup

Copy the template files and adjust them for your machine:

```powershell
Copy-Item src/main/resources/application.properties.template src/main/resources/application.properties
Copy-Item src/main/resources/openai.properties.template src/main/resources/openai.properties
```

Minimum configuration:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `openai.openaivsazureopenai`
- `openai.url`
- `openai.key`
- `openai.liveTranscriptionClientSecretUrl` and
  `openai.liveTranscriptionWebRtcUrl` only when overriding the standard OpenAI
  live-transcription endpoints
- `openai.liveTranscriptionSafetyIdentifier` when a stable,
  privacy-preserving provider safety identifier is required
- `prometheus.speech.model` and `prometheus.speech.url` when overriding the
  shared output-only Speech synthesis defaults
- `prometheus.admin.token` for Valerian Access Management

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

PROMETHEUS uses source-controlled Flyway migrations and Hibernate schema
validation. On startup, an existing pre-Flyway schema is baselined at version
0, version 1 creates definition/revision/lightweight-instance aggregates, and
version 2 performs the explicit runtime cutover. Version 2 preserves access-code
identities, allowed type keys, and declarative revisions/instances, but removes
the disposable legacy copied runtime graphs and their access-code links. The
twelve bundled JSON revisions are then imported idempotently; existing active
revisions are preserved and prewarmed before readiness. Never use Flyway
`clean` against the configured database. Review
`.agents/designer/DATABASE_TRANSITION.md` before upgrading an existing schema.

The default local URL is `http://localhost:8080`; it redirects to Valerian
Cockpit.

Open the main surfaces:

- Valerian Cockpit: `http://localhost:8080/valerian/`
- Valerian Designer: `http://localhost:8080/valerian-design/`
- Valerian Access Management: `http://localhost:8080/valerian-admin/`
- Talk to Me: `http://localhost:8080/public/talktome`
- API Workbench: `http://localhost:8080/apiworkbench/`
- Multilateral listener: `http://localhost:8080/multilateral/listen/`
- Multilateral reports: `http://localhost:8080/multilateral/reports/`

## Testing

Run the Java regression suite:

```powershell
.\mvnw.cmd test
```

The suite uses deterministic language-model fakes and disposable H2 databases
in MySQL mode for Flyway, native-JSON persistence, and Hibernate-validation
contracts. It does not call OpenAI, Azure, Speech, transcription, or browser
sensors. The destructive real-MySQL smoke is excluded from ordinary test runs.

The ordinary Maven lifecycle also installs the pinned frontend toolchain and
runs the Valerian Designer type check, focused tests, and production build. To
run that verification directly with a compatible local Node.js installation:

```powershell
npm ci --ignore-scripts
npm run designer:verify
```

The generated designer bundle is written to `target/generated-resources` and is
never edited or committed as source.

Run it only against an explicitly named dedicated local schema:

```powershell
$env:PROMETHEUS_DESIGNER_DB_SMOKE='true'
$env:PROMETHEUS_DESIGNER_DB_SMOKE_SCHEMA='prometheus_designer_smoke_local'
.\mvnw.cmd -Plocal-db-smoke "-Dtest=LocalMysqlSmokeTest" test
```

The smoke reads server credentials internally from the uncommitted local
`application.properties`, refuses a schema without the
`prometheus_designer_smoke_` prefix or one matching the normal configured
database, and creates/drops only that verified schema. It seeds a legacy graph
and preserved access-code assignment, runs Flyway plus Hibernate validation,
restarts Spring, and verifies all eight migration/lifecycle/runtime assertions
from `.agents/designer/TESTING.md`. Never point it at the normal application
database.

Run JavaScript syntax checks for the bundled clients:

```powershell
node --check src/main/resources/public/valerian/script.js
node --check src/main/resources/public/apiworkbench/script.js
node --check src/main/resources/public/talktome/script.js
node --check tests/playwright/valerian-column-expansion.spec.mjs
node --check tests/playwright/apiworkbench.spec.mjs
node --check tests/playwright/talktome.spec.mjs
```

Run the Playwright visual smoke tests:

```powershell
npm install
npx playwright install chromium
npm run test:valerian:visual
npm run test:apiworkbench:visual
npm run test:talktome:visual
```

The Valerian Playwright test starts or reuses `http://127.0.0.1:8080`, creates
or re-enables access code `VX102` through the admin API, and checks the facial
expression report, social context report, and behaviour board. The API
Workbench Playwright test uses deterministic mocked API responses to verify the
guided lifecycle, snippets, request execution, and SSE viewer. Set
`PROMETHEUS_ADMIN_TOKEN` when your local `prometheus.admin.token` differs from
the test default. Set `PROMETHEUS_SKIP_WEBSERVER=true` when the app is already
running.

The Talk to Me Playwright test uses the running Spring application and its
configured test database for access-code assignment, scoped agent lifecycle,
exact event/behaviour persistence, synthesis request mapping, audio completion,
Stop, and deletion. It replaces only the external OpenAI Speech and physical
speaker boundary with deterministic browser fakes, then checks the light
desktop and dark mobile layouts. It uses access code `TTM31` and the same
admin-token environment override.

## Connecting External Clients

External clients usually use the scoped demo API. It keeps agent instances
behind an access code and mirrors what the Valerian cockpit does. Trusted
backend tools can use the global agent endpoints shown later.

### 1. Open a scoped session

Create an access code and assign agent types in Valerian Access Management, or
use the admin API. Then validate the code:

```http
POST /demo/session
Content-Type: application/json

{
  "accessCode": "VX102"
}
```

The response contains the enabled agent types and existing scoped agents.

### 2. Create or select an agent

List agent types available to the code:

```http
GET /demo/agent-types
X-Prometheus-Access-Code: VX102
```

Create an assigned agent type:

```http
POST /demo/agents
X-Prometheus-Access-Code: VX102
Content-Type: application/json

{
  "agentDefinitionKey": "core.social_context_sensitivity"
}
```

Read the agent metadata before enabling perception or rendering controls:

```http
GET /demo/agents/{agentId}/info
X-Prometheus-Access-Code: VX102
```

Relevant response fields:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "Valerian Core social context sensitivity",
  "description": "English Valerian Core agent for social-context sensing.",
  "active": true,
  "languageCode": "en",
  "interactionProfile": {
    "supportedObservations": [
      "obs.user_utterance",
      "obs.human.presence",
      "obs.social.grouping",
      "obs.social.context"
    ],
    "supportedBehaviourModalities": [
      "speech",
      "nonVerbal.gesture",
      "nonVerbal.facialExpression",
      "nonVerbal.gaze",
      "nonVerbal.motion"
    ],
    "profileTags": []
  }
}
```

Clients should use `supportedObservations` to decide which sensing UI or sensors
to enable, and `supportedBehaviourModalities` to decide which behaviour channels
to render. An empty profile means "unknown"; fall back conservatively.

### 3. Publish perception events

Send observations to the agent with `acknowledge`:

```http
POST /demo/agents/{agentId}/acknowledge
X-Prometheus-Access-Code: VX102
Content-Type: application/json

{
  "type": "obs.user_utterance",
  "actor": "user",
  "kind": "observation",
  "payload": "What do you notice about this group?"
}
```

`payload` is always a string. For structured observations, encode the structured
payload as JSON inside the string:

```json
{
  "type": "obs.hand.sign",
  "actor": "user",
  "kind": "observation",
  "payload": "{\"source\":\"external.camera\",\"hand\":\"right\",\"sign\":\"rock\",\"confidence\":0.93,\"detectionMode\":\"client_camera\",\"ts\":\"2026-07-08T09:00:00Z\"}"
}
```

The response is:

```json
{
  "responseEvent": {
    "type": "resp.behaviour_plan",
    "actor": "assistant",
    "kind": "response",
    "payload": "{\"speech\":\"I saw rock. I will reveal mine now.\",\"motion\":{\"handSign\":\"paper\"}}",
    "createdDate": "2026-07-08T09:00:01Z",
    "statePath": ["Valerian Core RPS Reveal Sign"]
  },
  "active": true
}
```

`responseEvent` may be `null` when the event updates context but does not trigger
new behaviour.

Supported observation event types in the current public contract:

| Event type | Actor | Payload |
| --- | --- | --- |
| `obs.user_utterance` | `user` | Plain utterance text. |
| `obs.emotion.face` | `user` | JSON string with `emotion`, `confidence`, `valence`, `arousal`, optional `expressions`, source, and timestamp. |
| `obs.human.presence` | `user` | JSON string with aggregate human/tracked counts and source. |
| `obs.social.grouping` | `user` | JSON string with group count, singleton count, largest group size, groups, and source. |
| `obs.social.context` | `user` | JSON string with `schemaVersion: 1`, aggregate group fields, and per-person movement/attention fields. |
| `obs.hand.sign` | `user` | JSON string with `sign` as `rock`, `scissor`, or `paper`, plus confidence/source fields. |
| `obs.weather.current` | `system` | JSON string with location, condition, intensity, wind, temperature, precipitation, and timestamp. |
| `obs.weather.forecast` | `system` | JSON string with location and `days[]` forecast entries. |

Use only observations declared by the agent profile unless you are deliberately
testing fallback behaviour.

### 4. Subscribe to behaviour

Behaviour clients subscribe with Server-Sent Events:

```http
GET /demo/agents/{agentId}/behaviour/stream?accessCode=VX102
Accept: text/event-stream
```

Browser `EventSource` cannot set custom headers, so scoped browser clients pass
`accessCode` as a query parameter. Non-browser clients may use the
`X-Prometheus-Access-Code` header instead.

Each behaviour event has:

- SSE event name: `behaviour-live` for a new publication or
  `behaviour-replay` for initial/history/reconnect recovery
- SSE id: persisted event id when available
- SSE data: an `Event` object with type `resp.behaviour_plan`

The event `payload` is a JSON string containing a `BehaviourPlan`:

```json
{
  "speech": "That looks like a small group.",
  "nonVerbal": {
    "gesture": "ACKNOWLEDGE",
    "facialExpression": { "type": "attentive", "intensity": 0.55 },
    "gaze": { "direction": "toward_group", "focus": "group" },
    "motion": { "stillness": 0.75, "energy": 0.35 }
  },
  "motion": {
    "handSign": "paper"
  },
  "display": {
    "title": "Social context",
    "summary": "Two people nearby"
  }
}
```

Clients should ignore channels they cannot render. Reconnect with either the
standard `Last-Event-ID` header or `?lastEventId=<id>` to replay missed
behaviour events. Replayed events retain their original persisted IDs, data,
and order, but are labeled `behaviour-replay`; clients must not repeat live-only
effects such as speech playback for them. Heartbeats remain SSE comments.
Valerian correlates both persisted event IDs and stable event-envelope
fingerprints so an initial-history response followed by an SSE replay renders
the same behaviour only once.

### 5. Request generated behaviour without a new perception event

Clients can ask the current state to generate another complete behaviour plan:

```http
POST /demo/agents/{agentId}/behaviour/generate
X-Prometheus-Access-Code: VX102
Content-Type: application/json

{
  "outputProfile": "FULL_PLAN",
  "omitModalities": ["display"]
}
```

`FULL_PLAN` is the only output profile. The profile can be omitted because it is
also the default. `omitModalities` remains available when a renderer explicitly
cannot consume one of the otherwise supported behaviour channels.

The endpoint returns `200` when behaviour was generated, `409` when no behaviour
was produced, `404` when the agent is missing, and `400` for an unknown profile.

### 6. Monitor state and storage

Use these endpoints for diagnostics and operator UI:

```http
GET /demo/agents/{agentId}/state
GET /demo/agents/{agentId}/states
GET /demo/agents/{agentId}/storage
GET /demo/agents/{agentId}/eventhistory
GET /demo/agents/{agentId}/monitor/stream?accessCode=VX102
```

The monitor SSE stream emits `snapshot` events with current state, inner state
chain, active flag, known states, and storage entries.

## Global Agent API

The global API is useful for trusted tools, tests, or internal services. It is
not access-code scoped.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/agent` | List persisted agents. |
| `GET` | `/{agentId}/info` | Agent metadata and interaction profile. |
| `POST` | `/{agentId}/start` | Start the current state. |
| `DELETE` | `/{agentId}/reset` | Reset the agent. |
| `POST` | `/{agentId}/acknowledge` | Publish an event. |
| `GET` | `/{agentId}/prompt?profile=...` | Inspect the prompt contract. |
| `POST` | `/{agentId}/behaviour/generate` | Generate behaviour from current state. |
| `GET` | `/{agentId}/behaviour/stream` | Subscribe to behaviour SSE. |
| `GET` | `/{agentId}/monitor/stream` | Subscribe to monitor SSE. |

The request and response shapes are the same as the scoped demo API, without the
access-code header. Where a `profile` or `outputProfile` is accepted, omit it or
use `full_plan`; former speech/complement profiles are not supported.

## Transcription-First Speech

### Scoped live transcription

The transcription-first speech architecture uses an access-code-scoped,
transcription-only session contract. Read its agent-language-aware settings
descriptor first:

```http
GET /demo/agents/{agentId}/transcription/capabilities
X-Prometheus-Access-Code: VX102
```

Then issue an ephemeral `gpt-live-transcribe` WebRTC session:

```http
POST /demo/agents/{agentId}/transcription/session
X-Prometheus-Access-Code: VX102
Content-Type: application/json

{
  "turnDetection": {
    "type": "local_vad",
    "silenceDurationSeconds": 1.5
  },
  "noiseReduction": "far_field",
  "transcriptionPrompt": "محادثة مع وكيل بروميثيوس.",
  "transcriptionKeywords": ["بروميثيوس", "عائشة"],
  "languages": ["ar"],
  "transcriptionDelay": "medium"
}
```

Supported noise-reduction values are `near_field`, `far_field`, and `off`.
Supported turn modes are `local_vad` and `manual`; both keep provider turn
detection disabled so the browser commits explicit audio turns. Supported
languages are currently `ar`, `de`, and `en`, and delay accepts `minimal`, `low`,
`medium`, `high`, or `xhigh`. Omitted settings use far-field capture, local VAD
with 1.5 seconds of silence, the selected agent's language, and medium delay.

The response contains the ephemeral client secret, fixed model and session
type, settings schema version, OpenAI WebRTC URL, and a non-sensitive effective
settings summary. The prompt text and keywords are never echoed in that
summary. There is no combined speech-to-speech session or unscoped
transcription-session endpoint.

### Shared browser transcription engine

Valerian and `/multilateral/listen` use the same ES-module engine under
`public/transcription`. It acquires a cross-tab microphone lease, applies the
requested browser capture constraints, creates only a transcription WebRTC
data channel, commits local-VAD or manual turns, orders terminal transcripts by
provider item ID, and reconnects with a fresh scoped ephemeral secret. Partial
transcripts are display-only; a provider assistant response or remote media
track is reported as an unexpected diagnostic and is never rendered.

The operator panel exposes provider noise reduction, local/manual turn mode,
silence duration, context, keywords, expected languages, transcription delay,
input device, echo cancellation, noise suppression, automatic gain control,
and voice isolation when supported. The default group profile is far-field,
local VAD with 1.5 seconds silence, the agent language, medium delay, browser
echo/noise/gain processing enabled, and voice isolation disabled. Requested and
browser-applied capture values are displayed separately. Context and keywords
are intentionally not stored in local storage.

Run its deterministic browser gates with:

```powershell
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
npm.cmd run test:valerian:transcription
npm.cmd run test:valerian:visual
```

These suites mock microphone, WebRTC, SDP exchange, and provider events. They
do not replace the real acoustic matrix in
`.agents/TRANSCRIBE_SMOKE_RESULTS.md`.

Final provider transcripts enter PROMETHEUS through the same scoped event
boundary as typed input:

```http
POST /demo/agents/{agentId}/acknowledge?profile=full_plan
X-Prometheus-Access-Code: VX102
Content-Type: application/json

{"type":"obs.user_utterance","actor":"user","kind":"observation","payload":"Hello Valerian"}
```

The shared browser ingress serializes final turns, suppresses duplicate/stale
provider terminals, and exposes queued, sending, accepted, rejected, and
provider-error diagnostics. Partial or failed provider input is never sent.
The acknowledgement response is used only for lifecycle/fallback decisions;
canonical `resp.behaviour_plan` rendering remains driven by the behaviour SSE
stream so the same plan cannot appear twice. If acknowledgement legitimately
returns no response event, the client preserves typed-input semantics by
requesting one normal `full_plan` generation.

Valerian's output queue accepts `behaviour-live` events with non-empty speech
and a persisted SSE event ID. It processes those IDs in order and keeps
completed, failed, and deliberately skipped IDs distinct, so duplicate live
delivery and ordinary history/reconnect replay cannot speak twice. An explicit
transcription start may enqueue the current state's latest eligible assistant
event again; this intentional resume delivery is repeatable on later starts and
still synthesizes only the persisted plan. Synthesis begins through the
canonical event-scoped endpoint below, and playback is routed to the selected
output device. The microphone remains gated across a queued burst and opens
only after resume playback has been attempted; it reopens after completion,
Stop, synthesis/playback error, disconnect, or agent change. An expiring
cross-tab output lease ensures that only one Valerian page speaks a particular
agent's event.

### Output-only Speech

Any scoped client can request audio for the canonical speech in an already
persisted behaviour-plan event:

```http
GET /demo/agents/{agentId}/behaviours/latest/speech
X-Prometheus-Access-Code: VX102
```

This discovery endpoint returns `{ "eventId": "..." }` only when the latest
utterance in the agent's current-state history is an assistant behaviour plan
with speech. A later user utterance makes it return `204`, preventing stale
assistant speech from being replayed while a response is still pending. It
does not synthesize audio or mutate the agent.

Use the returned persisted identity with the canonical synthesis endpoint:

```http
POST /demo/agents/{agentId}/behaviours/{eventId}/speech?voice=cedar&speed=1.25
X-Prometheus-Access-Code: VX102
```

`eventId` is the UUID delivered as the behaviour SSE event ID. PROMETHEUS looks
up that event only in the scoped agent's history, requires a
`resp.behaviour_plan` with non-empty speech, and sends its exact persisted
speech to the provider. This endpoint has no request body and therefore cannot
synthesize browser-authored or foreign-agent text. It returns uncached,
streamed provider audio with an explicit `audio/*` content type. Unknown agents
or events return `404`; events that are not usable speech behaviours return
`409`; unsupported voices or speeds outside `0.25` through `4.0` return `400`.
The defaults are `alloy` and `1.0`.

Talk to Me sends the observation and speech options to a scoped backend
endpoint:

```http
POST /demo/talktome/agents/{agentId}/speech?voice=cedar&speed=1.25
X-Prometheus-Access-Code: TTM31
Content-Type: application/json

{"type":"obs.user_utterance","actor":"user","kind":"observation","payload":"Exact text"}
```

The dedicated endpoint first verifies that the scoped agent carries the
`utility.talk_to_me` profile tag. PROMETHEUS then acknowledges the observation
with the ordinary `FULL_PLAN` output profile, persists the deterministic
`core.talk_to_me` speech plan, and passes that canonical speech string to
`prometheus.speech.url` using `prometheus.speech.model` (default:
`gpt-4o-mini-tts`). The Talk to Me endpoint retains its exact-text behavior and
`utility.talk_to_me` agent-tag restriction while sharing provider configuration,
voice/speed validation, and streamed HTTP audio mechanics with canonical
behaviour speech.

## Admin API

Admin endpoints require:

```http
X-Prometheus-Admin-Token: <prometheus.admin.token>
```

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/agent-types` | List active declarative agent types with catalog metadata. |
| `GET` | `/admin/access-code-presets` | List backend-defined access-code presets. |
| `POST` | `/admin/access-code-presets/{presetKey}/apply` | Create a preset bundle transactionally. |
| `POST` | `/admin/access-codes` | Create an access code. |
| `GET` | `/admin/access-codes` | List access codes. |
| `PATCH` | `/admin/access-codes/{id}` | Enable or disable a code. |
| `PUT` | `/admin/access-codes/{id}/agent-types` | Replace assigned agent type keys. |
| `GET` | `/admin/access-codes/{id}/agents` | List agents linked to a code. |

Valerian Designer uses the same header for the complete definition lifecycle:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/agent-definitions` | List definition identities, active pointers, and revision summaries. |
| `GET` | `/admin/agent-definitions/{key}` | Retrieve one definition summary. |
| `GET` | `/admin/agent-definitions/{key}/revisions/{revision}` | Retrieve repository metadata and the definition document. |
| `GET` | `/admin/agent-definitions/{key}/revisions/{revision}/export` | Export the exact canonical JSON document. |
| `POST` | `/admin/agent-definitions` | Create a designer-owned draft. |
| `POST` | `/admin/agent-definitions/imports` | Import a document as an imported draft. |
| `PUT` | `/admin/agent-definitions/{key}/revisions/{revision}` | Replace a draft at an expected optimistic version. |
| `POST` | `/admin/agent-definitions/validation` | Validate a saved or unsaved definition without publishing. |
| `POST` | `/admin/agent-definitions/{key}/revisions/{revision}/publish` | Validate, compile, and immutably publish a draft. |
| `POST` | `/admin/agent-definitions/{key}/revisions/{revision}/activate` | Make a published revision active for new instances. |
| `POST` | `/admin/agent-definitions/{key}/revisions/{revision}/archive` | Archive a non-active published revision. |
| `POST` | `/admin/agent-definitions/{key}/revisions/{revision}/clone` | Copy a revision into a new designer draft identity/revision. |
| `GET` | `/admin/agent-definitions/component-catalog` | List registered component schemas, defaults, examples, capabilities, and UI copy. |
| `POST` | `/admin/agent-definitions/previews` | Compile and open an isolated preview from unsaved JSON or a saved draft. |
| `GET` | `/admin/agent-definitions/previews/{previewId}` | Inspect active state, storage, history, transcript, and safe diagnostics. |
| `POST` | `/admin/agent-definitions/previews/{previewId}/events` | Submit one runtime event to the preview. |
| `POST` | `/admin/agent-definitions/previews/{previewId}/generate` | Request behaviour generation with production runtime semantics. |
| `POST` | `/admin/agent-definitions/previews/{previewId}/reset` | Reset the disposable runtime to its initial state and storage. |
| `DELETE` | `/admin/agent-definitions/previews/{previewId}` | Close and discard the preview immediately. |

Create, import, and validation accept `{"definition": <schema-v1-document>}`.
Draft replacement adds `"optimisticVersion"`; publish, activate, and archive
accept `{"optimisticVersion": <repository-version>}`. Clone accepts
`{"targetKey": "...", "targetRevision": 2}`. The server assigns status,
provenance, timestamps, hashes, and optimistic versions; similarly named import
fields cannot forge repository metadata. Validation/publication failures return
stable diagnostic codes and JSON Pointers. Optimistic and lifecycle conflicts
return `409`, unknown resources `404`, malformed requests `400`, and structural
or publication validation failures `422`.

Preview creation accepts either `{"definition": <current-schema-v1-document>}`
or `{"key": "...", "revision": 1}` for an existing draft. A valid unsaved
document does not need to be saved or published. Preview sessions use the same
compiler, runtime engine, and registered components as production, but remain
in bounded in-memory storage only: they never create definition, agent,
access-code, event-history, or behaviour-history records. Idle access refreshes
the default 15-minute TTL; close or expiry makes the identifier return `404`.
The defaults are configurable with `prometheus.designer.preview.ttl`,
`max-sessions`, `max-operations`, `max-event-payload-chars`, and
`cleanup-delay-ms` under the same property prefix. Resource exhaustion returns
`429`; component-start failures return a safe `422` without provider details.

Access codes must be exactly five ASCII letters or digits. The backend treats
them as case-sensitive.

## CORS

Bundled clients are same-origin. External browser clients need an explicit
allowlist:

```properties
prometheus.cors.allowed-origins=http://127.0.0.1:5010,http://localhost:5010
prometheus.cors.allowed-origin-patterns=http://127.0.0.1:*,http://localhost:*
```

Keep this narrow because the scoped access code acts as a bearer-style client
credential.

## Repository Structure

```text
src/main/java/ch/zhaw/prometheus
  application/      Application services for agents, access codes, transcription, Speech, and scoped demos.
  controllers/      HTTP, SSE, admin, scoped demo, and static-client endpoints.
  definition/       JSON mapping, lifecycle/persistence, validation, components, compiler/cache, and runtime.
  logging/          SSE broadcasters.
  model/            Lightweight agent/event views plus shared behaviour, sensing, and RPS domain types.
  spi/              Language-model, live-transcription, and Speech integration boundaries.

src/main/resources/agent-definitions
  catalog/main/     Deterministic manifest and bundled versioned definition JSON.
  schema/           Executable schema-version-1 whole-agent JSON contract.

src/main/resources/public
  apiworkbench/     Guided REST/SSE API workbench for client developers.
  multilateral/     Meeting/group listener and report displays.
  talktome/         Public exact-text output-only Speech client.
  valerian/         Valerian cockpit.
  valerian-admin/   Valerian access management.

designer/           React/TypeScript/Vite source for Valerian Designer.
tests/playwright    Browser-level Valerian, Talk to Me, and API Workbench smoke tests.
```

## Developing New Agents

JSON is the only whole-agent definition language. Use schema version 1 at
`src/main/resources/agent-definitions/schema/agent-definition.schema.json` and
start from a bundled revision under `catalog/main`. Keep the stable definition
key outside the document lifecycle, give every state a stable ID, declare the
interaction profile, storage, resources, policies, transitions, decisions, and
actions explicitly, and reference only registered `(kind, version)` component
pairs. Published revisions are immutable; activation affects only subsequently
created instances, while existing instances keep their revision pin.

Bundled additions require a canonical revision JSON resource, a deterministic
manifest entry, schema/semantic/component validation, compilation/runtime
coverage, and public catalog documentation. Trusted Java extensions are limited
to reusable typed component kinds. Component authors must follow
`src/main/java/ch/zhaw/prometheus/definition/component/README.md`; component
configuration must be strict data and must never contain a Java class, bean
name, credential, or executable script.

Prefer clear replacement over compatibility shims while the framework remains
prototype-oriented.

## Deployment Notes

The repository contains Heroku/container-oriented resources:

- `Dockerfile`
- `src/main/resources/application-prod.properties`
- `src/main/resources/openai-prod.properties`
- `.github/workflows/deployment.yml`

Production deployments must provide database credentials and OpenAI credentials
through environment variables or platform config vars.

## Project Notes

- `.agents/messageinabottle.txt` is the compact session bootstrap prompt for a
  new coding agent.
- `.agents/CODEX.md` contains reusable, project-neutral engineering and
  milestone practices.
- `.agents/CONTEXT.MD` describes PROMETHEUS's purpose, current capabilities,
  regulation gaps, architecture, and repository boundaries.
- The top of `PROJECT.md` is the current engineering snapshot. The remaining
  milestone records are a historical audit to search selectively, not required
  startup reading.
