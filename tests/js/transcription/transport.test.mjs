import assert from "node:assert/strict";
import test from "node:test";
import { TranscriptionTransport } from "../../../src/main/resources/public/transcription/transport.js";

test("transport exchanges SDP, stops media, and reconnects once with a fresh session", async () => {
  const peers = [];
  const states = [];
  let sessions = 0;
  const media = fakeMedia();
  const transport = new TranscriptionTransport({
    fetchImpl: async (url, options) => {
      assert.equal(url, "https://api.openai.test/v1/realtime/calls");
      assert.equal(options.headers.Authorization, "Bearer ephemeral");
      return { ok: true, text: async () => "answer" };
    },
    peerConnectionFactory: () => {
      const peer = new FakePeer();
      peers.push(peer);
      return peer;
    },
    media,
    sessionFactory: async () => { sessions += 1; return session(); },
    reconnectBaseMs: 1,
    maximumReconnects: 1,
    onState: ({ state }) => states.push(state),
  });

  await transport.start(session(), { mediaPreferences: {}, turnDetectionMode: "local_vad" });
  assert.equal(transport.state, "connected");
  peers[0].connectionState = "failed";
  peers[0].dispatch("connectionstatechange");
  await waitFor(() => transport.epoch === 2 && transport.state === "connected");
  assert.equal(sessions, 1);
  assert.equal(media.acquires, 2);
  assert.ok(states.includes("reconnecting"));
  await transport.stop();
  assert.equal(transport.state, "stopped");
  assert.equal(media.releases, 3);
  assert.equal(peers.every((peer) => peer.closed), true);
});

test("manual mode clears, enables, commits, and disables the microphone", async () => {
  const media = fakeMedia();
  const peer = new FakePeer();
  const transport = new TranscriptionTransport({
    fetchImpl: async () => ({ ok: true, text: async () => "answer" }),
    peerConnectionFactory: () => peer,
    media,
  });
  await transport.start(session(), { turnDetectionMode: "manual" });
  assert.equal(transport.startManualTurn(), true);
  assert.equal(transport.commitManualTurn(), true);
  assert.deepEqual(peer.channel.sent.map((value) => JSON.parse(value).type),
    ["input_audio_buffer.clear", "input_audio_buffer.commit"]);
  assert.deepEqual(media.enabled.slice(-3), [false, true, false]);
  await transport.stop();
});

class FakePeer {
  constructor() {
    this.listeners = new Map();
    this.connectionState = "new";
    this.iceConnectionState = "new";
    this.channel = new FakeChannel();
  }
  addEventListener(name, handler) { this.listeners.set(name, handler); }
  dispatch(name) { this.listeners.get(name)?.(); }
  createDataChannel() { return this.channel; }
  addTrack() {}
  async createOffer() { return { type: "offer", sdp: "offer" }; }
  async setLocalDescription() {}
  async setRemoteDescription() {}
  close() { this.closed = true; this.connectionState = "closed"; this.dispatch("connectionstatechange"); }
}

class FakeChannel {
  constructor() { this.readyState = "open"; this.listeners = new Map(); this.sent = []; }
  addEventListener(name, handler) {
    if (!this.listeners.has(name)) this.listeners.set(name, []);
    this.listeners.get(name).push(handler);
  }
  send(value) { this.sent.push(value); }
  close() { this.readyState = "closed"; (this.listeners.get("close") || []).forEach((handler) => handler()); }
}

function fakeMedia() {
  const media = {
    acquires: 0, releases: 0, enabled: [], stream: { getAudioTracks: () => [{}] },
    async acquire() { this.acquires += 1; },
    release() { this.releases += 1; },
    setEnabled(value) { this.enabled.push(value); },
    addTracks(peer) { peer.addTrack(); },
  };
  return media;
}

function session() {
  return { sessionType: "transcription", model: "gpt-live-transcribe", clientSecret: "ephemeral",
    webRtcUrl: "https://api.openai.test/v1/realtime/calls" };
}

async function waitFor(condition) {
  const deadline = Date.now() + 1000;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error("condition timed out");
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}
