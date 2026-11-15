import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
if(!process.argv.includes('--allow-paid-provider'))throw Error('Explicit paid provider selection required');
const output=process.argv[process.argv.indexOf('--allow-paid-provider')+1],base=process.env.PROMETHEUS_BASE_URL;
if(!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base))throw Error('Owned loopback app required');
const report={scenario:'study-guessing',semanticReview:'PENDING',passed:false,turns:[]};
let headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},cursor=0;
async function api(path,body){const r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});if(!r.ok)throw Error(`${path}: ${r.status}`);return r.status===204?null:r.json();}
async function say(text){if(report.turns.length>=12)throw Error('Study turn cap');const input={requestId:randomUUID(),expectedTurn:cursor,text};const response=await api('/api/study/turns',input);assert.equal(response.status,'COMMITTED');assert.deepEqual(await api('/api/study/turns',input),response);const state=await api('/api/study/conversation');cursor=state.turnVersion;const activity=await api('/api/study/activity');report.turns.push({input:text,response,state,activity});return state;}
async function predict(){const activity=await api('/api/study/activity');assert.equal(activity.predictionRequired,true);await api('/api/study/predictions',{revision:activity.revision,prediction:'Private fixture prediction: retain the pending question after revision, otherwise ask one yes/no question.'});}
try{
  const invitation=await api('/admin/study/attempts',{mode:'SYNTHETIC',scenario:'guess'});headers={'X-Prometheus-Study-Token':invitation.token};
  const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v1.json','utf8'));
  await api('/api/study/consent',{version:'synthetic-test-v1'});
  await api('/api/study/forms/pre',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'Synthetic fixture'])));
  await api('/api/study/forms/intention',{intention:'A short guessing game'});await api('/api/study/forms/private',{private:'Hidden object: umbrella. I prefer short questions.'});
  let state=await say('Please propose, but do not start, a Twenty Questions-style game. I keep an everyday object secret and answer yes, no or unknown. You ask questions and make guesses. Use five questions total, with every guess counting as a question. A guess succeeds only when I confirm it. Pause preserves progress; stop ends the game. No sensors.');
  assert.equal(state.phase,'READY');assert.equal(state.draft.activity.kind,'GUESSING');assert.equal(state.draft.activity.questionLimit,5);assert.equal(state.agreement,null);
  await predict();state=await say('Those terms work for me. Let us begin.');assert.equal(state.phase,'EXECUTION');assert.equal(state.actions,1);
  const initial=state.agreement;const pending=JSON.parse((await api('/api/study/activity')).game).pending;
  state=await say('Pause the task.');assert.equal(state.phase,'PAUSED');state=await say('Resume the task.');assert.equal(state.actions,1);
  state=await say('Change the question limit to three in total, keeping our roles, answer choices, guess counting, success and stopping rules unchanged.');
  assert.equal(state.phase,'READY');assert.equal(state.draft.activity.questionLimit,3);
  for(const field of ['humanRole','agentRole','success','checks','reactions'])assert.deepEqual(state.draft[field],initial[field]);
  await predict();state=await say('I agree to this revised proposal. Start it now.');assert.equal(state.actions,1);
  assert.deepEqual(JSON.parse((await api('/api/study/activity')).game).pending,pending);
  // Deliberately withhold success: property answers and rejected guesses are all No.
  for(let i=0;i<3;i++)state=await say('No');
  assert.equal(state.phase,'REFLECTION');assert.equal(state.actions,3);
  assert.equal(JSON.parse((await api('/api/study/activity')).game).outcome,'QUESTION_LIMIT_REACHED');
  for(const event of await api('/api/study/events'))if(event.type==='resp.behaviour_plan')await api('/api/study/display',{eventId:event.id,clientTime:new Date().toISOString()});
  await api('/api/study/finish',{reason:'ACTIVITY_ENDED'});
  headers={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};report.export=await api(`/admin/study/attempts/${invitation.session.id}/export`);
  assert.equal(report.export.manifest.recordingComplete,true);assert.equal(report.export.agreements.length,2);assert.ok(!JSON.stringify(report.export).includes(invitation.token));report.passed=true;
}catch(error){report.error=error.message;process.exitCode=1;}
finally{await fs.writeFile(output,JSON.stringify(report,null,2));console.log(JSON.stringify({passed:report.passed,turns:report.turns.length,error:report.error}));}
