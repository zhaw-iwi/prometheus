import { expect, test } from "@playwright/test";

const ACCESS_CODE = "TTM31";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";
const ADMIN_TOKEN = process.env.PROMETHEUS_ADMIN_TOKEN || "laure";
const AGENT_TYPE = "core.talk_to_me";
const PROFILE_TAG = "utility.talk_to_me";

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

  const exactText = "Grüezi, \"Zürich\"!\nPlease read line two 🌍";
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
    window.__emitRealtime({ type: "response.created" });
    window.__emitRealtime({ type: "response.output_audio_transcript.done", transcript });
    window.__emitRealtime({ type: "response.done" });
  }, exactText);
  await expect(page.getByTestId("spoken-transcript")).toContainText(exactText);
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

  await page.getByTestId("disconnect-agent").click();
  await expect(page.getByTestId("realtime-status")).toHaveText("Offline");
  await expect(page.getByTestId("agent-select")).toHaveValue(agentId);
  await expect(page.getByTestId("connect-agent")).toBeEnabled();

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("delete-agent").click();
  await expect(page.getByTestId("agent-detail")).toHaveText("Instance deleted.");
  await expect(page.getByTestId("agent-select")).toHaveValue("");
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
