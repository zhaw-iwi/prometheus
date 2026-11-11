import { test, expect } from "./fixtures/offline-ui-assets.mjs";
import { handResult } from "../fixtures/hand-landmarks.mjs";

const id = "71717171-7171-4171-8171-717171717171";
const right = handResult({ thumb: .2, index: .4, middle: .6, ring: .8, little: 1 }, "Right");
const left = handResult({}, "Left");
left.landmarks[0][12].x = 1.1;
left.landmarks[0][16] = null;
const twoHands = Object.fromEntries(Object.keys(right).map(key => [key, [...right[key], ...left[key]]]));
twoHands.gestures = [[{ categoryName: "Open_Palm", score: .8 }], [{ categoryName: "Closed_Fist", score: .95 }]];

async function setup(page, context, { supportsPose = true, supportsSign = true, history = [] } = {}) {
  const errors = [], requests = [];
  page.on("pageerror", error => errors.push(error.message));
  await context.route("**/runtime/clock", route => route.fulfill({
    contentType: "application/json", body: JSON.stringify({ serverTimeMs: Date.now() }),
  }));
  await page.addInitScript(() => {
    window.EventSource = class extends EventTarget {
      constructor() { super(); this.readyState = 1; }
      close() { this.readyState = 2; }
    };
    navigator.mediaDevices.getUserMedia = async () => {
      const canvas = document.createElement("canvas"); canvas.width = 640; canvas.height = 480;
      const ctx = canvas.getContext("2d");
      const draw = () => { ctx.fillStyle = "#20494e"; ctx.fillRect(0, 0, 640, 480); };
      draw(); setInterval(draw, 50);
      return canvas.captureStream(15);
    };
  });
  const agent = { id, name: "Finger pose trial", active: true, languageCode: "en", interactionProfile: {
    supportedObservations: [...(supportsSign ? ["obs.hand.sign"] : []), ...(supportsPose ? ["obs.hand.pose"] : [])],
    supportedBehaviourModalities: ["speech"], profileTags: [] } };
  await context.route("**/demo/**", route => {
    const path = new URL(route.request().url()).pathname;
    let data;
    if (path === "/demo/session") data = { accessCode: "HAND", agentTypes: [], agents: [agent] };
    else if (path.endsWith("/info")) data = agent;
    else if (path.endsWith("/state")) data = { name: "Perception", innerNames: [] };
    else if (path.endsWith("/transcription/capabilities")) data = { schemaVersion: 1, sessionType: "transcription",
      model: "gpt-live-transcribe", capabilities: { assistantOutput: false, inputTranscription: true }, settings: [] };
    else if (path.endsWith("/acknowledge")) { requests.push(route.request().postDataJSON()); data = { active: true, responseEvent: null }; }
    else if (path.endsWith("/eventhistory")) data = history;
    else if (["/storage", "/states"].some(suffix => path.endsWith(suffix))) data = [];
    else return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(data) });
  });
  await context.route("https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35", route => route.fulfill({
    contentType: "application/javascript", headers: { "Access-Control-Allow-Origin": "*" }, body: `
      export const FilesetResolver = { forVisionTasks: async () => ({}) };
      export const GestureRecognizer = { createFromOptions: async (_, options) => {
        window.__handOptions = options;
        window.__handCreates = (window.__handCreates || 0) + 1;
        return { recognizeForVideo: () => { window.__handInferences = (window.__handInferences || 0) + 1;
          return window.__poseResult || { landmarks: [], worldLandmarks: [] }; },
          close() { window.__handClosed = true; } };
      } };
    `,
  }));
  for (const url of ["https://cdn.jsdelivr.net/npm/@tensorflow*/**", "https://cdn.jsdelivr.net/npm/face-api.js*/**"]) {
    await context.route(url, route => route.fulfill({ body: "", contentType: "application/javascript" }));
  }
  await page.goto(`/valerian/?agentId=${id}`);
  await page.getByTestId("access-code-input").fill("HAND");
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${id}`);
  await expect(page.locator("#observation_clock_status")).toHaveText("Sensor timing ready");
  return { errors, requests };
}

async function openSection(page, id) {
  await expect(page.locator("#sensing_accordion .collapsing")).toHaveCount(0);
  const section = page.locator(`#${id}`);
  if (!(await section.evaluate(element => element.classList.contains("show")))) {
    await page.locator(`[data-bs-target="#${id}"]`).click();
  }
  await expect(section).toHaveClass(/\bshow\b/);
}

async function start(page, result = twoHands) {
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-hand-enabled").check();
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Model ready");
  await page.evaluate(result => { window.__poseResult = result; }, result);
  await page.locator("#start_camera").click();
  await openSection(page, "sensed_signals");
  await expect(page.getByTestId("hand-pose-report")).toBeVisible();
}

test("MediaPipe starts automatically and shows RPS and both hands with both scoped observations", async ({ page, context }, info) => {
  const { errors, requests } = await setup(page, context);
  await start(page);
  expect(await page.evaluate(() => window.__handOptions.numHands)).toBe(2);
  expect(await page.evaluate(() => window.__handCreates)).toBe(1);
  await expect(page.getByTestId("hand-model-select")).toHaveCount(0);
  const values = page.getByTestId("hand-pose-hands").locator("output");
  await expect(values).toHaveText(["0.200", "0.400", "0.600", "0.800", "1.000", "0.000", "0.000", "—", "—", "0.000"]);
  await expect(page.getByTestId("hand-pose-hands").locator("[data-finger-state]")).toHaveText([
    "Straight", "Neutral", "Bent", "Bent", "Bent", "Straight", "Straight", "Unavailable", "Unavailable", "Straight",
  ]);
  await expect(page.locator("#hand_sign_report_details")).toBeVisible();
  await expect(page.getByTestId("hand-report-label")).toHaveText("Stein");
  await expect(page.getByTestId("hand-confidence-threshold")).toBeEnabled();
  expect(requests).toHaveLength(0);
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-emit-enabled").check();
  await openSection(page, "sensed_signals");
  await expect.poll(() => requests.some(event => event.type === "obs.hand.pose") && requests.some(event => event.type === "obs.hand.sign")).toBe(true);
  const sign = JSON.parse(requests.find(event => event.type === "obs.hand.sign").payload);
  const payload = JSON.parse(requests.find(event => event.type === "obs.hand.pose").payload);
  expect(sign).toMatchObject({ sign: "rock", confidence: .95, recognizer: "mediapipe" });
  expect(payload.recognizer).toBe("mediapipe"); expect(payload.ts).toBe(sign.ts);
  expect(payload.handCount).toBe(2); expect(payload.hands[0].fingers.index).toBe(.4);
  expect(payload.hands[1].fingers.middle).toBeNull(); expect(payload.sign).toBeUndefined();
  expect(payload.hands[0].fingerStates).toEqual({ thumb: "straight", index: "neutral", middle: "bent", ring: "bent", little: "bent" });
  expect(payload.hands[1].fingerStates.middle).toBeNull();
  await expect(page.locator("#hand_sign_report_details")).toBeVisible();
  await expect(page.getByTestId("hand-pose-report")).toBeVisible();
  await expect(page.getByTestId("hand-sign-status")).toContainText("Emitted");
  await expect(page.getByTestId("hand-pose-status")).toContainText("Emitted");
  await page.getByTestId("expand-hand-report").click();
  await page.getByTestId("hand-sign-report").screenshot({ path: info.outputPath("finger-pose-desktop.png") });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByTestId("hand-sign-report").screenshot({ path: info.outputPath("finger-pose-mobile.png") });
  await page.evaluate(() => { window.__poseResult = { landmarks: [], worldLandmarks: [] }; });
  await expect(page.getByTestId("hand-pose-hands")).toHaveText("No hands detected");
  await expect.poll(() => requests.some(event => event.type === "obs.hand.pose" && JSON.parse(event.payload).handCount === 0)).toBe(true);
  await page.evaluate(() => stopCamera());
  await expect(page.getByTestId("hand-pose-report")).toBeHidden();
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Camera stopped");
  expect(errors).toEqual([]);
});

test("combined MediaPipe selects the highest-confidence RPS sign from two hands for each capture", async ({ page, context }) => {
  const { errors, requests } = await setup(page, context);
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-hand-enabled").check();
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Model ready");
  expect(await page.evaluate(() => window.__handOptions.numHands)).toBe(2);
  await page.evaluate(() => {
    window.__poseResult = { gestures: [
      [{ categoryName: "Open_Palm", score: .8 }], [{ categoryName: "Closed_Fist", score: .95 }],
    ], landmarks: [] };
    updateHandCapture({ awaitingUserSign: true, captureId: "two-hands-first" });
  });
  await page.getByTestId("sensor-emit-enabled").check();
  await page.locator("#start_camera").click();
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  expect(requests[0].type).toBe("obs.hand.sign");
  expect(JSON.parse(requests[0].payload)).toMatchObject({
    sign: "rock", confidence: .95, captureId: "two-hands-first", stabilityFrames: 3,
  });
  await page.evaluate(() => {
    window.__poseResult.gestures[0][0].score = .99;
    updateHandCapture({ awaitingUserSign: true, captureId: "two-hands-second" });
  });
  await expect.poll(() => requests.some(event => JSON.parse(event.payload).captureId === "two-hands-second")).toBe(true);
  const second = requests.find(event => JSON.parse(event.payload).captureId === "two-hands-second");
  expect(JSON.parse(second.payload)).toMatchObject({
    sign: "paper", confidence: .99, captureId: "two-hands-second", stabilityFrames: 3,
  });
  expect(errors).toEqual([]);
});

test("older sign-only profiles can inspect bend but do not receive pose events", async ({ page, context }) => {
  const { requests, errors } = await setup(page, context, { supportsPose: false });
  await start(page);
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-emit-enabled").check();
  await openSection(page, "sensed_signals");
  await expect(page.getByTestId("hand-pose-status")).toContainText("does not support hand poses");
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  expect(requests.every(event => event.type === "obs.hand.sign")).toBe(true);
  await expect(page.getByTestId("hand-sign-status")).toContainText("Emitted");
  await expect(page.getByTestId("hand-pose-hands").locator("output")).toHaveCount(10);
  await page.locator("#stop_camera").click();
  await expect(page.getByTestId("hand-confidence-threshold")).toBeEnabled();
  expect(errors).toEqual([]);
});

test("pose-only profiles receive finger readings while RPS remains visible locally", async ({ page, context }) => {
  const { requests, errors } = await setup(page, context, { supportsSign: false });
  await start(page);
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-emit-enabled").check();
  await openSection(page, "sensed_signals");
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  expect(requests.every(event => event.type === "obs.hand.pose")).toBe(true);
  await expect(page.getByTestId("hand-sign-status")).toContainText("does not support named hand signs");
  await expect(page.getByTestId("hand-report-label")).toHaveText("Stein");
  await expect(page.getByTestId("hand-pose-status")).toContainText("Emitted");
  expect(errors).toEqual([]);
});

test("finger readings remain available when MediaPipe recognizes no RPS sign", async ({ page, context }) => {
  const { requests, errors } = await setup(page, context);
  await start(page, { ...twoHands, gestures: [[{ categoryName: "None", score: .99 }]] });
  await openSection(page, "sensor_toggles");
  await page.getByTestId("sensor-emit-enabled").check();
  await openSection(page, "sensed_signals");
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  expect(requests.every(event => event.type === "obs.hand.pose")).toBe(true);
  await expect(page.getByTestId("hand-report-label")).toHaveText("-");
  await expect(page.getByTestId("hand-pose-hands").locator('[data-finger="index"]').first()).toHaveText("0.400");
  await expect(page.getByTestId("hand-pose-status")).toContainText("Emitted");
  expect(errors).toEqual([]);
});

test("saved pose history renders through the ordinary report and clears with its agent context", async ({ page, context }) => {
  const { errors } = await setup(page, context, { history: [{ type: "obs.hand.pose", actor: "user", kind: "observation",
    payload: JSON.stringify({ source: "valerian.hand.camera", recognizer: "mediapipe-fingers", hands: [{ handedness: "left", fingers: { index: .75 } }] }) }] });
  await openSection(page, "sensed_signals");
  await expect(page.getByTestId("hand-pose-hands").locator('[data-finger="index"]')).toHaveText("0.750");
  await expect(page.getByTestId("hand-pose-hands").locator('[data-finger-state="index"]')).toHaveText("Unclassified");
  await expect(page.getByTestId("hand-sign-status")).toHaveText("From history");
  await expect(page.getByTestId("hand-report-detector")).toHaveText("MediaPipe Finger Bend (legacy)");
  await page.evaluate(() => applyInteractionProfile({ supportedObservations: ["obs.user_utterance"], supportedBehaviourModalities: ["speech"] }));
  await expect(page.getByTestId("hand-sign-report")).toBeHidden();
  await expect(page.getByTestId("hand-pose-hands")).toBeEmpty();
  expect(errors).toEqual([]);
});
