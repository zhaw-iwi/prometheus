import test from 'node:test';
import assert from 'node:assert/strict';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
import {normalizePlan} from '../../../src/main/resources/public/valerian/embodiment/model.js';

for(const sign of ['rock','paper','scissor'])test(`articulated ${sign} uses the correct fingers on both mirrored hands`,()=>{
  const robot=createRobot();robot.apply({pose:normalizePlan({motion:{handSign:sign,effector:'both_hands'}}),gesture:1,ambient:0,transition:1});
  for(const hand of ['left','right'])for(const [i,finger] of ['index','middle','ring','little'].entries()) {
    const bent=sign==='rock'||sign==='scissor'&&i>=2;
    for(let joint=0;joint<3;joint++)assert.equal(robot.root.getObjectByName(`${hand}.${finger}.${joint}`).rotation.x>1,bent);
  }
});
test('explicit sign owns its arm independently of simultaneous decorative gesture',()=>{
  const robot=createRobot(), pose=normalizePlan({nonVerbal:{gesture:'EXPLAIN'},motion:{handSign:'paper',effector:'right_hand'}});
  robot.apply({pose,gesture:0,ambient:0,transition:1});const a=robot.root.getObjectByName('right.shoulder').rotation.toArray();
  robot.apply({pose,gesture:1,ambient:0,transition:1});assert.deepEqual(robot.root.getObjectByName('right.shoulder').rotation.toArray(),a);
});

const expressions=['happy','curious','sad','concerned','attentive','uncertain','angry','surprised'];
const mouthVertices=robot=>Array.from(robot.root.getObjectByName('mouth').geometry.attributes.position.array);
function express(robot,type,intensity) {
  robot.apply({pose:normalizePlan({nonVerbal:{facialExpression:{type,intensity}}}),gesture:0,ambient:0});
  return mouthVertices(robot);
}
function mouthDimensions(vertices) {
  const points=[];for(let i=0;i<vertices.length;i+=3)points.push(vertices.slice(i,i+3));
  const halfWidth=Math.max(...points.map(p=>p[0]));
  const centre=points.filter(p=>Math.abs(p[0])<1e-6).map(p=>p[1]);
  const corners=points.filter(p=>Math.abs(Math.abs(p[0])-halfWidth)<1e-6).map(p=>p[1]);
  return {lift:corners.reduce((a,b)=>a+b,0)/corners.length-(Math.min(...centre)+Math.max(...centre))/2,
    opening:Math.max(...centre)-Math.min(...centre)};
}
test('every zero-intensity expression, neutral fallback and reset share a gently upturned mouth',()=>{
  const robot=createRobot(),baseline=mouthVertices(robot),shape=mouthDimensions(baseline);
  assert.ok(shape.lift>0&&shape.lift<0.03);
  for(const face of expressions)assert.deepEqual(express(robot,face,0),baseline,face);
  for(const face of ['neutral','warmNeutral','unknown'])assert.deepEqual(express(robot,face,1),baseline,face);
  express(robot,'sad',1);robot.apply({pose:null,gesture:0,ambient:0});
  assert.deepEqual(mouthVertices(robot),baseline);
});
test('plan intensity blends all mouth shapes from baseline while preserving expression direction',()=>{
  const robot=createRobot(),baseline=mouthVertices(robot),rest=mouthDimensions(baseline),shapes={};
  for(const face of expressions) {
    const full=express(robot,face,1),half=express(robot,face,0.5);shapes[face]=mouthDimensions(full);
    half.forEach((value,i)=>assert.ok(Math.abs(value-(baseline[i]+full[i])/2)<1e-7,face));
  }
  assert.ok(shapes.happy.lift>shapes.curious.lift&&shapes.curious.lift>rest.lift);
  assert.ok(shapes.sad.lift<shapes.concerned.lift&&shapes.concerned.lift<0);
  for(const face of ['attentive','uncertain','angry'])assert.ok(Math.abs(shapes[face].lift)<1e-7,face);
  assert.ok(shapes.surprised.opening>rest.opening*4);
  assert.ok(Math.abs(shapes.surprised.lift)<1e-7);
});

test('right hand up raises only the robot right arm',()=>{
  const robot=createRobot(),pose=normalizePlan({nonVerbal:{gesture:'RIGHT_HAND_UP'}});
  robot.apply({pose,gesture:0,ambient:0});
  const left=robot.root.getObjectByName('left.shoulder').rotation.toArray();
  const right=robot.root.getObjectByName('right.shoulder').rotation.toArray();
  robot.apply({pose,gesture:1,ambient:0});
  assert.deepEqual(robot.root.getObjectByName('left.shoulder').rotation.toArray(),left);
  assert.notDeepEqual(robot.root.getObjectByName('right.shoulder').rotation.toArray(),right);
});
