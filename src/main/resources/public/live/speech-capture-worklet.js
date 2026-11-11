// Runs on the rendering thread; its output is always silence.
class SpeechCapture extends AudioWorkletProcessor {
  constructor(options) {
    super(); this.inputOnly = !!options.processorOptions?.inputOnly;
    this.block = new Float32Array(Math.round(sampleRate / 50)); this.used = 0; this.voice = 0; this.active = false;
  }
  process(inputs, outputs) {
    for (const channels of outputs) for (const channel of channels) channel.fill(0);
    const input = inputs[0]?.[0];
    if (!input) return true;
    for (let i = 0; i < input.length; i++) {
      this.block[this.used++] = input[i];
      if (this.used === this.block.length) {
        const end = (currentTime + (i + 1) / sampleRate) * 1000;
        if (this.inputOnly) {
          const voiced = Math.sqrt(this.block.reduce((s, v) => s + v * v, 0) / this.block.length) > 500 / 32768;
          this.voice = voiced ? this.voice + 1 : 0;
          if (this.voice >= 2 && !this.active) { this.active = true; this.port.postMessage({voice: true}); }
          if (!voiced) this.active = false;
        } else this.port.postMessage({samples: this.block, end}, [this.block.buffer]);
        this.block = new Float32Array(Math.round(sampleRate / 50)); this.used = 0;
      }
    }
    return true;
  }
}
registerProcessor('live-speech-capture', SpeechCapture);
