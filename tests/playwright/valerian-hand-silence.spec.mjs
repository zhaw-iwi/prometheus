import {readFileSync,writeFileSync} from 'node:fs';
import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {hardware} from './fixtures/native-hardware.mjs';
import {openValerianSession} from './fixtures/valerian-session.mjs';
import {captureHandFrames} from './fixtures/hand-frames.mjs';
import {sign,fingers} from '../gptlive/hand_fixtures.mjs';

// Real MySQL, ingress, SSE, rig and Speech renderer; only providers/media are fixtures.
for(const mode of ['Text','Continuous'])test(`strict hand silence in ${mode} covers pose, sequence and prepared task`,async({page,context,request},info)=>{
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true'||!process.env.PROMETHEUS_ADMIN_TOKEN,'Use the disposable MySQL runner');
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN},code=Math.random().toString(36).slice(2,7).toUpperCase();
  const scope=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${scope.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code},agent=await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json();
  const path=`/demo/agents/${agent.id}`,speech=[],errors=[],evidence=[];
  const memory=async()=>Object.fromEntries((await(await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const history=async()=>await(await request.get(path+'/live/history',{headers})).json();
  page.on('pageerror',e=>errors.push(e.message));
  page.on('request',r=>{if(r.method()==='POST'&&/speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech/.test(r.url()))speech.push(r.url());});
  await hardware(context);
  await context.addInitScript(()=>{window.__handMediaPlays=[];document.addEventListener('playing',e=>{if(e.target instanceof HTMLAudioElement)__handMediaPlays.push(e.target.id);},true);});
  for(const url of ['https://cdn.jsdelivr.net/npm/@tensorflow*/**','https://cdn.jsdelivr.net/npm/face-api.js*/**'])await context.route(url,r=>r.fulfill({body:'',contentType:'application/javascript'}));
  const playback=()=>page.evaluate(()=>({media:__handMediaPlays.length,pcm:PrometheusTimings.snapshot().filter(t=>t.speech?.playbackStartSource).length}));
  const ready=async()=>{if(mode==='Continuous'){await expect(page.locator('#listen_status')).toHaveText('Listening');await expect.poll(()=>page.evaluate(()=>globalThis.__nativeMic?.enabled)).toBe(true);}};
  let ordinal=0;
  const say=async text=>{
    await ready();const response=page.waitForResponse(r=>r.url().includes(path+'/acknowledge')&&r.request().method()==='POST'&&r.request().postDataJSON()?.type==='obs.user_utterance');
    if(mode==='Text'){await page.getByTestId('text-input').fill(text);await page.getByTestId('send-text').click();}
    else await page.evaluate(({item,text})=>{for(const event of [{type:'input_audio_buffer.committed',item_id:item},{type:'conversation.item.input_audio_transcription.completed',item_id:item,transcript:text}])__nativeChannel.dispatchEvent(new MessageEvent('message',{data:JSON.stringify({event_id:crypto.randomUUID(),...event})}));},{item:'silent-'+(++ordinal),text});
    expect((await response).ok()).toBe(true);await ready();
  };
  const interpret=async(kind,value)=>request.post('/__live-fixture/direct-command',{data:{operation:'KEEP',agreement:null,reply:{},command:{intent:'ACTION',kind,value,silent:true}}});
  const pose={kind:'pose',hands:[{side:'left',fingers:{thumb:1,index:0,middle:1,ring:1,little:1}},{side:'right',fingers:{thumb:0,index:1,middle:1,ring:1,little:1}}]};
  const sequence={kind:'sequence',steps:Array.from({length:3},(_,i)=>({holdMs:1000,action:{kind:'pose',hands:[{side:'right',fingers:Object.fromEntries(fingers.map((f,j)=>[f,j<=i?0:1]))}]}}))};
  const silent=async(label,run)=>{
    const ids=new Set((await history()).map(e=>e.id)),before={speech:speech.length,playback:await playback()};
    await run();await page.waitForTimeout(2000);await ready();
    const events=(await history()).filter(e=>!ids.has(e.id)&&e.type==='resp.behaviour_plan').map(e=>({id:e.id,...JSON.parse(e.payload)}));
    expect(events.every(p=>!p.speech)).toBe(true);expect(events.filter(p=>p.motion?.handAction)).toHaveLength(1);
    expect(speech.length).toBe(before.speech);expect(await playback()).toEqual(before.playback);
    evidence.push({label,events,before,after:{speech:speech.length,playback:await playback()}});
  };
  try{
    await openValerianSession(page,`/valerian/?agentId=${agent.id}`,code);await page.getByRole('button',{name:'Robot',exact:true}).click();
    await page.evaluate(captureHandFrames);
    const stage=page.getByTestId('embodiment-stage');await expect(stage).toHaveAttribute('data-ready','true');
    if(mode==='Continuous'){await page.getByTestId('continuous-speech-tab').click();await page.getByTestId('toggle-transcription').click();await ready();}
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'KEEP',agreement:null,reply:{speech:'Ready for hand actions.'}}});
    await say('Say that you are ready');
    if(mode==='Continuous'){await expect.poll(()=>speech.length).toBeGreaterThan(0);await expect.poll(async()=>(await playback()).pcm).toBeGreaterThan(0);await ready();}
    await interpret('HAND_POSE',pose);
    await silent('custom pose',async()=>{await say('Show the custom two hand pose silently');await expect(stage).toHaveAttribute('data-hand-sign','custom_pose');await expect(stage).toHaveAttribute('data-hand-phase','released');expect((await memory())['command.lastResult'].silent).toBe(true);});
    const poseId=evidence.at(-1).events.find(p=>p.motion?.handAction).id;
    expect(await page.evaluate(id=>{
      const frame=window.__handFrames.get(id);
      return frame?.phase==='holding' && frame.pose.sign==='custom_pose' && frame.irisPixels>100 && frame.glError===0;
    },poseId)).toBe(true);
    await interpret('HAND_SEQUENCE',sequence);
    await silent('count sequence',async()=>{
      const captures=sequence.steps.map(async({action},index)=>{
        const step=index+1;
        const frame=await page.waitForFunction(step=>{
          const stage=document.querySelector('[data-testid="embodiment-stage"]');
          if(stage?.dataset.sequenceStep!==String(step)||stage.dataset.handPhase!=='holding')return false;
          const snapshot=ValerianEmbodiment.snapshot();
          return snapshot?.step===step&&snapshot.phase==='holding' ? snapshot : false;
        },step);
        const snapshot=await frame.jsonValue();
        expect(snapshot.pose.action).toEqual(action);
        writeFileSync(info.outputPath(`${mode}-count-${step}.png`),Buffer.from(snapshot.image.split(',')[1],'base64'));
      });
      await Promise.all([...captures,say('Count one to three silently')]);
      await expect(stage).toHaveAttribute('data-sequence-status','completed');await expect.poll(async()=>(await memory())['hand.execution.v1'].status).toBe('COMPLETED');
    });
    await request.delete('/__live-fixture/direct-command');
    const agreement=JSON.parse(readFileSync('src/test/resources/hand-interaction/named-sign-agreement.json','utf8'));agreement.reactions.rules[0].action='Show peace silently with your right hand';
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'PROPOSE',agreement,reply:{speech:'Prepare thumbs up, then accept.'}}});
    await request.post('/__live-fixture/hand-output',{data:{motion:{handAction:{schemaVersion:1,action:{kind:'sign',sign:'peace',effector:'right_hand'}}}}});
    await say('Propose a silent peace sign on my next thumbs up, once, after preparation and acceptance');
    const observe=async payload=>{const response=await request.post(path+'/acknowledge',{headers,data:{type:'obs.hand.signs',actor:'controlled-camera',kind:'observation',payload:JSON.stringify(payload)}});expect(response.ok()).toBe(true);};
    await observe(sign());await expect.poll(async()=>Object.keys((await memory())['coordination.evidence']||{}).length).toBe(1);await ready();
    await request.post('/__live-fixture/coordination-proposal',{data:{operation:'START',agreement:null,reply:{}}});await say('Start now');
    await expect.poll(async()=>Date.parse((await memory())['task.after'])<=Date.now()).toBe(true);await ready();
    expect((await memory())['task.actions']).toBe(0);
    await silent('prepared task including completion',async()=>{const cue=sign();await observe(cue);await observe(cue);await expect.poll(async()=>(await memory())['task.phase']).toBe('COMPLETED');await expect(stage).toHaveAttribute('data-hand-sign','peace');await expect(stage).toHaveAttribute('data-hand-phase','released');expect((await memory())['task.actions']).toBe(1);});
    expect((await memory())['coordination.agreement']).toEqual(agreement);expect(errors).toEqual([]);
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath(`${mode}-silent-peace.png`)});
    writeFileSync(info.outputPath('silence-evidence.json'),JSON.stringify({mode,evidence,history:await history(),memory:await memory()},null,2));
  }finally{await page.close();for(const fixture of ['direct-command','coordination-proposal','hand-output'])await request.delete('/__live-fixture/'+fixture);await request.delete(path,{headers});}
});
