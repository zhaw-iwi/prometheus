import test from 'node:test';
import assert from 'node:assert/strict';
import {capturesConsent, deniesRenderedHand} from './forum_speech.mjs';

test('a rendered gesture conflicts with a description-only reply', () => {
  assert.equal(deniesRenderedHand("I can't actually move or show a hand myself, but I can help describe or plan how it would look if that helps."), true);
  assert.equal(deniesRenderedHand('I cannot make hand signs. I can only describe them.'), true);
});

test('natural acknowledgements and distinctions about physical or confirmed motion are allowed', () => {
  for (const reply of ['', 'Sure.', 'I will show a thumbs-up.', 'I cannot confirm the renderer yet.',
    'I can’t move a physical hand. I can describe it, or I can show it on the digital robot.']) {
    assert.equal(deniesRenderedHand(reply), false, reply);
  }
});

test('active execution requires evidence of the scripted consent or its spoken repair', () => {
  for (const text of ['I accept the current proposal.', 'Start now.', 'Go ahead!', 'Yes.'])
    assert.equal(capturesConsent(text), true, text);
  for (const text of ['I accept the current', 'Now', "Don’t start.", ''])
    assert.equal(capturesConsent(text), false, text);
});
