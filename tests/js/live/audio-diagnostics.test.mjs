import test from "node:test";
import assert from "node:assert/strict";
import { LiveAudioMonitor } from "../../../src/main/resources/public/live/audio-diagnostics.js";

test("media events and RTC counters remain separate and never control playback or retain private stats", async () => {
  const audio = Object.assign(new EventTarget(), { paused: false, muted: false, readyState: 4, currentTime: 2, volume: 1 });
  const track = Object.assign(new EventTarget(), { enabled: true, muted: false, readyState: "live" });
  const events = []; const monitor = new LiveAudioMonitor(audio, value => events.push(value));
  monitor.track(track, "output"); audio.dispatchEvent(new Event("waiting"));
  track.muted = true; track.dispatchEvent(new Event("mute"));
  await monitor.sample({ getStats: async () => new Map([["private-id", {
    type: "inbound-rtp", kind: "audio", timestamp: 7, packetsLost: 2, concealedSamples: 960, jitter: .01,
    trackIdentifier: "private", remoteAddress: "private", codec: "private",
  }]]) });
  assert.equal(events.find(value => value.outcome === "waiting").mediaTimeMs, 2000);
  assert.equal(events.find(value => value.outcome === "mute").muted, true);
  assert.equal(events.at(-1).concealedSamples, 960); assert.equal(events.at(-1).direction, "output");
  assert.equal(JSON.stringify(events).includes("private"), false); assert.equal(audio.paused, false);
  monitor.close(); const count = events.length;
  audio.dispatchEvent(new Event("pause")); track.dispatchEvent(new Event("ended"));
  assert.equal(events.length, count);
});

test("slow stats never overlap or reappear after Stop and failures do not stop speech", async () => {
  const events = []; const monitor = new LiveAudioMonitor({}, value => events.push(value));
  let release, calls = 0;
  const peer = { getStats: () => { calls++; return new Promise(resolve => { release = resolve; }); } };
  const first = monitor.sample(peer); await monitor.sample(peer); assert.equal(calls, 1);
  monitor.close(); release(new Map()); await first; assert.equal(events.length, 0);
  const next = new LiveAudioMonitor({}, value => events.push(value));
  await next.sample({ getStats: async () => { throw new Error("private"); } });
  assert.deepEqual(events, [{ phase: "rtc_audio", outcome: "unavailable" }]); next.close();
});
