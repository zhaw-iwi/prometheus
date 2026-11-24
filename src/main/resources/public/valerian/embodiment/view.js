import {EmbodimentState} from './model.js';

const preference='valerian.behaviourView';
const state=new EmbodimentState();
let root,stage,canvas,status,summary,display,renderer,loading,frame,resizeObserver;
let mode='details',failed=false,disposed=false;
let poseRendered=null;
const reduced=matchMedia('(prefers-reduced-motion: reduce)');
function stored() {try{return localStorage.getItem(preference)==='robot'?'robot':'details';}catch{return 'details';}}
function visible() {return mode==='robot'&&!failed&&!document.hidden&&stage?.getClientRects().length>0;}
function caption() {
  const p=state.pose;
  summary.textContent=p ? [p.gesture!=='NONE'?p.gesture.toLowerCase().replaceAll('_',' '):'',p.faceLabel,
    p.gazeLabel?`gaze ${p.gazeLabel.replaceAll('_',' ')}`:'',p.sign?`${p.hand.replaceAll('_',' ')}: ${p.sign.replaceAll('_',' ')}`:'',
    ...p.warnings].filter(Boolean).join(' · ')||'No nonverbal action in this plan.' : 'Connect an agent to see its behaviour.';
  if(p?.action?.kind==='pose')summary.textContent=p.action.hands.map(h=>`${h.side} hand: `+Object.entries(h.fingers)
    .map(([finger,bend])=>`${finger} ${bend<.4?'stretched':bend>.55?'bent':'neutral'}`).join(', ')
    +(h.relations?.length?'; '+h.relations.map(r=>r.kind==='orientation'?`${r.axis.replaceAll('_',' ')} ${r.direction.replaceAll('_',' ')}`
      :`${r.fingers.join('/')} ${r.kind.replaceAll('_',' ')} ${r.operator==='lt'?'below':'above'} ${r.value}`).join('; '):'')).join(' / ');
  if(p?.action?.kind==='point')summary.textContent=`${p.hand.replaceAll('_',' ')}: pointing `+(p.action.resolved?`toward ${p.action.resolved.label} (orange destination in the virtual scene)`:`${p.action.direction.replaceAll('_',' ')} from the robot's perspective`);
  if(p?.action?.kind==='sequence')summary.textContent=`Finite hand sequence: ${p.action.steps.length} steps. History is a static first-step preview; completion requires digital playback receipts.`;
  display.replaceChildren();display.hidden=p?.display==null;
  if(p?.display!=null) {
    const label=document.createElement('div');label.className='embodiment-display-label';label.textContent='Robot display';
    display.append(label);
    const d=p.display;
    if(typeof d==='string') {const text=document.createElement('p');text.textContent=d;display.append(text);}
    else {
      const names={title:'',summary:'',text:'',note:'',agentSign:'Robot sign',userSign:'User sign',round:'Round',winner:'Winner'};
      for(const [key,name] of Object.entries(names))if(d[key]!=null&&typeof d[key]!=='object') {
        const text=document.createElement(key==='title'?'strong':'p');text.textContent=(name?name+': ':'')+String(d[key]);display.append(text);
      }
      const details=document.createElement('details'),heading=document.createElement('summary'),content=document.createElement('pre');
      heading.textContent='Display data';content.textContent=JSON.stringify(d,null,2);details.append(heading,content);display.append(details);
      // Unknown payloads have no readable summary: expose all data immediately.
      details.open=display.children.length===2;
    }
  }
}
function draw(now=performance.now()) {
  if(!renderer||disposed)return;
  const sample=state.sample(now);stage.dataset.animating=String(sample.active);
  renderer.draw(sample,now);
  if (poseRendered && visible() && sample.active && sample.handPhase==='holding' && sample.pose?.action?.kind==='pose') {
    const report=poseRendered; poseRendered=null; report();
  }
  // Expose the normalized pose actually submitted to WebGL, alongside animation state.
  stage.dataset.handSign=sample.pose?.sign||'';
  stage.dataset.handPhase=sample.handPhase||'';
  status.textContent=sample.handPhase ? ({preview:'Static hand pose preview',entering:'Moving into hand pose',holding:'Holding hand pose',releasing:'Releasing hand pose',released:'Hand pose released'})[sample.handPhase] : '';
  stage.dataset.sequenceStep=sample.sequenceStep||'';stage.dataset.sequenceStatus=sample.sequenceStatus||'';
  if(sample.sequenceStatus)status.textContent=`Step ${sample.sequenceStep} of ${sample.sequenceTotal}: ${sample.sequenceStatus==='completed'?'Digital sequence playback reported complete':sample.sequenceStatus==='preview'?'Static history preview':sample.sequenceStatus==='unknown'?'Playback uncertain; no automatic repeat':sample.sequenceStatus}`;
  const button=root.querySelector('[data-testid="stop-hand-sequence"]');if(button){button.hidden=!sample.sequenceStatus;button.disabled=!['starting','running','waiting'].includes(sample.sequenceStatus);}
  stage.dataset.gesture=sample.pose?.gesture||'NONE';
}
function stop() {cancelAnimationFrame(frame);frame=null;}
function loop(now) {
  frame=null;if(!visible()||!state.pose||reduced.matches)return;
  if(state.pose.action&&!state.sample(now).active){draw(now);return;}
  // Cap redraws to 30fps; no work for hidden or Details views.
  if(!loop.last||now-loop.last>=32) {draw(now);loop.last=now;}
  frame=requestAnimationFrame(loop);
}
function refresh() {
  if(!root||disposed)return;
  const v=visible();state.visibility(v,reduced.matches);stop();
  if(renderer) {const box=stage.getBoundingClientRect();renderer.resize(box.width,box.height);draw();}
  if(v&&state.pose&&!reduced.matches)frame=requestAnimationFrame(loop);
}
async function load() {
  if(renderer||failed||loading||disposed)return loading;
  status.textContent='Loading robot…';
  loading=import('./robot.js').then(({RobotScene})=>{
    if(disposed)return;
    renderer=new RobotScene(canvas);stage.dataset.ready='true';status.textContent='';refresh();
  }).catch(()=>{
    failed=true;status.textContent='Robot view is unavailable in this browser. Behaviour details remain available.';
    setMode('details',false);root.querySelector('[data-embodiment-mode="robot"]').disabled=true;
  }).finally(()=>{loading=null;});
  return loading;
}
function setMode(next,persist=true) {
  mode=next==='robot'&&!failed?'robot':'details';root.dataset.behaviourView=mode;
  for(const button of root.querySelectorAll('[data-embodiment-mode]'))button.setAttribute('aria-pressed',String(button.dataset.embodimentMode===mode));
  stage.parentElement.hidden=mode!=='robot';
  if(persist)try{localStorage.setItem(preference,mode);}catch{/* private storage is optional */}
  refresh();if(mode==='robot')void load();
}
function storage(event) {if(event.key===preference)setMode(stored(),false);}
function init() {
  if(root)return;
  root=document.querySelector('[data-testid="behaviour-state-board"]');
  stage=document.getElementById('embodiment_stage');canvas=document.getElementById('embodiment_canvas');
  status=document.getElementById('embodiment_status');summary=document.getElementById('embodiment_summary');
  display=document.getElementById('embodiment_display');
  const cancel=document.createElement('button');cancel.type='button';cancel.className='btn btn-outline-secondary btn-sm mt-2';cancel.dataset.testid='stop-hand-sequence';cancel.textContent='Stop hand sequence';cancel.hidden=true;cancel.addEventListener('click',settle);status.after(cancel);
  state.onChange=()=>{draw();stop();if(visible()&&state.pose&&!reduced.matches)frame=requestAnimationFrame(loop);};
  for(const button of root.querySelectorAll('[data-embodiment-mode]'))button.addEventListener('click',()=>setMode(button.dataset.embodimentMode));
  canvas.addEventListener('webglcontextlost',event=>{
    event.preventDefault();failed=true;stop();renderer?.dispose();renderer=null;
    status.textContent='The robot graphics context was lost. Reload to retry; behaviour details remain available.';
    setMode('details',false);root.querySelector('[data-embodiment-mode="robot"]').disabled=true;
  });
  resizeObserver=new ResizeObserver(refresh);resizeObserver.observe(stage);
  document.addEventListener('visibilitychange',refresh);reduced.addEventListener('change',refresh);
  window.addEventListener('storage',storage);caption();setMode(stored(),false);
}
function receive(plan,options={}) {
  if(disposed)return;
  // No delayed replay while loading the renderer or hidden behind another tab.
  state.visible=visible()&&!!renderer;state.reduced=reduced.matches;
  state.receive(plan,options,performance.now());
  if(stage && !options.preserveNonSpeech) stage.dataset.handEvent = options.fromHistory ? '' : options.eventId || '';
  if (!options.preserveNonSpeech) poseRendered=state.started!==null && !options.fromHistory ? options.poseRendered : null;
  if(root){caption();draw();stop();if(visible()&&state.pose&&!reduced.matches)frame=requestAnimationFrame(loop);}
}
function clear() {poseRendered=null;state.clear();stop();if(stage)delete stage.dataset.handEvent;if(root){caption();draw();}}
function settle() {state.settle();draw();}
// Explicit diagnostic capture: redraw and read the same submitted GPU frame.
function snapshot(){
  if(!renderer||disposed)return null;const now=performance.now(),sample=state.sample(now);renderer.draw(sample,now);
  const gl=canvas.getContext('webgl2');gl.finish();
  return {step:sample.sequenceStep,phase:sample.handPhase,status:sample.sequenceStatus,pose:sample.pose,image:canvas.toDataURL('image/png')};
}
function dispose() {
  state.clear();disposed=true;stop();resizeObserver?.disconnect();renderer?.dispose();renderer=null;
  document.removeEventListener('visibilitychange',refresh);reduced.removeEventListener('change',refresh);window.removeEventListener('storage',storage);
}
globalThis.ValerianEmbodiment={init,receive,clear,settle,dispose,snapshot};
