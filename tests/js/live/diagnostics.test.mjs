import test from "node:test";
import assert from "node:assert/strict";
import { LiveDiagnostics } from "../../../src/main/resources/public/live/diagnostics.js";

test("playback gate exports bounded cue correlation and fixed outcomes without speech or samples", () => {
  const d = new LiveDiagnostics({traceLimit: 2});
  for (const outcome of ['awaiting_capture', 'input_interrupted', 'already_finished'])
    d.record({handle:'session', phase:'playback_gate', cueId:'cue-1', segmentId:'segment-1', outcome,
      transcript:'private speech', samples:[1,2,3]});
  const session = d.snapshot().sessions[0];
  assert.equal(session.browser.length, 2); assert.equal(session.browserDropped, 1);
  assert.equal(session.browser[0].cueId, 'cue-1'); assert.equal(session.browser[0].outcome, 'input_interrupted');
  assert.equal(session.browser[0].segmentId, 'segment-1');
  assert.doesNotMatch(JSON.stringify(session), /private speech|samples/);
});

test("context diagnostics distinguish observed teardown from active failure without erasing either", () => {
  for (const stopFirst of [true, false]) {
    const d = new LiveDiagnostics();
    const stop = () => d.record({handle: "s", phase: "output_end", reason: "operator_stop"});
    const failed = () => d.record({handle: "s", phase: "status", status: {context: {state: "context_delivery_unconfirmed",
      recent: [{sequence: 1, phase: "failed", serverMs: 30}]}}});
    if (stopFirst) stop(); failed();
    if (!stopFirst) { assert.equal(d.snapshot().sessions[0].contextAssessment.classification, "active_delivery_failure"); stop(); }
    let s = d.snapshot().sessions[0];
    assert.equal(s.contextAssessment.classification, "unconfirmed_delivery_near_operator_stop");
    assert.equal(s.server.context.recent[0].phase, "failed");
    d.record({handle: "s", phase: "status", status: {context: {state: "closed",
      recent: [{sequence: 2, phase: "session_closed_during_delivery", serverMs: 31}]}}});
    s = d.snapshot().sessions[0]; assert.equal(s.contextAssessment.classification, "expected_teardown");
    assert.equal(s.server.context.recent.length, 2);
    assert.match(s.coverage.captureGaps, /not_proof/);
  }
  const old = new LiveDiagnostics();
  old.record({handle: "old", phase: "status", browserMs: 100, status: {context: {state: "context_delivery_unconfirmed",
    recent: [{sequence: 1, phase: "failed", serverMs: 30}]}}});
  old.record({handle: "old", phase: "output_end", browserMs: 20100, reason: "operator_stop"});
  assert.equal(old.snapshot().sessions[0].contextAssessment.classification, "active_delivery_failure",
    "A later operator Stop cannot reclassify an earlier active failure");
});

test("presence grace metadata is session-scoped without observation payloads", () => {
  const diagnostics = new LiveDiagnostics();
  diagnostics.record({ handle: "session", phase: "presence", outcome: "presence_resumed", graceMs: 2000, durationMs: 1500, text: "private" });
  const value = diagnostics.snapshot().sessions[0].browser[0];
  assert.equal(value.graceMs, 2000); assert.equal(value.durationMs, 1500); assert.equal(value.outcome, "presence_resumed");
  assert.equal(JSON.stringify(diagnostics.snapshot()).includes("private"), false);
});

test("media correlation is bounded approximate context, clears on input and never proves an output end", () => {
  let now = 0; const diagnostics = new LiveDiagnostics({ now: () => now });
  const status = { context: { revision: "revision1", recent: [{ sequence: 1, phase: "ack",
    type: "session.commentary.append", sourceId: "occasion1" }] } };
  diagnostics.record({ handle: "s", phase: "status", status });
  diagnostics.record({ handle: "s", phase: "caption_received", direction: "output", receiptId: "receipt1", text: "private" });
  const sample = () => diagnostics.record({ handle: "s", phase: "rtc_audio", direction: "output", totalAudioEnergy: 1 });
  sample(); let session = diagnostics.snapshot().sessions[0];
  assert.equal(session.media.at(-1).speakingSourceId, "occasion1");
  assert.equal(session.media.at(-1).outputReceiptId, "receipt1");
  assert.equal(session.media.at(-1).contextRevision, "revision1");
  assert.equal(session.coverage.outputEnd, "unknown"); assert.equal(session.coverage.providerTurnEnd, "not_available");
  assert.equal(session.media.at(-1).association, "latest_observed_context_not_response_binding");
  now = 30001; sample(); session = diagnostics.snapshot().sessions[0];
  assert.equal(session.media.at(-1).speakingSourceId, null); assert.equal(session.media.at(-1).outputReceiptId, null);
  diagnostics.record({ handle: "s", phase: "caption_received", direction: "input", receiptId: "user2" });
  diagnostics.record({ handle: "s", phase: "status", status }); sample();
  assert.equal(diagnostics.snapshot().sessions[0].media.at(-1).speakingSourceId, null, "Repeated status cannot rebind an old occasion");
  status.context.recent[0].sequence = 2; status.context.recent[0].sourceId = "occasion2";
  diagnostics.record({ handle: "s", phase: "status", status }); sample();
  assert.equal(diagnostics.snapshot().sessions[0].media.at(-1).speakingSourceId, "occasion2");
  assert.equal(JSON.stringify(diagnostics.snapshot()).includes("private"), false);
});

test("local output stop classifications retain fixed causes and never invent natural completion", () => {
  const diagnostics = new LiveDiagnostics();
  for (const [reason, classification] of [["operator_stop", "intentional_stop"], ["visitor_departure", "lifecycle_closure"],
    ["authorization_revoked", "lifecycle_closure"], ["transport_failure", "transport_failure"],
    ["provider_session_closed", "provider_session_closure"], ["private failure text", "unknown"]]) {
    diagnostics.record({ handle: "s", phase: "output_end", reason });
    const end = diagnostics.snapshot().sessions[0].outputEnd;
    assert.equal(end.classification, classification); assert.equal(end.utteranceEnd, "unknown");
    assert.equal(end.assessment, "local_output_stop_only");
  }
  assert.equal(JSON.stringify(diagnostics.snapshot()).includes("private"), false);
});

test("Live usage remains cumulative and export retains safe capture/configuration/error coverage", () => {
  const diagnostics = new LiveDiagnostics();
  diagnostics.record({ handle: "s", phase: "capture", capture: { requested: { echoCancellation: true, deviceId: "private" }, applied: { echoCancellation: false } } });
  for (const usageSeconds of [12, 15]) diagnostics.record({ handle: "s", phase: "status", status: { providerTelemetry: {
    usageSeconds, contextUsageRatio: .4, closeReason: "ended", configuration: { model: "gpt-live-1", voice: "marin", requestTimeoutMs: 1000, userSilenceMs: 2000, key: "private" },
    problems: [{ code: "invalid_request", type: "error", clientEventId: "event1", message: "private" }] } } });
  const session = diagnostics.snapshot().sessions[0];
  assert.equal(session.provider.usageSeconds, 15); assert.equal(session.provider.contextUsageRatio, .4);
  assert.equal(session.captureConfiguration.applied.echoCancellation, false);
  assert.equal(session.provider.configuration.userSilenceMs, 2000);
  assert.equal(session.provider.problems[0].code, "invalid_request");
  assert.equal(session.coverage.usage, "cumulative_latest"); assert.equal(JSON.stringify(session).includes("private"), false);
});

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
