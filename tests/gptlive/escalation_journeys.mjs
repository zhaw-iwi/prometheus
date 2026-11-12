// ES-08 scenarios reuse the real-app transport and production playback observer in core_backend_journey.
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

export const escalationClips = {
  'escalation-recovery': {
    proposal: 'Please help me set up a workshop welcome task. I show rock and judge whether your greeting is useful. After I accept and then show a fresh rock sign, give one short welcome to the workshop. First verify rock recognition. One contribution. Propose it without starting.',
    helpPreparation: "I'm stuck. What should I do next?",
    sense: 'What hand sign do you see now?',
    helpReady: "I don't understand. Do I need to demonstrate again?",
    accept: "Let's start.", back: 'Return to conversation.'
  },
  'escalation-dialogue': {
    proposal: 'Propose one riddle round about an object with keys that cannot open a door. I will guess the object and judge your feedback. Ask only the question first, then give feedback after I answer. Two contributions, no sensors or preparation. Do not reveal the answer or start yet.',
    accept: "Let's start.", help: "I'm stuck. Is our task still running?", sense: 'What hand sign do you see now?',
    fragment: 'My answer is', answer: 'My answer is a piano.',
    replace: 'Replace this with a new dialogue riddle round about something that gets wetter as it dries. Same roles, two contributions, question then feedback, no sensors or preparation. Propose only; wait for my acceptance.',
    stop: 'Stop the task.'
  },
  'escalation-social': {
    proposal: 'Propose a task: when another person joins me while I am already in the scene, give one brief welcome. I arrange the scene and judge the welcome. Success means I like it. One action only, no preparation checks. Do not start yet.',
    chooseCount: 'Propose the alternative of at least two people visible, one short welcome, the same roles and success criterion, no preparation. Do not start yet.',
    accept: "Let's start.", restart: 'Explicitly restart the same agreed task with a fresh budget.',
    pause: 'Pause the task.', resume: 'Resume the task.', stop: 'Stop the task.'
  }
};

export async function runEscalation(scenario, h) {
  const {page, say, settled, storage, observe, until, capturedResponse, api, agentPath, report, wait} = h;
  report.rubric = {automatedAuthorityAndDeliveryChecks: 'PENDING', semanticReview: 'PENDING',
    checks: ['truthful recovery and perception', 'visible supported terms and finite budget', 'no premature or duplicate contribution',
      'no answer leak or unsupported promise', 'played speech agrees with durable task state'], physicalAcceptance: 'NOT RUN'};
  const count = memory => memory['task.actions'] || 0;
  const protectedKeys = ['coordination.draft', 'coordination.agreement', 'coordination.run', 'coordination.evidence', 'task.actions', 'task.phase', 'task.revision'];
  function unchanged(before, after) {
    for (const key of protectedKeys) assert.deepEqual(after[key], before[key], `${key} changed during an ordinary/help turn`);
  }
  async function turn(name, expected, expectedUserText) {
    const before = await storage(), index = await page.evaluate(() => logs.length);
    const ledgerPath = `${agentPath}/live/transcripts?sessionId=${h.handle}`;
    const seen = new Set((await api(ledgerPath)).map(segment => segment.segmentId));
    await say(name);
    let answer;
    try {
      // Native speech can precede the durable interpreter result. Quiet captions are not a commit boundary.
      await until('durable visitor turn ' + name, async () => {
        const admitted = (await api(ledgerPath)).filter(segment => segment.speaker === 'USER' && !seen.has(segment.segmentId));
        for (const segment of admitted) if (!['PENDING', 'COMPLETE'].includes(segment.status))
          throw Error(`Visitor turn ${name}: ${segment.status}/${segment.reason}`);
        return admitted.length && admitted.every(segment => segment.status === 'COMPLETE');
      }, 55000);
      if (expectedUserText) {
        const admitted = (await api(ledgerPath)).filter(segment => segment.speaker === 'USER' && !seen.has(segment.segmentId));
        assert.match(admitted.map(segment => segment.text).join(' '), expectedUserText, 'Check the actually admitted visitor words');
      }
      answer = await settled(index, typeof expected === 'function' ? expected(await storage()) : expected);
    }
    catch (error) {
      report.turns.push({name, answer: await h.captionsFrom(index), before, memory: await storage(), error: error.message});
      throw error;
    }
    const memory = await storage();
    report.turns.push({name, answer, before, memory, state: await api(agentPath + '/state')});
    return {answer, memory};
  }
  async function freshQuestion() {
    let pending = Promise.resolve();
    const send = () => pending = pending.then(() => observe('obs.hand.sign', {sign: 'scissor', confidence: .87, ts: new Date().toISOString()}));
    await send(); const timer = setInterval(() => void send().catch(() => {}), 3000);
    try {
      const before = await storage(), result = await turn('sense', /scissor/i);
      unchanged(before, result.memory);
      assert.doesNotMatch(result.answer, /(?:cannot|can't|not able to).{0,20}(?:see|recognise|recognize)|don't have eyes/i);
    } finally { clearInterval(timer); await pending; }
  }
  async function arm(name = 'accept') {
    await turn(name, /wait|armed|active|ready/i); await capturedResponse('arming association');
    const memory = await storage(); assert.equal(memory['task.phase'], 'RUNNING'); assert.equal(count(memory), 0);
    return memory;
  }
  async function cueWindow() {
    const delay = Date.parse((await storage())['task.after']) - Date.now();
    assert.ok(!Number.isFinite(delay) || delay < 10000, 'Unexpected cue boundary');
    if (delay > 0) await wait(delay + 100);
  }
  async function delivered(index, expected, expectedCount) {
    const answer = await settled(index, expected);
    try {
      await until('final speech associated and played', async () => {
        const memory = await storage();
        if (memory['task.phase'] === 'UNCERTAIN') throw Error('Final delivery became UNCERTAIN: ' + memory['coordination.delivery']?.reason);
        return memory['task.phase'] === 'COMPLETED';
      }, 55000);
    } catch (error) {
      report.deliveries ||= []; report.deliveries.push({answer, memory: await storage(), error: error.message});
      throw error;
    }
    const memory = await storage(); assert.equal(count(memory), expectedCount);
    assert.equal(memory['coordination.delivery']?.status, 'PLAYBACK_CONFIRMED');
    const delivery = memory['coordination.delivery'];
    await until('accepted final playback acknowledgement', () => page.evaluate(({id, segment}) =>
      logs.some(e => e.type === 'trial.playback_ack' && e.accepted === true && e.cueId === id && e.segmentId === segment), delivery), 5000);
    report.deliveries ||= []; report.deliveries.push({answer, memory}); return {answer, memory};
  }

  // A promise to propose later is not a reviewed proposal; accepting during that later speech can be lost.
  const proposal = await turn('proposal', scenario === 'escalation-recovery'
    ? /(?:show|demonstrat|recognis|recogniz).{0,60}rock|rock.{0,60}(?:confirm|accept)/i
    : memory => memory['coordination.draft']?.questions?.length
      ? /(?:count|two|visible|alternative)[^?]*\?/i : h.acceptanceQuestion);
  assert.equal(count(proposal.memory), 0); assert.equal(proposal.memory['coordination.agreement'], undefined);
  let draft = proposal.memory['coordination.draft']; assert.ok(draft, 'Spoken request must produce a saved proposal');

  if (scenario === 'escalation-recovery') {
    assert.equal(draft.checks.length, 1); assert.equal(draft.checks[0].value, 'rock');
    assert.equal(draft.reactions.maxActions, 1); assert.equal(draft.reactions.firstAction, 'ON_CUE');
    const before = await storage(), help = await turn('helpPreparation', /rock|show|demonstrat|recogn/i);
    unchanged(before, help.memory);
    const preparationIndex = await page.evaluate(() => logs.length);
    await observe('obs.hand.sign', {sign: 'rock', confidence: .93, ts: new Date().toISOString()});
    report.prepared = await settled(preparationIndex, /recogn|ready|accept|start/i);
    assert.equal(Object.keys((await storage())['coordination.evidence']).length, 1);
    assert.equal(count(await storage()), 0); await freshQuestion();
    const ready = await storage(), readyHelp = await turn('helpReady', /ready|accept|start/i); unchanged(ready, readyHelp.memory);
    assert.match(readyHelp.answer, /no (?:further|additional|more) demonstration|already.{0,40}(?:passed|complete)/i,
      'Completed preparation must not require another demonstration before acceptance');
    const armed = await arm(); assert.deepEqual(armed['coordination.agreement'], draft); await cueWindow();
    const index = await page.evaluate(() => logs.length);
    await observe('obs.hand.sign', {sign: 'rock', confidence: .93, ts: new Date().toISOString()});
    await delivered(index, /welcome/i, 1);
    const returned = await turn('back', conversationReturn);
    assert.equal(returned.memory['task.phase'], 'CONFIGURATION'); assert.equal(returned.memory['coordination.draft'], undefined);
    assert.ok(returned.memory['coordination.outcomes'].some(o => o.run === armed['coordination.run'] && o.delivery?.status === 'PLAYBACK_CONFIRMED'));
  } else if (scenario === 'escalation-dialogue') {
    assert.equal(draft.reactions, null); assert.equal(draft.dialogueLimit, 2); assert.deepEqual(draft.questions, []);
    assert.doesNotMatch(proposal.answer, /piano|keyboard/i, 'Do not disclose the solution while proposing');
    const question = await turn('accept', /keys|door/i); await capturedResponse('riddle question association');
    assert.equal(count(question.memory), 1); assert.equal(question.memory['task.phase'], 'RUNNING');
    assert.doesNotMatch(question.answer, /piano|keyboard/i, 'Question must withhold its solution');
    assertOneQuestion(question.answer);
    const before = await storage(), help = await turn('help', /active|running|answer|pause|stop/i);
    unchanged(before, help.memory); assert.doesNotMatch(help.answer, /piano|keyboard/i, 'Help must not reveal the solution');
    await freshQuestion();
    const beforeFragment = await storage(), fragment = await turn('fragment', /finish|complete|go on/i, /my answer is/i);
    unchanged(beforeFragment, fragment.memory);
    assert.deepEqual(fragment.memory['task.response'], beforeFragment['task.response']);
    assert.doesNotMatch(fragment.answer, /piano|keyboard/i, 'Incomplete-answer support must not supply the solution');
    const index = await page.evaluate(() => logs.length); await say('answer');
    const feedback = await delivered(index, /piano/i, 2);
    const contribution = capturedContribution(feedback.memory, await api(`${agentPath}/live/transcripts?sessionId=${h.handle}`), await h.synthesizedSpeech());
    report.finalFeedbacks = [contribution];
    assert.match(contribution.text, feedbackEvaluation);
    report.feedbackReview = {question: question.answer, answer: 'My answer is a piano.', feedback: contribution.text,
      factualAssessment: 'PENDING semantic review against all clues; positive feedback is not automatically correct'};
    assert.doesNotMatch(contribution.text, /\?|another round|would you like|feedback.{0,30}welcome/i,
      'Final contribution must not append another question or invitation');
    assert.equal(feedback.memory['coordination.outcome'], 'LIMIT_REACHED');
    const oldRun = feedback.memory['coordination.run'], revision = await turn('replace', h.acceptanceQuestion);
    assert.equal(revision.memory['task.phase'], 'CONFIGURATION'); assert.equal(revision.memory['coordination.run'], oldRun);
    assert.equal(revision.memory['coordination.draft'].reactions, null);
    assert.equal(revision.memory['coordination.draft'].dialogueLimit, 2);
    assert.doesNotMatch(revision.answer, /towel/i, 'Do not reveal the replacement solution');
    const second = await turn('accept', /wetter|dries|dry/i); await capturedResponse('replacement question association');
    assert.notEqual(second.memory['coordination.run'], oldRun); assert.equal(count(second.memory), 1);
    assert.doesNotMatch(second.answer, /piano|keyboard|towel/i); assert.notEqual(second.answer, question.answer);
    const stopped = await turn('stop', /stop|end|cancel|reflection/i); assert.equal(stopped.memory['coordination.outcome'], 'CANCELLED');
  } else {
    if (draft.questions.length) {
      const clarification = await turn('chooseCount', h.acceptanceQuestion); draft = clarification.memory['coordination.draft'];
      assert.equal(count(clarification.memory), 0); assert.equal(clarification.memory['coordination.agreement'], undefined);
    }
    assert.deepEqual(draft.questions, []); assert.equal(draft.reactions.maxActions, 1);
    const rule = draft.reactions.rules.find(r => r.field === 'humanCount'); assert.ok(rule);
    assert.equal(rule.operator, 'gt'); assert.equal(rule.value, 1); assert.equal(rule.effect, 'ACT');
    assert.ok(!draft.reactions.rules.some(r => r.value === 'arrival'));
    assert.match(report.turns.at(-1).answer, visibleCountAlternative,
      'The spoken proposal must disclose the supported visible-count condition');
    const armed = await arm(); assert.deepEqual(armed['coordination.agreement'], draft); await cueWindow();
    await observe(rule.eventType, {humanCount: 1, ts: new Date().toISOString()}); assert.equal(count(await storage()), 0);
    const index = await page.evaluate(() => logs.length);
    await observe(rule.eventType, {humanCount: 2, ts: new Date().toISOString()}); await delivered(index, /welcome|hello|hi|glad/i, 1);
    const restarted = await arm('restart'); assert.notEqual(restarted['coordination.run'], armed['coordination.run']);
    const paused = await turn('pause', /pause/i); assert.equal(paused.memory['task.phase'], 'PAUSED');
    const resumed = await turn('resume', /resum|active|wait|running/i); assert.equal(resumed.memory['task.phase'], 'RUNNING');
    assert.equal(resumed.memory['coordination.run'], restarted['coordination.run']); assert.equal(count(resumed.memory), 0);
    const stopped = await turn('stop', /stop|end|cancel|reflection/i); assert.equal(stopped.memory['coordination.outcome'], 'CANCELLED');
    assert.equal(count(stopped.memory), 0);
  }
  report.rubric.automatedAuthorityAndDeliveryChecks = 'PASS';
}
