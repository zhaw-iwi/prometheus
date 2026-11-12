import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackSettlingGate, LivePlaybackObserver} from '../../../src/main/resources/public/live/playback-cue.js';

const ledger = (segmentId = 'speech', receiptIds = ['target']) => [{segmentId, speaker: 'ASSISTANT', status: 'COMPLETE', receiptIds}];
function fixture() {
  let now = 0;
  const seen = [], diagnostics = [], gate = new PlaybackSettlingGate(cue => seen.push(cue.id), () => now, () => now, value => diagnostics.push(value));
  const cue = {id: 'round-one', segmentId: null, expiresAt: new Date(60000).toISOString()};
  const sample = (voiced, playable = true, jump = 50, mediaTime) => {
    now += jump; gate.sample({voiced, playable, mediaTime: mediaTime ?? now / 1000});
  };
  const quiet = (ms = 750) => {for (let i = 0; i < ms / 50; i++) sample(false);};
  const speak = (id = 'target') => {gate.caption(id); for (let i = 0; i < 8; i++) sample(true);};
  const capture = () => gate.update({...cue, segmentId: 'speech'}, ledger());
  return {gate, seen, cue, sample, quiet, speak, capture, diagnostics};
}

test('matching capture and observed quiet are both necessary; duplicate polls acknowledge once', () => {
  const f = fixture(); f.gate.update(f.cue); f.speak(); f.quiet();
  assert.deepEqual(f.seen, []);
  f.capture(); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
  f.capture(); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('short speech finishing before the first cue retains its receipt-bound evidence across empty polls', () => {
  const f = fixture(); f.speak(); f.quiet(); f.gate.update(null, ledger()); f.quiet();
  f.gate.update({...f.cue, segmentId: 'speech'}); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
});

test('association delayed beyond ten seconds still uses the completed response within its deadline', () => {
  const f = fixture(); f.gate.update(f.cue); f.speak(); f.quiet(20000);
  f.capture(); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('a scoped countdown can settle after input discarded an earlier prefix of the same response', () => {
  const f = fixture();
  f.speak('discarded-prefix'); f.quiet(); f.gate.interrupt();
  // Live continues the same response after the input, then counts without an audio break.
  f.speak('fresh-intro');
  for (const id of ['schere', 'stein', 'papier']) {
    f.gate.caption(id); for (let i = 0; i < 4; i++) f.sample(true);
  }
  f.quiet();
  const cue = {...f.cue, segmentId: 'speech', receiptIds: ['schere', 'stein', 'papier']};
  const segments = ledger('speech', ['discarded-prefix', 'fresh-intro', 'schere', 'stein', 'papier']);
  f.gate.update(cue, segments); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
  f.gate.update(cue, segments); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('scoped receipts must be a bounded contiguous ordered selection from a complete assistant segment', () => {
  for (const scope of [['missing'], ['target', 'target'], ['target', 'prefix'], ['prefix', 'last'],
    Array.from({length: 33}, (_, i) => `receipt-${i}`), 'target']) {
    const f = fixture(); f.speak(); f.quiet();
    f.gate.update({...f.cue, segmentId: 'speech', receiptIds: scope}, ledger('speech', ['prefix', 'target', 'last']));
    f.quiet(); assert.deepEqual(f.seen, [], JSON.stringify(scope));
  }
  for (const invalid of [{status: 'PENDING'}, {speaker: 'USER'}]) {
    const f = fixture(); f.speak(); f.quiet();
    f.gate.update({...f.cue, segmentId: 'speech', receiptIds: ['target']}, [{...ledger()[0], ...invalid}]);
    f.quiet(); assert.deepEqual(f.seen, []);
  }
});

test('countdown receipt selection cannot borrow unrelated earlier audio or survive an interruption', () => {
  const f = fixture(); f.speak('other');
  for (let i = 0; i < 20; i++) f.sample(true);
  f.gate.caption('target'); for (let i = 0; i < 8; i++) f.sample(true);
  f.quiet();
  f.gate.update({...f.cue, segmentId: 'speech', receiptIds: ['target']}, ledger('speech', ['missing-prefix', 'target']));
  f.quiet(); assert.deepEqual(f.seen, []);
  assert.equal(f.diagnostics.at(-1).outcome, 'overlapping_segment_evidence');
  const g = fixture();
  const cue = {...g.cue, segmentId: 'speech', receiptIds: ['target']};
  g.gate.update(cue, ledger()); g.speak(); g.gate.interrupt(); g.quiet();
  g.gate.update(cue, ledger()); g.speak(); g.quiet(); assert.deepEqual(g.seen, []);
});

test('no voice, unplayable output, sampling gaps, stalled media and expired cues cannot trigger', () => {
  for (const receiptIds of [[], ['target']]) for (const failure of ['no_voice', 'unplayable', 'gap', 'stalled', 'expired']) {
    const f = fixture(), cue = {...f.cue, segmentId: 'speech', receiptIds};
    f.gate.update(cue, ledger());
    if (failure !== 'no_voice') f.speak(); else f.gate.caption('target');
    if (failure === 'expired') f.gate.update({...cue, expiresAt: new Date(0).toISOString()});
    for (let i = 0; i < 20; i++) f.sample(false, failure !== 'unplayable', failure === 'gap' ? 300 : 50, failure === 'stalled' ? 0 : undefined);
    assert.deepEqual(f.seen, [], `${receiptIds.length ? 'scoped' : 'whole'} ${failure}`);
  }
});

test('missing, incomplete, wrong-speaker or unrelated segment receipts never borrow other speech', () => {
  for (const scenario of ['missing', 'incomplete', 'user', 'before', 'after', 'overlap']) {
    const f = fixture();
    if (scenario === 'before') {f.speak('other'); f.quiet(2000);}
    if (scenario !== 'missing') f.gate.caption('target');
    if (scenario === 'after') {f.quiet(2000); f.speak('other');}
    if (['missing', 'incomplete', 'user', 'overlap'].includes(scenario)) f.speak(scenario === 'overlap' ? 'other' : 'target');
    f.quiet();
    const segments = ledger();
    if (scenario === 'missing') segments[0].receiptIds = ['not-received'];
    if (scenario === 'incomplete') segments[0].status = 'INCOMPLETE';
    if (scenario === 'user') segments[0].speaker = 'USER';
    f.gate.update({...f.cue, segmentId: 'speech'}, segments); f.quiet();
    assert.deepEqual(f.seen, [], scenario);
  }
});

test('interruption fences both known and not-yet-received cues; a new response needs new receipts', () => {
  for (const earlyCue of [false, true]) {
    const f = fixture(); if (earlyCue) f.gate.update(f.cue);
    f.speak(); f.quiet(); f.gate.interrupt(); f.capture(); f.quiet(); assert.deepEqual(f.seen, []);
    f.gate.update({id: 'round-two', segmentId: 'new', expiresAt: f.cue.expiresAt}, ledger('new', ['next']));
    f.quiet(); assert.deepEqual(f.seen, []);
    f.speak('next'); f.quiet(); assert.deepEqual(f.seen, ['round-two']);
  }
});

test('a caption arriving during an older utterance cannot claim that utterance as its playback', () => {
  const f = fixture(); f.speak('other');
  for (let i = 0; i < 20; i++) f.sample(true);
  f.gate.caption('target'); for (let i = 0; i < 8; i++) f.sample(true);
  f.quiet(); f.capture(); f.quiet(); assert.deepEqual(f.seen, []);
  assert.equal(f.diagnostics.at(-1).outcome, 'overlapping_segment_evidence');
});

test('muting or a sampling gap before association invalidates even already-settled evidence', () => {
  for (const receiptIds of [[], ['target']]) for (const muted of [true, false]) {
    const f = fixture(); f.speak(); f.quiet(); f.sample(false, !muted, muted ? 50 : 300);
    f.gate.update({...f.cue, segmentId: 'speech', receiptIds}, ledger());
    f.quiet(); assert.deepEqual(f.seen, []);
  }
});

test('quantized but progressing media time works', () => {
  const f = fixture(); f.capture(); f.gate.caption('target');
  for (let i = 1; i <= 25; i++) f.sample(i <= 5, true, 50, Math.floor(i / 5) / 4);
  assert.deepEqual(f.seen, ['round-one']);
});

test('evidence retention is bounded and old receipt identities cannot establish a new cue', () => {
  const f = fixture(); f.speak(); f.quiet(66000);
  f.gate.update({...f.cue, segmentId: 'speech', expiresAt: new Date(120000).toISOString()}, ledger()); f.quiet();
  assert.deepEqual(f.seen, []); assert.ok(f.gate.frames.length <= 1400); assert.equal(f.gate.receipts.size, 0);
  for (let i = 0; i < 1100; i++) f.gate.caption(`receipt-${i}`);
  assert.equal(f.gate.receipts.size, 1024);
});

test('a broken diagnostic consumer cannot block valid completion', () => {
  const f = fixture(); f.gate.diagnostic = () => {throw Error('consumer');};
  f.capture(); f.speak(); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('production observer samples before a cue and discards evidence on stream replacement and close', async () => {
  const originals = Object.fromEntries(['AudioContext', 'setInterval', 'clearInterval', 'document'].map(key => [key, globalThis[key]]));
  let now = 0, timer, voiced = false, closed = 0;
  globalThis.document = {hidden: false};
  globalThis.setInterval = fn => {timer = fn; return 1;}; globalThis.clearInterval = () => {timer = null;};
  const connection = () => ({connect() {}, disconnect() {}});
  globalThis.AudioContext = class {
    state = 'running';
    createMediaStreamSource() {return connection();}
    createGain() {return {...connection(), gain: {value: 0}};}
    createAnalyser() {return {...connection(), getFloatTimeDomainData(samples) {samples.fill(voiced ? .1 : 0);}};}
    async resume() {} async close() {closed++;}
  };
  const audio = {currentTime: 0, paused: false, muted: false, volume: 1, readyState: 4}, seen = [];
  const observer = new LivePlaybackObserver(audio, cue => seen.push(cue.id));
  observer.gate.now = () => now; observer.gate.wall = () => now;
  const stream = () => ({getAudioTracks: () => [{readyState: 'live', muted: false}]});
  const advance = (ms, voice = false) => {voiced = voice; for (let i = 0; i < ms / 50; i++) {now += 50; audio.currentTime = now / 1000; timer?.();}};
  try {
    observer.stream(stream()); await Promise.resolve(); advance(100);
    observer.caption('target'); advance(400, true); advance(2000);
    observer.update({id: 'late', segmentId: 'speech', expiresAt: new Date(60000).toISOString()}, ledger());
    advance(750); assert.deepEqual(seen, ['late']);
    observer.caption('next'); advance(400, true); advance(750);
    observer.stream(stream()); await Promise.resolve();
    observer.update({id: 'obsolete', segmentId: 'new', expiresAt: new Date(60000).toISOString()}, ledger('new', ['next']));
    advance(750); assert.deepEqual(seen, ['late']);
    observer.close(); advance(750); assert.equal(timer, null); assert.equal(closed, 2);
  } finally {
    observer.close();
    for (const [key, value] of Object.entries(originals)) {if (value === undefined) delete globalThis[key]; else globalThis[key] = value;}
  }
});


test('buffered playback binds receipts after initial media warmup but never after a later stall', () => {
  for (const failure of ['none', 'stall', 'interruption']) {
    const f = fixture(), observer = new LivePlaybackObserver({}, () => {}); observer.gate = f.gate;
    observer.scheduled({...f.cue, segmentId: 'speech'}, ledger()[0]);
    observer.sample({voiced: false, playable: false, mediaTime: 0});
    assert.equal(f.gate.receipts.size, 0);
    observer.sample({voiced: true, playable: true, mediaTime: .05});
    assert.equal(f.gate.receipts.size, 1);
    if (failure === 'stall') observer.sample({voiced: true, playable: false, mediaTime: .05});
    if (failure === 'interruption') observer.interrupt();
    for (let i = 0; i < 10; i++) f.sample(true);
    f.quiet();
    assert.deepEqual(f.seen, failure === 'none' ? ['round-one'] : []);
    observer.close();
  }
});

test('countdown acknowledgements retain audio but block execution until the backend confirms the complete input', () => {
  const f = fixture(), cue = {...f.cue, segmentId: 'speech', allowAcknowledgements: true};
  f.gate.update({...cue, segmentId: null}); f.speak(); f.gate.input('ready-yes');
  f.gate.update(cue, ledger()); f.quiet();
  assert.deepEqual(f.seen, []);
  assert.equal(f.diagnostics.at(-1).outcome, 'awaiting_input_classification');
  const acknowledgement = {speaker: 'USER', status: 'COMPLETE', reason: 'countdown_acknowledgement', receiptIds: ['ready-yes']};
  f.gate.update(cue, [...ledger(), acknowledgement]); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
  f.gate.update(cue, [...ledger(), acknowledgement]); f.quiet();
  assert.deepEqual(f.seen, ['round-one']);
});

test('a partial yes followed by stop, an incomplete input or ordinary conversation still invalidates the cue', () => {
  for (const result of [{status: 'COMPLETE', reason: 'acknowledged'}, {status: 'INCOMPLETE'}, {status: 'CLARIFICATION'},
    {status: 'LATE'}, {status: 'INCOMPLETE', reason: 'empty_transcript'}, {status: 'IGNORED', reason: 'unknown'}]) {
    const f = fixture(), cue = {...f.cue, segmentId: 'speech', allowAcknowledgements: true};
    f.gate.update(cue, ledger()); f.speak(); f.gate.input('yes-prefix'); f.gate.input('stop-suffix'); f.quiet();
    assert.deepEqual(f.seen, []);
    f.gate.update(cue, [...ledger(), {speaker: 'USER', ...result, receiptIds: ['yes-prefix', 'stop-suffix']}]);
    f.quiet(); assert.deepEqual(f.seen, []);
    assert.equal(f.gate.done.has(cue.id), true);
  }
});

test('a complete backend-ignored empty input resolves pending classification without cancelling the countdown', () => {
  const f = fixture(), cue = {...f.cue, segmentId: 'speech', allowAcknowledgements: true};
  f.gate.update({...cue, segmentId: null}); f.gate.input('whitespace');
  f.speak(); f.gate.update(cue, ledger()); f.quiet(); assert.deepEqual(f.seen, []);
  f.gate.update(cue, [...ledger(), {speaker: 'USER', status: 'IGNORED', reason: 'empty_transcript', receiptIds: ['whitespace']}]);
  f.quiet(); assert.deepEqual(f.seen, ['round-one']);
  f.gate.update(cue, ledger()); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('a slow countdown requires every complete segment and receipt before one playback acknowledgement', () => {
  const f = fixture(), cue = {...f.cue, segmentId: 'last', segmentIds: ['rock', 'last'], receiptIds: ['rock', 'paper', 'scissors']};
  f.gate.update({...cue, segmentId: null, segmentIds: [], receiptIds: []});
  f.speak('rock'); f.quiet(1000); f.speak('paper'); f.quiet(1000); f.speak('scissors'); f.quiet();
  const segments = [...ledger('rock', ['rock']), ...ledger('last', ['paper', 'scissors'])];
  f.gate.update(cue, segments.slice(1)); f.quiet(); assert.deepEqual(f.seen, []);
  f.gate.update(cue, segments); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
  f.gate.update(cue, segments); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
});

test('multi-segment playback rejects missing voice, invalid segment selections and interrupted evidence', () => {
  for (const failure of ['missing_voice', 'incomplete', 'duplicate', 'wrong_last', 'oversized', 'unselected', 'interrupted']) {
    const f = fixture(), cue = {...f.cue, segmentId: 'last', segmentIds: ['rock', 'last'], receiptIds: ['rock', 'paper', 'scissors']};
    f.gate.update({...cue, segmentId: null});
    if (failure === 'missing_voice') f.gate.caption('rock'); else f.speak('rock');
    f.quiet(2000); f.speak('paper'); f.quiet(1000); f.speak('scissors'); f.quiet();
    const segments = [...ledger('rock', ['rock']), ...ledger('last', ['paper', 'scissors'])];
    if (failure === 'incomplete') segments[0].status = 'INCOMPLETE';
    if (failure === 'duplicate') cue.segmentIds = ['rock', 'rock', 'last'];
    if (failure === 'wrong_last') cue.segmentIds = ['last', 'rock'];
    if (failure === 'oversized') cue.segmentIds = ['one', 'two', 'rock', 'last'];
    if (failure === 'unselected') {cue.segmentIds = ['extra', 'rock', 'last']; segments.unshift(...ledger('extra', ['other']));}
    if (failure === 'interrupted') f.gate.interrupt();
    f.gate.update(cue, segments); f.quiet(); assert.deepEqual(f.seen, [], failure);
  }
});

test('late acknowledgement captions use durable receipt classification; missing classification and withdrawn cues never execute', () => {
  const f = fixture(), cue = {...f.cue, segmentId: 'speech', allowAcknowledgements: true};
  f.gate.update(cue, [...ledger(), {speaker: 'USER', status: 'COMPLETE', reason: 'countdown_acknowledgement', receiptIds: ['yes']}]);
  f.speak(); f.gate.input('yes'); f.quiet(); assert.deepEqual(f.seen, ['round-one']);
  const g = fixture(), pending = {...g.cue, segmentId: 'speech', allowAcknowledgements: true};
  g.gate.update(pending, ledger()); g.speak(); g.gate.input('unknown'); g.quiet();
  assert.deepEqual(g.seen, []);
  g.gate.update(null); g.gate.update(pending, ledger()); g.quiet(); assert.deepEqual(g.seen, []);
});
