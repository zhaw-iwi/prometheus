// Original PCM stays in memory. Acoustic grouping is not semantic approval.
const ONSET_SKEW_MS = 1000; // Separate WebRTC/caption transports; still below the 2.4-second grouping gap.
const END_SKEW_MS = 1500; // Includes transport skew and the final caption token's spoken duration.
export class SpeechBuffer {
  constructor(rate, {quietMs = 2400, maxMs = 30000, retainMs = 60000} = {}) {
    if (!Number.isInteger(rate) || rate < 8000 || rate > 96000) throw Error('Unsupported capture rate');
    Object.assign(this, {rate, quietMs, maxMs, retainMs});
    this.receipts = new Map(); this.ready = []; this.active = null; this.last = null; this.blocked = false;
    this.quiet = null; this.serial = 0; this.generation = 0; this.preroll = [];
    this.frames = 0; this.voicedFrames = 0; this.peakRms = 0;
  }
  interrupt(blockTail = this.active !== null || this.blocked) {
    this.generation++; this.active = null; this.ready = []; this.receipts.clear(); this.blocked = blockTail; this.quiet = null; this.preroll = [];
  }
  caption(event, at) {
    if (typeof event.event_id !== 'string' || event.event_id.length > 200 || typeof event.delta !== 'string'
        || event.delta.length > 3000 || !Number.isFinite(at)) throw Error('Invalid receipt');
    if (this.receipts.has(event.event_id)) return;
    if (this.receipts.size >= 512) throw Error('Speech receipt buffer full');
    this.receipts.set(event.event_id, {at, event});
  }
  frame(samples, end) {
    if (!(samples instanceof Float32Array) || !samples.length || samples.length > this.rate / 5
        || !Number.isFinite(end) || samples.some(v => !Number.isFinite(v))) throw Error('Invalid audio block');
    const duration = samples.length * 1000 / this.rate, start = end - duration;
    if (this.last !== null && (end <= this.last || start - this.last > 100)) this.interrupt(true);
    this.last = end;
    const rms = Math.sqrt(samples.reduce((sum, v) => sum + v * v, 0) / samples.length), voiced = rms > 500 / 32768;
    this.frames++; this.voicedFrames += voiced ? 1 : 0; this.peakRms = Math.max(this.peakRms, rms);
    this.ready = this.ready.filter(s => end - s.end < this.retainMs);
    for (const [id, value] of this.receipts) if (end - value.at > this.retainMs) this.receipts.delete(id);
    if (voiced) this.quiet = null; else this.quiet ??= start;
    if (this.blocked) { if (this.quiet !== null && end - this.quiet >= this.quietMs) this.blocked = false; return; }
    if (!this.active && voiced) {
      const count = this.preroll.reduce((n, frame) => n + frame.length, 0);
      this.active = {id: ++this.serial, generation: this.generation, start, pcmStart: start - count * 1000 / this.rate,
        voiceEnd: end, frames: this.preroll, samples: count};
      this.preroll = [];
    }
    const current = this.active;
    if (!current) {
      this.preroll.push(samples);
      while (this.preroll.reduce((n, f) => n + f.length, 0) > this.rate / 10) this.preroll.shift();
      return;
    }
    current.frames.push(samples); current.samples += samples.length;
    if (voiced) current.voiceEnd = end;
    if (end - current.start > this.maxMs || current.samples > this.rate * this.maxMs / 1000) { this.interrupt(true); return; }
    if (this.quiet !== null && end - this.quiet >= this.quietMs) {
      current.end = end; this.ready.push(current); this.active = null;
      // At most two bounded utterances; never build an unbounded playback queue.
      if (this.ready.length > 2) this.ready.shift();
    }
  }
  select(segment) {
    if (segment?.speaker !== 'ASSISTANT' || segment.status !== 'COMPLETE' || !segment.receiptIds?.length
        || segment.receiptIds.length > 128 || new Set(segment.receiptIds).size !== segment.receiptIds.length) return null;
    const receipts = segment.receiptIds.map(id => this.receipts.get(id));
    if (receipts.some(v => !v)) return null;
    if (receipts.map(v => v.event.delta).join('').trim() !== segment.text) return null;
    const first = Math.min(...receipts.map(v => v.at)), last = Math.max(...receipts.map(v => v.at));
    const candidates = this.ready.filter(s => first >= s.start - ONSET_SKEW_MS && first <= s.start + ONSET_SKEW_MS
      && last >= s.voiceEnd - END_SKEW_MS && last <= s.voiceEnd + END_SKEW_MS
      && receipts.every(v => v.at >= s.start - ONSET_SKEW_MS && v.at <= s.voiceEnd + END_SKEW_MS));
    if (candidates.length !== 1) return null;
    const selected = candidates[0], ids = new Set(segment.receiptIds);
    if ([...this.receipts].some(([id, v]) => v.at >= selected.start - ONSET_SKEW_MS && v.at <= selected.voiceEnd + END_SKEW_MS && !ids.has(id))) return null;
    return selected;
  }
  diagnostic() {
    return {frames: this.frames, voicedFrames: this.voicedFrames, peakRms: this.peakRms,
      generation: this.generation, blocked: this.blocked, last: this.last, receiptCount: this.receipts.size,
      firstReceiptAt: this.receipts.values().next().value?.at,
      lastReceiptAt: [...this.receipts.values()].at(-1)?.at,
      active: this.active && {start: this.active.start, voiceEnd: this.active.voiceEnd},
      ready: this.ready.map(s => ({start: s.start, voiceEnd: s.voiceEnd, end: s.end}))};
  }
  take(candidate) {
    if (candidate.generation !== this.generation || !this.ready.includes(candidate)) return null;
    this.ready = this.ready.filter(s => s !== candidate);
    const length = Math.min(candidate.samples, Math.ceil((candidate.voiceEnd - candidate.pcmStart + 250) * this.rate / 1000));
    const pcm = new Float32Array(length); let at = 0;
    for (const frame of candidate.frames) { const n = Math.min(frame.length, length - at); if (n <= 0) break; pcm.set(frame.subarray(0, n), at); at += n; }
    return pcm;
  }
}
