import test from 'node:test';import assert from 'node:assert/strict';
import {createTargetTracker} from '../../../src/main/resources/public/valerian/hand/targets.js';
const frame={id:'camera',revision:1,space:'camera_image',mirrored:false};
const detection=(x,type='cup')=>({class:type,score:.8,bbox:[x,30,12,20]});
const time=n=>new Date(100000+n).toISOString();
test('bounded target classes keep positions and monotonic temporary identities',()=>{
  const tracker=createTargetTracker('test');const first=tracker.observe([detection(10),detection(60,'person'),detection(30,'dog')],100,100,frame,time(0));
  assert.deepEqual(first.targets.map(t=>t.type),['cup','person']);assert.equal(first.targets[0].position.x,.16);assert.equal(first.targets[0].expiresAt,time(3000));
  const moved=tracker.observe([detection(12),detection(62,'person')],100,100,frame,time(350));assert.deepEqual(moved.targets.map(t=>t.id),first.targets.map(t=>t.id));
  tracker.observe([],100,100,frame,time(700));assert.notEqual(tracker.observe([detection(12)],100,100,frame,time(1050)).targets[0].id,first.targets[0].id);
  tracker.clear();assert.notEqual(tracker.observe([detection(12)],100,100,frame,time(1400)).targets[0].id,first.targets[0].id);
});
test('crossing, merged detections, frame changes and long gaps cannot inherit old references',()=>{
  const tracker=createTargetTracker('cross');const before=tracker.observe([detection(30),detection(50)],100,100,frame,time(0)).targets.map(t=>t.id);
  const crossing=tracker.observe([detection(38),detection(42)],100,100,frame,time(350));assert.ok(crossing.targets.every(t=>!before.includes(t.id)));
  const merged=tracker.observe([detection(40)],100,100,frame,time(700));assert.ok(crossing.targets.every(t=>t.id!==merged.targets[0].id));
  const changed=tracker.observe([detection(40)],100,100,{...frame,revision:2},time(1050));assert.notEqual(changed.targets[0].id,merged.targets[0].id);
  assert.notEqual(tracker.observe([detection(40)],100,100,{...frame,revision:2},time(4000)).targets[0].id,changed.targets[0].id);
});
test('invalid boxes/classes and low scores cannot fabricate targets',()=>{
  const tracker=createTargetTracker('bounds');const snapshot=tracker.observe([{...detection(0),score:.49},{...detection(0),bbox:[NaN,0,1,1]},detection(0,'dog'),{...detection(0),bbox:[0,0,-1,1]},detection(200)],100,100,frame,time(0));assert.deepEqual(snapshot.targets,[]);
  assert.equal(tracker.observe(Array.from({length:20},(_,i)=>detection(i*4)),100,100,frame,time(350)).targets.length,16);
});
