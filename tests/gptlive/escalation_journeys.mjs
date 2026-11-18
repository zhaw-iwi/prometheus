// Historical ES-08 review helpers retained for labelled fixture regression tests.
// Current native provider journeys use gptlivefix_journey.mjs.
import assert from 'node:assert/strict';

// Wait for a usable invitation, not preliminary speech or "ready" on its own.
export const acceptanceInvitation = /\b(?:shall|would you like|want|wanna|accept|start|begin|ready|go ahead|proceed|try it)\b[^?]*\?|\bsay (?:if|when) you want (?:me|us) to (?:start|begin)\b|\bsay\s+["'“‘]?start\b|\b(?:we can|we will|we['’]ll|ready to)\s+(?:start|begin|proceed)\s+(?:when|whenever|once)\s+you\s+(?:say (?:so|go|start)|accept|(?:are|feel) ready)\b/i;
export const conversationReturn = /\b(?:conversation|chat(?:ting)?|talk(?:ing)?)\b/i;
export const visibleCountAlternative = /at least (?:two|2)|more than (?:one|1)|(?:two|2) people|you(?:'re| are) already in (?:view|the scene).{0,50}a second person is visible/i;
// This checks for an evaluation only. Its factual polarity is reviewed against the actual heard question.
export const feedbackEvaluation = /\b(?:correct|incorrect|right|wrong|exactly|yes|fits perfectly|not quite|does not fit|doesn't fit)\b|\b(?:fits|matches)\b.{0,80}\bbut not\b/i;
// A setup may reuse a clue word. Count actual questions; semantic review still checks their meaning.
export function assertOneQuestion(text) {
  assert.equal((text.match(/\?/g) || []).length, 1, 'One authorized riddle question must not be repeated in native speech');
}

// Review the associated contribution, not a later, separately authorized Reflection turn.
// Receipt-aware capture may have checked only a complete sentence suffix of this segment.
export function capturedContribution(memory, ledger, synthesized = []) {
  const response = memory['task.response'];
  assert.equal(response?.status, 'CAPTURED');
  if (response.origin === 'SYNTHESIZED') {
    const speech = synthesized.find(item => item.segmentId === response.segment && item.origin === 'SYNTHESIZED');
    assert.equal(speech?.played, true, 'Synthesized output requires separate persisted playback evidence');
    assert.equal(response.played, true); assert.ok(speech.text);
    return {segment: speech.segmentId, text: speech.text, origin: 'SYNTHESIZED'};
  }
  const segment = ledger.find(item => item.segmentId === response.segment && item.speaker === 'ASSISTANT');
  assert.equal(segment?.status, 'COMPLETE', 'The associated assistant segment must exist and be complete');
  const offset = response.speechOffset ?? 0;
  if (memory['live.speechApproval']) {
    assert.equal(response.played, true, 'Semantic capture is not observed playback');
    assert.equal(offset, 0, 'The approved audio must match the entire segment');
  }
  assert.ok(Number.isInteger(offset) && offset >= 0 && offset < segment.text.length, 'Invalid captured speech offset');
  return {segment: segment.segmentId, text: segment.text.slice(offset)};
}
