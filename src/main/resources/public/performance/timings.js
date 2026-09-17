const TRACE_HEADER = "X-Prometheus-Trace-Id";
const BEHAVIOUR_HEADER = "X-Prometheus-Behaviour-Id";
const STAGES = new Set(["submitted", "queued", "acknowledging", "accepted", "rejected", "cancelled",
  "http_start", "http_end", "sse_received", "rendered", "audio_queued", "audio_request",
  "audio_first_byte", "audio_downloaded", "audio_playing", "audio_completed", "audio_failed", "audio_stopped",
  "last_voice", "committed", "final_transcript"]);

/** Bounded, in-memory metadata only. All times are from the browser's monotonic clock. */
export class TurnTimings {
  constructor({ now = () => performance.now(), limit = 128, uuid = () => crypto.randomUUID() } = {}) {
    Object.assign(this, { now, limit, uuid });
    this.turns = new Map();
    this.events = new Map();
  }

  begin(agentId, times = {}) {
    const id = this.uuid();
    this.turns.set(id, { id, agentId, stages: {} });
    for (const stage of ["last_voice", "committed", "final_transcript"]) {
      if (Number.isFinite(times[stage])) this.mark(id, stage, times[stage]);
    }
    this.mark(id, "submitted");
    this.trim(this.turns);
    return id;
  }

  mark(id, stage, at = this.now()) {
    const record = this.turns.get(id);
    if (record && STAGES.has(stage) && Number.isFinite(at)) record.stages[stage] ??= at;
  }

  event(agentId, eventId, stage) {
    if (!agentId || !eventId || !STAGES.has(stage)) return;
    const key = `${agentId}:${eventId}`;
    if (!this.events.has(key)) this.events.set(key, { agentId, eventId, stages: {} });
    const record = this.events.get(key);
    record.stages[stage] ??= this.now();
    if (record.traceId) this.mark(record.traceId, stage, record.stages[stage]);
    this.trim(this.events);
  }

  bind(id, eventId) {
    const turn = this.turns.get(id);
    if (!turn || !eventId) return;
    const key = `${turn.agentId}:${eventId}`;
    const record = this.events.get(key) || { agentId: turn.agentId, eventId, stages: {} };
    record.traceId = id;
    turn.eventId = eventId;
    Object.entries(record.stages).forEach(([stage, at]) => this.mark(id, stage, at));
    this.events.set(key, record);
    this.trim(this.events);
  }

  traceFor(agentId, eventId) { return this.events.get(`${agentId}:${eventId}`)?.traceId; }

  async fetch(id, fetchImpl, url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (id) headers.set(TRACE_HEADER, id);
    this.mark(id, "http_start");
    try {
      const response = await fetchImpl(url, { ...options, headers });
      this.bind(id, response.headers.get(BEHAVIOUR_HEADER));
      if (!response.ok) this.mark(id, "rejected");
      return response;
    } catch (error) {
      this.mark(id, error?.name === "AbortError" ? "cancelled" : "rejected");
      throw error;
    } finally { this.mark(id, "http_end"); }
  }

  clear(agentId) {
    for (const entries of [this.turns, this.events]) {
      for (const [key, record] of entries) if (record.agentId === agentId) entries.delete(key);
    }
  }

  snapshot(agentId) {
    return [...this.turns.values()].filter((record) => record.agentId === agentId)
      .map((record) => structuredClone(record));
  }

  trim(entries) { while (entries.size > this.limit) entries.delete(entries.keys().next().value); }
}

export const turnTimings = new TurnTimings();
