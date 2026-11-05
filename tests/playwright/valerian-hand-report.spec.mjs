import { test, expect } from "./fixtures/offline-ui-assets.mjs";

const ids = ["71717171-7171-4171-8171-717171717171", "72727272-7272-4272-8272-727272727272"];

async function setup(page, context, theme = "light") {
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.addInitScript(theme => {
    localStorage.setItem("prometheus.valerian.theme", theme);
    window.__streams = [];
    window.EventSource = class extends EventTarget {
      constructor() { super(); this.readyState = 1; window.__streams.push(this); }
      close() { this.readyState = 2; }
    };
    window.__cameraStarts = 0;
    navigator.mediaDevices.getUserMedia = async () => {
      window.__cameraStarts++;
      const canvas = document.createElement("canvas");
      canvas.width = 640; canvas.height = 480;
      const ctx = canvas.getContext("2d");
      let frame = 0;
      const draw = () => {
        ctx.fillStyle = "#20494e"; ctx.fillRect(0, 0, 640, 480);
        ctx.fillStyle = "#e8f2e9"; ctx.font = "28px sans-serif";
        ctx.fillText("Simulated camera", 200, 220);
        ctx.fillRect(30 + (++frame % 200), 280, 40, 40);
      };
      draw();
      setInterval(draw, 80);
      return canvas.captureStream(15);
    };
  }, theme);
  const agents = ids.map((id, i) => ({ id, name: i ? "Speech only" : "Hand sign trial", active: true, languageCode: "en",
    interactionProfile: { supportedObservations: i ? ["obs.user_utterance"] : ["obs.user_utterance", "obs.hand.sign"],
      supportedBehaviourModalities: ["speech"], profileTags: [] } }));
  await context.route("**/demo/**", route => {
    const path = new URL(route.request().url()).pathname;
    let data;
    if (path === "/demo/session") data = { accessCode: "HAND", agentTypes: [], agents };
    else if (path.endsWith("/info")) data = agents.find(a => path.includes(a.id));
    else if (path.endsWith("/state")) data = { name: "Perception", innerNames: [] };
    else if (path.endsWith("/transcription/capabilities")) data = { schemaVersion: 1, sessionType: "transcription",
      model: "gpt-live-transcribe", capabilities: { assistantOutput: false, inputTranscription: true }, settings: [] };
    else if (["/eventhistory", "/storage", "/states"].some(suffix => path.endsWith(suffix))) data = [];
    else return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(data) });
  });
  for (const url of ["https://cdn.jsdelivr.net/npm/@tensorflow*/**", "https://cdn.jsdelivr.net/npm/face-api.js*/**"]) {
    await context.route(url, route => route.fulfill({ body: "", contentType: "application/javascript" }));
  }
  await page.goto(`/valerian/?agentId=${ids[0]}`);
  await page.getByTestId("access-code-input").fill("HAND");
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${ids[0]}`);
  // Exercise the ordinary camera loop with deterministic detections, without model downloads or hardware.
  await page.evaluate(async () => {
    window.__gesture = "Closed_Fist";
    camera.handPoseEstimator = (await import("/valerian/hand/finger-pose.js")).estimateHandPoses;
    camera.handDetectorReady = true;
    camera.handRecognizer = { recognizeForVideo: () => ({ gestures: [[{ categoryName: window.__gesture, score: .93 }]] }) };
  });
  await page.getByTestId("sensor-hand-enabled").check();
  await page.locator("#start_camera").click();
  await expect(page.getByTestId("camera-status")).toHaveText("Camera Live");
  await page.locator('[data-bs-target="#sensed_signals"]').click();
  await expect(page.getByTestId("hand-report-canned")).toHaveText("Closed_Fist");
  await page.evaluate(() => {
    window.__originalReport = document.getElementById("hand_report");
    window.__originalVideo = camera.video;
    window.__originalStream = camera.stream;
    window.__streamCount = window.__streams.length;
  });
  return errors;
}

async function expand(page) {
  await page.getByTestId("expand-hand-report").click();
  await expect(page.getByTestId("hand-sign-report")).toHaveAttribute("data-hand-report-mode", "floating");
}

async function expectCameraAlongsideReport(page) {
  const camera = await page.getByTestId("camera-video").boundingBox();
  const report = await page.getByTestId("hand-sign-report").boundingBox();
  const viewport = page.viewportSize();
  for (const box of [camera, report]) {
    expect(box).not.toBeNull();
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
    expect(box.y + box.height).toBeLessThanOrEqual(viewport.height);
  }
  expect(camera.x + camera.width <= report.x || report.x + report.width <= camera.x ||
    camera.y + camera.height <= report.y || report.y + report.height <= camera.y).toBe(true);
}

async function expectLiveViewPreserved(page) {
  expect(await page.evaluate(() => ({
    report: document.getElementById("hand_report") === window.__originalReport,
    video: camera.video === window.__originalVideo,
    stream: camera.video.srcObject === window.__originalStream,
    track: camera.stream.getVideoTracks()[0].readyState,
    cameraStarts: window.__cameraStarts,
    extraStreams: window.__streams.length - window.__streamCount,
  }))).toEqual({ report: true, video: true, stream: true, track: "live", cameraStarts: 1, extraStreams: 0 });
  await expect(page.getByTestId("hand-sign-report")).toHaveCount(1);
}

for (const theme of ["light", "dark"]) {
  test(`hand report expands beside the live camera, moves, resizes and restores in ${theme} mode`, async ({ page, context }, info) => {
    const errors = await setup(page, context, theme);
    await expand(page);
    const report = page.getByTestId("hand-sign-report");
    await expect(report).toHaveAttribute("aria-modal", "false");
    await expectCameraAlongsideReport(page);
    await page.evaluate(() => { window.__gesture = "Open_Palm"; });
    await expect(page.getByTestId("hand-report-canned")).toHaveText("Open_Palm");
    await expect(page.getByTestId("hand-report-confidence")).toHaveText("93%");
    await expectLiveViewPreserved(page);
    await page.screenshot({ path: info.outputPath(`camera-and-report-${theme}.png`) });

    const before = await report.boundingBox();
    const handle = page.locator("[data-hand-report-drag]");
    const header = await handle.boundingBox();
    await page.mouse.move(header.x + 20, header.y + 10);
    await page.mouse.down();
    await page.mouse.move(header.x - 100, header.y + 80, { steps: 5 });
    await page.mouse.up();
    expect((await report.boundingBox()).x).toBeLessThan(before.x - 100);
    await handle.focus();
    const moved = await report.boundingBox();
    await page.keyboard.press("ArrowRight");
    expect((await report.boundingBox()).x).toBe(moved.x + 20);

    const resize = await report.boundingBox();
    await page.mouse.move(resize.x + resize.width - 3, resize.y + resize.height - 3);
    await page.mouse.down();
    await page.mouse.move(resize.x + resize.width - 73, resize.y + resize.height - 53, { steps: 5 });
    await page.mouse.up();
    expect((await report.boundingBox()).width).toBeLessThan(resize.width - 50);
    await page.getByTestId("text-input").fill("Camera and report remain interactive.");
    await page.getByTestId("restore-hand-report").click();
    await expect(report).toHaveAttribute("data-hand-report-mode", "docked");
    await expect(page.locator("#hand_report_home > #hand_report")).toBeVisible();
    await expect(page.getByTestId("expand-hand-report")).toBeFocused();
    await expect(page.getByTestId("text-input")).toHaveValue("Camera and report remain interactive.");
    expect(await report.evaluate(element => element.style.width)).toBe("");
    await expectLiveViewPreserved(page);
    expect(errors).toEqual([]);
  });
}

for (const [device, viewport] of [["mobile", { width: 390, height: 844 }], ["tablet", { width: 820, height: 1000 }]]) {
  test(`${device} report leaves the camera visible and stays within the viewport after resizing`, async ({ page, context }, info) => {
    await page.setViewportSize(viewport);
    const errors = await setup(page, context, "dark");
    await expand(page);
    await expectCameraAlongsideReport(page);
    await page.screenshot({ path: info.outputPath(`camera-and-report-${device}.png`) });
    await page.setViewportSize({ width: 360, height: 640 });
    const report = page.getByTestId("hand-sign-report");
    await expect.poll(async () => {
      const box = await report.boundingBox();
      return box.x + box.width <= 360 && box.y + box.height <= 640;
    }).toBe(true);
    await expectCameraAlongsideReport(page);
    await page.locator("[data-hand-report-drag]").focus();
    await page.keyboard.press("Escape");
    await expect(report).toHaveAttribute("data-hand-report-mode", "docked");
    await expect(page.getByTestId("expand-hand-report")).toBeFocused();
    await expectLiveViewPreserved(page);
    expect(errors).toEqual([]);
  });
}

test("report can expand from the Sensing modal and returns home on profile change, disconnect and logout", async ({ page, context }) => {
  const errors = await setup(page, context);
  const report = page.getByTestId("hand-sign-report");
  await page.getByTestId("maximize-sensing-column").click();
  await expect(page.getByTestId("column-expansion-modal")).toBeVisible();
  await expand(page);
  await expect(page.getByTestId("column-expansion-modal")).toBeHidden();
  await expectCameraAlongsideReport(page);
  await expectLiveViewPreserved(page);
  await page.evaluate(() => applyInteractionProfile({ supportedObservations: ["obs.user_utterance"], supportedBehaviourModalities: ["speech"] }));
  await expect(report).toHaveAttribute("data-hand-report-mode", "docked");
  await expect(report).toBeHidden();
  await page.evaluate(() => applyInteractionProfile({ supportedObservations: ["obs.hand.sign"], supportedBehaviourModalities: ["speech"] }));
  await expand(page);
  await page.evaluate(() => disconnectAgent({ preserveInput: true, message: "Disconnected." }));
  await expect(report).toHaveAttribute("data-hand-report-mode", "docked");
  await expect(page.getByTestId("hand-report-label")).toHaveText("-");
  await page.evaluate(id => connectToAgent(id), ids[0]);
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${ids[0]}`);
  await expand(page);
  await page.getByTestId("clear-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeHidden();
  await expect(page.locator("#hand_report_home > #hand_report")).toBeHidden();
  await expect(report).toHaveAttribute("data-hand-report-mode", "docked");
  expect(errors).toEqual([]);
});
