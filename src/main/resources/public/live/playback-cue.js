// Optional execution gate. Observes the received track without rerouting audible output or recording it.
// A captured transcript plus observed quiet is evidence of settling, not proof of hardware audibility.
export class PlaybackSettlingGate {
  constructor(ready, now = () => performance.now(), wall = () => Date.now()) {
    Object.assign(this, {ready, now, wall}); this.done = new Set(); this.reset();
  }
  reset() { this.cue = null; this.last = null; this.voice = null; this.quiet = null; this.media = null; }
  update(cue) {
    if (!cue) { this.reset(); return; }
    if (this.cue?.id === cue.id && this.cue?.segmentId === cue.segmentId) return;
    if (this.cue?.id !== cue.id) { this.voice = null; this.quiet = null; }
    this.cue = cue; this.received = this.now();
  }
  interrupt() { if (this.cue) this.remember(this.cue.id); this.cue = null; this.quiet = null; }
  remember(id) { this.done.add(id); if (this.done.size > 64) this.done.delete(this.done.values().next().value); }
  sample({voiced, playable, mediaTime}) {
    const now = this.now(), gap = this.last === null || now - this.last > 250;
    const progressing = Number.isFinite(mediaTime) && this.media !== null && mediaTime > this.media;
    this.last = now; this.media = mediaTime;
    if (!playable || gap || !progressing) { this.quiet = null; return; }
    if (voiced) { this.voice = now; this.quiet = null; return; }
    this.quiet ??= now;
    const cue = this.cue;
    if (!cue?.segmentId || this.done.has(cue.id) || this.voice === null || now - this.voice > 10000
        || now - this.quiet < 500 || now - this.received < 500 || !(Date.parse(cue.expiresAt) > this.wall())) return;
    this.remember(cue.id); this.ready(cue);
  }
}

export class LivePlaybackObserver {
  constructor(audio, ready, diagnostic = () => {}) {
    this.audio = audio; this.diagnostic = diagnostic;
    this.gate = new PlaybackSettlingGate(ready); this.closed = false;
  }
  stream(stream) { this.output = stream; if (this.gate.cue) this.start(); }
  update(cue) {
    if (this.closed) return;
    this.gate.update(cue);
    if (cue) this.start(); else this.release();
  }
  interrupt() { this.gate.interrupt(); }
  async start() {
    if (this.context || !this.output || this.closed) return;
    try {
      const context = new AudioContext(); this.context = context;
      this.source = context.createMediaStreamSource(this.output);
      this.analyser = context.createAnalyser(); this.analyser.fftSize = 1024;
      this.silent = context.createGain(); this.silent.gain.value = 0;
      this.source.connect(this.analyser); this.analyser.connect(this.silent); this.silent.connect(context.destination);
      this.samples = new Float32Array(this.analyser.fftSize);
      await context.resume();
      if (this.closed || this.context !== context) return;
      this.timer = setInterval(() => {
        this.analyser.getFloatTimeDomainData(this.samples);
        const rms = Math.sqrt(this.samples.reduce((sum, value) => sum + value * value, 0) / this.samples.length);
        const track = this.output?.getAudioTracks()[0];
        this.gate.sample({voiced: rms > 500 / 32768, mediaTime: this.audio.currentTime,
          playable: context.state === 'running' && !document.hidden && !this.audio.paused && !this.audio.muted
            && this.audio.volume > 0 && this.audio.readyState >= 2 && track?.readyState === 'live' && !track.muted});
      }, 50);
    } catch (_) { this.release(); this.diagnostic({phase: 'playback_cue', outcome: 'monitor_unavailable'}); }
  }
  release() {
    clearInterval(this.timer); this.timer = null;
    this.source?.disconnect(); this.analyser?.disconnect(); this.silent?.disconnect();
    const context = this.context; this.context = null;
    if (context) void context.close().catch(() => {});
  }
  close() { this.closed = true; this.gate.reset(); this.release(); this.output = null; }
}
