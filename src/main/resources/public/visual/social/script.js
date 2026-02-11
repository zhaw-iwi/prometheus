let session = {
  agentId: null,
  cameraRunning: false,
};

let video = null;
let canvas = null;
let ctx = null;
let mediaStream = null;
let detectLoopTimer = null;
let detector = null;
let detectorReady = false;
let lastEmitAt = 0;
let lastPresenceSignature = null;
let lastGroupingSignature = null;

let nextTrackId = 1;
const tracks = new Map();

const DETECT_PERIOD_MS = 550;
const PERSON_SCORE_THRESHOLD = 0.45;
const TRACK_MAX_DISTANCE_NORM = 0.12;
const TRACK_TTL_MS = 2000;

window.addEventListener("load", async () => {
  video = document.getElementById("camera_video");
  canvas = document.getElementById("overlay_canvas");
  ctx = canvas.getContext("2d");
  session.agentId = getAgentId();
  wireUi();

  if (!session.agentId) {
    appendLog("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    setCameraStatus("Camera Idle");
    document.getElementById("start_camera").disabled = true;
    return;
  }

  await loadAgentInfo();
  setCameraStatus("Loading Model");
  await loadDetector();
  setCameraStatus("Camera Idle");
});

function wireUi() {
  document.getElementById("start_camera").addEventListener("click", startCamera);
  document.getElementById("stop_camera").addEventListener("click", stopCamera);
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
}

async function loadAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) {
    appendLog("Unable to load agent info.");
    return;
  }
  const data = await response.json();
  document.getElementById("agent_name").textContent = data.name;
  setActiveStatus(data.active);
}

async function showAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) {
    appendLog("Unable to load agent info.");
    return;
  }
  const data = await response.json();
  alert(`Name\n${data.name}\n\nDescription\n${data.description}`);
}

function setActiveStatus(isActive) {
  const el = document.getElementById("active_status");
  if (isActive === true) {
    el.textContent = "Active";
    el.className = "status-pill is-active";
  } else if (isActive === false) {
    el.textContent = "Inactive";
    el.className = "status-pill is-inactive";
  } else {
    el.textContent = "Unknown";
    el.className = "status-pill is-unknown";
  }
}

function setCameraStatus(text) {
  const el = document.getElementById("camera_status");
  el.textContent = text;
  if (text.includes("Live")) {
    el.className = "status-pill is-listening";
  } else if (text.includes("Error")) {
    el.className = "status-pill is-inactive";
  } else {
    el.className = "status-pill is-idle";
  }
}

async function loadDetector() {
  try {
    detector = await cocoSsd.load({ base: "lite_mobilenet_v2" });
    detectorReady = true;
    appendLog("Person detector ready.");
  } catch (err) {
    setCameraStatus("Error");
    appendLog("Model load failed: " + err.message);
  }
}

async function startCamera() {
  if (!detectorReady || session.cameraRunning) {
    return;
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment" },
      audio: false,
    });
    video.srcObject = mediaStream;
    await video.play();

    session.cameraRunning = true;
    document.getElementById("start_camera").disabled = true;
    document.getElementById("stop_camera").disabled = false;
    setCameraStatus("Camera Live");
    appendLog("Camera started.");

    runDetectionLoop();
  } catch (err) {
    setCameraStatus("Error");
    appendLog("Camera start failed: " + err.message);
  }
}

function stopCamera() {
  session.cameraRunning = false;
  if (detectLoopTimer) {
    clearTimeout(detectLoopTimer);
    detectLoopTimer = null;
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop());
    mediaStream = null;
  }
  if (video) {
    video.srcObject = null;
  }
  tracks.clear();
  clearOverlay();
  updateMetrics({ humanCount: 0, groupCount: 0, singletonCount: 0, largestGroupSize: 0 });
  document.getElementById("start_camera").disabled = false;
  document.getElementById("stop_camera").disabled = true;
  setCameraStatus("Camera Idle");
  appendLog("Camera stopped.");
}

async function runDetectionLoop() {
  if (!session.cameraRunning) {
    return;
  }

  try {
    const rawDetections = await detector.detect(video);
    const people = rawDetections
      .filter((d) => d && d.class === "person" && Number(d.score || 0) >= PERSON_SCORE_THRESHOLD)
      .map((d) => normalizePersonDetection(d));

    const tracked = updateTracks(people);
    const social = deriveSocialSituation(tracked);

    drawOverlay(tracked, social);
    updateMetrics(social);
    await emitObservations(social, tracked);
  } catch (err) {
    appendLog("Detection error: " + err.message);
  }

  detectLoopTimer = setTimeout(runDetectionLoop, DETECT_PERIOD_MS);
}

function normalizePersonDetection(detection) {
  const bbox = detection.bbox || [0, 0, 0, 0];
  const x = Number(bbox[0] || 0);
  const y = Number(bbox[1] || 0);
  const w = Number(bbox[2] || 0);
  const h = Number(bbox[3] || 0);
  return {
    x,
    y,
    w,
    h,
    score: Number(detection.score || 0),
    cx: x + w / 2,
    cy: y + h / 2,
  };
}

function updateTracks(detections) {
  const now = Date.now();
  const assignedTrackIds = new Set();
  const tracked = [];
  const frameDiag = Math.max(1, Math.hypot(video.videoWidth || 1, video.videoHeight || 1));

  for (const det of detections) {
    const bestMatch = findBestTrack(det, frameDiag, assignedTrackIds);
    if (bestMatch) {
      const track = tracks.get(bestMatch.id);
      track.cx = det.cx;
      track.cy = det.cy;
      track.box = [det.x, det.y, det.w, det.h];
      track.score = det.score;
      track.lastSeenAt = now;
      assignedTrackIds.add(track.id);
      tracked.push(trackToView(track));
    } else {
      const id = nextTrackId++;
      const created = {
        id,
        cx: det.cx,
        cy: det.cy,
        box: [det.x, det.y, det.w, det.h],
        score: det.score,
        firstSeenAt: now,
        lastSeenAt: now,
      };
      tracks.set(id, created);
      assignedTrackIds.add(id);
      tracked.push(trackToView(created));
    }
  }

  for (const [id, track] of tracks.entries()) {
    if (now - track.lastSeenAt > TRACK_TTL_MS) {
      tracks.delete(id);
    }
  }

  return tracked;
}

function findBestTrack(detection, frameDiag, assignedTrackIds) {
  let best = null;
  for (const track of tracks.values()) {
    if (assignedTrackIds.has(track.id)) {
      continue;
    }
    const distPx = Math.hypot(detection.cx - track.cx, detection.cy - track.cy);
    const distNorm = distPx / frameDiag;
    if (distNorm > TRACK_MAX_DISTANCE_NORM) {
      continue;
    }
    if (!best || distNorm < best.distNorm) {
      best = { id: track.id, distNorm };
    }
  }
  return best;
}

function trackToView(track) {
  return {
    id: track.id,
    cx: track.cx,
    cy: track.cy,
    box: track.box,
    score: track.score,
  };
}

function deriveSocialSituation(tracked) {
  const people = tracked || [];
  const n = people.length;
  if (n === 0) {
    return { humanCount: 0, groupCount: 0, singletonCount: 0, largestGroupSize: 0, groups: [] };
  }

  const threshold = Number(document.getElementById("group_distance_threshold").value || 0.16);
  const frameDiag = Math.max(1, Math.hypot(video.videoWidth || 1, video.videoHeight || 1));
  const uf = new UnionFind(n);

  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const dNorm = Math.hypot(people[i].cx - people[j].cx, people[i].cy - people[j].cy) / frameDiag;
      if (dNorm <= threshold) {
        uf.union(i, j);
      }
    }
  }

  const clusters = new Map();
  for (let i = 0; i < n; i++) {
    const root = uf.find(i);
    if (!clusters.has(root)) {
      clusters.set(root, []);
    }
    clusters.get(root).push(people[i].id);
  }

  const groups = Array.from(clusters.values())
    .map((members) => ({ members }))
    .sort((a, b) => b.members.length - a.members.length);

  const groupCount = groups.filter((g) => g.members.length >= 2).length;
  const singletonCount = groups.filter((g) => g.members.length === 1).length;
  const largestGroupSize = groups.length ? groups[0].members.length : 0;

  return {
    humanCount: n,
    groupCount,
    singletonCount,
    largestGroupSize,
    groups,
  };
}

async function emitObservations(social, tracked) {
  const emitEnabled = document.getElementById("emit_enabled").checked;
  if (!emitEnabled || !social) {
    return;
  }

  const now = Date.now();
  const minInterval = Number(document.getElementById("emit_interval_ms").value || 1500);
  if (now - lastEmitAt < minInterval) {
    return;
  }

  const presencePayload = {
    source: "visual.social",
    humanCount: social.humanCount,
    trackedCount: tracked.length,
    trackedIds: tracked.map((p) => p.id),
    avgDetectionConfidence: round(average(tracked.map((p) => p.score)), 3),
    ts: new Date().toISOString(),
  };

  const groupingPayload = {
    source: "visual.social",
    humanCount: social.humanCount,
    groupCount: social.groupCount,
    singletonCount: social.singletonCount,
    largestGroupSize: social.largestGroupSize,
    groupSizes: social.groups.map((g) => g.members.length),
    groups: social.groups.map((g) => ({ memberIds: g.members })),
    ts: new Date().toISOString(),
  };

  const presenceSignature = `${presencePayload.humanCount}|${presencePayload.trackedCount}`;
  const groupingSignature = `${groupingPayload.groupCount}|${groupingPayload.singletonCount}|${groupingPayload.largestGroupSize}|${groupingPayload.groupSizes.join(",")}`;
  if (presenceSignature === lastPresenceSignature && groupingSignature === lastGroupingSignature) {
    return;
  }

  const presenceOk = await acknowledgeEvent({
    type: "obs.human.presence",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(presencePayload),
  });
  const groupingOk = await acknowledgeEvent({
    type: "obs.social.grouping",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(groupingPayload),
  });

  if (presenceOk || groupingOk) {
    lastEmitAt = now;
    lastPresenceSignature = presenceSignature;
    lastGroupingSignature = groupingSignature;
    appendLog(
      `emit humans=${groupingPayload.humanCount} groups=${groupingPayload.groupCount} singletons=${groupingPayload.singletonCount} largest=${groupingPayload.largestGroupSize}`
    );
  }
}

function drawOverlay(tracked, social) {
  if (!canvas || !ctx) {
    return;
  }
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height;
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const scaleX = canvas.width / Math.max(1, video.videoWidth);
  const scaleY = canvas.height / Math.max(1, video.videoHeight);

  const idToGroup = new Map();
  const groups = social && social.groups ? social.groups : [];
  for (let i = 0; i < groups.length; i++) {
    for (const id of groups[i].members) {
      idToGroup.set(id, i);
    }
  }

  for (const person of tracked || []) {
    const [x, y, w, h] = person.box;
    const gid = idToGroup.has(person.id) ? idToGroup.get(person.id) : -1;
    const hue = gid >= 0 ? (gid * 57) % 360 : 24;
    ctx.lineWidth = 3;
    ctx.strokeStyle = `hsl(${hue}, 95%, 55%)`;
    ctx.strokeRect(x * scaleX, y * scaleY, w * scaleX, h * scaleY);
    ctx.fillStyle = "rgba(11, 19, 22, 0.72)";
    ctx.fillRect(x * scaleX, y * scaleY - 20, 88, 18);
    ctx.fillStyle = "#f7f3ea";
    ctx.font = "12px Spline Sans Mono, monospace";
    ctx.fillText(`#${person.id} g${gid + 1}`, x * scaleX + 4, y * scaleY - 7);
  }
}

function clearOverlay() {
  if (!canvas || !ctx) {
    return;
  }
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
}

function updateMetrics(social) {
  document.getElementById("human_count").textContent = String((social && social.humanCount) || 0);
  document.getElementById("group_count").textContent = String((social && social.groupCount) || 0);
  document.getElementById("singleton_count").textContent = String((social && social.singletonCount) || 0);
  document.getElementById("largest_group_size").textContent = String((social && social.largestGroupSize) || 0);
}

async function acknowledgeEvent(request) {
  try {
    const response = await fetch(`/${session.agentId}/acknowledge`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8",
      },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      appendLog(`ack failed: ${response.status}`);
      return false;
    }
    return true;
  } catch (err) {
    appendLog("ack failed: " + err.message);
    return false;
  }
}

function appendLog(message) {
  const log = document.getElementById("activity_log");
  const stamp = new Date().toLocaleTimeString();
  const next = `[${stamp}] ${message}`;
  log.textContent = log.textContent ? `${next}\n${log.textContent}` : next;
}

function average(values) {
  if (!values || values.length === 0) {
    return 0;
  }
  let sum = 0;
  for (const v of values) {
    sum += Number(v || 0);
  }
  return sum / values.length;
}

function round(value, digits) {
  const factor = Math.pow(10, digits || 0);
  return Math.round(value * factor) / factor;
}

function getAgentId() {
  const search = window.location.search;
  if (!search || search.length < 2) {
    return null;
  }
  if (search.includes("=")) {
    const params = new URLSearchParams(search);
    if (params.has("agentId")) {
      return params.get("agentId");
    }
    if (params.has("agent")) {
      return params.get("agent");
    }
  }
  return search.substring(1);
}

class UnionFind {
  constructor(size) {
    this.parent = Array.from({ length: size }, (_, i) => i);
    this.rank = Array.from({ length: size }, () => 0);
  }

  find(x) {
    if (this.parent[x] !== x) {
      this.parent[x] = this.find(this.parent[x]);
    }
    return this.parent[x];
  }

  union(a, b) {
    const ra = this.find(a);
    const rb = this.find(b);
    if (ra === rb) {
      return;
    }
    if (this.rank[ra] < this.rank[rb]) {
      this.parent[ra] = rb;
      return;
    }
    if (this.rank[ra] > this.rank[rb]) {
      this.parent[rb] = ra;
      return;
    }
    this.parent[rb] = ra;
    this.rank[ra] += 1;
  }
}
