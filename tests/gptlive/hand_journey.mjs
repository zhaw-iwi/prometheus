// Real provider acceptance. Sensor fixtures are controlled; interpretations, speech and receipts are real.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {installOfflineUiAssets} from '../playwright/fixtures/offline-ui-assets.mjs';
import {openValerianSession} from '../playwright/fixtures/valerian-session.mjs';
import {captureHandFrames} from '../playwright/fixtures/hand-frames.mjs';
import {trialCapture} from './trial_capture.mjs';
import {handBudgetCapture} from './hand_budget_capture.mjs';
import {playHandStimulus,handReplyObserved,spokenClarification,spokenPreparation,outboundHandAudio} from './hand_audio_input.mjs';
import {startObservationPumps} from './hand_observation_pump.mjs';
import {armingReady,speechLatency,conversationLatency,compareConversations} from './commanding_evidence.mjs';
import {runHandScenario} from './hand_scenarios.mjs';
import {commandTiming,controlTimings,interruptedCommandSource} from './hand_command_timing.mjs';

const [permission,output]=process.argv.slice(2),mode=process.env.PROMETHEUS_HAND_MODE;
const base=process.env.PROMETHEUS_BASE_URL,ledger=process.env.PROMETHEUS_HAND_BUDGET_LEDGER;
if(permission!=='--allow-paid-provider'||!output||mode!=='L-06-visitor'||!ledger||!base
    ||!['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname))throw Error('Use the isolated authorized hand runner');
const plan=JSON.parse(fs.readFileSync(process.env.PROMETHEUS_HAND_PAID_PLAN)),reservation=JSON.parse(fs.readFileSync(ledger));
assert.equal(reservation.authorization,'hand-interaction-2026-11-23-usd50');
assert.ok(reservation.attempts.length===114&&reservation.reservedUsd===316
  &&reservation.attempts.slice(88).reduce((sum,a)=>sum+a.reservedUsd,0)===130);
const directory=path.dirname(path.resolve(output));
const selected=reservation.attempts.at(-1);
assert.ok(selected.mode===mode&&selected.reservedUsd===5&&selected.allocationRevision===plan.allocationRevision
  &&selected.extensionAuthorization===plan.extensionAuthorization&&path.resolve(selected.artifact)===directory);
fs.writeFileSync(path.join(directory,'provider-attempt.started'),new Date().toISOString(),{flag:'wx'});
const clips=JSON.parse(fs.readFileSync(new URL('./hand-cases.json',import.meta.url))),stimulus=JSON.parse(fs.readFileSync('target/hand-interaction-audio/manifest.json'));
assert.equal(stimulus.providerCalls,0);assert.equal(stimulus.sampleRate,24000);
assert.equal(plan.acceptanceRevision,2,'Use the owner-approved mode boundary');
const report={mode,plan,acceptanceRevision:plan.acceptanceRevision,startedAt:new Date().toISOString(),automatedPassed:false,semanticReview:'PENDING',
  physicalAudibility:'NOT_RUN',sensorEvidence:'controlled scoped observations; not real-camera accuracy',
  turns:[],observations:[],observationTimings:[],observationTimingsDropped:0,checkpoints:[],screenshots:[],renderChecks:[],network:[],browserErrors:[],stimulus};
const started=Date.now(),wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let browser,page,agentPath,scoped,handle,closing=false,watchdog,pump,mediaSaved=false;
const check=()=>{
  if(closing)return;
  if(pump?.error)throw pump.error;
  if(Date.now()-started>390000)throw Error('Journey deadline');
};
async function api(route,method='GET',body,headers=scoped){check();const response=await fetch(base+route,{method,
  headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  if(!response.ok)throw Error(`${method} ${route}: HTTP ${response.status}`);return response.status===204?null:response.json();}
const memory=async()=>Object.fromEntries((await api(agentPath+'/storage')).map(e=>[e.key,JSON.parse(e.value)]));
const history=async()=>api(agentPath+'/live/history');
const contextTraces=new Map(),providerEvents=new Map(),silentInputReceipts=new Map();
function retainDiagnostics(status){
  for(const e of status?.context?.recent||[])if(e.phase.startsWith('command_'))contextTraces.set(e.sequence,e);
  for(const e of status?.recent||[])if(e.eventId&&e.type==='session.output_transcript.delta')providerEvents.set(e.eventId,e);
}
async function until(label,read,ms=45000){const deadline=Date.now()+ms;while(Date.now()<deadline){check();
  if(!closing&&handle){assert.equal(await page.getByTestId('gptlive-status').textContent(),'Active','Native transport stopped');
    assert.equal(await page.evaluate(()=>__handBudget.exhausted),null,'Native budget exceeded');}
  const result=await read();if(result)return result;await wait(250);}throw Error('Timed out: '+label);}
async function quiet(){await page.waitForFunction(()=>performance.now()-(__trial.lastVoice||0)>1200
  &&performance.now()-(__handBudget.lastNativeVoice||0)>1200
  &&performance.now()-(__trial.lastOutputCaption||0)>3500,null,{timeout:35000});}
async function say(name,{reply=true,directCommand=false}={}){
  assert.ok(report.turns.length<12,'Visitor-turn cap');await quiet();const before=await history(),prior=await memory();
  const turn={name,directCommand,intendedInput:clips[name],beforeIds:before.map(e=>e.id),beforeAgreement:prior['coordination.agreement'],
    beforeActions:prior['task.actions'],beforeCommandCount:prior['command.handledTurns']?.length||0,index:await page.evaluate(()=>__trial.events.length)};
  report.turns.push(turn);const clip=stimulus.clips[name],bytes=fs.readFileSync(path.join('target/hand-interaction-audio',clip.file));
  turn.clock=await page.evaluate(async()=>{
    const sent=performance.now(), response=await fetch('/runtime/clock',{cache:'no-store'});
    const {serverTimeMs}=await response.json();return {sent,received:performance.now(),serverTimeMs};
  });
  assert.equal(clip.text,clips[name]);assert.equal(createHash('sha256').update(bytes).digest('hex'),clip.sha256);console.log('Visitor: '+name);
  if(name.startsWith('accept'))turn.outboundBefore=await page.evaluate(outboundHandAudio);
  turn.input=await page.evaluate(playHandStimulus,{pcmBase64:bytes.toString('base64'),name});
  if(name.startsWith('accept'))turn.outboundAfter=await page.evaluate(outboundHandAudio);
  await until('durable user '+name,async()=>(await history()).some(e=>!turn.beforeIds.includes(e.id)&&e.type==='obs.user_utterance'));
  if(reply){await page.waitForFunction(handReplyObserved,{index:turn.index,startMs:turn.input.startMs},{timeout:35000});await quiet();}
  await updateTurn(turn);if(reply)assert.ok(Number.isFinite(turn.latency.speechEndToOutputMs),'Native audio missing');console.log('Reply: '+turn.spoken);return turn;
}
async function updateTurn(turn){turn.memory=await memory();turn.history=(await history()).filter(e=>!turn.beforeIds.includes(e.id));
  turn.events=await page.evaluate(i=>__trial.events.slice(i),turn.index);turn.spoken=turn.events.filter(e=>e.type==='session.output_transcript.delta').map(e=>e.delta).join('');turn.latency=speechLatency(turn.events,turn.input);}
async function checkpoint(name){const value={name,memory:await memory(),history:await history()};report.checkpoints.push(value);return value.memory;}
async function render(name,kind,eventId){
  assert.ok(eventId,'Expected a published hand event');
  const handle=await page.waitForFunction(id=>window.__handFrames.get(id),eventId);
  const {image,...frame}=await handle.jsonValue();await handle.dispose();
  assert.equal(frame.pose.sign,kind);assert.equal(frame.glError,0);assert.ok(frame.irisPixels>100);
  report.renderChecks.push({name,eventId,...frame});const file=name+'.png';
  fs.writeFileSync(path.join(directory,file),Buffer.from(image.split(',')[1],'base64'));report.screenshots.push(file);
}
const plans=events=>events.filter(e=>e.type==='resp.behaviour_plan').map(e=>({id:e.id,...JSON.parse(e.payload)}));
async function command(name,status,{kind,verify}={}){
  const turn=await say(name,{reply:status!=='ACCEPTED',directCommand:true});
  const receipt=await until('command '+name,async()=>{const m=await memory();
    return m['command.handledTurns']?.length===turn.beforeCommandCount+1 ? m['command.lastResult'] : null;});
  const source=(await history())[receipt.sourcePosition];assert.equal(source.type,'obs.user_utterance');assert.ok(!turn.beforeIds.includes(source.id));
  turn.commandSource={id:source.id,position:receipt.sourcePosition};
  // A visual command permits, but does not require, a spoken acknowledgement.
  // Leave time for its confirmed result to reach Live before the next visitor turn.
  if(status==='ACCEPTED')await wait(8000);await quiet();await updateTurn(turn);
  assert.equal(receipt.status,status);assert.equal(receipt.silent,false,'Live stimulus does not request strict silence');
  assert.deepEqual(turn.memory['coordination.agreement'],turn.beforeAgreement,'Direct command mutated agreement');assert.equal(turn.memory['task.actions'],turn.beforeActions,'Direct command spent task budget');
  const all=await history();const produced=plans(all.slice(receipt.sourcePosition+1)).filter(p=>p.motion?.handAction||p.motion?.handSign);
  if(status==='ACCEPTED'){assert.equal(produced.length,1,'One canonical hand output');assert.ok(!produced[0].speech,'Competing Speech');verify?.(produced[0].motion);turn.action=produced[0];if(kind)await render(name,kind,turn.action.id);}
  else assert.equal(produced.length,0,'Unaccepted command produced output');return turn;
}
async function observe(type,payload,{retain=true}={}){const startedAt=Date.now();let error;
  try{const result=await api(agentPath+'/acknowledge?profile=full_plan','POST',{type,actor:'controlled-camera',kind:'observation',payload:JSON.stringify(payload)});
    if(retain)report.observations.push({type,payload,responseEvent:result.responseEvent?.id});return payload;
  }catch(cause){
    error=cause.message;
    throw new Error(`${type} ${payload.observationId} captured ${payload.ts}, failed after ${Date.now()-startedAt} ms: ${error}`,{cause});
  }finally{if(report.observationTimings.length<2048)report.observationTimings.push({type,id:payload.observationId,capturedAt:payload.ts,startedAt,finishedAt:Date.now(),error});else report.observationTimingsDropped++;}}
async function propose(name,verify,{clarification,requireClarification=false}={}){let proposalTurn=await say(name);let m=await until('saved draft',async()=>{const m=await memory();return m['coordination.draft']?m:null;});
  assert.ok(!m['coordination.agreement']);assert.equal(m['task.actions']||0,0);
  if(requireClarification)assert.ok(clarification&&m['coordination.draft'].questions.length,'The unspecified response needs clarification');
  if(clarification&&m['coordination.draft'].questions.length){
    // A saved partial draft can precede its spoken question. Answer only after
    // that question has finished, as a person would in this happy path.
    const question=m['core.task.response'];assert.ok(question?.proposalReview);
    await until('completed clarification question',async()=>{const response=(await memory())['core.task.response'];return response?.id===question.id&&response.consumed;});
    const heard=await until('audible invitation to clarify the missing choice',async()=>spokenClarification(await history(),proposalTurn.beforeIds));
    await quiet();await checkpoint('clarification-question-completed');
    const previous=JSON.stringify(m['coordination.draft']);report.clarifications??=[];report.clarifications.push({questions:m['coordination.draft'].questions,heard,input:clarification});
    proposalTurn=await say(clarification);m=await until('clarified draft',async()=>{const next=await memory();return next['coordination.draft']&&JSON.stringify(next['coordination.draft'])!==previous?next:null;});
  }
  const draft=m['coordination.draft'];assert.deepEqual(draft.questions,[]);assert.ok(draft.checks.length>0,'Recognition preparation required');assert.equal(draft.reactions.maxActions,1);assert.equal(draft.reactions.firstAction,'ON_CUE');
  assert.ok(!m['coordination.agreement']);assert.ok(!m['task.actions']);verify(draft);report.proposal=structuredClone(draft);
  // The immediate native reply can precede the backend's preparation request.
  // Let that request finish before supplying the demonstration and its review.
  const request=m['core.task.response'];
  report.preparationPrompt=await until('completed preparation request',async()=>{
    const state=await memory(),response=state['core.task.response'];
    assert.deepEqual(state['coordination.draft'],draft,'Preparation terms changed');
    assert.ok(!state['coordination.agreement']&&!state['task.actions'],'Preparation must stay inactive');
    if(response?.supportOnly&&response.consumed&&(!request||response.id===request.id))return {id:response.id};
    if(response&&(!response.supportOnly||(request&&response.id!==request.id)))return null;
    // Native speech can precede consumption or survive withdrawal of the same preparation request.
    const heard=spokenPreparation(await history(),proposalTurn.beforeIds);
    return heard?{heard}:null;
  });
  await quiet();await checkpoint('preparation-request-completed');return draft;}
async function ready(){await until('all recognition checks and completed native review',async()=>{const m=await memory(),review=m['core.task.response'];return Object.keys(m['coordination.evidence']||{}).length===m['coordination.draft'].checks.length&&review?.proposalReview&&review.consumed;});await quiet();await checkpoint('prepared');}
async function waitForConsentRepair(){
  const m=await until('one completed consent repair',async()=>{
    const current=await memory(),repair=current['core.task.response'];return repair?.consentRepair&&repair.consumed?current:null;
  });
  assert.ok(!m['coordination.agreement']);assert.equal(m['task.actions']||0,0);
  assert.deepEqual(m['coordination.draft'],report.proposal);await checkpoint('incomplete consent repaired');
}
async function accept(){
  if(process.env.PROMETHEUS_HAND_CONSENT_REPAIR==='true'){
    assert.equal(mode,'L-03');report.deliberateConsentFragment=true;
    await say('acceptfragment');await waitForConsentRepair();
  }
  const turn=await say('accept');
  const admitted=turn.history.filter(e=>e.type==='obs.user_utterance');
  if(admitted.length===1 && /^(?:i accept|accept) the(?: current| saved)?[.!]?$/i.test(admitted[0].payload.trim())){
    await waitForConsentRepair();
    await say('acceptrepair');
  }
  const m=await until('accepted and confirmed arming',async()=>{const m=await memory();return m['coordination.agreement']&&armingReady(m,Date.now())?m:null;});
  assert.equal(m['task.actions'],0);assert.deepEqual(m['coordination.agreement'],report.proposal);report.accepted=m;}
async function complete(){await until('one complete contribution',async()=>{const m=await memory();if(['UNCERTAIN','PAUSED'].includes(m['task.phase']))throw Error('Task stopped with '+m['task.phase']);return m['task.phase']==='COMPLETED'&&m['task.actions']===1;},55000);await wait(8000);await quiet();return checkpoint('completed');}
async function zero(){assert.equal((await memory())['task.actions']||0,0,'Nonmatching evidence spent an action');}
async function runPump(callback){assert.ok(!pump);pump=startObservationPumps(Array.isArray(callback)?callback:[callback]);}
async function stopPump(){if(pump){pump.running=false;await pump.done;const error=pump.error;pump=null;if(error)throw error;}}
async function captureMedia(){
  if(mediaSaved)return;report.browser=await page.evaluate(()=>__trial.events);
  const captured=await page.evaluate(()=>__handBudget.finish());report.nativeBudget={...captured,files:undefined};
  report.audioFiles=captured.files.map((data,i)=>{const name=`native-output-${i}.webm`;fs.writeFileSync(path.join(directory,name),Buffer.from(data.split(',')[1],'base64'));return name;});mediaSaved=true;
}
async function sequenceFrames(name,expected,{stopAt=null,previousEvent=null}={}){
  const captured=new Set();let began=false;await until('sequence '+name,async()=>{
    if(!began){const execution=(await memory())['hand.execution.v1'];if(!execution||execution.id===previousEvent||execution.status!=='RUNNING')return false;began=true;}
    const snapshot=await page.evaluate(()=>ValerianEmbodiment.snapshot());
    if(snapshot.phase==='holding'&&snapshot.step>=1&&!captured.has(snapshot.step)){
      captured.add(snapshot.step);const file=name+'-'+snapshot.step+'.png';fs.writeFileSync(path.join(directory,file),Buffer.from(snapshot.image.split(',')[1],'base64'));
      report.screenshots.push(file);report.renderChecks.push({name,step:snapshot.step,pose:snapshot.pose});
      if(stopAt!==null&&snapshot.step===stopAt){await page.getByTestId('stop-hand-sequence').click();return true;}
    }
    if(snapshot.status==='completed')return true;
    if(['interrupted','unknown'].includes(snapshot.status))throw Error('Sequence playback '+snapshot.status);
    return false;
  },60000);
  assert.deepEqual([...captured],Array.from({length:stopAt===null?expected:stopAt},(_,i)=>i+1),'Missing or reordered visible steps');
  return checkpoint(name);
}
try{
  browser=await chromium.launch({headless:true,args:['--autoplay-policy=no-user-gesture-required']});
  watchdog=setTimeout(()=>{report.error='Overall watchdog expired';fs.writeFileSync(output,JSON.stringify(report,null,2));void browser.close();},390000);
  const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'no-preference'});await installOfflineUiAssets(context);
  // These journeys inject labelled sensor captures and never start camera models.
  // Avoid downloading unused third-party detector bundles during paid voice time.
  for(const url of ['https://cdn.jsdelivr.net/npm/@tensorflow*/**','https://cdn.jsdelivr.net/npm/face-api.js*/**'])
    await context.route(url,route=>route.fulfill({body:'',contentType:'application/javascript'}));
  await context.addInitScript({content:`(${trialCapture.toString()})();(${handBudgetCapture.toString()})(${JSON.stringify(plan.limits)});`});page=await context.newPage();page.setDefaultTimeout(30000);
  page.on('pageerror',error=>report.browserErrors.push(error.message));page.on('response',async response=>{if(!response.url().startsWith(base))return;const url=new URL(response.url());
    if(url.pathname.endsWith('/updates') && response.ok())await response.json().then(data=>{
      retainDiagnostics(data.status);
      for(const outcome of data.transcripts||[])if(outcome.speaker==='USER' && outcome.reason==='silent_input_audio')
        silentInputReceipts.set(outcome.segmentId,outcome);
    }).catch(()=>{});
    if(/acknowledge|speech-synthesis|speech-approvals|behaviours\/.*\/speech|playback-cues|hand-execution/.test(url.pathname))report.network.push({path:url.pathname,status:response.status(),method:response.request().method()});
    if(url.pathname.endsWith('/live/sessions')&&response.status()===201)handle=(await response.json()).handle;});
  const code=Math.random().toString(36).slice(2,7).toUpperCase(),definition=mode==='L-09-comparison'?'core.live_multimodal':'core.coordination';
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},access=await api('/admin/access-codes','POST',{code,enabled:true},admin);
  await api(`/admin/access-codes/${access.id}/agent-types`,'PUT',{agentTypeKeys:[definition]},admin);scoped={'X-Prometheus-Access-Code':code};
  const agent=await api('/demo/agents','POST',{agentDefinitionKey:definition});agentPath='/demo/agents/'+agent.id;report.agentId=agent.id;
  await openValerianSession(page,base+`/valerian/?agentId=${agent.id}`,code);
  await page.evaluate(captureHandFrames);
  await page.getByRole('button',{name:'Robot',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('[data-testid="embodiment-stage"]').dataset.ready==='true');
  await page.getByTestId('gptlive-tab').click();await page.getByTestId('gptlive-settings').locator(':scope > summary').click();
  const startLive=async()=>{
    await page.locator('#gptlive_voice').selectOption('willow');await page.getByTestId('gptlive-start').click();
    await page.waitForFunction(()=>['Active','Error'].includes(document.getElementById('gptlive_status').textContent));
    assert.equal(await page.getByTestId('gptlive-status').textContent(),'Active');
  };
  if(mode!=='L-06-visitor')await startLive();
  await runHandScenario(mode,{startLive,api:(suffix,method,body)=>api(agentPath+suffix,method,body),page,report,plan,directory,say,command,observe,memory,history,until,quiet,wait,checkpoint,render,propose,ready,accept,complete,zero,runPump,stopPump,sequenceFrames,captureMedia,conversationLatency,compareConversations});
  await stopPump();assert.deepEqual(report.browserErrors,[]);assert.ok(report.network.every(e=>!/speech-synthesis|speech-approvals|behaviours\/.*\/speech/.test(e.path)),'Competing output Speech route');report.automatedPassed=true;
}catch(error){report.error=error.message;report.errorStack=error.stack;process.exitCode=1;}
finally{
  closing=true;clearTimeout(watchdog);await stopPump().catch(e=>{report.pumpError=e.message;report.automatedPassed=false;process.exitCode=1;});
  if(page){await page.getByTestId('gptlive-stop').click({timeout:3000}).catch(()=>{});await wait(500);await captureMedia().catch(e=>{report.mediaError=e.message;report.browser||=[];report.automatedPassed=false;process.exitCode=1;});
    for(let i=0;i<report.turns.length;i++){const turn=report.turns[i];turn.events=report.browser.slice(turn.index,report.turns[i+1]?.index);turn.spoken=turn.events.filter(e=>e.type==='session.output_transcript.delta').map(e=>e.delta).join('');}
    report.timings=await page.evaluate(async()=>(await import('/performance/timings.js')).turnTimings.snapshot()).catch(()=>null);
    report.diagnostics=await page.evaluate(async()=>(await import('/live/diagnostics.js')).liveDiagnostics.snapshot()).catch(()=>null);
    await page.screenshot({path:path.join(directory,'final-cockpit.png')}).catch(()=>{});}
  if(agentPath){report.finalStorage=await memory().catch(()=>null);report.history=await history().catch(()=>null);if(handle)report.finalSession=await api(`${agentPath}/live/sessions/${handle}`).catch(()=>null);}
  retainDiagnostics(report.finalSession);
  report.silentInputReceipts=[...silentInputReceipts.values()];
  for(const turn of report.turns.filter(t=>t.directCommand)){
    const storage=turn.memory??(turn===report.turns.at(-1)?report.finalStorage:null);
    turn.commandSource??=interruptedCommandSource(turn,report.history,storage?.['command.lastResult']);
    turn.commandTiming=commandTiming({...turn,memory:storage},[...contextTraces.values()],providerEvents);
    if(turn.commandSource)turn.commandTiming.sourceCreated=report.history?.[turn.commandSource.position]?.createdDate;
  }
  report.controlTimings=controlTimings(report);
  await browser?.close();report.finishedAt=new Date().toISOString();fs.writeFileSync(output,JSON.stringify(report,null,2));
  console.log(report.automatedPassed?'Hand assertions passed; complete speech/audio/visual review required.':'Hand journey failed: '+report.error);
}
