import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizePlan, EmbodimentState} from '../../../src/main/resources/public/valerian/embodiment/model.js';

const plan = {speech:'Unchanged speech', nonVerbal:{gesture:'EXPLAIN', facialExpression:{type:'warm_smile',intensity:0.8},
  gaze:{direction:'toward_user',focus:'speaker'},motion:{energy:0.2,stillness:0.7}},
  motion:{handSign:'rock',effector:'left_hand',energy:0.6},display:{userSign:'paper',round:2,nested:{custom:'value'}}};
test('maps all channels without altering the canonical plan or enacting user signs',()=>{
  const original=JSON.stringify(plan), p=normalizePlan(plan);
  assert.equal(p.face,'happy'); assert.equal(p.intensity,0.8); assert.equal(p.gesture,'EXPLAIN');
  assert.equal(p.energy,0.6); assert.equal(p.stillness,0.7); assert.equal(p.sign,'rock'); assert.equal(p.hand,'left_hand');
  assert.deepEqual(p.gaze,[0,0]); assert.equal(p.focus,'speaker'); assert.deepEqual(p.display,plan.display);
  assert.equal(JSON.stringify(plan),original); assert.equal(normalizePlan({display:{userSign:'rock'}}).sign,'');
});
test('display agent sign follows existing precedence; both hands are explicit',()=>{
  assert.equal(normalizePlan({...plan,display:{agentSign:'scissor'}}).sign,'scissor');
  assert.equal(normalizePlan({motion:{handSign:'paper',effector:'both_hands'}}).hand,'both_hands');
});
test('legacy hand aliases and invalid display precedence agree with Details',()=>{
  for(const [input,expected] of [['Stein','rock'],['Schere','scissor'],['scissors','scissor'],['Papier','paper']]) {
    assert.equal(normalizePlan({motion:{handSign:input}}).sign,expected);
    assert.equal(normalizePlan({display:{agentSign:input}}).sign,expected);
  }
  const p=normalizePlan({motion:{handSign:'rock'},display:{agentSign:'unknown'}});
  assert.equal(p.sign,'rock');assert.match(p.warnings[0],/unknown/);
});
test('partial plans reset missing channels and invalid values fail neutrally',()=>{
  const p=normalizePlan({nonVerbal:{gesture:'BOUNCE',facialExpression:{type:'alien',intensity:Infinity},gaze:'moon'},
    motion:{handSign:'thumbs_up',energy:-2,stillness:8}});
  assert.equal(p.gesture,'NONE'); assert.equal(p.face,'neutral'); assert.equal(p.sign,'');
  assert.equal(p.energy,0); assert.equal(p.stillness,1); assert.equal(p.warnings.length,4);
  assert.equal(normalizePlan({motion:{handSign:'rock',effector:'head'}}).sign,'');
  assert.equal(normalizePlan({speech:'Only words'}).intensity,0);
  assert.equal(normalizePlan(null).gesture,'NONE');
  assert.equal(normalizePlan({nonVerbal:{facialExpression:'warmNeutral'}}).warnings.length,0);
  assert.equal(normalizePlan({nonVerbal:{facialExpression:'warmSmile'}}).face,'happy');
});
test('unknown labels cannot resolve inherited JavaScript object properties',()=>{
  const p=normalizePlan({nonVerbal:{facialExpression:'constructor',gaze:'__proto__'},motion:{handSign:'constructor'}});
  assert.equal(p.face,'neutral');assert.deepEqual(p.gaze,[0,0]);assert.equal(p.sign,'');assert.equal(p.warnings.length,3);
});
test('filters undeclared channels; scalar display content survives',()=>{
  const p=normalizePlan(plan,['speech']); assert.equal(p.gesture,'NONE');assert.equal(p.sign,'');assert.equal(p.display,null);
  assert.equal(normalizePlan({display:'Hello'}).display,'Hello');
  assert.equal(normalizePlan(plan,['nonVerbal']).face,'happy');
});
test('only fresh visible live/acknowledged plans animate, with finite duration',()=>{
  const s=new EmbodimentState();s.visibility(true);s.receive(plan,{delivery:'live'},100);
  assert.equal(s.sample(500).active,true);assert.equal(s.sample(5000).active,false);assert.equal(s.sample(5000).gesture,0);
  s.receive(plan,{delivery:'acknowledgement'},6000);assert.equal(s.sample(6100).active,true);
  for(const delivery of ['replay','history',undefined]) {s.receive(plan,{delivery},7000);assert.equal(s.sample(7100).active,false);}
  s.receive(plan,{delivery:'live',fromHistory:true},8000);assert.equal(s.sample(8100).active,false);
});
test('switching views, reducing motion and hiding cannot replay a received gesture',()=>{
  const s=new EmbodimentState();s.receive(plan,{delivery:'live'},0);s.visibility(true);
  assert.equal(s.sample(100).active,false);s.receive(plan,{delivery:'live'},200);
  s.visibility(false);s.visibility(true);assert.equal(s.sample(300).active,false);
  s.visibility(true,true);s.receive(plan,{delivery:'live'},400);assert.equal(s.sample(500).ambient,0);
  assert.equal(s.sample(500).active,false);
});
test('native speech preserves embodiment, supersession replaces it, reset clears it',()=>{
  const s=new EmbodimentState();s.visibility(true);s.receive(plan,{delivery:'live'},0);
  s.receive({speech:'Native words'},{preserveNonSpeech:true},100);assert.equal(s.pose.sign,'rock');
  s.receive({nonVerbal:{gesture:'POLITE'}},{delivery:'live'},200);assert.equal(s.pose.sign,'');
  s.settle();assert.equal(s.sample(300).active,false);s.clear();assert.equal(s.sample(400).pose,null);
});
