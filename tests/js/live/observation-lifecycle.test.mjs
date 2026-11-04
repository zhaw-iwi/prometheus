import test from "node:test";
import assert from "node:assert/strict";
import { ObservationTiming } from "../../../src/main/resources/public/observation/client.js";
import { ObservationClock } from "../../../src/main/resources/public/observation/clock.js";

function fixture() {
  let time = 1000, hidden = false, fail = false;
  const timers = new Map(); let sequence = 0;
  const schedule = (fn, delay) => { const id = ++sequence; timers.set(id, {fn, delay}); return id; };
  const cancel = id => timers.delete(id), states = [];
  const clock = new ObservationClock({ monotonic: () => time, wall: () => 1700000002640 + time,
    fetch: async () => ({ok: !fail, json: async () => ({serverTimeMs: 1700000000000 + time})}), schedule, cancel });
  const client = new ObservationTiming({clock, visible: () => !hidden, schedule, cancel, changed: value => states.push(value)});
  return { clock, client, timers, states, advance(ms) {time += ms;}, fail(value) { fail = value; },
    visibility(value) { hidden = value; return client.visibilityChanged(); },
    async tick() { const [id,timer] = [...timers][0]; timers.delete(id); timer.fn(); await new Promise(resolve => setImmediate(resolve)); } };
}
test("connection, hiding, resume and disconnect fence captures and leave no background timer", async () => {
  const f = fixture(); assert.equal(f.client.capture(), null); await f.client.start();
  const original = f.client.capture(); assert.ok(original);
  await f.visibility(true); assert.equal(f.client.accepts(original), false); assert.equal(f.timers.size, 0);
  f.advance(600000); await f.visibility(false); assert.ok(f.client.capture());
  assert.equal(f.client.accepts(original), false); f.client.stop();
  assert.equal(f.client.capture(), null); assert.equal(f.timers.size, 0);
});
test("failed initialization recovers on a bounded retry; failed refresh cannot renew old evidence", async () => {
  const f = fixture(); f.fail(true); assert.equal(await f.client.start(), false);
  assert.equal([...f.timers.values()][0].delay, 5000); f.fail(false); await f.tick();
  const old = f.client.capture(); f.advance(240000); f.fail(true); await f.tick();
  assert.equal(f.client.accepts(old), true); f.advance(60000); await f.tick();
  assert.equal(f.client.capture(), null); f.fail(false); await f.tick();
  assert.ok(f.client.capture()); assert.equal(f.client.accepts(old), false); f.client.stop();
});
test("scope replacement during a request cannot restore its calibration or timer", async () => {
  const f = fixture(); let release;
  f.clock.request = () => new Promise(resolve => {release = resolve;});
  const old = f.client.start(); f.client.stop();
  release({ok: true, json: async () => ({serverTimeMs: 1700000001000})});
  await old; assert.equal(f.client.capture(), null); assert.equal(f.timers.size, 0);
});
