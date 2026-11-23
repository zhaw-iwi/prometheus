// Executable subset of hand/interaction-v1.schema.json. Legacy RPS is separate.
export const namedSigns = ['fist','peace','open_palm','thumbs_up','thumbs_down','index_up'];
export function handAction(value) {
  const exact=(o,keys)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).length===keys.length&&keys.every(k=>Object.hasOwn(o,k));
  if(!exact(value,['schemaVersion','action'])||value.schemaVersion!==1)throw new Error('Invalid hand action version or fields');
  const a=value.action;
  if(!exact(a,['kind','sign','effector'])||a.kind!=='sign'||!namedSigns.includes(a.sign)
      ||!['left_hand','right_hand','both_hands'].includes(a.effector))throw new Error('Unsupported hand action');
  return {...a};
}
export const handTiming={enter:350,hold:500,release:350};
