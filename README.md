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

In **Continuous → Speech Output Settings**, Audio format defaults to
**Automatic (PCM when supported)**. The cockpit prepares a PCM renderer and the
selected speaker before requesting audio; unsupported or failed preparation uses
MP3. Select **MP3** for a comparison using the same voice, speed and transcription
preset. The existing voice/speed controls now live in this visible output panel;
saved settings are retained. Format changes apply to the next reply and controls
are locked during output. Refresh after deployment to load the new player.

**Conversation pace** in Live Transcription Settings offers Ultra Responsive
(0.5-second silence, minimal provider delay), Responsive (0.8 seconds, low delay),
and Pause tolerant (1.5 seconds, medium delay).
Pause tolerant remains the default. Presets change only these two values; saved
language, noise and device choices remain intact. Manual turn completion and
custom silence/delay values remain available. The shared multilateral listener
offers the same choices. Settings are locked during an active session.

Ultra Responsive shortens the configured silence interval by another 300 ms
relative to Responsive; actual speech-end-to-playback improvement depends on
turn segmentation and downstream timings. Longer within-sentence pauses may
split a turn. The Transcription delay dropdown remains independently adjustable.
Previously saved 0.5-second/low settings remain intact and now show Custom. After
updating, reload the cockpit and select Ultra Responsive while stopped to apply
0.5-second/minimal settings before starting transcription.

To try a 1,000 ms pause, stop transcription, select local VAD and set
**Silence duration (seconds)** to **1.0**, then restart transcription. The pace
selector shows Custom; provider delay can be selected separately.

Responsive reduces the local silence wait by 700 ms, but the frozen synthetic
pause replay produced extra segments at that threshold. Even 1.5 seconds split
the longer hesitation. Neither setting is certified for hesitant healthcare or
far-field speech; use longer custom timing or manual turns where needed. Run
`node tests/needforspeed/replay-vad.mjs` for the offline segmentation comparison.
It does not measure ASR accuracy or provider latency.

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

Production agent definitions live under
`src/main/java/ch/zhaw/prometheus/agentdefs`. Definitions implement
`AgentDefinition`, expose a stable key, and are discovered as Spring beans.

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
- Node.js only when running the Playwright visual smoke tests.
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

The default local URL is `http://localhost:8080`; it redirects to Valerian
Cockpit.

Open the main surfaces:

- Valerian Cockpit: `http://localhost:8080/valerian/`
- Valerian Access Management: `http://localhost:8080/valerian-admin/`
- Talk to Me: `http://localhost:8080/public/talktome`
- API Workbench: `http://localhost:8080/apiworkbench/`
- Multilateral listener: `http://localhost:8080/multilateral/listen/`
- Multilateral reports: `http://localhost:8080/multilateral/reports/`

## Testing

Use a disposable local MySQL schema and a restricted test account, supplied through
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`. Several integration fixtures delete stored agents;
some classes named `*UnitTest` also start a database-backed Spring context. Keep
the developer database out of these runs. Disable scheduled ticks with
`PROMETHEUS_RUNTIME_TICK_ENABLED=false` for deterministic fixtures.

Run the Java regression suite:

```powershell
.\mvnw.cmd test
```

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

### Combined behaviour generation

Every `PromptPolicy` generation requests one JSON behaviour plan, including
final states. Speech-only policies request the minimal `{"speech":"..."}` object;
policies with nonverbal instructions use the compact encoding below. Java composes the existing
outer, task, starter and nonverbal instructions; custom persisted prompts remain
in place and gain this behaviour after reload. Structured nonverbal instructions
apply inside `nonVerbal`, and gesture-only instructions apply to its `gesture`
field. Optional motion/display objects retain the existing contract.

The provider response uses `speech` and `nv`, with these fields inside `nv`:

| Compact field | Canonical nonverbal field |
| --- | --- |
| `g` | `gesture` (same semantic label) |
| `f: [type, intensity]` | `facialExpression: {type, intensity}` |
| `z: [direction, focus]` | `gaze: {direction, focus}` |
| `m: [stillness, energy]` | `motion: {stillness, energy}` inside `nonVerbal` |
| `x: {...}` | Other nonverbal fields under their full names, including custom, partial, null or extended objects |

Only exact field pairs use tuples; `x` preserves other shapes without inventing
missing values or losing extensions. Top-level `motion` (including hand signs)
and `display` remain ordinary full-name objects. Speech and all string contents
remain unchanged; only JSON formatting whitespace is omitted. Expansion precedes
existing validation and event publication, so stored plans, history, HTTP/SSE and
clients always use the canonical field names. Existing agents gain compact
instructions on their next generation; no recreation or cockpit setting is needed.

Canonical provider responses remain accepted for authored prompts/custom gateways.
Timing exports distinguish `behaviour_decode_compact` from
`behaviour_decode_canonical`; neither includes response content. Malformed compact
tuples, unknown short keys and overlapping short-key/`x` definitions fail without
a repair request or partial speech. Compare actual completion tokens and latency
in the Interaction Timing export; a shorter JSON representation alone does not
establish a deployment speedup.

Deterministic RPS output and Talk to Me still construct plans without model calls.
Speech-only policies use the same typed `BEHAVIOUR` / `JSON_OBJECT` inference path
and validation/publication boundary as combined policies; the raw-text generation
branch has been removed. No nonverbal defaults are added to speech-only replies.
Existing final states gain this behavior after reload without prompt migration.
Invalid structured output fails without
retrying or publishing partial speech. Unknown gesture labels become `NONE`;
unsupported nonverbal move/turn fields are removed as before. The obsolete second
nonverbal request and third gesture-repair request have been removed.

With ordered guards, ordinary coaching uses three text requests: two decisions
and one combined behaviour request. With the default combined guard strategy it
uses two: one guard group and one behaviour request, down from four at baseline.
Startup and direct facial/social reactions use one generation request. These are
offline call-count results; measured provider latency and response quality are
tracked separately in the results ledger.

The execution roadmap is in `.agents/PLAN_TRANSITION_EXECUTION.md`.

### Background transition actions

New transition actions default to background execution in the Spring application
runtime. Use `action.blocking()` when later actions, guards or state-entry prompts
need its result. Gather/choice dependencies and RPS updates explicitly block;
summary/outcome recording runs in the background on any transition. There is no
cockpit switch. Embedded `PolicyRuntime` instances without an `ActionExecution`
host still execute on the caller thread.

An action's `prepare` method freezes selected events, resolved prompts and values
on the agent thread and returns `PreparedAction` work that captures no entities.
The worker computes JSON storage writes; only the application applies them under
the existing agent lock in a fresh transaction. Custom background actions must
implement this contract; custom blocking actions retain `execute`.

Work starts after successful turn commit and runs FIFO per agent. Different
agents can progress concurrently. Reset/delete invalidates obsolete results;
write tokens prevent overwriting a newer foreground value. Accepted earlier
background writes are accounted for so queued writes preserve their order.
Failures never remove an already-published farewell or trigger a model retry.

This queue is intentionally **in memory**: queued/running work may be lost on a
Heroku restart. Configure `prometheus.actions.parallelism` (4), `.capacity` (64,
running plus reserved/queued jobs), and `.queue-timeout-ms` (30000). Admission
does not wait or silently fall back to blocking: exhaustion fails the turn before
persistence. Queue expiry and worker failures are explicit logged failures.
`background_action` logs correlate job/agent/action/turn IDs and completion,
failure, conflict or shutdown status. The HTTP timing export records
`action_queued`; completion after the HTTP response is only in server logs.

Schema update adds nullable `action.execution_mode`, `agent.execution_epoch`, and
`storage_entry.write_token` columns through the existing Hibernate update setup.
Existing stored summary/outcome extraction actions and summarisation actions gain
background execution on reload. Known SmallTalk/coaching summary states also
migrate their legacy action modes. Other legacy actions keep blocking semantics;
newly authored actions default to background. Deploy coordinated writers: these
in-process ordering guarantees do not support multiple independent agent writers.
Completed storage remains durable; no job records or restart recovery are added.

### Speculative conversational behaviour

The Spring runtime starts one eligible current-state behaviour request alongside
transition evaluation. If no transition occurs, the next explicit generation can
reuse it, including across separate acknowledge/generate HTTP requests and entity
reloads. Publication still follows the ordinary validated BehaviourPlan path.
Any transition (including an inner transition or self-loop) discards the candidate
immediately; new-state generation proceeds after required blocking actions without
waiting for the old request. Reset, deletion, newer input, rollback, expiry and
changed effective prompts/model routes also prevent reuse.

This is enabled by default with the OpenAI gateway, including Heroku; no cockpit
switch or agent recreation is needed. Eligibility requires a user utterance,
no-op regulation, ordinary State/OuterState and PromptPolicy composition, the
default prompt assembler, and known pure guards including a model decision.
Custom states/policies/guards/assemblers, deterministic policies, unconditional
transitions and sensory observations retain their existing path. Custom gateways
must explicitly support speculative inference and concurrent requests.

Configure `prometheus.behaviour.speculation.enabled` (true), `.parallelism` (2
active speculative requests), `.capacity` (64 retained candidates), and `.ttl-ms`
(30000 from preparation). There is no waiting worker queue: saturation skips
speculation. Required decisions and fresh behaviour do not use these permits.
The cache is in memory and local to one process; use a single agent writer.
Set `PROMETHEUS_BEHAVIOUR_SPECULATION_ENABLED=false` for a sequential comparison.

Transitions can cost an extra model request. Cancellation is best effort and may
not stop provider billing or computation; provider contention can reduce the
latency benefit. A reused failed/invalid candidate fails normal generation without
a hidden retry; failures of discarded candidates cannot publish a response.

The interaction timing panel/export includes started, reused, discarded and wait
markers. Completed speculative inference evidence is marked `scope=speculative`
with its original trace; that duration can overlap an earlier HTTP request and
must not be added to request latency. Request IDs prevent double counting in CSV.
Still-running discarded work can finish after response headers, so its usage may
only appear in correlated server logs. Missing usage remains unknown.

### Task-specific text inference

The text SPI accepts typed `InferenceRequest` snapshots with purpose, messages,
output shape, optional JSON schema, request ID and correlation ID. Existing
semantic gateway methods remain available for custom/test gateways. Purposes
are `behaviour`, `nonverbal`, `decision`, `extraction`, and `summary`; they are
explicit in code rather than guessed from prompt wording.

`openai.model` remains the fallback. Configure `openai.reasoning-effort` and
`openai.routes.<purpose>.model`, `.reasoning-effort`, `.max-completion-tokens`,
`.timeout-ms` or `.url` to override a purpose. Blank effort uses the provider
default. The template contains an opt-in Sol/Luna configuration with `none`
effort; no existing installation switches models automatically. The output cap
includes reasoning tokens. Text HTTP requests have a 30-second default deadline
and a 10-second connection deadline; failures do not retry or escalate models.

The checked-in `openai-prod.properties` enables the requested Heroku testing
configuration: Luna for behaviour, nonverbal, decision, extraction and summary,
all with explicit `none` reasoning effort. Each purpose retains its independent
route, so behaviour and decisions can still use different models. GPT-5.2 remains
the global fallback.
The ordinary local template stays opt-in. Heroku environment variables can
override file values; effective model/effort appear in the inference timing logs.

For Azure, the URL identifies the deployment. A model override must include its
matching deployment URL; `model` identifies the underlying model for capability
validation and is not sent in Azure payloads. Offline routing tests use loopback
HTTP providers. Heroku recordings cover Sol behaviour and Luna decisions;
Luna behaviour quality/latency and Azure deployment access remain unverified.

Optional sampling parameters are omitted on reasoning model families to avoid
model/effort incompatibilities. This changes the former temperature-zero decision
payload on GPT-5.2; compare decision quality before promoting candidate routes.
Non-reasoning models retain temperature 1 for behaviour and 0 for structured
work. Invalid booleans/JSON, missing content, refusal, filtering and truncated
completions fail explicitly. Provider error bodies are not included in errors.

Provider references: [GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol),
[GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna) and
[GPT-5.2](https://developers.openai.com/api/docs/models/gpt-5.2). Sol/Luna support
Chat Completions and `none` effort; account access and task quality remain to be
established for each deployment.

### Response latency diagnostics

POST requests accept an optional UUID `X-Prometheus-Trace-Id` and return a validated
trace ID. A request that publishes behaviour also returns
`X-Prometheus-Behaviour-Id`, identifying the persisted event delivered on SSE.
These headers do not grant access or change event payloads. Configured CORS
origins can send/read them.

In Valerian, open **Agent & Diagnostics → Interaction Timing**. Recording is
automatic for submitted speech/text turns; expand a turn to inspect milliseconds
from estimated speech end through commit, final transcription, backend processing,
cockpit refresh and playback. **Processing turn** replaces the misleading
**Transcript Sending** badge: acknowledgement can include model work, behaviour
generation and persistence, followed by cockpit refresh before acceptance.

**Export JSON** includes detailed browser stages, each acknowledgement/fallback/
Speech HTTP request, available server spans, actual text-model routes/effort and
reported token usage, applied capture flags, turn settings, and playback mode.
**Export CSV** provides one comparison row per turn. No transcript, assistant
text, prompts, access codes, provider credentials, device IDs or audio is included.
Export before reloading or clearing; reset/disconnect preserve the recording.
The drawer shows the latest 20 turns and exports all retained turns (up to 128).
Startup/restart assistant replay does not alter a previously recorded turn.

`X-Prometheus-Timing` carries a base64-encoded version-1 JSON snapshot with
request-relative server duration and spans. It is capped at 6,000 characters/
64 spans and marks truncation explicitly. JSON responses attach it before body
serialization; streaming Speech attaches it before streaming begins, without
buffering the audio. Pure guard workers share only bounded request-local
measurements. There is no trace lookup endpoint or cross-request server cache.
Headers omitted by a proxy, work still running at the snapshot, and truncated
detail are incomplete evidence. Server spans can overlap or contain one another;
do not add them or subtract their timestamps from browser timestamps.

Server `latency` log entries record inference purpose, model, effective configured effort (or provider default),
token counts when supplied, application/persistence/publication durations and
Speech response-header time. Durations are monotonic. Successful provider prompt
and response bodies are no longer logged by the text gateway.
Valerian retains at most 128 content-free turn and event timing records in memory:
`PrometheusTimings.snapshot(agentId)`. Transcript ingress, canonical SSE receipt,
rendering, first audio byte and media `playing` are joined even when SSE beats
the HTTP response. Shared transcription records the last detected voice, local
VAD turn boundary, successful local commit send, provider commit acknowledgement
receipt, first/last non-empty transcript delta receipt, and final transcript receipt.
Final receipt is recorded before waiting for earlier items to finish, preserving
the distinction between transcription finalisation and ordered submission.
Manual turns record commit send/receipt without an invented speech-end timestamp.
The panel, CSV and offline reporter include commit-send-to-acknowledgement,
acknowledgement-to-final, first-to-last-delta and last-delta-to-final intervals.
Acknowledgements and transcripts use browser receipt times, not provider clocks;
these intervals include network delay. Deltas can arrive before speech ends or a
commit is sent, so the timeline supports negative offsets relative to speech end.
Missing acknowledgements/deltas and older exports leave those measurements unknown.
The existing committed stage remains the local VAD boundary for comparison with
older recordings; commit_sent records the separate successful send boundary.
Browser timing telemetry is not uploaded, and timing data is not durably persisted.
Speech delivery diagnostics are retained temporarily in bounded server memory and
fetched into the same export. Server and browser clocks are separate; correlate
IDs, then compare local durations.
Speech end is the local VAD estimate and playback is a browser renderer/media
event, not a measurement at the physical speaker. Unsubmitted/failed ASR items
and startup replay do not create turn records; missing stages remain unknown.

Run `node --test tests/js/performance/*.test.mjs` and
`CatalogInferenceCountUnitTest` for offline diagnostics checks. The roadmap and
measured/unverified results are maintained in `.agents/PLAN_NEEDFORSPEED.md` and
`.agents/NEEDFORSPEED_RESULTS.md`.

The drawer's JSON export can be shared directly or summarized without Heroku logs:

~~~powershell
node tests/needforspeed/summarize.mjs target/interaction-timing.json
~~~

Browser exports label their source as `browser`; they do not certify that the
provider or media was unmocked. Filter turns by settings and annotate warm/cold,
ordinary/closing workload and physical device/network conditions before comparing
latency distributions.

For separately collected fixtures or live experiments, save JSON with `metadata`
and `turns` fields. Set metadata `source`
to `fixture` or `live`, `configuration` to a description of the actual settings,
and `workload` to e.g. `warm ordinary SMART`. Include browser/device/network,
corpus revision, model/effort/guard strategy and speech/transcription settings in
metadata. Set `turns` to `PrometheusTimings.snapshot(agentId)`; export before the
128-record limit evicts samples or the recording is cleared. Then run:

```powershell
node tests/needforspeed/summarize.mjs target/turn-export.json target/server.log
```

The offline reporter calculates browser-stage p50/p95, counts missing/invalid
stages and errors, consumes embedded server timings and optionally joins
text-request/usage logs by opaque trace ID. Missing
usage stays unknown; the report does not estimate cost from incomplete logs.
It flags samples below 50 and never mixes browser and server clocks. A fixture
report cannot establish live latency, model quality or physical audibility.

For reproducible database/browser checks, `node tests/needforspeed/provider-stub.mjs`
starts a synthetic provider on loopback port 8091. Point a separate test app's
`OPENAI_URL` at `http://127.0.0.1:8091/v1/chat/completions` and
`PROMETHEUS_SPEECH_URL` at `http://127.0.0.1:8091/v1/audio/speech`; use a dummy
`OPENAI_KEY`, `OPENAI_OPENAIVSAZUREOPENAI=openai`, and the isolated datasource.
Set `OPENAI_LIVETRANSCRIPTIONCLIENTSECRETURL=http://127.0.0.1:9/session` to keep
unmocked transcription requests local, and remove any purpose-route URL overrides
for that test process. The stub always rejects guards and serves fixed fixture
speech; it is not a quality evaluator. Start the app on a dedicated port and set
`PROMETHEUS_BASE_URL`, `PROMETHEUS_SKIP_WEBSERVER=true` and the test admin token
for Playwright. The exact integrated test commands are in the results record.

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
| `POST` | `/agent/singlestate` | Create an ad-hoc single-state agent. |
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

### Operational resilience and acoustic limits

The shared transport reacquires the selected microphone and requests a fresh
scoped ephemeral session after connection/data-channel failure or when the
active microphone track ends. It makes at most two automatic reconnect
attempts with exponential backoff; if they are exhausted, the operator sees
the last actionable failure instead of an indefinite reconnect state. Browser
`devicechange` events refresh the microphone and speaker choices.

Explicit Stop, page navigation, agent switch/delete, reset, and failed startup
release microphone tracks and the cross-tab lease. Reset follows the cockpit
lifecycle by stopping transcription and leaving it idle; the operator starts a
new session when ready. One browser tab owns microphone capture at a time, and
one Valerian tab owns audible output for each agent. A tab that retains the
microphone lease may keep accepting finalized provider events while hidden,
but browser background throttling, wireless-device behavior, network recovery,
and acoustic quality must still be checked on the target deployment hardware.

`gpt-live-transcribe` supplies transcript text, not speaker identity, in this
contract. PROMETHEUS can accept sequential turns from several people, but it
does not diarize them. Simultaneous or overlapping speech may be merged, split,
or partly missed and must not be treated as reliably attributed input. Manual
turn commit remains available when an operator needs an explicit boundary in a
difficult room.

Run its deterministic browser gates with:

```powershell
npm.cmd run test:transcription:unit
npm.cmd run test:speech:unit
npm.cmd run test:valerian:lifecycle
npm.cmd run test:valerian:transcription
npm.cmd run test:valerian:visual
```

These suites mock microphone, WebRTC, SDP exchange, and provider events. They
do not replace the real acoustic matrix in
`.agents/TRANSCRIBE_SMOKE_RESULTS.md`. Before release on a target installation,
record its actual microphone and speaker models and execute that matrix in
near-field, far-field, noisy/outdoor, wireless/Bluetooth, and multiple-speaker
conditions. Keep every unperformed row marked `NOT RUN`.

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

Valerian enables audible agent output only after **Start Transcription**.
While transcription is stopped, connecting, resetting, and receiving new
behaviours update the chat and behaviour display without synthesizing or playing
audio. **Stop Transcription**, reset, disconnect and agent changes disable
output and discard pending playback, including delayed startup lookups.
**Stop Speech** cancels the current output while keeping transcription enabled.

While enabled, Valerian's output queue accepts `behaviour-live` events with non-empty speech
and a persisted SSE event ID. It processes those IDs in order and keeps
completed, failed, and deliberately skipped IDs distinct, so duplicate live
delivery and ordinary history/reconnect replay cannot speak twice. An explicit
transcription start may enqueue the latest assistant utterance from canonical
chat history again, including a fresh agent's starting message. If the latest
utterance is from the user, or no assistant speech exists, nothing is spoken.
State-history selectors do not restrict this lookup. This intentional resume
delivery is repeatable on later starts and still synthesizes only the persisted
plan. Startup replay and subsequent live replies use the same saved Speech
voice, speed and selected output device; there is no separate reset voice or
speed configuration. Synthesis begins through the
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
`409`; unsupported voices, formats, or speeds outside `0.25` through `4.0` return
`400`. The defaults are `alloy`, `1.0` and `mp3`. The optional `format=pcm` requests
24 kHz mono signed 16-bit little-endian samples, returned as
`audio/pcm;rate=24000;channels=1;encoding=s16le`. Clients must explicitly support
that format. Other format values (including WAV) are not part of this endpoint's
contract. Talk to Me and requests omitting format continue using MP3.

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
| `GET` | `/admin/agent-types` | List registered `AgentDefinition` types with package metadata. |
| `GET` | `/admin/access-code-presets` | List backend-defined access-code presets. |
| `POST` | `/admin/access-code-presets/{presetKey}/apply` | Create a preset bundle transactionally. |
| `POST` | `/admin/access-codes` | Create an access code. |
| `GET` | `/admin/access-codes` | List access codes. |
| `PATCH` | `/admin/access-codes/{id}` | Enable or disable a code. |
| `PUT` | `/admin/access-codes/{id}/agent-types` | Replace assigned agent type keys. |
| `GET` | `/admin/access-codes/{id}/agents` | List agents linked to a code. |

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
  agentdefs/        Registered Valerian agent definitions.
  application/      Application services for agents, access codes, transcription, Speech, and scoped demos.
  controllers/      HTTP, SSE, admin, scoped demo, and static-client endpoints.
  logging/          SSE broadcasters.
  model/            Agent, state machine, event, behaviour, policy, regulation, and RPS domain model.
  spi/              Language-model, live-transcription, and Speech integration boundaries.

src/main/resources/public
  apiworkbench/     Guided REST/SSE API workbench for client developers.
  multilateral/     Meeting/group listener and report displays.
  talktome/         Public exact-text output-only Speech client.
  valerian/         Valerian cockpit.
  valerian-admin/   Valerian access management.

tests/playwright    Browser-level Valerian, Talk to Me, and API Workbench smoke tests.
```

## Developing New Agents

1. Start from an existing definition in `agentdefs/core` or
   `agentdefs/usecases/healthcare`.
2. Define prompts for state entry, response generation, transitions, actions,
   and outcome extraction.
3. Declare an `AgentInteractionProfile` that lists required observations and
   emitted behaviour modalities.
4. Implement `AgentDefinition` with a stable key and expose it as a Spring bean.
5. Use `createInstance(...)` for startup behaviour that should run immediately
   after scoped creation.
6. Add prompt/profile contract tests and update README/API examples if the
   public contract changes.

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

Need for Speed adds nullable internal `event.history_position` to preserve new
events' append order when database timestamps tie. With the configured Hibernate
`ddl-auto=update`, startup adds this column. For externally managed MySQL schemas,
apply the following before starting the upgraded writer:

```sql
ALTER TABLE `event` ADD COLUMN `history_position` BIGINT NULL;
```

Existing event IDs, payloads and timestamps remain intact;
legacy null-position rows retain their timestamp order and precede new rows.
Historical timestamp ties cannot be reconstructed. Upgrade writers together:
older writers do not assign positions. No database reset or event backfill is
required. This was verified only on a disposable local schema.

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

### Compatible transition checks

For the Heroku testing deployment, the speed improvements have these activation
rules (environment overrides take precedence over property files):

| Improvement | Activation |
| --- | --- |
| Combined speech/nonverbal generation | Automatic for compatible prompt policies requesting both channels. |
| Compact model response | Automatic for combined generation; expanded to canonical plans before publication. Timing exports identify compact versus canonical responses. |
| Combined transition decisions | Default `openai.guard-strategy=combined`; only eligible pure checks can share a request. |
| Purpose/model/effort routing | `openai-prod.properties` selects Luna for all five purposes at `none`; each purpose remains independently configurable and GPT-5.2 remains the fallback. |
| Progressive synthesized audio | Automatic when the browser supports MP3 MediaSource; otherwise buffered playback. No cockpit switch. |
| Shorter turn completion | Select local silence duration and provider transcription delay in the cockpit while stopped. Defaults remain 1.5 seconds and medium; saved choices apply on the next start. |
| Parallel guard evaluation | Server-side opt-in with `parallel` or `combined_parallel`; leave `combined` for the initial comparison, since speculative requests can increase work. |

Changing local silence from 1.0 to 0.5 seconds removes 500 ms of that waiting
window. It does not shorten provider transcription, model processing or synthesis,
and may split an utterance at a natural pause. The two-second end-to-end target
still needs live measurement; see the correlated timing instructions above.

`openai.guard-strategy=combined` groups known pure `StaticDecision`/`PromptPolicy`
checks from the active state path into one structured boolean request. Each check
retains its own selected history and resolved prompt. Java still applies outer,
transition-list and decision-list priority and executes selected actions once.
Local event-type filters can reject pure conjunctions before model inference.
Unknown state/decision subclasses, unconditional transitions and actions are
barriers; action execution invalidates all unused results. No cross-turn cache.

Groups require the same effective provider route and are bounded by
`openai.guard-batch-size` (16) and `openai.guard-max-characters` (65536 serialized
prompt characters); oversized/single checks use ordinary ordered calls. Provider
output-token limits still apply. Missing/extra IDs or non-boolean values fail the
whole group before actions. This is synchronous request composition, not the
OpenAI Batch API. Custom gateways remain ordered unless they opt in through
`guardInferenceOptions()`. Use `openai.guard-strategy=ordered` for comparison or a
deployment without strict structured outputs. Combining prompts can change model
judgments; live corpus quality remains a separate release check.

For explicit experiments, `openai.guard-strategy=parallel` evaluates separate
pure checks concurrently, and `combined_parallel` evaluates separate compatible
groups concurrently. Neither speculates on behaviour generation or actions.
Default limits are 4 global requests, 3 per turn, 16 waiting tasks, 5000 ms for
admission/queue waiting and 30000 ms per guard-evaluation session. Configure these
with `openai.guard-parallelism`, `guard-per-turn-parallelism`,
`guard-queue-capacity`, `guard-queue-wait-ms`, and `guard-turn-timeout-ms`.
All names use the `openai.` prefix. At most 64 candidates are prepared; subsequent
checks stay ordered. Admission reserves the entire group set within bounded
capacity; overload fails explicitly without redispatching the same work.

Results are consumed in logical priority order. A required failure/deadline
aborts evaluation; unneeded requests are cancelled on transition or turn end.
Already dispatched work may still be billed after cancellation. Provider timing
logs include request IDs and queue waits, but absent usage on cancellation is
unknown usage, not zero cost. Custom gateways need an explicitly supplied guard
executor and must be thread-safe to opt into parallel modes.

The application serializes each agent's start/acknowledge/generate/reset/tick
and scoped deletion operations, loading its aggregate after acquiring the lock.
Workers receive only immutable inference requests. Enclosing local transactions
retain the lock until commit/rollback. This is in-process serialization, not a
distributed lock across application instances. Scheduled ticks use the same
application boundary. Independent agents can proceed concurrently.

### Progressive canonical Speech playback

Valerian's Automatic format uses an AudioWorklet PCM renderer when a 24 kHz
AudioContext and the selected output device can be prepared. Preparation has a
two-second deadline and occurs before the provider request. Capability or setup
failure chooses MP3; no second synthesis request is used as a recovery mechanism.
PCM retains exact persisted speech, configured voice/speed and the media control's
volume/mute setting. The native file seek control is hidden during PCM output;
**Stop Playback** remains available.

PCM playback waits for 60 ms of audio samples (or a shorter complete response),
then consumes them as they arrive. The renderer holds at most two seconds of
audio, applies backpressure and preserves samples split across network chunks.
An underrun inserts silence and refills before continuing; confirmed interruptions
and inserted silence are reported. Final output drains using reported device
latency, with a 100 ms fallback when output latency is unavailable, before closing
the context and reopening input. Stream reads and stalled playback have a
30-second deadline, and each utterance is capped at 16 MiB. PCM uses more bandwidth
than MP3. See the [Web Audio specification](https://www.w3.org/TR/webaudio-1.1/).

MP3 uses the existing MediaSource path, with a same-response buffered fallback
if unsupported or setup fails. Stop aborts audio reading and renderer resources.
Failures after playback starts fail the item without resynthesis or prefix replay.
The shared queue owns ordering, cross-tab output ownership, replay suppression and
input gating for both paths. The backend flushes each provider chunk and closes
its upstream stream on downstream write failure.

Timing JSON/CSV records requested/effective format, fallback reason, audio
preparation duration, PCM prefill/initial buffer size, interruption count and
inserted silence. Buffer sizes are milliseconds of audio, not elapsed waiting
time. PCM audio_playing records the browser receiving notification of the first
samples consumed by the renderer; MP3 uses the media element playing event.
Neither measures physical audibility, and the two marker mechanisms can differ.
Compare end-to-end timing as well as preparation, first-byte and playback stages,
and listen for missing or clipped speech. Test startup separately from later turns.

PCM interruption detail is collected automatically in the same Interaction Timing
JSON export (`turn.pcm`, detail version 1). It records response-body read sizes and
waits, numbered blocks posted to/received by the renderer, producer backpressure,
and renderer start, buffer-empty, resume and finish positions. Preparation includes
sample rate and available browser-reported output latency. Stop/failure and EOF are
explicit. The timing drawer shows interruption positions; CSV includes retained and
dropped record counts, while JSON contains the full retained trace. Refresh the
cockpit after deployment, keep Ultra Responsive / Automatic selected, clear the old
timing log, and export JSON after the final reply has finished. No new switch is needed.

`at` uses browser `performance.now()`; worklet events are timestamped on receipt.
`contextTimeMs` samples the audio clock at that observation; `renderFrame` is the
actual AudioContext frame, and `playedFrames` counts source samples consumed,
excluding inserted silence. Divide frames by the recorded sample rate to obtain
seconds; do not subtract audio-clock values from browser timestamps. Matching
`block`/`chunk` IDs link posting and renderer receipt; that observation interval
includes both message directions. `bufferedFrames` is renderer occupancy, whereas
`outstandingFrames` also includes consumption not yet credited to the producer.
`readWaitMs` measures an awaited browser body read; gaps between reads also include
consumer backpressure, logged separately. These are not provider/network packet
timestamps. A `buffer_empty` event is provisional until `render_resume` confirms
`gapFrames`; waiting for EOF after the last sample is not a confirmed interruption.

Detail is content-free and bounded to 256 delivery and 64 renderer records per
retained turn, keeping the first half and latest half when full. Separate dropped
counts explicitly flag incomplete traces, so long replies cannot grow memory
without limit. Per-block delivery does not trigger panel rerenders. This adds
measurement only: the 60 ms prefill/refill and two-second queue remain unchanged.

Server audio delivery detail is also automatic for tracked live speech, including
MP3. Valerian requests `deliveryTiming=true` on the canonical speech POST; the
response returns `X-Prometheus-Speech-Delivery-Id`. After download or teardown, a
single background GET to
`/demo/agents/{agentId}/behaviours/{eventId}/speech/timing/{requestId}` retrieves
the matching trace using the existing access-code scope. This does not delay
playback or reopening the microphone. Other clients need not request diagnostics.
The endpoint returns 401 for invalid access, 404 for invisible/mismatched/missing
traces, and a no-store snapshot for an authorized match.

The existing JSON export includes `turn.speechDelivery`: retrieval status and the
server trace's pending/streaming/complete/error status, timed read/write/flush/close
operations, cumulative byte positions, final totals and any unfinished phase.
The drawer shows retrieval status and the longest recorded read/write/flush; CSV
adds diagnostic coverage fields. Wait for the final reply and confirm diagnostic
retrieval is **received** before exporting JSON. Failures, early teardown, expiry,
eviction or another server instance leave missing/incomplete evidence explicit.
Only fixed labels, IDs, counts and times are retained, never speech or audio.
Server storage holds at most 128 traces; entries expire after ten minutes and are
pruned on lookup/insertion. Each trace keeps 256 operations, preserving its first
and latest halves with a dropped count. Restart discards this diagnostic state.

Server times use a separate monotonic clock starting after provider headers, when
the trace is created. A long **read** locates waiting at the application's read of
the provider response; it cannot distinguish OpenAI generation from upstream
transport or HTTP-client buffering. Long **write/flush** operations expose waiting
while handing audio to the HTTP output. Prompt server writes followed by delayed
browser reads point toward downstream delivery or browser consumption. Match
`totalBytes` against browser PCM read positions even when chunk boundaries differ,
then compare intervals within each clock. Never subtract browser and server times;
flush completion alone does not establish browser receipt or physical audibility.

Native Chromium PCM/MP3 and provider-to-Tomcat streaming are tested with withheld
tails. Physical devices, Bluetooth, other browsers and Heroku/provider latency
remain deployment trial gates. OpenAI recommends WAV/PCM for fastest response
times, but this implementation does not establish a production latency gain:
[OpenAI Speech guidance](https://developers.openai.com/api/docs/guides/text-to-speech#streaming-realtime-audio).
