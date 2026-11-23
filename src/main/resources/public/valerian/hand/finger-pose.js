// MediaPipe world coordinates are isotropic: angles do not depend on image aspect ratio.
// This is a geometric estimate, not calibrated joint tracking or an occlusion detector.
export const FINGER_CHAINS = Object.freeze({
  thumb: [1, 2, 3, 4],
  index: [0, 5, 6, 7, 8],
  middle: [0, 9, 10, 11, 12],
  ring: [0, 13, 14, 15, 16],
  little: [0, 17, 18, 19, 20],
});

function inFrame(point) {
  // Hand landmarks do not supply per-finger visibility. The SDK may expose an
  // unset visibility field as zero; it cannot be used as an occlusion gate.
  return point && Number.isFinite(point.x) && Number.isFinite(point.y)
    && point.x >= 0 && point.x <= 1 && point.y >= 0 && point.y <= 1;
}

export function fingerBend(points, chain) {
  if (!chain.every(index => points?.[index] && ["x", "y", "z"].every(axis => Number.isFinite(points[index][axis])))) return null;
  let total = 0;
  for (let joint = 1; joint < chain.length - 1; joint++) {
    const [a, b, c] = chain.slice(joint - 1, joint + 2).map(index => points[index]);
    const incoming = [b.x - a.x, b.y - a.y, b.z - a.z];
    const outgoing = [c.x - b.x, c.y - b.y, c.z - b.z];
    const denominator = Math.hypot(...incoming) * Math.hypot(...outgoing);
    if (denominator < 1e-12) return null;
    const cosine = incoming.reduce((sum, value, axis) => sum + value * outgoing[axis], 0) / denominator;
    // Each joint contributes 0 when straight and 1 at a bend of 90 degrees or more.
    total += Math.min(1, Math.acos(Math.max(-1, Math.min(1, cosine))) / (Math.PI / 2));
  }
  return Math.round(total / (chain.length - 2) * 1000) / 1000;
}

export function classifyFingerBend(bend) {
  if (!Number.isFinite(bend) || bend < 0 || bend > 1) return null;
  if (bend < .4) return "straight";
  if (bend > .55) return "bent";
  return "neutral";
}

export function estimateHandPoses(result) {
  return (Array.isArray(result?.landmarks) ? result.landmarks : []).slice(0, 2).map((image, index) => {
    const category = result.handedness?.[index]?.[0];
    const label = typeof category?.categoryName === 'string' ? category.categoryName.toLowerCase() : null;
    const fingers = Object.fromEntries(Object.entries(FINGER_CHAINS).map(([name, chain]) => [name,
      chain.every(point => inFrame(image?.[point])) ? fingerBend(result.worldLandmarks?.[index], chain) : null]));
    return {
      handedness: ["left", "right"].includes(label) ? label : "unknown",
      // This score describes left/right classification, not finger accuracy or visibility.
      handednessConfidence: Number.isFinite(category?.score) ? Math.max(0, Math.min(1, category.score)) : null,
      fingers,
      fingerStates: Object.fromEntries(Object.entries(fingers).map(([name, bend]) => [name, classifyFingerBend(bend)])),
    };
  });
}

// Named shapes describe finger extension and thumb opposition, which are not
// identical to the legacy mean joint-curvature reading above. A straight thumb
// can be folded across the palm. Keep those measurements separate and explicit.
export function namedFingerStates(result, index, hand) {
  const points = result?.worldLandmarks?.[index];
  if (!hand || Object.values(hand.fingers).some(value => !Number.isFinite(value))) return null;
  const vector = (a, b) => ['x', 'y', 'z'].map(axis => points[b][axis] - points[a][axis]);
  const across = vector(5, 17), thumb = vector(2, 4);
  const widthSquared = across.reduce((sum, value) => sum + value * value, 0);
  if (!Number.isFinite(widthSquared) || widthSquared < 1e-12) return null;
  const opposition = across.reduce((sum, value, axis) => sum + value * thumb[axis], 0) / widthSquared;
  return {
    thumb: opposition < -.2 ? 'straight' : opposition > .15 ? 'bent' : 'neutral',
    ...Object.fromEntries(Object.entries(FINGER_CHAINS).filter(([name]) => name !== 'thumb')
      .map(([name, chain]) => {
        const proximal = fingerBend(points, chain.slice(1, 4));
        const distal = fingerBend(points, chain.slice(2, 5));
        // A folded distal joint must not be averaged away by a straight joint.
        const state = proximal === null || distal === null ? null
          : Math.max(proximal, distal) > .55 ? 'bent'
          : (proximal + distal) / 2 < .4 ? 'straight' : 'neutral';
        return [name, state];
      })),
  };
}
