import assert from "node:assert/strict";
import test from "node:test";
import { TurnTimings, timedAudioBlob, turnTimings } from "../../../src/main/resources/public/performance/timings.js";
import { ScopedTranscriptIngress } from "../../../src/main/resources/public/transcription/ingress.js";
import { TranscriptionEventRuntime } from "../../../src/main/resources/public/transcription/events.js";

test("SSE before HTTP joins only its agent, bounded records contain no content", async () => {
  let now = 0;
  const timings = new TurnTimings({ now: () => ++now, limit: 2, uuid: () => `id-${now}` });
  const id = timings.begin("a", { last_voice: 0, text: "private" });
  timings.event("a", "event", "sse_received");
  timings.event("b", "event", "audio_playing");
  await timings.fetch(id, async (_url, options) => {
    assert.equal(options.headers.get("X-Prometheus-Trace-Id"), id);
    return new Response(null, { headers: { "X-Prometheus-Behaviour-Id": "event" } });
  }, "/ack");
  timings.event("a", "event", "audio_playing");
  const record = timings.snapshot("a")[0];
  assert.ok(record.stages.sse_received < record.stages.http_end);
  assert.ok(record.stages.audio_playing > record.stages.http_end);
  assert.ok(!JSON.stringify(record).includes("private"));
  const cancelled = timings.begin("a");
  await assert.rejects(timings.fetch(cancelled, async () => { throw new DOMException("stop", "AbortError"); }, "/"));
  assert.ok(timings.snapshot("a")[1].stages.cancelled);
  timings.begin("b");
  assert.equal(timings.snapshot("a").length, 1);
  timings.clear("a");
  assert.equal(timings.snapshot("a").length, 0);
});

test("duplicate transcript creates one trace and fallback retains it", async () => {
  const agentId = "11111111-1111-4111-8111-111111111111";
  turnTimings.clear(agentId);
  const ids = [];
  const ingress = new ScopedTranscriptIngress({ agentId, fetchImpl: async (_url, options) => {
    ids.push(options.headers.get("X-Prometheus-Trace-Id"));
    return new Response(JSON.stringify({ responseEvent: null }));
  } });
  const turn = { epoch: 1, itemId: "one", text: "synthetic" };
  assert.equal(await ingress.submit(turn), true);
  assert.equal(await ingress.submit(turn), false);
  assert.equal(ids.length, 2);
  assert.equal(ids[0], ids[1]);
  assert.equal(turnTimings.snapshot(agentId).length, 1);
});

test("commit timings follow provider order through out-of-order finals and reset", async () => {
  const turns = [];
  let now = 100;
  const runtime = new TranscriptionEventRuntime({ now: () => now++, onFinal: turn => turns.push(turn) });
  runtime.beginEpoch(1);
  runtime.noteCommit({ lastVoiceAtMs: 10, observedAtMs: 20 });
  runtime.noteCommit({ lastVoiceAtMs: 30, observedAtMs: 40 });
  for (const item_id of ["a", "b"]) runtime.handle({ type: "input_audio_buffer.committed", item_id });
  for (const item_id of ["b", "a"]) runtime.handle({ type: "conversation.item.input_audio_transcription.completed", item_id, transcript: "synthetic" });
  await runtime.whenIdle();
  assert.deepEqual(turns.map(turn => turn.timings.last_voice), [10, 30]);
  assert.deepEqual(turns.map(turn => turn.timings.final_transcript), [101, 100]);
  runtime.beginEpoch(2);
  assert.equal(runtime.itemTimings.size, 0);
});

test("first-byte marker precedes held audio tail, preserving byte order", async () => {
  let release;
  const tail = new Promise(resolve => { release = resolve; });
  const stages = [];
  const response = new Response(new ReadableStream({ async start(controller) {
    controller.enqueue(new Uint8Array([1, 2]));
    await tail;
    controller.enqueue(new Uint8Array([3]));
    controller.close();
  } }), { headers: { "Content-Type": "audio/mpeg" } });
  let first;
  const initial = new Promise(resolve => { first = resolve; });
  const blobPromise = timedAudioBlob(response, stage => { stages.push(stage); if (stage === "audio_first_byte") first(); });
  await initial;
  assert.deepEqual(stages, ["audio_first_byte"]);
  release();
  assert.deepEqual([...new Uint8Array(await (await blobPromise).arrayBuffer())], [1, 2, 3]);
  assert.deepEqual(stages, ["audio_first_byte", "audio_downloaded"]);
});
