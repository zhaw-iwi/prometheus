import { LiveClient } from "./client.js";
import { liveDiagnostics } from "./diagnostics.js";
import { sanitizeMediaPreferences, captureSummary } from "../transcription/settings.js";

const PREFERENCES = "prometheus.valerian.gptlive.preferences.v1";
const $ = id => document.getElementById(id);

export class LiveCockpit {
  constructor({ beforeStart, onHistory, onLifecycle = () => {} }) {
    this.beforeStart = beforeStart; this.onHistory = onHistory; this.onLifecycle = onLifecycle;
    this.scope = {}; this.capability = {}; this.history = []; this.ledger = []; this.version = 0;
    this.preferences = this.restore();
    this.client = new LiveClient({ audio: $("gptlive_audio"), onState: value => this.state(value),
      onCaptions: value => this.captions(value), onHistory: (history, agentId) => { if (agentId === this.scope.agentId) { this.setHistory(history); onHistory(history); } },
      onLedger: (ledger, agentId) => { if (agentId === this.scope.agentId) { this.ledger = ledger; this.renderHistory(); } }, onDiagnostic: value => this.diagnostic(value) });
    $("gptlive_start").addEventListener("click", () => void this.start());
    $("gptlive_stop").addEventListener("click", () => void this.client.stop());
    $("gptlive_mute").addEventListener("click", () => void this.client.mute());
    $("gptlive_refresh").addEventListener("click", () => void this.devices());
    for (const id of ["voice", "input", "output", "echo", "noise", "gain"]) $("gptlive_" + id).addEventListener("change", () => this.save());
    this.controls();
  }
  get active() { return this.client.active; }
  get busy() { return this.client.busy; }
  stop(reason) { return this.client.stop(reason); }
  invalidate(reason) { return this.client.invalidate(reason); }
  async configure(scope) {
    const version = ++this.version;
    void this.client.invalidate();
    if (this.scope.agentId !== scope.agentId || this.scope.accessCode !== scope.accessCode) {
      void this.stop("Agent connection changed."); this.history = []; this.ledger = []; this.captions({}); this.renderHistory();
    }
    this.scope = { ...scope }; this.capability = {}; this.controls();
    $("gptlive_language").textContent = scope.agentId ? `Conversation language: ${scope.language || "agent default"}.` : "Connect a supported agent to begin.";
    if (!scope.accessCode) { $("gptlive_tab_item").hidden = true; return; }
    const path = scope.agentId ? `/demo/agents/${encodeURIComponent(scope.agentId)}/live/capabilities` : "/demo/live/capabilities";
    try {
      const response = await fetch(path, { headers: { "X-Prometheus-Access-Code": scope.accessCode }, signal: AbortSignal.timeout(10000), cache: "no-store" });
      if (!response.ok) throw new Error(); const capability = await response.json(); if (version !== this.version) return;
      this.capability = capability; $("gptlive_tab_item").hidden = !capability.enabled;
      this.options($("gptlive_voice"), (capability.voices || []).map(voice => ({ deviceId: voice, label: voice })), this.preferences.voice || "marin", false);
      const defaults = sanitizeMediaPreferences(scope.mediaPreferences || {});
      this.preferences.media ||= defaults;
      this.preferences.outputDeviceId ??= scope.outputDeviceId || "";
      $("gptlive_echo").checked = this.preferences.media.echoCancellation;
      $("gptlive_noise").checked = this.preferences.media.noiseSuppression;
      $("gptlive_gain").checked = this.preferences.media.autoGainControl;
      if (scope.agentId && !capability.eligible) this.state({ state: "Idle", detail: "This agent is outside the GPT-Live pilot. Use Text or Continuous." });
      await this.devices(); this.controls();
    } catch (_) { if (version === this.version) { $("gptlive_tab_item").hidden = true; this.controls(); } }
  }
  async start() {
    if (this.busy || !this.scope.agentId || !this.capability.eligible) return;
    const version = this.version; const scope = { ...this.scope }; this.starting = true; this.controls();
    try {
      await this.beforeStart(); if (version !== this.version) return;
      this.save(); this.ledger = []; this.renderHistory();
      await this.client.start({ ...scope, voice: this.preferences.voice, mediaPreferences: this.preferences.media, outputDeviceId: this.preferences.outputDeviceId });
    } catch (error) { if (version === this.version) this.state({ state: "Error", detail: error.message }); }
    finally { this.starting = false; this.controls(); }
  }
  state(value) {
    if (value.state) {
      this.client.state = value.state;
      $("gptlive_status").textContent = value.state;
      $("gptlive_status").className = `status-pill is-${value.state === "Active" ? "live" : ["Error", "Disconnected"].includes(value.state) ? "error" : "idle"}`;
    }
    if (value.detail !== undefined) $("gptlive_detail").textContent = value.detail;
    this.muted = value.muted ?? this.muted;
    $("gptlive_mute").textContent = this.muted ? "Unmute microphone" : "Mute microphone";
    $("gptlive_mute").setAttribute("aria-pressed", String(!!this.muted));
    if (value.state !== "Active") { $("gptlive_input_activity").textContent = "Microphone idle"; $("gptlive_output_activity").textContent = "Speaker idle"; }
    else $("gptlive_input_activity").textContent = this.muted ? "Microphone muted" : "Microphone on";
    this.controls(); this.onLifecycle(value);
  }
  controls() {
    const busy = this.busy || this.starting;
    $("gptlive_start").disabled = busy || !this.scope.agentId || !this.capability.enabled || !this.capability.eligible;
    $("gptlive_start").textContent = ["Error", "Disconnected"].includes(this.client?.state) ? "Reconnect GPT-Live" : "Start GPT-Live";
    $("gptlive_stop").disabled = !this.active;
    $("gptlive_mute").disabled = this.client?.state !== "Active";
    for (const id of ["voice", "input", "output", "echo", "noise", "gain", "refresh"]) $("gptlive_" + id).disabled = busy;
    $("gptlive_output").disabled = busy || typeof $("gptlive_audio").setSinkId !== "function";
  }
  captions(values) {
    $("gptlive_user_caption").textContent = values.USER || "Your speech will appear here.";
    $("gptlive_assistant_caption").textContent = values.ASSISTANT || "The assistant’s speech will appear here.";
  }
  setHistory(history) { this.history = Array.isArray(history) ? history : []; this.renderHistory(); }
  event(event) {
    if (!event?.id) return;
    const index = this.history.findIndex(value => value.id === event.id);
    if (index >= 0) this.history[index] = event; else this.history.push(event);
    this.renderHistory();
  }
  renderHistory() {
    const root = $("gptlive_history"); root.replaceChildren();
    const entries = this.history.flatMap(event => {
      if (event.type === "obs.user_utterance") return [{ role: "user", text: event.payload }];
      if (event.type !== "resp.behaviour_plan" || event.provenance?.origin === "BACKEND_INTENT") return [];
      try { const plan = JSON.parse(event.payload); return plan.speech ? [{ role: "assistant", text: plan.speech,
        label: event.provenance?.complete === false ? "Incomplete capture" : "" }] : []; } catch (_) { return []; }
    }).slice(-20);
    for (const item of this.ledger.slice().reverse().filter(value => value.status !== "COMPLETE" && !value.eventId))
      entries.push({ role: item.speaker === "USER" ? "user" : "assistant", text: item.text,
        label: ({ CLARIFICATION: "Clarification needed · not applied", INCOMPLETE: "Incomplete · not applied", LATE: "Late fragment · not applied", FAILED: "Processing failed · not retried", PENDING: "Processing outcome pending" })[item.status] || "Not applied" });
    for (const entry of entries.slice(-20)) {
      const row = document.createElement("div"); row.className = `demo-message ${entry.role}`;
      const bubble = document.createElement("div"); bubble.className = "demo-bubble";
      if (entry.label) { const label = document.createElement("div"); label.className = "small fw-semibold mb-1"; label.textContent = entry.label; bubble.append(label); }
      const text = document.createElement("span"); text.textContent = entry.text; bubble.append(text); row.append(bubble); root.append(row);
    }
    if (!entries.length) { const empty = document.createElement("p"); empty.className = "text-muted small m-0"; empty.textContent = "Recorded conversation will appear here."; root.append(empty); }
    root.scrollTop = root.scrollHeight;
  }
  diagnostic(value) {
    liveDiagnostics.record(value);
    if (value.generation !== this.client.generation) return;
    this.lastDiagnostic = value;
    if (value.phase === "capture") $("gptlive_capture").textContent = JSON.stringify(value.capture, null, 2);
    if (value.phase === "status") {
      const before = this.activity || {}; const current = value.status;
      $("gptlive_input_activity").textContent = this.muted ? "Microphone muted" : current.voicedInputSamples > (before.voicedInputSamples || 0) ? "Input activity" : "Microphone on";
      $("gptlive_output_activity").textContent = current.outputSamples > (before.outputSamples || 0) ? "Output activity" : "Speaker ready";
      this.activity = current;
      $("gptlive_context").textContent = `Context: ${current.context?.state || "unknown"} · Capture: ${current.captureState || "unknown"}`;
    }
  }
  async devices() {
    const version = this.version;
    try {
      const devices = await navigator.mediaDevices?.enumerateDevices?.() || []; if (version !== this.version) return;
      this.options($("gptlive_input"), devices.filter(value => value.kind === "audioinput"), this.preferences.media?.inputDeviceId || "");
      this.options($("gptlive_output"), devices.filter(value => value.kind === "audiooutput"), this.preferences.outputDeviceId || "");
      $("gptlive_routing").textContent = typeof $("gptlive_audio").setSinkId === "function"
        ? "Device labels may appear after microphone permission is granted." : "This browser uses the system speaker. Speaker selection is unavailable.";
    } catch (_) { $("gptlive_routing").textContent = "Audio devices are unavailable. Check Chrome permissions."; }
    this.controls();
  }
  options(select, devices, selected, defaultOption = true) {
    select.replaceChildren();
    if (defaultOption) select.add(new Option("System / browser default", ""));
    for (const [index, device] of devices.entries()) select.add(new Option(device.label || `Audio device ${index + 1}`, device.deviceId));
    if (selected && ![...select.options].some(option => option.value === selected)) select.add(new Option("Previously selected device (unavailable)", selected));
    select.value = selected;
  }
  save() {
    this.preferences = { voice: $("gptlive_voice").value || "marin", outputDeviceId: $("gptlive_output").value,
      media: sanitizeMediaPreferences({ inputDeviceId: $("gptlive_input").value, echoCancellation: $("gptlive_echo").checked,
        noiseSuppression: $("gptlive_noise").checked, autoGainControl: $("gptlive_gain").checked }) };
    $("gptlive_capture").textContent = JSON.stringify(captureSummary(this.preferences.media), null, 2);
    try { localStorage.setItem(PREFERENCES, JSON.stringify(this.preferences)); } catch (_) { /* Preferences are optional. */ }
  }
  restore() {
    try { const saved = JSON.parse(localStorage.getItem(PREFERENCES)); return saved ? {
      voice: ["marin", "quartz", "willow", "meridian"].includes(saved.voice) ? saved.voice : "marin",
      media: sanitizeMediaPreferences(saved.media), outputDeviceId: typeof saved.outputDeviceId === "string" && saved.outputDeviceId.length <= 512 ? saved.outputDeviceId : "",
    } : {}; } catch (_) { return {}; }
  }
}
