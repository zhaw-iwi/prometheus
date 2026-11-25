import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {pointingResult} from '../fixtures/hand-landmarks.mjs';
import {openValerianSession} from './fixtures/valerian-session.mjs';
import {hardware} from './fixtures/native-hardware.mjs';
import {captureHandFrames} from './fixtures/hand-frames.mjs';
import {point, ref, sceneConfig, targets} from '../gptlive/hand_fixtures.mjs';
import {startObservationPumps} from '../gptlive/hand_observation_pump.mjs';

test('detected cup selection prepares a cue and maps output while expired labels stay retired',async({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN||process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the enabled disposable MySQL runner');
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const captures=[];page.on('response',async r=>{
    if(r.ok()&&r.request().method()==='POST'&&r.url().includes('/acknowledge')&&r.request().postDataJSON()?.type==='obs.scene.targets'){
      const body=await r.json();if(body.sceneCapture)captures.push(JSON.parse(body.sceneCapture));
    }
  });
  page.on('response',r=>{if(r.url().includes('/acknowledge')&&!r.ok())errors.push('Acknowledgement HTTP '+r.status());});
  const access=await(await request.post('/admin/access-codes',{headers:{'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},data:{code:Math.random().toString(36).slice(2,7).toUpperCase(),enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:{'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':access.code},agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json(),path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  await context.addInitScript(()=>{
    window.__objects=[{class:'cup',score:.94,bbox:[540,130,70,240]},{class:'person',score:.9,bbox:[100,0,300,480]}];
    window.cocoSsd={load:async()=>({detect:async()=>window.__objects,dispose(){}})};
    navigator.mediaDevices.getUserMedia=async()=>{const c=document.createElement('canvas');c.width=640;c.height=480;const x=c.getContext('2d');setInterval(()=>{x.fillStyle='#244f55';x.fillRect(0,0,640,480);x.fillStyle='#eee0be';x.fillRect(540,130,70,240);},50);return c.captureStream(15);};
  });
  await context.route('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35',route=>route.fulfill({contentType:'application/javascript',headers:{'Access-Control-Allow-Origin':'*'},body:`export const FilesetResolver={forVisionTasks:async()=>({})};export const GestureRecognizer={createFromOptions:async()=>({recognizeForVideo:()=>window.__namedResult||{landmarks:[],gestures:[]},close(){}})};`}));
  for(const url of ['https://cdn.jsdelivr.net/npm/@tensorflow*/**','https://cdn.jsdelivr.net/npm/face-api.js*/**'])await context.route(url,r=>r.fulfill({body:'',contentType:'application/javascript'}));
  const section=async id=>{await expect(page.locator('#sensing_accordion .collapsing')).toHaveCount(0);if(!(await page.locator('#'+id).evaluate(e=>e.classList.contains('show'))))await page.locator(`[data-bs-target="#${id}"]`).click();await expect(page.locator('#'+id)).toHaveClass(/\bshow\b/);};
  const say=async text=>{const response=page.waitForResponse(r=>r.url().includes(path+'/acknowledge')&&r.request().method()==='POST'&&r.request().postDataJSON()?.type==='obs.user_utterance');await page.getByTestId('text-input').fill(text);await page.getByTestId('send-text').click();expect((await response).ok()).toBe(true);};
  const pose=async angle=>page.evaluate(result=>{window.__namedResult=result;},pointingResult(angle,'Right',4/3));
  try{
    await page.goto(`/valerian/?agentId=${agent.id}`);await page.getByTestId('access-code-input').fill(access.code);await page.getByTestId('submit-access-code').click();await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');await expect(page.locator('#observation_clock_status')).toHaveText('Sensor timing ready');
    await section('sensor_toggles');await page.locator('#sensor_social_enabled').check();await page.getByTestId('sensor-hand-enabled').check();await expect(page.getByTestId('hand-sign-status')).toHaveText('Model ready');await page.locator('#sensor_emit_enabled').check();await section('sensor_config');await page.locator('#emit_interval_ms').fill('500');
    await pose(180);await page.locator('#start_camera').click();await section('sensed_signals');
    await page.locator('#hand_scene_setup > summary').click();await page.locator('#scene_type').selectOption('virtual');await page.locator('#scene_label').fill('Cup pedestal');await page.locator('#scene_x').fill('3');await page.locator('#scene_y').fill('2');await page.locator('#scene_add').click();await page.locator('#scene_save').click();await expect(page.locator('#scene_status')).toContainText('Saved scene revision 1');
    await page.locator('#detected_target_setup > summary').click();await expect(page.locator('#target_status')).toContainText('2 fresh projected targets');
    await page.locator('#target_select').selectOption({index:1});await page.locator('#target_label').fill('My cup');await page.locator('#target_destination').selectOption({index:1});await page.locator('#target_save').click();await expect(page.locator('#target_saved')).toContainText('virtual counterpart saved');
    const saved=await memory(),reference=saved['hand.scene.selected.v1'];expect(saved['hand.scene.bindings.v1'][0].label).toBe('My cup');
    const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/composed-pose-agreement.json','utf8'));
    agreement.goal='Acknowledge when I point at my cup';agreement.reactions.goal=agreement.goal;agreement.humanRole='Point at the selected cup';agreement.agentRole='Acknowledge once';
    const condition={target:reference,handedness:'any'};delete agreement.checks[0].handPose;agreement.checks[0].eventType='obs.hand.pointing';agreement.checks[0].handPoint=condition;const rule=agreement.reactions.rules[0];delete rule.handPose;rule.eventType='obs.hand.pointing';rule.handPoint=condition;rule.action='Acknowledge the selected cup';
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Point at your cup to prepare, then accept.'}}});await say('When I point at my cup, acknowledge it once');expect((await memory())['coordination.draft']).toEqual(agreement);
    await pose(0);await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);await expect(page.locator('#target_selection')).toHaveText('Projected selection: My cup');await expect(page.locator('#target_source')).toContainText('excluded as the projected source');
    await pose(180);await expect(page.locator('#target_selection')).toHaveText('No resolved projected target.');await say('Start now');expect((await memory())['task.actions']).toBe(0);await pose(0);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await page.locator('#detected_target_setup').screenshot({path:info.outputPath('selected-cup.png')});
    await request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind:'POINT',value:{kind:'point',effector:'left_hand',target:reference},silent:true}}});
    await page.getByRole('button',{name:'Robot',exact:true}).click();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    const countBefore=(await(await request.get('/__live-fixture/inference')).json()).count;
    await request.post('/__live-fixture/inference/hold');const pending=say('Point at my cup silently');
    try {
      await expect.poll(async()=>(await(await request.get('/__live-fixture/inference')).json()).count).toBeGreaterThan(countBefore);
      const first=captures.length;await page.waitForTimeout(3800); // Cross the target TTL with the real source clock.
      expect(captures.length-first).toBeGreaterThanOrEqual(4);
      await expect(page.locator('#target_status')).toContainText('2 fresh projected targets');
      await expect(page.locator('#target_saved')).not.toContainText('expired');
      await page.locator('#detected_target_setup').screenshot({path:info.outputPath('fresh-during-inference.png')});
    } finally {await request.post('/__live-fixture/inference/release');}
    await pending;await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('ACCEPTED');const receipt=(await memory())['command.lastResult'];expect(receipt.handAction.action.resolved.position).toEqual({x:3,y:2,z:0});expect((await memory())['coordination.agreement']).toEqual(agreement);
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','released');
    // Two aligned boxes remain ambiguous even when one has a label.
    await page.evaluate(()=>{window.__objects.push({class:'bottle',score:.93,bbox:[450,130,60,240]});});await expect(page.locator('#target_selection')).toContainText('Ambiguous projection');await page.locator('#detected_target_setup').screenshot({path:info.outputPath('ambiguous-items.png')});
    await page.evaluate(()=>{window.__objects=[];});await expect(page.locator('#target_saved')).toContainText('expired or changed');await say('Point at my cup silently again');await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('CLARIFY');
    await page.evaluate(()=>{window.__objects=[{class:'cup',score:.94,bbox:[540,130,70,240]},{class:'person',score:.9,bbox:[100,0,300,480]}];});await expect(page.locator('#target_select option')).toHaveCount(3);expect(await page.locator('#target_select option').nth(1).textContent()).toBe('cup 1');expect(await page.locator('#target_select').inputValue()).toBe('');
    await page.setViewportSize({width:390,height:1000});await page.locator('#detected_target_setup').screenshot({path:info.outputPath('expired-label-390.png')});
    await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','preview');await expect(page.locator('#embodiment_summary')).toContainText('toward My cup');await page.locator('#embodiment_panel').screenshot({path:info.outputPath('point-at-cup-history-390.png')});
    writeFileSync(info.outputPath('dynamic-evidence.json'),JSON.stringify({reference,agreement,receipt},null,2));expect(errors).toEqual([]);
  }finally{await request.post('/__live-fixture/inference/release');await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');await request.delete('/__live-fixture/direct-command');}
});

test('selected person point and removal reach the Live transport for their own turns',async({page,context,request},info)=>{
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true'||!process.env.PROMETHEUS_ADMIN_TOKEN,'Use the enabled disposable fixture runner');
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code=Math.random().toString(36).slice(2,7).toUpperCase();
  const access=await (await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code};
  const agent=await (await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json();
  const path=`/demo/agents/${agent.id}`;
  const get=async route=>(await request.get(route,{headers})).json();
  const post=async(route,data)=>{
    const response=await request.post(route,{headers,data});
    expect(response.ok(),route+' HTTP '+response.status()).toBe(true);
    return response;
  };
  const observe=(type,payload)=>post(path+'/acknowledge?profile=full_plan',{kind:'observation',actor:'controlled-camera',type,payload:JSON.stringify(payload)});
  let person=true,pump,sceneReceipt,providerId;
  const refreshScene=async()=>{
    const capture=await observe('obs.scene.targets',targets({person}));
    sceneReceipt=JSON.parse((await capture.json()).sceneCapture);
  };
  const history=()=>get(path+'/live/history');
  const handOutputs=events=>events.filter(e=>{
    if(e.type!=='resp.behaviour_plan')return false;
    const motion=JSON.parse(e.payload).motion;
    return motion?.handAction||motion?.handSign;
  });
  const ask=async(text,status,expectedFacts)=>{
    await post('/__live-fixture/speak/'+providerId,{speaker:'USER',text});
    let userId,receipt,messages;
    await expect.poll(async()=>{
      const events=await history();
      const index=events.findIndex(e=>e.type==='obs.user_utterance'&&e.payload===text);
      if(index<0)return false;
      userId=events[index].id;
      const values=await get(path+'/storage');
      const last=values.find(v=>v.key==='command.lastResult');
      receipt=last&&JSON.parse(last.value);
      if(receipt?.sourcePosition!==index||receipt.status!==status)return false;
      const {commands}=await get('/__live-fixture/latest');
      messages=commands.map(JSON.parse).filter(c=>c.type==='session.thinking.append'&&c.content?.includes('COMMAND RESULT for user turn '+userId));
      return messages.some(c=>c.content.includes(expectedFacts));
    },{timeout:20000}).toBe(true);
    return {userId,receipt,messages};
  };
  await hardware(context);
  try{
    await openValerianSession(page,`/valerian/?agentId=${agent.id}`,code);
    await page.evaluate(captureHandFrames);
    await page.getByRole('button',{name:'Robot',exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    await page.getByTestId('gptlive-tab').click();
    await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    providerId=(await get('/__live-fixture/latest')).providerId;
    await observe('obs.hand.pointing',point(null));
    const scene=await (await post(path+'/hand-scene',sceneConfig(true))).json();
    await refreshScene();
    const selected=ref(sceneReceipt.targets.find(t=>t.id.endsWith(':visitor')));
    pump=startObservationPumps([refreshScene]);
    await post(path+'/hand-scene/binding',{target:selected,label:'Visitor',destination:ref(scene.targets[1])});
    await post('/__live-fixture/direct-command',{operation:'KEEP',agreement:null,reply:{},
      command:{intent:'ACTION',kind:'POINT',value:{kind:'point',effector:'right_hand',target:selected},silent:false}});
    const first=await ask('Point at the selected person labelled Visitor with your right hand.','ACCEPTED','This direct point needs no agreement change or consent.');
    expect(first.receipt.handAction.action.resolved.position).toEqual({x:-3,y:2,z:0});
    const outputs=handOutputs(await history());
    expect(outputs).toHaveLength(1);
    const removedAt=Date.now();
    person=false;
    await expect.poll(()=>Date.parse(sceneReceipt.ts)>=removedAt
      &&sceneReceipt.targets.some(t=>t.id.endsWith(':cup-a'))
      &&!sceneReceipt.targets.some(t=>t.id===selected.id)).toBe(true);
    const removal=sceneReceipt;
    const repeat=await ask('Point at that same Visitor again.','CLARIFY','The selected target was absent from the current scene.');
    expect(repeat.receipt.clarification).toBe('selected_target_absent');
    expect(repeat.messages.some(m=>m.content.includes('Ask for a fresh target selection.'))).toBe(true);
    expect(handOutputs(await history()).map(e=>e.id)).toEqual([outputs[0].id]);
    const frame=await page.waitForFunction(id=>window.__handFrames.get(id),outputs[0].id);
    const {image,...render}=await frame.jsonValue();
    await frame.dispose();
    expect(render.pose.hand).toBe('right_hand');
    expect(render.pose.action.target).toEqual(selected);
    expect(render.glError).toBe(0);
    expect(render.irisPixels).toBeGreaterThan(100);
    writeFileSync(info.outputPath('selected-visitor.png'),Buffer.from(image.split(',')[1],'base64'));
    writeFileSync(info.outputPath('point-and-removal.json'),JSON.stringify({first,repeat,removal,render,handEventId:outputs[0].id},null,2));
  }finally{
    if(pump){pump.running=false;await pump.done;}
    await page.close();await request.delete('/__live-fixture/direct-command');await request.delete(path,{headers});
  }
  expect(pump.error).toBeNull();
});
