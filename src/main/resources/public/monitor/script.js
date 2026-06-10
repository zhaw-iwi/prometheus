let session = {
  agentId: null,
  currentState: null,
  innerState: null,
  innerChain: [],
  states: [],
  storage: [],
  openStorageKeys: new Set(),
  storageSnapshot: "",
};

let logSource = null;
let logReconnectAttempt = 0;
let logSettings = {
  level: "INFO",
  loggers: new Set(["ch.zhaw.prometheus.model.State"]),
  maxChars: 600,
  showTimestamps: false,
};
let logBuffer = [];
let monitorSource = null;
let monitorReconnectAttempt = 0;
let behaviourSource = null;
let behaviourBuffer = [];
let logReconnectTimer = null;
let monitorReconnectTimer = null;
let behaviourReconnectTimer = null;
let behaviourReconnectAttempt = 0;
let behaviourLastEventId = null;
let isPageUnloading = false;
const SSE_RECONNECT_MIN_MS = 1000;
const SSE_RECONNECT_MAX_MS = 30000;
const SSE_RECONNECT_JITTER = 0.2;
const LOG_BUFFER_MAX = 1000;
const BEHAVIOUR_BUFFER_MAX = 500;
const APP_MESSAGE_DEDUPE_WINDOW_MS = 3000;
let lastAppMessage = null;

window.addEventListener("load", () => {
  session.agentId = getAgentId();
  if (!session.agentId) {
    appendLog("app", "Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    disableUi();
    return;
  }
  wireUi();
  window.addEventListener("beforeunload", cleanupStreams);
  window.addEventListener("pagehide", cleanupStreams);
  connectLogs();
  connectMonitor();
  connectBehaviour();
});

function wireUi() {
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  const loggerInput = document.getElementById("log_logger_filter");
  const levelSelect = document.getElementById("log_level_filter");
  const maxCharsInput = document.getElementById("log_max_chars");
  const timestampToggle = document.getElementById("log_show_timestamps");
  const copyButton = document.getElementById("log_copy");
  const clearButton = document.getElementById("log_clear");

  loggerInput.addEventListener("change", () => {
    const selected = Array.from(loggerInput.selectedOptions)
      .map((option) => option.value.trim())
      .filter((value) => value.length > 0);
    logSettings.loggers = new Set(selected);
    renderLogBuffer();
  });
  levelSelect.addEventListener("change", () => {
    logSettings.level = levelSelect.value;
    renderLogBuffer();
  });
  maxCharsInput.addEventListener("change", () => {
    const parsed = Number.parseInt(maxCharsInput.value, 10);
    if (!Number.isNaN(parsed) && parsed > 0) {
      logSettings.maxChars = parsed;
      renderLogBuffer();
    }
  });
  timestampToggle.addEventListener("change", () => {
    logSettings.showTimestamps = timestampToggle.checked;
    renderLogBuffer();
  });
  clearButton.addEventListener("click", () => {
    logBuffer = [];
    document.getElementById("log_output").textContent = "";
  });
  copyButton.addEventListener("click", () => {
    const output = document.getElementById("log_output").textContent || "";
    copyToClipboard(output);
  });
}

function disableUi() {
  document.getElementById("show_agent_info").disabled = true;
}

function setActiveStatus(isActive) {
  const status = document.getElementById("active_status");
  if (isActive === true) {
    status.textContent = "Active";
    status.className = "status-pill is-active";
  } else if (isActive === false) {
    status.textContent = "Inactive";
    status.className = "status-pill is-inactive";
  } else {
    status.textContent = "Unknown";
    status.className = "status-pill is-unknown";
  }
}

function updateCurrentState(stateName, innerName, innerChain) {
  session.currentState = stateName;
  session.innerState = innerName;
  session.innerChain = Array.isArray(innerChain) ? innerChain : [];
  const innermost = session.innerChain.length
    ? session.innerChain[session.innerChain.length - 1]
    : innerName || stateName || "Unknown";
  document.getElementById("current_state").textContent = innermost;
  renderStateList();
}

function renderStateList() {
  const list = document.getElementById("state_list");
  list.innerHTML = "";
  session.states.forEach((stateName) => {
    const item = document.createElement("li");
    item.className = "list-group-item d-flex justify-content-between align-items-center";
    item.textContent = stateName;
    if (stateName === session.currentState || session.innerChain.includes(stateName)) {
      const badge = document.createElement("span");
      badge.className = "badge text-bg-light";
      badge.textContent = "current";
      item.appendChild(badge);
    }
    list.appendChild(item);
  });
}

function renderStorageList() {
  session.openStorageKeys = getOpenStorageKeys();
  const list = document.getElementById("storage_list");
  list.innerHTML = "";
  if (!session.storage.length) {
    const item = document.createElement("div");
    item.className = "list-group-item";
    item.textContent = "No storage entries.";
    list.appendChild(item);
    return;
  }
  session.storage.forEach((entry, index) => {
    const keyValue = entry.key || "unknown";
    const safeKey = toSafeId(keyValue);
    const item = document.createElement("div");
    item.className = "list-group-item p-0";
    const headerId = `storage_header_${safeKey}_${index}`;
    const collapseId = `storage_collapse_${safeKey}_${index}`;

    const header = document.createElement("div");
    header.className = "d-flex align-items-center justify-content-between px-3 py-2 gap-2";

    const button = document.createElement("button");
    button.className =
      "btn btn-link text-start flex-grow-1 fw-semibold text-decoration-none text-body p-0";
    button.type = "button";
    button.setAttribute("data-bs-toggle", "collapse");
    button.setAttribute("data-bs-target", `#${collapseId}`);
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", collapseId);
    button.id = headerId;
    button.textContent = keyValue;
    button.dataset.storageKey = keyValue;

    const copyButton = document.createElement("button");
    copyButton.className = "btn btn-outline-ink btn-sm";
    copyButton.type = "button";
    copyButton.title = "Copy value";
    copyButton.innerHTML = '<i class="bi bi-clipboard"></i>';
    copyButton.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      copyToClipboard(formatStorageValue(entry.value));
    });

    header.appendChild(button);
    header.appendChild(copyButton);

    const collapse = document.createElement("div");
    collapse.className = "collapse";
    collapse.id = collapseId;
    collapse.setAttribute("aria-labelledby", headerId);
    collapse.setAttribute("data-bs-parent", "#storage_list");
    if (session.openStorageKeys.has(keyValue)) {
      collapse.classList.add("show");
      button.setAttribute("aria-expanded", "true");
    }

    const body = document.createElement("div");
    body.className = "px-3 pb-3";

    const value = document.createElement("pre");
    value.className = "mono small mb-0";
    value.textContent = formatStorageValue(entry.value);

    body.appendChild(value);
    collapse.appendChild(body);
    item.appendChild(header);
    item.appendChild(collapse);
    list.appendChild(item);
  });
}

function getOpenStorageKeys() {
  const openKeys = new Set();
  document.querySelectorAll("#storage_list .collapse.show").forEach((element) => {
    const headerId = element.getAttribute("aria-labelledby");
    if (!headerId) {
      return;
    }
    const button = document.getElementById(headerId);
    if (button && button.dataset.storageKey) {
      openKeys.add(button.dataset.storageKey);
    }
  });
  return openKeys;
}

function toSafeId(value) {
  return encodeURIComponent(value)
    .replace(/%/g, "_")
    .replace(/[^a-zA-Z0-9_-]/g, "_");
}

function formatStorageValue(rawValue) {
  if (rawValue === null || rawValue === undefined) {
    return "";
  }
  if (typeof rawValue !== "string") {
    try {
      return JSON.stringify(rawValue, null, 2);
    } catch (error) {
      return String(rawValue);
    }
  }
  const trimmed = rawValue.trim();
  if (!trimmed) {
    return "";
  }
  try {
    const parsed = JSON.parse(trimmed);
    return JSON.stringify(parsed, null, 2);
  } catch (error) {
    return rawValue;
  }
}

function serializeStorage(entries) {
  try {
    return JSON.stringify(entries ?? []);
  } catch (error) {
    return String(entries);
  }
}

function copyToClipboard(value) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(value);
    return;
  }
  const fallback = document.createElement("textarea");
  fallback.value = value;
  fallback.style.position = "fixed";
  fallback.style.opacity = "0";
  document.body.appendChild(fallback);
  fallback.select();
  document.execCommand("copy");
  document.body.removeChild(fallback);
}

function connectLogs() {
  if (isPageUnloading) {
    return;
  }
  if (logReconnectTimer) {
    clearTimeout(logReconnectTimer);
    logReconnectTimer = null;
  }
  if (logSource) {
    logSource.close();
  }
  logSource = new EventSource("/logs/stream");
  logSource.addEventListener("open", () => {
    logReconnectAttempt = 0;
  });
  logSource.addEventListener("log", (event) => {
    let data = null;
    try {
      data = JSON.parse(event.data);
    } catch (_) {
      appendLog("app", "Log stream payload parse failed.");
      return;
    }
    addLogEntry({
      timestamp: new Date(data.timestamp || Date.now()).toLocaleTimeString(),
      source: "",
      message: data.message || "",
      level: data.level,
      logger: data.logger,
      isLogEvent: true,
    });
  });
  logSource.onerror = () => {
    if (logSource) {
      logSource.close();
      logSource = null;
    }
    appendLog("app", "Log stream disconnected.");
    scheduleLogReconnect();
  };
}

function connectMonitor() {
  if (isPageUnloading) {
    return;
  }
  if (monitorReconnectTimer) {
    clearTimeout(monitorReconnectTimer);
    monitorReconnectTimer = null;
  }
  if (monitorSource) {
    monitorSource.close();
  }
  monitorSource = new EventSource(`/${session.agentId}/monitor/stream`);
  monitorSource.addEventListener("open", () => {
    monitorReconnectAttempt = 0;
  });
  monitorSource.addEventListener("snapshot", (event) => {
    let data = null;
    try {
      data = JSON.parse(event.data);
    } catch (_) {
      appendLog("app", "Monitor stream payload parse failed.");
      return;
    }
    applySnapshot(data);
  });
  monitorSource.onerror = () => {
    if (monitorSource) {
      monitorSource.close();
      monitorSource = null;
    }
    appendLog("app", "Monitor stream disconnected.");
    scheduleMonitorReconnect();
  };
}

function connectBehaviour() {
  if (isPageUnloading) {
    return;
  }
  if (behaviourReconnectTimer) {
    clearTimeout(behaviourReconnectTimer);
    behaviourReconnectTimer = null;
  }
  if (behaviourSource) {
    behaviourSource.close();
  }
  behaviourSource = new EventSource(behaviourStreamUrl());
  behaviourSource.addEventListener("open", () => {
    behaviourReconnectAttempt = 0;
  });
  behaviourSource.addEventListener("behaviour", (event) => {
    if (event.lastEventId) {
      behaviourLastEventId = event.lastEventId;
    }
    let data = null;
    try {
      data = JSON.parse(event.data);
    } catch (_) {
      addBehaviourEntry({
        timestamp: new Date().toLocaleTimeString(),
        content: event.data || "",
      });
      return;
    }
    addBehaviourEntry({
      timestamp: formatEventTimestamp(data),
      content: formatBehaviourSummary(data),
    });
  });
  behaviourSource.onerror = () => {
    if (behaviourSource) {
      behaviourSource.close();
      behaviourSource = null;
    }
    appendLog("app", "Behaviour stream disconnected.");
    scheduleBehaviourReconnect();
  };
}

function behaviourStreamUrl() {
  let url = `/${session.agentId}/behaviour/stream`;
  if (behaviourLastEventId) {
    url += `?lastEventId=${encodeURIComponent(behaviourLastEventId)}`;
  }
  return url;
}

function scheduleLogReconnect() {
  if (isPageUnloading || logReconnectTimer) {
    return;
  }
  const delay = nextReconnectDelayMs(logReconnectAttempt);
  logReconnectAttempt += 1;
  logReconnectTimer = setTimeout(() => {
    logReconnectTimer = null;
    connectLogs();
  }, delay);
}

function scheduleMonitorReconnect() {
  if (isPageUnloading || monitorReconnectTimer) {
    return;
  }
  const delay = nextReconnectDelayMs(monitorReconnectAttempt);
  monitorReconnectAttempt += 1;
  monitorReconnectTimer = setTimeout(() => {
    monitorReconnectTimer = null;
    connectMonitor();
  }, delay);
}

function scheduleBehaviourReconnect() {
  if (isPageUnloading || behaviourReconnectTimer) {
    return;
  }
  const delay = nextReconnectDelayMs(behaviourReconnectAttempt);
  behaviourReconnectAttempt += 1;
  behaviourReconnectTimer = setTimeout(() => {
    behaviourReconnectTimer = null;
    connectBehaviour();
  }, delay);
}

function nextReconnectDelayMs(attempt) {
  const base = Math.min(SSE_RECONNECT_MAX_MS, SSE_RECONNECT_MIN_MS * Math.pow(2, attempt));
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * SSE_RECONNECT_JITTER);
  return Math.max(SSE_RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function cleanupStreams() {
  isPageUnloading = true;
  if (logReconnectTimer) {
    clearTimeout(logReconnectTimer);
    logReconnectTimer = null;
  }
  if (monitorReconnectTimer) {
    clearTimeout(monitorReconnectTimer);
    monitorReconnectTimer = null;
  }
  if (behaviourReconnectTimer) {
    clearTimeout(behaviourReconnectTimer);
    behaviourReconnectTimer = null;
  }
  if (logSource) {
    logSource.close();
    logSource = null;
  }
  if (monitorSource) {
    monitorSource.close();
    monitorSource = null;
  }
  if (behaviourSource) {
    behaviourSource.close();
    behaviourSource = null;
  }
  logReconnectAttempt = 0;
  monitorReconnectAttempt = 0;
  behaviourReconnectAttempt = 0;
}

function applySnapshot(data) {
  if (!data) {
    return;
  }
  session.name = data.name || session.name;
  session.description = data.description || session.description;
  document.getElementById("agent_name").textContent = session.name || "Agent";
  setActiveStatus(data.active);
  updateCurrentState(data.stateName, data.innerName, data.innerNames);
  session.states = Array.isArray(data.states) ? data.states : [];
  renderStateList();

  const storage = Array.isArray(data.storage) ? data.storage : [];
  const snapshot = serializeStorage(storage);
  if (snapshot !== session.storageSnapshot) {
    session.storageSnapshot = snapshot;
    session.storage = storage;
    renderStorageList();
  }
}

async function showAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) {
    appendLog("app", "Unable to load agent info.");
    return;
  }
  const data = await response.json();
  alert(`Name\n${data.name}\n\nDescription\n${data.description}`);
}

function appendLog(source, message) {
  if (shouldSuppressAppMessage(source, message)) {
    return;
  }
  addLogEntry({
    timestamp: new Date().toLocaleTimeString(),
    source,
    message,
    isLogEvent: false,
  });
}

function addLogEntry(entry) {
  logBuffer.push(entry);
  if (logBuffer.length > LOG_BUFFER_MAX) {
    logBuffer.shift();
  }
  renderLogBuffer();
}

function addBehaviourEntry(entry) {
  behaviourBuffer.push(entry);
  if (behaviourBuffer.length > BEHAVIOUR_BUFFER_MAX) {
    behaviourBuffer.shift();
  }
  renderBehaviourBuffer();
}

function shouldSuppressAppMessage(source, message) {
  if (source !== "app") {
    return false;
  }
  const now = Date.now();
  if (lastAppMessage
    && lastAppMessage.source === source
    && lastAppMessage.message === message
    && now - lastAppMessage.timestamp < APP_MESSAGE_DEDUPE_WINDOW_MS) {
    return true;
  }
  lastAppMessage = {
    source,
    message,
    timestamp: now,
  };
  return false;
}

function renderLogBuffer() {
  const output = document.getElementById("log_output");
  output.textContent = "";
  logBuffer.forEach((entry) => {
    if (entry.isLogEvent && !shouldIncludeLog(entry)) {
      return;
    }
    const message = entry.isLogEvent
      ? truncateLogMessage(entry.message || "", logSettings.maxChars)
      : entry.message || "";
    const sourcePrefix = entry.source ? `${entry.source}: ` : "";
    const timestampPrefix = logSettings.showTimestamps ? `[${entry.timestamp}] ` : "";
    output.textContent += `${timestampPrefix}${sourcePrefix}${message}\n`;
  });
  output.scrollTop = output.scrollHeight;
}

function shouldIncludeLog(entry) {
  const level = (entry.level || "").toUpperCase();
  const logger = entry.logger || "";
  if (logSettings.level && level !== logSettings.level.toUpperCase()) {
    return false;
  }
  if (logSettings.loggers.size > 0) {
    const matches = Array.from(logSettings.loggers).some((filter) => logger.includes(filter));
    if (!matches) {
      return false;
    }
  }
  return true;
}

function truncateLogMessage(message, maxChars) {
  if (!maxChars || message.length <= maxChars) {
    return message;
  }
  return message.slice(0, maxChars) + "...";
}

function renderBehaviourBuffer() {
  const output = document.getElementById("behaviour_output");
  if (!output) {
    return;
  }
  output.textContent = behaviourBuffer
    .map((entry) => `[${entry.timestamp}] ${entry.content}`)
    .join("\n");
  output.scrollTop = output.scrollHeight;
}

function formatEventTimestamp(event) {
  if (!event || !event.createdDate) {
    return new Date().toLocaleTimeString();
  }
  const ts = new Date(event.createdDate);
  if (Number.isNaN(ts.getTime())) {
    return new Date().toLocaleTimeString();
  }
  return ts.toLocaleTimeString();
}

function formatBehaviourSummary(event) {
  if (!event || !event.payload) {
    return "(empty behaviour event)";
  }
  try {
    const plan = JSON.parse(event.payload);
    const parts = [];
    if (plan && typeof plan.speech === "string" && plan.speech.trim()) {
      parts.push(`speech="${plan.speech}"`);
    }
    if (plan && plan.nonVerbal != null) {
      const gesture = plan.nonVerbal && typeof plan.nonVerbal === "object"
        ? plan.nonVerbal.gesture
        : null;
      if (typeof gesture === "string" && gesture.trim()) {
        parts.push(`nonVerbal=${gesture}`);
      } else {
        parts.push(`nonVerbal=${JSON.stringify(plan.nonVerbal)}`);
      }
    }
    if (plan && plan.motion != null) {
      parts.push("motion=present");
    }
    if (plan && plan.display != null) {
      parts.push("display=present");
    }
    if (!parts.length) {
      return "(behaviour plan with no populated modalities)";
    }
    return parts.join(" | ");
  } catch (_) {
    return String(event.payload);
  }
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
