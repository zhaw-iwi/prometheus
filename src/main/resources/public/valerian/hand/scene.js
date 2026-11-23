// Operator setup and an unmirrored projection preview. The backend owns selection.
export function rayHits(ray,position,extent) {
  if(!ray)return false;let lo=0,hi=Infinity;
  for(const axis of ['x','y']){
    const o=ray.origin[axis],d=ray.vector[axis]/(axis==='x'?ray.aspectRatio:1),half=extent[axis]/2;
    if(Math.abs(d)<1e-8){if(o<position[axis]-half||o>position[axis]+half)return false;}
    else{const a=(position[axis]-half-o)/d,b=(position[axis]+half-o)/d;lo=Math.max(lo,Math.min(a,b));hi=Math.min(hi,Math.max(a,b));if(hi<lo)return false;}
  }return hi>=lo;
}
export function mappedPosition(transform,p){return{x:transform[0]*p.x+transform[1]*p.y+transform[2],y:transform[3]*p.x+transform[4]*p.y+transform[5],z:0};}
export function mountScene(root,save){
  let scene={},targets=[],point=null,enabled=false,revision=-1;
  const number=(id,label,value,min,max)=>`<label class="small">${label}<input id="scene_${id}" class="form-control form-control-sm" type="number" step="0.01" min="${min}" max="${max}" value="${value}"></label>`;
  root.innerHTML=`<summary>Pointing destinations</summary><p class="small mt-2">Register camera regions or destinations in the digital scene. Image coordinates are unmirrored (x right, y down, 0 to 1). A room location needs an explicit mapping; this does not measure room depth.</p>
    <p id="scene_status" role="status" class="small">Connect a target-capable Coordination instance.</p>
    <fieldset id="scene_fields"><label class="small">Name<input id="scene_label" class="form-control form-control-sm" maxlength="80" value="Display"></label>
    <label class="small">Destination type<select id="scene_type" class="form-select form-select-sm"><option value="region">Camera region</option><option value="virtual">Virtual destination</option></select></label>
    <div class="d-flex flex-wrap gap-2 mt-2">${number('x','Centre x',.9,-4,4)}${number('y','Centre y',.5,-1,5)}${number('z','Depth z (virtual only)',0,-3,3)}${number('width','Width',.2,0,2)}${number('height','Height',.2,0,2)}</div>
    <label class="small mt-2">Expires after seconds (0 = retained)<input id="scene_ttl" class="form-control form-control-sm" type="number" min="0" max="86400" value="0"></label>
    <button type="button" id="scene_add" class="btn btn-sm btn-outline-primary mt-2">Add destination</button>
    <ul id="scene_targets" class="small mt-2"></ul>
    <label class="small"><input id="scene_calibrated" type="checkbox"> Map this camera plane into the digital scene</label>
    <p class="small text-muted">Choose where the image edges belong in the scene (x right, y up). These are virtual units. Pointing destinations must be at least 1.8 units from the selected shoulder. Saving binds this mapping to the current camera frame.</p>
    <div class="d-flex flex-wrap gap-2">${number('left','Image left maps to x',-4,-4,4)}${number('right','Image right maps to x',4,-4,4)}${number('top','Image top maps to y',3,-1,5)}${number('bottom','Image bottom maps to y',0,-1,5)}</div>
    <button type="button" id="scene_save" class="btn btn-sm btn-primary mt-2">Save destinations and mapping</button></fieldset>
    <svg id="scene_preview" viewBox="0 0 400 230" role="img" aria-label="Unmirrored camera regions and projected pointing ray" style="width:100%;background:#eaf2f2;border-radius:8px;margin-top:12px"></svg>
    <p id="scene_selection" class="small mt-1"></p>`;
  const el=id=>root.querySelector('#scene_'+id),num=id=>Number(el(id).value);
  function message(text){el('status').textContent=text;}
  function render(list=true){
    el('fields').disabled=!enabled;if(list)el('targets').replaceChildren();
    for(const t of list?targets:[]){const row=document.createElement('li'),remove=document.createElement('button');row.append(document.createTextNode(`${t.label} (${t.type}) `));remove.textContent='Remove';remove.className='btn btn-sm btn-link';remove.type='button';remove.onclick=()=>{targets=targets.filter(other=>other!==t);render();message('Unsaved changes. Saving pauses active tasks; revise changed references.');};row.append(remove);el('targets').append(row);}
    const svg=el('preview');svg.replaceChildren();
    const draw=(tag,attributes,label)=>{const node=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const[k,v]of Object.entries(attributes))node.setAttribute(k,v);if(label)node.textContent=label;svg.append(node);};
    draw('text',{x:10,y:18,fill:'#173c45','font-size':12},'Unmirrored camera plane: x right, y down');
    const candidates=point?.validity==='valid'?targets.filter(t=>t.type==='region'&&(!t.expiresAt||Date.parse(t.expiresAt)>Date.now())&&rayHits(point.ray,t.position,t.extent)):[];
    for(const t of targets.filter(t=>t.type==='region')){const p=t.position,e=t.extent,selected=candidates.includes(t);draw('rect',{x:(p.x-e.x/2)*380+10,y:(p.y-e.y/2)*190+28,width:e.x*380,height:e.y*190,fill:selected?'#f2be76':'#bad3d3',stroke:selected?'#b95012':'#52777d','stroke-width':2});draw('text',{x:p.x*380+10,y:p.y*190+28,fill:'#16323c','font-size':12,'text-anchor':'middle'},t.label);}
    if(point?.ray){const r=point.ray;draw('line',{x1:r.origin.x*380+10,y1:r.origin.y*190+28,x2:(r.origin.x+r.vector.x/r.aspectRatio*2)*380+10,y2:(r.origin.y+r.vector.y*2)*190+28,stroke:'#155cc4','stroke-width':3});}
    el('selection').textContent=candidates.length===1?`Projected selection: ${candidates[0].label}`:candidates.length>1?'Ambiguous projection: '+candidates.map(t=>t.label).join(', '):'No unique projected region selected.';
  }
  el('add').onclick=()=>{
    const label=el('label').value.trim(),type=el('type').value,ttl=num('ttl');if(!label||targets.length>=16){message('Enter a name; at most sixteen destinations.');return;}
    const key='target-'+crypto.randomUUID().slice(0,8);
    targets.push({key,label,type,position:{x:num('x'),y:num('y'),z:type==='region'?0:num('z')},extent:{x:num('width'),y:num('height'),z:0},expiresAt:ttl?new Date(Date.now()+ttl*1000).toISOString():null});render();message('Unsaved destination. Save to use it in instructions or agreements.');
  };
  el('save').onclick=async()=>{
    const transform=el('calibrated').checked?[num('right')-num('left'),0,num('left'),0,num('bottom')-num('top'),num('top')]:null;
    const needsCamera=transform||targets.some(t=>t.type==='region');
    if(needsCamera&&!point?.frame){message('Start the camera and deliver a current pointing-frame sample first.');return;}
    el('save').disabled=true;message('Saving destinations...');
    try{const result=await save({schemaVersion:1,expectedRevision:scene.revision||0,cameraFrame:needsCamera?point.frame:null,transform,targets});revision=-1;api.update(result,true);message(`Saved scene revision ${result.revision}. Active tasks pause; revise changed target references.`);}
    catch(error){message(error.message||'Save failed. Refresh and check the current camera frame, bounds and mapping.');}
    finally{el('save').disabled=false;}
  };
  const api={update(value,capable){value=value||{};if(revision===(value.revision||0)&&enabled===capable)return;enabled=capable;if(revision!==(value.revision||0)){scene=value;revision=value.revision||0;targets=(value.targets||[]).map(t=>({key:t.id.slice(t.id.indexOf(':')+1),label:t.label,type:t.type,position:t.position,extent:t.extent,expiresAt:t.expiresAt}));const a=value.transform;el('calibrated').checked=!!a;if(a){el('left').value=a[2];el('right').value=a[0]+a[2];el('top').value=a[5];el('bottom').value=a[4]+a[5];}message(capable?`Scene revision ${revision}. ${targets.length} registered destinations.`:'Create a new Coordination instance for target pointing.');}render();},point(value){point=value;render(false);},clear(){point=null;revision=-1;api.update({},false);}};
  api.update({},false);return api;
}
