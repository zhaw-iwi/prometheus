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
  transcriptionListening: false,
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

const transcription = {
  transcriptionClient: null,
  transcriptionSettingsPanel: null,
  transcriptionAgentId: null,
  transcriptIngress: null,
  inputGated: false,
  manualTurnActive: false,
};

const speechPlayback = {
  coordinator: null,
  agentId: null,
  enqueueChain: Promise.resolve(),
  generation: 0,
};

const speechDevices = {
  outputDeviceId: "",
  devicesLoaded: false,
};

const cameraDevices = {
  deviceId: "",
  devicesLoaded: false,
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
  faceModelsError: "",
  socialDetectorError: "",
  handDetectorError: "",
  socialDetector: null,
  handRecognizer: null,
  tracks: new Map(),
  nextTrackId: 1,
  lastEmotionEmitAt: 0,
  lastSocialEmitAt: 0,
  lastEmotion: null,
  lastPresenceSignature: null,
  lastGroupingSignature: null,
  lastSocialContextSignature: null,
  lastGestureVideoTime: -1,
  stableGestureKey: null,
  stableGestureCount: 0,
  lastCameraEmitKey: null,
  lastCameraEmitAt: 0,
};

const weather = {
  current: null,
  forecast: null,
  locationQuery: "",
};

const columnExpansion = {
  modal: null,
  active: null,
};

const detachedView = {
  panel: null,
};

const controlOwnership = {
  windowId: createWindowId(),
  channel: null,
  owned: new Set(),
  heartbeatTimers: new Map(),
  staleSweepTimer: null,
};

const VALERIAN_COLUMN_KEYS = ["sensing", "interaction", "behaviour"];
const CONTROL_OWNERSHIP_CHANNEL = "prometheus.valerian.controlOwnership";
const CONTROL_OWNER_STORAGE_PREFIX = "prometheus.valerian.owner.";
const CONTROL_OWNER_TTL_MS = 5000;
const CONTROL_OWNER_HEARTBEAT_MS = 1000;
const CONTROL_RESOURCES = ["camera", "microphone"];

const RECONNECT_MIN_MS = 1000;
const RECONNECT_MAX_MS = 30000;
const RECONNECT_JITTER = 0.2;
const BEHAVIOUR_DUPLICATE_WINDOW_MS = 2500;
const ACTIVITY_LOG_LIMIT = 300;
const ACCESS_CODE_STORAGE_KEY = "prometheus.valerian.accessCode";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const SPEECH_OUTPUT_DEVICE_STORAGE_KEY = "prometheus.valerian.speechOutputDevice";
const SPEECH_VOICE_STORAGE_KEY = "prometheus.valerian.speechVoice";
const SPEECH_OUTPUT_SPEED_STORAGE_KEY = "prometheus.valerian.speechOutputSpeed";
const CAMERA_DEVICE_STORAGE_KEY = "prometheus.valerian.cameraDevice";
const THEME_STORAGE_KEY = "prometheus.valerian.theme";
const CAMERA_PERIOD_MS = 350;
const TRACK_TTL_MS = 1500;
const TRACK_MAX_DISTANCE_NORM = 0.14;
const TRACK_STATIONARY_DISTANCE_NORM = 0.008;
const TRACK_MOVING_DISTANCE_NORM = 0.018;
const TRACK_STATIONARY_AREA_DELTA = 0.06;
const TRACK_DEPTH_AREA_DELTA = 0.12;
const ATTENTION_CONFIDENCE_THRESHOLD = 0.62;
const PERSON_SCORE_THRESHOLD = 0.45;
const REQUIRED_STABLE_GESTURE_FRAMES = 3;
const FACE_MODEL_URI = "https://justadudewhohacks.github.io/face-api.js/models";
const MEDIAPIPE_TASKS_VERSION = "0.10.35";
const MEDIAPIPE_TASKS_URL = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}`;
const MEDIAPIPE_WASM_ROOT = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MEDIAPIPE_TASKS_VERSION}/wasm`;
const GESTURE_MODEL_URL = "https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/gesture_recognizer.task";
const OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
const OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

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

const GESTURE_UI = {
  OPEN_QUESTION: { icon: "bi-question-diamond", label: "Open Question", hint: "Inviting response" },
  EXPLAIN: { icon: "bi-hand-index-thumb", label: "Explanatory Sweep", hint: "Supporting explanation" },
  UNCERTAIN: { icon: "bi-question-circle", label: "Uncertainty", hint: "Low certainty" },
  ACKNOWLEDGE: { icon: "bi-check2-circle", label: "Acknowledgement", hint: "Closing acknowledgement" },
  POLITE: { icon: "bi-heart", label: "Polite", hint: "Softening social tone" },
  NONE: { icon: "bi-dash-lg", label: "NONE", hint: "No gesture" },
};

const BEHAVIOUR_CHANNELS = ["speech", "gesture", "face", "gaze", "motion", "display"];

const MANUAL_EMOTIONS = {
  neutral: { valence: 0, arousal: 0.2 },
  happy: { valence: 0.8, arousal: 0.55 },
  sad: { valence: -0.7, arousal: 0.35 },
  angry: { valence: -0.65, arousal: 0.75 },
  fearful: { valence: -0.75, arousal: 0.7 },
  disgusted: { valence: -0.72, arousal: 0.58 },
  surprised: { valence: 0.2, arousal: 0.8 },
};

const EMOTION_EXPRESSION_KEYS = ["neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised"];
const MANUAL_SOCIAL_MOVEMENT_STATES = ["unknown", "stationary", "moving", "approaching", "receding"];
const MANUAL_SOCIAL_ATTENTION_STATES = ["unknown", "attending", "not_attending"];

const PROFILE_VISUAL_OBSERVATIONS = [
  "obs.emotion.face",
  "obs.human.presence",
  "obs.social.grouping",
  "obs.social.context",
  "obs.hand.sign",
];

const PROFILE_WEATHER_OBSERVATIONS = [
  "obs.weather.current",
  "obs.weather.forecast",
];

const PROFILE_SENSOR_OBSERVATIONS = {
  emotion: ["obs.emotion.face"],
  social: ["obs.human.presence", "obs.social.grouping", "obs.social.context"],
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
  applyStoredTheme();
  configureValerianView();
  initControlOwnership();
  wireUi();
  loadStoredSpeechDeviceSelection();
  loadStoredSpeechSettings();
  loadStoredCameraDeviceSelection();
  renderSpeechOutputDeviceSelection([]);
  renderCameraDeviceSelections([]);
  showCockpit(false);
  applyInteractionProfile(null);
  resetStateView();
  resetStorageList();
  refreshAudioDevices({ requestPermission: false, silent: true });
  refreshCameraDevices({ requestPermission: false, silent: true });
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
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    button.addEventListener("click", toggleTheme);
  });
  wireColumnExpansion();
  wireColumnDetach();
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
  document.getElementById("toggle_transcription").addEventListener("click", () => toggleTranscription());
  document.getElementById("stop_speech_playback").addEventListener("click", () => {
    stopSpeechPlayback("operator_stop");
  });
  const transcriptionPush = document.getElementById("transcription_push_to_talk");
  transcriptionPush.addEventListener("pointerdown", beginManualTranscriptionTurn);
  ["pointerup", "pointercancel", "lostpointercapture"].forEach((eventName) => {
    transcriptionPush.addEventListener(eventName, finishManualTranscriptionTurn);
  });
  document.getElementById("diagnostics_drawer").addEventListener("show.bs.offcanvas", showAgentDrawerTab);
  document.getElementById("continuous_speech_tab").addEventListener("shown.bs.tab", () => {
    refreshAudioDevices({ requestPermission: false, silent: true });
  });
  speechOutputSettingControls().forEach((control) => {
    control.addEventListener("change", saveSpeechOutputSettingSelection);
  });
  document.getElementById("speech_output_device_select").addEventListener("change", () => {
    saveSpeechOutputDeviceSelection();
  });
  document.getElementById("refresh_audio_devices").addEventListener("click", () => {
    refreshAudioDevices();
  });
  document.getElementById("camera_device_select").addEventListener("change", () => {
    saveCameraDeviceSelection();
  });
  document.getElementById("refresh_camera_devices").addEventListener("click", () => {
    refreshCameraDevices({ requestPermission: true });
  });
  if (navigator.mediaDevices && typeof navigator.mediaDevices.addEventListener === "function") {
    navigator.mediaDevices.addEventListener("devicechange", () => {
      refreshAudioDevices({ requestPermission: false, silent: true });
      refreshCameraDevices({ requestPermission: false, silent: true });
    });
  }
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
      source: "valerian.hand.manual",
      detectionMode: "manual",
      confidence: 1.0,
    }));
  });
  document.querySelectorAll("[data-social-sample]").forEach((button) => {
    button.addEventListener("click", () => submitSocialSample(button.dataset.socialSample));
  });
  document.getElementById("manual_social_people_count").addEventListener("input", handleManualSocialShapeChange);
  document.getElementById("manual_social_group_preset").addEventListener("change", handleManualSocialShapeChange);
  document.getElementById("manual_social_group_sizes").addEventListener("input", handleManualSocialCustomGroupSizes);
  document.getElementById("send_manual_social_context").addEventListener("click", submitManualSocialDetails);
  renderManualSocialPeopleEditor();
  document.querySelectorAll("#sensor_emotion_enabled,#sensor_social_enabled,#sensor_hand_enabled").forEach((input) => {
    input.addEventListener("change", handleSensorModeChange);
  });
  document.getElementById("fetch_weather_current").addEventListener("click", fetchWeatherCurrent);
  document.getElementById("send_weather_current").addEventListener("click", sendWeatherCurrent);
  document.getElementById("send_weather_forecast").addEventListener("click", sendWeatherForecast);
}

function wireColumnExpansion() {
  const modalElement = document.getElementById("column_expansion_modal");
  if (!modalElement || !window.bootstrap) {
    return;
  }
  columnExpansion.modal = window.bootstrap.Modal.getOrCreateInstance(modalElement);
  document.querySelectorAll("[data-column-maximize]").forEach((button) => {
    button.addEventListener("click", () => {
      openColumnExpansion(button.dataset.columnMaximize, button.dataset.columnTitle || "Panel");
    });
  });
  modalElement.addEventListener("hidden.bs.modal", restoreExpandedColumn);
  modalElement.addEventListener("shown.bs.modal", refreshExpandedColumnLayout);
}

function openColumnExpansion(columnKey, title) {
  if (!columnKey) {
    return;
  }
  const panel = document.querySelector(`[data-column-panel="${columnKey}"]`);
  const placeholder = document.querySelector(`[data-column-placeholder="${columnKey}"]`);
  const modalBody = document.getElementById("column_expansion_body");
  const modalTitle = document.getElementById("column_expansion_title");
  if (!panel || !modalBody || !columnExpansion.modal) {
    return;
  }
  if (columnExpansion.active && columnExpansion.active.panel === panel) {
    columnExpansion.modal.show();
    return;
  }
  if (columnExpansion.active) {
    restoreExpandedColumn();
  }
  columnExpansion.active = {
    panel,
    originalParent: panel.parentNode,
    nextSibling: panel.nextSibling,
    placeholder,
  };
  if (modalTitle) {
    modalTitle.textContent = title;
  }
  setColumnPlaceholderVisible(placeholder, true);
  modalBody.replaceChildren(panel);
  columnExpansion.modal.show();
  refreshExpandedColumnLayout();
}

function restoreExpandedColumn() {
  const active = columnExpansion.active;
  if (!active) {
    return;
  }
  if (active.nextSibling && active.nextSibling.parentNode === active.originalParent) {
    active.originalParent.insertBefore(active.panel, active.nextSibling);
  } else {
    active.originalParent.appendChild(active.panel);
  }
  setColumnPlaceholderVisible(active.placeholder, false);
  columnExpansion.active = null;
  refreshExpandedColumnLayout();
}

function setColumnPlaceholderVisible(placeholder, visible) {
  if (!placeholder) {
    return;
  }
  placeholder.hidden = !visible;
  placeholder.classList.toggle("d-none", !visible);
}

function refreshExpandedColumnLayout() {
  window.requestAnimationFrame(() => {
    clearOverlay();
  });
}

function configureValerianView() {
  detachedView.panel = detachedPanelFromLocation();
  const mode = detachedView.panel ? "detached" : "cockpit";
  document.documentElement.dataset.valerianView = mode;
  document.documentElement.dataset.valerianPanel = detachedView.panel || "";
  if (document.body) {
    document.body.dataset.valerianView = mode;
    document.body.dataset.valerianPanel = detachedView.panel || "";
  }
  document.querySelectorAll("[data-column-key]").forEach((column) => {
    column.classList.toggle("is-detached-active", !!detachedView.panel
      && column.dataset.columnKey === detachedView.panel);
  });
  const title = detachedView.panel
    ? `Valerian ${columnDisplayTitle(detachedView.panel)}`
    : "Valerian Cockpit";
  const subtitle = document.getElementById("valerian_page_subtitle");
  if (subtitle) {
    subtitle.textContent = title;
  }
  document.title = title;
}

function detachedPanelFromLocation() {
  const params = new URLSearchParams(window.location.search || "");
  if (params.get("mode") !== "detached") {
    return null;
  }
  const panel = params.get("panel") || params.get("column") || "";
  return VALERIAN_COLUMN_KEYS.includes(panel) ? panel : "sensing";
}

function columnDisplayTitle(columnKey) {
  switch (columnKey) {
    case "sensing":
      return "Sensing";
    case "interaction":
      return "Interaction";
    case "behaviour":
      return "Behaviour";
    default:
      return "Panel";
  }
}

function wireColumnDetach() {
  document.querySelectorAll("[data-column-detach]").forEach((button) => {
    button.addEventListener("click", () => {
      openDetachedColumn(button.dataset.columnDetach);
    });
  });
}

function openDetachedColumn(columnKey) {
  if (!VALERIAN_COLUMN_KEYS.includes(columnKey)) {
    return;
  }
  if (!state.agentId) {
    appendSystemMessage("Connect an agent before opening a separate window.");
    return;
  }
  if (state.accessCode) {
    sessionStorage.setItem(ACCESS_CODE_STORAGE_KEY, state.accessCode);
  }
  const url = detachedColumnUrl(columnKey);
  const name = `prometheus-valerian-${columnKey}-${state.agentId}`;
  const opened = window.open(url, name, "popup,width=1180,height=900");
  if (opened) {
    opened.focus();
  } else {
    appendLog("app", "detached window blocked by browser popup settings.");
  }
}

function detachedColumnUrl(columnKey) {
  const url = new URL("/valerian/", window.location.origin);
  url.searchParams.set("mode", "detached");
  url.searchParams.set("panel", columnKey);
  url.searchParams.set("agentId", state.agentId);
  return url.toString();
}

function createWindowId() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }
  return `window-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function initControlOwnership() {
  if ("BroadcastChannel" in window) {
    controlOwnership.channel = new BroadcastChannel(CONTROL_OWNERSHIP_CHANNEL);
    controlOwnership.channel.addEventListener("message", handleControlOwnershipMessage);
  }
  window.addEventListener("storage", handleControlOwnershipStorageEvent);
  controlOwnership.staleSweepTimer = setInterval(() => {
    sweepStaleControlOwners();
    refreshControlOwnershipUi();
  }, CONTROL_OWNER_HEARTBEAT_MS);
  sweepStaleControlOwners();
  refreshControlOwnershipUi();
}

function handleControlOwnershipMessage(event) {
  const data = event && event.data;
  if (!data || data.ownerId === controlOwnership.windowId || !CONTROL_RESOURCES.includes(data.resource)) {
    return;
  }
  refreshControlOwnershipUi();
}

function handleControlOwnershipStorageEvent(event) {
  if (!event || !event.key || !event.key.startsWith(CONTROL_OWNER_STORAGE_PREFIX)) {
    return;
  }
  refreshControlOwnershipUi();
}

function claimControlOwnership(resource) {
  if (!CONTROL_RESOURCES.includes(resource)) {
    return false;
  }
  const existing = readControlOwner(resource);
  if (existing && existing.ownerId !== controlOwnership.windowId) {
    refreshControlOwnershipUi();
    return false;
  }
  writeControlOwner(resource);
  controlOwnership.owned.add(resource);
  startControlOwnershipHeartbeat(resource);
  broadcastControlOwnership("claim", resource);
  refreshControlOwnershipUi();
  return true;
}

function releaseControlOwnership(resource) {
  if (!CONTROL_RESOURCES.includes(resource)) {
    return;
  }
  stopControlOwnershipHeartbeat(resource);
  controlOwnership.owned.delete(resource);
  const existing = readControlOwner(resource, { removeStale: false });
  if (existing && existing.ownerId === controlOwnership.windowId) {
    removeControlOwner(resource);
  }
  broadcastControlOwnership("release", resource);
  refreshControlOwnershipUi();
}

function releaseAllControlOwnership() {
  CONTROL_RESOURCES.forEach((resource) => {
    if (controlOwnership.owned.has(resource)) {
      releaseControlOwnership(resource);
    }
  });
  if (controlOwnership.staleSweepTimer) {
    clearInterval(controlOwnership.staleSweepTimer);
    controlOwnership.staleSweepTimer = null;
  }
  if (controlOwnership.channel) {
    controlOwnership.channel.close();
    controlOwnership.channel = null;
  }
}

function controlOwnedByOther(resource) {
  const owner = readControlOwner(resource);
  return !!(owner && owner.ownerId !== controlOwnership.windowId);
}

function readControlOwner(resource, options = {}) {
  if (!CONTROL_RESOURCES.includes(resource)) {
    return null;
  }
  const removeStale = options.removeStale !== false;
  let raw = null;
  try {
    raw = localStorage.getItem(controlOwnerStorageKey(resource));
  } catch (error) {
    return null;
  }
  if (!raw) {
    return null;
  }
  let owner = null;
  try {
    owner = JSON.parse(raw);
  } catch (error) {
    if (removeStale) {
      removeControlOwner(resource);
    }
    return null;
  }
  if (!owner || !owner.ownerId || !Number.isFinite(Number(owner.updatedAt))) {
    if (removeStale) {
      removeControlOwner(resource);
    }
    return null;
  }
  if (removeStale && Date.now() - Number(owner.updatedAt) > CONTROL_OWNER_TTL_MS) {
    removeControlOwner(resource);
    broadcastControlOwnership("release", resource);
    return null;
  }
  return owner;
}

function writeControlOwner(resource) {
  const owner = {
    ownerId: controlOwnership.windowId,
    resource,
    panel: detachedView.panel || "cockpit",
    agentId: state.agentId || "",
    updatedAt: Date.now(),
  };
  try {
    localStorage.setItem(controlOwnerStorageKey(resource), JSON.stringify(owner));
  } catch (error) {
    // If storage is unavailable, this window can still operate locally.
  }
  return owner;
}

function removeControlOwner(resource) {
  try {
    localStorage.removeItem(controlOwnerStorageKey(resource));
  } catch (error) {
    // Ignore storage failures during cleanup.
  }
}

function controlOwnerStorageKey(resource) {
  return `${CONTROL_OWNER_STORAGE_PREFIX}${resource}`;
}

function startControlOwnershipHeartbeat(resource) {
  if (controlOwnership.heartbeatTimers.has(resource)) {
    return;
  }
  const timer = setInterval(() => {
    if (!controlOwnership.owned.has(resource)) {
      stopControlOwnershipHeartbeat(resource);
      return;
    }
    writeControlOwner(resource);
    broadcastControlOwnership("heartbeat", resource);
  }, CONTROL_OWNER_HEARTBEAT_MS);
  controlOwnership.heartbeatTimers.set(resource, timer);
}

function stopControlOwnershipHeartbeat(resource) {
  const timer = controlOwnership.heartbeatTimers.get(resource);
  if (!timer) {
    return;
  }
  clearInterval(timer);
  controlOwnership.heartbeatTimers.delete(resource);
}

function broadcastControlOwnership(type, resource) {
  if (!controlOwnership.channel) {
    return;
  }
  controlOwnership.channel.postMessage({
    type,
    resource,
    ownerId: controlOwnership.windowId,
    updatedAt: Date.now(),
  });
}

function sweepStaleControlOwners() {
  CONTROL_RESOURCES.forEach((resource) => readControlOwner(resource));
}

function refreshControlOwnershipUi() {
  updateCameraOwnershipControls();
  setTranscriptionControlsLocked(state.transcriptionListening);
}

function applyStoredTheme() {
  setTheme(loadStoredTheme(), { persist: false });
}

function loadStoredTheme() {
  try {
    return normalizeTheme(localStorage.getItem(THEME_STORAGE_KEY));
  } catch (error) {
    return "light";
  }
}

function toggleTheme() {
  setTheme(currentTheme() === "dark" ? "light" : "dark");
}

function currentTheme() {
  return normalizeTheme(document.documentElement.dataset.theme);
}

function normalizeTheme(theme) {
  return theme === "dark" ? "dark" : "light";
}

function setTheme(theme, options = {}) {
  const nextTheme = normalizeTheme(theme);
  document.documentElement.dataset.theme = nextTheme;
  document.documentElement.dataset.bsTheme = nextTheme;
  if (options.persist !== false) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch (error) {
      // Ignore storage failures; the visible theme can still change for this page.
    }
  }
  updateThemeControls(nextTheme);
}

function updateThemeControls(theme) {
  const dark = theme === "dark";
  const label = dark ? "Switch to light mode" : "Switch to dark mode";
  const icon = dark ? "bi-sun" : "bi-moon-stars";
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    button.title = label;
    button.setAttribute("aria-label", label);
    button.setAttribute("aria-pressed", dark ? "true" : "false");
    const iconElement = button.querySelector("i");
    if (iconElement) {
      iconElement.className = `bi ${icon}`;
    }
  });
}

function showAgentDrawerTab() {
  const tab = document.getElementById("agent_drawer_tab");
  if (!tab || !window.bootstrap || !window.bootstrap.Tab) {
    return;
  }
  window.bootstrap.Tab.getOrCreateInstance(tab).show();
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
  if (state.transcriptionListening) {
    await stopTranscription();
  }
  if (state.cameraRunning) {
    stopCamera({ silent: true });
  }
  await stopSpeechPlayback("agent_switch", { reset: true, silent: true });
  cleanupStreams();
  state.agentId = selectedAgentId;
  document.getElementById("agent_id_input").value = selectedAgentId;
  document.getElementById("agent_select").value = selectedAgentId;
  updateSelectedAgentStatus();
  state.lastBehaviourEventId = null;
  resetBehaviourDeduplication();
  resetSpeechSensingPanel();
  setControlsEnabled(false);
  const infoLoaded = await loadAgentInfo();
  if (!infoLoaded) {
    appendSystemMessage("Agent not found or unavailable.");
    await disconnectAgent({ preserveInput: true, silent: true });
    return;
  }
  await ensureLiveTranscriptionUi();
  await ensureSpeechPlaybackCoordinator();
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
    document.getElementById("agent_info_name").textContent = prometheusFacingText(data.name) || "-";
    document.getElementById("agent_info_description").textContent = prometheusFacingText(data.description) || "-";
    document.getElementById("agent_info_language").textContent = agentLanguageLabel(data.languageCode);
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
  await stopSpeechPlayback("agent_disconnect", { reset: true, silent: true });
  if (state.transcriptionListening) {
    await stopTranscription();
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
  resetSpeechSensingPanel();
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
  document.getElementById("agent_info_name").textContent = "-";
  document.getElementById("agent_info_description").textContent = "-";
  document.getElementById("agent_info_language").textContent = "-";
  renderAgentInteractionProfile(null);
  setActiveStatus(null);
  applyInteractionProfile(null);
}

function renderAgentInteractionProfile(profile) {
  renderProfileTokenList("agent_profile_observations", normalizeProfileList(profile && profile.supportedObservations));
  renderProfileTokenList("agent_profile_behaviours", normalizeProfileList(profile && profile.supportedBehaviourModalities));
  renderProfileTokenList("agent_profile_tags", normalizeProfileList(profile && profile.profileTags));
}

function agentLanguageLabel(languageCode) {
  return typeof languageCode === "string" && languageCode.trim() ? languageCode.trim() : "-";
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
  const hasWeatherSensing = capabilities.fallbackAll
    || profileListIntersects(capabilities.supportedObservations, PROFILE_WEATHER_OBSERVATIONS);
  setProfileElementVisible(document.getElementById("sensing_accordion"), hasVisualSensing || hasWeatherSensing);
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
  if (!profileListIntersects(capabilities.supportedObservations, PROFILE_WEATHER_OBSERVATIONS)) {
    resetWeatherState();
  }
  resetDisabledSensorState();
}

function currentInteractionCapabilities() {
  return resolveInteractionCapabilities(state.agentInfo && state.agentInfo.interactionProfile);
}

function currentProfileSupportsObservation(observation) {
  const capabilities = currentInteractionCapabilities();
  return capabilities.fallbackAll || profileListIntersects(capabilities.supportedObservations, [observation]);
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
      } else if (event.type === "obs.weather.current" || event.type === "obs.weather.forecast") {
        renderWeatherFromPayload(event.type, event.payload);
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
  ["behaviour-live", "behaviour-replay"].forEach((eventName) => {
    state.behaviourSource.addEventListener(eventName, (event) => {
      if (event.lastEventId) {
        state.lastBehaviourEventId = event.lastEventId;
      }
      try {
        handleBehaviourEnvelope(JSON.parse(event.data), {
          delivery: eventName === "behaviour-live" ? "live" : "replay",
          eventId: event.lastEventId || "",
        });
      } catch (_) {
        appendLog("stream", "invalid behaviour event.");
      }
    });
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
    await stopSpeechPlayback("agent_reset", { silent: true });
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
    resetSpeechSensingPanel();
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
    handleBehaviourEnvelope(responseEvent, { delivery: "acknowledgement" });
  } else {
    renderLatestEvent(responseEvent);
  }
}

function handleBehaviourEnvelope(event, options = {}) {
  if (!event || event.type !== "resp.behaviour_plan" || !event.payload) {
    return;
  }
  let plan = null;
  try {
    plan = JSON.parse(event.payload);
  } catch (_) {
    appendLog("behaviour", "payload is not valid json.");
    return;
  }
  queueBehaviourSpeech(plan, options);
  const key = behaviourEventKey(event, options.eventId);
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

function behaviourEventKey(event, eventId = "") {
  if (eventId) {
    return `id:${eventId}`;
  }
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

function queueBehaviourSpeech(plan, options = {}) {
  const eventId = typeof options.eventId === "string" ? options.eventId.trim() : "";
  const speech = typeof plan?.speech === "string" ? plan.speech : "";
  if (!eventId || !speech.trim()) {
    return;
  }
  const generation = speechPlayback.generation;
  const agentId = state.agentId;
  speechPlayback.enqueueChain = speechPlayback.enqueueChain.then(async () => {
    if (generation !== speechPlayback.generation || !agentId || state.agentId !== agentId) return;
    const coordinator = await ensureSpeechPlaybackCoordinator();
    if (generation !== speechPlayback.generation || state.agentId !== agentId) return;
    coordinator.enqueue({ eventId, speech, delivery: options.delivery || "visual" });
  }).catch((error) => {
    appendLog("speech-playback", `queue failed: ${error.message}`);
    handleSpeechPlaybackStatus({ state: "failed", eventId, message: error.message });
  });
}

async function ensureSpeechPlaybackCoordinator() {
  if (!state.agentId) throw new Error("Connect an agent before enabling speech playback.");
  if (speechPlayback.coordinator && speechPlayback.agentId === state.agentId) {
    return speechPlayback.coordinator;
  }
  const api = await waitForSpeechPlaybackApi();
  const agentId = state.agentId;
  let coordinator = null;
  let lease = null;
  lease = new api.OutputLease({
    agentId,
    onConflict: () => {
      if (lease.active && coordinator) void coordinator.stop("output_lease_lost");
    },
  });
  coordinator = new api.BehaviourSpeechPlaybackQueue({
    lease,
    synthesize: synthesizeBehaviourSpeech,
    play: playBehaviourSpeech,
    releaseResource: releaseSpeechAudioResource,
    setInputEnabled: setSpeechPlaybackInputEnabled,
    onStatus: handleSpeechPlaybackStatus,
  });
  speechPlayback.coordinator = coordinator;
  speechPlayback.agentId = agentId;
  setSpeechPlaybackStatus("Playback Ready", "idle", false);
  return coordinator;
}

function waitForSpeechPlaybackApi(timeoutMs = 5000) {
  if (window.PrometheusSpeechPlayback) return Promise.resolve(window.PrometheusSpeechPlayback);
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Speech playback modules did not load.")), timeoutMs);
    window.addEventListener("prometheus-speech-playback-ready", () => {
      clearTimeout(timeout);
      resolve(window.PrometheusSpeechPlayback);
    }, { once: true });
  });
}

async function synthesizeBehaviourSpeech(item, signal) {
  const params = new URLSearchParams();
  const voice = document.getElementById("speechVoiceInput")?.value?.trim() || "";
  const speed = document.getElementById("speechOutputSpeedInput")?.value?.trim() || "";
  if (voice) params.set("voice", voice);
  if (speed) params.set("speed", speed);
  const suffix = params.size ? `?${params.toString()}` : "";
  const response = await scopedFetch(demoAgentPath(`/behaviours/${encodeURIComponent(item.eventId)}/speech${suffix}`), {
    method: "POST",
    headers: { Accept: "audio/*" },
    signal,
  });
  if (!response.ok) throw new Error(`Speech synthesis failed (${response.status}).`);
  const blob = await response.blob();
  if (!blob.size || !String(blob.type || "audio/mpeg").toLowerCase().startsWith("audio/")) {
    throw new Error("Speech synthesis returned invalid audio.");
  }
  return { url: URL.createObjectURL(blob), contentType: blob.type || "audio/mpeg" };
}

function playBehaviourSpeech(resource, _item, signal) {
  const audio = activeAssistantAudioElement();
  return new Promise((resolve, reject) => {
    let settled = false;
    const cleanup = () => {
      audio.removeEventListener("ended", ended);
      audio.removeEventListener("error", failed);
      signal.removeEventListener("abort", stopped);
    };
    const finish = (action) => {
      if (settled) return;
      settled = true;
      cleanup();
      action();
    };
    const ended = () => finish(resolve);
    const failed = () => finish(() => reject(new Error(`Speech playback failed: ${assistantAudioErrorMessage()}.`)));
    const stopped = () => {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
      finish(() => reject(new DOMException("Speech playback was stopped.", "AbortError")));
    };
    audio.addEventListener("ended", ended, { once: true });
    audio.addEventListener("error", failed, { once: true });
    signal.addEventListener("abort", stopped, { once: true });
    if (signal.aborted) {
      stopped();
      return;
    }
    audio.pause();
    audio.srcObject = null;
    audio.src = resource.url;
    audio.load();
    Promise.resolve(applySelectedSpeechOutputDevice())
      .then(() => audio.play())
      .catch((error) => finish(() => reject(error)));
  });
}

function releaseSpeechAudioResource(resource) {
  if (!resource?.url) return;
  const audio = activeAssistantAudioElement();
  if (audio.getAttribute("src") === resource.url || audio.src === resource.url) {
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
  }
  URL.revokeObjectURL(resource.url);
}

function setSpeechPlaybackInputEnabled(enabled) {
  const inputEnabled = Boolean(enabled);
  transcription.inputGated = !inputEnabled;
  transcription.transcriptIngress?.setAccepting(inputEnabled);
  if (transcription.transcriptionClient && state.transcriptionListening) {
    transcription.transcriptionClient.setInputEnabled(inputEnabled);
  }
  if (!inputEnabled) {
    transcription.manualTurnActive = false;
    setText("continuous_speech_sensing_value", "-");
  }
  if (state.transcriptionListening) {
    const listen = document.getElementById("listen_status");
    listen.textContent = inputEnabled ? "Listening" : "Input Paused";
    listen.className = `status-pill is-${inputEnabled ? "listening" : "idle"}`;
  }
  updateTranscriptionManualControl();
  setTranscriptionControlsLocked(state.transcriptionListening);
}

function handleSpeechPlaybackStatus(status) {
  const mapping = {
    loading: ["Speech Loading", "idle", true],
    speaking: ["Speaking", "live", true],
    completed: ["Playback Ready", "idle", false],
    stopped: ["Playback Stopped", "idle", false],
    failed: ["Synthesis Error", "error", false],
  };
  if (status.state === "skipped" && status.reason === "replay_or_non_live") return;
  if (status.state === "skipped" && status.reason === "output_lease_conflict") {
    setSpeechPlaybackStatus("Output In Other Window", "idle", false);
  } else {
    const [label, mode, stoppable] = mapping[status.state] || ["Playback Ready", "idle", false];
    setSpeechPlaybackStatus(label, mode, stoppable);
  }
  appendLog("speech-playback", JSON.stringify(status));
}

function setSpeechPlaybackStatus(label, mode, stoppable) {
  const status = document.getElementById("speech_playback_status");
  if (status) {
    status.textContent = label;
    status.className = `status-pill is-${mode}`;
  }
  const stop = document.getElementById("stop_speech_playback");
  if (stop) stop.disabled = !stoppable;
}

async function stopSpeechPlayback(reason = "operator_stop", options = {}) {
  speechPlayback.generation += 1;
  speechPlayback.enqueueChain = Promise.resolve();
  const coordinator = speechPlayback.coordinator;
  if (coordinator) {
    await coordinator.stop(reason);
  } else {
    const audio = activeAssistantAudioElement();
    audio?.pause();
    audio?.removeAttribute("src");
    audio?.load();
    setSpeechPlaybackInputEnabled(true);
    if (!options.silent) setSpeechPlaybackStatus("Playback Stopped", "idle", false);
  }
  if (options.reset) {
    speechPlayback.coordinator = null;
    speechPlayback.agentId = null;
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
  resetBehaviourPanels();
  if (typeof plan.speech === "string" && plan.speech.trim()) {
    setText("speech_preview", plan.speech.trim());
    setBehaviourChannelActive("speech", true);
  }
  renderNonVerbal(plan.nonVerbal);
  renderMotion(plan.motion);
  renderDisplay(plan.display);
  appendLog("behaviour", `received ${behaviourSummary(plan)}`);
}

function renderNonVerbal(nonVerbal) {
  if (!nonVerbal || typeof nonVerbal !== "object") {
    setGestureVisual("NONE");
    return;
  }
  setGestureVisual(nonVerbal.gesture || "NONE");
  const face = nonVerbal.facialExpression;
  if (typeof face === "string") {
    setText("face_value", face);
    setBehaviourChannelActive("face", !!face.trim());
  } else if (face && typeof face === "object") {
    const faceLabel = asText(face.type || face.expression);
    const hasIntensity = face.intensity !== undefined && face.intensity !== null;
    setText("face_value", faceLabel);
    if (hasIntensity) {
      const intensity = asUnitNumber(face.intensity);
      setText("face_intensity_value", formatPercent(intensity));
      setBehaviourMeter("face_intensity_meter", intensity);
    }
    setBehaviourChannelActive("face", faceLabel !== "-" || hasIntensity);
  }
  const gaze = nonVerbal.gaze;
  if (typeof gaze === "string") {
    setText("gaze_value", gaze);
    setBehaviourChannelActive("gaze", !!gaze.trim());
  } else if (gaze && typeof gaze === "object") {
    const direction = asText(gaze.direction);
    const focus = asText(gaze.focus);
    setText("gaze_value", direction !== "-" ? direction : focus);
    setText("gaze_focus_value", `Focus ${focus}`);
    setBehaviourChannelActive("gaze", direction !== "-" || focus !== "-");
  }
  const motion = nonVerbal.motion;
  if (motion && typeof motion === "object") {
    renderMotionEnergyState(motion);
  }
}

function renderMotion(motion) {
  if (!motion || typeof motion !== "object") {
    return;
  }
  setBehaviourChannelActive("motion", true);
  const sign = normalizeSign(motion.handSign);
  if (sign) {
    renderAgentSign(sign);
    resetCameraEmissionGate();
  }
  renderMotionEnergyState(motion);
  if (motion.effector) {
    setText("motion_value", asText(motion.effector));
  } else if (sign) {
    setText("motion_value", SIGNS[sign].label);
  }
}

function renderMotionEnergyState(motion) {
  const hasEnergy = motion.energy !== undefined && motion.energy !== null;
  const hasStillness = motion.stillness !== undefined && motion.stillness !== null;
  if (hasEnergy) {
    const energy = asUnitNumber(motion.energy);
    setText("motion_energy_value", formatPercent(energy));
    setBehaviourMeter("motion_energy_meter", energy);
  }
  if (hasStillness) {
    const stillness = asUnitNumber(motion.stillness);
    setText("motion_stillness_value", formatPercent(stillness));
    setBehaviourMeter("motion_stillness_meter", stillness);
  }
  if (hasEnergy || hasStillness) {
    setBehaviourChannelActive("motion", true);
    const summary = [];
    if (hasEnergy) {
      summary.push(`energy ${formatPercent(motion.energy)}`);
    }
    if (hasStillness) {
      summary.push(`stillness ${formatPercent(motion.stillness)}`);
    }
    setText("motion_value", summary.join(" / "));
  }
}

function renderDisplay(display) {
  if (!display || typeof display !== "object") {
    return;
  }
  setBehaviourChannelActive("display", true);
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

async function toggleTranscription() {
  if (!state.transcriptionListening) {
    await startTranscription();
    return;
  }
  await stopTranscription();
}

async function startTranscription() {
  if (!state.agentId) {
    return;
  }
  if (speechPlayback.coordinator?.snapshot().current) {
    setTranscriptionTransportStatus("Input Paused", "idle", "Stop current speech playback before starting transcription.");
    return;
  }
  if (!claimControlOwnership("microphone")) {
    setTranscriptionGlobalStatus("Mic In Use", "idle");
    setTranscriptionTransportStatus("Mic In Use", "idle", "Microphone is active in another Valerian window.");
    setSpeechDeviceStatus("Microphone is active in another Valerian window.", "error");
    appendLog("transcription", "start blocked; microphone is active in another Valerian window.");
    return;
  }
  setTranscriptionState(true);
  appendLog("transcription", "starting gpt-live-transcribe session.");
  setTranscriptionTransportStatus("Transcription Starting", "idle", "");
  try {
    await ensureLiveTranscriptionUi();
    const settings = transcription.transcriptionSettingsPanel.apiValues();
    const mediaPreferences = transcription.transcriptionSettingsPanel.mediaValues();
    transcription.transcriptionSettingsPanel.setLifecycle("CONNECTING");
    const started = await transcription.transcriptionClient.start({ settings, mediaPreferences });
    transcription.transcriptionSettingsPanel.setAppliedCapture(started.appliedCapture);
    transcription.transcriptionSettingsPanel.setLifecycle("CONNECTED");
    updateTranscriptionManualControl();
  } catch (error) {
    appendLog("transcription", "start failed: " + error.message);
    await stopTranscription();
    setTranscriptionTransportStatus("Transcription Failed", "error", `Transcription start failed: ${error.message}`);
  }
}

async function stopTranscription() {
  setTranscriptionState(false);
  await stopSpeechPlayback("transcription_stop", { silent: true });
  if (transcription.transcriptionClient) {
    await transcription.transcriptionClient.stop();
  }
  if (transcription.transcriptionSettingsPanel) {
    transcription.transcriptionSettingsPanel.setAppliedCapture({});
    transcription.transcriptionSettingsPanel.setLifecycle("IDLE");
  }
  transcription.manualTurnActive = false;
  transcription.inputGated = false;
  updateTranscriptionManualControl();
  setTranscriptionTransportStatus("Transcription Idle", "idle", "");
  appendLog("transcription", "stopped.");
  releaseControlOwnership("microphone");
}

async function ensureLiveTranscriptionUi() {
  if (!state.agentId) throw new Error("Connect an agent before starting transcription.");
  if (transcription.transcriptionClient && transcription.transcriptionAgentId === state.agentId) {
    return transcription.transcriptionClient;
  }
  if (transcription.transcriptionClient) await transcription.transcriptionClient.stop();
  transcription.transcriptIngress?.setAccepting(false);
  const api = await waitForTranscriptionApi();
  const media = new api.TranscriptionMedia({
    onDiagnostic: handleLiveTranscriptionDiagnostic,
  });
  const ingress = new api.ScopedTranscriptIngress({
    agentId: state.agentId,
    accessCode: state.accessCode || "",
    canAccept: () => !transcription.inputGated,
    onQueued: renderQueuedLiveTranscript,
    onAccepted: handleAcceptedLiveTranscript,
    onStatus: handleTranscriptIngressStatus,
    onDiagnostic: handleLiveTranscriptionDiagnostic,
  });
  const client = new api.LiveTranscriptionClient({
    agentId: state.agentId,
    accessCode: state.accessCode || "",
    media,
    storageKey: `prometheus.valerian.transcription.${state.agentId}.v1`,
    onPartial: ({ text }) => {
      setText("continuous_speech_sensing_value", text || "-");
    },
    onFinal: handleLiveTranscriptionFinal,
    onState: handleLiveTranscriptionState,
    onInputState: handleLiveTranscriptionInputState,
    onDiagnostic: handleLiveTranscriptionDiagnostic,
  });
  const descriptor = await client.initialize();
  const root = document.getElementById("live_transcription_settings_root");
  transcription.transcriptionSettingsPanel = new api.TranscriptionSettingsPanel({
    root,
    preferences: client.preferences,
    media,
    onValidation: () => updateTranscriptionManualControl(),
  });
  transcription.transcriptionClient = client;
  transcription.transcriptIngress = ingress;
  transcription.transcriptionAgentId = state.agentId;
  appendLog("transcription", `loaded ${descriptor.model} settings schema ${descriptor.schemaVersion}.`);
  updateTranscriptionManualControl();
  return client;
}

function waitForTranscriptionApi(timeoutMs = 5000) {
  if (window.PrometheusTranscription) return Promise.resolve(window.PrometheusTranscription);
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Live-transcription modules did not load.")), timeoutMs);
    window.addEventListener("prometheus-transcription-ready", () => {
      clearTimeout(timeout);
      resolve(window.PrometheusTranscription);
    }, { once: true });
  });
}

function handleLiveTranscriptionFinal({ epoch, itemId, text }) {
  const transcript = String(text || "").trim();
  if (!transcript || isLikelyAsrHallucination(transcript)) {
    appendLog("transcription", `ignored empty or noisy final transcript for ${itemId}.`);
    return;
  }
  return transcription.transcriptIngress?.submit({ epoch, itemId, text: transcript }) || false;
}

function renderQueuedLiveTranscript({ itemId, text }) {
  appendMessage("user", text);
  renderSpeechSensingTranscript(text);
  appendLog("transcription", `final transcript ${itemId} queued.`);
}

async function handleAcceptedLiveTranscript({ itemId, text, acknowledgement }) {
  if (acknowledgement && typeof acknowledgement.active === "boolean") {
    setActiveStatus(acknowledgement.active);
  }
  renderLatestEvent({ type: "obs.user_utterance", payload: text });
  appendLog("transcription", `final transcript ${itemId} accepted.`);
  await Promise.all([loadStorage(), loadAgentState()]);
}

function handleTranscriptIngressStatus(status) {
  const mapping = {
    queued: ["Transcript Queued", "idle"],
    acknowledging: ["Transcript Sending", "idle"],
    accepted: ["Transcript Accepted", "live"],
    rejected: ["Transcript Rejected", "error"],
    "provider-error": ["Provider Error", "error"],
  };
  const [label, mode] = mapping[status.state] || ["Transcript Ready", "idle"];
  const element = document.getElementById("transcription_ingress_status");
  if (element) {
    element.textContent = label;
    element.className = `status-pill is-${mode}`;
  }
  appendLog("transcription-ingress", JSON.stringify(status));
}

function handleLiveTranscriptionDiagnostic(diagnostic) {
  if (["provider_error", "provider_transcription_failed"].includes(diagnostic?.code)) {
    handleTranscriptIngressStatus({ state: "provider-error", itemId: diagnostic.itemId || null,
      reason: diagnostic.code });
  }
  appendLog("transcription", JSON.stringify(diagnostic));
}

function handleLiveTranscriptionState({ state: transportState, message = "", attempt = 0 }) {
  const mapping = {
    connecting: ["Transcription Starting", "idle"],
    connected: ["Transcription Connected", "live"],
    reconnecting: [`Transcription Reconnecting ${attempt || ""}`.trim(), "error"],
    failed: ["Transcription Failed", "error"],
    stopping: ["Transcription Stopping", "idle"],
    stopped: ["Transcription Idle", "idle"],
  };
  const [label, mode] = mapping[transportState] || ["Transcription Idle", "idle"];
  setTranscriptionTransportStatus(label, mode, message);
  const lifecycle = transportState === "connected" ? "CONNECTED"
    : transportState === "reconnecting" ? "RECONNECTING"
      : transportState === "failed" ? "FAILED"
        : transportState === "connecting" ? "CONNECTING" : "IDLE";
  transcription.transcriptionSettingsPanel?.setLifecycle(lifecycle);
  updateTranscriptionManualControl();
}

function handleLiveTranscriptionInputState({ type }) {
  if (type.endsWith("speech_started")) {
    document.getElementById("listen_status").textContent = "Hearing Speech";
  } else if (type.endsWith("speech_stopped") || type.endsWith("committed")) {
    document.getElementById("listen_status").textContent = "Listening";
  }
  appendLog("transcription", type);
}

function updateTranscriptionManualControl() {
  const button = document.getElementById("transcription_push_to_talk");
  let manual = false;
  try {
    manual = transcription.transcriptionSettingsPanel?.apiValues()?.turnDetection?.type === "manual";
  } catch (_error) {
    manual = false;
  }
  button.classList.toggle("d-none", !manual);
  button.disabled = !manual || transcription.transcriptionClient?.transport?.state !== "connected";
  button.setAttribute("aria-pressed", transcription.manualTurnActive ? "true" : "false");
}

function beginManualTranscriptionTurn(event) {
  event.preventDefault();
  if (!transcription.transcriptionClient?.startManualTurn()) return;
  transcription.manualTurnActive = true;
  event.currentTarget.setPointerCapture?.(event.pointerId);
  updateTranscriptionManualControl();
}

function finishManualTranscriptionTurn(event) {
  event.preventDefault();
  if (!transcription.manualTurnActive) return;
  transcription.manualTurnActive = false;
  transcription.transcriptionClient?.commitManualTurn();
  updateTranscriptionManualControl();
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

function speechOutputSettingControls() {
  return [
    "speechVoiceInput",
    "speechOutputSpeedInput",
  ].map((id) => document.getElementById(id)).filter(Boolean);
}

function speechSettingStorageKey(storageName) {
  return {
    speechVoice: SPEECH_VOICE_STORAGE_KEY,
    speechOutputSpeed: SPEECH_OUTPUT_SPEED_STORAGE_KEY,
  }[storageName] || "";
}

function loadStoredSpeechSettings() {
  speechOutputSettingControls().forEach((control) => {
    const storageKey = speechSettingStorageKey(control.dataset.storageKey || "");
    if (!storageKey) {
      return;
    }
    const storedValue = localStorage.getItem(storageKey);
    if (storedValue === null) {
      return;
    }
    control.value = storedValue;
  });
}

function saveSpeechOutputSettingSelection(event) {
  const control = event.currentTarget;
  const storageKey = speechSettingStorageKey(control.dataset.storageKey || "");
  if (!storageKey) {
    return;
  }
  const value = control.value.trim();
  if (value) {
    localStorage.setItem(storageKey, value);
  } else {
    localStorage.removeItem(storageKey);
  }
}

function loadStoredSpeechDeviceSelection() {
  speechDevices.outputDeviceId = localStorage.getItem(SPEECH_OUTPUT_DEVICE_STORAGE_KEY) || "";
}

async function saveSpeechOutputDeviceSelection() {
  speechDevices.outputDeviceId = document.getElementById("speech_output_device_select").value || "";
  localStorage.setItem(SPEECH_OUTPUT_DEVICE_STORAGE_KEY, speechDevices.outputDeviceId);
  await applySelectedSpeechOutputDevice();
}

function speechAudioSelectionSupported() {
  return !!(navigator.mediaDevices && navigator.mediaDevices.enumerateDevices);
}

function speechOutputSelectionSupported() {
  return !!(activeAssistantAudioElement() && typeof activeAssistantAudioElement().setSinkId === "function");
}

async function refreshAudioDevices(options = {}) {
  const silent = options.silent === true;
  if (!speechAudioSelectionSupported()) {
    renderSpeechOutputDeviceSelection([]);
    setSpeechDeviceStatus("Audio device selection is not supported by this browser.", "error");
    setTranscriptionControlsLocked(state.transcriptionListening);
    return;
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices();
    const outputDevices = devices.filter((device) => device.kind === "audiooutput");
    renderSpeechOutputDeviceSelection(outputDevices);
    speechDevices.devicesLoaded = true;
    if (!silent) {
      const outputNote = speechOutputSelectionSupported() ? "" : " Speaker selection is not supported by this browser.";
      setSpeechDeviceStatus(`Audio devices refreshed.${outputNote}`, speechOutputSelectionSupported() ? "ready" : "");
    } else if (!speechOutputSelectionSupported()) {
      setSpeechDeviceStatus("Speaker selection is not supported by this browser; using browser default output.");
    }
  } catch (error) {
    setSpeechDeviceStatus(`Audio device refresh failed: ${error.message}`, "error");
    appendLog("speech-playback", `audio device refresh failed: ${error.message}`);
  } finally {
    setTranscriptionControlsLocked(state.transcriptionListening);
  }
}

function renderSpeechOutputDeviceSelection(outputDevices) {
  renderSpeechDeviceSelect(
    document.getElementById("speech_output_device_select"),
    outputDevices,
    "System / browser default",
    speechDevices.outputDeviceId
  );
}

function renderSpeechDeviceSelect(select, devices, defaultLabel, selectedDeviceId) {
  if (!select) {
    return;
  }
  select.replaceChildren(new Option(defaultLabel, ""));
  const seen = new Set([""]);
  devices.forEach((device, index) => {
    if (!device.deviceId || device.deviceId === "default" || seen.has(device.deviceId)) {
      return;
    }
    const label = device.label || `Speaker ${index + 1}`;
    select.appendChild(new Option(label, device.deviceId));
    seen.add(device.deviceId);
  });
  select.value = seen.has(selectedDeviceId) ? selectedDeviceId : "";
}

function selectedSpeechOutputDeviceId() {
  return document.getElementById("speech_output_device_select").value || "";
}

async function applySelectedSpeechOutputDevice() {
  const audio = activeAssistantAudioElement();
  const deviceId = selectedSpeechOutputDeviceId();
  if (!speechOutputSelectionSupported()) {
    const message = deviceId
      ? "Selected speaker cannot be applied because this browser does not support speaker selection."
      : "Speaker selection is not supported by this browser; using browser default output.";
    setSpeechDeviceStatus(message, deviceId ? "error" : "");
    appendLog("speech-playback", message);
    return false;
  }
  try {
    await audio.setSinkId(deviceId);
    setSpeechDeviceStatus(`Speaker output: ${selectedSpeechDeviceLabel(document.getElementById("speech_output_device_select"))}.`, "ready");
    return true;
  } catch (error) {
    const message = `Speaker selection failed: ${error.message}`;
    setSpeechDeviceStatus(message, "error");
    appendLog("speech-playback", message);
    return false;
  }
}

function selectedSpeechDeviceLabel(select) {
  const selected = select && select.options[select.selectedIndex];
  return selected ? selected.textContent : "System / browser default";
}

function setSpeechDeviceStatus(text, mode = "") {
  const el = document.getElementById("speech_device_status");
  if (!el) {
    return;
  }
  el.textContent = text;
  el.classList.toggle("text-danger", mode === "error");
  el.classList.toggle("text-success", mode === "ready");
  el.classList.toggle("text-muted", mode !== "error" && mode !== "ready");
}

function setTranscriptionTransportStatus(text, mode = "idle", detail = "") {
  const status = document.getElementById("transcription_transport_status");
  if (status) {
    status.textContent = text;
    status.className = `status-pill is-${mode || "idle"}`;
  }
  const detailElement = document.getElementById("transcription_transport_detail");
  if (detailElement) {
    detailElement.textContent = detail;
    detailElement.classList.toggle("text-danger", mode === "error");
    detailElement.classList.toggle("text-muted", mode !== "error");
  }
}

function setTranscriptionGlobalStatus(text, mode = "idle") {
  const status = document.getElementById("transcription_status");
  if (!status) {
    return;
  }
  status.textContent = text;
  status.className = `status-pill is-${mode || "idle"}`;
}

function loadStoredCameraDeviceSelection() {
  cameraDevices.deviceId = localStorage.getItem(CAMERA_DEVICE_STORAGE_KEY) || "";
}

async function saveCameraDeviceSelection() {
  cameraDevices.deviceId = selectedCameraDeviceId();
  localStorage.setItem(CAMERA_DEVICE_STORAGE_KEY, cameraDevices.deviceId);
  if (state.cameraRunning) {
    setCameraDeviceStatus("Switching camera input.", "ready");
    await restartCameraWithSelectedDevice();
  } else {
    setCameraDeviceStatus("Camera saved for the next camera session.", "ready");
  }
}

function cameraSelectionSupported() {
  return !!(navigator.mediaDevices && navigator.mediaDevices.enumerateDevices && navigator.mediaDevices.getUserMedia);
}

async function refreshCameraDevices(options = {}) {
  const requestPermission = options.requestPermission === true;
  const silent = options.silent === true;
  if (!cameraSelectionSupported()) {
    renderCameraDeviceSelections([]);
    setCameraDeviceStatus("Camera selection is not supported by this browser.", "error");
    updateCameraDeviceControls();
    return;
  }
  if (requestPermission && controlOwnedByOther("camera")) {
    setCameraDeviceStatus("Camera is active in another Valerian window.", "error");
    updateCameraDeviceControls();
    return;
  }
  let permissionStream = null;
  try {
    if (requestPermission && !camera.stream) {
      permissionStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
    }
    const devices = await navigator.mediaDevices.enumerateDevices();
    const videoDevices = devices.filter((device) => device.kind === "videoinput");
    renderCameraDeviceSelections(videoDevices);
    cameraDevices.devicesLoaded = true;
    if (!silent) {
      setCameraDeviceStatus(
        videoDevices.length ? `Camera devices refreshed (${videoDevices.length}).` : "No camera devices found.",
        videoDevices.length ? "ready" : "error"
      );
    }
  } catch (error) {
    setCameraDeviceStatus(`Camera device refresh failed: ${error.message}`, "error");
    appendLog("camera", `device refresh failed: ${error.message}`);
  } finally {
    if (permissionStream) {
      permissionStream.getTracks().forEach((track) => track.stop());
    }
    updateCameraDeviceControls();
  }
}

function renderCameraDeviceSelections(videoDevices) {
  const select = document.getElementById("camera_device_select");
  if (!select) {
    return;
  }
  select.replaceChildren(new Option("System / browser default", ""));
  const seen = new Set([""]);
  videoDevices.forEach((device, index) => {
    if (!device.deviceId || device.deviceId === "default" || seen.has(device.deviceId)) {
      return;
    }
    const label = device.label || `Camera ${index + 1}`;
    select.appendChild(new Option(label, device.deviceId));
    seen.add(device.deviceId);
  });
  select.value = seen.has(cameraDevices.deviceId) ? cameraDevices.deviceId : "";
}

function selectedCameraDeviceId() {
  return document.getElementById("camera_device_select").value || "";
}

function selectedCameraDeviceLabel() {
  const select = document.getElementById("camera_device_select");
  const selected = select && select.options[select.selectedIndex];
  return selected ? selected.textContent : "System / browser default";
}

function cameraVideoConstraints() {
  const constraints = {
    width: { ideal: 960 },
    height: { ideal: 720 },
  };
  const deviceId = selectedCameraDeviceId();
  if (deviceId) {
    constraints.deviceId = { exact: deviceId };
  } else {
    constraints.facingMode = "user";
  }
  return constraints;
}

async function restartCameraWithSelectedDevice() {
  stopCamera({ silent: true, preserveStatus: true });
  await startCamera();
}

function setCameraDeviceStatus(text, mode = "") {
  const el = document.getElementById("camera_device_status");
  if (!el) {
    return;
  }
  el.textContent = text || "";
  el.classList.toggle("text-danger", mode === "error");
  el.classList.toggle("text-success", mode === "ready");
  el.classList.toggle("text-muted", mode !== "error" && mode !== "ready");
}

function updateCameraDeviceControls() {
  const supported = cameraSelectionSupported();
  const enabled = !!state.agentId && supported && !controlOwnedByOther("camera");
  const select = document.getElementById("camera_device_select");
  const refresh = document.getElementById("refresh_camera_devices");
  if (select) {
    select.disabled = !enabled;
  }
  if (refresh) {
    refresh.disabled = !enabled;
  }
}

function updateCameraOwnershipControls() {
  const remote = controlOwnedByOther("camera");
  const enabled = !!state.agentId && !remote;
  const detectorControlIds = [
    "sensor_emit_enabled",
    "sensor_emotion_enabled",
    "sensor_social_enabled",
    "sensor_hand_enabled",
    "emit_interval_ms",
    "face_confidence_threshold",
    "group_distance_threshold",
    "hand_confidence_threshold",
  ];
  detectorControlIds.forEach((id) => {
    const element = document.getElementById(id);
    if (element) {
      element.disabled = !enabled;
    }
  });
  const start = document.getElementById("start_camera");
  if (start) {
    start.disabled = !enabled || state.cameraRunning;
  }
  const stop = document.getElementById("stop_camera");
  if (stop) {
    stop.disabled = !enabled || !state.cameraRunning;
  }
  updateCameraDeviceControls();
  const cameraStatus = document.getElementById("camera_status");
  const cameraDeviceStatus = document.getElementById("camera_device_status");
  if (remote && !state.cameraRunning) {
    setCameraStatus("Camera In Use", "idle");
    setCameraDeviceStatus("Camera is active in another Valerian window.", "error");
  } else if (!remote && !state.cameraRunning && cameraStatus && cameraStatus.textContent === "Camera In Use") {
    setCameraStatus("Camera Idle", "idle");
    if (cameraDeviceStatus && cameraDeviceStatus.textContent.includes("another Valerian window")) {
      setCameraDeviceStatus("Camera uses browser default.");
    }
  }
}

function activeAssistantAudioElement() {
  return document.getElementById("assistant_audio");
}

function assistantAudioErrorMessage() {
  const audio = activeAssistantAudioElement();
  const error = audio && audio.error;
  if (!error) {
    return "unknown media error";
  }
  if (typeof MediaError !== "undefined" && error.code === MediaError.MEDIA_ERR_ABORTED) {
    return "playback aborted";
  }
  if (typeof MediaError !== "undefined" && error.code === MediaError.MEDIA_ERR_NETWORK) {
    return "network error";
  }
  if (typeof MediaError !== "undefined" && error.code === MediaError.MEDIA_ERR_DECODE) {
    return "decode error";
  }
  if (typeof MediaError !== "undefined" && error.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED) {
    return "source not supported";
  }
  return `media error ${error.code}`;
}

async function startCamera() {
  if (state.cameraRunning) {
    return;
  }
  if (!claimControlOwnership("camera")) {
    setCameraStatus("Camera In Use", "idle");
    setCameraDeviceStatus("Camera is active in another Valerian window.", "error");
    appendLog("camera", "start blocked; camera is active in another Valerian window.");
    return;
  }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    setCameraStatus("Camera Error", "error");
    appendLog("camera", "camera API unavailable.");
    releaseControlOwnership("camera");
    return;
  }
  await ensureEnabledModels();
  try {
    camera.stream = await navigator.mediaDevices.getUserMedia({
      video: cameraVideoConstraints(),
      audio: false,
    });
    camera.video.srcObject = camera.stream;
    await camera.video.play();
    state.cameraRunning = true;
    document.getElementById("start_camera").disabled = true;
    document.getElementById("stop_camera").disabled = false;
    updateCameraDeviceControls();
    setCameraStatus("Camera Live", "live");
    setCameraDeviceStatus(`Camera input: ${selectedCameraDeviceLabel()}.`, "ready");
    refreshCameraDevices({ requestPermission: false, silent: true });
    runCameraLoop();
  } catch (error) {
    setCameraStatus("Camera Error", "error");
    setCameraDeviceStatus(`Camera start failed: ${error.message}`, "error");
    appendLog("camera", "start failed: " + error.message);
    stopCamera({ silent: true, preserveStatus: true });
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
  updateCameraDeviceControls();
  if (!options.preserveStatus) {
    setCameraStatus("Camera Idle", "idle");
  }
  if (!options.silent) {
    appendLog("camera", "stopped.");
  }
  releaseControlOwnership("camera");
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
  camera.faceModelsError = "";
  setEmotionEmitStatus("Loading model", "idle");
  if (!window.faceapi) {
    const message = "face-api unavailable";
    camera.faceModelsReady = false;
    camera.faceModelsError = message;
    resetEmotionReport({ statusText: "Model unavailable", statusMode: "error" });
    throw new Error(message);
  }
  try {
    await Promise.all([
      window.faceapi.nets.tinyFaceDetector.loadFromUri(FACE_MODEL_URI),
      window.faceapi.nets.faceExpressionNet.loadFromUri(FACE_MODEL_URI),
    ]);
    camera.faceModelsReady = true;
    camera.faceModelsError = "";
    setEmotionEmitStatus("Model ready", "idle");
    appendLog("camera", "face models ready.");
  } catch (error) {
    camera.faceModelsReady = false;
    camera.faceModelsError = errorMessage(error);
    resetEmotionReport({ statusText: "Model load failed", statusMode: "error" });
    throw error;
  }
}

async function loadSocialDetector() {
  camera.socialDetectorError = "";
  if (!window.cocoSsd) {
    const message = "coco-ssd unavailable";
    camera.socialDetectorReady = false;
    camera.socialDetectorError = message;
    throw new Error(message);
  }
  try {
    camera.socialDetector = await window.cocoSsd.load({ base: "lite_mobilenet_v2" });
    camera.socialDetectorReady = true;
    appendLog("camera", "person detector ready.");
  } catch (error) {
    camera.socialDetectorReady = false;
    camera.socialDetectorError = errorMessage(error);
    throw error;
  }
}

async function loadHandRecognizer() {
  camera.handDetectorError = "";
  try {
    const visionTasks = await import(MEDIAPIPE_TASKS_URL);
    const vision = await visionTasks.FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_ROOT);
    camera.handRecognizer = await visionTasks.GestureRecognizer.createFromOptions(vision, {
      baseOptions: { modelAssetPath: GESTURE_MODEL_URL },
      runningMode: "VIDEO",
      numHands: 1,
    });
    camera.handDetectorReady = true;
    appendLog("camera", "gesture recognizer ready.");
  } catch (error) {
    camera.handDetectorReady = false;
    camera.handDetectorError = errorMessage(error);
    throw error;
  }
}

async function runCameraLoop() {
  if (!state.cameraRunning) {
    return;
  }
  try {
    clearOverlay();
    if (isSensorModeEnabled("social") && camera.socialDetectorReady) {
      await detectSocial();
    } else if (isSensorModeEnabled("social")) {
      const statusText = camera.socialDetectorError ? "Social model unavailable" : "Social model not ready";
      renderSocialMetrics(null, []);
      setSocialContextStatusText(statusText, camera.socialDetectorError ? "error" : "idle");
      drawSocialStatus(statusText, camera.socialDetectorError ? "error" : "idle");
    }
    if (isSensorModeEnabled("emotion") && camera.faceModelsReady) {
      await detectEmotion();
    } else if (isSensorModeEnabled("emotion")) {
      const statusText = camera.faceModelsError ? "Model unavailable" : "Model not ready";
      resetEmotionReport({
        statusText,
        statusMode: camera.faceModelsError ? "error" : "idle",
      });
      drawFaceStatus(statusText, camera.faceModelsError ? "error" : "idle");
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
  let detection = null;
  try {
    detection = await window.faceapi
      .detectSingleFace(camera.video, new window.faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.45 }))
      .withFaceExpressions();
  } catch (error) {
    resetEmotionReport({ statusText: "Detection error", statusMode: "error" });
    drawFaceStatus("Face detection error", "error");
    appendLog("camera", "face detection failed: " + errorMessage(error));
    return;
  }
  if (!detection) {
    renderEmotionMetrics(null);
    drawFaceStatus("No face", "idle");
    return;
  }
  const emotion = deriveEmotion(detection.expressions);
  const faceScore = detection.detection.score;
  drawFaceBox(detection.detection.box, emotion, faceScore);
  renderEmotionMetrics(emotion, faceScore);
  await maybeEmitEmotion(emotion, faceScore);
}

async function detectSocial() {
  let rawDetections = [];
  try {
    rawDetections = await camera.socialDetector.detect(camera.video);
  } catch (error) {
    renderSocialMetrics(null, []);
    setSocialContextStatusText("Detection error", "error");
    drawSocialStatus("Social detection error", "error");
    appendLog("camera", "social detection failed: " + errorMessage(error));
    return;
  }
  const people = rawDetections
    .filter((d) => d && d.class === "person" && Number(d.score || 0) >= PERSON_SCORE_THRESHOLD)
    .map(normalizePersonDetection);
  const tracked = updateTracks(people);
  const social = deriveSocialSituation(tracked);
  drawSocialOverlay(tracked, social);
  if (tracked.length === 0) {
    drawSocialStatus("No people", "idle");
  }
  renderSocialMetrics(social, tracked);
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
    renderHandSignReport(candidate.sign, {
      source: "valerian.hand.camera",
      detectionMode: "client_camera",
      confidence: candidate.confidence,
      cannedGesture: candidate.cannedGesture,
      stabilityFrames: camera.stableGestureCount,
      statusText: "Live",
      statusMode: "live",
    });
  } else {
    setText("hand_sign_value", "-");
    resetHandSignReport("No sign");
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
    setEmotionEmitStatus("Live only", "idle");
    return;
  }
  const threshold = Number(document.getElementById("face_confidence_threshold").value || 0.55);
  if (emotion.confidence < threshold) {
    setEmotionEmitStatus("Below threshold", "idle");
    return;
  }
  if (!passesSensorEmitInterval("emotion")) {
    setEmotionEmitStatus("Cooldown", "idle");
    return;
  }
  if (camera.lastEmotion && camera.lastEmotion.emotion === emotion.emotion &&
    Math.abs(camera.lastEmotion.valence - emotion.valence) < 0.08 &&
    Math.abs(camera.lastEmotion.arousal - emotion.arousal) < 0.08) {
    setEmotionEmitStatus("Stable", "idle");
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
    setEmotionEmitStatus(`Emitted ${new Date().toLocaleTimeString()}`, "live");
  } else {
    setEmotionEmitStatus("Emit failed", "error");
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
  renderEmotionMetrics(emotion, 1);
  const data = await acknowledgeEvent({
    type: "obs.emotion.face",
    actor: "user",
    kind: "observation",
    payload: JSON.stringify(emotionPayload(emotion, "visual.facial.manual", 1, { detectionMode: "manual" })),
  }, { renderResponse: true });
  if (data) {
    camera.lastEmotion = emotion;
    markSensorEmitted("emotion");
    setEmotionEmitStatus(`Emitted ${new Date().toLocaleTimeString()}`, "live");
  } else {
    setEmotionEmitStatus("Emit failed", "error");
  }
  return !!data;
}

function renderEmotionMetrics(emotion, faceScore = 0) {
  if (!emotion) {
    resetEmotionReport();
    return;
  }
  const label = asText(emotion.emotion);
  const confidence = asUnitNumber(emotion.confidence);
  const valence = clamp(Number(emotion.valence || 0), -1, 1);
  const arousal = asUnitNumber(emotion.arousal);
  const faceConfidence = asUnitNumber(faceScore);
  setText("emotion_value", `${label} ${confidence.toFixed(2)}`);
  setText("emotion_valence_value", formatSignedDecimal(valence));
  setText("emotion_arousal_value", arousal.toFixed(2));
  setText("emotion_confidence_value", confidence.toFixed(2));
  setText("emotion_face_confidence_value", faceConfidence.toFixed(2));
  setEmotionAffectMarker(valence, arousal, label);
  setEmotionMeter("emotion_valence_meter", (valence + 1) / 2);
  setEmotionMeter("emotion_arousal_meter", arousal);
  setEmotionMeter("emotion_confidence_meter", confidence);
  setEmotionMeter("emotion_face_confidence_meter", faceConfidence);
  renderExpressionBars(emotion.expressions);
  setEmotionEmitStatus("Live", "live");
}

function resetEmotionReport(options = {}) {
  setText("emotion_value", "-");
  setText("emotion_valence_value", "0.00");
  setText("emotion_arousal_value", "0.00");
  setText("emotion_confidence_value", "0.00");
  setText("emotion_face_confidence_value", "0.00");
  setEmotionAffectMarker(0, 0, "neutral");
  setEmotionMeter("emotion_valence_meter", 0.5);
  setEmotionMeter("emotion_arousal_meter", 0);
  setEmotionMeter("emotion_confidence_meter", 0);
  setEmotionMeter("emotion_face_confidence_meter", 0);
  renderExpressionBars({});
  setEmotionEmitStatus(options.statusText || "No face", options.statusMode || "idle");
}

function setEmotionAffectMarker(valence, arousal, emotion) {
  const marker = document.getElementById("emotion_affect_marker");
  if (!marker) {
    return;
  }
  const x = round((clamp(Number(valence || 0), -1, 1) + 1) * 50, 1);
  const y = round(asUnitNumber(arousal) * 100, 1);
  marker.style.left = `${x}%`;
  marker.style.bottom = `${y}%`;
  marker.dataset.emotion = normalizeEmotionTone(emotion);
  marker.setAttribute("aria-label", `Valence ${formatSignedDecimal(valence)}, arousal ${asUnitNumber(arousal).toFixed(2)}`);
}

function normalizeEmotionTone(emotion) {
  const token = String(emotion || "neutral").trim().toLowerCase();
  return EMOTION_EXPRESSION_KEYS.includes(token) ? token : "neutral";
}

function setEmotionMeter(id, unitValue) {
  const el = document.getElementById(id);
  if (!el) {
    return;
  }
  const percent = formatPercent(unitValue);
  el.style.width = percent;
  el.setAttribute("aria-valuenow", String(Math.round(asUnitNumber(unitValue) * 100)));
  el.title = percent;
}

function renderExpressionBars(expressions) {
  for (const key of EMOTION_EXPRESSION_KEYS) {
    const value = asUnitNumber(expressions && expressions[key]);
    setText(`emotion_expression_${key}_value`, formatPercent(value));
    setEmotionMeter(`emotion_expression_${key}_meter`, value);
  }
}

function setEmotionEmitStatus(text, mode = "idle") {
  const el = document.getElementById("emotion_emit_status");
  if (!el) {
    return;
  }
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
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

function handleManualSocialShapeChange() {
  syncManualSocialGroupSizes();
  renderManualSocialPeopleEditor();
}

function handleManualSocialCustomGroupSizes() {
  const preset = document.getElementById("manual_social_group_preset");
  if (preset) {
    preset.value = "custom";
  }
}

function manualSocialPeopleCount() {
  const input = document.getElementById("manual_social_people_count");
  const count = clamp(Math.round(Number(input?.value || 0)), 0, 6);
  if (input && String(count) !== input.value) {
    input.value = String(count);
  }
  return count;
}

function setManualSocialPreset(kind) {
  const countInput = document.getElementById("manual_social_people_count");
  const presetInput = document.getElementById("manual_social_group_preset");
  const groupSizesInput = document.getElementById("manual_social_group_sizes");
  const presets = {
    alone: { count: 0, preset: "singletons", sizes: "" },
    single: { count: 1, preset: "singletons", sizes: "1" },
    pair: { count: 2, preset: "pair", sizes: "2" },
    crowd: { count: 3, preset: "group", sizes: "3" },
  };
  const selected = presets[kind];
  if (!selected) {
    return false;
  }
  if (countInput) {
    countInput.value = String(selected.count);
  }
  if (presetInput) {
    presetInput.value = selected.preset;
  }
  if (groupSizesInput) {
    groupSizesInput.value = selected.sizes;
  }
  renderManualSocialPeopleEditor();
  return true;
}

function syncManualSocialGroupSizes() {
  const input = document.getElementById("manual_social_group_sizes");
  const preset = document.getElementById("manual_social_group_preset")?.value || "singletons";
  if (!input || preset === "custom") {
    return;
  }
  const count = manualSocialPeopleCount();
  input.value = manualSocialGroupSizesForPreset(count, preset).join(",");
}

function manualSocialGroupSizesForPreset(count, preset) {
  if (count <= 0) {
    return [];
  }
  if (preset === "group") {
    return [count];
  }
  if (preset === "pair") {
    const sizes = [Math.min(2, count)];
    for (let remaining = count - sizes[0]; remaining > 0; remaining--) {
      sizes.push(1);
    }
    return sizes;
  }
  return Array.from({ length: count }, () => 1);
}

function renderManualSocialPeopleEditor() {
  const list = document.getElementById("manual_social_people_editor");
  if (!list) {
    return;
  }
  const existing = readManualSocialPeopleControls();
  const count = manualSocialPeopleCount();
  list.replaceChildren();
  if (count <= 0) {
    list.appendChild(socialEmptyState("No manual people", "manual-social-empty"));
    return;
  }
  for (let id = 1; id <= count; id++) {
    list.appendChild(manualSocialPersonCard(id, existing.get(id)));
  }
}

function manualSocialPersonCard(id, existing = {}) {
  const card = document.createElement("div");
  card.className = "manual-social-person-card";
  card.dataset.manualSocialPerson = String(id);
  card.dataset.testid = `manual-social-person-${id}`;

  const head = document.createElement("div");
  head.className = "manual-social-person-head";
  const title = document.createElement("span");
  title.textContent = `Person ${id}`;
  const confidence = document.createElement("span");
  confidence.className = "social-context-token";
  confidence.textContent = "conf 100%";
  head.append(title, confidence);

  const row = document.createElement("div");
  row.className = "row g-2";
  row.append(
    manualSocialSelectField(id, "movement", "Movement", MANUAL_SOCIAL_MOVEMENT_STATES, existing.movement || "unknown"),
    manualSocialRangeField(id, "movement_confidence", "Move conf", existing.movementConfidence ?? 0),
    manualSocialSelectField(id, "attention", "Attention", MANUAL_SOCIAL_ATTENTION_STATES, existing.attention || "unknown"),
    manualSocialRangeField(id, "attention_confidence", "Attention conf", existing.attentionConfidence ?? 0)
  );

  const cues = document.createElement("div");
  cues.className = "manual-social-cue-row";
  cues.append(
    manualSocialCheckbox(id, "person_visible", "Person visible", existing.personVisible !== false),
    manualSocialCheckbox(id, "face_visible", "Face visible", existing.faceVisible === true),
    manualSocialCheckbox(id, "near_frontal", "Near frontal", existing.nearFrontal === true),
    manualSocialCheckbox(id, "centered", "Centered", existing.centered === true)
  );

  card.append(head, row, cues);
  return card;
}

function manualSocialSelectField(id, key, label, values, selected) {
  const wrap = document.createElement("div");
  wrap.className = "col-6";
  const inputId = `manual_social_person_${id}_${key}`;
  const labelEl = document.createElement("label");
  labelEl.className = "form-label metric-label";
  labelEl.setAttribute("for", inputId);
  labelEl.textContent = label;
  const select = document.createElement("select");
  select.id = inputId;
  select.className = "form-select form-select-sm";
  select.dataset.manualSocialField = key;
  select.dataset.testid = inputId.replaceAll("_", "-");
  values.forEach((value) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value.replace(/_/g, " ");
    select.appendChild(option);
  });
  select.value = values.includes(selected) ? selected : "unknown";
  wrap.append(labelEl, select);
  return wrap;
}

function manualSocialRangeField(id, key, label, value) {
  const wrap = document.createElement("div");
  wrap.className = "col-6";
  const inputId = `manual_social_person_${id}_${key}`;
  const labelEl = document.createElement("label");
  labelEl.className = "form-label metric-label";
  labelEl.setAttribute("for", inputId);
  labelEl.textContent = label;
  const output = document.createElement("span");
  output.className = "ms-1";
  output.id = `${inputId}_value`;
  output.dataset.testid = `${inputId.replaceAll("_", "-")}-value`;
  const input = document.createElement("input");
  input.id = inputId;
  input.className = "form-range";
  input.type = "range";
  input.min = "0";
  input.max = "1";
  input.step = "0.05";
  input.value = String(asUnitNumber(value));
  input.dataset.manualSocialField = key;
  input.dataset.testid = inputId.replaceAll("_", "-");
  const update = () => {
    output.textContent = formatPercent(input.value);
  };
  input.addEventListener("input", update);
  update();
  labelEl.appendChild(output);
  wrap.append(labelEl, input);
  return wrap;
}

function manualSocialCheckbox(id, key, label, checked) {
  const wrap = document.createElement("div");
  wrap.className = "form-check";
  const inputId = `manual_social_person_${id}_${key}`;
  const input = document.createElement("input");
  input.id = inputId;
  input.className = "form-check-input";
  input.type = "checkbox";
  input.checked = !!checked;
  input.dataset.manualSocialField = key;
  input.dataset.testid = inputId.replaceAll("_", "-");
  const labelEl = document.createElement("label");
  labelEl.className = "form-check-label";
  labelEl.setAttribute("for", inputId);
  labelEl.textContent = label;
  wrap.append(input, labelEl);
  return wrap;
}

function readManualSocialPeopleControls() {
  const people = new Map();
  document.querySelectorAll("[data-manual-social-person]").forEach((card) => {
    const id = Number(card.dataset.manualSocialPerson || 0);
    if (!id) {
      return;
    }
    people.set(id, {
      movement: card.querySelector("[data-manual-social-field='movement']")?.value || "unknown",
      movementConfidence: asUnitNumber(card.querySelector("[data-manual-social-field='movement_confidence']")?.value),
      attention: card.querySelector("[data-manual-social-field='attention']")?.value || "unknown",
      attentionConfidence: asUnitNumber(card.querySelector("[data-manual-social-field='attention_confidence']")?.value),
      personVisible: card.querySelector("[data-manual-social-field='person_visible']")?.checked === true,
      faceVisible: card.querySelector("[data-manual-social-field='face_visible']")?.checked === true,
      nearFrontal: card.querySelector("[data-manual-social-field='near_frontal']")?.checked === true,
      centered: card.querySelector("[data-manual-social-field='centered']")?.checked === true,
    });
  });
  return people;
}

function manualSocialTrackedPeople() {
  const existing = readManualSocialPeopleControls();
  const count = manualSocialPeopleCount();
  const people = [];
  for (let id = 1; id <= count; id++) {
    const person = existing.get(id) || {};
    people.push({
      id,
      score: 1,
      activity: normalizeMovementState(person.movement),
      movementState: normalizeMovementState(person.movement),
      movementConfidence: asUnitNumber(person.movementConfidence),
      attention: {
        state: normalizeAttentionState(person.attention),
        confidence: asUnitNumber(person.attentionConfidence),
        personVisible: person.personVisible !== false,
        faceVisible: person.faceVisible === true,
        nearFrontal: person.nearFrontal === true,
        centered: person.centered === true,
        frontalCentered: person.nearFrontal === true && person.centered === true,
      },
    });
  }
  return people;
}

function manualSocialGroups(count) {
  const input = document.getElementById("manual_social_group_sizes");
  const preset = document.getElementById("manual_social_group_preset")?.value || "singletons";
  const sizes = preset === "custom"
    ? parseManualSocialGroupSizes(input?.value, count)
    : manualSocialGroupSizesForPreset(count, preset);
  const groups = [];
  let nextId = 1;
  for (const size of sizes) {
    if (nextId > count) {
      break;
    }
    const actualSize = Math.min(size, count - nextId + 1);
    if (actualSize <= 0) {
      continue;
    }
    groups.push({ members: Array.from({ length: actualSize }, (_, index) => nextId + index) });
    nextId += actualSize;
  }
  while (nextId <= count) {
    groups.push({ members: [nextId] });
    nextId += 1;
  }
  return groups;
}

function parseManualSocialGroupSizes(value, count) {
  const sizes = String(value || "")
    .split(",")
    .map((item) => Math.max(0, Math.round(Number(item.trim()))))
    .filter((size) => size > 0);
  return sizes.length > 0 ? sizes : manualSocialGroupSizesForPreset(count, "singletons");
}

function manualSocialSnapshot() {
  const people = manualSocialTrackedPeople();
  const groups = manualSocialGroups(people.length);
  return {
    social: {
      humanCount: people.length,
      groupCount: groups.filter((group) => group.members.length >= 2).length,
      singletonCount: groups.filter((group) => group.members.length === 1).length,
      largestGroupSize: groups.reduce((max, group) => Math.max(max, group.members.length), 0),
      groups,
    },
    people,
  };
}

async function submitManualSocialDetails() {
  const snapshot = manualSocialSnapshot();
  renderSocialMetrics(snapshot.social, snapshot.people);
  await submitSocialPayloads(snapshot.social, snapshot.people, "visual.social.manual");
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
  const frameWidth = Math.max(1, camera.video.videoWidth || 1);
  const frameHeight = Math.max(1, camera.video.videoHeight || 1);
  const frameDiag = Math.max(1, Math.hypot(camera.video.videoWidth || 1, camera.video.videoHeight || 1));
  for (const detection of detections) {
    const best = findBestTrack(detection, frameDiag, assigned);
    const attention = deriveAttentionSignal(detection, frameWidth, frameHeight);
    if (best) {
      const track = camera.tracks.get(best.id);
      const movement = deriveTrackMovement(track, detection, frameDiag);
      track.cx = detection.cx;
      track.cy = detection.cy;
      track.box = [detection.x, detection.y, detection.w, detection.h];
      track.score = detection.score;
      track.movementState = movement.state;
      track.activity = movement.state;
      track.movementConfidence = movement.confidence;
      track.speedNorm = movement.speedNorm;
      track.areaDelta = movement.areaDelta;
      track.attention = attention;
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
        movementState: "unknown",
        activity: "unknown",
        movementConfidence: 0,
        speedNorm: 0,
        areaDelta: 0,
        attention,
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

function deriveTrackMovement(track, detection, frameDiag) {
  if (!track || !track.box || !detection) {
    return { state: "unknown", confidence: 0, speedNorm: 0, areaDelta: 0 };
  }
  const speedNorm = Math.hypot(detection.cx - track.cx, detection.cy - track.cy) / Math.max(1, frameDiag);
  const previousArea = trackArea(track.box);
  const nextArea = Math.max(0, Number(detection.w || 0) * Number(detection.h || 0));
  const areaDelta = previousArea > 0 ? (nextArea - previousArea) / previousArea : 0;
  const absAreaDelta = Math.abs(areaDelta);
  if (!Number.isFinite(speedNorm) || !Number.isFinite(areaDelta)) {
    return { state: "unknown", confidence: 0, speedNorm: 0, areaDelta: 0 };
  }
  if (absAreaDelta >= TRACK_DEPTH_AREA_DELTA) {
    return {
      state: areaDelta > 0 ? "approaching" : "receding",
      confidence: clamp(absAreaDelta / 0.35, 0.35, 1),
      speedNorm,
      areaDelta,
    };
  }
  if (speedNorm >= TRACK_MOVING_DISTANCE_NORM) {
    return { state: "moving", confidence: clamp(speedNorm / 0.08, 0.35, 1), speedNorm, areaDelta };
  }
  if (speedNorm <= TRACK_STATIONARY_DISTANCE_NORM && absAreaDelta <= TRACK_STATIONARY_AREA_DELTA) {
    const motionShare = Math.max(speedNorm / TRACK_MOVING_DISTANCE_NORM, absAreaDelta / TRACK_DEPTH_AREA_DELTA);
    return { state: "stationary", confidence: clamp(1 - motionShare, 0.35, 1), speedNorm, areaDelta };
  }
  return { state: "unknown", confidence: 0, speedNorm, areaDelta };
}

function trackArea(box) {
  return Math.max(0, Number(box[2] || 0) * Number(box[3] || 0));
}

function deriveAttentionSignal(detection, frameWidth, frameHeight) {
  if (!detection) {
    return emptyAttentionSignal();
  }
  const width = Math.max(1, frameWidth);
  const height = Math.max(1, frameHeight);
  const score = asUnitNumber(detection.score);
  const boxWidthShare = clamp(Number(detection.w || 0) / width, 0, 1);
  const boxHeightShare = clamp(Number(detection.h || 0) / height, 0, 1);
  const aspectRatio = Number(detection.h || 0) / Math.max(0.0001, Number(detection.w || 0));
  const centerDistance = clamp(Math.abs(Number(detection.cx || 0) - width / 2) / (width / 2), 0, 1);
  const personVisible = score >= PERSON_SCORE_THRESHOLD;
  const faceVisible = personVisible && boxHeightShare >= 0.16 && boxWidthShare >= 0.04 && Number(detection.y || 0) <= height * 0.62;
  const nearFrontal = aspectRatio >= 1.2 && aspectRatio <= 4.8;
  const centered = centerDistance <= 0.45;
  const frontalCentered = nearFrontal && centered;
  const centeredScore = clamp(1 - centerDistance, 0, 1);
  const scaleScore = clamp(boxHeightShare / 0.55, 0, 1);
  const confidence = clamp(
    score * 0.36 +
    (faceVisible ? 0.24 : 0) +
    centeredScore * 0.2 +
    (frontalCentered ? 0.14 : nearFrontal ? 0.07 : 0) +
    scaleScore * 0.06,
    0,
    1
  );
  const state = personVisible
    ? (faceVisible && frontalCentered && confidence >= ATTENTION_CONFIDENCE_THRESHOLD ? "attending" : "not_attending")
    : "unknown";
  return { state, confidence, personVisible, faceVisible, nearFrontal, centered, frontalCentered };
}

function emptyAttentionSignal() {
  return {
    state: "unknown",
    confidence: 0,
    personVisible: false,
    faceVisible: false,
    nearFrontal: false,
    centered: false,
    frontalCentered: false,
  };
}

function trackToView(track) {
  const attention = normalizeAttentionSignal(track.attention, track);
  return {
    id: track.id,
    cx: track.cx,
    cy: track.cy,
    box: track.box,
    score: track.score,
    activity: track.activity || track.movementState || "unknown",
    movementState: track.movementState || "unknown",
    movementConfidence: Number(track.movementConfidence || 0),
    speedNorm: Number(track.speedNorm || 0),
    areaDelta: Number(track.areaDelta || 0),
    attention,
    attentionState: attention.state,
  };
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

function renderSocialMetrics(social, tracked = []) {
  const view = social || { humanCount: 0, groupCount: 0, singletonCount: 0, largestGroupSize: 0, groups: [] };
  const people = Array.isArray(tracked) ? tracked : [];
  const humanCount = Number(view.humanCount || 0);
  const groupCount = Number(view.groupCount || 0);
  const singletonCount = Number(view.singletonCount || 0);
  const largestGroupSize = Number(view.largestGroupSize || 0);
  setText("human_count", String(humanCount));
  setText("group_count", String(groupCount));
  setText("social_context_human_count", String(humanCount));
  setText("social_context_group_count", String(groupCount));
  setText("social_context_largest_group", String(largestGroupSize));
  setText("social_context_singleton_count", String(singletonCount));
  setSocialContextStatus(humanCount);
  renderSocialGroups(view.groups || []);
  renderSocialPeople(people);
}

function setSocialContextStatus(humanCount) {
  const el = document.getElementById("social_context_status");
  if (!el) {
    return;
  }
  if (humanCount <= 0) {
    el.textContent = "No people";
    el.className = "status-pill is-idle";
    return;
  }
  el.textContent = humanCount === 1 ? "1 person" : `${humanCount} people`;
  el.className = "status-pill is-live";
}

function setSocialContextStatusText(text, mode = "idle") {
  const el = document.getElementById("social_context_status");
  if (!el) {
    return;
  }
  el.textContent = text || "No people";
  el.className = `status-pill is-${mode || "idle"}`;
}

function renderSocialGroups(groups) {
  const list = document.getElementById("social_group_list");
  if (!list) {
    return;
  }
  list.replaceChildren();
  if (!groups || groups.length === 0) {
    list.appendChild(socialEmptyState("No groups", "social-group-empty"));
    return;
  }
  groups.forEach((group, index) => {
    const members = Array.isArray(group.members) ? group.members : [];
    const item = document.createElement("div");
    item.className = "social-context-item";
    item.dataset.testid = `social-group-${index + 1}`;
    const head = document.createElement("div");
    head.className = "social-context-item-head";
    const title = document.createElement("span");
    title.textContent = members.length >= 2 ? `Group ${index + 1}` : `Singleton ${index + 1}`;
    const size = document.createElement("span");
    size.className = "social-context-token";
    size.dataset.testid = `social-group-${index + 1}-size`;
    size.textContent = `size ${members.length}`;
    head.append(title, size);
    const tokenRow = document.createElement("div");
    tokenRow.className = "social-context-token-row";
    members.forEach((id) => tokenRow.appendChild(socialToken(`ID ${id}`)));
    item.append(head, tokenRow);
    list.appendChild(item);
  });
}

function renderSocialPeople(people) {
  const list = document.getElementById("social_person_list");
  if (!list) {
    return;
  }
  list.replaceChildren();
  if (!people || people.length === 0) {
    list.appendChild(socialEmptyState("No tracked people", "social-person-empty"));
    return;
  }
  people.forEach((person) => {
    const item = document.createElement("div");
    item.className = "social-context-item";
    item.dataset.testid = `social-person-${person.id}`;
    const head = document.createElement("div");
    head.className = "social-context-item-head";
    const title = document.createElement("span");
    title.textContent = `Person ${person.id}`;
    const confidence = document.createElement("span");
    confidence.className = "social-context-token";
    confidence.dataset.testid = `social-person-${person.id}-confidence`;
    confidence.textContent = `conf ${formatPercent(person.score || 0)}`;
    head.append(title, confidence);
    const tokenRow = document.createElement("div");
    tokenRow.className = "social-context-token-row";
    const activity = normalizeActivityState(person.activity || person.movementState);
    tokenRow.appendChild(socialToken(`activity ${activityLabel(activity)}`, {
      testId: `social-person-${person.id}-activity`,
      activityState: activity,
    }));
    const movementConfidence = asUnitNumber(person.movementConfidence);
    if (movementConfidence > 0) {
      tokenRow.appendChild(socialToken(`movement ${formatPercent(movementConfidence)}`, {
        testId: `social-person-${person.id}-movement-confidence`,
      }));
    }
    const attention = normalizeAttentionSignal(person.attention, person);
    tokenRow.appendChild(socialToken(`attention ${attentionLabel(attention.state)}`, {
      testId: `social-person-${person.id}-attention`,
      attentionState: attention.state,
    }));
    tokenRow.appendChild(socialToken(`attention ${formatPercent(attention.confidence)}`, {
      testId: `social-person-${person.id}-attention-confidence`,
    }));
    tokenRow.appendChild(socialToken(attention.personVisible ? "person visible" : "person hidden", {
      testId: `social-person-${person.id}-person-visible`,
    }));
    tokenRow.appendChild(socialToken(attention.faceVisible ? "face likely" : "face unclear", {
      testId: `social-person-${person.id}-face-visible`,
    }));
    tokenRow.appendChild(socialToken(attention.frontalCentered ? "centered yes" : "centered no", {
      testId: `social-person-${person.id}-centered`,
    }));
    item.append(head, tokenRow);
    list.appendChild(item);
  });
}

function normalizeActivityState(value) {
  const token = String(value || "unknown").trim().toLowerCase();
  return ["stationary", "moving", "approaching", "receding", "attending", "not_attending"].includes(token)
    ? token
    : "unknown";
}

function activityLabel(value) {
  return normalizeActivityState(value).replace(/_/g, " ");
}

function normalizeAttentionSignal(raw, fallback = {}) {
  const source = raw && typeof raw === "object" ? raw : {};
  const state = normalizeAttentionState(source.state || fallback.attentionState);
  return {
    state,
    confidence: asUnitNumber(source.confidence),
    personVisible: source.personVisible === true,
    faceVisible: source.faceVisible === true,
    nearFrontal: source.nearFrontal === true,
    centered: source.centered === true,
    frontalCentered: source.frontalCentered === true,
  };
}

function normalizeAttentionState(value) {
  const token = String(value || "unknown").trim().toLowerCase();
  return ["attending", "not_attending"].includes(token) ? token : "unknown";
}

function attentionLabel(value) {
  return normalizeAttentionState(value).replace(/_/g, " ");
}

function socialToken(text, options = {}) {
  const token = document.createElement("span");
  token.className = "social-context-token";
  if (options.testId) {
    token.dataset.testid = options.testId;
  }
  if (options.activityState) {
    token.dataset.activityState = options.activityState;
  }
  if (options.attentionState) {
    token.dataset.attentionState = options.attentionState;
  }
  token.textContent = text;
  return token;
}

function socialEmptyState(text, testId) {
  const el = document.createElement("div");
  el.className = "social-context-empty";
  el.dataset.testid = testId;
  el.textContent = text;
  return el;
}

async function maybeEmitSocial(social, tracked) {
  if (!document.getElementById("sensor_emit_enabled").checked || !social || !passesSensorEmitInterval("social")) {
    return;
  }
  await submitSocialPayloads(social, tracked, "visual.social");
}

async function submitSocialSample(kind) {
  if (!setManualSocialPreset(kind)) {
    return;
  }
  await submitManualSocialDetails();
}

async function submitSocialPayloads(social, tracked, source) {
  const people = Array.isArray(tracked) ? tracked : [];
  const groups = Array.isArray(social.groups) ? social.groups : [];
  const presencePayload = {
    source,
    humanCount: social.humanCount,
    trackedCount: people.length,
    trackedIds: people.map((p) => p.id),
    avgDetectionConfidence: round(average(people.map((p) => p.score || 1)), 3),
    ts: new Date().toISOString(),
  };
  const groupingPayload = {
    source,
    humanCount: social.humanCount,
    groupCount: social.groupCount,
    singletonCount: social.singletonCount,
    largestGroupSize: social.largestGroupSize,
    groupSizes: groups.map((g) => g.members.length),
    groups: groups.map((g) => ({ memberIds: g.members })),
    ts: new Date().toISOString(),
  };
  const socialContextSupported = currentProfileSupportsObservation("obs.social.context");
  const contextPayload = socialContextPayload(social, people, source);
  const presenceSignature = `${presencePayload.humanCount}|${presencePayload.trackedCount}`;
  const groupingSignature = `${groupingPayload.groupCount}|${groupingPayload.singletonCount}|${groupingPayload.largestGroupSize}|${groupingPayload.groupSizes.join(",")}`;
  const contextSignature = socialContextSignature(contextPayload);
  const emitPresence = presenceSignature !== camera.lastPresenceSignature;
  const emitGrouping = groupingSignature !== camera.lastGroupingSignature;
  const emitContext = socialContextSupported && contextSignature !== camera.lastSocialContextSignature;
  if (!emitPresence && !emitGrouping && !emitContext) {
    appendLog("social", "duplicate social sample skipped.");
    return;
  }
  if (emitPresence) {
    await acknowledgeEvent({
      type: "obs.human.presence",
      actor: "user",
      kind: "observation",
      payload: JSON.stringify(presencePayload),
    }, { renderResponse: false });
    camera.lastPresenceSignature = presenceSignature;
  }
  if (emitGrouping) {
    await acknowledgeEvent({
      type: "obs.social.grouping",
      actor: "user",
      kind: "observation",
      payload: JSON.stringify(groupingPayload),
    }, { renderResponse: !emitContext });
    camera.lastGroupingSignature = groupingSignature;
  }
  if (emitContext) {
    await acknowledgeEvent({
      type: "obs.social.context",
      actor: "user",
      kind: "observation",
      payload: JSON.stringify(contextPayload),
    }, { renderResponse: true });
    camera.lastSocialContextSignature = contextSignature;
  }
  markSensorEmitted("social");
}

function socialContextPayload(social, tracked, source) {
  const groups = Array.isArray(social.groups) ? social.groups : [];
  const people = Array.isArray(tracked) ? tracked : [];
  return {
    schemaVersion: 1,
    source,
    humanCount: Number(social.humanCount || 0),
    groupCount: Number(social.groupCount || 0),
    singletonCount: Number(social.singletonCount || 0),
    largestGroupSize: Number(social.largestGroupSize || 0),
    groupSizes: groups.map((group) => Array.isArray(group.members) ? group.members.length : 0),
    groups: groups.map((group) => {
      const members = Array.isArray(group.members) ? group.members : [];
      return { memberIds: members, size: members.length };
    }),
    people: people.map(socialContextPerson),
    ts: new Date().toISOString(),
  };
}

function socialContextPerson(person) {
  const attention = normalizeAttentionSignal(person.attention, person);
  return {
    id: person.id,
    detectionConfidence: round(asUnitNumber(person.score || 0), 3),
    movement: {
      state: normalizeMovementState(person.movementState || person.activity),
      confidence: round(asUnitNumber(person.movementConfidence), 3),
    },
    attention: {
      state: attention.state,
      confidence: round(attention.confidence, 3),
      personVisible: attention.personVisible,
      faceVisible: attention.faceVisible,
      nearFrontal: attention.nearFrontal,
      centered: attention.centered,
      frontalCentered: attention.frontalCentered,
    },
  };
}

function normalizeMovementState(value) {
  const token = String(value || "unknown").trim().toLowerCase();
  return ["stationary", "moving", "approaching", "receding"].includes(token) ? token : "unknown";
}

function socialContextSignature(payload) {
  return JSON.stringify({
    humanCount: payload.humanCount,
    groupCount: payload.groupCount,
    singletonCount: payload.singletonCount,
    largestGroupSize: payload.largestGroupSize,
    groupSizes: payload.groupSizes,
    groups: payload.groups.map((group) => group.memberIds),
    people: payload.people.map((person) => ({
      id: person.id,
      detectionConfidence: person.detectionConfidence,
      movement: person.movement,
      attention: person.attention,
    })),
  });
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
    source: "valerian.hand.camera",
    detectionMode: "client_camera",
    confidence: round(candidate.confidence, 3),
    cannedGesture: candidate.cannedGesture,
    stabilityFrames: camera.stableGestureCount,
  });
  if (ok) {
    camera.lastCameraEmitKey = emitKey;
    markSensorEmitted("hand");
    renderHandSignReport(candidate.sign, {
      source: "valerian.hand.camera",
      detectionMode: "client_camera",
      confidence: candidate.confidence,
      cannedGesture: candidate.cannedGesture,
      stabilityFrames: camera.stableGestureCount,
      statusText: `Emitted ${new Date().toLocaleTimeString()}`,
      statusMode: "live",
    });
  }
}

async function submitHandSign(sign, options = {}) {
  const normalized = normalizeSign(sign);
  if (!normalized) {
    appendLog("hand", "unknown hand sign.");
    return false;
  }
  renderUserSign(normalized);
  renderHandSignReport(normalized, {
    source: options.source || "valerian.hand.manual",
    detectionMode: options.detectionMode || "manual",
    confidence: typeof options.confidence === "number" ? options.confidence : 1.0,
    cannedGesture: options.cannedGesture,
    stabilityFrames: options.stabilityFrames,
    statusText: "Sending",
    statusMode: "idle",
  });
  const payload = {
    source: options.source || "valerian.hand.manual",
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
  if (data) {
    renderHandSignReport(normalized, {
      source: payload.source,
      detectionMode: payload.detectionMode,
      confidence: payload.confidence,
      cannedGesture: payload.cannedGesture,
      stabilityFrames: payload.stabilityFrames,
      statusText: `Emitted ${new Date().toLocaleTimeString()}`,
      statusMode: "live",
    });
  } else {
    setHandSignStatus("Emit failed", "error");
  }
  return !!data;
}

async function fetchWeatherCurrent() {
  try {
    const payloads = await loadWeatherPayloads();
    weather.current = payloads.current;
    weather.forecast = payloads.forecast;
    weather.locationQuery = weatherLocationQuery();
    renderWeatherPayload(weather.current);
    setWeatherReportStatus("Fetched current", "live");
    appendLog("weather", "current weather fetched.");
    return true;
  } catch (error) {
    renderWeatherStatus("Weather unavailable");
    appendLog("weather", errorMessage(error));
    return false;
  }
}

async function sendWeatherCurrent() {
  if (!state.agentId) {
    appendLog("weather", "send skipped: no agent.");
    return false;
  }
  if (!weather.current || weather.locationQuery !== weatherLocationQuery()) {
    const fetched = await fetchWeatherCurrent();
    if (!fetched) {
      return false;
    }
  }
  const data = await acknowledgeEvent({
    type: "obs.weather.current",
    actor: "system",
    kind: "observation",
    payload: JSON.stringify(weather.current),
  }, { renderResponse: false });
  if (data) {
    setWeatherReportStatus(`Sent current ${new Date().toLocaleTimeString()}`, "live");
    appendLog("weather", "current weather sent.");
  }
  return !!data;
}

async function sendWeatherForecast() {
  if (!state.agentId) {
    appendLog("weather", "send skipped: no agent.");
    return false;
  }
  if (!weather.forecast || weather.locationQuery !== weatherLocationQuery()) {
    try {
      const payloads = await loadWeatherPayloads();
      weather.current = payloads.current;
      weather.forecast = payloads.forecast;
      weather.locationQuery = weatherLocationQuery();
      renderWeatherPayload(weather.forecast);
      setWeatherReportStatus("Fetched forecast", "live");
    } catch (error) {
      renderWeatherStatus("Weather unavailable");
      appendLog("weather", errorMessage(error));
      return false;
    }
  }
  const data = await acknowledgeEvent({
    type: "obs.weather.forecast",
    actor: "system",
    kind: "observation",
    payload: JSON.stringify(weather.forecast),
  }, { renderResponse: false });
  if (data) {
    setWeatherReportStatus(`Sent forecast ${new Date().toLocaleTimeString()}`, "live");
    appendLog("weather", "weather forecast sent.");
  }
  return !!data;
}

async function loadWeatherPayloads() {
  const query = weatherLocationQuery();
  if (!query) {
    throw new Error("weather location required.");
  }
  const location = await resolveWeatherLocation(query);
  const forecast = await fetchOpenMeteoForecast(location);
  return normalizeOpenMeteoWeather(query, location, forecast);
}

function weatherLocationQuery() {
  const input = document.getElementById("weather_location_input");
  return input && input.value ? input.value.trim() : "";
}

async function resolveWeatherLocation(query) {
  const params = new URLSearchParams({
    name: query,
    count: "1",
    language: "de",
    format: "json",
  });
  const data = await fetchJson(`${OPEN_METEO_GEOCODING_URL}?${params.toString()}`);
  const result = data && Array.isArray(data.results) ? data.results[0] : null;
  if (!result || typeof result.latitude !== "number" || typeof result.longitude !== "number") {
    throw new Error("weather location not found.");
  }
  const name = result.name || query;
  const country = result.country || "";
  return {
    name,
    country,
    label: country ? `${name}, ${country}` : name,
    latitude: result.latitude,
    longitude: result.longitude,
    timezone: result.timezone || "auto",
  };
}

async function fetchOpenMeteoForecast(location) {
  const params = new URLSearchParams({
    latitude: String(location.latitude),
    longitude: String(location.longitude),
    current: [
      "weather_code",
      "temperature_2m",
      "precipitation",
      "rain",
      "showers",
      "snowfall",
      "cloud_cover",
      "wind_speed_10m",
      "wind_gusts_10m",
      "is_day",
    ].join(","),
    daily: [
      "weather_code",
      "temperature_2m_max",
      "temperature_2m_min",
      "precipitation_sum",
      "rain_sum",
      "showers_sum",
      "snowfall_sum",
      "wind_speed_10m_max",
      "wind_gusts_10m_max",
    ].join(","),
    forecast_days: "3",
    timezone: location.timezone || "auto",
  });
  return await fetchJson(`${OPEN_METEO_FORECAST_URL}?${params.toString()}`);
}

async function fetchJson(url) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 7000);
  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`weather service returned ${response.status}.`);
    }
    return await response.json();
  } finally {
    clearTimeout(timeout);
  }
}

function normalizeOpenMeteoWeather(query, location, data) {
  const current = data && data.current ? data.current : {};
  const currentCondition = weatherCondition(
    current.weather_code,
    current.precipitation,
    current.rain,
    current.showers,
    current.snowfall);
  const currentPrecipitation = maxNumber(current.precipitation, Number(current.rain || 0) + Number(current.showers || 0),
    current.snowfall);
  const base = weatherLocationPayload(query, location);
  return {
    current: {
      ...base,
      source: "open-meteo.client",
      kind: "current",
      condition: currentCondition,
      intensity: weatherIntensity(currentCondition, currentPrecipitation, false),
      wind: weatherWind(current.wind_speed_10m, current.wind_gusts_10m),
      is_day: Number(current.is_day || 0) === 1,
      cloud_cover: Math.round(Number(current.cloud_cover || 0)),
      temperature_c: round(current.temperature_2m, 1),
      precipitation_mm: round(currentPrecipitation, 2),
      weather_code: Number(current.weather_code || 0),
      observed_at: current.time || new Date().toISOString(),
      ts: new Date().toISOString(),
    },
    forecast: {
      ...base,
      source: "open-meteo.client",
      kind: "forecast",
      days: normalizeForecastDays(data && data.daily),
      ts: new Date().toISOString(),
    },
  };
}

function weatherLocationPayload(query, location) {
  return {
    location_query: query,
    location_name: location.name,
    country: location.country,
    location_label: location.label,
    latitude: round(location.latitude, 5),
    longitude: round(location.longitude, 5),
    timezone: location.timezone,
  };
}

function normalizeForecastDays(daily) {
  if (!daily || !Array.isArray(daily.time)) {
    return [];
  }
  return daily.time.slice(0, 3).map((date, index) => {
    const precipitation = maxNumber(
      arrayNumber(daily.precipitation_sum, index),
      arrayNumber(daily.rain_sum, index) + arrayNumber(daily.showers_sum, index),
      arrayNumber(daily.snowfall_sum, index));
    const condition = weatherCondition(
      arrayNumber(daily.weather_code, index),
      precipitation,
      arrayNumber(daily.rain_sum, index),
      arrayNumber(daily.showers_sum, index),
      arrayNumber(daily.snowfall_sum, index));
    return {
      date,
      condition,
      intensity: weatherIntensity(condition, precipitation, true),
      wind: weatherWind(arrayNumber(daily.wind_speed_10m_max, index), arrayNumber(daily.wind_gusts_10m_max, index)),
      temperature_min_c: round(arrayNumber(daily.temperature_2m_min, index), 1),
      temperature_max_c: round(arrayNumber(daily.temperature_2m_max, index), 1),
      precipitation_mm: round(precipitation, 2),
      weather_code: arrayNumber(daily.weather_code, index),
    };
  });
}

function weatherCondition(codeValue, precipitationValue, rainValue, showersValue, snowfallValue) {
  const code = Number(codeValue || 0);
  const rain = Number(rainValue || 0) + Number(showersValue || 0);
  const snowfall = Number(snowfallValue || 0);
  if (snowfall > 0 || [71, 73, 75, 77, 85, 86].includes(code)) {
    return "snow";
  }
  if ([95, 96, 99].includes(code)) {
    return "storm";
  }
  if (Number(precipitationValue || 0) > 0 || rain > 0
    || [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)) {
    return "rain";
  }
  if ([45, 48].includes(code)) {
    return "fog";
  }
  if ([2, 3].includes(code)) {
    return "cloudy";
  }
  return "clear";
}

function weatherIntensity(condition, precipitation, daily) {
  if (!["rain", "snow", "storm"].includes(condition)) {
    return "none";
  }
  const mediumThreshold = daily ? 2 : 1;
  const heavyThreshold = daily ? 10 : 4;
  if (precipitation >= heavyThreshold) {
    return "heavy";
  }
  if (precipitation >= mediumThreshold) {
    return "medium";
  }
  return "light";
}

function weatherWind(speed, gusts) {
  return maxNumber(speed, gusts) >= 25 ? "windy" : "calm";
}

function maxNumber(...values) {
  return Math.max(...values.map((value) => Number(value || 0)));
}

function arrayNumber(values, index) {
  return Array.isArray(values) ? Number(values[index] || 0) : 0;
}

function renderWeatherFromPayload(type, payload) {
  if (!payload) {
    return;
  }
  try {
    renderWeatherPayload(JSON.parse(payload));
  } catch (_) {
    renderWeatherStatus(type);
  }
}

function renderWeatherPayload(payload) {
  if (!payload || typeof payload !== "object") {
    renderWeatherStatus("-");
    resetWeatherReport();
    return;
  }
  renderWeatherReport(payload);
  if (payload.kind === "forecast") {
    const first = Array.isArray(payload.days) ? payload.days[0] : null;
    const location = payload.location_label || "Weather";
    renderWeatherStatus(first
      ? `Forecast ${location}: ${first.condition}, ${first.temperature_min_c}-${first.temperature_max_c} C`
      : `Forecast ${location}`);
    return;
  }
  renderWeatherStatus(`${payload.location_label || "Weather"}: ${payload.condition}, ${payload.temperature_c} C`);
}

function renderWeatherStatus(value) {
  setText("weather_value", value || "-");
}

function renderWeatherReport(payload) {
  const kind = payload.kind === "forecast" ? "forecast" : "current";
  const location = payload.location_label || payload.location_name || payload.location_query || "-";
  setText("weather_report_location", location);
  if (kind === "forecast") {
    const days = Array.isArray(payload.days) ? payload.days : [];
    const first = days[0] || {};
    setText("weather_report_condition", weatherConditionLabel(first.condition));
    setText("weather_report_temperature", weatherDayTemperature(first));
    setText("weather_report_precipitation", weatherPrecipitationLabel(first));
    setText("weather_report_wind", weatherWindLabel(first.wind));
    setText("weather_report_light", "Forecast");
    renderWeatherForecastStrip(days);
    setWeatherReportStatus("Forecast", "live");
    return;
  }
  setText("weather_report_condition", weatherConditionLabel(payload.condition));
  setText("weather_report_temperature", typeof payload.temperature_c === "number" ? `${payload.temperature_c} C` : "-");
  setText("weather_report_precipitation", weatherPrecipitationLabel(payload));
  setText("weather_report_wind", weatherWindLabel(payload.wind));
  setText("weather_report_light", payload.is_day === true ? "Day" : payload.is_day === false ? "Night" : "-");
  renderWeatherForecastStrip(weather.forecast && Array.isArray(weather.forecast.days) ? weather.forecast.days : []);
  setWeatherReportStatus("Current", "live");
}

function resetWeatherReport() {
  setText("weather_report_location", "-");
  setText("weather_report_condition", "-");
  setText("weather_report_temperature", "-");
  setText("weather_report_precipitation", "-");
  setText("weather_report_wind", "-");
  setText("weather_report_light", "-");
  renderWeatherForecastStrip([]);
  setWeatherReportStatus("No weather", "idle");
}

function setWeatherReportStatus(text, mode = "idle") {
  const el = document.getElementById("weather_report_status");
  if (!el) {
    return;
  }
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
}

function renderWeatherForecastStrip(days) {
  const list = document.getElementById("weather_forecast_strip");
  if (!list) {
    return;
  }
  list.replaceChildren();
  const visibleDays = Array.isArray(days) ? days.slice(0, 3) : [];
  if (visibleDays.length === 0) {
    list.appendChild(socialEmptyState("No forecast", "weather-forecast-empty"));
    return;
  }
  visibleDays.forEach((day, index) => {
    const card = document.createElement("div");
    card.className = "weather-day-card";
    card.dataset.testid = `weather-forecast-day-${index + 1}`;
    const date = document.createElement("strong");
    date.dataset.testid = `weather-forecast-day-${index + 1}-date`;
    date.textContent = shortWeatherDate(day.date);
    const condition = document.createElement("span");
    condition.dataset.testid = `weather-forecast-day-${index + 1}-condition`;
    condition.textContent = weatherConditionLabel(day.condition);
    const temp = document.createElement("span");
    temp.dataset.testid = `weather-forecast-day-${index + 1}-temperature`;
    temp.textContent = weatherDayTemperature(day);
    card.append(date, condition, temp);
    list.appendChild(card);
  });
}

function weatherConditionLabel(condition) {
  const token = String(condition || "").trim().toLowerCase();
  const labels = {
    clear: "Clear",
    cloudy: "Cloudy",
    fog: "Fog",
    rain: "Rain",
    snow: "Snow",
    storm: "Storm",
  };
  return labels[token] || (token ? token : "-");
}

function weatherWindLabel(wind) {
  const token = String(wind || "").trim().toLowerCase();
  if (token === "windy") {
    return "Windy";
  }
  if (token === "calm") {
    return "Calm";
  }
  return token || "-";
}

function weatherPrecipitationLabel(payload) {
  const intensity = payload && payload.intensity ? String(payload.intensity) : "";
  const amount = typeof payload?.precipitation_mm === "number" ? `${payload.precipitation_mm} mm` : "";
  if (intensity && intensity !== "none" && amount) {
    return `${intensity} (${amount})`;
  }
  return amount || (intensity ? intensity : "-");
}

function weatherDayTemperature(day) {
  if (typeof day?.temperature_min_c === "number" || typeof day?.temperature_max_c === "number") {
    const min = typeof day.temperature_min_c === "number" ? day.temperature_min_c : "?";
    const max = typeof day.temperature_max_c === "number" ? day.temperature_max_c : "?";
    return `${min}-${max} C`;
  }
  return "-";
}

function shortWeatherDate(value) {
  const token = String(value || "").trim();
  return token.length > 5 ? token.slice(5) : token || "-";
}

function resetWeatherState() {
  weather.current = null;
  weather.forecast = null;
  weather.locationQuery = "";
  renderWeatherStatus("-");
  resetWeatherReport();
}

function drawFaceBox(box, emotion, faceScore) {
  if (!box) {
    return;
  }
  const scale = overlayScale();
  const displayBox = mirroredOverlayBox(box.x, box.y, box.width, box.height, scale);
  camera.ctx.lineWidth = 3;
  camera.ctx.strokeStyle = "#ff7a00";
  camera.ctx.strokeRect(displayBox.x, displayBox.y, displayBox.width, displayBox.height);
  const label = emotion
    ? `Face ${asText(emotion.emotion)} ${asUnitNumber(faceScore).toFixed(2)}`
    : "Face";
  drawOverlayLabel(displayBox.x, Math.max(8, displayBox.y - 24), label, "#ff7a00");
}

function drawFaceStatus(text, mode = "idle") {
  if (!text || !camera.ctx) {
    return;
  }
  overlayScale();
  drawOverlayLabel(8, 8, text, mode === "error" ? "#dc2626" : "#ff7a00");
}

function drawOverlayLabel(x, y, text, color) {
  if (!camera.ctx || !text) {
    return;
  }
  const paddingX = 7;
  const height = 22;
  camera.ctx.save();
  camera.ctx.font = "600 12px system-ui, -apple-system, BlinkMacSystemFont, sans-serif";
  const width = Math.ceil(camera.ctx.measureText(text).width + paddingX * 2);
  const clampedX = clamp(Number(x || 0), 4, Math.max(4, camera.canvas.width - width - 4));
  const clampedY = clamp(Number(y || 0), 4, Math.max(4, camera.canvas.height - height - 4));
  camera.ctx.fillStyle = color || "#111827";
  camera.ctx.fillRect(clampedX, clampedY, width, height);
  camera.ctx.fillStyle = "#ffffff";
  camera.ctx.fillText(text, clampedX + paddingX, clampedY + 15);
  camera.ctx.restore();
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
    drawOverlayLabel(displayBox.x, Math.max(8, displayBox.y - 24),
      `Person ${person.id} ${asUnitNumber(person.score).toFixed(2)}`, camera.ctx.strokeStyle);
  }
}

function drawSocialStatus(text, mode = "idle") {
  if (!text || !camera.ctx) {
    return;
  }
  overlayScale();
  drawOverlayLabel(8, 36, text, mode === "error" ? "#dc2626" : "#059669");
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
    resetEmotionReport();
    camera.lastEmotion = null;
    camera.lastEmotionEmitAt = 0;
  }
  if (!isSensorModeEnabled("social")) {
    renderSocialMetrics(null, []);
    camera.tracks.clear();
    camera.lastPresenceSignature = null;
    camera.lastGroupingSignature = null;
    camera.lastSocialContextSignature = null;
    camera.lastSocialEmitAt = 0;
  }
  if (!isSensorModeEnabled("hand")) {
    setText("hand_sign_value", "-");
    resetHandSignReport("Detector off");
    camera.stableGestureKey = null;
    camera.stableGestureCount = 0;
    camera.lastCameraEmitKey = null;
    camera.lastCameraEmitAt = 0;
  }
}

function renderSpeechSensingTranscript(transcript) {
  const text = typeof transcript === "string" ? transcript.trim() : "";
  const value = text || "-";
  setText("continuous_speech_sensing_value", value);
}

function resetSpeechSensingPanel() {
  renderSpeechSensingTranscript("");
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
      renderHandSignReport(sign, {
        source: parsed.source,
        detectionMode: parsed.detectionMode,
        confidence: parsed.confidence,
        cannedGesture: parsed.cannedGesture,
        stabilityFrames: parsed.stabilityFrames,
        statusText: "From history",
        statusMode: "idle",
      });
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
  setText(`${prefix}_sign_label`, ui ? ui.label : "-");
  setText(`${prefix}_sign_visual`, ui ? ui.symbol : "-");
}

function renderHandSignReport(sign, options = {}) {
  const normalized = normalizeSign(sign);
  const ui = normalized ? SIGNS[normalized] : null;
  if (!ui) {
    resetHandSignReport("No sign");
    return;
  }
  const confidence = asUnitNumber(options.confidence ?? 1);
  setText("hand_report_visual", ui.symbol);
  setText("hand_report_label", ui.label);
  setText("hand_report_confidence", formatPercent(confidence));
  setSignalMeter("hand_report_confidence_meter", confidence);
  setText("hand_report_source", sourceLabel(options.source));
  setText("hand_report_mode", modeLabel(options.detectionMode));
  setText("hand_report_canned", options.cannedGesture || "-");
  setText("hand_report_stability", options.stabilityFrames ? `${options.stabilityFrames} frames` : "-");
  setHandSignStatus(options.statusText || "Live", options.statusMode || "live");
}

function resetHandSignReport(statusText = "No sign") {
  setText("hand_report_visual", "-");
  setText("hand_report_label", "-");
  setText("hand_report_confidence", "0%");
  setSignalMeter("hand_report_confidence_meter", 0);
  setText("hand_report_source", "-");
  setText("hand_report_mode", "-");
  setText("hand_report_canned", "-");
  setText("hand_report_stability", "-");
  setHandSignStatus(statusText, "idle");
}

function setHandSignStatus(text, mode = "idle") {
  const el = document.getElementById("hand_sign_status");
  if (!el) {
    return;
  }
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
}

function sourceLabel(value) {
  const token = String(value || "").trim();
  if (!token) {
    return "-";
  }
  if (token.includes(".manual")) {
    return "Manual";
  }
  if (token.includes(".camera")) {
    return "Camera";
  }
  if (token.includes("open-meteo")) {
    return "Open-Meteo";
  }
  return token;
}

function modeLabel(value) {
  const token = String(value || "").trim();
  return token ? token.replace(/_/g, " ") : "-";
}

function setSignalMeter(id, unitValue) {
  const el = document.getElementById(id);
  if (!el) {
    return;
  }
  const value = asUnitNumber(unitValue);
  el.style.width = formatPercent(value);
  el.setAttribute("aria-valuenow", String(Math.round(value * 100)));
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
  BEHAVIOUR_CHANNELS.forEach((channel) => setBehaviourChannelActive(channel, false));
  setText("speech_preview", "-");
  setGestureVisual("NONE");
  setText("face_value", "-");
  setText("face_intensity_value", "0%");
  setBehaviourMeter("face_intensity_meter", 0);
  setText("gaze_value", "-");
  setText("gaze_focus_value", "Focus -");
  setText("motion_value", "-");
  setText("motion_energy_value", "0%");
  setBehaviourMeter("motion_energy_meter", 0);
  setText("motion_stillness_value", "0%");
  setBehaviourMeter("motion_stillness_meter", 0);
  renderSign("agent", null);
  renderSign("user", null);
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
    "continuous_speech_tab",
  ]);
  document.querySelectorAll("button, textarea, select, input").forEach((el) => {
    if (alwaysEnabled.has(el.id) || el.hasAttribute("data-theme-toggle") || el.classList.contains("btn-close") ||
      el.hasAttribute("data-column-maximize") || el.dataset.bsDismiss === "offcanvas" ||
      el.dataset.bsToggle === "collapse") {
      return;
    }
    el.disabled = !enabled;
  });
  document.getElementById("start_camera").disabled = !enabled || state.cameraRunning || controlOwnedByOther("camera");
  document.getElementById("stop_camera").disabled = !enabled || !state.cameraRunning || controlOwnedByOther("camera");
  updateCameraDeviceControls();
  updateCameraOwnershipControls();
  setTranscriptionControlsLocked(state.transcriptionListening);
  updateAgentTypeControls();
  updateAgentSelectionControls();
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

function setTranscriptionState(isListening) {
  state.transcriptionListening = isListening;
  const transcriptionButton = document.getElementById("toggle_transcription");
  const listenStatus = document.getElementById("listen_status");
  const status = document.getElementById("transcription_status");
  if (isListening) {
    transcriptionButton.innerHTML = '<i class="bi bi-mic-mute-fill me-2"></i>Stop Transcription';
    transcriptionButton.classList.add("is-listening");
    transcriptionButton.disabled = false;
    listenStatus.textContent = "Listening";
    listenStatus.className = "status-pill is-listening";
    status.textContent = "Transcription Live";
    status.className = "status-pill is-live";
  } else {
    transcriptionButton.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Transcription';
    transcriptionButton.classList.remove("is-listening");
    transcriptionButton.disabled = false;
    listenStatus.textContent = "Idle";
    listenStatus.className = "status-pill is-idle";
    status.textContent = "Transcription Idle";
    status.className = "status-pill is-idle";
  }
  setTranscriptionControlsLocked(isListening);
}

function setTranscriptionControlsLocked(locked) {
  const remote = controlOwnedByOther("microphone");
  const connected = !!state.agentId;
  const outputActive = !!speechPlayback.coordinator?.snapshot().current;
  speechOutputSettingControls().forEach((element) => {
    element.disabled = outputActive;
  });
  const outputSelect = document.getElementById("speech_output_device_select");
  if (outputSelect) {
    outputSelect.disabled = outputActive || !speechAudioSelectionSupported() || !speechOutputSelectionSupported();
  }
  const refreshButton = document.getElementById("refresh_audio_devices");
  if (refreshButton) {
    refreshButton.disabled = outputActive || !speechAudioSelectionSupported();
  }
  const toggle = document.getElementById("toggle_transcription");
  if (toggle && !state.transcriptionListening) {
    toggle.disabled = remote || !connected || outputActive;
  }
  const speechStatus = document.getElementById("speech_device_status");
  if (remote && !state.transcriptionListening) {
    setTranscriptionGlobalStatus("Mic In Use", "idle");
    setTranscriptionTransportStatus("Mic In Use", "idle", "Microphone is active in another Valerian window.");
    setSpeechDeviceStatus("Microphone is active in another Valerian window.", "error");
  } else if (!remote && !state.transcriptionListening && document.getElementById("transcription_status").textContent === "Mic In Use") {
    setTranscriptionGlobalStatus("Transcription Idle", "idle");
    setTranscriptionTransportStatus("Transport Idle", "idle", "");
    if (speechStatus && speechStatus.textContent.includes("another Valerian window")) {
      setSpeechDeviceStatus("Audio devices use browser defaults.");
    }
  }
}

function setCameraStatus(text, mode) {
  const el = document.getElementById("camera_status");
  el.textContent = text;
  el.className = `status-pill is-${mode || "idle"}`;
}

function cleanupAll() {
  state.isPageUnloading = true;
  void stopSpeechPlayback("page_unload", { reset: true, silent: true });
  releaseAllControlOwnership();
  cleanupStreams();
  stopCamera({ silent: true });
  if (state.transcriptionListening) {
    stopTranscription();
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

function normalizeGestureToken(value) {
  const token = String(value || "")
    .trim()
    .toUpperCase()
    .replace(/\s+/g, "_")
    .replace(/-/g, "_");
  return GESTURE_UI[token] ? token : "NONE";
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

function asUnitNumber(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return 0;
  }
  return clamp(parsed, 0, 1);
}

function formatPercent(unitValue) {
  return `${Math.round(asUnitNumber(unitValue) * 100)}%`;
}

function formatSignedDecimal(value) {
  const rounded = round(Number(value || 0), 2);
  return `${rounded > 0 ? "+" : ""}${rounded.toFixed(2)}`;
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

function setBehaviourChannelActive(channel, active) {
  const el = document.getElementById(`behaviour_chip_${channel}`);
  if (!el) {
    return;
  }
  el.classList.toggle("is-active", !!active);
  el.classList.toggle("is-inactive", !active);
}

function setBehaviourMeter(id, unitValue) {
  const el = document.getElementById(id);
  if (!el) {
    return;
  }
  const percent = formatPercent(unitValue);
  el.style.width = percent;
  el.setAttribute("aria-valuenow", String(Math.round(asUnitNumber(unitValue) * 100)));
  el.title = percent;
}

function setGestureVisual(value) {
  const token = normalizeGestureToken(value);
  const ui = GESTURE_UI[token] || GESTURE_UI.NONE;
  const icon = document.getElementById("gesture_icon");
  if (icon) {
    icon.className = `bi ${ui.icon}`;
  }
  setText("gesture_value", ui.label);
  setText("gesture_hint", ui.hint);
  setBehaviourChannelActive("gesture", token !== "NONE");
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
