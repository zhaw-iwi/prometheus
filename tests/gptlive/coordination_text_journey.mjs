// Real text inference through the scoped application and disposable MySQL only.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const [permission, output] = process.argv.slice(2);
const scenario = process.argv.find(v => v.startsWith('--scenario='))?.split('=')[1] || 'baseline';
const base = process.env.PROMETHEUS_BASE_URL;
if (permission !== '--allow-paid-provider' || !output || !base ||
    !['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname))
  throw Error('Use the isolated acceptance runner for paid Coordination checks');
const report = {scenario, startedAt: new Date().toISOString(), passed: false, turns: []};
let agentPath, scoped, turns = 0;
const started = Date.now();
async function api(route, method = 'GET', body, headers = scoped) {
  if (Date.now() - started > 300000) throw Error('Coordination journey time budget exceeded');
  const response = await fetch(base + route, {method, headers: {...headers, 'Content-Type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(45000)});
  if (!response.ok) throw Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
async function storage() {
  return Object.fromEntries((await api(agentPath + '/storage')).map(e => [e.key, JSON.parse(e.value)]));
}
async function say(text) {
  if (++turns > 12) throw Error('Coordination journey request budget exceeded');
  const response = await api(agentPath + '/acknowledge', 'POST', {type: 'obs.user_utterance', actor: 'user', kind: 'observation', payload: text});
  const state = await api(agentPath + '/state'), memory = await storage();
  report.turns.push({input: text, response, state, memory});
  return {response, state, memory};
}
try {
  const admin = {'X-Prometheus-Admin-Token': process.env.PROMETHEUS_ADMIN_TOKEN};
  const code = ('C' + Math.random().toString(36).slice(2, 6)).toUpperCase();
  const access = await api('/admin/access-codes', 'POST', {code, enabled: true}, admin);
  await api(`/admin/access-codes/${access.id}/agent-types`, 'PUT', {agentTypeKeys: ['core.coordination']}, admin);
  scoped = {'X-Prometheus-Access-Code': code};
  const agent = await api('/demo/agents', 'POST', {agentDefinitionKey: 'core.coordination'});
  agentPath = '/demo/agents/' + (agent.id || agent.ID);
  const result = await say('Who are you, and what can we do together?');
  assert.match(JSON.stringify(result.response), /Valerian/i);
  assert.equal(result.memory['coordination.agreement'], undefined);
  if (scenario === 'lifecycle') {
    const proposal = await say('Propose but do not start a dialogue task: you give me one short welcome sentence for lab visitors, I judge it. Success means I accept it. Limit one contribution; no sensors or preparation checks.');
    assert.ok(proposal.memory['coordination.draft']);
    const started = await say('I accept that proposal. Start the task now.');
    assert.equal(started.memory['task.phase'], 'COMPLETED');
    const run = started.memory['coordination.run'];
    const queried = await say('What state are you in now?');
    assert.match(JSON.stringify(queried.response), /REFLECTION/i);
    const ambiguous = await say('Start all over again');
    assert.match(JSON.stringify(ambiguous.response), /restart the same agreed task/i);
    assert.equal(ambiguous.memory['coordination.run'], run);
    const restarted = await say('Explicitly restart the same agreed task with a fresh contribution.');
    assert.notEqual(restarted.memory['coordination.run'], run);
    assert.equal(restarted.memory['task.actions'], 1);
    const returned = await say('Could you take us back to the initial conversation state?');
    assert.equal(returned.memory['task.phase'], 'CONFIGURATION');
    assert.match(JSON.stringify(returned.response), /now in Conversation/i);
    const state = await say('Where are we?');
    assert.match(JSON.stringify(state.response), /Current state: CONVERSATION/i);
    assert.ok(state.memory['coordination.outcomes'].length >= 2);
  } else if (scenario === 'multimodal') {
    const proposal = await say('Propose but do not start: after acceptance, on a fresh scissor sign tell this joke: I told my computer I needed a break, and it froze. Then estimate my current visible facial expression, with uncertainty. I show signs and judge the response. Success is my acceptance. One action this session, defaults, no preparation checks.');
    assert.deepEqual(proposal.memory['coordination.draft'].questions, []);
    assert.equal(proposal.memory['coordination.draft'].reactions.rules[0].value, 'scissor');
    const armed = await say('Start the saved proposal now.'); assert.equal(armed.memory['task.actions'], 0);
    await new Promise(resolve => setTimeout(resolve, 3500));
    for (const [type, value] of [['obs.emotion.face', {emotion:'happy', valence:.8, confidence:.95, facePresent:true}],
      ['obs.hand.sign', {sign:'scissor', confidence:1}]]) {
      const response = await api(agentPath + '/acknowledge', 'POST', {type, actor:'sensor', kind:'observation', payload:JSON.stringify({...value, ts:new Date().toISOString()})});
      if (type === 'obs.hand.sign') report.contribution = response;
    }
    assert.equal((await storage())['task.phase'], 'COMPLETED'); assert.equal((await storage())['task.actions'], 1);
    assert.match(JSON.stringify(report.contribution), /computer.*(?:break|brake).*froze/is);
    assert.match(JSON.stringify(report.contribution), /look|seem|appear|estimate|suggest|might/i);
    assert.match(JSON.stringify(report.contribution), /happy|smil|cheer|positive|amused/i);
    const back = await say('Return to the initial conversation state.');
    assert.equal(back.memory['task.phase'], 'CONFIGURATION'); assert.match(JSON.stringify(back.response), /Conversation/i);
    assert.match(JSON.stringify((await say('Which state are we in?')).response), /CONVERSATION/i);
    const another = await say('Propose a new dialogue task: give one welcome sentence for lab visitors, I judge it. Success is my acceptance. No sensors or preparation checks. Do not start.');
    assert.equal(another.memory['coordination.draft'].reactions, null); assert.equal(another.memory['task.phase'], 'CONFIGURATION');
    assert.ok(another.memory['coordination.outcomes'].length >= 1);
  } else if (scenario === 'revision') {
    const proposal = await say('Propose one short joke on rock: I show the sign and judge the joke; success means I like it. One action in this session, standard recognition settings, no preparation checks. Do not start yet.');
    assert.ok(proposal.memory['coordination.draft']?.reactions);
    const accepted = await say('Start the saved proposal now.');
    const original = accepted.memory['coordination.agreement'];
    assert.equal(original.reactions.firstAction, 'ON_CUE'); assert.equal(original.reactions.maxActions, 1);
    await new Promise(resolve => setTimeout(resolve, 3500));
    await api(agentPath + '/acknowledge', 'POST', {type: 'obs.hand.sign', actor: 'camera', kind: 'observation', payload: JSON.stringify({sign:'rock', confidence:1, ts:new Date().toISOString()})});
    assert.equal((await storage())['task.phase'], 'COMPLETED');
    const edited = await say('Make only the response a dry one-liner, keeping every other term unchanged.');
    const revised = edited.memory['coordination.draft']; assert.ok(revised?.reactions);
    const protectedTerms = agreement => {
      const copy = structuredClone(agreement); delete copy.goal; delete copy.agentRole; delete copy.questions;
      delete copy.reactions.goal; copy.reactions.rules.forEach(rule => delete rule.action); return copy;
    };
    assert.deepEqual(protectedTerms(revised), protectedTerms(original));
    assert.match(revised.reactions.rules[0].action, /dry|one.liner/i);
    const restarted = await say('Start the revised proposal now.');
    assert.equal(restarted.memory['task.actions'], 0); assert.equal(restarted.memory['task.phase'], 'RUNNING');
    const replacement = await say('Instead replace this sensor task with a dialogue task: give one welcome sentence when I accept, with no sensor trigger or preparation; I judge whether it is suitable.');
    assert.equal(replacement.memory['coordination.draft'].reactions, null);
    assert.equal(replacement.memory['task.phase'], 'CONFIGURATION');
    assert.match(JSON.stringify(replacement.response), /replacement proposal/i);
  } else assert.equal(scenario, 'baseline', 'Scenario is not implemented yet');
  report.passed = true;
} catch (error) {report.error = error.message; process.exitCode = 1;}
finally {
  if (agentPath) {
    report.finalStorage = await storage().catch(e => ({error: e.message}));
    report.history = await api(agentPath + '/eventhistory').catch(e => ({error: e.message}));
  }
  fs.mkdirSync(path.dirname(output), {recursive: true});
  fs.writeFileSync(output, JSON.stringify(report, null, 2));
  console.log(report.passed ? 'Coordination text journey passed.' : 'Journey failed: ' + report.error);
}
