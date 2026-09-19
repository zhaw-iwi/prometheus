import { turnTimings } from "./timings.js";
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
  const list = root.querySelector("[data-timing-turns]");
  const count = root.querySelector("[data-timing-count]");
  const buttons = [...root.querySelectorAll("[data-timing-export]")];
  let scheduled = false;
  const render = () => {
    scheduled = false;
    const turns = turnTimings.snapshot();
    count.textContent = turns.length ? `${turns.length} turn${turns.length === 1 ? "" : "s"} retained · latest 20 shown` : "No turns recorded yet. Start transcription and speak to collect timings.";
    buttons.forEach(button => { button.disabled = !turns.length; });
    const open = new Set([...list.querySelectorAll("details[open]")].map(node => node.dataset.traceId));
    list.replaceChildren();
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
  root.querySelector("[data-timing-clear]").addEventListener("click", () => turnTimings.clear());
  buttons.forEach(button => button.addEventListener("click", () => {
    const turns = turnTimings.snapshot();
    const csv = button.dataset.timingExport === "csv";
    const content = csv ? timingCsv(turns) : JSON.stringify(timingExport(turns, {
      userAgent: navigator.userAgent, timeOrigin: performance.timeOrigin,
    }), null, 2);
    const url = URL.createObjectURL(new Blob([content], { type: csv ? "text/csv;charset=utf-8" : "application/json" }));
    const link = element("a");
    link.href = url;
    link.download = `prometheus-interaction-timing-${new Date().toISOString().replaceAll(/[:.]/g, "-")}.${csv ? "csv" : "json"}`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }));
  render();
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount, { once: true });
else mount();
