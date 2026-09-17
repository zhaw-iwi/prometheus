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
  const base = turn.stages.last_voice ?? turn.stages.submitted;
  body.append(table(Object.entries(turn.stages).sort((a, b) => a[1] - b[1])
    .map(([stage, at]) => [STAGE_LABELS[stage] || stage, ms(difference(base, at))]),
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
          span.effort && `effort ${span.effort}`, span.status === "error" && "failed"].filter(Boolean).join(" · "),
        ms(span.durationMs),
      ]);
      section.append(table(rows, "Server spans (may overlap)"));
      if (request.server.truncated) section.append(element("p", "Server detail was truncated; totals are incomplete.", "small"));
    } else section.append(element("p", "Server detail unavailable.", "small text-body-secondary"));
    body.append(section);
  }
  const config = turn.configuration || {}, speech = turn.speech || {};
  body.append(table([
    ["Recorded at", turn.startedAt],
    ["Playback", speech.playbackMode || "Unknown"], ["Voice / speed", `${speech.voice ?? config.voice ?? "Default"} / ${speech.speed ?? config.speed ?? "Default"}`],
    ["Turn detection", config.turnDetection || "Unknown"],
    ["Silence duration", Number.isFinite(config.silenceDurationSeconds) ? `${config.silenceDurationSeconds} s` : "Unknown"],
    ["Transcription delay", config.transcriptionDelay || "Unknown"],
  ], "Settings for this turn"));
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
