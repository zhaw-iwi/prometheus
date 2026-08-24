import { TranscriptionEventRuntime } from "./events.js";
import { BrowserLocalVad } from "./local-vad.js";
import { TranscriptionMedia } from "./media.js";
import { TranscriptionPreferences, validateDescriptor } from "./settings.js";
import { TranscriptionTransport } from "./transport.js";

const ACCESS_CODE_HEADER = "X-Prometheus-Access-Code";

export class LiveTranscriptionClient {
  constructor({
    agentId,
    accessCode = "",
    fetchImpl = globalThis.fetch.bind(globalThis),
    media = new TranscriptionMedia(),
    peerConnectionFactory,
    localVadFactory = (callbacks) => new BrowserLocalVad(callbacks),
    storage,
    storageKey,
    onPartial = () => {},
    onFinal = async () => {},
    onState = () => {},
    onInputState = () => {},
    onDiagnostic = () => {},
    transportOptions = {},
  } = {}) {
    if (!isUuid(agentId)) throw new Error("A valid agent id is required for live transcription.");
    this.agentId = agentId;
    this.accessCode = accessCode;
    this.fetchImpl = fetchImpl;
    this.media = media;
    this.storage = storage;
    this.storageKey = storageKey || `prometheus.transcription.${agentId}.v1`;
    this.onState = onState;
    this.onInputState = onInputState;
    this.onDiagnostic = onDiagnostic;
    this.events = new TranscriptionEventRuntime({ onPartial, onFinal, onInputState, onDiagnostic });
    this.localVad = localVadFactory({
      onSpeechStart: () => onInputState({ type: "local_vad.speech_started" }),
      onSpeechStop: (event) => onInputState({ type: "local_vad.speech_stopped", ...event }),
      onCommit: ({ reason }) => {
        if (!this.transport.commitLocalVadTurn()) onDiagnostic({ code: "local_vad_commit_skipped", reason });
      },
    });
    this.transport = new TranscriptionTransport({
      ...transportOptions,
      fetchImpl,
      peerConnectionFactory,
      media,
      sessionFactory: () => this.createSession(),
      onEvent: (event) => this.events.handle(event),
      onEpoch: ({ epoch }) => this.events.beginEpoch(epoch),
      onBeforeReconnect: async () => {
        await this.events.whenIdle();
        this.events.settleEpoch();
        await this.localVad.stop();
      },
      onConnected: () => this.syncLocalVad(),
      onState: (event) => {
        if (["reconnecting", "failed", "stopping", "stopped"].includes(event.state)) void this.localVad.stop();
        onState(event);
      },
      onDiagnostic,
    });
  }

  async initialize() {
    if (this.descriptor) return this.descriptor;
    const response = await this.fetchScoped(this.path("/transcription/capabilities"), {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) throw new Error(`Live-transcription capabilities failed (${response.status}).`);
    this.descriptor = validateDescriptor(await response.json());
    this.preferences = new TranscriptionPreferences(this.descriptor, {
      storage: this.storage,
      storageKey: this.storageKey,
    });
    return this.descriptor;
  }

  async start({ settings, mediaPreferences } = {}) {
    await this.initialize();
    this.settings = settings ? structuredClone(settings) : this.preferences.validate();
    this.mediaPreferences = mediaPreferences ? { ...mediaPreferences } : this.preferences.mediaValues();
    const sessionInfo = await this.createSession();
    if (sessionInfo.settingsSchemaVersion !== this.descriptor.schemaVersion) {
      throw new Error("Live-transcription settings schema changed; reload the page.");
    }
    await this.transport.start(sessionInfo, {
      mediaPreferences: this.mediaPreferences,
      turnDetectionMode: this.settings.turnDetection.type,
    });
    return { descriptor: this.descriptor, sessionInfo, appliedCapture: this.media.appliedAudioSettings() };
  }

  async createSession() {
    await this.initialize();
    const settings = this.settings || this.preferences.validate();
    const response = await this.fetchScoped(this.path("/transcription/session"), {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify(settings),
    });
    if (!response.ok) throw new Error(`Live-transcription session creation failed (${response.status}).`);
    const sessionInfo = await response.json();
    if (sessionInfo.sessionType !== "transcription" || sessionInfo.model !== "gpt-live-transcribe") {
      throw new Error("Live-transcription session metadata is invalid.");
    }
    return sessionInfo;
  }

  async stop() {
    await this.events.whenIdle();
    this.events.settleEpoch();
    await this.localVad.stop();
    await this.transport.stop();
  }

  async replaceMedia(mediaPreferences) {
    this.mediaPreferences = { ...mediaPreferences };
    await this.localVad.stop();
    const applied = await this.transport.replaceMedia(this.mediaPreferences);
    await this.syncLocalVad();
    return applied;
  }

  startManualTurn() {
    return this.transport.startManualTurn();
  }

  commitManualTurn() {
    return this.transport.commitManualTurn();
  }

  setInputEnabled(enabled) {
    const accepting = Boolean(enabled) && this.transport.state === "connected";
    if (!accepting) {
      this.transport.setInputEnabled(false);
      this.events.settleEpoch();
      void this.localVad.stop();
      return;
    }
    this.events.beginEpoch(this.transport.epoch);
    this.transport.setInputEnabled(true);
    void this.syncLocalVad();
  }

  async syncLocalVad() {
    if (this.transport.state === "connected" && this.settings?.turnDetection?.type === "local_vad") {
      await this.localVad.start(this.media.stream, this.settings.turnDetection.silenceDurationSeconds);
    } else {
      await this.localVad.stop();
    }
  }

  fetchScoped(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (this.accessCode) headers.set(ACCESS_CODE_HEADER, this.accessCode);
    return this.fetchImpl(url, { ...options, headers });
  }

  path(suffix) {
    return `/demo/agents/${encodeURIComponent(this.agentId)}${suffix}`;
  }
}

function isUuid(value) {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
