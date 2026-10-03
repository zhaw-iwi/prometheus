// Shared footer and export model. All values are allowlisted; clocks stay separate.
const token = value => typeof value === "string" && /^[\w.:/-]{1,128}$/.test(value) ? value : null;
const number = value => Number.isFinite(value) && value >= 0 ? value : null;
const fields = (value = {}, names = [], numbers = []) => Object.fromEntries([
  ...names.map(key => [key, token(value[key])]), ...numbers.map(key => [key, number(value[key])]),
]);
const labels = { thinking: "Thinking", preparing_speech: "Preparing response", updating_task: "Updating task",
  agent_queue: "Waiting to process", queued: "Waiting to process", persist: "Saving result", processing: "Processing",
  speaking: "Speaking", loading: "Preparing speech", output: "Live output activity", connecting: "Connecting",
  stopping: "Stopping", failed: "Processing failed", rejected: "Input could not be processed", "provider-error": "Speech service error" };
const cues = { waiting_for_cue: "Waiting for a cue", cooldown: "Waiting for the next cue",
  awaiting_response_capture: "Waiting for response capture", stale_or_missing_observation: "Waiting for a fresh cue",
  insufficient_samples: "Waiting for a stable cue", low_confidence: "Waiting for a clearer cue", face_missing: "Waiting for a face cue",
  missing_field: "Waiting for a usable cue", invalid_observation: "Waiting for a usable cue", condition_not_met: "Waiting for a matching cue",
  task_waiting: "Waiting for the condition to change", task_paused: "Task paused", task_completed: "Task completed" };
export const ISSUE_CATEGORIES = ["speech_cut_off", "long_pause", "unexpected_response", "missed_cue"];
export class InteractionActivity {
  constructor({ now = () => performance.now(), wall = () => Date.now() } = {}) {
    this.now = now; this.wall = wall; this.listeners = new Set(); this.journals = new Map(); this.markers = [];
    this.droppedJournals = 0; this.droppedMarkers = 0; this.taskStates = []; this.droppedTaskStates = 0; this.scope(null);
  }
  scope(agentId) {
    this.agentId = token(agentId); this.epoch = null; this.backend = null; this.received = null;
    this.connected = false; this.local = new Map(); this.listening = false; this.generation = (this.generation || 0) + 1;
    this.task = null; this.acceptedUntil = 0; this.acceptedKind = null;
    this.notify(); return this.generation;
  }
  transport(connected) { this.connected = connected; this.notify(); }
  taskStatus(value) {
    const previous = this.task;
    this.task = value ? { ...fields(value, ["epoch", "phase", "window"], ["revision", "nextCueInMs"]),
      draft: value.draft === true, responseCaptured: value.responseCaptured === true } : null;
    if (this.agentId && this.task && ["epoch", "phase", "window", "revision", "draft", "responseCaptured"].some(key => previous?.[key] !== this.task[key])) {
      this.taskStates.push({ agentId: this.agentId, ...this.task, browserMs: this.now(), wallMs: this.wall() });
      if (this.taskStates.length > 256) { this.taskStates.shift(); this.droppedTaskStates++; }
    }
    this.notify();
  }
  accept(value) {
    if (!this.agentId || value?.version !== 1 || value.agentId !== this.agentId) return;
    if (this.backend && number(value.revision) < this.backend.revision) return;
    const epoch = token(value.epoch);
    // Epoch changes arrive on the already fenced monitor connection in server order.
    if (this.epoch && epoch !== this.epoch) { this.local.clear(); this.acceptedUntil = 0; }
    this.epoch = epoch;
    const key = `${this.agentId}/${epoch || "unknown"}`;
    let journal = this.journals.get(key);
    if (journal && number(value.revision) < journal.revision) return;
    if (!journal) { journal = { agentId: this.agentId, epoch, recent: [], retainedDropped: 0, revision: 0 }; this.journals.set(key, journal); }
    const previousRevision = journal.revision;
    const entries = new Map(journal.recent.map(entry => [entry.sequence, entry]));
    for (const entry of (value.recent || []).slice(-128)) {
      if (number(entry.sequence) === null) continue;
      entries.set(entry.sequence, { ...fields(entry, ["operationId", "traceId", "sessionId", "sourceId", "eventId", "spanId", "stage", "outcome"],
        ["sequence", "serverMs", "durationMs"]), details: fields(entry.details || {}, ["requestId", "purpose", "model", "effort", "kind", "reason", "phase"], ["inputTokens", "outputTokens", "providerRequests", "taskRevision"]) });
    }
    journal.retainedDropped += Math.max(0, entries.size - 1024); journal.recent = [...entries.values()].slice(-1024);
    Object.assign(journal, fields(value, [], ["revision", "serverMs", "dropped", "omittedOperations"]));
    journal.active = (value.active || []).slice(0, 32).map(entry => fields(entry, ["operationId", "stage"], ["startedAt", "elapsedMs"]));
    journal.cue = value.cue ? fields(value.cue, ["reason", "sourceId"], ["occurrences", "serverMs"]) : null;
    const acceptedCue = journal.recent.findLast(entry => entry.sequence > previousRevision && entry.stage === "cue" &&
      entry.outcome === "cue_matched" && value.serverMs - entry.serverMs < 5000);
    if (acceptedCue) { this.acceptedUntil = this.now() + 4000; this.acceptedKind = acceptedCue.details.kind; }
    const terminal = journal.recent.filter(entry => entry.stage === "operation").at(-1);
    if (terminal?.outcome === "failed" && terminal.sequence > previousRevision && value.serverMs - terminal.serverMs < 10000)
      this.set("backend_error", "failed", { ttl: 10000 });
    else if (terminal?.outcome === "complete" && terminal.sequence > previousRevision) this.local.delete("backend_error");
    this.backend = journal; this.received = this.now(); this.connected = true;
    while (this.journals.size > 16) { this.journals.delete(this.journals.keys().next().value); this.droppedJournals++; }
    this.notify();
  }
  set(key, stage, { generation = this.generation, ttl = null } = {}) {
    if (!this.agentId || generation !== this.generation || !token(key)) return;
    if (!stage) this.local.delete(key);
    else if (labels[stage]) {
      const previous = this.local.get(key);
      this.local.set(key, { stage, since: previous?.stage === stage ? previous.since : this.now(), until: ttl ? this.now() + ttl : null });
      while (this.local.size > 64) this.local.delete(this.local.keys().next().value);
    }
    this.notify();
  }
  view() {
    if (!this.agentId) return { label: "Connect an agent to begin", busy: false, elapsed: null, tone: "idle" };
    const now = this.now();
    const accepted = now < this.acceptedUntil ? (this.acceptedKind === "obs.emotion.face"
      ? "Cue accepted. You can relax your expression." : "Your cue was received.") : "";
    const local = [...this.local.values()].filter(entry => entry.until === null || entry.until > now);
    const failed = local.find(entry => ["failed", "rejected", "provider-error"].includes(entry.stage));
    const active = this.backend?.active || [];
    const stale = active.length && (!this.connected || now - this.received > 6000);
    if (failed) return { label: labels[failed.stage], busy: false, tone: "error", elapsed: null };
    const speaking = local.find(entry => ["speaking", "output"].includes(entry.stage));
    if (speaking) return { label: labels[speaking.stage], detail: accepted, busy: false, tone: "live", elapsed: null };
    if (stale) return { label: "Progress unavailable", detail: "The last update is out of date. The outcome is unknown.", busy: false, tone: "warning", elapsed: null };
    if (active.length) {
      const entry = active.find(entry => entry.stage === "thinking") || active[0];
      return { label: labels[entry.stage] || "Processing", detail: accepted || (active.length > 1 ? `${active.length} operations in progress` : ""),
        busy: true, tone: "active", elapsed: (entry.elapsedMs || 0) + now - this.received };
    }
    if (local.length) {
      const entry = local[0], elapsed = now - entry.since;
      return elapsed > 30000 ? { label: "Waiting for confirmation", busy: false, tone: "warning", elapsed }
        : { label: labels[entry.stage], busy: true, tone: "active", elapsed };
    }
    if (accepted) return { label: "Cue accepted", detail: accepted, busy: false, tone: "live", elapsed: null };
    const taskLabel = this.task?.phase === "CONFIGURATION" ? (this.task.draft ? "Awaiting your acceptance" : "Ready to configure")
      : this.task?.phase === "PAUSED" ? "Task paused" : this.task?.phase === "COMPLETED" ? "Task completed"
      : this.task?.phase === "WAITING" ? "Waiting for the condition to change" : null;
    if (taskLabel) return { label: taskLabel, busy: false, tone: "idle", elapsed: null };
    if (this.task?.phase === "RUNNING" && !this.task.responseCaptured)
      return { label: "Waiting for response capture", detail: "Captions have not yet confirmed the response content.", busy: false, tone: "idle", elapsed: null };
    const cue = cues[this.backend?.cue?.reason];
    if (cue) return { label: cue, busy: false, tone: "idle", elapsed: null };
    if (!this.connected && this.received !== null) return { label: "Reconnecting progress", busy: false, tone: "warning", elapsed: null };
    return { label: this.listening ? "Listening" : "Ready", busy: false, tone: "idle", elapsed: null };
  }
  mark(category) {
    if (!this.agentId || !ISSUE_CATEGORIES.includes(category)) return;
    this.markers.push({ category, agentId: this.agentId, epoch: this.epoch, browserMs: this.now(), wallMs: this.wall(),
      operationIds: (this.backend?.active || []).map(entry => entry.operationId), association: "operator_marker_approximate" });
    if (this.markers.length > 64) { this.markers.shift(); this.droppedMarkers++; } this.notify();
  }
  snapshot() { return structuredClone({ version: 1, journals: [...this.journals.values()], droppedJournals: this.droppedJournals,
    markers: this.markers, droppedMarkers: this.droppedMarkers, taskStates: this.taskStates, droppedTaskStates: this.droppedTaskStates,
    coverage: { backend: this.received === null ? "not_observed" : this.connected ? "connected" : "disconnected",
      clocks: { browser: "performance_now_ms", server: "unix_ms", duration: "server_monotonic_ms" },
      retention: { journals: 16, entriesPerJournal: 1024, issueMarkers: 64, taskStates: 256 }, physicalAudibility: "not_measured", providerCompletion: "not_available" } }); }
  clear() { this.journals.clear(); this.markers = []; this.taskStates = []; this.droppedJournals = this.droppedMarkers = this.droppedTaskStates = 0; this.notify(); }
  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  notify() { for (const fn of this.listeners) fn(); }
}
export const interactionActivity = new InteractionActivity();
globalThis.PrometheusActivity = interactionActivity;

export function mountActivity() {
  const root = document.getElementById("interaction_activity"); if (!root) return;
  const label = root.querySelector("[data-activity-label]"), detail = root.querySelector("[data-activity-detail]"), elapsed = root.querySelector("[data-activity-elapsed]");
  const render = () => {
    const value = interactionActivity.view();
    if (label.textContent !== value.label) label.textContent = value.label;
    detail.textContent = value.detail || ""; detail.hidden = !value.detail;
    elapsed.textContent = value.elapsed >= 1000 ? `${Math.floor(value.elapsed / 1000)}s` : "";
    root.dataset.busy = String(value.busy); root.dataset.tone = value.tone;
    root.querySelector("[data-issue-open]").disabled = !interactionActivity.agentId;
  };
  interactionActivity.subscribe(render); render();
  const timer = setInterval(render, 250);
  window.addEventListener("pagehide", () => clearInterval(timer), { once: true });
  root.querySelectorAll("[data-issue-marker]").forEach(button => button.addEventListener("click", () => {
    interactionActivity.mark(button.dataset.issueMarker);
    root.querySelector("[data-issue-feedback]").textContent = "Issue marked in Telemetry";
  }));
}
