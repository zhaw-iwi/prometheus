import test from "node:test";
import assert from "node:assert/strict";
import { TurnTimings, decodeServerTiming } from "../../../src/main/resources/public/performance/timings.js";
import { measurements, timingExport, timingCsv, outcome, observedInferences } from "../../../src/main/resources/public/performance/report.js";
import { summarize } from "../../needforspeed/summarize.mjs";

const serverHeader = btoa(JSON.stringify({ version: 1, durationMs: 4200.5, truncated: false,
  spans: [{ stage: "inference", durationMs: 4000, offsetMs: 10, status: "ok", model: "fixture-model", purpose: "DECISION", effort: "none" }],
  text: "private-provider-payload" }));

test("transcription stages survive capture and exports with missing receipts left unknown", () => {
  const timings = new TurnTimings({ now: () => 1000, uuid: () => "trace" });
  const stages = { last_voice: 100, committed: 600, commit_sent: 601, commit_acknowledged: 650,
    transcript_first_delta: 50, transcript_last_delta: 800, final_transcript: 900 };
  timings.begin("agent", { ...stages, transcript: "private-transcript", providerTimestamp: 123 });
  const exported = timingExport(timings.snapshot());
  assert.deepEqual(exported.turns[0].stages, { ...stages, submitted: 1000 });
  const expected = { commitAcknowledgement: 49, acknowledgedFinal: 250, transcriptDeltas: 750, transcriptDeltaTail: 100 };
  const csv = timingCsv(timings.snapshot()).split("\r\n").map(row => row.split(","));
  const summary = summarize(exported);
  for (const [key, value] of Object.entries(expected)) {
    assert.equal(exported.turns[0].durationsMs[key], value);
    assert.equal(csv[1][csv[0].indexOf(`"${key}Ms"`)], `"${value}"`);
    assert.equal(summary.measurements[key].p50Ms, value);
    assert.equal(measurements({ stages: { committed: 10, final_transcript: 20 } })[key], null);
  }
  assert.match(exported.metadata.interpretation, /browser receipt times/);
  assert.ok(!JSON.stringify(exported).includes("private"));
});

test("all supported transcription delays survive timing capture and JSON/CSV export", () => {
  for (const delay of ["minimal", "low", "medium", "high", "xhigh", "private-unsupported"]) {
    const timings = new TurnTimings({ now: () => 1, uuid: () => "trace" });
    timings.configuration = () => ({ transcriptionDelay: delay, transcriptionPrompt: "private-prompt" });
    timings.begin("agent");
    const turns = timings.snapshot();
    const json = JSON.stringify(timingExport(turns)), csv = timingCsv(turns);
    if (delay === "private-unsupported") {
      assert.equal(JSON.parse(json).turns[0].configuration.transcriptionDelay, undefined);
    } else {
      assert.equal(JSON.parse(json).turns[0].configuration.transcriptionDelay, delay);
      assert.ok(csv.includes(`"${delay}"`));
    }
    assert.ok(!json.includes("private"));
    assert.ok(!csv.includes("private"));
  }
});

test("PCM delivery settings, interruptions and renderer timing survive private-content filtering", () => {
  const timings = new TurnTimings({ now: () => 100, uuid: () => "trace" });
  timings.configuration = () => ({ formatPreference: "auto" });
  const id = timings.begin("agent"); timings.bind(id, "event");
  timings.mark(id, "audio_prepare_start", 90); timings.mark(id, "audio_prepare_end", 95);
  timings.speech("agent", "event", { formatPreference: "auto", format: "pcm", playbackMode: "pcm", playbackStartSource: "pcm_renderer",
    pcmPrefillMs: 60, pcmInitialBufferedMs: 100, pcmUnderruns: 2, pcmGapMs: 150, deviceId: "private-device", samples: ["private-audio"] });
  const exported = timingExport(timings.snapshot()), csv = timingCsv(timings.snapshot());
  assert.equal(exported.turns[0].durationsMs.speechPreparation, 5);
  assert.equal(exported.turns[0].speech.format, "pcm");
  assert.equal(exported.turns[0].speech.pcmUnderruns, 2);
  assert.equal(exported.turns[0].speech.playbackStartSource, "pcm_renderer");
  assert.match(csv, /pcmInitialBufferedMs/); assert.match(csv, /"pcm_renderer"/);
  assert.ok(!JSON.stringify(exported).includes("private")); assert.ok(!csv.includes("private"));
  timings.speech("agent", "event", { fallbackReason: "private-error", format: "private-format" });
  assert.ok(!JSON.stringify(timings.snapshot()).includes("private"));
});

test("SSE speech before acknowledgement keeps independent HTTP and server timings on the same turn", async () => {
  let time = 10;
  const timings = new TurnTimings({ now: () => time, uuid: () => "trace" });
  timings.configuration = () => ({ turnDetection: "local_vad", silenceDurationSeconds: .5,
    transcriptionDelay: "low", voice: "alloy", speed: 1, capture: { echoCancellation: true, deviceId: "private-device" },
    transcriptionPrompt: "private-prompt", accessCode: "private-code" });
  const id = timings.begin("agent", { last_voice: 0, committed: 5, final_transcript: 9 });
  let finishSpeech;
  time = 12;
  timings.event("agent", "event", "sse_received");
  const audio = timings.fetchEvent("agent", "event", () => new Promise(resolve => { finishSpeech = resolve; }), "/speech?voice=alloy");
  time = 14;
  await timings.fetch(id, async () => new Response(null, { headers: {
    "X-Prometheus-Behaviour-Id": "event", "X-Prometheus-Timing": serverHeader,
  } }), "/acknowledge?profile=full_plan");
  time = 16;
  finishSpeech(new Response(null, { headers: { "X-Prometheus-Timing": serverHeader } }));
  await audio;
  timings.speech("agent", "event", { playbackMode: "progressive" });
  timings.event("agent", "event", "audio_playing");
  const [turn] = timings.snapshot();
  assert.equal(turn.requests.length, 2);
  assert.deepEqual(turn.requests.find(request => request.kind === "speech").end, 16);
  assert.equal(turn.requests.find(request => request.kind === "acknowledge").server.spans[0].model, "fixture-model");
  assert.equal(measurements(turn).voiceResponse, 16);
  assert.equal(turn.speech.playbackMode, "progressive");
  assert.ok(!JSON.stringify(timingExport([turn])).includes("private"));
  assert.ok(!JSON.stringify(turn).includes("/speech?"));
});

test("missing and invalid stages stay unknown, cancelled audio is retained, and exports describe clocks", async () => {
  let time = 0;
  const timings = new TurnTimings({ now: () => ++time, uuid: () => "cancelled" });
  const id = timings.begin("agent");
  timings.bind(id, "event");
  await assert.rejects(timings.fetchEvent("agent", "event", async () => { throw new DOMException("private", "AbortError"); }, "/speech"));
  const [turn] = timings.snapshot();
  assert.equal(outcome(turn), "Cancelled");
  assert.equal(turn.requests[0].status, "cancelled");
  assert.equal(measurements(turn).voiceResponse, null);
  assert.equal(measurements({ stages: { last_voice: 10, audio_playing: 2 } }).voiceResponse, null);
  const exported = timingExport([turn]);
  assert.match(exported.metadata.clock, /separate/);
  assert.equal(exported.turns[0].durationsMs.localSilence, null);
  assert.match(timingCsv([turn]), /voiceResponseMs/);
  assert.ok(!timingCsv([turn]).includes("null"));
  assert.ok(!JSON.stringify(exported).includes("private"));
  timings.clear();
  assert.deepEqual(timings.snapshot(), []);
});

test("server timing is bounded and rejects invalid payloads without breaking interaction", () => {
  assert.equal(decodeServerTiming("garbage"), null);
  assert.equal(decodeServerTiming("x".repeat(6001)), null);
  assert.equal(decodeServerTiming(btoa('{"version":2,"spans":[]}')), null);
  const data = decodeServerTiming(btoa(JSON.stringify({ version: 1, truncated: true, spans: [
    { stage: "inference", durationMs: 3, model: "private message", prompt: "private prompt" },
    { stage: "persist", durationMs: -1 },
  ] })));
  assert.equal(data.truncated, true);
  assert.equal(data.spans.length, 1);
  assert.ok(!JSON.stringify(data).includes("private"));
});

test("speculative work survives export with its origin and is counted once across requests", () => {
  const started = { spans: [{ stage: "speculation_started", request: "candidate", durationMs: 0, status: "ok" }] };
  const finished = decodeServerTiming(btoa(JSON.stringify({ version: 1, spans: [
    { stage: "inference", request: "candidate", scope: "speculative", originTrace: "origin-trace", durationMs: 1600,
      status: "ok", purpose: "BEHAVIOUR", model: "fixture", promptTokens: 100, completionTokens: 20, providerRequests: 1 },
    { stage: "speculation_reused", request: "candidate", durationMs: 0, status: "ok" },
    { stage: "inference", request: "decision", durationMs: 1000, status: "ok", purpose: "DECISION" },
  ] })));
  assert.equal(finished.spans[0].scope, "speculative");
  assert.equal(finished.spans[0].originTrace, "origin-trace");
  const requests = observedInferences([started, finished]);
  assert.equal(requests.length, 2);
  assert.equal(requests.find(request => request.request === "candidate").completionTokens, 20);
  assert.equal(observedInferences([started]).length, 1); // A discarded/incomplete request still represents work.
  assert.match(timingExport([]).metadata.interpretation, /earlier HTTP request/);
});
