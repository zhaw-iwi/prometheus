import { test } from "node:test";
import assert from "node:assert/strict";
import { createSpeechAudio } from "../../../src/main/resources/public/speech/progressive.js";

function harness({ supported = true, setupFailure = false, decodeFailure = false } = {}) {
  const urls = new Map(), revoked = [], appended = [];
  let source, feed, cancelled = 0, firstAppend;
  const first = new Promise(resolve => { firstAppend = resolve; });
  class MediaSource extends EventTarget {
    static isTypeSupported() { return supported; }
    constructor() { super(); source = this; this.readyState = "closed"; }
    addSourceBuffer() {
      if (setupFailure) throw new Error("unsupported");
      const buffer = new EventTarget();
      buffer.appendBuffer = chunk => {
        appended.push([...chunk]);
        queueMicrotask(() => { buffer.dispatchEvent(new Event(decodeFailure ? "error" : "updateend")); firstAppend(); });
      };
      return buffer;
    }
    removeSourceBuffer() {}
    endOfStream() { this.readyState = "ended"; }
  }
  const urlApi = {
    createObjectURL(value) { const url = `blob:${urls.size}`; urls.set(url, value); return url; },
    revokeObjectURL(url) { revoked.push(url); },
  };
  const audio = { load() { if (source?.readyState === "closed") queueMicrotask(() => { source.readyState = "open"; source.dispatchEvent(new Event("sourceopen")); }); } };
  const response = new Response(new ReadableStream({ start(c) { feed = c; }, cancel() { cancelled++; } }), { headers: { "Content-Type": "audio/mpeg" } });
  return { response, MediaSourceClass: MediaSource, urlApi, audio, feed, appended, revoked, first, urls, get cancelled() { return cancelled; } };
}

test("appends in order and exposes progress before EOF; dispose is idempotent", async () => {
  const h = harness(), stages = [];
  const r = await createSpeechAudio(h.response, { ...h, onStage: s => stages.push(s) });
  await r.attach(h.audio);
  h.feed.enqueue(new Uint8Array([1, 2])); await h.first;
  assert.equal(r.progressive, true);
  assert.deepEqual(stages, ["audio_first_byte"]);
  h.feed.enqueue(new Uint8Array([3])); h.feed.close(); await r.done;
  assert.deepEqual(h.appended, [[1, 2], [3]]);
  assert.deepEqual(stages, ["audio_first_byte", "audio_downloaded"]);
  r.dispose(); r.dispose(); assert.deepEqual(h.revoked, [r.url]);
});

test("unsupported capability and setup failure use the same response in buffered mode", async () => {
  for (const options of [{ supported: false }, { setupFailure: true }]) {
    const h = harness(options);
    h.feed.enqueue(new Uint8Array([1, 2, 3])); h.feed.close();
    const r = await createSpeechAudio(h.response, h);
    await r.attach(h.audio); await r.done;
    assert.equal(r.progressive, false);
    assert.equal(h.urls.get(r.url).size, 3);
    assert.deepEqual(h.appended, []); r.dispose();
  }
});

test("bounded buffered and progressive paths cancel oversized upstream bodies", async () => {
  for (const supported of [false, true]) {
    const h = harness({ supported });
    h.feed.enqueue(new Uint8Array([1, 2, 3]));
    if (!supported) await assert.rejects(createSpeechAudio(h.response, { ...h, maxBytes: 2 }), /size limit/);
    else {
      const r = await createSpeechAudio(h.response, { ...h, maxBytes: 2 });
      await r.attach(h.audio); await assert.rejects(r.done, /size limit/); r.dispose();
    }
    assert.equal(h.cancelled, 1);
  }
});

test("Stop before attach or while waiting for the tail closes the reader", async () => {
  for (const attach of [false, true]) {
    const h = harness(), controller = new AbortController();
    const r = await createSpeechAudio(h.response, { ...h, signal: controller.signal });
    if (attach) { await r.attach(h.audio); h.feed.enqueue(new Uint8Array([1])); await h.first; }
    controller.abort(); await assert.rejects(r.done, { name: "AbortError" });
    r.dispose(); assert.equal(h.cancelled, 1);
  }
});

test("empty and midstream decoding failures are explicit, never replayed", async () => {
  const empty = harness({ supported: false }); empty.feed.close();
  await assert.rejects(createSpeechAudio(empty.response, empty), /empty audio/);
  const h = harness({ decodeFailure: true });
  const r = await createSpeechAudio(h.response, h); await r.attach(h.audio);
  h.feed.enqueue(new Uint8Array([1])); await assert.rejects(r.done, /decoding failed/);
  r.dispose(); assert.equal(h.cancelled, 1); assert.equal(h.urls.size, 1);
});

test("stalled tail times out as a failure and releases the upstream reader", async (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const h = harness();
  const r = await createSpeechAudio(h.response, { ...h, timeoutMs: 30000 });
  await r.attach(h.audio);
  t.mock.timers.tick(30000);
  await assert.rejects(r.done, /timed out/);
  assert.equal(h.cancelled, 1); r.dispose();
});
