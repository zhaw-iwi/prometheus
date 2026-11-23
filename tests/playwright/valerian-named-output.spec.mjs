import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {synchronizeEmbodimentFrame} from './fixtures/embodiment-frame.mjs';
const signs=['fist','peace','open_palm','thumbs_up','thumbs_down','index_up','shaka','horns','middle_finger'];
const action=(sign,effector='right_hand')=>({motion:{handAction:{schemaVersion:1,action:{kind:'sign',sign,effector}}}});
test('named output renders nine signs, releases fresh actions and previews history through local MySQL',async({page,request},info)=>{
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true'||!process.env.PROMETHEUS_ADMIN_TOKEN,'Use the disposable MySQL runner');
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},code=Math.random().toString(36).slice(2,7).toUpperCase();
  const scope=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${scope.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination','core.live_multimodal']}});
  const headers={'X-Prometheus-Access-Code':code};
  const agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json(),path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const history=async()=>await(await request.get(path+'/eventhistory',{headers})).json();
  const say=async text=>{await page.getByTestId('text-input').fill(text);await page.getByTestId('send-text').click();};
  let limited;const errors=[];page.on('pageerror',e=>errors.push(e.message));
  try {
    await page.goto(`/valerian/?agentId=${agent.id}`);await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
    await expect(page.getByTestId('send-text')).toBeEnabled();await page.getByRole('button',{name:'Robot',exact:true}).click();
    const stage=page.getByTestId('embodiment-stage');await expect(stage).toHaveAttribute('data-ready','true');
    for(const [i,sign] of signs.entries()){
      await request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind:'HAND_SIGN',value:sign,effector:i%2?'right_hand':'both_hands',silent:true}}});
      await say(`Show ${sign.replaceAll('_',' ')} silently`);
      await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(i+1);
      await expect(stage).toHaveAttribute('data-hand-sign',sign);await expect(stage).toHaveAttribute('data-hand-phase','released');
    }
    const outputs=(await history()).filter(e=>e.type==='resp.behaviour_plan'&&JSON.parse(e.payload).motion?.handAction);
    expect(outputs).toHaveLength(signs.length);expect(outputs.every(e=>!JSON.parse(e.payload).speech)).toBe(true);
    expect((await memory())['task.actions']).toBeUndefined();expect((await memory())['coordination.agreement']).toBeUndefined();
    await page.reload();await expect(stage).toHaveAttribute('data-ready','true');await expect(stage).toHaveAttribute('data-hand-phase','preview');
    expect((await history()).filter(e=>e.type==='resp.behaviour_plan'&&JSON.parse(e.payload).motion?.handAction)).toHaveLength(signs.length);
    await page.evaluate(e=>handleBehaviourEnvelope(e,{delivery:'live',eventId:e.id}),outputs.at(-1));
    await expect(stage).toHaveAttribute('data-hand-phase','preview');
    for(const [width,theme] of [[1440,'light'],[1440,'dark'],[390,'light']]){
      await page.setViewportSize({width,height:1000});if(await page.locator('html').getAttribute('data-theme')!==theme)await page.getByTestId('cockpit-theme-toggle').click();
      await synchronizeEmbodimentFrame(page);await page.locator('#embodiment_panel').screenshot({path:info.outputPath(`named-output-${width}-${theme}.png`)});
    }
    // The same canonical action is produced by the accepted cue path.
    await request.delete('/__live-fixture/direct-command');
    const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/named-sign-agreement.json','utf8'));
    agreement.reactions.rules[0].action='Show peace silently with both hands';
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Prepare a thumbs up; then accept.'}}});
    await say('When I show thumbs up, show peace silently with both hands once, after preparation and acceptance');
    await expect.poll(async()=>(await memory())['coordination.draft']).toEqual(agreement);
    const observe=async id=>{const ts=new Date().toISOString();return request.post(path+'/acknowledge',{headers,data:{type:'obs.hand.signs',actor:'camera',kind:'observation',payload:JSON.stringify({schemaVersion:1,observationId:id,source:'browser-test',ts,frame:{id:'camera',revision:1,space:'camera_image',mirrored:false},hands:[{id:'one',handedness:'right',sign:'thumbs_up',confidence:.9}]})}});};
    expect((await observe('prep')).ok()).toBe(true);await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'START',agreement:null,reply:{}}});await say('Start now');
    await expect.poll(async()=>(await memory())['task.actions']).toBe(0);
    await expect.poll(async()=>Date.parse((await memory())['task.after'])<=Date.now()).toBe(true);
    expect((await observe('fresh')).ok()).toBe(true);await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await expect(stage).toHaveAttribute('data-hand-sign','peace');await expect(stage).toHaveAttribute('data-hand-phase','released');
    expect((await observe('fresh')).ok()).toBe(true);expect((await memory())['task.actions']).toBe(1);
    writeFileSync(info.outputPath('named-output-evidence.json'),JSON.stringify({history:await history(),memory:await memory()},null,2));
    limited=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.live_multimodal'}})).json();
    await request.post('/__live-fixture/direct-command',{data:{command:{intent:'ACTION',kind:'HAND_SIGN',value:'thumbs_up',silent:true}}});
    await page.goto(`/valerian/?agentId=${limited.id}`);await expect(page.getByTestId('send-text')).toBeEnabled();
    await say('Show thumbs up silently');
    const limitedMemory=async()=>Object.fromEntries((await(await request.get(`/demo/agents/${limited.id}/storage`,{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
    await expect.poll(async()=>(await limitedMemory())['command.lastResult']?.status).toBe('UNSUPPORTED');
    await expect(stage).not.toHaveAttribute('data-hand-sign','thumbs_up');
    expect((await(await request.get(`/demo/agents/${limited.id}/eventhistory`,{headers})).json()).some(e=>e.type==='resp.behaviour_plan'&&JSON.parse(e.payload).motion?.handAction)).toBe(false);
    expect(errors).toEqual([]);
  } finally {await request.delete('/__live-fixture/direct-command');await request.delete('/__live-fixture/coordination-proposal');await request.delete(path,{headers});if(limited)await request.delete(`/demo/agents/${limited.id}`,{headers});}
});
test('named pose front and side contact sheet uses the production hand rig',async({page},info)=>{
  await page.goto('/valerian/');await page.setViewportSize({width:1320,height:1660});
  await page.evaluate(async signs=>{
    const {RobotScene}=await import('/valerian/embodiment/robot.js');const {normalizePlan}=await import('/valerian/embodiment/model.js');
    document.body.replaceChildren();document.body.style.cssText='margin:0;background:#e8eeed;color:#172d34;font:18px sans-serif';
    const grid=document.createElement('main');grid.style.cssText='display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:12px';document.body.append(grid);
    for(const sign of signs)for(const angle of ['front','side']){
      const cell=document.createElement('section');cell.style.cssText='background:white;border-radius:12px;padding:8px;text-align:center';
      const title=document.createElement('div');title.textContent=sign.replaceAll('_',' ')+' / '+angle;const canvas=document.createElement('canvas');canvas.style.cssText='width:100%;height:360px';cell.append(title,canvas);grid.append(cell);
      const scene=new RobotScene(canvas);scene.resize(400,360);scene.camera.position.set(angle==='side'?5:0,1.5,8);scene.camera.lookAt(0,1.1,0);
      const pose=normalizePlan({motion:{handAction:{schemaVersion:1,action:{kind:'sign',sign,effector:'both_hands'}}}});
      scene.draw({pose,gesture:0,ambient:0,transition:1,handBlend:1},0);
      canvas.getContext('webgl2').finish();
      const snapshot=document.createElement('canvas');snapshot.width=canvas.width;snapshot.height=canvas.height;snapshot.style.cssText=canvas.style.cssText;
      const context=snapshot.getContext('2d');context.drawImage(canvas,0,0);
      const pixels=context.getImageData(0,0,snapshot.width,snapshot.height).data;
      if(pixels.filter((value,index)=>index%4===3&&value>0).length<1000)throw new Error('Empty pose rendering');
      cell.replaceChild(snapshot,canvas);scene.dispose();scene.renderer.forceContextLoss();
    }
  },signs);
  await page.screenshot({path:info.outputPath('named-pose-contact-sheet.png'),fullPage:true});
  expect(await page.locator('canvas').count()).toBe(signs.length*2);
});
