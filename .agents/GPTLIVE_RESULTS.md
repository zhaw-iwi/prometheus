# GPT-Live implementation and acceptance evidence

Branch: `feature/gptlive`. Roadmap: [PLAN_GPTLIVE.md](PLAN_GPTLIVE.md).
Implementation authorization includes committing/pushing each milestone and
continuing automatically. A passed synthetic test is not acoustic acceptance.

## GL-01 / project Milestone 184 — 2026-09-27

Implemented an opt-in scoped typed session gateway, WebRTC SDP exchange, backend
sideband, acknowledged input mute/unmute, graceful close with bounded hangup
fallback, capacity/lifetime bounds and content-free diagnostic counters. Invalid
SDP answers trigger orphan cleanup. Access code and agent bind the opaque local
handle. HTTP response sizes and fragmented WebSocket messages are bounded;
HTTP body deadlines cancel the pending request. No provider credentials, SDP,
captions or audio appear in application diagnostic records.

Temporary probe: `/live/probe.html`. Explicit start, browser capture requests,
shared microphone/output ownership, temporary captions and immediate local Stop.
Task/ingress integration is deliberately absent at this milestone. Probe uses
default OS devices; device settings and cockpit integration belong to GL-06.

### Automated results — PASS

- `mvnw.cmd -q -DskipTests compile`.
- `mvnw.cmd -q "-Dtest=LiveSessionGatewayUnitTest,ScopedLiveSessionServiceUnitTest,ScopedLiveSessionControllerWebMvcTest" test`:
  10 tests, zero failures/errors. Covers loopback payload, malformed/rejected/
  oversized response, stalled response-body deadline, sideband fragmentation,
  orphan cleanup, scope/feature/capacity, mute ACK, finalization timeout, expiry,
  disconnect and redaction/bounded logs.
- `LiveSessionSmokeIntegrationTest`: one passing test against disposable local
  MySQL schema `prometheus_gptlive_ae30f6cdfe`, with an account restricted to that
  schema. Real scoped controllers, access records and persisted agent; only the
  Live and language providers stubbed. Verifies create, cross-code rejection and
  graceful close. Schema/account removed afterward. First run exposed an invalid
  combined-plan stub; fixed before the successful run.
- `node --check src/main/resources/public/live/probe.js`: PASS.
- Synthetic provider shapes retained in `src/test/resources/live/synthetic-events.json`.
  No recordings or real user/provider transcript content were collected.

### Provider access and live session — PARTIAL / NOT RUN

Read-only `GET /v1/models/gpt-live-1` with configured account: HTTP 200 and matching
model ID. No token or full response retained. This proves model visibility only.
Live WebRTC connect/transcripts/finalization and paid voice session: **NOT RUN**;
requires interactive trial. No claims about actual model quality or latency.

Official contracts checked: [WebRTC](https://developers.openai.com/api/docs/guides/voice-webrtc?api=live),
[server controls](https://developers.openai.com/api/docs/guides/voice-server-controls?api=live),
[Live conversations](https://developers.openai.com/api/docs/guides/live-conversations),
[delegation](https://developers.openai.com/api/docs/guides/live-delegation),
[sideband events](https://developers.openai.com/api/reference/resources/live/sideband-websocket),
and [hangup](https://developers.openai.com/api/reference/resources/live/subresources/sessions/methods/hangup).
The provider schema uses `gpt-live-1`, client delegation and `store:false`.
Offered voices are marin/quartz/willow/meridian; these were documented but have not
been auditioned. Local lifetime/capacity limits are not provider SLA guarantees.

Transcript fragments have provider start/end milliseconds and event IDs, but no
utterance-final event. Reflected PCM input has no provider timestamp; output PCM
has start/end milliseconds. Input sample count and receipt time can support a
conservative activity detector, with ambiguity made explicit. Browser and server
clocks cannot be treated as one clock. Mute ACK confirms a command, not whether
earlier reflected audio was consumed. `session.started` on the browser gates
input, after the server has attached; initial sideband observation gaps remain a
live-trial question. Narration ACK is not playback completion and provides no
exact correlation between a backend intent and paraphrased output.

### Built-in microphone/speakers — NOT RUN

Chrome/Windows: NOT RUN. Chrome/Linux: NOT RUN. No physical microphone/speaker
trial or screenshot-based browser acceptance is claimed for GL-01.

### Bluetooth microphone/speakers — NOT RUN

Both OS trials remain for the later physical comparison. Echo cancellation was
requested at browser capture only; no Bluetooth fix or root cause is established.
