import test from "node:test";
import assert from "node:assert/strict";
import { CountdownCue, SpeechCuePlayback } from "../../../src/main/resources/public/live/speech-cue.js";

const first = { id: "11111111-1111-4111-8111-111111111111", roundId: "22222222-2222-4222-8222-222222222222" };
const second = { id: "33333333-3333-4333-8333-333333333333", roundId: "44444444-4444-4444-8444-444444444444" };
function fixture() {
  let now = 0; const events = [], gate = new CountdownCue(value => events.push(value), () => now);
  const sample = (duration, output = 0, input = 0, playing = true) => {
    for (let i = 0; i < duration; i += 20) { now += 20; gate.sample({ output, input, playing }); }
  };
  const caption = (text = "Eins, zwei, drei!", id = "count", start_ms = 100) => gate.receive({ type: "session.output_transcript.delta", event_id: id, delta: text, start_ms, end_ms: start_ms + 300 });
  return { gate, events, sample, caption, jump(value) { now += value; } };
}
test("countdown text plus observed output followed by quiet emits once; track activity alone does not", () => {
  const f = fixture(); f.gate.arm(first); f.sample(1000, .1); f.sample(1000); assert.equal(f.events.length, 0);
  f.caption("Eins, ", "one"); f.caption("zwei, ", "two"); f.caption("drei!", "three");
  f.sample(600, .1); f.sample(780); assert.equal(f.events.length, 0); f.sample(20);
  assert.equal(f.events[0].outcome, "complete"); assert.deepEqual(f.events[0].receiptIds, ["one", "two", "three"]);
  assert.equal(f.gate.arm(first), false); f.sample(2000); assert.equal(f.events.length, 1);
});
test("silence without output, missing countdown and unfinished captions never open capture", () => {
  for (const words of ["Eins, zwei", "Wir spielen jetzt", "Eins, zwei, drei! Warte noch."]) {
    const f = fixture(); f.gate.arm(first); f.caption(words); f.sample(1000, .1); f.sample(2000);
    assert.equal(f.events.length, 0); f.sample(28000); assert.equal(f.events[0].outcome, "timeout");
  }
  const f = fixture(); f.gate.arm(first); f.caption(); f.sample(2000); assert.equal(f.events.length, 0);
});
test("speech interruption cancels while isolated input noise does not", () => {
  const f = fixture(); f.gate.arm(first); f.caption(); f.sample(400, .1); f.sample(20, 0, .1); f.sample(800);
  assert.equal(f.events[0].outcome, "complete");
  const g = fixture(); g.gate.arm(first); g.caption(); g.sample(200, .1, .1); assert.equal(g.events[0].outcome, "interrupted");
  const h = fixture(); h.gate.arm(first); h.gate.receive({ type: "session.input_transcript.delta", delta: "Stopp" });
  assert.equal(h.events[0].outcome, "interrupted");
});
test("playback failure, event-loop gaps and ambiguous countdowns recover without completion", () => {
  const f = fixture(); f.gate.arm(first); f.sample(20, .1, 0, false); assert.equal(f.events[0].outcome, "unavailable");
  const g = fixture(); g.gate.arm(first); g.sample(20, .1); g.jump(300); g.sample(20); assert.equal(g.events[0].outcome, "unavailable");
  const h = fixture(); h.gate.arm(first); h.caption("Eins, zwei, drei. Eins, zwei, drei!"); assert.equal(h.events[0].outcome, "unavailable");
});
test("new rounds reset evidence, duplicate arming preserves it, and Stop clears without a callback", () => {
  const f = fixture(); f.gate.arm(first); f.caption(); f.sample(400, .1); assert.equal(f.gate.arm(first), false);
  f.caption(); f.sample(800); assert.equal(f.events.length, 1);
  f.gate.arm(second); f.sample(1000, .1); f.sample(1000); assert.equal(f.events.length, 1);
  f.caption(); f.gate.clear(); f.sample(1000); assert.equal(f.events.length, 1);
});
test("older session-timeline captions cannot satisfy a new round", () => {
  const f = fixture(); f.gate.arm({ ...first, minimumStartMs: 1000 }); f.caption("Eins, zwei, drei!", "old", 100);
  f.sample(1000, .1); f.sample(1000); assert.equal(f.events.length, 0);
  f.caption("Eins, zwei, drei!", "new", 1100); f.sample(400, .1); f.sample(800); assert.equal(f.events[0].outcome, "complete");
});
test("playback observation uses existing streams, releases its nodes and ignores a late sampling callback", () => {
  let now = 0, sample, output = .1, closed = 0, disconnected = 0;
  const events = [], streams = [], context = { state: "running", resume: async () => {}, close: async () => { closed++; },
    createMediaStreamSource(stream) { streams.push(stream); return { connect() {}, disconnect() { disconnected++; } }; },
    createAnalyser() { const index = streams.length; return { fftSize: 1024, getFloatTimeDomainData(samples) { samples.fill(index === 1 ? output : 0); }, disconnect() { disconnected++; } }; } };
  const playback = new SpeechCuePlayback({ paused: false, muted: false, volume: 1 }, value => events.push(value), {
    now: () => now, createContext: () => context, schedule: callback => { sample = callback; return 1; }, cancel() {},
  });
  playback.streams("existing output", "existing input"); playback.arm({ speechCue: first });
  playback.receive({ type: "session.output_transcript.delta", event_id: "count", delta: "Eins, zwei, drei!", start_ms: 0, end_ms: 300 });
  for (let i = 0; i < 20; i++) { now += 20; sample(); }
  output = 0; for (let i = 0; i < 40; i++) { now += 20; sample(); }
  assert.equal(events[0].outcome, "complete"); assert.deepEqual(streams, ["existing output", "existing input"]);
  playback.close(); sample(); assert.equal(closed, 1); assert.equal(disconnected, 4); assert.equal(events.length, 1);
});
