import test from "node:test";
import assert from "node:assert/strict";
import { ObservationClock, CLOCK_LIMITS } from "../../../src/main/resources/public/observation/clock.js";

function fixture(offset = 2640, outward = 20, inward = 80) {
  const base = Date.parse("2026-11-04T12:17:19Z");
  let mono = 1000, wall = base + offset, server = base;
  const f = { base, options: null, timer: null,
    advance(ms) { mono += ms; wall += ms; server += ms; },
    jump(ms) { wall += ms; }, rollback() { mono -= 1; },
    now: () => server,
    fetch: async (_, options) => { f.options = options; f.advance(outward); const sampled = server; f.advance(inward);
      return { ok: true, json: async () => ({ serverTimeMs: sampled }) }; } };
  f.clock = new ObservationClock({ fetch: (...args) => f.fetch(...args), monotonic: () => mono, wall: () => wall,
    schedule: callback => { f.timer = callback; return 1; }, cancel: () => { f.timer = null; } });
  return f;
}

test("independent wall offsets and asymmetric latency never date capture after server time", async () => {
  for (const offset of [2640, -2640, 3600000]) for (const [out, back] of [[20, 80], [80, 20], [0, 1000]]) {
    const f = fixture(offset, out, back); assert.equal(await f.clock.synchronize(), true);
    assert.equal(f.options.cache, "no-store"); assert.equal(f.options.credentials, "omit");
    const captured = f.clock.capture();
    assert.equal(Date.parse(captured.ts), f.now() - back - 1);
    f.advance(400); assert.equal(Date.parse(captured.ts), f.now() - back - 401);
    assert.equal(f.clock.accepts(captured), true);
  }
});
test("unavailable, expiry, drift and refresh boundaries are explicit", async () => {
  const f = fixture(); assert.equal(f.clock.capture(), null); await f.clock.synchronize();
  const captured = f.clock.capture(); f.advance(CLOCK_LIMITS.refreshAfterMs - 1);
  assert.equal(f.clock.needsRefresh(), false); f.advance(1); assert.equal(f.clock.needsRefresh(), true);
  assert.equal(Date.parse(f.clock.capture().ts), f.now() - 80 - 25);
  f.advance(CLOCK_LIMITS.maxAgeMs - CLOCK_LIMITS.refreshAfterMs);
  assert.equal(f.clock.capture(), null); assert.equal(f.clock.accepts(captured), false);
  assert.equal(f.clock.snapshot().reason, "clock_expired");
});
test("wall changes and monotonic rollback invalidate rather than shift old captures", async () => {
  for (const change of [f => f.jump(1001), f => f.jump(-1001), f => f.rollback()]) {
    const f = fixture(); await f.clock.synchronize(); const captured = f.clock.capture();
    change(f); assert.equal(f.clock.capture(), null); assert.equal(f.clock.accepts(captured), false);
    assert.equal(f.clock.snapshot().reason, "clock_discontinuity");
  }
});
test("bad samples and excessive uncertainty cannot create a calibration", async () => {
  for (const value of [null, {}, {serverTimeMs: "42"}, {serverTimeMs: -1}, {serverTimeMs: 1.5}]) {
    const f = fixture(); f.fetch = async () => ({ok: true, json: async () => value});
    assert.equal(await f.clock.synchronize(), false); assert.equal(f.clock.capture(), null);
  }
  const slow = fixture(0, 500, 501); assert.equal(await slow.clock.synchronize(), false);
  assert.equal(slow.clock.snapshot().reason, "clock_uncertain");
});
test("failed refresh preserves a valid sample but does not extend its lifetime", async () => {
  const f = fixture(); await f.clock.synchronize(); f.advance(240000);
  f.fetch = async () => { throw new Error("private network detail"); };
  assert.equal(await f.clock.synchronize(), false); assert.notEqual(f.clock.capture(), null);
  assert.equal(f.clock.snapshot().lastFailure, "clock_request_failed");
  f.advance(60000); assert.equal(f.clock.capture(), null);
});
test("timeout is bounded even if fetch ignores abort", async () => {
  const f = fixture(); f.fetch = () => new Promise(() => {});
  const pending = f.clock.synchronize(); f.timer(); assert.equal(await pending, false);
  assert.equal(f.clock.snapshot().reason, "clock_timeout");
});
test("late calibration cannot restore a disconnected scope; captures survive ordinary refresh", async () => {
  const f = fixture(); await f.clock.synchronize(); const first = f.clock.capture();
  f.advance(100); await f.clock.synchronize(); assert.equal(f.clock.accepts(first), true);
  let release; f.fetch = () => new Promise(resolve => { release = resolve; });
  const pending = f.clock.synchronize(); assert.equal(f.clock.synchronize(), pending);
  f.clock.invalidate("scope_changed"); release({ok: true, json: async () => ({serverTimeMs: f.now()})});
  assert.equal(await pending, false); assert.equal(f.clock.capture(), null);
  assert.equal(f.clock.accepts(first), false);
});
