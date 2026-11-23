// Executable subset of hand/interaction-v1.schema.json. Legacy RPS is separate.
import {fingerPatterns} from './patterns.js';
import {validateRelations} from './relations.js';
import {validatePointAction} from './pointing.js';
export const namedSigns = Object.keys(fingerPatterns);
export function handAction(value) {
  const exact=(o,keys)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).length===keys.length&&keys.every(k=>Object.hasOwn(o,k));
  if(!exact(value,['schemaVersion','action'])||value.schemaVersion!==1)throw new Error('Invalid hand action version or fields');
  const a=value.action;
  if(a?.kind==='point'){validatePointAction(a);return structuredClone(a);}
  if(a?.kind==='pose') {
    if(!exact(a,['kind','hands'])||!Array.isArray(a.hands)||a.hands.length<1||a.hands.length>2)throw new Error('Invalid finger pose');
    const sides=new Set();
    for(const hand of a.hands) {
      if(!exact(hand,Object.hasOwn(hand,'relations')?['side','fingers','relations']:['side','fingers'])||!['left','right'].includes(hand.side)||sides.has(hand.side)
          ||!exact(hand.fingers,['thumb','index','middle','ring','little'])
          ||!Object.values(hand.fingers).every(v=>Number.isFinite(v)&&v>=0&&v<=1))throw new Error('Invalid finger pose');
      sides.add(hand.side);
      if(Object.hasOwn(hand,'relations'))validateRelations(hand.relations,hand.fingers);
    }
    return structuredClone(a);
  }
  if(!exact(a,['kind','sign','effector'])||a.kind!=='sign'||!namedSigns.includes(a.sign)
      ||!['left_hand','right_hand','both_hands'].includes(a.effector))throw new Error('Unsupported hand action');
  return {...a};
}
export const handTiming={enter:350,hold:500,release:350};
