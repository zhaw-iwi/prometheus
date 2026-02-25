let session = {
  agentId: null,
  cameraRunning: false,
  userName: "user-1",
};

let video = null;
let canvas = null;
let ctx = null;
let mediaStream = null;
let detectLoopTimer = null;
let modelReady = false;
let lastEmitAt = 0;
let lastEmitted = null;

const DETECT_PERIOD_MS = 350;
const MODEL_URI = "https://justadudewhohacks.github.io/face-api.js/models";

window.addEventListener("load", async () => {
  video = document.getElementById("camera_video");
  canvas = document.getElementById("overlay_canvas");
  ctx = canvas.getContext("2d");
  session.agentId = getAgentId();
  session.userName = restoreUserName();
  wireUi();
  const userNameInput = document.getElementById("user_name");
  if (userNameInput) {
    userNameInput.value = session.userName;
  }

  if (!session.agentId) {
    appendLog("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    setCameraStatus("Camera Idle");
    document.getElementById("start_camera").disabled = true;
    return;
  }

  await loadAgentInfo();
  setCameraStatus("Loading Model");
  await loadModels();
  setCameraStatus("Camera Idle");
});

function wireUi() {
  document.getElementById("start_camera").addEventListener("click", startCamera);
  document.getElementById("stop_camera").addEventListener("click", stopCamera);
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  document.getElementById("user_name").addEventListener("input", onUserNameChanged);
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

async function loadModels() {
  try {
    await Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URI),
      faceapi.nets.faceExpressionNet.loadFromUri(MODEL_URI),
    ]);
    modelReady = true;
    appendLog("Face models loaded.");
  } catch (err) {
    setCameraStatus("Error");
    appendLog("Model load failed: " + err.message);
  }
}

async function startCamera() {
  if (!modelReady || session.cameraRunning) {
    return;
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user" },
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
  clearOverlay();
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
    const detection = await faceapi
      .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.45 }))
      .withFaceExpressions();

    if (!detection) {
      updateMetrics(null);
      clearOverlay();
    } else {
      drawDetection(detection.detection.box);
      const emotion = deriveEmotion(detection.expressions);
      updateMetrics(emotion);
      await emitEmotionObservation(emotion, detection.detection.score);
    }
  } catch (err) {
    appendLog("Detection error: " + err.message);
  }

  detectLoopTimer = setTimeout(runDetectionLoop, DETECT_PERIOD_MS);
}

function drawDetection(box) {
  if (!box) {
    clearOverlay();
    return;
  }
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height;
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const scaleX = canvas.width / video.videoWidth;
  const scaleY = canvas.height / video.videoHeight;

  ctx.lineWidth = 3;
  ctx.strokeStyle = "#ff7a00";
  ctx.strokeRect(box.x * scaleX, box.y * scaleY, box.width * scaleX, box.height * scaleY);
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

function deriveEmotion(expressions) {
  const dominant = Object.entries(expressions || {}).sort((a, b) => b[1] - a[1])[0] || ["neutral", 0];
  const label = dominant[0];
  const confidence = Number(dominant[1] || 0);

  const happy = Number(expressions?.happy || 0);
  const sad = Number(expressions?.sad || 0);
  const angry = Number(expressions?.angry || 0);
  const fearful = Number(expressions?.fearful || 0);
  const surprised = Number(expressions?.surprised || 0);
  const neutral = Number(expressions?.neutral || 0);

  const valence = clamp(happy - sad - 0.7 * angry - 0.6 * fearful, -1, 1);
  const arousal = clamp(0.2 * neutral + 0.7 * surprised + 0.6 * angry + 0.55 * fearful + 0.35 * happy, 0, 1);

  return {
    emotion: label,
    confidence,
    valence,
    arousal,
    expressions,
    facePresent: true,
  };
}

function updateMetrics(emotion) {
  if (!emotion) {
    document.getElementById("emotion_name").textContent = "none";
    document.getElementById("emotion_confidence").textContent = "0.00";
    document.getElementById("valence_value").textContent = "0.00";
    document.getElementById("arousal_value").textContent = "0.00";
    return;
  }
  document.getElementById("emotion_name").textContent = emotion.emotion;
  document.getElementById("emotion_confidence").textContent = emotion.confidence.toFixed(2);
  document.getElementById("valence_value").textContent = emotion.valence.toFixed(2);
  document.getElementById("arousal_value").textContent = emotion.arousal.toFixed(2);
}

async function emitEmotionObservation(emotion, faceScore) {
  const emitEnabled = document.getElementById("emit_enabled").checked;
  if (!emitEnabled || !emotion) {
    return;
  }
  const confidenceThreshold = Number(document.getElementById("confidence_threshold").value || 0.55);
  if (emotion.confidence < confidenceThreshold) {
    return;
  }

  const now = Date.now();
  const minInterval = Number(document.getElementById("emit_interval_ms").value || 1000);
  if (now - lastEmitAt < minInterval) {
    return;
  }

  const changeThreshold = Number(document.getElementById("change_threshold").value || 0.08);
  if (lastEmitted) {
    const sameLabel = lastEmitted.emotion === emotion.emotion;
    const dv = Math.abs(lastEmitted.valence - emotion.valence);
    const da = Math.abs(lastEmitted.arousal - emotion.arousal);
    if (sameLabel && dv < changeThreshold && da < changeThreshold) {
      return;
    }
  }

  const payload = {
    source: "visual.multifacial",
    userName: resolveUserName(),
    emotion: emotion.emotion,
    confidence: round(emotion.confidence, 3),
    valence: round(emotion.valence, 3),
    arousal: round(emotion.arousal, 3),
    faceDetectionConfidence: round(Number(faceScore || 0), 3),
    facePresent: true,
    expressions: compressExpressions(emotion.expressions),
    ts: new Date().toISOString(),
  };

  const ok = await acknowledgeEvent({
    type: "obs.emotion.face",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(payload),
  });

  if (ok) {
    lastEmitAt = now;
    lastEmitted = {
      emotion: emotion.emotion,
      valence: emotion.valence,
      arousal: emotion.arousal,
    };
    appendLog(`emit obs.emotion.face user=${payload.userName} ${emotion.emotion} v=${payload.valence} a=${payload.arousal} c=${payload.confidence}`);
  }
}

function onUserNameChanged(event) {
  const next = normalizeUserName(event?.target?.value);
  session.userName = next;
  localStorage.setItem("prometheus.multifacial.userName", next);
}

function resolveUserName() {
  const input = document.getElementById("user_name");
  const candidate = normalizeUserName(input?.value);
  if (candidate !== session.userName) {
    session.userName = candidate;
    localStorage.setItem("prometheus.multifacial.userName", candidate);
  }
  return candidate;
}

function restoreUserName() {
  try {
    const saved = localStorage.getItem("prometheus.multifacial.userName");
    return normalizeUserName(saved);
  } catch (_) {
    return "user-1";
  }
}

function normalizeUserName(raw) {
  const trimmed = (raw || "").trim();
  return trimmed || "user-1";
}

function compressExpressions(expressions) {
  const result = {};
  const entries = Object.entries(expressions || {});
  for (const [k, v] of entries) {
    result[k] = round(Number(v || 0), 3);
  }
  return result;
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

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
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
