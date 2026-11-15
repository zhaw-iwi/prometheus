import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

const [permission, output] = process.argv.slice(2);
if (permission !== '--allow-paid-provider' || !output) throw Error('Explicit bounded provider run required');
const base = process.env.PROMETHEUS_BASE_URL;
if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base)) throw Error('Owned local application required');
const report = {scenario:'study-durable-ingress', semanticReview:'PENDING', turns:[], passed:false};
let headers = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN}, turnVersion=0;
async function api(path, method='GET', body) {
  const response = await fetch(base+path,{method,headers:{...headers,'Content-Type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(45000)});
  if (!response.ok) throw Error(`${method} ${path}: ${response.status}`);
  return response.status===204?null:response.json();
}
async function turn(text) {
  if (report.turns.length >= 6) throw Error('Study provider turn cap');
  const request={requestId:randomUUID(),expectedTurn:turnVersion,text};
  const response=await api('/api/study/turns','POST',request);
  assert.equal(response.status,'COMMITTED');
  assert.ok(response.inputEventId); assert.ok(response.responseEventId);
  assert.deepEqual(await api('/api/study/turns','POST',request),response);
  const conversation=await api('/api/study/conversation');
  turnVersion=conversation.turnVersion;
  report.turns.push({input:text,response,conversation}); return conversation;
}
try {
  const invitation=await api('/admin/study/attempts','POST',{mode:'SYNTHETIC',scenario:'guess'});
  headers={'X-Prometheus-Study-Token':invitation.token};
  await api('/api/study/consent','POST',{version:'synthetic-test-v1'});
  const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v1.json','utf8'));
  await api('/api/study/forms/pre','POST',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'Synthetic fixture'])));
  await api('/api/study/forms/intention','POST',{intention:'Arrange a short welcoming activity'});
  await api('/api/study/forms/private','POST',{private:'Withheld fixture preference'});
  let state=await turn('Propose, but do not start, this text activity: write one short welcome sentence. I judge its wording. Success means I accept the sentence. Limit two assistant contributions. No sensors or preparation.');
  assert.ok(state.draft); assert.equal(state.agreement,null); assert.equal(state.draft.reactions,null);
  state=await turn('I accept the saved proposal. Start now.');
  assert.ok(state.agreement); assert.equal(state.actions,1);
  state=await turn('Pause the task.'); assert.equal(state.phase,'PAUSED');
  assert.equal(state.turns.length,3); assert.equal(state.turnVersion,3);
  for (const event of await api('/api/study/events')) if (event.type==='resp.behaviour_plan') {
    const receipt={eventId:event.id,clientTime:new Date().toISOString()};
    await api('/api/study/display','POST',receipt); await api('/api/study/display','POST',receipt);
  }
  headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const exported=await api(`/admin/study/attempts/${invitation.session.id}/export`);
  assert.equal(exported.manifest.recordingComplete,true); assert.equal(exported.manifest.displayComplete,true);
  assert.equal(exported.agreements.length,1); assert.equal(exported.requests.length,3);
  assert.ok(!JSON.stringify(exported).includes(invitation.token));
  report.export=exported;
  report.passed=true;
} catch(error) { report.error=error.message; process.exitCode=1; }
finally {
  await fs.writeFile(output,JSON.stringify(report,null,2));
  console.log(JSON.stringify({passed:report.passed,turns:report.turns.length,error:report.error}));
}
