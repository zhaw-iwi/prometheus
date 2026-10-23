import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const source = fs.readFileSync(new URL("../../../src/main/resources/public/valerian/script.js", import.meta.url), "utf8");

// Exercise camera classification through the real acknowledgement/header code.
// Only media, rendering and the HTTP response are simulated.
function fixture() {
  let now = Date.parse("2026-10-20T08:00:00Z");
  const requests = [], elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, { checked: true, value: "0.65", textContent: "" });
    return elements.get(id);
  };
  const context = vm.createContext({
    Date: class extends Date {
      constructor(...args) { super(...(args.length ? args : [now])); }
      static now() { return now; }
    },
    Headers, performance: { now: () => now }, HTMLMediaElement: { HAVE_CURRENT_DATA: 2 },
    state: { agentId: "agent", accessCode: "code" }, liveVoice: { ui: { active: true } },
    document: { getElementById: element },
    setText(id, value) { element(id).textContent = value; },
    renderHandSignReport(sign, options) { context.setHandSignStatus(options.statusText, options.statusMode); },
    resetHandSignReport(status) { context.setHandSignStatus(status); },
    renderUserSign() {}, setActiveStatus() {}, renderLatestEvent() {}, appendLog() {}, handleResponseEvent() {},
    renderAgentSign() {}, resetBehaviourPanels() {}, renderNonVerbal() {}, renderMotionEnergyState() {},
    setBehaviourChannelActive() {}, behaviourSummary() { return "plan"; }, winnerLabel(value) { return value; },
    resetEmotionReport() {}, renderSocialMetrics() {}, resetWeatherState() {}, setCameraStatus() {},
    async fetch(url, options) {
      requests.push({ url, ...options });
      return { ok: context.httpStatus === 200, status: context.httpStatus,
        async json() { return { active: true, responseEvent: null }; } };
    },
    httpStatus: 200,
  });
  for (const name of ["camera", "CANNED_GESTURE_TO_SIGN", "SIGNS"]) {
    vm.runInContext(new RegExp(`^const ${name} = \\{[\\s\\S]*?^\\};`, "m").exec(source)[0], context);
  }
  for (const name of ["ACCESS_CODE_HEADER", "REQUIRED_STABLE_GESTURE_FRAMES"]) {
    vm.runInContext(new RegExp(`^const ${name} = .*;`, "m").exec(source)[0], context);
  }
  for (const name of ["detectHandSign", "selectCameraGesture", "updateCameraStability", "maybeEmitCameraSign",
    "submitHandSign", "acknowledgeEvent", "timedScopedFetch", "scopedFetch", "demoAgentPath",
    "setHandSignStatus", "markSensorEmitted", "normalizeSign", "round", "resetCameraEmissionGate",
    "renderBehaviourPlan", "renderMotion", "renderDisplay", "captureIdOf", "updateHandCapture", "resetSensingState"]) {
    const match = new RegExp(`^(?:async )?function ${name}\\(`, "m").exec(source);
    assert.ok(match, name);
    const rest = source.slice(match.index), end = /^}/m.exec(rest);
    vm.runInContext(rest.slice(0, end.index + 1), context);
  }
  vm.runInContext(`
    camera.video = { readyState: 2, currentTime: 0 };
    camera.handRecognizer = { recognizeForVideo: () => result };
    globalThis.camera = camera;
  `, context);
  let overlay = null;
  context.drawGestureOverlay = candidate => { overlay = candidate; };
  return { context, requests, element,
    advance(ms) { now += ms; },
    overlay: () => overlay,
    status: () => element("hand_sign_status").textContent,
    async frame(gesture = "Open_Palm", confidence = .9) {
      context.result = { gestures: [[{ categoryName: gesture, score: confidence }]],
        landmarks: [[{ x: .5, y: .5 }]] };
      context.camera.video.currentTime += .35;
      await context.detectHandSign();
    },
  };
}

test("camera rock, scissor and paper reach the scoped framework as timestamped observations", async () => {
  const f = fixture();
  for (const [gesture, sign] of [["Closed_Fist", "rock"], ["Victory", "scissor"], ["Open_Palm", "paper"]]) {
    for (let frame = 0; frame < 3; frame++) await f.frame(gesture);
    const request = f.requests.at(-1), event = JSON.parse(request.body), payload = JSON.parse(event.payload);
    assert.equal(request.url, "/demo/agents/agent/acknowledge");
    assert.equal(request.method, "POST");
    assert.equal(request.headers.get("X-Prometheus-Access-Code"), "code");
    assert.equal(event.type, "obs.hand.sign");
    assert.equal(event.actor, "user");
    assert.equal(event.kind, "observation");
    assert.equal(payload.sign, sign);
    assert.equal(payload.source, "valerian.hand.camera");
    assert.equal(payload.detectionMode, "client_camera");
    assert.equal(payload.confidence, .9);
    assert.equal(payload.stabilityFrames, 3);
    assert.equal(Date.parse(payload.ts), f.context.Date.now());
  }
  assert.equal(f.requests.length, 3);
});

const opening = captureId => ({ motion: { handSign: "rock" }, display: { awaitingUserSign: true, captureId } });
const payload = request => JSON.parse(JSON.parse(request.body).payload);

test("preparation captures all three signs and the game requires fresh stable frames even for the same last sign", async () => {
  const f = fixture();
  const preparation = { display: { mode: "game_setup", phase: "recognise_all_signs", awaitingUserSign: true,
    captureId: "preparation", remainingSigns: ["rock", "paper", "scissor"] } };
  f.context.renderBehaviourPlan(preparation);
  for (const gesture of ["Closed_Fist", "Open_Palm", "Victory"]) {
    await f.frame(gesture); await f.frame(gesture); await f.frame(gesture);
    f.context.renderBehaviourPlan(preparation); // Progress displays keep the same capture.
    f.advance(2500);
  }
  assert.deepEqual(f.requests.map(request => payload(request).sign), ["rock", "paper", "scissor"]);
  assert.ok(f.requests.every(request => payload(request).captureId === "preparation"));
  f.context.renderBehaviourPlan(opening("game"));
  await f.frame("Victory"); await f.frame("Victory"); assert.equal(f.requests.length, 3);
  await f.frame("Victory");
  assert.equal(payload(f.requests[3]).captureId, "game");
  assert.equal(payload(f.requests[3]).sign, "scissor");
});

test("duplicate opening plans preserve fresh-frame progress and cooldown; next capture accepts the same sign", async () => {
  const f = fixture();
  await f.frame(); await f.frame(); // Pre-opening frames cannot count toward the new capture.
  f.context.renderBehaviourPlan(opening("round-one"));
  await f.frame(); f.context.renderBehaviourPlan(opening("round-one"));
  await f.frame(); assert.equal(f.requests.length, 0);
  f.context.renderBehaviourPlan(opening("round-one"));
  await f.frame(); assert.equal(f.requests.length, 1);
  assert.equal(payload(f.requests[0]).captureId, "round-one");
  assert.equal(payload(f.requests[0]).stabilityFrames, 3);
  f.context.renderBehaviourPlan(opening("round-one")); await f.frame();
  assert.equal(f.requests.length, 1, "replayed motion must not bypass ordinary cooldown");
  f.context.renderBehaviourPlan(opening("round-two"));
  await f.frame(); await f.frame(); assert.equal(f.requests.length, 1);
  await f.frame(); assert.equal(f.requests.length, 2, "no 2.5-second wait for the next capture");
  assert.equal(payload(f.requests[1]).captureId, "round-two");
});

test("a delayed request retains its original capture and cannot restore the new capture's cooldown or status", async () => {
  const f = fixture(); let release;
  const original = f.context.fetch;
  f.context.renderBehaviourPlan(opening("old"));
  f.context.fetch = async (...args) => { await new Promise(resolve => { release = resolve; }); return original(...args); };
  await f.frame(); await f.frame(); const pending = f.frame();
  f.context.renderBehaviourPlan(opening("new"));
  f.context.setHandSignStatus("Waiting for new capture");
  release(); await pending;
  assert.equal(payload(f.requests[0]).captureId, "old");
  assert.equal(f.context.camera.lastCameraEmitKey, null);
  assert.equal(f.status(), "Waiting for new capture");
  f.context.fetch = original;
  await f.frame(); await f.frame(); assert.equal(f.requests.length, 1);
  await f.frame(); assert.equal(payload(f.requests[1]).captureId, "new");
});

test("manual input echoes the active capture; results and cockpit reset cannot reopen or leak it", async () => {
  const f = fixture();
  f.context.renderBehaviourPlan(opening("current"));
  f.context.renderDisplay({ awaitingUserSign: false, captureId: "older" });
  await f.context.submitHandSign("rock"); assert.equal(payload(f.requests.at(-1)).captureId, "current");
  f.context.renderDisplay({ awaitingUserSign: false, captureId: "current" });
  f.context.renderBehaviourPlan(opening("current"));
  await f.context.submitHandSign("paper"); assert.equal(payload(f.requests.at(-1)).captureId, undefined);
  f.context.renderBehaviourPlan(opening("next"));
  f.context.resetSensingState();
  await f.context.submitHandSign("paper"); assert.equal(payload(f.requests.at(-1)).captureId, undefined);
});

test("legacy capture displays still reset stability and emit ordinary observations without a capture ID", async () => {
  const f = fixture();
  await f.frame(); await f.frame();
  f.context.renderBehaviourPlan({ display: { awaitingUserSign: true } });
  await f.frame(); await f.frame(); assert.equal(f.requests.length, 0);
  await f.frame(); assert.equal(f.requests.length, 1);
  assert.equal(payload(f.requests[0]).captureId, undefined);
});

test("a visible overlay explicitly reports disabled emission instead of implying delivery", async () => {
  const f = fixture(); f.element("sensor_emit_enabled").checked = false;
  for (let frame = 0; frame < 3; frame++) await f.frame();
  assert.ok(f.overlay()?.landmarks);
  assert.equal(f.requests.length, 0);
  assert.match(f.status(), /not sent.*enable.*camera observations/i);
  f.element("sensor_emit_enabled").checked = true;
  await f.frame();
  assert.equal(f.requests.length, 1);
  assert.match(f.status(), /^Emitted /);
});

test("low confidence and unstable signs explain why the framework has no reading", async () => {
  const f = fixture();
  for (let frame = 0; frame < 3; frame++) await f.frame("Open_Palm", .4);
  assert.equal(f.requests.length, 0);
  assert.match(f.status(), /not sent.*confidence/i);
  await f.frame("Victory");
  assert.match(f.status(), /stabili.*1\/3/i);
  await f.frame("Closed_Fist");
  assert.match(f.status(), /stabili.*1\/3/i);
  assert.equal(f.requests.length, 0);
});

test("browser confidence gates emission and never claims game acceptance", async () => {
  const f = fixture();
  for (let frame = 0; frame < 3; frame++) await f.frame("Open_Palm", .649);
  assert.equal(f.requests.length, 0);
  for (let frame = 0; frame < 3; frame++) await f.frame("Open_Palm", .65);
  assert.equal(f.requests.length, 1);
  assert.equal(JSON.parse(JSON.parse(f.requests[0].body).payload).confidence, .65);
  assert.match(f.status(), /^Emitted /); assert.doesNotMatch(f.status(), /accepted|ready|won|winner/i);
});

test("confirmed hand delivery stays visible during cooldown and held signs are resent", async () => {
  const f = fixture();
  for (let frame = 0; frame < 3; frame++) await f.frame();
  const emitted = f.status();
  f.advance(1000); await f.frame();
  assert.equal(f.requests.length, 1);
  assert.equal(f.status(), emitted);
  f.advance(1500); await f.frame();
  assert.equal(f.requests.length, 2);
  assert.notEqual(JSON.parse(JSON.parse(f.requests[0].body).payload).ts,
    JSON.parse(JSON.parse(f.requests[1].body).payload).ts);
});

test("failed sends and a missing connection never claim that the sign was emitted", async () => {
  const f = fixture(); f.context.httpStatus = 500;
  for (let frame = 0; frame < 3; frame++) await f.frame();
  assert.equal(f.status(), "Emit failed");
  assert.equal(f.context.camera.lastCameraEmitKey, null);
  f.context.httpStatus = 200; await f.frame();
  assert.match(f.status(), /^Emitted /);
  f.context.state.agentId = null; await f.frame();
  assert.match(f.status(), /not sent.*connect/i);
  assert.equal(f.requests.length, 2);
});

test("an old hand acknowledgement cannot restore cooldown after a new capture window opens", async () => {
  const f = fixture();
  let release;
  const original = f.context.fetch;
  f.context.fetch = async (...args) => { await new Promise(resolve => { release = resolve; }); return original(...args); };
  await f.frame(); await f.frame();
  const old = f.frame();
  f.context.resetCameraEmissionGate();
  release(); await old;
  assert.equal(f.context.camera.lastCameraEmitKey, null);
  assert.equal(f.context.camera.lastCameraEmitAt, 0);
  f.context.fetch = original;
  await f.frame();
  assert.equal(f.requests.length, 2);
  await f.frame();
  assert.equal(f.requests.length, 2, "ordinary cooldown still applies after the fresh send");
});
