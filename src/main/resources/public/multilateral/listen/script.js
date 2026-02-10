let session = {
  agentId: null,
  isListening: false,
};

let peerConnection = null;
let dataChannel = null;
let micStream = null;
let utteranceCount = 0;

window.addEventListener("load", () => {
  session.agentId = getAgentId();
  if (!session.agentId) {
    setStatusMessage("Missing agent id in URL. Use ?{UUID} or ?agentId=UUID.");
    disableToggle();
    return;
  }
  wireUi();
  loadAgentInfo();
});

function wireUi() {
  document.getElementById("toggle_listen").addEventListener("click", toggleListening);
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
    button.innerHTML = '<i class="bi bi-mic-mute-fill me-2"></i>Stop Listening';
    status.textContent = "Listening";
    status.className = "status-pill is-listening";
    setStatusMessage("Listening for multi-speaker audio.");
  } else {
    button.innerHTML = '<i class="bi bi-mic-fill me-2"></i>Start Listening';
    status.textContent = "Idle";
    status.className = "status-pill is-idle";
    setStatusMessage("Waiting for audio input.");
  }
}

function setStatusMessage(message) {
  const status = document.querySelector(".card-body .small");
  if (status) {
    status.textContent = message;
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
  appendLog("app", "Starting multilateral listening session...");
  setListeningState(true);
  try {
    const sessionInfo = await createRealtimeSession();
    await setupRealtimeConnection(sessionInfo);
    await waitForDataChannelOpen();
    sendSessionUpdate();
  } catch (error) {
    appendLog("app", "Failed to start: " + error.message);
    await stopListening();
  }
}

async function stopListening() {
  appendLog("app", "Stopping multilateral listening session...");
  setListeningState(false);
  if (dataChannel) {
    dataChannel.close();
    dataChannel = null;
  }
  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }
  if (micStream) {
    micStream.getTracks().forEach((track) => track.stop());
    micStream = null;
  }
}

async function loadAgentInfo() {
  const response = await fetch(`/${session.agentId}/info`);
  if (!response.ok) {
    appendLog("app", "Unable to load agent info.");
    return;
  }
  const data = await response.json();
  document.getElementById("agent_name").textContent = data.name || "Multilateral Listener";
}

async function createRealtimeSession() {
  const response = await fetch("/realtime/session", {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error("Realtime session creation failed.");
  }
  return await response.json();
}

async function setupRealtimeConnection(sessionInfo) {
  peerConnection = new RTCPeerConnection();
  dataChannel = peerConnection.createDataChannel("oai-events");
  dataChannel.addEventListener("message", handleRealtimeEvent);

  micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  micStream.getTracks().forEach((track) => peerConnection.addTrack(track, micStream));

  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  const answerResponse = await fetch(
    `${sessionInfo.realtimeUrl}?model=${encodeURIComponent(sessionInfo.model)}`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${sessionInfo.clientSecret}`,
        "Content-Type": "application/sdp",
      },
      body: offer.sdp,
    }
  );

  if (!answerResponse.ok) {
    throw new Error("Realtime SDP exchange failed.");
  }

  const answer = await answerResponse.text();
  await peerConnection.setRemoteDescription({ type: "answer", sdp: answer });
  appendLog("realtime", "WebRTC session established.");
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

function sendSessionUpdate() {
  if (!dataChannel || dataChannel.readyState !== "open") {
    appendLog("realtime", "Data channel not ready for session update.");
    return;
  }
  dataChannel.send(
    JSON.stringify({
      type: "session.update",
      session: {
        instructions: "You are a transcription engine for multi-speaker discussions. Transcribe accurately and do not respond.",
        turn_detection: {
          type: "server_vad",
          create_response: false,
        },
      },
    })
  );
  appendLog("realtime", "Session update sent.");
}

function handleRealtimeEvent(event) {
  let data = null;
  try {
    data = JSON.parse(event.data);
  } catch (err) {
    appendLog("realtime", "Non-JSON event received.");
    return;
  }

  if (data.type === "conversation.item.input_audio_transcription.delta") {
    const partial = data.transcript || "";
    if (partial.trim()) {
      document.getElementById("live_transcript").textContent = partial;
    }
  } else if (data.type === "conversation.item.input_audio_transcription.completed") {
    const transcript = data.transcript || "";
    if (transcript.trim()) {
      document.getElementById("live_transcript").textContent = transcript;
      addTranscriptEntry(transcript);
      appendLog("realtime", "User transcript completed.");
      acknowledgeTranscript(transcript);
    }
  }
}

function addTranscriptEntry(text) {
  const log = document.getElementById("transcript_log");
  const empty = document.getElementById("transcript_empty");
  if (empty) {
    empty.remove();
  }
  utteranceCount += 1;
  const item = document.createElement("div");
  item.className = "transcript-item";

  const meta = document.createElement("div");
  meta.className = "transcript-meta mono";
  meta.textContent = `Utterance ${String(utteranceCount).padStart(2, "0")} · ${new Date().toLocaleTimeString()}`;

  const body = document.createElement("div");
  body.className = "transcript-text";
  body.textContent = text;

  item.appendChild(meta);
  item.appendChild(body);
  log.prepend(item);
}

async function acknowledgeTranscript(transcript) {
  const response = await fetch(`/${session.agentId}/acknowledge`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
    },
    body: JSON.stringify({
      type: "obs.user_utterance",
      actor: "user",
      kind: "observation",
      payload: transcript,
    }),
  });
  if (!response.ok) {
    appendLog("promise", "acknowledge failed.");
  }
}

function appendLog(source, message) {
  const prefix = source ? `[${source}] ` : "";
  console.log(`${prefix}${message}`);
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
