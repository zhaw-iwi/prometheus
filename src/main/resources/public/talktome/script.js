const TALK_TO_ME_AGENT_KEY = "core.talk_to_me";
const TALK_TO_ME_PROFILE_TAG = "utility.talk_to_me";
const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const ACCESS_CODE_STORAGE_KEY = "prometheus.talktome.accessCode";
const THEME_STORAGE_KEY = "prometheus.talktome.theme";
const VOICE_STORAGE_KEY = "prometheus.talktome.voice";
const SPEED_STORAGE_KEY = "prometheus.talktome.outputSpeed";
const SPEAKER_STORAGE_KEY = "prometheus.talktome.speaker";
const MAX_TEXT_CODE_POINTS = 2000;
const DEFAULT_SPEECH_TEXT = [
  "Love is patient, love is kind.",
  "It does not envy, it does not boast, it is not proud.",
  "It does not dishonor others, it is not self-seeking, it is not easily angered,",
  "it keeps no record of wrongs.",
  "Love does not delight in evil but rejoices with the truth.",
  "It always protects, always trusts, always hopes, always perseveres.",
].join(" ");

const state = {
  accessCode: null,
  agentTypes: [],
  agents: [],
  selectedAgentId: null,
  connectedAgentId: null,
  responseActive: false,
  sessionReady: false,
};

const realtime = {
  peerConnection: null,
  dataChannel: null,
  callId: null,
  sessionReadyResolver: null,
  transcript: "",
  requestedText: "",
};

document.addEventListener("DOMContentLoaded", () => {
  bindControls();
  loadPreferences();
  updateThemeButtons();
  setSpeechText(DEFAULT_SPEECH_TEXT);
  renderAgents();
  refreshSpeakers({ silent: true });

  const storedCode = sessionStorage.getItem(ACCESS_CODE_STORAGE_KEY);
  if (storedCode) {
    document.getElementById("access_code_input").value = storedCode;
  }
});

function bindControls() {
  document.getElementById("submit_access_code").addEventListener("click", () => submitAccessCode());
  document.getElementById("access_code_input").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      submitAccessCode();
    }
  });
  document.getElementById("clear_access_code").addEventListener("click", () => clearAccessSession());
  document.getElementById("create_agent").addEventListener("click", () => createAgent());
  document.getElementById("connect_agent").addEventListener("click", () => connectSelectedAgent());
  document.getElementById("disconnect_agent").addEventListener("click", () => disconnectAgent());
  document.getElementById("delete_agent").addEventListener("click", () => deleteSelectedAgent());
  document.getElementById("agent_select").addEventListener("change", async (event) => {
    if (state.connectedAgentId) {
      await disconnectAgent();
    }
    state.selectedAgentId = event.target.value || null;
    refreshLifecycleControls();
  });
  document.getElementById("speech_text").addEventListener("input", updateCharacterCount);
  document.getElementById("load_default_text").addEventListener("click", () => {
    setSpeechText(DEFAULT_SPEECH_TEXT, { focus: true });
  });
  document.getElementById("clear_speech_text").addEventListener("click", () => {
    setSpeechText("", { focus: true });
  });
  document.getElementById("speak_text").addEventListener("click", () => speakText());
  document.getElementById("stop_speech").addEventListener("click", stopSpeech);
  document.getElementById("refresh_speakers").addEventListener("click", () => refreshSpeakers());
  document.getElementById("speaker_select").addEventListener("change", saveSpeakerSelection);
  document.getElementById("voice_select").addEventListener("change", sessionSettingChanged);
  document.getElementById("speed_select").addEventListener("change", sessionSettingChanged);
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    button.addEventListener("click", toggleTheme);
  });
  window.addEventListener("beforeunload", closeRealtimeCallForUnload);
}

function loadPreferences() {
  selectStoredValue("voice_select", VOICE_STORAGE_KEY, "alloy");
  selectStoredValue("speed_select", SPEED_STORAGE_KEY, "1");
  const speaker = localStorage.getItem(SPEAKER_STORAGE_KEY);
  if (speaker) {
    document.getElementById("speaker_select").dataset.preferredDeviceId = speaker;
  }
}

function selectStoredValue(id, key, fallback) {
  const select = document.getElementById(id);
  const stored = localStorage.getItem(key) || fallback;
  if (Array.from(select.options).some((option) => option.value === stored)) {
    select.value = stored;
  }
}

async function submitAccessCode() {
  const code = document.getElementById("access_code_input").value.trim();
  if (!code) {
    setStatus("access_code_status", "Access code required.", "error");
    return;
  }
  const button = document.getElementById("submit_access_code");
  button.disabled = true;
  try {
    const session = await fetchJson("/demo/session", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ accessCode: code }),
    });
    state.accessCode = session.accessCode || code;
    state.agentTypes = Array.isArray(session.agentTypes) ? session.agentTypes : [];
    state.agents = Array.isArray(session.agents) ? session.agents : [];
    state.selectedAgentId = null;
    sessionStorage.setItem(ACCESS_CODE_STORAGE_KEY, state.accessCode);
    document.getElementById("active_access_code").textContent = `Access ${state.accessCode}`;
    showApplication(true);
    renderAgents();
    setStatus("access_code_status", "Access accepted.", "success");
  } catch (error) {
    sessionStorage.removeItem(ACCESS_CODE_STORAGE_KEY);
    setStatus("access_code_status", readableError(error, "Access code rejected."), "error");
  } finally {
    button.disabled = false;
  }
}

async function clearAccessSession() {
  await disconnectAgent({ silent: true });
  sessionStorage.removeItem(ACCESS_CODE_STORAGE_KEY);
  state.accessCode = null;
  state.agentTypes = [];
  state.agents = [];
  state.selectedAgentId = null;
  document.getElementById("access_code_input").value = "";
  setStatus("access_code_status", "", "");
  showApplication(false);
  renderAgents();
  document.getElementById("access_code_input").focus();
}

function showApplication(show) {
  document.getElementById("access_screen").hidden = show;
  document.getElementById("talktome_shell").hidden = !show;
}

function talkToMeAllowed() {
  return state.agentTypes.some((type) => type && type.key === TALK_TO_ME_AGENT_KEY);
}

function talkToMeAgents() {
  return state.agents.filter((agent) => {
    const tags = agent && agent.interactionProfile && agent.interactionProfile.profileTags;
    return Array.isArray(tags) && tags.includes(TALK_TO_ME_PROFILE_TAG);
  });
}

function renderAgents() {
  const select = document.getElementById("agent_select");
  const agents = talkToMeAgents();
  select.replaceChildren();
  if (!agents.length) {
    select.appendChild(new Option("No Talk to Me instances", ""));
    state.selectedAgentId = null;
  } else {
    agents.forEach((agent, index) => {
      const shortId = String(agent.id || "").slice(0, 8);
      select.appendChild(new Option(`${agent.name || "Talk to Me"} ${index + 1} · ${shortId}`, agent.id));
    });
    const selectionStillVisible = agents.some((agent) => agent.id === state.selectedAgentId);
    state.selectedAgentId = selectionStillVisible ? state.selectedAgentId : agents[0].id;
    select.value = state.selectedAgentId;
  }

  if (state.accessCode && !talkToMeAllowed()) {
    setStatus("agent_detail", "This access code does not permit the core.talk_to_me agent type. Ask an administrator to assign it.", "error");
  } else if (state.accessCode && !agents.length) {
    setStatus("agent_detail", "No instance yet. Create one to begin.", "");
  } else if (agents.length) {
    setStatus("agent_detail", `${agents.length} scoped instance${agents.length === 1 ? "" : "s"} available.`, "success");
  } else {
    setStatus("agent_detail", "", "");
  }
  refreshLifecycleControls();
}

async function refreshSession() {
  const session = await fetchJson("/demo/session", {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify({ accessCode: state.accessCode }),
  });
  state.agentTypes = Array.isArray(session.agentTypes) ? session.agentTypes : [];
  state.agents = Array.isArray(session.agents) ? session.agents : [];
  renderAgents();
}

async function createAgent() {
  if (!state.accessCode || !talkToMeAllowed()) {
    setStatus("agent_detail", "This access code cannot create Talk to Me instances.", "error");
    return;
  }
  setLifecycleBusy(true);
  try {
    const created = await scopedFetchJson("/demo/agents", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ agentDefinitionKey: TALK_TO_ME_AGENT_KEY }),
    });
    state.agents.push(created);
    state.selectedAgentId = created.id;
    renderAgents();
    setStatus("agent_detail", "Instance created. Connect when you are ready.", "success");
  } catch (error) {
    setStatus("agent_detail", readableError(error, "Unable to create the instance."), "error");
  } finally {
    setLifecycleBusy(false);
  }
}

async function connectSelectedAgent() {
  const agentId = state.selectedAgentId;
  if (!agentId || state.connectedAgentId) {
    return;
  }
  setLifecycleBusy(true);
  setRealtimeStatus("Connecting", "busy");
  setStatus("speech_status", "Opening a receive-only Realtime session.", "");
  try {
    await setupRealtimeConnection(agentId);
    state.connectedAgentId = agentId;
    setRealtimeStatus("Connected", "live");
    setStatus("speech_status", "Connected. Enter text and choose Speak.", "success");
  } catch (error) {
    await closeRealtimeResources();
    setRealtimeStatus("Connection failed", "idle");
    setStatus("speech_status", readableError(error, "Realtime connection failed."), "error");
  } finally {
    setLifecycleBusy(false);
    refreshLifecycleControls();
  }
}

async function disconnectAgent(options = {}) {
  const wasConnected = !!state.connectedAgentId;
  await closeRealtimeResources();
  state.connectedAgentId = null;
  state.responseActive = false;
  setRealtimeStatus("Offline", "idle");
  if (wasConnected && !options.silent) {
    setStatus("speech_status", "Disconnected. The instance remains available until you delete it.", "");
  }
  refreshLifecycleControls();
}

async function deleteSelectedAgent() {
  const agentId = state.selectedAgentId;
  if (!agentId) {
    return;
  }
  if (!window.confirm("Delete this Talk to Me instance? This cannot be undone.")) {
    return;
  }
  setLifecycleBusy(true);
  try {
    if (state.connectedAgentId === agentId) {
      await disconnectAgent({ silent: true });
    }
    const response = await scopedFetch(`/demo/agents/${encodeURIComponent(agentId)}`, { method: "DELETE" });
    if (!response.ok) {
      throw responseError(response, "Instance deletion failed.");
    }
    state.selectedAgentId = null;
    await refreshSession();
    setStatus("agent_detail", "Instance deleted.", "success");
  } catch (error) {
    setStatus("agent_detail", readableError(error, "Unable to delete the instance."), "error");
  } finally {
    setLifecycleBusy(false);
  }
}

function refreshLifecycleControls() {
  const selected = !!state.selectedAgentId;
  const connected = !!state.connectedAgentId;
  const busy = document.getElementById("create_agent").dataset.busy === "true";
  document.getElementById("create_agent").disabled = busy || !state.accessCode || !talkToMeAllowed();
  document.getElementById("agent_select").disabled = busy || connected || talkToMeAgents().length === 0;
  document.getElementById("connect_agent").disabled = busy || !selected || connected;
  document.getElementById("disconnect_agent").disabled = busy || !connected;
  document.getElementById("delete_agent").disabled = busy || !selected;
  document.getElementById("speech_text").disabled = !connected;
  document.getElementById("load_default_text").disabled = !connected;
  document.getElementById("clear_speech_text").disabled = !connected;
  document.getElementById("voice_select").disabled = connected;
  document.getElementById("speed_select").disabled = connected;
  document.getElementById("speak_text").disabled = !connected || state.responseActive || !validSpeechText();
  document.getElementById("stop_speech").disabled = !connected || !state.responseActive;
  document.getElementById("connection_settings_guidance").textContent = connected
    ? "Voice and output speed are locked for this call. Disconnect to change them. Speaker changes apply immediately."
    : "Choose voice and output speed before connecting. Speaker can be changed at any time.";
  setAgentStatus(connected ? "Connected" : "Not connected", connected ? "live" : "idle");
}

function setLifecycleBusy(busy) {
  document.getElementById("create_agent").dataset.busy = String(busy);
  refreshLifecycleControls();
}

async function setupRealtimeConnection(agentId) {
  state.sessionReady = false;
  realtime.transcript = "";
  realtime.peerConnection = new RTCPeerConnection();
  realtime.peerConnection.addTransceiver("audio", { direction: "recvonly" });
  realtime.peerConnection.ontrack = (event) => {
    const audio = document.getElementById("assistant_audio");
    audio.srcObject = event.streams[0];
    applySelectedSpeaker().finally(() => audio.play().catch(() => {
      setStatus("speaker_status", "Audio is ready; press Play if browser autoplay is blocked.", "");
    }));
  };
  realtime.peerConnection.addEventListener("connectionstatechange", () => {
    const connectionState = realtime.peerConnection && realtime.peerConnection.connectionState;
    if (state.connectedAgentId && (connectionState === "failed" || connectionState === "disconnected")) {
      setRealtimeStatus("Connection interrupted", "idle");
      setStatus("speech_status", "Realtime transport was interrupted. Disconnect and reconnect.", "error");
    }
  });

  realtime.dataChannel = realtime.peerConnection.createDataChannel("oai-events");
  realtime.dataChannel.addEventListener("message", handleRealtimeEvent);
  const sessionReady = new Promise((resolve) => {
    realtime.sessionReadyResolver = resolve;
  });

  const offer = await realtime.peerConnection.createOffer();
  await realtime.peerConnection.setLocalDescription(offer);
  const call = await createRealtimeCall(agentId, offer.sdp);
  realtime.callId = call.callId || call.id || null;
  await realtime.peerConnection.setRemoteDescription({ type: "answer", sdp: call.sdp });
  await waitForDataChannelOpen();
  await withTimeout(sessionReady, 8000, "Realtime session did not become ready.");
}

async function createRealtimeCall(agentId, offerSdp) {
  const params = new URLSearchParams({
    voice: document.getElementById("voice_select").value,
    outputSpeed: document.getElementById("speed_select").value,
    turnDetection: "server_vad",
    generateComplement: "false",
  });
  const response = await scopedFetch(`/demo/agents/${encodeURIComponent(agentId)}/realtime/call?${params}`, {
    method: "POST",
    headers: { "Content-Type": "application/sdp" },
    body: offerSdp,
  });
  if (!response.ok) {
    throw responseError(response, "Realtime call creation failed.");
  }
  return response.json();
}

function waitForDataChannelOpen() {
  if (realtime.dataChannel && realtime.dataChannel.readyState === "open") {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Realtime data channel did not open.")), 5000);
    realtime.dataChannel.addEventListener("open", () => {
      clearTimeout(timeout);
      resolve();
    }, { once: true });
  });
}

function handleRealtimeEvent(event) {
  let data;
  try {
    data = JSON.parse(event.data);
  } catch (_) {
    return;
  }
  if (data.type === "session.updated") {
    state.sessionReady = true;
    if (realtime.sessionReadyResolver) {
      realtime.sessionReadyResolver();
      realtime.sessionReadyResolver = null;
    }
  } else if (data.type === "response.created") {
    state.responseActive = true;
    realtime.transcript = "";
    setRealtimeStatus("Speaking", "busy");
    refreshLifecycleControls();
  } else if (data.type === "response.output_audio_transcript.delta" || data.type === "response.output_text.delta") {
    realtime.transcript += data.delta || "";
    renderTranscript(realtime.transcript);
  } else if (data.type === "response.output_audio_transcript.done" || data.type === "response.output_text.done") {
    realtime.transcript = data.transcript || data.text || realtime.transcript;
    renderTranscript(realtime.transcript);
  } else if (data.type === "response.done") {
    finishRealtimeResponse(data.response || {});
  } else if (data.type === "response.cancelled" || data.type === "response.canceled") {
    finishRealtimeResponse({ status: "cancelled", status_details: { reason: "client_cancelled" } });
  } else if (data.type === "error") {
    state.responseActive = false;
    setRealtimeStatus("Realtime error", "idle");
    setStatus("speech_status", data.error && data.error.message ? data.error.message : "Realtime returned an error.", "error");
    refreshLifecycleControls();
  }
}

async function speakText() {
  const text = document.getElementById("speech_text").value;
  if (!state.connectedAgentId || !validSpeechText(text)) {
    updateCharacterCount();
    return;
  }
  state.responseActive = true;
  realtime.requestedText = text;
  realtime.transcript = "";
  setRealtimeStatus("Requested", "busy");
  setStatus("speech_status", "PROMETHEUS accepted the speech request.", "");
  renderTranscript("");
  refreshLifecycleControls();
  try {
    const response = await scopedFetch(`/demo/agents/${encodeURIComponent(state.connectedAgentId)}/acknowledge?profile=realtime_speech`, {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({
        type: "obs.user_utterance",
        actor: "user",
        kind: "observation",
        payload: text,
      }),
    });
    if (!response.ok) {
      throw responseError(response, "Speech request failed.");
    }
  } catch (error) {
    state.responseActive = false;
    setRealtimeStatus("Connected", "live");
    setStatus("speech_status", readableError(error, "Unable to submit the text."), "error");
    refreshLifecycleControls();
  }
}

function stopSpeech() {
  if (!realtime.dataChannel || realtime.dataChannel.readyState !== "open") {
    return;
  }
  realtime.dataChannel.send(JSON.stringify({ type: "response.cancel" }));
  realtime.dataChannel.send(JSON.stringify({ type: "output_audio_buffer.clear" }));
  state.responseActive = false;
  setRealtimeStatus("Connected", "live");
  setStatus("speech_status", "Speech stopped.", "");
  refreshLifecycleControls();
}

async function closeRealtimeResources() {
  const callId = realtime.callId;
  realtime.callId = null;
  realtime.sessionReadyResolver = null;
  realtime.requestedText = "";
  realtime.transcript = "";
  state.sessionReady = false;
  if (realtime.dataChannel) {
    realtime.dataChannel.close();
    realtime.dataChannel = null;
  }
  if (realtime.peerConnection) {
    realtime.peerConnection.close();
    realtime.peerConnection = null;
  }
  const audio = document.getElementById("assistant_audio");
  audio.pause();
  audio.srcObject = null;
  audio.removeAttribute("src");
  audio.load();
  if (callId) {
    await fetch(`/realtime/calls/${encodeURIComponent(callId)}`, { method: "DELETE" }).catch(() => undefined);
  }
}

function closeRealtimeCallForUnload() {
  if (realtime.callId) {
    fetch(`/realtime/calls/${encodeURIComponent(realtime.callId)}`, {
      method: "DELETE",
      keepalive: true,
    }).catch(() => undefined);
  }
  if (realtime.peerConnection) {
    realtime.peerConnection.close();
  }
}

function sessionSettingChanged() {
  localStorage.setItem(VOICE_STORAGE_KEY, document.getElementById("voice_select").value);
  localStorage.setItem(SPEED_STORAGE_KEY, document.getElementById("speed_select").value);
}

async function refreshSpeakers(options = {}) {
  const select = document.getElementById("speaker_select");
  if (!navigator.mediaDevices || typeof navigator.mediaDevices.enumerateDevices !== "function") {
    select.replaceChildren(new Option("System / browser default", ""));
    setStatus("speaker_status", "Speaker selection is not supported by this browser; using browser default output.", "error");
    return;
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices();
    const speakers = devices.filter((device) => device.kind === "audiooutput");
    const preferred = select.value || select.dataset.preferredDeviceId || localStorage.getItem(SPEAKER_STORAGE_KEY) || "";
    select.replaceChildren(new Option("System / browser default", ""));
    const known = new Set([""]);
    speakers.forEach((device, index) => {
      if (!device.deviceId || device.deviceId === "default" || known.has(device.deviceId)) {
        return;
      }
      select.appendChild(new Option(device.label || `Speaker ${index + 1}`, device.deviceId));
      known.add(device.deviceId);
    });
    select.value = known.has(preferred) ? preferred : "";
    delete select.dataset.preferredDeviceId;
    if (!options.silent) {
      setStatus("speaker_status", `Speakers refreshed. ${speakers.length || 1} output option${speakers.length === 1 ? "" : "s"} detected.`, "success");
    }
    await applySelectedSpeaker();
  } catch (error) {
    setStatus("speaker_status", readableError(error, "Speaker refresh failed."), "error");
  }
}

async function saveSpeakerSelection() {
  localStorage.setItem(SPEAKER_STORAGE_KEY, document.getElementById("speaker_select").value || "");
  await applySelectedSpeaker();
}

async function applySelectedSpeaker() {
  const audio = document.getElementById("assistant_audio");
  const select = document.getElementById("speaker_select");
  const deviceId = select.value || "";
  if (typeof audio.setSinkId !== "function") {
    setStatus("speaker_status", "Speaker selection is not supported by this browser; using browser default output.", deviceId ? "error" : "");
    return false;
  }
  try {
    await audio.setSinkId(deviceId);
    const label = select.options[select.selectedIndex] ? select.options[select.selectedIndex].textContent : "System / browser default";
    setStatus("speaker_status", `Speaker output: ${label}.`, "success");
    return true;
  } catch (error) {
    setStatus("speaker_status", readableError(error, "Speaker selection failed."), "error");
    return false;
  }
}

function validSpeechText(value = document.getElementById("speech_text").value) {
  const count = codePointCount(value);
  return !!value && value.trim().length > 0 && count <= MAX_TEXT_CODE_POINTS;
}

function setSpeechText(value, { focus = false } = {}) {
  const textarea = document.getElementById("speech_text");
  textarea.value = value;
  updateCharacterCount();
  if (focus) {
    textarea.focus();
  }
}

function updateCharacterCount() {
  const text = document.getElementById("speech_text").value;
  const count = codePointCount(text);
  const counter = document.getElementById("character_count");
  counter.textContent = `${count} / ${MAX_TEXT_CODE_POINTS}`;
  counter.classList.toggle("text-danger", count > MAX_TEXT_CODE_POINTS);
  if (count > MAX_TEXT_CODE_POINTS) {
    setStatus("speech_status", `Text is ${count - MAX_TEXT_CODE_POINTS} characters over the limit.`, "error");
  }
  refreshLifecycleControls();
}

function codePointCount(value) {
  return Array.from(value || "").length;
}

function renderTranscript(text) {
  const transcript = document.getElementById("spoken_transcript");
  transcript.textContent = text ? `Realtime transcript\n${text}` : "";
  transcript.hidden = !text;
}

function finishRealtimeResponse(response) {
  const finalTranscript = transcriptFromResponse(response);
  if (finalTranscript) {
    realtime.transcript = finalTranscript;
    renderTranscript(finalTranscript);
  }

  const status = response.status || "completed";
  const details = response.status_details || {};
  state.responseActive = false;
  setRealtimeStatus("Connected", "live");

  if (status === "completed") {
    if (realtime.transcript && !matchesRequestedSpeech(realtime.transcript, realtime.requestedText)) {
      setStatus("speech_status", "Realtime completed, but its final transcript differs from the submitted text.", "error");
    } else {
      setStatus("speech_status", "Speech completed.", "success");
    }
  } else if (status === "incomplete") {
    const message = details.reason === "max_output_tokens"
      ? "Speech was cut off because Realtime reached its output-token limit."
      : details.reason === "content_filter"
        ? "Speech was cut off by the Realtime content filter."
        : "Realtime returned an incomplete speech response.";
    setStatus("speech_status", message, "error");
  } else if (status === "cancelled" || status === "canceled") {
    setStatus("speech_status", details.reason === "turn_detected"
      ? "Speech was interrupted when Realtime detected a new turn."
      : "Speech stopped.", details.reason === "turn_detected" ? "error" : "");
  } else {
    const errorMessage = details.error && (details.error.message || details.error.code);
    setStatus("speech_status", errorMessage || "Realtime failed to complete the speech response.", "error");
  }
  refreshLifecycleControls();
}

function transcriptFromResponse(response) {
  if (!response || !Array.isArray(response.output)) {
    return "";
  }
  return response.output.flatMap((item) => Array.isArray(item && item.content) ? item.content : [])
    .map((part) => part && (part.transcript || part.text) || "")
    .join("");
}

function matchesRequestedSpeech(transcript, requestedText) {
  return normalizeSpokenText(transcript) === normalizeSpokenText(requestedText);
}

function normalizeSpokenText(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

function setAgentStatus(text, mode) {
  setPill("agent_status", text, mode);
}

function setRealtimeStatus(text, mode) {
  setPill("realtime_status", text, mode);
}

function setPill(id, text, mode) {
  const element = document.getElementById(id);
  element.textContent = text;
  element.classList.remove("is-idle", "is-live", "is-busy");
  element.classList.add(`is-${mode}`);
}

function setStatus(id, text, mode) {
  const element = document.getElementById(id);
  element.textContent = text;
  element.classList.remove("is-error", "is-success");
  if (mode) {
    element.classList.add(`is-${mode}`);
  }
}

function toggleTheme() {
  const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  document.documentElement.dataset.bsTheme = next;
  localStorage.setItem(THEME_STORAGE_KEY, next);
  updateThemeButtons();
}

function updateThemeButtons() {
  const dark = document.documentElement.dataset.theme === "dark";
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    button.title = dark ? "Switch to light mode" : "Switch to dark mode";
    button.setAttribute("aria-label", button.title);
    const icon = button.querySelector("i");
    if (icon) {
      icon.className = dark ? "bi bi-sun" : "bi bi-moon-stars";
    }
  });
}

function scopedFetch(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (state.accessCode) {
    headers.set(ACCESS_CODE_HEADER, state.accessCode);
  }
  return fetch(path, { ...options, headers });
}

async function scopedFetchJson(path, options = {}) {
  const response = await scopedFetch(path, options);
  if (!response.ok) {
    throw responseError(response, "Request failed.");
  }
  return response.json();
}

async function fetchJson(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    throw responseError(response, "Request failed.");
  }
  return response.json();
}

function responseError(response, fallback) {
  const error = new Error(`${fallback} (${response.status})`);
  error.status = response.status;
  return error;
}

function readableError(error, fallback) {
  return error && error.message ? error.message : fallback;
}

function withTimeout(promise, timeoutMs, message) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(message)), timeoutMs);
    promise.then((value) => {
      clearTimeout(timeout);
      resolve(value);
    }, (error) => {
      clearTimeout(timeout);
      reject(error);
    });
  });
}
