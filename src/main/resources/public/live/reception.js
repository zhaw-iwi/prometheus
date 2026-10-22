/**
 * One operator arm, one voice session per occupied encounter. Observation and commit
 * are separate: confirmed local departure stops audio without waiting for HTTP.
 */
export class PresenceReception {
  constructor({ start, stop, waiting = () => {}, failed = () => {}, diagnostic = () => {}, now = Date.now,
    schedule = (fn, ms) => setTimeout(fn, ms), cancel = timer => clearTimeout(timer) }) {
    Object.assign(this, { start, stop, waiting, failed, diagnostic, now, schedule, cancel });
    this.armed = false; this.version = 0; this.generation = 0; this.count = null; this.pending = null;
    this.absentSince = null; this.graceMs = 0;
  }
  arm(graceMs = 0) {
    this.clearDeparture(); this.graceMs = Number.isFinite(graceMs) ? Math.max(0, Math.min(2000, graceMs)) : 0;
    this.armed = true; this.version++; this.generation++; this.count = null; this.seenAt = this.now();
    this.needsSample = true; this.waiting();
  }
  observe(count) {
    if (!this.armed || !Number.isInteger(count) || count < 0) return false;
    // A late timer/positive sample cannot extend an already completed absence.
    this.checkDeparture();
    this.seenAt = this.now();
    const edge = this.count === null || (count === 0) !== (this.count === 0);
    this.count = count;
    if (edge) {
      this.version++;
      this.needsSample = true;
      if (count === 0) {
        this.absentSince = this.now();
        this.report("absence_pending");
        if (this.graceMs === 0) this.checkDeparture();
        else this.departureTimer = this.schedule(() => this.checkDeparture(), this.graceMs);
      } else if (this.absentSince !== null) {
        this.report("presence_resumed"); this.clearDeparture();
      }
    }
    return edge || this.needsSample;
  }
  async committed(count, observedVersion = this.version) {
    if (!this.armed || observedVersion !== this.version || count !== this.count) return;
    this.needsSample = false;
    if (count === 0 || this.pending || this.running) return;
    const generation = this.generation;
    const task = (async () => {
      await this.closing;
      if (!this.armed || generation !== this.generation || this.count === 0) return;
      this.running = true;
      await this.start();
    })();
    this.pending = task;
    try { await task; }
    catch (error) { if (this.armed && generation === this.generation) { this.disarm(error.message, "startup_failed"); this.failed(error); } }
    finally {
      if (this.pending === task) this.pending = null;
      if (generation !== this.generation) this.running = false;
      if (this.armed && this.count > 0 && !this.running) this.needsSample = true;
    }
  }
  report(outcome) {
    this.diagnostic({ phase: "presence", outcome, graceMs: this.graceMs,
      durationMs: this.absentSince === null ? 0 : Math.max(0, this.now() - this.absentSince) });
  }
  clearDeparture() {
    if (this.departureTimer !== undefined) this.cancel(this.departureTimer);
    this.departureTimer = undefined; this.absentSince = null;
  }
  checkDeparture() {
    if (!this.armed || this.count !== 0 || this.absentSince === null || this.now() - this.absentSince < this.graceMs) return;
    this.report("departure_confirmed"); this.clearDeparture(); this.version++; this.generation++; this.running = false;
    const generation = this.generation;
    // stop() synchronously detaches audio/input, independently of a slow HTTP acknowledgement.
    this.closing = Promise.resolve(this.stop("No visitors visible.", "visitor_departure"));
    this.closing.then(() => { if (this.armed && generation === this.generation && this.count === 0) this.waiting(); });
  }
  checkFreshness() {
    if (this.armed && this.now() - this.seenAt >= 15000) {
      this.disarm("Visitor sensing stopped. Check the camera, then start reception again.", "presence_stale");
      this.failed(new Error("Visitor sensing stopped. Check the camera, then start reception again."));
    }
    this.checkDeparture();
  }
  disarm(reason = "Reception stopped.", cause = "operator_stop") {
    this.clearDeparture(); this.armed = false; this.version++; this.generation++; this.running = false; this.needsSample = false;
    return this.stop(reason, cause);
  }
}
