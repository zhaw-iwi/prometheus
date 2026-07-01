# Valerian Cockpit Speedup Compatibility Handoff

This document is for a Codex agent working in the external repository:

```text
https://github.com/zhaw-iwi/valerian
```

It explains how to adapt `apps/valerian-cockpit` to the PROMETHEUS backend speedup branch created in this repository on `feature/speedup`.

The external Valerian repository was inspected at:

```text
branch: main
commit: f0b364cb87099416f253c274a4920da0929079ee
```

## Goal

Make Valerian Cockpit use the new PROMETHEUS verbal-turn fast path for browser text interactions while keeping the existing robot/sensing behaviour intact.

The important backend change in PROMETHEUS is:

```text
POST /demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech
```

This endpoint does both operations in one backend turn:

1. acknowledges the incoming event
2. if acknowledge itself produced no behaviour, generates a fallback behaviour immediately

It returns the same `ResponseView` shape as `/acknowledge`:

```json
{
  "responseEvent": {
    "type": "resp.behaviour_plan",
    "actor": "assistant",
    "kind": "response",
    "payload": "{\"speech\":\"...\"}"
  },
  "active": true
}
```

The intended fast Valerian text flow is:

1. User sends text.
2. Cockpit posts `obs.user_utterance` to `acknowledge-and-generate?profile=realtime_speech`.
3. Cockpit immediately renders `responseEvent.payload.speech` if present.
4. Cockpit requests non-speech backend complement separately:

```text
POST /demo/agents/{agentId}/behaviour/generate
{"outputProfile":"backend_complement"}
```

5. Complement behaviour arrives through the existing behaviour SSE/history catch-up path and is dispatched/rendered as usual.

Do not use `full_plan` for the fast text path. `FULL_PLAN` can require an additional model call for nonverbal output before speech is visible.

## Why Valerian Is Affected

The current Valerian Cockpit text flow still uses the older two-request pattern:

File:

```text
apps/valerian-cockpit/static/app.js
```

Current relevant functions:

```text
sendText()
acknowledgeUserUtterance(text)
generateBehaviour(outputProfile)
```

Current behaviour:

```text
POST /demo/agents/{agentId}/acknowledge
if no responseEvent:
  POST /demo/agents/{agentId}/behaviour/generate
  {"outputProfile":"full_plan"}
```

This remains backward-compatible with PROMETHEUS, but it misses the new speedup:

- it has two HTTP/service turns for ordinary text input
- it asks for `full_plan`, which can delay visible speech until nonverbal planning has completed

The Realtime speech tab is less affected on the browser side. PROMETHEUS owns Realtime transcript ingestion and exact-speech sideband generation. Once the backend is upgraded, Realtime speech-to-speech benefits mostly without Valerian browser code changes. The browser-side transcript batching constant can still be lowered for UI parity.

## Backend Contract To Target

Implement against these PROMETHEUS endpoints.

### Fast Text Endpoint

```http
POST /demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech
X-Prometheus-Access-Code: <access code>
Content-Type: application/json; charset=utf-8
```

Request body:

```json
{
  "type": "obs.user_utterance",
  "actor": "user",
  "kind": "observation",
  "payload": "Hello"
}
```

Response body:

```json
{
  "responseEvent": {
    "type": "resp.behaviour_plan",
    "actor": "assistant",
    "kind": "response",
    "payload": "{\"speech\":\"Hello, I am listening.\"}"
  },
  "active": true
}
```

The profile query value may be lowercase with underscore:

```text
realtime_speech
```

PROMETHEUS normalizes it to `REALTIME_SPEECH`.

### Complement Endpoint

```http
POST /demo/agents/{agentId}/behaviour/generate
X-Prometheus-Access-Code: <access code>
Content-Type: application/json; charset=utf-8
```

Request body:

```json
{
  "outputProfile": "backend_complement"
}
```

The complement response itself is only an HTTP status. The actual non-speech `resp.behaviour_plan` arrives through the existing behaviour SSE stream or event-history catch-up. Existing `generateBehaviour(outputProfile)` can be reused with `"backend_complement"`.

### Existing Endpoints Remain Valid

The old endpoint remains valid for manual observations, weather observations, detector observations, and backward compatibility:

```text
POST /demo/agents/{agentId}/acknowledge
```

Do not migrate non-text observations to `acknowledge-and-generate`. They are not ordinary verbal turns and should keep their current behaviour.

## Exact Valerian Files To Change

Primary implementation:

```text
apps/valerian-cockpit/static/app.js
```

Tests/contracts:

```text
apps/valerian-cockpit/tests/test_static_server.py
apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js
apps/valerian-cockpit/smoke/cockpit_idless_duplicate_behaviour_smoke.js
apps/valerian-cockpit/smoke/cockpit_stream_reconnect_smoke.js
apps/valerian-cockpit/smoke/cockpit_rps_hand_delegation_smoke.js
```

Optional docs:

```text
apps/valerian-cockpit/README.md
PROJECT.md
```

Only update `PROJECT.md` if the Valerian repository follows the same milestone audit-trail discipline for this change.

## Implementation Plan

### 1. Lower Browser Transcript Batch Delay

In `apps/valerian-cockpit/static/app.js`, change:

```js
const TRANSCRIPT_BATCH_DELAY_MS = 900;
```

to:

```js
const TRANSCRIPT_BATCH_DELAY_MS = 400;
```

This only affects browser-side transcript display gating. The Realtime backend latency is controlled by PROMETHEUS via:

```properties
openai.realtimeTranscriptBatchDelayMs=400
```

Still make the browser change for consistency with the PROMETHEUS cockpit.

### 2. Add A Behaviour Speech Helper

Add a small helper near `acknowledgeUserUtterance` or near the behaviour parsing helpers:

```js
function eventHasSpeech(event) {
  if (!event || event.type !== "resp.behaviour_plan" || !event.payload) {
    return false;
  }
  try {
    const plan = JSON.parse(event.payload);
    return typeof plan.speech === "string" && plan.speech.trim().length > 0;
  } catch (_) {
    return false;
  }
}
```

Use this helper only to decide whether to request backend complement after the fast text response.

### 3. Change `sendText()` To Use Speech-First Flow

Current logic is approximately:

```js
const data = await acknowledgeUserUtterance(text);
if (data && !data.responseEvent) {
  await generateBehaviour("full_plan");
}
```

Replace it with:

```js
const data = await acknowledgeUserUtterance(text);
if (data?.legacyTextFlow) {
  if (!data.responseEvent) {
    await generateBehaviour("full_plan");
  }
  return;
}
if (eventHasSpeech(data?.responseEvent)) {
  void generateBehaviour("backend_complement");
}
```

Rationale:

- New backend: speech should already be returned by `acknowledge-and-generate`.
- Complement generation should not block transcript rendering.
- If a legacy PROMETHEUS backend is used and the new endpoint is not available, the fallback keeps the old flow working.

If you decide not to support legacy fallback, simplify to:

```js
const data = await acknowledgeUserUtterance(text);
if (eventHasSpeech(data?.responseEvent)) {
  void generateBehaviour("backend_complement");
}
```

The fallback is recommended because Valerian may be used against deployed PROMETHEUS instances that have not yet merged the speedup branch.

### 4. Change `acknowledgeUserUtterance(text)`

Current target endpoint:

```js
demoAgentPath("/acknowledge")
```

New fast target:

```js
`${demoAgentPath("/acknowledge-and-generate")}?profile=realtime_speech`
```

Recommended implementation shape:

```js
async function acknowledgeUserUtterance(text) {
  const request = {
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload: text,
  };
  try {
    const response = await scopedFetch(`${demoAgentPath("/acknowledge-and-generate")}?profile=realtime_speech`, {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(request),
    });
    if (response.status === 404 || response.status === 405) {
      return acknowledgeUserUtteranceLegacy(text, request);
    }
    if (!response.ok) {
      throw new Error(`acknowledge-and-generate failed: ${response.status}`);
    }
    const data = await response.json();
    setStatus(els.interactionStatus, "Observation sent.", "ready");
    appendActivity("User utterance acknowledged and speech requested.");
    appendSignalSent("obs.user_utterance", text, "text");
    handleResponseEvent(data.responseEvent);
    return data;
  } catch (error) {
    setStatus(els.interactionStatus, "Observation failed.", "error");
    appendActivity(error.message);
    showAlert(`Observation failed: ${error.message}`, "danger");
    return null;
  }
}
```

Then add the legacy helper:

```js
async function acknowledgeUserUtteranceLegacy(text, request) {
  try {
    const response = await scopedFetch(demoAgentPath("/acknowledge"), {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      throw new Error(`acknowledge failed: ${response.status}`);
    }
    const data = await response.json();
    data.legacyTextFlow = true;
    setStatus(els.interactionStatus, "Observation sent.", "ready");
    appendActivity("User utterance acknowledged through legacy endpoint.");
    appendSignalSent("obs.user_utterance", text, "text");
    handleResponseEvent(data.responseEvent);
    return data;
  } catch (error) {
    setStatus(els.interactionStatus, "Observation failed.", "error");
    appendActivity(error.message);
    showAlert(`Observation failed: ${error.message}`, "danger");
    return null;
  }
}
```

Important:

- Keep `sendManualObservation`, `sendWeatherObservation`, and `sendDetectorObservation` on `/acknowledge`.
- Do not append assistant transcript manually in `acknowledgeUserUtterance`; `handleResponseEvent(data.responseEvent)` already routes behaviour rendering.
- Do not wait for `generateBehaviour("backend_complement")` unless a test needs determinism. The user-visible speech should not be blocked.

### 5. Keep `generateBehaviour(outputProfile)` Mostly Unchanged

Existing code posts:

```js
JSON.stringify({ outputProfile })
```

That is sufficient. Calling:

```js
generateBehaviour("backend_complement")
```

will send:

```json
{"outputProfile":"backend_complement"}
```

PROMETHEUS accepts lowercase underscore and maps it to `BACKEND_COMPLEMENT`.

### 6. Realtime Speech Tab

No endpoint change is required in the Valerian browser code for speech-to-speech.

Current speech call endpoint should stay:

```text
POST /demo/agents/{agentId}/realtime/call?...query settings...
```

PROMETHEUS sideband now processes accepted transcripts through the combined backend turn internally. The Valerian browser receives audio/transcript over Realtime as before.

Do update the browser transcript display delay to `400` ms as described above.

Do not add browser-side `/acknowledge-and-generate` calls for Realtime transcripts. That would duplicate the backend sideband's transcript ingress.

## Smoke-Test Mock Server Updates

Several Valerian smoke tests mock PROMETHEUS with the old flow:

```js
if (req.url === `/demo/agents/${agentId}/acknowledge` && req.method === "POST") return json(res, 200, { active: true });
if (req.url === `/demo/agents/${agentId}/behaviour/generate` && req.method === "POST") {
  json(res, 200, {});
  setTimeout(() => emitBehaviour(), 50);
  return;
}
```

For tests that exercise text input, add support for the new endpoint:

```js
if (req.url === `/demo/agents/${agentId}/acknowledge-and-generate?profile=realtime_speech`
    && req.method === "POST") {
  const event = createSpeechOnlyBehaviourEvent();
  return json(res, 200, { active: true, responseEvent: event });
}
```

Then make `/behaviour/generate` distinguish complement from old full plan:

```js
if (req.url === `/demo/agents/${agentId}/behaviour/generate` && req.method === "POST") {
  const bodyJson = body ? JSON.parse(body) : {};
  json(res, 200, {});
  setTimeout(() => {
    if (bodyJson.outputProfile === "backend_complement") {
      emitComplementBehaviour();
    } else {
      emitFullPlanBehaviour();
    }
  }, 50);
  return;
}
```

Recommended mock behaviour shapes:

Speech returned by `acknowledge-and-generate`:

```js
{
  type: "resp.behaviour_plan",
  actor: "assistant",
  kind: "response",
  payload: JSON.stringify({ speech: `Smoke response ${sequence}` })
}
```

Complement emitted by `/behaviour/generate`:

```js
{
  type: "resp.behaviour_plan",
  actor: "assistant",
  kind: "response",
  payload: JSON.stringify({ nonVerbal: { gesture: "EXPLAIN" } })
}
```

Do not include speech in the complement event, otherwise transcript assertions may see duplicate assistant messages.

Tests likely requiring updates:

```text
apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js
apps/valerian-cockpit/smoke/cockpit_idless_duplicate_behaviour_smoke.js
apps/valerian-cockpit/smoke/cockpit_stream_reconnect_smoke.js
apps/valerian-cockpit/smoke/cockpit_rps_hand_delegation_smoke.js
```

Keep tests for manual and detector observations pointed at `/acknowledge`, for example:

```text
apps/valerian-cockpit/smoke/cockpit_rps_manual_smoke.js
```

That test verifies manual `obs.hand.sign` acknowledgement and should not move to the verbal fast path.

## Static Contract Test Updates

File:

```text
apps/valerian-cockpit/tests/test_static_server.py
```

Update `test_text_interaction_and_behaviour_stream_contract`.

Current assertions include:

```py
assert 'demoAgentPath("/acknowledge")' in js
assert 'demoAgentPath("/behaviour/generate")' in js
assert 'generateBehaviour("full_plan")' in js
```

Recommended new assertions:

```py
assert 'demoAgentPath("/acknowledge-and-generate")' in js
assert '?profile=realtime_speech' in js
assert 'demoAgentPath("/behaviour/generate")' in js
assert 'generateBehaviour("backend_complement")' in js
assert 'function eventHasSpeech(event)' in js
```

If you keep the legacy fallback, keep or add:

```py
assert 'demoAgentPath("/acknowledge")' in js
assert 'legacyTextFlow' in js
assert 'generateBehaviour("full_plan")' in js
```

Do not remove other `demoAgentPath("/acknowledge")` assertions used by manual sensing, weather, or detector tests.

Also update any Realtime/static contract assertion for the transcript delay:

```py
assert "TRANSCRIPT_BATCH_DELAY_MS = 400" in js
```

## Documentation Updates In Valerian

Update:

```text
apps/valerian-cockpit/README.md
```

Add a short note near the text interaction description:

```markdown
Text interaction uses PROMETHEUS' speech-first verbal-turn path when available:
the cockpit posts user utterances to
`/demo/agents/{agentId}/acknowledge-and-generate?profile=realtime_speech`,
renders returned `BehaviourPlan.speech` immediately, and then requests
`BACKEND_COMPLEMENT` nonverbal behaviour through `/behaviour/generate`.
Manual, weather, and detector observations continue to use `/acknowledge`.
```

Add a note near the speech/realtime section:

```markdown
Speech-to-speech transcript ingestion remains backend-owned by PROMETHEUS'
Realtime sideband. The cockpit must not acknowledge Realtime transcripts itself.
```

Update `PROJECT.md` if the repository expects every milestone to be recorded.
Use the local Valerian milestone style already present in that file.

## Verification Commands

From the root of `zhaw-iwi/valerian`:

```powershell
python -m pytest apps/valerian-cockpit/tests/test_static_server.py
```

If the environment has Playwright installed and the cockpit server can run:

```powershell
python apps/valerian-cockpit/valerian_cockpit_server.py --port 5010
```

Then, in another terminal:

```powershell
node apps/valerian-cockpit/smoke/cockpit_e2e_smoke.js http://127.0.0.1:5010
node apps/valerian-cockpit/smoke/cockpit_idless_duplicate_behaviour_smoke.js http://127.0.0.1:5010
node apps/valerian-cockpit/smoke/cockpit_stream_reconnect_smoke.js http://127.0.0.1:5010
node apps/valerian-cockpit/smoke/cockpit_rps_hand_delegation_smoke.js http://127.0.0.1:5010
node apps/valerian-cockpit/smoke/cockpit_rps_manual_smoke.js http://127.0.0.1:5010
```

`cockpit_rps_manual_smoke.js` is included to verify manual observation acknowledgement still uses the old `/acknowledge` path.

If running the full Python suite is practical:

```powershell
python -m pytest
```

## Acceptance Criteria

The change is complete when:

- Text input sends `obs.user_utterance` to `/acknowledge-and-generate?profile=realtime_speech`.
- Text input no longer calls `generateBehaviour("full_plan")` on the new-backend path.
- Text input calls `generateBehaviour("backend_complement")` after a returned speech behaviour.
- Returned speech from `acknowledge-and-generate` appears in the transcript without waiting for complement generation.
- Nonverbal complement behaviour still renders and dispatches through the existing SSE/history paths.
- Manual observations, weather observations, and detector observations still use `/acknowledge`.
- Realtime speech call creation stays on `/demo/agents/{agentId}/realtime/call`.
- Browser-side `TRANSCRIPT_BATCH_DELAY_MS` is `400`.
- Static tests and relevant smoke tests pass.

## Common Pitfalls

- Do not request `backend_complement` before a speech event exists. PROMETHEUS derives complement from the latest assistant speech in event history.
- Do not include speech in mocked complement events. It can create duplicate assistant transcript bubbles.
- Do not migrate manual observations to `acknowledge-and-generate`; that can make visual/manual observations unexpectedly generate verbal responses.
- Do not add browser-side Realtime transcript acknowledgement. PROMETHEUS sideband already handles speech transcript ingress.
- Do not remove existing SSE deduplication or history catch-up logic. The complement event depends on those paths.
- Keep access-code handling unchanged. All scoped requests still need `X-Prometheus-Access-Code`, and SSE still uses query parameters because `EventSource` cannot set custom headers.
