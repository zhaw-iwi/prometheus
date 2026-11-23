import { readFileSync, writeFileSync } from "node:fs";
import { test, expect } from "./fixtures/offline-ui-assets.mjs";

const scenario = JSON.parse(readFileSync(new URL("../gptlive/gptlivefix-scenarios.json", import.meta.url))).sensor;

// Real controllers, state machine, MySQL, SSE and canonical Speech route. Only
// external providers and microphone capture are synthetic; PCM rendering is real.
test("Coordination Text and Continuous preserve agreement, preparation and one canonical contribution", async ({ page, context, request }, info) => {
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || !process.env.PROMETHEUS_LIVE_EXPECT_ENABLED,
    "Use the disposable MySQL acceptance runner.");
  const admin = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const accessResponse = await request.post("/admin/access-codes", { headers: admin, data: { code, enabled: true } });
  expect(accessResponse.status()).toBe(201);
  const access = await accessResponse.json();
  expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, {
    headers: admin, data: { agentTypeKeys: ["core.coordination"] },
  })).ok()).toBe(true);
  const headers = { "X-Prometheus-Access-Code": code };
  const agents = [], evidence = {};
  expect((await request.post("/__live-fixture/coordination-proposal", { data: scenario.proposal })).ok()).toBe(true);
  await transcriptionHardware(context);
  await page.goto("/valerian/");
  await page.getByTestId("access-code-input").fill(code);
  await page.getByTestId("submit-access-code").click();
  const speechRequests = [], acknowledgements = [];
  page.on("request", r => {
    if (r.method() === "POST" && /\/behaviours\/[^/]+\/speech\?/.test(r.url())) speechRequests.push(r);
    if (r.method() === "POST" && r.url().includes("/acknowledge")) acknowledgements.push(r);
  });
  try {
    for (const mode of ["text", "continuous"]) {
      await page.locator("#open_diagnostics").click();
      await page.getByTestId("agent-type-select").selectOption("core.coordination");
      const created = page.waitForResponse(r => r.url().endsWith("/demo/agents") && r.request().method() === "POST");
      await page.getByTestId("create-agent-instance").click();
      const agent = await (await created).json(); agents.push(agent.id);
      const path = `/demo/agents/${agent.id}`;
      const memory = async () => Object.fromEntries((await (await request.get(path + "/storage", { headers })).json())
        .map(entry => [entry.key, JSON.parse(entry.value)]));
      // The additive projection retains persisted IDs; raw eventhistory has no ID field.
      const history = async () => (await request.get(path + "/live/history", { headers })).json();
      await page.getByTestId("connect-agent").click();
      await expect(page.locator("#behaviour_status")).toHaveText("Behaviour Live");
      await page.keyboard.press("Escape");
      const speechBefore = speechRequests.length;
      const providerBefore = (await (await request.get("/__live-fixture/speech")).json()).length;
      const microphoneReady = async () => {
        if (mode !== "continuous") return;
        await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Connected");
        await expect(page.locator("#listen_status")).toHaveText("Listening");
        await expect.poll(() => page.evaluate(() => window.__continuousMic?.enabled)).toBe(true);
      };
      if (mode === "continuous") {
        await page.getByTestId("continuous-speech-tab").click();
        await page.getByTestId("toggle-transcription").click();
        await microphoneReady(); // Includes real canonical startup replay before microphone acquisition.
      }
      let turn = 0;
      const utterance = async text => {
        await microphoneReady();
        const accepted = page.waitForResponse(r => r.url().includes(path + "/acknowledge") && r.request().method() === "POST");
        if (mode === "text") {
          await page.getByTestId("text-input").fill(text);
          await page.getByTestId("send-text").click();
        } else {
          const item = `continuous-${++turn}`;
          const before = acknowledgements.length;
          await emit(page, { type: "input_audio_buffer.committed", item_id: item });
          await emit(page, { type: "conversation.item.input_audio_transcription.delta", item_id: item, delta: "Partial must stay local" });
          await expect(page.locator("#continuous_speech_sensing_value")).toHaveText("Partial must stay local");
          expect(acknowledgements).toHaveLength(before);
          await emit(page, { type: "conversation.item.input_audio_transcription.completed", item_id: item, transcript: text });
          // A repeated terminal cannot perform the turn again, even with a new receipt ID.
          await emit(page, { type: "conversation.item.input_audio_transcription.completed", item_id: item, transcript: text });
        }
        const response = await accepted; expect(response.ok()).toBe(true);
        // Typed input uses the endpoint's FULL_PLAN default; transcription names it explicitly.
        expect(new URL(response.url()).searchParams.get("profile")).toBe(mode === "continuous" ? "full_plan" : null);
        const body = await response.json();
        expect(body.responseEvent?.type).toBe("resp.behaviour_plan");
        if (mode === "continuous") {
          await expect(page.locator("#listen_status")).toHaveText("Input Paused");
          expect(await page.evaluate(() => window.__continuousMic.enabled)).toBe(false);
          await microphoneReady();
        }
        return body;
      };
      const observe = async ts => {
        const response = await request.post(path + "/acknowledge?profile=full_plan", { headers,
          data: { type: "obs.hand.sign", actor: "camera", kind: "observation",
            payload: JSON.stringify({ sign: "scissor", confidence: 0.9, ts }) } });
        expect(response.ok()).toBe(true); return response.json();
      };
      await utterance(scenario.proposalInput);
      const proposed = await memory();
      expect(proposed["coordination.draft"]).toEqual(scenario.proposal.agreement);
      expect(proposed["coordination.evidence"]).toEqual({});
      expect(proposed["coordination.agreement"]).toBeUndefined();
      // A request to start cannot replace observation-based preparation.
      await utterance(scenario.acceptanceInput);
      expect((await memory())["coordination.agreement"]).toBeUndefined();
      const preparationTime = new Date().toISOString();
      await observe(preparationTime);
      await expect.poll(async () => Object.keys((await memory())["coordination.evidence"] || {}).length).toBe(1);
      await microphoneReady();
      await utterance(scenario.acceptanceInput);
      const accepted = await memory();
      expect(accepted["coordination.agreement"]).toEqual(scenario.proposal.agreement);
      expect(accepted["task.actions"]).toBe(0);
      expect(Object.keys(accepted["coordination.acceptedEvidence"])).toHaveLength(1);
      expect((await history()).filter(e => e.type === "resp.behaviour_plan")
        .some(e => JSON.parse(e.payload).speech === "Welcome to the lab.")).toBe(false);
      // Reusing the preparation sample is not a fresh execution cue.
      await observe(preparationTime);
      expect((await memory())["task.actions"]).toBe(0);
      const wait = Date.parse((await memory())["task.after"]) - Date.now();
      expect(wait).toBeLessThan(5000);
      if (wait >= 0) await page.waitForTimeout(wait + 25);
      const cueTime = new Date().toISOString();
      const contribution = await observe(cueTime);
      expect(contribution.responseEvent?.type).toBe("resp.behaviour_plan");
      expect(JSON.parse(contribution.responseEvent.payload)).toMatchObject({
        speech: "Welcome to the lab.", nonVerbal: { gesture: "ACKNOWLEDGE" },
      });
      if (mode === "continuous") {
        await expect(page.locator("#listen_status")).toHaveText("Input Paused");
        expect(await page.evaluate(() => window.__continuousMic.enabled)).toBe(false);
        await microphoneReady();
      }
      await observe(cueTime);
      const completed = await memory();
      expect(completed["task.actions"]).toBe(1);
      expect(completed["task.phase"]).toBe("COMPLETED");
      expect(completed["coordination.outcome"]).toBe("LIMIT_REACHED");
      const events = await history();
      expect(events.filter(e => e.type === "obs.user_utterance").map(e => e.payload)).toEqual([
        scenario.proposalInput, scenario.acceptanceInput, scenario.acceptanceInput,
      ]);
      const spoken = speechRequests.slice(speechBefore);
      if (mode === "continuous") {
        const synthesized = (await (await request.get("/__live-fixture/speech")).json()).slice(providerBefore);
        expect(spoken.length).toBeGreaterThanOrEqual(5);
        expect(new Set(spoken.map(r => new URL(r.url()).pathname)).size).toBe(spoken.length);
        expect(synthesized).toHaveLength(spoken.length);
        for (const [i, speech] of spoken.entries()) {
          expect(speech.postData()).toBeNull();
          const eventId = new URL(speech.url()).pathname.split("/").at(-2);
          const event = events.find(e => e.id === eventId);
          expect(event?.type).toBe("resp.behaviour_plan");
          expect(synthesized[i]).toEqual({ text: JSON.parse(event.payload).speech, voice: "alloy", speed: 1, format: "pcm" });
        }
        expect(await page.evaluate(() => window.PrometheusTimings.snapshot().some(t => t.speech?.playbackStartSource === "pcm_renderer"))).toBe(true);
        await page.getByTestId("toggle-transcription").click();
        await expect(page.getByTestId("transcription-transport-status")).toHaveText("Transcription Idle");
        expect(await page.evaluate(() => window.__continuousMic.readyState)).toBe("ended");
      } else expect(spoken).toHaveLength(0);
      evidence[mode] = { agreement: completed["coordination.agreement"],
        preparation: Object.keys(accepted["coordination.acceptedEvidence"]),
        actionsOnAcceptance: accepted["task.actions"], actions: completed["task.actions"],
        phase: completed["task.phase"], outcome: completed["coordination.outcome"],
        contribution: JSON.parse(contribution.responseEvent.payload), speechRequests: spoken.length };
      writeFileSync(info.outputPath(`hand-baseline-${mode}.json`), JSON.stringify({
        ...evidence[mode], persistedEvents: events.length, userTurns: 3, submittedHandObservations: 4,
      }, null, 2));
      await page.locator('[data-column-panel="interaction"]').screenshot({path: info.outputPath(`hand-baseline-${mode}.png`)});
      await page.locator("#open_diagnostics").click();
      await page.getByTestId("connect-agent").click(); // The connected button toggles to Disconnect.
      await page.keyboard.press("Escape");
    }
    const { speechRequests: textCount, ...typed } = evidence.text;
    const { speechRequests: continuousCount, ...continuous } = evidence.continuous;
    expect(continuous).toEqual(typed);
  } finally {
    await info.attach("mode-parity", { body: Buffer.from(JSON.stringify(evidence, null, 2)), contentType: "application/json" });
    await page.close();
    for (const id of agents) await request.delete(`/demo/agents/${id}`, { headers });
    await request.delete("/__live-fixture/coordination-proposal");
    // The runner drops the access record with its disposable schema.
  }
});

async function emit(page, event) {
  await page.evaluate(event => window.__continuousChannel.dispatchEvent(new MessageEvent("message", {
    data: JSON.stringify({ event_id: crypto.randomUUID(), ...event }),
  })), event);
}

async function transcriptionHardware(context) {
  await context.route("https://transcription.invalid/offer", route => route.fulfill({ contentType: "application/sdp", body: "synthetic-answer" }));
  await context.addInitScript(() => {
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: {
      async getUserMedia() {
        const audio = new AudioContext(), destination = audio.createMediaStreamDestination();
        window.__continuousCapture = audio; window.__continuousMic = destination.stream.getAudioTracks()[0];
        return destination.stream;
      },
      async enumerateDevices() { return []; }, getSupportedConstraints() { return {}; },
      addEventListener() {}, removeEventListener() {},
    } });
    window.RTCPeerConnection = class extends EventTarget {
      constructor() { super(); this.connectionState = "new"; this.iceConnectionState = "new"; this.senders = []; }
      createDataChannel() {
        const channel = new EventTarget(); channel.readyState = "connecting";
        channel.send = () => {}; channel.close = () => { channel.readyState = "closed"; };
        window.__continuousChannel = this.channel = channel; return channel;
      }
      addTrack(track) { this.senders.push({ track, replaceTrack: async next => { this.senders[0].track = next; } }); }
      getSenders() { return this.senders; }
      async createOffer() { return { type: "offer", sdp: "synthetic-offer" }; }
      async setLocalDescription() {}
      async setRemoteDescription() {
        this.connectionState = "connected"; this.channel.readyState = "open";
        queueMicrotask(() => this.channel.dispatchEvent(new Event("open")));
      }
      close() { this.connectionState = "closed"; }
    };
  });
}
