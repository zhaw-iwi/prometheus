import test from "node:test";
import assert from "node:assert/strict";
import { TurnTimings } from "../../../src/main/resources/public/performance/timings.js";
import { timingExport, timingCsv } from "../../../src/main/resources/public/performance/report.js";

const id = "11111111-1111-4111-8111-111111111111";
const response = () => new Response(null, { headers: { "X-Prometheus-Speech-Delivery-Id": id } });
const snapshot = () => ({ version: 1, id, status: "complete", phase: null, finishedMs: 450,
  bytesRead: 19200, bytesFlushed: 19200, dropped: 0, text: "private-speech",
  steps: [{ sequence: 1, phase: "read", startedMs: 20, completedMs: 420, bytes: 9600, totalBytes: 19200, audio: "private-audio" }] });
const flush = () => new Promise(resolve => setImmediate(resolve));

test("server delivery fetch is deferred, once-only, joins early SSE and exports numeric metadata", async () => {
  const timings = new TurnTimings({ now: () => 100, uuid: () => "trace" });
  timings.begin("agent"); let calls = 0;
  const finish = timings.captureSpeechDelivery("agent", "event", response(), async (requestId, signal) => {
    calls++; assert.equal(requestId, id); assert.equal(signal.aborted, false);
    return Response.json(snapshot());
  });
  assert.equal(calls, 0);
  timings.bind("trace", "event");
  assert.equal(timings.snapshot()[0].speechDelivery.retrieval, "waiting");
  finish(); finish(); await flush();
  assert.equal(calls, 1);
  const exported = timingExport(timings.snapshot());
  assert.equal(exported.turns[0].speechDelivery.retrieval, "received");
  assert.equal(exported.turns[0].speechDelivery.server.steps[0].completedMs, 420);
  assert.equal(exported.turns[0].speechDelivery.server.bytesRead, 19200);
  assert.ok(!JSON.stringify(exported).includes("private"));
  assert.match(exported.metadata.speechDeliveryTiming, /never subtract/);
  assert.match(timingCsv(timings.snapshot()), /speechDeliveryRetrieval/);
  assert.match(timingCsv(timings.snapshot()), /"received"/);
});

test("late diagnostic completion cannot recreate cleared turns or attach to another agent", async () => {
  const timings = new TurnTimings({ uuid: () => "trace" });
  timings.begin("agent"); let done;
  const finish = timings.captureSpeechDelivery("agent", "event", response(), () => new Promise(resolve => { done = resolve; }));
  finish(); timings.bind("trace", "event"); timings.clear();
  done(Response.json(snapshot())); await flush();
  assert.deepEqual(timings.snapshot(), []); assert.equal(timings.events.size, 0);
  timings.begin("other"); timings.bind("trace", "event");
  assert.equal(timings.snapshot()[0].speechDelivery, undefined);
});

test("missing, expired, failed, mismatched and timed-out diagnostics remain separate from playback failures", async t => {
  for (const [kind, expected] of [["missing", "none"], ["expired", "unavailable"], ["failed", "error"], ["mismatch", "invalid"], ["oversize", "invalid"], ["badstep", "invalid"], ["timeout", "error"]]) {
    const timings = new TurnTimings({ uuid: () => "trace" });
    timings.begin("agent"); timings.bind("trace", "event");
    if (kind === "timeout") t.mock.timers.enable({ apis: ["setTimeout"] });
    const finish = timings.captureSpeechDelivery("agent", "event", kind === "missing" ? new Response() : response(), async (_id, signal) => {
      if (kind === "missing") assert.fail("Legacy response must not fetch detail");
      if (kind === "expired") return new Response(null, { status: 404 });
      if (kind === "failed") throw new Error("private-error");
      if (kind === "timeout") return new Promise((_, reject) => signal.addEventListener("abort", () => reject(new Error("timeout"))));
      if (kind === "badstep") return Response.json({ ...snapshot(), steps: [{ phase: "private-unknown-phase" }] });
      return Response.json({ ...snapshot(), ...(kind === "mismatch" ? { id: "wrong" } : { steps: Array(257).fill({}) }) });
    });
    finish();
    if (kind === "timeout") t.mock.timers.tick(5001);
    await flush();
    const turn = timings.snapshot()[0];
    assert.equal(turn.speechDelivery?.retrieval || "none", expected);
    assert.equal(turn.stages.audio_failed, undefined); assert.equal(turn.stages.rejected, undefined);
    assert.ok(!JSON.stringify(turn).includes("private"));
    if (kind === "timeout") t.mock.timers.reset();
  }
});
