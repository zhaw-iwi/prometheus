const reference=t=>({id:t.id,revision:t.revision,frame:t.frame});
const same=(a,b)=>!!a&&!!b&&a.id===b.id&&a.revision===b.revision&&a.frame?.id===b.frame?.id&&a.frame?.revision===b.frame?.revision&&a.frame?.space===b.frame?.space&&a.frame?.mirrored===b.frame?.mirrored;
export function mountTargets(root,save){
  let snapshot={},configured={},bindings=[],selected=null,enabled=false,lastOptions='',lastDestinations='',lastPoint=null;
  root.innerHTML=`<summary>Detected people and items</summary><p class="small mt-2">Enable Social sensing to retain people, cups, bottles and books. Tracks expire after three seconds and uncertain crossings lose their references. These are projected boxes, not identity or room depth.</p>
    <p id="target_status" class="small" role="status">No fresh detected targets.</p>
    <label class="small d-block">Current target<select id="target_select" class="form-select form-select-sm"></select></label>
    <label class="small d-block mt-2">Your label<input id="target_label" class="form-control form-control-sm" maxlength="80" placeholder="e.g. the red cup"></label>
    <label class="small d-block mt-2">Virtual counterpart for robot output<select id="target_destination" class="form-select form-select-sm"></select></label>
    <p class="small text-muted">Register a virtual destination above to demonstrate pointing. A camera box alone cannot locate the item in the room. Labels apply only to this temporary track.</p>
    <button id="target_save" type="button" class="btn btn-sm btn-primary">Select and save label</button>
    <p id="target_saved" class="small mt-2" role="status"></p><p id="target_selection" class="small" role="status">No resolved projected target.</p><p id="target_source" class="small text-muted">Source person unresolved.</p>`;
  const el=name=>root.querySelector('#target_'+name),fresh=()=> (snapshot.targets||[]).filter(t=>Date.parse(t.expiresAt)>Date.now());
  function render(){
    const targets=fresh(),options=targets.map((t,i)=>{const binding=bindings.find(b=>same(b.target,reference(t)));return{key:JSON.stringify(reference(t)),label:binding?.label||`${t.type} ${i+1}`};});
    const signature=JSON.stringify(options);
    if(signature!==lastOptions){const prior=el('select').value;el('select').replaceChildren(new Option('Choose a current target',''),...options.map(o=>new Option(o.label,o.key)));if(options.some(o=>o.key===prior))el('select').value=prior;else if(selected&&options.some(o=>o.key===JSON.stringify(selected)))el('select').value=JSON.stringify(selected);lastOptions=signature;}
    const destinations=(configured.targets||[]).filter(t=>t.type==='virtual'&&(!t.expiresAt||Date.parse(t.expiresAt)>Date.now()));const signature2=JSON.stringify(destinations.map(t=>[reference(t),t.label]));
    if(signature2!==lastDestinations){const prior=el('destination').value;el('destination').replaceChildren(new Option('Input selection only (no output mapping)',''),...destinations.map(t=>new Option(t.label,JSON.stringify(reference(t)))));if(destinations.some(t=>JSON.stringify(reference(t))===prior))el('destination').value=prior;lastDestinations=signature2;}
    el('status').textContent=enabled?(targets.length?`${targets.length} fresh projected targets. Moving or vanished items may need selection again.`:'No fresh detected targets. Enable Social sensing and observation delivery.'):'Create a new Coordination instance for dynamic targets.';
    el('save').disabled=!enabled||!el('select').value||!el('label').value.trim();
    if(selected&&!targets.some(t=>same(reference(t),selected)))el('saved').textContent='The selected track expired or changed. Select a fresh target; its old label will not transfer.';
    renderPoint();
  }
  el('select').onchange=()=>{const value=el('select').value;if(value){const ref=JSON.parse(value),t=fresh().find(t=>same(reference(t),ref)),binding=bindings.find(b=>same(b.target,ref));el('label').value=binding?.label||t?.type||'';el('destination').value=binding?.destination?JSON.stringify(binding.destination):'';}render();};
  el('label').oninput=render;
  el('save').onclick=async()=>{el('save').disabled=true;try{const target=JSON.parse(el('select').value),destination=el('destination').value?JSON.parse(el('destination').value):null;
    await save({target,label:el('label').value.trim(),destination});selected=target;el('saved').textContent=destination?'Selected target and virtual counterpart saved.':'Selected for input. Robot output still needs a virtual counterpart.';
  }catch(error){el('saved').textContent=error.message||'The target changed or expired; select again.';}finally{render();}};
  const timer=setInterval(()=>{if(!document.hidden)render();},500);
  function point(value){lastPoint=value;renderPoint();}
  function renderPoint(){
    const value=lastPoint&&Date.now()-Date.parse(lastPoint.ts)<3000?lastPoint:null;
    api.source(value?.sourceAssociation);const resolution=value?.resolution;
    const label=ref=>bindings.find(b=>same(b.target,ref))?.label||fresh().find(t=>same(reference(t),ref))?.type||'registered target';
    const current=resolution?.target&&[...fresh(),...(configured.targets||[])].some(t=>same(reference(t),resolution.target)&&(!t.expiresAt||Date.parse(t.expiresAt)>Date.now()));
    el('selection').textContent=resolution?.status==='resolved'&&current?`Projected selection: ${label(resolution.target)}`:resolution?.status==='ambiguous'?'Ambiguous projection: select or reposition one target.':'No resolved projected target.';
  }
  const api={update(state){({snapshot={},configured={},bindings=[],selected=null,enabled=false}=state);render();},source(value){el('source').textContent=value?.status==='projected'?'One overlapping person box was excluded as the projected source. This is not verified identity.':'Source person unresolved; no identity inferred.';},clear(){lastPoint=null;snapshot={};configured={};bindings=[];selected=null;enabled=false;lastOptions='';lastDestinations='';el('label').value='';el('saved').textContent='';api.source(null);render();},dispose(){clearInterval(timer);}};
  api.point=point;render();return api;
}
