import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {handResult} from '../fixtures/hand-landmarks.mjs';

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
