let session = {
  agentId: null,
  isListening: false,
  agentActive: null,
  activeMode: null,
};

let peerConnection = null;
let dataChannel = null;
let micStream = null;
let realtimeCallId = null;
let behaviourSource = null;
let behaviourReconnectTimer = null;
let behaviourReconnectAttempt = 0;
let isPageUnloading = false;
const BEHAVIOUR_RECONNECT_MIN_MS = 1000;
const BEHAVIOUR_RECONNECT_MAX_MS = 30000;
const BEHAVIOUR_RECONNECT_JITTER = 0.2;
const TRANSCRIPT_BATCH_DELAY_MS = 900;
const REALTIME_MODE_CONTINUOUS = "continuous";
const REALTIME_MODE_PUSH_TO_TALK = "push_to_talk";
let assistantTranscriptBuffer = "";
let gifState = "idle";
let gifSwapTimeout = null;
const gifFadeMs = 600;
let assistantAudioSeen = false;
let realtimeResponseActive = false;
let pendingInputItemIds = new Set();
let processedInputItemIds = new Set();
let transcriptCandidates = [];
let transcriptFlushTimer = null;
let activeTurnDetection = null;
const gifSources = {
  idle: "her.gif",
  thinking: "her-fast.gif",
};
let sessionSettings = {
  voice: "",
  turnDetection: "server_vad",
};
let pushToTalkActive = false;
let spaceKeyBindingActive = false;
let recordedStream = null;
let mediaRecorder = null;
let recordedChunks = [];
let recordedMimeType = "";
let discardRecordedTurn = false;
let lastBackendBehaviourCreatedDate = null;
let lastBackendBehaviourEventId = null;
let lastBackendSpeech = "";

window.addEventListener("load", () => {
  session.agentId = getAgentId();
  window.addEventListener("beforeunload", cleanupBehaviourStream);
  window.addEventListener("pagehide", cleanupBehaviourStream);
  if (!session.agentId) {
    appendLog("app", "Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    disableToggle();
    return;
  }
  wireUi();
  loadAgentInfo();
});

function wireUi() {
  document.getElementById("toggle_listen").addEventListener("click", () => toggleListening(REALTIME_MODE_CONTINUOUS));
  document.getElementById("toggle_push_to_talk_listen")
    .addEventListener("click", () => toggleListening(REALTIME_MODE_PUSH_TO_TALK));
  document.getElementById("reset_agent").addEventListener("click", resetAgent);
  document.getElementById("show_agent_info").addEventListener("click", showAgentInfo);
  const voiceSelect = document.getElementById("voice_select");
  const turnDetectionSelect = document.getElementById("turn_detection_select");
  const pushToTalkButton = document.getElementById("push_to_talk");
  if (voiceSelect) {
    voiceSelect.addEventListener("change", () => {
      sessionSettings.voice = voiceSelect.value;
      if (session.isListening) {
        appendLog("realtime", "Restart realtime to apply voice changes.");
      }
    });
  }
  if (turnDetectionSelect) {
    turnDetectionSelect.addEventListener("change", () => {
      sessionSettings.turnDetection = turnDetectionSelect.value;
      if (session.isListening) {
        appendLog("realtime", "Restart realtime to apply VAD changes.");
      }
    });
  }
  if (pushToTalkButton) {
    pushToTalkButton.addEventListener("mousedown", startPushToTalk);
    pushToTalkButton.addEventListener("touchstart", startPushToTalk, { passive: false });
    pushToTalkButton.addEventListener("mouseup", stopPushToTalk);
    pushToTalkButton.addEventListener("mouseleave", stopPushToTalk);
    pushToTalkButton.addEventListener("touchend", stopPushToTalk);
    pushToTalkButton.addEventListener("touchcancel", stopPushToTalk);
  }
  updatePushToTalkUi();
}

function disableToggle() {
  const button = document.getElementById("toggle_listen");
  button.disabled = true;
}

function setListeningState(isListening, mode = session.activeMode || REALTIME_MODE_CONTINUOUS) {
  session.isListening = isListening;
  const continuousButton = document.getElementById("toggle_listen");
  const pushToTalkButton = document.getElementById("toggle_push_to_talk_listen");
  const status = document.getElementById("listen_status");
  const continuousActive = isListening && mode === REALTIME_MODE_CONTINUOUS;
  const pushToTalkActive = isListening && mode === REALTIME_MODE_PUSH_TO_TALK;
  if (isListening) {
    continuousButton.innerHTML = continuousActive
      ? '<i class="bi bi-mic-mute-fill me-2"></i>Stop Continuous'
      : '<i class="bi bi-mic-fill me-2"></i>Start Continuous';
    pushToTalkButton.innerHTML = pushToTalkActive
      ? '<i class="bi bi-mic-mute-fill me-2"></i>Stop Push to Talk'
      : '<i class="bi bi-mic-fill me-2"></i>Start Push to Talk';
    status.textContent = "Listening";
    status.className = "status-pill is-listening";
    continuousButton.classList.toggle("is-listening", continuousActive);
    pushToTalkButton.classList.toggle("is-listening", pushToTalkActive);
    continuousButton.disabled = !continuousActive;
    pushToTalkButton.disabled = !pushToTalkActive;
    updatePushToTalkUi();
    setRealtimeControlsLocked(true);
    setGifState("idle");
  } else {
    continuousButton.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Continuous';
    pushToTalkButton.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Push to Talk';
    status.textContent = "Idle";
    status.className = "status-pill is-idle";
    continuousButton.classList.remove("is-listening");
    pushToTalkButton.classList.remove("is-listening");
    continuousButton.disabled = false;
    pushToTalkButton.disabled = false;
    updatePushToTalkUi();
    setRealtimeControlsLocked(false);
    const gif = document.getElementById("realtime_gif");
    if (gif) {
      gif.classList.add("is-hidden");
    }
  }
}

function setRealtimeControlsLocked(locked) {
  ["voice_select", "turn_detection_select", "generate_side_behaviour"].forEach((id) => {
    const element = document.getElementById(id);
    if (element) {
      element.disabled = locked;
    }
  });
}

function setActiveStatus(isActive) {
  session.agentActive = typeof isActive === "boolean" ? isActive : null;
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

async function toggleListening(mode = REALTIME_MODE_CONTINUOUS) {
  if (!session.isListening) {
    await startListening(mode);
    return;
  }
  if (session.activeMode === mode) {
    await stopListening();
  } else {
    appendLog("realtime", "Stop the active speech session before starting another mode.");
  }
}

async function startListening(mode = REALTIME_MODE_CONTINUOUS) {
  appendLog("app", mode === REALTIME_MODE_PUSH_TO_TALK
    ? "Starting recorded push-to-talk session..."
    : "Starting realtime session...");
  session.activeMode = mode;
  setListeningState(true, mode);
  resetRealtimeTranscriptGate();
  try {
    const eventHistory = await fetchEventHistory();
    primeBehaviourCursor(eventHistory || []);
    if (mode === REALTIME_MODE_PUSH_TO_TALK) {
      connectBehaviourStream();
      try {
        await playLatestAssistantSpeechForPushToTalk();
      } catch (error) {
        appendLog("realtime", "Initial assistant audio failed: " + error.message);
      }
      updatePushToTalkUi();
      appendLog("realtime", "Recorded push-to-talk ready.");
      return;
    }
    await setupRealtimeConnection(mode);
    await waitForDataChannelOpen();
    connectBehaviourStream();
    updatePushToTalkUi();
  } catch (error) {
    appendLog("app", "Failed to start: " + error.message);
    await stopListening();
  }
}

async function stopListening() {
  appendLog("app", "Stopping realtime session...");
  const stoppingMode = session.activeMode;
  setListeningState(false, stoppingMode);
  gifState = "idle";
  if (gifSwapTimeout) {
    clearTimeout(gifSwapTimeout);
    gifSwapTimeout = null;
  }
  const audio = document.getElementById("assistant_audio");
  if (audio) {
    audio.pause();
    audio.removeAttribute("src");
    audio.srcObject = null;
    audio.load();
  }
  if (dataChannel) {
    dataChannel.close();
    dataChannel = null;
  }
  if (behaviourSource) {
    behaviourSource.close();
    behaviourSource = null;
  }
  if (behaviourReconnectTimer) {
    clearTimeout(behaviourReconnectTimer);
    behaviourReconnectTimer = null;
  }
  behaviourReconnectAttempt = 0;
  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }
  if (micStream) {
    micStream.getTracks().forEach((track) => track.stop());
    micStream = null;
  }
  if (realtimeCallId) {
    closeRealtimeCall(realtimeCallId);
    realtimeCallId = null;
  }
  session.activeMode = null;
  activeTurnDetection = null;
  pushToTalkActive = false;
  realtimeResponseActive = false;
  cleanupRecordedTurn(true);
  resetRealtimeTranscriptGate();
}

async function loadAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) {
    appendLog("app", "Unable to load agent info.");
    return;
  }
  const data = await response.json();
  document.getElementById("agent_name").textContent = data.name;
  setActiveStatus(data.active);
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

async function fetchEventHistory() {
  const response = await fetch(`/${session.agentId}/eventhistory`);
  if (!response.ok) {
    appendLog("app", "Unable to load event history.");
    return [];
  }
  return await response.json();
}

async function setupRealtimeConnection(mode = REALTIME_MODE_CONTINUOUS) {
  const settings = currentRealtimeSettings(mode);
  activeTurnDetection = settings.turnDetection || "server_vad";
  peerConnection = new RTCPeerConnection();
  peerConnection.ontrack = (event) => {
    const audio = document.getElementById("assistant_audio");
    audio.srcObject = event.streams[0];
  };

  dataChannel = peerConnection.createDataChannel("oai-events");
  dataChannel.addEventListener("message", handleRealtimeEvent);

  micStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
  });
  setMicEnabled(true);
  micStream.getTracks().forEach((track) => peerConnection.addTrack(track, micStream));

  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  const call = await createRealtimeCall(offer.sdp, settings);
  realtimeCallId = call.callId || null;
  await peerConnection.setRemoteDescription({ type: "answer", sdp: call.sdp });
  appendLog("realtime", "WebRTC session established.");
}

async function createRealtimeCall(offerSdp, settings = currentRealtimeSettings()) {
  const params = new URLSearchParams();
  if (settings.voice) {
    params.set("voice", settings.voice);
  }
  params.set("turnDetection", settings.turnDetection || "server_vad");
  params.set("generateComplement", String(shouldGenerateSideBehaviour()));
  const response = await fetch(`/${session.agentId}/realtime/call?${params.toString()}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/sdp",
    },
    body: offerSdp,
  });
  if (!response.ok) {
    throw new Error("Realtime call creation failed.");
  }
  return await response.json();
}

function currentRealtimeSettings(mode = session.activeMode || REALTIME_MODE_CONTINUOUS) {
  if (mode === REALTIME_MODE_PUSH_TO_TALK) {
    return {
      voice: sessionSettings.voice,
    };
  }
  return {
    voice: sessionSettings.voice,
    turnDetection: sessionSettings.turnDetection || "server_vad",
  };
}

function closeRealtimeCall(callId) {
  fetch(`/realtime/calls/${encodeURIComponent(callId)}`, { method: "DELETE" }).catch(() => {
  });
}

function waitForDataChannelOpen(timeoutMs = 5000) {
  if (dataChannel && dataChannel.readyState === "open") {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error("Data channel not ready."));
    }, timeoutMs);
    const handleOpen = () => {
      clearTimeout(timeout);
      dataChannel.removeEventListener("open", handleOpen);
      resolve();
    };
    dataChannel.addEventListener("open", handleOpen);
  });
}

function handleRealtimeEvent(event) {
  let data = null;
  try {
    data = JSON.parse(event.data);
  } catch (err) {
    appendLog("realtime", "Non-JSON event received.");
    return;
  }

  if (data.type === "input_audio_buffer.committed") {
    rememberInputItem(data.item_id);
  } else if (data.type === "input_audio_buffer.cleared") {
    clearPendingInputItems();
  } else if (data.type === "conversation.item.input_audio_transcription.completed") {
    queueTranscriptCandidate(data);
  } else if (data.type === "response.created") {
    assistantAudioSeen = false;
    assistantTranscriptBuffer = "";
    realtimeResponseActive = true;
  } else if (data.type === "conversation.item.input_audio_transcription.delta") {
    const partial = data.delta || data.transcript || "";
    if (partial.trim() && !shouldIgnoreTranscriptPreview(data, partial)) {
      document.getElementById("user_transcript").textContent = partial;
    }
  } else if (data.type === "response.output_audio_transcript.delta") {
    assistantAudioSeen = true;
    assistantTranscriptBuffer += data.delta || "";
    document.getElementById("assistant_transcript").textContent = assistantTranscriptBuffer;
  } else if (data.type === "response.output_text.delta") {
    assistantTranscriptBuffer += data.delta || "";
    document.getElementById("assistant_transcript").textContent = assistantTranscriptBuffer;
  } else if (data.type === "response.output_audio_transcript.done") {
    assistantAudioSeen = false;
    if (assistantTranscriptBuffer.trim()) {
      document.getElementById("assistant_transcript").textContent = assistantTranscriptBuffer;
      assistantTranscriptBuffer = "";
      setGifState("idle");
    }
  } else if (data.type === "response.output_text.done") {
    if (!assistantAudioSeen) {
      if (assistantTranscriptBuffer.trim()) {
        document.getElementById("assistant_transcript").textContent = assistantTranscriptBuffer;
        assistantTranscriptBuffer = "";
        setGifState("idle");
      }
    }
  } else if (data.type === "response.done" || data.type === "response.cancelled") {
    realtimeResponseActive = false;
  }
}

function rememberInputItem(itemId) {
  if (!itemId || processedInputItemIds.has(itemId)) {
    return;
  }
  pendingInputItemIds.add(itemId);
}

function queueTranscriptCandidate(data) {
  const transcript = data.transcript || "";
  if (!transcript.trim()) {
    markTranscriptItemsProcessed([{ itemId: data.item_id || "" }]);
    return;
  }
  transcriptCandidates.push({
    itemId: data.item_id || "",
    eventId: data.event_id || "",
    transcript: transcript.trim(),
  });
  if (!transcriptFlushTimer) {
    transcriptFlushTimer = setTimeout(flushTranscriptCandidates, TRANSCRIPT_BATCH_DELAY_MS);
  }
}

function flushTranscriptCandidates() {
  const candidates = transcriptCandidates.slice();
  transcriptCandidates = [];
  transcriptFlushTimer = null;
  const selected = selectTranscriptCandidate(candidates);
  markTranscriptItemsProcessed(candidates);
  if (!selected) {
    appendLog("realtime", "Ignored noisy or duplicate user transcript.");
    return;
  }
  document.getElementById("user_transcript").textContent = selected.transcript;
  appendLog("realtime", "User transcript completed.");
  setGifState("thinking");
}

function selectTranscriptCandidate(candidates) {
  let selected = null;
  for (const candidate of candidates) {
    if (!candidate.transcript.trim() || transcriptItemAlreadyProcessed(candidate) ||
      !transcriptItemMatchesPendingCommit(candidate) || isLikelyAsrHallucination(candidate.transcript)) {
      continue;
    }
    selected = candidate;
  }
  return selected;
}

function shouldIgnoreTranscriptPreview(data, transcript) {
  const candidate = { itemId: data.item_id || "", transcript: transcript || "" };
  return transcriptItemAlreadyProcessed(candidate) || !transcriptItemMatchesPendingCommit(candidate) ||
    isLikelyAsrHallucination(transcript);
}

function transcriptItemAlreadyProcessed(candidate) {
  return !!candidate.itemId && processedInputItemIds.has(candidate.itemId);
}

function transcriptItemMatchesPendingCommit(candidate) {
  return !candidate.itemId || pendingInputItemIds.size === 0 || pendingInputItemIds.has(candidate.itemId);
}

function markTranscriptItemsProcessed(candidates) {
  candidates.forEach((candidate) => {
    if (!candidate.itemId) {
      return;
    }
    processedInputItemIds.add(candidate.itemId);
    pendingInputItemIds.delete(candidate.itemId);
  });
}

function clearQueuedTranscriptCandidates() {
  transcriptCandidates = [];
  if (transcriptFlushTimer) {
    clearTimeout(transcriptFlushTimer);
    transcriptFlushTimer = null;
  }
  clearPendingInputItems();
}

function clearPendingInputItems() {
  pendingInputItemIds.clear();
}

function resetRealtimeTranscriptGate() {
  clearQueuedTranscriptCandidates();
  processedInputItemIds = new Set();
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

function shouldGenerateSideBehaviour() {
  const checkbox = document.getElementById("generate_side_behaviour");
  if (!checkbox) {
    return true;
  }
  return checkbox.checked;
}

function connectBehaviourStream() {
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
    if (!session.isListening) {
      return;
    }
    if (event.lastEventId) {
      lastBackendBehaviourEventId = event.lastEventId;
    }
    let data = null;
    try {
      data = JSON.parse(event.data);
    } catch (_) {
      return;
    }
    if (!data) {
      return;
    }
    if (data.createdDate && data.createdDate === lastBackendBehaviourCreatedDate) {
      return;
    }
    const speech = getEventSpeech(data);
    if (!speech || !speech.trim()) {
      return;
    }
    if (speech === lastBackendSpeech) {
      return;
    }
    lastBackendBehaviourCreatedDate = data.createdDate || null;
    lastBackendSpeech = speech;
    document.getElementById("assistant_transcript").textContent = speech;
    appendLog("policy", "Backend behaviour persisted.");
  });
  behaviourSource.onerror = () => {
    if (behaviourSource) {
      behaviourSource.close();
      behaviourSource = null;
    }
    appendLog("policy", "Behaviour stream disconnected.");
    scheduleBehaviourReconnect();
  };
}

function behaviourStreamUrl() {
  let url = `/${session.agentId}/behaviour/stream`;
  if (lastBackendBehaviourEventId) {
    url += `?lastEventId=${encodeURIComponent(lastBackendBehaviourEventId)}`;
  }
  return url;
}

function scheduleBehaviourReconnect() {
  if (isPageUnloading || !session.isListening || behaviourReconnectTimer) {
    return;
  }
  const delay = nextBehaviourReconnectDelayMs();
  behaviourReconnectTimer = setTimeout(() => {
    behaviourReconnectTimer = null;
    connectBehaviourStream();
  }, delay);
}

function nextBehaviourReconnectDelayMs() {
  const base = Math.min(BEHAVIOUR_RECONNECT_MAX_MS, BEHAVIOUR_RECONNECT_MIN_MS * Math.pow(2, behaviourReconnectAttempt));
  behaviourReconnectAttempt += 1;
  const jitterFactor = 1 + ((Math.random() * 2 - 1) * BEHAVIOUR_RECONNECT_JITTER);
  return Math.max(BEHAVIOUR_RECONNECT_MIN_MS, Math.floor(base * jitterFactor));
}

function cleanupBehaviourStream() {
  isPageUnloading = true;
  if (behaviourReconnectTimer) {
    clearTimeout(behaviourReconnectTimer);
    behaviourReconnectTimer = null;
  }
  if (behaviourSource) {
    behaviourSource.close();
    behaviourSource = null;
  }
  behaviourReconnectAttempt = 0;
}

function primeBehaviourCursor(eventHistory) {
  if (!Array.isArray(eventHistory) || eventHistory.length === 0) {
    return;
  }
  for (let i = eventHistory.length - 1; i >= 0; i--) {
    const event = eventHistory[i];
    if (!event || event.type !== "resp.behaviour_plan" || event.actor !== "assistant") {
      continue;
    }
    lastBackendBehaviourCreatedDate = event.createdDate || null;
    const speech = getEventSpeech(event);
    if (speech && speech.trim()) {
      lastBackendSpeech = speech;
    }
    return;
  }
}

async function resetAgent() {
  if (!confirm("Reset the event history?")) {
    return;
  }
  const response = await fetch(`/${session.agentId}/reset`, {
    method: "DELETE",
  });
  if (!response.ok) {
    appendLog("app", "Reset failed.");
    return;
  }
  const data = await response.json();
  await stopListening();
  setActiveStatus(data.active);
  document.getElementById("user_transcript").textContent = "";
  document.getElementById("assistant_transcript").textContent = "";
  assistantTranscriptBuffer = "";
}

function applySessionSettings() {
  if (!session.isListening) {
    return;
  }
  appendLog("realtime", "Restart realtime to apply session settings.");
}

function updatePushToTalkUi() {
  const pushToTalkButton = document.getElementById("push_to_talk");
  const isManualActive = session.isListening && session.activeMode === REALTIME_MODE_PUSH_TO_TALK;
  if (pushToTalkButton) {
    pushToTalkButton.disabled = !isManualActive;
    if (!isManualActive) {
      pushToTalkButton.classList.remove("is-pressed");
    }
  }
  if (isManualActive) {
    enableSpaceKeyPushToTalk();
  } else {
    disableSpaceKeyPushToTalk();
  }
}

function startPushToTalk(event) {
  if (event) {
    event.preventDefault();
  }
  const button = document.getElementById("push_to_talk");
  if (!session.isListening || session.activeMode !== REALTIME_MODE_PUSH_TO_TALK || pushToTalkActive) {
    return;
  }
  pushToTalkActive = true;
  if (button) {
    button.classList.add("is-pressed");
  }
  startRecordedTurn();
}

function stopPushToTalk(event) {
  if (event) {
    event.preventDefault();
  }
  const button = document.getElementById("push_to_talk");
  if (!pushToTalkActive) {
    return;
  }
  pushToTalkActive = false;
  if (button) {
    button.classList.remove("is-pressed");
  }
  stopRecordedTurn();
}

function enableSpaceKeyPushToTalk() {
  if (spaceKeyBindingActive) {
    return;
  }
  window.addEventListener("keydown", handleSpaceKeyDown);
  window.addEventListener("keyup", handleSpaceKeyUp);
  spaceKeyBindingActive = true;
}

function disableSpaceKeyPushToTalk() {
  if (!spaceKeyBindingActive) {
    return;
  }
  window.removeEventListener("keydown", handleSpaceKeyDown);
  window.removeEventListener("keyup", handleSpaceKeyUp);
  spaceKeyBindingActive = false;
}

function handleSpaceKeyDown(event) {
  if (event.code !== "Space") {
    return;
  }
  if (shouldIgnoreSpace(event)) {
    return;
  }
  event.preventDefault();
  if (event.repeat) {
    return;
  }
  startPushToTalk();
}

function handleSpaceKeyUp(event) {
  if (event.code !== "Space") {
    return;
  }
  if (shouldIgnoreSpace(event)) {
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

function sendRealtimeEvent(payload) {
  if (!dataChannel || dataChannel.readyState !== "open") {
    return;
  }
  dataChannel.send(JSON.stringify(payload));
}

async function startRecordedTurn() {
  if (!window.MediaRecorder || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    appendLog("realtime", "Recorded push-to-talk is not supported by this browser.");
    pushToTalkActive = false;
    document.getElementById("push_to_talk").classList.remove("is-pressed");
    return;
  }
  try {
    cleanupRecordedTurn(true);
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });
    const mimeType = preferredRecordedAudioMimeType();
    const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    recordedStream = stream;
    mediaRecorder = recorder;
    recordedChunks = [];
    recordedMimeType = recorder.mimeType || mimeType || "audio/webm";
    discardRecordedTurn = false;
    recorder.addEventListener("dataavailable", (event) => {
      if (event.data && event.data.size > 0) {
        recordedChunks.push(event.data);
      }
    });
    recorder.addEventListener("stop", handleRecordedTurnStopped);
    recorder.start();
    appendLog("realtime", "Recording turn.");
  } catch (error) {
    appendLog("realtime", "Recording failed: " + error.message);
    pushToTalkActive = false;
    document.getElementById("push_to_talk").classList.remove("is-pressed");
    cleanupRecordedTurn(true);
  }
}

function stopRecordedTurn() {
  if (!mediaRecorder || mediaRecorder.state === "inactive") {
    cleanupRecordedTurn(true);
    return;
  }
  mediaRecorder.stop();
}

async function handleRecordedTurnStopped() {
  const discard = discardRecordedTurn;
  const chunks = recordedChunks.slice();
  const mimeType = recordedMimeType || "audio/webm";
  cleanupRecordedTurn(false);
  if (discard) {
    return;
  }
  const blob = new Blob(chunks, { type: mimeType });
  if (blob.size === 0) {
    appendLog("realtime", "Recorded turn was empty.");
    return;
  }
  await submitRecordedSpeechTurn(blob);
}

function cleanupRecordedTurn(discard) {
  discardRecordedTurn = !!discard;
  if (mediaRecorder && mediaRecorder.state !== "inactive") {
    mediaRecorder.stop();
    return;
  }
  if (recordedStream) {
    recordedStream.getTracks().forEach((track) => track.stop());
    recordedStream = null;
  }
  mediaRecorder = null;
  recordedChunks = [];
  recordedMimeType = "";
}

async function submitRecordedSpeechTurn(blob) {
  const form = new FormData();
  form.append("audio", blob, recordedAudioFilename(blob.type));
  const params = new URLSearchParams();
  if (sessionSettings.voice) {
    params.set("voice", sessionSettings.voice);
  }
  params.set("generateComplement", String(shouldGenerateSideBehaviour()));
  appendLog("realtime", "Uploading recorded turn.");
  setGifState("thinking");
  try {
    const response = await fetch(`/${session.agentId}/speech-turn?${params.toString()}`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      appendLog("realtime", `Recorded turn failed: ${response.status}`);
      setGifState("idle");
      return;
    }
    const data = await response.json();
    handleRecordedSpeechTurnResponse(data);
  } catch (error) {
    appendLog("realtime", "Recorded turn failed: " + error.message);
    setGifState("idle");
  }
}

async function playLatestAssistantSpeechForPushToTalk() {
  const params = new URLSearchParams();
  if (sessionSettings.voice) {
    params.set("voice", sessionSettings.voice);
  }
  const response = await fetch(`/${session.agentId}/speech/latest?${params.toString()}`, {
    method: "POST",
  });
  if (response.status === 404) {
    appendLog("realtime", "No stored assistant speech to read.");
    return;
  }
  if (!response.ok) {
    appendLog("realtime", `Initial assistant audio failed: ${response.status}`);
    return;
  }
  const data = await response.json();
  const speech = typeof data.speech === "string" ? data.speech.trim() : "";
  if (speech) {
    document.getElementById("assistant_transcript").textContent = speech;
    lastBackendSpeech = speech;
  }
  playRecordedSpeechAudio(data);
}

function handleRecordedSpeechTurnResponse(data) {
  if (!data) {
    return;
  }
  const transcript = typeof data.transcript === "string" ? data.transcript.trim() : "";
  if (transcript) {
    document.getElementById("user_transcript").textContent = transcript;
    appendLog("realtime", "Recorded user transcript completed.");
  }
  if (data.response) {
    if (typeof data.response.active === "boolean") {
      setActiveStatus(data.response.active);
    }
    const speech = getEventSpeech(data.response.responseEvent);
    if (speech && speech.trim()) {
      document.getElementById("assistant_transcript").textContent = speech;
      lastBackendSpeech = speech;
    }
  }
  playRecordedSpeechAudio(data);
  setGifState("idle");
}

function playRecordedSpeechAudio(data) {
  if (!data || !data.audioBase64) {
    return;
  }
  const audio = document.getElementById("assistant_audio");
  audio.pause();
  audio.srcObject = null;
  audio.src = `data:${data.audioContentType || "audio/mpeg"};base64,${data.audioBase64}`;
  audio.load();
  audio.play().catch(() => {
    appendLog("realtime", "Assistant audio ready; browser blocked autoplay.");
  });
}

function preferredRecordedAudioMimeType() {
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/mp4",
  ];
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) || "";
}

function recordedAudioFilename(contentType) {
  if (contentType && contentType.includes("mp4")) {
    return "speech-turn.mp4";
  }
  return "speech-turn.webm";
}

function setMicEnabled(enabled) {
  if (!micStream) {
    return;
  }
  micStream.getAudioTracks().forEach((track) => {
    track.enabled = enabled;
  });
}

function setGifState(state) {
  gifState = state;
  const gif = document.getElementById("realtime_gif");
  if (!gif) {
    return;
  }
  if (!session.isListening) {
    if (gifSwapTimeout) {
      clearTimeout(gifSwapTimeout);
      gifSwapTimeout = null;
    }
    gif.classList.add("is-hidden");
    return;
  }
  const source = gifSources[state] || gifSources.idle;
  const currentSource = gif.dataset.source || gif.getAttribute("src") || "";
  if (currentSource === source || currentSource.endsWith(source)) {
    gif.classList.remove("is-hidden");
    return;
  }
  if (gifSwapTimeout) {
    clearTimeout(gifSwapTimeout);
  }
  const isHidden = gif.classList.contains("is-hidden");
  if (!isHidden) {
    gif.classList.add("is-hidden");
  }
  gifSwapTimeout = setTimeout(() => {
    gif.src = source;
    gif.dataset.source = source;
    if (session.isListening) {
      gif.classList.remove("is-hidden");
    }
    gifSwapTimeout = null;
  }, isHidden ? 0 : gifFadeMs);
}

function appendLog(source, message) {
  const prefix = source ? `[${source}] ` : "";
  console.log(`${prefix}${message}`);
}

function getEventSpeech(event) {
  if (!event) {
    return null;
  }
  if (!event.payload) {
    return null;
  }
  try {
    const plan = JSON.parse(event.payload);
    if (plan && typeof plan.speech === "string" && plan.speech.trim()) {
      return plan.speech;
    }
  } catch (_) {
    return null;
  }
  return null;
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
