/**
 * One operator arm, one voice session per occupied encounter. Observation and commit
 * are separate: local departure must silence audio before a slow HTTP acknowledgement.
 */
export class PresenceReception {
  constructor({ start, stop, waiting = () => {}, failed = () => {}, now = Date.now }) {
    Object.assign(this, { start, stop, waiting, failed, now });
    this.armed = false; this.version = 0; this.count = null; this.pending = null;
  }
  arm() {
    this.armed = true; this.version++; this.count = null; this.seenAt = this.now();
    this.needsSample = true; this.waiting();
  }
  observe(count) {
    if (!this.armed || !Number.isInteger(count) || count < 0) return false;
    this.seenAt = this.now();
    const edge = this.count === null || (count === 0) !== (this.count === 0);
    this.count = count;
    if (edge) {
      this.needsSample = true;
      if (count === 0) {
        this.version++; this.running = false;
        // stop() synchronously detaches the audio and microphone before its first await.
        this.closing = Promise.resolve(this.stop("No visitors visible.", "visitor_departure"));
        this.closing.then(() => { if (this.armed && this.count === 0) this.waiting(); });
      }
    }
    return edge || this.needsSample;
  }
  async committed(count, observedVersion = this.version) {
    if (!this.armed || observedVersion !== this.version || count !== this.count) return;
    this.needsSample = false;
    if (count === 0 || this.pending || this.running) return;
    const version = this.version;
    const task = (async () => {
      await this.closing;
      if (!this.armed || version !== this.version || this.count === 0) return;
      this.running = true;
      await this.start();
    })();
    this.pending = task;
    try { await task; }
    catch (error) { this.disarm(error.message, "startup_failed"); this.failed(error); }
    finally {
      if (this.pending === task) this.pending = null;
      if (version !== this.version) this.running = false;
      if (this.armed && this.count > 0 && !this.running) this.needsSample = true;
    }
  }
  checkFreshness() {
    if (this.armed && this.now() - this.seenAt >= 15000) {
      this.disarm("Visitor sensing stopped. Check the camera, then start reception again.", "presence_stale");
      this.failed(new Error("Visitor sensing stopped. Check the camera, then start reception again."));
    }
  }
  disarm(reason = "Reception stopped.", cause = "operator_stop") {
    this.armed = false; this.version++; this.running = false; this.needsSample = false;
    return this.stop(reason, cause);
  }
}
