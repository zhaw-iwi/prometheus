import {test} from 'node:test';
import assert from 'node:assert/strict';
import {SpeechBuffer} from '../../../src/main/resources/public/live/speech-buffer.js';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';

function fixture() {
  const buffer = new SpeechBuffer(8000); let time = 0;
  const frame = (value = 0) => { time += 20; buffer.frame(new Float32Array(160).fill(value), time); };
  const wait = (ms, value = 0) => { for (let i = 0; i < ms / 20; i++) frame(value); };
  const caption = (id, delta) => buffer.caption({event_id: id, delta}, time);
  const utterance = () => { wait(100); caption('first', 'The task '); wait(800, .1); caption('last', 'is stopped.'); wait(100, .1); wait(2400); };
  const segment = {segmentId: 'one', speaker: 'ASSISTANT', status: 'COMPLETE', receiptIds: ['first', 'last'], text: 'The task is stopped.'};
  return {buffer, frame, wait, caption, utterance, segment};
}
test('only a whole receipt-matched utterance releases original PCM once, including quiet onset', () => {
  const f = fixture(); f.utterance(); const candidate = f.buffer.select(f.segment); assert.ok(candidate);
  const pcm = f.buffer.take(candidate); assert.equal(pcm.length, 10000);
  assert.equal(pcm[0], 0); assert.ok(pcm[800] > .09); assert.equal(pcm.at(-1), 0);
  assert.equal(f.buffer.take(candidate), null);
});
test('incomplete, wrong, reordered, missing or extra receipts cannot identify audible content', () => {
  for (const change of [{status: 'PENDING'}, {speaker: 'USER'}, {text: 'It is paused.'}, {receiptIds: ['last', 'first']},
    {receiptIds: ['first', 'missing']}, {receiptIds: ['first', 'first']}, {receiptIds: []}]) {
    const f = fixture(); f.utterance(); assert.equal(f.buffer.select({...f.segment, ...change}), null);
  }
  const f = fixture(); f.wait(100); f.caption('untracked', 'Oops.'); f.utterance(); assert.equal(f.buffer.select(f.segment), null);
});
test('silence and partial audio cannot be mistaken for a complete captioned answer', () => {
  const f = fixture(); f.caption('first', 'The task '); f.caption('last', 'is stopped.'); f.wait(4000);
  assert.equal(f.buffer.select(f.segment), null);
  const g = fixture(); g.utterance(); g.buffer.caption({event_id: 'late', delta: ' Extra words.'}, 900);
  assert.equal(g.buffer.select(g.segment), null);
});
test('separate caption and WebRTC transports tolerate bounded onset skew without accepting arbitrary earlier text', () => {
  for (const [delay, accepted] of [[900, true], [1100, false]]) {
    const f = fixture(); f.caption('first', 'The task '); f.wait(delay); f.wait(800, .1);
    f.caption('last', 'is stopped.'); f.wait(100, .1); f.wait(2400);
    assert.equal(!!f.buffer.select(f.segment), accepted);
  }
  for (const [tail, accepted] of [[1400, true], [1600, false]]) {
    const f = fixture(); f.wait(100); f.caption('first', 'The task '); f.wait(500, .1);
    f.caption('last', 'is stopped.'); f.wait(tail, .1); f.wait(2400);
    assert.equal(!!f.buffer.select(f.segment), accepted);
  }
});
test('interruption discards buffered speech and cannot capture a continuing tail', () => {
  const f = fixture(); f.utterance(); const before = f.buffer.select(f.segment); f.wait(100, .1); f.buffer.interrupt();
  assert.equal(f.buffer.take(before), null); f.wait(500, .1); assert.equal(f.buffer.active, null);
  f.wait(2400); f.utterance(); assert.ok(f.buffer.select(f.segment));
});
test('audio gaps, overflow and expiry fail closed with bounded memory', () => {
  const f = fixture(); f.utterance(); f.buffer.frame(new Float32Array(160).fill(.1), 5000);
  assert.equal(f.buffer.select(f.segment), null);
  const g = fixture(); g.wait(31000, .1); assert.equal(g.buffer.active, null); assert.equal(g.buffer.ready.length, 0);
  const h = fixture(); h.utterance(); h.wait(61000); assert.equal(h.buffer.select(h.segment), null); assert.equal(h.buffer.ready.length, 0);
  assert.throws(() => h.buffer.frame(new Float32Array([NaN]), 70000));
});
test('capture worklet transfers original samples while every output remains silent', async () => {
  const sent = []; let Processor;
  const scope = {Float32Array, Math, sampleRate: 8000, currentTime: 0,
    AudioWorkletProcessor: class { constructor() { this.port = {postMessage: value => sent.push(value)}; } },
    registerProcessor: (_, type) => { Processor = type; }};
  vm.runInNewContext(await readFile(new URL('../../../src/main/resources/public/live/speech-capture-worklet.js', import.meta.url), 'utf8'), scope);
  const capture = new Processor({}), input = new Float32Array(160).fill(.25), output = new Float32Array(160).fill(1);
  capture.process([[input]], [[output]]);
  assert.ok(output.every(v => v === 0)); assert.deepEqual(sent[0].samples, input);
  sent.length = 0; const vad = new Processor({processorOptions: {inputOnly: true}});
  vad.process([[input]], [[output]]); vad.process([[input]], [[output]]);
  assert.equal(sent.length, 1); assert.equal(sent[0].voice, true); assert.ok(output.every(v => v === 0));
});
