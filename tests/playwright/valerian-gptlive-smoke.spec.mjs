import { test, expect } from "./fixtures/offline-ui-assets.mjs";

// This suite must run only via tests/gptlive/run_acceptance.py. No PROMETHEUS HTTP or SSE mocks.
const enabled = process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== "false";
test("real scoped cockpit, durable Live conversation and legacy feature-off speech", async ({ page, context, request }, info) => {
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || !process.env.PROMETHEUS_LIVE_EXPECT_ENABLED, "Use the isolated acceptance runner.");
  const headers = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const created = await request.post("/admin/access-codes", { headers, data: { code, enabled: true } }); expect(created.status()).toBe(201);
  const access = await created.json();
  expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, { headers, data: { agentTypeKeys: ["core.rock_scissor_paper"] } })).ok()).toBe(true);
  const scoped = { "X-Prometheus-Access-Code": code };
  await hardware(context);
  await page.goto("/valerian/"); await page.getByTestId("access-code-input").fill(code); await page.getByTestId("submit-access-code").click();
  await page.locator("#open_diagnostics").click(); await page.getByTestId("agent-type-select").selectOption("core.rock_scissor_paper");
  const creation = page.waitForResponse(response => response.url().endsWith("/demo/agents") && response.request().method() === "POST");
  await page.getByTestId("create-agent-instance").click(); const agent = await (await creation).json(); const path = `/demo/agents/${agent.id}`;
  expect(agent.interactionProfile.capabilityAwareness).toBe(true);
  expect((await (await request.get("/__live-fixture/latest")).json()).behaviourContext).toContain("AGENT CAPABILITIES");
  await page.getByTestId("connect-agent").click(); await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${agent.id}`);
  // The connection label precedes history hydration; wait for the real stream to open before sending input.
  await expect(page.locator("#behaviour_status")).toHaveText("Behaviour Live");
  await page.keyboard.press("Escape");
  if (!enabled) {
    await expect(page.getByTestId("gptlive-tab")).toBeHidden(); await expect(page.getByTestId("continuous-speech-tab")).toBeVisible();
    await page.waitForFunction(() => state.monitorReady);
    const detailReads = [];
    page.on("request", request => { if (/\/(state|storage)$/.test(new URL(request.url()).pathname)) detailReads.push(request.url()); });
    await page.getByTestId("text-input").fill("I am ready to start a round"); await page.getByTestId("send-text").click();
    await expect(page.getByTestId("round-value")).toHaveText("1");
    await expect(page.getByTestId("send-text")).toBeEnabled();
    expect(detailReads).toHaveLength(0); // Real monitor SSE supplied the updated state/storage.
    const history = await (await request.get(path + "/eventhistory", { headers: scoped })).json();
    expect(history.some(event => event.type === "resp.behaviour_plan" && JSON.parse(event.payload).speech)).toBe(true);
    const speech = await (await request.get(path + "/behaviours/latest/speech", { headers: scoped })).json();
    const audio = await request.post(`${path}/behaviours/${speech.eventId}/speech?format=pcm`, { headers: scoped });
    expect(audio.ok()).toBe(true); expect((await audio.body()).length).toBe(4800);
    expect((await (await request.get("/__live-fixture/latest")).json()).ttsCalls).toBe(1);
    await page.locator('[data-column-panel="interaction"]').screenshot({ path: info.outputPath("feature-off-text.png") });
  } else {
    await expect(page.getByTestId("gptlive-start")).toBeEnabled(); await page.getByTestId("gptlive-tab").click();
    const liveCreation = page.waitForResponse(response => response.url().endsWith("/live/sessions") && response.status() === 201);
    await page.getByTestId("gptlive-start").click(); const session = await (await liveCreation).json();
    await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const provider = await (await request.get("/__live-fixture/latest")).json();
    expect(provider.instructions).toContain("AGENT CAPABILITIES");
    // Observe actual SSE separately as evidence; the cockpit's own EventSource also remains real.
    await page.evaluate(({ path, code }) => {
      window.__smokeEvents = []; window.__smokeSource = new EventSource(`${path}/behaviour/stream?accessCode=${code}&projection=conversation`);
      window.__smokeSource.addEventListener("behaviour-live", event => window.__smokeEvents.push(JSON.parse(event.data)));
    }, { path, code });
    expect((await request.post(`/__live-fixture/speak/${provider.providerId}`, { data: { speaker: "USER", text: "I am ready to start a round", receipt: "browser_user_one" } })).ok()).toBe(true);
    await expect(page.getByTestId("round-value")).toHaveText("1");
    await expect(page.getByTestId("gptlive-history")).toContainText("I am ready to start a round");
    await expect(page.getByTestId("gptlive-history")).toContainText("The round is ready.");
    await expect.poll(async () => page.evaluate(async () => {
      const { liveDiagnostics } = await import("/live/diagnostics.js");
      return liveDiagnostics.snapshot().sessions.some(session => session.media.some(value => value.concealedSamples === 960));
    })).toBe(true);
    const acknowledge = async (type, payload) => expect((await request.post(path + "/acknowledge", { headers: scoped,
      data: { type, actor: "sensor", kind: "observation", payload: JSON.stringify(payload) } })).ok()).toBe(true);
    await acknowledge("obs.weather.current", { condition: "rain", temperature: 17 });
    await expect.poll(async () => JSON.stringify((await (await request.get("/__live-fixture/latest")).json()).commands)).toContain("obs.weather.current");
    await acknowledge("obs.hand.sign", { sign: "rock", confidence: 1 });
    await expect(page.getByTestId("display-value")).toContainText('"winner"');
    await expect.poll(async () => (await page.evaluate(() => window.__smokeEvents)).filter(event => event.provenance?.origin === "NATIVE").length).toBeGreaterThan(0);
    const history = await (await request.get(path + "/live/history", { headers: scoped })).json();
    expect(history.filter(event => event.type === "obs.user_utterance")).toHaveLength(1);
    expect(history.some(event => event.provenance?.origin === "BACKEND_INTENT" && event.plannedSpeech)).toBe(true);
    expect(history.some(event => event.provenance?.origin === "NATIVE" && event.provenance.segmentId)).toBe(true);
    const ledger = await (await request.get(`${path}/live/transcripts?sessionId=${session.handle}`, { headers: scoped })).json();
    expect(ledger.some(value => value.speaker === "USER" && value.status === "COMPLETE" && value.receiptIds.includes("browser_user_one"))).toBe(true);
    const storage = await (await request.get(path + "/storage", { headers: scoped })).json();
    expect(JSON.parse(storage.find(value => value.key === "rps_rounds").value)).toHaveLength(1);
    expect((await (await request.get("/__live-fixture/latest")).json()).ttsCalls).toBe(0);
    await page.getByTestId("gptlive-stop").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Idle");
    await expect.poll(() => page.evaluate(async () => (await import("/live/diagnostics.js")).liveDiagnostics.snapshot().sessions.at(-1)?.server?.captureState)).toBe("closed");
    expect((await (await request.get(`${path}/live/sessions/${session.handle}`, { headers: scoped })).json()).finalized).toBe(true);
    const diagnostics = await page.evaluate(async () => (await import("/live/diagnostics.js")).liveDiagnostics.snapshot());
    const trial = diagnostics.sessions.find(value => value.handle === session.handle);
    expect(trial.server.audio.recent.some(value => value.inputSamples > 0)).toBe(true);
    expect(trial.media.some(value => value.outcome === "local_stop")).toBe(true);
    expect(JSON.stringify(diagnostics)).not.toContain("I am ready to start a round");
    expect(JSON.stringify(diagnostics)).not.toContain("private-device");
    await page.getByTestId("gptlive-panel").screenshot({ path: info.outputPath("persisted-live-conversation.png") });
    await page.goto(`/valerian/?agentId=${agent.id}`); await expect(page.getByTestId("gptlive-start")).toBeEnabled(); await page.getByTestId("gptlive-tab").click();
    await expect(page.getByTestId("gptlive-history")).toContainText("I am ready to start a round");
    expect(await page.evaluate(() => window.__smokeCaptures)).toBe(0);
    await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const refreshed = await (await request.get("/__live-fixture/latest")).json();
    expect(refreshed.providerId).not.toBe(provider.providerId);
    expect(refreshed.instructions).toContain("AGENT CAPABILITIES");
    await page.getByTestId("gptlive-stop").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Idle");
  }
  expect((await request.delete(path, { headers: scoped })).status()).toBe(204);
});

test("Live Multimodal exposes sensors and keeps embodiment beside native speech", async ({ page, context, request }, info) => {
  test.skip(!enabled || !process.env.PROMETHEUS_ADMIN_TOKEN || !process.env.PROMETHEUS_LIVE_EXPECT_ENABLED, "Requires the isolated Live-enabled app.");
  const headers = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const created = await request.post("/admin/access-codes", { headers, data: { code, enabled: true } });
  expect(created.status()).toBe(201); const access = await created.json();
  expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, { headers, data: { agentTypeKeys: ["core.live_multimodal"] } })).ok()).toBe(true);
  const scoped = { "X-Prometheus-Access-Code": code };
  await hardware(context);
  await page.goto("/valerian/"); await page.getByTestId("access-code-input").fill(code); await page.getByTestId("submit-access-code").click();
  await page.locator("#open_diagnostics").click(); await page.getByTestId("agent-type-select").selectOption("core.live_multimodal");
  const creation = page.waitForResponse(response => response.url().endsWith("/demo/agents") && response.request().method() === "POST");
  await page.getByTestId("create-agent-instance").click(); const agent = await (await creation).json(); const path = `/demo/agents/${agent.id}`;
  try {
    expect(agent.interactionProfile.capabilityAwareness).toBe(true);
    expect(agent.interactionProfile.supportedObservations).toHaveLength(9);
    expect(agent.interactionProfile.supportedBehaviourModalities).toHaveLength(7);
    await page.getByTestId("connect-agent").click(); await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${agent.id}`);
    await page.keyboard.press("Escape");
    expect(await (await request.get(path + "/eventhistory", { headers: scoped })).json()).toHaveLength(0);
    await page.getByTestId("gptlive-tab").click(); await page.getByTestId("gptlive-start").click();
    await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const provider = await (await request.get("/__live-fixture/latest")).json();
    expect(provider.instructions).toContain("AGENT CAPABILITIES");
    expect((await request.post(path + "/acknowledge", { headers: scoped, data: {
      type: "obs.weather.current", actor: "sensor", kind: "observation",
      payload: JSON.stringify({ location_label: "Winterthur", condition: "rain", temperature_c: 17, observed_at: new Date().toISOString() }),
    } })).ok()).toBe(true);
    await expect.poll(async () => (await (await request.get("/__live-fixture/latest")).json()).commands.join("\n")).toContain("Winterthur");
    expect((await request.post(`/__live-fixture/speak/${provider.providerId}`, { data: {
      speaker: "USER", text: "Show paper and a visual caption", receipt: "multimodal_browser_user",
    } })).ok()).toBe(true);
    await expect(page.getByTestId("gesture-value")).toHaveText("Acknowledgement");
    await expect(page.getByTestId("face-value")).toHaveText("attentive");
    await expect(page.getByTestId("gaze-value")).toHaveText("toward_user");
    await expect(page.getByTestId("motion-energy-value")).toHaveText("20%");
    await expect(page.getByTestId("display-value")).toContainText("Ready");
    const history = await (await request.get(path + "/eventhistory", { headers: scoped })).json();
    const plans = history.filter(event => event.type === "resp.behaviour_plan").map(event => JSON.parse(event.payload));
    expect(plans.length).toBeGreaterThan(0); expect(plans.every(plan => !plan.speech)).toBe(true);
    expect((await request.post(`/__live-fixture/speak/${provider.providerId}`, { data: {
      speaker: "ASSISTANT", text: "Here is a small demonstration", receipt: "multimodal_browser_assistant",
    } })).ok()).toBe(true);
    await expect(page.getByTestId("gptlive-history")).toContainText("Here is a small demonstration");
    await expect(page.getByTestId("gesture-value")).toHaveText("Acknowledgement");
    await expect(page.getByTestId("display-value")).toContainText("Ready");
    expect((await (await request.get("/__live-fixture/latest")).json()).ttsCalls).toBe(0);
    await page.screenshot({ path: info.outputPath("live-multimodal-desktop.png"), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('[data-column-panel="behaviour"]').screenshot({ path: info.outputPath("live-multimodal-mobile-behaviour.png") });
    await page.getByTestId("gptlive-stop").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Idle");
  } finally {
    expect((await request.delete(path, { headers: scoped })).status()).toBe(204);
  }
});

test("Generic activation telemetry follows real pending work, cue waiting, failure and reset", async ({ page, context, request }, info) => {
  test.skip(!enabled || !process.env.PROMETHEUS_ADMIN_TOKEN || !process.env.PROMETHEUS_LIVE_EXPECT_ENABLED, "Requires the isolated Live-enabled app.");
  const admin = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const access = await (await request.post("/admin/access-codes", { headers: admin, data: { code, enabled: true } })).json();
  expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, { headers: admin, data: { agentTypeKeys: ["core.generic_multimodal_behaviour"] } })).ok()).toBe(true);
  const scoped = { "X-Prometheus-Access-Code": code };
  const agent = await (await request.post("/demo/agents", { headers: scoped, data: { agentDefinitionKey: "core.generic_multimodal_behaviour" } })).json();
  const path = `/demo/agents/${agent.id}`;
  const control = action => request.post(`/__live-fixture/inference/${action}`);
  const snapshot = () => page.evaluate(() => window.PrometheusActivity.snapshot());
  await hardware(context);
  try {
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId("access-code-input").fill(code); await page.getByTestId("submit-access-code").click();
    await expect(page.getByTestId("gptlive-start")).toBeEnabled();
    await page.getByTestId("gptlive-tab").click();
    const liveCreation = page.waitForResponse(response => response.url().endsWith("/live/sessions") && response.status() === 201);
    await page.getByTestId("gptlive-start").click(); const liveSession = await (await liveCreation).json();
    await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const provider = await (await request.get("/__live-fixture/latest")).json();
    await control("hold");
    expect((await request.post(`/__live-fixture/speak/${provider.providerId}`, { data: { speaker: "USER", text: "Activate the private fixture task", receipt: "activation_receipt" } })).ok()).toBe(true);
    await expect(page.getByTestId("activity-label")).toHaveText("Thinking");
    const inFlight = (await snapshot()).journals.at(-1);
    expect(inFlight.active.some(value => value.stage === "thinking")).toBe(true);
    expect(inFlight.recent.some(value => value.sourceId && value.sessionId)).toBe(true);
    const overlap = request.post(path + "/behaviour/generate", { headers: scoped });
    await expect.poll(async () => (await snapshot()).journals.at(-1).active.length).toBeGreaterThanOrEqual(2);
    await expect(page.getByTestId("activity-label")).toHaveText("Thinking");
    await page.getByTestId("interaction-activity").screenshot({ path: info.outputPath("real-held-activation.png") });
    await page.evaluate(() => window.PrometheusActivity.mark("long_pause"));
    await control("release"); expect([200, 409]).toContain((await overlap).status()); await control("clear");
    await expect(page.getByTestId("gptlive-history")).toContainText("The round is ready.");
    await expect.poll(async () => (await (await request.get(path + "/storage", { headers: scoped })).json()).find(value => value.key === "task.phase")?.value).toBe('"RUNNING"');
    await expect.poll(async () => (await snapshot()).journals.at(-1).active.length).toBe(0);
    await expect.poll(async () => (await (await request.get(path + "/live/transcripts?sessionId=" + liveSession.handle, { headers: scoped })).json()).some(value => value.speaker === "ASSISTANT" && value.status === "COMPLETE")).toBe(true);
    const observe = () => request.post(path + "/acknowledge", { headers: scoped, data: {
      type: "obs.emotion.face", actor: "sensor", kind: "observation", payload: JSON.stringify({ valence: -.8, confidence: .1, facePresent: true, ts: new Date().toISOString() }),
    } });
    // Exercise existing cooldown before fresh weak evidence; this does not bypass task guards.
    await expect.poll(async () => { await observe(); return (await snapshot()).journals.at(-1).cue?.reason; }).toBe("low_confidence");
    await expect(page.getByTestId("activity-label")).toHaveText("Waiting for a clearer cue");
    await expect(page.getByTestId("interaction-activity")).toHaveAttribute("data-busy", "false");
    await request.post(`/__live-fixture/usage/${provider.providerId}`);
    await expect.poll(() => page.evaluate(async () => (await import("/live/diagnostics.js")).liveDiagnostics.snapshot().sessions.at(-1).provider?.usageSeconds)).toBe(12.5);
    expect(await page.evaluate(async () => (await import("/live/diagnostics.js")).liveDiagnostics.snapshot().sessions.at(-1).provider.configuration.silenceMs)).toBe(800);
    const recording = await snapshot();
    expect(recording.markers[0].operationIds.length).toBeGreaterThan(0);
    expect(JSON.stringify(recording)).not.toContain("private fixture task");
    expect(recording.journals.at(-1).recent.some(value => value.stage === "inference" && value.details.purpose === "EXTRACTION")).toBe(true);
    await page.getByTestId("gptlive-stop").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Idle");
    await control("hold");
    const failure = request.post(path + "/acknowledge", { headers: scoped, data: { type: "obs.user_utterance", actor: "user", kind: "observation", payload: "Change the private fixture task" } });
    await expect(page.getByTestId("activity-label")).toHaveText("Thinking");
    await control("fail"); expect((await failure).status()).toBe(500); await control("clear");
    await expect(page.getByTestId("activity-label")).toHaveText("Processing failed");
    expect((await snapshot()).journals.at(-1).active).toHaveLength(0);
    const oldEpoch = (await snapshot()).journals.at(-1).epoch;
    await page.locator("#open_diagnostics").click();
    page.once("dialog", dialog => dialog.accept()); await page.getByTestId("reset-agent").click();
    await expect.poll(async () => (await snapshot()).journals.at(-1).epoch).not.toBe(oldEpoch);
    await expect(page.getByTestId("activity-label")).toHaveText("Ready to configure");
  } finally {
    await control("clear"); await request.delete(path, { headers: scoped });
  }
});

async function hardware(context) {
  await context.addInitScript(() => {
    window.__smokeCaptures = 0;
    Object.defineProperty(HTMLMediaElement.prototype, "srcObject", { configurable: true, get() { return this.__stream || null; }, set(value) { this.__stream = value; } });
    HTMLMediaElement.prototype.play = async function () {}; HTMLMediaElement.prototype.pause = function () {};
    HTMLMediaElement.prototype.setSinkId = async function () {};
    class Track extends EventTarget { constructor() { super(); this.kind = "audio"; this.enabled = true; } stop() {} getSettings() { return { echoCancellation: true }; } }
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: {
      async getUserMedia() { window.__smokeCaptures++; const track = new Track(); return { getAudioTracks: () => [track], getTracks: () => [track] }; },
      async enumerateDevices() { return []; }, addEventListener() {}, removeEventListener() {}, getSupportedConstraints() { return {}; },
    } });
    window.RTCPeerConnection = class {
      constructor() { this.iceGatheringState = "complete"; }
      createDataChannel() { return this.channel = { close() {} }; } addTrack() {} getSenders() { return []; }
      async createOffer() { return { type: "offer", sdp: "v=0 synthetic-offer" }; } async setLocalDescription(value) { this.localDescription = value; }
      async setRemoteDescription() { this.channel.onmessage?.({ data: '{"type":"session.started"}' }); this.ontrack?.({ streams: [{}], track: new Track() }); }
      async getStats() { return new Map([["private-device", { type: "inbound-rtp", kind: "audio", timestamp: performance.now(),
        packetsLost: 2, concealedSamples: 960, jitter: .01, trackIdentifier: "private-device" }]]); }
      close() {}
    };
  });
}
