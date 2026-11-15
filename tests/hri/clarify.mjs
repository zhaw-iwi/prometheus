import assert from 'node:assert/strict';
export async function clarify(say,scenario){
  const requests={guess:'Could we arrange a short guessing game for a fictional activity? I want to keep an object secret. Please ask me about the missing rules before proposing or starting.',
    context:'I would like a fictional medication-routine check-in based on simulated visible-person counts. Please ask me about the missing conditions before proposing or starting.',
    signs:'I would like a short fictional preference check-in using simulated hand signs. We have not agreed the questions or mappings. Please ask me about a missing requirement before proposing or starting.'};
  const state=await say(requests[scenario]);assert.equal(state.agreement,null);assert.equal(state.actions,0);
  const reply=JSON.parse(state.turns.at(-1).receipt.responsePayload);assert.deepEqual(Object.keys(reply),['speech']);assert.ok(reply.speech.includes('?'));
  assert.ok(!['READY','EXECUTION','REFLECTION'].includes(state.phase),'Missing requirements must be clarified before a complete proposal or action');
}
