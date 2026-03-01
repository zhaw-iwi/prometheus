let session = {
  agentId: null,
  stream: null,
};
let streamReconnectTimer = null;
let streamReconnectAttempt = 0;
let monitorStream = null;
let isPageUnloading = false;
let autoReconnectEnabled = true;
const STREAM_RECONNECT_MIN_MS = 1000;
const STREAM_RECONNECT_MAX_MS = 30000;
const STREAM_RECONNECT_JITTER = 0.2;

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
  const stream = new EventSource(`/${session.agentId}/behaviour/stream`);
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
  };
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
  if (session.stream) {
    session.stream.close();
    session.stream = null;
  }
  if (monitorStream) {
    monitorStream.close();
    monitorStream = null;
  }
  streamReconnectAttempt = 0;
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
  const gesture = normalizeGestureLabel(plan ? plan.nonVerbal : null);
  renderGesture(gesture);
  const speech = plan && typeof plan.speech === "string" ? plan.speech : "";
  document.getElementById("speech_preview").textContent = speech && speech.trim() ? speech : "(no speech)";
  appendLog(`behaviour received: gesture=${gesture}`);
}

function normalizeGestureLabel(nonVerbal) {
  if (!nonVerbal) {
    return "NONE";
  }
  if (typeof nonVerbal === "string") {
    return normalizeToken(nonVerbal);
  }
  if (typeof nonVerbal === "object") {
    if (typeof nonVerbal.gesture === "string") {
      return normalizeToken(nonVerbal.gesture);
    }
    if (typeof nonVerbal.label === "string") {
      return normalizeToken(nonVerbal.label);
    }
  }
  return "NONE";
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

