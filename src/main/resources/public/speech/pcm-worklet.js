import { PcmRenderBuffer, PCM_RATE } from "./pcm-buffer.js";

class PcmSpeechProcessor extends AudioWorkletProcessor {
  static get parameterDescriptors() { return [{ name: "gain", defaultValue: 1, minValue: 0, maxValue: 1, automationRate: "k-rate" }]; }
  constructor() {
    super();
    this.buffer = new PcmRenderBuffer();
    this.credit = 0; this.failed = false; this.playedFrames = 0;
    this.port.onmessage = ({ data }) => {
      try {
        if (data.type === "samples") {
          this.buffer.append(data.samples);
          this.diagnostic("render_receive", { chunk: data.chunk, block: data.block, frames: data.samples.length });
        }
        else if (data.type === "end") this.buffer.eof = true;
      } catch (_) { this.failed = true; this.port.postMessage({ type: "error" }); }
    };
  }
  diagnostic(event, details = {}, frame = currentFrame) {
    this.port.postMessage({ type: "diagnostic", event, renderFrame: frame,
      playedFrames: this.playedFrames, bufferedFrames: this.buffer.length, ...details });
  }
  process(_inputs, outputs, parameters) {
    if (this.failed) return false;
    const pendingGap = this.buffer.pendingGap;
    const result = this.buffer.render(outputs[0][0]);
    const gain = parameters.gain[0];
    if (gain !== 1) for (let index = 0; index < result.consumed; index++) outputs[0][0][index] *= gain;
    this.credit += result.consumed;
    if (result.first) {
      this.diagnostic("render_start", { bufferedFrames: result.initialFrames });
      this.port.postMessage({ type: "playing", bufferedMs: result.initialFrames / PCM_RATE * 1000 });
    }
    if (result.resumed) {
      this.diagnostic("render_resume", { bufferedFrames: result.initialFrames, gapFrames: pendingGap });
      this.port.postMessage({ type: "underrun", count: this.buffer.underruns, gapMs: this.buffer.gapFrames / PCM_RATE * 1000 });
    }
    this.playedFrames += result.consumed;
    if (this.buffer.pendingGap > 0 && (pendingGap === 0 || result.resumed)) {
      this.diagnostic("buffer_empty", {}, currentFrame + result.consumed);
    }
    if (this.credit >= 1024 || this.buffer.buffering || result.ended) {
      if (this.credit) this.port.postMessage({ type: "consumed", frames: this.credit });
      this.credit = 0;
    }
    if (result.ended) {
      this.diagnostic("render_end", { pendingGapFrames: this.buffer.pendingGap }, currentFrame + result.consumed);
      this.port.postMessage({ type: "ended" });
    }
    return !result.ended;
  }
}
registerProcessor("prometheus-pcm-speech", PcmSpeechProcessor);
