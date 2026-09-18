export const PCM_RATE = 24000;
export const PCM_PREFILL = 1440; // 60 ms, also used after a buffer underrun.
export const PCM_CAPACITY = 48000; // At most two seconds queued in the renderer.

/** Network chunks can split an individual little-endian signed 16-bit sample. */
export class Pcm16Decoder {
  constructor() { this.lowByte = null; }
  decode(bytes) {
    const samples = new Float32Array(Math.floor((bytes.length + (this.lowByte === null ? 0 : 1)) / 2));
    let input = 0, output = 0;
    const sample = (low, high) => ((low | (high << 8)) << 16 >> 16) / 32768;
    if (this.lowByte !== null && bytes.length) {
      samples[output++] = sample(this.lowByte, bytes[input++]); this.lowByte = null;
    }
    while (input + 1 < bytes.length) {
      samples[output++] = sample(bytes[input], bytes[input + 1]); input += 2;
    }
    if (input < bytes.length) this.lowByte = bytes[input];
    return samples;
  }
  finish() { if (this.lowByte !== null) throw new Error("PCM stream ended inside an audio sample."); }
}

/** Single producer/consumer on the audio worklet thread; no shared memory needed. */
export class PcmRenderBuffer {
  constructor({ capacity = PCM_CAPACITY, prefill = PCM_PREFILL } = {}) {
    this.samples = new Float32Array(capacity);
    this.prefill = prefill;
    this.read = 0; this.write = 0; this.length = 0;
    this.eof = false; this.started = false; this.buffering = true;
    this.underruns = 0; this.gapFrames = 0; this.pendingGap = 0;
  }
  append(samples) {
    if (this.eof || samples.length > this.samples.length - this.length) throw new Error("PCM playback buffer overflow.");
    for (const value of samples) {
      this.samples[this.write] = value;
      this.write = (this.write + 1) % this.samples.length;
    }
    this.length += samples.length;
  }
  render(output) {
    output.fill(0);
    let first = false, resumed = false, initialFrames = 0, consumed = 0;
    if (this.buffering && this.length && (this.length >= this.prefill || this.eof)) {
      first = !this.started;
      initialFrames = this.length;
      resumed = this.started && this.pendingGap > 0;
      if (resumed) { this.underruns++; this.gapFrames += this.pendingGap; }
      this.pendingGap = 0; this.started = true; this.buffering = false;
    }
    if (!this.buffering) {
      consumed = Math.min(output.length, this.length);
      for (let index = 0; index < consumed; index++) {
        output[index] = this.samples[this.read];
        this.read = (this.read + 1) % this.samples.length;
      }
      this.length -= consumed;
    }
    if (consumed < output.length && this.started && !this.eof) {
      this.buffering = true;
      this.pendingGap += output.length - consumed;
    }
    return { consumed, first, resumed, initialFrames, ended: this.eof && !this.length };
  }
}
