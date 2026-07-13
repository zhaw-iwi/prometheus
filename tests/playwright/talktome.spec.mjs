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
  const assignment = await request.put(`/admin/access-codes/${accessCode.id}/agent-types`, {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
    data: { agentTypeKeys: [AGENT_TYPE] },
  });
  expect(assignment.ok(), await assignment.text()).toBeTruthy();
  await deleteScopedAgents(request);
});

test.afterAll(async ({ request }) => {
  await deleteScopedAgents(request);
});

test("public Talk to Me manages a scoped instance and persists exact speech", async ({ page, request }, testInfo) => {
  await installBrowserAudioFakes(page);

  let callRequest;
  await page.route(/\/demo\/agents\/[^/]+\/realtime\/call(?:\?.*)?$/, async (route) => {
    callRequest = route.request();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ sdp: "fake-answer", model: "gpt-realtime", callId: "fake-talk-call" }),
    });
  });
  await page.route("**/realtime/calls/fake-talk-call", async (route) => {
    await route.fulfill({ status: 204, body: "" });
  });

  await page.goto("/public/talktome");
  await expect(page).toHaveURL(/\/public\/talktome$/);
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await attachScreenshot(page.getByTestId("access-screen"), testInfo, "talktome-access-light");

  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("talktome-shell")).toBeVisible();
  await expect(page.getByTestId("agent-detail")).toContainText("No instance yet");
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);
  await expect(page.getByTestId("character-count"))
    .toHaveText(`${Array.from(DEFAULT_SPEECH_TEXT).length} / 2000`);
  await expect(page.getByTestId("load-default-text")).toBeDisabled();
  await expect(page.getByTestId("clear-speech-text")).toBeDisabled();
  await expect(page.getByTestId("voice-select")).toHaveValue("alloy");
  await expect(page.getByTestId("voice-select")).toBeEnabled();
  await expect(page.getByTestId("speed-select")).toBeEnabled();
  await expect(page.getByTestId("speaker-select")).toBeEnabled();
  await expect(page.getByTestId("refresh-speakers")).toBeEnabled();
  await expect(page.getByTestId("connection-guidance"))
    .toHaveText("Choose voice and output speed before connecting. Speaker can be changed at any time.");

  const lifecycleBoxes = await Promise.all([
    page.getByTestId("create-agent").boundingBox(),
    page.getByTestId("delete-agent").boundingBox(),
    page.getByTestId("connection-settings").boundingBox(),
    page.getByTestId("connect-agent").boundingBox(),
    page.getByTestId("disconnect-agent").boundingBox(),
  ]);
  const [createBox, deleteBox, connectionSettingsBox, connectBox, disconnectBox] = lifecycleBoxes;
  expect(lifecycleBoxes.every(Boolean)).toBeTruthy();
  expect(Math.abs(createBox.y - deleteBox.y)).toBeLessThan(2);
  expect(Math.abs(connectBox.y - disconnectBox.y)).toBeLessThan(2);
  expect(connectionSettingsBox.y).toBeGreaterThan(createBox.y + createBox.height);
  expect(connectBox.y).toBeGreaterThan(connectionSettingsBox.y + connectionSettingsBox.height);
  expect(Math.abs(createBox.width - deleteBox.width)).toBeLessThan(2);
  expect(Math.abs(connectBox.width - disconnectBox.width)).toBeLessThan(2);

  const speechControlBoxes = await Promise.all([
    page.getByTestId("load-default-text").boundingBox(),
    page.getByTestId("clear-speech-text").boundingBox(),
    page.getByTestId("speech-text").boundingBox(),
  ]);
  const [loadDefaultBox, clearTextBox, speechTextBox] = speechControlBoxes;
  expect(speechControlBoxes.every(Boolean)).toBeTruthy();
  expect(Math.abs(loadDefaultBox.y - clearTextBox.y)).toBeLessThan(2);
  expect(clearTextBox.x).toBeGreaterThan(loadDefaultBox.x + loadDefaultBox.width);
  expect(speechTextBox.y).toBeGreaterThan(loadDefaultBox.y + loadDefaultBox.height);

  await page.getByTestId("create-agent").click();
  await expect(page.getByTestId("agent-detail")).toContainText("Instance created");
  const agentId = await page.getByTestId("agent-select").inputValue();
  expect(agentId).toMatch(/^[0-9a-f-]{36}$/);

  await expect(page.getByTestId("speaker-select").locator('option[value="speaker-2"]')).toHaveCount(1);
  await page.getByTestId("speaker-select").selectOption("speaker-2");
  await expect.poll(() => page.evaluate(() => window.__selectedSinkId)).toBe("speaker-2");
  await page.getByTestId("voice-select").selectOption("cedar");
  await page.getByTestId("speed-select").selectOption("1.25");

  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("realtime-status")).toHaveText("Connected");
  await expect(page.getByTestId("speech-text")).toBeEnabled();
  await expect(page.getByTestId("load-default-text")).toBeEnabled();
  await expect(page.getByTestId("clear-speech-text")).toBeEnabled();
  await expect(page.getByTestId("voice-select")).toBeDisabled();
  await expect(page.getByTestId("speed-select")).toBeDisabled();
  await expect(page.getByTestId("speaker-select")).toBeEnabled();
  await expect(page.getByTestId("refresh-speakers")).toBeEnabled();
  await expect(page.getByTestId("connection-guidance"))
    .toHaveText("Voice and output speed are locked for this call. Disconnect to change them. Speaker changes apply immediately.");
  expect(callRequest).toBeTruthy();
  expect(callRequest.headers()[ACCESS_CODE_HEADER.toLowerCase()]).toBe(ACCESS_CODE);
  expect(callRequest.postData()).toBe("fake-offer");
  const callUrl = new URL(callRequest.url());
  expect(callUrl.searchParams.get("voice")).toBe("cedar");
  expect(callUrl.searchParams.get("outputSpeed")).toBe("1.25");
  expect(callUrl.searchParams.get("generateComplement")).toBe("false");
  expect(await page.evaluate(() => window.__transceivers)).toEqual([
    { kind: "audio", direction: "recvonly" },
  ]);
  expect(await page.evaluate(() => window.__microphoneRequests)).toBe(0);

  await page.getByTestId("clear-speech-text").click();
  await expect(page.getByTestId("speech-text")).toHaveValue("");
  await expect(page.getByTestId("character-count")).toHaveText("0 / 2000");
  await expect(page.getByTestId("speak-text")).toBeDisabled();

  await page.getByTestId("load-default-text").click();
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);
  await expect(page.getByTestId("character-count"))
    .toHaveText(`${Array.from(DEFAULT_SPEECH_TEXT).length} / 2000`);
  await expect(page.getByTestId("speak-text")).toBeEnabled();

  let exactText = "Love is patient, love is kind.";
  exactText += " It does not envy, it does not boast, it is not proud.";
  exactText += " It does not dishonor others, it is not self-seeking,";
  exactText += " it is not easily angered, it keeps no record of wrongs.";
  exactText += " Love does not delight in evil but rejoices with the truth.";
  exactText += " It always protects, always trusts, always hopes, always perseveres.";
  expect(exactText).toBe(DEFAULT_SPEECH_TEXT);
  await page.getByTestId("speech-text").fill(exactText);
  await expect(page.getByTestId("character-count")).toHaveText(`${Array.from(exactText).length} / 2000`);

  const acknowledgeResponsePromise = page.waitForResponse((response) =>
    response.request().method() === "POST"
      && response.url().includes(`/demo/agents/${agentId}/acknowledge?profile=realtime_speech`));
  await page.getByTestId("speak-text").click();
  const acknowledgeResponse = await acknowledgeResponsePromise;
  expect(acknowledgeResponse.ok(), await acknowledgeResponse.text()).toBeTruthy();
  const acknowledgement = await acknowledgeResponse.json();
  expect(JSON.parse(acknowledgement.responseEvent.payload).speech).toBe(exactText);

  await page.evaluate((transcript) => {
    const partialTranscript = transcript.slice(0, transcript.indexOf(" it keeps no record"));
    window.__emitRealtime({ type: "response.created" });
    window.__emitRealtime({ type: "response.output_audio_transcript.delta", delta: partialTranscript });
    window.__emitRealtime({ type: "response.output_audio_transcript.done", transcript });
    window.__emitRealtime({
      type: "response.done",
      response: {
        status: "completed",
        output: [{ type: "message", content: [{ type: "audio", transcript }] }],
      },
    });
  }, exactText);
  await expect(page.getByTestId("spoken-transcript")).toContainText(exactText);
  await expect(page.getByTestId("speech-status")).toHaveText("Speech completed.");

  await page.evaluate((transcript) => {
    const partialTranscript = transcript.slice(0, transcript.indexOf(" it keeps no record"));
    window.__emitRealtime({ type: "response.created" });
    window.__emitRealtime({
      type: "response.done",
      response: {
        status: "completed",
        output: [{ type: "message", content: [{ type: "audio", transcript: partialTranscript }] }],
      },
    });
  }, exactText);
  await expect(page.getByTestId("speech-status"))
    .toHaveText("Realtime completed, but its final transcript differs from the submitted text.");

  await page.evaluate(() => {
    window.__emitRealtime({ type: "response.created" });
    window.__emitRealtime({
      type: "response.done",
      response: { status: "incomplete", status_details: { reason: "max_output_tokens" } },
    });
  });
  await expect(page.getByTestId("speech-status"))
    .toHaveText("Speech was cut off because Realtime reached its output-token limit.");

  await page.evaluate((transcript) => {
    window.__emitRealtime({ type: "response.created" });
    window.__emitRealtime({
      type: "response.done",
      response: {
        status: "completed",
        output: [{ type: "message", content: [{ type: "audio", transcript }] }],
      },
    });
  }, exactText);
  await expect(page.getByTestId("speech-status")).toHaveText("Speech completed.");

  const historyResponse = await request.get(`/demo/agents/${agentId}/eventhistory`, {
    headers: { [ACCESS_CODE_HEADER]: ACCESS_CODE },
  });
  expect(historyResponse.ok(), await historyResponse.text()).toBeTruthy();
  const history = await historyResponse.json();
  expect(history).toHaveLength(2);
  expect(history[0].payload).toBe(exactText);
  expect(JSON.parse(history[1].payload).speech).toBe(exactText);

  await attachScreenshot(page.getByTestId("talktome-shell"), testInfo, "talktome-connected-desktop");
  await page.getByTestId("app-theme-toggle").click();
  await expect.poll(() => page.locator("h1").evaluate((element) => getComputedStyle(element).color))
    .toBe("rgb(235, 244, 242)");
  await page.setViewportSize({ width: 390, height: 844 });
  await attachScreenshot(page.getByTestId("talktome-shell"), testInfo, "talktome-connected-mobile-dark");

  await page.getByTestId("clear-speech-text").click();
  await expect(page.getByTestId("speech-text")).toHaveValue("");

  await page.getByTestId("disconnect-agent").click();
  await expect(page.getByTestId("realtime-status")).toHaveText("Offline");
  await expect(page.getByTestId("agent-select")).toHaveValue(agentId);
  await expect(page.getByTestId("connect-agent")).toBeEnabled();
  await expect(page.getByTestId("load-default-text")).toBeDisabled();
  await expect(page.getByTestId("clear-speech-text")).toBeDisabled();
  await expect(page.getByTestId("voice-select")).toBeEnabled();
  await expect(page.getByTestId("speed-select")).toBeEnabled();
  await expect(page.getByTestId("speaker-select")).toBeEnabled();
  await expect(page.getByTestId("connection-guidance"))
    .toHaveText("Choose voice and output speed before connecting. Speaker can be changed at any time.");

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("delete-agent").click();
  await expect(page.getByTestId("agent-detail")).toHaveText("Instance deleted.");
  await expect(page.getByTestId("agent-select")).toHaveValue("");

  await page.reload();
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await expect(page.getByTestId("speech-text")).toHaveValue(DEFAULT_SPEECH_TEXT);
  await expect(page.getByTestId("character-count"))
    .toHaveText(`${Array.from(DEFAULT_SPEECH_TEXT).length} / 2000`);
});

async function installBrowserAudioFakes(page) {
  await page.addInitScript(() => {
    window.__microphoneRequests = 0;
    window.__selectedSinkId = "";
    window.__transceivers = [];
    window.__realtimeChannels = [];

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

    Object.defineProperty(HTMLMediaElement.prototype, "setSinkId", {
      configurable: true,
      value: async function setSinkId(deviceId) {
        window.__selectedSinkId = deviceId;
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "play", {
      configurable: true,
      value: async () => undefined,
    });
    Object.defineProperty(HTMLMediaElement.prototype, "pause", {
      configurable: true,
      value: () => undefined,
    });
    Object.defineProperty(HTMLMediaElement.prototype, "load", {
      configurable: true,
      value: () => undefined,
    });

    class FakeDataChannel extends EventTarget {
      constructor() {
        super();
        this.readyState = "open";
        this.sent = [];
      }

      send(data) {
        this.sent.push(data);
      }

      close() {
        this.readyState = "closed";
      }

      emit(data) {
        this.dispatchEvent(new MessageEvent("message", { data: JSON.stringify(data) }));
      }
    }

    class FakePeerConnection extends EventTarget {
      constructor() {
        super();
        this.connectionState = "connected";
      }

      addTransceiver(kind, options) {
        window.__transceivers.push({ kind, direction: options && options.direction });
        return {};
      }

      createDataChannel() {
        const channel = new FakeDataChannel();
        window.__realtimeChannels.push(channel);
        return channel;
      }

      async createOffer() {
        return { type: "offer", sdp: "fake-offer" };
      }

      async setLocalDescription(description) {
        this.localDescription = description;
      }

      async setRemoteDescription(description) {
        this.remoteDescription = description;
        queueMicrotask(() => window.__realtimeChannels.at(-1).emit({ type: "session.updated" }));
      }

      close() {
        this.connectionState = "closed";
      }
    }

    window.RTCPeerConnection = FakePeerConnection;
    window.__emitRealtime = (data) => window.__realtimeChannels.at(-1).emit(data);
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
