import { ObservationClock } from "./clock.js";

/** One connection owns calibration, periodic refresh and capture generations. */
export class ObservationTiming {
  constructor({ clock = new ObservationClock(), visible = () => !document.hidden,
    schedule = setTimeout, cancel = clearTimeout, changed = () => {} } = {}) {
    Object.assign(this, { clock, visible, schedule, cancel, changed });
    this.running = false; this.timer = null; this.generation = 0;
  }
  start() { this.stop(); this.running = true; return this.refresh(); }
  stop() {
    this.generation++;
    this.running = false; this.cancel(this.timer); this.timer = null;
    this.clock.invalidate("disconnected"); this.changed(this.clock.snapshot());
  }
  visibilityChanged() {
    this.generation++;
    this.cancel(this.timer); this.timer = null;
    this.clock.invalidate(this.visible() ? "clock_resuming" : "page_hidden");
    this.changed(this.clock.snapshot());
    if (this.running && this.visible()) return this.refresh();
  }
  async refresh() {
    if (!this.running || !this.visible()) return false;
    const generation = this.generation;
    if (this.clock.needsRefresh()) await this.clock.synchronize();
    if (!this.running || !this.visible() || generation !== this.generation) return false;
    const snapshot = this.clock.snapshot(); this.changed(snapshot);
    this.cancel(this.timer);
    this.timer = this.schedule(() => { void this.refresh(); }, snapshot.status === "ready" ? 30000 : 5000);
    return snapshot.status === "ready";
  }
  capture() {
    if (!this.running || !this.visible()) return null;
    const capture = this.clock.capture();
    if (!capture) this.changed(this.clock.snapshot());
    return capture;
  }
  accepts(capture) { return this.running && this.visible() && this.clock.accepts(capture); }
  snapshot() { return this.clock.snapshot(); }
}
