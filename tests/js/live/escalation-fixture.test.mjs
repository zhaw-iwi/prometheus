import test from 'node:test';
import assert from 'node:assert/strict';
import {acceptanceInvitation, conversationReturn, visibleCountAlternative, positiveFeedback} from '../../gptlive/escalation_journeys.mjs';

test('acceptance pacing recognises natural invitations and rejects preliminary speech', () => {
  for (const text of ['Shall we start it now?', 'Say “start”, and we will try it.',
    'We can begin when you say so.', 'We’ll start whenever you are ready.', 'Ready to start when you say go.'])
    assert.match(text, acceptanceInvitation);
  for (const text of ['Sure, I can propose that.', 'I am checking.', 'Ready to accept.',
    'I will propose it without starting.', 'Okay, starting it now.'])
    assert.doesNotMatch(text, acceptanceInvitation);
});

test('return pacing accepts ordinary conversation paraphrases', () => {
  for (const text of ["Sure, let's continue chatting. What's next?", 'We are back in Conversation.', 'We can talk again.'])
    assert.match(text, conversationReturn);
  assert.doesNotMatch('The task is still active.', conversationReturn);
});

test('count disclosure accepts simultaneous visibility but not arrival alone', () => {
  for (const text of ['More than one person is visible.', 'At least two people are visible.',
    "When you're already in view and a second person is visible, I give one brief welcome."])
    assert.match(text, visibleCountAlternative);
  for (const text of ['When another person joins you.', 'When a second person arrives.', 'When one person is visible.'])
    assert.doesNotMatch(text, visibleCountAlternative);
});

test('feedback recognises a fitting answer without accepting the solution alone', () => {
  for (const text of ['Correct, a piano.', 'Nice guess. It fits perfectly; a piano has keys, but they open no doors.'])
    assert.match(text, positiveFeedback);
  for (const text of ['Piano.', 'Incorrect, piano is wrong.', 'The piano has bright keys.'])
    assert.doesNotMatch(text, positiveFeedback);
});
