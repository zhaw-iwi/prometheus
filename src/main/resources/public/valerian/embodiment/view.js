import {EmbodimentState} from './model.js';

const preference='valerian.behaviourView';
const state=new EmbodimentState();
let root,stage,canvas,status,summary,display,renderer,loading,frame,resizeObserver;
let mode='details',failed=false,disposed=false;
const reduced=matchMedia('(prefers-reduced-motion: reduce)');
function stored() {try{return localStorage.getItem(preference)==='robot'?'robot':'details';}catch{return 'details';}}
function visible() {return mode==='robot'&&!failed&&!document.hidden&&stage?.getClientRects().length>0;}
function caption() {
  const p=state.pose;
  summary.textContent=p ? [p.gesture!=='NONE'?p.gesture.toLowerCase().replaceAll('_',' '):'',p.faceLabel,
    p.gazeLabel?`gaze ${p.gazeLabel.replaceAll('_',' ')}`:'',p.sign?`${p.hand.replaceAll('_',' ')}: ${p.sign}`:'',
    ...p.warnings].filter(Boolean).join(' · ')||'No nonverbal action in this plan.' : 'Connect an agent to see its behaviour.';
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
}
function stop() {cancelAnimationFrame(frame);frame=null;}
function loop(now) {
  frame=null;if(!visible()||!state.pose||reduced.matches)return;
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
function receive(plan,options) {
  if(disposed)return;
  // No delayed replay while loading the renderer or hidden behind another tab.
  state.visible=visible()&&!!renderer;state.reduced=reduced.matches;
  state.receive(plan,options,performance.now());
  if(root){caption();draw();stop();if(visible()&&state.pose&&!reduced.matches)frame=requestAnimationFrame(loop);}
}
function clear() {state.clear();stop();if(root){caption();draw();}}
function settle() {state.settle();draw();}
function dispose() {
  disposed=true;stop();resizeObserver?.disconnect();renderer?.dispose();renderer=null;
  document.removeEventListener('visibilitychange',refresh);reduced.removeEventListener('change',refresh);window.removeEventListener('storage',storage);
}
globalThis.ValerianEmbodiment={init,receive,clear,settle,dispose};
