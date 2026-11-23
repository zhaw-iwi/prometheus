import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {pointingResult} from '../fixtures/hand-landmarks.mjs';

test('detected cup selection prepares a cue and maps output while expired labels stay retired',async({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN||process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the enabled disposable MySQL runner');
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(r.url().includes('/acknowledge')&&!r.ok())errors.push('Acknowledgement HTTP '+r.status());});
  const access=await(await request.post('/admin/access-codes',{headers:{'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},data:{code:Math.random().toString(36).slice(2,7).toUpperCase(),enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:{'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':access.code},agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json(),path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  await context.addInitScript(()=>{
    window.__objects=[{class:'cup',score:.94,bbox:[540,130,70,240]}];
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
    await page.locator('#detected_target_setup > summary').click();await expect(page.locator('#target_status')).toContainText('1 fresh projected targets');
    await page.locator('#target_select').selectOption({index:1});await page.locator('#target_label').fill('My cup');await page.locator('#target_destination').selectOption({index:1});await page.locator('#target_save').click();await expect(page.locator('#target_saved')).toContainText('virtual counterpart saved');
    const saved=await memory(),reference=saved['hand.scene.selected.v1'];expect(saved['hand.scene.bindings.v1'][0].label).toBe('My cup');
    const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/composed-pose-agreement.json','utf8'));
    agreement.goal='Acknowledge when I point at my cup';agreement.reactions.goal=agreement.goal;agreement.humanRole='Point at the selected cup';agreement.agentRole='Acknowledge once';
    const condition={target:reference,handedness:'any'};delete agreement.checks[0].handPose;agreement.checks[0].eventType='obs.hand.pointing';agreement.checks[0].handPoint=condition;const rule=agreement.reactions.rules[0];delete rule.handPose;rule.eventType='obs.hand.pointing';rule.handPoint=condition;rule.action='Acknowledge the selected cup';
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Point at your cup to prepare, then accept.'}}});await say('When I point at my cup, acknowledge it once');expect((await memory())['coordination.draft']).toEqual(agreement);
    await pose(0);await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);await expect(page.locator('#target_selection')).toHaveText('Projected selection: My cup');
    await pose(180);await expect(page.locator('#target_selection')).toHaveText('No resolved projected target.');await say('Start now');expect((await memory())['task.actions']).toBe(0);await pose(0);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await page.locator('#detected_target_setup').screenshot({path:info.outputPath('selected-cup.png')});
    await request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind:'POINT',value:{kind:'point',effector:'left_hand',target:reference},silent:true}}});
    await page.getByRole('button',{name:'Robot',exact:true}).click();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    await say('Point at my cup silently');await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('ACCEPTED');const receipt=(await memory())['command.lastResult'];expect(receipt.handAction.action.resolved.position).toEqual({x:3,y:2,z:0});expect((await memory())['coordination.agreement']).toEqual(agreement);
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','released');
    // Two aligned boxes remain ambiguous even when one has a label.
    await page.evaluate(()=>{window.__objects.push({class:'bottle',score:.93,bbox:[450,130,60,240]});});await expect(page.locator('#target_selection')).toContainText('Ambiguous projection');await page.locator('#detected_target_setup').screenshot({path:info.outputPath('ambiguous-items.png')});
    await page.evaluate(()=>{window.__objects=[];});await expect(page.locator('#target_saved')).toContainText('expired or changed');await say('Point at my cup silently again');await expect.poll(async()=>(await memory())['command.lastResult']?.status).toBe('CLARIFY');
    await page.evaluate(()=>{window.__objects=[{class:'cup',score:.94,bbox:[540,130,70,240]}];});await expect(page.locator('#target_select option')).toHaveCount(2);expect(await page.locator('#target_select option').last().textContent()).toBe('cup 1');expect(await page.locator('#target_select').inputValue()).toBe('');
    await page.setViewportSize({width:390,height:1000});await page.locator('#detected_target_setup').screenshot({path:info.outputPath('expired-label-390.png')});
    await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','preview');await expect(page.locator('#embodiment_summary')).toContainText('toward My cup');await page.locator('#embodiment_panel').screenshot({path:info.outputPath('point-at-cup-history-390.png')});
    writeFileSync(info.outputPath('dynamic-evidence.json'),JSON.stringify({reference,agreement,receipt},null,2));expect(errors).toEqual([]);
  }finally{await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');await request.delete('/__live-fixture/direct-command');}
});
