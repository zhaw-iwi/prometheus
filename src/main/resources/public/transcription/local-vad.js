const SAMPLE_INTERVAL_MS = 50;

export class LocalVadSegmenter {
  constructor({
    silenceDurationSeconds = 1.5,
    activationLevel = 0.015,
    noiseFloor = 0.003,
    noiseMultiplier = 3,
    minimumSpeechMs = 120,
    maximumSegmentMs = 120000,
    onSpeechStart = () => {},
    onSpeechStop = () => {},
    onCommit = () => {},
  } = {}) {
    Object.assign(this, { activationLevel, noiseFloor, noiseMultiplier, minimumSpeechMs, maximumSegmentMs,
      onSpeechStart, onSpeechStop, onCommit });
    this.configure(silenceDurationSeconds);
    this.reset();
  }

  configure(silenceDurationSeconds) {
    if (!Number.isFinite(silenceDurationSeconds) || silenceDurationSeconds < 0.5 || silenceDurationSeconds > 10) {
      throw new Error("Local VAD silence duration must be between 0.5 and 10 seconds.");
    }
    this.silenceDurationMs = silenceDurationSeconds * 1000;
  }

  observe(level, observedAtMs) {
    if (!Number.isFinite(level) || level < 0 || !Number.isFinite(observedAtMs)) return;
    const threshold = Math.max(this.activationLevel, this.noiseFloor * this.noiseMultiplier);
    if (!this.speechActive) {
      if (level >= threshold) {
        this.speechCandidateAt ??= observedAtMs;
        if (observedAtMs - this.speechCandidateAt >= this.minimumSpeechMs) {
          this.speechActive = true;
          this.speechStartedAt = this.speechCandidateAt;
          this.lastVoiceAt = observedAtMs;
          this.speechCandidateAt = null;
          this.onSpeechStart({ observedAtMs });
        }
      } else {
        this.speechCandidateAt = null;
        this.noiseFloor = Math.min(this.activationLevel, this.noiseFloor * 0.95 + level * 0.05);
      }
      return;
    }
    if (level >= threshold) this.lastVoiceAt = observedAtMs;
    if (observedAtMs - this.speechStartedAt >= this.maximumSegmentMs) this.finish("maximum_duration", observedAtMs);
    else if (observedAtMs - this.lastVoiceAt >= this.silenceDurationMs) this.finish("silence", observedAtMs);
  }

  finish(reason, observedAtMs) {
    this.reset();
    this.onSpeechStop({ reason, observedAtMs });
    this.onCommit({ reason, observedAtMs });
  }

  reset() {
    this.speechActive = false;
    this.speechCandidateAt = null;
    this.speechStartedAt = null;
    this.lastVoiceAt = null;
  }
}

export class BrowserLocalVad {
  constructor({
    audioContextFactory = defaultAudioContextFactory,
    setIntervalImpl = globalThis.setInterval.bind(globalThis),
    clearIntervalImpl = globalThis.clearInterval.bind(globalThis),
    now = () => globalThis.performance.now(),
    ...callbacks
  } = {}) {
    this.audioContextFactory = audioContextFactory;
    this.setIntervalImpl = setIntervalImpl;
    this.clearIntervalImpl = clearIntervalImpl;
    this.now = now;
    this.segmenter = new LocalVadSegmenter(callbacks);
  }

  async start(stream, silenceDurationSeconds) {
    await this.stop();
    if (!stream || (stream.getAudioTracks?.() || []).length === 0) {
      throw new Error("Local VAD requires an active microphone stream.");
    }
    this.segmenter.configure(silenceDurationSeconds);
    const context = this.audioContextFactory();
    try {
      this.context = context;
      this.source = context.createMediaStreamSource(stream);
      this.analyser = context.createAnalyser();
      this.analyser.fftSize = 1024;
      this.analyser.smoothingTimeConstant = 0.1;
      this.source.connect(this.analyser);
      this.samples = new Float32Array(this.analyser.fftSize);
      if (context.state === "suspended") await context.resume();
      this.timer = this.setIntervalImpl(() => this.sample(), SAMPLE_INTERVAL_MS);
      this.running = true;
    } catch (error) {
      await this.stop();
      throw error;
    }
  }

  sample() {
    if (!this.analyser) return;
    this.analyser.getFloatTimeDomainData(this.samples);
    let sumSquares = 0;
    for (const sample of this.samples) sumSquares += sample * sample;
    this.segmenter.observe(Math.sqrt(sumSquares / this.samples.length), this.now());
  }

  async stop() {
    if (this.timer != null) this.clearIntervalImpl(this.timer);
    this.timer = null;
    this.running = false;
    this.segmenter.reset();
    this.source?.disconnect?.();
    this.analyser?.disconnect?.();
    const context = this.context;
    this.source = null;
    this.analyser = null;
    this.samples = null;
    this.context = null;
    if (context && context.state !== "closed") await context.close();
  }
}

function defaultAudioContextFactory() {
  const AudioContextType = globalThis.AudioContext || globalThis.webkitAudioContext;
  if (!AudioContextType) throw new Error("Web Audio is unavailable.");
  return new AudioContextType();
}
