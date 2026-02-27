# PROMETHEUS + Unitree G1 Realtime Multimodal Guide (Python)

This guide explains how to run PROMETHEUS with a Unitree G1 setup where:
- the robot handles speech I/O through your own realtime client,
- visual sensing clients continuously send observations,
- PROMETHEUS coordinates multimodal context and behaviour planning,
- the robot executes both speech and arm gestures.

It is based on the integration flow in:
- `src/test/java/ch/zhaw/prometheus/integration/RealtimeSpeechBackendComplementReplayIntegrationTest.java`

## 1. Core idea for robot operation

Use a split-output pattern:

1. `REALTIME_SPEECH` profile for spoken output contract.
2. `BACKEND_COMPLEMENT` profile for non-speech complementary behaviour (gesture/motion/display channels).

This avoids the realtime LLM speaking raw JSON while still enabling multimodal behaviour.

## 2. Event model you will use

PROMETHEUS ingests everything as events (`POST /{agentId}/acknowledge`):

- user speech transcript:
  - `type=obs.user_utterance`, `actor=user`, `kind=observation`
- visual sensing (example):
  - `type=obs.emotion.face`, `actor=user`, `kind=observation`
- assistant speech produced by your realtime client:
  - `type=resp.behaviour_plan`, `actor=assistant`, `kind=response`
  - payload contains at least `{"speech":"..."}` for the uttered text

PROMETHEUS emits behaviour plans (`resp.behaviour_plan`) via:
- `GET /{agentId}/behaviour/stream` (SSE)

## 3. End-to-end runtime loop (robot + PROMETHEUS)

Assume `base_url = "http://localhost:8080"` and known `agentId`.

1. Start agent (once per session):
- `POST /{agentId}/start`

2. Get realtime prompt contract for your speech pipeline:
- `GET /{agentId}/prompt?profile=REALTIME_SPEECH`
- Use `promptMessages` to configure your realtime model/system instruction.

3. During interaction, send observations:
- speech transcript chunks/finals as `obs.user_utterance`
- visual signals (face emotion, social observations, etc.) as observation events

4. Your realtime client speaks a response through the robot speaker.

5. Immediately acknowledge spoken assistant utterance back to PROMETHEUS:
- `POST /{agentId}/acknowledge` with assistant `resp.behaviour_plan` payload `{"speech":"..."}`

6. If state transitions on `acknowledge` enter a `starting` state, PROMETHEUS may auto-generate assistant behaviour and publish it on SSE:
- `GET /{agentId}/behaviour/stream`
- if that behaviour contains `speech`, your realtime client can speak it out
- do not re-acknowledge that same SSE-originated behaviour as a new assistant event

7. Optionally ask PROMETHEUS for complementary non-speech behaviour:
- `POST /{agentId}/behaviour/generate`
- body: `{"outputProfile":"BACKEND_COMPLEMENT","omitModalities":["speech"]}`

8. Read latest emitted behaviour event from behaviour SSE:
- parse `payload` as `BehaviourPlan`
- use `nonVerbal` / `motion` fields to drive robot arm gestures

9. Repeat steps 3-8 continuously.

## 4. Request examples (Python-friendly JSON)

### 4.1 User utterance observation

```json
{
  "type": "obs.user_utterance",
  "actor": "user",
  "kind": "observation",
  "payload": "I am overwhelmed with two deadlines and feel tense."
}
```

### 4.2 Face emotion observation

```json
{
  "type": "obs.emotion.face",
  "actor": "user",
  "kind": "observation",
  "payload": "{\"emotion\":\"sad\",\"confidence\":0.92,\"valence\":-0.70,\"arousal\":0.61}"
}
```

### 4.3 Assistant speech acknowledgment (from your realtime client)

```json
{
  "type": "resp.behaviour_plan",
  "actor": "assistant",
  "kind": "response",
  "payload": "{\"speech\":\"That sounds heavy. Let us choose one deadline first and make a tiny plan.\"}"
}
```

### 4.4 Generate complementary behaviour

```json
{
  "outputProfile": "BACKEND_COMPLEMENT",
  "omitModalities": ["speech"]
}
```

## 5. Expected behaviour output

From `/behaviour/stream`, expect events like:

```json
{
  "type": "resp.behaviour_plan",
  "actor": "assistant",
  "kind": "response",
  "payload": "{\"speech\":null,\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"},\"motion\":null,\"display\":null}"
}
```

For robot execution:
- ignore `speech` if null (already spoken by realtime pipeline),
- map `nonVerbal.gesture` (and later `motion`) to Unitree arm animation primitives.

## 6. Mapping to the integration test

See:
- `src/test/java/ch/zhaw/prometheus/integration/RealtimeSpeechBackendComplementReplayIntegrationTest.java`
- `src/main/resources/scripts/realtime-speech-backend-complement-replay-script.json`

What that test proves:
- `REALTIME_SPEECH` prompt contract is returned,
- multimodal context (speech + face emotion) is present in prompt history,
- assistant realtime speech is acknowledged,
- `BACKEND_COMPLEMENT` generation returns nonverbal-only plan (`speech == null`).

Use it as a reference sequence for your Python orchestrator.

## 7. Minimal Python orchestration sketch

```python
import requests

base = "http://localhost:8080"
agent_id = "<uuid>"

def post(path, body=None):
    return requests.post(f"{base}{path}", json=body, timeout=3)

def get(path):
    return requests.get(f"{base}{path}", timeout=3)

# 1) Start
post(f"/{agent_id}/start")

# 2) Prompt for realtime speech contract
prompt = get(f"/{agent_id}/prompt?profile=REALTIME_SPEECH").json()

# 3) Send user speech + visual observations
post(f"/{agent_id}/acknowledge", {
    "type": "obs.user_utterance", "actor": "user", "kind": "observation",
    "payload": "I am overwhelmed with two deadlines and feel tense."
})
post(f"/{agent_id}/acknowledge", {
    "type": "obs.emotion.face", "actor": "user", "kind": "observation",
    "payload": "{\"emotion\":\"sad\",\"confidence\":0.92}"
})

# 4) Your realtime client speaks text -> acknowledge it
spoken = "That sounds heavy. Let us choose one deadline first and make a tiny plan."
post(f"/{agent_id}/acknowledge", {
    "type": "resp.behaviour_plan", "actor": "assistant", "kind": "response",
    "payload": f"{{\"speech\":\"{spoken}\"}}"
})

# 5) Optionally ask PROMETHEUS for complementary non-speech behaviour
post(f"/{agent_id}/behaviour/generate", {
    "outputProfile": "BACKEND_COMPLEMENT",
    "omitModalities": ["speech"]
})

# 6) Read /{agentId}/behaviour/stream SSE and execute gestures on Unitree
```

## 8. Practical notes for Unitree G1

- Keep speech and gesture controllers decoupled:
  - speech timing from realtime client,
  - gesture timing from PROMETHEUS behaviour stream.
- If no prior assistant speech exists, `BACKEND_COMPLEMENT` may produce no event (expected).
- Entering a `starting` state during `acknowledge` can emit behaviour immediately on SSE (including speech).
- Start with deterministic gesture mapping table:
  - `OPEN_QUESTION`, `ACKNOWLEDGE`, `EXPLAIN`, `POLITE`, `UNCERTAIN`, `NONE`
- Use monitor stream (`/{agentId}/monitor/stream`) for debugging state and transitions.
