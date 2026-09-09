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

test("ended microphone track reissues the session and reacquires input", async () => {
  const peers = [];
  const diagnostics = [];
  let sessions = 0;
  const media = fakeMedia();
  const transport = new TranscriptionTransport({
    fetchImpl: async () => ({ ok: true, text: async () => "answer" }),
    peerConnectionFactory: () => {
      const peer = new FakePeer();
      peers.push(peer);
      return peer;
    },
    media,
    sessionFactory: async () => { sessions += 1; return session(); },
    reconnectBaseMs: 1,
    maximumReconnects: 2,
    onDiagnostic: (diagnostic) => diagnostics.push(diagnostic),
  });

  await transport.start(session());
  media.tracks[0].end();
  await waitFor(() => peers.length === 2 && transport.state === "connected");

  assert.equal(sessions, 1);
  assert.equal(media.acquires, 2);
  assert.equal(media.tracks[0].stopped, true);
  assert.ok(diagnostics.some(({ code }) => code === "microphone_track_ended"));
  await transport.stop();
});

test("only the active replacement track can trigger reconnect", async () => {
  const peers = [];
  let sessions = 0;
  const media = fakeMedia();
  const transport = new TranscriptionTransport({
    fetchImpl: async () => ({ ok: true, text: async () => "answer" }),
    peerConnectionFactory: () => {
      const peer = new FakePeer();
      peers.push(peer);
      return peer;
    },
    media,
    sessionFactory: async () => { sessions += 1; return session(); },
    reconnectBaseMs: 1,
    maximumReconnects: 1,
  });

  await transport.start(session());
  const oldTrack = media.tracks[0];
  await transport.replaceMedia({ inputDeviceId: "room-mic" });
  const replacementTrack = media.tracks[1];
  oldTrack.end();
  await new Promise((resolve) => setTimeout(resolve, 10));
  assert.equal(peers.length, 1);
  assert.equal(sessions, 0);

  replacementTrack.end();
  await waitFor(() => peers.length === 2 && transport.state === "connected");
  assert.equal(sessions, 1);
  await transport.stop();
});

test("reconnect retries transient session issuance failure with its actionable reason", async () => {
  const peers = [];
  const states = [];
  const diagnostics = [];
  let sessions = 0;
  const transport = new TranscriptionTransport({
    fetchImpl: async () => ({ ok: true, text: async () => "answer" }),
    peerConnectionFactory: () => {
      const peer = new FakePeer();
      peers.push(peer);
      return peer;
    },
    media: fakeMedia(),
    sessionFactory: async () => {
      sessions += 1;
      if (sessions === 1) throw new Error("temporary session service outage");
      return session();
    },
    reconnectBaseMs: 1,
    maximumReconnects: 2,
    onState: (state) => states.push(state),
    onDiagnostic: (diagnostic) => diagnostics.push(diagnostic),
  });

  await transport.start(session());
  peers[0].connectionState = "disconnected";
  peers[0].dispatch("connectionstatechange");
  await waitFor(() => sessions === 2 && transport.state === "connected");

  assert.equal(peers.length, 2);
  assert.deepEqual(states.filter(({ attempt }) => attempt).map(({ attempt }) => attempt), [1, 2]);
  assert.ok(diagnostics.some(({ code, message }) => code === "reconnect_failed"
    && message === "temporary session service outage"));
  await transport.stop();
});

test("exhausted reconnect exposes the last actionable failure", async () => {
  const peers = [];
  const states = [];
  const transport = new TranscriptionTransport({
    fetchImpl: async () => ({ ok: true, text: async () => "answer" }),
    peerConnectionFactory: () => {
      const peer = new FakePeer();
      peers.push(peer);
      return peer;
    },
    media: fakeMedia(),
    sessionFactory: async () => { throw new Error("requested microphone not found"); },
    reconnectBaseMs: 1,
    maximumReconnects: 1,
    onState: (state) => states.push(state),
  });

  await transport.start(session());
  peers[0].connectionState = "failed";
  peers[0].dispatch("connectionstatechange");
  await waitFor(() => transport.state === "failed"
    && states.at(-1)?.message?.includes("requested microphone not found"));

  assert.match(states.at(-1).message, /Automatic transcription reconnect exhausted/);
  assert.match(states.at(-1).message, /requested microphone not found/);
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
    acquires: 0, releases: 0, enabled: [], tracks: [], stream: null,
    async acquire() {
      this.acquires += 1;
      const track = new FakeTrack();
      this.tracks.push(track);
      this.stream = { getTracks: () => [track], getAudioTracks: () => [track] };
    },
    release() {
      this.releases += 1;
      this.stream?.getTracks().forEach((track) => track.stop());
      this.stream = null;
    },
    setEnabled(value) {
      this.enabled.push(value);
      this.stream?.getAudioTracks().forEach((track) => { track.enabled = value; });
    },
    addTracks(peer) { peer.addTrack(this.stream.getAudioTracks()[0]); },
    async replaceAudioTrack() {
      const oldStream = this.stream;
      const track = new FakeTrack();
      this.tracks.push(track);
      this.stream = { getTracks: () => [track], getAudioTracks: () => [track] };
      oldStream?.getTracks().forEach((oldTrack) => oldTrack.stop());
      return {};
    },
  };
  return media;
}

class FakeTrack extends EventTarget {
  constructor() {
    super();
    this.kind = "audio";
    this.enabled = true;
    this.stopped = false;
  }
  stop() { this.stopped = true; }
  end() { this.dispatchEvent(new Event("ended")); }
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
