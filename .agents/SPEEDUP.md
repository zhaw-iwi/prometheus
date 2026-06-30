# Valerian Cockpit Verbal-Turn Speedup Transfer Specification

This document is a self-contained implementation brief for a Codex agent working
in the sibling repository:

```text
zhaw-iwi/valerian.git
local checkout expected at ../valerian from this PROMETHEUS repository
```

The goal is to update the external Valerian cockpit so it benefits from the
latest PROMETHEUS verbal-turn latency improvements for text and speech
interactions.

## Source Of The Specification

The source implementation is PROMETHEUS commit:

```text
670b5154f24595f2537e503478f762b5949ed3e5
Reduce verbal turn latency
```

Relevant PROMETHEUS files:

```text
src/main/java/ch/zhaw/prometheus/application/AgentApplicationService.java
src/main/java/ch/zhaw/prometheus/application/ScopedDemoService.java
src/main/java/ch/zhaw/prometheus/controllers/AgentControllerRealtime.java
src/main/java/ch/zhaw/prometheus/controllers/ScopedDemoController.java
src/main/java/ch/zhaw/prometheus/application/RealtimeSidebandService.java
src/main/java/ch/zhaw/prometheus/spi/OpenAIProperties.java
src/main/resources/openai.properties.template
src/main/resources/public/valerian/script.js
src/test/java/ch/zhaw/prometheus/controllers/ValerianClientStaticResourceContractTest.java
README.md
PROJECT.md
```

Relevant target Valerian files, as of local inspection:

```text
apps/valerian-cockpit/static/app.js
apps/valerian-cockpit/tests/test_static_server.py
apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js
apps/valerian-cockpit/smoke/cockpit_idless_duplicate_behaviour_smoke.js
apps/valerian-cockpit/smoke/cockpit_rps_hand_delegation_smoke.js
apps/valerian-cockpit/smoke/cockpit_rps_manual_smoke.js
apps/valerian-cockpit/smoke/cockpit_stream_reconnect_smoke.js
apps/valerian-cockpit/README.md
README.md
PROJECT.md
```

Adapt file names if the target repository has moved code.

## Required PROMETHEUS Backend Baseline

The external cockpit speedup depends on PROMETHEUS being upgraded to commit
`670b515` or a later commit that includes the same public API and sideband
behavior.

Required backend behavior:

- `POST /demo/agents/{agentId}/acknowledge-and-generate`
- optional query parameter `?profile=realtime_speech`
- same request body shape as `/acknowledge`
- response body shape `ResponseView` with `responseEvent` and `active`
- `responseEvent` may be a `resp.behaviour_plan` event whose payload contains
  speech
- `POST /demo/agents/{agentId}/behaviour/generate` accepts
  `{"outputProfile":"backend_complement"}`
- Realtime sideband processes accepted speech transcripts through the combined
  backend turn path with `REALTIME_SPEECH`
- PROMETHEUS exposes or defaults `openai.realtimeTranscriptBatchDelayMs = 400`

Do not implement these backend features in the Valerian repository. Valerian is
a browser cockpit and robot-server monorepo. It should call the upgraded
PROMETHEUS API and document the backend version requirement.

If old PROMETHEUS deployments must remain usable, add an explicit compatibility
fallback only for `404` or `405` from `/acknowledge-and-generate`, log it in the
activity log, and keep the fast path as the default. If compatibility is not in
scope, fail visibly and document that PROMETHEUS must be upgraded.

## What Changed In PROMETHEUS

PROMETHEUS reduced verbal-turn latency in three places:

1. Backend service fast path
   - Added `AgentApplicationService.acknowledgeAndGenerate(...)`.
   - It loads the agent once, acknowledges the user event, and if no transition
     produced a response, immediately generates a behavior in the same backend
     turn and persistence cycle.

2. Scoped API fast path
   - Added global endpoint:

```text
POST /{agentID}/acknowledge-and-generate
```

   - Added scoped Valerian endpoint:

```text
POST /demo/agents/{agentId}/acknowledge-and-generate
```

3. Client and sideband speech-first profile
   - Text turns use `acknowledge-and-generate?profile=realtime_speech`.
   - Returned speech is rendered immediately.
   - Non-speech behavior is requested afterward via `backend_complement`.
   - Realtime sideband speech transcripts use the same combined backend path.
   - Transcript batching delay was reduced from 900 ms to 400 ms.

The external Valerian cockpit needs the browser-side pieces. The speech backend
speedup mainly comes from pointing the cockpit at an upgraded PROMETHEUS server.

## Target Goal

Update the external Valerian cockpit so:

- text input no longer calls `/acknowledge` and then `generateBehaviour("full_plan")`
  for ordinary verbal turns
- text input calls `/acknowledge-and-generate?profile=realtime_speech`
- text speech from the HTTP response renders immediately through the existing
  behavior-plan rendering path
- nonverbal complement behavior is requested afterward using
  `generateBehaviour("backend_complement")`
- speech transcript UI batching uses 400 ms to match PROMETHEUS' lower latency
  setting
- manual observations, weather observations, camera/detector observations, and
  RPS hand-sign observations continue using plain `/acknowledge`
- tests, smoke mocks, README, and `PROJECT.md` are updated in the same
  Valerian milestone

## Non-Goals

Do not include these unless separately requested:

- changing robot-server behavior
- changing robot gesture or hand-sign delegation
- changing PROMETHEUS backend Java code from the Valerian repository
- replacing the existing WebRTC `/realtime/call` flow
- adding new visual UI panels
- rewriting transcript, behavior, or dispatch architecture
- making live Heroku smoke tests mandatory for automated validation

## Target Repository Startup Instructions

Before editing the Valerian repository:

1. Change to the sibling repository:

```powershell
cd ..\valerian
```

2. Read the target repository's project instructions and history:

```powershell
Get-Content -Raw .agents/CONTEXT.md
Get-Content -Raw .agents/CODEX.md
Get-Content -Raw PROJECT.md
```

If one of these files is missing or has a different name, inspect the available
`.agents` files, `README.md`, and `PROJECT.md` before editing.

3. Check the worktree:

```powershell
git status --short --branch
```

Preserve user changes. Do not revert unrelated files.

4. Treat this as a Valerian milestone. Update Valerian `PROJECT.md` at the end.

## Current Target Code Shape

At the time this guide was written, the external cockpit has this text path in:

```text
apps/valerian-cockpit/static/app.js
```

Current behavior:

```javascript
async function sendText() {
  ...
  const data = await acknowledgeUserUtterance(text);
  if (data && !data.responseEvent) {
    await generateBehaviour("full_plan");
  }
}

async function acknowledgeUserUtterance(text) {
  ...
  const response = await scopedFetch(demoAgentPath("/acknowledge"), {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify(request),
  });
  ...
  handleResponseEvent(data.responseEvent);
  return data;
}
```

Current speech transcript display batching:

```javascript
const TRANSCRIPT_BATCH_DELAY_MS = 900;
```

There are other `/acknowledge` calls for manual observations, weather, sensing,
and RPS. Those are observation-only flows and should not be switched to
`acknowledge-and-generate`.

## Implementation Plan

### 1. Lower Browser Transcript Batch Delay

In `apps/valerian-cockpit/static/app.js`, change:

```javascript
const TRANSCRIPT_BATCH_DELAY_MS = 900;
```

to:

```javascript
const TRANSCRIPT_BATCH_DELAY_MS = 400;
```

This affects the cockpit's local display/projection of Realtime transcript
candidates. The actual speech-response latency improvement comes from the
upgraded PROMETHEUS sideband, but keeping the browser gate at 400 ms keeps the
operator readout aligned with the backend behavior.

### 2. Add Speech Detection For Response Events

Add a small helper near `handleResponseEvent(...)`, `parseBehaviourPlan(...)`,
or `acknowledgeUserUtterance(...)`.

Recommended implementation using the existing parser:

```javascript
function responseEventHasSpeech(event) {
  if (!event || event.type !== "resp.behaviour_plan") {
    return false;
  }
  const plan = parseBehaviourPlan(event.payload);
  return !!(plan && typeof plan.speech === "string" && plan.speech.trim());
}
```

If `parseBehaviourPlan` is not in scope after future refactors, parse the JSON
payload defensively and return false on parse errors.

### 3. Switch Text Turns To Acknowledge-And-Generate

Change `acknowledgeUserUtterance(text)` so the text path calls:

```text
POST /demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech
```

with the same event body:

```json
{
  "type": "obs.user_utterance",
  "actor": "user",
  "kind": "observation",
  "payload": "<text>"
}
```

The access-code header behavior must remain unchanged because `scopedFetch`
already owns it.

Recommended direct change:

```javascript
const response = await scopedFetch(
  demoAgentPath("/acknowledge-and-generate?profile=realtime_speech"),
  {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify(request),
  }
);
```

Alternatively, if you want a reusable helper:

```javascript
function acknowledgementPath(options = {}) {
  const endpoint = options.generateIfNoResponse ? "/acknowledge-and-generate" : "/acknowledge";
  const query = options.profile ? `?profile=${encodeURIComponent(options.profile)}` : "";
  return demoAgentPath(`${endpoint}${query}`);
}
```

Use that helper only where it improves clarity. Do not route all observation
acknowledgements through the fast path by accident.

### 4. Request Backend Complement After Returned Speech

Change `sendText()` so it no longer calls `generateBehaviour("full_plan")` when
the acknowledgement response has no behavior.

New text flow:

```javascript
const data = await acknowledgeUserUtterance(text);
if (data && responseEventHasSpeech(data.responseEvent)) {
  void generateBehaviour("backend_complement");
}
```

Important behavior:

- `acknowledgeUserUtterance(...)` should still call
  `handleResponseEvent(data.responseEvent)` before returning.
- The HTTP response speech should render immediately through the existing
  `handleResponseEvent -> handleBehaviourEnvelope -> renderBehaviourPlan`
  path.
- `generateBehaviour("backend_complement")` should be fire-and-forget or at
  least not block visible speech rendering.
- Do not request `full_plan` as a fallback after the fast-path call. That would
  reintroduce the extra full generation path the speedup removed.
- `backend_complement` should request non-speech behavior only. PROMETHEUS will
  omit speech for this profile.

### 5. Preserve Observation-Only Acknowledgements

Leave these target cockpit flows on plain `/acknowledge`:

- `sendManualObservation(...)`
- `sendWeatherObservation(...)`
- browser camera/detector observation sends
- manual RPS hand-sign fallback observations
- any observation where the user is not expecting immediate verbal assistant
  speech

Reason: the fast path is for visible verbal turns. Sensing events can trigger
state changes in PROMETHEUS, but they should not all force fallback speech
generation.

Search the target repository for:

```powershell
rg -n "demoAgentPath\\(\"/acknowledge\"|/acknowledge|generateBehaviour\\(\"full_plan\"|TRANSCRIPT_BATCH_DELAY_MS" apps/valerian-cockpit
```

After the change, it is valid for `/acknowledge` to remain in observation-only
flows. `generateBehaviour("full_plan")` should not remain in the text-submit
fallback path.

### 6. Keep Realtime Speech Call Flow Unchanged

The external cockpit's Speech tab should continue using:

```text
POST /demo/agents/{agentId}/realtime/call
Content-Type: application/sdp
```

Do not make the browser send Realtime speech transcripts to
`/acknowledge-and-generate`. PROMETHEUS' backend sideband now owns that path.

Expected Speech-tab changes in Valerian:

- lower `TRANSCRIPT_BATCH_DELAY_MS` to `400`
- update docs to require PROMETHEUS `670b515` or newer for lower speech
  response latency
- keep existing query parameters such as `generateComplement`, VAD settings,
  audio tuning, and barge-in behavior unchanged

## Test Updates

Update Valerian tests in:

```text
apps/valerian-cockpit/tests/test_static_server.py
```

Minimum assertions to add or update:

- text flow contains `acknowledge-and-generate`
- text flow contains `profile=realtime_speech`
- text flow contains `generateBehaviour("backend_complement")`
- text flow contains `responseEventHasSpeech`
- text flow still contains plain `/acknowledge` for observation-only flows
- text flow no longer asserts `generateBehaviour("full_plan")` as the text
  fallback
- `TRANSCRIPT_BATCH_DELAY_MS = 400`
- Realtime call flow still contains
  `demoAgentPath(\`/realtime/call?${params.toString()}\`)`
- Realtime call flow still sends SDP with `Content-Type: application/sdp`
- Realtime call flow still forwards `generateComplement`

Be careful with broad string assertions. Since `/acknowledge` remains valid for
manual/weather/sensing observations, do not assert that the whole file lacks
`/acknowledge`.

Recommended smoke script changes:

- Update mock PROMETHEUS servers in smoke scripts that drive text input to
  handle:

```text
POST /demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech
```

- Return a `ResponseView` with immediate assistant speech, for example:

```json
{
  "active": true,
  "responseEvent": {
    "id": "behaviour-fast-1",
    "type": "resp.behaviour_plan",
    "actor": "assistant",
    "kind": "response",
    "payload": "{\"speech\":\"Fast response.\"}"
  }
}
```

- Continue handling `POST /demo/agents/{agentId}/behaviour/generate`.
- For complement requests, verify the body contains:

```json
{"outputProfile":"backend_complement"}
```

- Keep smoke scripts for manual observations expecting plain
  `/demo/agents/{agentId}/acknowledge`.

When matching URLs in Node smoke servers, prefer parsing the URL so the query
string does not make exact path checks brittle:

```javascript
const parsedUrl = new URL(req.url, "http://127.0.0.1");
if (parsedUrl.pathname === `/demo/agents/${agentId}/acknowledge-and-generate`
    && parsedUrl.searchParams.get("profile") === "realtime_speech"
    && req.method === "POST") {
  ...
}
```

## Documentation Updates In Valerian

Update at least:

```text
apps/valerian-cockpit/README.md
PROJECT.md
```

Update root `README.md` too if it has a cockpit behavior or setup section that
mentions text interaction, PROMETHEUS compatibility, or speech latency.

Suggested operator documentation:

```text
Text turns use PROMETHEUS' speech-first combined turn path. The cockpit sends
user text to /demo/agents/{agentId}/acknowledge-and-generate with
profile=realtime_speech, renders returned speech immediately, then asks
PROMETHEUS for backend_complement nonverbal behavior. This requires a
PROMETHEUS backend that includes the Reduce verbal turn latency milestone.
```

Suggested speech documentation:

```text
Speech-to-speech still starts through /demo/agents/{agentId}/realtime/call. The
lower speech response latency comes from PROMETHEUS' backend sideband, which
uses the combined acknowledge-and-generate path for accepted transcripts and a
400 ms transcript batch delay. The cockpit keeps its local transcript display
batching at 400 ms to match.
```

Add a Valerian `PROJECT.md` milestone entry with:

- date
- goal
- what changed
- how to run
- how to test
- known issues and decisions
- next steps

## Verification Commands

Run the target repository's normal checks. Based on the current Valerian
repository, run:

```powershell
python -m pytest apps/valerian-cockpit/tests/test_static_server.py
python -m compileall apps/valerian-cockpit
node --check apps/valerian-cockpit/static/app.js
```

If Node is unavailable locally, report that explicitly and still run the Python
checks.

If smoke scripts were changed, syntax-check them:

```powershell
node --check apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js
node --check apps/valerian-cockpit/smoke/cockpit_idless_duplicate_behaviour_smoke.js
node --check apps/valerian-cockpit/smoke/cockpit_rps_hand_delegation_smoke.js
node --check apps/valerian-cockpit/smoke/cockpit_rps_manual_smoke.js
node --check apps/valerian-cockpit/smoke/cockpit_stream_reconnect_smoke.js
```

Optional browser smoke, with the cockpit server running:

```powershell
python apps/valerian-cockpit/valerian_cockpit_server.py --host 127.0.0.1 --port 5010
node apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js http://127.0.0.1:5010
```

## Manual Acceptance Test

Use a PROMETHEUS server at commit `670b515` or newer.

1. Start PROMETHEUS.
2. Start Valerian cockpit:

```powershell
python apps/valerian-cockpit/valerian_cockpit_server.py --host 127.0.0.1 --port 5010
```

3. Open `http://127.0.0.1:5010/`.
4. Set the PROMETHEUS host.
5. Enter an access code and connect a verbal agent.
6. Send a text utterance.
7. Confirm the browser network panel shows:

```text
POST /demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech
```

8. Confirm assistant speech appears immediately from the HTTP response.
9. Confirm a follow-up request is sent:

```text
POST /demo/agents/{agentId}/behaviour/generate
{"outputProfile":"backend_complement"}
```

10. Confirm nonverbal behavior, if produced, arrives through the behavior
    stream without duplicating speech.
11. Send manual weather, social, emotion, or hand-sign observations and confirm
    they still call plain `/acknowledge`.
12. Start Speech mode and confirm Realtime still uses `/realtime/call`.
13. Speak to the agent and confirm speech responses benefit from the upgraded
    PROMETHEUS backend sideband. The cockpit should not send transcript
    acknowledgements itself.

## Risks And Decisions

- The speedup requires an upgraded PROMETHEUS backend. Against older
  PROMETHEUS servers, `/acknowledge-and-generate` will fail unless a
  compatibility fallback is deliberately implemented.
- `backend_complement` is asynchronous from the user's perspective. Speech is
  prioritized; physical behavior can arrive shortly afterward.
- Some text turns may legitimately return no speech, for example if the agent
  is inactive or the backend cannot generate. Do not mask this with an
  automatic `full_plan` fallback unless product owners explicitly request old
  behavior.
- Lowering transcript batching to 400 ms is a latency/noise tradeoff. The
  cockpit already has duplicate, pending item, and assistant-echo gates; keep
  those gates intact.
- Observation-only sensing events should not force speech generation. Preserve
  plain `/acknowledge` for those paths.

## Completion Checklist

The Valerian milestone is complete when:

- text input uses `/acknowledge-and-generate?profile=realtime_speech`
- returned behavior speech renders immediately
- complement behavior uses `backend_complement`
- text fallback no longer requests `full_plan`
- speech transcript display batching is 400 ms
- manual/weather/camera/RPS observations still use plain `/acknowledge`
- Realtime speech still uses `/realtime/call`
- static tests and smoke mocks cover the new request contract
- docs mention the PROMETHEUS backend prerequisite and the new text/speech
  speedup behavior
- Valerian `PROJECT.md` has a milestone entry
- relevant tests and syntax checks pass, or unavailable checks are explicitly
  reported
- stop for review and commit
