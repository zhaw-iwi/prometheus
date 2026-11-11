import test from "node:test";
import assert from "node:assert/strict";
import { estimateHandPoses, fingerBend, classifyFingerBend, FINGER_CHAINS } from "../../../src/main/resources/public/valerian/hand/finger-pose.js";
import { handResult } from "../../fixtures/hand-landmarks.mjs";

test("bend is continuous from straight to curled for every finger, including the thumb", () => {
  for (const bend of [0, .2, .5, .8, 1]) {
    const result = handResult(Object.fromEntries(Object.keys(FINGER_CHAINS).map(name => [name, bend])));
    assert.deepEqual(Object.values(estimateHandPoses(result)[0].fingers), Array(5).fill(bend));
  }
});

test("classification has an inclusive neutral band and keeps unavailable values separate", () => {
  for (const [value, state] of [[0, "straight"], [.25, "straight"], [.399, "straight"], [.4, "neutral"],
    [.55, "neutral"], [.551, "bent"], [1, "bent"]]) assert.equal(classifyFingerBend(value), state);
  for (const value of [null, undefined, NaN, Infinity, -Infinity, -.1, 1.1, "0.2", true]) {
    assert.equal(classifyFingerBend(value), null);
  }
});

test("each hand carries client classifications of the same rounded values that are sent", () => {
  const [hand] = estimateHandPoses(handResult({ thumb: .3996, index: .399, middle: .55, ring: .551, little: 1 }));
  assert.deepEqual(hand.fingers, { thumb: .4, index: .399, middle: .55, ring: .551, little: 1 });
  assert.deepEqual(hand.fingerStates, { thumb: "neutral", index: "straight", middle: "neutral", ring: "bent", little: "bent" });
});

test("3D rotation, translation, scaling and image aspect ratio do not change world-space bend", () => {
  const result = handResult({ thumb: .2, index: .4, middle: .6, ring: .8, little: 1 });
  const before = estimateHandPoses(result)[0].fingers;
  result.worldLandmarks[0] = result.worldLandmarks[0].map(p => ({ x: 2 + 3 * p.z, y: 4 + 3 * p.x, z: 6 + 3 * p.y }));
  result.landmarks[0] = result.landmarks[0].map(p => ({ ...p, y: p.y / 2 }));
  assert.deepEqual(estimateHandPoses(result)[0].fingers, before);
});

test("missing, off-frame, degenerate and nonfinite fingers are unavailable", () => {
  const result = handResult();
  result.landmarks[0][4].x = 1.1;
  result.landmarks[0][8].y = -.1;
  result.landmarks[0][12] = null;
  result.worldLandmarks[0][16].x = NaN;
  result.worldLandmarks[0][20] = result.worldLandmarks[0][19];
  assert.deepEqual(Object.values(estimateHandPoses(result)[0].fingers), Array(5).fill(null));
  assert.deepEqual(Object.values(estimateHandPoses(result)[0].fingerStates), Array(5).fill(null));
  assert.equal(fingerBend(null, FINGER_CHAINS.thumb), null);
  assert.equal(estimateHandPoses({ landmarks: result.landmarks })[0].fingers.thumb, null);
});

test("the SDK's default zero visibility is not misread as proof of occlusion", () => {
  const result = handResult({ index: .75 });
  result.landmarks[0].forEach(point => { point.visibility = 0; });
  assert.equal(estimateHandPoses(result)[0].fingers.index, .75);
});

test("hands need no named gesture and remain separate; absent hands have no invented zero readings", () => {
  const right = handResult({ index: .25 }, "Right"), left = handResult({ index: .75 }, "Left");
  const result = Object.fromEntries(Object.keys(right).map(key => [key, [...right[key], ...left[key]]]));
  const hands = estimateHandPoses(result);
  assert.equal(hands[0].handedness, "right"); assert.equal(hands[0].fingers.index, .25);
  assert.equal(hands[1].handedness, "left"); assert.equal(hands[1].fingers.index, .75);
  assert.equal(hands[0].fingerStates.index, "straight"); assert.equal(hands[1].fingerStates.index, "bent");
  assert.deepEqual(estimateHandPoses({}), []);
  assert.deepEqual(estimateHandPoses(null), []);
});
