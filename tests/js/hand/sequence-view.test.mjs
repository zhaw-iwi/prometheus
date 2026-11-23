import {test} from 'node:test';
import assert from 'node:assert/strict';
import {countReading,stepLabel} from '../../../src/main/resources/public/valerian/hand/sequence-view.js';

const fingers=['thumb','index','middle','ring','little'];
function hand(side,n){return {handedness:side,fingers:Object.fromEntries(fingers.map((f,i)=>[f,i<n?0:1])),fingerStates:Object.fromEntries(fingers.map((f,i)=>[f,i<n?'straight':'bent']))};}
test('counts distinguish ten known values from missing, neutral and ambiguous readings',()=>{
  for(let n=0;n<=10;n++)assert.match(countReading({hands:n>5?[hand('right',5),hand('left',n-5)]:[hand('right',n)]}),new RegExp('total '+n+' '));
  const h=hand('right',1);h.fingers.thumb=null;assert.match(countReading({hands:[h]}),/unavailable/);
  h.fingers.thumb=.5;h.fingerStates.thumb='neutral';assert.match(countReading({hands:[h]}),/unavailable/);
  assert.match(countReading({hands:[hand('right',1),hand('right',2)]}),/sides are uncertain/);
  assert.match(countReading(null),/No hands/);
});
test('step labels explain the convention and reference frame',()=>{
  assert.match(stepLabel({kind:'count',count:3,hands:'both'}),/3 extended fingers.*both hands/);
  assert.match(stepLabel({kind:'point',condition:{direction:'up_left'}}),/up left in the agreed frame/);
  assert.match(stepLabel({kind:'point',condition:{target:{}}}),/agreed target/);
  assert.match(stepLabel({kind:'sign',condition:{sign:'ok_ring',handedness:'right'}}),/ok ring/);
});
