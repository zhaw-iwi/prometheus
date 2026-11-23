import {geometryFingers} from './geometry.js';
export const directionAngles={right:0,down_right:Math.PI/4,down:Math.PI/2,down_left:3*Math.PI/4,left:Math.PI,up_left:-3*Math.PI/4,up:-Math.PI/2,up_right:-Math.PI/4};
const exact=(o,keys)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).length===keys.length&&keys.every(k=>Object.hasOwn(o,k));
const number=(v,min,max)=>Number.isFinite(v)&&v>=min&&v<=max;
export function validateRelations(relations,fingers) {
  if(!Array.isArray(relations)||relations.length>6)throw new Error('Invalid hand relations');
  const axes=new Set(),bounds=new Map();
  for(const r of relations) {
    if(r?.kind==='orientation') {
      if(!exact(r,['kind','axis','direction','toleranceDegrees'])||r.axis!=='index_axis'||!Object.hasOwn(directionAngles,r.direction)
          ||!number(r.toleranceDegrees,5,45)||axes.has(r.axis)||fingers.index!==0)throw new Error('Orientation needs one straight index axis');
      axes.add(r.axis);
    } else {
      if(!exact(r,['kind','fingers','operator','value'])||!['tip_proximity','spread'].includes(r.kind)
          ||!Array.isArray(r.fingers)||r.fingers.length!==2||r.fingers[0]===r.fingers[1]||!r.fingers.every(f=>geometryFingers.includes(f))
          ||!['lt','gt'].includes(r.operator)||!number(r.value,0,r.kind==='spread'?1:2)||r.operator==='lt'&&r.value===0)throw new Error('Invalid relation');
      const pair=[...r.fingers].sort((a,b)=>geometryFingers.indexOf(a)-geometryFingers.indexOf(b)).join('_');
      if(r.kind==='spread') {
        if(pair!=='index_middle'||fingers.index!==0||fingers.middle!==0||r.operator==='gt'&&r.value>=.3)throw new Error('Unsupported digital finger spread');
      } else if(pair!=='thumb_index'||r.operator!=='lt'||r.value<.05||fingers.thumb!==.7||fingers.index!==.735)throw new Error('Proximity requires the thumb/index ring preset');
      const key=r.kind+'/'+pair,range=bounds.get(key)||[0,r.kind==='spread'?1:4];
      if(r.operator==='lt')range[1]=Math.min(range[1],r.value);else range[0]=Math.max(range[0],r.value);
      if(range[0]>=range[1])throw new Error('Conflicting hand relations');bounds.set(key,range);
    }
  }
}
export function outputRelations(relations=[]) {
  let lower=0,upper=.3,spread=false,orientation=null,ring=false;
  for(const r of relations) {
    if(r.kind==='orientation')orientation=r.direction;
    else if(r.kind==='tip_proximity')ring=true;
    else {spread=true;if(r.operator==='gt')lower=Math.max(lower,r.value);else upper=Math.min(upper,r.value);}
  }
  return {ring,orientation,spread:spread?(lower+upper)/2*Math.PI:0};
}
