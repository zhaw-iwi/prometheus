import test from "node:test";
import assert from "node:assert/strict";
import { summarize } from "../../needforspeed/summarize.mjs";

test("latency report separates missing/invalid clocks, failures, and unknown usage", () => {
  const report = summarize({ metadata: { source: "fixture", configuration: "synthetic", workload: "warm ordinary" }, turns: [
    { id: "a", stages: { last_voice: 0, audio_playing: 1000 } },
    { id: "b", stages: { last_voice: 100, audio_playing: 3100, audio_stopped: 3200 } },
    { id: "c", stages: { audio_failed: 80 } },
    { id: "d", stages: { last_voice: 200, audio_playing: 100 } },
  ] }, `latency trace=a request=r1 stage=inference_usage model=model effort=none purpose=DECISION promptTokens=12 completionTokens=3
latency trace=a request=r1 stage=inference model=model effort=none purpose=DECISION requests=1 status=ok
latency trace=b request=r2 stage=inference model=model effort=none purpose=DECISION requests=1 status=error
latency trace=unrelated request=r3 stage=inference model=model effort=none purpose=DECISION requests=1 status=ok`);
  assert.deepEqual(report.measurements.voiceResponse, { samples: 2, missing: 1, invalid: 1, p50Ms: 2000, p95Ms: 2900, fewerThan50Samples: true });
  assert.equal(report.measurements.presentation.p50Ms, null);
  assert.deepEqual(report.textInference[0], { model: "model", effort: "none", purpose: "DECISION", requests: 2, errors: 1,
    knownPromptTokens: 12, knownCompletionTokens: 3, missingUsage: 1 });
  assert.equal(report.statuses.audio_failed, 1); assert.equal(report.statuses.audio_stopped, 1);
  assert.ok(report.cost.startsWith("NOT MEASURED"));
});

test("empty exports and missing metadata cannot masquerade as successful live evidence", () => {
  assert.throws(() => summarize({ turns: [] }), /metadata/);
  const report = summarize({ metadata: { source: "live", configuration: "baseline", workload: "warm" }, turns: [] });
  assert.equal(report.measurements.voiceResponse.samples, 0); assert.equal(report.measurements.voiceResponse.p95Ms, null);
  assert.equal(report.textInference, null);
});
