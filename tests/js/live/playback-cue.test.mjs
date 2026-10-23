import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackSettlingGate} from '../../../src/main/resources/public/live/playback-cue.js';

function fixture() {
  let now = 0; const seen = [], gate = new PlaybackSettlingGate(cue => seen.push(cue.id), () => now, () => now);
  const cue = {id: 'round-one', segmentId: null, expiresAt: new Date(30000).toISOString()};
  const sample = (voiced, playable = true, jump = 50) => {
    now += jump; gate.sample({voiced, playable, mediaTime: now / 1000});
  };
  const quiet = () => {for (let i = 0; i < 15; i++) sample(false);};
  return {gate, seen, cue, sample, quiet};
}
test('captured countdown and observed playback quiet are both necessary; duplicate updates cannot execute twice', () => {
  const f = fixture(); f.gate.update(f.cue); f.sample(true); f.sample(true); f.quiet();
  assert.deepEqual(f.seen, []);
  f.gate.update({...f.cue, segmentId: 'complete-countdown'}); f.quiet();
  assert.deepEqual(f.seen, ['round-one']); f.gate.update({...f.cue, segmentId: 'complete-countdown'}); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
});
test('no observed voice, stalled/muted output, sampling gaps and expired cues cannot trigger', () => {
  for (const failure of ['no_voice', 'unplayable', 'gap', 'expired']) {
    const f = fixture(); f.gate.update({...f.cue, segmentId: 'complete-countdown',
      ...(failure === 'expired' ? {expiresAt: new Date(0).toISOString()} : {})});
    if (failure !== 'no_voice') {f.sample(true); f.sample(true);}
    for (let i = 0; i < 15; i++) f.sample(false, failure !== 'unplayable', failure === 'gap' ? 300 : 50);
    assert.deepEqual(f.seen, [], failure);
  }
});
test('interruption and replacement cancel the old cue; a new round needs its own voice evidence', () => {
  const f = fixture(); f.gate.update({...f.cue, segmentId: 'old'}); f.sample(true); f.sample(true);
  f.gate.interrupt(); f.gate.update({...f.cue, segmentId: 'old'}); f.quiet(); assert.deepEqual(f.seen, []);
  f.gate.update({id: 'round-two', segmentId: 'new', expiresAt: f.cue.expiresAt});
  f.quiet(); assert.deepEqual(f.seen, []); f.sample(true); f.quiet(); assert.deepEqual(f.seen, ['round-two']);
  f.gate.update(null); f.quiet(); assert.deepEqual(f.seen, ['round-two']);
});
