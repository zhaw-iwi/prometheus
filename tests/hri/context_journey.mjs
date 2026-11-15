import {completeAssessment} from './complete.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
if(!process.argv.includes('--allow-paid-provider'))throw Error('Explicit paid selection required');
const output=process.argv[process.argv.indexOf('--allow-paid-provider')+1],base=process.env.PROMETHEUS_BASE_URL;
if(!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base))throw Error('Owned loopback app required');
const report={scenario:'study-context-checkin',semanticReview:'PENDING',passed:false,turns:[]};
let headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},cursor=0;
const displayed=new Set();
async function api(path,body){const r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});if(!r.ok)throw Error(`${path}: ${r.status}`);return r.status===204?null:r.json();}
async function record(input,response){assert.equal(response.status,'COMMITTED');const state=await api('/api/study/conversation'),activity=await api('/api/study/activity');cursor=state.turnVersion;
  report.turns.push({input,response,state,activity});if(report.turns.length>20)throw Error('Study request cap');
  for(const event of await api('/api/study/events'))if(event.type==='resp.behaviour_plan'&&!displayed.has(event.id)){await api('/api/study/display',{eventId:event.id,clientTime:new Date().toISOString(),disposition:event.withheld?'WITHHELD':'DISPLAY'});displayed.add(event.id);}return state;}
async function say(text){const request={requestId:randomUUID(),expectedTurn:cursor,text};const response=await api('/api/study/turns',request);assert.deepEqual(await api('/api/study/turns',request),response);return record(text,response);}
async function scene(count){const activity=await api('/api/study/activity');const request={requestId:randomUUID(),expectedScene:activity.sceneVersion,humanCount:count,observedAt:new Date().toISOString()};const response=await api('/api/study/scene',request);assert.deepEqual(await api('/api/study/scene',request),response);return record({simulatedCount:count},response);}
async function predict(){const activity=await api('/api/study/activity');assert.equal(activity.predictionRequired,true);await api('/api/study/predictions',{revision:activity.revision,prediction:'PRIVATE_CONTEXT_PREDICTION: wait for a fresh one-person scene; explicit resumption for postponement.'});}
try{
  const invitation=await api('/admin/study/attempts',{mode:'SYNTHETIC',scenario:'context'});headers={'X-Prometheus-Study-Token':invitation.token};
  const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v2.json','utf8'));await api('/api/study/consent',{version:'synthetic-test-v1'});
  await api('/api/study/forms/pre',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'Synthetic fixture'])));
  await api('/api/study/forms/intention',{intention:'A fictional routine check-in'});await api('/api/study/forms/private',{private:'PRIVATE_CONTEXT_PREFERENCE: prefer neutral wording.'});
  let state=await say('Propose, but do not start, one fictional medication-routine check-in. After acceptance, wait for a fresh simulated scene with exactly one visible person, then ask "In this fictional scenario, was the medication routine completed?" I answer yes or no. For yes, say "Thank you for letting me know." For no, say "You can consider a reminder in this fictional scenario." Two or more visible people postpone the check-in without repeating its private question. Continue only after I explicitly resume with one person visible. Departure ends an ongoing encounter; return alone does not restart it. Unknown scene means wait. Success is delivering the agreed fictional response, not verifying medication use. No real camera or patient identification.');
  assert.equal(state.phase,'READY');assert.equal(state.draft.activity.kind,'CONTEXT_CHECKIN');const initial=state.draft;
  await predict();state=await say('Those conditions sound right. Let us start.');assert.equal(state.phase,'EXECUTION');assert.equal(state.actions,0);
  state=await scene(1);assert.equal(state.actions,1);const pending=JSON.parse((await api('/api/study/activity')).context).pending;
  state=await scene(2);assert.equal(state.phase,'PAUSED');assert.ok(!JSON.parse(report.turns.at(-1).response.responsePayload).speech.includes('medication'));
  state=await scene(1);assert.equal(state.phase,'PAUSED');
  state=await say('Make only the support for a no answer shorter and neutral: "A reminder may help." Keep the question, yes response, roles, success and all scene and stopping conditions unchanged.');
  assert.equal(state.phase,'READY');assert.equal(state.draft.activity.noReply,'A reminder may help.');
  for(const field of ['question','yesReply','startCount','postponeAt','departure','resume'])assert.deepEqual(state.draft.activity[field],initial.activity[field]);
  for(const field of ['humanRole','success','checks','dialogueLimit','reactions'])assert.deepEqual(state.draft[field],initial[field]);
  await predict();state=await say('I accept this revised proposal. Start it now.');assert.equal(state.phase,'PAUSED');
  state=await scene(1);assert.equal(state.phase,'PAUSED');state=await say('Resume');assert.equal(state.actions,1);
  assert.deepEqual(JSON.parse((await api('/api/study/activity')).context).pending,pending);
  state=await say('No');assert.equal(state.phase,'REFLECTION');assert.equal(JSON.parse(report.turns.at(-1).response.responsePayload).speech,'A reminder may help.');
  assert.equal(JSON.parse((await api('/api/study/activity')).context).outcome,'FICTIONAL_NO_REPORTED');
  await scene(0);state=await scene(1);assert.equal(state.phase,'REFLECTION');
  await api('/api/study/finish',{reason:'ACTIVITY_ENDED'});await completeAssessment(api);headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  report.export=await api(`/admin/study/attempts/${invitation.session.id}/export`);assert.equal(report.export.manifest.recordingComplete,true);assert.equal(report.export.manifest.procedureComplete,true);assert.equal(report.export.agreements.length,2);
  assert.ok(!JSON.stringify(report.export).includes(invitation.token));report.passed=true;
}catch(error){report.error=error.message;process.exitCode=1;}
finally{await fs.writeFile(output,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:report.passed,turns:report.turns.length,error:report.error}));}
