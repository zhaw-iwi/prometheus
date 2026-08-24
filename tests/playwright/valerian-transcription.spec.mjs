import { expect, test } from "@playwright/test";

const ACCESS_CODE = "TRANSCRIBE";
const AGENT_ID = "11111111-1111-4111-8111-111111111111";
const LIVE_BEHAVIOUR_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const REPLAY_BEHAVIOUR_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const SECOND_BEHAVIOUR_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
const SLOW_BEHAVIOUR_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
const ERROR_BEHAVIOUR_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
const AGENT = {
  id: AGENT_ID,
  name: "Live Transcription Test Agent",
  description: "Deterministic browser boundary for live transcription.",
  active: true,
  languageCode: "de",
  interactionProfile: {
    supportedObservations: ["obs.user_utterance"],
    supportedBehaviourModalities: ["speech", "nonVerbal.gesture"],
    profileTags: [],
  },
};

test.beforeEach(async ({ context }) => {
  await installApiMocks(context);
  await installBrowserMediaMocks(context);
});

test("mocked WebRTC emits partial UI and one ordered finalized turn", async ({ page }) => {
  const acknowledgeRequests = [];
  const speechRequests = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/acknowledge")) acknowledgeRequests.push(request);
    if (new URL(request.url()).pathname.endsWith("/speech")) speechRequests.push(request);
  });
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("live-transcription-settings-toggle").click();
  await expect(page.getByTestId("live-transcription-settings-root")).toContainText("gpt-live-transcribe");
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await emitProviderEvent(page, { type: "input_audio_buffer.committed", event_id: "commit-1", item_id: "item-1" });
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.delta", event_id: "delta-1", item_id: "item-1", delta: "Guten ",
  });
  await expect(page.getByTestId("continuous-speech-sensing-value")).toHaveText("Guten ");
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.completed", event_id: "done-1", item_id: "item-1",
    transcript: "Guten Morgen, PROMETHEUS.",
  });
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.completed", event_id: "done-duplicate", item_id: "item-1",
    transcript: "Guten Morgen, PROMETHEUS.",
  });

  await expect(page.getByTestId("message-list").locator(".demo-message.user")).toHaveCount(1);
  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(0);
  await expect(page.getByTestId("message-list")).toContainText("Guten Morgen, PROMETHEUS.");
  await expect(page.getByTestId("transcription-ingress-status")).toHaveText("Transcript Accepted");
  expect(acknowledgeRequests).toHaveLength(1);
  expect(new URL(acknowledgeRequests[0].url()).searchParams.get("profile")).toBe("full_plan");
  expect(acknowledgeRequests[0].headers()["x-prometheus-access-code"]).toBe(ACCESS_CODE);
  expect(acknowledgeRequests[0].postDataJSON()).toEqual({
    type: "obs.user_utterance", actor: "user", kind: "observation", payload: "Guten Morgen, PROMETHEUS.",
  });

  await emitBehaviourSse(page, "behaviour-live", LIVE_BEHAVIOUR_ID, behaviourEvent());
  await emitBehaviourSse(page, "behaviour-live", LIVE_BEHAVIOUR_ID, behaviourEvent());
  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);
  await expect(page.getByTestId("message-list")).toContainText("Guten Morgen. I heard you clearly.");
  await expect(page.getByTestId("behaviour-channel-strip")).toContainText("Speech");
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  expect(speechRequests).toHaveLength(1);
  expect(new URL(speechRequests[0].url()).pathname)
    .toBe(`/demo/agents/${AGENT_ID}/behaviours/${LIVE_BEHAVIOUR_ID}/speech`);
  expect(new URL(speechRequests[0].url()).searchParams.get("voice")).toBe("alloy");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(false);
  expect(await page.evaluate(() => window.__transcriptionChannels.at(-1).sent
    .map((value) => JSON.parse(value).type))).toContain("input_audio_buffer.clear");

  await page.evaluate(() => window.__finishSpeechPlayback());
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Playback Ready");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(true);

  await emitBehaviourSse(page, "behaviour-replay", REPLAY_BEHAVIOUR_ID,
    behaviourEvent("This replay must stay silent."));
  await page.waitForTimeout(100);
  expect(speechRequests).toHaveLength(1);

  await emitProviderEvent(page, { type: "input_audio_buffer.committed", event_id: "commit-failed",
    item_id: "item-failed" });
  await emitProviderEvent(page, { type: "conversation.item.input_audio_transcription.failed",
    event_id: "failed-1", item_id: "item-failed", error: { code: "audio_unintelligible" } });
  await expect(page.getByTestId("transcription-ingress-status")).toHaveText("Provider Error");
  expect(acknowledgeRequests).toHaveLength(1);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests)).toHaveLength(1);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests[0].audio)).toMatchObject({
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
  });

  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.every((track) => track.stopped))).toBe(true);
});

test("manual turn commits, device changes persist, and transport reconnects", async ({ page }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("live-transcription-settings-toggle").click();
  await page.getByTestId("transcription-turnDetection-type").selectOption("manual");
  await page.getByTestId("transcription-input-device").selectOption("room-mic");
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  const push = page.getByTestId("transcription-push-to-talk");
  await expect(push).toBeVisible();
  await push.dispatchEvent("pointerdown", { pointerId: 1 });
  await push.dispatchEvent("pointerup", { pointerId: 1 });
  expect(await page.evaluate(() => window.__transcriptionChannels.at(-1).sent.map((value) => JSON.parse(value).type)))
    .toEqual(["input_audio_buffer.clear", "input_audio_buffer.commit"]);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests.at(-1).audio.deviceId.exact)).toBe("room-mic");

  await page.evaluate(() => {
    const peer = window.__transcriptionPeers.at(-1);
    peer.connectionState = "failed";
    peer.dispatchEvent(new Event("connectionstatechange"));
  });
  await expect.poll(() => page.evaluate(() => window.__transcriptionPeers.length)).toBe(2);
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(2);
});

test("permission denial is visible and releases ownership", async ({ page }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.evaluate(() => { window.__transcriptionMedia.deny = true; });
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Failed");
  await expect(page.getByTestId("transcription-transport-detail")).toContainText("permission denied");
  await expect(page.getByTestId("toggle-transcription")).toBeEnabled();
});

test("two Valerian pages elect one output owner for the same live behaviour", async ({ page, context }) => {
  const other = await context.newPage();
  const requests = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/speech")) requests.push(request);
  });
  other.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/speech")) requests.push(request);
  });
  await openConnectedValerian(page);
  await openConnectedValerian(other);

  await emitBehaviourSse(page, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("One owner."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  await emitBehaviourSse(other, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("One owner."));
  await expect(other.getByTestId("speech-playback-status")).toHaveText("Output In Other Window");
  expect(requests).toHaveLength(1);

  await page.evaluate(() => window.__finishSpeechPlayback());
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Playback Ready");
  await other.close();
});

test("Stop and synthesis failure both reopen live transcription input", async ({ page }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await emitBehaviourSse(page, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("Stop this output."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(false);
  await page.getByTestId("stop-speech-playback").click();
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Playback Stopped");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(true);

  await emitBehaviourSse(page, "behaviour-live", ERROR_BEHAVIOUR_ID, behaviourEvent("Provider failure."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Synthesis Error");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(true);
});

test("multilateral listener uses the same shared transcription engine", async ({ page }) => {
  await page.goto(`/multilateral/listen/?agentId=${AGENT_ID}&accessCode=${ACCESS_CODE}`);
  await expect(page.getByTestId("listen-transcription-settings")).toContainText("Provider transcription");
  await page.locator("#toggle_listen").click();
  await expect(page.locator("#listen_status")).toHaveText("Listening");
  await emitProviderEvent(page, { type: "input_audio_buffer.committed", event_id: "multi-c1", item_id: "multi-1" });
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.delta", event_id: "multi-d1", item_id: "multi-1", delta: "Meeting ",
  });
  await expect(page.locator("#live_transcript")).toHaveText("Meeting ");
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.completed", event_id: "multi-f1", item_id: "multi-1",
    transcript: "Meeting transcript.",
  });
  await expect(page.locator("#transcript_log .transcript-item")).toHaveCount(1);
  await expect(page.locator("#transcript_log")).toContainText("Meeting transcript.");
  await page.locator("#toggle_listen").click();
  await expect(page.locator("#listen_status")).toHaveText("Idle");
});

test("transcription settings states produce deterministic desktop and narrow visual artifacts", async ({ page }, testInfo) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await attach(page, testInfo, "transcription-settings-closed-desktop", page.locator("[data-column-panel=interaction]"));
  await page.getByTestId("live-transcription-settings-toggle").click();
  await attach(page, testInfo, "transcription-settings-open-desktop", page.getByTestId("live-transcription-settings-root"));
  await page.getByTestId("transcription-turnDetection-silenceDurationSeconds").fill("0.1");
  await page.getByTestId("transcription-turnDetection-silenceDurationSeconds").dispatchEvent("change");
  await expect(page.getByTestId("transcription-turnDetection-silenceDurationSeconds")).toHaveClass(/is-invalid/);
  await attach(page, testInfo, "transcription-settings-validation-desktop", page.getByTestId("live-transcription-settings-root"));
  await page.getByTestId("transcription-turnDetection-silenceDurationSeconds").fill("1.5");
  await page.getByTestId("transcription-turnDetection-silenceDurationSeconds").dispatchEvent("change");
  await page.getByTestId("toggle-transcription").click();
  await attach(page, testInfo, "transcription-listening-desktop", page.locator("[data-column-panel=interaction]"));

  await emitBehaviourSse(page, "behaviour-live", SLOW_BEHAVIOUR_ID, behaviourEvent("Visual speech state."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speech Loading");
  await attach(page, testInfo, "speech-loading-desktop", page.locator("[data-column-panel=interaction]"));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  await attach(page, testInfo, "speech-speaking-desktop", page.locator("[data-column-panel=interaction]"));
  await page.getByTestId("stop-speech-playback").click();
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Playback Stopped");
  await attach(page, testInfo, "speech-stopped-desktop", page.locator("[data-column-panel=interaction]"));

  await emitBehaviourSse(page, "behaviour-live", ERROR_BEHAVIOUR_ID, behaviourEvent("Visual provider failure."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Synthesis Error");
  await attach(page, testInfo, "speech-error-desktop", page.locator("[data-column-panel=interaction]"));

  await page.setViewportSize({ width: 390, height: 844 });
  await attach(page, testInfo, "transcription-settings-open-narrow", page.getByTestId("live-transcription-settings-root"));
  await attach(page, testInfo, "speech-error-narrow", page.locator("[data-column-panel=interaction]"));
});

async function openConnectedValerian(page) {
  await page.goto(`/valerian/?agentId=${AGENT_ID}`);
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
  await expect(page.getByTestId("agent-connection-state")).toContainText(AGENT_ID);
  await expect(page.getByTestId("live-transcription-settings-root")).toContainText("Provider transcription");
}

async function emitProviderEvent(page, event) {
  await page.evaluate((payload) => {
    const channel = window.__transcriptionChannels.at(-1);
    channel.dispatchEvent(new MessageEvent("message", { data: JSON.stringify(payload) }));
  }, event);
}

async function emitBehaviourSse(page, eventName, eventId, event) {
  await page.evaluate(({ eventName: name, eventId: id, envelope }) => {
    const source = window.__eventSources.find((candidate) => candidate.url.includes("/behaviour/stream"));
    source.emit(name, envelope, id);
  }, { eventName, eventId, envelope: event });
}

async function attach(page, testInfo, name, locator) {
  await locator.scrollIntoViewIfNeeded();
  await testInfo.attach(name, {
    body: await locator.screenshot({ animations: "disabled" }),
    contentType: "image/png",
  });
}

async function installApiMocks(context) {
  await context.route("**/demo/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === "POST" && path === "/demo/session") {
      return route.fulfill(json({ accessCode: ACCESS_CODE, agentTypes: [], agents: [AGENT] }));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/info`) return route.fulfill(json(AGENT));
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/eventhistory`) return route.fulfill(json([]));
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/storage`) return route.fulfill(json([]));
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/state`) {
      return route.fulfill(json({ name: "Listening", innerName: null, innerNames: [] }));
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/states`) return route.fulfill(json(["Listening"]));
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/behaviour/stream`) {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/monitor/stream`) {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    if (request.method() === "GET" && path === `/demo/agents/${AGENT_ID}/transcription/capabilities`) {
      return route.fulfill(json(capabilities()));
    }
    if (request.method() === "POST" && path === `/demo/agents/${AGENT_ID}/transcription/session`) {
      return route.fulfill(json({ clientSecret: "ephemeral-test", sessionType: "transcription",
        model: "gpt-live-transcribe", settingsSchemaVersion: 1,
        webRtcUrl: "https://api.openai.test/v1/realtime/calls", effectiveSettings: {} }));
    }
    if (request.method() === "POST" && path === `/demo/agents/${AGENT_ID}/acknowledge`) {
      return route.fulfill(json({ active: true, responseEvent: behaviourEvent() }));
    }
    if (request.method() === "POST" && path.endsWith("/speech")) {
      const eventId = path.split("/").at(-2);
      if (eventId === ERROR_BEHAVIOUR_ID) return route.fulfill({ status: 502, body: "" });
      if (eventId === SLOW_BEHAVIOUR_ID) await new Promise((resolve) => setTimeout(resolve, 500));
      return route.fulfill({ status: 200, contentType: "audio/mpeg", body: "mock-mp3-audio" });
    }
    if (request.method() === "POST" && path === `/demo/agents/${AGENT_ID}/behaviour/generate`) {
      return route.fulfill({ status: 200, body: "" });
    }
    return route.fulfill({ status: 404, body: "" });
  });
  await context.route("https://api.openai.test/**", (route) => route.fulfill({
    status: 200, contentType: "application/sdp", body: "mock-answer-sdp",
  }));
}

async function installBrowserMediaMocks(context) {
  await context.addInitScript(() => {
    window.__transcriptionSessionRequests = 0;
    window.__transcriptionChannels = [];
    window.__transcriptionPeers = [];
    window.__eventSources = [];
    window.__audioPlayback = { plays: 0, pauses: 0, sinkIds: [], revoked: [] };
    let objectUrlSequence = 0;
    URL.createObjectURL = () => `blob:mock-speech-${++objectUrlSequence}`;
    URL.revokeObjectURL = (url) => window.__audioPlayback.revoked.push(url);
    Object.defineProperty(HTMLMediaElement.prototype, "src", {
      configurable: true,
      get() { return this.__mockSpeechSrc || ""; },
      set(value) { this.__mockSpeechSrc = String(value || ""); },
    });
    HTMLMediaElement.prototype.play = function play() {
      window.__audioPlayback.plays += 1;
      queueMicrotask(() => this.dispatchEvent(new Event("playing")));
      return Promise.resolve();
    };
    HTMLMediaElement.prototype.pause = function pause() { window.__audioPlayback.pauses += 1; };
    HTMLMediaElement.prototype.load = function load() {};
    HTMLMediaElement.prototype.setSinkId = async function setSinkId(deviceId) {
      window.__audioPlayback.sinkIds.push(deviceId);
    };
    window.__finishSpeechPlayback = () => document.getElementById("assistant_audio")
      .dispatchEvent(new Event("ended"));
    class FakeEventSource extends EventTarget {
      static CONNECTING = 0; static OPEN = 1; static CLOSED = 2;
      constructor(url) {
        super();
        this.url = String(url);
        this.readyState = FakeEventSource.CONNECTING;
        window.__eventSources.push(this);
        queueMicrotask(() => {
          this.readyState = FakeEventSource.OPEN;
          this.dispatchEvent(new Event("open"));
        });
      }
      emit(name, value, eventId = "") {
        const event = new MessageEvent(name, { data: JSON.stringify(value) });
        Object.defineProperty(event, "lastEventId", { value: eventId });
        this.dispatchEvent(event);
      }
      close() { this.readyState = FakeEventSource.CLOSED; }
    }
    window.EventSource = FakeEventSource;
    class FakeTrack {
      constructor() { this.kind = "audio"; this.enabled = true; this.stopped = false; }
      stop() { this.stopped = true; }
      getSettings() { return { echoCancellation: true, noiseSuppression: true, autoGainControl: true, voiceIsolation: false }; }
    }
    window.__transcriptionMedia = { requests: [], tracks: [], deny: false };
    const mediaDevices = {
      async getUserMedia(constraints) {
        window.__transcriptionMedia.requests.push(structuredClone(constraints));
        if (window.__transcriptionMedia.deny) throw new Error("permission denied by test");
        const track = new FakeTrack();
        window.__transcriptionMedia.tracks.push(track);
        return { getTracks: () => [track], getAudioTracks: () => [track] };
      },
      async enumerateDevices() {
        return [
          { kind: "audioinput", deviceId: "default", label: "System default" },
          { kind: "audioinput", deviceId: "room-mic", label: "Room microphone" },
        ];
      },
      getSupportedConstraints() { return { echoCancellation: true, noiseSuppression: true, autoGainControl: true }; },
      addEventListener() {}, removeEventListener() {},
    };
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: mediaDevices });

    class FakeChannel extends EventTarget {
      constructor() { super(); this.readyState = "connecting"; this.sent = []; }
      send(value) { this.sent.push(value); }
      open() { this.readyState = "open"; this.dispatchEvent(new Event("open")); }
      close() { this.readyState = "closed"; this.dispatchEvent(new Event("close")); }
    }
    class FakePeer extends EventTarget {
      constructor() {
        super();
        this.connectionState = "new";
        this.iceConnectionState = "new";
        this.senders = [];
        window.__transcriptionPeers.push(this);
      }
      createDataChannel() {
        this.channel = new FakeChannel();
        window.__transcriptionChannels.push(this.channel);
        return this.channel;
      }
      addTrack(track) { this.senders.push({ track, replaceTrack: async (next) => { this.senders[0].track = next; } }); }
      getSenders() { return this.senders; }
      async createOffer() { return { type: "offer", sdp: "mock-offer-sdp" }; }
      async setLocalDescription() {}
      async setRemoteDescription() {
        this.connectionState = "connected";
        queueMicrotask(() => this.channel.open());
      }
      close() { this.connectionState = "closed"; }
    }
    window.RTCPeerConnection = FakePeer;

    const originalFetch = window.fetch.bind(window);
    window.fetch = async (...args) => {
      const url = String(args[0]);
      if (url.includes("/transcription/session")) window.__transcriptionSessionRequests += 1;
      return originalFetch(...args);
    };
    class FakeAudioContext {
      constructor() { this.state = "running"; }
      createMediaStreamSource() { return { connect() {}, disconnect() {} }; }
      createAnalyser() { return { fftSize: 1024, smoothingTimeConstant: 0, connect() {}, disconnect() {},
        getFloatTimeDomainData(values) { values.fill(0); } }; }
      async close() { this.state = "closed"; }
      async resume() { this.state = "running"; }
    }
    window.AudioContext = FakeAudioContext;
  });
}

function capabilities() {
  const base = { allowedValues: [], minimum: null, maximum: null, step: null, maxLength: null,
    maxItems: null, minItems: null, itemPattern: null, activeSessionBehavior: "live-input-boundary",
    visibleWhen: null, sensitive: false };
  return {
    schemaVersion: 1, sessionType: "transcription", model: "gpt-live-transcribe",
    capabilities: { assistantOutput: false, inputTranscription: true },
    settings: [
      { ...base, key: "noiseReduction", control: "select", defaultValue: "far_field", allowedValues: ["near_field", "far_field", "off"] },
      { ...base, key: "turnDetection.type", control: "select", defaultValue: "local_vad", allowedValues: ["local_vad", "manual"] },
      { ...base, key: "turnDetection.silenceDurationSeconds", control: "number", defaultValue: 1.5,
        minimum: 0.5, maximum: 10, step: 0.1, visibleWhen: "turnDetection.type=local_vad" },
      { ...base, key: "transcriptionPrompt", control: "text", defaultValue: "", maxLength: 1024, sensitive: true },
      { ...base, key: "transcriptionKeywords", control: "string-list", defaultValue: [], maxLength: 100,
        maxItems: 100, minItems: 0, itemPattern: "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._'/-]*$", sensitive: true },
      { ...base, key: "languages", control: "multi-select", defaultValue: ["de"], allowedValues: ["en", "de"], minItems: 1, maxItems: 2 },
      { ...base, key: "transcriptionDelay", control: "select", defaultValue: "medium", allowedValues: ["minimal", "low", "medium", "high", "xhigh"] },
    ],
  };
}

function json(body) {
  return { status: 200, contentType: "application/json", body: JSON.stringify(body) };
}

function behaviourEvent(speech = "Guten Morgen. I heard you clearly.") {
  return {
    type: "resp.behaviour_plan",
    actor: "assistant",
    kind: "response",
    createdDate: "2026-08-24T10:00:00Z",
    payload: JSON.stringify({
      speech,
      nonVerbal: { gesture: "ACKNOWLEDGE", facialExpression: { type: "warm", intensity: 0.7 },
        gaze: { direction: "forward", focus: "speaker" } },
      motion: { energy: 0.3 },
      display: { text: "Listening" },
    }),
  };
}
