import { MicrophoneLease, TranscriptionMedia } from "../transcription/media.js";
import { OutputLease } from "../speech/playback.js";
import { captureSummary } from "../transcription/settings.js";
import { LiveCaptions } from "./captions.js";
import { LiveAudioMonitor } from "./audio-diagnostics.js";
import { SpeechCuePlayback } from "./speech-cue.js";

export class LiveClient {
  constructor({ audio, fetch = globalThis.fetch.bind(globalThis), createPeer = () => new RTCPeerConnection(),
    createMedia = onConflict => new TranscriptionMedia({ lease: new MicrophoneLease({ onConflict }) }),
    createOutputLease = (agentId, onConflict) => new OutputLease({ agentId, onConflict }),
    onState = () => {}, onCaptions = () => {}, onHistory = () => {}, onLedger = () => {}, onDiagnostic = () => {},
    pollMs = 1000, timeoutMs = 20000 } = {}) {
    Object.assign(this, { audio, fetch, createPeer, createMedia, createOutputLease, onState, onCaptions, onHistory, onLedger, onDiagnostic, pollMs, timeoutMs });
    this.current = null; this.state = "Idle"; this.closing = null; this.generation = 0;
  }
  get active() { return !!this.current; }
  get busy() { return this.active || !!this.closing; }
  observeBehaviour(display) { this.current?.speechCue?.arm(display); }
  status(state, detail = "", extra = {}) { this.state = state; this.onState({ state, detail, ...extra }); }
  check(run) { if (this.current !== run) throw new DOMException("Session stopped", "AbortError"); }
  diagnostic(run, value) {
    value = { ...value, browserMs: value.browserMs ?? performance.now() };
    if (!run.handle) {
      (run.pendingDiagnostics ||= []).push(value);
      if (run.pendingDiagnostics.length > 16) run.pendingDiagnostics.shift();
      return;
    }
    try {
      this.onDiagnostic({ ...value, browserMs: value.browserMs ?? performance.now(), agentId: run.agentId, handle: run.handle, generation: run.generation });
    } catch (_) { /* Observability cannot interrupt speech or prevent local cleanup. */ }
  }
  invalidate(reason = "Agent connection changed.") {
    const closing = this.stop(reason, "Idle", "scope_change"); this.generation++; this.status("Idle", reason); return closing;
  }
  async gatherIce(run) {
    if (run.peer.iceGatheringState === "complete") return;
    await new Promise((resolve, reject) => {
      const finish = error => {
        clearTimeout(timer); run.peer.removeEventListener("icegatheringstatechange", changed); run.cancelGathering = null;
        if (error) reject(error); else resolve();
      };
      const changed = () => { if (run.peer.iceGatheringState === "complete") finish(); };
      const timer = setTimeout(() => finish(new Error("Voice connection setup timed out. Reconnect when ready.")), this.timeoutMs);
      run.cancelGathering = () => finish(new DOMException("Session stopped", "AbortError"));
      run.peer.addEventListener("icegatheringstatechange", changed); changed();
    });
  }
  async api(run, suffix, options = {}) {
    const response = await this.fetch(`/demo/agents/${encodeURIComponent(run.agentId)}/live/${suffix}`, {
      ...options, headers: { "Content-Type": "application/json", "X-Prometheus-Access-Code": run.accessCode },
      cache: "no-store", signal: AbortSignal.timeout(this.timeoutMs),
    });
    if (!response.ok) {
      let detail;
      if (response.status === 502) {
        try {
          const { code } = await response.json();
          detail = new Map([
            ["live_provider_quota_exhausted", "OpenAI API credits or quota are exhausted. Check API billing and limits, then start GPT-Live again."],
            ["live_provider_rate_limited", "OpenAI is limiting voice requests. Wait briefly, then start GPT-Live again."],
            ["live_provider_authentication", "OpenAI rejected the configured API credentials. Check the server's API key."],
            ["live_provider_access_denied", "OpenAI denied this voice request. Check the API project's permissions and model access."],
          ]).get(code);
        } catch (_) { /* Empty or unknown error responses retain the generic status message. */ }
      }
      const error = new Error(detail || ({ 401: "Access is no longer valid.", 404: "Agent or session is unavailable.",
        409: "This agent already has an active voice session.", 503: "GPT-Live is disabled.", 502: "The voice provider is unavailable." })[response.status] || `Voice request failed (${response.status}).`);
      error.liveReason = [401, 403].includes(response.status) ? "authorization_revoked" : "backend_failure";
      throw error;
    }
    return response.json();
  }
  async start({ agentId, accessCode, voice = "marin", mediaPreferences = {}, outputDeviceId = "" }) {
    if (this.busy) throw new Error("Wait for the previous voice session to stop.");
    if (!agentId || !accessCode) throw new Error("Connect an agent first.");
    const run = { agentId, accessCode, muted: false, generation: ++this.generation, captions: new LiveCaptions(), ledgerKey: "", stopped: false };
    this.current = run; this.status("Connecting", "Preparing microphone and speaker…"); this.onCaptions(run.captions.snapshot());
    run.audioMonitor = new LiveAudioMonitor(this.audio, value => this.diagnostic(run, { ...value, browserMs: performance.now() }));
    run.speechCue = new SpeechCuePlayback(this.audio, value => {
      if (this.current !== run || !run.handle) return;
      this.diagnostic(run, { phase: "speech_cue", outcome: value.outcome, sourceId: value.cueId, roundId: value.roundId });
      void this.api(run, `sessions/${run.handle}/speech-cues`, { method: "POST", body: JSON.stringify(value) })
        .then(accepted => { if (this.current === run) this.diagnostic(run, { phase: "speech_cue_ack", outcome: accepted ? "accepted" : "rejected", sourceId: value.cueId, roundId: value.roundId }); })
        .catch(() => { if (this.current === run) this.diagnostic(run, { phase: "speech_cue_ack", outcome: "unconfirmed", sourceId: value.cueId, roundId: value.roundId }); });
    }, { onArm: cue => this.diagnostic(run, { phase: "speech_cue_armed", sourceId: cue.id, roundId: cue.roundId }) });
    try {
      const capability = await this.api(run, "capabilities"); this.check(run);
      if (!capability.enabled || !capability.eligible) throw new Error("This agent is not available for the GPT-Live pilot.");
      if (outputDeviceId && typeof this.audio.setSinkId !== "function") throw new Error("Speaker selection is unavailable. Choose the system default.");
      run.output = this.createOutputLease(agentId, () => { if (this.current === run) void this.stop("Output ownership was lost.", "Disconnected", "output_ownership_lost"); });
      if (!run.output.acquire()) throw new Error("Another window owns this agent's speaker output.");
      run.media = this.createMedia(() => { if (this.current === run) void this.stop("Another window owns the microphone.", "Disconnected", "microphone_ownership_lost"); });
      await run.media.acquire(mediaPreferences);
      if (this.current !== run) { run.media.release(); return; }
      run.media.setEnabled(false);
      this.diagnostic(run, { phase: "capture", capture: captureSummary(mediaPreferences, run.media.appliedAudioSettings()) });
      this.audio.muted = false;
      if (typeof this.audio.setSinkId === "function") await this.audio.setSinkId(outputDeviceId);
      this.check(run);
      run.peer = this.createPeer(); run.media.addTracks(run.peer);
      for (const track of run.media.stream.getAudioTracks()) run.audioMonitor.track(track, "input");
      for (const track of run.media.stream.getAudioTracks()) track.addEventListener("ended", () => { if (this.current === run) void this.stop("Microphone disconnected. Reconnect when ready.", "Disconnected", "microphone_ended"); });
      run.peer.ontrack = event => {
        if (this.current !== run) return;
        this.audio.srcObject = event.streams[0] || new MediaStream([event.track]);
        run.speechCue.streams(this.audio.srcObject, run.media.stream);
        if (event.track) run.audioMonitor.track(event.track, "output");
        this.diagnostic(run, { phase: "output_track" });
        event.track?.addEventListener?.("ended", () => { if (this.current === run) void this.stop("Speaker track ended.", "Disconnected", "output_track_ended"); });
        Promise.resolve(this.audio.play()).catch(() => { if (this.current === run) void this.stop("Speaker playback was blocked.", "Error", "playback_blocked"); });
      };
      run.peer.onconnectionstatechange = () => { if (this.current === run && ["failed", "disconnected", "closed"].includes(run.peer.connectionState)) void this.stop("Voice connection lost. Reconnect when ready.", "Disconnected", "transport_failure"); };
      run.channel = run.peer.createDataChannel("oai-events");
      let started;
      run.started = new Promise(resolve => { started = resolve; run.cancelStartup = resolve; });
      run.channel.onclose = () => { if (this.current === run) void this.stop("Voice data connection closed.", "Disconnected", "data_channel_closed"); };
      run.channel.onmessage = ({ data }) => {
        if (this.current !== run) return;
        try {
          const event = JSON.parse(data);
          run.speechCue.receive(event);
          if (event.type === "session.input_transcript.delta" || event.type === "session.output_transcript.delta")
            this.diagnostic(run, { phase: "caption_received", receiptId: event.event_id, direction: event.type === "session.output_transcript.delta" ? "output" : "input", providerStartMs: event.start_ms, providerEndMs: event.end_ms });
          if (event.type === "session.started") started();
          if (run.captions.receive(event)) this.onCaptions(run.captions.snapshot());
          if (event.type === "session.closed") void this.stop("Voice session ended.", "Disconnected", "provider_session_closed");
          if (event.type === "error") void this.stop("The voice provider reported an error.", "Error", "provider_error");
        } catch (_) { void this.stop("Invalid or excessive voice captions.", "Error", "invalid_provider_event"); }
      };
      const offer = await run.peer.createOffer(); this.check(run);
      await run.peer.setLocalDescription(offer); this.check(run);
      await this.gatherIce(run); this.check(run);
      const sdp = run.peer.localDescription?.sdp;
      if (!sdp) throw new Error("Voice connection did not produce an offer.");
      run.creation = this.api(run, "sessions", { method: "POST", body: JSON.stringify({ sdp, voice }) });
      const session = await run.creation; run.handle = session.handle;
      for (const value of run.pendingDiagnostics || []) this.diagnostic(run, value);
      run.pendingDiagnostics = [];
      if (this.current !== run) { await this.finalize(run); return; }
      await run.peer.setRemoteDescription({ type: "answer", sdp: session.sdp }); this.check(run);
      let timer;
      try { await Promise.race([run.started, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error("Voice startup timed out.")), this.timeoutMs); })]); }
      finally { clearTimeout(timer); }
      this.check(run);
      await this.api(run, `sessions/${run.handle}/input?muted=false`, { method: "POST" }); this.check(run);
      run.media.setEnabled(true); this.status("Active", "Speak naturally. You can interrupt while the assistant speaks.", { muted: false });
      if (this.pollMs) run.timer = setInterval(() => void this.poll(), this.pollMs);
      this.diagnostic(run, { phase: "started" });
    } catch (error) {
      if (this.current === run) await this.stop(error?.name === "NotAllowedError" ? "Microphone permission was denied. Allow it in Chrome, then reconnect." : error.message, "Error", error.liveReason || "startup_failed");
    }
  }
  async poll() {
    const run = this.current; if (!run?.handle || run.polling) return; run.polling = true;
    void run.audioMonitor.sample(run.peer);
    try {
      const update = await this.api(run, `sessions/${run.handle}/updates?transcriptRevision=${run.transcriptRevision ?? -1}`); this.check(run);
      const status = update.status;
      this.diagnostic(run, { phase: "status", status });
      if (status.state !== "attached") {
        const error = new Error("Backend voice connection lost. Reconnect when ready.");
        error.liveReason = status.state === "closed" ? "backend_session_closed" : "backend_failure";
        throw error;
      }
      run.transcriptRevision = update.transcriptRevision;
      const ledger = update.transcripts;
      if (!Array.isArray(ledger)) return;
      const key = ledger.map(value => `${value.segmentId}:${value.status}:${value.eventId}`).join("|");
      if (key !== run.ledgerKey) {
        const previous = run.ledgerEntries || new Set();
        run.ledgerEntries = new Set();
        for (const value of ledger) {
          const identity = `${value.segmentId}:${value.status}:${value.eventId}`;
          run.ledgerEntries.add(identity);
          if (!previous.has(identity)) this.diagnostic(run, { phase: "segment_observed", segmentId: value.segmentId, outcome: value.status });
        }
        run.ledgerKey = key; run.captions.reconcile(ledger); this.onCaptions(run.captions.snapshot()); this.onLedger(ledger, run.agentId);
        const history = await this.api(run, "history"); this.check(run); this.onHistory(history, run.agentId);
      }
    } catch (error) { if (this.current === run) await this.stop(error.message, "Disconnected", error.liveReason || "backend_failure"); }
    finally { run.polling = false; }
  }
  async mute() {
    const run = this.current; if (!run?.handle || this.state !== "Active" || run.muting) return;
    run.muting = true; run.media.setEnabled(false);
    try {
      const muted = !run.muted;
      await this.api(run, `sessions/${run.handle}/input?muted=${muted}`, { method: "POST" }); this.check(run);
      run.muted = muted; run.media.setEnabled(!muted);
      this.diagnostic(run, { phase: "input_control", outcome: muted ? "muted" : "unmuted" }); this.status("Active", muted ? "Microphone muted. You can still hear the assistant." : "Microphone on.", { muted });
    } catch (error) { if (this.current === run) await this.stop(error.message, "Error", error.liveReason || "input_control_failed"); }
    finally { run.muting = false; }
  }
  stop(detail = "Voice stopped.", finalState = "Idle", reason = "operator_stop") {
    const run = this.current; if (!run) return this.closing || Promise.resolve();
    this.current = null; run.stopped = true;
    run.speechCue.close();
    // Silence and release are synchronous, before any provider or backend response.
    this.audio.pause(); this.audio.muted = true; this.audio.srcObject = null;
    this.diagnostic(run, { phase: "output_end", reason });
    run.audioMonitor.playback("local_stop"); run.audioMonitor.close();
    run.media?.release(); run.output?.release(); clearInterval(run.timer);
    run.channel?.close(); run.peer?.close();
    run.cancelStartup?.();
    run.cancelGathering?.();
    this.diagnostic(run, { phase: "local_stop", reason });
    this.status("Stopping", detail);
    const closing = this.finalize(run).then(result => {
      this.diagnostic(run, { phase: "stopped", finalized: result?.finalized === true, status: result?.state ? result : undefined });
      if (this.generation !== run.generation) return;
      this.status(finalState, `${detail} Finalization ${result?.finalized ? "confirmed" : "unconfirmed"}.`);
    }).finally(() => { if (this.closing === closing) { this.closing = null; this.onState({ state: this.state, settled: true }); } });
    this.closing = closing; return closing;
  }
  finalize(run) {
    if (!run.finalizing) run.finalizing = (async () => {
      try {
        if (!run.handle && run.creation) run.handle = (await run.creation).handle;
        if (run.handle) {
          const result = await this.api(run, `sessions/${run.handle}`, { method: "DELETE", keepalive: true });
          // Refresh the final ledger without delaying local Stop or finalization status.
          void Promise.all([this.api(run, `transcripts?sessionId=${encodeURIComponent(run.handle)}`), this.api(run, "history")]).then(([ledger, history]) => {
            if (this.generation !== run.generation) return;
            this.onLedger(ledger, run.agentId); this.onHistory(history, run.agentId);
          }).catch(() => {});
          return result;
        }
      } catch (_) { /* Local Stop succeeds even when provider finalization is unknown. */ }
      return { finalized: false };
    })();
    return run.finalizing;
  }
}
