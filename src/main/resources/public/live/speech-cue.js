const uuid = value => typeof value === "string" && /^[a-f0-9-]{36}$/i.test(value);
const countdown = /\b(?:eins|1)[\s,;.!…–-]+(?:zwei|2)[\s,;.!…–-]+(?:drei|3)\b/gi;

/** Conservative local cue, not a provider response-done event or word-aligned clock. */
export class CountdownCue {
  constructor(emit, now = () => performance.now()) { this.emit = emit; this.now = now; this.finished = new Set(); }
  arm(cue) {
    if (!uuid(cue?.id) || !uuid(cue?.roundId) || this.finished.has(cue.id) || this.pending?.id === cue.id) return false;
    this.pending = { id: cue.id, roundId: cue.roundId, opened: this.now(), text: "", receipts: [],
      minimumStartMs: Number.isFinite(cue.minimumStartMs) ? cue.minimumStartMs : 0,
      lastVoice: null, lastSample: null, matched: null, inputSince: null, seen: new Set() };
    return true;
  }
  receive(event) {
    const cue = this.pending; if (!cue) return;
    if (event.type === "session.input_transcript.delta" && event.delta?.trim()) { this.finish("interrupted"); return; }
    if (event.type !== "session.output_transcript.delta" || cue.seen.has(event.event_id)) return;
    if (Number.isFinite(event.end_ms) && event.end_ms < cue.minimumStartMs) return;
    if (typeof event.event_id !== "string" || !Number.isFinite(event.start_ms) || !Number.isFinite(event.end_ms)
      || event.start_ms < 0 || event.end_ms < event.start_ms || typeof event.delta !== "string") { this.finish("unavailable"); return; }
    cue.seen.add(event.event_id); cue.receipts.push(event.event_id); cue.text += event.delta;
    if (cue.receipts.length > 32 || cue.text.length > 4000 || /["“”„«»]/.test(cue.text)) { this.finish("unavailable"); return; }
    const matches = [...cue.text.matchAll(countdown)];
    if (matches.length > 1) { this.finish("unavailable"); return; }
    cue.matched = matches.length === 1 && /^[\s.!?…]*$/.test(cue.text.slice(matches[0].index + matches[0][0].length)) ? this.now() : null;
  }
  sample({ output, input, playing }) {
    const cue = this.pending; if (!cue) return;
    const now = this.now();
    if (now - cue.opened >= 30000) { this.finish("timeout"); return; }
    if (!playing || !Number.isFinite(output) || !Number.isFinite(input)
      || cue.lastSample !== null && now - cue.lastSample > 250) { this.finish("unavailable"); return; }
    cue.lastSample = now;
    if (input >= .015) {
      cue.inputSince ??= now;
      if (now - cue.inputSince >= 100) { this.finish("interrupted"); return; }
    } else cue.inputSince = null;
    if (output >= .008) cue.lastVoice = now;
    if (cue.matched !== null && cue.lastVoice !== null && cue.lastVoice >= cue.matched - 1500
      && now - Math.max(cue.lastVoice, cue.matched) >= 800 && cue.inputSince === null) this.finish("complete");
  }
  finish(outcome) {
    const cue = this.pending; if (!cue) return;
    this.pending = null; this.finished.add(cue.id);
    if (this.finished.size > 64) this.finished.delete(this.finished.values().next().value);
    this.emit({ cueId: cue.id, roundId: cue.roundId, outcome, receiptIds: outcome === "complete" ? cue.receipts : [] });
  }
  clear() { if (this.pending) this.finished.add(this.pending.id); this.pending = null; }
}

/** Inspect existing WebRTC streams without recording, replacing playback or retaining PCM. */
export class SpeechCuePlayback {
  constructor(audio, emit, { now = () => performance.now(), createContext = () => new (globalThis.AudioContext || globalThis.webkitAudioContext)(),
    schedule = (callback, delay) => globalThis.setInterval(callback, delay),
    cancel = timer => globalThis.clearInterval(timer), onArm = () => {} } = {}) {
    this.audio = audio;
    Object.assign(this, { createContext, schedule, cancel, onArm });
    this.gate = new CountdownCue(value => { this.cancel(this.timer); this.timer = null; emit(value); }, now);
  }
  streams(output, input) { this.output = output; this.input = input; }
  arm(display) {
    if (!display?.speechCue) {
      if (display?.roundId && display.phase !== "introduction") { this.gate.clear(); this.cancel(this.timer); this.timer = null; }
      return;
    }
    if (!this.gate.arm(display.speechCue)) return;
    this.onArm(display.speechCue);
    try {
      if (!this.context) {
        if (!this.output || !this.input) throw new Error("Audio observation unavailable");
        this.context = this.createContext();
        this.nodes = [this.output, this.input].map(stream => {
          const source = this.context.createMediaStreamSource(stream), analyser = this.context.createAnalyser();
          analyser.fftSize = 1024; source.connect(analyser);
          return { source, analyser, samples: new Float32Array(analyser.fftSize) };
        });
      }
      void this.context.resume().catch(() => this.gate.finish("unavailable"));
      this.cancel(this.timer);
      this.timer = this.schedule(() => {
        if (!this.gate.pending || !this.context) return;
        if (this.context.state !== "running") { this.gate.finish("unavailable"); return; }
        const rms = this.nodes.map(node => {
          node.analyser.getFloatTimeDomainData(node.samples);
          return Math.sqrt(node.samples.reduce((sum, value) => sum + value * value, 0) / node.samples.length);
        });
        this.gate.sample({ output: rms[0], input: rms[1], playing: !this.audio.paused && !this.audio.muted && this.audio.volume > 0 });
      }, 20);
    } catch (_) { this.gate.finish("unavailable"); }
  }
  receive(event) { this.gate.receive(event); }
  close() {
    this.gate.clear(); this.cancel(this.timer);
    for (const node of this.nodes || []) { node.source.disconnect(); node.analyser.disconnect(); node.samples.fill(0); }
    void this.context?.close().catch(() => {});
    this.nodes = []; this.context = null;
  }
}
