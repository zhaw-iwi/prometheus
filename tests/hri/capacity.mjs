import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {completeAssessment} from './complete.mjs';
const [directory,mode]=process.argv.slice(2),base=process.env.PROMETHEUS_BASE_URL;
if(!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base))throw Error('Owned loopback app required');
const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},seconds=Number(mode),recover=mode==='--recover';
const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v2.json','utf8'));
const waveSize=Number(process.env.PROMETHEUS_STUDY_WAVE??8);
const report={passed:false,qualified:false,seconds,sessions:waveSize,samples:[],requests:[],errors:[],queueMs:[],sseConnections:0,sseFrames:0};
const clients=[],controllers=[],timers=[],background=[];
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const saved=(name,value)=>fs.writeFile(path.join(directory,name),JSON.stringify(value,null,2));
let phase='setup';
async function api(route,body,headers=admin,expected=200){
  const start=performance.now();let response;
  try{response=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{...headers,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});}
  catch(error){report.errors.push({route,kind:error.name});throw error;}
  const elapsed=performance.now()-start;report.requests.push({route:route.replace(/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}/g,':id'),phase,status:response.status,ms:elapsed});
  const timing=response.headers.get('X-Prometheus-Timing');if(timing)try{const data=JSON.parse(Buffer.from(timing,'base64url').toString());for(const span of data.spans??[])if(span.stage==='agent_queue')report.queueMs.push(span.durationMs);}catch{}
  assert.equal(response.status,expected,`${route}: ${response.status}, expected ${expected}`);
  return response.status===204?null:response.json();
}
async function metrics(){const data=await api('/__live-fixture/capacity');report.samples.push({...data,at:Date.now(),phase});assert.equal(data.pool.waiting,0);await saved('capacity-progress.json',{phase,at:Date.now(),elapsedSeconds:(Date.now()-report.samples[0].at)/1000,samples:report.samples.length,queries:data.questions-report.samples[0].questions,requests:report.requests.length,pool:data.pool,errors:report.errors});return data;}
function track(promise){const handled=promise.catch(error=>{report.errors.push({background:error.message});});background.push(handled);return handled;}
function connect(client){
  client.stream?.abort();const controller=new AbortController();client.stream=controller;controllers.push(controller);report.sseConnections++;
  const task=(async()=>{const response=await fetch(base+'/api/study/stream',{headers:client.headers,signal:controller.signal});assert.equal(response.status,200);for await(const bytes of response.body){report.sseFrames++;if(Buffer.from(bytes).toString().includes('data:')&&!client.refreshing)await refresh(client);}})();
  track(task.catch(error=>{if(!controller.signal.aborted)throw error;}));
}
async function refresh(c,force=false){
  if(c.refreshing){await c.refreshing;if(!force)return;}
  c.refreshing=(async()=>{
    const [page,state,events,activity]=await Promise.all(['page','conversation','events','activity'].map(p=>api('/api/study/'+p,undefined,c.headers)));
    assert.equal(page.session.id,c.id);c.cursor=state.turnVersion;c.state=state;c.activity=activity;
    const serialized=JSON.stringify({state,events});for(const other of clients)if(other!==c)assert.ok(!serialized.includes(other.marker),'Cross-session conversation leakage');
    assert.ok(!serialized.includes(c.private),'Private answer reached conversation');
    for(const event of events)if(event.type==='resp.behaviour_plan'&&!c.displayed.has(event.id)){
      await api('/api/study/display',{eventId:event.id,clientTime:new Date().toISOString(),disposition:event.withheld?'WITHHELD':'DISPLAY'},c.headers,204);c.displayed.add(event.id);
    }
  })();try{await c.refreshing;}finally{c.refreshing=null;}
}
async function mutation(c,route,body){const result=await api('/api/study/'+route,body,c.headers);assert.equal(result.status,'COMMITTED');assert.deepEqual(await api('/api/study/'+route,body,c.headers),result);c.last={route,body,result};await refresh(c,true);return result;}
async function say(c,text){return mutation(c,'turns',{requestId:randomUUID(),expectedTurn:c.cursor,text});}
async function prediction(c){await refresh(c,true);assert.equal(c.activity.predictionRequired,true);await api('/api/study/predictions',{revision:c.activity.revision,prediction:c.private+' prediction'},c.headers);}
async function scene(c,count,planned=false){await refresh(c);return mutation(c,'scene',{requestId:randomUUID(),expectedScene:c.activity.sceneVersion,humanCount:count,observedAt:new Date().toISOString(),...(planned?{probeStep:c.activity.sceneProbeStep}:{})});}
async function sign(c,value){await refresh(c);const scope=JSON.parse(c.activity.signScope);delete scope.remaining;return mutation(c,'sign',{...scope,requestId:randomUUID(),expectedTurn:c.cursor,observationId:randomUUID(),sign:value,observedAt:new Date().toISOString()});}
async function create(scenario,index,activate=true){
  const i=await api('/admin/study/attempts',{mode:'SYNTHETIC',scenario});
  const c={id:i.session.id,token:i.token,headers:{'X-Prometheus-Study-Token':i.token},scenario,marker:'PUBLIC_CAPACITY_CLIENT_'+index+'_',private:'PRIVATE_CAPACITY_'+index+'_',displayed:new Set(),cursor:0};clients.push(c);
  await api('/api/study/consent',{version:'synthetic-test-v1'},c.headers);
  await api('/api/study/forms/pre',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'Synthetic fixture'])),c.headers);
  await api('/api/study/forms/intention',{intention:c.private+' intention'},c.headers);
  if(activate)await api('/api/study/forms/private',{private:c.private},c.headers);
  return c;
}
function terms(scenario){
  const common={goal:'A fictional bounded activity',humanRole:'Respond using the agreed choices',agentRole:'Ask the agreed questions',success:'The agreed responses are recorded',questions:[],checks:[],reactions:null};
  if(scenario==='guess')return {...common,success:'The human confirms a specific guess',dialogueLimit:6,activity:{kind:'GUESSING',secretOwner:'HUMAN',answers:['YES','NO','UNKNOWN'],questionLimit:5,guessesCount:true}};
  if(scenario==='context')return {...common,dialogueLimit:2,activity:{kind:'CONTEXT_CHECKIN',question:'In this fictional scenario, was the medication routine completed?',yesReply:'Thank you for letting me know.',noReply:'You can consider the reminder in the fictional care plan.',startCount:1,postponeAt:2,departure:'END',resume:'EXPLICIT_WHEN_ONE'}};
  return {...common,dialogueLimit:4,activity:{kind:'SIGN_ANSWERS',questions:['Would you like a short activity?','Would you prefer a quiet activity?','Would you like a break afterwards?'],yesSign:'THUMBS_UP',noSign:'THUMBS_DOWN',waitSign:'OPEN_PALM',resume:'EXPLICIT_TEXT',demonstrate:true}};
}
function scripts(){
  const replies={};const response=(operation,agreement=null)=>JSON.stringify({operation,agreement,reply:{speech:'Ready.'}});
  for(const c of clients){c.propose=c.marker+' propose the assigned bounded activity.';c.revise=(c.scenario==='guess'?'Change the question limit to three, keeping every other term unchanged. ':c.scenario==='signs'?'Swap the yes and no signs, keeping all other conditions unchanged. ':'Replace only the no response with A reminder may help, preserving all other conditions. ')+c.marker;c.clarify=c.marker+' help me specify the activity.';
    const initial=terms(c.scenario),revised=structuredClone(initial);
    if(c.scenario==='guess'){revised.activity.questionLimit=3;revised.dialogueLimit=4;}
    else if(c.scenario==='context')revised.activity.noReply='A reminder may help.';
    else{revised.activity.yesSign='THUMBS_DOWN';revised.activity.noSign='THUMBS_UP';}
    replies[c.clarify]=JSON.stringify({operation:'KEEP',agreement:null,reply:{speech:'Which roles and conditions would you like?'}});
    replies[c.propose]=response('PROPOSE',initial);replies[c.revise]=response('REPLACE',revised);
  }
  replies['I accept this proposal. Start now.']=response('START');return replies;
}
function actions(c){
  const steps=[()=>say(c,c.clarify),()=>say(c,c.propose)];
  const demo=()=>['THUMBS_UP','THUMBS_DOWN','OPEN_PALM'].forEach(value=>steps.push(()=>sign(c,value)));
  const accept=()=>steps.push(()=>prediction(c),()=>say(c,'I accept this proposal. Start now.'));
  if(c.scenario==='signs')demo();accept();
  if(c.scenario==='guess')steps.push(()=>say(c,'Pause'),()=>say(c,'Resume'));
  if(c.scenario==='context')for(const count of [1,2,1])steps.push(()=>scene(c,count,true));
  if(c.scenario==='signs')steps.push(()=>sign(c,'THUMBS_UP'),()=>sign(c,'OPEN_PALM'),()=>say(c,'Resume'));
  steps.push(()=>say(c,c.revise));if(c.scenario==='signs')demo();accept();
  if(c.scenario==='guess')for(let i=0;i<3;i++)steps.push(()=>say(c,'No'));
  if(c.scenario==='context')steps.push(()=>scene(c,1),()=>say(c,'Resume'),()=>say(c,'No'),()=>scene(c,0),()=>scene(c,1));
  if(c.scenario==='signs')steps.push(()=>sign(c,'THUMBS_DOWN'),()=>sign(c,'THUMBS_UP'));
  steps.push(async()=>{await refresh(c);assert.equal(c.state.phase,'REFLECTION');
    if(c.scenario==='guess')assert.equal(JSON.parse(c.activity.game).outcome,'QUESTION_LIMIT_REACHED');
    if(c.scenario==='context')assert.equal(JSON.parse(c.activity.context).outcome,'FICTIONAL_NO_REPORTED');
    if(c.scenario==='signs')assert.deepEqual(JSON.parse(c.activity.signs).answers.map(a=>a.answer),['YES','YES','NO']);
  });return steps;
}
function distribution(values){const a=[...values].sort((a,b)=>a-b);return a.length?{n:a.length,p50:a[Math.ceil(a.length*.5)-1],p95:a[Math.ceil(a.length*.95)-1],max:a.at(-1)}:null;}
try{
  if(recover){
    const checkpoints=JSON.parse(await fs.readFile(path.join(directory,'recovery-private.json'),'utf8'));
    for(const c of checkpoints){const headers={'X-Prometheus-Study-Token':c.token};const page=await api('/api/study/page',undefined,headers);assert.equal(page.session.id,c.id);
      if(c.pending){await api('/api/study/presence',{},headers);assert.deepEqual(await api('/api/study/'+c.last.route,c.last.body,headers),c.last.result);
        const activity=await api('/api/study/activity',undefined,headers);assert.deepEqual(JSON.parse(activity.context).pending,c.pending);
        await api('/api/study/finish',{reason:'TECHNICAL'},headers);await completeAssessment((p,b)=>api(p,b,headers));}
      const data=await api('/admin/study/attempts/'+c.id+'/export');assert.equal(data.manifest.recordingComplete,true);assert.equal(data.manifest.procedureComplete,true);assert.equal(data.agreements.length,c.pending?1:2);
    }
    await saved('capacity-recovery.json',{passed:true,completed:checkpoints.length-1,pendingRecovered:1,receiptReplay:true,assessmentAndExport:true});
  }else{
    const before=await metrics();const setupStart=performance.now();
    await Promise.all(Array.from({length:waveSize},(_,i)=>create(['guess','context','signs'][i%3],i)));
    const wave=[...clients];const waiter=await create('context',waveSize,false);
    const full=await api('/api/study/forms/private',{private:waiter.private},waiter.headers,429);assert.match(full.message,/occupied/);
    assert.equal((await api('/api/study/page',undefined,waiter.headers)).session.stage,'PRIVATE');
    await api('/__live-fixture/study-script',{replies:scripts(),delayMillis:1000});
    await Promise.all(wave.map(c=>refresh(c)));wave.forEach(connect);
    // Real idle SSE checkpoint, before any active action.
    await sleep(1000);await Promise.all(wave.map(c=>c.refreshing));const idle=await metrics();assert.equal(idle.pool.active,0);report.idlePool=idle.pool;
    report.setup={ms:performance.now()-setupStart,queries:idle.questions-before.questions};
    phase='active';const start=performance.now(),activeStart=await metrics();
    timers.push(setInterval(()=>track(metrics()),10000));
    timers.push(setInterval(()=>track(Promise.all(wave.filter(c=>!c.done).map(c=>api('/api/study/presence',{},c.headers)))),30000));
    const duration=seconds*1000,activeDuration=duration*.8;
    await Promise.all(wave.map(async(c,index)=>{
      const steps=actions(c);for(let step=0;step<steps.length;step++){
        await sleep(Math.max(0,start+index*100+step*activeDuration/(steps.length-1)-performance.now()));
        if(step===Math.floor(steps.length/2)){connect(c);await refresh(c);}
        try{await steps[step]();}catch(error){throw new Error(`${c.scenario} step ${step}: ${error.message}`,{cause:error});}
      }
    }));
    const activeEnd=await metrics();report.active={ms:performance.now()-start,queries:activeEnd.questions-activeStart.questions};
    phase='assessment';await sleep(Math.max(0,start+duration*.85-performance.now()));
    await Promise.all(wave.map(async c=>{c.done=true;await api('/api/study/finish',{reason:'ACTIVITY_ENDED'},c.headers);c.stream.abort();await completeAssessment((p,b)=>api(p,b,c.headers));}));
    // The formerly blocked participant now gets a slot and leaves a durable pending question for restart.
    await api('/api/study/forms/private',{private:waiter.private},waiter.headers);await refresh(waiter);await say(waiter,waiter.propose);await prediction(waiter);await say(waiter,'I accept this proposal. Start now.');await scene(waiter,1,true);
    const pending=JSON.parse(waiter.activity.context).pending;assert.ok(pending);waiter.done=true;
    await sleep(Math.max(0,start+duration-performance.now()));phase='export';const exportStart=performance.now(),exportBefore=await metrics();
    const dataset=await api('/admin/study/export?mode=SYNTHETIC');await saved('capacity-export.json',dataset);
    for(const c of wave){const data=await api('/admin/study/attempts/'+c.id+'/export');assert.equal(data.manifest.recordingComplete,true);assert.equal(data.manifest.procedureComplete,true);assert.equal(data.agreements.length,2);
      const ids=data.requests.map(r=>r.receipt.requestId);assert.equal(new Set(ids).size,ids.length);assert.ok(!JSON.stringify(data).includes(c.token));
    }
    const end=await metrics();report.export={ms:performance.now()-exportStart,queries:end.questions-exportBefore.questions};
    report.measuredMs=performance.now()-start;report.totalQueries=end.questions-before.questions;
    report.projectedHourIncludingTwoSetupsAndExports=report.totalQueries*2;
    report.activeQueriesPerHour=report.active.queries/report.active.ms*3600000;
    report.numericHeadroomMet=report.projectedHourIncludingTwoSetupsAndExports<=28800&&report.activeQueriesPerHour<=28800;
    report.latency={};for(const name of ['setup','active','assessment','export']){
      const rows=report.requests.filter(r=>r.phase===name&&!r.route.startsWith('/__live-fixture'));
      report.latency[name]={turn:distribution(rows.filter(r=>/\/(turns|scene|sign)$/.test(r.route)).map(r=>r.ms)),other:distribution(rows.filter(r=>! /\/(turns|scene|sign)$/.test(r.route)).map(r=>r.ms))};
    }
    report.queue=distribution(report.queueMs);report.queueCoverage=report.queue?'Instrumented agent_queue spans':'No agent_queue spans exposed on study routes; pool waiters sampled separately';
    report.cpuSeconds=(end.processCpuNanos-before.processCpuNanos)/1e9;
    report.peakHeapBytes=Math.max(...report.samples.map(s=>s.heapUsed));report.peakThreads=Math.max(...report.samples.map(s=>s.threads));
    assert.equal(report.errors.length,0);for(const name of ['active','assessment']){const l=report.latency[name];if(l.turn){assert.ok(l.turn.p95<=5000);assert.ok(l.turn.max<=30000);}if(l.other){assert.ok(l.other.p95<=2000);assert.ok(l.other.max<=30000);}}
    await saved('recovery-private.json',[...wave.map(c=>({id:c.id,token:c.token})),{id:waiter.id,token:waiter.token,pending,last:waiter.last}]);
    report.passed=true;report.qualified=seconds>=1800&&report.numericHeadroomMet;
    if(seconds>=1800)assert.equal(report.qualified,true,'Measured workload exceeds predeclared capacity bounds');
  }
}catch(error){report.errors.push({failure:error.message,stack:error.stack});process.exitCode=1;}
finally{
  timers.forEach(clearInterval);controllers.forEach(c=>c.abort());await Promise.allSettled(background);
  if(!recover)await saved('capacity.json',report);else if(process.exitCode)await saved('capacity-recovery.json',{passed:false,errors:report.errors});
  console.log(JSON.stringify({passed:recover?!process.exitCode:report.passed,qualified:report.qualified,errors:report.errors,latency:report.latency,totalQueries:report.totalQueries,projected:report.projectedHourIncludingTwoSetupsAndExports,activeRate:report.activeQueriesPerHour}));
}

process.exit(process.exitCode??0);
