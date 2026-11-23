import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {pointingResult} from '../fixtures/hand-landmarks.mjs';

test('configured destinations ground a camera cue and a silent robot point through local MySQL', async ({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the enabled disposable MySQL runner');
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(r.url().includes('/acknowledge')&&!r.ok())errors.push(`Acknowledgement HTTP ${r.status()}`);});
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code=Math.random().toString(36).slice(2,7).toUpperCase();
  const access=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code};
  const agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json();
  const path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/composed-pose-agreement.json','utf8'));
  await context.addInitScript(()=>{
    navigator.mediaDevices.getUserMedia=async()=>{
      const canvas=document.createElement('canvas');canvas.width=640;canvas.height=480;
      const ctx=canvas.getContext('2d');const draw=()=>{ctx.fillStyle='#20494e';ctx.fillRect(0,0,640,480);};
      draw();setInterval(draw,50);return canvas.captureStream(15);
    };
  });
  await context.route('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35',route=>route.fulfill({
    contentType:'application/javascript',headers:{'Access-Control-Allow-Origin':'*'},body:`
      export const FilesetResolver={forVisionTasks:async()=>({})};
      export const GestureRecognizer={createFromOptions:async()=>({recognizeForVideo:()=>window.__namedResult||{landmarks:[],gestures:[]},close(){}})};`,
  }));
  for(const url of ['https://cdn.jsdelivr.net/npm/@tensorflow*/**','https://cdn.jsdelivr.net/npm/face-api.js*/**'])
    await context.route(url,route=>route.fulfill({body:'',contentType:'application/javascript'}));
  const sends=[];page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/acknowledge')){const body=r.postDataJSON();if(body?.type==='obs.hand.pointing')sends.push(JSON.parse(body.payload));}});
  const frame=async good=>{await page.evaluate(r=>{window.__namedResult=r;},pointingResult(good?0:180,'Right',4/3));};
  const section=async id=>{
    await expect(page.locator('#sensing_accordion .collapsing')).toHaveCount(0);
    if(!(await page.locator('#'+id).evaluate(e=>e.classList.contains('show'))))await page.locator(`[data-bs-target="#${id}"]`).click();
    await expect(page.locator('#'+id)).toHaveClass(/\bshow\b/);
  };
  const say=async text=>{const accepted=page.waitForResponse(r=>r.url().includes(path+'/acknowledge')&&r.request().method()==='POST'&&r.request().postDataJSON()?.type==='obs.user_utterance');
    await page.getByTestId('text-input').fill(text);await page.getByTestId('send-text').click();expect((await accepted).ok()).toBe(true);};
  const delivery=async enabled=>{await section('sensor_toggles');await page.locator('#sensor_emit_enabled').setChecked(enabled);await section('sensed_signals');};
  try{
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await expect(page.locator('#observation_clock_status')).toHaveText('Sensor timing ready');
    await section('sensor_toggles');await page.getByTestId('sensor-hand-enabled').check();
    await expect(page.getByTestId('hand-sign-status')).toHaveText('Model ready');
    await page.locator('#sensor_emit_enabled').check();await section('sensor_config');
    await page.locator('#emit_interval_ms').fill('500');
    await frame(false);await page.locator('#start_camera').click();await section('sensed_signals');
    await expect(page.locator('#hand_pose_hands .finger-pose-hand')).toHaveCount(1);
    await expect.poll(()=>sends.length).toBeGreaterThan(0);
    await page.locator('#hand_scene_setup > summary').click();
    await expect(page.locator('#scene_add')).toBeEnabled();
    await page.locator('#scene_x').fill('0.9');await page.locator('#scene_y').fill(String(sends.at(-1).ray.origin.y));
    await page.locator('#scene_width').fill('0.1');await page.locator('#scene_height').fill('0.3');
    await page.locator('#scene_add').click();await page.locator('#scene_calibrated').check();
    await page.locator('#scene_left').fill('-4');await page.locator('#scene_right').fill('4');await page.locator('#scene_save').click();
    await expect(page.locator('#scene_status')).toContainText('Saved scene revision 1');
    const scene=await(await request.get(path+'/hand-scene',{headers})).json(),target=scene.targets[0];
    const reference={id:target.id,revision:target.revision,frame:target.frame};
    const condition={target:reference,handedness:'any'};
    agreement.goal='Explain the registered display when I point at it';agreement.reactions.goal=agreement.goal;
    agreement.humanRole='Point at the display after accepting';agreement.agentRole='Explain the display once';
    delete agreement.checks[0].handPose;agreement.checks[0].eventType='obs.hand.pointing';agreement.checks[0].handPoint=condition;
    const rule=agreement.reactions.rules[0];delete rule.handPose;rule.eventType='obs.hand.pointing';rule.handPoint=condition;rule.action='Explain the display in one sentence';
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Point at the registered display to prepare, then accept.'}}});
    await say('When I point at the registered display, explain it once');
    expect((await memory())['coordination.draft']).toEqual(agreement);
    await frame(true);await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);
    await expect(page.locator('#scene_selection')).toHaveText('Projected selection: Display');
    await page.locator('#hand_scene_setup').screenshot({path:info.outputPath('configured-scene.png')});
    await delivery(false);await say('Start now');expect((await memory())['task.actions']).toBe(0);
    await delivery(true);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await delivery(false);await page.getByRole('button',{name:'Robot',exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    const action={kind:'point',effector:'left_hand',target:reference};
    await request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind:'POINT',value:action,silent:true}}});
    await say('Point at the registered display silently');
    await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('ACCEPTED');
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','released');
    expect((await memory())['coordination.agreement']).toEqual(agreement);expect((await memory())['task.actions']).toBe(1);
    const receipt=(await memory())['command.lastResult'];expect(receipt.handAction.action.resolved.position.x).toBeCloseTo(3.2);
    await page.locator('#scene_label').fill('Doorway');await page.locator('#scene_add').click();await page.locator('#scene_save').click();
    await expect(page.locator('#scene_status')).toContainText('Saved scene revision 2');
    await expect(page.locator('#scene_selection')).toContainText('Ambiguous projection: Display, Doorway');
    const ambiguous={...sends.at(-1),observationId:'ambiguous-'+Date.now(),ts:new Date().toISOString()};
    expect((await request.post(path+'/acknowledge',{headers,data:{type:'obs.hand.pointing',actor:'camera',kind:'observation',payload:JSON.stringify(ambiguous)}})).ok()).toBe(true);
    const pointEvents=(await(await request.get(path+'/eventhistory',{headers})).json()).filter(e=>e.type==='obs.hand.pointing');
    expect(JSON.parse(pointEvents.at(-1).payload).resolution.status).toBe('ambiguous');
    await page.locator('#hand_scene_setup').screenshot({path:info.outputPath('ambiguous-scene.png')});
    await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','preview');
    await expect(page.locator('#embodiment_summary')).toContainText('toward Display');
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('point-at-display.png')});
    await page.setViewportSize({width:390,height:1000});await page.locator('#embodiment_panel').screenshot({path:info.outputPath('point-at-display-390.png')});
    await section('sensed_signals');await page.locator('#hand_scene_setup > summary').click();
    await expect(page.locator('#scene_targets')).toContainText('Display');
    await page.locator('#hand_scene_setup').screenshot({path:info.outputPath('configured-scene-390.png')});
    // A new registration cannot redirect an old accepted reference.
    const changed={schemaVersion:1,expectedRevision:2,cameraFrame:null,transform:null,targets:[{key:'display',label:'Display',type:'virtual',position:{x:-3,y:2,z:0},extent:{x:.1,y:.1,z:0},expiresAt:null}]};
    expect((await request.post(path+'/hand-scene',{headers,data:changed})).ok()).toBe(true);
    await say('Point at that display silently again');await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('CLARIFY');
    expect((await memory())['coordination.agreement']).toEqual(agreement);
    writeFileSync(info.outputPath('scene-evidence.json'),JSON.stringify({scene,agreement,receipt},null,2));expect(errors).toEqual([]);
  }finally{await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');await request.delete('/__live-fixture/direct-command');}
});
