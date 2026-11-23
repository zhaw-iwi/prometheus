import test from 'node:test';
import assert from 'node:assert/strict';
import * as T from '../../../src/main/resources/public/valerian/embodiment/vendor/three.module.js';
import {normalizePlan,EmbodimentState} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
import {namedSigns,handAction} from '../../../src/main/resources/public/valerian/hand/actions.js';
const plan=(sign,effector='both_hands')=>({motion:{handAction:{schemaVersion:1,action:{kind:'sign',sign,effector}}}});
for(const sign of namedSigns)test(`named ${sign} articulates both sides and a single selected hand`,()=>{
  for(const effector of ['left_hand','right_hand','both_hands']){
    const robot=createRobot(),pose=normalizePlan(plan(sign,effector));robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1});robot.root.updateMatrixWorld(true);
    for(const side of ['left','right']){
      const selected=effector==='both_hands'||effector===side+'_hand';
      for(const [i,finger] of ['index','middle','ring','little'].entries()){
        const folded=selected&&(sign==='fist'||sign.startsWith('thumbs_')||sign==='peace'&&i>=2||sign==='index_up'&&i!==0);
        assert.equal(robot.root.getObjectByName(`${side}.${finger}.1`).rotation.x>1,folded,side+' '+finger);
      }
      if(selected&&sign.startsWith('thumbs_')){
        const thumb=robot.root.getObjectByName(`${side}.thumb`);
        const direction=new T.Vector3(0,-1,0).applyQuaternion(thumb.getWorldQuaternion(new T.Quaternion()));
        assert.ok(sign==='thumbs_up'?direction.y>.99:direction.y<-.99,`${side} thumb y=${direction.y}`);
      }
      if(selected&&sign==='index_up'){
        const index=robot.root.getObjectByName(`${side}.index`);
        assert.ok(new T.Vector3(0,-1,0).applyQuaternion(index.getWorldQuaternion(new T.Quaternion())).y>.99);
      }
    }
  }
});
test('named output is gated and malformed or conflicting motion never falls back to a previous sign',()=>{
  assert.equal(normalizePlan(plan('thumbs_up'),['motion.handSign']).sign,'');
  assert.match(normalizePlan(plan('thumbs_up'),['motion.handSign']).warnings[0],/unavailable/);
  assert.equal(normalizePlan({...plan('peace'),motion:{...plan('peace').motion,handSign:'rock'}}).sign,'');
  for(const invalid of [null,{}, {schemaVersion:2,action:{kind:'sign',sign:'peace',effector:'left_hand'}},
    {schemaVersion:1,action:{kind:'sign',sign:'constructor',effector:'left_hand'}}])assert.throws(()=>handAction(invalid));
});
test('named output holds for 500ms then releases; history, hidden and reduced views never execute it',()=>{
  const state=new EmbodimentState();state.visibility(true);state.receive(plan('thumbs_up'),{delivery:'live'},0);
  assert.equal(state.sample(350).handPhase,'holding');assert.equal(state.sample(849).handBlend,1);
  assert.equal(state.sample(850).handPhase,'releasing');assert.equal(state.sample(1200).handBlend,0);assert.equal(state.sample(1200).active,false);
  const robot=createRobot();robot.apply(state.sample(1200));
  assert.equal(robot.root.getObjectByName('right.index.1').rotation.x,.2);
  for(const delivery of ['history','replay']){state.receive(plan('peace'),{delivery},1500);assert.equal(state.sample(2000).handPhase,'preview');assert.equal(state.sample(2000).active,false);}
  state.visibility(false);state.receive(plan('fist'),{delivery:'live'},2100);state.visibility(true);assert.equal(state.sample(2200).handPhase,'preview');
  state.visibility(true,true);state.receive(plan('index_up'),{delivery:'live'},2300);assert.equal(state.sample(2400).active,false);
});
