import { createSpeechAudio } from "../../../src/main/resources/public/speech/progressive.js";
import assert from "node:assert/strict";
import test from "node:test";
import { TurnTimings, turnTimings } from "../../../src/main/resources/public/performance/timings.js";
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
  let now = 0;
  const runtime = new TranscriptionEventRuntime({ now: () => now, onFinal: turn => turns.push(turn) });
  const receive = (at, type, item_id, extra = {}) => {
    now = at;
    runtime.handle({ type, item_id, ...extra });
  };
  const delta = "conversation.item.input_audio_transcription.delta";
  const completed = "conversation.item.input_audio_transcription.completed";
  runtime.beginEpoch(1);
  receive(5, delta, "a", { event_id: "a-d1", delta: "private partial" });
  runtime.noteCommit({ lastVoiceAtMs: 10, observedAtMs: 20, sentAtMs: 21 });
  runtime.noteCommit({ lastVoiceAtMs: 30, observedAtMs: 40, sentAtMs: 41 });
  receive(50, "input_audio_buffer.committed", "a", { event_id: "a-c" });
  receive(51, "input_audio_buffer.committed", "a", { event_id: "a-c" });
  receive(52, "input_audio_buffer.committed", "a", { event_id: "a-c-duplicate" });
  receive(55, "input_audio_buffer.committed", "b");
  receive(60, delta, "a", { event_id: "a-d2", delta: " more" });
  receive(61, delta, "a", { event_id: "a-d2", delta: " more" });
  receive(62, delta, "a", { delta: "" });
  receive(70, completed, "b", { transcript: "second" });
  receive(71, completed, "b", { transcript: "duplicate" });
  receive(72, delta, "b", { delta: "late" });
  receive(80, completed, "a", { transcript: "first" });
  receive(81, delta, "a", { delta: "late" });
  await runtime.whenIdle();
  assert.deepEqual(turns.map(turn => turn.text), ["first", "second"]);
  assert.deepEqual(turns.map(turn => turn.timings), [
    { last_voice: 10, committed: 20, commit_sent: 21, commit_acknowledged: 50,
      transcript_first_delta: 5, transcript_last_delta: 60, final_transcript: 80 },
    { last_voice: 30, committed: 40, commit_sent: 41, commit_acknowledged: 55, final_transcript: 70 },
  ]);
  assert.equal(runtime.pendingCommits.length, 0);
  assert.ok(!JSON.stringify(turns.map(turn => turn.timings)).includes("private"));
  runtime.beginEpoch(2);
  assert.equal(runtime.itemTimings.size, 0);
  receive(90, "input_audio_buffer.committed", "a");
  receive(100, completed, "a", { transcript: "new epoch" });
  await runtime.whenIdle();
  assert.deepEqual(turns.at(-1).timings, { commit_acknowledged: 90, final_transcript: 100 });
  runtime.settleEpoch();
  assert.equal(runtime.itemTimings.size, 0);
});

test("completion receipt precedes ordered release and missing acknowledgement stays unknown", async () => {
  const turns = [];
  let now = 10;
  const runtime = new TranscriptionEventRuntime({ now: () => now, onFinal: turn => turns.push(turn) });
  runtime.beginEpoch(1);
  runtime.handle({ type: "conversation.item.input_audio_transcription.completed", item_id: "a", transcript: "first" });
  now = 20;
  runtime.handle({ type: "input_audio_buffer.committed", item_id: "a" });
  now = 30;
  runtime.handle({ type: "conversation.item.created", item: { id: "b", role: "user" } });
  now = 40;
  runtime.handle({ type: "conversation.item.input_audio_transcription.completed", item_id: "b", transcript: "second" });
  await runtime.whenIdle();
  assert.deepEqual(turns.map(turn => turn.timings), [
    { final_transcript: 10, commit_acknowledged: 20 }, { final_transcript: 40 },
  ]);
  for (let index = 0; index < 150; index++) {
    runtime.noteCommit({ sentAtMs: now });
    runtime.handle({ type: "conversation.item.input_audio_transcription.delta", item_id: `bounded-${index}`, delta: "x" });
  }
  assert.equal(runtime.itemTimings.size, 128);
  assert.equal(runtime.pendingCommits.length, 128);
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
  let blob;
  const resourcePromise = createSpeechAudio(response, {
    MediaSourceClass: null,
    urlApi: { createObjectURL: value => { blob = value; return "blob:fixture"; }, revokeObjectURL: () => {} },
    onStage: stage => { stages.push(stage); if (stage === "audio_first_byte") first(); },
  });
  await initial;
  assert.deepEqual(stages, ["audio_first_byte"]);
  release();
  const resource = await resourcePromise;
  assert.deepEqual([...new Uint8Array(await blob.arrayBuffer())], [1, 2, 3]);
  resource.dispose();
  assert.deepEqual(stages, ["audio_first_byte", "audio_downloaded"]);
});
