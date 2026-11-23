import {test} from 'node:test';
import assert from 'node:assert/strict';
import '../../../src/main/resources/public/valerian/instance-labels.js';
const labels=globalThis.PrometheusInstanceLabels.labels;

test('same-name instances always have eight-character identities and retain full map keys',()=>{
  const instances=[{id:'a3ca2f38-1111-4111-8111-111111111111',name:'Coordination'},
    {id:'b3ca2f38-2222-4222-8222-222222222222',name:'Coordination'}];
  const result=labels(instances);
  assert.equal(result.get(instances[0].id),'Coordination · a3ca2f38');
  assert.equal(result.get(instances[1].id),'Coordination · b3ca2f38');
  assert.equal(labels([instances[0]]).get(instances[0].id),result.get(instances[0].id));
});
test('colliding prefixes extend until unique independently of list order',()=>{
  const instances=['12345678-aaaa','12345678-aaab','87654321-aaaa'].map(id=>({id,name:'Agent'}));
  const result=labels(instances),reordered=labels([...instances].reverse());
  for(const item of instances)assert.equal(result.get(item.id),reordered.get(item.id));
  assert.equal(result.get(instances[0].id),'Agent · 12345678-aaaa');
  assert.equal(new Set(result.values()).size,3);
});
test('missing and Unicode names remain text without renaming or truncating',()=>{
  const name='長い名前 🪨 '.repeat(30)+'<img src=x onerror=alert(1)>';
  const instances=[{id:'aabbccdd-0000',name},{id:'eeff0011-0000'},null,{id:''}];
  const result=labels(instances);
  assert.equal(result.get('aabbccdd-0000'),name+' · aabbccdd');
  assert.equal(result.get('eeff0011-0000'),'Agent · eeff0011');
  assert.equal(result.size,2);assert.equal(instances[0].name,name);
});
test('duplicate identities do not force a longer prefix',()=>{
  const agent={id:'aabbccdd-0000',name:'Same'};
  assert.deepEqual(labels([agent,agent]),labels([agent]));
});
test('compact option names keep the identity visible while full labels remain available',()=>{
  const compact=globalThis.PrometheusInstanceLabels.optionLabel;
  assert.equal(compact('Coordination · aabbccdd'),'Coordination · aabbccdd');
  assert.match(compact('Long name '.repeat(20)+' · aabbccdd'),/… · aabbccdd$/);
  assert.match(compact('長い名前'.repeat(20)+' · aabbccdd'),/… · aabbccdd$/);
});
