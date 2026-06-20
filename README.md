# PROMETHEUS

PROMETHEUS is an event-driven Java framework for building digital agents with explicit state-machine control and a first-class regulation layer.

It evolves the PROMISE approach from turn-based text interaction into multimodal sensing and multimodal behaviour while keeping transitions, guards, and actions explicit and testable.

### Why

Many agent systems still assume turn-based chat: user says something, agent replies. That model breaks in real environments where agents must interpret voice, visual, social, and system signals continuously, decide when to act proactively, and sometimes stay silent, yield, or disengage safely.

### What

PROMETHEUS is an event-driven, regulation-aware framework for engineering multimodal agents with explicit control. It combines inspectable state-machine task logic with first-class regulation, so behaviour can adapt to context without becoming opaque or unpredictable.

### How

PROMETHEUS models all inputs as `Event` objects and all outputs as structured `BehaviourPlan` objects (`speech`, `nonVerbal`, `motion`, `display`). State transitions, guards, and actions remain explicit and testable, while regulation modules modulate expression and emit bounded control signals (for example opportunities and interrupts). This unified model supports use cases from conversational check-ins to embodied assistance and ambient monitoring.

## What You Can Build

- Digital agents with two-layer behaviour control: state-machine interaction flow plus regulation inspired by motivational models for adaptive behavior.
- Multimodal agents that combine user utterances with multimodal sensing (e.g., facial expressions, heart rate variability, gaze, social context).
- Multimodal behaviour generation across channels (e.g., gestures, facial expressions, gaze, proxemics, prosody).
- Embodied AI scenarios such as virtual avatars and robotic systems.

## Clients (Quick Tour)

Most runtime clients take `?agentId=<uuid>`. The Valerian cockpit starts with an access-code screen and then uses scoped `/demo/...` endpoints.
For the complete list including multilateral endpoints, see `All Client Endpoints` below.

### Prometheus Demo Cockpit

- URL: `http://localhost:8080/valerian/`
- Purpose: single-page PROMETHEUS demo surface with agent selection, text input, realtime speech-to-speech, camera sensing controls, manual event shortcuts, behaviour visualization, and diagnostics drawer.
- Users enter a configured access code first. Accepted codes are stored in `sessionStorage` for the current browser session.
- The drawer opens on the `Agent` tab, which shows assigned agent types, known instances, connection controls, and agent metadata including name, description, language code, and interaction profile. Users create one or more instances from the assigned types, then select an instance in `Known Agents`; diagnostics are available on the second tab.
- `Known Agents` lists only instances linked to the active access code. Delete removes the visible scoped instance link and deletes the underlying agent only when no other code links remain.
- Agent selection/start controls live in the drawer. Dropdown selection or manual typing only selects an Agent ID; `Connect` validates it through the scoped demo API and opens live streams. Once connected, the same button becomes `Disconnect`. `Start Agent` calls the scoped agent runtime start endpoint. The drawer shows the connected agent's name, description, and interaction profile.
- Without an explicit `?agentId=` URL after access-code validation or drawer selection, the cockpit leaves the Agent ID empty and does not auto-connect to a stored or guessed agent.
- The center column has separate Text and Continuous Speech tabs. Sensing and sensed input signals are on the left; rendered `BehaviourPlan` output is on the right.
- On connect, the Text tab hydrates from existing agent event history, including prior user utterances and assistant behaviour-plan speech.
- The cockpit suppresses duplicate assistant renders when the same behaviour response arrives through both an HTTP response and the behaviour stream.
- The Diagnostics tab shows a configurable activity log, current/available state view, and storage entries as expandable key rows with copy-to-clipboard value buttons.
- Camera sensing modes are independently toggleable while the camera is running. Face emotion, social grouping, and hand-sign detection can run in any combination; mirrored overlay boxes align with the mirrored self-view. `Emit camera observations` sends enabled camera detections, including hand signs, with per-mode throttles.
- The sensing card is visual-only: it groups visual detectors, configuration, manual emotion/social/hand inputs, and sensed visual signal readouts. The Continuous Speech tab includes a speech-sensing readout for the latest accepted Realtime ASR user utterance. Behaviour modalities render as full-width rows.
- After `Connect`, the cockpit reads `interactionProfile` from agent info and hides irrelevant sensing controls and behaviour rows. Agents without a declared profile keep the full cockpit visible as a fallback.
- If the connected profile declares no visual observations, the sensing card hides the camera viewer and camera controls and shows a no-visual-sensing message.

### Prometheus Admin Cockpit

- URL: `http://localhost:8080/valerian-admin/`
- Purpose: small root/admin page for configuring Valerian access codes without manual database changes.
- Admin token is entered in the page and stored in `sessionStorage` for the current browser session.
- The page can create manually typed five-character access codes, generate non-ambiguous five-character codes client-side, enable/disable codes, assign registered agent types with checkboxes, and inspect instances linked to each code.

### Text Client

- URL: `http://localhost:8080/?agentId=<uuid>`
- Purpose: text-first conversational interaction with the agent.

![Text client screenshot](.readme/client-text.png)

### Realtime Client

- URL: `http://localhost:8080/realtime/?agentId=<uuid>`
- Purpose: low-latency voice interaction via OpenAI Realtime.

![Realtime client screenshot](.readme/client-realtime.png)

### Monitor Client

- URL: `http://localhost:8080/monitor/?agentId=<uuid>`
- Purpose: live runtime visibility (state snapshots, behaviour events, logs).

![Monitor client screenshot](.readme/client-monitor.png)

### Visual Facial Client

- URL: `http://localhost:8080/visual/facial/?agentId=<uuid>`
- Purpose: visual/facial rendering channel for multimodal output.

![Visual facial client screenshot](.readme/client-visual-facial.png)

### Visual Multifacial Client

- URL: `http://localhost:8080/visual/multifacial/?agentId=<uuid>`
- Purpose: facial-emotion capture with per-user naming for multi-user interactions.

### Visual Social Client

- URL: `http://localhost:8080/visual/social/?agentId=<uuid>`
- Purpose: social visual presentation channel for multimodal output.

![Visual social client screenshot](.readme/client-visual-social.png)

### Nonverbal Behaviour Renderer

- URL: `http://localhost:8080/nonverbal/?agentId=<uuid>`
- Purpose: dedicated nonverbal behaviour stream rendering.

![Nonverbal behaviour renderer screenshot](.readme/client-nonverbal-renderer.png)

### RPS Manual Client

- URL: `http://localhost:8080/rps/?agentId=<uuid>`
- Purpose: Schere-Stein-Papier demo surface that renders the agent's `motion.handSign` and emits manual or camera-detected `obs.hand.sign` events.

## Core Concepts

- `Event`: unified input and internal signal model (observations, responses, control events).
- `State` and `Transition`: explicit control with prompt-based decisions and actions.
- `BehaviourPlan`: structured output across behaviour modalities (`speech`, `nonVerbal`, `motion`, `display`).
- `Storage`: per-agent key-value memory shared across states.
- `OuterState`: hierarchical control across nested state machines.
- Regulation and policy runtime: modulation is explicit and bounded by control semantics.

## Tech Stack

- Java 21
- Spring Boot 3.4.x
- Maven Wrapper (`mvnw`, `mvnw.cmd`)
- MySQL (JPA/Hibernate)

## Local Setup

### 1. Prerequisites

- JDK (`java -version`)
- MySQL running locally

### 2. Configure properties

Create or update:

- `src/main/resources/application.properties`
- `src/main/resources/openai.properties`

You can copy from:

- `src/main/resources/application.properties.template`
- `src/main/resources/openai.properties.template`

Minimum local fields:

- DB: `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
- OpenAI or Azure: `openai.openaivsazureopenai`, `openai.url`, `openai.key`
- Optional admin API: `prometheus.admin.token`
- Optional external browser clients:
  `prometheus.cors.allowed-origins`,
  `prometheus.cors.allowed-origin-patterns`
- Optional realtime: `openai.realtimeModel`, `openai.realtimeInputTranscriptionModel`,
  `openai.realtimeTranscriptionModel`, `openai.realtimeTranscriptionLanguage`,
  `openai.realtimeTranscriptionDelay`, `openai.realtimeSafetyIdentifier`,
  `openai.realtimeClientSecretUrl`, `openai.realtimeCallsUrl`

### 3. Run

```bash
./mvnw spring-boot:run
```

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

App default URL: `http://localhost:8080`

### 4. External browser clients and CORS

The bundled PROMETHEUS clients are same-origin and do not need CORS. If a
separate browser client such as `zhaw-iwi/valerian.git` runs on another origin
and calls PROMETHEUS directly, configure an explicit allowlist.

For a local laptop cockpit:

```properties
prometheus.cors.allowed-origins=http://127.0.0.1:5010,http://localhost:5010
```

For variable local ports or hostnames, use origin patterns instead:

```properties
prometheus.cors.allowed-origin-patterns=http://127.0.0.1:*,http://localhost:*
```

On Heroku, set the equivalent config vars:

```bash
PROMETHEUS_CORS_ALLOWED_ORIGINS=http://127.0.0.1:5010,http://localhost:5010
PROMETHEUS_CORS_ALLOWED_ORIGIN_PATTERNS=http://127.0.0.1:*,http://localhost:*
```

Keep the allowlist as narrow as practical because the scoped demo access code is
used as a bearer-style client credential.

## Agent Definitions and Creation

Reusable agent type definitions live in `src/main/java/ch/zhaw/prometheus/agentdefs`.
Each production definition implements `AgentDefinition`, exposes a stable `key()`, builds an unsaved `Agent` through `createAgent()`, and may define its startup path in `createInstance(...)`.
The current migrated definitions call `Agent.start(...)` inside `createInstance(...)`, preserving the former seed-test startup behaviour as developer-written code.

Registered definitions:

- `basic.single_state_guessing_game` - Single-state guessing game with guided prompt flow.
- `basic.single_state_micro_coaching` - Single-state supportive micro-coaching agent.
- `basic.single_state_co_creation` - Single-state collaborative co-creation conversation.
- `basic.four_states_linear` - Four-state linear progression with explicit stage transitions.
- `basic.four_states_circular` - Four-state circular loop for iterative dialogue cycles.
- `multimodal.single_state_in` - Single-state micro-coaching with multimodal sensing inputs.
- `multimodal.single_state_out` - Single-state interaction with deterministic multimodal behaviour output.
- `multimodal.single_state_in_out` - Single-state interaction combining multimodal sensing and multimodal behaviour output.
- `gigielderlycare.therapy_appointment_reminder` - GIGI elderly-care therapy appointment reminder with resistance-aware coaching.
- `gigielderlycare.guessing_game` - GIGI elderly-care guessing game where GIGI gives clues.
- `gigielderlycare.guessing_game_user_guess` - GIGI elderly-care yes/no guessing game where the user guesses GIGI's secret item.
- `gigielderlycare.smart_goal_coaching` - GIGI elderly-care SMART goal coaching for small wellbeing steps.
- `gigitdsr.guessing_game_with_gestures` - GIGI TDSR German yes/no guessing game with structured nonverbal gesture output.
- `gigitdsr.social_context_sensitivity` - GIGI TDSR German social context demo that reacts to computed visual-social situation changes.
- `gigitdsr.rock_scissor_paper` - GIGI TDSR German Schere-Stein-Papier demo with deterministic `motion.handSign` output and manual or camera-detected `obs.hand.sign` input via the `/rps` client.
- `gigitdsr.tour_conversation` - GIGI TDSR German general-purpose station conversation agent with TDSR route/persona grounding and occasional nonverbal gestures.

The GIGI TDSR definitions include concise Tour de Suisse Robotique persona
context and keep it guarded as background knowledge, so normal demo turns stay
focused on the active game or sensing capability. German-facing TDSR prompt
text uses UTF-8 umlauts, and the general tour conversation agent is instructed
to keep replies short while varying one-, two-, and rare three-sentence answers.

### Option A: Seed registered agents from tests

The classes under `src/test/java/ch/zhaw/prometheus/agents` are thin manual seed wrappers around production definitions. To persist one initialized agent locally:

1. Run the wrapper test once.
2. The test creates the production definition, runs its `createInstance(...)` startup path, and saves the initialized agent in the database.
3. List agents via `GET /agent` and use the returned UUID.

### Option B: Create a simple single-state agent via REST

Use `POST /agent/singlestate` with `SingleStateAgentCreateDTO` shape (see `src/main/java/ch/zhaw/prometheus/controllers/dto/SingleStateAgentCreateDTO.java`).

## All Client Endpoints

Most clients take `?agentId=<uuid>`. The Valerian cockpit uses an access-code session first.

- Prometheus demo cockpit: `http://localhost:8080/valerian/`
- Prometheus admin cockpit: `http://localhost:8080/valerian-admin/`
- Chat client (text-to-text): `http://localhost:8080/?agentId=<uuid>`
- Realtime voice client (speech-to-speech): `http://localhost:8080/realtime/?agentId=<uuid>`
- Agent monitor: `http://localhost:8080/monitor/?agentId=<uuid>`
- Visual facial detector: `http://localhost:8080/visual/facial/?agentId=<uuid>`
- Visual multifacial detector: `http://localhost:8080/visual/multifacial/?agentId=<uuid>`
- Visual social detector: `http://localhost:8080/visual/social/?agentId=<uuid>`
- Nonverbal behaviour renderer: `http://localhost:8080/nonverbal/?agentId=<uuid>`
- RPS manual sensing client: `http://localhost:8080/rps/?agentId=<uuid>`
- Multilateral listen: `http://localhost:8080/multilateral/listen/?agentId=<uuid>`
- Multilateral reports: `http://localhost:8080/multilateral/reports/?agentId=<uuid>`

## API Overview

### Agent metadata

- `GET /agent`
- `GET /agent/{id}`
- `GET /agent/eventhistory`
- `GET /agent/{id}/eventhistory`
- `POST /agent/singlestate`

`GET /agent`, `GET /agent/{id}`, and `GET /{agentID}/info` return
`languageCode` and an `interactionProfile` object. `languageCode` is optional
agent metadata used as a Realtime transcription language hint; custom
`POST /agent/singlestate` requests may include it and otherwise default to
`en`. The profile declares the observation event types an
agent expects and the behaviour modalities it can emit, for example
`obs.hand.sign`, `obs.social.grouping`, `speech`, `motion.handSign`, and
`display`. It is persisted with the `Agent` aggregate and is metadata, not
runtime `Storage`.
Seed agent templates declare profiles through `AgentInteractionProfiles`
factories such as `speechOnly()`, `multimodalOutput()`, and
`multimodalInputOutput()`.

### Agent runtime

- `GET /{agentID}/info`
- `GET /{agentID}/state`
- `GET /{agentID}/states`
- `GET /{agentID}/storage`
- `GET /{agentID}/eventhistory`
- `POST /{agentID}/start`
- `DELETE /{agentID}/reset`

### Realtime and event ingress

- `GET /{agentID}/prompt` (optional `?profile=FULL_PLAN|REALTIME_SPEECH|BACKEND_COMPLEMENT`)
- `POST /{agentID}/acknowledge` returns `ResponseView` (`responseEvent`, `active`)
- `POST /{agentID}/realtime/call` with raw SDP body (`Content-Type: application/sdp`)
- `DELETE /realtime/calls/{callId}`
- `POST /realtime/transcription/session`

### Streaming (SSE)

- `GET /{agentID}/monitor/stream`
- `GET /{agentID}/behaviour/stream`
- `POST /{agentID}/behaviour/generate` (optional body: `omitModalities`, `outputProfile`)
- `GET /logs/stream`
- SSE publish failures are isolated from main HTTP endpoint handling and scheduler tick processing.
- SSE streams use finite emitter lifetimes plus periodic heartbeat comments (`prometheus.sse.heartbeat.delay-ms`, default `25000`) so dead connections are discovered and cleaned up.
- Behaviour SSE frames carry persisted event ids. Reconnecting clients may pass `Last-Event-ID` or `?lastEventId=<id>` to replay missed behaviour events from event history.
- Browser clients close EventSource streams on page unload and use one reconnect timer per stream with bounded exponential backoff and jitter on disconnect.
- Monitor client log and behaviour panes use bounded in-memory buffers to avoid unbounded growth during long sessions or repeated stream failures.

### Admin access-code API

Admin endpoints require header `X-Prometheus-Admin-Token` with the exact value from `prometheus.admin.token`.
Access codes are case-sensitive, are not normalized by the backend, and must be exactly five ASCII letters or digits.
The same operations are available through the Prometheus admin cockpit at `/valerian-admin/`.

- `GET /admin/agent-types`
- `POST /admin/access-codes`
- `GET /admin/access-codes`
- `PATCH /admin/access-codes/{id}`
- `PUT /admin/access-codes/{id}/agent-types`
- `GET /admin/access-codes/{id}/agents`

Create body:

```json
{
  "code": "af7u1",
  "enabled": true
}
```

Allowed-type replacement body:

```json
{
  "agentTypeKeys": ["gigitdsr.rock_scissor_paper"]
}
```

`PUT /admin/access-codes/{id}/agent-types` replaces the complete assignment. Send a
smaller list to remove types, a larger list to add types, or an empty list to
clear all assigned agent types.

### Scoped demo API

The scoped demo API is intended for the Valerian cockpit and does not change the
existing global agent endpoints. Requests use an existing enabled access code.
Pass the code as header `X-Prometheus-Access-Code`. Browser SSE clients may pass
the same value as `?accessCode=<code>` because `EventSource` cannot set custom
headers.

- `POST /demo/session`
- `GET /demo/agent-types`
- `GET /demo/agents`
- `POST /demo/agents`
- `DELETE /demo/agents/{agentId}`
- `GET /demo/agents/{agentId}/info`
- `GET /demo/agents/{agentId}/eventhistory`
- `GET /demo/agents/{agentId}/state`
- `GET /demo/agents/{agentId}/states`
- `GET /demo/agents/{agentId}/storage`
- `POST /demo/agents/{agentId}/start`
- `DELETE /demo/agents/{agentId}/reset`
- `POST /demo/agents/{agentId}/acknowledge`
- `POST /demo/agents/{agentId}/behaviour/generate`
- `GET /demo/agents/{agentId}/behaviour/stream`
- `GET /demo/agents/{agentId}/monitor/stream`
- `GET /demo/agents/{agentId}/prompt`
- `POST /demo/agents/{agentId}/realtime/call` with raw SDP body (`Content-Type: application/sdp`)

Session body:

```json
{
  "accessCode": "af7u1"
}
```

Agent creation body:

```json
{
  "agentDefinitionKey": "gigitdsr.rock_scissor_paper"
}
```

Only agent types assigned to the access code can be created. Agents created
through a code are visible only through that code unless an admin or later flow
links the same agent to another code. Deleting a scoped agent removes the link
first and deletes the underlying `Agent` only when no other access code links
remain.

## Developer Workflow for New Agents

1. Start from an existing production definition under `src/main/java/ch/zhaw/prometheus/agentdefs`, such as `basic/SingleStateMicroCoaching.java` or `multimodal/SingleStateMultimodalInOut.java`.
2. Define prompts for outer state, inner state(s), transition decisions, and actions.
3. Use `Storage` keys for extracted values consumed by later states.
4. Declare an `AgentInteractionProfile` when the agent expects specific observation signals or supports specific output modalities. Prefer the common `AgentInteractionProfiles` factories when they fit.
5. For multimodal behaviour, include nonverbal policy prompts (`PromptPolicy#setNonVerbalPlanPrompt` and optional `PromptPolicy#setNonVerbalGesturePrompt` fallback) and ingest nonverbal events via `/acknowledge`.
6. Implement `AgentDefinition`, choose a stable key, and register the definition in `AgentDefinitionRegistry`.
7. Put startup behavior directly in `createInstance(...)`; call `Agent.start(...)` there only when this agent type should be started during creation.
8. Add a thin test wrapper only when manual database seeding is useful.
9. Seed the agent, run app, then iterate using the Prometheus demo cockpit, Monitor, and behaviour streams.
10. Add or adapt controller DTOs and endpoints when you need reusable agent creation APIs beyond `/agent/singlestate`.

### Event example

```json
{
  "type": "obs.user_utterance",
  "actor": "user",
  "kind": "observation",
  "payload": "I am feeling better today."
}
```

Assistant behaviour plan events use:

- `type`: `resp.behaviour_plan`
- `actor`: `assistant`
- `kind`: `response`
- `payload`: JSON string of a `BehaviourPlan`

Visual social observations use raw event types:

- `obs.human.presence`
- `obs.social.grouping`

The visual social client and the Prometheus demo cockpit can emit these raw events
from camera detection. The Prometheus demo cockpit also includes manual social
scenario buttons that emit the same raw event contract for rehearsal without a
camera.

When `obs.social.grouping` is acknowledged, PROMETHEUS may persist a computed
social event:

- `type`: `obs.social.situation_change`
- `actor`: `system`
- `kind`: `observation`
- `payload.changeType`: `arrival`, `departure`, `crowd_detected`,
  `now_alone`, `single_person_nearby`, or `group_size_changed`

Schere-Stein-Papier hand-sign observations use:

- `type`: `obs.hand.sign`
- `actor`: `user`
- `kind`: `observation`
- `payload.sign`: `rock`, `scissor`, or `paper`
- optional payload fields: `hand`, `confidence`, `detectionMode`, `source`

The RPS client at `/rps/?agentId=<uuid>` and the Prometheus demo cockpit at
`/valerian/?agentId=<uuid>` emit this event shape from manual sign buttons
with `source=rps.web` and `detectionMode=manual`. Their optional camera mode
uses MediaPipe Gesture Recognizer in the browser and maps canned gestures as
follows: `Closed_Fist -> rock`, `Victory -> scissor`, `Open_Palm -> paper`.
Camera events use `source=rps.web.camera` and `detectionMode=client_camera`.

Schere-Stein-Papier reveal behaviours use top-level `BehaviourPlan.motion`:

```json
{
  "effector": "right_hand",
  "armPose": "present_forward",
  "handSign": "rock",
  "timing": {
    "synchronizeWithSpeech": "Schere, Stein, Papier",
    "revealAt": "phrase_end"
  },
  "confidence": 1.0
}
```

Notes:
- `/{agentID}/acknowledge` may already return a `responseEvent` (for example when a transition enters a starting state).
- `/{agentID}/behaviour/generate` can be called in final states; final-state prompts may still produce behaviour while `active=false`.
- The Valerian cockpit renders the gesture field as `NONE` when a behaviour event contains no `nonVerbal` object, making speech-only turns explicit instead of leaving a stale previous gesture visible.

## Realtime Notes

- Continuous speech browsers no longer obtain OpenAI client secrets. They create a WebRTC offer and post the raw SDP to PROMETHEUS:
  - global client: `POST /{agentID}/realtime/call`
  - Valerian scoped client: `POST /demo/agents/{agentId}/realtime/call`
  - query options: `voice`, `turnDetection=server_vad|semantic_vad`, `generateComplement=true|false`
- PROMETHEUS forwards the SDP to OpenAI `/v1/realtime/calls` with the current `REALTIME_SPEECH` prompt already installed as session `instructions`.
- Agent instances can carry an optional `languageCode` such as `de` or `en`. Definition-backed agents set this according to their prompt language; the current registered built-ins use German prompts and set `de`. Custom `/agent/singlestate` creation defaults to `en` unless the request supplies another `languageCode`. Agent-bound Realtime speech calls forward the value as `audio.input.transcription.language` to reduce cross-language transcription drift.
- Speech-to-speech calls use `openai.realtimeInputTranscriptionModel` for `audio.input.transcription.model` and default to `gpt-4o-transcribe`. This is the built-in ASR model for the Realtime call, not the response-generation model. Transcription-only sessions still use `openai.realtimeTranscriptionModel` and default to `gpt-realtime-whisper`.
- PROMETHEUS opens a backend sideband WebSocket for the returned call ID. The sideband listens for Realtime transcript events, batches asynchronous transcript completions by committed input item, suppresses duplicate and known caption-hallucination transcripts, acknowledges accepted user utterances through the normal PROMETHEUS runtime, generates canonical `REALTIME_SPEECH` through the backend when needed, refreshes session instructions after state transitions, and only then triggers an out-of-band `response.create` with `conversation=none`, empty input context, and an exact-speech instruction.
- PROMETHEUS persists the canonical assistant speech as `resp.behaviour_plan` before Realtime speaks it. Browser clients render live audio/transcript but do not acknowledge assistant responses themselves.
  - Server default: `openai.realtimeModel=gpt-realtime-2`.
  - Voice controls include the GA voice options `cedar` and `marin`.
  - Optional endpoint override: `openai.realtimeCallsUrl`.
- The multilateral listening client obtains a transcription-only client secret from
  `POST /realtime/transcription/session`.
  - Server default: `openai.realtimeTranscriptionModel=gpt-realtime-whisper`.
  - Optional transcription hints: `openai.realtimeTranscriptionLanguage`, `openai.realtimeTranscriptionDelay`.
  - When `/multilateral/listen` is opened for an agent, it passes `agentId`; the endpoint uses the agent `languageCode` as a transcription language override when present and falls back to `openai.realtimeTranscriptionLanguage` otherwise.
  - `gpt-realtime-whisper` omits turn detection, so `/multilateral/listen` sends periodic
    `input_audio_buffer.commit` events while listening.
  - Known caption-style ASR hallucinations are filtered before display or `/acknowledge`.
- If `openai.realtimeSafetyIdentifier` is set, the backend sends it as `OpenAI-Safety-Identifier` when creating
  Realtime calls and transcription client secrets. Use a stable, privacy-preserving identifier.
- Browser clients establish WebRTC by posting SDP to PROMETHEUS. PROMETHEUS returns the OpenAI SDP answer plus a `callId`.
- `/{agentID}/prompt?profile=REALTIME_SPEECH` remains the backend prompt source for Realtime session instructions.
- When PROMETHEUS acknowledgement or backend speech generation returns speech, the sideband asks Realtime to say that exact text from an empty out-of-band response context and does not persist a duplicate assistant event.
- Active Realtime calls render canonical backend `BehaviourPlan.speech` for any published assistant behaviour event, including responses triggered by non-speech observations such as hand signs or visual social signals.
- Browser speech clients expose continuous Realtime speech only. Users choose `server_vad` or `semantic_vad`; OpenAI VAD owns turn chunking.
- Continuous Realtime restarts through its sideband startup configuration: if the latest utterance in the current state history is assistant-authored, PROMETHEUS asks Realtime to read that exact stored `BehaviourPlan.speech`. Non-speech observations or backend complement events after the assistant speech do not prevent replay, but a later user utterance does.
- To complement realtime speech with nonverbal backend output, call:
  - `POST /{agentID}/behaviour/generate`
  - body example: `{"outputProfile":"BACKEND_COMPLEMENT","omitModalities":["speech"]}`

For Python exploration, see `PROMISE_Realtime.ipynb`.

## Deployment to Heroku

This repository already contains Heroku-oriented production resources:

- `Dockerfile`
- `src/main/resources/application-prod.properties`
- `src/main/resources/openai-prod.properties`
- `.github/workflows/deployment.yml`

### 1. Heroku app setup

- Create app in Heroku.
- Add JawsDB MySQL.
- Set stack to container:

```bash
heroku stack:set container --app <your-app-name>
```

### 2. Heroku config vars

Set at minimum:

- `OPENAI_KEY`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `PORT` (provided automatically by Heroku runtime)

### 3. GitHub Actions deployment

Update `.github/workflows/deployment.yml` with your app and secret names.

Current workflow deploys Docker to Heroku on push to branch `au_restaurant_prod`.

### 4. Run with prod profile

Container command uses:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Repository Notes

- `.agents/CONTEXT.MD` defines the PROMETHEUS framework context for agentic development: purpose, canonical use cases, requirements, and architectural specifications.
- `.agents/CODEX.md` defines the engineering workflow and execution discipline coding agents must follow in this repository.
- `.agents/humandevhowto.txt` provides example prompts for starting a coding-agent session.
