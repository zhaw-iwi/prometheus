// Bounded metadata only. Never derive latency by subtracting different clocks.
const id = value => typeof value === "string" && /^[\w-]{1,128}$/.test(value) ? value : null;
const name = value => typeof value === "string" && /^[\w.:/-]{1,128}$/.test(value) ? value : null;
const number = value => Number.isFinite(value) ? value : null;
const pick = (value = {}, strings = [], numbers = []) => Object.fromEntries([
  ...strings.map(key => [key, id(value[key])]), ...numbers.map(key => [key, number(value[key])]),
]);
const trace = value => ({ ...pick(value, ["phase", "receiptId", "segmentId", "outcome", "revision", "sourceId", "eventId", "clientEventId"],
  ["serverMs", "sequence", "providerStartMs", "providerEndMs", "contentBytes"]),
  sourceIds: (Array.isArray(value.sourceIds) ? value.sourceIds : []).slice(0, 16).map(id).filter(Boolean),
  type: typeof value.type === "string" && /^[a-z_.]{1,100}$/.test(value.type) ? value.type : null });
const audioNumbers = ["readyState", "mediaTimeMs", "volume", "mediaErrorCode", "statsMs", "packetsReceived", "packetsLost", "packetsSent",
  "jitter", "concealedSamples", "silentConcealedSamples", "concealmentEvents", "totalSamplesReceived", "jitterBufferDelay",
  "jitterBufferEmittedCount", "audioLevel", "totalAudioEnergy", "totalSamplesDuration"];
const audioWindow = value => pick(value, [], ["serverMs", "endServerMs", "inputSamples", "outputSamples", "voicedInputSamples",
  "voicedOutputSamples", "inputPeakRms", "outputPeakRms"]);
const stopReasons = new Set(["operator_stop", "scope_change", "visitor_departure", "presence_stale", "startup_failed",
  "output_ownership_lost", "microphone_ownership_lost", "microphone_ended", "output_track_ended", "playback_blocked",
  "transport_failure", "data_channel_closed", "provider_session_closed", "provider_error", "invalid_provider_event",
  "backend_failure", "backend_session_closed", "authorization_revoked", "input_control_failed"]);
const stopClass = reason => reason === "operator_stop" ? "intentional_stop"
  : ["scope_change", "visitor_departure", "presence_stale", "authorization_revoked", "backend_session_closed"].includes(reason) ? "lifecycle_closure"
  : ["transport_failure", "data_channel_closed", "backend_failure"].includes(reason) ? "transport_failure"
  : reason === "provider_session_closed" ? "provider_session_closure"
  : stopReasons.has(reason) ? "local_failure" : "unknown";
function retain(previous, incoming, limit, key = value => value.sequence ?? JSON.stringify(value)) {
  const entries = new Map((previous?.recent || []).map(value => [key(value), value]));
  for (const value of incoming) entries.set(key(value), value);
  const removed = Math.max(0, entries.size - limit);
  return { recent: [...entries.values()].slice(-limit), retainedDropped: (previous?.retainedDropped || 0) + removed };
}
export class LiveDiagnostics {
  constructor({ now = () => performance.now(), limit = 16, traceLimit = 128 } = {}) {
    Object.assign(this, { now, limit, traceLimit }); this.sessions = new Map(); this.listeners = new Set(); this.dropped = 0;
  }
  record(value) {
    const handle = id(value.handle); if (!handle) return;
    if (!this.sessions.has(handle)) this.sessions.set(handle, { handle, agentId: id(value.agentId), browser: [], browserDropped: 0,
      media: [], mediaDropped: 0, finalization: "pending",
      clocks: { browser: "performance_now_ms", server: "unix_ms", provider: "audio_offset_ms", media: "media_element_ms", rtc: "webrtc_stats_ms" } });
    const entry = this.sessions.get(handle);
    const browserMs = number(value.browserMs) ?? this.now();
    if (value.phase === "caption_received" && value.direction === "output")
      entry.latestOutputReceipt = { receiptId: id(value.receiptId), browserMs };
    if (value.phase === "caption_received" && value.direction === "input") {
      entry.latestOutputReceipt = null; entry.speakingSource = null;
    }
    if (value.phase === "output_end") entry.outputEnd = {
      browserMs, reason: stopReasons.has(value.reason) ? value.reason : "unknown", classification: stopClass(value.reason),
      utteranceEnd: "unknown", assessment: "local_output_stop_only" };
    const recent = item => item && browserMs >= item.browserMs && browserMs - item.browserMs <= 30000;
    if (["playback", "media_track", "rtc_audio"].includes(value.phase)) {
      entry.media.push({ browserMs: number(value.browserMs) ?? this.now(), ...pick(value, ["phase", "outcome", "direction"], audioNumbers),
        contextRevision: id(entry.server?.context?.revision), speakingSourceId: recent(entry.speakingSource) ? entry.speakingSource.sourceId : null,
        outputReceiptId: recent(entry.latestOutputReceipt) ? entry.latestOutputReceipt.receiptId : null,
        association: "latest_observed_context_not_response_binding",
        ...Object.fromEntries(["paused", "muted", "enabled", "ended"].map(key => [key, typeof value[key] === "boolean" ? value[key] : null])) });
      if (entry.media.length > 2400) { entry.media.shift(); entry.mediaDropped++; }
    } else if (value.phase !== "status") {
      entry.browser.push({ browserMs, ...pick(value, ["phase", "receiptId", "segmentId", "cueId", "outcome", "direction"], ["providerStartMs", "providerEndMs", "graceMs", "durationMs"]),
        reason: stopReasons.has(value.reason) ? value.reason : null });
      if (entry.browser.length > this.traceLimit) { entry.browser.shift(); entry.browserDropped++; }
    }
    if (value.phase === "capture") entry.captureConfiguration = Object.fromEntries(["requested", "applied"].map(group => [group,
      Object.fromEntries(["echoCancellation", "noiseSuppression", "autoGainControl", "voiceIsolation"].map(key => [key,
        typeof value.capture?.[group]?.[key] === "boolean" ? value.capture[group][key] : null]))]));
    if (value.phase === "stopped") entry.finalization = value.finalized === true ? "confirmed" : "unconfirmed";
    if (value.status) {
      const status = value.status, capture = status.capture || {}, context = status.context || {};
      const previous = entry.server;
      const provider = status.providerTelemetry || {};
      entry.provider = { ...pick(provider, ["closeReason"], ["usageSeconds", "contextUsageRatio"]),
        problems: (provider.problems || []).slice(-16).map(value => pick(value, ["code", "type", "clientEventId"], ["serverMs"])),
        configuration: { ...Object.fromEntries(["model", "voice", "build"].map(key => [key, name(provider.configuration?.[key])])),
          ...pick(provider.configuration, [], ["requestTimeoutMs", "telemetryVersion", "silenceMs", "userSilenceMs", "latenessMs", "maxOpenMs", "maxAudioGapMs"]) } };
      entry.server = { ...pick(status, ["state", "epoch", "reason", "captureState"], ["eventCount", "inputSamples", "outputSamples", "voicedInputSamples", "dropped"]),
        finalized: status.finalized === true, ...retain(previous, (status.recent || []).slice(-64).map(trace), 1024),
        capture: { ...pick(capture, ["state"], ["queued", "queueHighWater", "receipts", "dropped"]),
          ...retain(previous?.capture, (capture.recent || []).slice(-64).map(trace), 1024) },
        context: { ...pick(context, ["state", "revision"], ["dropped", "pendingClarifications"]),
          ...retain(previous?.context, (context.recent || []).slice(-64).map(trace), 1024) },
        audio: { ...pick(status.audio, [], ["dropped"]),
          ...retain(previous?.audio, (status.audio?.recent || []).slice(-129).map(audioWindow), 600, value => value.serverMs) } };
      const occasion = entry.server.context.recent.findLast(value => value.phase === "ack"
        && value.type === "session.commentary.append" && value.sourceId && value.sequence > (entry.lastSpeakingSequence || 0));
      if (occasion) {
        entry.lastSpeakingSequence = occasion.sequence;
        entry.speakingSource = { sourceId: occasion.sourceId, browserMs };
      }
    }
    const contextFailure = entry.server?.context?.recent?.findLast(value =>
      value.phase === "session_closed_during_delivery" || value.phase === "failed" && entry.server.context.state === "context_delivery_unconfirmed");
    if (contextFailure && entry.contextFailureObserved?.sequence !== contextFailure.sequence)
      entry.contextFailureObserved = {sequence: contextFailure.sequence, browserMs};
    const nearOperatorStop = entry.outputEnd?.reason === "operator_stop" && entry.contextFailureObserved
      && Math.abs(entry.outputEnd.browserMs - entry.contextFailureObserved.browserMs) <= 5000;
    entry.contextAssessment = contextFailure ? {
      sequence: contextFailure.sequence, serverMs: contextFailure.serverMs,
      classification: contextFailure.phase === "session_closed_during_delivery" ? "expected_teardown"
        : nearOperatorStop ? "unconfirmed_delivery_near_operator_stop" : "active_delivery_failure",
      timing: "separate_clocks_no_causal_claim"
    } : null;
    entry.coverage = { audioContent: "not_collected", physicalAudibility: "not_measured", providerTurnEnd: "not_available",
      captureGaps: "incomplete_evidence_not_proof_of_audible_clipping",
      outputEnd: entry.outputEnd ? "local_stop_observed" : "unknown", responseAssociation: "approximate_context_only",
      usage: entry.provider?.usageSeconds === null || !entry.provider ? "not_reported" : "cumulative_latest",
      media: entry.media.length ? "observed" : "not_observed", captureSettings: entry.captureConfiguration ? "observed" : "not_observed" };
    while (this.sessions.size > this.limit) { this.sessions.delete(this.sessions.keys().next().value); this.dropped++; }
    for (const listener of this.listeners) listener();
  }
  snapshot() { return { sessions: structuredClone([...this.sessions.values()]), droppedSessions: this.dropped }; }
  clear() { this.sessions.clear(); this.dropped = 0; for (const listener of this.listeners) listener(); }
  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
}
export const liveDiagnostics = new LiveDiagnostics();
