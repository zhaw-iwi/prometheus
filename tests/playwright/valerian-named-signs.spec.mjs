import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {handResult} from '../fixtures/hand-landmarks.mjs';

test('named hand emitter prepares, accepts and executes one fresh cue through local MySQL', async ({page,context,request},info)=>{
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true','Use the enabled disposable MySQL runner');
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code=Math.random().toString(36).slice(2,7).toUpperCase();
  const access=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code};
  const agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json();
  const path=`/demo/agents/${agent.id}`;
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/named-sign-agreement.json','utf8'));
  agreement.reactions.rules[0].action='Offer one welcome';
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
  const sends=[];page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/acknowledge')){const body=r.postDataJSON();if(body?.type==='obs.hand.signs')sends.push(JSON.parse(body.payload));}});
  const frame=async sign=>{const result=handResult(sign==='Thumb_Up'?{thumb:0,index:1,middle:1,ring:1,little:1}:{thumb:1,index:0,middle:0,ring:1,little:1},'Right');result.gestures=[[{categoryName:sign,score:.92}]];await page.evaluate(r=>{window.__namedResult=r;},result);};
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
    await say('Propose one welcome after preparing a right-hand thumbs up and accepting its next cue');
    expect((await memory())['coordination.draft']).toEqual(agreement);
    await section('sensor_toggles');await page.getByTestId('sensor-hand-enabled').check();
    await expect(page.getByTestId('hand-sign-status')).toHaveText('Model ready');
    await page.locator('#sensor_emit_enabled').check();await section('sensor_config');
    await page.locator('#emit_interval_ms').fill('500');
    await frame('Victory');await page.locator('#start_camera').click();await section('sensed_signals');
    await expect(page.getByTestId('hand-named-detection')).toContainText('peace');
    await expect.poll(()=>sends.length).toBeGreaterThan(0);
    expect((await memory())['coordination.evidence']).toEqual({});
    await frame('Thumb_Up');
    await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);
    await delivery(false);
    expect((await memory())['task.actions']).toBeUndefined();
    await say('Start now');expect((await memory())['task.actions']).toBe(0);
    await frame('Victory');await delivery(true);
    await expect(page.getByTestId('hand-named-detection')).toContainText('peace');
    await page.waitForTimeout(700);expect((await memory())['task.actions']).toBe(0);
    await frame('Thumb_Up');await expect.poll(async()=>(await memory())['task.actions']).toBe(1);
    await expect(page.getByTestId('hand-named-status')).toContainText('Delivered');
    expect((await memory())['coordination.agreement']).toEqual(agreement);
    for(const [width,theme] of [[1440,'light'],[1440,'dark'],[390,'light']]){
      await page.setViewportSize({width,height:1000});
      if(await page.locator('html').getAttribute('data-theme')!==theme)await page.getByTestId('cockpit-theme-toggle').click();
      await expect(page.getByTestId('hand-named-status')).toContainText('Delivered');
      await page.getByTestId('hand-sign-report').screenshot({path:info.outputPath(`named-signs-${width}-${theme}.png`)});
    }
    await delivery(false);
    const cue=sends.filter(s=>s.hands.some(h=>h.sign==='thumbs_up')).at(-1);
    expect((await request.post(path+'/acknowledge',{headers,data:{type:'obs.hand.signs',actor:'camera',kind:'observation',payload:JSON.stringify(cue)}})).ok()).toBe(true);
    expect((await memory())['task.actions']).toBe(1);
    writeFileSync(info.outputPath('named-sign-evidence.json'),JSON.stringify({actions:1,frames:sends,agreement},null,2));
    expect(errors).toEqual([]);
  }finally{await page.close();await request.delete(path,{headers});await request.delete('/__live-fixture/coordination-proposal');}
});
