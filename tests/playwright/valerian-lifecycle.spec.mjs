import { expect, test } from "./fixtures/offline-ui-assets.mjs";

const ACCESS_CODE = "LIFE1";
const AGENT_ID = "71717171-7171-4171-8171-717171717171";
const AGENT_KEY = "core.multimodal_behaviour";
const AGENT = {
  id: AGENT_ID,
  name: "Valerian Lifecycle Agent",
  description: "Deterministic lifecycle hydration fixture.",
  active: true,
  languageCode: "en",
  interactionProfile: {
    supportedObservations: [
      "obs.user_utterance",
      "obs.emotion.face",
      "obs.social.context",
      "obs.hand.sign",
      "obs.weather.current",
      "obs.weather.forecast",
    ],
    supportedBehaviourModalities: ["speech", "nonVerbal.gesture", "display"],
    profileTags: [],
  },
};
const STARTER_BEHAVIOUR = behaviourEvent("Welcome from the persisted starter.", "2026-08-29T08:00:00Z");
const USED_HISTORY = [
  event("obs.user_utterance", "user", "observation", "What happened earlier?", "2026-08-29T08:01:00Z"),
  event("obs.emotion.face", "user", "observation", JSON.stringify({
    emotion: "happy",
    confidence: 0.9,
    valence: 0.8,
    arousal: 0.5,
    faceDetectionConfidence: 0.95,
    expressions: { happy: 0.9, neutral: 0.1 },
  }), "2026-08-29T08:01:01Z"),
  event("obs.social.context", "user", "observation", JSON.stringify({
    humanCount: 2,
    groupCount: 1,
    singletonCount: 0,
    largestGroupSize: 2,
    groups: [{ memberIds: [1, 2] }],
    people: [
      { id: 1, detectionConfidence: 0.9, movement: { state: "stationary", confidence: 0.8 },
        attention: { state: "attending", confidence: 0.85, personVisible: true, faceVisible: true,
          nearFrontal: true, centered: true, frontalCentered: true } },
      { id: 2, detectionConfidence: 0.8, movement: { state: "moving", confidence: 0.7 },
        attention: { state: "unknown", confidence: 0.2, personVisible: true, faceVisible: false,
          nearFrontal: false, centered: false, frontalCentered: false } },
    ],
  }), "2026-08-29T08:01:02Z"),
  event("obs.hand.sign", "user", "observation", JSON.stringify({
    sign: "paper", source: "valerian.hand.camera", detectionMode: "client_camera", confidence: 0.88,
  }), "2026-08-29T08:01:03Z"),
  event("obs.weather.forecast", "user", "observation", JSON.stringify({
    kind: "forecast",
    location_label: "Doha, Qatar",
    days: [{ date: "2026-08-29", condition: "clear", temperature_min_c: 30, temperature_max_c: 39 }],
  }), "2026-08-29T08:01:04Z"),
  behaviourEvent("This conversation and sensing state came from history.", "2026-08-29T08:01:05Z"),
];

async function stableRecoveryStreams(context) {
  await context.addInitScript(() => {
    window.EventSource = class {
      static OPEN = 1; static CLOSED = 2;
      constructor(url) { this.url = url; this.readyState = 1; this.listeners = {}; setTimeout(() => this.listeners.open?.({}), 0); }
      addEventListener(name, listener) { this.listeners[name] = listener; }
      close() { this.readyState = 2; }
    };
  });
}

for (const layout of [{ name: "desktop", width: 1440, height: 1000 }, { name: "narrow", width: 390, height: 844 }]) {
  test(`service failure preserves history without replay and allows read-only recovery ${layout.name}`, async ({page, context}, testInfo) => {
    await stableRecoveryStreams(context);
    await page.setViewportSize({width: layout.width, height: layout.height});
    await context.addInitScript(() => localStorage.setItem("prometheus.valerian.theme", "dark"));
    await installLifecycleApiMocks(context, {created: true, history: [STARTER_BEHAVIOUR]});
    let mutations = 0;
    await context.route(`**/demo/agents/${AGENT_ID}/acknowledge*`, route => {
      ++mutations; return route.fulfill({status: 503, contentType: "application/json", body: "{}"});
    });
    await page.goto(`/valerian/?agentId=${AGENT_ID}`); await enterAccessCode(page);
    await page.getByTestId("text-input").fill("Help me understand what happened"); await page.getByTestId("send-text").click();
    const notice = page.getByTestId("service-recovery");
    await expect(notice).toBeVisible(); await expect(notice).toContainText("last known");
    // A delayed snapshot from the old stream is not proof of a recovered connection or mutation outcome.
    await page.evaluate(() => applyMonitorSnapshot({stateName: "Conversation", storage: []}));
    await expect(notice).toHaveAttribute("data-state", "unavailable");
    await expect(page.getByTestId("message-list")).toContainText("persisted starter");
    await notice.scrollIntoViewIfNeeded(); await notice.screenshot({path: testInfo.outputPath(`service-unavailable-${layout.name}.png`)});
    await page.getByTestId("service-recovery-check").click();
    await expect(notice).toHaveAttribute("data-state", "review");
    await expect(notice).toContainText("may have committed"); expect(mutations).toBe(1);
    await expect(page.getByTestId("message-list")).toContainText("persisted starter");
    await page.locator("#service_recovery_dismiss").click(); await expect(notice).toBeHidden();
    await page.evaluate(() => state.monitorSource.onerror());
    await expect(notice).toBeVisible(); await expect(notice).toContainText("last known");
    await page.locator("#open_diagnostics").click(); await page.getByTestId("connect-agent").click();
    await expect(notice).toBeHidden(); await assertColumnsEmpty(page); expect(mutations).toBe(1);
  });
}

for (const status of [200, 503]) test(`late mutation reply ${status} cannot revive data or recovery after reconnect`, async ({page, context}) => {
  await stableRecoveryStreams(context);
  await installLifecycleApiMocks(context, {created: true, history: [STARTER_BEHAVIOUR]});
  let release, seen;
  const wait = new Promise(resolve => {release = resolve;}); const started = new Promise(resolve => {seen = resolve;});
  await context.route(`**/demo/agents/${AGENT_ID}/acknowledge*`, async route => {
    seen(); await wait; await route.fulfill(status === 503 ? {status, body: "{}"}
      : json({active: false, responseEvent: behaviourEvent("Old connection response.", "2026-11-10T13:00:00Z")}));
  });
  await page.goto(`/valerian/?agentId=${AGENT_ID}`); await enterAccessCode(page);
  await expect(page.getByTestId("send-text")).toBeEnabled();
  await page.evaluate(() => { window.__lateTurn = sendUserUtterance("Explain this", {renderUser: true}); }); await started;
  await page.locator("#open_diagnostics").click(); await page.getByTestId("connect-agent").click();
  await page.getByTestId("connect-agent").click(); release(); await page.evaluate(() => window.__lateTurn);
  await expect(page.getByTestId("agent-connection-state")).toContainText("Connected");
  await expect(page.getByTestId("service-recovery")).toBeHidden();
  await expect(page.getByTestId("speech-preview")).toHaveText("Welcome from the persisted starter.");
});

test("service login failure does not accuse a valid access code", async ({page, context}) => {
  await installLifecycleApiMocks(context, {created: true, history: []});
  await context.route("**/demo/session", route => route.fulfill({status: 500, body: "{}"}));
  await page.goto("/valerian/"); await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("access-code-status")).toContainText("Service unavailable");
  await expect(page.getByTestId("access-code-status")).not.toContainText("rejected");
  await expect(page.getByTestId("cockpit-shell")).not.toBeVisible();
});

for (const layout of [{ name: "desktop", width: 1440, height: 1000, theme: "light" },
  { name: "narrow", width: 390, height: 844, theme: "dark" }]) {
  test(`Coordination recovery guidance remains readable ${layout.name}`, async ({ page, context }, testInfo) => {
    await page.setViewportSize({ width: layout.width, height: layout.height });
    await context.addInitScript(theme => localStorage.setItem("prometheus.valerian.theme", theme), layout.theme);
    const words = "Current coordination state: READY. The proposal and preparation are ready; no further demonstration is needed. Say Start to accept the saved proposal. After acceptance, show a fresh matching cue. The last update was not applied; the agreement and preparation were preserved.";
    const scenario = { created: false, history: [behaviourEvent(words, "2026-11-10T10:00:00Z")] };
    await installLifecycleApiMocks(context, scenario);
    await page.goto("/valerian/"); await enterAccessCode(page);
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("agent-type-select").selectOption(AGENT_KEY);
    await page.getByTestId("create-agent-instance").click();
    await page.getByTestId("connect-agent").click();
    await page.locator('#diagnostics_drawer [data-bs-dismiss="offcanvas"]').click();
    await expect(page.locator("#diagnostics_drawer")).not.toBeVisible();
    const message = page.getByTestId("message-list").locator(".demo-message.assistant");
    await expect(message).toHaveCount(1); await expect(message).toContainText("no further demonstration");
    await expect(page.getByTestId("speech-preview")).toHaveText(words);
    await message.scrollIntoViewIfNeeded();
    await message.screenshot({ path: testInfo.outputPath(`recovery-${layout.name}.png`) });
    expect(await message.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("connect-agent").click(); await assertColumnsEmpty(page);
  });
}

test("Valerian columns follow access, create, connect, disconnect, and logout lifecycle", async ({ page, context }) => {
  const scenario = { created: false, history: [STARTER_BEHAVIOUR] };
  await installLifecycleApiMocks(context, scenario);

  await page.goto("/valerian/");
  await enterAccessCode(page);
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
  await assertColumnsEmpty(page);

  await page.locator("#open_diagnostics").click();
  await page.getByTestId("agent-type-select").selectOption(AGENT_KEY);
  await page.getByTestId("create-agent-instance").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Selected ${AGENT_ID}`);
  await assertColumnsEmpty(page);

  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${AGENT_ID}`);
  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);
  await expect(page.getByTestId("message-list")).toContainText("Welcome from the persisted starter.");
  await expect(page.getByTestId("speech-preview")).toHaveText("Welcome from the persisted starter.");

  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Selected ${AGENT_ID}`);
  await assertColumnsEmpty(page);

  scenario.history = USED_HISTORY;
  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("message-list").locator(".demo-message.user")).toHaveCount(1);
  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);
  await expect(page.getByTestId("emotion-value")).toHaveText("happy 0.90");
  await expect(page.getByTestId("human-count")).toHaveText("2");
  await expect(page.getByTestId("group-count")).toHaveText("1");
  await expect(page.getByTestId("hand-sign-value")).toContainText("Papier");
  await expect(page.getByTestId("weather-value")).toContainText("Forecast Doha, Qatar");
  await expect(page.getByTestId("speech-preview")).toHaveText("This conversation and sensing state came from history.");

  await page.getByTestId("connect-agent").click();
  await assertColumnsEmpty(page);
  await page.keyboard.press("Escape");
  await page.getByTestId("clear-access-code").click();
  await expect(page.getByTestId("access-screen")).toBeVisible();

  await enterAccessCode(page);
  await expect(page.getByTestId("agent-connection-state")).toHaveText("No agent selected");
  await assertColumnsEmpty(page);
  await expect(page.locator("#activity_log")).toHaveText("");
});

test("Coordination social alternative states the actual count condition", async ({ page, context }, testInfo) => {
  const proposal = "Proposed task: Offer to continue another time once at least two people are visible. Limit: 1 sensor action. Stop can end earlier. The proposal is awaiting your acceptance and is not active.";
  await installLifecycleApiMocks(context, { created: true, stateName: "Coordination reflection", history: [
    behaviourEvent(proposal, "2026-11-10T12:00:00Z"),
    event("obs.human.presence", "camera", "observation", JSON.stringify({humanCount: 2, trackedCount: 2,
      avgDetectionConfidence: .9, ts: new Date().toISOString()}), new Date().toISOString()),
    behaviourEvent("We can continue another time.", new Date().toISOString()),
  ] });
  await page.goto(`/valerian/?agentId=${AGENT_ID}`); await enterAccessCode(page);
  await expect(page.getByTestId("human-count")).toHaveText("2");
  const first = page.getByTestId("message-list").locator(".demo-message.assistant").first();
  await expect(first).toContainText("at least two people");
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Coordination reflection");
  await first.scrollIntoViewIfNeeded(); await first.screenshot({path: testInfo.outputPath("social-condition.png")});
});

test("Coordination dialogue shows agreed limits, question and feedback distinctly", async ({ page, context }, testInfo) => {
  const proposal = "Proposed task: Try one short riddle together. Limit: 2 agent contributions, including questions and feedback. Stop can end earlier. The proposal is awaiting your acceptance and is not active. Shall we start it now?";
  await installLifecycleApiMocks(context, { created: true, stateName: "Coordination reflection", history: [
    behaviourEvent(proposal, "2026-11-10T12:00:00Z"),
    event("obs.user_utterance", "user", "observation", "Start", "2026-11-10T12:00:01Z"),
    behaviourEvent("What becomes shorter while it works?", "2026-11-10T12:00:02Z"),
    event("obs.user_utterance", "user", "observation", "A candle", "2026-11-10T12:00:03Z"),
    behaviourEvent("A candle fits that riddle.", "2026-11-10T12:00:04Z"),
  ] });
  await page.goto(`/valerian/?agentId=${AGENT_ID}`); await enterAccessCode(page);
  const replies = page.getByTestId("message-list").locator(".demo-message.assistant");
  await expect(replies).toHaveCount(3); await expect(replies.first()).toContainText("Limit: 2");
  await expect(replies.nth(1)).not.toContainText("candle"); await expect(replies.nth(2)).toContainText("candle");
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Coordination reflection");
  await replies.first().scrollIntoViewIfNeeded();
  await replies.first().screenshot({ path: testInfo.outputPath("dialogue-limits.png") });
});

test("Coordination perception guidance agrees with sensed input and unchanged status", async ({ page, context }, testInfo) => {
  const words = "When you asked, a fresh supplied hand observation reported scissors (recognition confidence 87%). This is a supplied estimate, not direct access to camera images.";
  await installLifecycleApiMocks(context, { created: true, stateName: "Coordination awaiting acceptance", history: [
    event("obs.hand.sign", "camera", "observation", JSON.stringify({ sign: "scissor", confidence: .868,
      ts: new Date().toISOString() }), new Date().toISOString()),
    behaviourEvent(words, new Date().toISOString()),
  ] });
  await page.goto(`/valerian/?agentId=${AGENT_ID}`); await enterAccessCode(page);
  await expect(page.getByTestId("hand-sign-value")).toContainText(/Schere|scissor/i);
  await expect(page.getByTestId("message-list")).toContainText("scissors");
  await expect(page.getByTestId("speech-preview")).toHaveText(words);
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Coordination awaiting acceptance");
  await page.getByTestId("message-list").locator(".demo-message.assistant").screenshot({ path: testInfo.outputPath("perception-guidance.png") });
});

test("hand confidence requirements follow monitor proposals, activation and disconnect", async ({ page, context }) => {
  await installLifecycleApiMocks(context, { created: true, history: [] });
  await page.goto(`/valerian/?agentId=${AGENT_ID}`);
  await enterAccessCode(page);
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${AGENT_ID}`);
  const note = page.getByTestId("hand-confidence-policy");
  await page.locator('[data-bs-target="#sensor_config"]').click();
  await expect(note).toBeVisible();
  await expect(page.getByTestId("hand-confidence-threshold")).toHaveValue("0.65");
  const snapshot = (key, minimum) => page.evaluate(({key, minimum}) => {
    const rule = {eventType: "obs.hand.sign", value: "scissor", minConfidence: minimum};
    const value = key === "coordination.draft" ? {checks: [rule], reactions: {rules: [rule]}} : {rules: [rule]};
    applyMonitorSnapshot({storage: [{key, value: JSON.stringify(value)}]});
  }, {key, minimum});
  await snapshot("coordination.draft", .8);
  await expect(note).toContainText("Proposed task adds hand minimum: scissor ≥ 0.80 (preparation)");
  await expect(note).toContainText("0.80 (execution)");
  await snapshot("coordination.draft", 0);
  await expect(note).toContainText("Proposed task uses client hand confidence; no additional minimum");
  await snapshot("task.spec", .8);
  await expect(note).toContainText("Saved task adds hand minimum: scissor ≥ 0.80 (execution)");
  await expect(page.getByTestId("hand-confidence-threshold")).toHaveValue("0.65");
  await page.locator("#open_diagnostics").click();
  await page.getByTestId("connect-agent").click();
  await expect(note).toHaveText("Client filters hand confidence and stability. Task rules still apply.");
});

test("camera hand report separates visible detection from acknowledged framework delivery", async ({ page, context }) => {
  const scenario = { created: true, history: [] };
  await installLifecycleApiMocks(context, scenario);
  const observations = [];
  await context.route(`**/demo/agents/${AGENT_ID}/acknowledge`, route => {
    observations.push(route.request().postDataJSON());
    return route.fulfill(json({ active: true, responseEvent: null }));
  });
  await page.goto("/valerian/");
  await enterAccessCode(page);
  await page.locator("#open_diagnostics").click();
  await page.locator("#agent_select").selectOption(AGENT_ID);
  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${AGENT_ID}`);
  await page.keyboard.press("Escape");
  await expect(page.locator("#observation_clock_status")).toHaveText("Sensor timing ready");
  await page.getByTestId("sensor-emit-enabled").check();

  // Feed the real detector callback while keeping media/model downloads offline.
  const frames = (confidence, count) => page.evaluate(async ({ confidence, count }) => {
    camera.video = { readyState: HTMLMediaElement.HAVE_CURRENT_DATA, currentTime: camera.video.currentTime };
    camera.handRecognizer = { recognizeForVideo: () => ({
      gestures: [[{ categoryName: "Open_Palm", score: confidence }]],
      landmarks: [Array.from({ length: 21 }, (_, i) => ({ x: .4 + i / 100, y: .5 }))],
    }) };
    for (let i = 0; i < count; i++) { camera.video.currentTime += .35; await detectHandSign(); }
  }, { confidence, count });
  await frames(.4, 3);
  await expect(page.getByTestId("hand-sign-value")).toContainText("Papier 0.40");
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Not sent: below hand confidence threshold");
  expect(observations).toHaveLength(0);
  await frames(.9, 1);
  expect(observations).toHaveLength(1);
  expect(observations[0].type).toBe("obs.hand.sign");
  expect(JSON.parse(observations[0].payload)).toMatchObject({ sign: "paper", confidence: .9, source: "valerian.hand.camera" });
  await expect(page.getByTestId("hand-sign-status")).toContainText("Emitted");
  await frames(.9, 1);
  expect(observations).toHaveLength(1);
  await expect(page.getByTestId("hand-sign-status")).toContainText("Emitted");
  await page.getByTestId("sensor-emit-enabled").uncheck();
  await frames(.9, 1);
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Not sent: enable Emit camera observations");
  expect(observations).toHaveLength(1);
});

for (const lead of [60_000, -60_000]) {
  test(`observation clock preserves scissors capture with browser wall offset ${lead} ms`, async ({ page, context }) => {
    test.skip(!process.env.PROMETHEUS_LIVE_EXPECT_ENABLED, "Requires the runtime clock in disposable app acceptance");
    await installLifecycleApiMocks(context, { created: true, history: [] }, true);
    const observations = [];
    await context.route(`**/demo/agents/${AGENT_ID}/acknowledge`, route => {
      observations.push({ event: route.request().postDataJSON(), received: Date.now() });
      return route.fulfill(json({ active: true, responseEvent: null }));
    });
    await page.addInitScript(offset => {
      const NativeDate = Date;
      globalThis.Date = class extends NativeDate {
        constructor(...args) { super(...(args.length ? args : [NativeDate.now() + offset])); }
        static now() { return NativeDate.now() + offset; }
      };
    }, lead);
    // The real no-store server endpoint remains unmocked; browser time alone is wrong.
    await page.goto(`/valerian/?agentId=${AGENT_ID}`);
    await enterAccessCode(page);
    await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${AGENT_ID}`);
    await expect(page.locator("#observation_clock_status")).toHaveText("Sensor timing ready");
    await page.getByTestId("sensor-emit-enabled").check();
    const beforeCapture = Date.now();
    await page.evaluate(async () => {
      camera.video = { readyState: HTMLMediaElement.HAVE_CURRENT_DATA, currentTime: 0 };
      camera.handRecognizer = { recognizeForVideo: () => ({
        gestures: [[{ categoryName: "Victory", score: .899 }]],
        landmarks: [Array.from({ length: 21 }, (_, i) => ({ x: .4 + i / 100, y: .5 }))],
      }) };
      for (let i = 0; i < 3; i++) { camera.video.currentTime += .35; await detectHandSign(); }
    });
    await expect.poll(() => observations.length).toBe(1);
    const payload = JSON.parse(observations[0].event.payload);
    expect(payload).toMatchObject({ sign: "scissor", confidence: .899, source: "valerian.hand.camera" });
    const captured = Date.parse(payload.ts);
    expect(captured).toBeLessThanOrEqual(observations[0].received);
    expect(captured).toBeGreaterThan(beforeCapture - 1500);
    expect(Math.abs(await page.evaluate(() => Date.now()) - captured)).toBeGreaterThan(50_000);
    await page.evaluate(() => { globalThis.oldObservationCapture = PrometheusObservationTiming.capture(); });

    await page.locator("#open_diagnostics").click();
    await page.getByTestId("connect-agent").click();
    await expect(page.locator("#observation_clock_status")).toHaveText("Connect to synchronize sensor timing");
    await page.getByTestId("connect-agent").click();
    await expect(page.locator("#observation_clock_status")).toHaveText("Sensor timing ready");
    expect(await page.evaluate(() => PrometheusObservationTiming.accepts(oldObservationCapture))).toBe(false);
    await page.locator("#interaction_timing_tab").click();
    const pending = page.waitForEvent("download");
    await page.locator('[data-timing-export="json"]').click();
    const download = await pending, stream = await download.createReadStream(), chunks = [];
    for await (const chunk of stream) chunks.push(chunk);
    const timing = JSON.parse(Buffer.concat(chunks).toString()).observationTiming;
    expect(timing.current.status).toBe("ready");
    expect(Math.abs(timing.current.offsetMs + lead)).toBeLessThan(1500);
    expect(timing.recent.some(row => row.reason === "disconnected")).toBe(true);
    expect(timing.recent.length).toBeLessThanOrEqual(32);
  });
}

for (const theme of ["light", "dark"]) {
  test(`weather preview and current/forecast submission work in ${theme} mode`, async ({ page, context }, testInfo) => {
    const weather = await openWeatherControls(page, context, theme);
    await expect(page.getByTestId("send-weather-current")).toBeEnabled();
    await expect(page.getByTestId("send-weather-forecast")).toBeEnabled();
    await page.getByTestId("fetch-weather-current").click();
    await expect(page.getByTestId("weather-action-status")).toBeVisible();
    await expect(page.getByTestId("weather-action-status")).toHaveText("Fetched current — not sent");
    await expect(page.getByTestId("weather-report-temperature")).toHaveText("9.5 C");
    expect(weather.observations).toHaveLength(0);

    await page.getByTestId("send-weather-current").click();
    await expect(page.getByTestId("weather-report-status")).toContainText("Sent current");
    await page.getByTestId("send-weather-forecast").click();
    await expect(page.getByTestId("weather-report-status")).toContainText("Sent forecast");
    expect(weather.observations.map(item => item.event.type)).toEqual(["obs.weather.current", "obs.weather.forecast"]);
    for (const observation of weather.observations) {
      expect(observation.accessCode).toBe(ACCESS_CODE);
      expect(observation.event).toMatchObject({ actor: "system", kind: "observation" });
      expect(JSON.parse(observation.event.payload)).toMatchObject({ location_label: "Zurich, Switzerland" });
    }
    expect(JSON.parse(weather.observations[0].event.payload)).toMatchObject({ kind: "current", temperature_c: 9.5 });
    expect(JSON.parse(weather.observations[1].event.payload).days).toHaveLength(3);
    await expect(page.getByTestId("weather-action-status")).toContainText("Sent forecast");
    await page.locator('[data-bs-target="#sensed_signals"]').click();
    await page.getByTestId("hand-sign-report").screenshot({ path: testInfo.outputPath(`hand-report-${theme}.png`) });
    await page.getByTestId("weather-report").screenshot({ path: testInfo.outputPath(`weather-report-${theme}.png`) });
    if (theme === "dark") {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByTestId("weather-report").screenshot({ path: testInfo.outputPath("weather-report-mobile.png") });
    }
  });
}

test("weather errors replace stale success and allow retry without sending a failed location lookup", async ({ page, context }) => {
  const weather = await openWeatherControls(page, context, "dark");
  // Send also fetches automatically when no preview exists.
  weather.ackStatus = 500;
  await page.getByTestId("send-weather-current").click();
  await expect(page.getByTestId("weather-action-status")).toHaveText("Send current failed — retry");
  weather.ackStatus = 200;
  await page.getByTestId("send-weather-current").click();
  await expect(page.getByTestId("weather-report-status")).toContainText("Sent current");
  weather.ackStatus = 500;
  await page.getByTestId("send-weather-forecast").click();
  await expect(page.getByTestId("weather-action-status")).toHaveText("Send forecast failed — retry");
  weather.ackStatus = 200;
  await page.getByTestId("send-weather-forecast").click();
  await expect(page.getByTestId("weather-report-status")).toContainText("Sent forecast");
  expect(weather.observations).toHaveLength(4);

  weather.found = false;
  await page.getByTestId("weather-location-input").fill("Unknown place");
  await page.getByTestId("send-weather-forecast").click();
  await expect(page.getByTestId("weather-action-status")).toHaveText("Fetch failed — weather location not found.");
  await page.getByTestId("send-weather-current").click();
  await expect(page.getByTestId("weather-action-status")).toHaveText("Fetch failed — weather location not found.");
  expect(weather.observations).toHaveLength(4);
  weather.found = true;
  await page.getByTestId("weather-location-input").fill("Zurich, Switzerland");
  await page.getByTestId("send-weather-forecast").click();
  await expect(page.getByTestId("weather-action-status")).toContainText("Sent forecast");
  expect(weather.observations).toHaveLength(5);
  expect(JSON.parse(weather.observations[4].event.payload)).toMatchObject({
    kind: "forecast", location_query: "Zurich, Switzerland",
  });
});

async function openWeatherControls(page, context, theme) {
  const weather = { observations: [], ackStatus: 200, found: true };
  await installLifecycleApiMocks(context, { created: true, history: [] });
  await context.route(`**/demo/agents/${AGENT_ID}/acknowledge`, route => {
    weather.observations.push({ event: route.request().postDataJSON(),
      accessCode: route.request().headers()["x-prometheus-access-code"] });
    return route.fulfill({ ...json({ active: true, responseEvent: null }), status: weather.ackStatus });
  });
  await context.route("https://geocoding-api.open-meteo.com/**", route => route.fulfill(json({
    results: weather.found ? [{ name: "Zurich", country: "Switzerland", latitude: 47.38, longitude: 8.54,
      timezone: "Europe/Zurich" }] : [],
  })));
  await context.route("https://api.open-meteo.com/**", route => route.fulfill(json({
    current: { time: "2026-10-22T09:00", weather_code: 0, temperature_2m: 9.5, precipitation: 0,
      rain: 0, showers: 0, snowfall: 0, cloud_cover: 10, wind_speed_10m: 3, wind_gusts_10m: 5, is_day: 1 },
    daily: { time: ["2026-10-22", "2026-10-23", "2026-10-24"], weather_code: [61, 61, 61],
      temperature_2m_min: [6.8, 6.5, 6.3], temperature_2m_max: [13.3, 13.4, 9.7], precipitation_sum: [1, 2, 3] },
  })));
  await page.addInitScript(value => localStorage.setItem("prometheus.valerian.theme", value), theme);
  await page.goto(`/valerian/?agentId=${AGENT_ID}`);
  await enterAccessCode(page);
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${AGENT_ID}`);
  await page.locator('[data-bs-target="#manual_weather_events"]').click();
  return weather;
}

async function enterAccessCode(page) {
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
}

async function assertColumnsEmpty(page) {
  await expect(page.getByTestId("message-list").locator(".demo-message")).toHaveCount(0);
  await expect(page.getByTestId("text-input")).toHaveValue("");
  await expect(page.getByTestId("continuous-speech-sensing-value")).toHaveText("-");
  await expect(page.getByTestId("emotion-value")).toHaveText("-");
  await expect(page.getByTestId("human-count")).toHaveText("0");
  await expect(page.getByTestId("group-count")).toHaveText("0");
  await expect(page.getByTestId("hand-sign-value")).toHaveText("-");
  await expect(page.getByTestId("weather-value")).toHaveText("-");
  await expect(page.getByTestId("speech-preview")).toHaveText("-");
  await expect(page.getByTestId("latest-behaviour-event")).toHaveText("-");
  await expect(page.getByTestId("behaviour-chip-speech")).not.toHaveClass(/is-active/);
}

async function installLifecycleApiMocks(context, scenario, realClock = false) {
  if (!realClock) await context.route("**/runtime/clock", route => route.fulfill(json({ serverTimeMs: Date.now() })));
  await context.route("**/demo/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === "POST" && path === "/demo/session") {
      return route.fulfill(json({
        accessCode: ACCESS_CODE,
        agentTypes: [{ key: AGENT_KEY, displayName: "Multimodal Behaviour", description: "Lifecycle fixture" }],
        agents: scenario.created ? [AGENT] : [],
      }));
    }
    if (request.method() === "POST" && path === "/demo/agents") {
      scenario.created = true;
      return route.fulfill(json(AGENT));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/info`) {
      return route.fulfill(json(AGENT));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/eventhistory`) {
      return route.fulfill(json(scenario.history));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/storage`) {
      return route.fulfill(json([{ key: "visit", value: "used" }]));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/state`) {
      return route.fulfill(json({ name: scenario.stateName || "Conversation", innerName: null, innerNames: [] }));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/states`) {
      return route.fulfill(json(["Conversation"]));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/transcription/capabilities`) {
      return route.fulfill(json(transcriptionCapabilities()));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/behaviour/stream`) {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/monitor/stream`) {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    return route.fulfill({ status: 404, body: "" });
  });
}

function transcriptionCapabilities() {
  const base = { activeSessionBehavior: "live-input-boundary", sensitive: false, allowedValues: [] };
  return {
    schemaVersion: 1,
    sessionType: "transcription",
    model: "gpt-live-transcribe",
    capabilities: { assistantOutput: false, inputTranscription: true },
    settings: [
      { ...base, key: "noiseReduction", control: "select", defaultValue: "far_field",
        allowedValues: ["near_field", "far_field", "off"] },
      { ...base, key: "turnDetection.type", control: "select", defaultValue: "local_vad",
        allowedValues: ["local_vad", "manual"], activeSessionBehavior: "local-input-boundary" },
      { ...base, key: "turnDetection.silenceDurationSeconds", control: "number", defaultValue: 1.5,
        minimum: 0.5, maximum: 10, step: 0.1, visibleWhen: "turnDetection.type=local_vad",
        activeSessionBehavior: "local-input-boundary" },
      { ...base, key: "transcriptionPrompt", control: "text", defaultValue: "", maxLength: 1024, sensitive: true },
      { ...base, key: "transcriptionKeywords", control: "string-list", defaultValue: [], maxLength: 100,
        maxItems: 100, minItems: 0, itemPattern: "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._'/-]*$", sensitive: true },
      { ...base, key: "languages", control: "multi-select", defaultValue: ["en"],
        allowedValues: ["ar", "de", "en"], minItems: 1, maxItems: 3 },
      { ...base, key: "transcriptionDelay", control: "select", defaultValue: "medium",
        allowedValues: ["minimal", "low", "medium", "high", "xhigh"] },
    ],
  };
}

function event(type, actor, kind, payload, createdDate) {
  return { type, actor, kind, payload, createdDate };
}

function behaviourEvent(speech, createdDate) {
  return event("resp.behaviour_plan", "assistant", "response", JSON.stringify({
    speech,
    nonVerbal: { gesture: "ACKNOWLEDGE" },
    display: { text: "Lifecycle" },
  }), createdDate);
}

function json(body) {
  return { status: 200, contentType: "application/json", body: JSON.stringify(body) };
}
