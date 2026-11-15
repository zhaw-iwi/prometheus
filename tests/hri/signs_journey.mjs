import {clarify} from './clarify.mjs';
import {completeAssessment} from './complete.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
if(!process.argv.includes('--allow-paid-provider'))throw Error('Explicit paid selection required');
const output=process.argv[process.argv.indexOf('--allow-paid-provider')+1],base=process.env.PROMETHEUS_BASE_URL;
if(!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base))throw Error('Owned loopback app required');
const report={scenario:'study-sign-answers',semanticReview:'PENDING',passed:false,turns:[]};
let headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},cursor=0;
const displayed=new Set();
async function api(path,body){const r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});if(!r.ok)throw Error(`${path}: ${r.status}`);return r.status===204?null:r.json();}
async function record(input,response){assert.equal(response.status,'COMMITTED');const state=await api('/api/study/conversation'),activity=await api('/api/study/activity');cursor=state.turnVersion;
  report.turns.push({input,response,state,activity});if(report.turns.length>24)throw Error('Study request cap');
  for(const event of await api('/api/study/events'))if(event.type==='resp.behaviour_plan'&&!displayed.has(event.id)){await api('/api/study/display',{eventId:event.id,clientTime:new Date().toISOString()});displayed.add(event.id);}return state;}
async function say(text){const request={requestId:randomUUID(),expectedTurn:cursor,text};const response=await api('/api/study/turns',request);assert.deepEqual(await api('/api/study/turns',request),response);return record(text,response);}
async function sign(value){const scope=JSON.parse((await api('/api/study/activity')).signScope);delete scope.remaining;
  const request={...scope,requestId:randomUUID(),expectedTurn:cursor,observationId:randomUUID(),sign:value,observedAt:new Date().toISOString()};
  const response=await api('/api/study/sign',request);assert.deepEqual(await api('/api/study/sign',request),response);return record({simulatedSign:value,scope},response);}
async function demonstrate(){for(const value of ['THUMBS_UP','THUMBS_DOWN','OPEN_PALM'])await sign(value);}
async function predict(){const activity=await api('/api/study/activity');assert.equal(activity.predictionRequired,true);await api('/api/study/predictions',{revision:activity.revision,prediction:'PRIVATE_SIGN_PREDICTION: ask the saved pending question, and use the current agreed mapping.'});}
try{
  const invitation=await api('/admin/study/attempts',{mode:'SYNTHETIC',scenario:'signs'});headers={'X-Prometheus-Study-Token':invitation.token};
  const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v2.json','utf8'));await api('/api/study/consent',{version:'synthetic-test-v1'});
  await api('/api/study/forms/pre',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'Synthetic fixture'])));
  await api('/api/study/forms/intention',{intention:'PRIVATE_SIGN_INTENTION: fictional preferences'});await api('/api/study/forms/private',{private:'PRIVATE_SIGN_PREFERENCE: prefer explicit waiting.'});
  await clarify(say,'signs');
  let state=await say('Propose, but do not start, a fictional preference check-in. You ask exactly these questions in order: "Would you like a short activity?", "Would you prefer a quiet activity?", "Would you like a break afterwards?" I answer using simulated buttons: thumbs up means yes, thumbs down means no, open palm means wait without answering. Only my explicit text Resume continues the same pending question. I want to demonstrate all three signs before acceptance. One fresh sign answers only its current pending question and must not carry over. End after recording all agreed answers; Stop ends earlier. Success means recording the answers, not any health outcome. No camera.');
  assert.equal(state.phase,'PREPARATION');assert.equal(state.draft.activity.kind,'SIGN_ANSWERS');const initial=state.draft;
  await demonstrate();await predict();state=await say('Those conditions sound right. Let us start.');assert.equal(state.phase,'EXECUTION');assert.equal(state.actions,1);
  await sign('THUMBS_UP');const pending=JSON.parse((await api('/api/study/activity')).signs).pending;
  state=await sign('OPEN_PALM');assert.equal(state.phase,'PAUSED');state=await say('Resume');assert.equal(state.actions,2);
  state=await say('Please swap the yes and no mappings: thumbs down now means yes and thumbs up means no. Keep the questions, roles, success, demonstrations, wait, resumption and stopping rules unchanged.');
  assert.equal(state.phase,'PREPARATION');assert.equal(state.draft.activity.yesSign,'THUMBS_DOWN');assert.equal(state.draft.activity.noSign,'THUMBS_UP');
  const before=structuredClone(initial),after=structuredClone(state.draft);for(const v of [before,after])for(const key of ['yesSign','noSign'])delete v.activity[key];assert.deepEqual(after,before);
  await demonstrate();await predict();state=await say('I accept this revised proposal. Start it now.');assert.equal(state.phase,'EXECUTION');assert.equal(state.actions,2);
  assert.deepEqual(JSON.parse((await api('/api/study/activity')).signs).pending,pending);await sign('THUMBS_DOWN');state=await sign('THUMBS_UP');assert.equal(state.phase,'REFLECTION');
  const final=JSON.parse((await api('/api/study/activity')).signs);assert.equal(final.outcome,'ANSWERS_RECORDED');assert.deepEqual(final.answers.map(a=>a.answer),['YES','YES','NO']);
  await api('/api/study/finish',{reason:'ACTIVITY_ENDED'});await completeAssessment(api);headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  report.export=await api(`/admin/study/attempts/${invitation.session.id}/export`);assert.equal(report.export.manifest.recordingComplete,true);assert.equal(report.export.manifest.procedureComplete,true);assert.equal(report.export.agreements.length,2);
  assert.ok(!JSON.stringify(report.export).includes(invitation.token));report.passed=true;
}catch(error){report.error=error.message;process.exitCode=1;}
finally{await fs.writeFile(output,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:report.passed,turns:report.turns.length,error:report.error}));}
