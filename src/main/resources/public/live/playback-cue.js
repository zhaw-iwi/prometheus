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
    this.pendingInputs = new Set(); this.inputResults = new Map();
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
    if (this.pendingInputs.size && (!cue || cue.id !== this.cue?.id || !cue.allowAcknowledgements)) this.interrupt();
    // Keep identities only, never transcript text. Ledger polls can precede the cue.
    for (const segment of (segments || []).slice(-64)) {
      if (segment.speaker === 'ASSISTANT' && segment.status === 'COMPLETE' && segment.receiptIds?.length)
        this.segments.set(segment.segmentId, [...segment.receiptIds]);
      if (segment.speaker === 'USER' && segment.status !== 'PENDING') {
        const harmless = segment.status === 'COMPLETE' && segment.reason === 'countdown_acknowledgement'
          || segment.status === 'IGNORED' && segment.reason === 'empty_transcript';
        for (const id of (segment.receiptIds || []).slice(0, 128)) {
          this.inputResults.set(id, harmless);
          if (this.pendingInputs.delete(id) && !harmless) this.interrupt();
        }
      }
    }
    while (this.segments.size > 64) this.segments.delete(this.segments.keys().next().value);
    while (this.inputResults.size > 1024) this.inputResults.delete(this.inputResults.keys().next().value);
    // No cue yet is normal while context is being refreshed; do not lose its earlier audio.
    if (!cue) { this.cue = null; this.lastDiagnostic = null; return; }
    if (this.cue?.id === cue.id && this.cue?.segmentId === cue.segmentId && this.cue?.expiresAt === cue.expiresAt
      && this.cue?.allowAcknowledgements === cue.allowAcknowledgements
      && JSON.stringify(this.cue.receiptIds || []) === JSON.stringify(cue.receiptIds || [])
      && JSON.stringify(this.cue.segmentIds || []) === JSON.stringify(cue.segmentIds || [])) return;
    this.cue = cue; this.received = this.now();
    this.state(this.done.has(cue.id) ? 'already_finished' : cue.segmentId ? 'capture_received' : 'awaiting_capture');
  }
  interrupt() {
    if (this.cue) { this.state('input_interrupted'); this.remember(this.cue.id); }
    this.reset(); // Also fences a cue/association that has not reached this browser yet.
  }
  input(id) {
    if (!this.cue?.allowAcknowledgements || typeof id !== 'string' || !/^[A-Za-z0-9_:.\-]{1,200}$/.test(id)
      || this.pendingInputs.size >= 128) { this.interrupt(); return; }
    if (this.inputResults.has(id)) {
      if (!this.inputResults.get(id)) this.interrupt();
      return;
    }
    // Retain playback evidence, but block execution until the backend has classified
    // the COMPLETE user segment. A partial "Ja" can still become "Ja, stopp".
    this.pendingInputs.add(id); this.state('awaiting_input_classification');
  }
  remember(id) { this.done.add(id); if (this.done.size > 64) this.done.delete(this.done.values().next().value); }
  evidence() {
    const segments = this.cue.segmentIds ?? [this.cue.segmentId], selection = this.cue.receiptIds;
    if (!Array.isArray(segments) || !segments.length || segments.length > 3
      || new Set(segments).size !== segments.length || segments.at(-1) !== this.cue.segmentId
      || segments.some(id => typeof id !== 'string' || !/^[A-Za-z0-9._:-]{1,160}$/.test(id))
      || selection != null && !Array.isArray(selection)) return 'awaiting_segment_evidence';
    const groups = segments.map(id => this.segments.get(id));
    if (groups.some(ids => !ids?.length || ids.length > 128)) return 'awaiting_segment_evidence';
    const segmentIds = groups.flat();
    const scoped = selection?.length > 0, ids = scoped ? selection : segmentIds;
    if (segments.length > 1 && (!scoped || groups.some(group => !group.some(id => ids.includes(id)))
      || new Set(segmentIds).size !== segmentIds.length)) return 'awaiting_segment_evidence';
    const from = scoped ? segmentIds.indexOf(ids[0]) : 0;
    if (scoped && (ids.length > 32 || new Set(ids).size !== ids.length || from < 0
      || ids.some((id, i) => typeof id !== 'string' || !/^[A-Za-z0-9_:.\-]{1,200}$/.test(id) || segmentIds[from + i] !== id)))
      return 'awaiting_segment_evidence';
    if (ids.some(id => !this.receipts.has(id))) return 'awaiting_segment_evidence';
    // Each acoustically separate word needs local voice evidence. The last word
    // cannot lend its audio to a missing earlier segment of the countdown.
    const groupTimes = groups.map(group => group.filter(id => ids.includes(id)).map(id => this.receipts.get(id)));
    if (groupTimes.some((times, index) => {
      const next = groupTimes[index + 1];
      const end = Math.min(Math.max(...times) + 1000, next ? Math.min(...next) - 250 : Infinity);
      return !this.frames.some(frame => frame.voiced && frame.at >= Math.min(...times) - 250
        && frame.at <= end);
    })) return 'voice_unobserved';
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
      : this.pendingInputs.size ? 'awaiting_input_classification'
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
  /** A buffered clip has known receipts before audio starts; bind them at observed onset,
   * after the media element has begun progressing, not during its initial silent stall. */
  scheduled(cue, segment, onsetWaitMs = 500) {
    if (!Number.isFinite(onsetWaitMs) || onsetWaitMs < 500 || onsetWaitMs > 45000) throw Error('Invalid onset bound');
    this.gate.reset(); this.gate.update(cue, [segment]);
    this.onset = {ids: [...segment.receiptIds], deadline: performance.now() + onsetWaitMs};
  }
  update(cue, segments) {
    if (this.closed) return;
    this.gate.update(cue, segments);
  }
  interrupt() { this.onset = null; this.gate.interrupt(); }
  sample(value) {
    this.gate.sample(value);
    if (this.onset && performance.now() > this.onset.deadline) this.onset = null;
    if (this.onset && value.voiced && this.gate.frames.at(-1)?.voiced) {
      for (const id of this.onset.ids) this.gate.caption(id);
      this.onset = null;
    }
  }
  input(id) {
    if (this.gate.cue?.allowAcknowledgements) this.gate.input(id);
    else this.interrupt();
  }
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
        this.sample({voiced: rms > 500 / 32768, mediaTime: this.audio.currentTime,
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
  close() { this.closed = true; this.onset = null; this.gate.reset(); this.release(); this.output = null; }
}
