const TRACE_HEADER = "X-Prometheus-Trace-Id";
const BEHAVIOUR_HEADER = "X-Prometheus-Behaviour-Id";
const TIMING_HEADER = "X-Prometheus-Timing";
const TRANSCRIPTION_STAGES = ["last_voice", "committed", "commit_sent", "commit_acknowledged",
  "transcript_first_delta", "transcript_last_delta", "final_transcript"];
const STAGES = new Set(["submitted", "queued", "acknowledging", "accepted", "rejected", "cancelled",
  "http_start", "http_end", "sse_received", "rendered", "audio_queued", "audio_request",
  "audio_first_byte", "audio_downloaded", "audio_playing", "audio_completed", "audio_failed", "audio_stopped",
  ...TRANSCRIPTION_STAGES, "acknowledged", "processing_complete",
  "ui_refresh_start", "ui_refresh_end"]);

/** Bounded, in-memory metadata only. All times are from the browser's monotonic clock. */
export class TurnTimings {
  constructor({ now = () => performance.now(), limit = 128, uuid = () => crypto.randomUUID() } = {}) {
    Object.assign(this, { now, limit, uuid });
    this.turns = new Map();
    this.events = new Map();
    this.listeners = new Set();
    this.configuration = () => ({});
  }

  begin(agentId, times = {}) {
    const id = this.uuid();
    this.turns.set(id, { id, agentId, startedAt: new Date().toISOString(), stages: {}, requests: [],
      configuration: safeConfiguration(this.configuration()) });
    for (const stage of TRANSCRIPTION_STAGES) {
      if (Number.isFinite(times[stage])) this.mark(id, stage, times[stage]);
    }
    this.mark(id, "submitted");
    this.trim(this.turns);
    return id;
  }

  mark(id, stage, at = this.now()) {
    const record = this.turns.get(id);
    if (record && STAGES.has(stage) && Number.isFinite(at)) {
      record.stages[stage] ??= at;
      this.changed();
    }
  }

  event(agentId, eventId, stage) {
    if (!agentId || !eventId || !STAGES.has(stage)) return;
    const record = this.eventRecord(agentId, eventId);
    record.stages[stage] ??= this.now();
    if (record.traceId) this.mark(record.traceId, stage, record.stages[stage]);
    this.trim(this.events);
  }

  eventRecord(agentId, eventId) {
    const key = `${agentId}:${eventId}`;
    if (!this.events.has(key)) this.events.set(key, { agentId, eventId, stages: {}, requests: [], speech: {} });
    this.trim(this.events);
    return this.events.get(key);
  }

  speech(agentId, eventId, values) {
    if (!agentId || !eventId) return;
    const record = this.eventRecord(agentId, eventId);
    Object.assign(record.speech, safeSpeech(values));
    const turn = this.turns.get(record.traceId);
    if (turn) turn.speech = { ...record.speech };
    this.changed();
  }

  bind(id, eventId) {
    const turn = this.turns.get(id);
    if (!turn || !eventId) return;
    const key = `${turn.agentId}:${eventId}`;
    const record = this.eventRecord(turn.agentId, eventId);
    record.traceId = id;
    turn.eventId = eventId;
    for (const request of record.requests) if (!turn.requests.includes(request) && turn.requests.length < 16) turn.requests.push(request);
    turn.speech = { ...record.speech };
    Object.entries(record.stages).forEach(([stage, at]) => this.mark(id, stage, at));
    this.events.set(key, record);
    this.trim(this.events);
    this.changed();
  }

  traceFor(agentId, eventId) { return this.events.get(`${agentId}:${eventId}`)?.traceId; }

  async fetch(id, fetchImpl, url, options = {}) {
    return this.request(this.turns.get(id), id, fetchImpl, url, options);
  }

  // Speech can start via SSE before acknowledgement headers bind the event to its turn.
  async fetchEvent(agentId, eventId, fetchImpl, url, options = {}) {
    const record = this.eventRecord(agentId, eventId);
    return this.request(record, record.traceId, fetchImpl, url, options);
  }

  async request(record, id, fetchImpl, url, options) {
    const headers = new Headers(options.headers || {});
    if (id) headers.set(TRACE_HEADER, id);
    const request = { kind: requestKind(url), start: this.now(), status: "pending" };
    if (record && record.requests.length < 16) record.requests.push(request);
    const turn = this.turns.get(record?.traceId);
    if (turn && !turn.requests.includes(request) && turn.requests.length < 16) turn.requests.push(request);
    this.mark(id, "http_start");
    try {
      const response = await fetchImpl(url, { ...options, headers });
      this.bind(id, response.headers.get(BEHAVIOUR_HEADER));
      request.status = response.ok ? "ok" : "error";
      request.httpStatus = response.status;
      request.traceId = response.headers.get(TRACE_HEADER) || id || null;
      request.server = decodeServerTiming(response.headers.get(TIMING_HEADER));
      if (!response.ok) this.mark(id, "rejected");
      return response;
    } catch (error) {
      request.status = error?.name === "AbortError" ? "cancelled" : "error";
      this.mark(id, error?.name === "AbortError" ? "cancelled" : "rejected");
      throw error;
    } finally {
      request.end = this.now();
      this.mark(id, "http_end");
      this.changed();
    }
  }

  clear(agentId) {
    for (const entries of [this.turns, this.events]) {
      for (const [key, record] of entries) if (!agentId || record.agentId === agentId) entries.delete(key);
    }
    this.changed();
  }

  snapshot(agentId) {
    return [...this.turns.values()].filter((record) => !agentId || record.agentId === agentId)
      .map((record) => structuredClone(record));
  }

  trim(entries) { while (entries.size > this.limit) entries.delete(entries.keys().next().value); }

  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
  changed() {
    if (this.notificationPending) return;
    this.notificationPending = true;
    queueMicrotask(() => {
      this.notificationPending = false;
      for (const listener of this.listeners) listener();
    });
  }
}

function requestKind(url) {
  const path = String(url).split("?")[0];
  if (path.endsWith("/acknowledge")) return "acknowledge";
  if (path.endsWith("/behaviour/generate")) return "generate";
  if (path.endsWith("/speech")) return "speech";
  return "other";
}

const identifier = value => typeof value === "string" && /^[A-Za-z0-9_.:/-]{1,96}$/.test(value) ? value : undefined;
const duration = value => Number.isFinite(value) && value >= 0 ? value : undefined;

export function decodeServerTiming(value) {
  if (!value || value.length > 6000) return null;
  try {
    const data = JSON.parse(atob(value));
    if (data.version !== 1 || !Array.isArray(data.spans) || data.spans.length > 64) return null;
    return { version: 1, durationMs: duration(data.durationMs), truncated: data.truncated === true,
      spans: data.spans.filter(span => span && identifier(span.stage) && duration(span.durationMs) !== undefined).map(span => ({
        stage: span.stage, durationMs: span.durationMs, offsetMs: duration(span.offsetMs),
        scope: span.scope === "speculative" ? "speculative" : undefined,
        originTrace: identifier(span.originTrace),
        status: ["ok", "error"].includes(span.status) ? span.status : "unknown",
        ...Object.fromEntries(["request", "purpose", "model", "effort"].map(key => [key, identifier(span[key])])),
        ...Object.fromEntries(["promptTokens", "completionTokens", "providerRequests"].map(key =>
          [key, Number.isInteger(span[key]) && span[key] >= 0 ? span[key] : undefined])),
      })) };
  } catch { return null; }
}

export function safeConfiguration(value = {}) {
  return {
    turnDetection: ["local_vad", "manual"].includes(value.turnDetection) ? value.turnDetection : undefined,
    silenceDurationSeconds: duration(value.silenceDurationSeconds),
    transcriptionDelay: ["minimal", "low", "medium", "high", "xhigh"].includes(value.transcriptionDelay) ? value.transcriptionDelay : undefined,
    transcriptionModel: identifier(value.transcriptionModel),
    ...safeSpeech(value),
    capture: Object.fromEntries(["echoCancellation", "noiseSuppression", "autoGainControl", "voiceIsolation"]
      .filter(key => typeof value.capture?.[key] === "boolean").map(key => [key, value.capture[key]])),
  };
}

function safeSpeech(value = {}) {
  return Object.fromEntries(Object.entries({ voice: identifier(value.voice), speed: duration(value.speed),
    playbackMode: ["progressive", "buffered"].includes(value.playbackMode) ? value.playbackMode : undefined,
    outputDevice: ["default", "selected"].includes(value.outputDevice) ? value.outputDevice : undefined,
  }).filter(([, entry]) => entry !== undefined));
}

export const turnTimings = new TurnTimings();
