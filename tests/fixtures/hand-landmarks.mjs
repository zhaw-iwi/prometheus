// Deterministic geometry, not photographs or evidence of model accuracy.
export function handResult(bends = {}, side = "Right") {
  const chains = { thumb: [1, 2, 3, 4], index: [0, 5, 6, 7, 8],
    middle: [0, 9, 10, 11, 12], ring: [0, 13, 14, 15, 16], little: [0, 17, 18, 19, 20] };
  const points = Array.from({ length: 21 }, () => ({ x: 0, y: 0, z: 0 }));
  for (const [finger, chain] of Object.entries(chains)) {
    let x = 0, y = 0;
    chain.forEach((index, step) => {
      if (step) {
        const angle = (step - 1) * (bends[finger] || 0) * Math.PI / 2;
        x += .025 * Math.cos(angle); y += .025 * Math.sin(angle);
      }
      points[index] = { x, y, z: 0 };
    });
  }
  return { worldLandmarks: [points], landmarks: [points.map(p => ({ x: .4 + p.x, y: .4 + p.y, z: p.z }))],
    handedness: [[{ categoryName: side, score: .95 }]], gestures: [] };
}
