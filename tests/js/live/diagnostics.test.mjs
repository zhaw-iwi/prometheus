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
  assert.deepEqual(session.clocks, { browser: "performance_now_ms", server: "unix_ms", provider: "audio_offset_ms" });
  assert.equal(JSON.stringify(result).includes("private"), false);
  diagnostics.record({ handle: "session2", phase: "started" }); assert.equal(diagnostics.snapshot().droppedSessions, 1);
});
