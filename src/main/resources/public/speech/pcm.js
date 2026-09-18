import { Pcm16Decoder, PCM_RATE, PCM_PREFILL, PCM_CAPACITY } from "./pcm-buffer.js";

const WORKLET = new URL("./pcm-worklet.js", import.meta.url).href;
const stopped = () => new DOMException("Speech playback was stopped.", "AbortError");

/** Prepare the actual renderer/device before choosing a provider format. */
export async function preparePcmSpeech({
  signal, deviceId = "", volume = 1, onStage = () => {}, onMetrics = () => {},
  AudioContextClass = globalThis.AudioContext, AudioWorkletNodeClass = globalThis.AudioWorkletNode,
  setupTimeoutMs = 2000, timeoutMs = 30000, maxBytes = 16 * 1024 * 1024,
} = {}) {
  if (signal?.aborted) throw stopped();
  if (!AudioContextClass || !AudioWorkletNodeClass) return { resource: null, fallbackReason: "pcm_unsupported" };
  let context, node, reader, response, wake, resolvePlay, rejectPlay, timer;
  let disposed = false, started = false, total = 0, queued = 0, settled = false;
  const controller = new AbortController();
  const playDone = new Promise((resolve, reject) => { resolvePlay = resolve; rejectPlay = reject; });
  playDone.catch(() => {});
  const check = () => { if (disposed || controller.signal.aborted) throw controller.signal.reason || stopped(); };
  const fail = (error) => {
    if (settled) return;
    settled = true; clearTimeout(timer);
    controller.abort(error); node?.disconnect(); wake?.(); rejectPlay(error);
    void reader?.cancel().catch(() => {});
  };
  const abort = () => fail(stopped());
  signal?.addEventListener("abort", abort, { once: true });
  async function bounded(promise, milliseconds, message) {
    let deadline;
    let cancel;
    try {
      check();
      return await Promise.race([promise,
        new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error(message)), milliseconds); }),
        new Promise((_, reject) => {
          cancel = () => reject(controller.signal.reason || stopped());
          controller.signal.addEventListener("abort", cancel, { once: true });
        }),
      ]);
    } finally { clearTimeout(deadline); controller.signal.removeEventListener("abort", cancel); }
  }
  function refreshDeadline() {
    clearTimeout(timer);
    timer = setTimeout(() => fail(new Error("PCM playback made no progress.")), timeoutMs);
  }
  const resource = {
    progressive: true, format: "pcm", url: null,
    setResponse(value) {
      check();
      if (response) throw new Error("PCM response is already assigned.");
      response = value;
      const type = response.headers.get("Content-Type") || "";
      const parts = type.toLowerCase().split(";").map(part => part.trim());
      if (parts[0] !== "audio/pcm" || !["rate=24000", "channels=1", "encoding=s16le"].every(part => parts.includes(part))
          || !response.body?.getReader) throw new Error("Speech synthesis returned invalid PCM audio metadata.");
      if (Number(response.headers.get("Content-Length")) > maxBytes) throw new Error("Speech audio exceeds the playback size limit.");
      reader = response.body.getReader();
    },
    async play(onPlaying = () => {}) {
      check();
      if (started || !reader) throw new Error("PCM playback is not ready.");
      started = true;
      onMetrics({ pcmPrefillMs: PCM_PREFILL / PCM_RATE * 1000, pcmUnderruns: 0, pcmGapMs: 0,
        playbackStartSource: "pcm_renderer" });
      node.port.onmessage = ({ data }) => {
        if (settled || disposed) return;
        if (data.type === "playing") {
          onMetrics({ pcmInitialBufferedMs: data.bufferedMs });
          onPlaying(); refreshDeadline();
        } else if (data.type === "underrun") {
          onMetrics({ pcmUnderruns: data.count, pcmGapMs: data.gapMs });
        } else if (data.type === "consumed") {
          queued = Math.max(0, queued - data.frames); wake?.(); refreshDeadline();
        } else if (data.type === "error") fail(new Error("PCM audio rendering failed."));
        else if (data.type === "ended") {
          // Drain the renderer/device pipeline before teardown and reopening input.
          clearTimeout(timer);
          const latency = (context.baseLatency || 0) + (Number.isFinite(context.outputLatency) ? context.outputLatency : 0.1);
          timer = setTimeout(() => {
            if (!settled) { settled = true; resolvePlay(); }
          }, Math.max(20, latency * 1000));
        }
      };
      node.onprocessorerror = () => fail(new Error("PCM audio processor failed."));
      context.onstatechange = () => { if (context.state !== "running") fail(new Error("PCM audio output was interrupted.")); };
      refreshDeadline();
      void pump().catch(fail);
      return playDone;
    },
    async dispose() {
      if (disposed) return;
      abort(); disposed = true; clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
      if (node) { node.disconnect(); node.port.close(); node.onprocessorerror = null; }
      if (context) { context.onstatechange = null; void context.close().catch(() => {}); }
      if (reader) await reader.cancel().catch(() => {});
      else if (response) await response.body?.cancel().catch(() => {});
    },
  };
  async function pump() {
    const decoder = new Pcm16Decoder();
    try {
      for (;;) {
        const chunk = await bounded(reader.read(), timeoutMs, "PCM stream timed out.");
        check();
        if (chunk.done) {
          decoder.finish();
          if (!total) throw new Error("Speech synthesis returned empty audio.");
          onStage("audio_downloaded");
          node.port.postMessage({ type: "end" });
          return;
        }
        if (!total && chunk.value.byteLength) onStage("audio_first_byte");
        total += chunk.value.byteLength;
        if (total > maxBytes) throw new Error("Speech audio exceeds the playback size limit.");
        const samples = decoder.decode(chunk.value);
        for (let offset = 0; offset < samples.length;) {
          const length = Math.min(2400, samples.length - offset);
          while (queued + length > PCM_CAPACITY) {
            await bounded(new Promise(resolve => { wake = resolve; }), timeoutMs, "PCM playback buffer stalled.");
            wake = null; check();
          }
          check();
          const block = samples.slice(offset, offset + length);
          queued += length;
          node.port.postMessage({ type: "samples", samples: block }, [block.buffer]);
          offset += length;
        }
      }
    } finally { reader.releaseLock(); }
  }
  try {
    context = new AudioContextClass({ sampleRate: PCM_RATE, latencyHint: "interactive" });
    if (!context.audioWorklet || context.sampleRate !== PCM_RATE || (deviceId && typeof context.setSinkId !== "function")) {
      await resource.dispose();
      return { resource: null, fallbackReason: "pcm_unsupported" };
    }
    await bounded((async () => {
      if (deviceId) await context.setSinkId(deviceId);
      check();
      await context.audioWorklet.addModule(WORKLET);
      check();
      await context.resume();
    })(), setupTimeoutMs, "PCM audio preparation timed out.");
    check();
    node = new AudioWorkletNodeClass(context, "prometheus-pcm-speech", { numberOfInputs: 0, numberOfOutputs: 1,
      outputChannelCount: [1], parameterData: { gain: Number.isFinite(volume) ? Math.max(0, Math.min(1, volume)) : 1 } });
    node.connect(context.destination);
    return { resource, fallbackReason: null };
  } catch (error) {
    await resource.dispose();
    if (signal?.aborted || error?.name === "AbortError") throw stopped();
    return { resource: null, fallbackReason: "pcm_setup_failed" };
  }
}
