// Controlled sensor evidence for provider interpretation/control acceptance, not camera accuracy.
export const frame={id:'hand-live-camera',revision:1,space:'camera_image',mirrored:false};
export const fingers=['thumb','index','middle','ring','little'];
let ordinal=0;
const base=version=>({schemaVersion:version,observationId:'hand-live-'+(++ordinal),captureIndex:ordinal,
  source:'hand-live-fixture',ts:new Date().toISOString(),frame:{...frame}});
export function sign(value='thumbs_up',side='right') {return {...base(1),hands:[{id:side+'-track',handedness:side,sign:value,confidence:.95}]};}
export function hand(side,values) {return {id:side+'-track',handedness:side,fingers:Object.fromEntries(fingers.map((f,i)=>[f,values[i]])),
  fingerStates:Object.fromEntries(fingers.map((f,i)=>[f,values[i]===null?null:values[i]<.4?'straight':values[i]>.55?'bent':'neutral']))};}
export function pose(hands) {return {...base(2),hands};}
export const custom=()=>pose([hand('left',[1,0,1,1,1]),hand('right',[0,1,1,1,1])]);
export const count=n=>pose([hand('right',fingers.map((_,i)=>i<n?0:1))]);
export const attention=()=>pose([hand('right',[1,0,1,1,1])]);
export function ring(near=true) {
  const h=hand('right',[1,1,0,0,0]),pairs=fingers.flatMap((a,i)=>fingers.slice(i+1).map(b=>a+'_'+b));
  h.geometry={schemaVersion:1,tipDistances:Object.fromEntries(pairs.map(p=>[p,null])),spreads:Object.fromEntries(pairs.map(p=>[p,null])),axes:{palm_normal:null,index_axis:null,thumb_axis:null}};
  h.geometry.tipDistances.thumb_index=near ? .1 : .8;return pose([h]);
}
export function point(direction='right',origin={x:.3,y:.5},source=true) {
  const vectors={right:[1,0],left:[-1,0],up:[0,-1],down:[0,1]};const v=vectors[direction];
  return {...base(1),handId:'right-track',handedness:'right',validity:v?'valid':'unavailable',direction:v?direction:null,
    ray:v?{origin,vector:{x:v[0],y:v[1]},aspectRatio:4/3}:null,resolution:{status:'unresolved'},...(source?{handOrigin:{x:.2,y:.5}}:{})};
}
export function sceneConfig(dynamic=false) {return {schemaVersion:1,expectedRevision:0,cameraFrame:dynamic?null:{...frame},transform:dynamic?null:[8,0,-4,0,-3,3],
  targets:dynamic?[
    {key:'cup-place',label:'Cup pedestal',type:'virtual',position:{x:3,y:2,z:0},extent:{x:.1,y:.1,z:0},expiresAt:null},
    {key:'person-place',label:'Visitor position',type:'virtual',position:{x:-3,y:2,z:0},extent:{x:.1,y:.1,z:0},expiresAt:null}
  ]:[{key:'display',label:'Display',type:'region',position:{x:.9,y:.5,z:0},extent:{x:.1,y:.3,z:0},expiresAt:null}]};}
export function targets({ambiguous=false,person=true}={}) {
  const at=new Date(),expires=new Date(+at+3000),items=[['source','person',.2,.5,.3,.9],['cup-a','cup',.7,.5,.12,.2]];
  if(ambiguous)items.push(['cup-b','cup',.9,.5,.1,.2]);
  if(person)items.push(['visitor','person',.8,.2,.15,.25]);
  return {schemaVersion:1,observationId:'scene-'+(++ordinal),source:'hand-scene-fixture',ts:at.toISOString(),frame:{...frame},targets:items.map(([id,type,x,y,w,h])=>({
    id,revision:1,label:type,type,provenance:'detected',frame:{...frame},position:{x,y,z:0},extent:{x:w,y:h,z:0},observedAt:at.toISOString(),expiresAt:expires.toISOString()}))};
}
export const ref=target=>({id:target.id,revision:target.revision,frame:target.frame});
export function emptyScene(){const snapshot=targets();snapshot.targets=[];return snapshot;}
