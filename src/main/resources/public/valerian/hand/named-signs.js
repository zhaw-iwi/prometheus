import {estimateHandPoses, namedFingerStates} from './finger-pose.js';
import {derivedSign} from './patterns.js';
import {measureHandGeometry,ringSign,geometryStableWithin} from './geometry.js';
import {estimatePointing} from './pointing.js';
// MediaPipe classes are shape estimates. Handedness is subject-side, not identity.
export const NAMED_SIGNS = Object.freeze({
  Closed_Fist: "fist", Victory: "peace", Open_Palm: "open_palm",
  Thumb_Up: "thumbs_up", Thumb_Down: "thumbs_down", Pointing_Up: "index_up",
});
const score = value => Number.isFinite(value) && value >= 0 && value <= 1 ? value : null;
const best = values => Array.isArray(values) ? [...values].sort((a, b) => (score(b?.score) ?? -1) - (score(a?.score) ?? -1))[0] : null;

function classifyHands(result, threshold, poses, relational, aspectRatio, previousRings=new Set()) {
  const poseHands=estimateHandPoses(result);
  const hands=poseHands.map((hand,index)=>{
    const gesture = best(result.gestures?.[index]), side = best((result.handedness ?? result.handednesses)?.[index]);
    const confidence = score(gesture?.score), label = typeof side?.categoryName === 'string' ? side.categoryName.toLowerCase() : null;
    const sign = Object.hasOwn(NAMED_SIGNS, gesture?.categoryName) ? NAMED_SIGNS[gesture.categoryName] : null;
    hand.id=`hand-${index}`;hand.handedness=score(side?.score)>=.5&&['left','right'].includes(label)?label:'unknown';
    delete hand.handednessConfidence;
    const states=poses?namedFingerStates(result,index,hand):null;
    const contradicted=states?.index==='straight'&&['thumbs_up','thumbs_down'].includes(sign);
    if(poses&&relational)hand.geometry=measureHandGeometry(result,index,hand,aspectRatio);
    const derived=poses&&relational&&ringSign(hand,states,hand.geometry,previousRings.has(hand.id+'/'+hand.handedness))?'ok_ring':poses?derivedSign(hand,states):null;
    return {id:hand.id,handedness:hand.handedness,
      sign: derived || (!contradicted && confidence !== null && confidence >= threshold ? sign : null), confidence:derived?null:confidence};
  });
  return {hands,poseHands};
}
export function namedHands(result, threshold = .65, poses = false, relational=false, aspectRatio=1) {
  return classifyHands(result,threshold,poses,relational,aspectRatio).hands;
}

export function createNamedSignTracker(sessionId = crypto.randomUUID()) {
  let sequence = 0, lastKey = null, started = 0, frames = 0, lastTime = -Infinity;
  let poseKey=null,poseStarted=0,poseFrames=0,poseAnchor=[],previousRings=new Set();
  let pointKey=null,pointStarted=0,pointFrames=0,frameAspect=null,frameRevision=1;
  return {
    newFrame() {frameRevision++;this.reset();},
    reset() { lastKey = null; frames = 0; lastTime = -Infinity; poseKey=null;poseFrames=0;poseAnchor=[];previousRings.clear();pointKey=null;pointFrames=0; },
    observe(result, ts, threshold, poses=false, relational=false, aspectRatio=1,pointing=false) {
      const time=Date.parse(ts),gap=!Number.isFinite(time)||!Number.isFinite(lastTime)||time<=lastTime||time-lastTime>1000;
      if(frameAspect!==null&&frameAspect!==aspectRatio)frameRevision++;
      frameAspect=aspectRatio;
      if(gap)previousRings.clear();
      const {hands,poseHands}=classifyHands(result,threshold,poses,relational,aspectRatio,previousRings);
      previousRings=new Set(hands.filter(h=>h.sign==='ok_ring').map(h=>h.id+'/'+h.handedness));
      const nextPoseKey=JSON.stringify(poseHands.map(h=>[h.handedness,h.fingerStates]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b))));
      const moved=relational&&(poseAnchor.length!==poseHands.length||poseHands.some((h,i)=>h.handedness!==poseAnchor[i]?.handedness||!geometryStableWithin(poseAnchor[i]?.geometry,h.geometry)));
      if(gap||nextPoseKey!==poseKey||moved){poseStarted=time;poseFrames=0;poseAnchor=poseHands;}
      poseKey=nextPoseKey;poseFrames++;
      const key = JSON.stringify(hands.map(h => [h.handedness, h.sign]).sort());
      if (!Number.isFinite(time) || !Number.isFinite(lastTime) || time <= lastTime || time - lastTime > 1000 || key !== lastKey) {
        frames = 0; started = time;
      }
      lastKey = key; lastTime = time; frames++;
      const observation={schemaVersion: 1, observationId: `${sessionId}:${++sequence}`, source: "valerian.hand.camera", ts,
          frame: {id: `camera-${sessionId}`, revision: frameRevision, space: "camera_image", mirrored: false}, hands};
      const estimate=pointing?estimatePointing(result,poseHands,aspectRatio):null;
      const nextPointKey=estimate?JSON.stringify([frameRevision,estimate.handId,estimate.handedness,estimate.direction,estimate.validity]):null;
      if(gap||nextPointKey!==pointKey){pointStarted=time;pointFrames=0;}
      pointKey=nextPointKey;pointFrames++;
      const {hands:ignored,...header}=observation;
      return {stable: Number.isFinite(time) && frames >= 3 && time - started >= 300, frames,observation,
        pointing:estimate?{...header,...estimate}:null,pointFrames,pointStable:!!estimate&&Number.isFinite(time)&&pointFrames>=3&&time-pointStarted>=300,
        poseStable:Number.isFinite(time)&&poseFrames>=3&&time-poseStarted>=300,poseFrames,
        pose:{...observation,schemaVersion:2,hands:poseHands}};
    },
  };
}
