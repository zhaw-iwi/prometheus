const fingers=['thumb','index','middle','ring','little'];
export function countReading(payload){
  const hands=payload?.hands||[];
  if(!hands.length)return 'No hands available for counting.';
  if(hands.length>2||hands.some(h=>!['left','right'].includes(h.handedness))||new Set(hands.map(h=>h.handedness)).size!==hands.length)return 'Count unavailable: hand sides are uncertain.';
  if(hands.some(h=>fingers.some(f=>!Number.isFinite(h.fingers?.[f])||!['straight','bent'].includes(h.fingerStates?.[f]))))return 'Count unavailable: a finger is neutral or unknown.';
  return hands.map(h=>`${h.handedness} hand: ${fingers.filter(f=>h.fingerStates[f]==='straight').length}`).join(' · ')+` · total ${hands.reduce((n,h)=>n+fingers.filter(f=>h.fingerStates[f]==='straight').length,0)} (${hands.length} reported ${hands.length===1?'hand':'hands'})`;
}
export function stepLabel(step){
  if(step.kind==='count')return `${step.count} extended ${step.count===1?'finger':'fingers'} · ${step.hands==='either'?'one unambiguous hand':step.hands+' hand'+(step.hands==='both'?'s':'')}`;
  if(step.kind==='sign')return `${step.condition.sign.replaceAll('_',' ')} · ${step.condition.handedness} hand`;
  if(step.kind==='point')return step.condition.target?'Point at the agreed target':`Point ${step.condition.direction.replaceAll('_',' ')} in the agreed frame`;
  return 'Custom finger pose · '+step.condition.hands.map(h=>h.handedness+' hand').join(' and ');
}
const messages={waiting_first:'Show the first step.',dwelling:'Keep this step steady.',waiting_step:'Show the next step.',awaiting_release:'Release the repeated pose with a known different pose.',released:'Now show the repeated step again.',unknown_evidence:'A required reading is unavailable; this step needs fresh steady evidence.',wrong_step:'Wrong stable step; start again from the first step.',timeout:'The attempt timed out; start again from the first step.',restart_required:'The source changed. Ask to restart preparation, or resume the paused task from its first step.',complete:'Whole sequence recognized. A held final pose cannot repeat it.'};
export function mountSequence(root){
  let state={enabled:false,items:[],progress:{},phase:''},count='No hands available for counting.',countAt=0;
  root.innerHTML='<summary>Hand sequences and counting</summary><p id="sequence_count" class="small mt-2" aria-live="polite"></p><p class="small text-muted">Counts require known straight or bent readings for every participating finger. Hidden fingers may still be inferred by the model.</p><div id="sequence_progress" aria-live="polite"></div>';
  const el=id=>root.querySelector('#sequence_'+id);
  function render(){
    el('count').textContent=state.enabled?(Date.now()-countAt>3000?'No fresh hands available for counting.':count):'Create a new Coordination instance for sequence input.';
    const container=el('progress');container.replaceChildren();
    if(!state.items.length){const p=document.createElement('p');p.className='small';p.textContent='Agree an ordered sequence to see preparation and task progress here.';container.append(p);return;}
    for(const item of state.items){
      const p=state.progress[item.slot]||{},index=p.index||0,total=item.sequence.steps.length,section=document.createElement('section');section.className='small mb-3';
      const heading=document.createElement('strong');heading.textContent=`${item.slot.startsWith('prep-')?'Preparation':'Task'}: ${index} of ${total} steps`;section.append(heading);
      const meter=document.createElement('progress');meter.max=total;meter.value=index;meter.className='d-block w-100 mt-1';meter.setAttribute('aria-label','Recognized sequence steps');section.append(meter);
      const list=document.createElement('ol');list.className='mt-2 mb-1';item.sequence.steps.forEach((step,i)=>{const li=document.createElement('li');li.textContent=stepLabel(step);li.dataset.state=i<index?'complete':i===index?'current':'waiting';if(i===index)li.style.fontWeight='600';list.append(li);});section.append(list);
      const status=document.createElement('p');status.className='mb-1';status.dataset.testid='sequence-status';
      const expired=index<total&&p.startedAt&&(Date.now()-Date.parse(p.startedAt)>item.sequence.maximumDurationMs||Date.now()-Date.parse(p.progressAt)>item.sequence.maximumGapMs);
      status.textContent=state.phase==='PAUSED'?'Task paused. Resume explicitly to restart the sequence from step one.':expired?messages.timeout:messages[p.status||'waiting_first']||'Waiting for fresh evidence.';section.append(status);container.append(section);
    }
  }
  const timer=setInterval(()=>{if(!document.hidden)render();},500);
  const api={update(value){state=value;render();},pose(payload){count=countReading(payload);countAt=payload?Date.now():0;render();},clear(){state={enabled:false,items:[],progress:{},phase:''};countAt=0;count='No hands available for counting.';render();},dispose(){clearInterval(timer);}};render();return api;
}
