import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { estimateHandPoses } from "../../../src/main/resources/public/valerian/hand/finger-pose.js";
import { handResult } from "../../fixtures/hand-landmarks.mjs";

const source = fs.readFileSync(new URL("../../../src/main/resources/public/valerian/script.js", import.meta.url), "utf8");

// Exercise camera classification through the real acknowledgement/header code.
// Only media, rendering and the HTTP response are simulated.
function fixture({ poseSupported = false, signSupported = true } = {}) {
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
    renderHandPoseReport(payload, status = "Detected") { context.posePayload = payload; context.setHandPoseStatus(status); },
    currentProfileSupportsObservation(type) { return type === "obs.hand.pose" ? context.poseSupported : context.signSupported; },
    poseSupported, signSupported, estimateHandPoses, recognitions: 0,
    renderUserSign() {}, setActiveStatus() {}, renderLatestEvent() {}, appendLog() {}, handleResponseEvent() {},
    renderAgentSign() {}, resetBehaviourPanels() {}, renderNonVerbal() {}, renderMotionEnergyState() {},
    setBehaviourChannelActive() {}, behaviourSummary() { return "plan"; }, winnerLabel(value) { return value; },
    resetEmotionReport() {}, renderSocialMetrics() {}, resetWeatherState() {}, setCameraStatus() {},
    async fetch(url, options) {
      requests.push({ url, ...options });
      return { ok: context.httpStatus === 200, status: context.httpStatus,
        async json() { return { active: true, responseEvent: null }; } };
    },
    httpStatus: 200, timingAvailable: true, timingEpoch: 1,
  });
  context.PrometheusObservationTiming = {
    capture: () => context.timingAvailable ? {ts: new Date(now - 2640).toISOString(), epoch: context.timingEpoch} : null,
    accepts: capture => context.timingAvailable && capture?.epoch === context.timingEpoch,
  };
  for (const name of ["camera", "CANNED_GESTURE_TO_SIGN", "SIGNS"]) {
    vm.runInContext(new RegExp(`^const ${name} = \\{[\\s\\S]*?^\\};`, "m").exec(source)[0], context);
  }
  for (const name of ["ACCESS_CODE_HEADER", "REQUIRED_STABLE_GESTURE_FRAMES"]) {
    vm.runInContext(new RegExp(`^const ${name} = .*;`, "m").exec(source)[0], context);
  }
  for (const name of ["captureObservationTime", "usableObservationTime", "detectHandSign", "selectCameraGesture", "updateCameraStability", "maybeEmitCameraSign",
    "submitHandSign", "acknowledgeEvent", "timedScopedFetch", "scopedFetch", "captureConnection", "currentConnection", "demoAgentPath", "sensorBackendResult",
    "setHandSignStatus", "setHandPoseStatus", "handRecognizerLabel", "markSensorEmitted", "normalizeSign", "round", "resetCameraEmissionGate",
    "renderBehaviourPlan", "renderMotion", "renderDisplay", "captureIdOf", "updateHandCapture", "resetSensingState", "renderHandConfidencePolicy"]) {
    const match = new RegExp(`^(?:async )?function ${name}\\(`, "m").exec(source);
    assert.ok(match, name);
    const rest = source.slice(match.index), end = /^}/m.exec(rest);
    vm.runInContext(rest.slice(0, end.index + 1), context);
  }
  for (const name of ["maybeEmitHandPose", "passesSensorEmitInterval"]) {
    const match = new RegExp(`^(?:async )?function ${name}\\(`, "m").exec(source);
    const rest = source.slice(match.index), end = /^}/m.exec(rest);
    vm.runInContext(rest.slice(0, end.index + 1), context);
  }
  vm.runInContext(`
    camera.video = { readyState: 2, currentTime: 0 };
    camera.handRecognizer = { recognizeForVideo: () => { recognitions++; return result; } };
    camera.handPoseEstimator = estimateHandPoses;
    globalThis.camera = camera;
  `, context);
  let overlay = null;
  context.drawGestureOverlay = candidate => { overlay = candidate; };
  return { context, requests, element,
    advance(ms) { now += ms; },
    overlay: () => overlay,
    status: () => element("hand_sign_status").textContent,
    poseStatus: () => element("hand_pose_status").textContent,
    async poseFrame(result = handResult({ index: .5 })) {
      context.camera.handModel = "mediapipe";
      context.result = result;
      context.camera.video.currentTime += .35;
      await context.detectHandSign();
    },
    async frame(gesture = "Open_Palm", confidence = .9) {
      context.result = { gestures: [[{ categoryName: gesture, score: confidence }]],
        landmarks: [[{ x: .5, y: .5 }]] };
      context.camera.video.currentTime += .35;
      await context.detectHandSign();
    },
  };
}

test("RPS capture uses the configured confidence and still requires three qualifying samples", async () => {
  const f = fixture();
  f.element("hand_confidence_threshold").value = "0.60";
  f.context.updateHandCapture({ awaitingUserSign: true, captureId: "paper-capture" });
  await f.frame("Open_Palm", .64); await f.frame("Open_Palm", .64);
  assert.equal(f.requests.length, 0);
  await f.frame("Open_Palm", .64);
  const data = JSON.parse(JSON.parse(f.requests[0].body).payload);
  assert.equal(data.sign, "paper"); assert.equal(data.confidence, .64);
  assert.equal(data.stabilityFrames, 3); assert.equal(data.captureId, "paper-capture");
});

test("generic finger readings use their own timestamped scoped event and publish no named sign", async () => {
  const f = fixture({ poseSupported: true });
  f.element("emit_interval_ms").value = "2500";
  await f.poseFrame(); await f.poseFrame(); assert.equal(f.requests.length, 0);
  await f.poseFrame();
  const request = f.requests[0], event = JSON.parse(request.body), data = JSON.parse(event.payload);
  assert.equal(event.type, "obs.hand.pose"); assert.equal(event.actor, "user");
  assert.equal(request.headers.get("X-Prometheus-Access-Code"), "code");
  assert.equal(data.hands[0].fingers.index, .5); assert.equal(data.hands[0].fingers.thumb, 0);
  assert.deepEqual(data.hands[0].fingerStates, {
    thumb: "straight", index: "neutral", middle: "straight", ring: "straight", little: "straight",
  });
  assert.equal(data.hands[0].handedness, "right"); assert.equal(data.recognizer, "mediapipe");
  assert.equal(data.sign, undefined); assert.equal(data.confidence, undefined); assert.equal(data.captureId, undefined);
  assert.equal(Date.parse(data.ts), f.context.Date.now() - 2640);
  await f.poseFrame(); assert.equal(f.requests.length, 1, "continuous measurements obey the emission interval");
  f.advance(2501);
  const empty = { landmarks: [], worldLandmarks: [] };
  await f.poseFrame(empty); await f.poseFrame(empty); await f.poseFrame(empty);
  assert.equal(JSON.parse(JSON.parse(f.requests[1].body).payload).handCount, 0);
});

test("pose reporting works with emission off or unsupported profiles without sending observations", async () => {
  const f = fixture({ poseSupported: true }); f.element("sensor_emit_enabled").checked = false;
  await f.poseFrame(); await f.poseFrame(); await f.poseFrame();
  assert.equal(f.context.posePayload.hands[0].fingers.index, .5); assert.equal(f.requests.length, 0);
  f.element("sensor_emit_enabled").checked = true; f.context.poseSupported = false;
  await f.poseFrame(); assert.equal(f.requests.length, 0); assert.match(f.poseStatus(), /does not support hand poses/);
});

test("camera reset fences delayed pose inference and acknowledgement", async () => {
  const f = fixture({ poseSupported: true }); let release;
  f.context.camera.handRecognizer.recognizeForVideo = () => new Promise(resolve => { release = resolve; });
  const inference = f.poseFrame();
  f.context.resetCameraEmissionGate(); f.context.setHandSignStatus("Camera stopped");
  release(handResult()); await inference;
  assert.equal(f.context.posePayload, undefined); assert.equal(f.requests.length, 0);
  f.context.camera.handRecognizer.recognizeForVideo = () => f.context.result;
  const original = f.context.fetch;
  f.context.fetch = async (...args) => { await new Promise(resolve => { release = resolve; }); return original(...args); };
  await f.poseFrame(); await f.poseFrame(); const sending = f.poseFrame();
  await Promise.resolve();
  f.context.resetCameraEmissionGate(); f.context.setHandSignStatus("New camera context");
  release(); await sending;
  assert.equal(f.status(), "New camera context"); assert.equal(f.context.camera.lastCameraEmitAt, 0);
  assert.equal(f.context.camera.lastHandPoseEmitAt, 0);
});

test("one MediaPipe inference publishes independent sign and finger events with the same frame timestamp", async () => {
  const f = fixture({ poseSupported: true }); f.element("emit_interval_ms").value = "2500";
  const capturedAt = f.context.captureObservationTime().ts;
  const original = f.context.fetch;
  f.context.fetch = (...args) => {
    if (JSON.parse(args[1].body).type === "obs.hand.sign") f.advance(450);
    return original(...args);
  };
  const result = handResult({ index: .8 });
  result.gestures = [[{ categoryName: "Closed_Fist", score: .95 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.context.recognitions, 3);
  assert.deepEqual(f.requests.map(request => JSON.parse(request.body).type), ["obs.hand.sign", "obs.hand.pose"]);
  const [sign, pose] = f.requests.map(request => JSON.parse(JSON.parse(request.body).payload));
  assert.equal(sign.sign, "rock"); assert.equal(sign.confidence, .95);
  assert.equal(pose.hands[0].fingerStates.index, "bent"); assert.equal(pose.hands[0].fingers.index, .8);
  assert.equal(sign.ts, pose.ts); assert.equal(sign.recognizer, pose.recognizer);
  assert.equal(pose.ts, capturedAt, "both events retain calibrated capture time across delayed sign delivery");
  assert.match(f.status(), /^Emitted /); assert.match(f.poseStatus(), /^Emitted /);
});

test("finger reporting remains local when calibrated timing is unavailable", async () => {
  const f = fixture({ poseSupported: true }); f.context.timingAvailable = false;
  const result = handResult(); result.gestures = [[{ categoryName: "Open_Palm", score: .9 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.ok(f.context.posePayload.hands.length);
  assert.equal(f.requests.length, 0);
  assert.match(f.status(), /timing unavailable/);
  assert.match(f.poseStatus(), /timing unavailable/);
});

test("clock invalidation during sign delivery blocks its companion pose without refreshing the capture time", async () => {
  const f = fixture({ poseSupported: true }), original = f.context.fetch;
  f.context.fetch = (...args) => {
    if (JSON.parse(args[1].body).type === "obs.hand.sign") f.context.timingEpoch++;
    return original(...args);
  };
  const result = handResult(); result.gestures = [[{ categoryName: "Open_Palm", score: .9 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.requests.length, 1);
  assert.equal(JSON.parse(f.requests[0].body).type, "obs.hand.sign");
  assert.equal(f.context.camera.lastHandPoseEmitAt, 0);
  assert.match(f.poseStatus(), /timing unavailable/);
});

test("finger cadence cannot consume a later RPS sign's cooldown, or be consumed by it", async () => {
  const f = fixture({ poseSupported: true }); f.element("emit_interval_ms").value = "5000";
  await f.poseFrame(); await f.poseFrame(); await f.poseFrame();
  assert.equal(f.requests.length, 1);
  const result = handResult(); result.gestures = [[{ categoryName: "Open_Palm", score: .9 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.requests.length, 2, "the preceding pose does not suppress a fresh sign");
  f.advance(2500); await f.poseFrame(result);
  assert.equal(f.requests.length, 3, "signs retain their 2.5-second cooldown");
  f.advance(2500); await f.poseFrame(result);
  const types = f.requests.map(request => JSON.parse(request.body).type);
  assert.equal(types.filter(type => type === "obs.hand.pose").length, 2);
  assert.equal(types.filter(type => type === "obs.hand.sign").length, 3);
});

test("pose-only agents receive finger data even for low-confidence RPS predictions", async () => {
  const f = fixture({ poseSupported: true, signSupported: false });
  const result = handResult({ index: .5 }); result.gestures = [[{ categoryName: "Open_Palm", score: .4 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.requests.length, 1); assert.equal(JSON.parse(f.requests[0].body).type, "obs.hand.pose");
  assert.match(f.status(), /does not support named hand signs/); assert.match(f.poseStatus(), /^Emitted /);
});

test("a failed sign acknowledgement does not suppress finger delivery or claim RPS success", async () => {
  const f = fixture({ poseSupported: true }), original = f.context.fetch;
  f.context.fetch = (...args) => {
    f.context.httpStatus = JSON.parse(args[1].body).type === "obs.hand.sign" ? 500 : 200;
    return original(...args);
  };
  const result = handResult(); result.gestures = [[{ categoryName: "Open_Palm", score: .9 }]];
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.status(), "Emit failed"); assert.match(f.poseStatus(), /^Emitted /);
  assert.equal(f.context.camera.lastCameraEmitAt, 0); assert.ok(f.context.camera.lastHandPoseEmitAt > 0);
});

test("a capture reset during a sign acknowledgement fences the companion pose from the old frame", async () => {
  const f = fixture({ poseSupported: true }), original = f.context.fetch;
  let release;
  f.context.updateHandCapture({ awaitingUserSign: true, captureId: "old" });
  f.context.fetch = async (...args) => {
    await new Promise(resolve => { release = resolve; });
    return original(...args);
  };
  const result = handResult(); result.gestures = [[{ categoryName: "Open_Palm", score: .9 }]];
  await f.poseFrame(result); await f.poseFrame(result); const pending = f.poseFrame(result);
  await Promise.resolve();
  f.context.updateHandCapture({ awaitingUserSign: true, captureId: "new" });
  release(); await pending;
  assert.equal(f.requests.length, 1); assert.equal(JSON.parse(JSON.parse(f.requests[0].body).payload).captureId, "old");
  assert.equal(f.context.camera.lastHandPoseEmitAt, 0);
  f.context.fetch = original;
  await f.poseFrame(result); await f.poseFrame(result); await f.poseFrame(result);
  assert.equal(f.requests.length, 3);
  assert.equal(JSON.parse(JSON.parse(f.requests[1].body).payload).captureId, "new");
  assert.equal(JSON.parse(f.requests[2].body).type, "obs.hand.pose");
});

test("a new spoken countdown closes leftover capture so ordinary sensing cannot submit the old capture ID", async () => {
  const f = fixture();
  f.context.updateHandCapture({awaitingUserSign: true, captureId: "old"});
  f.context.updateHandCapture({awaitingUserSign: false, phase: "spoken_countdown", agentSign: "rock"});
  assert.equal(f.context.camera.handCaptureOpen, false);
  await f.frame(); await f.frame(); await f.frame(); assert.equal(f.requests.length, 1);
  assert.equal(JSON.parse(JSON.parse(f.requests[0].body).payload).captureId, undefined);
  f.context.updateHandCapture({awaitingUserSign: true, captureId: "new"});
  await f.frame(); await f.frame(); await f.frame(); assert.equal(f.requests.length, 2);
});

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
    assert.equal(Date.parse(payload.ts), f.context.Date.now() - 2640);
  }
  assert.equal(f.requests.length, 3);
});

const opening = captureId => ({ motion: { handSign: "rock" }, display: { awaitingUserSign: true, captureId } });
const payload = request => JSON.parse(JSON.parse(request.body).payload);

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
  await Promise.resolve(); // Async recognition completes before this test delays the HTTP acknowledgement.
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

test("low-confidence frames cannot count toward a later confident sign", async () => {
  const f = fixture();
  await f.frame("Open_Palm", .4); await f.frame("Open_Palm", .4);
  await f.frame("Open_Palm", .9);
  assert.equal(f.context.camera.stableGestureCount, 1);
  assert.equal(f.requests.length, 0);
  await f.frame(); await f.frame();
  assert.equal(f.requests.length, 1);
});

test("an inference finishing after capture reset cannot contribute stale frames or send a sign", async () => {
  const f = fixture(); let finish;
  f.context.camera.handRecognizer.recognizeForVideo = () => new Promise(resolve => { finish = resolve; });
  const pending = f.frame();
  f.context.updateHandCapture({ awaitingUserSign: true, captureId: "new" });
  finish({ gestures: [[{ categoryName: "Open_Palm", score: .99 }]], landmarks: [] });
  await pending;
  assert.equal(f.context.camera.stableGestureCount, 0);
  assert.equal(f.requests.length, 0);
  assert.equal(f.context.camera.handInferencePending, false);
});

test("cancelled old inference cannot overwrite a newly selected model's readiness or report", async () => {
  const f = fixture(); let reject;
  f.context.camera.handRecognizer.recognizeForVideo = () => new Promise((_, fail) => { reject = fail; });
  const pending = f.frame();
  f.context.resetCameraEmissionGate();
  f.context.camera.handDetectorReady = true;
  f.context.setHandSignStatus("New model ready");
  reject(new Error("Old worker closed")); await pending;
  assert.equal(f.status(), "New model ready");
  assert.equal(f.context.camera.handDetectorReady, true);
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

test("client tuning alone decides whether a stable .757 scissors observation is sent", async () => {
  const f = fixture(); f.element("hand_confidence_threshold").value = "0.8";
  for (let frame = 0; frame < 3; frame++) await f.frame("Victory", .757);
  assert.equal(f.requests.length, 0);
  f.element("hand_confidence_threshold").value = "0.65";
  await f.frame("Victory", .757); await f.frame("Victory", .757);
  assert.equal(f.requests.length, 0, "the new client threshold still requires three confident frames");
  await f.frame("Victory", .757);
  assert.equal(f.requests.length, 1);
  assert.equal(payload(f.requests[0]).sign, "scissor");
  assert.equal(payload(f.requests[0]).confidence, .757);
  await f.frame("Victory", .757);
  assert.equal(f.requests.length, 1, "ordinary client cooldown still applies");
});

test("confidence note distinguishes proposal checks, execution overrides and client defaults", () => {
  const f = fixture();
  const rule = minimum => ({ eventType: "obs.hand.sign", value: "scissor", minConfidence: minimum });
  const show = entries => {
    f.context.state.storage = Object.entries(entries).map(([key, value]) => ({key, value: JSON.stringify(value)}));
    f.context.renderHandConfidencePolicy(); return f.element("hand_confidence_policy").textContent;
  };
  assert.match(show({"task.spec": {rules: [rule(.8)]}}), /Saved task adds hand minimum: scissor ≥ 0.80 \(execution\)/);
  assert.match(show({"task.spec": {rules: [rule(.8)]}, "coordination.draft": {checks: [rule(.9)], reactions: {rules: [rule(0)]}}}),
    /Proposed task adds hand minimum: scissor ≥ 0.90 \(preparation\)/);
  assert.match(show({"coordination.draft": {checks: [rule(0)], reactions: {rules: [rule(0)]}}}), /no additional minimum/);
  assert.match(show({"task.draft": {rules: [rule(0)]}, "task.spec": {rules: [rule(.8)]}}), /Proposed task uses client/);
  assert.doesNotMatch(show({"coordination.agreement": {reactions: {rules: [rule(.8)]}}}), /0.80/,
    "an archived agreement in Conversation must not appear active");
  assert.doesNotMatch(show({}), /0.80|0.90|Proposed|Saved/);
  f.context.state.storage = [{key: "task.spec", value: "broken"}];
  f.context.renderHandConfidencePolicy();
  assert.match(f.element("hand_confidence_policy").textContent, /unavailable/);
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
  await Promise.resolve();
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


test("hand capture time survives delayed submission and unavailable or old-scope timing blocks sending", async () => {
  const f = fixture(), captured = f.context.captureObservationTime();
  f.advance(4000);
  await f.context.submitHandSign("scissor", {observed: captured});
  assert.equal(payload(f.requests[0]).ts, captured.ts);
  f.context.timingEpoch++;
  assert.equal(await f.context.submitHandSign("scissor", {observed: captured}), false);
  f.context.timingAvailable = false;
  await f.frame(); await f.frame(); await f.frame();
  assert.equal(f.requests.length, 1);
  assert.match(f.status(), /timing unavailable/);
});
