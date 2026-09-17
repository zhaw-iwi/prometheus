const MP3 = "audio/mpeg";
const MAX_BYTES = 16 * 1024 * 1024;

// One fetched response, one reader, one pending append. Never request synthesis again.
export async function createSpeechAudio(response, {
  signal, onStage = () => {}, MediaSourceClass = globalThis.MediaSource,
  urlApi = globalThis.URL, maxBytes = MAX_BYTES, timeoutMs = 30000,
} = {}) {
  const type = (response.headers.get("Content-Type") || MP3).split(";")[0].trim().toLowerCase();
  if (!type.startsWith("audio/") || !response.body?.getReader) {
    await response.body?.cancel();
    throw new Error("Speech synthesis returned invalid audio.");
  }
  const controller = new AbortController();
  const reader = response.body.getReader();
  let source, buffer, total = 0, released = false, disposed = false, attached = false;
  let resolveDone, rejectDone;
  const done = new Promise((resolve, reject) => { resolveDone = resolve; rejectDone = reject; });
  done.catch(() => {}); // The player subscribes after synthesis returns the resource.
  const cancelReader = () => { void finishReader(); };
  controller.signal.addEventListener("abort", cancelReader, { once: true });
  const abort = () => { controller.abort(stopped()); rejectDone(stopped()); };
  signal?.addEventListener("abort", abort, { once: true });
  if (signal?.aborted) abort();
  const resource = {
    url: null, contentType: type, progressive: false, done,
    async attach(audio) {
      if (attached) throw new Error("Speech audio is already attached.");
      attached = true;
      check();
      audio.src = resource.url;
      audio.load();
      if (!source) return;
      try {
        if (source.readyState !== "open") await waitEvent(source, "sourceopen");
        buffer = source.addSourceBuffer(MP3);
      } catch (error) {
        check();
        // MSE setup failed before any playback; consume the same untouched body.
        urlApi.revokeObjectURL(resource.url);
        source = null;
        await buffered();
        audio.src = resource.url;
        audio.load();
        return;
      }
      void pump().then(resolveDone, (error) => { rejectDone(error); controller.abort(error); });
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      abort();
      signal?.removeEventListener("abort", abort);
      if (source?.readyState === "open" && buffer) {
        try { if (buffer.updating) buffer.abort(); source.removeSourceBuffer(buffer); } catch (_) { /* already detached */ }
      }
      if (resource.url) urlApi.revokeObjectURL(resource.url);
    },
  };
  function check() { if (controller.signal.aborted) throw controller.signal.reason || stopped(); }
  function releaseReader() {
    if (released) return;
    released = true;
    reader.releaseLock();
  }
  async function finishReader() {
    if (released) return;
    await reader.cancel().catch(() => {});
    releaseReader();
  }
  async function next() {
    check();
    let timer;
    try {
      const chunk = await Promise.race([
        reader.read(),
        new Promise((_, reject) => { timer = setTimeout(() => { const error = new Error("Speech audio stream timed out."); controller.abort(error); reject(error); }, timeoutMs); }),
      ]);
      check();
      if (chunk.done) {
        if (!total) throw new Error("Speech synthesis returned empty audio.");
        onStage("audio_downloaded");
        return null;
      }
      if (total === 0 && chunk.value.byteLength) onStage("audio_first_byte");
      total += chunk.value.byteLength;
      if (total > maxBytes) throw new Error("Speech audio exceeds the playback size limit.");
      return chunk.value;
    } finally { clearTimeout(timer); }
  }
  function waitEvent(target, name, action = () => {}) {
    return new Promise((resolve, reject) => {
      let timer;
      const cleanup = () => {
        clearTimeout(timer);
        target.removeEventListener(name, success);
        target.removeEventListener("error", failure);
        controller.signal.removeEventListener("abort", cancelled);
      };
      const success = () => { cleanup(); resolve(); };
      const failure = () => { cleanup(); reject(new Error("Speech audio decoding failed.")); };
      const cancelled = () => { cleanup(); reject(stopped()); };
      target.addEventListener(name, success, { once: true });
      target.addEventListener("error", failure, { once: true });
      controller.signal.addEventListener("abort", cancelled, { once: true });
      timer = setTimeout(() => { cleanup(); reject(new Error("Speech audio preparation timed out.")); }, timeoutMs);
      if (controller.signal.aborted) { cancelled(); return; }
      try { action(); } catch (error) { cleanup(); reject(error); }
    });
  }
  async function buffered() {
    const chunks = [];
    try {
      for (let chunk; (chunk = await next()) !== null;) chunks.push(chunk);
      resource.url = urlApi.createObjectURL(new Blob(chunks, { type }));
      resource.progressive = false;
      resolveDone();
    } finally { await finishReader(); }
  }
  async function pump() {
    try {
      for (let chunk; (chunk = await next()) !== null;) {
        if (chunk.byteLength) await waitEvent(buffer, "updateend", () => buffer.appendBuffer(chunk));
      }
      check();
      if (source.readyState === "open") source.endOfStream();
    } finally { await finishReader(); }
  }
  try {
    check();
    if (Number(response.headers.get("Content-Length")) > maxBytes) throw new Error("Speech audio exceeds the playback size limit.");
    if (type === MP3 && MediaSourceClass?.isTypeSupported?.(MP3)) {
      try {
        source = new MediaSourceClass();
        resource.url = urlApi.createObjectURL(source);
        resource.progressive = true;
      } catch (_) { source = null; }
    }
    if (!source) await buffered();
    return resource;
  } catch (error) {
    resource.dispose();
    // Cancellation settles any read before the lock is released.
    if (!released) { await reader.cancel().catch(() => {}); releaseReader(); }
    throw error;
  }
}

function stopped() { return new DOMException("Speech playback was stopped.", "AbortError"); }
