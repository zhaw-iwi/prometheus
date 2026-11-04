import { ObservationClock } from "./clock.js";

/** One connection owns calibration, periodic refresh and capture generations. */
export class ObservationTiming {
  constructor({ clock = new ObservationClock(), visible = () => !document.hidden,
    schedule = setTimeout, cancel = clearTimeout, changed = () => {} } = {}) {
    Object.assign(this, { clock, visible, schedule, cancel, changed });
    this.running = false; this.timer = null; this.generation = 0;
    this.clearDiagnostics();
  }
  start() { this.stop(); this.running = true; return this.refresh(); }
  stop() {
    this.generation++;
    this.running = false; this.cancel(this.timer); this.timer = null;
    this.clock.invalidate("disconnected"); this.report();
  }
  visibilityChanged() {
    this.generation++;
    this.cancel(this.timer); this.timer = null;
    this.clock.invalidate(this.visible() ? "clock_resuming" : "page_hidden");
    this.report();
    if (this.running && this.visible()) return this.refresh();
  }
  async refresh() {
    if (!this.running || !this.visible()) return false;
    const generation = this.generation;
    if (this.clock.needsRefresh()) await this.clock.synchronize();
    if (!this.running || !this.visible() || generation !== this.generation) return false;
    const snapshot = this.clock.snapshot(); this.report();
    this.cancel(this.timer);
    this.timer = this.schedule(() => { void this.refresh(); }, snapshot.status === "ready" ? 30000 : 5000);
    return snapshot.status === "ready";
  }
  capture() {
    if (!this.running || !this.visible()) return null;
    const capture = this.clock.capture();
    if (!capture) this.report();
    return capture;
  }
  accepts(capture) { return this.running && this.visible() && this.clock.accepts(capture); }
  snapshot() { return this.clock.snapshot(); }
  clearDiagnostics() { this.recent = []; this.dropped = 0; this.lastDiagnostic = null; }
  report() {
    const status = this.snapshot(), safe = safeClockStatus(status);
    const key = JSON.stringify([this.clock.epoch, safe.status, safe.reason, safe.offsetMs, safe.lastFailure]);
    if (key !== this.lastDiagnostic) {
      this.lastDiagnostic = key;
      this.recent.push({browserMs: this.clock.monotonic(), ...safe});
      if (this.recent.length > 32) { this.recent.splice(16, 1); this.dropped++; }
    }
    this.changed(status);
  }
  diagnostics() {
    return {version: 1, current: safeClockStatus(this.snapshot()), recent: this.recent.map(row => ({...row})),
      dropped: this.dropped, interpretation: "Conservative server-time calibration; uncertainty is a bound within the configured drift assumption, not a measured network direction or physical capture time."};
  }
}

export function safeClockStatus(value) {
  const reasons = new Set(["clock_unavailable", "clock_resuming", "disconnected", "page_hidden", "scope_changed",
    "clock_expired", "clock_discontinuity", "clock_timeout", "clock_uncertain", "clock_invalid_sample", "clock_request_failed"]);
  const result = {status: value?.status === "ready" ? "ready" : "unavailable",
    reason: reasons.has(value?.reason) ? value.reason : null,
    lastFailure: reasons.has(value?.lastFailure) ? value.lastFailure : null};
  for (const key of ["roundTripMs", "calibrationAgeMs", "uncertaintyMs", "offsetMs"])
    result[key] = Number.isFinite(value?.[key]) && (key === "offsetMs" || value[key] >= 0) ? value[key] : null;
  return result;
}
