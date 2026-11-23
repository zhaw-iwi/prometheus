import test from 'node:test';
import assert from 'node:assert/strict';
import {namedHandResult as handResult} from '../../fixtures/hand-landmarks.mjs';
import {namedHands,createNamedSignTracker} from '../../../src/main/resources/public/valerian/hand/named-signs.js';
import {normalizePlan} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
import {handAction} from '../../../src/main/resources/public/valerian/hand/actions.js';

const patterns={shaka:{thumb:0,index:1,middle:1,ring:1,little:0},horns:{thumb:1,index:0,middle:1,ring:1,little:0},middle_finger:{thumb:1,index:1,middle:0,ring:1,little:1}};
test('derived shapes use all five classified fingers and carry no fabricated confidence',()=>{
  for(const [sign,pattern] of Object.entries(patterns))for(const side of ['Left','Right']) {
    const result=handResult(pattern,side);
    assert.equal(namedHands(result,.99,true)[0].sign,sign);assert.equal(namedHands(result,.99,true)[0].confidence,null);
    assert.equal(namedHands(result,.65,false)[0].sign,null,'Older profiles do not adopt the new vocabulary');
    for(const mirror of [1,-1]) {
      const transformed=structuredClone(result);
      transformed.worldLandmarks[0]=transformed.worldLandmarks[0].map(p=>({x:2+3*p.z,y:4+mirror*3*p.x,z:6+3*p.y}));
      transformed.landmarks[0]=transformed.landmarks[0].map(p=>({...p,x:1-p.x}));
      assert.equal(namedHands(transformed,.99,true)[0].sign,sign,'Rotation, scale and mirroring preserve extension/opposition');
    }
    for(const finger of Object.keys(pattern)) {
      const neutral=handResult({...pattern,[finger]:.5},side);assert.notEqual(namedHands(neutral,.65,true)[0].sign,sign);
    }
    result.worldLandmarks[0][4]=null;assert.equal(namedHands(result,.65,true)[0].sign,null);
  }
});
test('pose and named evidence share one capture identity, stabilize separately and never merge frames',()=>{
  const tracker=createNamedSignTracker('poses');
  const result=handResult(patterns.shaka,'Right'),other=handResult(patterns.horns,'Left');
  for(const key of ['landmarks','worldLandmarks','handedness'])result[key].push(...other[key]);
  const at=ms=>tracker.observe(result,new Date(100000+ms).toISOString(),.65,true);
  at(0);at(150);const capture=at(300);
  assert.equal(capture.poseStable,true);assert.equal(capture.stable,true);
  assert.equal(capture.pose.observationId,capture.observation.observationId);assert.deepEqual(capture.pose.frame,capture.observation.frame);
  assert.equal(capture.pose.hands.length,2);assert.deepEqual(capture.observation.hands.map(h=>h.sign),['shaka','horns']);
  result.worldLandmarks[0][4]=null;assert.equal(at(450).poseStable,false);
  tracker.reset();assert.equal(at(600).poseStable,false);
});
test('all ten requested finger values reach distinct joints and capability tags gate custom output',()=>{
  const hands=[{side:'left',fingers:{thumb:0,index:.25,middle:.5,ring:.75,little:1}},{side:'right',fingers:{thumb:1,index:.75,middle:.5,ring:.25,little:0}}];
  const plan={motion:{handAction:{schemaVersion:1,action:{kind:'pose',hands}}}};
  assert.equal(normalizePlan(plan,['motion.handAction'],[]).sign,'');
  const pose=normalizePlan(plan,['motion.handAction'],['hand.poses.v2']),robot=createRobot();
  robot.apply({pose,gesture:0,ambient:0,transition:1,handBlend:1});
  for(const hand of hands)for(const [finger,bend] of Object.entries(hand.fingers)) {
    const joint=robot.root.getObjectByName(`${hand.side}.${finger}.1`);
    assert.ok(Math.abs(joint.rotation.x-bend*(finger==='thumb'?1:1.5))<1e-12,hand.side+' '+finger);
  }
  const invalid=structuredClone(plan.motion.handAction);delete invalid.action.hands[0].fingers.index;
  assert.throws(()=>handAction(invalid));
  const duplicate=structuredClone(plan.motion.handAction);duplicate.action.hands[1].side='left';assert.throws(()=>handAction(duplicate));
});
