import test from 'node:test';
import assert from 'node:assert/strict';
import {armingReady, speechLatency, conversationLatency, compareConversations} from './commanding_evidence.mjs';

test('fresh cues wait for the current arming receipt and its updated boundary', () => {
  const memory = {'task.after': new Date(3000).toISOString(),
    'core.task.response': {id: 'current', session: 's', epoch: 'e'},
    'task.response': {id: 'current', session: 's', epoch: 'e', purpose: 'ARMING', status: 'CHECKING'}};
  assert.equal(armingReady(memory, 4000), false);
  memory['task.response'].status = 'CAPTURED';memory['task.after'] = new Date(5000).toISOString();
  assert.equal(armingReady(memory, 4000), false);
  assert.equal(armingReady(memory, 5000), false);
  assert.equal(armingReady(memory, 5001), true);
  memory['task.response'].id = 'old';assert.equal(armingReady(memory, 5001), false);
});

test('first native onset includes speech before the visitor finishes, excluding earlier audio', () => {
  const events = [50, 300, 1600, 2700].map(at => ({type: 'output_voice', at}));
  assert.deepEqual(speechLatency(events, {startMs: 100, speechEndMs: 2000}),
    {speechEndToOutputMs: -1700, legacyNearEndOnsetMs: -400});
});
test('missing audio and incomplete samples are inconclusive, never zero latency', () => {
  const turn = {name: 'hello', events: [], input: {startMs: 100, speechEndMs: 2000}};
  assert.equal(speechLatency([], turn.input).speechEndToOutputMs, null);
  assert.throws(() => conversationLatency([turn]), /Three matched/);
  assert.throws(() => conversationLatency(['hello', 'activities', 'planning'].map(name => ({...turn, name}))), /Missing observed/);
});
test('matched comparison keeps the original median and maximum limits', () => {
  const report = onsets => ({plan: {models: {live: 'synthetic'}},
    stimulus: {clips: Object.fromEntries(['hello', 'activities', 'planning'].map(name => [name, {sha256: name}]))},
    turns: onsets.map((at, i) => ({name: ['hello', 'activities', 'planning'][i],
      input: {startMs: 0, speechEndMs: 1000}, events: [{type: 'output_voice', at}]}))});
  const baseline = report([1000, 1100, 1200]);
  assert.equal(compareConversations(baseline, report([1900, 2100, 3200])).passed, true);
  assert.equal(compareConversations(baseline, report([1900, 2101, 3200])).passed, false);
  assert.equal(compareConversations(baseline, report([1900, 2100, 3201])).passed, false);
  const mismatch = report([1000, 1100, 1200]);mismatch.stimulus.clips.hello.sha256 = 'different';
  assert.throws(() => compareConversations(baseline, mismatch), /Unmatched visitor/);
});
