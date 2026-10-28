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
  assert.equal(scenario, 'baseline', 'Scenario is not implemented yet');
  const result = await say('Who are you, and what can we do together?');
  assert.match(JSON.stringify(result.response), /Valerian/i);
  assert.equal(result.memory['coordination.agreement'], undefined);
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
