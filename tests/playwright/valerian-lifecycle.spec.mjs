import { expect, test } from "@playwright/test";

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
