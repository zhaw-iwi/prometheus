import { test, expect } from "@playwright/test";

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
  await page.getByTestId("connect-agent").click(); await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${agent.id}`);
  // The connection label precedes history hydration; wait for the real stream to open before sending input.
  await expect(page.locator("#behaviour_status")).toHaveText("Behaviour Live");
  await page.keyboard.press("Escape");
  if (!enabled) {
    await expect(page.getByTestId("gptlive-tab")).toBeHidden(); await expect(page.getByTestId("continuous-speech-tab")).toBeVisible();
    await page.getByTestId("text-input").fill("I am ready to start a round"); await page.getByTestId("send-text").click();
    await expect(page.getByTestId("round-value")).toHaveText("1");
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
    // Observe actual SSE separately as evidence; the cockpit's own EventSource also remains real.
    await page.evaluate(({ path, code }) => {
      window.__smokeEvents = []; window.__smokeSource = new EventSource(`${path}/behaviour/stream?accessCode=${code}&projection=conversation`);
      window.__smokeSource.addEventListener("behaviour-live", event => window.__smokeEvents.push(JSON.parse(event.data)));
    }, { path, code });
    expect((await request.post(`/__live-fixture/speak/${provider.providerId}`, { data: { speaker: "USER", text: "I am ready to start a round", receipt: "browser_user_one" } })).ok()).toBe(true);
    await expect(page.getByTestId("round-value")).toHaveText("1");
    await expect(page.getByTestId("gptlive-history")).toContainText("I am ready to start a round");
    await expect(page.getByTestId("gptlive-history")).toContainText("The round is ready.");
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
    await expect(page.locator("#gptlive_context")).toContainText("Capture: closed");
    expect((await (await request.get(`${path}/live/sessions/${session.handle}`, { headers: scoped })).json()).finalized).toBe(true);
    await page.getByTestId("gptlive-panel").screenshot({ path: info.outputPath("persisted-live-conversation.png") });
    await page.goto(`/valerian/?agentId=${agent.id}`); await expect(page.getByTestId("gptlive-start")).toBeEnabled(); await page.getByTestId("gptlive-tab").click();
    await expect(page.getByTestId("gptlive-history")).toContainText("I am ready to start a round");
    expect(await page.evaluate(() => window.__smokeCaptures)).toBe(0);
    await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const refreshed = await (await request.get("/__live-fixture/latest")).json();
    expect(refreshed.providerId).not.toBe(provider.providerId);
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
    expect(agent.interactionProfile.supportedObservations).toHaveLength(9);
    expect(agent.interactionProfile.supportedBehaviourModalities).toHaveLength(7);
    await page.getByTestId("connect-agent").click(); await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${agent.id}`);
    await page.keyboard.press("Escape");
    expect(await (await request.get(path + "/eventhistory", { headers: scoped })).json()).toHaveLength(0);
    await page.getByTestId("gptlive-tab").click(); await page.getByTestId("gptlive-start").click();
    await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    const provider = await (await request.get("/__live-fixture/latest")).json();
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
      close() {}
    };
  });
}
