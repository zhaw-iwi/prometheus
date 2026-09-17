import assert from "node:assert/strict";
import test from "node:test";
import { LiveTranscriptionClient } from "../../../src/main/resources/public/transcription/client.js";

const AGENT_ID = "11111111-1111-4111-8111-111111111111";

test("only successful local and manual commits record send time without invented voice boundaries", () => {
  let commitVad;
  let now = 21;
  const client = new LiveTranscriptionClient({
    agentId: AGENT_ID, now: () => now,
    media: { setEnabled: () => {} },
    localVadFactory: ({ onCommit }) => { commitVad = onCommit; return {}; },
  });
  client.events.beginEpoch(1);
  client.transport.commitLocalVadTurn = () => true;
  commitVad({ lastVoiceAtMs: 10, observedAtMs: 20 });
  client.transport.commitLocalVadTurn = () => false;
  commitVad({ lastVoiceAtMs: 12, observedAtMs: 22 });
  now = 30;
  client.transport.commitManualTurn = () => true;
  assert.equal(client.commitManualTurn(), true);
  client.transport.commitManualTurn = () => false;
  assert.equal(client.commitManualTurn(), false);
  assert.deepEqual(client.events.pendingCommits, [
    { last_voice: 10, committed: 20, commit_sent: 21 },
    { last_voice: undefined, committed: undefined, commit_sent: 30 },
  ]);
});

test("playback gating clears and settles input before starting a fresh transcription buffer", async () => {
  const calls = [];
  const client = new LiveTranscriptionClient({
    agentId: AGENT_ID,
    media: { setEnabled: () => {} },
    localVadFactory: () => ({ start: async () => {}, stop: async () => { calls.push("vad-stop"); } }),
  });
  client.transport.state = "connected";
  client.transport.epoch = 7;
  client.transport.setInputEnabled = (enabled) => calls.push(`transport:${enabled}`);
  client.events.settleEpoch = () => calls.push("events:settle");
  client.events.beginEpoch = (epoch) => calls.push(`events:begin:${epoch}`);
  client.syncLocalVad = async () => { calls.push("vad-sync"); };

  client.setInputEnabled(false);
  client.setInputEnabled(true);
  await Promise.resolve();

  assert.deepEqual(calls, [
    "transport:false", "events:settle", "vad-stop",
    "events:begin:7", "transport:true", "vad-sync",
  ]);
});

test("page unload starts event, VAD, and transport teardown synchronously", () => {
  const calls = [];
  const client = new LiveTranscriptionClient({
    agentId: AGENT_ID,
    media: { setEnabled: () => {} },
    localVadFactory: () => ({ start: async () => {}, stop: async () => { calls.push("vad-stop"); } }),
  });
  client.events.settleEpoch = () => calls.push("events:settle");
  client.transport.stop = async () => { calls.push("transport-stop"); };

  client.stopForPageUnload();

  assert.deepEqual(calls, ["events:settle", "vad-stop", "transport-stop"]);
});
