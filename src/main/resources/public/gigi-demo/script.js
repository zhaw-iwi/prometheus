const state = {
  agentId: null,
  agents: [],
  agentInfo: null,
  behaviourSource: null,
  monitorSource: null,
  lastBehaviourEventId: null,
  seenBehaviourKeys: new Set(),
  streamReconnectTimer: null,
  streamReconnectAttempt: 0,
  monitorReconnectTimer: null,
  monitorReconnectAttempt: 0,
  isPageUnloading: false,
  realtimeListening: false,
  cameraRunning: false,
};

const realtime = {
  peerConnection: null,
  dataChannel: null,
  micStream: null,
  assistantTranscriptBuffer: "",
  assistantAppended: false,
  assistantAudioSeen: false,
  suppressAssistantAppend: false,
  lastSystemPrompt: "",
  pushToTalkActive: false,
  spaceKeyBindingActive: false,
  pendingBackendSpeech: null,
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
  lastEmitAt: 0,
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
  renderTemperatureValue();
  updatePushToTalkUi();
  appendSystemMessage("Select or paste an agent ID, then start an interaction.");

  state.agentId = getAgentIdFromLocation() || localStorage.getItem("gigiDemoAgentId");
  await loadAgents();
  if (state.agentId) {
    document.getElementById("agent_id_input").value = state.agentId;
    await connectToAgent(state.agentId);
  } else {
    setControlsEnabled(false);
  }
}

function wireUi() {
  document.getElementById("connect_agent").addEventListener("click", () => {
    connectToAgent(document.getElementById("agent_id_input").value.trim());
  });
  document.getElementById("agent_select").addEventListener("change", (event) => {
    const id = event.target.value;
    document.getElementById("agent_id_input").value = id;
    if (id) {
      connectToAgent(id);
    }
  });
  document.getElementById("start_agent").addEventListener("click", startAgent);
  document.getElementById("reset_agent").addEventListener("click", resetAgent);
  document.getElementById("send_text").addEventListener("click", sendTextInput);
  document.getElementById("text_input").addEventListener("keydown", handleTextKeyDown);
  document.getElementById("clear_messages").addEventListener("click", clearMessages);
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
    input.addEventListener("change", ensureEnabledModels);
  });
}

async function loadAgents() {
  const select = document.getElementById("agent_select");
  select.innerHTML = '<option value="">Select agent</option>';
  try {
    const response = await fetch("/agent");
    if (!response.ok) {
      appendLog("app", `agent list failed: ${response.status}`);
      return;
    }
    state.agents = await response.json();
    const sorted = [...state.agents].sort((a, b) => agentSortKey(a).localeCompare(agentSortKey(b)));
    for (const agent of sorted) {
      const id = agentIdOf(agent);
      if (!id) {
        continue;
      }
      const option = document.createElement("option");
      option.value = id;
      option.textContent = isGigiAgent(agent) ? `${agent.name} *` : agent.name || id;
      select.appendChild(option);
    }
    if (!state.agentId) {
      const firstGigi = sorted.find(isGigiAgent);
      state.agentId = firstGigi ? agentIdOf(firstGigi) : null;
      if (state.agentId) {
        document.getElementById("agent_id_input").value = state.agentId;
        select.value = state.agentId;
      }
    } else {
      select.value = state.agentId;
    }
  } catch (error) {
    appendLog("app", "agent list failed: " + error.message);
  }
}

async function connectToAgent(agentId) {
  if (!agentId) {
    setControlsEnabled(false);
    appendSystemMessage("Missing agent ID.");
    return;
  }
  cleanupStreams();
  state.agentId = agentId;
  localStorage.setItem("gigiDemoAgentId", agentId);
  document.getElementById("agent_id_input").value = agentId;
  document.getElementById("agent_select").value = agentId;
  state.lastBehaviourEventId = null;
  state.seenBehaviourKeys.clear();
  setControlsEnabled(true);
  clearMessages();
  appendSystemMessage("Connected.");
  await loadAgentInfo();
  await loadEventHistory();
  await loadStorage();
  connectBehaviourStream();
  connectMonitorStream();
}

async function loadAgentInfo() {
  try {
    const response = await fetch(`/${state.agentId}/info`);
    if (!response.ok) {
      appendLog("app", `agent info failed: ${response.status}`);
      setActiveStatus(null);
      return;
    }
    const data = await response.json();
    state.agentInfo = data;
    document.getElementById("agent_subtitle").textContent = data.name || "PROMETHEUS TDSR test console";
    document.getElementById("agent_info_name").textContent = data.name || "-";
    document.getElementById("agent_info_description").textContent = data.description || "-";
    setActiveStatus(data.active);
  } catch (error) {
    appendLog("app", "agent info failed: " + error.message);
  }
}

async function loadEventHistory() {
  try {
    const response = await fetch(`/${state.agentId}/eventhistory`);
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
      }
    }
    return events || [];
  } catch (error) {
    appendLog("app", "event history failed: " + error.message);
    return [];
  }
}

async function loadStorage() {
  try {
    const response = await fetch(`/${state.agentId}/storage`);
    if (!response.ok) {
      setText("storage_view", "-");
      return;
    }
    const storage = await response.json();
    setText("storage_view", JSON.stringify(storage, null, 2));
  } catch (_) {
    setText("storage_view", "-");
  }
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
  state.monitorSource = new EventSource(`/${state.agentId}/monitor/stream`);
  state.monitorSource.addEventListener("open", () => {
    state.monitorReconnectAttempt = 0;
  });
  state.monitorSource.addEventListener("snapshot", (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data && typeof data.active === "boolean") {
        setActiveStatus(data.active);
      }
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
  let url = `/${state.agentId}/behaviour/stream`;
  if (state.lastBehaviourEventId) {
    url += `?lastEventId=${encodeURIComponent(state.lastBehaviourEventId)}`;
  }
  return url;
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
    const response = await fetch(`/${state.agentId}/start`, { method: "POST" });
    if (!response.ok) {
      appendLog("app", `start failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    handleResponseEvent(data.responseEvent);
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
    const response = await fetch(`/${state.agentId}/reset`, { method: "DELETE" });
    if (!response.ok) {
      appendLog("app", `reset failed: ${response.status}`);
      return;
    }
    const data = await response.json();
    setActiveStatus(data.active);
    clearMessages();
    state.seenBehaviourKeys.clear();
    resetBehaviourPanels();
    handleResponseEvent(data.responseEvent);
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
  return true;
}

async function acknowledgeEvent(request, options = {}) {
  if (!state.agentId) {
    appendLog("app", "ack skipped: no agent.");
    return null;
  }
  const profile = options.profile ? `?profile=${encodeURIComponent(options.profile)}` : "";
  try {
    const response = await fetch(`/${state.agentId}/acknowledge${profile}`, {
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
    const response = await fetch(`/${state.agentId}/behaviour/generate`, {
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
  const key = event.createdDate ? `${event.createdDate}|${event.payload}` : event.payload;
  if (state.seenBehaviourKeys.has(key)) {
    return;
  }
  state.seenBehaviourKeys.add(key);
  let plan = null;
  try {
    plan = JSON.parse(event.payload);
  } catch (_) {
    appendLog("behaviour", "payload is not valid json.");
    return;
  }
  renderBehaviourPlan(plan);
  renderLatestEvent(event);
  if (!options.fromHistory && typeof plan.speech === "string" && plan.speech.trim()) {
    appendMessage("assistant", plan.speech.trim());
  }
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
  try {
    const promptBundle = await fetchPromptBundle();
    setActiveStatus(promptBundle.active);
    const sessionInfo = await createRealtimeSession();
    await setupRealtimeConnection(sessionInfo);
    await waitForDataChannelOpen();
    applySessionSettings();
    updatePushToTalkUi();
    applyPromptBundle(promptBundle, true);
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
  const audio = document.getElementById("assistant_audio");
  audio.pause();
  audio.removeAttribute("src");
  audio.srcObject = null;
  audio.load();
  realtime.pushToTalkActive = false;
  disableSpaceKeyPushToTalk();
  updatePushToTalkUi();
  appendLog("realtime", "stopped.");
}

async function fetchPromptBundle() {
  const response = await fetch(`/${state.agentId}/prompt?profile=realtime_speech`);
  if (!response.ok) {
    throw new Error("prompt fetch failed.");
  }
  const data = await response.json();
  appendLog("policy", "prompt bundle received.");
  return data;
}

async function createRealtimeSession() {
  const response = await fetch("/realtime/session", { method: "POST" });
  if (!response.ok) {
    throw new Error("realtime session creation failed.");
  }
  return await response.json();
}

async function setupRealtimeConnection(sessionInfo) {
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
  const answerResponse = await fetch(sessionInfo.realtimeCallsUrl, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${sessionInfo.clientSecret}`,
      "Content-Type": "application/sdp",
    },
    body: offer.sdp,
  });
  if (!answerResponse.ok) {
    throw new Error("realtime SDP exchange failed.");
  }
  const answer = await answerResponse.text();
  await realtime.peerConnection.setRemoteDescription({ type: "answer", sdp: answer });
  appendLog("realtime", "WebRTC session established.");
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
  if (data.type === "conversation.item.input_audio_transcription.completed") {
    const transcript = data.transcript || "";
    if (transcript.trim()) {
      appendMessage("user", transcript.trim());
      handleRealtimeUserTranscript(transcript.trim());
    }
  } else if (data.type === "conversation.item.input_audio_transcription.delta") {
    setText("latest_event", data.transcript || "");
  } else if (data.type === "response.output_audio_transcript.delta" || data.type === "response.output_text.delta") {
    realtime.assistantAudioSeen = true;
    realtime.assistantTranscriptBuffer += data.delta || "";
    setText("speech_preview", realtime.assistantTranscriptBuffer);
  } else if (data.type === "response.output_audio_transcript.done" || data.type === "response.output_text.done") {
    const transcript = realtime.assistantTranscriptBuffer.trim();
    if (transcript) {
      setText("speech_preview", transcript);
      if (!realtime.suppressAssistantAppend && !realtime.assistantAppended) {
        appendMessage("assistant", transcript);
        appendAssistantTranscript(transcript);
        realtime.assistantAppended = true;
      }
    }
    realtime.suppressAssistantAppend = false;
    realtime.assistantTranscriptBuffer = "";
  } else if (data.type === "response.done") {
    realtime.assistantAudioSeen = false;
  }
}

async function handleRealtimeUserTranscript(transcript) {
  const ackData = await acknowledgeEvent({
    type: "obs.user_utterance",
    actor: "user",
    kind: "observation",
    payload: transcript,
  }, { profile: "backend_complement", renderResponse: true });
  if (!ackData) {
    return;
  }
  if (ackData.active !== false && document.getElementById("generate_side_behaviour").checked) {
    await generateBehaviour("backend_complement");
  }
  const promptBundle = await fetchPromptBundle();
  setActiveStatus(promptBundle.active);
  applyPromptBundle(promptBundle, true);
}

async function appendAssistantTranscript(transcript) {
  const data = await acknowledgeEvent({
    type: "resp.behaviour_plan",
    actor: "assistant",
    kind: "response",
    payload: JSON.stringify({ speech: transcript }),
  }, { renderResponse: false });
  if (data) {
    appendLog("policy", "assistant response stored.");
  }
}

function applyPromptBundle(promptBundle, shouldRespond) {
  sendSessionUpdate(buildSystemPrompt(promptBundle), currentRealtimeSettings());
  if (shouldRespond) {
    sendResponseCreate(buildResponseInstruction(promptBundle));
  }
}

function buildSystemPrompt(promptBundle) {
  if (!promptBundle || !Array.isArray(promptBundle.promptMessages)) {
    return "";
  }
  return promptBundle.promptMessages
    .map((message) => `${message.role || "user"}: ${message.content || ""}`)
    .filter((line) => line.trim())
    .join("\n");
}

function buildResponseInstruction(promptBundle) {
  const telemetry = "Use the provided PROMETHEUS perception telemetry when relevant. Do not mention unavailable perception if telemetry is present.";
  if (promptBundle && promptBundle.active === false) {
    return `The interaction has ended. Briefly acknowledge and do not continue. ${telemetry}`;
  }
  if (promptBundle && Array.isArray(promptBundle.promptMessages) && promptBundle.promptMessages.length <= 1) {
    return `Begin the interaction now. ${telemetry}`;
  }
  return `Respond to the latest input while following the system instructions. ${telemetry}`;
}

function sendSessionUpdate(systemPrompt, settings) {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    return;
  }
  realtime.lastSystemPrompt = systemPrompt || "";
  const sessionPayload = {
    type: "realtime",
    instructions: realtime.lastSystemPrompt,
    output_modalities: ["audio"],
  };
  const audio = {};
  if (settings.voice) {
    audio.output = {
      voice: settings.voice,
    };
  }
  if (settings.turnDetection === "none") {
    audio.input = {
      turn_detection: null,
    };
  } else if (settings.turnDetection) {
    audio.input = {
      turn_detection: {
        type: settings.turnDetection,
        create_response: false,
        interrupt_response: false,
      },
    };
  }
  if (Object.keys(audio).length > 0) {
    sessionPayload.audio = audio;
  }
  console.log(`[session.update] turnDetection=${settings.turnDetection}`);
  realtime.dataChannel.send(JSON.stringify({ type: "session.update", session: sessionPayload }));
  appendLog("realtime", "session updated.");
}

function sendResponseCreate(instructions) {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    appendLog("realtime", "response.create skipped.");
    return;
  }
  realtime.assistantAudioSeen = false;
  realtime.assistantAppended = false;
  realtime.assistantTranscriptBuffer = "";
  realtime.dataChannel.send(JSON.stringify({
    type: "response.create",
    response: {
      instructions,
      output_modalities: ["audio"],
    },
  }));
}

function applySessionSettings() {
  if (!state.realtimeListening) {
    return;
  }
  sendSessionUpdate(realtime.lastSystemPrompt, currentRealtimeSettings());
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
  if (document.getElementById("sensor_emotion_enabled").checked && !camera.faceModelsReady) {
    loaders.push(loadFaceModels());
  }
  if (document.getElementById("sensor_social_enabled").checked && !camera.socialDetectorReady) {
    loaders.push(loadSocialDetector());
  }
  if (document.getElementById("sensor_hand_enabled").checked && !camera.handDetectorReady) {
    loaders.push(loadHandRecognizer());
  }
  if (loaders.length > 0) {
    setCameraStatus("Loading Models", "idle");
    await Promise.allSettled(loaders);
    if (!state.cameraRunning) {
      setCameraStatus("Camera Idle", "idle");
    }
  }
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
    if (document.getElementById("sensor_social_enabled").checked && camera.socialDetectorReady) {
      await detectSocial();
    }
    if (document.getElementById("sensor_emotion_enabled").checked && camera.faceModelsReady) {
      await detectEmotion();
    }
    if (document.getElementById("sensor_hand_enabled").checked && camera.handDetectorReady) {
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
  setText("emotion_value", `${emotion.emotion} ${emotion.confidence.toFixed(2)}`);
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
    setText("camera_sign_value", `${SIGNS[candidate.sign].label} ${candidate.confidence.toFixed(2)}`);
  } else {
    setText("camera_sign_value", "-");
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
  if (emotion.confidence < threshold || !passesEmitInterval()) {
    return;
  }
  if (camera.lastEmotion && camera.lastEmotion.emotion === emotion.emotion &&
    Math.abs(camera.lastEmotion.valence - emotion.valence) < 0.08 &&
    Math.abs(camera.lastEmotion.arousal - emotion.arousal) < 0.08) {
    return;
  }
  const payload = {
    source: "visual.facial",
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
  }, { renderResponse: true });
  if (ok) {
    camera.lastEmitAt = Date.now();
    camera.lastEmotion = emotion;
  }
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
  if (!document.getElementById("sensor_emit_enabled").checked || !social || !passesEmitInterval()) {
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
  camera.lastEmitAt = Date.now();
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
  if (!candidate || !document.getElementById("sensor_emit_enabled").checked ||
    !document.getElementById("hand_auto_send").checked) {
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
    camera.lastCameraEmitAt = Date.now();
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
  const { scaleX, scaleY } = overlayScale();
  camera.ctx.lineWidth = 3;
  camera.ctx.strokeStyle = "#ff7a00";
  camera.ctx.strokeRect(box.x * scaleX, box.y * scaleY, box.width * scaleX, box.height * scaleY);
}

function drawSocialOverlay(tracked, social) {
  const { scaleX, scaleY } = overlayScale();
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
    const hue = gid >= 0 ? (gid * 57) % 360 : 180;
    camera.ctx.lineWidth = 2;
    camera.ctx.strokeStyle = `hsl(${hue}, 75%, 42%)`;
    camera.ctx.strokeRect(x * scaleX, y * scaleY, w * scaleX, h * scaleY);
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

function clearOverlay() {
  if (!camera.ctx) {
    return;
  }
  const rect = camera.canvas.getBoundingClientRect();
  camera.canvas.width = rect.width;
  camera.canvas.height = rect.height;
  camera.ctx.clearRect(0, 0, camera.canvas.width, camera.canvas.height);
}

function passesEmitInterval() {
  const minInterval = Number(document.getElementById("emit_interval_ms").value || 2500);
  return Date.now() - camera.lastEmitAt >= minInterval;
}

function resetCameraEmissionGate() {
  camera.lastCameraEmitKey = null;
  camera.lastCameraEmitAt = 0;
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
  const payload = event.payload ? shortPayload(event.payload) : "";
  setText("latest_event", payload ? `${event.type}: ${payload}` : event.type);
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
  const log = document.getElementById("activity_log");
  if (!log) {
    return;
  }
  const stamp = new Date().toLocaleTimeString();
  const next = `[${stamp}] ${scope}: ${message}`;
  log.textContent = log.textContent ? `${next}\n${log.textContent}` : next;
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
}

function setControlsEnabled(enabled) {
  document.querySelectorAll("button, textarea, select, input").forEach((el) => {
    if (el.id === "agent_id_input" || el.id === "agent_select" || el.id === "connect_agent") {
      return;
    }
    el.disabled = !enabled;
  });
  document.getElementById("stop_camera").disabled = true;
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

function agentIdOf(agent) {
  return agent && (agent.id || agent.ID || agent.iD);
}

function isGigiAgent(agent) {
  const text = `${agent && agent.name ? agent.name : ""} ${agent && agent.description ? agent.description : ""}`;
  return text.toLowerCase().includes("gigi tdsr");
}

function agentSortKey(agent) {
  return `${isGigiAgent(agent) ? "0" : "1"}-${agent && agent.name ? agent.name : ""}`;
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
