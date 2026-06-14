const state = {
  accessCode: null,
  agentTypes: [],
  agentId: null,
  selectedAgentId: null,
  agents: [],
  agentInfo: null,
  behaviourSource: null,
  monitorSource: null,
  lastBehaviourEventId: null,
  seenBehaviourKeys: new Set(),
  recentBehaviourPayloads: new Map(),
  streamReconnectTimer: null,
  streamReconnectAttempt: 0,
  monitorReconnectTimer: null,
  monitorReconnectAttempt: 0,
  isPageUnloading: false,
  realtimeListening: false,
  cameraRunning: false,
  activityEntries: [],
  activityWrap: true,
  activityShowTimestamps: true,
  currentState: null,
  innerState: null,
  innerChain: [],
  availableStates: [],
  storage: [],
  openStorageKeys: new Set(),
  storageSnapshot: null,
};

const realtime = {
  peerConnection: null,
  dataChannel: null,
  micStream: null,
  callId: null,
  assistantTranscriptBuffer: "",
  assistantAudioSeen: false,
  responseActive: false,
  pushToTalkActive: false,
  spaceKeyBindingActive: false,
  pendingInputItemIds: new Set(),
  processedInputItemIds: new Set(),
  transcriptCandidates: [],
  transcriptFlushTimer: null,
};

const camera = {
  video: null,
  canvas: null,
  ctx: null,
  stream: null,
  loopTimer: null,
  faceModelsReady: false,
  socialDetectorReady: false,
  handDetectorReady: false,
  socialDetector: null,
  handRecognizer: null,
  tracks: new Map(),
  nextTrackId: 1,
  lastEmotionEmitAt: 0,
  lastSocialEmitAt: 0,
  lastEmotion: null,
  lastPresenceSignature: null,
  lastGroupingSignature: null,
  lastGestureVideoTime: -1,
  stableGestureKey: null,
  stableGestureCount: 0,
  lastCameraEmitKey: null,
  lastCameraEmitAt: 0,
};

const RECONNECT_MIN_MS = 1000;
const RECONNECT_MAX_MS = 30000;
const RECONNECT_JITTER = 0.2;
const BEHAVIOUR_DUPLICATE_WINDOW_MS = 2500;
const TRANSCRIPT_BATCH_DELAY_MS = 900;
const ACTIVITY_LOG_LIMIT = 300;
const ACCESS_CODE_STORAGE_KEY = "prometheus.valerian.accessCode";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const CAMERA_PERIOD_MS = 350;
const TRACK_TTL_MS = 1500;
const TRACK_MAX_DISTANCE_NORM = 0.14;
const PERSON_SCORE_THRESHOLD = 0.45;
const REQUIRED_STABLE_GESTURE_FRAMES = 3;
const FACE_MODEL_URI = "https://justadudewhohacks.github.io/face-api.js/models";
const MEDIAPIPE_TASKS_VERSION = "0.10.35";
const MEDIAPIPE_TASKS_URL = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}`;
const MEDIAPIPE_WASM_ROOT = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}/wasm`;
const GESTURE_MODEL_URL = "https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/gesture_recognizer.task";

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

const MANUAL_EMOTIONS = {
  neutral: { valence: 0, arousal: 0.2 },
  happy: { valence: 0.8, arousal: 0.55 },
  sad: { valence: -0.7, arousal: 0.35 },
  angry: { valence: -0.65, arousal: 0.75 },
  fearful: { valence: -0.75, arousal: 0.7 },
  surprised: { valence: 0.2, arousal: 0.8 },
};

const PROFILE_VISUAL_OBSERVATIONS = [
  "obs.emotion.face",
  "obs.human.presence",
  "obs.social.grouping",
  "obs.hand.sign",
];

const PROFILE_SENSOR_OBSERVATIONS = {
  emotion: ["obs.emotion.face"],
  social: ["obs.human.presence", "obs.social.grouping"],
  hand: ["obs.hand.sign"],
};

const HAND_CONNECTIONS = [
  [0, 1], [1, 2], [2, 3], [3, 4],
  [0, 5], [5, 6], [6, 7], [7, 8],
  [5, 9], [9, 10], [10, 11], [11, 12],
  [9, 13], [13, 14], [14, 15], [15, 16],
  [13, 17], [17, 18], [18, 19], [19, 20],
  [0, 17],
];

window.addEventListener("load", init);
window.addEventListener("beforeunload", cleanupAll);
window.addEventListener("pagehide", cleanupAll);

async function init() {
  camera.video = document.getElementById("camera_video");
  camera.canvas = document.getElementById("overlay_canvas");
  camera.ctx = camera.canvas.getContext("2d");
  wireUi();
  showCockpit(false);
  applyInteractionProfile(null);
  resetStateView();
  resetStorageList();
  updatePushToTalkUi();
  appendSystemMessage("Select or create an agent instance, then connect.");

  state.selectedAgentId = getAgentIdFromLocation();
  const storedAccessCode = sessionStorage.getItem(ACCESS_CODE_STORAGE_KEY);
  if (storedAccessCode) {
    document.getElementById("access_code_input").value = storedAccessCode;
    await openAccessSession(storedAccessCode, { fromStorage: true });
  } else {
    setAccessStatus("");
    renderAgentTypes();
    renderAgents();
    updateSelectedAgentStatus();
    setControlsEnabled(false);
  }
}

function wireUi() {
  document.getElementById("submit_access_code").addEventListener("click", submitAccessCode);
  document.getElementById("access_code_input").addEventListener("keydown", handleAccessCodeKeyDown);
  document.getElementById("clear_access_code").addEventListener("click", clearAccessSession);
  document.getElementById("agent_type_select").addEventListener("change", updateAgentTypeControls);
  document.getElementById("create_agent_instance").addEventListener("click", createAgentInstance);
  document.getElementById("delete_agent").addEventListener("click", deleteSelectedAgent);
  document.getElementById("connect_agent").addEventListener("click", async () => {
    if (state.agentId) {
      await disconnectAgent({ preserveInput: true, message: "Disconnected." });
    } else {
      await connectToAgent(document.getElementById("agent_id_input").value.trim());
    }
  });
  document.getElementById("agent_select").addEventListener("change", async (event) => {
    await selectAgent(event.target.value, { updateInput: true });
  });
  document.getElementById("agent_id_input").addEventListener("input", async (event) => {
    await selectAgent(event.target.value, { updateInput: false, updateSelect: true });
  });
  document.getElementById("start_agent").addEventListener("click", startAgent);
  document.getElementById("reset_agent").addEventListener("click", resetAgent);
  document.getElementById("send_text").addEventListener("click", sendTextInput);
  document.getElementById("text_input").addEventListener("keydown", handleTextKeyDown);
  document.getElementById("clear_messages").addEventListener("click", clearMessages);
  document.getElementById("clear_activity_log").addEventListener("click", clearActivityLog);
  document.getElementById("activity_log_wrap").addEventListener("change", (event) => {
    state.activityWrap = event.target.checked;
    renderActivityLog();
  });
  document.getElementById("activity_log_timestamps").addEventListener("change", (event) => {
    state.activityShowTimestamps = event.target.checked;
    renderActivityLog();
  });
  document.getElementById("toggle_realtime").addEventListener("click", toggleRealtime);
  document.getElementById("voice_select").addEventListener("change", applySessionSettings);
  document.getElementById("turn_detection_select").addEventListener("change", () => {
    updatePushToTalkUi();
    applySessionSettings();
  });
  document.getElementById("push_to_talk").addEventListener("mousedown", startPushToTalk);
  document.getElementById("push_to_talk").addEventListener("touchstart", startPushToTalk, { passive: false });
  document.getElementById("push_to_talk").addEventListener("mouseup", stopPushToTalk);
  document.getElementById("push_to_talk").addEventListener("mouseleave", stopPushToTalk);
  document.getElementById("push_to_talk").addEventListener("touchend", stopPushToTalk);
  document.getElementById("push_to_talk").addEventListener("touchcancel", stopPushToTalk);
  document.getElementById("start_camera").addEventListener("click", startCamera);
  document.getElementById("stop_camera").addEventListener("click", () => stopCamera());

  document.querySelectorAll("[data-utterance]").forEach((button) => {
    button.addEventListener("click", () => sendUserUtterance(button.dataset.utterance, { renderUser: true }));
  });
  document.querySelectorAll("[data-emotion]").forEach((button) => {
    button.addEventListener("click", () => submitEmotionSample(button.dataset.emotion));
  });
  document.querySelectorAll("[data-sign]").forEach((button) => {
    button.addEventListener("click", () => submitHandSign(button.dataset.sign, {
      source: "rps.web",
      detectionMode: "manual",
      confidence: 1.0,
    }));
  });
  document.querySelectorAll("[data-social-sample]").forEach((button) => {
    button.addEventListener("click", () => submitSocialSample(button.dataset.socialSample));
  });
  document.querySelectorAll("#sensor_emotion_enabled,#sensor_social_enabled,#sensor_hand_enabled").forEach((input) => {
    input.addEventListener("change", handleSensorModeChange);
  });
}

function handleAccessCodeKeyDown(event) {
  if (event.key !== "Enter") {
    return;
  }
  event.preventDefault();
  submitAccessCode();
}

async function submitAccessCode() {
  const code = document.getElementById("access_code_input").value;
  await openAccessSession(code);
}

async function openAccessSession(accessCode, options = {}) {
  if (!accessCode) {
    setAccessStatus("Access code required.", "error");
    return false;
  }
  setAccessStatus("Checking access code.");
  const button = document.getElementById("submit_access_code");
  button.disabled = true;
  try {
    const response = await fetch("/demo/session", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ accessCode }),
    });
    if (!response.ok) {
      throw new Error(`session rejected: ${response.status}`);
    }
    const session = await response.json();
    state.accessCode = session.accessCode || accessCode;
    state.agentTypes = Array.isArray(session.agentTypes) ? session.agentTypes : [];
    state.agents = Array.isArray(session.agents) ? session.agents : [];
    sessionStorage.setItem(ACCESS_CODE_STORAGE_KEY, state.accessCode);
    document.getElementById("active_access_code").textContent = `Access ${state.accessCode}`;
    showCockpit(true);
    setAccessStatus("Access accepted.", "success");
    renderAgentTypes();
    renderAgents();
    if (state.selectedAgentId) {
      document.getElementById("agent_id_input").value = state.selectedAgentId;
      document.getElementById("agent_select").value = state.selectedAgentId;
      updateSelectedAgentStatus();
      await connectToAgent(state.selectedAgentId);
    } else {
      updateSelectedAgentStatus();
      setControlsEnabled(false);
    }
    if (!options.fromStorage) {
      appendSystemMessage("Access accepted. Create or select an agent instance.");
    }
    return true;
  } catch (error) {
    sessionStorage.removeItem(ACCESS_CODE_STORAGE_KEY);
    state.accessCode = null;
    state.agentTypes = [];
    state.agents = [];
    renderAgentTypes();
    renderAgents();
    showCockpit(false);
    setControlsEnabled(false);
    setAccessStatus("Access code rejected.", "error");
    appendLog("app", error.message);
    return false;
  } finally {
    button.disabled = false;
    updateAgentTypeControls();
    updateSelectedAgentStatus();
  }
}

async function clearAccessSession() {
  await disconnectAgent({ preserveInput: false, silent: true });
  sessionStorage.removeItem(ACCESS_CODE_STORAGE_KEY);
  state.accessCode = null;
  state.agentTypes = [];
  state.agents = [];
  state.selectedAgentId = null;
  document.getElementById("access_code_input").value = "";
  document.getElementById("agent_id_input").value = "";
  setAccessStatus("");
  renderAgentTypes();
  renderAgents();
  showCockpit(false);
  setControlsEnabled(false);
}

function showCockpit(visible) {
  const accessScreen = document.getElementById("access_screen");
  const cockpitShell = document.getElementById("cockpit_shell");
  accessScreen.hidden = visible;
  accessScreen.classList.toggle("d-none", visible);
  cockpitShell.hidden = !visible;
  cockpitShell.classList.toggle("d-none", !visible);
}

function setAccessStatus(message, mode) {
  const status = document.getElementById("access_code_status");
  status.textContent = message || "";
  status.className = `access-status mt-2${mode ? ` is-${mode}` : ""}`;
}

async function loadAgents() {
  if (!state.accessCode) {
    state.agents = [];
    renderAgents();
    return;
  }
  try {
    const response = await scopedFetch("/demo/agents");
    if (!response.ok) {
      appendLog("app", `agent list failed: ${response.status}`);
      return;
    }
    state.agents = await response.json();
    renderAgents();
  } catch (error) {
    appendLog("app", "agent list failed: " + error.message);
  }
}

function renderAgentTypes() {
  const select = document.getElementById("agent_type_select");
  select.innerHTML = '<option value="">Select agent type</option>';
  const sorted = [...state.agentTypes].sort((a, b) => agentTypeSortKey(a).localeCompare(agentTypeSortKey(b)));
  for (const type of sorted) {
    if (!type || !type.key) {
      continue;
    }
    const option = document.createElement("option");
    option.value = type.key;
    option.textContent = prometheusFacingText(type.displayName || type.key);
    option.title = prometheusFacingText(type.description || type.key);
    select.appendChild(option);
  }
  updateAgentTypeControls();
}

function renderAgents() {
  const select = document.getElementById("agent_select");
  select.innerHTML = '<option value="">Select agent</option>';
  const sorted = [...state.agents].sort((a, b) => agentSortKey(a).localeCompare(agentSortKey(b)));
  for (const agent of sorted) {
    const id = agentIdOf(agent);
    if (!id) {
      continue;
    }
    const option = document.createElement("option");
    option.value = id;
    option.textContent = prometheusFacingText(agent.name || id);
    select.appendChild(option);
  }
  if (state.selectedAgentId) {
    select.value = state.selectedAgentId;
  }
  updateAgentSelectionControls();
}

async function createAgentInstance() {
  const agentDefinitionKey = document.getElementById("agent_type_select").value;
  if (!state.accessCode || !agentDefinitionKey) {
    setAgentTypeStatus("Select an agent type first.", "error");
    return;
  }
  setAgentTypeStatus("Creating instance.");
  const button = document.getElementById("create_agent_instance");
  button.disabled = true;
  try {
    const response = await scopedFetch("/demo/agents", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ agentDefinitionKey }),
    });
    if (!response.ok) {
      setAgentTypeStatus(`Create failed: ${response.status}`, "error");
      appendLog("app", `create agent failed: ${response.status}`);
      return;
    }
    const agent = await response.json();
    state.agents = mergeAgents(state.agents, [agent]);
    renderAgents();
    const createdId = agentIdOf(agent);
    if (createdId) {
      await selectAgent(createdId, { updateInput: true, updateSelect: true });
    }
    setAgentTypeStatus("Instance created.", "success");
    appendSystemMessage(`Created ${prometheusFacingText(agent.name || createdId || "agent instance")}.`);
  } catch (error) {
    setAgentTypeStatus("Create failed.", "error");
    appendLog("app", "create agent failed: " + error.message);
  } finally {
    updateAgentTypeControls();
  }
}

async function deleteSelectedAgent() {
  const selectedAgentId = state.selectedAgentId || document.getElementById("agent_id_input").value;
  if (!state.accessCode || !selectedAgentId || !isVisibleAgentId(selectedAgentId)) {
    return;
  }
  if (state.agentId === selectedAgentId) {
    await disconnectAgent({ preserveInput: false, silent: true });
  }
  try {
    const response = await scopedFetch(`/demo/agents/${encodeURIComponent(selectedAgentId)}`, { method: "DELETE" });
    if (!response.ok && response.status !== 404) {
      appendLog("app", `delete failed: ${response.status}`);
      return;
    }
    state.agents = state.agents.filter((agent) => agentIdOf(agent) !== selectedAgentId);
    if (state.selectedAgentId === selectedAgentId) {
      state.selectedAgentId = null;
      document.getElementById("agent_id_input").value = "";
    }
    renderAgents();
    updateSelectedAgentStatus();
    appendSystemMessage("Agent instance deleted.");
  } catch (error) {
    appendLog("app", "delete failed: " + error.message);
  }
}

async function selectAgent(agentId, options = {}) {
  const selectedAgentId = typeof agentId === "string" ? agentId.trim() : "";
  state.selectedAgentId = selectedAgentId || null;
  if (options.updateInput !== false) {
    document.getElementById("agent_id_input").value = selectedAgentId;
  }
  if (options.updateSelect !== false) {
    document.getElementById("agent_select").value = selectedAgentId;
  }
  if (state.agentId && state.agentId !== selectedAgentId) {
    await disconnectAgent({ preserveInput: true, silent: true });
  }
  updateSelectedAgentStatus();
}

async function connectToAgent(agentId) {
  const selectedAgentId = typeof agentId === "string" ? agentId.trim() : "";
  if (!selectedAgentId) {
    await disconnectAgent({ preserveInput: false, silent: true });
    appendSystemMessage("Missing agent ID.");
    return;
  }
  state.selectedAgentId = selectedAgentId;
  if (state.realtimeListening) {
    await stopRealtime();
  }
  if (state.cameraRunning) {
    stopCamera({ silent: true });
  }
  cleanupStreams();
  state.agentId = selectedAgentId;
  document.getElementById("agent_id_input").value = selectedAgentId;
  document.getElementById("agent_select").value = selectedAgentId;
  updateSelectedAgentStatus();
  state.lastBehaviourEventId = null;
  resetBehaviourDeduplication();
  setControlsEnabled(false);
  const infoLoaded = await loadAgentInfo();
  if (!infoLoaded) {
    appendSystemMessage("Agent not found or unavailable.");
    await disconnectAgent({ preserveInput: true, silent: true });
    return;
  }
  clearMessages();
  appendSystemMessage("Connected.");
  await loadEventHistory();
  await loadStorage();
  await loadAgentState();
  setControlsEnabled(true);
  updateConnectionButton();
  connectBehaviourStream();
  connectMonitorStream();
}

async function loadAgentInfo() {
  if (!state.agentId) {
    return false;
  }
  try {
    const response = await scopedFetch(demoAgentPath("/info"));
    if (!response.ok) {
      appendLog("app", `agent info failed: ${response.status}`);
      resetAgentInfo();
      return false;
    }
    const data = await response.json();
    state.agentInfo = data;
    document.getElementById("agent_subtitle").textContent = prometheusFacingText(data.name) || "PROMETHEUS demo console";
    document.getElementById("agent_info_name").textContent = prometheusFacingText(data.name) || "-";
    document.getElementById("agent_info_description").textContent = prometheusFacingText(data.description) || "-";
    renderAgentInteractionProfile(data.interactionProfile);
    setActiveStatus(data.active);
    applyInteractionProfile(data.interactionProfile);
    return true;
  } catch (error) {
    appendLog("app", "agent info failed: " + error.message);
    resetAgentInfo();
    return false;
  }
}

async function disconnectAgent(options = {}) {
  if (state.realtimeListening) {
    await stopRealtime();
  }
  if (state.cameraRunning) {
    stopCamera({ silent: true });
  }
  cleanupStreams();
  state.agentId = null;
  state.agentInfo = null;
  state.lastBehaviourEventId = null;
  resetBehaviourDeduplication();
  if (!options.preserveInput) {
    document.getElementById("agent_id_input").value = "";
    state.selectedAgentId = null;
  }
  document.getElementById("agent_select").value = state.selectedAgentId || "";
  resetStorageList();
  resetStateView();
  setBehaviourStatus("Behaviour Idle", "idle");
  resetAgentInfo();
  setControlsEnabled(false);
  updateSelectedAgentStatus();
  updateConnectionButton();
  if (!options.silent && options.message) {
    appendSystemMessage(options.message);
  }
}

function clearAgentConnection(options = {}) {
  return disconnectAgent(options);
}

function resetAgentInfo() {
  state.agentInfo = null;
  document.getElementById("agent_subtitle").textContent = "PROMETHEUS demo console";
  document.getElementById("agent_info_name").textContent = "-";
  document.getElementById("agent_info_description").textContent = "-";
  renderAgentInteractionProfile(null);
  setActiveStatus(null);
  applyInteractionProfile(null);
}

function renderAgentInteractionProfile(profile) {
  renderProfileTokenList("agent_profile_observations", normalizeProfileList(profile && profile.supportedObservations));
  renderProfileTokenList("agent_profile_behaviours", normalizeProfileList(profile && profile.supportedBehaviourModalities));
  renderProfileTokenList("agent_profile_tags", normalizeProfileList(profile && profile.profileTags));
}

function renderProfileTokenList(id, values) {
  const container = document.getElementById(id);
  if (!container) {
    return;
  }
  container.replaceChildren();
  if (!Array.isArray(values) || values.length === 0) {
    container.textContent = "-";
    return;
  }
  for (const value of values) {
    const token = document.createElement("span");
    token.className = "profile-token";
    token.textContent = value;
    container.appendChild(token);
  }
}

function applyInteractionProfile(profile) {
  const capabilities = resolveInteractionCapabilities(profile);
  document.querySelectorAll("[data-profile-observations],[data-profile-behaviours]").forEach((element) => {
    const visible = profileElementVisible(element, capabilities);
    setProfileElementVisible(element, visible);
  });
  updateVisualSensingEmptyState(capabilities);
  resetUnsupportedSensorModes(capabilities);
}

function resolveInteractionCapabilities(profile) {
  const supportedObservations = normalizeProfileList(profile && profile.supportedObservations);
  const supportedBehaviourModalities = normalizeProfileList(profile && profile.supportedBehaviourModalities);
  return {
    supportedObservations,
    supportedBehaviourModalities,
    fallbackAll: supportedObservations.length === 0 && supportedBehaviourModalities.length === 0,
  };
}

function normalizeProfileList(values) {
  if (!Array.isArray(values)) {
    return [];
  }
  return Array.from(new Set(values
    .filter((value) => typeof value === "string" && value.trim())
    .map((value) => value.trim())));
}

function profileElementVisible(element, capabilities) {
  if (capabilities.fallbackAll) {
    return true;
  }
  const requiredObservations = profileTokens(element.dataset.profileObservations);
  const requiredBehaviourModalities = profileTokens(element.dataset.profileBehaviours);
  if (requiredObservations.length === 0 && requiredBehaviourModalities.length === 0) {
    return true;
  }
  return profileListIntersects(capabilities.supportedObservations, requiredObservations)
    || profileListIntersects(capabilities.supportedBehaviourModalities, requiredBehaviourModalities);
}

function setProfileElementVisible(element, visible) {
  if (!element) {
    return;
  }
  element.hidden = !visible;
  element.classList.toggle("d-none", !visible);
}

function updateVisualSensingEmptyState(capabilities) {
  const hasVisualSensing = capabilities.fallbackAll
    || profileListIntersects(capabilities.supportedObservations, PROFILE_VISUAL_OBSERVATIONS);
  setProfileElementVisible(document.getElementById("sensing_accordion"), hasVisualSensing);
  setProfileElementVisible(document.getElementById("no_visual_sensing_message"), !hasVisualSensing);
  if (!hasVisualSensing && state.cameraRunning) {
    stopCamera({ silent: true });
  }
}

function profileTokens(value) {
  if (typeof value !== "string" || !value.trim()) {
    return [];
  }
  return value.split(/\s+/).map((token) => token.trim()).filter(Boolean);
}

function profileListIntersects(supported, required) {
  if (!Array.isArray(supported) || !Array.isArray(required)) {
    return false;
  }
  return required.some((requiredToken) => supported
    .some((supportedToken) => profileTokenMatches(supportedToken, requiredToken)));
}

function profileTokenMatches(supportedToken, requiredToken) {
  if (!supportedToken || !requiredToken) {
    return false;
  }
  return supportedToken === requiredToken
    || supportedToken.startsWith(`${requiredToken}.`)
    || requiredToken.startsWith(`${supportedToken}.`);
}

function resetUnsupportedSensorModes(capabilities) {
  if (capabilities.fallbackAll) {
    return;
  }
  for (const [mode, observations] of Object.entries(PROFILE_SENSOR_OBSERVATIONS)) {
    if (profileListIntersects(capabilities.supportedObservations, observations)) {
      continue;
    }
    const input = document.getElementById(`sensor_${mode}_enabled`);
    if (input) {
      input.checked = false;
    }
  }
  const hasVisualObservation = profileListIntersects(capabilities.supportedObservations, PROFILE_VISUAL_OBSERVATIONS);
  if (!hasVisualObservation) {
    const emit = document.getElementById("sensor_emit_enabled");
    if (emit) {
      emit.checked = false;
    }
  }
  resetDisabledSensorState();
}

function updateSelectedAgentStatus() {
  const selected = state.selectedAgentId || document.getElementById("agent_id_input").value.trim();
  const text = state.agentId
    ? `Connected to ${state.agentId}`
    : selected
      ? `Selected ${selected}`
      : "No agent selected";
  setText("agent_connection_state", text);
  updateConnectionButton();
  updateAgentSelectionControls();
}

function updateConnectionButton() {
  const button = document.getElementById("connect_agent");
  if (!button) {
    return;
  }
  if (state.agentId) {
    button.innerHTML = '<i class="bi bi-plug-fill me-2"></i>Disconnect';
    button.classList.remove("btn-outline-ink");
    button.classList.add("btn-outline-danger");
    button.setAttribute("aria-pressed", "true");
  } else {
    button.innerHTML = '<i class="bi bi-plug me-2"></i>Connect';
    button.classList.add("btn-outline-ink");
    button.classList.remove("btn-outline-danger");
    button.setAttribute("aria-pressed", "false");
  }
}

async function loadEventHistory() {
  try {
    const response = await scopedFetch(demoAgentPath("/eventhistory"));
    if (!response.ok) {
      appendLog("app", `event history failed: ${response.status}`);
      return [];
    }
    const events = await response.json();
    for (const event of events || []) {
      if (event.type === "resp.behaviour_plan") {
        handleBehaviourEnvelope(event, { fromHistory: true });
      } else if (event.type === "obs.hand.sign") {
        renderUserSignFromPayload(event.payload);
      } else if (event.type === "obs.social.situation_change") {
        renderLatestEvent(event);
      } else if (event.type === "obs.user_utterance") {
        renderHistoricalUserUtterance(event);
      }
    }
    return events || [];
  } catch (error) {
    appendLog("app", "event history failed: " + error.message);
    return [];
  }
}

async function loadStorage() {
  if (!state.agentId) {
    resetStorageList();
    return;
  }
  try {
    const response = await scopedFetch(demoAgentPath("/storage"));
    if (!response.ok) {
      resetStorageList();
      return;
    }
    const storage = await response.json();
    setStorageEntries(storage);
  } catch (_) {
    resetStorageList();
  }
}

async function loadAgentState() {
  if (!state.agentId) {
    resetStateView();
    return;
  }
  try {
    const [stateResponse, statesResponse] = await Promise.all([
      scopedFetch(demoAgentPath("/state")),
      scopedFetch(demoAgentPath("/states")),
    ]);
    const stateInfo = stateResponse.ok ? await stateResponse.json() : null;
    const states = statesResponse.ok ? await statesResponse.json() : [];
    applyStateSnapshot({
      stateName: stateInfo && stateInfo.name,
      innerName: stateInfo && stateInfo.innerName,
      innerNames: Array.isArray(stateInfo && stateInfo.innerNames) ? stateInfo.innerNames : [],
      states: Array.isArray(states) ? states : [],
    });
  } catch (error) {
    appendLog("app", "state load failed: " + error.message);
    resetStateView();
  }
}

function applyMonitorSnapshot(data) {
  if (!data) {
    return;
  }
  if (typeof data.active === "boolean") {
    setActiveStatus(data.active);
  }
  applyStateSnapshot(data);
  if (Array.isArray(data.storage)) {
    setStorageEntries(data.storage);
  }
}

function applyStateSnapshot(data) {
  if (!data) {
    return;
  }
  const hasStateInfo = data.stateName || data.name || data.innerName || Array.isArray(data.innerNames);
  if (hasStateInfo) {
    updateCurrentState(data.stateName || data.name || null, data.innerName || null, data.innerNames || []);
  }
  if (Array.isArray(data.states)) {
    state.availableStates = data.states;
    renderStateList();
  }
}

function resetStateView() {
  state.currentState = null;
  state.innerState = null;
  state.innerChain = [];
  state.availableStates = [];
  setText("diagnostics_current_state", "Unknown");
  renderStateList();
}

function updateCurrentState(stateName, innerName, innerChain) {
  state.currentState = stateName;
  state.innerState = innerName;
  state.innerChain = Array.isArray(innerChain) ? innerChain : [];
  const innermost = state.innerChain.length
    ? state.innerChain[state.innerChain.length - 1]
    : innerName || stateName || "Unknown";
  setText("diagnostics_current_state", innermost);
  renderStateList();
}

function renderStateList() {
  const list = document.getElementById("diagnostics_state_list");
  if (!list) {
    return;
  }
  list.innerHTML = "";
  const names = state.availableStates.length
    ? state.availableStates
    : (state.currentState ? [state.currentState] : []);
  if (!names.length) {
    const item = document.createElement("li");
    item.className = "list-group-item";
    item.textContent = "No states available.";
    list.appendChild(item);
    return;
  }
  names.forEach((stateName) => {
    const item = document.createElement("li");
    item.className = "list-group-item d-flex justify-content-between align-items-center gap-2";
    const label = document.createElement("span");
    label.textContent = stateName;
    item.appendChild(label);
    if (stateName === state.currentState || state.innerChain.includes(stateName)) {
      const badge = document.createElement("span");
      badge.className = "badge text-bg-light";
      badge.textContent = "current";
      item.appendChild(badge);
    }
    list.appendChild(item);
  });
}

function setStorageEntries(entries) {
  const nextEntries = Array.isArray(entries) ? entries : [];
  const snapshot = serializeStorage(nextEntries);
  if (snapshot === state.storageSnapshot) {
    return;
  }
  state.storageSnapshot = snapshot;
  state.storage = nextEntries;
  renderStorageList();
}

function resetStorageList() {
  state.storage = [];
  state.openStorageKeys = new Set();
  state.storageSnapshot = null;
  renderStorageList();
}

function renderStorageList() {
  state.openStorageKeys = getOpenStorageKeys();
  const list = document.getElementById("storage_list");
  if (!list) {
    return;
  }
  list.innerHTML = "";
  if (!state.storage.length) {
    const item = document.createElement("div");
    item.className = "list-group-item";
    item.textContent = "No storage entries.";
    list.appendChild(item);
    return;
  }
  state.storage.forEach((entry, index) => {
    const keyValue = entry && entry.key ? entry.key : "unknown";
    const safeKey = toSafeId(keyValue);
    const item = document.createElement("div");
    item.className = "list-group-item p-0";
    const headerId = `storage_header_${safeKey}_${index}`;
    const collapseId = `storage_collapse_${safeKey}_${index}`;

    const header = document.createElement("div");
    header.className = "d-flex align-items-center justify-content-between px-3 py-2 gap-2";

    const button = document.createElement("button");
    button.className = "btn btn-link text-start flex-grow-1 fw-semibold text-decoration-none text-body p-0";
    button.type = "button";
    button.id = headerId;
    button.textContent = keyValue;
    button.dataset.storageKey = keyValue;
    button.setAttribute("data-bs-toggle", "collapse");
    button.setAttribute("data-bs-target", `#${collapseId}`);
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", collapseId);

    const copyButton = document.createElement("button");
    copyButton.className = "btn btn-outline-ink btn-sm toolbar-button";
    copyButton.type = "button";
    copyButton.title = "Copy value";
    copyButton.setAttribute("aria-label", `Copy ${keyValue} value`);
    copyButton.setAttribute("data-testid", "storage-copy-button");
    copyButton.innerHTML = '<i class="bi bi-clipboard"></i>';
    copyButton.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      copyToClipboard(formatStorageValue(entry && entry.value));
    });

    header.appendChild(button);
    header.appendChild(copyButton);

    const collapse = document.createElement("div");
    collapse.className = "collapse";
    collapse.id = collapseId;
    collapse.setAttribute("aria-labelledby", headerId);
    collapse.setAttribute("data-bs-parent", "#storage_list");
    if (state.openStorageKeys.has(keyValue)) {
      collapse.classList.add("show");
      button.setAttribute("aria-expanded", "true");
    }

    const body = document.createElement("div");
    body.className = "px-3 pb-3";

    const value = document.createElement("pre");
    value.className = "storage-value mono mb-0";
    value.textContent = formatStorageValue(entry && entry.value);

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
    navigator.clipboard.writeText(value).catch(() => copyToClipboardFallback(value));
    return;
  }
  copyToClipboardFallback(value);
}

function copyToClipboardFallback(value) {
  const fallback = document.createElement("textarea");
  fallback.value = value;
  fallback.style.position = "fixed";
  fallback.style.opacity = "0";
  document.body.appendChild(fallback);
  fallback.select();
  document.execCommand("copy");
  document.body.removeChild(fallback);
}

function connectBehaviourStream() {
  if (!state.agentId || state.behaviourSource || state.isPageUnloading) {
    return;
  }
  if (state.streamReconnectTimer) {
    clearTimeout(state.streamReconnectTimer);
    state.streamReconnectTimer = null;
  }
  state.behaviourSource = new EventSource(behaviourStreamUrl());
  setBehaviourStatus("Behaviour Connecting", "idle");
  state.behaviourSource.addEventListener("open", () => {
    state.streamReconnectAttempt = 0;
    setBehaviourStatus("Behaviour Live", "live");
    appendLog("stream", "behaviour stream connected.");
  });
  state.behaviourSource.addEventListener("behaviour", (event) => {
    if (event.lastEventId) {
      state.lastBehaviourEventId = event.lastEventId;
    }
    try {
      handleBehaviourEnvelope(JSON.parse(event.data));
    } catch (_) {
      appendLog("stream", "invalid behaviour event.");
    }
  });
  state.behaviourSource.onerror = () => {
    closeBehaviourStream();
    setBehaviourStatus("Behaviour Error", "error");
    scheduleBehaviourReconnect();
  };
}

function connectMonitorStream() {
  if (!state.agentId || state.monitorSource || state.isPageUnloading) {
    return;
  }
  state.monitorSource = new EventSource(monitorStreamUrl());
  state.monitorSource.addEventListener("open", () => {
    state.monitorReconnectAttempt = 0;
  });
  state.monitorSource.addEventListener("snapshot", (event) => {
    try {
      const data = JSON.parse(event.data);
      applyMonitorSnapshot(data);
    } catch (_) {
      return;
    }
  });
  state.monitorSource.onerror = () => {
    if (state.monitorSource) {
      state.monitorSource.close();
      state.monitorSource = null;
    }
    scheduleMonitorReconnect();
  };
}

function behaviourStreamUrl() {
  const params = new URLSearchParams();
  if (state.accessCode) {
    params.set("accessCode", state.accessCode);
  }
  if (state.lastBehaviourEventId) {
    params.set("lastEventId", state.lastBehaviourEventId);
  }
  return `${demoAgentPath("/behaviour/stream")}?${params.toString()}`;
}

function monitorStreamUrl() {
  const params = new URLSearchParams();
  if (state.accessCode) {
    params.set("accessCode", state.accessCode);
  }
  return `${demoAgentPath("/monitor/stream")}?${params.toString()}`;
}

function closeBehaviourStream() {
  if (state.behaviourSource) {
    state.behaviourSource.close();
    state.behaviourSource = null;
  }
}

function scheduleBehaviourReconnect() {
  if (state.isPageUnloading || state.streamReconnectTimer) {
    return;
  }
  state.streamReconnectTimer = setTimeout(() => {
    state.streamReconnectTimer = null;
    connectBehaviourStream();
  }, nextReconnectDelayMs(state.streamReconnectAttempt++));
}

function scheduleMonitorReconnect() {
  if (state.isPageUnloading || state.monitorReconnectTimer) {
    return;
  }
  state.monitorReconnectTimer = setTimeout(() => {
    state.monitorReconnectTimer = null;
    connectMonitorStream();
  }, nextReconnectDelayMs(state.monitorReconnectAttempt++));
}

async function startAgent() {
  if (!state.agentId) {
    return;
  }
  try {
    const response = await scopedFetch(demoAgentPath("/start"), { method: "POST" });
    if (!response.ok) {
      appendLog("app", `start failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    handleResponseEvent(data.responseEvent);
    await loadAgentState();
    await loadStorage();
    appendLog("app", "agent started.");
  } catch (error) {
    appendLog("app", "start failed: " + error.message);
  }
}

async function resetAgent() {
  if (!state.agentId || !window.confirm("Reset this agent event history?")) {
    return;
  }
  try {
    const response = await scopedFetch(demoAgentPath("/reset"), { method: "DELETE" });
    if (!response.ok) {
      appendLog("app", `reset failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    clearMessages();
    resetBehaviourDeduplication();
    resetBehaviourPanels();
    handleResponseEvent(data.responseEvent);
    await loadAgentState();
    await loadStorage();
    appendLog("app", "agent reset.");
  } catch (error) {
    appendLog("app", "reset failed: " + error.message);
  }
}

function handleTextKeyDown(event) {
  if (event.key !== "Enter" || event.shiftKey) {
    return;
  }
  event.preventDefault();
  sendTextInput();
}

async function sendTextInput() {
  const input = document.getElementById("text_input");
  const text = input.value.trim();
  if (!text) {
    return;
  }
  input.value = "";
  await sendUserUtterance(text, { renderUser: true });
}

async function sendUserUtterance(text, options = {}) {
  if (!state.agentId || !text) {
    return false;
  }
  if (options.renderUser) {
    appendMessage("user", text);
  }
  const data = await acknowledgeEvent({
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload: text,
  }, { renderResponse: true });
  if (!data) {
    return false;
  }
  if (!data.responseEvent) {
    await generateBehaviour("full_plan");
  }
  await loadStorage();
  await loadAgentState();
  return true;
}

async function acknowledgeEvent(request, options = {}) {
  if (!state.agentId) {
    appendLog("app", "ack skipped: no agent.");
    return null;
  }
  const profile = options.profile ? `?profile=${encodeURIComponent(options.profile)}` : "";
  try {
    const response = await scopedFetch(demoAgentPath(`/acknowledge${profile}`), {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify(request),
    });
    if (!response.ok) {
      appendLog("ack", `${request.type} failed: ${response.status}`);
      return null;
    }
    const data = await response.json();
    if (data && typeof data.active === "boolean") {
      setActiveStatus(data.active);
    }
    renderLatestEvent({ type: request.type, payload: request.payload });
    appendLog("ack", request.type);
    if (options.renderResponse !== false) {
      handleResponseEvent(data.responseEvent);
    }
    return data;
  } catch (error) {
    appendLog("ack", `${request.type} failed: ${error.message}`);
    return null;
  }
}

async function generateBehaviour(outputProfile) {
  try {
    const response = await scopedFetch(demoAgentPath("/behaviour/generate"), {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ outputProfile }),
    });
    if (!response.ok && response.status !== 409) {
      appendLog("policy", `generate failed: ${response.status}`);
      return false;
    }
    appendLog("policy", response.status === 409 ? "no behaviour generated." : "behaviour generated.");
    return response.ok;
  } catch (error) {
    appendLog("policy", "generate failed: " + error.message);
    return false;
  }
}

function handleResponseEvent(responseEvent) {
  if (!responseEvent) {
    return;
  }
  if (responseEvent.type === "resp.behaviour_plan") {
    handleBehaviourEnvelope(responseEvent);
  } else {
    renderLatestEvent(responseEvent);
  }
}

function handleBehaviourEnvelope(event, options = {}) {
  if (!event || event.type !== "resp.behaviour_plan" || !event.payload) {
    return;
  }
  const key = behaviourEventKey(event);
  if ((key && state.seenBehaviourKeys.has(key))
    || (!options.fromHistory && recentBehaviourPayloadSeen(event.payload))) {
    return;
  }
  if (key) {
    state.seenBehaviourKeys.add(key);
  }
  if (!options.fromHistory) {
    rememberRecentBehaviourPayload(event.payload);
  }
  let plan = null;
  try {
    plan = JSON.parse(event.payload);
  } catch (_) {
    appendLog("behaviour", "payload is not valid json.");
    return;
  }
  renderBehaviourPlan(plan);
  renderLatestEvent(event);
  if (options.renderTranscript !== false && typeof plan.speech === "string" && plan.speech.trim()) {
    appendMessage("assistant", plan.speech.trim());
  }
}

function resetBehaviourDeduplication() {
  state.seenBehaviourKeys.clear();
  state.recentBehaviourPayloads.clear();
}

function behaviourEventKey(event) {
  if (!event || !event.createdDate || !event.payload) {
    return null;
  }
  return `${event.createdDate}|${event.payload}`;
}

function recentBehaviourPayloadSeen(payload) {
  pruneRecentBehaviourPayloads();
  const lastSeenAt = state.recentBehaviourPayloads.get(payload);
  return typeof lastSeenAt === "number" && Date.now() - lastSeenAt < BEHAVIOUR_DUPLICATE_WINDOW_MS;
}

function rememberRecentBehaviourPayload(payload) {
  pruneRecentBehaviourPayloads();
  state.recentBehaviourPayloads.set(payload, Date.now());
}

function pruneRecentBehaviourPayloads() {
  const now = Date.now();
  for (const [payload, seenAt] of state.recentBehaviourPayloads.entries()) {
    if (now - seenAt > BEHAVIOUR_DUPLICATE_WINDOW_MS) {
      state.recentBehaviourPayloads.delete(payload);
    }
  }
}

function getEventSpeech(event) {
  if (!event || !event.payload) {
    return null;
  }
  try {
    const plan = JSON.parse(event.payload);
    if (plan && typeof plan.speech === "string" && plan.speech.trim()) {
      return plan.speech.trim();
    }
  } catch (_) {
    return null;
  }
  return null;
}

function renderHistoricalUserUtterance(event) {
  const text = eventPayloadText(event && event.payload);
  if (text) {
    appendMessage("user", text);
  }
}

function eventPayloadText(payload) {
  if (typeof payload === "string") {
    return payload.trim();
  }
  if (payload === null || typeof payload === "undefined") {
    return "";
  }
  if (typeof payload === "object") {
    return JSON.stringify(payload);
  }
  return String(payload).trim();
}

function renderBehaviourPlan(plan) {
  if (!plan || typeof plan !== "object") {
    return;
  }
  if (typeof plan.speech === "string" && plan.speech.trim()) {
    setText("speech_preview", plan.speech.trim());
  }
  renderNonVerbal(plan.nonVerbal);
  renderMotion(plan.motion);
  renderDisplay(plan.display);
  appendLog("behaviour", `received ${behaviourSummary(plan)}`);
}

function renderNonVerbal(nonVerbal) {
  if (!nonVerbal || typeof nonVerbal !== "object") {
    return;
  }
  setText("gesture_value", asText(nonVerbal.gesture));
  const face = nonVerbal.facialExpression;
  if (typeof face === "string") {
    setText("face_value", face);
  } else if (face && typeof face === "object") {
    setText("face_value", asText(face.type || face.expression));
  }
  const gaze = nonVerbal.gaze;
  if (typeof gaze === "string") {
    setText("gaze_value", gaze);
  } else if (gaze && typeof gaze === "object") {
    setText("gaze_value", asText(gaze.direction || gaze.focus));
  }
  const motion = nonVerbal.motion;
  if (motion && typeof motion === "object") {
    setText("motion_value", `energy ${round(Number(motion.energy || 0), 2)}`);
  }
}

function renderMotion(motion) {
  if (!motion || typeof motion !== "object") {
    return;
  }
  const sign = normalizeSign(motion.handSign);
  if (sign) {
    renderAgentSign(sign);
    resetCameraEmissionGate();
  }
  if (motion.effector) {
    setText("motion_value", asText(motion.effector));
  } else if (sign) {
    setText("motion_value", SIGNS[sign].label);
  }
}

function renderDisplay(display) {
  if (!display || typeof display !== "object") {
    return;
  }
  setText("display_value", JSON.stringify(display, null, 2));
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

async function toggleRealtime() {
  if (!state.realtimeListening) {
    await startRealtime();
  } else {
    await stopRealtime();
  }
}

async function startRealtime() {
  if (!state.agentId) {
    return;
  }
  setRealtimeState(true);
  appendLog("realtime", "starting.");
  resetRealtimeTranscriptGate();
  try {
    await setupRealtimeConnection();
    await waitForDataChannelOpen();
    updatePushToTalkUi();
  } catch (error) {
    appendLog("realtime", "start failed: " + error.message);
    await stopRealtime();
  }
}

async function stopRealtime() {
  setRealtimeState(false);
  if (realtime.dataChannel) {
    realtime.dataChannel.close();
    realtime.dataChannel = null;
  }
  if (realtime.peerConnection) {
    realtime.peerConnection.close();
    realtime.peerConnection = null;
  }
  if (realtime.micStream) {
    realtime.micStream.getTracks().forEach((track) => track.stop());
    realtime.micStream = null;
  }
  if (realtime.callId) {
    closeRealtimeCall(realtime.callId);
    realtime.callId = null;
  }
  const audio = document.getElementById("assistant_audio");
  audio.pause();
  audio.removeAttribute("src");
  audio.srcObject = null;
  audio.load();
  realtime.pushToTalkActive = false;
  realtime.responseActive = false;
  resetRealtimeTranscriptGate();
  disableSpaceKeyPushToTalk();
  updatePushToTalkUi();
  appendLog("realtime", "stopped.");
}

async function setupRealtimeConnection() {
  realtime.peerConnection = new RTCPeerConnection();
  realtime.peerConnection.ontrack = (event) => {
    document.getElementById("assistant_audio").srcObject = event.streams[0];
  };
  realtime.dataChannel = realtime.peerConnection.createDataChannel("oai-events");
  realtime.dataChannel.addEventListener("message", handleRealtimeEvent);
  realtime.micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  realtime.micStream.getTracks().forEach((track) => realtime.peerConnection.addTrack(track, realtime.micStream));

  const offer = await realtime.peerConnection.createOffer();
  await realtime.peerConnection.setLocalDescription(offer);
  const call = await createRealtimeCall(offer.sdp);
  realtime.callId = call.callId || null;
  await realtime.peerConnection.setRemoteDescription({ type: "answer", sdp: call.sdp });
  appendLog("realtime", "WebRTC session established.");
}

async function createRealtimeCall(offerSdp) {
  const settings = currentRealtimeSettings();
  const params = new URLSearchParams();
  if (settings.voice) {
    params.set("voice", settings.voice);
  }
  params.set("turnDetection", settings.turnDetection || "server_vad");
  params.set("generateComplement", String(document.getElementById("generate_side_behaviour").checked));
  const response = await scopedFetch(demoAgentPath(`/realtime/call?${params.toString()}`), {
    method: "POST",
    headers: {
      "Content-Type": "application/sdp",
    },
    body: offerSdp,
  });
  if (!response.ok) {
    throw new Error("realtime call creation failed.");
  }
  return await response.json();
}

function closeRealtimeCall(callId) {
  fetch(`/realtime/calls/${encodeURIComponent(callId)}`, { method: "DELETE" }).catch(() => {
  });
}

function waitForDataChannelOpen(timeoutMs = 5000) {
  if (realtime.dataChannel && realtime.dataChannel.readyState === "open") {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("data channel not ready.")), timeoutMs);
    const handleOpen = () => {
      clearTimeout(timeout);
      realtime.dataChannel.removeEventListener("open", handleOpen);
      resolve();
    };
    realtime.dataChannel.addEventListener("open", handleOpen);
  });
}

function handleRealtimeEvent(event) {
  let data = null;
  try {
    data = JSON.parse(event.data);
  } catch (_) {
    appendLog("realtime", "non-json event.");
    return;
  }
  if (data.type === "input_audio_buffer.committed") {
    rememberRealtimeInputItem(data.item_id);
  } else if (data.type === "input_audio_buffer.cleared") {
    clearQueuedRealtimeTranscriptCandidates();
  } else if (data.type === "conversation.item.input_audio_transcription.completed") {
    queueRealtimeTranscriptCandidate(data);
  } else if (data.type === "response.created") {
    realtime.responseActive = true;
    realtime.assistantAudioSeen = false;
    realtime.assistantTranscriptBuffer = "";
  } else if (data.type === "response.output_audio_transcript.delta" || data.type === "response.output_text.delta") {
    realtime.assistantAudioSeen = true;
    realtime.assistantTranscriptBuffer += data.delta || "";
    setText("speech_preview", realtime.assistantTranscriptBuffer);
  } else if (data.type === "response.output_audio_transcript.done" || data.type === "response.output_text.done") {
    const transcript = realtime.assistantTranscriptBuffer.trim();
    if (transcript) {
      setText("speech_preview", transcript);
    }
    realtime.assistantTranscriptBuffer = "";
  } else if (data.type === "response.done") {
    realtime.assistantAudioSeen = false;
    realtime.responseActive = false;
  } else if (data.type === "response.cancelled") {
    realtime.assistantAudioSeen = false;
    realtime.responseActive = false;
  }
}

function rememberRealtimeInputItem(itemId) {
  if (!itemId || realtime.processedInputItemIds.has(itemId)) {
    return;
  }
  realtime.pendingInputItemIds.add(itemId);
}

function queueRealtimeTranscriptCandidate(data) {
  const transcript = data.transcript || "";
  if (!transcript.trim()) {
    markRealtimeTranscriptItemsProcessed([{ itemId: data.item_id || "" }]);
    return;
  }
  realtime.transcriptCandidates.push({
    itemId: data.item_id || "",
    eventId: data.event_id || "",
    transcript: transcript.trim(),
  });
  if (!realtime.transcriptFlushTimer) {
    realtime.transcriptFlushTimer = setTimeout(flushRealtimeTranscriptCandidates, TRANSCRIPT_BATCH_DELAY_MS);
  }
}

function flushRealtimeTranscriptCandidates() {
  const candidates = realtime.transcriptCandidates.slice();
  realtime.transcriptCandidates = [];
  realtime.transcriptFlushTimer = null;
  const selected = selectRealtimeTranscriptCandidate(candidates);
  markRealtimeTranscriptItemsProcessed(candidates);
  if (!selected) {
    appendLog("realtime", "ignored noisy or duplicate user transcript.");
    return;
  }
  appendMessage("user", selected.transcript);
  appendLog("realtime", "user transcript completed.");
}

function selectRealtimeTranscriptCandidate(candidates) {
  let selected = null;
  for (const candidate of candidates) {
    if (!candidate.transcript.trim() || realtimeTranscriptItemAlreadyProcessed(candidate) ||
      !realtimeTranscriptItemMatchesPendingCommit(candidate) || isLikelyAsrHallucination(candidate.transcript)) {
      continue;
    }
    selected = candidate;
  }
  return selected;
}

function realtimeTranscriptItemAlreadyProcessed(candidate) {
  return !!candidate.itemId && realtime.processedInputItemIds.has(candidate.itemId);
}

function realtimeTranscriptItemMatchesPendingCommit(candidate) {
  return !candidate.itemId || realtime.pendingInputItemIds.size === 0 ||
    realtime.pendingInputItemIds.has(candidate.itemId);
}

function markRealtimeTranscriptItemsProcessed(candidates) {
  candidates.forEach((candidate) => {
    if (!candidate.itemId) {
      return;
    }
    realtime.processedInputItemIds.add(candidate.itemId);
    realtime.pendingInputItemIds.delete(candidate.itemId);
  });
}

function clearQueuedRealtimeTranscriptCandidates() {
  realtime.transcriptCandidates = [];
  if (realtime.transcriptFlushTimer) {
    clearTimeout(realtime.transcriptFlushTimer);
    realtime.transcriptFlushTimer = null;
  }
  realtime.pendingInputItemIds.clear();
}

function resetRealtimeTranscriptGate() {
  clearQueuedRealtimeTranscriptCandidates();
  realtime.processedInputItemIds = new Set();
}

function isLikelyAsrHallucination(transcript) {
  const normalized = normalizeTranscriptForGate(transcript);
  return normalized === "untertitel der amara org community" ||
    normalized === "subtitles by the amara org community" ||
    normalized === "captions by the amara org community";
}

function normalizeTranscriptForGate(transcript) {
  return String(transcript || "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function applySessionSettings() {
  if (!state.realtimeListening) {
    return;
  }
  appendLog("realtime", "restart realtime to apply voice or mode changes.");
}

function currentRealtimeSettings() {
  return {
    voice: document.getElementById("voice_select").value,
    turnDetection: document.getElementById("turn_detection_select").value || "server_vad",
  };
}

function updatePushToTalkUi() {
  const settings = currentRealtimeSettings();
  const button = document.getElementById("push_to_talk");
  const isManual = settings.turnDetection === "none";
  button.classList.toggle("d-none", !isManual);
  button.disabled = !state.realtimeListening || !isManual;
  if (isManual && state.realtimeListening) {
    enableSpaceKeyPushToTalk();
    setMicEnabled(false);
  } else {
    disableSpaceKeyPushToTalk();
    if (state.realtimeListening) {
      setMicEnabled(true);
    }
  }
}

function startPushToTalk(event) {
  if (event) {
    event.preventDefault();
  }
  if (!state.realtimeListening || currentRealtimeSettings().turnDetection !== "none" || realtime.pushToTalkActive) {
    return;
  }
  realtime.pushToTalkActive = true;
  document.getElementById("push_to_talk").classList.add("is-pressed");
  prepareManualTurn();
  setMicEnabled(true);
}

function stopPushToTalk(event) {
  if (event) {
    event.preventDefault();
  }
  if (!realtime.pushToTalkActive) {
    return;
  }
  realtime.pushToTalkActive = false;
  document.getElementById("push_to_talk").classList.remove("is-pressed");
  setMicEnabled(false);
  commitManualTurn();
}

function enableSpaceKeyPushToTalk() {
  if (realtime.spaceKeyBindingActive) {
    return;
  }
  window.addEventListener("keydown", handleSpaceKeyDown);
  window.addEventListener("keyup", handleSpaceKeyUp);
  realtime.spaceKeyBindingActive = true;
}

function disableSpaceKeyPushToTalk() {
  if (!realtime.spaceKeyBindingActive) {
    return;
  }
  window.removeEventListener("keydown", handleSpaceKeyDown);
  window.removeEventListener("keyup", handleSpaceKeyUp);
  realtime.spaceKeyBindingActive = false;
}

function handleSpaceKeyDown(event) {
  if (event.code !== "Space" || shouldIgnoreSpace(event)) {
    return;
  }
  event.preventDefault();
  if (!event.repeat) {
    startPushToTalk();
  }
}

function handleSpaceKeyUp(event) {
  if (event.code !== "Space" || shouldIgnoreSpace(event)) {
    return;
  }
  event.preventDefault();
  stopPushToTalk();
}

function shouldIgnoreSpace(event) {
  const target = event.target;
  if (!target) {
    return false;
  }
  if (target.isContentEditable) {
    return true;
  }
  const tagName = target.tagName ? target.tagName.toLowerCase() : "";
  return tagName === "input" || tagName === "textarea" || tagName === "select";
}

function commitManualTurn() {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    return;
  }
  realtime.dataChannel.send(JSON.stringify({ type: "input_audio_buffer.commit" }));
}

function prepareManualTurn() {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    return;
  }
  if (realtime.responseActive) {
    sendRealtimeEvent({ type: "response.cancel" });
    sendRealtimeEvent({ type: "output_audio_buffer.clear" });
    realtime.responseActive = false;
  }
  sendRealtimeEvent({ type: "input_audio_buffer.clear" });
}

function sendRealtimeEvent(payload) {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    return;
  }
  realtime.dataChannel.send(JSON.stringify(payload));
}

function setMicEnabled(enabled) {
  if (!realtime.micStream) {
    return;
  }
  realtime.micStream.getAudioTracks().forEach((track) => {
    track.enabled = enabled;
  });
}

async function startCamera() {
  if (state.cameraRunning) {
    return;
  }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    setCameraStatus("Camera Error", "error");
    appendLog("camera", "camera API unavailable.");
    return;
  }
  await ensureEnabledModels();
  try {
    camera.stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user", width: { ideal: 960 }, height: { ideal: 720 } },
      audio: false,
    });
    camera.video.srcObject = camera.stream;
    await camera.video.play();
    state.cameraRunning = true;
    document.getElementById("start_camera").disabled = true;
    document.getElementById("stop_camera").disabled = false;
    setCameraStatus("Camera Live", "live");
    runCameraLoop();
  } catch (error) {
    setCameraStatus("Camera Error", "error");
    appendLog("camera", "start failed: " + error.message);
    stopCamera({ silent: true });
  }
}

function stopCamera(options = {}) {
  if (camera.loopTimer) {
    clearTimeout(camera.loopTimer);
    camera.loopTimer = null;
  }
  if (camera.stream) {
    camera.stream.getTracks().forEach((track) => track.stop());
    camera.stream = null;
  }
  camera.video.srcObject = null;
  state.cameraRunning = false;
  clearOverlay();
  document.getElementById("start_camera").disabled = false;
  document.getElementById("stop_camera").disabled = true;
  setCameraStatus("Camera Idle", "idle");
  if (!options.silent) {
    appendLog("camera", "stopped.");
  }
}

async function ensureEnabledModels() {
  const loaders = [];
  if (isSensorModeEnabled("emotion") && !camera.faceModelsReady) {
    loaders.push(loadFaceModels());
  }
  if (isSensorModeEnabled("social") && !camera.socialDetectorReady) {
    loaders.push(loadSocialDetector());
  }
  if (isSensorModeEnabled("hand") && !camera.handDetectorReady) {
    loaders.push(loadHandRecognizer());
  }
  if (loaders.length > 0) {
    setCameraStatus("Loading Models", "idle");
    const results = await Promise.allSettled(loaders);
    results
      .filter((result) => result.status === "rejected")
      .forEach((result) => appendLog("camera", "model load failed: " + errorMessage(result.reason)));
    setCameraStatus(state.cameraRunning ? "Camera Live" : "Camera Idle", state.cameraRunning ? "live" : "idle");
  }
}

async function handleSensorModeChange() {
  resetDisabledSensorState();
  await ensureEnabledModels();
}

function isSensorModeEnabled(mode) {
  const ids = {
    emotion: "sensor_emotion_enabled",
    social: "sensor_social_enabled",
    hand: "sensor_hand_enabled",
  };
  const input = document.getElementById(ids[mode]);
  return !!(input && input.checked);
}

async function loadFaceModels() {
  if (!window.faceapi) {
    appendLog("camera", "face-api unavailable.");
    return;
  }
  await Promise.all([
    window.faceapi.nets.tinyFaceDetector.loadFromUri(FACE_MODEL_URI),
    window.faceapi.nets.faceExpressionNet.loadFromUri(FACE_MODEL_URI),
  ]);
  camera.faceModelsReady = true;
  appendLog("camera", "face models ready.");
}

async function loadSocialDetector() {
  if (!window.cocoSsd) {
    appendLog("camera", "coco-ssd unavailable.");
    return;
  }
  camera.socialDetector = await window.cocoSsd.load({ base: "lite_mobilenet_v2" });
  camera.socialDetectorReady = true;
  appendLog("camera", "person detector ready.");
}

async function loadHandRecognizer() {
  const visionTasks = await import(MEDIAPIPE_TASKS_URL);
  const vision = await visionTasks.FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_ROOT);
  camera.handRecognizer = await visionTasks.GestureRecognizer.createFromOptions(vision, {
    baseOptions: { modelAssetPath: GESTURE_MODEL_URL },
    runningMode: "VIDEO",
    numHands: 1,
  });
  camera.handDetectorReady = true;
  appendLog("camera", "gesture recognizer ready.");
}

async function runCameraLoop() {
  if (!state.cameraRunning) {
    return;
  }
  try {
    clearOverlay();
    if (isSensorModeEnabled("social") && camera.socialDetectorReady) {
      await detectSocial();
    }
    if (isSensorModeEnabled("emotion") && camera.faceModelsReady) {
      await detectEmotion();
    }
    if (isSensorModeEnabled("hand") && camera.handDetectorReady) {
      await detectHandSign();
    }
  } catch (error) {
    appendLog("camera", "detection failed: " + error.message);
  }
  camera.loopTimer = setTimeout(runCameraLoop, CAMERA_PERIOD_MS);
}

async function detectEmotion() {
  const detection = await window.faceapi
    .detectSingleFace(camera.video, new window.faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.45 }))
    .withFaceExpressions();
  if (!detection) {
    setText("emotion_value", "-");
    return;
  }
  drawFaceBox(detection.detection.box);
  const emotion = deriveEmotion(detection.expressions);
  renderEmotionMetrics(emotion);
  await maybeEmitEmotion(emotion, detection.detection.score);
}

async function detectSocial() {
  const rawDetections = await camera.socialDetector.detect(camera.video);
  const people = rawDetections
    .filter((d) => d && d.class === "person" && Number(d.score || 0) >= PERSON_SCORE_THRESHOLD)
    .map(normalizePersonDetection);
  const tracked = updateTracks(people);
  const social = deriveSocialSituation(tracked);
  drawSocialOverlay(tracked, social);
  renderSocialMetrics(social);
  await maybeEmitSocial(social, tracked);
}

async function detectHandSign() {
  if (camera.video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA ||
    camera.video.currentTime === camera.lastGestureVideoTime) {
    return;
  }
  const result = camera.handRecognizer.recognizeForVideo(camera.video, performance.now());
  const candidate = selectCameraGesture(result);
  updateCameraStability(candidate);
  drawGestureOverlay(candidate);
  if (candidate) {
    setText("hand_sign_value", `${SIGNS[candidate.sign].label} ${candidate.confidence.toFixed(2)}`);
  } else {
    setText("hand_sign_value", "-");
  }
  await maybeEmitCameraSign(candidate);
  camera.lastGestureVideoTime = camera.video.currentTime;
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
  return {
    emotion: label,
    confidence,
    valence: clamp(happy - sad - 0.7 * angry - 0.6 * fearful, -1, 1),
    arousal: clamp(0.2 * neutral + 0.7 * surprised + 0.6 * angry + 0.55 * fearful + 0.35 * happy, 0, 1),
    expressions,
    facePresent: true,
  };
}

async function maybeEmitEmotion(emotion, faceScore) {
  if (!document.getElementById("sensor_emit_enabled").checked || !emotion) {
    return;
  }
  const threshold = Number(document.getElementById("face_confidence_threshold").value || 0.55);
  if (emotion.confidence < threshold || !passesSensorEmitInterval("emotion")) {
    return;
  }
  if (camera.lastEmotion && camera.lastEmotion.emotion === emotion.emotion &&
    Math.abs(camera.lastEmotion.valence - emotion.valence) < 0.08 &&
    Math.abs(camera.lastEmotion.arousal - emotion.arousal) < 0.08) {
    return;
  }
  const ok = await acknowledgeEvent({
    type: "obs.emotion.face",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(emotionPayload(emotion, "visual.facial", faceScore)),
  }, { renderResponse: true });
  if (ok) {
    markSensorEmitted("emotion");
    camera.lastEmotion = emotion;
  }
}

async function submitEmotionSample(label) {
  const spec = MANUAL_EMOTIONS[label];
  if (!spec) {
    appendLog("emotion", "unknown manual emotion.");
    return false;
  }
  const emotion = {
    emotion: label,
    confidence: 1,
    valence: spec.valence,
    arousal: spec.arousal,
    expressions: manualEmotionExpressions(label),
    facePresent: true,
  };
  renderEmotionMetrics(emotion);
  const data = await acknowledgeEvent({
    type: "obs.emotion.face",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(emotionPayload(emotion, "visual.facial.manual", 1, { detectionMode: "manual" })),
  }, { renderResponse: true });
  if (data) {
    camera.lastEmotion = emotion;
    markSensorEmitted("emotion");
  }
  return !!data;
}

function renderEmotionMetrics(emotion) {
  if (!emotion) {
    setText("emotion_value", "-");
    return;
  }
  setText("emotion_value", `${emotion.emotion} ${Number(emotion.confidence || 0).toFixed(2)}`);
}

function emotionPayload(emotion, source, faceScore, extra = {}) {
  return {
    source,
    emotion: emotion.emotion,
    confidence: round(emotion.confidence, 3),
    valence: round(emotion.valence, 3),
    arousal: round(emotion.arousal, 3),
    faceDetectionConfidence: round(Number(faceScore || 0), 3),
    facePresent: true,
    expressions: compressExpressions(emotion.expressions),
    ts: new Date().toISOString(),
    ...extra,
  };
}

function manualEmotionExpressions(label) {
  return Object.fromEntries(
    ["neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised"]
      .map((emotion) => [emotion, emotion === label ? 1 : 0])
  );
}

function normalizePersonDetection(detection) {
  const bbox = detection.bbox || [0, 0, 0, 0];
  const x = Number(bbox[0] || 0);
  const y = Number(bbox[1] || 0);
  const w = Number(bbox[2] || 0);
  const h = Number(bbox[3] || 0);
  return { x, y, w, h, score: Number(detection.score || 0), cx: x + w / 2, cy: y + h / 2 };
}

function updateTracks(detections) {
  const now = Date.now();
  const assigned = new Set();
  const tracked = [];
  const frameDiag = Math.max(1, Math.hypot(camera.video.videoWidth || 1, camera.video.videoHeight || 1));
  for (const detection of detections) {
    const best = findBestTrack(detection, frameDiag, assigned);
    if (best) {
      const track = camera.tracks.get(best.id);
      track.cx = detection.cx;
      track.cy = detection.cy;
      track.box = [detection.x, detection.y, detection.w, detection.h];
      track.score = detection.score;
      track.lastSeenAt = now;
      assigned.add(track.id);
      tracked.push(trackToView(track));
    } else {
      const id = camera.nextTrackId++;
      const track = {
        id,
        cx: detection.cx,
        cy: detection.cy,
        box: [detection.x, detection.y, detection.w, detection.h],
        score: detection.score,
        firstSeenAt: now,
        lastSeenAt: now,
      };
      camera.tracks.set(id, track);
      assigned.add(id);
      tracked.push(trackToView(track));
    }
  }
  for (const [id, track] of camera.tracks.entries()) {
    if (now - track.lastSeenAt > TRACK_TTL_MS) {
      camera.tracks.delete(id);
    }
  }
  return tracked;
}

function findBestTrack(detection, frameDiag, assigned) {
  let best = null;
  for (const track of camera.tracks.values()) {
    if (assigned.has(track.id)) {
      continue;
    }
    const distNorm = Math.hypot(detection.cx - track.cx, detection.cy - track.cy) / frameDiag;
    if (distNorm <= TRACK_MAX_DISTANCE_NORM && (!best || distNorm < best.distNorm)) {
      best = { id: track.id, distNorm };
    }
  }
  return best;
}

function trackToView(track) {
  return { id: track.id, cx: track.cx, cy: track.cy, box: track.box, score: track.score };
}

function deriveSocialSituation(tracked) {
  const people = tracked || [];
  const n = people.length;
  if (n === 0) {
    return { humanCount: 0, groupCount: 0, singletonCount: 0, largestGroupSize: 0, groups: [] };
  }
  const threshold = Number(document.getElementById("group_distance_threshold").value || 0.16);
  const frameDiag = Math.max(1, Math.hypot(camera.video.videoWidth || 1, camera.video.videoHeight || 1));
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
  const groups = Array.from(clusters.values()).map((members) => ({ members }))
    .sort((a, b) => b.members.length - a.members.length);
  return {
    humanCount: n,
    groupCount: groups.filter((g) => g.members.length >= 2).length,
    singletonCount: groups.filter((g) => g.members.length === 1).length,
    largestGroupSize: groups.length ? groups[0].members.length : 0,
    groups,
  };
}

function renderSocialMetrics(social) {
  setText("human_count", String((social && social.humanCount) || 0));
  setText("group_count", String((social && social.groupCount) || 0));
}

async function maybeEmitSocial(social, tracked) {
  if (!document.getElementById("sensor_emit_enabled").checked || !social || !passesSensorEmitInterval("social")) {
    return;
  }
  await submitSocialPayloads(social, tracked, "visual.social");
}

async function submitSocialSample(kind) {
  const samples = {
    alone: { humanCount: 0, groupCount: 0, singletonCount: 0, largestGroupSize: 0, groups: [] },
    single: { humanCount: 1, groupCount: 0, singletonCount: 1, largestGroupSize: 1, groups: [{ members: [1] }] },
    pair: { humanCount: 2, groupCount: 1, singletonCount: 0, largestGroupSize: 2, groups: [{ members: [1, 2] }] },
    crowd: { humanCount: 3, groupCount: 1, singletonCount: 0, largestGroupSize: 3, groups: [{ members: [1, 2, 3] }] },
  };
  const social = samples[kind];
  if (!social) {
    return;
  }
  renderSocialMetrics(social);
  await submitSocialPayloads(social, social.groups.flatMap((g) => g.members).map((id) => ({ id, score: 1 })), "visual.social.manual");
}

async function submitSocialPayloads(social, tracked, source) {
  const presencePayload = {
    source,
    humanCount: social.humanCount,
    trackedCount: tracked.length,
    trackedIds: tracked.map((p) => p.id),
    avgDetectionConfidence: round(average(tracked.map((p) => p.score || 1)), 3),
    ts: new Date().toISOString(),
  };
  const groupingPayload = {
    source,
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
  if (presenceSignature === camera.lastPresenceSignature && groupingSignature === camera.lastGroupingSignature) {
    appendLog("social", "duplicate social sample skipped.");
    return;
  }
  await acknowledgeEvent({
    type: "obs.human.presence",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(presencePayload),
  }, { renderResponse: false });
  await acknowledgeEvent({
    type: "obs.social.grouping",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(groupingPayload),
  }, { renderResponse: true });
  markSensorEmitted("social");
  camera.lastPresenceSignature = presenceSignature;
  camera.lastGroupingSignature = groupingSignature;
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
      best = {
        sign,
        confidence,
        cannedGesture,
        landmarks: Array.isArray(result.landmarks) ? result.landmarks[i] : null,
      };
    }
  }
  return best;
}

function updateCameraStability(candidate) {
  const key = candidate ? `${candidate.sign}|${candidate.cannedGesture}` : null;
  if (!key) {
    camera.stableGestureKey = null;
    camera.stableGestureCount = 0;
    return;
  }
  if (key === camera.stableGestureKey) {
    camera.stableGestureCount += 1;
  } else {
    camera.stableGestureKey = key;
    camera.stableGestureCount = 1;
  }
}

async function maybeEmitCameraSign(candidate) {
  if (!candidate || !document.getElementById("sensor_emit_enabled").checked) {
    return;
  }
  if (candidate.confidence < Number(document.getElementById("hand_confidence_threshold").value || 0.65)) {
    return;
  }
  if (camera.stableGestureCount < REQUIRED_STABLE_GESTURE_FRAMES) {
    return;
  }
  const emitKey = `${candidate.sign}|${candidate.cannedGesture}`;
  if (emitKey === camera.lastCameraEmitKey && Date.now() - camera.lastCameraEmitAt < 2500) {
    return;
  }
  const ok = await submitHandSign(candidate.sign, {
    source: "rps.web.camera",
    detectionMode: "client_camera",
    confidence: round(candidate.confidence, 3),
    cannedGesture: candidate.cannedGesture,
    stabilityFrames: camera.stableGestureCount,
  });
  if (ok) {
    camera.lastCameraEmitKey = emitKey;
    markSensorEmitted("hand");
  }
}

async function submitHandSign(sign, options = {}) {
  const normalized = normalizeSign(sign);
  if (!normalized) {
    appendLog("rps", "unknown hand sign.");
    return false;
  }
  renderUserSign(normalized);
  const payload = {
    source: options.source || "rps.web",
    hand: options.hand || "unknown",
    sign: normalized,
    confidence: typeof options.confidence === "number" ? options.confidence : 1.0,
    detectionMode: options.detectionMode || "manual",
    ts: new Date().toISOString(),
  };
  if (options.cannedGesture) {
    payload.cannedGesture = options.cannedGesture;
  }
  if (options.stabilityFrames) {
    payload.stabilityFrames = options.stabilityFrames;
  }
  const data = await acknowledgeEvent({
    type: "obs.hand.sign",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(payload),
  }, { renderResponse: true });
  return !!data;
}

function drawFaceBox(box) {
  if (!box) {
    return;
  }
  const scale = overlayScale();
  const displayBox = mirroredOverlayBox(box.x, box.y, box.width, box.height, scale);
  camera.ctx.lineWidth = 3;
  camera.ctx.strokeStyle = "#ff7a00";
  camera.ctx.strokeRect(displayBox.x, displayBox.y, displayBox.width, displayBox.height);
}

function drawSocialOverlay(tracked, social) {
  const scale = overlayScale();
  const idToGroup = new Map();
  const groups = social && social.groups ? social.groups : [];
  for (let i = 0; i < groups.length; i++) {
    for (const id of groups[i].members) {
      idToGroup.set(id, i);
    }
  }
  for (const person of tracked || []) {
    const [x, y, w, h] = person.box;
    const displayBox = mirroredOverlayBox(x, y, w, h, scale);
    const gid = idToGroup.has(person.id) ? idToGroup.get(person.id) : -1;
    const hue = gid >= 0 ? (gid * 57) % 360 : 180;
    camera.ctx.lineWidth = 2;
    camera.ctx.strokeStyle = `hsl(${hue}, 75%, 42%)`;
    camera.ctx.strokeRect(displayBox.x, displayBox.y, displayBox.width, displayBox.height);
  }
}

function drawGestureOverlay(candidate) {
  const landmarks = candidate && candidate.landmarks;
  if (!Array.isArray(landmarks)) {
    return;
  }
  const width = camera.canvas.width;
  const height = camera.canvas.height;
  camera.ctx.lineWidth = 2;
  camera.ctx.strokeStyle = "#1d4ed8";
  camera.ctx.fillStyle = "#1d4ed8";
  for (const [a, b] of HAND_CONNECTIONS) {
    const pa = landmarks[a];
    const pb = landmarks[b];
    if (!pa || !pb) {
      continue;
    }
    camera.ctx.beginPath();
    camera.ctx.moveTo((1 - pa.x) * width, pa.y * height);
    camera.ctx.lineTo((1 - pb.x) * width, pb.y * height);
    camera.ctx.stroke();
  }
  for (const p of landmarks) {
    camera.ctx.beginPath();
    camera.ctx.arc((1 - p.x) * width, p.y * height, 3, 0, Math.PI * 2);
    camera.ctx.fill();
  }
}

function overlayScale() {
  const rect = camera.canvas.getBoundingClientRect();
  if (camera.canvas.width !== Math.floor(rect.width) || camera.canvas.height !== Math.floor(rect.height)) {
    camera.canvas.width = rect.width;
    camera.canvas.height = rect.height;
  }
  return {
    scaleX: camera.canvas.width / Math.max(1, camera.video.videoWidth || camera.canvas.width),
    scaleY: camera.canvas.height / Math.max(1, camera.video.videoHeight || camera.canvas.height),
  };
}

function mirroredOverlayBox(x, y, width, height, scale) {
  const boxWidth = width * scale.scaleX;
  return {
    x: camera.canvas.width - ((x + width) * scale.scaleX),
    y: y * scale.scaleY,
    width: boxWidth,
    height: height * scale.scaleY,
  };
}

function clearOverlay() {
  if (!camera.ctx) {
    return;
  }
  const rect = camera.canvas.getBoundingClientRect();
  camera.canvas.width = rect.width;
  camera.canvas.height = rect.height;
  camera.ctx.clearRect(0, 0, camera.canvas.width, camera.canvas.height);
}

function passesSensorEmitInterval(mode) {
  const minInterval = Number(document.getElementById("emit_interval_ms").value || 2500);
  const lastEmitAtByMode = {
    emotion: camera.lastEmotionEmitAt,
    social: camera.lastSocialEmitAt,
    hand: camera.lastCameraEmitAt,
  };
  return Date.now() - (lastEmitAtByMode[mode] || 0) >= minInterval;
}

function markSensorEmitted(mode) {
  const now = Date.now();
  if (mode === "emotion") {
    camera.lastEmotionEmitAt = now;
  } else if (mode === "social") {
    camera.lastSocialEmitAt = now;
  } else if (mode === "hand") {
    camera.lastCameraEmitAt = now;
  }
}

function resetCameraEmissionGate() {
  camera.lastCameraEmitKey = null;
  camera.lastCameraEmitAt = 0;
}

function resetDisabledSensorState() {
  if (!isSensorModeEnabled("emotion")) {
    setText("emotion_value", "-");
    camera.lastEmotion = null;
    camera.lastEmotionEmitAt = 0;
  }
  if (!isSensorModeEnabled("social")) {
    setText("human_count", "0");
    setText("group_count", "0");
    camera.tracks.clear();
    camera.lastPresenceSignature = null;
    camera.lastGroupingSignature = null;
    camera.lastSocialEmitAt = 0;
  }
  if (!isSensorModeEnabled("hand")) {
    setText("hand_sign_value", "-");
    camera.stableGestureKey = null;
    camera.stableGestureCount = 0;
    camera.lastCameraEmitKey = null;
    camera.lastCameraEmitAt = 0;
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
  setText(`${prefix}_sign_label`, ui ? `${ui.symbol} ${ui.label}` : "-");
}

function renderLatestEvent(event) {
  if (!event) {
    return;
  }
  if (event.type !== "resp.behaviour_plan") {
    return;
  }
  const payload = event.payload ? shortPayload(event.payload) : "";
  setText("latest_behaviour_event", payload ? `${event.type}: ${payload}` : event.type);
}

function appendMessage(role, text) {
  const list = document.getElementById("messages");
  const row = document.createElement("div");
  row.className = `demo-message ${role}`;
  const bubble = document.createElement("div");
  bubble.className = "demo-bubble";
  bubble.textContent = text;
  row.appendChild(bubble);
  list.appendChild(row);
  list.scrollTop = list.scrollHeight;
}

function appendSystemMessage(text) {
  appendMessage("system", text);
}

function clearMessages() {
  document.getElementById("messages").innerHTML = "";
}

function appendLog(scope, message) {
  state.activityEntries.unshift({
    timestamp: new Date().toLocaleTimeString(),
    scope,
    message,
  });
  if (state.activityEntries.length > ACTIVITY_LOG_LIMIT) {
    state.activityEntries.length = ACTIVITY_LOG_LIMIT;
  }
  renderActivityLog();
}

function clearActivityLog() {
  state.activityEntries = [];
  renderActivityLog();
}

function renderActivityLog() {
  const log = document.getElementById("activity_log");
  if (!log) {
    return;
  }
  log.classList.toggle("is-wrapped", state.activityWrap);
  log.textContent = state.activityEntries.map((entry) => {
    const timestamp = state.activityShowTimestamps ? `[${entry.timestamp}] ` : "";
    return `${timestamp}${entry.scope}: ${entry.message}`;
  }).join("\n");
}

function resetBehaviourPanels() {
  setText("speech_preview", "-");
  setText("gesture_value", "-");
  setText("face_value", "-");
  setText("gaze_value", "-");
  setText("motion_value", "-");
  setText("agent_sign_label", "-");
  setText("user_sign_label", "-");
  setText("round_value", "-");
  setText("winner_value", "-");
  setText("display_value", "-");
  setText("latest_behaviour_event", "-");
}

function setControlsEnabled(enabled) {
  const alwaysEnabled = new Set([
    "access_code_input",
    "submit_access_code",
    "clear_access_code",
    "agent_type_select",
    "create_agent_instance",
    "agent_id_input",
    "agent_select",
    "connect_agent",
    "delete_agent",
    "open_diagnostics",
    "agent_drawer_tab",
    "diagnostics_drawer_tab",
    "clear_activity_log",
    "activity_log_wrap",
    "activity_log_timestamps",
    "text_interaction_tab",
    "speech_interaction_tab",
  ]);
  document.querySelectorAll("button, textarea, select, input").forEach((el) => {
    if (alwaysEnabled.has(el.id) || el.classList.contains("btn-close") ||
      el.dataset.bsDismiss === "offcanvas" || el.dataset.bsToggle === "collapse") {
      return;
    }
    el.disabled = !enabled;
  });
  document.getElementById("start_camera").disabled = !enabled || state.cameraRunning;
  document.getElementById("stop_camera").disabled = !enabled || !state.cameraRunning;
  updateAgentTypeControls();
  updateAgentSelectionControls();
  updatePushToTalkUi();
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

function setBehaviourStatus(text, mode) {
  const el = document.getElementById("behaviour_status");
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
}

function setRealtimeState(isListening) {
  state.realtimeListening = isListening;
  const button = document.getElementById("toggle_realtime");
  const listen = document.getElementById("listen_status");
  const status = document.getElementById("realtime_status");
  if (isListening) {
    button.innerHTML = '<i class="bi bi-mic-mute-fill me-2"></i>Stop Realtime';
    button.classList.add("is-listening");
    listen.textContent = "Listening";
    listen.className = "status-pill is-listening";
    status.textContent = "Realtime Live";
    status.className = "status-pill is-live";
  } else {
    button.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Realtime';
    button.classList.remove("is-listening");
    listen.textContent = "Idle";
    listen.className = "status-pill is-idle";
    status.textContent = "Realtime Idle";
    status.className = "status-pill is-idle";
  }
}

function setCameraStatus(text, mode) {
  const el = document.getElementById("camera_status");
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
}

function cleanupAll() {
  state.isPageUnloading = true;
  cleanupStreams();
  stopCamera({ silent: true });
  if (state.realtimeListening) {
    stopRealtime();
  }
}

function cleanupStreams() {
  if (state.streamReconnectTimer) {
    clearTimeout(state.streamReconnectTimer);
    state.streamReconnectTimer = null;
  }
  if (state.monitorReconnectTimer) {
    clearTimeout(state.monitorReconnectTimer);
    state.monitorReconnectTimer = null;
  }
  closeBehaviourStream();
  if (state.monitorSource) {
    state.monitorSource.close();
    state.monitorSource = null;
  }
}

function nextReconnectDelayMs(attempt) {
  const base = Math.min(RECONNECT_MAX_MS, RECONNECT_MIN_MS * Math.pow(2, attempt));
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * RECONNECT_JITTER);
  return Math.max(RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function getAgentIdFromLocation() {
  const search = window.location.search;
  if (!search || search.length < 2) {
    return null;
  }
  if (search.includes("=")) {
    const params = new URLSearchParams(search);
    return params.get("agentId") || params.get("agent");
  }
  return search.substring(1);
}

function scopedFetch(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.accessCode) {
    headers.set(ACCESS_CODE_HEADER, state.accessCode);
  }
  return fetch(url, { ...options, headers });
}

function demoAgentPath(path) {
  return `/demo/agents/${encodeURIComponent(state.agentId)}${path || ""}`;
}

function updateAgentTypeControls() {
  const select = document.getElementById("agent_type_select");
  const button = document.getElementById("create_agent_instance");
  if (!select || !button) {
    return;
  }
  button.disabled = !state.accessCode || !select.value;
}

function updateAgentSelectionControls() {
  const deleteButton = document.getElementById("delete_agent");
  const connectButton = document.getElementById("connect_agent");
  const selected = state.selectedAgentId || document.getElementById("agent_id_input").value.trim();
  if (deleteButton) {
    deleteButton.disabled = !state.accessCode || !selected || !isVisibleAgentId(selected);
  }
  if (connectButton) {
    connectButton.disabled = !state.accessCode || (!state.agentId && !selected);
  }
}

function setAgentTypeStatus(message, mode) {
  const status = document.getElementById("agent_type_status");
  if (!status) {
    return;
  }
  status.textContent = message || "";
  status.className = `access-status mb-3${mode ? ` is-${mode}` : ""}`;
}

function mergeAgents(existing, additions) {
  const byId = new Map();
  for (const agent of [...(existing || []), ...(additions || [])]) {
    const id = agentIdOf(agent);
    if (id) {
      byId.set(id, agent);
    }
  }
  return Array.from(byId.values());
}

function isVisibleAgentId(agentId) {
  return !!agentId && state.agents.some((agent) => agentIdOf(agent) === agentId);
}

function agentIdOf(agent) {
  return agent && (agent.id || agent.ID || agent.iD);
}

function agentSortKey(agent) {
  return agent && agent.name ? agent.name : agentIdOf(agent) || "";
}

function agentTypeSortKey(agentType) {
  return agentType && (agentType.displayName || agentType.key) ? (agentType.displayName || agentType.key) : "";
}

function prometheusFacingText(value) {
  if (typeof value !== "string") {
    return value || "";
  }
  const legacyAgentName = String.fromCharCode(103, 105, 103, 105);
  const legacyDomainName = String.fromCharCode(116, 100, 115, 114);
  return value
    .replace(new RegExp(`\\b${legacyAgentName} on Prometheus\\b`, "gi"), "Prometheus")
    .replace(new RegExp(`\\b${legacyAgentName}\\b`, "gi"), "Prometheus")
    .replace(new RegExp(`\\b${legacyDomainName}\\b`, "gi"), "")
    .replace(/\s{2,}/g, " ")
    .trim();
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
    return "Agent";
  }
  if (token === "user") {
    return "User";
  }
  if (token === "draw") {
    return "Draw";
  }
  return "-";
}

function behaviourSummary(plan) {
  const parts = [];
  if (plan.speech) {
    parts.push("speech");
  }
  if (plan.nonVerbal) {
    parts.push("nonVerbal");
  }
  if (plan.motion) {
    parts.push("motion");
  }
  if (plan.display) {
    parts.push("display");
  }
  return parts.join(", ") || "empty";
}

function compressExpressions(expressions) {
  const result = {};
  for (const [key, value] of Object.entries(expressions || {})) {
    result[key] = round(Number(value || 0), 3);
  }
  return result;
}

function shortPayload(payload) {
  if (typeof payload !== "string") {
    return "";
  }
  if (payload.length <= 140) {
    return payload;
  }
  return `${payload.substring(0, 137)}...`;
}

function average(values) {
  if (!values || values.length === 0) {
    return 0;
  }
  return values.reduce((sum, value) => sum + Number(value || 0), 0) / values.length;
}

function errorMessage(error) {
  return error && error.message ? error.message : String(error || "unknown error");
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
    } else if (this.rank[ra] > this.rank[rb]) {
      this.parent[rb] = ra;
    } else {
      this.parent[rb] = ra;
      this.rank[ra] += 1;
    }
  }
}
