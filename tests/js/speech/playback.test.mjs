import assert from "node:assert/strict";
import test from "node:test";
import {
  BehaviourSpeechPlaybackQueue,
  OutputLease,
} from "../../../src/main/resources/public/speech/playback.js";

test("orders live speech, deduplicates IDs, and records replay as deliberately skipped", async () => {
  const calls = [];
  const queue = new BehaviourSpeechPlaybackQueue({
    synthesize: async (item) => { calls.push(`load:${item.eventId}`); return item.eventId; },
    play: async (_resource, item) => { calls.push(`play:${item.eventId}`); },
  });

  assert.equal(queue.enqueue(item("one", "live")), true);
  assert.equal(queue.enqueue(item("replay", "replay")), false);
  assert.equal(queue.enqueue(item("one", "live")), false);
  assert.equal(queue.enqueue(item("two", "live")), true);
  await queue.whenIdle();

  assert.deepEqual(calls, ["load:one", "play:one", "load:two", "play:two"]);
  assert.deepEqual(queue.snapshot(), {
    queued: [], current: null, completed: ["one", "two"], failed: [], skipped: ["replay"],
  });
});

test("keeps input gated across failure recovery and reopens it after the ordered queue", async () => {
  const gate = [];
  const states = [];
  const queue = new BehaviourSpeechPlaybackQueue({
    synthesize: async (candidate) => {
      if (candidate.eventId === "broken") throw new Error("provider rejected synthesis");
      return candidate.eventId;
    },
    play: async () => {},
    setInputEnabled: (enabled) => gate.push(enabled),
    onStatus: ({ state, eventId }) => states.push(`${state}:${eventId}`),
  });

  queue.enqueue(item("broken", "live"));
  queue.enqueue(item("recovered", "live"));
  await queue.whenIdle();

  assert.deepEqual(gate, [false, true]);
  assert.deepEqual(queue.snapshot().failed, ["broken"]);
  assert.deepEqual(queue.snapshot().completed, ["recovered"]);
  assert.ok(states.includes("failed:broken"));
  assert.ok(states.includes("speaking:recovered"));
});

test("explicit resume delivery can replay the same persisted event on each speech-mode start", async () => {
  const calls = [];
  const queue = new BehaviourSpeechPlaybackQueue({
    synthesize: async (candidate) => candidate.eventId,
    play: async (_resource, candidate) => { calls.push(candidate.eventId); },
  });

  assert.equal(queue.enqueue({ eventId: "starter", delivery: "resume" }), true);
  await queue.whenIdle();
  assert.equal(queue.enqueue({ eventId: "starter", speech: "Welcome.", delivery: "live" }), false);
  assert.equal(queue.enqueue({ eventId: "starter", delivery: "resume" }), true);
  await queue.whenIdle();

  assert.deepEqual(calls, ["starter", "starter"]);
});

test("Stop aborts current playback, skips queued IDs, releases ownership, and reopens input", async () => {
  const gate = [];
  const leaseCalls = [];
  let speaking;
  const started = new Promise((resolve) => { speaking = resolve; });
  const queue = new BehaviourSpeechPlaybackQueue({
    lease: {
      acquire: () => { leaseCalls.push("acquire"); return true; },
      release: () => leaseCalls.push("release"),
    },
    synthesize: async (candidate) => candidate.eventId,
    play: async (_resource, _candidate, signal) => new Promise((resolve, reject) => {
      speaking();
      signal.addEventListener("abort", () => reject(new DOMException("stopped", "AbortError")), { once: true });
    }),
    setInputEnabled: (enabled) => gate.push(enabled),
  });

  queue.enqueue(item("current", "live"));
  queue.enqueue(item("queued", "live"));
  await started;
  await queue.stop();

  assert.deepEqual(queue.snapshot().skipped.sort(), ["current", "queued"]);
  assert.deepEqual(gate, [false, true]);
  assert.ok(leaseCalls.includes("release"));
});

test("per-agent output lease admits one owner and recovers after release", () => {
  const storage = memoryStorage();
  const first = new OutputLease({ agentId: "agent", ownerId: "first", storage, heartbeatMs: 60000 });
  const second = new OutputLease({ agentId: "agent", ownerId: "second", storage, heartbeatMs: 60000 });

  assert.equal(first.acquire(), true);
  assert.equal(second.acquire(), false);
  first.release();
  assert.equal(second.acquire(), true);
  second.release();
});

function item(eventId, delivery) {
  return { eventId, delivery, speech: `Speech ${eventId}` };
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
}
