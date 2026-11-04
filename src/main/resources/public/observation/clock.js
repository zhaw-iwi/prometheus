export const CLOCK_LIMITS = Object.freeze({ maxRoundTripMs: 1000, timeoutMs: 2000,
  maxAgeMs: 300000, refreshAfterMs: 240000, wallJumpMs: 1000, driftPerMs: .0001 });

/** Capture on a conservative server-time bound; never derive age from the local wall clock. */
export class ObservationClock {
  constructor({ fetch: request = globalThis.fetch.bind(globalThis), monotonic = () => performance.now(),
    wall = () => Date.now(), schedule = setTimeout, cancel = clearTimeout } = {}) {
    Object.assign(this, { request, monotonic, wall, schedule, cancel });
    this.epoch = 0; this.calibration = null; this.pending = null; this.reason = "clock_unavailable";
    this.lastFailure = null;
  }

  invalidate(reason = "clock_unavailable") {
    this.epoch++; this.calibration = null; this.reason = reason;
    this.pending?.controller.abort(); this.pending = null;
  }

  snapshot() {
    const sample = this.calibration, now = this.monotonic(), wall = this.wall();
    if (sample) {
      const age = now - sample.received;
      if (!Number.isFinite(age) || age < 0 || !Number.isFinite(wall)
          || Math.abs(wall - sample.wall - age) > CLOCK_LIMITS.wallJumpMs) this.invalidate("clock_discontinuity");
      else if (age >= CLOCK_LIMITS.maxAgeMs) this.invalidate("clock_expired");
    }
    const current = this.calibration;
    const age = current ? now - current.received : null;
    return { status: current ? "ready" : "unavailable", reason: current ? null : this.reason,
      roundTripMs: current?.roundTrip ?? null, calibrationAgeMs: age,
      uncertaintyMs: current ? current.roundTrip + 2 * (1 + age * CLOCK_LIMITS.driftPerMs) : null,
      offsetMs: current ? current.server - current.wall : null, lastFailure: this.lastFailure };
  }

  needsRefresh() {
    const status = this.snapshot();
    return status.status !== "ready" || status.calibrationAgeMs >= CLOCK_LIMITS.refreshAfterMs;
  }

  capture() {
    if (this.snapshot().status !== "ready") return null;
    const age = this.monotonic() - this.calibration.received;
    const time = Math.floor(this.calibration.server + age - 1 - age * CLOCK_LIMITS.driftPerMs);
    return Object.freeze({ ts: new Date(time).toISOString(), epoch: this.epoch });
  }

  accepts(capture) {
    return !!capture && capture.epoch === this.epoch && this.snapshot().status === "ready";
  }

  synchronize() {
    if (this.pending) return this.pending.promise;
    const request = { epoch: this.epoch, controller: new AbortController() };
    this.pending = request;
    request.promise = this.sample(request);
    return request.promise;
  }

  async sample(request) {
    const sent = this.monotonic(), sentWall = this.wall();
    let timer;
    try {
      const timeout = new Promise((_, reject) => {
        timer = this.schedule(() => { request.controller.abort(); reject(new Error("clock_timeout")); }, CLOCK_LIMITS.timeoutMs);
      });
      const data = await Promise.race([timeout, (async () => {
        const response = await this.request("/runtime/clock", { cache: "no-store", credentials: "omit", signal: request.controller.signal });
        if (!response.ok) throw new Error("clock_request_failed");
        return response.json();
      })()]);
      if (request.epoch !== this.epoch) return false;
      const received = this.monotonic(), wall = this.wall(), roundTrip = received - sent;
      if (!Number.isFinite(roundTrip) || roundTrip < 0 || !Number.isFinite(wall)
          || Math.abs(wall - sentWall - roundTrip) > CLOCK_LIMITS.wallJumpMs) {
        this.invalidate("clock_discontinuity"); return false;
      }
      if (!Number.isSafeInteger(data?.serverTimeMs) || data.serverTimeMs <= 0
          || data.serverTimeMs > 8640000000000000) throw new Error("clock_invalid_sample");
      if (roundTrip > CLOCK_LIMITS.maxRoundTripMs) throw new Error("clock_uncertain");
      this.calibration = { server: data.serverTimeMs, received, wall, roundTrip };
      this.reason = null; this.lastFailure = null;
      return true;
    } catch (error) {
      if (request.epoch === this.epoch) {
        this.lastFailure = ["clock_timeout", "clock_uncertain", "clock_invalid_sample"].includes(error.message)
          ? error.message : "clock_request_failed";
        if (!this.calibration) this.reason = this.lastFailure;
      }
      return false;
    } finally {
      this.cancel(timer);
      if (this.pending === request) this.pending = null;
    }
  }
}
