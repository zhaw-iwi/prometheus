// Bounded metadata only. Never derive latency by subtracting different clocks.
const id = value => typeof value === "string" && /^[\w-]{1,128}$/.test(value) ? value : null;
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
    if (["playback", "media_track", "rtc_audio"].includes(value.phase)) {
      entry.media.push({ browserMs: number(value.browserMs) ?? this.now(), ...pick(value, ["phase", "outcome", "direction"], audioNumbers),
        ...Object.fromEntries(["paused", "muted", "enabled", "ended"].map(key => [key, typeof value[key] === "boolean" ? value[key] : null])) });
      if (entry.media.length > 2400) { entry.media.shift(); entry.mediaDropped++; }
    } else if (value.phase !== "status") {
      entry.browser.push({ browserMs: number(value.browserMs) ?? this.now(), ...pick(value, ["phase", "receiptId", "segmentId", "outcome"], ["providerStartMs", "providerEndMs"]) });
      if (entry.browser.length > this.traceLimit) { entry.browser.shift(); entry.browserDropped++; }
    }
    if (value.phase === "stopped") entry.finalization = value.finalized === true ? "confirmed" : "unconfirmed";
    if (value.status) {
      const status = value.status, capture = status.capture || {}, context = status.context || {};
      const previous = entry.server;
      entry.server = { ...pick(status, ["state", "epoch", "reason", "captureState"], ["eventCount", "inputSamples", "outputSamples", "voicedInputSamples", "dropped"]),
        finalized: status.finalized === true, ...retain(previous, (status.recent || []).slice(-64).map(trace), 1024),
        capture: { ...pick(capture, ["state"], ["queued", "queueHighWater", "receipts", "dropped"]),
          ...retain(previous?.capture, (capture.recent || []).slice(-64).map(trace), 1024) },
        context: { ...pick(context, ["state", "revision"], ["dropped", "pendingClarifications"]),
          ...retain(previous?.context, (context.recent || []).slice(-64).map(trace), 1024) },
        audio: { ...pick(status.audio, [], ["dropped"]),
          ...retain(previous?.audio, (status.audio?.recent || []).slice(-129).map(audioWindow), 600, value => value.serverMs) } };
    }
    while (this.sessions.size > this.limit) { this.sessions.delete(this.sessions.keys().next().value); this.dropped++; }
    for (const listener of this.listeners) listener();
  }
  snapshot() { return { sessions: structuredClone([...this.sessions.values()]), droppedSessions: this.dropped }; }
  clear() { this.sessions.clear(); this.dropped = 0; for (const listener of this.listeners) listener(); }
  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
}
export const liveDiagnostics = new LiveDiagnostics();
