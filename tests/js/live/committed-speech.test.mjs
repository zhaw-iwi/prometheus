import {test} from 'node:test';
import assert from 'node:assert/strict';
import {CommittedSpeechPlayer} from '../../../src/main/resources/public/live/committed-speech.js';

function fixture() {
  globalThis.document = {hidden: false};
  const calls = [], audible = [], heard = [], warnings = [];
  const reply = {status: 'APPROVED', id: 'request', segmentId: 'synthesis', text: 'Stopped.',
    receiptIds: ['synthesis-synthesis'], expiresAt: new Date(Date.now() + 60000).toISOString(),
    sampleRate: 24000, pcm: Buffer.from([0, 32, 0, 32]).toString('base64')};
  const player = new CommittedSpeechPlayer({audio: {paused: false, muted: false, volume: 1},
    api: async path => {calls.push(path); return path.startsWith('speech-synthesis/') ? reply : true;},
    played: value => heard.push(value), withheld: value => warnings.push(value)});
  player.buffer = {generation: 0, interrupt() {this.generation++;}};
  player.context = {currentTime: 1, state: 'running',
    createBuffer: () => ({copyToChannel() {}}), createBufferSource: () => ({connect() {}, disconnect() {},
      start() {audible.push('start');}, stop() {audible.push('stop');}}), close: async () => {}};
  player.observer = {scheduled() {}, interrupt() {}, close() {}}; player.requestId = 'request';
  return {player, calls, audible, heard, warnings, reply};
}
test('a pause before source end cannot confirm delivery; observed end acknowledges once', async () => {
  const f = fixture(); f.player.finalCue = {id: 'request', segmentId: 'synthesis'};
  await f.player.check(); await f.player.finish(f.reply);
  assert.deepEqual(f.audible, ['start']); assert.deepEqual(f.heard, []);
  const source = f.player.playing; source.onended(); await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.heard.length, 1); assert.equal(f.heard[0].pcm, undefined);
  await f.player.finish(f.reply); await f.player.check();
  assert.deepEqual(f.calls, ['speech-synthesis/request', 'speech-approvals/request/release',
    'speech-approvals/request/played', 'playback-cues/request']); f.player.close();
});
test('late native input captions do not cancel committed output; microphone activity still does', async () => {
  const f = fixture(); await f.player.check();
  f.player.inputTranscript({type: 'session.input_transcript.delta', delta: 't: {"'});
  assert.deepEqual(f.audible, ['start']); assert.ok(f.player.current);
  f.player.interrupt(); assert.deepEqual(f.audible, ['start', 'stop']); assert.equal(f.player.current, null);
  assert.deepEqual(f.heard, []); f.player.close();
});
test('interruption during synthesis or release never starts audio or retries the paid request', async () => {
  for (const phase of ['speech-synthesis/request', 'speech-approvals/request/release']) {
    const f = fixture(); let resolve, entered;
    const waiting = new Promise(done => {entered = done;});
    f.player.api = async path => {if (path !== phase) return f.reply; entered(); return new Promise(done => {resolve = done;});};
    const check = f.player.check(); await waiting; f.player.interrupt(); resolve(phase.startsWith('speech-synthesis') ? f.reply : true); await check;
    await f.player.check(); assert.deepEqual(f.audible, []); assert.deepEqual(f.heard, []); f.player.close();
  }
});
test('lost completion acknowledgement never replays audio or sends the final task cue', async () => {
  const f = fixture(); f.player.finalCue = {id: 'request', segmentId: 'synthesis'};
  const api = f.player.api; f.player.api = async path => {if (path.endsWith('/played')) throw Error('lost'); return api(path);};
  await f.player.check(); f.player.playing.onended(); await f.player.finish(f.reply); await f.player.check();
  assert.deepEqual(f.audible, ['start']); assert.deepEqual(f.heard, []); assert.equal(f.warnings.length, 1);
  assert.ok(!f.calls.some(path => path.startsWith('playback-cues/'))); f.player.close();
});
test('invalid identity, expired output and malformed PCM never reach release', async () => {
  for (const change of [r => r.id = 'other', r => r.receiptIds = ['native-caption'], r => r.pcm = 'AA==',
    r => r.sampleRate = 16000, r => r.expiresAt = new Date(0).toISOString()]) {
    const f = fixture(); change(f.reply); await f.player.check();
    assert.deepEqual(f.audible, []); assert.equal(f.calls.length, 1); f.player.close();
  }
});
test('known leading PCM silence does not lose the first observed speech onset', async () => {
  const f = fixture(), bytes = Buffer.alloc(24000 * 2 * 2);
  for (let i = 18000; i < 36000; i++) bytes.writeInt16LE(Math.round(6000 * Math.sin(2 * Math.PI * 440 * i / 24000)), i * 2);
  f.reply.pcm = bytes.toString('base64'); let onsetWait;
  f.player.observer.scheduled = (cue, segment, wait) => {onsetWait = wait;};
  await f.player.check(); assert.deepEqual(f.audible, ['start']);
  assert.ok(onsetWait >= 1200 && onsetWait <= 1300, 'Allow the measured 750ms leading silence plus the existing onset allowance');
  f.player.close();
});
test('replacement and interruption after scheduling cannot be reported as delivered', async () => {
  for (const replace of [false, true]) {
    const f = fixture(); await f.player.check(); const source = f.player.playing;
    if (replace) f.player.update([], null, 'replacement'); else f.player.interrupt();
    source.onended(); await f.player.finish(f.reply);
    assert.deepEqual(f.audible, ['start', 'stop']); assert.deepEqual(f.heard, []); f.player.close();
  }
});
