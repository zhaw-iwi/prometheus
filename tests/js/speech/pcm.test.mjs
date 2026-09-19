import test from "node:test";
import assert from "node:assert/strict";
import { Pcm16Decoder, PcmRenderBuffer, PCM_CAPACITY } from "../../../src/main/resources/public/speech/pcm-buffer.js";
import { preparePcmSpeech } from "../../../src/main/resources/public/speech/pcm.js";

const flush = () => new Promise(resolve => setImmediate(resolve));
const headers = { "Content-Type": "audio/pcm;rate=24000;channels=1;encoding=s16le" };

test("PCM decoder preserves signed little-endian samples across every byte boundary", () => {
  const bytes = new Uint8Array([0, 128, 255, 255, 0, 0, 255, 127]);
  for (let split = 0; split <= bytes.length; split++) {
    const decoder = new Pcm16Decoder();
    assert.deepEqual([...decoder.decode(bytes.slice(0, split)), ...decoder.decode(new Uint8Array()),
      ...decoder.decode(bytes.slice(split))], [-1, -1 / 32768, 0, 32767 / 32768]);
    decoder.finish();
  }
  const truncated = new Pcm16Decoder(); truncated.decode(new Uint8Array([1]));
  assert.throws(() => truncated.finish(), /inside an audio sample/);
});

test("PCM renderer waits for prefill, wraps in order, drains short EOF and bounds memory", () => {
  const buffer = new PcmRenderBuffer({ capacity: 8, prefill: 4 });
  const output = new Float32Array(3);
  buffer.append(new Float32Array([1, 2]));
  assert.equal(buffer.render(output).consumed, 0);
  buffer.append(new Float32Array([3, 4, 5, 6]));
  assert.equal(buffer.render(output).first, true);
  assert.deepEqual([...output], [1, 2, 3]);
  buffer.append(new Float32Array([7, 8, 9, 10]));
  assert.throws(() => buffer.append(new Float32Array(2)), /overflow/);
  buffer.render(output); assert.deepEqual([...output], [4, 5, 6]);
  buffer.render(output); assert.deepEqual([...output], [7, 8, 9]);
  buffer.eof = true;
  assert.equal(buffer.render(output).ended, true);
  assert.deepEqual([...output], [10, 0, 0]);
  const short = new PcmRenderBuffer(); short.append(new Float32Array([0.5])); short.eof = true;
  assert.deepEqual(short.render(output), { consumed: 1, first: true, resumed: false, initialFrames: 1, ended: true });
});

test("renderer counts only gaps followed by more audio, never initial buffering or final silence", () => {
  const buffer = new PcmRenderBuffer({ capacity: 8, prefill: 2 });
  const output = new Float32Array(4);
  buffer.render(output);
  buffer.append(new Float32Array([1, 2])); buffer.render(output);
  buffer.render(output);
  assert.equal(buffer.underruns, 0);
  buffer.append(new Float32Array([3, 4]));
  assert.equal(buffer.render(output).resumed, true);
  assert.equal(buffer.underruns, 1);
  assert.equal(buffer.gapFrames, 6);
  buffer.eof = true; buffer.render(output);
  assert.equal(buffer.underruns, 1);
});

function harness({ sinkFailure = false, moduleFailure = false } = {}) {
  const stages = [], metrics = [], calls = [], diagnostics = [];
  let clock = 0;
  let node, context, feed, cancelled = 0;
  class AudioContextClass {
    constructor() { context = this; this.state = "running"; this.sampleRate = 24000; this.currentTime = 0; this.destination = {}; }
    audioWorklet = { addModule: async () => { calls.push("module"); if (moduleFailure) throw new Error("module"); } };
    async setSinkId(id) { calls.push(`sink:${id}`); if (sinkFailure) throw new Error("sink"); }
    async resume() { calls.push("resume"); }
    async close() { this.state = "closed"; calls.push("close"); }
  }
  class AudioWorkletNodeClass {
    constructor() {
      node = this; this.buffer = new PcmRenderBuffer(); this.maxQueued = 0;
      this.port = { postMessage: data => {
        if (data.type === "samples") this.buffer.append(data.samples);
        if (data.type === "end") this.buffer.eof = true;
        this.maxQueued = Math.max(this.maxQueued, this.buffer.length);
      }, close: () => calls.push("port-close") };
    }
    connect() { calls.push("connect"); }
    disconnect() { calls.push("disconnect"); }
    render(frames) {
      const output = new Float32Array(frames), result = this.buffer.render(output);
      if (result.first) this.port.onmessage({ data: { type: "playing", bufferedMs: result.initialFrames / 24 } });
      if (result.consumed) this.port.onmessage({ data: { type: "consumed", frames: result.consumed } });
      if (result.ended) this.port.onmessage({ data: { type: "ended" } });
      return output;
    }
  }
  const response = new Response(new ReadableStream({ start(controller) { feed = controller; }, cancel() { cancelled++; } }), { headers });
  return { AudioContextClass, AudioWorkletNodeClass, response, feed, stages, metrics, calls, diagnostics,
    now: () => clock, advance: ms => { clock += ms; }, onDiagnostic: event => diagnostics.push(event),
    onStage: stage => stages.push(stage), onMetrics: value => metrics.push(value),
    get node() { return node; }, get context() { return context; }, get cancelled() { return cancelled; } };
}

test("PCM starts on rendered samples before EOF, applies the selected device and cancels the held tail", async () => {
  const h = harness(), controller = new AbortController();
  const { resource } = await preparePcmSpeech({ ...h, deviceId: "speaker", signal: controller.signal });
  assert.deepEqual(h.calls.slice(0, 4), ["sink:speaker", "module", "resume", "connect"]);
  resource.setResponse(h.response);
  const playing = resource.play(() => h.stages.push("playing"));
  h.feed.enqueue(new Uint8Array(4800)); await flush();
  assert.equal(h.diagnostics.find(event => event.type === "read").bytes, 4800);
  assert.equal(h.diagnostics.find(event => event.type === "posted").frames, 2400);
  assert.deepEqual(h.stages, ["audio_first_byte"]);
  h.node.render(128);
  assert.deepEqual(h.stages, ["audio_first_byte", "playing"]);
  controller.abort();
  await assert.rejects(playing, { name: "AbortError" });
  await resource.dispose(); await resource.dispose();
  assert.equal(h.diagnostics.filter(event => event.type === "stopped").length, 1);
  assert.equal(h.cancelled, 1);
  assert.equal(h.calls.filter(call => call === "close").length, 1);
});

test("PCM backpressure caps queued audio and completion waits for rendering", async () => {
  const h = harness();
  const { resource } = await preparePcmSpeech(h);
  resource.setResponse(h.response);
  let complete = false;
  const playing = resource.play().then(() => { complete = true; });
  h.feed.enqueue(new Uint8Array(PCM_CAPACITY * 3)); h.feed.close(); await flush();
  assert.equal(h.node.maxQueued, PCM_CAPACITY);
  h.advance(125);
  assert.equal(complete, false);
  for (let index = 0; index < 8 && !h.node.buffer.eof; index++) { h.node.render(12000); await flush(); }
  assert.ok(h.node.maxQueued <= PCM_CAPACITY);
  assert.equal(h.stages.at(-1), "audio_downloaded");
  assert.equal(complete, false);
  h.node.render(PCM_CAPACITY);
  await playing; await resource.dispose();
  assert.ok(h.diagnostics.some(event => event.type === "backpressure_end" && event.waitMs === 125));
  assert.equal(h.diagnostics.find(event => event.type === "eof").totalBytes, PCM_CAPACITY * 3);
  assert.ok(!h.diagnostics.some(event => ["stopped", "failed"].includes(event.type)));
});

test("PCM read observations distinguish transport waiting from backpressure and preserve renderer clocks", async () => {
  const h = harness(), controller = new AbortController();
  const { resource } = await preparePcmSpeech({ ...h, signal: controller.signal });
  resource.setResponse(h.response);
  const playing = resource.play();
  h.advance(25); h.feed.enqueue(new Uint8Array(4800)); await flush();
  h.advance(175); h.feed.enqueue(new Uint8Array(2400)); await flush();
  const reads = h.diagnostics.filter(event => event.type === "read");
  assert.deepEqual(reads.map(({ chunk, bytes, readWaitMs }) => [chunk, bytes, readWaitMs]), [[1, 4800, 25], [2, 2400, 175]]);
  assert.equal(reads[1].previousReadGapMs, 175);
  assert.equal(reads[1].outstandingFrames, 2400);
  h.context.currentTime = 0.2;
  h.node.port.onmessage({ data: { type: "diagnostic", event: "buffer_empty", renderFrame: 4700, playedFrames: 2400, bufferedFrames: 0 } });
  assert.deepEqual(h.diagnostics.at(-1), { type: "buffer_empty", at: 200, contextTimeMs: 200,
    renderFrame: 4700, playedFrames: 2400, bufferedFrames: 0 });
  controller.abort(); await assert.rejects(playing, { name: "AbortError" }); await resource.dispose();
});

test("real worklet metadata locates starvation and resume without counting final tail wait as an interruption", async () => {
  const saved = Object.fromEntries(["AudioWorkletProcessor", "registerProcessor", "currentFrame"].map(key => [key, globalThis[key]]));
  let Processor;
  try {
    globalThis.AudioWorkletProcessor = class { constructor() { this.messages = []; this.port = { postMessage: data => this.messages.push(data) }; } };
    globalThis.registerProcessor = (_name, type) => { Processor = type; };
    globalThis.currentFrame = 0;
    await import("../../../src/main/resources/public/speech/pcm-worklet.js");
    const processor = new Processor();
    const render = () => { processor.process([], [[new Float32Array(128)]], { gain: [1] }); globalThis.currentFrame += 128; };
    const append = block => processor.port.onmessage({ data: { type: "samples", samples: new Float32Array(1440), chunk: block, block } });
    render(); append(1);
    for (let i = 0; i < 14; i++) render();
    const empty = processor.messages.find(event => event.event === "buffer_empty");
    assert.equal(empty.renderFrame, 128 + 1440); assert.equal(empty.playedFrames, 1440);
    append(2); render();
    const resume = processor.messages.find(event => event.event === "render_resume");
    assert.equal(resume.renderFrame, 1920); assert.equal(resume.gapFrames, 352);
    assert.equal(resume.playedFrames, 1440); assert.equal(resume.bufferedFrames, 1440);
    for (let i = 0; i < 14; i++) render();
    processor.port.onmessage({ data: { type: "end" } }); render();
    const end = processor.messages.find(event => event.event === "render_end");
    assert.equal(end.playedFrames, 2880); assert.ok(end.pendingGapFrames > 0);
    assert.equal(processor.messages.filter(event => event.type === "underrun").length, 1);
    assert.deepEqual(processor.messages.filter(event => event.event === "render_receive").map(event => event.block), [1, 2]);
    assert.ok(!JSON.stringify(processor.messages).includes('"samples"'));
  } finally {
    for (const [key, value] of Object.entries(saved)) { if (value === undefined) delete globalThis[key]; else globalThis[key] = value; }
  }
});

test("unsupported output and setup failures release preparation before an MP3 request", async () => {
  assert.equal((await preparePcmSpeech({ AudioContextClass: null })).fallbackReason, "pcm_unsupported");
  for (const options of [{ sinkFailure: true }, { moduleFailure: true }]) {
    const h = harness(options);
    const result = await preparePcmSpeech({ ...h, deviceId: "speaker" });
    assert.equal(result.resource, null);
    assert.equal(result.fallbackReason, "pcm_setup_failed");
    assert.equal(h.context.state, "closed");
  }
  const h = harness();
  h.AudioContextClass.prototype.setSinkId = undefined;
  assert.equal((await preparePcmSpeech({ ...h, deviceId: "speaker" })).fallbackReason, "pcm_unsupported");
});

test("invalid PCM metadata, truncated samples and oversized audio fail without a second request", async () => {
  for (const kind of ["metadata", "truncated", "oversized", "empty"]) {
    const h = harness();
    const { resource } = await preparePcmSpeech({ ...h, maxBytes: 4 });
    if (kind === "metadata") {
      assert.throws(() => resource.setResponse(new Response(new Uint8Array(2))), /metadata/);
    } else {
      resource.setResponse(h.response);
      const playing = resource.play();
      if (kind !== "empty") h.feed.enqueue(new Uint8Array(kind === "truncated" ? 1 : 5));
      h.feed.close();
      await assert.rejects(playing, /sample|size limit|empty/);
    }
    await resource.dispose();
    assert.equal(h.context.state, "closed");
  }
});

test("PCM stalled network timeout and output interruption release a pending reader", async t => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  for (const kind of ["timeout", "interrupted"]) {
    const h = harness();
    const { resource } = await preparePcmSpeech({ ...h, timeoutMs: 100 });
    resource.setResponse(h.response);
    const playing = resource.play();
    if (kind === "timeout") t.mock.timers.tick(101);
    else { h.context.state = "suspended"; h.context.onstatechange(); }
    await assert.rejects(playing, /progress|interrupted/);
    await resource.dispose(); assert.equal(h.cancelled, 1);
  }
});
