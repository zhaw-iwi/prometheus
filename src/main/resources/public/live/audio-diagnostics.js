// Observe existing media only. No audio recording, extra capture, device IDs or SDP.
const finite = value => Number.isFinite(value) ? value : null;
const metrics = ["packetsReceived", "packetsLost", "packetsSent", "jitter", "concealedSamples", "silentConcealedSamples",
  "concealmentEvents", "totalSamplesReceived", "jitterBufferDelay", "jitterBufferEmittedCount", "audioLevel",
  "totalAudioEnergy", "totalSamplesDuration"];
export class LiveAudioMonitor {
  constructor(audio, emit) {
    this.audio = audio; this.emit = emit; this.cleanups = []; this.closed = false; this.sampling = false;
    for (const event of ["playing", "waiting", "stalled", "pause", "ended", "emptied", "error", "volumechange"])
      this.listen(audio, event, () => this.playback(event));
  }
  listen(target, event, callback) {
    target?.addEventListener?.(event, callback);
    this.cleanups.push(() => target?.removeEventListener?.(event, callback));
  }
  playback(outcome) {
    if (this.closed) return;
    this.emit({ phase: "playback", outcome, paused: !!this.audio.paused, muted: !!this.audio.muted,
      readyState: finite(this.audio.readyState), mediaTimeMs: finite(this.audio.currentTime * 1000),
      volume: finite(this.audio.volume), mediaErrorCode: finite(this.audio.error?.code) });
  }
  track(track, direction) {
    const report = outcome => {
      if (!this.closed) this.emit({ phase: "media_track", direction, outcome,
        muted: !!track.muted, enabled: !!track.enabled, ended: track.readyState === "ended" });
    };
    for (const event of ["mute", "unmute", "ended"]) this.listen(track, event, () => report(event));
    report("attached");
  }
  async sample(peer) {
    if (this.closed || this.sampling || !peer?.getStats) return;
    this.sampling = true;
    try {
      const stats = await peer.getStats();
      if (this.closed) return;
      this.playback("sample");
      for (const value of stats.values()) {
        if ((value.kind || value.mediaType) !== "audio") continue;
        const direction = ({ "inbound-rtp": "output", "outbound-rtp": "input", "media-source": "microphone" })[value.type];
        if (!direction) continue;
        this.emit({ phase: "rtc_audio", direction, statsMs: finite(value.timestamp),
          ...Object.fromEntries(metrics.map(key => [key, finite(value[key])])) });
      }
    } catch (_) {
      if (!this.closed) this.emit({ phase: "rtc_audio", outcome: "unavailable" });
    } finally { this.sampling = false; }
  }
  close() { this.closed = true; for (const cleanup of this.cleanups.splice(0)) cleanup(); }
}
