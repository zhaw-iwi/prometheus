import test from 'node:test';
import assert from 'node:assert/strict';
import {acceptanceInvitation, conversationReturn, visibleCountAlternative, feedbackEvaluation, capturedContribution, assertOneQuestion} from '../../gptlive/escalation_journeys.mjs';

test('synthesized contributions require their own played provenance and cannot borrow a native caption', () => {
  const memory = {'task.response': {status: 'CAPTURED', origin: 'SYNTHESIZED', segment: 'speech', played: true}};
  const output = {segmentId: 'speech', origin: 'SYNTHESIZED', played: true, text: 'Correct.'};
  assert.throws(() => capturedContribution(memory, [{segmentId: 'speech', speaker: 'ASSISTANT', status: 'COMPLETE', text: 'Invented.'}]));
  assert.throws(() => capturedContribution(memory, [], [{...output, played: false}]));
  assert.equal(capturedContribution(memory, [], [output]).text, 'Correct.');
});

test('final feedback review selects its captured segment and leaves later reflection separate', () => {
  const memory = {'task.response': {status: 'CAPTURED', segment: 'feedback'}};
  const ledger = [
    {segmentId: 'feedback', speaker: 'ASSISTANT', status: 'COMPLETE', text: 'Correct, a piano.'},
    {segmentId: 'reflection', speaker: 'ASSISTANT', status: 'COMPLETE', text: 'How was that?'}
  ];
  assert.deepEqual(capturedContribution(memory, ledger), {segment: 'feedback', text: 'Correct, a piano.'});
  ledger[0].text += ' Would you like another round?';
  assert.match(capturedContribution(memory, ledger).text, /Would you like another round\?$/,
    'An invitation inside the actual contribution must remain visible to the rubric');
});

test('final feedback review respects the persisted receipt-aware suffix offset', () => {
  const early = 'Checking that now. ', feedback = 'Correct, a piano.';
  const memory = {'task.response': {status: 'CAPTURED', segment: 'merged', speechOffset: early.length}};
  assert.equal(capturedContribution(memory, [
    {segmentId: 'merged', speaker: 'ASSISTANT', status: 'COMPLETE', text: early + feedback}
  ]).text, feedback);
});

test('final feedback review rejects absent, incomplete or unassociated speech and invalid offsets', () => {
  const response = {status: 'CAPTURED', segment: 'feedback'};
  const segment = {segmentId: 'feedback', speaker: 'ASSISTANT', status: 'COMPLETE', text: 'Correct.'};
  const review = (r, s) => capturedContribution({'task.response': r}, s);
  assert.throws(() => review(response, []));
  assert.throws(() => review({...response, status: 'CHECKING'}, [segment]));
  assert.throws(() => review(response, [{...segment, speaker: 'USER'}]));
  assert.throws(() => review(response, [{...segment, status: 'PARTIAL'}]));
  for (const speechOffset of [-1, .5, segment.text.length])
    assert.throws(() => review({...response, speechOffset}, [segment]));
});

test('acceptance pacing recognises natural invitations and rejects preliminary speech', () => {
  for (const text of ['Shall we start it now?', 'Say “start”, and we will try it.',
    'Say if you want me to start.', 'We can begin when you say so.', 'We’ll start whenever you are ready.', 'Ready to start when you say go.'])
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

test('feedback requires an evaluation but leaves factual polarity for semantic review', () => {
  for (const text of ['Correct, a piano.', 'Nice guess. It fits perfectly; a piano has keys, but they open no doors.',
    'Not quite. A piano fits the keys clue, but a keyboard fits space and enter too.', 'Incorrect, piano is wrong.',
    'A piano fits the keys clue, but not the others: the answer is a keyboard.'])
    assert.match(text, feedbackEvaluation);
  for (const text of ['Piano.', 'The piano has bright keys.', 'Let me evaluate your answer.'])
    assert.doesNotMatch(text, feedbackEvaluation);
});

test('one riddle question may follow a setup that repeats its clue word', () => {
  assert.doesNotThrow(() => assertOneQuestion('I’ve got keys but no locks. What object has keys that cannot open a door?'));
  assert.throws(() => assertOneQuestion('What has keys but opens no doors? What object has keys that cannot open a door?'));
  assert.throws(() => assertOneQuestion('I will ask a question later.'));
});
