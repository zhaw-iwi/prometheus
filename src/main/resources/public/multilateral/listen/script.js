import { LiveTranscriptionClient } from "../../transcription/client.js";
import { TranscriptionMedia } from "../../transcription/media.js";
import { TranscriptionSettingsPanel } from "../../transcription/settings-panel.js";

const session = { agentId: null, accessCode: "", isListening: false, client: null, panel: null, manualActive: false };
let utteranceCount = 0;

window.addEventListener("load", async () => {
  session.agentId = getAgentId();
  session.accessCode = new URLSearchParams(window.location.search).get("accessCode") || "";
  document.getElementById("access_code").value = session.accessCode;
  if (!session.agentId) {
    setStatusMessage("Missing agent id in URL. Use ?agentId=UUID.");
    document.getElementById("toggle_listen").disabled = true;
    return;
  }
  wireUi();
  await loadAgentInfo();
  if (session.accessCode) await initializeClient();
});

function wireUi() {
  document.getElementById("toggle_listen").addEventListener("click", toggleListening);
  document.getElementById("access_code").addEventListener("change", async (event) => {
    session.accessCode = event.target.value.trim();
    if (!session.isListening && session.accessCode) await initializeClient({ replace: true });
  });
  const push = document.getElementById("transcription_push_to_talk");
  push.addEventListener("pointerdown", beginManualTurn);
  ["pointerup", "pointercancel", "lostpointercapture"].forEach((name) => {
    push.addEventListener(name, finishManualTurn);
  });
}

async function initializeClient({ replace = false } = {}) {
  if (session.client && !replace) return session.client;
  if (session.client) await session.client.stop();
  const media = new TranscriptionMedia({ onDiagnostic: logDiagnostic });
  const client = new LiveTranscriptionClient({
    agentId: session.agentId,
    accessCode: session.accessCode,
    media,
    storageKey: `prometheus.multilateral.transcription.${session.agentId}.v1`,
    onPartial: ({ text }) => { document.getElementById("live_transcript").textContent = text; },
    onFinal: handleFinalTranscript,
    onState: handleTransportState,
    onInputState: ({ type }) => appendLog("transcription", type),
    onDiagnostic: logDiagnostic,
  });
  await client.initialize();
  session.client = client;
  session.panel = new TranscriptionSettingsPanel({
    root: document.getElementById("live_transcription_settings_root"),
    preferences: client.preferences,
    media,
    onValidation: updateManualControl,
  });
  updateManualControl();
  return client;
}

async function toggleListening() {
  if (session.isListening) await stopListening();
  else await startListening();
}

async function startListening() {
  session.accessCode = document.getElementById("access_code").value.trim();
  if (!session.accessCode) {
    setStatusMessage("Enter the access code that owns this agent.", true);
    return;
  }
  setListeningState(true);
  try {
    const client = await initializeClient({ replace: session.client?.accessCode !== session.accessCode });
    session.panel.setLifecycle("CONNECTING");
    const started = await client.start({ settings: session.panel.apiValues(), mediaPreferences: session.panel.mediaValues() });
    session.panel.setAppliedCapture(started.appliedCapture);
    session.panel.setLifecycle("CONNECTED");
    updateManualControl();
  } catch (error) {
    appendLog("app", `Failed to start: ${error.message}`);
    await stopListening();
    setStatusMessage(error.message, true);
  }
}

async function stopListening() {
  setListeningState(false);
  session.manualActive = false;
  if (session.client) await session.client.stop();
  session.panel?.setAppliedCapture({});
  session.panel?.setLifecycle("IDLE");
  updateManualControl();
}

function setListeningState(isListening) {
  session.isListening = isListening;
  const button = document.getElementById("toggle_listen");
  const status = document.getElementById("listen_status");
  button.innerHTML = isListening
    ? '<i class="bi bi-mic-mute-fill me-2"></i>Stop Listening'
    : '<i class="bi bi-mic-fill me-2"></i>Start Listening';
  status.textContent = isListening ? "Listening" : "Idle";
  status.className = `status-pill is-${isListening ? "listening" : "idle"}`;
  setStatusMessage(isListening ? "Listening for multi-speaker audio." : "Waiting for audio input.");
}

function handleTransportState({ state, message = "", attempt = 0 }) {
  const status = document.getElementById("listen_status");
  if (state === "connected") {
    status.textContent = "Listening";
    status.className = "status-pill is-listening";
  } else if (state === "reconnecting") {
    status.textContent = `Reconnect ${attempt}`;
    status.className = "status-pill is-error";
  } else if (state === "failed") {
    status.textContent = "Failed";
    status.className = "status-pill is-error";
    setStatusMessage(message || "Transcription transport failed.", true);
  }
  session.panel?.setLifecycle(state === "connected" ? "CONNECTED"
    : state === "reconnecting" ? "RECONNECTING" : state === "failed" ? "FAILED" : "IDLE");
  updateManualControl();
}

async function handleFinalTranscript({ itemId, text }) {
  const transcript = String(text || "").trim();
  if (!transcript || isLikelyAsrHallucination(transcript)) {
    appendLog("transcription", `Ignored noisy transcript ${itemId}.`);
    return;
  }
  document.getElementById("live_transcript").textContent = transcript;
  addTranscriptEntry(transcript);
  appendLog("transcription", `Final transcript ${itemId}.`);
  await acknowledgeTranscript(transcript);
}

function beginManualTurn(event) {
  event.preventDefault();
  if (!session.client?.startManualTurn()) return;
  session.manualActive = true;
  event.currentTarget.setPointerCapture?.(event.pointerId);
  updateManualControl();
}

function finishManualTurn(event) {
  event.preventDefault();
  if (!session.manualActive) return;
  session.manualActive = false;
  session.client?.commitManualTurn();
  updateManualControl();
}

function updateManualControl() {
  const push = document.getElementById("transcription_push_to_talk");
  let manual = false;
  try { manual = session.panel?.apiValues()?.turnDetection?.type === "manual"; } catch (_error) { /* invalid UI */ }
  push.classList.toggle("d-none", !manual);
  push.disabled = !manual || session.client?.transport?.state !== "connected";
  push.setAttribute("aria-pressed", session.manualActive ? "true" : "false");
}

async function loadAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) return;
  const data = await response.json();
  document.getElementById("agent_name").textContent = data.name || "Multilateral Listener";
}

async function acknowledgeTranscript(transcript) {
  const response = await fetch(`/${session.agentId}/acknowledge`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify({ type: "obs.user_utterance", actor: "user", kind: "observation", payload: transcript }),
  });
  if (!response.ok) appendLog("prometheus", "acknowledge failed.");
}

function addTranscriptEntry(text) {
  const log = document.getElementById("transcript_log");
  document.getElementById("transcript_empty")?.remove();
  utteranceCount += 1;
  const item = document.createElement("div");
  item.className = "transcript-item";
  const meta = document.createElement("div");
  meta.className = "transcript-meta mono";
  meta.textContent = `Utterance ${String(utteranceCount).padStart(2, "0")} · ${new Date().toLocaleTimeString()}`;
  const body = document.createElement("div");
  body.className = "transcript-text";
  body.textContent = text;
  item.append(meta, body);
  log.prepend(item);
}

function isLikelyAsrHallucination(transcript) {
  const normalized = String(transcript || "").normalize("NFKD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
  return ["untertitel der amara org community", "subtitles by the amara org community",
    "captions by the amara org community"].includes(normalized);
}

function setStatusMessage(message, error = false) {
  const status = document.querySelector(".card-body .small");
  if (status) {
    status.textContent = message;
    status.classList.toggle("text-danger", error);
  }
}

function logDiagnostic(diagnostic) {
  appendLog("transcription", JSON.stringify(diagnostic));
}

function appendLog(source, message) {
  console.log(`[${source}] ${message}`);
}

function getAgentId() {
  const search = window.location.search;
  if (!search || search.length < 2) return null;
  const params = new URLSearchParams(search);
  return params.get("agentId") || params.get("agent") || (search.includes("=") ? null : search.substring(1));
}
