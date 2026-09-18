export const METRICS = [
  ["voiceResponse", "Speech end → playback", "last_voice", "audio_playing"],
  ["localSilence", "Silence detection", "last_voice", "committed"],
  ["transcriptionFinal", "Commit → final transcript", "committed", "final_transcript"],
  ["commitAcknowledgement", "Commit sent → acknowledgement", "commit_sent", "commit_acknowledged"],
  ["acknowledgedFinal", "Commit acknowledgement → final transcript", "commit_acknowledged", "final_transcript"],
  ["transcriptDeltas", "First → last transcript delta", "transcript_first_delta", "transcript_last_delta"],
  ["transcriptDeltaTail", "Last transcript delta → final transcript", "transcript_last_delta", "final_transcript"],
  ["transcriptDispatch", "Final transcript → submission", "final_transcript", "submitted"],
  ["clientQueue", "Browser turn queue", "submitted", "acknowledging"],
  ["processing", "Process turn (including fallback)", "acknowledging", "processing_complete"],
  ["uiRefresh", "Refresh cockpit", "ui_refresh_start", "ui_refresh_end"],
  ["presentation", "Submission → rendered response", "submitted", "rendered"],
  ["speechFirstByte", "Speech request → first audio byte", "audio_request", "audio_first_byte"],
  ["speechPreparation", "Prepare audio output", "audio_prepare_start", "audio_prepare_end"],
  ["speechPlayback", "First audio byte → playback", "audio_first_byte", "audio_playing"],
  ["speechDownload", "Speech request → download complete", "audio_request", "audio_downloaded"],
];

export const STAGE_LABELS = {
  last_voice: "Last detected voice", committed: "Local VAD turn boundary",
  commit_sent: "Local commit sent", commit_acknowledged: "Provider commit acknowledgement received",
  transcript_first_delta: "First transcript delta received", transcript_last_delta: "Last transcript delta received",
  final_transcript: "Final transcript received",
  submitted: "Transcript submitted", queued: "Turn queued", acknowledging: "Processing turn",
  http_start: "First HTTP request", http_end: "First HTTP response headers",
  acknowledged: "Acknowledgement read", processing_complete: "Turn processing complete",
  ui_refresh_start: "Cockpit refresh started", ui_refresh_end: "Cockpit refresh finished", accepted: "Transcript accepted",
  sse_received: "Behaviour received (SSE)", rendered: "Response rendered", audio_queued: "Audio queued",
  audio_request: "Speech requested", audio_first_byte: "First audio byte", audio_downloaded: "Audio download complete",
  audio_prepare_start: "Audio output preparation started", audio_prepare_end: "Audio output prepared",
  audio_playing: "Audio playing", audio_completed: "Audio completed", audio_failed: "Audio failed",
  audio_stopped: "Audio stopped", rejected: "Request rejected", cancelled: "Request cancelled",
};

export const SERVER_LABELS = {
  agent_queue: "Agent queue", acknowledge: "Acknowledge", generate: "Generate behaviour",
  persist: "Persist agent", monitor_publish: "Publish monitor", behaviour_publish: "Publish behaviour",
  behaviour_plan: "Behaviour plan", speech_text: "Speech text", inference: "Model request",
  inference_queue: "Model queue", speech_headers: "Speech provider headers",
  action_queued: "Background action queued", speculation_started: "Speculative request started",
  speculation_reused: "Speculative response reused", speculation_discarded: "Speculative response discarded",
  speculation_skipped: "Speculation skipped", speculation_wait: "Wait for speculative response",
};

export function difference(from, to) {
  return Number.isFinite(from) && Number.isFinite(to) && to >= from ? to - from : null;
}

export function measurements(turn) {
  return Object.fromEntries(METRICS.map(([key, , from, to]) => [key, difference(turn.stages[from], turn.stages[to])]));
}

export function outcome(turn) {
  const stages = turn.stages;
  for (const [stage, label] of [["audio_failed", "Audio failed"], ["audio_stopped", "Audio stopped"],
    ["cancelled", "Cancelled"], ["rejected", "Request failed"], ["audio_completed", "Complete"],
    ["audio_playing", "Playing"], ["accepted", "Accepted; playback unobserved"]]) {
    if (Number.isFinite(stages[stage])) return label;
  }
  return "Processing turn";
}

export function timingExport(turns, browser = {}) {
  return { schemaVersion: 1, metadata: { source: "browser", exportedAt: new Date().toISOString(),
    browser: { userAgent: browser.userAgent, timeOrigin: browser.timeOrigin },
    clock: "Browser stages and HTTP boundaries are performance.now() milliseconds. Server spans use a separate request-relative clock.",
    pcmTiming: "PCM detail version 1: at is browser observation/receipt time (performance.now); contextTimeMs samples AudioContext.currentTime there. renderFrame is the renderer's AudioContext frame, divided by prepared.sampleRate for seconds; playedFrames is consumed source audio, excluding inserted silence. Do not subtract audio-clock times from browser times. posted/render_receive share block and chunk IDs; their receipt interval includes both message directions. outstandingFrames includes frames not yet credited by the renderer, not exact occupancy; bufferedFrames is actual renderer occupancy. readWaitMs measures an awaited response-body read; previousReadGapMs also includes consumer backpressure, with separate wait events. Browser reads are not provider/network packets. buffer_empty is provisional until render_resume confirms gapFrames; pendingGapFrames at render_end can be tail waiting, not an interruption. Delivery (256) and renderer (64) lists retain their first half and latest half; dropped counts signal incomplete evidence. No audio samples or text are recorded.",
    interpretation: "Speech end is estimated by local VAD; audio_playing is a browser event, not physical audibility. PCM marks first samples consumed by the audio renderer; MP3 uses the media element playing event. Transcription acknowledgements, deltas and finals are browser receipt times, not provider timestamps; intervals include network delay. Deltas can arrive before speech end or commit. Nested/parallel durations must not be added. Spans with scope=speculative describe work that may start in an earlier HTTP request; their offsets are not relative to the enclosing request. Missing measurements are unknown.",
  }, turns: turns.map(turn => ({ ...structuredClone(turn), outcome: outcome(turn), durationsMs: measurements(turn) })) };
}

export function timingCsv(turns) {
  const fields = ["traceId", "agentId", "startedAt", "outcome", "playbackMode", "voice", "speed", "turnDetection",
    "silenceDurationSeconds", "transcriptionDelay", "formatPreference", "audioFormat", "audioFallbackReason", "playbackStartSource",
    "pcmPrefillMs", "pcmInitialBufferedMs", "pcmUnderruns", "pcmGapMs",
    "pcmDeliveryRecords", "pcmDeliveryDropped", "pcmRendererRecords", "pcmRendererDropped",
    ...METRICS.map(([key]) => `${key}Ms`), "observedModelRequests", "modelRoutes", "missingServerRequests", "serverTimingTruncated"];
  const rows = turns.map(turn => {
    const metrics = measurements(turn), config = turn.configuration || {}, speech = turn.speech || {};
    const servers = (turn.requests || []).map(request => request.server).filter(Boolean);
    return [turn.id, turn.agentId, turn.startedAt, outcome(turn), speech.playbackMode,
      speech.voice ?? config.voice, speech.speed ?? config.speed, config.turnDetection, config.silenceDurationSeconds,
      config.transcriptionDelay, speech.formatPreference ?? config.formatPreference, speech.format, speech.fallbackReason, speech.playbackStartSource,
      speech.pcmPrefillMs, speech.pcmInitialBufferedMs, speech.pcmUnderruns, speech.pcmGapMs,
      turn.pcm?.delivery.length, turn.pcm?.deliveryDropped, turn.pcm?.renderer.length, turn.pcm?.rendererDropped,
      ...METRICS.map(([key]) => metrics[key]),
      servers.length ? observedInferences(servers).length : null,
      [...new Set(servers.flatMap(server => server.spans).filter(span => span.stage === "inference")
        .map(span => [span.purpose, span.model, span.effort].filter(Boolean).join("/")))].join("; "),
      (turn.requests || []).filter(request => !request.server).length,
      servers.length ? servers.some(server => server.truncated) : null];
  });
  const cell = value => {
    if (value == null) return "";
    const text = String(value);
    return `"${(/^[=+\-@\t\r]/.test(text) ? "'" : "") + text.replaceAll('"', '""')}"`;
  };
  return [fields, ...rows].map(row => row.map(cell).join(",")).join("\r\n") + "\r\n";
}

// Started work and its eventual completion may appear in different HTTP responses.
export function observedInferences(servers) {
  const observed = new Map();
  servers.forEach((server, serverIndex) => server.spans.forEach((span, spanIndex) => {
    if (!["inference", "speculation_started"].includes(span.stage)) return;
    const key = span.request || `${serverIndex}:${spanIndex}`;
    if (!observed.has(key) || span.stage === "inference") observed.set(key, span);
  }));
  return [...observed.values()];
}
