import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const stages = {
  voiceResponse: ["last_voice", "audio_playing"],
  presentation: ["submitted", "rendered"],
  speechDelivery: ["sse_received", "audio_playing"],
  turnCompletion: ["last_voice", "final_transcript"],
  localSilence: ["last_voice", "committed"],
  transcriptionFinal: ["committed", "final_transcript"],
  commitAcknowledgement: ["commit_sent", "commit_acknowledged"],
  acknowledgedFinal: ["commit_acknowledged", "final_transcript"],
  transcriptDeltas: ["transcript_first_delta", "transcript_last_delta"],
  transcriptDeltaTail: ["transcript_last_delta", "final_transcript"],
  speechFirstByte: ["audio_request", "audio_first_byte"],
  speechDownload: ["audio_request", "audio_downloaded"],
};

export function summarize({ metadata, turns }, serverLog = "") {
  const browserExport = metadata?.source === "browser" && metadata?.clock;
  if ((!browserExport && (!["fixture", "live"].includes(metadata?.source) || !metadata?.configuration || !metadata?.workload)) || !Array.isArray(turns)) {
    throw new Error("Supply a cockpit export or metadata source (fixture/live), configuration, workload and exported turns.");
  }
  const ids = new Set(turns.map(turn => turn.id));
  const measurements = Object.fromEntries(Object.entries(stages).map(([name, [start, end]]) => {
    const values = []; let missing = 0, invalid = 0;
    for (const turn of turns) {
      const from = turn.stages?.[start], to = turn.stages?.[end];
      if (!Number.isFinite(from) || !Number.isFinite(to)) missing++;
      else if (to < from) invalid++;
      else values.push(to - from);
    }
    values.sort((a, b) => a - b);
    return [name, { samples: values.length, missing, invalid, p50Ms: quantile(values, 0.5), p95Ms: quantile(values, 0.95),
      fewerThan50Samples: values.length < 50 }];
  }));
  const requests = new Map();
  const coverage = { httpRequests: 0, missingServerTiming: 0, truncatedServerTiming: 0 };
  for (const turn of turns) {
    for (const http of turn.requests || []) {
      coverage.httpRequests++;
      if (!http.server) { coverage.missingServerTiming++; continue; }
      if (http.server.truncated) coverage.truncatedServerTiming++;
      for (const span of http.server.spans || []) {
        if (span.stage === "inference" && span.request) requests.set(span.request, {
          ...span, dispatched: span.providerRequests === 1,
        });
      }
    }
  }
  for (const line of serverLog.split(/\r?\n/)) {
    const fields = Object.fromEntries([...line.matchAll(/\b(\w+)=([^\s]+)/g)].map(match => [match[1], match[2]]));
    if (!ids.has(fields.trace) || !fields.request || !["inference", "inference_usage"].includes(fields.stage)) continue;
    const request = requests.get(fields.request) || {};
    Object.assign(request, fields);
    if (fields.stage === "inference") request.dispatched = Number(fields.requests) === 1;
    requests.set(fields.request, request);
  }
  const groups = new Map();
  for (const request of requests.values()) {
    if (!request.dispatched) continue;
    const key = `${request.model}/${request.effort}/${request.purpose}`;
    const group = groups.get(key) || { model: request.model, effort: request.effort, purpose: request.purpose,
      requests: 0, errors: 0, knownPromptTokens: 0, knownCompletionTokens: 0, missingUsage: 0 };
    group.requests++;
    if (request.status !== "ok") group.errors++;
    if (/^\d+$/.test(request.promptTokens ?? "") && /^\d+$/.test(request.completionTokens ?? "")) {
      group.knownPromptTokens += Number(request.promptTokens); group.knownCompletionTokens += Number(request.completionTokens);
    } else group.missingUsage++;
    groups.set(key, group);
  }
  return {
    metadata, turns: turns.length, measurements, coverage,
    statuses: Object.fromEntries(["rejected", "cancelled", "audio_failed", "audio_stopped"].map(stage =>
      [stage, turns.filter(turn => Number.isFinite(turn.stages?.[stage])).length])),
    textInference: groups.size ? [...groups.values()] : null,
    cost: "NOT MEASURED: absent usage/cancelled work and Speech/transcription billing require provider evidence",
    notes: ["Browser clocks only; do not subtract server timestamps. Quantiles use linear interpolation.",
      "Only observed dispatched model requests in timing headers/logs are counted; missing, truncated or still-running work is unknown.",
      "Cockpit exports may mix settings/workloads. Filter turns before comparing configuration-specific quantiles.",
      "Fixture timing is not production latency. Separate cold/warm and ordinary/closing workloads.",
      "These statistics do not certify response quality, physical audibility or the two-second target."],
  };
}

function quantile(values, fraction) {
  if (!values.length) return null;
  const position = (values.length - 1) * fraction, lower = Math.floor(position), upper = Math.ceil(position);
  return Math.round((values[lower] + (values[upper] - values[lower]) * (position - lower)) * 1000) / 1000;
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const input = JSON.parse(await readFile(process.argv[2], "utf8"));
  const log = process.argv[3] ? await readFile(process.argv[3], "utf8") : "";
  process.stdout.write(JSON.stringify(summarize(input, log), null, 2) + "\n");
}
