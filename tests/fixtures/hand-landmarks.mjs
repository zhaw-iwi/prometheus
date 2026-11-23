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

// Anatomical palm separation for opposition/extension tests; still synthetic.
export function namedHandResult(bends = {}, side = 'Right') {
  const result = handResult(bends, side), points = result.worldLandmarks[0];
  for (const [i, name] of ['index','middle','ring','little'].entries()) {
    const root = 5 + i * 4, bend = bends[name] ?? 0;
    let x = -.03 + .02 * i, y = .05;
    for (let step = 0; step < 4; step++) {
      if (step) { const angle = (step - 1) * bend * Math.PI / 2; x += .025 * Math.sin(angle); y += .025 * Math.cos(angle); }
      points[root + step] = {x,y,z:0};
    }
  }
  points[1] = {x:-.025,y:.01,z:0};points[2] = {x:-.04,y:.025,z:0};
  const reach = -.04 + .08 * (bends.thumb ?? 0);
  points[3] = {x:points[2].x+reach/2,y:.04,z:0};points[4] = {x:points[2].x+reach,y:.055,z:0};
  result.landmarks[0] = points.map(p=>({x:.5+p.x,y:.4+p.y,z:p.z}));
  return result;
}

export function ringResult(gap=.1,side='Right',aspectRatio=1) {
  const result=namedHandResult({thumb:1,index:.7,middle:0,ring:0,little:0},side),p=result.landmarks[0];
  const distance=(a,b)=>Math.hypot((p[a].x-p[b].x)*aspectRatio,p[a].y-p[b].y);
  const scale=Math.max(distance(0,9),distance(5,17));
  p[4]={...p[8],x:p[8].x+gap*scale/aspectRatio};
  return result;
}
