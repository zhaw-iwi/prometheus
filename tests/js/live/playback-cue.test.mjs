import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackSettlingGate} from '../../../src/main/resources/public/live/playback-cue.js';

function fixture() {
  let now = 0; const seen = [], diagnostics = [], gate = new PlaybackSettlingGate(cue => seen.push(cue.id), () => now, () => now, value => diagnostics.push(value));
  const cue = {id: 'round-one', segmentId: null, expiresAt: new Date(30000).toISOString()};
  const sample = (voiced, playable = true, jump = 50, mediaTime) => {
    now += jump; gate.sample({voiced, playable, mediaTime: mediaTime ?? now / 1000});
  };
  const quiet = () => {for (let i = 0; i < 15; i++) sample(false);};
  return {gate, seen, cue, sample, quiet, diagnostics};
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
test('resuming after muted output cannot reuse pre-mute speech as a completed countdown', () => {
  const f = fixture(); f.gate.update({...f.cue, segmentId: 'countdown'});
  f.sample(true); f.sample(true); f.sample(false, false); f.quiet();
  assert.deepEqual(f.seen, []);
});
test('quantized but progressing HTML media time works; a genuinely stalled clock does not', () => {
  for (const stalled of [false, true]) {
    const f = fixture(); f.gate.update({...f.cue, segmentId: 'countdown'});
    for (let i = 1; i <= 25; i++) f.sample(i <= 5, true, 50, stalled ? 0 : Math.floor(i / 5) / 4);
    assert.deepEqual(f.seen, stalled ? [] : ['round-one']);
  }
});

test('delayed capture retains observed playback evidence and explains its settling boundary', () => {
  const f = fixture(); f.gate.update(f.cue); f.sample(true); f.sample(true); f.quiet();
  assert.equal(f.diagnostics.at(-1).outcome, 'awaiting_capture');
  const count = f.diagnostics.length; f.quiet(); assert.equal(f.diagnostics.length, count);
  f.gate.update({...f.cue, segmentId: 'captured'}); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
  assert.ok(f.diagnostics.some(value => value.outcome === 'ready' && value.cueId === f.cue.id && value.segmentId === 'captured'));
});

test('user input before late capture leaves a diagnosable interruption without manufacturing completion', () => {
  const f = fixture(); f.gate.update(f.cue); f.sample(true); f.sample(true); f.quiet();
  f.gate.interrupt(); f.gate.update({...f.cue, segmentId: 'late-capture'}); f.quiet();
  assert.deepEqual(f.seen, []);
  assert.ok(f.diagnostics.some(value => value.outcome === 'input_interrupted'));
  assert.equal(f.diagnostics.at(-1).outcome, 'already_finished');
});

test('a broken diagnostic consumer cannot block a valid playback acknowledgement', () => {
  const f = fixture(); f.gate.diagnostic = () => { throw Error('consumer'); };
  f.gate.update({...f.cue, segmentId: 'captured'}); f.sample(true); f.sample(true); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
});
