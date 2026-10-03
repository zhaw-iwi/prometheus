import { test, expect } from "./fixtures/offline-ui-assets.mjs";

const ID = "11111111-1111-4111-8111-111111111111";
const AGENT = { id: ID, name: "Valerian voice pilot", description: "Multimodal conversation", active: true, languageCode: "en",
  interactionProfile: { supportedObservations: ["obs.user_utterance", "obs.emotion.face"], supportedBehaviourModalities: ["speech", "nonVerbal.gesture", "display"], profileTags: [] } };
const json = body => ({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
const event = (id, speech, provenance = null) => ({ id, type: "resp.behaviour_plan", actor: "assistant", kind: "response", payload: JSON.stringify({ speech }), provenance, createdDate: "2026-09-28T10:00:00Z" });

test("text fallback with no generated behaviour returns to Ready", async ({ page, context }) => {
  await setup(context); await open(page); await page.getByTestId("text-interaction-tab").click();
  await page.route("**/acknowledge", route => route.fulfill(json({ active: true, responseEvent: null })));
  await page.route("**/behaviour/generate", route => route.fulfill({ status: 409 }));
  await page.getByTestId("text-input").fill("Nothing to add"); await page.getByTestId("send-text").click();
  await expect(page.getByTestId("send-text")).toBeEnabled();
  await expect(page.getByTestId("activity-label")).toHaveText("Ready");
});

for (const theme of ["light", "dark"]) for (const mobile of [false, true]) {
  test(`unified activity footer and Telemetry ${theme} ${mobile ? "mobile" : "desktop"}`, async ({ page, context }, info) => {
    await setup(context); await page.setViewportSize(mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 });
    await open(page); await page.evaluate(theme => setTheme(theme), theme);
    const publish = value => page.evaluate(value => {
      const source = window.__live.sources.find(source => source.url.includes("/monitor/stream"));
      source.dispatchEvent(new MessageEvent("activity", { data: JSON.stringify(value) }));
    }, { version: 1, agentId: ID, epoch: "epoch1", revision: 1, recent: [], ...value });
    await publish({ active: [{ operationId: "one", stage: "thinking", elapsedMs: 3400 }, { operationId: "two", stage: "persist", elapsedMs: 20 }] });
    await expect(page.getByTestId("activity-label")).toHaveText("Thinking");
    const footer = page.getByTestId("interaction-activity");
    await expect(footer).toHaveAttribute("data-busy", "true"); await expect(footer).toContainText("2 operations in progress");
    await page.getByTestId("continuous-speech-tab").click(); await expect(footer).toBeVisible();
    await page.emulateMedia({ reducedMotion: "reduce" });
    expect(await footer.locator(".activity-indicator").evaluate(node => getComputedStyle(node).animationName)).toBe("none");
    await footer.screenshot({ path: info.outputPath(`thinking-${theme}-${mobile}.png`) });
    await footer.getByRole("button", { name: "Mark an interaction issue" }).click();
    await footer.getByRole("button", { name: "Long pause", exact: true }).click();
    await publish({ revision: 2, active: [], cue: { reason: "insufficient_samples", occurrences: 4 } });
    await expect(page.getByTestId("activity-label")).toHaveText("Waiting for a stable cue");
    await expect(footer).toHaveAttribute("data-busy", "false");
    expect(await page.locator("#speech_playback_status, #transcription_ingress_status, #gptlive_context").count()).toBe(0);
    expect(await footer.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
    await page.locator("#open_diagnostics").click(); await page.getByRole("tab", { name: "Telemetry", exact: true }).click();
    await expect(page.locator("[data-activity-telemetry]")).toContainText("long pause");
    await expect(page.getByTestId("timing-export-json")).toBeEnabled();
    const download = page.waitForEvent("download"); await page.getByTestId("timing-export-json").click();
    const exported = await download; expect(exported.suggestedFilename()).toMatch(/^prometheus-telemetry-/);
    await page.screenshot({ path: info.outputPath(`telemetry-${theme}-${mobile}.png`) });
    await page.keyboard.press("Escape");
    await publish({ revision: 3, active: [{ operationId: "one", stage: "thinking", elapsedMs: 500 }] });
    await page.evaluate(() => { window.PrometheusActivity.received -= 7000; });
    await expect(page.getByTestId("activity-label")).toHaveText("Progress unavailable");
    await page.evaluate(() => window.PrometheusActivity.scope(null));
    await publish({ revision: 4, active: [{ stage: "thinking" }] });
    await expect(page.getByTestId("activity-label")).toHaveText("Connect an agent to begin");
  });
}

async function setup(context, scenario = {}) {
  Object.assign(scenario, { requests: [], history: [], ledger: [], ...scenario });
  await context.route("**/demo/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method(); scenario.requests.push({ path, method });
    if (path === "/demo/session") return route.fulfill(json({ accessCode: "LIVE1", agentTypes: [], agents: scenario.agents || [AGENT] }));
    if (method === "DELETE" && /^\/demo\/agents\/[^/]+$/.test(path)) return route.fulfill({ status: 204 });
    if (path.endsWith("/live/capabilities")) return route.fulfill(json({ enabled: scenario.enabled !== false, eligible: scenario.eligible !== false, model: "gpt-live-1", voices: ["marin", "quartz", "willow", "meridian"] }));
    if (path.endsWith("/info")) return route.fulfill(json(AGENT));
    if (path.endsWith("/history") || path.endsWith("/eventhistory")) return route.fulfill(json(scenario.history));
    if (path.endsWith("/storage")) return route.fulfill(json([]));
    if (path.endsWith("/states")) return route.fulfill(json(["Listening"]));
    if (path.endsWith("/state")) return route.fulfill(json({ name: "Listening", innerNames: [] }));
    if (path.endsWith("/transcription/capabilities")) return route.fulfill(json({ schemaVersion: 1, sessionType: "transcription", model: "gpt-live-transcribe", capabilities: { assistantOutput: false, inputTranscription: true }, settings: [] }));
    if (path.endsWith("/live/sessions") && method === "POST") return route.fulfill(scenario.providerError
      ? { ...json(scenario.providerErrorBody || {}), status: scenario.providerError }
      : { ...json({ handle: "session1", sdp: "v=0 answer", sidebandReady: true }), status: 201 });
    if (path.endsWith("/live/transcripts")) return route.fulfill(json(scenario.ledger));
    if (path.endsWith("/updates")) return route.fulfill(json({
      status: { state: scenario.disconnected ? "disconnected" : "attached", captureState: "active", context: { state: "ready", revision: "r1", recent: [] } },
      transcriptRevision: JSON.stringify(scenario.ledger).length, transcripts: scenario.ledger,
    }));
    if (path.includes("/live/sessions/")) return route.fulfill(json(method === "DELETE" ? { state: "closed", finalized: true } : { state: scenario.disconnected ? "disconnected" : "attached", captureState: "active", inputSamples: 100, outputSamples: 100, voicedInputSamples: 20, context: { state: "ready", revision: "r1", recent: [] } }));
    if (path.endsWith("/reset")) { scenario.history = []; return route.fulfill(json({ active: true, responseEvent: null })); }
    return route.fulfill({ status: 404, body: "" });
  });
  await context.addInitScript(() => {
    window.__live = { peers: [], tracks: [], sources: [], plays: 0, pauses: 0, captures: 0, denied: false, sinks: [] };
    Object.defineProperty(HTMLMediaElement.prototype, "srcObject", { configurable: true, get() { return this.__stream || null; }, set(value) { this.__stream = value; } });
    HTMLMediaElement.prototype.play = async function () { window.__live.plays++; };
    HTMLMediaElement.prototype.pause = function () { window.__live.pauses++; };
    HTMLMediaElement.prototype.setSinkId = async function (value) { window.__live.sinks.push(value); };
    class Track extends EventTarget { constructor() { super(); this.kind = "audio"; this.enabled = true; } stop() { this.stopped = true; } getSettings() { return { echoCancellation: true, noiseSuppression: true, autoGainControl: true }; } }
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: {
      async getUserMedia() { window.__live.captures++; if (window.__live.denied) throw new DOMException("Denied", "NotAllowedError"); const track = new Track(); window.__live.tracks.push(track); return { getAudioTracks: () => [track], getTracks: () => [track] }; },
      async enumerateDevices() { return [{ kind: "audioinput", deviceId: "mic", label: "Built-in microphone" }, { kind: "audiooutput", deviceId: "speaker", label: "Built-in speakers" }]; },
      addEventListener() {}, removeEventListener() {}, getSupportedConstraints() { return { echoCancellation: true, noiseSuppression: true, autoGainControl: true }; },
    } });
    class Channel extends EventTarget { constructor() { super(); this.readyState = "connecting"; } send() {} close() { this.readyState = "closed"; this.onclose?.(); this.dispatchEvent(new Event("close")); } emit(value) { const event = new MessageEvent("message", { data: JSON.stringify(value) }); this.onmessage?.(event); this.dispatchEvent(event); } }
    class Peer extends EventTarget {
      constructor() { super(); this.iceGatheringState = "complete"; this.connectionState = "new"; this.senders = []; window.__live.peers.push(this); }
      createDataChannel() { return this.channel = new Channel(); }
      addTrack(track) { this.senders.push({ track }); } getSenders() { return this.senders; }
      async createOffer() { return { type: "offer", sdp: "v=0 offer" }; } async setLocalDescription(value) { this.localDescription = value; }
      async setRemoteDescription() { this.connectionState = "connected"; this.channel.readyState = "open"; this.channel.dispatchEvent(new Event("open")); this.channel.emit({ type: "session.started" }); this.ontrack?.({ streams: [{}], track: new Track() }); }
      close() { this.connectionState = "closed"; }
    }
    window.RTCPeerConnection = Peer;
    class Source extends EventTarget { constructor(url) { super(); this.url = url; window.__live.sources.push(this); queueMicrotask(() => this.dispatchEvent(new Event("open"))); } close() {} emit(value) { this.dispatchEvent(new MessageEvent("behaviour-live", { data: JSON.stringify(value), lastEventId: value.id })); } }
    window.EventSource = Source;
  });
  return scenario;
}
async function open(page, connect = true) {
  await page.goto(`/valerian/${connect ? `?agentId=${ID}` : ""}`);
  await page.getByTestId("access-code-input").fill("LIVE1"); await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
  if (connect) await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  await page.getByTestId("gptlive-tab").click();
}
async function emit(page, type, id, delta) { await page.evaluate(value => window.__live.peers.at(-1).channel.emit(value), { type, event_id: id, delta, start_ms: 0, end_ms: 500 }); }
async function screenshot(page, info, name) {
  const panel = page.getByTestId("gptlive-panel");
  await expect(panel).toBeVisible();
  expect(await panel.evaluate(root => [...root.querySelectorAll("button,select,summary")].filter(node => node.offsetWidth).every(node => node.getBoundingClientRect().right <= root.getBoundingClientRect().right + 1))).toBe(true);
  const path = info.outputPath(`${name}.png`); await panel.screenshot({ path, animations: "disabled" }); await info.attach(name, { path, contentType: "image/png" });
  const tabs = page.locator("#interaction_tabs");
  expect(await tabs.evaluate(root => [...root.querySelectorAll("button")].filter(node => node.offsetWidth).every(node => node.getBoundingClientRect().right <= root.getBoundingClientRect().right + 1))).toBe(true);
  await tabs.screenshot({ path: info.outputPath(`${name}-tabs.png`), animations: "disabled" });
}

test("stable camera readings refresh through the cockpit while Live is active", async ({ page, context }) => {
  await setup(context);
  const observations = [];
  await context.route("**/demo/agents/*/acknowledge", async route => {
    observations.push(route.request().postDataJSON());
    await route.fulfill(json({ active: true, responseEvent: null }));
  });
  await open(page);
  await page.getByTestId("gptlive-start").click();
  await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  const start = new Date("2026-09-28T10:00:00Z");
  await page.clock.setFixedTime(start);
  const sense = () => page.evaluate(async () => {
    document.getElementById("sensor_emit_enabled").checked = true;
    await maybeEmitEmotion({ emotion: "neutral", confidence: .99, valence: 0, arousal: .2,
      expressions: { neutral: .99 } }, .99);
  });
  await sense(); expect(observations).toHaveLength(1);
  await page.clock.setFixedTime(new Date(start.getTime() + 3000));
  await sense(); expect(observations).toHaveLength(1);
  await page.clock.setFixedTime(new Date(start.getTime() + 5000));
  await sense(); expect(observations).toHaveLength(2);
  expect(Date.parse(JSON.parse(observations[1].payload).ts) - Date.parse(JSON.parse(observations[0].payload).ts)).toBe(5000);
  await page.getByTestId("gptlive-stop").click();
  await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  await page.clock.setFixedTime(new Date(start.getTime() + 30000));
  await sense(); expect(observations).toHaveLength(2);
});

for (const width of [1440, 390]) for (const theme of ["light", "dark"]) {
  test(`third tab, settings, overlap and committed history at ${width}px ${theme}`, async ({ page, context }, info) => {
    const scenario = await setup(context);
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
    await context.addInitScript(value => localStorage.setItem("prometheus.valerian.theme", value), theme);
    await open(page); expect(await page.evaluate(() => window.__live.captures)).toBe(0);
    await page.getByTestId("gptlive-settings").locator("summary").first().click();
    await page.getByTestId("gptlive-voice").selectOption("willow"); await page.getByTestId("gptlive-input").selectOption("mic"); await page.getByTestId("gptlive-output").selectOption("speaker");
    await screenshot(page, info, `idle-settings-${width}-${theme}`);
    await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    await expect(page.getByTestId("gptlive-voice")).toBeDisabled(); await expect(page.getByTestId("text-input")).toBeDisabled();
    await emit(page, "session.input_transcript.delta", "u1", "How does the room feel?");
    await emit(page, "session.output_transcript.delta", "a1", "It seems calm and welcoming.");
    await expect(page.getByTestId("gptlive-user-caption")).toHaveText("How does the room feel?");
    await expect(page.getByTestId("gptlive-assistant-caption")).toHaveText("It seems calm and welcoming.");
    await screenshot(page, info, `active-overlap-${width}-${theme}`);
    const native = event("native1", "It seems calm and welcoming.", { origin: "NATIVE", complete: true });
    scenario.history = [{ id: "user1", type: "obs.user_utterance", payload: "How does the room feel?" }, native];
    scenario.ledger = [{ segmentId: "us", speaker: "USER", status: "COMPLETE", eventId: "user1", receiptIds: ["u1"] }, { segmentId: "as", speaker: "ASSISTANT", status: "COMPLETE", eventId: "native1", receiptIds: ["a1"] }];
    await page.evaluate(value => window.__live.sources.find(source => source.url.includes("behaviour/stream")).emit(value), native);
    await expect(page.getByTestId("gptlive-history").locator(".demo-message.assistant")).toHaveCount(1);
    await expect(page.getByTestId("gptlive-user-caption")).toHaveText("Your speech will appear here.");
    await expect(page.getByTestId("message-list").locator(".demo-message.assistant")).toHaveCount(1);
    expect(scenario.requests.some(value => value.path.endsWith("/speech"))).toBe(false);
    await page.getByTestId("gptlive-mute").click(); await expect(page.getByTestId("gptlive-mute")).toHaveText("Unmute microphone");
    await page.getByTestId("gptlive-stop").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Idle");
    expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
    await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    await page.getByTestId("text-interaction-tab").click(); await expect(page.getByTestId("send-text")).toBeEnabled();
    expect(await page.evaluate(() => document.getElementById("gptlive_audio").srcObject)).toBeNull();
  });
}

test("no agent never starts; microphone denial and unsupported speaker routing are visible", async ({ page, context }, info) => {
  await setup(context); await open(page, false); await expect(page.getByTestId("gptlive-start")).toBeDisabled();
  expect(await page.evaluate(() => window.__live.captures)).toBe(0);
  await screenshot(page, info, "no-agent");
  await page.goto(`/valerian/?agentId=${ID}`); await expect(page.getByTestId("gptlive-start")).toBeEnabled(); await page.getByTestId("gptlive-tab").click();
  await page.evaluate(() => { window.__live.denied = true; delete HTMLMediaElement.prototype.setSinkId; });
  await page.getByTestId("gptlive-settings").locator("summary").first().click(); await page.locator("#gptlive_refresh").click();
  await expect(page.getByTestId("gptlive-output")).toBeDisabled(); await expect(page.locator("#gptlive_routing")).toContainText("system speaker");
  await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Error");
  await expect(page.getByTestId("gptlive-detail")).toContainText("permission was denied"); await screenshot(page, info, "denied-microphone");
});

test("provider error, keyboard navigation and another window's lease require explicit recovery", async ({ page, context }) => {
  const scenario = await setup(context, { providerError: 502 }); await open(page);
  await page.getByTestId("gptlive-start").focus(); await page.keyboard.press("Enter"); await expect(page.getByTestId("gptlive-status")).toHaveText("Error");
  await expect(page.getByTestId("gptlive-detail")).toContainText("provider is unavailable");
  scenario.providerError = 401; await page.getByTestId("gptlive-start").click();
  await expect(page.getByTestId("gptlive-detail")).toContainText("Access is no longer valid");
  scenario.providerError = null;
  const other = await context.newPage(); await open(other); await other.getByTestId("gptlive-start").click(); await expect(other.getByTestId("gptlive-status")).toHaveText("Active");
  await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-detail")).toContainText(/Another|another/);
  expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
  await other.getByTestId("gptlive-stop").click(); await expect(other.getByTestId("gptlive-status")).toHaveText("Idle");
  await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
});

test("exhausted API credits show billing guidance and release capture on desktop and mobile", async ({ page, context }, info) => {
  const scenario = await setup(context, { providerError: 502,
    providerErrorBody: { code: "live_provider_quota_exhausted", message: "private-provider-sentinel" } });
  await open(page);
  await page.getByTestId("gptlive-start").click();
  await expect(page.getByTestId("gptlive-status")).toHaveText("Error");
  await expect(page.getByTestId("gptlive-panel")).toContainText("OpenAI API credits or quota are exhausted.");
  await expect(page.getByTestId("gptlive-panel")).toContainText("Check API billing and limits");
  await expect(page.getByTestId("gptlive-panel")).not.toContainText("private-provider-sentinel");
  await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
  expect(await page.evaluate(() => document.getElementById("gptlive_audio").srcObject)).toBeNull();
  expect(scenario.requests.filter(value => value.path.endsWith("/live/sessions") && value.method === "POST")).toHaveLength(1);
  await screenshot(page, info, "live-quota-desktop.png");
  await page.setViewportSize({ width: 390, height: 844 });
  await screenshot(page, info, "live-quota-mobile.png");
});

test("agent without Live capability keeps existing modes and cannot start capture", async ({ page, context }, info) => {
  const scenario = await setup(context, { eligible: false });
  await page.goto(`/valerian/?agentId=${ID}`);
  await page.getByTestId("access-code-input").fill("LIVE1"); await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("send-text")).toBeEnabled();
  await expect(page.getByTestId("continuous-speech-tab")).toBeVisible();
  await page.getByTestId("gptlive-tab").click();
  await expect(page.getByTestId("gptlive-start")).toBeDisabled();
  await expect(page.getByTestId("gptlive-detail")).toHaveText("This agent does not support GPT-Live. Use Text or Continuous.");
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    await screenshot(page, info, `live-unsupported-${width}`);
  }
  expect(await page.evaluate(() => window.__live.captures)).toBe(0);
  expect(scenario.requests.filter(value => value.path.endsWith("/live/sessions") && value.method === "POST")).toHaveLength(0);
});

test("feature disabled preserves the two existing tabs", async ({ page, context }) => {
  await setup(context, { enabled: false }); await page.goto(`/valerian/?agentId=${ID}`);
  await page.getByTestId("access-code-input").fill("LIVE1"); await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("send-text")).toBeEnabled(); await expect(page.getByTestId("gptlive-tab")).toBeHidden();
  await expect(page.getByTestId("continuous-speech-tab")).toBeVisible(); expect(await page.evaluate(() => window.__live.captures)).toBe(0);
});

test("network, sideband and microphone loss require a fresh explicit connection", async ({ page, context }, info) => {
  const scenario = await setup(context); await open(page);
  for (const failure of ["network", "sideband", "track"]) {
    await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
    if (failure === "sideband") scenario.disconnected = true;
    else await page.evaluate(kind => {
      if (kind === "track") window.__live.tracks.at(-1).dispatchEvent(new Event("ended"));
      else { const peer = window.__live.peers.at(-1); peer.connectionState = "failed"; peer.onconnectionstatechange(); }
    }, failure);
    await expect(page.getByTestId("gptlive-status")).toHaveText("Disconnected");
    expect(await page.evaluate(() => document.getElementById("gptlive_audio").srcObject)).toBeNull();
    expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
    scenario.disconnected = false;
  }
  expect(scenario.requests.filter(value => value.path.endsWith("/live/sessions") && value.method === "POST")).toHaveLength(3);
  await page.locator("#open_diagnostics").click(); await page.getByTestId("interaction-timing-tab").click();
  await expect(page.getByTestId("timing-turns")).toContainText("GPT-Live");
  await page.getByTestId("timing-turns").locator("summary").first().click();
  await expect(page.getByTestId("timing-turns")).toContainText("separate clocks");
  await expect(page.getByTestId("timing-turns")).toContainText("Export records omitted");
  await expect(page.getByTestId("timing-turns")).toContainText("Server ring evictions");
  await page.screenshot({ path: info.outputPath("live-timing-drawer.png") });
});

test("reload, reset, switch, delete and logout cannot restore old captions or tracks", async ({ page, context }) => {
  const second = { ...AGENT, id: "22222222-2222-4222-8222-222222222222", name: "Second agent" };
  await setup(context, { agents: [AGENT, second] }); page.on("dialog", dialog => dialog.accept()); await open(page);
  await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  await page.reload(); await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  expect(await page.evaluate(() => window.__live.captures)).toBe(0);
  await page.getByTestId("gptlive-tab").click(); await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  await page.locator("#open_diagnostics").click();
  await page.evaluate(() => { window.__oldLivePeer = window.__live.peers.at(-1); window.__oldLiveSource = window.__live.sources.find(source => source.url.includes("/behaviour/")); });
  await page.getByTestId("reset-agent").click(); await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  await page.evaluate(() => { window.__oldLivePeer.channel.emit({ type: "session.output_transcript.delta", event_id: "late", delta: "Obsolete caption" }); window.__oldLiveSource.emit({ id: "late", type: "resp.behaviour_plan", payload: '{"speech":"Obsolete speech"}' }); });
  await expect(page.getByTestId("gptlive-assistant-caption")).not.toContainText("Obsolete");
  await expect(page.getByTestId("message-list")).not.toContainText("Obsolete");
  await page.keyboard.press("Escape"); await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  await page.locator("#open_diagnostics").click(); await page.getByTestId("agent-select").selectOption(second.id);
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Selected ${second.id}`);
  expect(await page.evaluate(() => document.getElementById("gptlive_audio").srcObject)).toBeNull();
  await page.getByTestId("connect-agent").click(); await expect(page.getByTestId("gptlive-start")).toBeEnabled();
  await page.keyboard.press("Escape"); await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  await page.locator("#open_diagnostics").click(); await page.getByTestId("delete-agent").click();
  await expect(page.getByTestId("agent-connection-state")).toHaveText("No agent selected");
  expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
  await page.getByTestId("agent-select").selectOption(ID); await page.getByTestId("connect-agent").click();
  await page.keyboard.press("Escape"); await page.getByTestId("gptlive-start").click(); await expect(page.getByTestId("gptlive-status")).toHaveText("Active");
  await page.getByTestId("clear-access-code").click(); await expect(page.getByTestId("access-screen")).toBeVisible();
  expect(await page.evaluate(() => window.__live.tracks.every(track => track.stopped))).toBe(true);
});
