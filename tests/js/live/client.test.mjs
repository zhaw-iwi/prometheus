import test from "node:test";
import assert from "node:assert/strict";
import { LiveClient } from "../../../src/main/resources/public/live/client.js";
import { LiveCaptions } from "../../../src/main/resources/public/live/captions.js";
import { MicrophoneLease } from "../../../src/main/resources/public/transcription/media.js";
import { OutputLease } from "../../../src/main/resources/public/speech/playback.js";

const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };
test("provider receives the completed ICE offer; Stop also cancels gathering before creating a session", async () => {
  for (const stop of [false, true]) {
    const f = fixture(), waiting = deferred(); let changed;
    f.peer.iceGatheringState = "gathering";
    f.peer.addEventListener = (_, listener) => { changed = listener; waiting.resolve(); };
    f.peer.removeEventListener = () => { changed = null; };
    const start = f.start(); await waiting.promise;
    assert.equal(f.requests.some(value => value.url.endsWith("sessions")), false);
    if (stop) { await f.client.stop(); await start; assert.equal(f.requests.length, 1); }
    else {
      f.peer.localDescription = { sdp: "v=0 gathered-candidates" }; f.peer.iceGatheringState = "complete"; changed(); await start;
      assert.equal(JSON.parse(f.requests.find(value => value.url.endsWith("sessions")).request.body).sdp, "v=0 gathered-candidates");
      assert.equal(f.client.state, "Active"); await f.client.stop();
    }
    assert.equal(changed, null);
  }
});
test("scope invalidation fences same-agent reset callbacks while completing old cleanup", async () => {
  const cleanup = deferred(); const f = fixture({ respond: async (_, request) => request.method === "DELETE" ? cleanup.promise : null });
  await f.start(); const closed = f.client.invalidate("Agent reset.");
  assert.equal(f.audio.srcObject, null); assert.equal(f.client.state, "Idle");
  cleanup.resolve({ ok: true, json: async () => ({ finalized: true }) }); await closed;
  await new Promise(resolve => setImmediate(resolve)); assert.equal(f.histories.length, 0); assert.equal(f.client.state, "Idle");
});

test("network, sideband and track failures silence locally and reconnect only by explicit Start", async () => {
  for (const fail of [f => { f.state.status.state = "disconnected"; return f.client.poll(); },
    f => { f.peer.connectionState = "failed"; f.peer.onconnectionstatechange(); },
    f => f.track.dispatchEvent(new Event("ended"))]) {
    const f = fixture(); await f.start(); await fail(f); await f.client.closing;
    assert.equal(f.audio.paused, true); assert.equal(f.track.enabled, false); assert.equal(f.client.state, "Disconnected");
    assert.equal(f.requests.filter(value => value.url.endsWith("sessions")).length, 1);
    f.state.status.state = "attached"; await f.start(); assert.equal(f.client.state, "Active"); await f.client.stop();
  }
});
function fixture(options = {}) {
  const requests = [], states = [], diagnostics = [], captions = [], histories = [];
  const track = new EventTarget(); Object.assign(track, { enabled: false });
  const media = { stream: { getAudioTracks: () => [track] }, acquire: async () => {}, addTracks() {},
    setEnabled(value) { track.enabled = value; }, release() { track.enabled = false; this.released = true; }, appliedAudioSettings: () => ({ echoCancellation: true }) };
  const audio = { srcObject: null, muted: false, pause() { this.paused = true; }, play: async () => {}, setSinkId: async () => {} };
  const channel = { close() { this.closed = true; this.onclose?.(); } };
  const peer = { iceGatheringState: "complete", createDataChannel: () => channel, createOffer: async () => ({ sdp: "v=0 offer" }),
    async setLocalDescription(value) { this.localDescription = value; },
    setRemoteDescription: async () => { channel.onmessage({ data: JSON.stringify({ type: "session.started" }) }); }, close() { this.closed = true; } };
  const output = { acquire: () => true, release() { this.released = true; } };
  const state = { ledger: [], history: [], status: { state: "attached", captureState: "active" }, capability: { enabled: true, eligible: true } };
  const fetch = async (url, request = {}) => {
    requests.push({ url, request });
    if (options.respond) { const response = await options.respond(url, request); if (response) return response; }
    const body = url.includes("/updates?") ? { status: state.status, transcriptRevision: JSON.stringify(state.ledger).length, transcripts: state.ledger }
      : url.endsWith("capabilities") ? state.capability : request.method === "DELETE" ? { finalized: true }
      : url.endsWith("sessions") ? { handle: "session1", sdp: "v=0 answer" } : url.includes("transcripts?") ? state.ledger
      : url.endsWith("history") ? state.history : state.status;
    return { ok: true, json: async () => body };
  };
  const client = new LiveClient({ audio, fetch, createPeer: () => peer, createMedia: () => media, createOutputLease: () => output,
    onState: value => states.push(value), onDiagnostic: value => diagnostics.push(value), onCaptions: value => captions.push(value),
    onHistory: value => histories.push(value), pollMs: 0, timeoutMs: 200, ...options.client });
  return { client, track, media, audio, channel, peer, output, requests, states, diagnostics, captions, histories, state,
    start: () => client.start({ agentId: "agent1", accessCode: "private", voice: "marin", mediaPreferences: {} }),
    emit: event => channel.onmessage({ data: JSON.stringify(event) }) };
}

test("one heartbeat request skips unchanged transcripts and resumes after a revision", async () => {
  let revision = 0;
  const f = fixture({ respond: async url => url.includes("/updates?") ? {
    ok: true, json: async () => ({ status: { state: "attached" }, transcriptRevision: revision,
      transcripts: url.endsWith(`=${revision}`) ? null : [] }),
  } : null });
  await f.start(); f.requests.length = 0;
  await f.client.poll(); await f.client.poll();
  assert.equal(f.requests.length, 2);
  assert.ok(f.requests.every(value => value.url.includes("/updates?")));
  assert.ok(f.requests[0].url.endsWith("=-1")); assert.ok(f.requests[1].url.endsWith("=0"));
  revision = 1; await f.client.poll(); await f.client.poll();
  assert.ok(f.requests.at(-1).url.endsWith("=1"));
  await f.client.stop();
});

test("start waits for sideband/session readiness, input mute preserves output, Stop silences before cleanup", async () => {
  const cleanup = deferred();
  const f = fixture({ respond: async (_, request) => request.method === "DELETE" ? cleanup.promise : null });
  assert.equal(f.requests.length, 0); await f.start(); assert.equal(f.client.state, "Active"); assert.equal(f.track.enabled, true);
  assert.equal(f.requests.filter(value => value.url.endsWith("sessions")).length, 1);
  await f.client.mute(); assert.equal(f.track.enabled, false); assert.equal(f.audio.paused, undefined);
  await f.client.mute(); assert.equal(f.track.enabled, true);
  const stopped = f.client.stop();
  assert.equal(f.audio.paused, true); assert.equal(f.audio.srcObject, null); assert.equal(f.media.released, true);
  assert.equal(f.output.released, true); assert.equal(f.peer.closed, true); assert.equal(f.client.state, "Stopping");
  cleanup.resolve({ ok: true, json: async () => ({ finalized: true }) }); await stopped; assert.equal(f.client.state, "Idle");
  assert.equal(f.diagnostics.at(-1).finalized, true);
});

test("overlapping caption receipts reconcile by identity, preserving legitimate repeated words", () => {
  const captions = new LiveCaptions();
  const event = { type: "session.input_transcript.delta", event_id: "u1", delta: "again ", start_ms: 10 };
  captions.receive(event); captions.receive(event);
  captions.receive({ ...event, event_id: "u2", start_ms: 20, delta: "again" });
  captions.receive({ ...event, type: "session.output_transcript.delta", event_id: "a1", delta: "Understood" });
  assert.deepEqual(captions.snapshot(), { USER: "again again", ASSISTANT: "Understood" });
  captions.reconcile([{ status: "PENDING", receiptIds: ["u1"] }]); assert.equal(captions.pending.size, 3);
  captions.reconcile([{ status: "COMPLETE", receiptIds: ["u1", "u2"] }]);
  assert.deepEqual(captions.snapshot(), { USER: "", ASSISTANT: "Understood" });
  captions.receive(event); assert.equal(captions.snapshot().USER, "");
});

test("poll reconciles persisted captions and late callbacks cannot restore a stopped session", async () => {
  const f = fixture(); await f.start();
  f.emit({ type: "session.input_transcript.delta", event_id: "u1", delta: "Accepted", start_ms: 0 });
  f.state.ledger = [{ segmentId: "s1", status: "COMPLETE", eventId: "e1", receiptIds: ["u1"] }]; f.state.history = [{ id: "e1" }];
  await f.client.poll(); assert.equal(f.captions.at(-1).USER, ""); assert.deepEqual(f.histories.at(-1), [{ id: "e1" }]);
  await f.client.stop(); const count = f.captions.length;
  f.emit({ type: "session.output_transcript.delta", event_id: "late", delta: "obsolete" });
  f.peer.ontrack({ streams: [{}] }); assert.equal(f.captions.length, count); assert.equal(f.audio.srcObject, null);
});

test("permission rejection, unsupported routing and provider scope errors release media and require explicit retry", async () => {
  const denied = fixture(); denied.media.acquire = async () => { throw new DOMException("denied", "NotAllowedError"); };
  await denied.start(); assert.equal(denied.client.state, "Error"); assert.equal(denied.output.released, true);
  assert.match(denied.states.find(value => value.state === "Error").detail, /permission was denied/);
  const routing = fixture(); delete routing.audio.setSinkId;
  await routing.client.start({ agentId: "a", accessCode: "x", outputDeviceId: "room" }); assert.equal(routing.client.state, "Error");
  assert.equal(routing.requests.length, 1);
  const scope = fixture({ respond: async () => ({ ok: false, status: 401 }) }); await scope.start();
  assert.equal(scope.client.state, "Error"); assert.equal(scope.requests.length, 1);
});

test("Stop during session creation cleans up a late handle without enabling the microphone", async () => {
  const created = deferred(), started = deferred();
  const f = fixture({ respond: async (url, request) => {
    if (url.endsWith("sessions") && request.method === "POST") { started.resolve(); return created.promise; }
  } });
  const opening = f.start(); await started.promise; const stopping = f.client.stop();
  assert.equal(f.track.enabled, false); assert.equal(f.peer.closed, true);
  created.resolve({ ok: true, json: async () => ({ handle: "late", sdp: "v=0 answer" }) });
  await Promise.all([opening, stopping]);
  assert.equal(f.requests.filter(value => value.request.method === "DELETE").length, 1);
  assert.equal(f.requests.some(value => value.url.includes("muted=false")), false);
});

test("provider errors show only known billing/access categories and always release media without retry", async () => {
  const cases = [
    ["live_provider_quota_exhausted", /credits or quota are exhausted/],
    ["live_provider_rate_limited", /limiting voice requests/],
    ["live_provider_authentication", /API credentials/],
    ["live_provider_access_denied", /permissions and model access/],
    ["private-sentinel", /voice provider is unavailable/],
    ["constructor", /voice provider is unavailable/],
    [null, /voice provider is unavailable/],
  ];
  for (const [code, expected] of cases) {
    const f = fixture({ respond: async (url, request) => url.endsWith("sessions") && request.method === "POST"
      ? { ok: false, status: 502, json: async () => { if (code === null) throw new Error("empty response"); return { code, message: "private-sentinel" }; } }
      : null });
    await f.start();
    const detail = f.states.find(value => value.state === "Error").detail;
    assert.match(detail, expected); assert.equal(detail.includes("private-sentinel"), false);
    assert.equal(f.client.active, false); assert.equal(f.media.released, true); assert.equal(f.output.released, true);
    assert.equal(f.peer.closed, true); assert.equal(f.track.enabled, false);
    assert.equal(f.requests.filter(value => value.url.endsWith("sessions")).length, 1);
  }
});

test("shared microphone and per-agent speaker leases exclude another mode and recover after expiry", () => {
  const data = new Map(), storage = { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  let now = 0;
  const first = new MicrophoneLease({ storage, ownerId: "continuous", now: () => now });
  const live = new MicrophoneLease({ storage, ownerId: "live", now: () => now });
  const speaker1 = new OutputLease({ storage, ownerId: "continuous", agentId: "a", now: () => now });
  const speaker2 = new OutputLease({ storage, ownerId: "live", agentId: "a", now: () => now });
  try {
    first.acquire(); assert.throws(() => live.acquire(), /Another PROMETHEUS/); assert.equal(speaker1.acquire(), true); assert.equal(speaker2.acquire(), false);
    now = 30000; live.acquire(); assert.equal(speaker2.acquire(), true);
  } finally { first.release(); live.release(); speaker1.release(); speaker2.release(); }
});
