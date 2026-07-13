import { expect, test } from "@playwright/test";

const ACCESS_CODE = "TTM31";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";
const ADMIN_TOKEN = process.env.PROMETHEUS_ADMIN_TOKEN || "laure";
const AGENT_TYPE = "core.talk_to_me";
const PROFILE_TAG = "utility.talk_to_me";
const DEFAULT_SPEECH_TEXT = [
  "Love is patient, love is kind.",
  "It does not envy, it does not boast, it is not proud.",
  "It does not dishonor others, it is not self-seeking, it is not easily angered,",
  "it keeps no record of wrongs.",
  "Love does not delight in evil but rejoices with the truth.",
  "It always protects, always trusts, always hopes, always perseveres.",
].join(" ");

test.beforeAll(async ({ request }) => {
  const accessCode = await ensureAccessCode(request, ACCESS_CODE);
  const assignment = await request.put("/admin/access-codes/" + accessCode.id + "/agent-types", {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
    data: { agentTypeKeys: [AGENT_TYPE] },
  });
  expect(assignment.ok(), await assignment.text()).toBeTruthy();
  await deleteScopedAgents(request);
});

test.afterAll(async ({ request }) => {
  await deleteScopedAgents(request);
});

test("public Talk to Me synthesizes and completes exact scoped speech", async ({ page, request }, testInfo) => {
  await installBrowserAudioFakes(page);

  const speechRequests = [];
  await page.route(/\/demo\/talktome\/agents\/[^/]+\/speech(?:\?.*)?$/, async (route) => {
    const browserRequest = route.request();
    const url = new URL(browserRequest.url());
    const match = url.pathname.match(/\/demo\/talktome\/agents\/([^/]+)\/speech$/);
    const body = browserRequest.postDataJSON();
    speechRequests.push({
      headers: browserRequest.headers(),
      body,
      voice: url.searchParams.get("voice"),
      speed: url.searchParams.get("speed"),
    });

    const acknowledged = await request.post("/demo/agents/" + match[1] + "/acknowledge?profile=realtime_speech", {
      headers: { [ACCESS_CODE_HEADER]: ACCESS_CODE },
      data: body,
    });
    expect(acknowledged.ok(), await acknowledged.text()).toBeTruthy();
    const acknowledgement = await acknowledged.json();
    expect(JSON.parse(acknowledgement.responseEvent.payload).speech).toBe(body.payload);

    await route.fulfill({
      status: 200,
      contentType: "audio/mpeg",
      body: Buffer.from([73, 68, 51, 4, 0, 0, 0, 0, 0, 0]),
    });
  });

  await page.goto("/public/talktome");
  await expect(page).toHaveURL(/\/public\/talktome$/);
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await attachScreenshot(page.getByTestId("access-screen"), testInfo, "talktome-access-light");

  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("talktome-shell")).toBeVisible();
  await expect(page.getByTestId("agent-detail")).toContainText("No instance yet");
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("No instance");
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);
  await expect(page.getByTestId("character-count"))
    .toHaveText(Array.from(DEFAULT_SPEECH_TEXT).length + " / 2000");
  await expect(page.getByTestId("speech-text")).toBeDisabled();
  await expect(page.getByTestId("load-default-text")).toBeDisabled();
  await expect(page.getByTestId("clear-speech-text")).toBeDisabled();
  await expect(page.getByTestId("voice-select")).toHaveValue("alloy");
  await expect(page.getByTestId("speech-settings-guidance"))
    .toHaveText("Voice and output speed apply to the next request. Speaker can be changed at any time.");

  const lifecycleBoxes = await Promise.all([
    page.getByTestId("create-agent").boundingBox(),
    page.getByTestId("delete-agent").boundingBox(),
    page.getByTestId("speech-settings").boundingBox(),
  ]);
  const [createBox, deleteBox, speechSettingsBox] = lifecycleBoxes;
  expect(lifecycleBoxes.every(Boolean)).toBeTruthy();
  expect(Math.abs(createBox.y - deleteBox.y)).toBeLessThan(2);
  expect(Math.abs(createBox.width - deleteBox.width)).toBeLessThan(2);
  expect(speechSettingsBox.y).toBeGreaterThan(createBox.y + createBox.height);

  await page.getByTestId("create-agent").click();
  await expect(page.getByTestId("agent-detail")).toContainText("Instance created");
  const agentId = await page.getByTestId("agent-select").inputValue();
  expect(agentId).toMatch(/^[0-9a-f-]{36}$/);
  await expect(page.getByTestId("agent-status")).toHaveText("Ready");
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("Ready");
  await expect(page.getByTestId("speech-text")).toBeEnabled();
  await expect(page.getByTestId("load-default-text")).toBeEnabled();
  await expect(page.getByTestId("clear-speech-text")).toBeEnabled();

  await expect(page.getByTestId("speaker-select").locator('option[value="speaker-2"]')).toHaveCount(1);
  await page.getByTestId("speaker-select").selectOption("speaker-2");
  await expect.poll(() => page.evaluate(() => window.__selectedSinkId)).toBe("speaker-2");
  await page.getByTestId("voice-select").selectOption("cedar");
  await page.getByTestId("speed-select").selectOption("1.25");

  await page.getByTestId("clear-speech-text").click();
  await expect(page.getByTestId("speech-text")).toHaveValue("");
  await expect(page.getByTestId("speak-text")).toBeDisabled();
  await page.getByTestId("load-default-text").click();
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);

  await page.getByTestId("speak-text").click();
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("Playing");
  await expect(page.getByTestId("speech-status")).toHaveText("Playing synthesized speech.");
  await expect(page.getByTestId("spoken-transcript")).toContainText(DEFAULT_SPEECH_TEXT);
  await expect(page.getByTestId("spoken-transcript")).toContainText("Speech text");
  await expect(page.getByTestId("speech-text")).toBeDisabled();
  await expect(page.getByTestId("voice-select")).toBeDisabled();
  await expect(page.getByTestId("speed-select")).toBeDisabled();
  await expect(page.getByTestId("stop-speech")).toBeEnabled();

  expect(speechRequests).toHaveLength(1);
  expect(speechRequests[0].headers[ACCESS_CODE_HEADER.toLowerCase()]).toBe(ACCESS_CODE);
  expect(speechRequests[0].voice).toBe("cedar");
  expect(speechRequests[0].speed).toBe("1.25");
  expect(speechRequests[0].body).toEqual({
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload: DEFAULT_SPEECH_TEXT,
  });
  expect(await page.evaluate(() => window.__audioBlobs)).toEqual([
    { size: 10, type: "audio/mpeg" },
  ]);
  expect(await page.evaluate(() => window.__microphoneRequests)).toBe(0);

  await page.getByTestId("assistant-audio").evaluate((audio) => audio.dispatchEvent(new Event("ended")));
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("Ready");
  await expect(page.getByTestId("speech-status")).toHaveText("Speech completed.");
  await expect(page.getByTestId("speech-text")).toBeEnabled();
  await expect(page.getByTestId("voice-select")).toBeEnabled();

  const historyResponse = await request.get("/demo/agents/" + agentId + "/eventhistory", {
    headers: { [ACCESS_CODE_HEADER]: ACCESS_CODE },
  });
  expect(historyResponse.ok(), await historyResponse.text()).toBeTruthy();
  const history = await historyResponse.json();
  expect(history).toHaveLength(2);
  expect(history[0].payload).toBe(DEFAULT_SPEECH_TEXT);
  expect(JSON.parse(history[1].payload).speech).toBe(DEFAULT_SPEECH_TEXT);

  await page.getByTestId("speech-text").fill("Please stop this playback.");
  await page.getByTestId("speak-text").click();
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("Playing");
  await page.getByTestId("stop-speech").click();
  await expect(page.getByTestId("speech-renderer-status")).toHaveText("Ready");
  await expect(page.getByTestId("speech-status")).toHaveText("Speech stopped.");
  expect(await page.evaluate(() => window.__revokedAudioUrls.length)).toBeGreaterThan(0);

  await attachScreenshot(page.getByTestId("talktome-shell"), testInfo, "talktome-ready-desktop");
  await page.getByTestId("app-theme-toggle").click();
  await expect.poll(() => page.locator("h1").evaluate((element) => getComputedStyle(element).color))
    .toBe("rgb(235, 244, 242)");
  await page.setViewportSize({ width: 390, height: 844 });
  await attachScreenshot(page.getByTestId("talktome-shell"), testInfo, "talktome-ready-mobile-dark");

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("delete-agent").click();
  await expect(page.getByTestId("agent-detail")).toHaveText("Instance deleted.");
  await expect(page.getByTestId("agent-select")).toHaveValue("");

  await page.reload();
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);
});

async function installBrowserAudioFakes(page) {
  await page.addInitScript(() => {
    window.__microphoneRequests = 0;
    window.__selectedSinkId = "";
    window.__audioBlobs = [];
    window.__revokedAudioUrls = [];

    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: {
        enumerateDevices: async () => [
          { kind: "audiooutput", deviceId: "default", label: "System default", groupId: "outputs" },
          { kind: "audiooutput", deviceId: "speaker-1", label: "Studio Speakers", groupId: "outputs" },
          { kind: "audiooutput", deviceId: "speaker-2", label: "USB Headset", groupId: "outputs" },
        ],
        getUserMedia: async () => {
          window.__microphoneRequests += 1;
          throw new Error("Talk to Me must not request microphone access.");
        },
      },
    });

    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: (blob) => {
        window.__audioBlobs.push({ size: blob.size, type: blob.type });
        return "data:audio/mpeg;base64,SUQzBAAAAAAA";
      },
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: (url) => window.__revokedAudioUrls.push(url),
    });
    Object.defineProperty(HTMLMediaElement.prototype, "setSinkId", {
      configurable: true,
      value: async function setSinkId(deviceId) {
        window.__selectedSinkId = deviceId;
      },
    });
    const nativeMediaAddEventListener = HTMLMediaElement.prototype.addEventListener;
    Object.defineProperty(HTMLMediaElement.prototype, "addEventListener", {
      configurable: true,
      value: function addEventListener(type, listener, options) {
        if (type === "error") {
          window.__mediaErrorListener = listener;
          return;
        }
        return nativeMediaAddEventListener.call(this, type, listener, options);
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "play", {
      configurable: true,
      value: async function play() {
        this.dispatchEvent(new Event("play"));
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "pause", {
      configurable: true,
      value: () => undefined,
    });
    Object.defineProperty(HTMLMediaElement.prototype, "load", {
      configurable: true,
      value: () => undefined,
    });
  });
}

async function ensureAccessCode(request, code) {
  const createResponse = await request.post("/admin/access-codes", {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
    data: { code, enabled: true },
  });
  if (createResponse.status() === 201) {
    return createResponse.json();
  }
  if (createResponse.status() !== 409) {
    throw new Error(`Unable to create access code ${code}: ${createResponse.status()} ${await createResponse.text()}`);
  }

  const listResponse = await request.get("/admin/access-codes", {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
  });
  expect(listResponse.ok(), await listResponse.text()).toBeTruthy();
  const existing = (await listResponse.json()).find((entry) => entry && entry.code === code);
  if (!existing) {
    throw new Error(`Access code ${code} already exists but was not returned by the admin endpoint.`);
  }
  if (!existing.enabled) {
    const enabled = await request.patch(`/admin/access-codes/${existing.id}`, {
      headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
      data: { enabled: true },
    });
    expect(enabled.ok(), await enabled.text()).toBeTruthy();
    return enabled.json();
  }
  return existing;
}

async function deleteScopedAgents(request) {
  const sessionResponse = await request.post("/demo/session", {
    data: { accessCode: ACCESS_CODE },
  });
  if (!sessionResponse.ok()) {
    return;
  }
  const session = await sessionResponse.json();
  const agents = (session.agents || []).filter((agent) =>
    agent.interactionProfile?.profileTags?.includes(PROFILE_TAG));
  for (const agent of agents) {
    const deleted = await request.delete(`/demo/agents/${agent.id}`, {
      headers: { [ACCESS_CODE_HEADER]: ACCESS_CODE },
    });
    expect(deleted.ok(), await deleted.text()).toBeTruthy();
  }
}

async function attachScreenshot(locator, testInfo, name) {
  const path = testInfo.outputPath(`${name}.png`);
  const screenshot = await locator.screenshot({ animations: "disabled", path });
  expect(screenshot.byteLength).toBeGreaterThan(10_000);
  await testInfo.attach(name, { path, contentType: "image/png" });
}
