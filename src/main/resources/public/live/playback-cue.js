// Observes the received track without rerouting audible output or recording it.
// A captured transcript plus observed quiet is evidence of settling, not proof of hardware audibility.
const RETAIN_MS = 65000, QUIET_MS = 500;
export class PlaybackSettlingGate {
  constructor(ready, now = () => performance.now(), wall = () => Date.now(), diagnostic = () => {}) {
    Object.assign(this, {ready, now, wall, diagnostic}); this.done = new Set(); this.reset();
  }
  reset() {
    this.cue = null; this.last = null; this.media = null; this.progressAt = null; this.lastDiagnostic = null;
    this.frames = []; this.receipts = new Map(); this.segments = new Map();
  }
  state(outcome) {
    if (!this.cue) return;
    const signature = `${this.cue.id}:${this.cue.segmentId || ''}:${outcome}`;
    if (signature === this.lastDiagnostic) return;
    this.lastDiagnostic = signature;
    try { this.diagnostic({phase: 'playback_gate', outcome, cueId: this.cue.id, segmentId: this.cue.segmentId || null}); }
    catch (_) { /* Diagnostics must never change playback or completion. */ }
  }
  caption(id) {
    if (typeof id !== 'string' || !id || id.length > 200 || this.receipts.has(id)) return;
    this.receipts.set(id, this.now());
    if (this.receipts.size > 1024) this.receipts.delete(this.receipts.keys().next().value);
  }
  update(cue, segments) {
    // Keep identities only, never transcript text. Ledger polls can precede the cue.
    for (const segment of (segments || []).slice(-64)) {
      if (segment.speaker === 'ASSISTANT' && segment.status === 'COMPLETE' && segment.receiptIds?.length)
        this.segments.set(segment.segmentId, [...segment.receiptIds]);
    }
    while (this.segments.size > 64) this.segments.delete(this.segments.keys().next().value);
    // No cue yet is normal while context is being refreshed; do not lose its earlier audio.
    if (!cue) { this.cue = null; this.lastDiagnostic = null; return; }
    if (this.cue?.id === cue.id && this.cue?.segmentId === cue.segmentId && this.cue?.expiresAt === cue.expiresAt
      && JSON.stringify(this.cue.receiptIds || []) === JSON.stringify(cue.receiptIds || [])) return;
    this.cue = cue; this.received = this.now();
    this.state(this.done.has(cue.id) ? 'already_finished' : cue.segmentId ? 'capture_received' : 'awaiting_capture');
  }
  interrupt() {
    if (this.cue) { this.state('input_interrupted'); this.remember(this.cue.id); }
    this.reset(); // Also fences a cue/association that has not reached this browser yet.
  }
  remember(id) { this.done.add(id); if (this.done.size > 64) this.done.delete(this.done.values().next().value); }
  evidence() {
    const segmentIds = this.segments.get(this.cue.segmentId), selection = this.cue.receiptIds;
    if (!segmentIds || selection != null && !Array.isArray(selection)) return 'awaiting_segment_evidence';
    const scoped = selection?.length > 0, ids = scoped ? selection : segmentIds;
    const from = scoped ? segmentIds.indexOf(ids[0]) : 0;
    if (scoped && (ids.length > 32 || new Set(ids).size !== ids.length || from < 0
      || ids.some((id, i) => typeof id !== 'string' || !/^[A-Za-z0-9_:.\-]{1,200}$/.test(id) || segmentIds[from + i] !== id)))
      return 'awaiting_segment_evidence';
    if (ids.some(id => !this.receipts.has(id))) return 'awaiting_segment_evidence';
    const times = ids.map(id => this.receipts.get(id)), first = Math.min(...times), last = Math.max(...times);
    // A countdown may finish a response whose earlier captions were discarded by input.
    // Observed prefixes from this same completed segment can establish audio continuity;
    // missing prefixes are not required, and unrelated speech remains excluded.
    const prefix = scoped ? segmentIds.slice(0, from).filter(id => this.receipts.has(id)).map(id => this.receipts.get(id)) : [];
    const evidenceStart = Math.min(first, ...prefix);
    // Captions and audio are separate transports. Bound their local overlap, and
    // never use arbitrary voice observed earlier/later in the session as proof.
    const voice = this.frames.findLast(frame => frame.voiced && frame.at >= first - 250 && frame.at <= last + 1000);
    if (!voice) return 'voice_unobserved';
    let onset = voice.at;
    for (let i = this.frames.indexOf(voice) - 1; i >= 0; i--) {
      const frame = this.frames[i];
      if (!frame.voiced) continue;
      if (onset - frame.at > QUIET_MS + 50) break;
      onset = frame.at;
    }
    if (onset < evidenceStart - 250) return 'overlapping_segment_evidence';
    let quiet = null, settled = null;
    for (const frame of this.frames) {
      if (frame.at <= voice.at) continue;
      if (frame.voiced) quiet = null;
      else quiet ??= frame.at;
      if (quiet !== null && frame.at - quiet >= QUIET_MS && frame.at - last >= QUIET_MS) { settled = frame.at; break; }
    }
    if (settled === null) return 'awaiting_quiet';
    if ([...this.receipts].some(([id, at]) => at >= evidenceStart - 250 && at <= settled && !segmentIds.includes(id)))
      return 'overlapping_segment_evidence';
    return null;
  }
  sample({voiced, playable, mediaTime}) {
    const now = this.now(), gap = this.last !== null && now - this.last > 250;
    // HTML media time may be quantized. Require progress across a bounded window,
    // not a different value on every 50 ms analyser sample.
    if (Number.isFinite(mediaTime) && (this.media === null || mediaTime > this.media)) this.progressAt = now;
    const progressing = Number.isFinite(mediaTime) && this.progressAt !== null && now - this.progressAt < 500;
    this.last = now; this.media = mediaTime;
    if (!playable || gap || !progressing) {
      this.frames = []; this.receipts.clear();
      this.state(!playable ? 'output_unplayable' : gap ? 'sampling_gap' : 'media_stalled'); return;
    }
    this.frames.push({at: now, voiced});
    while (this.frames.length > 1400 || this.frames[0]?.at < now - RETAIN_MS) this.frames.shift();
    for (const [id, at] of this.receipts) if (at < now - RETAIN_MS) this.receipts.delete(id);
    const cue = this.cue;
    if (!cue) return;
    const waiting = this.done.has(cue.id) ? 'already_finished'
      : !(Date.parse(cue.expiresAt) > this.wall()) ? 'expired'
      : !cue.segmentId ? 'awaiting_capture'
      : this.evidence() || (now - this.received < 500 ? 'awaiting_capture_settling' : null);
    if (waiting) { this.state(waiting); return; }
    this.state('ready'); this.remember(cue.id); this.ready(cue);
  }
}

export class LivePlaybackObserver {
  constructor(audio, ready, diagnostic = () => {}) {
    this.audio = audio; this.diagnostic = diagnostic;
    this.gate = new PlaybackSettlingGate(ready, undefined, undefined, diagnostic); this.closed = false;
  }
  stream(stream) {
    if (this.closed) return;
    if (this.output && this.output !== stream) { this.gate.reset(); this.release(); }
    this.output = stream; this.start();
  }
  caption(id) { if (!this.closed) this.gate.caption(id); }
  update(cue, segments) {
    if (this.closed) return;
    this.gate.update(cue, segments);
  }
  interrupt() { this.gate.interrupt(); }
  async start() {
    if (this.context || !this.output || this.closed) return;
    let context;
    try {
      context = new AudioContext(); this.context = context;
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
    } catch (_) {
      if (this.closed || this.context !== context) return;
      this.release();
      try { this.diagnostic({phase: 'playback_cue', outcome: 'monitor_unavailable'}); } catch (_) { /* Diagnostic only. */ }
    }
  }
  release() {
    clearInterval(this.timer); this.timer = null;
    this.source?.disconnect(); this.analyser?.disconnect(); this.silent?.disconnect();
    const context = this.context; this.context = null;
    if (context) void context.close().catch(() => {});
  }
  close() { this.closed = true; this.gate.reset(); this.release(); this.output = null; }
}
