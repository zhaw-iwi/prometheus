import test from "node:test";
import assert from "node:assert/strict";
import { LiveDiagnostics } from "../../../src/main/resources/public/live/diagnostics.js";

test("Live exports retain bounded correlations, separate clocks and uncertain finalization without content", () => {
  const diagnostics = new LiveDiagnostics({ now: () => 10, traceLimit: 2, limit: 1 });
  const privateFields = { text: "private", audio: "private", sdp: "private", accessCode: "private", deviceId: "private" };
  diagnostics.record({ handle: "session1", agentId: "agent1", phase: "started", ...privateFields });
  for (let i = 0; i < 4; i++) diagnostics.record({ handle: "session1", phase: "caption_received", receiptId: "r" + i, providerStartMs: i, ...privateFields });
  diagnostics.record({ handle: "session1", phase: "stopped", finalized: false, status: { state: "closed", epoch: "epoch1", ...privateFields,
    context: { revision: "revision1", recent: [{ phase: "ack", sourceId: "source1", serverMs: 999, ...privateFields }] } } });
  const result = diagnostics.snapshot(), session = result.sessions[0];
  assert.equal(session.finalization, "unconfirmed"); assert.equal(session.browserDropped, 4);
  assert.equal(session.browser.length, 2); assert.equal(session.server.context.recent[0].sourceId, "source1");
  assert.deepEqual(session.clocks, { browser: "performance_now_ms", server: "unix_ms", provider: "audio_offset_ms", media: "media_element_ms", rtc: "webrtc_stats_ms" });
  assert.equal(JSON.stringify(result).includes("private"), false);
  diagnostics.record({ handle: "session2", phase: "started" }); assert.equal(diagnostics.snapshot().droppedSessions, 1);
});

test("audio telemetry is allowlisted and polling retains earlier context and updates open audio windows", () => {
  const diagnostics = new LiveDiagnostics({ now: () => 100 });
  const privateFields = { sdp: "private", deviceId: "private", transcript: "private", samples: ["private"] };
  diagnostics.record({ handle: "s1", phase: "rtc_audio", direction: "output", browserMs: 22,
    packetsLost: 3, concealedSamples: 40, jitter: .02, ...privateFields });
  for (let i = 0; i < 2; i++) diagnostics.record({ handle: "s1", phase: "status", status: {
    context: { recent: [{ sequence: i, phase: "ack", serverMs: 50 + i, type: "session.thinking.append", contentBytes: 100, ...privateFields }] },
    audio: { recent: [{ serverMs: 20, endServerMs: 30 + i, outputSamples: 100 + i, ...privateFields }] },
  } });
  const result = diagnostics.snapshot().sessions[0];
  assert.equal(result.media[0].browserMs, 22); assert.equal(result.media[0].concealedSamples, 40);
  assert.equal(result.server.context.recent.length, 2);
  assert.equal(result.server.audio.recent.length, 1); assert.equal(result.server.audio.recent[0].outputSamples, 101);
  assert.equal(JSON.stringify(result).includes("private"), false);
  for (let i = 0; i < 2401; i++) diagnostics.record({ handle: "s1", phase: "playback", outcome: "sample" });
  assert.equal(diagnostics.snapshot().sessions[0].media.length, 2400);
  assert.equal(diagnostics.snapshot().sessions[0].mediaDropped, 2);
  assert.equal(diagnostics.snapshot().sessions[0].browser.length, 0, "Audio samples do not evict transcript correlations");
});
