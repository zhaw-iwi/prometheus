import assert from "node:assert/strict";
import test from "node:test";
import { OrderedTranscriptAssembler, TranscriptionEventRuntime } from "../../../src/main/resources/public/transcription/events.js";

test("final transcripts release in committed order even when completion is out of order", () => {
  const assembler = new OrderedTranscriptAssembler();
  assembler.beginEpoch(7);
  assembler.accept(event("input_audio_buffer.committed", "c1", { item_id: "first" }));
  assembler.accept(event("input_audio_buffer.committed", "c2", { item_id: "second" }));
  assert.deepEqual(assembler.accept(event("conversation.item.input_audio_transcription.completed", "t2",
    { item_id: "second", transcript: "second turn" })).finals, []);
  assert.deepEqual(assembler.accept(event("conversation.item.input_audio_transcription.completed", "t1",
    { item_id: "first", transcript: "first turn" })).finals, [
      { epoch: 7, itemId: "first", text: "first turn" },
      { epoch: 7, itemId: "second", text: "second turn" },
    ]);
});

test("partials are UI-only and duplicate terminal events release once", () => {
  const assembler = new OrderedTranscriptAssembler();
  assembler.beginEpoch(2);
  assembler.accept(event("input_audio_buffer.committed", "c1", { item_id: "turn" }));
  assert.equal(assembler.accept(event("conversation.item.input_audio_transcription.delta", "d1",
    { item_id: "turn", delta: "hel" })).partials[0].text, "hel");
  const completed = event("conversation.item.input_audio_transcription.completed", "f1",
    { item_id: "turn", transcript: "hello" });
  assert.equal(assembler.accept(completed).finals.length, 1);
  assert.equal(assembler.accept(completed).finals.length, 0);
  assert.equal(assembler.accept({ ...completed, event_id: "f2" }).finals.length, 0);
});

test("epoch change prevents a queued stale final from reaching the consumer", async () => {
  const finals = [];
  let releaseHandler;
  const blocked = new Promise((resolve) => { releaseHandler = resolve; });
  const runtime = new TranscriptionEventRuntime({
    onFinal: async (final) => { await blocked; finals.push(final); },
  });
  runtime.beginEpoch(1);
  runtime.handle(event("input_audio_buffer.committed", "c1", { item_id: "old" }));
  runtime.handle(event("conversation.item.input_audio_transcription.completed", "f1",
    { item_id: "old", transcript: "stale" }));
  runtime.beginEpoch(2);
  releaseHandler();
  await runtime.whenIdle();
  assert.deepEqual(finals, []);
});

test("assistant output is diagnostics only", () => {
  const diagnostics = [];
  const runtime = new TranscriptionEventRuntime({ onDiagnostic: (diagnostic) => diagnostics.push(diagnostic) });
  runtime.beginEpoch(1);
  runtime.handle({ type: "response.output_audio.delta", delta: "ignored" });
  assert.equal(diagnostics[0].code, "unexpected_assistant_event");
});

function event(type, eventId, overrides) {
  return { type, event_id: eventId, ...overrides };
}
