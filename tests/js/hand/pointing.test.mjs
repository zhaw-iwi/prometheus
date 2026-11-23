import test from 'node:test';
import assert from 'node:assert/strict';
import * as T from '../../../src/main/resources/public/valerian/embodiment/vendor/three.module.js';
import {pointingResult} from '../../fixtures/hand-landmarks.mjs';
import {createNamedSignTracker} from '../../../src/main/resources/public/valerian/hand/named-signs.js';
import {directionSector,planeDirections,robotFrame,robotDirection} from '../../../src/main/resources/public/valerian/hand/pointing.js';
import {normalizePlan,EmbodimentState} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
const at=(tracker,result,ms,aspect=1)=>tracker.observe(result,new Date(100000+ms).toISOString(),.65,true,true,aspect,true);

test('eight aspect-corrected directions share capture identity and timed stability',()=>{
  for(const [i,direction] of planeDirections.entries())for(const aspect of [1,4/3,16/9]){
    const tracker=createNamedSignTracker('point'),result=pointingResult(i*45,'Right',aspect);
    assert.equal(at(tracker,result,0,aspect).pointStable,false);at(tracker,result,150,aspect);
    const sample=at(tracker,result,300,aspect);assert.equal(sample.pointStable,true);assert.equal(sample.pointing.direction,direction);
    assert.equal(sample.pointing.observationId,sample.pose.observationId);assert.deepEqual(sample.pointing.frame,sample.observation.frame);
    assert.equal(sample.pointing.ray.aspectRatio,aspect);
    assert.equal(at(tracker,result,300,aspect).pointStable,false,'Repeated timestamp resets dwell');
  }
});

test('sector edges, foreshortening, unknown joints and competing hands cannot establish a direction',()=>{
  assert.equal(directionSector(Math.cos(18*Math.PI/180),Math.sin(18*Math.PI/180)),'right');
  for(const angle of [19,22.5,26])assert.equal(directionSector(Math.cos(angle*Math.PI/180),Math.sin(angle*Math.PI/180)),null);
  assert.equal(directionSector(0,0),null);
  const tracker=createNamedSignTracker('bad');assert.equal(at(tracker,pointingResult(22.5),0).pointing.validity,'boundary');
  const unknown=pointingResult();unknown.worldLandmarks[0][8]=null;assert.equal(at(tracker,unknown,150).pointing.direction,null);
  const two=pointingResult(),other=pointingResult(180,'Left');for(const key of ['landmarks','worldLandmarks','handedness'])two[key].push(...other[key]);
  assert.equal(at(tracker,two,300).pointing.validity,'ambiguous');
  const depth=pointingResult();depth.worldLandmarks[0]=depth.worldLandmarks[0].map(p=>({x:p.x*.01,y:p.y,z:-p.x}));
  depth.landmarks[0]=depth.worldLandmarks[0].map(p=>({x:.5+p.x,y:.5+p.y,z:p.z}));
  assert.equal(at(tracker,depth,450).pointing.validity,'foreshortened');
  const conflicting=pointingResult(45);conflicting.gestures=[[{categoryName:'Thumb_Down',score:.95}]];
  const classified=at(tracker,conflicting,600);assert.equal(classified.pointing.direction,'down_right');
  assert.equal(classified.observation.hands[0].sign,null,'Known straight index contradicts a closed-finger thumb sign');
});

test('capture-frame revision changes with aspect, while geometric reflection reverses only image-left/right',()=>{
  const tracker=createNamedSignTracker('frame'),result=pointingResult();let sample=at(tracker,result,0);
  assert.equal(sample.pointing.direction,'right');assert.equal(sample.pointing.frame.revision,1);
  result.landmarks[0]=result.landmarks[0].map(p=>({...p,x:1-p.x}));sample=at(tracker,result,150);
  assert.equal(sample.pointing.direction,'left');assert.equal(sample.pointing.handedness,'right');
  sample=at(tracker,result,300,4/3);assert.equal(sample.pointing.frame.revision,2);assert.equal(sample.pointStable,false);
  tracker.newFrame();sample=at(tracker,result,450,4/3);assert.equal(sample.pointing.frame.revision,3);assert.equal(sample.pointStable,false);
});

test('robot-relative output couples arm and index across both hands and releases without replay',()=>{
  for(const direction of planeDirections)for(const side of ['left','right']){
    const action={kind:'point',effector:side+'_hand',direction,frame:robotFrame};
    const plan={motion:{handAction:{schemaVersion:1,action}}};
    assert.equal(normalizePlan(plan,['motion.handAction'],[]).action,null);
    const pose=normalizePlan(plan,['motion.handAction'],['hand.pointing.v1']),robot=createRobot();
    robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1});robot.root.updateMatrixWorld(true);
    const tip=robot.root.getObjectByName(side+'.index.tip').getWorldPosition(new T.Vector3());
    const base=robot.root.getObjectByName(side+'.index').getWorldPosition(new T.Vector3());
    const shoulder=robot.root.getObjectByName(side+'.shoulder').getWorldPosition(new T.Vector3());
    const d=robotDirection(direction),axis=new T.Vector3(d.x,d.y,d.z);
    assert.ok(tip.clone().sub(base).normalize().dot(axis)>.999,side+' index '+direction);
    assert.ok(tip.clone().sub(shoulder).normalize().dot(axis)>.99,side+' arm '+direction);
    const state=new EmbodimentState();state.visibility(true);state.receive(plan,{delivery:'live'},0);
    assert.equal(state.sample(500).handPhase,'holding');assert.equal(state.sample(1200).handPhase,'released');
    state.receive(plan,{delivery:'history'},2000);assert.equal(state.sample(2500).handPhase,'preview');
  }
  const bad={motion:{handAction:{schemaVersion:1,action:{kind:'point',effector:'right_hand',direction:'forward',frame:robotFrame}}}};
  assert.equal(normalizePlan(bad).action,null);bad.motion.handAction.action.direction='right';bad.motion.handAction.action.frame={...robotFrame,revision:2};assert.equal(normalizePlan(bad).action,null);
});
