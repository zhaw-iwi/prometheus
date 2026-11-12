import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ApprovedSpeechPlayer} from '../../../src/main/resources/public/live/approved-speech.js';

function fixture(status = 'APPROVED') {
  globalThis.document = {hidden: false};
  const calls = [], audible = [], heard = [], warnings = [];
  const segment = {segmentId: 'segment', status: 'COMPLETE', speaker: 'ASSISTANT', text: 'Stopped.', receiptIds: ['r']};
  const approval = {status, id: 'request', segmentId: 'segment', text: 'Stopped.', receiptIds: ['r'], expiresAt: new Date(Date.now() + 60000).toISOString()};
  const player = new ApprovedSpeechPlayer({audio: {paused: false, muted: false, volume: 1},
    api: async (path, body) => {calls.push(path); return path === 'speech-approvals' ? approval : true;},
    played: value => heard.push(value), withheld: value => warnings.push(value)});
  const candidate = {voiceEnd: 0};
  player.buffer = {generation: 0, select: () => candidate, take: () => new Float32Array([.1, .2]), interrupt() {this.generation++;}};
  player.context = {sampleRate: 8000, currentTime: 1, state: 'running',
    createBuffer: () => ({copyToChannel() {}}), createBufferSource: () => ({connect() {}, disconnect() {}, start() {audible.push('start');}, stop() {audible.push('stop');}}), close: async () => {}};
  player.observer = {scheduled() {}, update() {}, caption() {}, interrupt() {}, close() {}};
  player.segments.set(segment.segmentId, segment);
  return {player, calls, audible, heard, warnings, approval, segment};
}
test('approval and release cannot count as heard; only observed completion enables final acknowledgement', async () => {
  const f = fixture(); f.player.finalCue = {id: 'request', segmentId: 'segment'};
  await f.player.check(); assert.deepEqual(f.audible, ['start']); assert.deepEqual(f.heard, []);
  assert.deepEqual(f.calls, ['speech-approvals', 'speech-approvals/request/release']);
  await f.player.finish(f.approval); await f.player.finish(f.approval);
  assert.equal(f.heard.length, 1); assert.deepEqual(f.calls.slice(2), ['speech-approvals/request/played', 'playback-cues/request']);
  await f.player.check(); assert.equal(f.audible.length, 1); f.player.close();
});
test('rejection, missing proof and release failure never schedule audio', async () => {
  for (const status of ['REJECTED', 'DISCARDED', 'PENDING', 'CHECKING', 'malformed', 'APPROVED']) {
    const f = fixture(status); if (status === 'APPROVED') f.approval.receiptIds = ['wrong'];
    await f.player.check(); assert.deepEqual(f.audible, []); assert.deepEqual(f.heard, []); f.player.close();
  }
  const f = fixture(); f.player.api = async path => path === 'speech-approvals' ? f.approval : false;
  await f.player.check(); assert.deepEqual(f.audible, []); f.player.close();
});
test('a correction discards the rejected draft and plays only the checked new segment once', async () => {
  const f = fixture('CORRECTING');
  await f.player.check(); await f.player.check();
  assert.deepEqual(f.audible, []); assert.deepEqual(f.heard, []); assert.deepEqual(f.warnings, []);
  assert.deepEqual(f.calls, ['speech-approvals']);
  f.player.segments.set('corrected', {...f.segment, segmentId: 'corrected', receiptIds: ['new-receipt']});
  Object.assign(f.approval, {status: 'APPROVED', segmentId: 'corrected', receiptIds: ['new-receipt']});
  await f.player.check(); await f.player.finish(f.approval); await f.player.check();
  assert.deepEqual(f.audible, ['start']); assert.equal(f.heard.length, 1);
  assert.equal(f.calls.filter(path => path.endsWith('/release')).length, 1); f.player.close();
});
test('interruption while either approval or release is in flight fences the result', async () => {
  for (const phase of ['speech-approvals', 'speech-approvals/request/release']) {
    const f = fixture(); let resolve, entered;
    const waiting = new Promise(done => {entered = done;});
    f.player.api = async path => {if (path !== phase) return f.approval; entered(); return new Promise(done => {resolve = done;});};
    const check = f.player.check(); await waiting; f.player.interrupt(); resolve(phase === 'speech-approvals' ? f.approval : true); await check;
    assert.deepEqual(f.audible, []); assert.deepEqual(f.heard, []); f.player.close();
  }
});
test('replacement, mute and expiry stop scheduled playback without reporting it heard', async () => {
  for (const kind of ['replacement', 'mute', 'expired']) {
    const f = fixture(); await f.player.check();
    if (kind === 'mute') f.player.mute(true);
    else { if (kind === 'expired') f.player.current.expiresAt = new Date(0).toISOString(); f.player.update([], null, kind === 'replacement' ? 'new-request' : 'request'); }
    assert.deepEqual(f.audible, ['start', 'stop']); assert.deepEqual(f.heard, []); assert.equal(f.player.current, null); f.player.close();
  }
});
test('hidden, muted, paused and suspended outputs never become playback proof', async () => {
  for (const kind of ['hidden', 'muted', 'paused', 'suspended']) {
    const f = fixture();
    if (kind === 'hidden') document.hidden = true;
    else if (kind === 'suspended') f.player.context.state = 'suspended'; else f.player.audio[kind] = true;
    await f.player.check(); assert.deepEqual(f.audible, []); assert.deepEqual(f.heard, []); f.player.close();
  }
});
test('missing audio becomes visible recovery guidance once instead of silent indefinite waiting', async () => {
  const f = fixture(); f.player.buffer.select = () => null; f.player.buffer.diagnostic = () => ({frames: 2});
  f.player.attempts.set('segment', {seen: performance.now() - 11000});
  await f.player.check(); await f.player.check();
  assert.equal(f.warnings.length, 1); assert.match(f.warnings[0], /complete audio/);
  assert.deepEqual(f.calls, []); assert.deepEqual(f.audible, []); f.player.close();
});
