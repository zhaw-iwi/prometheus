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

const SIGNS = {
  rock: { label: "Stein", symbol: "\u270A" },
  scissor: { label: "Schere", symbol: "\u270C" },
  paper: { label: "Papier", symbol: "\u270B" },
};

window.addEventListener("load", async () => {
  session.agentId = getAgentId();
  wireUi();
  window.addEventListener("beforeunload", cleanupStreams);
  window.addEventListener("pagehide", cleanupStreams);

  if (!session.agentId) {
    appendLog("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    setStreamStatus("Stream Error");
    setControlsEnabled(false);
    return;
  }

  await loadAgentInfo();
  await loadEventHistory();
  connectMonitorStream();
  connectBehaviourStream();
});

function wireUi() {
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  document.getElementById("start_game").addEventListener("click", startGame);
  document.getElementById("ready_round").addEventListener("click", () => acknowledgeUserUtterance("Bereit."));
  document.getElementById("play_again").addEventListener("click", () => acknowledgeUserUtterance("Ja, noch eine Runde."));
  document.getElementById("stop_game").addEventListener("click", () => acknowledgeUserUtterance("Nein, bitte beende das Spiel."));
  document.querySelectorAll("[data-sign]").forEach((button) => {
    button.addEventListener("click", () => submitHandSign(button.dataset.sign));
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
}

async function startGame() {
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
  await acknowledgeEvent({
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload,
  }, `utterance sent: ${payload}`);
}

async function submitHandSign(sign) {
  const normalized = normalizeSign(sign);
  if (!normalized) {
    appendLog("unknown hand sign.");
    return;
  }
  renderUserSign(normalized);
  const payload = {
    source: "rps.web",
    hand: "right",
    sign: normalized,
    confidence: 1.0,
    detectionMode: "manual",
    ts: new Date().toISOString(),
  };
  await acknowledgeEvent({
    type: "obs.hand.sign",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(payload),
  }, `manual sign sent: ${SIGNS[normalized].label}`);
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
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    handleResponseEvent(data.responseEvent);
    appendLog(successMessage);
  } catch (error) {
    appendLog("ack failed: " + error.message);
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
