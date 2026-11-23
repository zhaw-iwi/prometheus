import {namedFingerStates} from './finger-pose.js';
export const planeDirections=['right','down_right','down','down_left','left','up_left','up','up_right'];
export const robotFrame=Object.freeze({id:'valerian.robot',revision:1,space:'robot_local',mirrored:false});
const round=n=>Math.round(n*10000)/10000;
export function directionSector(x,y) {
  if(!Number.isFinite(x)||!Number.isFinite(y)||Math.hypot(x,y)<1e-8)return null;
  const angle=Math.atan2(y,x)*180/Math.PI,sector=Math.round(angle/45);
  return Math.abs(angle-sector*45)<=18.5?planeDirections[(sector+8)%8]:null;
}
export function estimatePointing(result,hands,aspectRatio=1) {
  const absent=validity=>({handId:'none',handedness:'unknown',direction:null,validity,ray:null,resolution:{status:'unresolved'}});
  if(!Number.isFinite(aspectRatio)||aspectRatio<=0||aspectRatio>10)return absent('unavailable');
  const candidates=hands.map((hand,index)=>({hand,index,states:namedFingerStates(result,index,hand)}))
    .filter(c=>c.states?.index==='straight'&&['middle','ring','little'].every(f=>c.states?.[f]==='bent'));
  if(candidates.length!==1)return absent(candidates.length?'ambiguous':'unavailable');
  const {hand,index}=candidates[0],image=result.landmarks[index],world=result.worldLandmarks[index];
  if(![0,5,8,9,17].every(i=>[image?.[i],world?.[i]].every(p=>p&&['x','y','z'].every(k=>Number.isFinite(p[k])))))return absent('unavailable');
  const distance=(a,b)=>Math.hypot((image[a].x-image[b].x)*aspectRatio,image[a].y-image[b].y);
  const scale=Math.max(distance(0,9),distance(5,17));
  const x=(image[8].x-image[5].x)*aspectRatio,y=image[8].y-image[5].y,length=Math.hypot(x,y);
  const wx=world[8].x-world[5].x,wy=world[8].y-world[5].y,wz=world[8].z-world[5].z;
  if(!Number.isFinite(scale)||scale<1e-6||length/scale<.25||Math.hypot(wx,wy)/Math.hypot(wx,wy,wz)<.35)return absent('foreshortened');
  const direction=directionSector(x,y);if(!direction)return absent('boundary');
  return {handId:hand.id,handedness:hand.handedness,direction,validity:'valid',
    ray:{origin:{x:round(image[8].x),y:round(image[8].y)},vector:{x:round(x/length),y:round(y/length)},aspectRatio},resolution:{status:'unresolved'}};
}
export function validatePointAction(a) {
  const exact=(o,keys)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).length===keys.length&&keys.every(k=>Object.hasOwn(o,k));
  if(a?.kind==='point'&&a.target){
    const frame=f=>exact(f,['id','revision','space','mirrored'])&&typeof f.id==='string'&&/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,79}$/.test(f.id)&&Number.isInteger(f.revision)&&f.revision>0&&f.mirrored===false;
    const p=a.resolved?.position;
    if(!exact(a,['kind','effector','target','resolved'])||!['left_hand','right_hand'].includes(a.effector)||!exact(a.target,['id','revision','frame'])
      ||typeof a.target.id!=='string'||!Number.isInteger(a.target.revision)||a.target.revision<1||!frame(a.target.frame)||!['camera_image','virtual_scene'].includes(a.target.frame.space)
      ||!exact(a.resolved,['label','position','frame'])||typeof a.resolved.label!=='string'||a.resolved.label.length>80||!frame(a.resolved.frame)||a.resolved.frame.space!=='virtual_scene'
      ||!exact(p,['x','y','z'])||![p.x,p.y,p.z].every(Number.isFinite)||p.x< -4||p.x>4||p.y< -1||p.y>5||Math.abs(p.z)>3)throw new Error('Unresolved or invalid target');
    if(Math.hypot(p.x-(a.effector==='left_hand'?.58:-.58),p.y-1.29,p.z)<1.8)throw new Error('Destination is inside the pointing arm workspace');
    return;
  }
  if(!exact(a,['kind','effector','direction','frame'])||a.kind!=='point'||!['left_hand','right_hand'].includes(a.effector)
      ||!planeDirections.includes(a.direction)||!exact(a.frame,Object.keys(robotFrame))||!Object.keys(robotFrame).every(k=>a.frame[k]===robotFrame[k]))
    throw new Error('Unsupported pointing direction or frame');
}
// Camera y points down. Robot-local right is its own right (viewer-left).
export function robotDirection(direction) {
  const index=planeDirections.indexOf(direction);if(index<0)throw new Error('Unsupported direction');
  const angle=index*Math.PI/4;return {x:-Math.cos(angle),y:-Math.sin(angle),z:0};
}
