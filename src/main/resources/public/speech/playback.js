const LIVE_DELIVERY = "live";
const RESUME_DELIVERY = "resume";
const LEASE_PREFIX = "prometheus.valerian.output-lease.v1.";

export class BehaviourSpeechPlaybackQueue {
  constructor({
    synthesize,
    play,
    releaseResource = () => {},
    lease = { acquire: () => true, release: () => {} },
    setInputEnabled = () => {},
    onStatus = () => {},
  } = {}) {
    if (typeof synthesize !== "function" || typeof play !== "function") {
      throw new Error("Speech synthesis and playback boundaries are required.");
    }
    Object.assign(this, { synthesize, play, releaseResource, lease, setInputEnabled, onStatus });
    this.items = [];
    this.known = new Set();
    this.completed = new Set();
    this.failed = new Set();
    this.skipped = new Set();
    this.current = null;
    this.draining = false;
    this.inputGated = false;
    this.generation = 0;
    this.drainPromise = Promise.resolve();
  }

  enqueue(candidate) {
    const item = normalizeItem(candidate);
    if (!item) return false;
    const repeatable = item.delivery === RESUME_DELIVERY;
    if (!repeatable && this.known.has(item.eventId)) return false;
    this.known.add(item.eventId);
    if (item.delivery !== LIVE_DELIVERY && !repeatable) {
      this.skipped.add(item.eventId);
      this.status("skipped", item, { reason: "replay_or_non_live" });
      return false;
    }
    this.items.push(item);
    this.startDrain();
    return true;
  }

  async stop(reason = "operator_stop") {
    this.generation += 1;
    const queued = this.items.splice(0);
    queued.forEach((item) => this.skipped.add(item.eventId));
    if (this.current) {
      this.skipped.add(this.current.item.eventId);
      this.current.controller.abort(reason);
    }
    this.lease.release();
    this.releaseInput();
    this.status("stopped", this.current?.item || queued[0] || null, { reason });
    await this.whenIdle();
  }

  whenIdle() {
    return this.drainPromise;
  }

  snapshot() {
    return {
      queued: this.items.map((item) => item.eventId),
      current: this.current?.item.eventId || null,
      completed: [...this.completed],
      failed: [...this.failed],
      skipped: [...this.skipped],
    };
  }

  startDrain() {
    if (this.draining) return;
    this.draining = true;
    const generation = this.generation;
    this.drainPromise = this.drain(generation).finally(() => {
      this.draining = false;
      this.releaseInput();
      if (this.items.length > 0) this.startDrain();
    });
  }

  async drain(generation) {
    while (this.items.length > 0 && generation === this.generation) {
      const item = this.items.shift();
      await this.process(item, generation);
    }
  }

  async process(item, generation) {
    if (!this.lease.acquire(item.eventId)) {
      this.skipped.add(item.eventId);
      this.status("skipped", item, { reason: "output_lease_conflict" });
      return;
    }
    const controller = new AbortController();
    this.current = { item, controller, resource: null };
    this.gateInput();
    this.status("loading", item);
    try {
      const resource = await this.synthesize(item, controller.signal);
      this.current.resource = resource;
      if (controller.signal.aborted || generation !== this.generation) throw aborted();
      this.status("speaking", item);
      await this.play(resource, item, controller.signal);
      if (controller.signal.aborted || generation !== this.generation) throw aborted();
      this.completed.add(item.eventId);
      this.status("completed", item);
    } catch (error) {
      if (controller.signal.aborted || generation !== this.generation || error?.name === "AbortError") {
        this.skipped.add(item.eventId);
      } else {
        this.failed.add(item.eventId);
        this.status("failed", item, { message: safeMessage(error) });
      }
    } finally {
      try { await this.releaseResource(this.current?.resource, item); } catch (_error) { /* continue */ }
      this.current = null;
      this.lease.release();
    }
  }

  gateInput() {
    if (this.inputGated) return;
    this.inputGated = true;
    this.setInputEnabled(false);
  }

  releaseInput() {
    if (!this.inputGated) return;
    this.inputGated = false;
    this.setInputEnabled(true);
  }

  status(state, item, details = {}) {
    this.onStatus({ state, eventId: item?.eventId || null, ...details });
  }
}

export class OutputLease {
  constructor({
    agentId,
    storage = safeLocalStorage(),
    ownerId = globalThis.crypto?.randomUUID?.() || `tab-${Date.now()}`,
    now = () => Date.now(),
    ttlMs = 12000,
    heartbeatMs = 4000,
    onConflict = () => {},
  } = {}) {
    if (typeof agentId !== "string" || !agentId.trim()) throw new Error("An agent id is required for output ownership.");
    Object.assign(this, { storage, ownerId, now, ttlMs, heartbeatMs, onConflict });
    this.key = `${LEASE_PREFIX}${encodeURIComponent(agentId.trim())}`;
    this.handleStorage = this.handleStorage.bind(this);
    this.active = false;
  }

  acquire() {
    if (this.active) return true;
    const current = this.read();
    if (current && current.ownerId !== this.ownerId && current.expiresAt > this.now()) {
      this.onConflict({ ownerId: current.ownerId });
      return false;
    }
    if (!this.write() || this.read()?.ownerId !== this.ownerId) {
      this.onConflict({ ownerId: this.read()?.ownerId || null });
      return false;
    }
    globalThis.addEventListener?.("storage", this.handleStorage);
    this.heartbeat = globalThis.setInterval?.(() => this.write(), this.heartbeatMs);
    this.active = true;
    return true;
  }

  release() {
    if (this.heartbeat != null) globalThis.clearInterval?.(this.heartbeat);
    this.heartbeat = null;
    globalThis.removeEventListener?.("storage", this.handleStorage);
    if (this.read()?.ownerId === this.ownerId) {
      try { this.storage.removeItem(this.key); } catch (_error) { /* advisory lease */ }
    }
    this.active = false;
  }

  handleStorage(event) {
    if (!this.active || event?.key !== this.key) return;
    const current = this.read();
    if (current && current.ownerId !== this.ownerId && current.expiresAt > this.now()) {
      this.onConflict({ ownerId: current.ownerId });
    }
  }

  read() {
    try {
      const value = JSON.parse(this.storage.getItem(this.key));
      return typeof value?.ownerId === "string" && Number.isFinite(value.expiresAt) ? value : null;
    } catch (_error) {
      return null;
    }
  }

  write() {
    try {
      this.storage.setItem(this.key, JSON.stringify({ ownerId: this.ownerId, expiresAt: this.now() + this.ttlMs }));
      return true;
    } catch (_error) {
      return false;
    }
  }
}

function normalizeItem(candidate) {
  const eventId = typeof candidate?.eventId === "string" ? candidate.eventId.trim() : "";
  const speech = typeof candidate?.speech === "string" ? candidate.speech : "";
  const delivery = typeof candidate?.delivery === "string" ? candidate.delivery.trim().toLowerCase() : "";
  const speechRequired = delivery !== RESUME_DELIVERY;
  return eventId && (!speechRequired || speech.trim()) ? { eventId, speech, delivery } : null;
}

function safeMessage(error) {
  return error instanceof Error ? error.message.slice(0, 160) : "Speech playback failed.";
}

function aborted() {
  return new DOMException("Speech playback was stopped.", "AbortError");
}

function safeLocalStorage() {
  try { return globalThis.localStorage; }
  catch (_error) { return { getItem: () => null, setItem: () => {}, removeItem: () => {} }; }
}
