// Explicitly paid, synthetic visitor audio; real scoped app, OpenAI, MySQL and WebGL.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {installOfflineUiAssets} from '../playwright/fixtures/offline-ui-assets.mjs';
import {synchronizeEmbodimentFrame} from '../playwright/fixtures/embodiment-frame.mjs';
import {trialCapture} from './trial_capture.mjs';
import {armingReady, speechLatency, conversationLatency} from './commanding_evidence.mjs';

const [permission,output]=process.argv.slice(2);
const mode=process.argv.find(v=>v.startsWith('--agent=commanding-'))?.slice('--agent=commanding-'.length);
const base=process.env.PROMETHEUS_BASE_URL,ledger=process.env.PROMETHEUS_COMMANDING_BUDGET_LEDGER;
if(permission!=='--allow-paid-provider'||!output||!['hp1','hp2','hp3','baseline','ordinary'].includes(mode)
  ||!base||!['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname)||!ledger)
  throw Error('Use the authorized isolated commanding runner.');
const reservation=JSON.parse(fs.readFileSync(ledger));
const plan=JSON.parse(fs.readFileSync(process.env.PROMETHEUS_COMMANDING_PAID_PLAN||new URL('./commanding-paid-plan.json',import.meta.url)));
assert.equal(reservation.authorization,plan.authorization||'commanding-2026-11-19-usd40');
assert.ok(reservation.attempts.length<=plan.maximumAttempts&&reservation.reservedUsd<=plan.maximumReservedUsd);
assert.ok(reservation.attempts.some(e=>e.mode==='commanding-'+mode&&e.reservedUsd===5&&path.resolve(e.artifact)===path.dirname(path.resolve(output))));
fs.writeFileSync(path.join(path.dirname(output),'provider-attempt.started'),new Date().toISOString(),{flag:'wx'});
const clips=JSON.parse(fs.readFileSync(new URL('./commanding-cases.json',import.meta.url)));
const stimulus=JSON.parse(fs.readFileSync('target/commanding-audio/manifest.json'));
assert.equal(stimulus.providerCalls,0);assert.equal(stimulus.sampleRate,24000);
const report={mode,plan,startedAt:new Date().toISOString(),automatedPassed:false,semanticReview:'PENDING',physicalAudibility:'NOT_RUN',
  coveredJourneys:mode==='hp1'?['HP-01','HP-02','HP-04 Coordination']:mode==='hp3'?['HP-03','HP-04 baseline']:
    mode==='hp2'?['HP-02','HP-04 Coordination']:[mode],
  turns:[],network:[],screenshots:[],renderChecks:[],browserErrors:[],stimulus,clocks:{browser:'performance_now_ms',server:'unix_ms',provider:'audio_offset_ms'}};
const started=Date.now(),directory=path.dirname(output);
let browser,page,agentPath,scoped,handle,turns=0,closing=false,watchdog;
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const check=()=>{if(!closing&&Date.now()-started>plan.limits.journeySeconds*1000)throw Error('Journey budget exhausted');};
async function api(route,method='GET',body,headers=scoped){
  check();const response=await fetch(base+route,{method,headers:{...headers,'Content-Type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  if(!response.ok)throw Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status===204?null:response.json();
}
const memory=async()=>Object.fromEntries((await api(agentPath+'/storage')).map(e=>[e.key,JSON.parse(e.value)]));
const history=async()=>{
  const events=await api(agentPath+'/live/history');
  assert.ok(events.every(e=>typeof e.id==='string'),'Live history must expose persisted event identity');
  return events;
};
async function until(label,action,ms=45000){
  const end=Date.now()+ms;
  while(Date.now()<end){check();if(handle&&!closing&&await page.getByTestId('gptlive-status').textContent()!=='Active')throw Error('Native transport stopped');
    const value=await action();if(value)return value;await wait(250);}
  throw Error('Timed out: '+label);
}
async function quiet(){await page.waitForFunction(()=>performance.now()-(__trial.lastVoice||0)>1200
  &&performance.now()-(__trial.lastOutputCaption||0)>3500,null,{timeout:35000});}
async function pose(name,sign,gesture){
  if(sign)await page.getByTestId('embodiment-stage').waitFor({state:'visible'});
  await page.waitForFunction(({sign,gesture})=>{const e=document.querySelector('[data-testid="embodiment-stage"]');
    return e?.dataset.ready==='true'&&(!sign||e.dataset.handSign===sign)&&(!gesture||e.dataset.gesture===gesture);},{sign,gesture});
  const rendering=await synchronizeEmbodimentFrame(page);report.renderChecks.push({name,...rendering});
  assert.equal(rendering.glError,0);assert.ok(rendering.irisPixels>100,'Default face must remain visible');
  const file=name+'.png';await page.locator('#embodiment_panel').screenshot({path:path.join(directory,file)});report.screenshots.push(file);
}
async function say(name,{reply=true,immediate=false,silent=false}={}){
  if(++turns>plan.limits.visitorTurns)throw Error('Visitor turn cap exceeded');
  if(!immediate)await quiet();
  const before=await history(),ids=new Set(before.map(e=>e.id));
  const prior=await memory();
  const turn={name,intendedInput:clips[name],beforeIds:[...ids],beforeCommandCount:prior['command.handledTurns']?.length||0,
    beforeAgreement:prior['coordination.agreement'],beforeActions:prior['task.actions'],index:await page.evaluate(()=>__trial.events.length)};report.turns.push(turn);
  const clip=stimulus.clips[name],bytes=fs.readFileSync(path.join('target/commanding-audio',clip.file));
  assert.equal(clip.text,clips[name]);assert.equal(createHash('sha256').update(bytes).digest('hex'),clip.sha256);
  console.log('Visitor: '+name);
  turn.input=await page.evaluate(async({bytes,name})=>{
    const pcm=new Int16Array(Uint8Array.from(bytes).buffer),samples=Float32Array.from(pcm,v=>v/32768);
    const buffer=__trial.context.createBuffer(1,samples.length,24000);buffer.copyToChannel(samples,0);
    let last=samples.length-1;while(last>0&&Math.abs(samples[last])<.01)last--;
    const source=__trial.context.createBufferSource();source.buffer=buffer;source.connect(__trial.input);
    const at=performance.now();__trial.mark('input_start',{name});
    await new Promise(resolve=>{source.onended=resolve;source.start();});
    const speechEndMs=at+last/24;__trial.mark('input_end',{name,speechEndMs});
    return{startMs:at,speechEndMs,bufferEndMs:performance.now()};
  },{bytes:[...bytes],name});
  await until('durable user '+name,async()=>(await history()).some(e=>!ids.has(e.id)&&e.type==='obs.user_utterance'));
  if(silent)await wait(8000);
  else if(reply){await page.waitForFunction(i=>__trial.events.slice(i).some(e=>e.type==='session.output_transcript.delta'),turn.index,{timeout:35000});await quiet();}
  turn.memory=await memory();turn.history=(await history()).filter(e=>!ids.has(e.id));
  turn.events=await page.evaluate(i=>__trial.events.slice(i),turn.index);
  turn.spoken=turn.events.filter(e=>e.type==='session.output_transcript.delta').map(e=>e.delta).join('');
  turn.latency=speechLatency(turn.events,turn.input);
  if(silent){assert.ok(!turn.events.some(e=>e.type==='output_voice'),'Silent request produced native audio');assert.equal(turn.spoken,'');}
  else if(reply)assert.ok(Number.isFinite(turn.latency.speechEndToOutputMs),'No observed native audio for '+name);
  return turn;
}
async function command(name,status,{sign,gesture,silent=false}={}){
  const turn=await say(name,{silent,reply:status!=='ACCEPTED'});
  const completed=await until('command receipt '+name,async()=>{const m=await memory();return m['command.handledTurns']?.length>turn.beforeCommandCount?m:null;});
  assert.equal(completed['command.handledTurns'].length,turn.beforeCommandCount+1,'One command receipt for '+name);
  assert.equal(completed['command.lastResult']?.status,status,'Command classification for '+name);
  assert.equal(completed['command.lastResult']?.silent,silent,'Explicit user silence flag for '+name);
  if(!silent){await wait(1500);await quiet();}
  turn.events=await page.evaluate(i=>__trial.events.slice(i),turn.index);
  turn.spoken=turn.events.filter(e=>e.type==='session.output_transcript.delta').map(e=>e.delta).join('');
  turn.latency=speechLatency(turn.events,turn.input);
  if(silent){assert.ok(!turn.events.some(e=>e.type==='output_voice'),'Silent request produced native audio');assert.equal(turn.spoken,'');}
  turn.memory=await memory();turn.history=(await history()).filter(e=>!turn.beforeIds?.includes(e.id));
  const receipt=turn.memory['command.lastResult'],all=await history(),source=all[receipt.sourcePosition];
  assert.equal(source?.type,'obs.user_utterance');assert.ok(!turn.beforeIds.includes(source.id));turn.commandSource={id:source.id,position:receipt.sourcePosition};
  const produced=all.slice(receipt.sourcePosition+1).filter(e=>e.type==='resp.behaviour_plan')
    .map(e=>({eventId:e.id,...JSON.parse(e.payload)})).filter(p=>p.motion?.handSign||p.nonVerbal?.gesture&&p.nonVerbal.gesture!=='NONE');
  if(status==='ACCEPTED'){
    const matches=produced.filter(p=>(!sign||p.motion?.handSign===sign)&&(!gesture||p.nonVerbal?.gesture===gesture));
    assert.equal(matches.length,1,'One correlated canonical action');turn.actionEventId=matches[0].eventId;
    assert.ok(!matches[0].speech,'Native command authored competing speech');await pose(name,sign,gesture);
  }else assert.equal(produced.length,0,'Non-action command emitted movement');
  assert.deepEqual(turn.memory['coordination.agreement'],turn.beforeAgreement,'Command changed task agreement');
  assert.equal(turn.memory['task.actions'],turn.beforeActions,'Command spent task budget');
  return turn;
}
async function observe(sign,ts=new Date().toISOString()){
  await api(agentPath+'/acknowledge?profile=full_plan','POST',{type:'obs.hand.sign',actor:'camera',kind:'observation',payload:JSON.stringify({sign,confidence:.95,ts})});return ts;
}
async function acceptedTask(){
  await say('proposal',{reply:false});let m=await memory(),draft=m['coordination.draft'];assert.ok(draft);assert.equal(draft.reactions.firstAction,'ON_CUE');
  assert.equal(draft.reactions.maxActions,1);assert.equal(draft.reactions.rules[0].value,'rock');
  await say('accept',{immediate:true});m=await memory();assert.ok(m['coordination.agreement']);assert.equal(m['task.actions'],0);
  report.accepted=structuredClone(m);
  report.armed=await until('confirmed arming and cue boundary',async()=>{const current=await memory();return armingReady(current,Date.now())?current:null;});
  await observe('paper');assert.equal((await memory())['task.actions'],0);
  const index=await page.evaluate(()=>__trial.events.length),ts=await observe('rock');
  await until('confirmed one contribution',async()=>{const m=await memory();if(m['coordination.delivery']?.status==='UNCERTAIN')throw Error('Contribution became uncertain');return m['task.phase']==='COMPLETED'&&m['task.actions']===1;},55000);
  await quiet();report.contribution={memory:await memory(),events:await page.evaluate(i=>__trial.events.slice(i),index)};
  await observe('rock',ts);assert.equal((await memory())['task.actions'],1);
  await say('return');await command('question','ACCEPTED',{gesture:'OPEN_QUESTION'});
}
try{
  browser=await chromium.launch({headless:true,args:['--autoplay-policy=no-user-gesture-required']});
  watchdog=setTimeout(()=>{report.error='Overall watchdog expired';fs.writeFileSync(output,JSON.stringify(report,null,2));void browser.close();},plan.limits.journeySeconds*1000);
  const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  await installOfflineUiAssets(context);await context.addInitScript(trialCapture);page=await context.newPage();page.setDefaultTimeout(30000);
  page.on('pageerror', error=>report.browserErrors.push(error.message));
  page.on('response',async r=>{if(!r.url().startsWith(base))return;const url=new URL(r.url());
    if(/acknowledge|behaviours\/.*\/speech|speech-synthesis|speech-approvals|playback-cues/.test(url.pathname))report.network.push({path:url.pathname,status:r.status(),method:r.request().method()});
    if(url.pathname.endsWith('/live/sessions')&&r.status()===201)handle=(await r.json()).handle;
  });
  const code=Math.random().toString(36).slice(2,7).toUpperCase(),definition=['hp3','baseline'].includes(mode)?'core.live_multimodal':'core.coordination';
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access=await api('/admin/access-codes','POST',{code,enabled:true},admin);
  await api(`/admin/access-codes/${access.id}/agent-types`,'PUT',{agentTypeKeys:[definition]},admin);scoped={'X-Prometheus-Access-Code':code};
  const agent=await api('/demo/agents','POST',{agentDefinitionKey:definition});agentPath='/demo/agents/'+agent.id;report.agentId=agent.id;
  await page.goto(base+`/valerian/?agentId=${agent.id}`);await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
  await page.getByTestId('send-text').waitFor({state:'visible'});
  await page.getByRole('button',{name:'Robot',exact:true}).click();
  await page.getByTestId('gptlive-tab').click();await page.getByTestId('gptlive-settings').locator(':scope > summary').click();
  await page.locator('#gptlive_voice').selectOption('willow');await page.getByTestId('gptlive-start').click();
  await page.waitForFunction(()=>['Active','Error'].includes(document.getElementById('gptlive_status').textContent));
  assert.equal(await page.getByTestId('gptlive-status').textContent(),'Active');
  // Fresh-session matched samples also verify final voice guidance without another paid session.
  if(['hp1','hp2','hp3'].includes(mode)){
    for(const name of ['hello','activities','planning'])await say(name);
    report.ordinaryLatency=conversationLatency(report.turns);
  }
  if(['baseline','ordinary'].includes(mode)){
    for(const name of ['hello','activities','planning'])await say(name);
    report.latency=conversationLatency(report.turns);
    if(mode==='baseline')fs.writeFileSync('target/commanding-native-baseline.json',JSON.stringify({artifact:path.resolve(output),...report.latency}));
    else{const baseline=JSON.parse(fs.readFileSync('target/commanding-native-baseline.json'));report.latency.baseline=baseline;
      assert.equal(baseline.method,report.latency.method,'Matched onset measurement required');
      assert.ok(report.latency.medianMs<=baseline.medianMs+1000&&report.latency.maxMs<=baseline.maxMs+2000,'Predeclared latency bound missed');}
  }else if(mode==='hp1'){
    await command('capability','CAPABILITY');await command('rock','ACCEPTED',{sign:'rock'});
    await command('question','ACCEPTED',{gesture:'OPEN_QUESTION'});await command('repeat','ACCEPTED',{gesture:'OPEN_QUESTION'});
    await command('silent','ACCEPTED',{sign:'paper',silent:true});
    // Twelve visitor turns total including the ordinary prelude; all original caps remain enforced.
    await acceptedTask();
  }else if(mode==='hp3'){
    await command('capability','CAPABILITY');await command('scissors','ACCEPTED',{sign:'scissor'});
    await command('question','ACCEPTED',{gesture:'OPEN_QUESTION'});await command('unsupported','UNSUPPORTED');
  }else await acceptedTask();
  assert.ok(report.network.every(e=>!/speech-synthesis|speech-approvals|behaviours\/.*\/speech/.test(e.path)),'Competing native Speech/TTS route');
  assert.deepEqual(report.browserErrors,[],'Unexpected browser errors');
  report.automatedPassed=true;
}catch(error){report.error=error.message;report.errorStack=error.stack;process.exitCode=1;}
finally{
  closing=true;clearTimeout(watchdog);
  if(page){report.browser=await page.evaluate(()=>__trial.events).catch(()=>[]);
    report.timings=await page.evaluate(async()=>(await import('/performance/timings.js')).turnTimings.snapshot()).catch(()=>null);
    await page.getByTestId('gptlive-stop').click({timeout:3000}).catch(()=>{});await wait(1000);
    report.diagnostics=await page.evaluate(async()=>(await import('/live/diagnostics.js')).liveDiagnostics.snapshot()).catch(()=>null);
    await page.screenshot({path:path.join(directory,'final-cockpit.png')}).catch(()=>{});}
  if(agentPath){report.finalStorage=await memory().catch(()=>null);report.history=await history().catch(()=>null);
    report.conversation=await api(agentPath+'/live/history').catch(()=>null);if(handle)report.finalSession=await api(`${agentPath}/live/sessions/${handle}`).catch(()=>null);}
  await browser?.close();report.finishedAt=new Date().toISOString();fs.writeFileSync(output,JSON.stringify(report,null,2));
  console.log(report.automatedPassed?'Commanding assertions passed; full semantic review required.':'Commanding journey failed: '+report.error);
}
