import assert from "node:assert/strict";
import test from "node:test";
import { TranscriptionEventRuntime } from "../../../src/main/resources/public/transcription/events.js";
import { ScopedTranscriptIngress } from "../../../src/main/resources/public/transcription/ingress.js";

const AGENT_ID = "11111111-1111-4111-8111-111111111111";

test("ordered finalized turns use the scoped FULL_PLAN acknowledgement boundary", async () => {
  const requests = [];
  const statuses = [];
  const accepted = [];
  let releaseFirst;
  const firstResponse = new Promise((resolve) => { releaseFirst = resolve; });
  const ingress = new ScopedTranscriptIngress({
    agentId: AGENT_ID,
    accessCode: "ROOM-CODE",
    fetchImpl: async (url, options) => {
      const body = JSON.parse(options.body);
      requests.push({ url, method: options.method, headers: Object.fromEntries(options.headers), body });
      if (requests.length === 1) await firstResponse;
      return jsonResponse({ active: true, responseEvent: { type: "resp.behaviour_plan", payload: "{}" } });
    },
    onAccepted: (turn) => accepted.push(turn.text),
    onStatus: (status) => statuses.push(`${status.state}:${status.itemId}`),
  });

  const first = ingress.submit({ epoch: 3, itemId: "one", text: "First" });
  const second = ingress.submit({ epoch: 3, itemId: "two", text: "Second" });
  await Promise.resolve();
  assert.equal(requests.length, 1);
  releaseFirst();
  assert.deepEqual(await Promise.all([first, second]), [true, true]);

  assert.deepEqual(requests.map((request) => request.body.payload), ["First", "Second"]);
  assert.ok(requests.every((request) => request.url
    === `/demo/agents/${AGENT_ID}/acknowledge?profile=full_plan`));
  assert.ok(requests.every((request) => request.headers["x-prometheus-access-code"] === "ROOM-CODE"));
  assert.ok(requests.every((request) => request.body.type === "obs.user_utterance"));
  assert.deepEqual(accepted, ["First", "Second"]);
  assert.deepEqual(statuses, ["queued:one", "queued:two", "acknowledging:one", "accepted:one",
    "acknowledging:two", "accepted:two"]);
});

test("no-response acknowledgement requests one FULL_PLAN fallback without rendering HTTP output", async () => {
  const requests = [];
  let accepted;
  const ingress = new ScopedTranscriptIngress({
    agentId: AGENT_ID,
    fetchImpl: async (url, options) => {
      requests.push({ url, body: JSON.parse(options.body) });
      return url.endsWith("/behaviour/generate") ? emptyResponse(200)
        : jsonResponse({ active: true, responseEvent: null });
    },
    onAccepted: (turn) => { accepted = turn; },
  });
  assert.equal(await ingress.submit({ epoch: 1, itemId: "fallback", text: "Please respond" }), true);
  assert.deepEqual(requests.map((request) => request.url), [
    `/demo/agents/${AGENT_ID}/acknowledge?profile=full_plan`,
    `/demo/agents/${AGENT_ID}/behaviour/generate`,
  ]);
  assert.equal(requests[1].body.outputProfile, "full_plan");
  assert.equal(accepted.acknowledgement.responseEvent, null);
});

test("partials, empty terminals, duplicate terminals, stale epochs, and playback-gated turns acknowledge none", async () => {
  const requests = [];
  const ingress = new ScopedTranscriptIngress({
    agentId: AGENT_ID,
    fetchImpl: async (...args) => { requests.push(args); return jsonResponse({ active: true }); },
  });
  ingress.setAccepting(false);
  assert.equal(await ingress.submit({ epoch: 1, itemId: "gated", text: "speaker echo" }), false);
  ingress.setAccepting(true);

  const runtime = new TranscriptionEventRuntime({ onFinal: (turn) => ingress.submit(turn) });
  runtime.beginEpoch(5);
  runtime.handle(event("input_audio_buffer.committed", "c-empty", { item_id: "empty" }));
  runtime.handle(event("conversation.item.input_audio_transcription.delta", "d-empty",
    { item_id: "empty", delta: "partial only" }));
  runtime.handle(event("conversation.item.input_audio_transcription.completed", "f-empty",
    { item_id: "empty", transcript: " " }));
  runtime.handle(event("input_audio_buffer.committed", "c-stale", { item_id: "stale" }));
  runtime.handle(event("conversation.item.input_audio_transcription.completed", "f-stale",
    { item_id: "stale", transcript: "old epoch" }));
  runtime.beginEpoch(6);
  runtime.handle(event("conversation.item.input_audio_transcription.completed", "f-stale-duplicate",
    { item_id: "stale", transcript: "old epoch" }));
  await runtime.whenIdle();
  await ingress.whenIdle();
  assert.equal(requests.length, 0);
});

test("rejected and provider-failed turns stay out of the accepted path", async () => {
  const statuses = [];
  const diagnostics = [];
  const ingress = new ScopedTranscriptIngress({
    agentId: AGENT_ID,
    fetchImpl: async () => emptyResponse(403),
    onStatus: (status) => statuses.push(status.state),
    onDiagnostic: (diagnostic) => diagnostics.push(diagnostic),
  });
  assert.equal(await ingress.submit({ epoch: 1, itemId: "denied", text: "Do not persist" }), false);
  assert.deepEqual(statuses, ["queued", "acknowledging", "rejected"]);
  assert.equal(diagnostics[0].code, "acknowledge_rejected");

  const providerDiagnostics = [];
  const runtime = new TranscriptionEventRuntime({
    onFinal: (turn) => ingress.submit(turn),
    onDiagnostic: (diagnostic) => providerDiagnostics.push(diagnostic),
  });
  runtime.beginEpoch(2);
  runtime.handle(event("input_audio_buffer.committed", "c-failed", { item_id: "provider-failed" }));
  runtime.handle(event("conversation.item.input_audio_transcription.failed", "f-failed",
    { item_id: "provider-failed", error: { code: "audio_unintelligible" } }));
  await runtime.whenIdle();
  assert.equal(providerDiagnostics[0].code, "provider_transcription_failed");
});

function event(type, eventId, overrides) {
  return { type, event_id: eventId, ...overrides };
}

function jsonResponse(body) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

function emptyResponse(status) {
  return new Response(null, { status });
}
