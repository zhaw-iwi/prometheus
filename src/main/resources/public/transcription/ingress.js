const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";
const FULL_PLAN = "full_plan";

export class ScopedTranscriptIngress {
  constructor({
    agentId,
    accessCode = "",
    fetchImpl = globalThis.fetch.bind(globalThis),
    canAccept = () => true,
    onQueued = () => {},
    onAccepted = () => {},
    onStatus = () => {},
    onDiagnostic = () => {},
  } = {}) {
    if (!isUuid(agentId)) throw new Error("A valid agent id is required for transcript ingress.");
    this.agentId = agentId;
    this.accessCode = accessCode;
    this.fetchImpl = fetchImpl;
    this.canAccept = canAccept;
    this.onQueued = onQueued;
    this.onAccepted = onAccepted;
    this.onStatus = onStatus;
    this.onDiagnostic = onDiagnostic;
    this.accepting = true;
    this.seenTurns = new Set();
    this.queue = Promise.resolve();
  }

  submit(turn) {
    const normalized = normalizeTurn(turn);
    if (!normalized) return Promise.resolve(false);
    const key = `${normalized.epoch}:${normalized.itemId}`;
    if (this.seenTurns.has(key)) return Promise.resolve(false);
    this.seenTurns.add(key);
    if (!this.accepting || !this.canAccept(normalized)) {
      this.status("rejected", normalized, { reason: "input_gated" });
      return Promise.resolve(false);
    }
    this.status("queued", normalized);
    this.onQueued(normalized);
    const delivery = this.queue.then(() => this.deliver(normalized));
    this.queue = delivery.catch(() => false);
    return delivery;
  }

  setAccepting(accepting) {
    this.accepting = Boolean(accepting);
  }

  whenIdle() {
    return this.queue;
  }

  async deliver(turn) {
    if (!this.accepting || !this.canAccept(turn)) {
      this.status("rejected", turn, { reason: "input_gated" });
      return false;
    }
    this.status("acknowledging", turn);
    let response;
    try {
      response = await this.fetchScoped(`${this.agentPath()}/acknowledge?profile=${FULL_PLAN}`, {
        method: "POST",
        headers: { Accept: "application/json", "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({
          type: "obs.user_utterance",
          actor: "user",
          kind: "observation",
          payload: turn.text,
        }),
      });
    } catch (error) {
      this.reject(turn, "acknowledge_network_error", error);
      return false;
    }
    if (!response.ok) {
      this.reject(turn, "acknowledge_rejected", null, { status: response.status });
      return false;
    }

    let acknowledgement;
    try {
      acknowledgement = await response.json();
    } catch (error) {
      this.reject(turn, "acknowledge_invalid_response", error);
      return false;
    }
    if (!acknowledgement?.responseEvent) await this.requestFallbackBehaviour(turn);
    await this.onAccepted({ ...turn, acknowledgement });
    this.status("accepted", turn, { active: acknowledgement?.active });
    return true;
  }

  async requestFallbackBehaviour(turn) {
    let response;
    try {
      response = await this.fetchScoped(`${this.agentPath()}/behaviour/generate`, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ outputProfile: FULL_PLAN }),
      });
    } catch (error) {
      this.onDiagnostic({ code: "fallback_behaviour_network_error", itemId: turn.itemId,
        message: safeMessage(error) });
      return;
    }
    if (!response.ok && response.status !== 409) {
      this.onDiagnostic({ code: "fallback_behaviour_rejected", itemId: turn.itemId, status: response.status });
    }
  }

  reject(turn, reason, error, details = {}) {
    const diagnostic = { code: reason, itemId: turn.itemId, ...details };
    const message = safeMessage(error);
    if (message) diagnostic.message = message;
    this.onDiagnostic(diagnostic);
    this.status("rejected", turn, { reason, ...details });
  }

  status(state, turn, details = {}) {
    this.onStatus({ state, epoch: turn.epoch, itemId: turn.itemId, ...details });
  }

  fetchScoped(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (this.accessCode) headers.set(ACCESS_CODE_HEADER, this.accessCode);
    return this.fetchImpl(url, { ...options, headers });
  }

  agentPath() {
    return `/demo/agents/${encodeURIComponent(this.agentId)}`;
  }
}

function normalizeTurn(turn) {
  const epoch = Number.isInteger(turn?.epoch) ? turn.epoch : null;
  const itemId = typeof turn?.itemId === "string" ? turn.itemId.trim() : "";
  const text = typeof turn?.text === "string" ? turn.text.trim() : "";
  return epoch === null || !itemId || !text ? null : { epoch, itemId, text };
}

function safeMessage(error) {
  return error instanceof Error ? error.message.slice(0, 160) : "";
}

function isUuid(value) {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
