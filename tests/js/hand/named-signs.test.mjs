import test from 'node:test';
import assert from 'node:assert/strict';
import {NAMED_SIGNS, namedHands, createNamedSignTracker} from '../../../src/main/resources/public/valerian/hand/named-signs.js';

const result = (gesture = 'Thumb_Up', side = 'Right', confidence = .9) => ({
  landmarks: [Array(21).fill({x:.5,y:.5,z:0})], gestures: [[{categoryName:gesture,score:confidence}]],
  handedness: [[{categoryName:side,score:.95}]],
});
test('six detector classes map per hand without renaming the legacy vocabulary', () => {
  assert.equal(Object.keys(NAMED_SIGNS).length, 6);
  for (const [category, sign] of Object.entries(NAMED_SIGNS)) {
    assert.deepEqual(namedHands(result(category))[0], {id:'hand-0',handedness:'right',sign,confidence:.9});
  }
  const two = result(); const second = result('Victory', 'Left');
  for (const key of Object.keys(two)) two[key].push(...second[key]);
  assert.deepEqual(namedHands(two).map(h=>[h.handedness,h.sign]), [['right','thumbs_up'],['left','peace']]);
  two.landmarks.push([]); assert.equal(namedHands(two).length,2);
});
test('unknown, low-confidence, invalid and absent classifications never manufacture a sign', () => {
  for (const category of ['None','ILoveYou','rock','middle_finger','__proto__','constructor','toString']) assert.equal(namedHands(result(category))[0].sign,null);
  for (const confidence of [.1,NaN,Infinity,-1,2]) assert.equal(namedHands(result('Thumb_Up','Left',confidence))[0].sign,null);
  const ambiguous = result(); ambiguous.gestures[0].push({categoryName:'None',score:.99});
  assert.equal(namedHands(ambiguous)[0].sign,null);
  ambiguous.handedness[0][0].score=.3; assert.equal(namedHands(ambiguous)[0].handedness,'unknown');
  assert.deepEqual(namedHands({}),[]);
  assert.deepEqual(namedHands({landmarks:'invalid'}),[]);
  assert.equal(namedHands(result('Thumb_Up',42))[0].handedness,'unknown');
});
test('stability requires three distinct ordered captures over 300 ms and resets on gaps and changes', () => {
  const tracker=createNamedSignTracker('test');
  const at=(ms,frame=result())=>tracker.observe(frame,new Date(100000+ms).toISOString(),.65);
  assert.equal(at(0).stable,false); assert.equal(at(100).stable,false); assert.equal(at(300).stable,true);
  assert.equal(at(300).stable,false); assert.equal(at(2000).stable,false);
  at(2100); assert.equal(at(2300).stable,true);
  assert.equal(at(2400,result('Thumb_Down')).stable,false);
  tracker.reset(); assert.equal(at(2500).stable,false);
  tracker.observe(result(),null,.65); assert.equal(at(2600).stable,false);
  at(2700); const settled=at(2900); assert.equal(settled.stable,true);
  assert.equal(settled.observation.frame.mirrored,false);
  assert.notEqual(at(3100).observation.observationId,settled.observation.observationId);
});
