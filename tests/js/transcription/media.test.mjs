import assert from "node:assert/strict";
import test from "node:test";
import { MicrophoneLease } from "../../../src/main/resources/public/transcription/media.js";

test("microphone lease rejects a second live owner and becomes reusable after release", () => {
  const storage = memoryStorage();
  const first = new MicrophoneLease({ storage, ownerId: "first", now: () => 1000, heartbeatMs: 60000 });
  const second = new MicrophoneLease({ storage, ownerId: "second", now: () => 1000, heartbeatMs: 60000 });

  first.acquire();
  assert.throws(() => second.acquire(), /Another PROMETHEUS tab/);
  first.release();
  assert.doesNotThrow(() => second.acquire());
  second.release();
});

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}
