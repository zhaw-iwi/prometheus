import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {handResult} from '../fixtures/hand-landmarks.mjs';

test('composed pose emitter prepares, accepts and executes one fresh two-hand cue through local MySQL', async ({page,context,request},info)=>{
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
  await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Shall we prepare this task?'}}});
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
  const sends=[];page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/acknowledge')){const body=r.postDataJSON();if(body?.type==='obs.hand.pose')sends.push(JSON.parse(body.payload));}});
  const frame=async good=>{
    const left=handResult({thumb:0,index:good!==false?0:1,middle:.5,ring:1,little:0},'Left');
    const right=handResult({thumb:1,index:1,middle:1,ring:.5,little:1},'Right');
    if(good===null)right.worldLandmarks[0][12]=null;
    for(const key of ['landmarks','worldLandmarks','handedness'])left[key].push(...right[key]);
    await page.evaluate(r=>{window.__namedResult=r;},left);
  };
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
    await say('Propose a silent custom pose after preparing the left index straight and right middle bent and accepting its next cue');
    expect((await memory())['coordination.draft']).toEqual(agreement);
    await section('sensor_toggles');await page.getByTestId('sensor-hand-enabled').check();
    await expect(page.getByTestId('hand-sign-status')).toHaveText('Model ready');
    await page.locator('#sensor_emit_enabled').check();await section('sensor_config');
    await page.locator('#emit_interval_ms').fill('500');
    await frame(false);await page.locator('#start_camera').click();await section('sensed_signals');
    await expect(page.locator('#hand_pose_hands .finger-pose-hand')).toHaveCount(2);
    await expect.poll(()=>sends.length).toBeGreaterThan(0);
    expect((await memory())['coordination.evidence']).toEqual({});
    await frame(true);
    await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);
    await delivery(false);
    await page.getByRole('button',{name:'Robot',exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    expect((await memory())['task.actions']).toBeUndefined();
    await say('Start now');expect((await memory())['task.actions']).toBe(0);
    await frame(false);await delivery(true);
    await expect(page.locator('#hand_pose_hands .finger-pose-hand')).toHaveCount(2);
    await page.waitForTimeout(700);expect((await memory())['task.actions']).toBe(0);
    await frame(null);await expect(page.locator('#hand_pose_hands')).toContainText('Unavailable');await page.locator('#hand_pose_report').screenshot({path:info.outputPath('composed-unavailable.png')});await page.waitForTimeout(700);expect((await memory())['task.actions']).toBe(0);
    await frame(true);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-sign','custom_pose');
    await expect(page.locator('#hand_pose_status')).toContainText('Emitted');
    expect((await memory())['coordination.agreement']).toEqual(agreement);
    for(const [width,theme] of [[1440,'light'],[1440,'dark'],[390,'light']]){
      await page.setViewportSize({width,height:1000});
      if(await page.locator('html').getAttribute('data-theme')!==theme)await page.getByTestId('cockpit-theme-toggle').click();
      await expect(page.locator('#hand_pose_status')).toContainText('Emitted');
      await page.getByTestId('hand-sign-report').screenshot({path:info.outputPath(`composed-poses-${width}-${theme}.png`)});
    }
    await delivery(false);
    const cue=sends.at(-1);
    expect((await request.post(path+'/acknowledge',{headers,data:{type:'obs.hand.pose',actor:'camera',kind:'observation',payload:JSON.stringify(cue)}})).ok()).toBe(true);
    expect((await memory())['task.actions']).toBe(1);
    writeFileSync(info.outputPath('composed-pose-evidence.json'),JSON.stringify({actions:1,frames:sends,agreement},null,2));
    await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','preview');
    expect((await memory())['coordination.agreement']).toEqual(agreement);
    const pose={kind:'pose',hands:[{side:'left',fingers:{thumb:0,index:.25,middle:.5,ring:.75,little:1}},{side:'right',fingers:{thumb:1,index:.75,middle:.5,ring:.25,little:0}}]};
    await request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind:'HAND_POSE',value:pose,silent:true}}});
    await say('Show the custom two-hand pose silently');
    await expect.poll(async()=>(await memory())['command.lastResult']?.kind).toBe('HAND_POSE');
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','released');
    expect((await memory())['task.actions']).toBe(1);expect((await memory())['coordination.agreement']).toEqual(agreement);
    const plans=(await(await request.get(path+'/eventhistory',{headers})).json()).filter(e=>e.type==='resp.behaviour_plan'&&JSON.parse(e.payload).motion?.handAction);
    expect(plans).toHaveLength(2);expect(plans.every(e=>!JSON.parse(e.payload).speech)).toBe(true);
    expect(JSON.parse(plans.at(-1).payload).motion.handAction.action).toEqual(pose);
    await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-phase','preview');
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('custom-direct-output-390.png')});
    expect(errors).toEqual([]);
  }finally{await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');await request.delete('/__live-fixture/direct-command');}
});

test('custom pose front and side contact sheet uses the production hand rig',async({page},info)=>{
  await page.goto('/valerian/');await page.setViewportSize({width:1320,height:1660});
  await page.evaluate(async sides=>{
    const {RobotScene}=await import('/valerian/embodiment/robot.js');const {normalizePlan}=await import('/valerian/embodiment/model.js');
    document.body.replaceChildren();document.body.style.cssText='margin:0;background:#e8eeed;color:#172d34;font:18px sans-serif';
    const grid=document.createElement('main');grid.style.cssText='display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:12px';document.body.append(grid);
    for(const side of sides)for(const angle of ['front','side']){
      const cell=document.createElement('section');cell.style.cssText='background:white;border-radius:12px;padding:8px;text-align:center';
      const title=document.createElement('div');title.textContent=side+' / '+angle;const canvas=document.createElement('canvas');canvas.style.cssText='width:100%;height:360px';cell.append(title,canvas);grid.append(cell);
      const scene=new RobotScene(canvas);scene.resize(400,360);scene.camera.position.set(angle==='side'?5:0,1.5,8);scene.camera.lookAt(0,1.1,0);
      const hands=[{side:'left',fingers:{thumb:0,index:.25,middle:.5,ring:.75,little:1}},{side:'right',fingers:{thumb:1,index:.75,middle:.5,ring:.25,little:0}}].filter(h=>side==='both'||h.side===side);
      const pose=normalizePlan({motion:{handAction:{schemaVersion:1,action:{kind:'pose',hands}}}});
      scene.draw({pose,gesture:0,ambient:0,transition:1,handBlend:1},0);
      canvas.getContext('webgl2').finish();
      const snapshot=document.createElement('canvas');snapshot.width=canvas.width;snapshot.height=canvas.height;snapshot.style.cssText=canvas.style.cssText;
      const context=snapshot.getContext('2d');context.drawImage(canvas,0,0);
      const pixels=context.getImageData(0,0,snapshot.width,snapshot.height).data;
      if(pixels.filter((value,index)=>index%4===3&&value>0).length<1000)throw new Error('Empty pose rendering');
      cell.replaceChild(snapshot,canvas);scene.dispose();scene.renderer.forceContextLoss();
    }
  },['left','right','both']);
  await page.screenshot({path:info.outputPath('custom-pose-contact-sheet.png'),fullPage:true});
  expect(await page.locator('canvas').count()).toBe(6);
});
