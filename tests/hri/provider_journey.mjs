import fs from 'node:fs/promises';
import assert from 'node:assert/strict';

const [permission, output] = process.argv.slice(2);
if (permission !== '--allow-paid-provider' || !output) throw Error('Explicit bounded provider run required');
const base = process.env.PROMETHEUS_BASE_URL;
if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base)) throw Error('Owned local application required');
const report = {scenario:'study-foundation', semanticReview:'PENDING', turns:[], passed:false};
let headers = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN}, agentPath;
async function api(path, method='GET', body) {
  const response = await fetch(base+path,{method,headers:{...headers,'Content-Type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(45000)});
  if (!response.ok) throw Error(`${method} ${path}: ${response.status}`);
  return response.status===204?null:response.json();
}
async function turn(text) {
  if (report.turns.length >= 6) throw Error('Study provider turn cap');
  const response = await api(agentPath+'/acknowledge','POST',{type:'obs.user_utterance',actor:'user',kind:'observation',payload:text});
  const memory=Object.fromEntries((await api(agentPath+'/storage')).map(e=>[e.key,JSON.parse(e.value)]));
  report.turns.push({input:text,response,memory}); return memory;
}
try {
  const code=('S'+Math.random().toString(36).slice(2,6)).toUpperCase();
  const access=await api('/admin/access-codes','POST',{code,enabled:true});
  await api(`/admin/access-codes/${access.id}/agent-types`,'PUT',{agentTypeKeys:['study.coordination']});
  headers={'X-Prometheus-Access-Code':code};
  const agent=await api('/demo/agents','POST',{agentDefinitionKey:'study.coordination'});
  agentPath='/demo/agents/'+agent.id;
  assert.equal(agent.interactionProfile?.externalRealtimeSpeech ?? false,false);
  let memory=await turn('Propose, but do not start, this text activity: write one short welcome sentence. I judge its wording. Success means I accept the sentence. Limit two assistant contributions. No sensors or preparation.');
  assert.ok(memory['coordination.draft']); assert.equal(memory['coordination.agreement'],undefined);
  assert.equal(memory['coordination.draft'].reactions,null);
  memory=await turn('I accept the saved proposal. Start now.');
  assert.ok(memory['coordination.agreement']); assert.equal(memory['task.actions'],1);
  memory=await turn('Pause the task.'); assert.equal(memory['task.phase'],'PAUSED');
  report.passed=true;
} catch(error) { report.error=error.message; process.exitCode=1; }
finally {
  await fs.writeFile(output,JSON.stringify(report,null,2));
  if (agentPath) await api(agentPath,'DELETE');
  console.log(JSON.stringify({passed:report.passed,turns:report.turns.length,error:report.error}));
}
