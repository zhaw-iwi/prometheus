import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {handResult} from '../fixtures/hand-landmarks.mjs';
import {hardware} from './fixtures/native-hardware.mjs';
import {openValerianSession} from './fixtures/valerian-session.mjs';
import {runHandScenario} from '../gptlive/hand_scenarios.mjs';
import {armingReady} from '../gptlive/commanding_evidence.mjs';

for(const delayed of [false,true])test(`L-07 fixture rehearsal through native delivery with ${delayed?'slow':'normal'} receipts`,async({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN||process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the disposable fixture runner');
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code=Math.random().toString(36).slice(2,7).toUpperCase();
  const access=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code};
  const agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json();
  const path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const history=async()=>(await request.get(path+'/live/history',{headers})).json();
  const provider=async()=>(await request.get('/__live-fixture/latest')).json();
  const fixture=async(route,data)=>expect((await request.post('/__live-fixture/'+route,{data})).ok()).toBe(true);
  const inputs=JSON.parse(readFileSync('tests/gptlive/hand-cases.json','utf8'));
  const check={eventType:'obs.hand.sequence',minConfidence:0,handSequence:{schemaVersion:1,steps:[1,2,3].map(count=>({kind:'count',count,hands:'right'})),dwellMs:300,maximumGapMs:5000,maximumDurationMs:120000}};
  const partial={goal:'React once to the fresh sequence after acceptance.',humanRole:'Show the right-hand one-two-three sequence.',agentRole:'',success:'',
    questions:['What should I do on the fresh sequence?'],checks:[check],dialogueLimit:4,reactions:null};
  const agreement={...partial,questions:[],humanRole:'Demonstrate, accept, repeat and judge the response.',agentRole:'Say only ready once.',
    success:'The visitor judges whether the robot said only ready once.',reactions:{goal:partial.goal,maxActions:1,sessionBound:true,firstAction:'ON_CUE',
      rules:[{...check,action:'Say exactly: "ready"',effect:'ACT',samples:1,cooldownSeconds:3}]}};
  const lines=['What should I do on the fresh sequence?','Please demonstrate one, two, then three fingers on your right hand.',
    'You demonstrated the sequence. After acceptance I will respond once to a fresh sequence. You judge the result. Shall we start?',
    'The task is armed. I am waiting for a fresh one-two-three sequence.','ready'];
  const checkpoints=[],frames=[],competingSpeech=[];
  // Attempt 106's six HTTP durations for the valid preparation demonstration.
  const slowPreparationMs=[274,459,1730,1442,88,1384];
  const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  let providerId;
  const turn=async(name,operation,proposal)=>{
    await fixture('coordination-proposal',{operation,agreement:proposal,reply:{}});
    await fixture('speak/'+providerId,{speaker:'USER',text:inputs[name]});
  };
  const occasionConsumed=async flag=>expect.poll(async()=>{
    const state=await memory(),response=state['core.task.response'];
    return response?.[flag]===true&&response.consumed===true;
  }).toBe(true);
  const checkpoint=async name=>{
    const state=await memory();
    checkpoints.push({name,phase:state['task.phase'],actions:state['task.actions']??0,progress:state['hand.sequence.progress.v1']});
    return state;
  };
  await hardware(context);
  page.on('request',r=>{if(r.method()==='POST'&&/speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech/.test(r.url()))competingSpeech.push(r.url());});
  try{
    await openValerianSession(page,`/valerian/?agentId=${agent.id}`,code);
    await page.getByTestId('gptlive-tab').click();await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    providerId=(await provider()).providerId;
    await fixture('commentary/'+providerId,lines);
    await fixture('hand-output',{speech:'ready'});
    await runHandScenario('L-07',{
      memory,history,checkpoint,wait,
      zero:async()=>expect((await memory())['task.actions']??0).toBe(0),
      observe:async(type,sample)=>{
        const started=Date.now();
        const response=await request.post(path+'/acknowledge?profile=full_plan',{headers,data:{type,actor:'controlled-camera',kind:'observation',payload:JSON.stringify(sample)}});
        expect(response.status(),sample.observationId).toBe(200);
        if(delayed&&checkpoints.at(-1)?.name==='counts-1-3'&&slowPreparationMs.length){
          await wait(Math.max(0,slowPreparationMs.shift()-(Date.now()-started)));
        }
        frames.push({index:sample.captureIndex,captured:Date.parse(sample.ts),received:Date.now()});
      },
      propose:async(name,verify,{clarification})=>{
        await turn(name,'PROPOSE',partial);await occasionConsumed('proposalReview');
        expect((await memory())['coordination.draft']).toEqual(partial);
        await turn(clarification,'PROPOSE',agreement);await occasionConsumed('supportOnly');
        const state=await memory();expect(state['coordination.agreement']).toBeUndefined();
        expect(state['coordination.draft']).toEqual(agreement);verify(state['coordination.draft']);return state['coordination.draft'];
      },
      ready:async()=>{
        await occasionConsumed('proposalReview');
        const s=await memory();expect(Object.keys(s['coordination.evidence'])).toHaveLength(1);expect(s['coordination.agreement']).toBeUndefined();
      },
      accept:async()=>{
        await turn('accept','START',null);await expect.poll(async()=>armingReady(await memory(),Date.now())).toBe(true);
        const s=await memory();expect(s['coordination.agreement']).toEqual(agreement);expect(s['task.actions']).toBe(0);
      },
      complete:async()=>{
        await expect.poll(async()=>(await memory())['task.response']?.status).toBe('CAPTURED');
        const state=await memory();expect(state['task.phase']).toBe('FINISHING');
        const p=await provider(),id=state['task.response'].id;
        const command=p.commands.map(JSON.parse).filter(e=>e.type==='session.commentary.append'&&e.content.includes(id));
        expect(command).toHaveLength(1);expect(command[0].content).toContain('Say this exact line once, then listen: "ready"');
        const caption=p.captions.map(JSON.parse).find(e=>e.type==='session.output_transcript.delta'&&e.delta==='ready');
        expect(caption).toBeTruthy();
        await page.evaluate(c=>{__nativeReceive(c);__nativeGain.gain.value=.15;},caption);
        await wait(700);await page.evaluate(()=>{__nativeGain.gain.value=0;});
        await expect.poll(async()=>(await memory())['coordination.delivery']?.status).toBe('PLAYBACK_CONFIRMED');
      }
    });
    const final=await memory();expect(final['task.phase']).toBe('COMPLETED');expect(final['task.actions']).toBe(1);
    const heard=(await provider()).captions.map(JSON.parse).filter(e=>e.type==='session.output_transcript.delta').map(e=>e.delta);
    expect(heard).toEqual(lines);expect(competingSpeech).toEqual([]);
    expect(new Set(frames.map(f=>f.index)).size).toBe(frames.length);if(delayed)expect(slowPreparationMs).toHaveLength(0);
    writeFileSync(info.outputPath('l07-fixture-evidence.json'),JSON.stringify({checkpoints,frames,heard,delivery:final['coordination.delivery'],actions:final['task.actions']},null,2));
  }finally{
    await page.close();await request.delete('/__live-fixture/hand-output');await request.delete('/__live-fixture/coordination-proposal');await request.delete(path,{headers});
  }
});

test('ordered counting exposes progress and lost sensing without replaying preparation',async({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN||process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the enabled disposable MySQL runner');
  const errors=[],frames=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().endsWith('/acknowledge')&&r.postDataJSON()?.type==='obs.hand.pose')frames.push(JSON.parse(r.postDataJSON().payload));});
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access=await(await request.post('/admin/access-codes',{headers:admin,data:{code:Math.random().toString(36).slice(2,7).toUpperCase(),enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':access.code},agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json(),path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  await context.addInitScript(()=>{navigator.mediaDevices.getUserMedia=async()=>{const c=document.createElement('canvas');c.width=640;c.height=480;setInterval(()=>{c.getContext('2d').fillRect(0,0,640,480);},50);return c.captureStream(15);};});
  await context.route('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35',r=>r.fulfill({contentType:'application/javascript',headers:{'Access-Control-Allow-Origin':'*'},body:`export const FilesetResolver={forVisionTasks:async()=>({})};export const GestureRecognizer={createFromOptions:async()=>({recognizeForVideo:()=>window.__hand||{landmarks:[],gestures:[]},close(){}})};`}));
  for(const url of ['https://cdn.jsdelivr.net/npm/@tensorflow*/**','https://cdn.jsdelivr.net/npm/face-api.js*/**'])await context.route(url,r=>r.fulfill({body:'',contentType:'application/javascript'}));
  const section=async id=>{await expect(page.locator('#sensing_accordion .collapsing')).toHaveCount(0);if(!(await page.locator('#'+id).evaluate(e=>e.classList.contains('show'))))await page.locator(`[data-bs-target="#${id}"]`).click();await expect(page.locator('#'+id)).toHaveClass(/\bshow\b/);};
  const say=async text=>{const response=page.waitForResponse(r=>r.url().includes(path+'/acknowledge')&&r.request().method()==='POST'&&r.request().postDataJSON()?.type==='obs.user_utterance');await page.getByTestId('text-input').fill(text);await page.getByTestId('send-text').click();expect((await response).ok()).toBe(true);};
  const count=async n=>page.evaluate(result=>{window.__hand=result;},n===null?{landmarks:[],gestures:[]}:handResult(Object.fromEntries(['thumb','index','middle','ring','little'].map((f,i)=>[f,i<n?0:1]))));
  const index=async slot=>(await memory())['hand.sequence.progress.v1']?.[slot]?.index||0;
  const sequence={schemaVersion:1,steps:[1,2,3].map(count=>({kind:'count',count,hands:'right'})),dwellMs:300,maximumGapMs:5000,maximumDurationMs:30000};
  const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/named-sign-agreement.json','utf8'));agreement.goal='Acknowledge after one then two then three';agreement.reactions.goal=agreement.goal;agreement.humanRole='Show counts on the right hand';agreement.agentRole='Acknowledge once';
  for(const row of [agreement.checks[0],agreement.reactions.rules[0]]){delete row.handSign;row.eventType='obs.hand.sequence';row.handSequence=sequence;row.minConfidence=0;}
  try{
    await page.goto(`/valerian/?agentId=${agent.id}`);await page.getByTestId('access-code-input').fill(access.code);await page.getByTestId('submit-access-code').click();await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');await expect(page.locator('#observation_clock_status')).toHaveText('Sensor timing ready');
    await section('sensor_toggles');await page.getByTestId('sensor-hand-enabled').check();await expect(page.getByTestId('hand-sign-status')).toHaveText('Model ready');await page.locator('#sensor_emit_enabled').check();await page.locator('#start_camera').click();await section('sensed_signals');await page.locator('#hand_sequence_setup > summary').click();
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Demonstrate the whole sequence to prepare.'}}});await say('When I show one then two then three, acknowledge once');expect((await memory())['coordination.draft']).toEqual(agreement);
    await count(1);await expect.poll(()=>index('prep-0')).toBe(1);await page.locator('#hand_sequence_setup').screenshot({path:info.outputPath('one-of-three.png')});
    await count(3);await expect.poll(()=>index('prep-0')).toBe(0);await expect(page.locator('[data-testid="sequence-status"]')).toContainText('Wrong stable step');
    await count(null);await expect(page.locator('#sequence_count')).toContainText('No hands');
    for(const n of [1,2,3]){await count(n);await expect.poll(()=>index('prep-0')).toBe(n);}
    await say('Start now');expect((await memory())['task.actions']).toBe(0);await count(1);await expect.poll(()=>index('run-0'),{timeout:12000}).toBe(1);
    await page.locator('#stop_camera').click();await expect(page.locator('#sequence_count')).toContainText('No fresh hands');await page.locator('#start_camera').click();await count(2);await expect.poll(async()=>(await memory())['task.phase']).toBe('PAUSED');expect((await memory())['task.actions']).toBe(0);
    await page.setViewportSize({width:390,height:1000});await page.locator('#hand_sequence_setup').screenshot({path:info.outputPath('paused-source-390.png')});
    await say('Resume');await count(1);await expect.poll(()=>index('run-0'),{timeout:12000}).toBe(1);await count(2);await expect.poll(()=>index('run-0')).toBe(2);await count(3);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    const finalFrames=frames.length;await expect.poll(()=>frames.length,{timeout:6000}).toBeGreaterThan(finalFrames);expect((await memory())['task.actions']).toBe(1);await page.locator('#hand_sequence_setup').screenshot({path:info.outputPath('completed-390.png')});
    expect(frames.some((p,i)=>i>0&&Date.parse(p.ts)-Date.parse(frames[i-1].ts)<1000)).toBe(true);expect(frames.every(p=>Number.isInteger(p.captureIndex))).toBe(true);expect(errors).toEqual([]);
    writeFileSync(info.outputPath('sequence-evidence.json'),JSON.stringify({agreement,frames,memory:await memory()},null,2));
  }finally{await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');}
});
