// Bounded metadata only. Never derive latency by subtracting different clocks.
const id = value => typeof value === "string" && /^[\w-]{1,128}$/.test(value) ? value : null;
const number = value => Number.isFinite(value) ? value : null;
const pick = (value = {}, strings = [], numbers = []) => Object.fromEntries([
  ...strings.map(key => [key, id(value[key])]), ...numbers.map(key => [key, number(value[key])]),
]);
const trace = value => ({ ...pick(value, ["phase", "receiptId", "segmentId", "outcome", "revision", "sourceId", "eventId", "clientEventId"],
  ["serverMs", "sequence", "providerStartMs", "providerEndMs"]),
  type: typeof value.type === "string" && /^[a-z_.]{1,100}$/.test(value.type) ? value.type : null });
export class LiveDiagnostics {
  constructor({ now = () => performance.now(), limit = 16, traceLimit = 128 } = {}) {
    Object.assign(this, { now, limit, traceLimit }); this.sessions = new Map(); this.listeners = new Set(); this.dropped = 0;
  }
  record(value) {
    const handle = id(value.handle); if (!handle) return;
    if (!this.sessions.has(handle)) this.sessions.set(handle, { handle, agentId: id(value.agentId), browser: [], browserDropped: 0,
      finalization: "pending", clocks: { browser: "performance_now_ms", server: "unix_ms", provider: "audio_offset_ms" } });
    const entry = this.sessions.get(handle);
    if (value.phase !== "status") {
      entry.browser.push({ browserMs: this.now(), ...pick(value, ["phase", "receiptId", "segmentId", "outcome"], ["providerStartMs", "providerEndMs"]) });
      if (entry.browser.length > this.traceLimit) { entry.browser.shift(); entry.browserDropped++; }
    }
    if (value.phase === "stopped") entry.finalization = value.finalized === true ? "confirmed" : "unconfirmed";
    if (value.status) {
      const status = value.status, capture = status.capture || {}, context = status.context || {};
      entry.server = { ...pick(status, ["state", "epoch", "reason", "captureState"], ["eventCount", "inputSamples", "outputSamples", "voicedInputSamples", "dropped"]),
        finalized: status.finalized === true, recent: (status.recent || []).slice(-64).map(trace),
        capture: { ...pick(capture, ["state"], ["queued", "queueHighWater", "receipts", "dropped"]), recent: (capture.recent || []).slice(-64).map(trace) },
        context: { ...pick(context, ["state", "revision"], ["dropped", "pendingClarifications"]), recent: (context.recent || []).slice(-64).map(trace) } };
    }
    while (this.sessions.size > this.limit) { this.sessions.delete(this.sessions.keys().next().value); this.dropped++; }
    for (const listener of this.listeners) listener();
  }
  snapshot() { return { sessions: structuredClone([...this.sessions.values()]), droppedSessions: this.dropped }; }
  clear() { this.sessions.clear(); this.dropped = 0; for (const listener of this.listeners) listener(); }
  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
}
export const liveDiagnostics = new LiveDiagnostics();
