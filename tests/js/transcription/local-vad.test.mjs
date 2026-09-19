import assert from "node:assert/strict";
import test from "node:test";
import { LocalVadSegmenter } from "../../../src/main/resources/public/transcription/local-vad.js";
import { evaluatePauseCorpus } from "../../needforspeed/replay-vad.mjs";

test("local VAD commits once after stable speech and configured silence", () => {
  const events = [];
  const vad = new LocalVadSegmenter({
    silenceDurationSeconds: 0.5,
    minimumSpeechMs: 100,
    onSpeechStart: () => events.push("start"),
    onSpeechStop: ({ reason }) => events.push(`stop:${reason}`),
    onCommit: ({ reason }) => events.push(`commit:${reason}`),
  });

  vad.observe(0.03, 0);
  vad.observe(0.03, 100);
  vad.observe(0.03, 200);
  vad.observe(0, 650);
  vad.observe(0, 700);

  assert.deepEqual(events, ["start", "stop:silence", "commit:silence"]);
});

test("local VAD ignores short noise and validates silence limits", () => {
  let commits = 0;
  const vad = new LocalVadSegmenter({ onCommit: () => commits += 1 });
  vad.observe(0.03, 0);
  vad.observe(0, 50);
  vad.observe(0, 2000);
  assert.equal(commits, 0);
  assert.throws(() => vad.configure(0.1), /between 0.5 and 10/);
});

test("responsive silence resets on intervening speech and commits once at 800ms", () => {
  const commits = [], vad = new LocalVadSegmenter({ silenceDurationSeconds: 0.8, onCommit: c => commits.push(c) });
  for (const [level, time] of [[0.03, 0], [0.03, 150], [0, 900], [0.03, 940], [0, 1739]]) vad.observe(level, time);
  assert.equal(commits.length, 0);
  vad.observe(0, 1740); vad.observe(0, 2000);
  assert.deepEqual(commits, [{ reason: "silence", observedAtMs: 1740, lastVoiceAtMs: 940 }]);
});

test("maximum duration remains bounded and reset discards incomplete segments", () => {
  const commits = [], vad = new LocalVadSegmenter({ maximumSegmentMs: 1000, onCommit: c => commits.push(c) });
  vad.observe(0.03, 0); vad.observe(0.03, 150); vad.observe(0.03, 1000);
  assert.equal(commits[0].reason, "maximum_duration");
  vad.observe(0.03, 1100); vad.observe(0.03, 1300); vad.reset(); vad.observe(0, 4000);
  assert.equal(commits.length, 1);
});

test("frozen pause replay records the responsive quality tradeoff instead of promoting it", async () => {
  const rows = await evaluatePauseCorpus();
  assert.deepEqual(rows.map(row => row.commits), [2, 1, 2, 1, 4, 2, 2, 2]);
  assert.ok(rows.every(row => row.missingSegments === 0));
  assert.ok(rows.every(row => row.completionDelayMs.every(delay => delay === row.silenceSeconds * 1000)));
});
