import {test,expect} from './fixtures/offline-ui-assets.mjs';
import {trialCapture} from '../gptlive/trial_capture.mjs';
import {synchronizeEmbodimentFrame} from './fixtures/embodiment-frame.mjs';

const id='82828282-8282-4282-8282-828282828282';
const plan={speech:'Which option would you prefer?',nonVerbal:{gesture:'OPEN_QUESTION',facialExpression:{type:'warm_smile',intensity:0.7},
  gaze:{direction:'toward_user',focus:'speaker'},motion:{energy:0.6,stillness:0.5}},
  motion:{handSign:'scissor',effector:'right_hand'},display:{agentSign:'scissor',userSign:'paper',round:2,winner:'user',note:'Your turn'}};
const event=(payload,n=1)=>({id:`event-${n}`,type:'resp.behaviour_plan',createdDate:`2026-11-15T12:00:${String(n).padStart(2,'0')}Z`,payload:JSON.stringify(payload)});
async function fixture({context,page},options={}) {
  await context.route(/https:\/\/.*(?:tensorflow|face-api)/,route=>route.abort());
  await context.addInitScript(()=>{
    window.EventSource=class {static OPEN=1;readyState=1;listeners={};constructor(url){this.url=url;(window.testStreams??=[]).push(this);}
      addEventListener(type,fn){this.listeners[type]=fn;} close(){this.readyState=2;}};
  });
  const profile={supportedObservations:['obs.user_utterance'],supportedBehaviourModalities:['speech','nonVerbal','motion','display']};
  const agent={id,name:'Embodiment fixture',active:true,languageCode:'en',interactionProfile:profile};
  const json=body=>({status:200,contentType:'application/json',body:JSON.stringify(body)});
  let writes=0;
  await context.route('**/demo/**',route=>{
    const req=route.request(),path=new URL(req.url()).pathname;
    if(req.method()!=='GET'&&path!=='/demo/session')writes++;
    if(path==='/demo/session')return route.fulfill(json({accessCode:'ROBOT',agentTypes:[{key:'core.multimodal_behaviour',displayName:'Multimodal'}],agents:[agent]}));
    if(path==='/demo/agent-types')return route.fulfill(json([{key:'core.multimodal_behaviour',displayName:'Multimodal',interactionProfile:profile}]));
    if(path==='/demo/agents')return route.fulfill(json([agent]));
    if(path.endsWith('/info'))return route.fulfill(json(agent));
    if(path.endsWith('/eventhistory'))return route.fulfill(json(options.empty?[]:[event(plan)]));
    if(path.endsWith('/storage'))return route.fulfill(json([]));
    if(path.endsWith('/state'))return route.fulfill(json({name:'Conversation',innerNames:[]}));
    if(path.endsWith('/states'))return route.fulfill(json(['Conversation']));
    if(path.endsWith('/transcription/capabilities'))return route.fulfill(json({schemaVersion:1,sessionType:'transcription',model:'gpt-live-transcribe',settings:[],capabilities:{assistantOutput:false,inputTranscription:true}}));
    return route.fulfill({status:404,body:''});
  });
  await page.goto(`/valerian/?agentId=${id}`);
  await page.getByTestId('access-code-input').fill('ROBOT');await page.getByTestId('submit-access-code').click();
  if(!options.empty)await expect(page.getByTestId('speech-preview')).toHaveText(plan.speech);
  if(options.robot!==false)await robot(page);
  return ()=>writes;
}
async function robot(page) {
  await page.getByRole('button',{name:'Robot',exact:true}).click();
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
}
async function publish(page,p,n=2,delivery='live',extra={}) {
  await page.evaluate(({e,delivery,extra})=>handleBehaviourEnvelope(e,{delivery,eventId:e.id,...extra}),{e:event(p,n),delivery,extra});
}

for(const capture of [false,true]) test(`embodiment renders a minimal command after an initially empty history (capture ${capture})`,async({page,context},info)=>{
  const diagnostics=[];page.on('console',m=>{if(['warning','error'].includes(m.type()))diagnostics.push(m.text());});
  if(capture)await context.addInitScript(trialCapture);
  await page.emulateMedia({reducedMotion:'reduce'});await fixture({page,context},{empty:true});
  await page.evaluate(async(capture)=>{
    if(capture)await navigator.mediaDevices.getUserMedia({audio:true});
    const ctx=capture?__trial.context:new AudioContext();await ctx.resume();
    const source=ctx.createOscillator(),remote=ctx.createMediaStreamDestination();
    source.connect(remote);source.start();
    const audio=document.createElement('audio');audio.srcObject=remote.stream;document.body.append(audio);await audio.play();
  },capture);
  await publish(page,{nonVerbal:{gesture:'NONE'},motion:{handSign:'rock'}});
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-hand-sign','rock');
  const {irisPixels,glError}=await synchronizeEmbodimentFrame(page);expect(glError).toBe(0);
  expect(irisPixels,'The default eyes remain visible with a minimal hand command and active audio').toBeGreaterThan(100);
  await page.locator('#embodiment_panel').screenshot({path:info.outputPath('minimal-rock-empty-history.png')});
  await info.attach('graphics-diagnostics',{contentType:'application/json',body:Buffer.from(JSON.stringify(diagnostics))});
});

test('embodiment switches middle region only, persists preference and renders real WebGL',async({page,context},info)=>{
  const writes=await fixture({page,context},{robot:false});
  await expect(page.getByTestId('gesture-value')).toBeVisible();const before=writes();
  const latest=await page.getByTestId('latest-behaviour-event').textContent();await robot(page);
  await expect(page.getByTestId('gesture-value')).toBeHidden();await expect(page.getByTestId('speech-preview')).toHaveText(plan.speech);
  await expect(page.getByTestId('latest-behaviour-event')).toHaveText(latest);
  await expect(page.locator('#embodiment_summary')).toContainText('right hand: scissor');
  await expect(page.locator('#embodiment_display')).toContainText('User sign: paper');
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
  await page.locator('[data-column-panel="behaviour"]').screenshot({path:info.outputPath('robot-light.png')});
  await page.evaluate(()=>setTheme('dark'));
  await page.locator('[data-column-panel="behaviour"]').screenshot({path:info.outputPath('robot-dark.png')});
  await page.getByRole('button',{name:'Details',exact:true}).click();await expect(page.getByTestId('gesture-value')).toBeVisible();
  await robot(page);await page.reload();await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
  assertNoWrites();function assertNoWrites(){expect(writes()).toBe(before);}
});

test('embodiment animates fresh events once and preserves native, replay, reset semantics',async({page,context})=>{
  await fixture({page,context});const p={nonVerbal:{gesture:'EXPLAIN'},display:{text:'<img src=x onerror=alert(1)>'}};
  await publish(page,p);await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','true');
  await publish(page,p); // Duplicate identity must not re-admit the animation.
  await expect(page.locator('#embodiment_display')).toContainText('<img');expect(await page.locator('#embodiment_display img').count()).toBe(0);
  await page.getByRole('button',{name:'Details',exact:true}).click();await robot(page);
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
  await publish(page,{nonVerbal:{gesture:'POLITE'}},3,'replay');
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
  await page.evaluate(e=>handleBehaviourEnvelope({...e,provenance:{origin:'NATIVE'}},{eventId:e.id,delivery:'live'}),event({speech:'Native speech'},4));
  await expect(page.locator('#embodiment_summary')).toContainText('polite');
  await publish(page,{motion:{handSign:'Schere'},display:{agentSign:'unknown'}},5);
  await expect(page.locator('#embodiment_summary')).toContainText('right hand: scissor');
  await expect(page.getByTestId('agent-sign-label')).toHaveText('Schere');
  await page.locator('#open_diagnostics').click();await page.getByTestId('connect-agent').click();
  await expect(page.locator('#embodiment_summary')).toContainText('Connect an agent');
  await expect(page.getByTestId('speech-preview')).toHaveText('-');
});

test('embodiment reduced motion, all signs, narrow layout and maximized column',async({page,context},info)=>{
  await page.emulateMedia({reducedMotion:'reduce'});await fixture({page,context});
  for(const [index,sign] of ['rock','paper','scissor'].entries()) {
    await publish(page,{nonVerbal:{facialExpression:'attentive',gaze:'left'},motion:{handSign:sign,effector:'both_hands'}},index+2);
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath(`hands-${sign}.png`)});
  }
  await page.getByTestId('maximize-behaviour-column').click();
  await expect(page.locator('#column_expansion_body #embodiment_canvas')).toBeVisible();
  await page.locator('#embodiment_panel').screenshot({path:info.outputPath('robot-maximized.png')});
  await page.locator('#column_expansion_modal .btn-close').click();
  await page.setViewportSize({width:390,height:844});
  await page.locator('#embodiment_panel').screenshot({path:info.outputPath('robot-narrow.png')});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('embodiment detached view loads canonical history without animation',async({page,context},info)=>{
  await fixture({page,context});
  const popupPromise=page.waitForEvent('popup');await page.getByTestId('detach-behaviour-column').click();const popup=await popupPromise;
  await expect(popup.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
  await expect(popup.getByTestId('speech-preview')).toHaveText(plan.speech);
  await expect(popup.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
  await popup.locator('#embodiment_panel').screenshot({path:info.outputPath('robot-detached.png')});await popup.close();
});

test('embodiment WebGL failure falls back to unchanged details',async({page,context})=>{
  await context.addInitScript(()=>{const original=HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext=function(type,...args){return type.startsWith('webgl')?null:original.call(this,type,...args);};});
  await fixture({page,context},{robot:false});await page.getByRole('button',{name:'Robot',exact:true}).click();
  await expect(page.locator('#embodiment_status')).toContainText('unavailable');
  await expect(page.getByTestId('gesture-value')).toBeVisible();await expect(page.getByTestId('speech-preview')).toHaveText(plan.speech);
});
