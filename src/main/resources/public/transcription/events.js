const COMMITTED = "input_audio_buffer.committed";
const CREATED = "conversation.item.created";
const DELTA = "conversation.item.input_audio_transcription.delta";
const COMPLETED = "conversation.item.input_audio_transcription.completed";
const FAILED = "conversation.item.input_audio_transcription.failed";

export class OrderedTranscriptAssembler {
  constructor() {
    this.beginEpoch(0);
  }

  beginEpoch(epoch) {
    this.epoch = epoch;
    this.order = [];
    this.items = new Map();
    this.seenEvents = new Set();
  }

  accept(rawEvent) {
    const event = parseEvent(rawEvent);
    const output = { accepted: false, partials: [], finals: [], diagnostics: [] };
    if (!event?.type) return output;
    if (event.event_id && this.seenEvents.has(event.event_id)) return output;
    if (event.event_id) this.seenEvents.add(event.event_id);

    if (event.type === COMMITTED) {
      const item = this.registerOrder(event.item_id);
      if (item && !item.committed) {
        item.committed = true;
        output.accepted = true;
      }
    } else if (event.type === CREATED && event.item?.role === "user") {
      this.registerOrder(event.item.id || event.item_id);
    } else if (event.type === DELTA) {
      const item = this.item(event.item_id);
      if (item && !item.terminal && typeof event.delta === "string") {
        output.accepted = true;
        const index = Number.isInteger(event.content_index) ? event.content_index : 0;
        item.parts.set(index, (item.parts.get(index) || "") + event.delta);
        output.partials.push({ epoch: this.epoch, itemId: item.id, delta: event.delta, text: assembled(item) });
      }
    } else if (event.type === COMPLETED || event.type === FAILED) {
      const item = this.item(event.item_id);
      if (item && !item.terminal) {
        output.accepted = true;
        item.terminal = true;
        item.failed = event.type === FAILED;
        item.text = item.failed ? "" : String(event.transcript ?? assembled(item)).trim();
      }
    }
    output.finals.push(...this.drain());
    return output;
  }

  settle() {
    for (const item of this.items.values()) {
      if (!item.terminal) {
        item.terminal = true;
        item.failed = true;
      }
    }
    return this.drain();
  }

  registerOrder(itemId) {
    const item = this.item(itemId);
    if (item && !item.ordered) {
      item.ordered = true;
      this.order.push(item.id);
    }
    return item;
  }

  drain() {
    const finals = [];
    while (this.order.length > 0) {
      const item = this.items.get(this.order[0]);
      if (!item?.terminal) break;
      this.order.shift();
      if (!item.released) {
        item.released = true;
        if (!item.failed && item.text) finals.push({ epoch: this.epoch, itemId: item.id, text: item.text });
      }
    }
    return finals;
  }

  item(itemId) {
    if (typeof itemId !== "string" || !itemId) return null;
    if (!this.items.has(itemId)) {
      this.items.set(itemId, {
        id: itemId, ordered: false, terminal: false, failed: false, released: false, text: "", parts: new Map(),
      });
    }
    return this.items.get(itemId);
  }
}

export class TranscriptionEventRuntime {
  constructor({
    onPartial = () => {},
    onFinal = async () => {},
    onInputState = () => {},
    onDiagnostic = () => {},
    now = null,
  } = {}) {
    this.now = now;
    this.pendingCommits = [];
    this.itemTimings = new Map();
    this.onPartial = onPartial;
    this.onFinal = onFinal;
    this.onInputState = onInputState;
    this.onDiagnostic = onDiagnostic;
    this.assembler = new OrderedTranscriptAssembler();
    this.queue = Promise.resolve();
    this.accepting = false;
  }

  beginEpoch(epoch) {
    this.pendingCommits = [];
    this.itemTimings.clear();
    this.assembler.beginEpoch(epoch);
    this.accepting = true;
  }

  noteCommit({ lastVoiceAtMs, observedAtMs, sentAtMs }) {
    this.pendingCommits.push({ last_voice: lastVoiceAtMs, committed: observedAtMs, commit_sent: sentAtMs });
    if (this.pendingCommits.length > 128) this.pendingCommits.shift();
  }

  handle(rawEvent) {
    const event = parseEvent(rawEvent);
    if (!event) {
      this.onDiagnostic({ code: "invalid_provider_event" });
      return;
    }
    const receivedAt = this.now?.();
    const result = this.assembler.accept(event);
    // Use accepted events so duplicate commits cannot consume the next turn's
    // local timing, and duplicate/late deltas cannot move the finalisation boundary.
    if (this.now && result.accepted && [COMMITTED, DELTA, COMPLETED].includes(event.type)) {
      const timing = this.itemTimings.get(event.item_id) || {};
      if (event.type === COMMITTED) {
        Object.assign(timing, this.pendingCommits.shift(), { commit_acknowledged: receivedAt });
      } else if (event.type === DELTA && event.delta.length) {
        timing.transcript_first_delta ??= receivedAt;
        timing.transcript_last_delta = receivedAt;
      } else if (event.type === COMPLETED) {
        timing.final_transcript = receivedAt;
      }
      this.itemTimings.set(event.item_id, timing);
      while (this.itemTimings.size > 128) this.itemTimings.delete(this.itemTimings.keys().next().value);
    }
    result.partials.forEach((partial) => this.onPartial(partial));
    this.enqueue(result.finals.map((turn) => this.now
      ? { ...turn, timings: { ...this.itemTimings.get(turn.itemId) } } : turn));
    if (["input_audio_buffer.speech_started", "input_audio_buffer.speech_stopped", COMMITTED,
      "input_audio_buffer.cleared"].includes(event.type)) {
      this.onInputState({ type: event.type, itemId: event.item_id || null });
    } else if (event.type.startsWith("response.") || event.type.includes("output_audio")) {
      this.onDiagnostic({ code: "unexpected_assistant_event", eventType: event.type });
    } else if (event.type === "error") {
      this.onDiagnostic({ code: "provider_error", providerCode: safeCode(event.error?.code) });
    } else if (event.type === FAILED) {
      this.onDiagnostic({ code: "provider_transcription_failed", itemId: event.item_id || null,
        providerCode: safeCode(event.error?.code) });
    } else if (event.type === "session.created" || event.type === "session.updated") {
      if (event.session?.type && event.session.type !== "transcription") {
        this.onDiagnostic({ code: "unexpected_session_type" });
      }
    }
  }

  settleEpoch() {
    this.accepting = false;
    this.assembler.settle();
    this.pendingCommits = [];
    this.itemTimings.clear();
  }

  whenIdle() {
    return this.queue;
  }

  enqueue(finals) {
    for (const final of finals) {
      this.queue = this.queue.then(async () => {
        if (!this.accepting || final.epoch !== this.assembler.epoch) {
          this.onDiagnostic({ code: "stale_transcript_epoch", itemId: final.itemId });
          return;
        }
        await this.onFinal(final);
      }).catch(() => this.onDiagnostic({ code: "final_transcript_handler_failed", itemId: final.itemId }));
    }
  }
}

function assembled(item) {
  return [...item.parts.entries()].sort(([left], [right]) => left - right).map(([, value]) => value).join("");
}

function parseEvent(rawEvent) {
  if (typeof rawEvent === "string") {
    try { return JSON.parse(rawEvent); } catch (_error) { return null; }
  }
  return rawEvent && typeof rawEvent === "object" ? rawEvent : null;
}

function safeCode(value) {
  return typeof value === "string" && /^[a-z0-9_.-]{1,64}$/i.test(value) ? value : "provider_error";
}
