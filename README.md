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

All clients take `?agentId=<uuid>`.
For the complete list including multilateral endpoints, see `All Client Endpoints` below.

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
- Optional realtime: `openai.realtimeModel`, `openai.realtimeSessionUrl`, `openai.realtimeUrl`

### 3. Run

```bash
./mvnw spring-boot:run
```

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

App default URL: `http://localhost:8080`

## Seed or Create Agents

### Option A: Seed example agents from tests

Templates:

- `src/test/java/ch/zhaw/prometheus/agents/SingleStateGuessingGame.java` - Single-state guessing game with guided prompt flow.
- `src/test/java/ch/zhaw/prometheus/agents/SingleStateMicroCoaching.java` - Single-state supportive micro-coaching agent.
- `src/test/java/ch/zhaw/prometheus/agents/SingleStateCoCreation.java` - Single-state collaborative co-creation conversation.
- `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare/SingleStateTherapyAppointmentReminder.java` - GIGI elderly-care therapy appointment reminder with resistance-aware coaching.
- `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare/SingleStateGuessingGame.java` - GIGI elderly-care guessing game where GIGI gives clues.
- `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare/SingleStateGuessingGameUserGuess.java` - GIGI elderly-care yes/no guessing game where the user guesses GIGI's secret item.
- `src/test/java/ch/zhaw/prometheus/agents/gigielderlycare/SingleStateSmartGoalCoaching.java` - GIGI elderly-care SMART goal coaching for small wellbeing steps.
- `src/test/java/ch/zhaw/prometheus/agents/FourStatesLinear.java` - Four-state linear progression with explicit stage transitions.
- `src/test/java/ch/zhaw/prometheus/agents/FourStatesCircular.java` - Four-state circular loop for iterative dialogue cycles.
- `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalIn.java` - Single-state micro-coaching with multimodal sensing inputs.
- `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalOut.java` - Single-state interaction with deterministic multimodal behaviour output.
- `src/test/java/ch/zhaw/prometheus/agents/multimodal/SingleStateMultimodalInOut.java` - Single-state interaction combining multimodal sensing and multimodal behaviour output.

Some templates are marked `@Disabled("Manual seed test")` and some are directly runnable. To use them:

1. Copy one class and remove `@Disabled`, or create your own seed test from it.
2. Run the test once.
3. The test saves an initialized agent in the database.
4. List agents via `GET /agent` and use the returned UUID.

### Option B: Create a simple single-state agent via REST

Use `POST /agent/singlestate` with `SingleStateAgentCreateDTO` shape (see `src/main/java/ch/zhaw/prometheus/controllers/dto/SingleStateAgentCreateDTO.java`).

## All Client Endpoints

All clients take `?agentId=<uuid>`.

- Chat client (text-to-text): `http://localhost:8080/?agentId=<uuid>`
- Realtime voice client (speech-to-speech): `http://localhost:8080/realtime/?agentId=<uuid>`
- Agent monitor: `http://localhost:8080/monitor/?agentId=<uuid>`
- Visual facial detector: `http://localhost:8080/visual/facial/?agentId=<uuid>`
- Visual multifacial detector: `http://localhost:8080/visual/multifacial/?agentId=<uuid>`
- Visual social detector: `http://localhost:8080/visual/social/?agentId=<uuid>`
- Nonverbal behaviour renderer: `http://localhost:8080/nonverbal/?agentId=<uuid>`
- Multilateral listen: `http://localhost:8080/multilateral/listen/?agentId=<uuid>`
- Multilateral reports: `http://localhost:8080/multilateral/reports/?agentId=<uuid>`

## API Overview

### Agent metadata

- `GET /agent`
- `GET /agent/{id}`
- `GET /agent/eventhistory`
- `GET /agent/{id}/eventhistory`
- `POST /agent/singlestate`

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
- `POST /realtime/session`

### Streaming (SSE)

- `GET /{agentID}/monitor/stream`
- `GET /{agentID}/behaviour/stream`
- `POST /{agentID}/behaviour/generate` (optional body: `omitModalities`, `outputProfile`)
- `GET /logs/stream`
- SSE publish failures are isolated from main HTTP endpoint handling and scheduler tick processing.
- Browser clients close EventSource streams on page unload and use one reconnect timer per stream with bounded exponential backoff and jitter on disconnect.
- Monitor client log and behaviour panes use bounded in-memory buffers to avoid unbounded growth during long sessions or repeated stream failures.

## Developer Workflow for New Agents

1. Start from `SingleStateMicroCoaching` or `SingleStateMultimodalInOut` test templates.
2. Define prompts for outer state, inner state(s), transition decisions, and actions.
3. Use `Storage` keys for extracted values consumed by later states.
4. For multimodal behaviour, include nonverbal policy prompts (`PromptPolicy#setNonVerbalPlanPrompt` and optional `PromptPolicy#setNonVerbalGesturePrompt` fallback) and ingest nonverbal events via `/acknowledge`.
5. Seed the agent, run app, then iterate using Monitor and behaviour streams.
6. Add or adapt controller DTOs and endpoints when you need reusable agent creation APIs beyond `/agent/singlestate`.

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

Notes:
- `/{agentID}/acknowledge` may already return a `responseEvent` (for example when a transition enters a starting state).
- `/{agentID}/behaviour/generate` can be called in final states; final-state prompts may still produce behaviour while `active=false`.

## Realtime Notes

- Browser obtains an ephemeral secret from `POST /realtime/session`.
- Realtime client sends transcript-derived events to `/{agentID}/acknowledge`.
- PROMETHEUS returns orchestration prompt bundles via `/{agentID}/prompt`.
  - Use `/{agentID}/prompt?profile=REALTIME_SPEECH` for OpenAI Realtime instruction setup to avoid JSON-style behaviour-plan output.
- Assistant outputs are stored as behaviour-plan events.
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
