let session = {
  agentId: null,
  isListening: false,
  agentActive: null,
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
  document.getElementById("toggle_listen").addEventListener("click", toggleListening);
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
      updatePushToTalkUi();
      if (session.isListening) {
        appendLog("realtime", "Restart realtime to apply mode changes.");
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

function setListeningState(isListening) {
  session.isListening = isListening;
  const button = document.getElementById("toggle_listen");
  const status = document.getElementById("listen_status");
  if (isListening) {
    button.innerHTML = '<i class="bi bi-mic-mute-fill me-2"></i>Stop';
    status.textContent = "Listening";
    status.className = "status-pill is-listening";
    button.classList.add("is-listening");
    updatePushToTalkUi();
    setGifState("idle");
  } else {
    button.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start';
    status.textContent = "Idle";
    status.className = "status-pill is-idle";
    button.classList.remove("is-listening");
    updatePushToTalkUi();
    const gif = document.getElementById("realtime_gif");
    if (gif) {
      gif.classList.add("is-hidden");
    }
  }
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

async function toggleListening() {
  if (!session.isListening) {
    await startListening();
  } else {
    await stopListening();
  }
}

async function startListening() {
  appendLog("app", "Starting realtime session...");
  setListeningState(true);
  resetRealtimeTranscriptGate();
  try {
    const eventHistory = await fetchEventHistory();
    primeBehaviourCursor(eventHistory || []);
    await setupRealtimeConnection();
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
  setListeningState(false);
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
  pushToTalkActive = false;
  realtimeResponseActive = false;
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

async function setupRealtimeConnection() {
  peerConnection = new RTCPeerConnection();
  peerConnection.ontrack = (event) => {
    const audio = document.getElementById("assistant_audio");
    audio.srcObject = event.streams[0];
  };

  dataChannel = peerConnection.createDataChannel("oai-events");
  dataChannel.addEventListener("message", handleRealtimeEvent);

  micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  micStream.getTracks().forEach((track) => peerConnection.addTrack(track, micStream));

  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  const call = await createRealtimeCall(offer.sdp);
  realtimeCallId = call.callId || null;
  await peerConnection.setRemoteDescription({ type: "answer", sdp: call.sdp });
  appendLog("realtime", "WebRTC session established.");
}

async function createRealtimeCall(offerSdp) {
  const params = new URLSearchParams();
  if (sessionSettings.voice) {
    params.set("voice", sessionSettings.voice);
  }
  params.set("turnDetection", sessionSettings.turnDetection || "server_vad");
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
    clearQueuedTranscriptCandidates();
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
  const manualControls = document.getElementById("manual_controls");
  const pushToTalkButton = document.getElementById("push_to_talk");
  const isManual = sessionSettings.turnDetection === "none";
  if (manualControls) {
    manualControls.classList.toggle("d-none", !isManual);
  }
  if (pushToTalkButton) {
    pushToTalkButton.disabled = !session.isListening || !isManual;
    if (!session.isListening || !isManual) {
      pushToTalkButton.classList.remove("is-pressed");
    }
  }
  if (isManual && session.isListening) {
    enableSpaceKeyPushToTalk();
  } else {
    disableSpaceKeyPushToTalk();
  }
  if (session.isListening) {
    if (isManual) {
      setMicEnabled(false);
    } else {
      setMicEnabled(true);
    }
  }
}

function startPushToTalk(event) {
  if (event) {
    event.preventDefault();
  }
  const button = document.getElementById("push_to_talk");
  if (!session.isListening || sessionSettings.turnDetection !== "none" || pushToTalkActive) {
    return;
  }
  pushToTalkActive = true;
  if (button) {
    button.classList.add("is-pressed");
  }
  prepareManualTurn();
  setMicEnabled(true);
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
  setMicEnabled(false);
  commitManualTurn();
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

function commitManualTurn() {
  if (!dataChannel || dataChannel.readyState !== "open") {
    return;
  }
  dataChannel.send(
    JSON.stringify({
      type: "input_audio_buffer.commit",
    })
  );
}

function prepareManualTurn() {
  if (!dataChannel || dataChannel.readyState !== "open") {
    return;
  }
  if (realtimeResponseActive) {
    sendRealtimeEvent({ type: "response.cancel" });
    sendRealtimeEvent({ type: "output_audio_buffer.clear" });
    realtimeResponseActive = false;
  }
  sendRealtimeEvent({ type: "input_audio_buffer.clear" });
}

function sendRealtimeEvent(payload) {
  if (!dataChannel || dataChannel.readyState !== "open") {
    return;
  }
  dataChannel.send(JSON.stringify(payload));
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
