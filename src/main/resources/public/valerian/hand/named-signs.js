// MediaPipe classes are shape estimates. Handedness is subject-side, not identity.
export const NAMED_SIGNS = Object.freeze({
  Closed_Fist: "fist", Victory: "peace", Open_Palm: "open_palm",
  Thumb_Up: "thumbs_up", Thumb_Down: "thumbs_down", Pointing_Up: "index_up",
});
const score = value => Number.isFinite(value) && value >= 0 && value <= 1 ? value : null;
const best = values => Array.isArray(values) ? [...values].sort((a, b) => (score(b?.score) ?? -1) - (score(a?.score) ?? -1))[0] : null;

export function namedHands(result, threshold = .65) {
  const count = Array.isArray(result?.landmarks) ? Math.min(2, result.landmarks.length) : 0;
  return Array.from({length: count}, (_, index) => {
    const gesture = best(result.gestures?.[index]), side = best((result.handedness ?? result.handednesses)?.[index]);
    const confidence = score(gesture?.score), label = typeof side?.categoryName === 'string' ? side.categoryName.toLowerCase() : null;
    const sign = Object.hasOwn(NAMED_SIGNS, gesture?.categoryName) ? NAMED_SIGNS[gesture.categoryName] : null;
    return {id: `hand-${index}`, handedness: score(side?.score) >= .5 && ["left", "right"].includes(label) ? label : "unknown",
      sign: confidence !== null && confidence >= threshold ? sign : null, confidence};
  });
}

export function createNamedSignTracker(sessionId = crypto.randomUUID()) {
  let sequence = 0, lastKey = null, started = 0, frames = 0, lastTime = -Infinity;
  return {
    reset() { lastKey = null; frames = 0; lastTime = -Infinity; },
    observe(result, ts, threshold) {
      const hands = namedHands(result, threshold), time = Date.parse(ts);
      const key = JSON.stringify(hands.map(h => [h.handedness, h.sign]).sort());
      if (!Number.isFinite(time) || !Number.isFinite(lastTime) || time <= lastTime || time - lastTime > 1000 || key !== lastKey) {
        frames = 0; started = time;
      }
      lastKey = key; lastTime = time; frames++;
      return {stable: Number.isFinite(time) && frames >= 3 && time - started >= 300, frames,
        observation: {schemaVersion: 1, observationId: `${sessionId}:${++sequence}`, source: "valerian.hand.camera", ts,
          frame: {id: `camera-${sessionId}`, revision: 1, space: "camera_image", mirrored: false}, hands}};
    },
  };
}
