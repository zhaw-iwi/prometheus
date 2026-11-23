import test from 'node:test';
import assert from 'node:assert/strict';
import {rayHits,mappedPosition} from '../../../src/main/resources/public/valerian/hand/scene.js';
import {normalizePlan,EmbodimentState} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
import * as T from '../../../src/main/resources/public/valerian/embodiment/vendor/three.module.js';
const frame={id:'scene:example',revision:1,space:'virtual_scene',mirrored:false};
const action=position=>({kind:'point',effector:'left_hand',target:{id:'example:display',revision:1,frame},resolved:{label:'Display',position,frame}});
const plan=a=>({motion:{handAction:{schemaVersion:1,action:a}}});
test('image rays select regions only in front, with explicit aspect correction',()=>{
  const ray={origin:{x:.5,y:.5},vector:{x:1,y:0},aspectRatio:16/9},p={x:.9,y:.5,z:0},e={x:.1,y:.2,z:0};
  assert.equal(rayHits(ray,p,e),true);assert.equal(rayHits({...ray,vector:{x:-1,y:0}},p,e),false);
  assert.equal(rayHits(ray,{...p,y:.2},e),false);
  const regions=[p,{...p,x:.7}];assert.equal(regions.filter(p=>rayHits(ray,p,e)).length,2,'Aligned destinations stay ambiguous');
  assert.deepEqual(mappedPosition([8,0,-4,0,-3,3],p),{x:3.2,y:1.5,z:0});
});
test('target output needs a bounded backend-resolved snapshot and its separate opt-in',()=>{
  const a=action({x:3.2,y:1.5,z:0});assert.equal(normalizePlan(plan(a)).sign,'point');
  assert.equal(normalizePlan(plan(a),['motion.handAction'],['hand.pointing.v1']).action,null);
  assert.equal(normalizePlan(plan(a),['motion.handAction'],['hand.pointing.v1','hand.targets.v1']).sign,'point');
  const unresolved=structuredClone(a);delete unresolved.resolved;assert.equal(normalizePlan(plan(unresolved)).action,null);
  for(const position of [{x:Infinity,y:1,z:0},{x:0,y:7,z:0},{x:0,y:1,z:4}])assert.equal(normalizePlan(plan(action(position))).action,null);
});
test('both arms point toward registered three-dimensional virtual positions and history does not replay',()=>{
  for(const side of ['left','right'])for(const position of [{x:3.2,y:1.5,z:0},{x:-3,y:2,z:1},{x:2,y:3.5,z:-1}]){
    const a=action(position);a.effector=side+'_hand';const robot=createRobot(),pose=normalizePlan(plan(a));robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1},0);robot.root.updateMatrixWorld(true);
    const shoulder=robot.root.getObjectByName(side+'.shoulder').getWorldPosition(new T.Vector3()),base=robot.root.getObjectByName(side+'.index').getWorldPosition(new T.Vector3()),tip=robot.root.getObjectByName(side+'.index.tip').getWorldPosition(new T.Vector3());
    assert.ok(tip.clone().sub(base).normalize().dot(new T.Vector3(position.x,position.y,position.z).sub(tip).normalize())>.999999,'The index ray must intersect the destination, including its offset from the shoulder');
    const state=new EmbodimentState();state.visibility(true);state.receive(plan(a),{delivery:'history'},0);assert.equal(state.sample(500).handPhase,'preview');
    state.receive(plan(a),{delivery:'live'},0);assert.equal(state.sample(1200).handPhase,'released');
  }
});
