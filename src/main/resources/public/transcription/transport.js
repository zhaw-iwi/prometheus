export class TranscriptionTransport {
  constructor({
    fetchImpl = globalThis.fetch.bind(globalThis),
    peerConnectionFactory = () => new RTCPeerConnection(),
    media,
    sessionFactory,
    reconnectBaseMs = 500,
    maximumReconnects = 2,
    dataChannelTimeoutMs = 10000,
    onEvent = () => {},
    onEpoch = () => {},
    onState = () => {},
    onDiagnostic = () => {},
    onConnected = async () => {},
    onBeforeReconnect = async () => {},
  } = {}) {
    if (!media) throw new Error("A transcription media boundary is required.");
    this.fetchImpl = fetchImpl;
    this.peerConnectionFactory = peerConnectionFactory;
    this.media = media;
    this.sessionFactory = sessionFactory;
    this.reconnectBaseMs = reconnectBaseMs;
    this.maximumReconnects = maximumReconnects;
    this.dataChannelTimeoutMs = dataChannelTimeoutMs;
    Object.assign(this, { onEvent, onEpoch, onState, onDiagnostic, onConnected, onBeforeReconnect });
    this.state = "idle";
    this.epoch = 0;
    this.reconnectAttempts = 0;
    this.deliberateStop = false;
  }

  async start(sessionInfo, { mediaPreferences = {}, turnDetectionMode = "local_vad" } = {}) {
    this.deliberateStop = false;
    this.reconnectAttempts = 0;
    this.options = { mediaPreferences: { ...mediaPreferences }, turnDetectionMode };
    return this.connect(sessionInfo, false);
  }

  async connect(sessionInfo, reconnecting) {
    validateSession(sessionInfo);
    await this.teardown({ releaseMedia: true });
    this.epoch += 1;
    const epoch = this.epoch;
    this.onEpoch({ epoch, reconnecting });
    this.transition(reconnecting ? "reconnecting" : "connecting", { epoch });
    try {
      await this.media.acquire(this.options.mediaPreferences);
      const peer = this.peerConnectionFactory();
      this.peerConnection = peer;
      this.bindPeer(peer, epoch);
      peer.ontrack = () => this.onDiagnostic({ code: "unexpected_remote_media" });
      const channel = peer.createDataChannel("oai-events");
      this.dataChannel = channel;
      const opened = this.waitForChannel(channel, epoch);
      this.media.setEnabled(this.options.turnDetectionMode !== "manual");
      this.media.addTracks(peer);
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      const response = await this.fetchImpl(sessionInfo.webRtcUrl, {
        method: "POST",
        headers: { Authorization: `Bearer ${sessionInfo.clientSecret}`, "Content-Type": "application/sdp" },
        body: offer.sdp,
      });
      if (!response.ok) throw new Error(`WebRTC SDP exchange failed (${response.status}).`);
      await peer.setRemoteDescription({ type: "answer", sdp: await response.text() });
      await opened;
      if (epoch !== this.epoch || this.deliberateStop) throw new Error("Transcription connection was superseded.");
      this.transition("connected", { epoch });
      await this.onConnected({ epoch, reconnecting });
      if (reconnecting) this.reconnectAttempts = 0;
      return epoch;
    } catch (error) {
      await this.teardown({ releaseMedia: true });
      this.transition("failed", { epoch, message: error.message });
      throw error;
    }
  }

  send(event) {
    if (this.dataChannel?.readyState !== "open") return false;
    this.dataChannel.send(JSON.stringify(event));
    return true;
  }

  clearInput() {
    return this.send({ type: "input_audio_buffer.clear" });
  }

  commitInput() {
    return this.send({ type: "input_audio_buffer.commit" });
  }

  startManualTurn() {
    if (this.options?.turnDetectionMode !== "manual" || this.state !== "connected") return false;
    this.clearInput();
    this.media.setEnabled(true);
    return true;
  }

  commitManualTurn() {
    if (this.options?.turnDetectionMode !== "manual" || this.state !== "connected") return false;
    const committed = this.commitInput();
    this.media.setEnabled(false);
    return committed;
  }

  commitLocalVadTurn() {
    return this.options?.turnDetectionMode === "local_vad" && this.state === "connected" && this.commitInput();
  }

  setInputEnabled(enabled) {
    this.media.setEnabled(enabled && this.options?.turnDetectionMode !== "manual");
    if (!enabled) this.clearInput();
  }

  async replaceMedia(mediaPreferences) {
    this.options.mediaPreferences = { ...mediaPreferences };
    return this.media.replaceAudioTrack(this.peerConnection, mediaPreferences);
  }

  async stop() {
    this.deliberateStop = true;
    this.clearReconnectTimer();
    this.transition("stopping", { epoch: this.epoch });
    await this.teardown({ releaseMedia: true });
    this.transition("stopped", { epoch: this.epoch });
  }

  bindPeer(peer, epoch) {
    const handle = () => {
      if (epoch !== this.epoch || this.deliberateStop || this.tearingDown) return;
      if (["failed", "disconnected", "closed"].includes(peer.connectionState)) this.scheduleReconnect(epoch);
    };
    peer.addEventListener?.("connectionstatechange", handle);
    peer.addEventListener?.("iceconnectionstatechange", () => {
      if (epoch === this.epoch) this.onDiagnostic({ code: "ice_state", state: peer.iceConnectionState });
    });
  }

  waitForChannel(channel, epoch) {
    return new Promise((resolve, reject) => {
      let timeout = null;
      channel.addEventListener("open", () => {
        if (timeout != null) globalThis.clearTimeout(timeout);
        if (epoch === this.epoch) resolve();
        else reject(new Error("Stale transcription data channel opened."));
      }, { once: true });
      channel.addEventListener("message", (event) => {
        if (epoch === this.epoch) this.onEvent(event.data);
      });
      channel.addEventListener("close", () => {
        if (epoch === this.epoch && !this.deliberateStop && !this.tearingDown) this.scheduleReconnect(epoch);
      });
      if (channel.readyState === "open") resolve();
      else timeout = globalThis.setTimeout(() => reject(new Error("Transcription data channel timed out.")),
        this.dataChannelTimeoutMs);
    });
  }

  scheduleReconnect(epoch) {
    if (this.reconnectTimer || this.deliberateStop || epoch !== this.epoch) return;
    if (!this.sessionFactory || this.reconnectAttempts >= this.maximumReconnects) {
      this.transition("failed", { epoch, message: "Automatic transcription reconnect exhausted." });
      return;
    }
    const delayMs = this.reconnectBaseMs * (2 ** this.reconnectAttempts);
    this.reconnectAttempts += 1;
    this.transition("reconnecting", { epoch, attempt: this.reconnectAttempts, delayMs });
    this.reconnectTimer = globalThis.setTimeout(async () => {
      this.reconnectTimer = null;
      try {
        await this.onBeforeReconnect({ epoch });
        const sessionInfo = await this.sessionFactory();
        await this.connect(sessionInfo, true);
      } catch (error) {
        this.onDiagnostic({ code: "reconnect_failed", message: error.message });
        if (!this.deliberateStop) this.scheduleReconnect(this.epoch);
      }
    }, delayMs);
  }

  async teardown({ releaseMedia }) {
    const channel = this.dataChannel;
    const peer = this.peerConnection;
    this.dataChannel = null;
    this.peerConnection = null;
    this.tearingDown = true;
    try {
      try { channel?.close?.(); } catch (_error) { /* teardown continues */ }
      try { peer?.close?.(); } catch (_error) { /* teardown continues */ }
      if (releaseMedia) this.media.release();
    } finally {
      this.tearingDown = false;
    }
  }

  clearReconnectTimer() {
    if (this.reconnectTimer != null) globalThis.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  transition(state, details = {}) {
    this.state = state;
    this.onState({ state, ...details });
  }
}

function validateSession(sessionInfo) {
  if (!sessionInfo || sessionInfo.sessionType !== "transcription" || sessionInfo.model !== "gpt-live-transcribe"
      || typeof sessionInfo.clientSecret !== "string" || !sessionInfo.clientSecret
      || typeof sessionInfo.webRtcUrl !== "string" || !sessionInfo.webRtcUrl) {
    throw new Error("Live-transcription session metadata is invalid.");
  }
}
