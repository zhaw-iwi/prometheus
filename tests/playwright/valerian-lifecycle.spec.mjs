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

async function installLifecycleApiMocks(context, scenario) {
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
      return route.fulfill(json({ name: "Conversation", innerName: null, innerNames: [] }));
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
