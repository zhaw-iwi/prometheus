let session = {
  agentId: null,
  stream: null,
  monitorStream: null,
};

let lastBehaviourEventId = null;
let streamReconnectTimer = null;
let streamReconnectAttempt = 0;
let monitorReconnectTimer = null;
let monitorReconnectAttempt = 0;
let isPageUnloading = false;
let autoReconnectEnabled = true;
const STREAM_RECONNECT_MIN_MS = 1000;
const STREAM_RECONNECT_MAX_MS = 30000;
const STREAM_RECONNECT_JITTER = 0.2;
const seenBehaviourKeys = new Set();

let video = null;
let gestureCanvas = null;
let gestureCtx = null;
let cameraStream = null;
let gestureRecognizer = null;
let gestureDetectorReady = false;
let gestureLoopTimer = null;
let lastGestureVideoTime = -1;
let stableGestureKey = null;
let stableGestureCount = 0;
let lastCameraEmitKey = null;
let lastCameraEmitAt = 0;

const MEDIAPIPE_TASKS_VERSION = "0.10.35";
const MEDIAPIPE_TASKS_URL = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}`;
const MEDIAPIPE_WASM_ROOT = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}/wasm`;
const GESTURE_MODEL_URL = "https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/gesture_recognizer.task";
const GESTURE_DETECT_PERIOD_MS = 250;
const REQUIRED_STABLE_GESTURE_FRAMES = 3;
const HAND_CONNECTIONS = [
  [0, 1], [1, 2], [2, 3], [3, 4],
  [0, 5], [5, 6], [6, 7], [7, 8],
  [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16],
  [13, 17], [17, 18], [18, 19], [19, 20],
  [0, 17],
];
const CANNED_GESTURE_TO_SIGN = {
  Closed_Fist: "rock",
  Open_Palm: "paper",
  Victory: "scissor",
};

const SIGNS = {
  rock: { label: "Stein", symbol: "\u270A" },
  scissor: { label: "Schere", symbol: "\u270C" },
  paper: { label: "Papier", symbol: "\u270B" },
};

window.addEventListener("load", async () => {
  video = document.getElementById("camera_video");
  gestureCanvas = document.getElementById("gesture_overlay_canvas");
  gestureCtx = gestureCanvas.getContext("2d");
  session.agentId = getAgentId();
  wireUi();
  renderCameraThreshold();
  window.addEventListener("beforeunload", cleanupStreams);
  window.addEventListener("pagehide", cleanupStreams);

  if (!session.agentId) {
    appendLog("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    setStreamStatus("Stream Error");
    setCameraStatus("Kamera Idle", "idle");
    setControlsEnabled(false);
    return;
  }

  await loadAgentInfo();
  await loadEventHistory();
  await loadGestureRecognizer();
  connectMonitorStream();
  connectBehaviourStream();
});

function wireUi() {
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  document.getElementById("start_game").addEventListener("click", startGame);
  document.getElementById("ready_round").addEventListener("click", () => acknowledgeUserUtterance("Bereit."));
  document.getElementById("play_again").addEventListener("click", () => acknowledgeUserUtterance("Ja, noch eine Runde."));
  document.getElementById("stop_game").addEventListener("click", () => acknowledgeUserUtterance("Nein, bitte beende das Spiel."));
  document.getElementById("start_camera").addEventListener("click", startCamera);
  document.getElementById("stop_camera").addEventListener("click", () => stopCamera());
  document.getElementById("camera_confidence_threshold").addEventListener("input", renderCameraThreshold);
  document.querySelectorAll("[data-sign]").forEach((button) => {
    button.addEventListener("click", () => submitHandSign(button.dataset.sign, {
      source: "rps.web",
      detectionMode: "manual",
      confidence: 1.0,
    }));
  });
}

function setControlsEnabled(enabled) {
  document.querySelectorAll("button").forEach((button) => {
    if (button.id !== "show_agent_info") {
      button.disabled = !enabled;
    }
  });
}

async function loadAgentInfo() {
  try {
    const response = await fetch(`/${session.agentId}/info`);
    if (!response.ok) {
      appendLog(`agent info failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    document.getElementById("agent_name").textContent = data.name || "GIGI RPS";
    setActiveStatus(data.active);
  } catch (error) {
    appendLog("agent info failed: " + error.message);
  }
}

async function showAgentInfo() {
  try {
    const response = await fetch(`/${session.agentId}/info`);
    if (!response.ok) {
      appendLog(`agent info failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    alert(`Name\n${data.name}\n\nDescription\n${data.description}`);
  } catch (error) {
    appendLog("agent info failed: " + error.message);
  }
}

async function loadGestureRecognizer() {
  const startButton = document.getElementById("start_camera");
  startButton.disabled = true;

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    setCameraStatus("Kamera Fehler", "error");
    appendLog("camera API unavailable.");
    return;
  }

  try {
    setCameraStatus("Modell laden", "idle");
    const visionTasks = await import(MEDIAPIPE_TASKS_URL);
    const vision = await visionTasks.FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_ROOT);
    gestureRecognizer = await visionTasks.GestureRecognizer.createFromOptions(vision, {
      baseOptions: {
        modelAssetPath: GESTURE_MODEL_URL,
      },
      runningMode: "VIDEO",
      numHands: 1,
    });
    gestureDetectorReady = true;
    startButton.disabled = false;
    setCameraStatus("Kamera Idle", "idle");
    appendLog("gesture recognizer ready.");
  } catch (error) {
    gestureDetectorReady = false;
    setCameraStatus("Kamera Fehler", "error");
    appendLog("gesture recognizer failed: " + error.message);
  }
}

async function startCamera() {
  if (!gestureDetectorReady || cameraStream || isPageUnloading) {
    return;
  }
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: "user",
        width: { ideal: 960 },
        height: { ideal: 720 },
      },
      audio: false,
    });
    video.srcObject = cameraStream;
    await video.play();

    document.getElementById("start_camera").disabled = true;
    document.getElementById("stop_camera").disabled = false;
    setCameraStatus("Kamera Live", "live");
    appendLog("camera started.");
    runGestureLoop();
  } catch (error) {
    setCameraStatus("Kamera Fehler", "error");
    appendLog("camera start failed: " + error.message);
    stopCamera({ silent: true });
  }
}

function stopCamera(options = {}) {
  if (gestureLoopTimer) {
    clearTimeout(gestureLoopTimer);
    gestureLoopTimer = null;
  }
  if (cameraStream) {
    cameraStream.getTracks().forEach((track) => track.stop());
    cameraStream = null;
  }
  if (video) {
    video.srcObject = null;
  }
  lastGestureVideoTime = -1;
  stableGestureKey = null;
  stableGestureCount = 0;
  clearGestureOverlay();
  renderCameraDetection(null);
  if (gestureDetectorReady && !isPageUnloading) {
    document.getElementById("start_camera").disabled = false;
  }
  document.getElementById("stop_camera").disabled = true;
  setCameraStatus("Kamera Idle", "idle");
  if (!options.silent) {
    appendLog("camera stopped.");
  }
}

async function runGestureLoop() {
  if (!cameraStream || !gestureRecognizer || isPageUnloading) {
    return;
  }

  try {
    if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.currentTime !== lastGestureVideoTime) {
      const result = gestureRecognizer.recognizeForVideo(video, performance.now());
      const candidate = selectCameraGesture(result);
      updateCameraStability(candidate);
      drawGestureOverlay(candidate);
      await maybeEmitCameraSign(candidate);
      lastGestureVideoTime = video.currentTime;
    }
  } catch (error) {
    appendLog("gesture detection failed: " + error.message);
  }

  gestureLoopTimer = setTimeout(runGestureLoop, GESTURE_DETECT_PERIOD_MS);
}

function selectCameraGesture(result) {
  const gestures = result && Array.isArray(result.gestures) ? result.gestures : [];
  let best = null;

  for (let i = 0; i < gestures.length; i++) {
    const top = Array.isArray(gestures[i]) ? gestures[i][0] : null;
    if (!top) {
      continue;
    }
    const cannedGesture = top.categoryName || top.displayName || "";
    const sign = CANNED_GESTURE_TO_SIGN[cannedGesture];
    if (!sign) {
      continue;
    }
    const confidence = Number(top.score || 0);
    if (!best || confidence > best.confidence) {
      const handedness = result.handedness && result.handedness[i] && result.handedness[i][0]
        ? result.handedness[i][0].categoryName
        : null;
      best = {
        sign,
        confidence,
        cannedGesture,
        hand: normalizeHandedness(handedness),
        landmarks: result.landmarks && result.landmarks[i] ? result.landmarks[i] : [],
      };
    }
  }

  return best;
}

function updateCameraStability(candidate) {
  const threshold = cameraConfidenceThreshold();
  if (!candidate || candidate.confidence < threshold) {
    stableGestureKey = null;
    stableGestureCount = 0;
    renderCameraDetection(candidate);
    return;
  }

  const key = `${candidate.sign}|${candidate.cannedGesture}`;
  if (key === stableGestureKey) {
    stableGestureCount += 1;
  } else {
    stableGestureKey = key;
    stableGestureCount = 1;
  }
  renderCameraDetection(candidate);
}

async function maybeEmitCameraSign(candidate) {
  if (!document.getElementById("camera_emit_enabled").checked) {
    return;
  }
  if (!candidate || candidate.confidence < cameraConfidenceThreshold()) {
    return;
  }
  if (stableGestureCount < REQUIRED_STABLE_GESTURE_FRAMES) {
    return;
  }

  const emitKey = `${candidate.sign}|${candidate.cannedGesture}`;
  if (emitKey === lastCameraEmitKey) {
    return;
  }

  const now = Date.now();
  const intervalMs = Math.max(500, Number(document.getElementById("camera_emit_interval_ms").value || 1800));
  if (now - lastCameraEmitAt < intervalMs) {
    return;
  }
  lastCameraEmitAt = now;

  const ok = await submitHandSign(candidate.sign, {
    source: "rps.web.camera",
    detectionMode: "client_camera",
    confidence: candidate.confidence,
    hand: candidate.hand,
    cannedGesture: candidate.cannedGesture,
    stabilityFrames: stableGestureCount,
  });
  if (ok) {
    lastCameraEmitKey = emitKey;
  }
}

function resetCameraEmissionGate() {
  lastCameraEmitKey = null;
  lastCameraEmitAt = 0;
}

function renderCameraDetection(candidate) {
  const accepted = candidate && candidate.confidence >= cameraConfidenceThreshold();
  setText("camera_sign_value", accepted ? SIGNS[candidate.sign].label : "-");
  setText("camera_confidence_value", candidate ? round(candidate.confidence, 2).toFixed(2) : "0.00");
  setText("camera_stability_value", `${stableGestureCount}/${REQUIRED_STABLE_GESTURE_FRAMES}`);
}

function renderCameraThreshold() {
  setText("camera_threshold_value", cameraConfidenceThreshold().toFixed(2));
}

function cameraConfidenceThreshold() {
  return clamp(Number(document.getElementById("camera_confidence_threshold").value || 0.7), 0, 1);
}

function drawGestureOverlay(candidate) {
  if (!gestureCanvas || !gestureCtx) {
    return;
  }
  const rect = gestureCanvas.getBoundingClientRect();
  gestureCanvas.width = rect.width;
  gestureCanvas.height = rect.height;
  gestureCtx.clearRect(0, 0, gestureCanvas.width, gestureCanvas.height);

  const landmarks = candidate && Array.isArray(candidate.landmarks) ? candidate.landmarks : [];
  if (!landmarks.length) {
    return;
  }

  gestureCtx.save();
  gestureCtx.translate(gestureCanvas.width, 0);
  gestureCtx.scale(-1, 1);
  gestureCtx.lineWidth = 3;
  gestureCtx.strokeStyle = "#ff7a00";
  gestureCtx.fillStyle = "#00b0a2";

  for (const [from, to] of HAND_CONNECTIONS) {
    const a = landmarks[from];
    const b = landmarks[to];
    if (!a || !b) {
      continue;
    }
    gestureCtx.beginPath();
    gestureCtx.moveTo(a.x * gestureCanvas.width, a.y * gestureCanvas.height);
    gestureCtx.lineTo(b.x * gestureCanvas.width, b.y * gestureCanvas.height);
    gestureCtx.stroke();
  }

  for (const point of landmarks) {
    gestureCtx.beginPath();
    gestureCtx.arc(point.x * gestureCanvas.width, point.y * gestureCanvas.height, 4, 0, Math.PI * 2);
    gestureCtx.fill();
  }
  gestureCtx.restore();
}

function clearGestureOverlay() {
  if (!gestureCanvas || !gestureCtx) {
    return;
  }
  const rect = gestureCanvas.getBoundingClientRect();
  gestureCanvas.width = rect.width;
  gestureCanvas.height = rect.height;
  gestureCtx.clearRect(0, 0, gestureCanvas.width, gestureCanvas.height);
}

function normalizeHandedness(value) {
  const token = String(value || "").trim().toLowerCase();
  if (token === "left") {
    return "left";
  }
  if (token === "right") {
    return "right";
  }
  return "unknown";
}

async function loadEventHistory() {
  try {
    const response = await fetch(`/${session.agentId}/eventhistory`);
    if (!response.ok) {
      appendLog(`event history failed: ${response.status}`);
      return;
    }
    const history = await response.json();
    for (const event of history || []) {
      if (event.type === "resp.behaviour_plan") {
        handleBehaviourEnvelope(event);
      }
      if (event.type === "obs.hand.sign") {
        renderUserSignFromPayload(event.payload);
      }
    }
  } catch (error) {
    appendLog("event history failed: " + error.message);
  }
}

function connectBehaviourStream() {
  if (!session.agentId || session.stream || isPageUnloading) {
    return;
  }
  autoReconnectEnabled = true;
  if (streamReconnectTimer) {
    clearTimeout(streamReconnectTimer);
    streamReconnectTimer = null;
  }
  session.stream = new EventSource(behaviourStreamUrl());
  setStreamStatus("Stream Connecting");

  session.stream.addEventListener("open", () => {
    streamReconnectAttempt = 0;
    setStreamStatus("Stream Live");
    appendLog("behaviour stream connected.");
  });

  session.stream.addEventListener("behaviour", (event) => {
    if (event.lastEventId) {
      lastBehaviourEventId = event.lastEventId;
    }
    try {
      handleBehaviourEnvelope(JSON.parse(event.data));
    } catch (_) {
      appendLog("invalid behaviour event.");
    }
  });

  session.stream.onerror = () => {
    setStreamStatus("Stream Error");
    appendLog("behaviour stream disconnected.");
    closeBehaviourStream();
    scheduleBehaviourReconnect();
  };
}

function behaviourStreamUrl() {
  let url = `/${session.agentId}/behaviour/stream`;
  if (lastBehaviourEventId) {
    url += `?lastEventId=${encodeURIComponent(lastBehaviourEventId)}`;
  }
  return url;
}

function connectMonitorStream() {
  if (!session.agentId || session.monitorStream || isPageUnloading) {
    return;
  }
  session.monitorStream = new EventSource(`/${session.agentId}/monitor/stream`);
  session.monitorStream.addEventListener("open", () => {
    monitorReconnectAttempt = 0;
  });
  session.monitorStream.addEventListener("snapshot", (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data && typeof data.active === "boolean") {
        setActiveStatus(data.active);
      }
    } catch (_) {
      return;
    }
  });
  session.monitorStream.onerror = () => {
    if (session.monitorStream) {
      session.monitorStream.close();
      session.monitorStream = null;
    }
    scheduleMonitorReconnect();
  };
}

function closeBehaviourStream() {
  if (session.stream) {
    session.stream.close();
    session.stream = null;
  }
}

function scheduleBehaviourReconnect() {
  if (isPageUnloading || !autoReconnectEnabled || streamReconnectTimer) {
    return;
  }
  streamReconnectTimer = setTimeout(() => {
    streamReconnectTimer = null;
    connectBehaviourStream();
  }, nextReconnectDelayMs(streamReconnectAttempt++));
}

function scheduleMonitorReconnect() {
  if (isPageUnloading || monitorReconnectTimer) {
    return;
  }
  monitorReconnectTimer = setTimeout(() => {
    monitorReconnectTimer = null;
    connectMonitorStream();
  }, nextReconnectDelayMs(monitorReconnectAttempt++));
}

function nextReconnectDelayMs(attempt) {
  const base = Math.min(STREAM_RECONNECT_MAX_MS, STREAM_RECONNECT_MIN_MS * Math.pow(2, attempt));
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * STREAM_RECONNECT_JITTER);
  return Math.max(STREAM_RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function cleanupStreams() {
  isPageUnloading = true;
  autoReconnectEnabled = false;
  if (streamReconnectTimer) {
    clearTimeout(streamReconnectTimer);
    streamReconnectTimer = null;
  }
  if (monitorReconnectTimer) {
    clearTimeout(monitorReconnectTimer);
    monitorReconnectTimer = null;
  }
  closeBehaviourStream();
  if (session.monitorStream) {
    session.monitorStream.close();
    session.monitorStream = null;
  }
  stopCamera({ silent: true });
}

async function startGame() {
  resetCameraEmissionGate();
  try {
    const response = await fetch(`/${session.agentId}/start`, { method: "POST" });
    if (!response.ok) {
      appendLog(`start failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    handleResponseEvent(data.responseEvent);
    appendLog("start sent.");
  } catch (error) {
    appendLog("start failed: " + error.message);
  }
}

async function acknowledgeUserUtterance(payload) {
  resetCameraEmissionGate();
  await acknowledgeEvent({
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload,
  }, `utterance sent: ${payload}`);
}

async function submitHandSign(sign, options = {}) {
  const normalized = normalizeSign(sign);
  if (!normalized) {
    appendLog("unknown hand sign.");
    return false;
  }
  renderUserSign(normalized);
  const detectionMode = options.detectionMode || "manual";
  const confidence = typeof options.confidence === "number" ? round(clamp(options.confidence, 0, 1), 3) : 1.0;
  const payload = {
    source: options.source || "rps.web",
    hand: options.hand || "right",
    sign: normalized,
    confidence,
    detectionMode,
    ts: new Date().toISOString(),
  };
  if (options.cannedGesture) {
    payload.cannedGesture = options.cannedGesture;
  }
  if (Number.isFinite(options.stabilityFrames)) {
    payload.stabilityFrames = options.stabilityFrames;
  }
  const ok = await acknowledgeEvent({
    type: "obs.hand.sign",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(payload),
  }, `${detectionMode === "manual" ? "manual" : "camera"} sign sent: ${SIGNS[normalized].label}`);
  return ok;
}

async function acknowledgeEvent(request, successMessage) {
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
    const data = await response.json();
    setActiveStatus(data.active);
    handleResponseEvent(data.responseEvent);
    appendLog(successMessage);
    return true;
  } catch (error) {
    appendLog("ack failed: " + error.message);
    return false;
  }
}

function handleResponseEvent(responseEvent) {
  if (!responseEvent || responseEvent.type !== "resp.behaviour_plan") {
    return;
  }
  handleBehaviourEnvelope(responseEvent);
}

function handleBehaviourEnvelope(event) {
  if (!event || event.type !== "resp.behaviour_plan" || !event.payload) {
    return;
  }
  const key = event.createdDate ? `${event.createdDate}|${event.payload}` : event.payload;
  if (seenBehaviourKeys.has(key)) {
    return;
  }
  seenBehaviourKeys.add(key);
  let plan = null;
  try {
    plan = JSON.parse(event.payload);
  } catch (_) {
    appendLog("behaviour payload is not valid json.");
    return;
  }
  renderBehaviourPlan(plan);
}

function renderBehaviourPlan(plan) {
  if (!plan || typeof plan !== "object") {
    return;
  }
  if (typeof plan.speech === "string" && plan.speech.trim()) {
    document.getElementById("speech_preview").textContent = plan.speech.trim();
  }
  if (plan.motion && typeof plan.motion === "object") {
    renderMotion(plan.motion);
  }
  if (plan.display && typeof plan.display === "object") {
    renderDisplay(plan.display);
  }
  appendLog(`behaviour received: handSign=${motionHandSign(plan.motion) || "-"}`);
}

function renderMotion(motion) {
  const sign = normalizeSign(motion.handSign);
  if (sign) {
    renderAgentSign(sign);
    resetCameraEmissionGate();
  }
  setText("effector_value", asText(motion.effector));
  const timing = motion.timing && typeof motion.timing === "object" ? motion.timing : {};
  setText("timing_value", asText(timing.revealAt || timing.synchronizeWithSpeech));
}

function renderDisplay(display) {
  if (display.agentSign) {
    const agentSign = normalizeSign(display.agentSign);
    if (agentSign) {
      renderAgentSign(agentSign);
    }
  }
  if (display.userSign) {
    const userSign = normalizeSign(display.userSign);
    if (userSign) {
      renderUserSign(userSign);
    }
  }
  if (display.round !== undefined && display.round !== null) {
    setText("round_value", String(display.round));
  }
  if (display.winner) {
    setText("winner_value", winnerLabel(display.winner));
  }
}

function renderUserSignFromPayload(payload) {
  if (!payload) {
    return;
  }
  try {
    const parsed = JSON.parse(payload);
    const sign = normalizeSign(parsed.sign);
    if (sign) {
      renderUserSign(sign);
    }
  } catch (_) {
    return;
  }
}

function renderAgentSign(sign) {
  renderSign("agent", sign);
}

function renderUserSign(sign) {
  renderSign("user", sign);
}

function renderSign(prefix, sign) {
  const normalized = normalizeSign(sign);
  const ui = normalized ? SIGNS[normalized] : null;
  setText(`${prefix}_sign_symbol`, ui ? ui.symbol : "-");
  setText(`${prefix}_sign_label`, ui ? ui.label : "Kein Zeichen");
}

function motionHandSign(motion) {
  if (!motion || typeof motion !== "object") {
    return null;
  }
  return normalizeSign(motion.handSign);
}

function normalizeSign(value) {
  if (typeof value !== "string") {
    return null;
  }
  const token = value.trim().toLowerCase().replace(/\s+/g, "_").replace(/-/g, "_");
  if (token === "rock" || token === "stein") {
    return "rock";
  }
  if (token === "scissor" || token === "scissors" || token === "schere") {
    return "scissor";
  }
  if (token === "paper" || token === "papier") {
    return "paper";
  }
  return null;
}

function winnerLabel(value) {
  const token = String(value || "").trim().toLowerCase();
  if (token === "agent") {
    return "GIGI";
  }
  if (token === "user") {
    return "Du";
  }
  if (token === "draw") {
    return "Unentschieden";
  }
  return "-";
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function round(value, digits) {
  const factor = Math.pow(10, digits || 0);
  return Math.round(Number(value || 0) * factor) / factor;
}

function asText(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "-";
  }
  return value.trim();
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = value;
  }
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

function setStreamStatus(text) {
  const el = document.getElementById("stream_status");
  el.textContent = text;
  if (text.includes("Live")) {
    el.className = "status-pill is-listening";
  } else if (text.includes("Error")) {
    el.className = "status-pill is-inactive";
  } else {
    el.className = "status-pill is-idle";
  }
}

function setCameraStatus(text, state) {
  const el = document.getElementById("camera_status");
  el.textContent = text;
  if (state === "live") {
    el.className = "status-pill is-listening align-self-center";
  } else if (state === "error") {
    el.className = "status-pill is-inactive align-self-center";
  } else {
    el.className = "status-pill is-idle align-self-center";
  }
}

function appendLog(message) {
  const log = document.getElementById("activity_log");
  const stamp = new Date().toLocaleTimeString();
  const next = `[${stamp}] ${message}`;
  log.textContent = log.textContent ? `${next}\n${log.textContent}` : next;
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
