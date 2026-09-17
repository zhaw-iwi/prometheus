import { expect, test } from "@playwright/test";

const ACCESS_CODE = "TRANSCRIBE";
const AGENT_ID = "11111111-1111-4111-8111-111111111111";
const SECOND_AGENT_ID = "22222222-2222-4222-8222-222222222222";
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
const SECOND_AGENT = {
  ...AGENT,
  id: SECOND_AGENT_ID,
  name: "Second Live Transcription Test Agent",
  languageCode: "en",
};

test.beforeEach(async ({ context }) => {
  await installApiMocks(context);
  await installBrowserMediaMocks(context);
});

test("history hydration and initial SSE replay render one assistant message", async ({ page }) => {
  const greeting = behaviourEvent("Welcome from persisted history.");
  await page.route(`**/demo/agents/${AGENT_ID}/eventhistory`, (route) => route.fulfill(json([greeting])));

  await openConnectedValerian(page);
  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);

  await emitBehaviourSse(page, "behaviour-replay", REPLAY_BEHAVIOUR_ID, greeting);

  await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);
  await expect(page.getByTestId("message-list")).toContainText("Welcome from persisted history.");
});

test("starting transcription speaks the latest persisted assistant utterance before opening input", async ({ page }) => {
  const greeting = behaviourEvent("Welcome back from persisted history.");
  const speechRequests = [];
  await page.route(`**/demo/agents/${AGENT_ID}/eventhistory`, (route) => route.fulfill(json([greeting])));
  await page.route(`**/demo/agents/${AGENT_ID}/behaviours/latest/speech`, (route) => route.fulfill(json({
    eventId: REPLAY_BEHAVIOUR_ID,
  })));
  page.on("request", (request) => {
    if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) {
      speechRequests.push(request);
    }
  });

  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();

  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  expect(speechRequests).toHaveLength(1);
  expect(new URL(speechRequests[0].url()).pathname)
    .toBe(`/demo/agents/${AGENT_ID}/behaviours/${REPLAY_BEHAVIOUR_ID}/speech`);
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(0);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests)).toHaveLength(0);

  await page.evaluate(() => window.__finishSpeechPlayback());
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(1);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests)).toHaveLength(1);

  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  expect(speechRequests).toHaveLength(2);
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(1);
  expect(await page.evaluate(() => window.__transcriptionMedia.requests)).toHaveLength(1);
  await page.evaluate(() => window.__finishSpeechPlayback());
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(2);
});

for (const delivery of ["before", "after"]) {
  test(`reset starter stays silent with SSE ${delivery} the reset response until transcription starts`, async ({ page }) => {
    const starter = behaviourEvent("A fresh reset greeting.");
    const speechRequests = [];
    let latestId = REPLAY_BEHAVIOUR_ID;
    await page.addInitScript(() => {
      localStorage.setItem("prometheus.valerian.speechVoice", "cedar");
      localStorage.setItem("prometheus.valerian.speechOutputSpeed", "1.25");
      localStorage.setItem("prometheus.valerian.speechOutputDevice", "room-speaker");
    });
    page.on("request", request => {
      if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) {
        speechRequests.push(request);
      }
    });
    await page.route(`**/demo/agents/${AGENT_ID}/behaviours/latest/speech`, route =>
      route.fulfill(json({ eventId: latestId })));
    await page.route(`**/demo/agents/${AGENT_ID}/reset`, async route => {
      if (delivery === "before") await emitBehaviourSse(page, "behaviour-live", REPLAY_BEHAVIOUR_ID, starter);
      await route.fulfill(json({ active: true, responseEvent: starter }));
    });
    await openConnectedValerian(page);
    await page.evaluate(() => { window.confirm = () => true; });
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("reset-agent").click();
    await expect(page.getByTestId("message-list")).toContainText("A fresh reset greeting.");
    if (delivery === "after") await emitBehaviourSse(page, "behaviour-live", REPLAY_BEHAVIOUR_ID, starter);
    await page.waitForTimeout(100);
    expect(speechRequests).toHaveLength(0);
    expect(await page.evaluate(() => window.__audioPlayback.plays)).toBe(0);
    await page.locator("#diagnostics_drawer .btn-close").click();
    await page.getByTestId("continuous-speech-tab").click();
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
    expect(speechRequests).toHaveLength(1);
    expect(new URL(speechRequests[0].url()).pathname).toContain(REPLAY_BEHAVIOUR_ID);
    expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(0);
    await page.evaluate(() => window.__finishSpeechPlayback());
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

    await emitBehaviourSse(page, "behaviour-live", LIVE_BEHAVIOUR_ID, behaviourEvent("An active speech reply."));
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
    expect(speechRequests).toHaveLength(2);
    for (const request of speechRequests) {
      expect(new URL(request.url()).searchParams.get("voice")).toBe("cedar");
      expect(new URL(request.url()).searchParams.get("speed")).toBe("1.25");
    }
    expect(await page.evaluate(() => window.__audioPlayback.sinkIds.filter(Boolean)))
      .toEqual(["room-speaker", "room-speaker"]);
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
    await emitBehaviourSse(page, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("A late reply after Stop."));
    await expect(page.getByTestId("message-list")).toContainText("A late reply after Stop.");
    await page.waitForTimeout(100);
    expect(speechRequests).toHaveLength(2);
    expect(await page.evaluate(() => window.__audioPlayback.plays)).toBe(2);

    latestId = SECOND_BEHAVIOUR_ID;
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
    expect(speechRequests).toHaveLength(3);
    expect(new URL(speechRequests[2].url()).pathname).toContain(SECOND_BEHAVIOUR_ID);
  });
}

for (const restart of [false, true]) {
  test(`stopped resume lookup cannot speak or reopen input${restart ? " after a new start" : ""}`, async ({ page }) => {
    let release;
    const held = new Promise(resolve => { release = resolve; });
    let lookups = 0;
    const speechRequests = [];
    page.on("request", request => {
      if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) speechRequests.push(request);
    });
    await page.route(`**/demo/agents/${AGENT_ID}/behaviours/latest/speech`, async route => {
      if (++lookups === 1) {
        await held;
        await route.fulfill(json({ eventId: REPLAY_BEHAVIOUR_ID }));
      } else await route.fulfill({ status: 204, body: "" });
    });
    await openConnectedValerian(page);
    await page.getByTestId("continuous-speech-tab").click();
    const lookup = page.waitForRequest(`**/behaviours/latest/speech`);
    await page.getByTestId("toggle-transcription").click();
    await lookup;
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
    if (restart) {
      await page.getByTestId("toggle-transcription").click();
      await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
    }
    const oldResponse = page.waitForResponse(`**/behaviours/latest/speech`);
    release();
    await oldResponse;
    await page.waitForTimeout(100);
    expect(speechRequests).toHaveLength(0);
    expect(await page.evaluate(() => window.__audioPlayback.plays)).toBe(0);
    expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(restart ? 1 : 0);
    await expect(page.getByTestId("transcription-transport-status"))
      .toHaveText(restart ? "Transcription Connected" : "Transcription Idle");
  });
}

test("Stop Speech during the starter still opens transcription input", async ({ page }) => {
  await page.route(`**/demo/agents/${AGENT_ID}/behaviours/latest/speech`, route =>
    route.fulfill(json({ eventId: REPLAY_BEHAVIOUR_ID })));
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  await page.getByTestId("stop-speech-playback").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  expect(await page.evaluate(() => window.__transcriptionSessionRequests)).toBe(1);
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).enabled)).toBe(true);
});

test("mocked WebRTC emits partial UI and one ordered finalized turn", async ({ page }) => {
  const acknowledgeRequests = [];
  const speechRequests = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/acknowledge")) acknowledgeRequests.push(request);
    if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) {
      speechRequests.push(request);
    }
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

  const timings = await page.evaluate(agentId => window.PrometheusTimings.snapshot(agentId), AGENT_ID);
  expect(timings).toHaveLength(1);
  expect(timings[0].id).toBe(acknowledgeRequests[0].headers()["x-prometheus-trace-id"]);
  expect(timings[0].eventId).toBe(LIVE_BEHAVIOUR_ID);
  for (const stage of ["final_transcript", "submitted", "acknowledging", "sse_received", "rendered",
    "audio_request", "audio_first_byte", "audio_downloaded", "audio_playing"]) {
    expect(timings[0].stages[stage], stage).toEqual(expect.any(Number));
  }
  expect(JSON.stringify(timings)).not.toContain("Guten Morgen");

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

for (const width of [1440, 390]) {
  test("interaction timing drawer exports correlated turns at " + width + "px", async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    let release;
    const hold = new Promise(resolve => { release = resolve; });
    const server = Buffer.from(JSON.stringify({ version: 1, durationMs: 4000, truncated: false, spans: [
      { stage: "inference", durationMs: 3500, offsetMs: 100, status: "ok", request: "fixture-inference",
        scope: "speculative", originTrace: "fixture-origin",
        purpose: "BEHAVIOUR", model: "fixture-model", effort: "none", promptTokens: 10, completionTokens: 2 },
      { stage: "speculation_started", durationMs: 0, status: "ok", request: "fixture-inference", purpose: "BEHAVIOUR" },
      { stage: "speculation_reused", durationMs: 0, status: "ok", request: "fixture-inference", purpose: "BEHAVIOUR" },
    ] })).toString("base64");
    await page.route("**/acknowledge?profile=full_plan", async route => {
      await hold;
      await route.fulfill({ ...json({ active: true, responseEvent: behaviourEvent() }), headers: {
        "X-Prometheus-Behaviour-Id": LIVE_BEHAVIOUR_ID,
        "X-Prometheus-Timing": server,
        "X-Prometheus-Trace-Id": route.request().headers()["x-prometheus-trace-id"],
      } });
    });
    await openConnectedValerian(page);
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("interaction-timing-tab").click();
    await expect(page.getByTestId("timing-count")).toContainText("No turns recorded");
    await expect(page.getByTestId("timing-export-json")).toBeDisabled();
    await page.locator("#diagnostics_drawer .btn-close").click();
    await page.getByTestId("continuous-speech-tab").click();
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
    // Feed the same local-VAD commit boundary used by the microphone; no real acoustic claim.
    await page.evaluate(() => {
      const runtime = transcription.transcriptionClient.events;
      const now = performance.now();
      // A partial can arrive while the person is still speaking.
      runtime.now = () => now - 800;
      runtime.handle({ type: "conversation.item.input_audio_transcription.delta",
        event_id: "timing-d1", item_id: "timing", delta: "Private partial" });
      runtime.now = () => performance.now();
      runtime.noteCommit({ lastVoiceAtMs: now - 500, observedAtMs: now, sentAtMs: now });
    });
    await emitProviderEvent(page, { type: "input_audio_buffer.committed", event_id: "timing-c", item_id: "timing" });
    await emitProviderEvent(page, { type: "conversation.item.input_audio_transcription.delta",
      event_id: "timing-d2", item_id: "timing", delta: " final words" });
    await emitProviderEvent(page, { type: "conversation.item.input_audio_transcription.completed",
      event_id: "timing-f", item_id: "timing", transcript: "Private spoken words for export exclusion." });
    await expect(page.getByTestId("transcription-ingress-status")).toHaveText("Processing turn");
    // Exercise audio delivery before acknowledgement correlates the event.
    await emitBehaviourSse(page, "behaviour-live", LIVE_BEHAVIOUR_ID, behaviourEvent("Private assistant reply."));
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
    release();
    await expect(page.getByTestId("transcription-ingress-status")).toHaveText("Transcript Accepted");
    await page.evaluate(() => window.__finishSpeechPlayback());
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Playback Ready");
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("interaction-timing-tab").click();
    const turns = page.getByTestId("timing-turns");
    await expect(turns.locator("details")).toHaveCount(1);
    await turns.locator("summary").click();
    await expect(turns).toContainText("fixture-model");
    await expect(turns).toContainText("3500.0 ms");
    await expect(turns).toContainText("Speculative response reused");
    await expect(turns).toContainText("Its duration is not additional turn latency.");
    await expect(turns).toContainText("buffered");
    await expect(turns).toContainText("Silence detection");
    await expect(turns).toContainText("Commit sent → acknowledgement");
    await expect(turns).toContainText("Provider commit acknowledgement received");
    await expect(turns).toContainText("Last transcript delta → final transcript");
    await expect(turns.getByRole("row", { name: "First transcript delta received -300.0 ms", exact: true })).toBeAttached();
    await expect(page.getByTestId("interaction-timing-panel")).toHaveCSS("opacity", "1");
    expect(await page.locator("#diagnostics_drawer").evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath("interaction-timing-" + width + ".png") });
    const jsonDownload = page.waitForEvent("download");
    await page.getByTestId("timing-export-json").click();
    const downloadedJson = await jsonDownload;
    const stream = await downloadedJson.createReadStream();
    const chunks = [];
    for await (const chunk of stream) chunks.push(chunk);
    const text = Buffer.concat(chunks).toString("utf8"), exported = JSON.parse(text);
    expect(exported.turns).toHaveLength(1);
    expect(exported.turns[0].requests.map(request => request.kind).sort()).toEqual(["acknowledge", "speech"]);
    expect(exported.turns[0].speech.playbackMode).toBe("buffered");
    expect(exported.turns[0].durationsMs.voiceResponse).toBeGreaterThanOrEqual(500);
    for (const metric of ["commitAcknowledgement", "acknowledgedFinal", "transcriptDeltas", "transcriptDeltaTail"]) {
      expect(exported.turns[0].durationsMs[metric]).toBeGreaterThanOrEqual(0);
    }
    for (const stage of ["commit_sent", "commit_acknowledged", "transcript_first_delta", "transcript_last_delta"]) {
      expect(Number.isFinite(exported.turns[0].stages[stage])).toBe(true);
    }
    expect(exported.turns[0].configuration.silenceDurationSeconds).toBe(1.5);
    expect(exported.turns[0].requests.find(request => request.kind === "acknowledge").server.spans[0].effort).toBe("none");
    expect(exported.turns[0].requests.find(request => request.kind === "acknowledge").server.spans[0].scope).toBe("speculative");
    expect(exported.turns[0].requests.find(request => request.kind === "acknowledge").server.spans[0].originTrace).toBe("fixture-origin");
    for (const excluded of ["Private spoken", "Private partial", "Private assistant", ACCESS_CODE, "ephemeral-test", "room-mic"]) {
      expect(text).not.toContain(excluded);
    }
    const csvDownload = page.waitForEvent("download");
    await page.getByTestId("timing-export-csv").click();
    expect((await csvDownload).suggestedFilename()).toMatch(/\.csv$/);
    const originalTiming = await page.evaluate(() => window.PrometheusTimings.snapshot());
    await page.locator("#diagnostics_drawer .btn-close").click();
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
    await page.route(`**/demo/agents/${AGENT_ID}/behaviours/latest/speech`, route =>
      route.fulfill(json({ eventId: LIVE_BEHAVIOUR_ID })));
    await page.getByTestId("toggle-transcription").click();
    await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
    await page.evaluate(() => window.__finishSpeechPlayback());
    await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
    expect(await page.evaluate(() => window.PrometheusTimings.snapshot())).toEqual(originalTiming);
    // A reset retains collected evidence. The explicit Clear action starts a fresh recording.
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("agent-drawer-tab").click();
    await page.evaluate(() => { window.confirm = () => true; });
    await page.getByTestId("reset-agent").click();
    await expect(page.getByTestId("transcription-transport-status")).toHaveText(/^(Transcription|Transport) Idle$/);
    await page.getByTestId("interaction-timing-tab").click();
    await expect(turns.locator("details")).toHaveCount(1);
    await page.getByTestId("timing-clear").click();
    await expect(turns.locator("details")).toHaveCount(0);
    await expect(page.getByTestId("timing-export-json")).toBeDisabled();
  });
}

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

test("microphone removal reconnects while device refresh and output routing remain usable", async ({ page }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("live-transcription-settings-toggle").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await page.evaluate(() => {
    window.__transcriptionMedia.devices.push(
      { kind: "audioinput", deviceId: "backup-mic", label: "Backup microphone" },
    );
    navigator.mediaDevices.dispatchDeviceChange();
  });
  await expect(page.getByTestId("transcription-input-device").locator('option[value="backup-mic"]')).toHaveCount(1);
  await page.getByTestId("speech-output-device").selectOption("room-speaker");
  await expect.poll(() => page.evaluate(() => window.__audioPlayback.sinkIds.at(-1))).toBe("room-speaker");

  await page.evaluate(() => window.__transcriptionMedia.tracks.at(-1).end());
  await expect.poll(() => page.evaluate(() => window.__transcriptionSessionRequests)).toBe(2);
  await expect.poll(() => page.evaluate(() => window.__transcriptionPeers.length)).toBe(2);
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks[0].stopped)).toBe(true);
});

test("hidden-tab input survives while refresh releases the microphone lease", async ({ page, context }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  const other = await context.newPage();
  await openConnectedValerian(other);
  await expect(other.getByTestId("toggle-transcription")).toBeDisabled();
  await expect(other.getByTestId("transcription-transport-status")).toHaveText("Mic In Use");

  await page.evaluate(() => window.__setDocumentVisibility("hidden"));
  await emitProviderEvent(page, { type: "input_audio_buffer.committed", event_id: "hidden-c1", item_id: "hidden-1" });
  await emitProviderEvent(page, {
    type: "conversation.item.input_audio_transcription.completed", event_id: "hidden-f1", item_id: "hidden-1",
    transcript: "Hidden tab transcript.",
  });
  await expect(page.getByTestId("message-list")).toContainText("Hidden tab transcript.");

  await page.reload();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
  await expect(page.getByTestId("agent-connection-state")).toContainText(AGENT_ID);
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transport Idle");
  await page.getByTestId("continuous-speech-tab").click();
  await expect(page.getByTestId("toggle-transcription")).toBeEnabled();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  await other.close();
});

test("reset, agent switch, and delete settle live transcription ownership", async ({ page }) => {
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await page.evaluate(() => { window.confirm = () => true; });
  await page.locator("#open_diagnostics").click();
  await expect(page.getByTestId("agent-drawer-tab")).toBeVisible();
  await page.getByTestId("reset-agent").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transport Idle");
  await expect.poll(() => page.evaluate(() => window.__transcriptionMedia.tracks.every((track) => track.stopped)))
    .toBe(true);
  await expect(page.getByTestId("agent-connection-state")).toContainText(AGENT_ID);

  await page.getByTestId("agent-select").selectOption(SECOND_AGENT_ID);
  await expect(page.getByTestId("agent-connection-state")).toHaveText(`Selected ${SECOND_AGENT_ID}`);
  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(SECOND_AGENT_ID);
  await page.locator("#diagnostics_drawer .btn-close").click();
  await expect(page.locator("#diagnostics_drawer")).not.toBeVisible();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await page.locator("#open_diagnostics").click();
  await page.getByTestId("agent-select").selectOption(AGENT_ID);
  await page.getByTestId("connect-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(AGENT_ID);
  await expect.poll(() => page.evaluate(() => window.__transcriptionMedia.tracks.every((track) => track.stopped)))
    .toBe(true);
  await page.locator("#diagnostics_drawer .btn-close").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await page.locator("#open_diagnostics").click();
  await page.getByTestId("delete-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toHaveText("No agent selected");
  await expect(page.getByTestId("toggle-transcription")).toBeDisabled();
  expect(await page.evaluate(() => window.__transcriptionMedia.tracks.every((track) => track.stopped))).toBe(true);
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

test("only the Valerian page with started transcription speaks a shared live behaviour", async ({ page, context }) => {
  const other = await context.newPage();
  const requests = [];
  page.on("request", (request) => {
    if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) requests.push(request);
  });
  other.on("request", (request) => {
    if (request.method() === "POST" && new URL(request.url()).pathname.endsWith("/speech")) requests.push(request);
  });
  await openConnectedValerian(page);
  await openConnectedValerian(other);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");

  await emitBehaviourSse(page, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("One owner."));
  await expect(page.getByTestId("speech-playback-status")).toHaveText("Speaking");
  await emitBehaviourSse(other, "behaviour-live", SECOND_BEHAVIOUR_ID, behaviourEvent("One owner."));
  await expect(other.getByTestId("message-list")).toContainText("One owner.");
  await expect(other.getByTestId("speech-playback-status")).toHaveText("Playback Ready");
  expect(await other.evaluate(() => window.__audioPlayback.plays)).toBe(0);
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
  await page.getByTestId("transcription-turn-preset").selectOption("responsive");
  const session = page.waitForRequest(request => request.method() === "POST" && request.url().endsWith("/transcription/session"));
  await page.locator("#toggle_listen").click();
  expect((await session).postDataJSON().transcriptionDelay).toBe("low");
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

test("conversation pace supports keyboard choice, retained reconnect settings and manual mode", async ({ page }, testInfo) => {
  const sessions = [];
  page.on("request", request => { if (request.method() === "POST" && request.url().endsWith("/transcription/session")) sessions.push(request.postDataJSON()); });
  await openConnectedValerian(page);
  await page.getByTestId("continuous-speech-tab").click();
  await page.getByTestId("live-transcription-settings-toggle").click();
  const preset = page.getByTestId("transcription-turn-preset");
  await expect(preset).toHaveValue("pause_tolerant");
  await preset.focus(); await preset.press("Home"); await preset.press("Enter");
  await expect(preset).toHaveValue("ultra_responsive");
  await expect(preset.locator("option:checked")).toHaveText("Ultra Responsive (0.5 s pause, minimal delay)");
  await expect(page.getByTestId("transcription-turnDetection-silenceDurationSeconds")).toHaveValue("0.5");
  await expect(page.getByTestId("transcription-transcriptionDelay")).toHaveValue("minimal");
  await page.getByTestId("transcription-languages").selectOption(["de", "en"]);
  await page.getByTestId("transcription-noiseReduction").selectOption("near_field");
  await attach(page, testInfo, "conversation-pace-desktop", preset.locator(".."));
  await page.setViewportSize({ width: 390, height: 900 });
  await attach(page, testInfo, "conversation-pace-mobile", preset.locator(".."));
  await page.getByTestId("toggle-transcription").click();
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  await expect(preset).toBeDisabled();
  expect(sessions[0]).toMatchObject({ turnDetection: { type: "local_vad", silenceDurationSeconds: 0.5 }, transcriptionDelay: "minimal", languages: ["de", "en"], noiseReduction: "near_field" });
  await page.evaluate(() => {
    const peer = window.__transcriptionPeers.at(-1);
    peer.connectionState = "failed"; peer.dispatchEvent(new Event("connectionstatechange"));
  });
  await expect.poll(() => sessions.length).toBe(2);
  expect(sessions[1]).toEqual(sessions[0]);
  await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
  await page.getByTestId("toggle-transcription").click();
  await page.getByTestId("transcription-turnDetection-type").selectOption("manual");
  await expect(preset).not.toBeVisible();
  await expect(page.getByTestId("transcription-transcriptionDelay")).toHaveValue("minimal");
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

for (const width of [1440, 390]) {
  test(`speech controls remain usable through loading, speaking, Stop and failure at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    let release;
    const ready = new Promise(resolve => { release = resolve; });
    await page.route(`**/behaviours/${SLOW_BEHAVIOUR_ID}/speech*`, async route => {
      await ready;
      await route.fulfill({ status: 200, contentType: "audio/mpeg", body: Buffer.from([1, 2, 3]) });
    });
    await openConnectedValerian(page);
    await page.getByTestId("continuous-speech-tab").click();
    await page.getByTestId("toggle-transcription").click();
    await emitBehaviourSse(page, "behaviour-live", SLOW_BEHAVIOUR_ID, behaviourEvent("Visual speech state."));
    const status = page.getByTestId("speech-playback-status"), stop = page.getByTestId("stop-speech-playback");
    const row = status.locator("..");
    try {
      await expect(status).toHaveText("Speech Loading"); await expect(stop).toBeEnabled();
      await attach(page, testInfo, `speech-loading-${width}`, row);
    } finally { release(); }
    await expect(status).toHaveText("Speaking");
    await attach(page, testInfo, `speech-speaking-${width}`, row);
    await stop.click(); await expect(status).toHaveText("Playback Stopped"); await expect(stop).toBeDisabled();
    await attach(page, testInfo, `speech-stopped-${width}`, row);
    await emitBehaviourSse(page, "behaviour-live", ERROR_BEHAVIOUR_ID, behaviourEvent("Visual provider failure."));
    await expect(status).toHaveText("Synthesis Error");
    await attach(page, testInfo, `speech-failed-${width}`, row);
  });
}

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
  const path = testInfo.outputPath(`${name}.png`);
  await locator.screenshot({ path, animations: "disabled" });
  await testInfo.attach(name, {
    path,
    contentType: "image/png",
  });
}

async function installApiMocks(context) {
  await context.route("**/demo/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const agentMatch = path.match(/^\/demo\/agents\/([^/]+)(\/.*)?$/);
    const scopedAgentId = agentMatch ? decodeURIComponent(agentMatch[1]) : null;
    const scopedPath = agentMatch?.[2] || "";
    const agent = scopedAgentId === AGENT_ID
      ? AGENT
      : scopedAgentId === SECOND_AGENT_ID ? SECOND_AGENT : null;
    if (request.method() === "POST" && path === "/demo/session") {
      return route.fulfill(json({ accessCode: ACCESS_CODE, agentTypes: [], agents: [AGENT, SECOND_AGENT] }));
    }
    if (request.method() === "POST" && scopedPath.endsWith("/speech")) {
      const eventId = scopedPath.split("/").at(-2);
      if (eventId === ERROR_BEHAVIOUR_ID) return route.fulfill({ status: 502, body: "" });
      if (eventId === SLOW_BEHAVIOUR_ID) await new Promise((resolve) => setTimeout(resolve, 500));
      return route.fulfill({ status: 200, contentType: "audio/mpeg", body: "mock-mp3-audio" });
    }
    if (!agent) return route.fulfill({ status: 404, body: "" });
    if (request.method() === "GET" && scopedPath === "/info") return route.fulfill(json(agent));
    if (request.method() === "GET" && scopedPath === "/eventhistory") return route.fulfill(json([]));
    if (request.method() === "GET" && scopedPath === "/storage") return route.fulfill(json([]));
    if (request.method() === "GET" && scopedPath === "/state") {
      return route.fulfill(json({ name: "Listening", innerName: null, innerNames: [] }));
    }
    if (request.method() === "GET" && scopedPath === "/states") return route.fulfill(json(["Listening"]));
    if (request.method() === "GET" && scopedPath === "/behaviour/stream") {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    if (request.method() === "GET" && scopedPath === "/monitor/stream") {
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: ": connected\n\n" });
    }
    if (request.method() === "GET" && scopedPath === "/transcription/capabilities") {
      return route.fulfill(json(capabilities()));
    }
    if (request.method() === "POST" && scopedPath === "/transcription/session") {
      return route.fulfill(json({ clientSecret: "ephemeral-test", sessionType: "transcription",
        model: "gpt-live-transcribe", settingsSchemaVersion: 1,
        webRtcUrl: "https://api.openai.test/v1/realtime/calls", effectiveSettings: {} }));
    }
    if (request.method() === "GET" && scopedPath === "/behaviours/latest/speech") {
      return route.fulfill({ status: 204, body: "" });
    }
    if (request.method() === "POST" && scopedPath === "/acknowledge") {
      return route.fulfill({ ...json({ active: true, responseEvent: behaviourEvent() }),
        headers: { "X-Prometheus-Behaviour-Id": LIVE_BEHAVIOUR_ID } });
    }
    if (request.method() === "DELETE" && scopedPath === "/reset") {
      return route.fulfill(json({ active: true, responseEvent: null }));
    }
    if (request.method() === "DELETE" && scopedPath === "") {
      return route.fulfill({ status: 204, body: "" });
    }
    if (request.method() === "POST" && scopedPath === "/behaviour/generate") {
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
    // This suite mocks decoding; native MSE is covered by progressive-speech.spec.mjs.
    window.MediaSource = undefined;
    window.__transcriptionSessionRequests = 0;
    window.__transcriptionChannels = [];
    window.__transcriptionPeers = [];
    window.__eventSources = [];
    window.__audioPlayback = { plays: 0, pauses: 0, sinkIds: [], revoked: [] };
    let objectUrlSequence = 0;
    const createObjectURL = URL.createObjectURL.bind(URL), revokeObjectURL = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = (blob) => blob.type === "application/json" || blob.type?.startsWith("text/csv")
      ? createObjectURL(blob) : `blob:mock-speech-${++objectUrlSequence}`;
    URL.revokeObjectURL = (url) => { window.__audioPlayback.revoked.push(url); revokeObjectURL(url); };
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
    let documentVisibility = "visible";
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => documentVisibility,
    });
    window.__setDocumentVisibility = (value) => {
      documentVisibility = value;
      document.dispatchEvent(new Event("visibilitychange"));
    };
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
    class FakeTrack extends EventTarget {
      constructor(deviceId) {
        super();
        this.kind = "audio";
        this.deviceId = deviceId;
        this.enabled = true;
        this.stopped = false;
      }
      stop() { this.stopped = true; }
      end() { this.dispatchEvent(new Event("ended")); }
      getSettings() {
        return { deviceId: this.deviceId, echoCancellation: true, noiseSuppression: true,
          autoGainControl: true, voiceIsolation: false };
      }
    }
    window.__transcriptionMedia = {
      requests: [],
      tracks: [],
      deny: false,
      devices: [
        { kind: "audioinput", deviceId: "default", label: "System default" },
        { kind: "audioinput", deviceId: "room-mic", label: "Room microphone" },
        { kind: "audiooutput", deviceId: "room-speaker", label: "Room speaker" },
      ],
    };
    const mediaDeviceEvents = new EventTarget();
    const mediaDevices = {
      async getUserMedia(constraints) {
        window.__transcriptionMedia.requests.push(structuredClone(constraints));
        if (window.__transcriptionMedia.deny) throw new Error("permission denied by test");
        const deviceId = constraints.audio?.deviceId?.exact || "default";
        const track = new FakeTrack(deviceId);
        window.__transcriptionMedia.tracks.push(track);
        return { getTracks: () => [track], getAudioTracks: () => [track] };
      },
      async enumerateDevices() {
        return structuredClone(window.__transcriptionMedia.devices);
      },
      getSupportedConstraints() { return { echoCancellation: true, noiseSuppression: true, autoGainControl: true }; },
      addEventListener(...args) { mediaDeviceEvents.addEventListener(...args); },
      removeEventListener(...args) { mediaDeviceEvents.removeEventListener(...args); },
      dispatchDeviceChange() { mediaDeviceEvents.dispatchEvent(new Event("devicechange")); },
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
      { ...base, key: "languages", control: "multi-select", defaultValue: ["ar"], allowedValues: ["ar", "de", "en"], minItems: 1, maxItems: 3 },
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
