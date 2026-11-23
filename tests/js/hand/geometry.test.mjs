import test from 'node:test';
import assert from 'node:assert/strict';
import * as T from '../../../src/main/resources/public/valerian/embodiment/vendor/three.module.js';
import {ringResult} from '../../fixtures/hand-landmarks.mjs';
import {estimateHandPoses} from '../../../src/main/resources/public/valerian/hand/finger-pose.js';
import {measureHandGeometry,geometryStableWithin} from '../../../src/main/resources/public/valerian/hand/geometry.js';
import {namedHands,createNamedSignTracker} from '../../../src/main/resources/public/valerian/hand/named-signs.js';
import {handAction} from '../../../src/main/resources/public/valerian/hand/actions.js';
import {directionAngles} from '../../../src/main/resources/public/valerian/hand/relations.js';
import {normalizePlan} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
const geometry=(r,aspect=1)=>measureHandGeometry(r,0,estimateHandPoses(r)[0],aspect);
const wrap=action=>({motion:{handAction:{schemaVersion:1,action}}});

test('projected gap is scale/translation/aspect invariant, mirroring changes the index axis',()=>{
  const original=ringResult(.1),before=geometry(original);assert.equal(before.tipDistances.thumb_index,.1);
  for(const ratio of [1,4/3,16/9]) {
    const result=structuredClone(original);result.landmarks[0]=result.landmarks[0].map(p=>({...p,x:(p.x-.3)*.5/ratio+.3,y:(p.y-.3)*.5+.3}));
    const after=geometry(result,ratio);assert.equal(after.tipDistances.thumb_index,.1);assert.deepEqual(after.spreads,before.spreads);
  }
  const reflected=structuredClone(original);reflected.landmarks[0]=reflected.landmarks[0].map(p=>({...p,x:1-p.x}));
  const after=geometry(reflected);assert.equal(after.tipDistances.thumb_index,.1);
  assert.equal(after.axes.index_axis.x,-before.axes.index_axis.x);assert.equal(after.axes.index_axis.y,before.axes.index_axis.y);
  assert.equal(before.axes.palm_normal,null,'Face-on normal has no reliable projected direction');
  const missing=structuredClone(original);missing.worldLandmarks[0][4]=null;
  const unknown=geometry(missing);assert.equal(unknown.tipDistances.thumb_index,null);assert.equal(unknown.axes.thumb_axis,null);
  assert.equal(unknown.tipDistances.middle_ring,before.tipDistances.middle_ring);
  assert.equal(geometryStableWithin(before,unknown),false);
});

test('ring requires proximity and three extended fingers, with bounded hysteresis and reset',()=>{
  assert.equal(namedHands(ringResult(),.99,true,true)[0].sign,'ok_ring');
  assert.equal(namedHands(ringResult(),.99,true,false)[0].sign,null);
  assert.notEqual(namedHands(ringResult(.25),.99,true,true)[0].sign,'ok_ring');
  const missing=ringResult();missing.worldLandmarks[0][4]=null;assert.equal(namedHands(missing,.99,true,true)[0].sign,null);
  const tracker=createNamedSignTracker('ring');const at=(gap,ms)=>tracker.observe(ringResult(gap),new Date(100000+ms).toISOString(),.99,true,true);
  at(.17,0);at(.20,150);const held=at(.20,300);assert.equal(held.stable,true);assert.equal(held.observation.hands[0].sign,'ok_ring');
  assert.equal(held.observation.hands[0].confidence,null);assert.equal(held.pose.hands[0].geometry.tipDistances.thumb_index,.2);
  assert.equal(at(.23,450).observation.hands[0].sign,null);
  assert.equal(at(.20,2000).observation.hands[0].sign,null,'A new capture interval must re-enter below .18');
});

test('both rendered rings close their tips and reset opposition on the next pose',()=>{
  const robot=createRobot();robot.apply({pose:normalizePlan(wrap({kind:'sign',sign:'ok_ring',effector:'both_hands'})),gesture:0,ambient:0,transition:1,handBlend:1});
  robot.root.updateMatrixWorld(true);
  for(const side of ['left','right']) {
    const a=robot.root.getObjectByName(side+'.thumb.tip').getWorldPosition(new T.Vector3());
    const b=robot.root.getObjectByName(side+'.index.tip').getWorldPosition(new T.Vector3());
    assert.ok(a.distanceTo(b)/(.195*1.3)<.05,side+' ring gap');
  }
  robot.apply({pose:normalizePlan(wrap({kind:'sign',sign:'open_palm',effector:'both_hands'})),gesture:0,ambient:0,transition:1,handBlend:1});
  for(const side of ['left','right'])assert.equal(robot.root.getObjectByName(side+'.thumb').rotation.x,0);
});

test('supported spread and index orientation are rendered together; incompatible requests reject',()=>{
  const hand={side:'left',fingers:{thumb:1,index:0,middle:0,ring:1,little:1},relations:[
    {kind:'spread',fingers:['index','middle'],operator:'gt',value:.1},{kind:'spread',fingers:['index','middle'],operator:'lt',value:.25},
    {kind:'orientation',axis:'index_axis',direction:'up_right',toleranceDegrees:10}]};
  const plan=wrap({kind:'pose',hands:[hand]});assert.equal(normalizePlan(plan,['motion.handAction'],['hand.poses.v2']).sign,'');
  const pose=normalizePlan(plan,['motion.handAction'],['hand.poses.v2','hand.geometry.v1']),robot=createRobot();
  robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1});robot.root.updateMatrixWorld(true);
  const axis=name=>robot.root.getObjectByName('left.'+name+'.tip').getWorldPosition(new T.Vector3())
    .sub(robot.root.getObjectByName('left.'+name).getWorldPosition(new T.Vector3())).normalize();
  assert.ok(axis('index').dot(new T.Vector3(Math.SQRT1_2,Math.SQRT1_2,0))>.999);
  const spread=axis('index').angleTo(axis('middle'))/Math.PI;assert.ok(spread>.1&&spread<.25);
  for(const mutate of [h=>h.fingers.index=.5,h=>h.relations[2].axis='palm_normal',h=>h.relations[1].value=.05,h=>h.relations[0].value=.3]) {
    const bad=structuredClone(plan.motion.handAction);mutate(bad.action.hands[0]);assert.throws(()=>handAction(bad));
  }
});

test('both hand orientations preserve all eight projected directions',()=>{
  for(const side of ['left','right'])for(const [direction,angle] of Object.entries(directionAngles)) {
    const robot=createRobot(),pose=normalizePlan(wrap({kind:'pose',hands:[{side,
      fingers:{thumb:1,index:0,middle:0,ring:1,little:1},relations:[
        {kind:'spread',fingers:['index','middle'],operator:'gt',value:.1},
        {kind:'orientation',axis:'index_axis',direction,toleranceDegrees:5}]}]}));
    robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1});robot.root.updateMatrixWorld(true);
    const axis=robot.root.getObjectByName(side+'.index.tip').getWorldPosition(new T.Vector3())
      .sub(robot.root.getObjectByName(side+'.index').getWorldPosition(new T.Vector3())).normalize();
    assert.ok(axis.dot(new T.Vector3(Math.cos(angle),-Math.sin(angle),0))>.999,side+' '+direction);
  }
});
