// Camera-plane proximity is an estimate, never proof of contact or metric depth.
export const geometryFingers = ['thumb','index','middle','ring','little'];
export const geometryPairs = geometryFingers.flatMap((a,i)=>geometryFingers.slice(i+1).map(b=>`${a}_${b}`));
const tips={thumb:4,index:8,middle:12,ring:16,little:20};
const bases={thumb:2,index:5,middle:9,ring:13,little:17};
const finite=p=>p&&['x','y','z'].every(k=>Number.isFinite(p[k]));
const vector=(a,b)=>[b.x-a.x,b.y-a.y,b.z-a.z];
const dot=(a,b)=>a.reduce((sum,v,i)=>sum+v*b[i],0);
const round=n=>Math.round(n*10000)/10000;
function projectedAxis(v) {
  const length=Math.hypot(v[0],v[1]);
  if(length<1e-8||length/Math.hypot(...v)<.2)return null;
  return {x:round(v[0]/length),y:round(v[1]/length)};
}
export function measureHandGeometry(result,index,hand,aspectRatio=1) {
  const image=result?.landmarks?.[index],world=result?.worldLandmarks?.[index];
  const available=name=>Number.isFinite(hand?.fingers?.[name]);
  const valid=ids=>ids.every(i=>finite(image?.[i])&&finite(world?.[i])&&image[i].x>=0&&image[i].x<=1&&image[i].y>=0&&image[i].y<=1);
  const distance=(a,b)=>Math.hypot((image[a].x-image[b].x)*aspectRatio,image[a].y-image[b].y);
  const scale=valid([0,5,9,17])&&Number.isFinite(aspectRatio)&&aspectRatio>0&&aspectRatio<=10
    ? Math.max(distance(0,9),distance(5,17)):0;
  const tipDistances={},spreads={};
  for(const pair of geometryPairs) {
    const [a,b]=pair.split('_');
    const known=available(a)&&available(b)&&valid([bases[a],bases[a]+1,tips[a],bases[b],bases[b]+1,tips[b]])&&scale>1e-6;
    const proximity=known?distance(tips[a],tips[b])/scale:null;
    tipDistances[pair]=proximity!==null&&proximity<=4?round(proximity):null;
    const u=known?vector(world[bases[a]],world[bases[a]+1]):null;
    const v=known?vector(world[bases[b]],world[bases[b]+1]):null;
    const lengths=known?Math.hypot(...u)*Math.hypot(...v):0;
    spreads[pair]=lengths>1e-12?round(Math.acos(Math.max(-1,Math.min(1,dot(u,v)/lengths)))/Math.PI):null;
  }
  const axes={palm_normal:null,index_axis:null,thumb_axis:null};
  for(const name of ['index','thumb'])if(available(name)&&scale>1e-6) {
    const a=image[bases[name]],b=image[tips[name]];
    axes[`${name}_axis`]=projectedAxis([(b.x-a.x)*aspectRatio,b.y-a.y,0]);
  }
  if(scale>1e-6&&['left','right'].includes(hand?.handedness)) {
    const a=vector(world[0],world[5]),b=vector(world[0],world[17]),side=hand.handedness==='left'?-1:1;
    axes.palm_normal=projectedAxis([a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]].map(n=>n*side));
  }
  return {schemaVersion:1,tipDistances,spreads,axes};
}

export function ringSign(hand,states,geometry,wasRing=false) {
  const distance=geometry?.tipDistances?.thumb_index;
  return Number.isFinite(distance)&&distance<(wasRing ? .22 : .18)
    &&Number.isFinite(hand?.fingers?.thumb)&&Number.isFinite(hand?.fingers?.index)&&hand.fingers.index>.25
    &&['middle','ring','little'].every(name=>states?.[name]==='straight');
}

export function geometryStableWithin(previous,current) {
  if(!previous||!current)return previous===current;
  for(const map of ['tipDistances','spreads'])for(const pair of geometryPairs) {
    const a=previous[map][pair],b=current[map][pair];
    if(a===null||b===null) {if(a!==b)return false;}
    else if(Math.abs(a-b)>.04)return false;
  }
  for(const axis of ['palm_normal','index_axis','thumb_axis']) {
    const a=previous.axes[axis],b=current.axes[axis];
    if(a===null||b===null) {if(a!==b)return false;}
    else if(a.x*b.x+a.y*b.y<Math.cos(8*Math.PI/180))return false;
  }
  return true;
}
