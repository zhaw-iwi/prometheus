import assert from "node:assert/strict";
import test from "node:test";
import { LiveTranscriptionClient } from "../../../src/main/resources/public/transcription/client.js";

const AGENT_ID = "11111111-1111-4111-8111-111111111111";

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
