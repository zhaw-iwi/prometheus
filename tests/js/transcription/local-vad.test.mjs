import assert from "node:assert/strict";
import test from "node:test";
import { LocalVadSegmenter } from "../../../src/main/resources/public/transcription/local-vad.js";

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
