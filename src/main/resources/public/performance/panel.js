import { interactionActivity, mountActivity } from "./activity.js";
import { turnTimings } from "./timings.js";
import { liveDiagnostics } from "../live/diagnostics.js";
import { METRICS, STAGE_LABELS, SERVER_LABELS, difference, measurements, outcome, timingExport, timingCsv } from "./report.js";

const ms = value => Number.isFinite(value) ? `${value.toFixed(1)} ms` : "Unknown";

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function table(rows, caption) {
  const node = element("table", undefined, "table table-sm timing-table mb-3");
  node.append(element("caption", caption, "caption-top"));
  const body = element("tbody");
  for (const [label, value] of rows) {
    const row = element("tr"), heading = element("th", label);
    heading.scope = "row";
    row.append(heading, element("td", value));
    body.append(row);
  }
  node.append(body);
  return node;
}

function details(turn) {
  const body = element("div", undefined, "mt-2");
  const metrics = measurements(turn);
  body.append(table(METRICS.map(([key, label]) => [label, ms(metrics[key])]), "Browser durations"));
  body.append(element("p", "Transcription times are observed in this browser and include network delay. Deltas may arrive before speech end; timeline offsets can be negative. Overlapping durations must not be added.", "small text-body-secondary"));
  const base = turn.stages.last_voice ?? turn.stages.submitted;
  body.append(table(Object.entries(turn.stages).sort((a, b) => a[1] - b[1])
    .map(([stage, at]) => [STAGE_LABELS[stage] || stage, ms(Number.isFinite(base) ? at - base : null)]),
  `Timeline from ${Number.isFinite(turn.stages.last_voice) ? "last detected voice" : "submission"}`));
  for (const request of turn.requests || []) {
    const section = element("div", undefined, "timing-request mb-3");
    section.append(element("h6", `${request.kind} · ${request.status}${request.httpStatus ? ` (${request.httpStatus})` : ""}`));
    section.append(table([
      ["Browser request → headers", ms(difference(request.start, request.end))],
      ["Server handling before body", ms(request.server?.durationMs)],
    ], "HTTP request"));
    if (request.server) {
      const rows = request.server.spans.map(span => [
        [SERVER_LABELS[span.stage] || span.stage, span.purpose, span.model,
          span.scope === "speculative" && "speculative work",
          span.effort && `effort ${span.effort}`, span.status === "error" && "failed"].filter(Boolean).join(" · "),
        ms(span.durationMs),
      ]);
      section.append(table(rows, "Server spans (may overlap)"));
      if (request.server.spans.some(span => span.scope === "speculative")) section.append(element("p",
        "Speculative work may span multiple HTTP requests. Its duration is not additional turn latency.", "small text-body-secondary"));
      if (request.server.truncated) section.append(element("p", "Server detail was truncated; totals are incomplete.", "small"));
    } else section.append(element("p", "Server detail unavailable.", "small text-body-secondary"));
    body.append(section);
  }
  const config = turn.configuration || {}, speech = turn.speech || {};
  if (turn.speechDelivery) {
    const delivery = turn.speechDelivery, server = delivery.server;
    const maximum = phase => {
      const values = server?.steps.filter(step => step.phase === phase).map(step => step.completedMs - step.startedMs) || [];
      return values.length ? Math.max(...values) : null;
    };
    body.append(table([
      ["Diagnostic retrieval", delivery.retrieval], ["Stream status", server?.status || "Unknown"],
      ["Longest recorded provider read", ms(maximum("read"))],
      ["Longest recorded output write", ms(maximum("write"))], ["Longest recorded output flush", ms(maximum("flush"))],
      ["Records omitted", server?.dropped ?? "Unknown"],
    ], "Server audio delivery"));
    body.append(element("p", "Server reads and writes use a separate clock. JSON contains cumulative byte positions for comparison with browser reads; flush completion does not prove browser receipt.", "small text-body-secondary"));
  }
  body.append(table([
    ["Recorded at", turn.startedAt],
    ["Playback", speech.playbackMode || "Unknown"], ["Voice / speed", `${speech.voice ?? config.voice ?? "Default"} / ${speech.speed ?? config.speed ?? "Default"}`],
    ["Audio format", speech.format || "Unknown"],
    ["Format preference", speech.formatPreference ?? config.formatPreference ?? "Unknown"],
    ["PCM fallback", { pcm_unsupported: "Browser or speaker unsupported", pcm_setup_failed: "Audio preparation failed" }[speech.fallbackReason] || "None recorded"],
    ["Playback start observed by", { pcm_renderer: "PCM audio renderer", media_element: "Media element" }[speech.playbackStartSource] || "Unknown"],
    ["PCM buffer target / initial", `${ms(speech.pcmPrefillMs)} / ${ms(speech.pcmInitialBufferedMs)}`],
    ["PCM interruptions / silence inserted", `${speech.pcmUnderruns ?? "Unknown"} / ${ms(speech.pcmGapMs)}`],
    ["Turn detection", config.turnDetection || "Unknown"],
    ["Silence duration", Number.isFinite(config.silenceDurationSeconds) ? `${config.silenceDurationSeconds} s` : "Unknown"],
    ["Transcription delay", config.transcriptionDelay || "Unknown"],
  ], "Settings for this turn"));
  if (turn.pcm) {
    const pcm = turn.pcm, rate = pcm.renderer.find(event => event.type === "prepared")?.sampleRate;
    const audioMs = frames => Number.isFinite(rate) && rate > 0 && Number.isFinite(frames) ? frames / rate * 1000 : null;
    const section = element("div");
    section.dataset.pcmDetail = "";
    section.append(element("p", `PCM detail: ${pcm.delivery.length} delivery and ${pcm.renderer.length} renderer records. ` +
      `${pcm.deliveryDropped + pcm.rendererDropped} records omitted by retention limits. Full detail is included in JSON export.`, "small"));
    const labels = { render_start: "Playback started", buffer_empty: "Buffer empty (provisional)", render_resume: "Playback resumed", render_end: "Renderer finished" };
    section.append(table(pcm.renderer.filter(event => labels[event.type]).map(event => [labels[event.type],
      `Audio position ${ms(audioMs(event.playedFrames))}` +
      (event.type === "render_resume" ? `; inserted silence ${ms(audioMs(event.gapFrames))}` : "")]), "PCM interruptions"));
    section.append(element("p", "An empty buffer is an interruption only if more audio resumes. Positions count audio samples, not browser time or physical audibility.", "small text-body-secondary"));
    body.append(section);
  }
  body.append(element("div", `Agent ${turn.agentId}\nTrace ${turn.id}`, "small font-monospace text-break"));
  return body;
}

function mount() {
  const root = document.getElementById("interaction_timing_panel");
  if (!root) return;
  mountActivity();
  const activityRoot = root.querySelector("[data-activity-telemetry]");
  const list = root.querySelector("[data-timing-turns]");
  const count = root.querySelector("[data-timing-count]");
  const buttons = [...root.querySelectorAll("[data-timing-export]")];
  let scheduled = false;
  const render = () => {
    scheduled = false;
    const turns = turnTimings.snapshot();
    const live = liveDiagnostics.snapshot();
    const activity = interactionActivity.snapshot();
    const activityOpen = activityRoot.querySelector("details")?.open ?? true;
    activityRoot.replaceChildren();
    if (activity.journals.length || activity.markers.length) {
      const section = element("details", undefined, "surface-panel timing-turn mb-2");
      section.open = activityOpen;
      section.append(element("summary", `Backend activity / ${activity.journals.length} recordings / ${activity.markers.length} issue markers`));
      section.append(table([["Progress stream", activity.coverage.backend], ["Recordings / markers omitted", `${activity.droppedJournals} / ${activity.droppedMarkers}`]], "Collection coverage"));
      for (const journal of activity.journals.slice(-3).reverse()) {
        section.append(table([["Agent / epoch", `${journal.agentId} / ${journal.epoch || "Unknown"}`],
          ["In progress", journal.active.map(value => value.stage).join(", ") || "None at last update"],
          ["Cue waiting reason / evaluations", journal.cue ? `${journal.cue.reason} / ${journal.cue.occurrences}` : "None recorded"],
          ["Server ring evictions / export omissions", `${journal.dropped ?? "Unknown"} / ${journal.retainedDropped}`],
          ["Operations omitted", journal.omittedOperations ?? "Unknown"]], "Latest backend state"));
        const outcomes = journal.recent.filter(value => value.stage === "operation" || value.stage === "inference").slice(-8);
        section.append(table(outcomes.map(value => [value.stage === "inference" ? `Model / ${value.details.purpose || "unknown"}` : `${value.details.kind || "operation"} / ${value.outcome}`,
          value.stage === "inference" ? `${value.details.model || "Unknown"} / ${value.details.inputTokens ?? "?"} input / ${value.details.outputTokens ?? "?"} output tokens` : ms(value.durationMs)]), "Recent operations"));
      }
      if (activity.markers.length) section.append(table(activity.markers.slice(-10).map(value => [value.category.replaceAll("_", " "), new Date(value.wallMs).toLocaleTimeString()]), "Operator issue markers (approximate association)"));
      section.append(element("p", "Progress stages describe observed work, not private model reasoning. Server and browser clocks are separate. Full bounded correlations are included in JSON.", "small text-body-secondary"));
      activityRoot.append(section);
    }
    count.textContent = turns.length ? `${turns.length} turn${turns.length === 1 ? "" : "s"} retained · latest 20 shown` : "No ordinary turns recorded yet. Backend and Live records appear below when available.";
    buttons.forEach(button => { button.disabled = !turns.length && (button.dataset.timingExport === "csv" || !live.sessions.length && !activity.journals.length && !activity.markers.length); });
    const open = new Set([...list.querySelectorAll("details[open]")].map(node => node.dataset.traceId));
    list.replaceChildren();
    if (live.sessions.length) {
      count.textContent += ` GPT-Live: ${live.sessions.length} sessions, ${live.droppedSessions} sessions omitted.`;
      for (const session of live.sessions.slice(-5).reverse()) {
        const section = element("details", undefined, "surface-panel timing-turn mb-2");
        section.dataset.traceId = session.handle; section.open = open.has(session.handle);
        section.append(element("summary", `GPT-Live · ${session.server?.state || "connecting"} · finalization ${session.finalization}`));
        section.append(table([["Session", session.handle], ["Epoch", session.server?.epoch || "Unknown"],
          ["Context", session.server?.context?.state || "Unknown"], ["Revision", session.server?.context?.revision || "Unknown"],
          ["Capture", session.server?.captureState || "Unknown"],
          ["Provider usage (cumulative)", Number.isFinite(session.provider?.usageSeconds) ? `${session.provider.usageSeconds} seconds` : "Not reported"],
          ["Context utilisation", Number.isFinite(session.provider?.contextUsageRatio) ? `${(session.provider.contextUsageRatio * 100).toFixed(1)}%` : "Not reported"],
          ["Provider close / latest error", `${session.provider?.closeReason || "None"} / ${session.provider?.problems.at(-1)?.code || "None"}`], ["Queue peak", session.server?.capture?.queueHighWater ?? "Unknown"],
          ["Export records omitted", session.browserDropped + (session.mediaDropped || 0) + (session.server?.retainedDropped || 0)
            + (session.server?.capture?.retainedDropped || 0) + (session.server?.context?.retainedDropped || 0) + (session.server?.audio?.retainedDropped || 0)],
          ["Server ring evictions", (session.server?.dropped || 0) + (session.server?.capture?.dropped || 0)
            + (session.server?.context?.dropped || 0) + (session.server?.audio?.dropped || 0)]], "Live session diagnostics"));
        section.append(element("p", "Browser, server, provider audio, media playback and RTC statistics use separate clocks. Server ring evictions may already be retained in the export. Output activity does not prove audibility. JSON includes metadata only; CSV contains ordinary turns.", "small text-body-secondary"));
        list.append(section);
      }
    }
    turns.slice(-20).reverse().forEach((turn, index) => {
      const node = element("details", undefined, "surface-panel timing-turn mb-2");
      node.dataset.traceId = turn.id;
      const label = `Turn ${turns.length - index} · ${outcome(turn)} · ${ms(measurements(turn).voiceResponse)}`;
      node.append(element("summary", label));
      let populated = false;
      const populate = () => { if (!populated && node.open) { node.append(details(turn)); populated = true; } };
      node.addEventListener("toggle", populate);
      node.open = open.has(turn.id);
      populate();
      list.append(node);
    });
  };
  const schedule = () => {
    if (!scheduled && root.classList.contains("active")) { scheduled = true; requestAnimationFrame(render); }
  };
  document.getElementById("interaction_timing_tab").addEventListener("shown.bs.tab", schedule);
  turnTimings.subscribe(schedule);
  liveDiagnostics.subscribe(schedule);
  interactionActivity.subscribe(schedule);
  root.querySelector("[data-timing-clear]").addEventListener("click", () => { turnTimings.clear(); liveDiagnostics.clear(); interactionActivity.clear(); });
  buttons.forEach(button => button.addEventListener("click", () => {
    const turns = turnTimings.snapshot();
    const csv = button.dataset.timingExport === "csv";
    const content = csv ? timingCsv(turns) : JSON.stringify({ ...timingExport(turns, {
      userAgent: navigator.userAgent, timeOrigin: performance.timeOrigin,
    }), live: liveDiagnostics.snapshot(), activity: interactionActivity.snapshot() }, null, 2);
    const url = URL.createObjectURL(new Blob([content], { type: csv ? "text/csv;charset=utf-8" : "application/json" }));
    const link = element("a");
    link.href = url;
    link.download = `prometheus-telemetry-${new Date().toISOString().replaceAll(/[:.]/g, "-")}.${csv ? "csv" : "json"}`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }));
  render();
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount, { once: true });
else mount();
