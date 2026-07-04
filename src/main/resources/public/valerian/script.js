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
  activeMode: null,
  activeTurnDetection: null,
  assistantTranscriptBuffer: "",
  assistantAudioSeen: false,
  assistantAudioActive: false,
  lastAssistantTranscript: "",
  lastAssistantTranscriptAt: 0,
  userSpeechActive: false,
  responseActive: false,
  micRestoreTimer: null,
  micMutedForAssistant: false,
  playbackIssueActive: false,
  lastPlaybackWarningAt: 0,
  statsTimer: null,
  lastAudioStats: null,
  lastStatsWarningAt: 0,
  lastBargeInCancelAt: 0,
  pendingInputItemIds: new Set(),
  processedInputItemIds: new Set(),
  transcriptCandidates: [],
  transcriptFlushTimer: null,
};

const speechDevices = {
  inputDeviceId: "",
  outputDeviceId: "",
  devicesLoaded: false,
};

const speechSettings = {
  bargeInCancelEnabled: true,
  echoGuardEnabled: false,
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

const weather = {
  current: null,
  forecast: null,
  locationQuery: "",
};

const columnExpansion = {
  modal: null,
  active: null,
};

const RECONNECT_MIN_MS = 1000;
const RECONNECT_MAX_MS = 30000;
const RECONNECT_JITTER = 0.2;
const BEHAVIOUR_DUPLICATE_WINDOW_MS = 2500;
const TRANSCRIPT_BATCH_DELAY_MS = 900;
const REALTIME_MODE_CONTINUOUS = "continuous";
const ACTIVITY_LOG_LIMIT = 300;
const ACCESS_CODE_STORAGE_KEY = "prometheus.valerian.accessCode";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const SPEECH_INPUT_DEVICE_STORAGE_KEY = "prometheus.valerian.speechInputDevice";
const SPEECH_OUTPUT_DEVICE_STORAGE_KEY = "prometheus.valerian.speechOutputDevice";
const SPEECH_VOICE_STORAGE_KEY = "prometheus.valerian.speechVoice";
const SPEECH_VAD_MODE_STORAGE_KEY = "prometheus.valerian.speechVadMode";
const SPEECH_COMPLEMENT_STORAGE_KEY = "prometheus.valerian.speechComplement";
const SPEECH_VAD_THRESHOLD_STORAGE_KEY = "prometheus.valerian.speechVadThreshold";
const SPEECH_VAD_PREFIX_PADDING_MS_STORAGE_KEY = "prometheus.valerian.speechVadPrefixPaddingMs";
const SPEECH_VAD_SILENCE_DURATION_MS_STORAGE_KEY = "prometheus.valerian.speechVadSilenceDurationMs";
const SPEECH_VAD_EAGERNESS_STORAGE_KEY = "prometheus.valerian.speechVadEagerness";
const SPEECH_VAD_INTERRUPT_RESPONSE_STORAGE_KEY = "prometheus.valerian.speechVadInterruptResponse";
const SPEECH_INPUT_NOISE_REDUCTION_STORAGE_KEY = "prometheus.valerian.speechInputNoiseReduction";
const SPEECH_OUTPUT_SPEED_STORAGE_KEY = "prometheus.valerian.speechOutputSpeed";
const SPEECH_REASONING_EFFORT_STORAGE_KEY = "prometheus.valerian.speechReasoningEffort";
const SPEECH_MAX_OUTPUT_TOKENS_STORAGE_KEY = "prometheus.valerian.speechMaxOutputTokens";
const SPEECH_TRANSCRIPTION_LOGPROBS_STORAGE_KEY = "prometheus.valerian.speechTranscriptionLogprobs";
const SPEECH_BARGE_IN_CANCEL_STORAGE_KEY = "prometheus.valerian.speechBargeInCancel";
const SPEECH_ECHO_GUARD_STORAGE_KEY = "prometheus.valerian.speechEchoGuard";
const CAMERA_DEVICE_STORAGE_KEY = "prometheus.valerian.cameraDevice";
const THEME_STORAGE_KEY = "prometheus.valerian.theme";
const REALTIME_ICE_FAILURE_MESSAGE = "Realtime WebRTC ICE failed. Stop and restart speech; check network/STUN/TURN if it repeats.";
const REALTIME_CONNECTION_FAILURE_MESSAGE = "Realtime WebRTC connection failed. Stop and restart speech; check network/STUN/TURN if it repeats.";
const REALTIME_ECHO_GUARD_RELEASE_MS = 1200;
const REALTIME_ECHO_GUARD_MAX_MUTE_MS = 30000;
const REALTIME_PLAYBACK_WARNING_COOLDOWN_MS = 3000;
const REALTIME_STATS_POLL_MS = 2000;
const REALTIME_STATS_WARNING_COOLDOWN_MS = 5000;
const REALTIME_BARGE_IN_CANCEL_COOLDOWN_MS = 750;
const REALTIME_ECHO_TRANSCRIPT_MAX_AGE_MS = 45000;
const REALTIME_ECHO_TRANSCRIPT_MIN_CHARS = 18;
const REALTIME_ECHO_TRANSCRIPT_SIMILARITY = 0.78;
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
  surprised: { valence: 0.2, arousal: 0.8 },
};

const EMOTION_EXPRESSION_KEYS = ["neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised"];

const PROFILE_VISUAL_OBSERVATIONS = [
  "obs.emotion.face",
  "obs.human.presence",
  "obs.social.grouping",
  "obs.hand.sign",
];

const PROFILE_WEATHER_OBSERVATIONS = [
  "obs.weather.current",
  "obs.weather.forecast",
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
  applyStoredTheme();
  wireUi();
  loadStoredSpeechDeviceSelection();
  loadStoredSpeechSettings();
  loadStoredCameraDeviceSelection();
  renderSpeechDeviceSelections([], []);
  renderCameraDeviceSelections([]);
  registerAssistantAudioDiagnostics();
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
  document.getElementById("toggle_realtime").addEventListener("click", () => toggleRealtime());
  document.getElementById("diagnostics_drawer").addEventListener("show.bs.offcanvas", showAgentDrawerTab);
  document.getElementById("continuous_speech_tab").addEventListener("shown.bs.tab", () => {
    refreshAudioDevices({ requestPermission: false, silent: true });
  });
  speechSessionSettingControls().forEach((control) => {
    control.addEventListener("change", saveSpeechSessionSettingSelection);
  });
  document.getElementById("speechBargeInCancelToggle").addEventListener("change", saveSpeechBargeInCancelSelection);
  document.getElementById("speechEchoGuardToggle").addEventListener("change", saveSpeechEchoGuardSelection);
  document.getElementById("speech_input_device_select").addEventListener("change", saveSpeechInputDeviceSelection);
  document.getElementById("speech_output_device_select").addEventListener("change", () => {
    saveSpeechOutputDeviceSelection();
  });
  document.getElementById("refresh_audio_devices").addEventListener("click", () => {
    refreshAudioDevices({ requestPermission: true });
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
  resetSpeechSensingPanel();
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
  document.getElementById("agent_subtitle").textContent = "PROMETHEUS demo console";
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

async function toggleRealtime() {
  if (!state.realtimeListening) {
    await startRealtime();
    return;
  }
  await stopRealtime();
}

async function startRealtime() {
  if (!state.agentId) {
    return;
  }
  realtime.activeMode = REALTIME_MODE_CONTINUOUS;
  setRealtimeState(true);
  resetRealtimeTranscriptGate();
  appendLog("realtime", "starting.");
  setRealtimeTransportStatus("Transport Starting", "idle", "");
  try {
    await setupRealtimeConnection();
    await waitForDataChannelOpen();
  } catch (error) {
    appendLog("realtime", "start failed: " + error.message);
    await stopRealtime();
    setRealtimeTransportStatus("Transport Failed", "error", `Realtime start failed: ${error.message}`);
  }
}

async function stopRealtime() {
  const stoppingMode = realtime.activeMode;
  setRealtimeState(false, stoppingMode);
  if (realtime.dataChannel) {
    realtime.dataChannel.close();
    realtime.dataChannel = null;
  }
  if (realtime.peerConnection) {
    realtime.peerConnection.close();
    realtime.peerConnection = null;
  }
  stopRealtimeStatsDiagnostics();
  restoreRealtimeMicrophoneAfterAssistant();
  if (realtime.micStream) {
    realtime.micStream.getTracks().forEach((track) => track.stop());
    realtime.micStream = null;
  }
  if (realtime.callId) {
    closeRealtimeCall(realtime.callId);
    realtime.callId = null;
  }
  realtime.activeMode = null;
  realtime.activeTurnDetection = null;
  const audio = document.getElementById("assistant_audio");
  audio.pause();
  audio.removeAttribute("src");
  audio.srcObject = null;
  audio.load();
  realtime.responseActive = false;
  realtime.assistantAudioSeen = false;
  realtime.assistantAudioActive = false;
  realtime.userSpeechActive = false;
  realtime.playbackIssueActive = false;
  realtime.lastAudioStats = null;
  realtime.lastStatsWarningAt = 0;
  resetRealtimeTranscriptGate();
  setRealtimeTransportStatus("Transport Idle", "idle", "");
  appendLog("realtime", "stopped.");
}

async function setupRealtimeConnection(mode = REALTIME_MODE_CONTINUOUS) {
  const settings = currentRealtimeSettings(mode);
  realtime.activeMode = mode;
  realtime.activeTurnDetection = settings.turnDetection || "server_vad";
  realtime.playbackIssueActive = false;
  realtime.lastPlaybackWarningAt = 0;
  realtime.assistantAudioActive = false;
  realtime.userSpeechActive = false;
  realtime.lastAudioStats = null;
  realtime.lastStatsWarningAt = 0;
  realtime.lastBargeInCancelAt = 0;
  realtime.peerConnection = new RTCPeerConnection();
  wireRealtimePeerDiagnostics(realtime.peerConnection);
  realtime.peerConnection.ontrack = (event) => {
    const audio = activeAssistantAudioElement();
    registerRemoteAudioTrackDiagnostics(event.track);
    audio.srcObject = event.streams[0];
    applySelectedSpeechOutputDevice().finally(() => {
      audio.play().catch(() => {
        appendLog("realtime", "assistant audio autoplay was blocked.");
      });
    });
  };
  realtime.dataChannel = realtime.peerConnection.createDataChannel("oai-events");
  realtime.dataChannel.addEventListener("message", handleRealtimeEvent);
  realtime.dataChannel.addEventListener("close", () => {
    if (state.realtimeListening) {
      setRealtimeTransportStatus("Transport Failed", "error", "Realtime data channel closed unexpectedly. Stop and restart speech.");
    }
  });
  realtime.dataChannel.addEventListener("error", () => {
    if (state.realtimeListening) {
      setRealtimeTransportStatus("Transport Failed", "error", "Realtime data channel failed. Stop and restart speech.");
    }
  });
  realtime.micStream = await navigator.mediaDevices.getUserMedia({
    audio: speechInputConstraints(),
  });
  logActiveSpeechInputSettings(realtime.micStream);
  refreshAudioDevices({ requestPermission: false, silent: true });
  setRealtimeMicrophoneEnabled(true);
  realtime.micStream.getTracks().forEach((track) => realtime.peerConnection.addTrack(track, realtime.micStream));

  const offer = await realtime.peerConnection.createOffer();
  await realtime.peerConnection.setLocalDescription(offer);
  const call = await createRealtimeCall(offer.sdp, settings);
  realtime.callId = call.callId || call.id || null;
  await realtime.peerConnection.setRemoteDescription({ type: "answer", sdp: call.sdp });
  startRealtimeStatsDiagnostics();
  appendLog("realtime", "WebRTC session established.");
}

async function createRealtimeCall(offerSdp, settings = currentRealtimeSettings()) {
  const params = new URLSearchParams();
  appendRealtimeCallParam(params, "voice", settings.voice);
  params.set("turnDetection", settings.turnDetection || "server_vad");
  params.set("generateComplement", String(settings.generateComplement));
  appendRealtimeCallParam(params, "vadThreshold", settings.vadThreshold);
  appendRealtimeCallParam(params, "vadPrefixPaddingMs", settings.vadPrefixPaddingMs);
  appendRealtimeCallParam(params, "vadSilenceDurationMs", settings.vadSilenceDurationMs);
  appendRealtimeCallParam(params, "vadEagerness", settings.vadEagerness);
  appendRealtimeCallParam(params, "vadInterruptResponse", settings.vadInterruptResponse);
  appendRealtimeCallParam(params, "inputNoiseReduction", settings.inputNoiseReduction);
  appendRealtimeCallParam(params, "outputSpeed", settings.outputSpeed);
  appendRealtimeCallParam(params, "reasoningEffort", settings.reasoningEffort);
  appendRealtimeCallParam(params, "maxOutputTokens", settings.maxOutputTokens);
  if (settings.includeInputTranscriptionLogprobs) {
    appendRealtimeCallParam(params, "includeInputTranscriptionLogprobs", true);
  }
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

function appendRealtimeCallParam(params, key, value) {
  if (value === null || value === undefined || value === "") {
    return;
  }
  params.set(key, String(value));
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
    clearRealtimePendingInputItems();
  } else if (data.type === "input_audio_buffer.speech_started") {
    handleRealtimeUserSpeechStarted();
  } else if (data.type === "input_audio_buffer.speech_stopped") {
    handleRealtimeUserSpeechStopped();
  } else if (data.type === "conversation.item.input_audio_transcription.completed") {
    queueRealtimeTranscriptCandidate(data);
  } else if (data.type === "response.created") {
    realtime.responseActive = true;
    realtime.assistantAudioSeen = false;
    markRealtimeAssistantAudioActive();
    realtime.assistantTranscriptBuffer = "";
    muteRealtimeMicrophoneForAssistant();
  } else if (data.type === "response.audio.delta" || data.type === "response.output_audio.delta") {
    realtime.assistantAudioSeen = true;
    markRealtimeAssistantAudioActive();
    muteRealtimeMicrophoneForAssistant();
  } else if (data.type === "response.output_audio_transcript.delta" || data.type === "response.output_text.delta") {
    realtime.assistantAudioSeen = true;
    markRealtimeAssistantAudioActive();
    muteRealtimeMicrophoneForAssistant();
    realtime.assistantTranscriptBuffer += data.delta || "";
    setText("speech_preview", realtime.assistantTranscriptBuffer || "-");
  } else if (data.type === "response.output_audio_transcript.done" || data.type === "response.output_text.done") {
    const transcript = realtime.assistantTranscriptBuffer.trim() ||
      String(data.transcript || data.text || "").trim();
    if (transcript) {
      setText("speech_preview", transcript);
      rememberRealtimeAssistantTranscript(transcript);
    }
    realtime.assistantTranscriptBuffer = "";
    scheduleRealtimeMicrophoneRestoreAfterAssistant();
  } else if (data.type === "response.done" || data.type === "response.audio.done" ||
    data.type === "response.output_audio.done" || data.type === "response.cancelled" ||
    data.type === "response.canceled") {
    realtime.assistantAudioSeen = false;
    realtime.responseActive = false;
    markRealtimeAssistantAudioDone();
    scheduleRealtimeMicrophoneRestoreAfterAssistant();
  }
}

function handleRealtimeUserSpeechStarted() {
  realtime.userSpeechActive = true;
  appendLog("realtime", "user speech started.");
  if (realtime.assistantAudioActive && speechSettings.bargeInCancelEnabled) {
    cancelRealtimeAssistantResponse("User barge-in detected; requested assistant cancellation.");
  } else if (realtime.assistantAudioActive) {
    appendLog("realtime", "user barge-in detected; cancellation is disabled.");
  }
}

function handleRealtimeUserSpeechStopped() {
  realtime.userSpeechActive = false;
  appendLog("realtime", "user speech stopped.");
}

function markRealtimeAssistantAudioActive() {
  realtime.assistantAudioActive = true;
}

function markRealtimeAssistantAudioDone() {
  realtime.assistantAudioActive = false;
}

function cancelRealtimeAssistantResponse(reason) {
  const now = Date.now();
  if (now - realtime.lastBargeInCancelAt < REALTIME_BARGE_IN_CANCEL_COOLDOWN_MS) {
    return false;
  }
  realtime.lastBargeInCancelAt = now;
  const sent = sendRealtimeClientEvent({ type: "response.cancel" }, reason);
  if (sent) {
    realtime.assistantAudioActive = false;
    realtime.assistantTranscriptBuffer = "";
    setText("speech_preview", "-");
  }
  return sent;
}

function sendRealtimeClientEvent(payload, activityMessage = "") {
  const channel = realtime.dataChannel;
  if (!channel || channel.readyState !== "open") {
    appendLog("realtime", `client event not sent; data channel is ${channel ? channel.readyState : "missing"}.`);
    return false;
  }
  try {
    channel.send(JSON.stringify(payload));
    if (activityMessage) {
      appendLog("realtime", activityMessage);
    }
    return true;
  } catch (error) {
    appendLog("realtime", `client event failed: ${errorMessage(error)}`);
    return false;
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
  const hasEchoCandidate = candidates.some((candidate) => isProbableAssistantEcho(candidate.transcript));
  const selected = selectRealtimeTranscriptCandidate(candidates);
  markRealtimeTranscriptItemsProcessed(candidates);
  if (!selected) {
    appendLog("realtime", hasEchoCandidate
      ? "Suppressed probable assistant echo transcript."
      : "ignored noisy or duplicate user transcript.");
    return;
  }
  appendMessage("user", selected.transcript);
  renderSpeechSensingTranscript(selected.transcript);
  appendLog("realtime", "user transcript completed.");
}

function selectRealtimeTranscriptCandidate(candidates) {
  let selected = null;
  for (const candidate of candidates) {
    if (!candidate.transcript.trim() || realtimeTranscriptItemAlreadyProcessed(candidate) ||
      !realtimeTranscriptItemMatchesPendingCommit(candidate) || isLikelyAsrHallucination(candidate.transcript) ||
      isProbableAssistantEcho(candidate.transcript)) {
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
  clearRealtimePendingInputItems();
}

function clearRealtimePendingInputItems() {
  realtime.pendingInputItemIds.clear();
}

function resetRealtimeTranscriptGate() {
  clearQueuedRealtimeTranscriptCandidates();
  realtime.processedInputItemIds = new Set();
}

function rememberRealtimeAssistantTranscript(transcript) {
  const normalized = normalizeTranscriptForGate(transcript);
  if (!normalized) {
    return;
  }
  realtime.lastAssistantTranscript = transcript;
  realtime.lastAssistantTranscriptAt = Date.now();
}

function isProbableAssistantEcho(transcript) {
  const assistant = realtime.lastAssistantTranscript;
  if (!assistant || Date.now() - realtime.lastAssistantTranscriptAt > REALTIME_ECHO_TRANSCRIPT_MAX_AGE_MS) {
    return false;
  }
  const userText = normalizeTranscriptForGate(transcript);
  const assistantText = normalizeTranscriptForGate(assistant);
  if (userText.length < REALTIME_ECHO_TRANSCRIPT_MIN_CHARS ||
    assistantText.length < REALTIME_ECHO_TRANSCRIPT_MIN_CHARS) {
    return false;
  }
  if (userText === assistantText) {
    return true;
  }
  if (userText.includes(assistantText) || assistantText.includes(userText)) {
    return true;
  }
  return transcriptTokenSimilarity(userText, assistantText) >= REALTIME_ECHO_TRANSCRIPT_SIMILARITY;
}

function transcriptTokenSimilarity(left, right) {
  const leftTokens = new Set(left.split(" ").filter((token) => token.length > 2));
  const rightTokens = new Set(right.split(" ").filter((token) => token.length > 2));
  if (!leftTokens.size || !rightTokens.size) {
    return 0;
  }
  let intersection = 0;
  leftTokens.forEach((token) => {
    if (rightTokens.has(token)) {
      intersection += 1;
    }
  });
  return intersection / Math.max(leftTokens.size, rightTokens.size);
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

function speechSessionSettingControls() {
  return [
    "speechVoiceInput",
    "speechVadSelect",
    "speechComplementToggle",
    "speechVadThresholdInput",
    "speechVadPrefixInput",
    "speechVadSilenceInput",
    "speechVadEagernessSelect",
    "speechVadInterruptResponseSelect",
    "speechInputNoiseReductionSelect",
    "speechOutputSpeedInput",
    "speechReasoningEffortSelect",
    "speechMaxOutputTokensInput",
    "speechTranscriptionLogprobsToggle",
  ].map((id) => document.getElementById(id)).filter(Boolean);
}

function speechSettingStorageKey(storageName) {
  return {
    speechVoice: SPEECH_VOICE_STORAGE_KEY,
    speechVadMode: SPEECH_VAD_MODE_STORAGE_KEY,
    speechComplement: SPEECH_COMPLEMENT_STORAGE_KEY,
    speechVadThreshold: SPEECH_VAD_THRESHOLD_STORAGE_KEY,
    speechVadPrefixPaddingMs: SPEECH_VAD_PREFIX_PADDING_MS_STORAGE_KEY,
    speechVadSilenceDurationMs: SPEECH_VAD_SILENCE_DURATION_MS_STORAGE_KEY,
    speechVadEagerness: SPEECH_VAD_EAGERNESS_STORAGE_KEY,
    speechVadInterruptResponse: SPEECH_VAD_INTERRUPT_RESPONSE_STORAGE_KEY,
    speechInputNoiseReduction: SPEECH_INPUT_NOISE_REDUCTION_STORAGE_KEY,
    speechOutputSpeed: SPEECH_OUTPUT_SPEED_STORAGE_KEY,
    speechReasoningEffort: SPEECH_REASONING_EFFORT_STORAGE_KEY,
    speechMaxOutputTokens: SPEECH_MAX_OUTPUT_TOKENS_STORAGE_KEY,
    speechTranscriptionLogprobs: SPEECH_TRANSCRIPTION_LOGPROBS_STORAGE_KEY,
  }[storageName] || "";
}

function loadStoredSpeechSettings() {
  speechSessionSettingControls().forEach((control) => {
    const storageKey = speechSettingStorageKey(control.dataset.storageKey || "");
    if (!storageKey) {
      return;
    }
    const storedValue = localStorage.getItem(storageKey);
    if (storedValue === null) {
      return;
    }
    if (control.type === "checkbox") {
      control.checked = storedValue === "true";
    } else {
      control.value = storedValue;
    }
  });
  speechSettings.bargeInCancelEnabled = localStorage.getItem(SPEECH_BARGE_IN_CANCEL_STORAGE_KEY) !== "false";
  document.getElementById("speechBargeInCancelToggle").checked = speechSettings.bargeInCancelEnabled;
  speechSettings.echoGuardEnabled = localStorage.getItem(SPEECH_ECHO_GUARD_STORAGE_KEY) === "true";
  document.getElementById("speechEchoGuardToggle").checked = speechSettings.echoGuardEnabled;
}

function saveSpeechSessionSettingSelection(event) {
  const control = event.currentTarget;
  const storageKey = speechSettingStorageKey(control.dataset.storageKey || "");
  if (!storageKey) {
    return;
  }
  const value = control.type === "checkbox" ? String(control.checked) : control.value.trim();
  if (value) {
    localStorage.setItem(storageKey, value);
  } else {
    localStorage.removeItem(storageKey);
  }
  applySessionSettings();
}

function saveSpeechBargeInCancelSelection() {
  speechSettings.bargeInCancelEnabled = document.getElementById("speechBargeInCancelToggle").checked;
  localStorage.setItem(SPEECH_BARGE_IN_CANCEL_STORAGE_KEY, String(speechSettings.bargeInCancelEnabled));
  setSpeechDeviceStatus(speechSettings.bargeInCancelEnabled
    ? "Barge-in cancellation enabled; user speech interrupts assistant playback."
    : "Barge-in cancellation disabled; assistant playback will continue during user speech.", "ready");
}

function saveSpeechEchoGuardSelection() {
  speechSettings.echoGuardEnabled = document.getElementById("speechEchoGuardToggle").checked;
  localStorage.setItem(SPEECH_ECHO_GUARD_STORAGE_KEY, String(speechSettings.echoGuardEnabled));
  if (speechSettings.echoGuardEnabled) {
    setSpeechDeviceStatus("Half-duplex fallback enabled; microphone pauses during assistant playback.", "ready");
    return;
  }
  restoreRealtimeMicrophoneAfterAssistant("Half-duplex fallback disabled; microphone resumed.");
  setSpeechDeviceStatus("Half-duplex fallback disabled; full-duplex barge-in is active.", "ready");
}

function loadStoredSpeechDeviceSelection() {
  speechDevices.inputDeviceId = localStorage.getItem(SPEECH_INPUT_DEVICE_STORAGE_KEY) || "";
  speechDevices.outputDeviceId = localStorage.getItem(SPEECH_OUTPUT_DEVICE_STORAGE_KEY) || "";
}

function saveSpeechInputDeviceSelection() {
  speechDevices.inputDeviceId = document.getElementById("speech_input_device_select").value || "";
  localStorage.setItem(SPEECH_INPUT_DEVICE_STORAGE_KEY, speechDevices.inputDeviceId);
  setSpeechDeviceStatus(state.realtimeListening
    ? "Microphone saved. Restart speech to use the new input device."
    : "Microphone saved for the next speech session.", "ready");
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
  const requestPermission = options.requestPermission === true;
  const silent = options.silent === true;
  if (!speechAudioSelectionSupported()) {
    renderSpeechDeviceSelections([], []);
    setSpeechDeviceStatus("Audio device selection is not supported by this browser.", "error");
    setRealtimeControlsLocked(state.realtimeListening);
    return;
  }
  let permissionStream = null;
  try {
    if (requestPermission) {
      permissionStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    }
    const devices = await navigator.mediaDevices.enumerateDevices();
    const inputDevices = devices.filter((device) => device.kind === "audioinput");
    const outputDevices = devices.filter((device) => device.kind === "audiooutput");
    renderSpeechDeviceSelections(inputDevices, outputDevices);
    speechDevices.devicesLoaded = true;
    if (!silent) {
      const outputNote = speechOutputSelectionSupported() ? "" : " Speaker selection is not supported by this browser.";
      setSpeechDeviceStatus(`Audio devices refreshed.${outputNote}`, speechOutputSelectionSupported() ? "ready" : "");
    } else if (!speechOutputSelectionSupported()) {
      setSpeechDeviceStatus("Speaker selection is not supported by this browser; using browser default output.");
    }
  } catch (error) {
    setSpeechDeviceStatus(`Audio device refresh failed: ${error.message}`, "error");
    appendLog("realtime", `audio device refresh failed: ${error.message}`);
  } finally {
    if (permissionStream) {
      permissionStream.getTracks().forEach((track) => track.stop());
    }
    setRealtimeControlsLocked(state.realtimeListening);
  }
}

function renderSpeechDeviceSelections(inputDevices, outputDevices) {
  renderSpeechDeviceSelect(
    document.getElementById("speech_input_device_select"),
    inputDevices,
    "audioinput",
    "System / browser default",
    speechDevices.inputDeviceId
  );
  renderSpeechDeviceSelect(
    document.getElementById("speech_output_device_select"),
    outputDevices,
    "audiooutput",
    "System / browser default",
    speechDevices.outputDeviceId
  );
}

function renderSpeechDeviceSelect(select, devices, kind, defaultLabel, selectedDeviceId) {
  if (!select) {
    return;
  }
  select.replaceChildren(new Option(defaultLabel, ""));
  const seen = new Set([""]);
  devices.forEach((device, index) => {
    if (!device.deviceId || device.deviceId === "default" || seen.has(device.deviceId)) {
      return;
    }
    const label = device.label || `${kind === "audioinput" ? "Microphone" : "Speaker"} ${index + 1}`;
    select.appendChild(new Option(label, device.deviceId));
    seen.add(device.deviceId);
  });
  select.value = seen.has(selectedDeviceId) ? selectedDeviceId : "";
}

function selectedSpeechInputDeviceId() {
  return document.getElementById("speech_input_device_select").value || "";
}

function selectedSpeechOutputDeviceId() {
  return document.getElementById("speech_output_device_select").value || "";
}

function speechInputConstraints() {
  const constraints = {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    channelCount: { ideal: 1 },
    voiceIsolation: true,
  };
  const deviceId = selectedSpeechInputDeviceId();
  if (deviceId) {
    constraints.deviceId = { exact: deviceId };
  }
  return constraints;
}

async function applySelectedSpeechOutputDevice() {
  const audio = activeAssistantAudioElement();
  const deviceId = selectedSpeechOutputDeviceId();
  if (!speechOutputSelectionSupported()) {
    const message = deviceId
      ? "Selected speaker cannot be applied because this browser does not support speaker selection."
      : "Speaker selection is not supported by this browser; using browser default output.";
    setSpeechDeviceStatus(message, deviceId ? "error" : "");
    appendLog("realtime", message);
    return false;
  }
  try {
    await audio.setSinkId(deviceId);
    setSpeechDeviceStatus(`Speaker output: ${selectedSpeechDeviceLabel(document.getElementById("speech_output_device_select"))}.`, "ready");
    return true;
  } catch (error) {
    const message = `Speaker selection failed: ${error.message}`;
    setSpeechDeviceStatus(message, "error");
    appendLog("realtime", message);
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
  const enabled = !!state.agentId && supported;
  const select = document.getElementById("camera_device_select");
  const refresh = document.getElementById("refresh_camera_devices");
  if (select) {
    select.disabled = !enabled;
  }
  if (refresh) {
    refresh.disabled = !enabled;
  }
}

function wireRealtimePeerDiagnostics(peerConnection) {
  if (!peerConnection) {
    return;
  }
  setRealtimeTransportStatus("Transport Starting", "idle", "");
  peerConnection.addEventListener("iceconnectionstatechange", () => {
    handleRealtimeIceConnectionState(peerConnection.iceConnectionState);
  });
  peerConnection.addEventListener("connectionstatechange", () => {
    handleRealtimeConnectionState(peerConnection.connectionState);
  });
  peerConnection.addEventListener("icegatheringstatechange", () => {
    handleRealtimeIceGatheringState(peerConnection.iceGatheringState);
  });
  peerConnection.addEventListener("icecandidateerror", handleRealtimeIceCandidateError);
}

function handleRealtimeIceConnectionState(iceState) {
  appendLog("realtime", `ICE connection state: ${iceState}.`);
  if (iceState === "checking") {
    setRealtimeTransportStatus("ICE Checking", "idle", "");
  } else if (iceState === "connected" || iceState === "completed") {
    setRealtimeTransportStatus("Transport Connected", "live", "");
  } else if (iceState === "disconnected") {
    setRealtimeTransportStatus("Transport Interrupted", "error", "Realtime WebRTC ICE disconnected; speech may recover or may need a restart.");
  } else if (iceState === "failed") {
    appendLog("realtime", REALTIME_ICE_FAILURE_MESSAGE);
    setRealtimeTransportStatus("Transport Failed", "error", REALTIME_ICE_FAILURE_MESSAGE);
    setRealtimeGlobalStatus("Realtime ICE Failed", "error");
  } else if (iceState === "closed") {
    setRealtimeTransportStatus("Transport Idle", "idle", "");
  }
}

function handleRealtimeConnectionState(connectionState) {
  appendLog("realtime", `Peer connection state: ${connectionState}.`);
  if (connectionState === "connecting") {
    setRealtimeTransportStatus("Transport Connecting", "idle", "");
  } else if (connectionState === "connected") {
    setRealtimeTransportStatus("Transport Connected", "live", "");
  } else if (connectionState === "disconnected") {
    setRealtimeTransportStatus("Transport Interrupted", "error", "Realtime WebRTC connection disconnected; speech may recover or may need a restart.");
  } else if (connectionState === "failed") {
    appendLog("realtime", REALTIME_CONNECTION_FAILURE_MESSAGE);
    setRealtimeTransportStatus("Transport Failed", "error", REALTIME_CONNECTION_FAILURE_MESSAGE);
    setRealtimeGlobalStatus("Realtime Failed", "error");
  } else if (connectionState === "closed") {
    setRealtimeTransportStatus("Transport Idle", "idle", "");
  }
}

function handleRealtimeIceGatheringState(gatheringState) {
  appendLog("realtime", `ICE gathering state: ${gatheringState}.`);
  if (gatheringState === "gathering") {
    setRealtimeTransportStatus("ICE Gathering", "idle", "");
  }
}

function handleRealtimeIceCandidateError(event) {
  const details = realtimeIceCandidateErrorDetails(event);
  const message = `Realtime WebRTC ICE candidate error${details}. Check network/STUN/TURN if speech cannot connect.`;
  appendLog("realtime", message);
  setRealtimeTransportStatus("ICE Candidate Error", "error", message);
}

function realtimeIceCandidateErrorDetails(event) {
  const parts = [];
  if (event.errorCode) {
    parts.push(`code ${event.errorCode}`);
  }
  if (event.errorText) {
    parts.push(event.errorText);
  }
  if (event.url) {
    parts.push(event.url);
  }
  return parts.length ? ` (${parts.join("; ")})` : "";
}

function setRealtimeTransportStatus(text, mode = "idle", detail = "") {
  const status = document.getElementById("realtime_transport_status");
  if (status) {
    status.textContent = text;
    status.className = `status-pill is-${mode || "idle"}`;
  }
  const detailEl = document.getElementById("realtime_transport_detail");
  if (detailEl) {
    detailEl.textContent = detail;
    detailEl.classList.toggle("text-danger", mode === "error");
    detailEl.classList.toggle("text-muted", mode !== "error");
  }
}

function setRealtimeGlobalStatus(text, mode = "idle") {
  const status = document.getElementById("realtime_status");
  if (!status) {
    return;
  }
  status.textContent = text;
  status.className = `status-pill is-${mode || "idle"}`;
}

function currentRealtimeSettings() {
  return {
    voice: trimmedInputValue("speechVoiceInput"),
    turnDetection: document.getElementById("speechVadSelect").value || "server_vad",
    generateComplement: document.getElementById("speechComplementToggle").checked,
    vadThreshold: parseNumberRange(document.getElementById("speechVadThresholdInput").value, 0, 1),
    vadPrefixPaddingMs: parseIntegerRange(document.getElementById("speechVadPrefixInput").value, 0, 2000),
    vadSilenceDurationMs: parseIntegerRange(document.getElementById("speechVadSilenceInput").value, 0, 3000),
    vadEagerness: document.getElementById("speechVadEagernessSelect").value || "",
    vadInterruptResponse: parseOptionalBoolean(document.getElementById("speechVadInterruptResponseSelect").value),
    inputNoiseReduction: document.getElementById("speechInputNoiseReductionSelect").value || "",
    outputSpeed: parseNumberRange(document.getElementById("speechOutputSpeedInput").value, 0.25, 1.5),
    reasoningEffort: document.getElementById("speechReasoningEffortSelect").value || "",
    maxOutputTokens: parseIntegerRange(document.getElementById("speechMaxOutputTokensInput").value, 1, 4096),
    includeInputTranscriptionLogprobs: document.getElementById("speechTranscriptionLogprobsToggle").checked,
  };
}

function trimmedInputValue(id) {
  const element = document.getElementById(id);
  return element ? element.value.trim() : "";
}

function parseNumberRange(value, min, max) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return "";
  }
  const parsed = Number.parseFloat(String(value).trim());
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : "";
}

function parseIntegerRange(value, min, max) {
  if (value === null || value === undefined || String(value).trim() === "") {
    return "";
  }
  if (!/^-?\d+$/.test(String(value).trim())) {
    return "";
  }
  const parsed = Number.parseInt(String(value).trim(), 10);
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : "";
}

function parseOptionalBoolean(value) {
  if (value === "true" || value === "false") {
    return value;
  }
  return "";
}

function activeAssistantAudioElement() {
  return document.getElementById("assistant_audio");
}

function setMicEnabled(enabled) {
  if (!realtime.micStream) {
    return;
  }
  realtime.micStream.getAudioTracks().forEach((track) => {
    track.enabled = enabled;
  });
}

function realtimeAudioTracks() {
  return realtime.micStream ? realtime.micStream.getAudioTracks() : [];
}

function setRealtimeMicrophoneEnabled(enabled, reason = "") {
  const tracks = realtimeAudioTracks();
  if (!tracks.length) {
    return false;
  }
  tracks.forEach((track) => {
    track.enabled = enabled;
  });
  const muted = !enabled;
  if (realtime.micMutedForAssistant !== muted && reason) {
    appendLog("realtime", reason);
  }
  realtime.micMutedForAssistant = muted;
  return true;
}

function clearRealtimeMicRestoreTimer() {
  if (!realtime.micRestoreTimer) {
    return;
  }
  clearTimeout(realtime.micRestoreTimer);
  realtime.micRestoreTimer = null;
}

function muteRealtimeMicrophoneForAssistant() {
  if (!speechSettings.echoGuardEnabled) {
    return;
  }
  clearRealtimeMicRestoreTimer();
  setRealtimeMicrophoneEnabled(false, "Half-duplex fallback paused the microphone while assistant audio is active.");
  realtime.micRestoreTimer = setTimeout(() => {
    realtime.micRestoreTimer = null;
    restoreRealtimeMicrophoneAfterAssistant("Half-duplex fallback resumed the microphone after a response timeout.");
  }, REALTIME_ECHO_GUARD_MAX_MUTE_MS);
}

function scheduleRealtimeMicrophoneRestoreAfterAssistant() {
  if (!realtime.micMutedForAssistant) {
    return;
  }
  clearRealtimeMicRestoreTimer();
  realtime.micRestoreTimer = setTimeout(() => {
    realtime.micRestoreTimer = null;
    restoreRealtimeMicrophoneAfterAssistant("Half-duplex fallback resumed the microphone after assistant playback.");
  }, REALTIME_ECHO_GUARD_RELEASE_MS);
}

function restoreRealtimeMicrophoneAfterAssistant(reason = "") {
  clearRealtimeMicRestoreTimer();
  setRealtimeMicrophoneEnabled(true, reason);
}

function registerAssistantAudioDiagnostics() {
  const audio = activeAssistantAudioElement();
  if (!audio) {
    return;
  }
  audio.addEventListener("playing", () => clearAssistantAudioPlaybackIssue("Assistant audio playback resumed."));
  audio.addEventListener("waiting", () => {
    reportAssistantAudioPlaybackIssue("Assistant audio is buffering; playback may sound choppy.");
  });
  audio.addEventListener("stalled", () => {
    reportAssistantAudioPlaybackIssue("Assistant audio stalled; playback may sound choppy.");
  });
  audio.addEventListener("error", () => {
    reportAssistantAudioPlaybackIssue(`Assistant audio playback error: ${assistantAudioErrorMessage()}`);
  });
}

function registerRemoteAudioTrackDiagnostics(track) {
  if (!track || typeof track.addEventListener !== "function") {
    return;
  }
  track.addEventListener("mute", () => {
    reportAssistantAudioPlaybackIssue("Remote assistant audio track is muted by WebRTC; playback may be interrupted.");
  });
  track.addEventListener("unmute", () => clearAssistantAudioPlaybackIssue("Remote assistant audio track resumed."));
  track.addEventListener("ended", () => {
    reportAssistantAudioPlaybackIssue("Remote assistant audio track ended unexpectedly.");
  });
}

function reportAssistantAudioPlaybackIssue(message) {
  if (!state.realtimeListening) {
    return;
  }
  const now = Date.now();
  if (now - realtime.lastPlaybackWarningAt < REALTIME_PLAYBACK_WARNING_COOLDOWN_MS) {
    return;
  }
  realtime.playbackIssueActive = true;
  realtime.lastPlaybackWarningAt = now;
  setRealtimeTransportStatus("Audio Warning", "error", message);
  appendLog("realtime", message);
}

function clearAssistantAudioPlaybackIssue(message) {
  if (!realtime.playbackIssueActive) {
    return;
  }
  realtime.playbackIssueActive = false;
  if (state.realtimeListening && message) {
    appendLog("realtime", message);
  }
  if (state.realtimeListening) {
    setRealtimeTransportStatus("Transport Connected", "live", "");
  }
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

function startRealtimeStatsDiagnostics() {
  stopRealtimeStatsDiagnostics();
  const peerConnection = realtime.peerConnection;
  if (!peerConnection || typeof peerConnection.getStats !== "function") {
    return;
  }
  realtime.statsTimer = setInterval(() => {
    pollRealtimeAudioStats();
  }, REALTIME_STATS_POLL_MS);
  pollRealtimeAudioStats();
}

function stopRealtimeStatsDiagnostics() {
  if (!realtime.statsTimer) {
    return;
  }
  clearInterval(realtime.statsTimer);
  realtime.statsTimer = null;
}

async function pollRealtimeAudioStats() {
  const peerConnection = realtime.peerConnection;
  if (!peerConnection || typeof peerConnection.getStats !== "function") {
    return;
  }
  let stats = null;
  try {
    stats = await peerConnection.getStats();
  } catch (error) {
    appendLog("realtime", `stats unavailable: ${errorMessage(error)}`);
    return;
  }
  const sample = extractRealtimeAudioStats(stats);
  if (!sample) {
    return;
  }
  const warning = realtimeAudioStatsWarning(sample, realtime.lastAudioStats);
  realtime.lastAudioStats = sample;
  if (warning) {
    reportRealtimeStatsIssue(warning);
  }
}

function extractRealtimeAudioStats(stats) {
  const sample = {
    packetsLost: 0,
    jitter: 0,
    concealedSamples: 0,
    jitterBufferDelay: 0,
    jitterBufferEmittedCount: 0,
    rtt: null,
  };
  let hasInboundAudio = false;
  stats.forEach((report) => {
    if (report.type === "inbound-rtp" && (report.kind === "audio" || report.mediaType === "audio")) {
      hasInboundAudio = true;
      sample.packetsLost += finiteNumber(report.packetsLost);
      sample.jitter = Math.max(sample.jitter, finiteNumber(report.jitter));
      sample.concealedSamples += finiteNumber(report.concealedSamples);
      sample.jitterBufferDelay += finiteNumber(report.jitterBufferDelay);
      sample.jitterBufferEmittedCount += finiteNumber(report.jitterBufferEmittedCount);
    } else if (report.type === "candidate-pair" &&
      (report.selected || (report.nominated && report.state === "succeeded"))) {
      const rtt = finiteNumberOrNull(report.currentRoundTripTime);
      if (rtt !== null) {
        sample.rtt = rtt;
      }
    }
  });
  return hasInboundAudio ? sample : null;
}

function realtimeAudioStatsWarning(current, previous) {
  if (!previous) {
    return "";
  }
  const packetLossDelta = Math.max(0, current.packetsLost - previous.packetsLost);
  const concealedDelta = Math.max(0, current.concealedSamples - previous.concealedSamples);
  const emittedDelta = Math.max(0, current.jitterBufferEmittedCount - previous.jitterBufferEmittedCount);
  const delayDelta = Math.max(0, current.jitterBufferDelay - previous.jitterBufferDelay);
  const jitterMs = Math.round(current.jitter * 1000);
  const jitterBufferMs = emittedDelta > 0 ? Math.round((delayDelta / emittedDelta) * 1000) : 0;
  const rttMs = current.rtt === null ? 0 : Math.round(current.rtt * 1000);
  const issues = [];
  if (packetLossDelta > 0) {
    issues.push(`${packetLossDelta} lost audio packets`);
  }
  if (concealedDelta > 960) {
    issues.push(`${concealedDelta} concealed audio samples`);
  }
  if (jitterMs > 80) {
    issues.push(`jitter ${jitterMs} ms`);
  }
  if (jitterBufferMs > 120) {
    issues.push(`jitter buffer ${jitterBufferMs} ms`);
  }
  if (rttMs > 800) {
    issues.push(`RTT ${rttMs} ms`);
  }
  return issues.length ? `Realtime audio stats warning: ${issues.join(", ")}.` : "";
}

function reportRealtimeStatsIssue(message) {
  const now = Date.now();
  if (now - realtime.lastStatsWarningAt < REALTIME_STATS_WARNING_COOLDOWN_MS) {
    return;
  }
  realtime.lastStatsWarningAt = now;
  setRealtimeTransportStatus("Audio Warning", "error", message);
  appendLog("realtime", message);
}

function finiteNumber(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
}

function finiteNumberOrNull(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function logActiveSpeechInputSettings(stream) {
  const track = stream && stream.getAudioTracks ? stream.getAudioTracks()[0] : null;
  if (!track || typeof track.getSettings !== "function") {
    return;
  }
  const settings = track.getSettings();
  appendLog("realtime", `microphone processing: echoCancellation=${String(settings.echoCancellation)}, ` +
    `noiseSuppression=${String(settings.noiseSuppression)}, autoGainControl=${String(settings.autoGainControl)}, ` +
    `channelCount=${String(settings.channelCount)}.`);
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
    renderEmotionMetrics(null);
    return;
  }
  drawFaceBox(detection.detection.box);
  const emotion = deriveEmotion(detection.expressions);
  renderEmotionMetrics(emotion, detection.detection.score);
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

function resetEmotionReport() {
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
  setEmotionEmitStatus("No face", "idle");
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
    source: "valerian.hand.camera",
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
    appendLog("hand", "unknown hand sign.");
    return false;
  }
  renderUserSign(normalized);
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
  return !!data;
}

async function fetchWeatherCurrent() {
  try {
    const payloads = await loadWeatherPayloads();
    weather.current = payloads.current;
    weather.forecast = payloads.forecast;
    weather.locationQuery = weatherLocationQuery();
    renderWeatherPayload(weather.current);
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
    return;
  }
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

function resetWeatherState() {
  weather.current = null;
  weather.forecast = null;
  weather.locationQuery = "";
  renderWeatherStatus("-");
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
    resetEmotionReport();
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
  document.getElementById("start_camera").disabled = !enabled || state.cameraRunning;
  document.getElementById("stop_camera").disabled = !enabled || !state.cameraRunning;
  updateCameraDeviceControls();
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

function setRealtimeState(isListening, mode = realtime.activeMode || REALTIME_MODE_CONTINUOUS) {
  state.realtimeListening = isListening;
  const continuousButton = document.getElementById("toggle_realtime");
  const continuousListen = document.getElementById("listen_status");
  const status = document.getElementById("realtime_status");
  const continuousActive = isListening && mode === REALTIME_MODE_CONTINUOUS;
  if (isListening) {
    continuousButton.innerHTML = continuousActive
      ? '<i class="bi bi-mic-mute-fill me-2"></i>Stop Continuous'
      : '<i class="bi bi-mic-fill me-2"></i>Start Continuous';
    continuousButton.classList.toggle("is-listening", continuousActive);
    continuousButton.disabled = !continuousActive;
    continuousListen.textContent = continuousActive ? "Listening" : "Idle";
    continuousListen.className = `status-pill is-${continuousActive ? "listening" : "idle"}`;
    status.textContent = "Realtime Live";
    status.className = "status-pill is-live";
  } else {
    continuousButton.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Continuous';
    continuousButton.classList.remove("is-listening");
    continuousButton.disabled = false;
    continuousListen.textContent = "Idle";
    continuousListen.className = "status-pill is-idle";
    status.textContent = "Realtime Idle";
    status.className = "status-pill is-idle";
  }
  setRealtimeControlsLocked(isListening);
}

function setRealtimeControlsLocked(locked) {
  speechSessionSettingControls().forEach((element) => {
    element.disabled = locked;
  });
  [
    "speech_input_device_select",
  ].forEach((id) => {
    const element = document.getElementById(id);
    if (element) {
      element.disabled = locked;
    }
  });
  const bargeIn = document.getElementById("speechBargeInCancelToggle");
  if (bargeIn) {
    bargeIn.disabled = false;
  }
  const echoGuard = document.getElementById("speechEchoGuardToggle");
  if (echoGuard) {
    echoGuard.disabled = false;
  }
  const outputSelect = document.getElementById("speech_output_device_select");
  if (outputSelect) {
    outputSelect.disabled = !speechAudioSelectionSupported() || !speechOutputSelectionSupported();
  }
  const refreshButton = document.getElementById("refresh_audio_devices");
  if (refreshButton) {
    refreshButton.disabled = !speechAudioSelectionSupported();
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
