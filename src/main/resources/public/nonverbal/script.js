let session = {
  agentId: null,
  stream: null,
};
let streamReconnectTimer = null;
let streamReconnectAttempt = 0;
let monitorStream = null;
let monitorReconnectTimer = null;
let monitorReconnectAttempt = 0;
let lastBehaviourEventId = null;
let isPageUnloading = false;
let autoReconnectEnabled = true;
const STREAM_RECONNECT_MIN_MS = 1000;
const STREAM_RECONNECT_MAX_MS = 30000;
const STREAM_RECONNECT_JITTER = 0.2;
const NONVERBAL_TIMELINE_MAX = 8;
let nonverbalTimeline = [];

const GESTURE_UI = {
  OPEN_QUESTION: { emoji: "\uD83E\uDD32", label: "Open Question" },
  EXPLAIN: { emoji: "\u270B", label: "Explanatory Sweep" },
  UNCERTAIN: { emoji: "\uD83E\uDD37", label: "Uncertainty Shrug" },
  ACKNOWLEDGE: { emoji: "\uD83D\uDE4C", label: "Acknowledgement Close Hands" },
  POLITE: { emoji: "\uD83D\uDE4F", label: "Polite Apology" },
  NONE: { emoji: "-", label: "None" },
};

window.addEventListener("load", async () => {
  session.agentId = getAgentId();
  wireUi();
  renderTimeline();
  if (!session.agentId) {
    appendLog("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    setStreamStatus("Stream Error");
    disableControls();
    return;
  }
  window.addEventListener("beforeunload", cleanupStream);
  window.addEventListener("pagehide", cleanupStream);
  await loadAgentInfo();
  connectMonitorStream();
  connectStream();
});

function wireUi() {
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  document.getElementById("connect_stream").addEventListener("click", connectStream);
  document.getElementById("disconnect_stream").addEventListener("click", disconnectStream);
}

function disableControls() {
  document.getElementById("connect_stream").disabled = true;
  document.getElementById("disconnect_stream").disabled = true;
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

function connectStream() {
  if (!session.agentId || session.stream || isPageUnloading) {
    return;
  }
  autoReconnectEnabled = true;
  if (streamReconnectTimer) {
    clearTimeout(streamReconnectTimer);
    streamReconnectTimer = null;
  }
  const stream = new EventSource(behaviourStreamUrl());
  session.stream = stream;
  setStreamStatus("Stream Connecting");
  document.getElementById("connect_stream").disabled = true;
  document.getElementById("disconnect_stream").disabled = false;

  stream.addEventListener("open", () => {
    streamReconnectAttempt = 0;
    setStreamStatus("Stream Live");
    appendLog("behaviour stream connected.");
  });

  stream.addEventListener("behaviour", (event) => {
    if (event.lastEventId) {
      lastBehaviourEventId = event.lastEventId;
    }
    handleBehaviourEvent(event.data);
  });

  stream.onerror = () => {
    setStreamStatus("Stream Error");
    appendLog("behaviour stream disconnected.");
    disconnectStream(false);
    scheduleReconnect();
  };
}

function connectMonitorStream() {
  if (!session.agentId || monitorStream || isPageUnloading) {
    return;
  }
  monitorStream = new EventSource(`/${session.agentId}/monitor/stream`);
  monitorStream.addEventListener("open", () => {
    monitorReconnectAttempt = 0;
  });
  monitorStream.addEventListener("snapshot", (event) => {
    let data = null;
    try {
      data = JSON.parse(event.data);
    } catch (_) {
      return;
    }
    if (data && typeof data.active === "boolean") {
      setActiveStatus(data.active);
    }
  });
  monitorStream.onerror = () => {
    if (monitorStream) {
      monitorStream.close();
      monitorStream = null;
    }
    scheduleMonitorReconnect();
  };
}

function behaviourStreamUrl() {
  let url = `/${session.agentId}/behaviour/stream`;
  if (lastBehaviourEventId) {
    url += `?lastEventId=${encodeURIComponent(lastBehaviourEventId)}`;
  }
  return url;
}

function disconnectStream(manual = true) {
  if (manual) {
    autoReconnectEnabled = false;
  }
  if (session.stream) {
    session.stream.close();
    session.stream = null;
  }
  setStreamStatus("Stream Idle");
  document.getElementById("connect_stream").disabled = false;
  document.getElementById("disconnect_stream").disabled = true;
  if (manual) {
    appendLog("behaviour stream closed.");
  }
}

function scheduleMonitorReconnect() {
  if (isPageUnloading || monitorReconnectTimer) {
    return;
  }
  const delay = nextMonitorReconnectDelayMs();
  monitorReconnectTimer = setTimeout(() => {
    monitorReconnectTimer = null;
    connectMonitorStream();
  }, delay);
}

function nextMonitorReconnectDelayMs() {
  const base = Math.min(STREAM_RECONNECT_MAX_MS, STREAM_RECONNECT_MIN_MS * Math.pow(2, monitorReconnectAttempt));
  monitorReconnectAttempt += 1;
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * STREAM_RECONNECT_JITTER);
  return Math.max(STREAM_RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function scheduleReconnect() {
  if (isPageUnloading || !autoReconnectEnabled || streamReconnectTimer) {
    return;
  }
  const delay = nextReconnectDelayMs();
  streamReconnectTimer = setTimeout(() => {
    streamReconnectTimer = null;
    connectStream();
  }, delay);
}

function nextReconnectDelayMs() {
  const base = Math.min(STREAM_RECONNECT_MAX_MS, STREAM_RECONNECT_MIN_MS * Math.pow(2, streamReconnectAttempt));
  streamReconnectAttempt += 1;
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * STREAM_RECONNECT_JITTER);
  return Math.max(STREAM_RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function cleanupStream() {
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
  if (session.stream) {
    session.stream.close();
    session.stream = null;
  }
  if (monitorStream) {
    monitorStream.close();
    monitorStream = null;
  }
  streamReconnectAttempt = 0;
  monitorReconnectAttempt = 0;
}

function handleBehaviourEvent(raw) {
  let event = null;
  try {
    event = JSON.parse(raw);
  } catch (_) {
    appendLog("invalid behaviour event payload.");
    return;
  }
  if (!event || !event.payload) {
    return;
  }
  let plan = null;
  try {
    plan = JSON.parse(event.payload);
  } catch (_) {
    appendLog("behaviour payload is not valid json.");
    return;
  }
  if (!plan || !plan.nonVerbal || typeof plan.nonVerbal !== "object") {
    appendLog("behaviour payload missing nonVerbal object.");
    return;
  }
  const nonVerbal = normalizeNonVerbal(plan.nonVerbal);
  renderNonVerbal(nonVerbal);
  const speech = plan && typeof plan.speech === "string" ? plan.speech : "";
  document.getElementById("speech_preview").textContent = speech && speech.trim() ? speech : "(no speech)";
  addTimelineEntry(nonVerbal, event.createdDate);
  appendLog(`behaviour received: gesture=${nonVerbal.gesture}, face=${nonVerbal.facialExpression.type}, gaze=${nonVerbal.gaze.direction}`);
}

function normalizeToken(value) {
  const token = (value || "")
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_")
    .replace(/-/g, "_");
  if (GESTURE_UI[token]) {
    return token;
  }
  return "NONE";
}

function renderGesture(gesture) {
  const ui = GESTURE_UI[gesture] || GESTURE_UI.NONE;
  document.getElementById("gesture_emoji").textContent = ui.emoji;
  document.getElementById("gesture_label").textContent = ui.label;
}

function normalizeNonVerbal(raw) {
  return {
    gesture: normalizeToken(raw.gesture),
    facialExpression: {
      type: asText(raw.facialExpression && raw.facialExpression.type),
      intensity: asUnitNumber(raw.facialExpression && raw.facialExpression.intensity),
    },
    gaze: {
      direction: asText(raw.gaze && raw.gaze.direction),
      focus: asText(raw.gaze && raw.gaze.focus),
    },
    posture: {
      type: asText(raw.posture && raw.posture.type),
      lean: asText(raw.posture && raw.posture.lean),
      openness: asUnitNumber(raw.posture && raw.posture.openness),
    },
    prosody: {
      rate: asText(raw.prosody && raw.prosody.rate),
      pitch: asText(raw.prosody && raw.prosody.pitch),
      volume: asText(raw.prosody && raw.prosody.volume),
    },
    proxemics: {
      distance: asText(raw.proxemics && raw.proxemics.distance),
    },
    motion: {
      stillness: asUnitNumber(raw.motion && raw.motion.stillness),
      energy: asUnitNumber(raw.motion && raw.motion.energy),
    },
  };
}

function asText(value) {
  if (typeof value !== "string" || !value.trim()) {
    return "N/A";
  }
  return value.trim();
}

function asUnitNumber(value) {
  const parsed = Number(value);
  if (Number.isNaN(parsed)) {
    return 0;
  }
  return Math.max(0, Math.min(1, parsed));
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = value;
  }
}

function setMeter(id, unitValue) {
  const el = document.getElementById(id);
  if (el) {
    el.style.width = `${Math.round(unitValue * 100)}%`;
  }
}

function renderNonVerbal(nonVerbal) {
  renderGesture(nonVerbal.gesture);
  setText("face_type", nonVerbal.facialExpression.type);
  setText("face_intensity_text", nonVerbal.facialExpression.intensity.toFixed(2));
  setMeter("face_intensity_meter", nonVerbal.facialExpression.intensity);

  setText("gaze_direction", nonVerbal.gaze.direction);
  setText("gaze_focus", nonVerbal.gaze.focus);

  setText("posture_type", nonVerbal.posture.type);
  setText("posture_lean", nonVerbal.posture.lean);
  setText("posture_openness_text", nonVerbal.posture.openness.toFixed(2));
  setMeter("posture_openness_meter", nonVerbal.posture.openness);

  setText("prosody_rate", nonVerbal.prosody.rate);
  setText("prosody_pitch", nonVerbal.prosody.pitch);
  setText("prosody_volume", nonVerbal.prosody.volume);

  setText("proxemics_distance", nonVerbal.proxemics.distance);

  setText("motion_stillness_text", nonVerbal.motion.stillness.toFixed(2));
  setMeter("motion_stillness_meter", nonVerbal.motion.stillness);
  setText("motion_energy_text", nonVerbal.motion.energy.toFixed(2));
  setMeter("motion_energy_meter", nonVerbal.motion.energy);
}

function addTimelineEntry(nonVerbal, createdDate) {
  const timestamp = formatTimestamp(createdDate);
  const summary = `gesture=${nonVerbal.gesture} | face=${nonVerbal.facialExpression.type} | gaze=${nonVerbal.gaze.direction} | posture=${nonVerbal.posture.type}`;
  nonverbalTimeline.unshift({ timestamp, summary });
  if (nonverbalTimeline.length > NONVERBAL_TIMELINE_MAX) {
    nonverbalTimeline = nonverbalTimeline.slice(0, NONVERBAL_TIMELINE_MAX);
  }
  renderTimeline();
}

function renderTimeline() {
  const el = document.getElementById("nonverbal_timeline");
  if (!el) {
    return;
  }
  if (!nonverbalTimeline.length) {
    el.innerHTML = '<div class="timeline-item"><strong>Now</strong>No nonverbal events yet.</div>';
    return;
  }
  el.innerHTML = nonverbalTimeline
    .map((item) => `<div class="timeline-item"><strong>${item.timestamp}</strong>${escapeHtml(item.summary)}</div>`)
    .join("");
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function formatTimestamp(createdDate) {
  if (!createdDate) {
    return new Date().toLocaleTimeString();
  }
  const date = new Date(createdDate);
  if (Number.isNaN(date.getTime())) {
    return new Date().toLocaleTimeString();
  }
  return date.toLocaleTimeString();
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

