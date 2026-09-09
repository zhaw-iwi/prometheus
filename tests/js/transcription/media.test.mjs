import assert from "node:assert/strict";
import test from "node:test";
import { MicrophoneLease, TranscriptionMedia } from "../../../src/main/resources/public/transcription/media.js";

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

test("active microphone replacement swaps the sender and releases the old track", async () => {
  const oldTrack = fakeTrack("old-mic");
  const nextTrack = fakeTrack("room-mic");
  const requests = [];
  const media = new TranscriptionMedia({
    mediaDevices: {
      async getUserMedia(constraints) {
        requests.push(constraints);
        return stream(nextTrack);
      },
    },
    lease: { release() {} },
  });
  media.stream = stream(oldTrack);
  const sender = {
    track: oldTrack,
    async replaceTrack(track) { this.track = track; },
  };

  const applied = await media.replaceAudioTrack({ getSenders: () => [sender] }, {
    inputDeviceId: "room-mic",
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    voiceIsolation: false,
  });

  assert.equal(requests[0].audio.deviceId.exact, "room-mic");
  assert.equal(sender.track, nextTrack);
  assert.equal(oldTrack.stopped, true);
  assert.equal(nextTrack.stopped, false);
  assert.equal(applied.deviceId, "room-mic");
});

function fakeTrack(deviceId) {
  return {
    kind: "audio",
    stopped: false,
    stop() { this.stopped = true; },
    getSettings() { return { deviceId }; },
  };
}

function stream(track) {
  return { getTracks: () => [track], getAudioTracks: () => [track] };
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}
