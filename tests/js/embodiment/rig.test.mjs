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
