import test from "node:test";
import assert from "node:assert/strict";
import { PresenceReception } from "../../../src/main/resources/public/live/reception.js";

function deferred() {
  let resolve; const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}
function fixture(overrides = {}) {
  let now = 0;
  const calls = [];
  const reception = new PresenceReception({
    start: async () => { calls.push("start"); },
    stop: () => { calls.push("silence"); return Promise.resolve(); },
    waiting: () => calls.push("waiting"), failed: () => calls.push("failed"),
    now: () => now, ...overrides,
  });
  return { reception, calls, advance: ms => { now += ms; } };
}

test("departure, stale presence and operator stop pass distinct metadata without delaying silence", () => {
  const causes = []; const { reception: r, advance } = fixture({ stop: (_, cause) => { causes.push(cause); } });
  r.arm(); r.observe(1); r.observe(0); assert.equal(causes.at(-1), "visitor_departure");
  r.arm(); advance(15000); r.checkFreshness(); assert.equal(causes.at(-1), "presence_stale");
  r.disarm(); assert.equal(causes.at(-1), "operator_stop");
});

test("arming is quiet; only an acknowledged arrival starts one session", async () => {
  const { reception: r, calls } = fixture();
  r.arm(); assert.deepEqual(calls, ["waiting"]);
  assert.equal(r.observe(1), true); assert.deepEqual(calls, ["waiting"]);
  await r.committed(1);
  for (let i = 0; i < 10; i++) { r.observe(1); await r.committed(1); }
  r.observe(2); await r.committed(2);
  assert.equal(calls.filter(x => x === "start").length, 1);
});

test("departure silences synchronously before server acknowledgement; re-entry waits for close", async () => {
  const closed = deferred(), calls = [];
  const { reception: r } = fixture({
    start: async () => calls.push("start"),
    stop: () => { calls.push("silence"); return closed.promise; },
  });
  r.arm(); r.observe(1); await r.committed(1);
  r.observe(0); assert.deepEqual(calls, ["start", "silence"]);
  r.observe(1); const next = r.committed(1);
  await Promise.resolve(); assert.deepEqual(calls, ["start", "silence"]);
  closed.resolve(); await next;
  assert.deepEqual(calls, ["start", "silence", "start"]);
});

test("late occupied acknowledgements cannot resurrect a departed encounter", async () => {
  const { reception: r, calls } = fixture();
  r.arm(); r.observe(1); const oldVersion = r.version; r.observe(0); await r.committed(1, oldVersion);
  assert.equal(calls.includes("start"), false);
  await r.committed(0); r.observe(1); await r.committed(1, oldVersion);
  assert.equal(calls.includes("start"), false);
  await r.committed(1);
  assert.equal(calls.filter(x => x === "start").length, 1);
});

test("departure and operator Stop fence in-flight startup and automatic restarts", async () => {
  const startup = deferred(), calls = [];
  const { reception: r } = fixture({
    start: () => { calls.push("start"); return startup.promise; },
    stop: () => { calls.push("silence"); },
  });
  r.arm(); r.observe(1); const starting = r.committed(1);
  await Promise.resolve();
  r.observe(0); r.disarm(); startup.resolve(); await starting;
  r.observe(1); await r.committed(1);
  assert.equal(calls.filter(x => x === "start").length, 1);
  assert.equal(r.armed, false);
});

test("missing camera readings disarm and silence, while stable readings keep the reception armed", async () => {
  const { reception: r, calls, advance } = fixture();
  r.arm(); r.observe(1); await r.committed(1);
  for (let i = 0; i < 5; i++) { advance(5000); r.observe(1); r.checkFreshness(); }
  assert.equal(r.armed, true);
  advance(15000); r.checkFreshness();
  assert.equal(r.armed, false); assert.ok(calls.includes("silence")); assert.ok(calls.includes("failed"));
});

test("failed startup does not repeatedly retry while visitors remain visible", async () => {
  const { reception: r, calls } = fixture({ start: async () => { throw new Error("provider unavailable"); } });
  r.arm(); r.observe(1); await r.committed(1);
  assert.equal(r.armed, false); assert.ok(calls.includes("failed"));
  r.observe(1); await r.committed(1); assert.equal(r.armed, false);
});
