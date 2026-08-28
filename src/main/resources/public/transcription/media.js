import { buildAudioConstraints } from "./settings.js";

const LEASE_KEY = "prometheus.transcription.microphone-lease.v1";

export class MicrophoneLease {
  constructor({
    storage = safeLocalStorage(),
    ownerId = globalThis.crypto?.randomUUID?.() || `tab-${Date.now()}`,
    now = () => Date.now(),
    ttlMs = 12000,
    heartbeatMs = 4000,
    onConflict = () => {},
  } = {}) {
    this.storage = storage;
    this.ownerId = ownerId;
    this.now = now;
    this.ttlMs = ttlMs;
    this.heartbeatMs = heartbeatMs;
    this.onConflict = onConflict;
    this.handleStorage = this.handleStorage.bind(this);
  }

  acquire() {
    const current = this.read();
    if (current && current.ownerId !== this.ownerId && current.expiresAt > this.now()) {
      this.onConflict();
      throw new Error("Another PROMETHEUS tab is using the microphone.");
    }
    const stored = this.write();
    const confirmed = stored ? this.read() : null;
    if (stored && confirmed?.ownerId !== this.ownerId) {
      this.onConflict();
      throw new Error("The microphone lease could not be acquired.");
    }
    globalThis.addEventListener?.("storage", this.handleStorage);
    this.heartbeat = stored ? globalThis.setInterval(() => this.write(), this.heartbeatMs) : null;
    this.active = true;
  }

  release() {
    if (this.heartbeat != null) globalThis.clearInterval(this.heartbeat);
    this.heartbeat = null;
    globalThis.removeEventListener?.("storage", this.handleStorage);
    if (this.read()?.ownerId === this.ownerId) {
      try { this.storage.removeItem(LEASE_KEY); } catch (_error) { /* advisory lease */ }
    }
    this.active = false;
  }

  handleStorage(event) {
    if (this.active && event.key === LEASE_KEY) {
      const current = this.read();
      if (current && current.ownerId !== this.ownerId && current.expiresAt > this.now()) this.onConflict();
    }
  }

  read() {
    try {
      const value = JSON.parse(this.storage.getItem(LEASE_KEY));
      return typeof value?.ownerId === "string" && Number.isFinite(value.expiresAt) ? value : null;
    } catch (_error) {
      return null;
    }
  }

  write() {
    try {
      this.storage.setItem(LEASE_KEY, JSON.stringify({ ownerId: this.ownerId, expiresAt: this.now() + this.ttlMs }));
      return true;
    } catch (_error) {
      return false;
    }
  }
}

export class TranscriptionMedia {
  constructor({
    mediaDevices = globalThis.navigator?.mediaDevices,
    lease = new MicrophoneLease(),
    onDiagnostic = () => {},
  } = {}) {
    this.mediaDevices = mediaDevices;
    this.lease = lease;
    this.onDiagnostic = onDiagnostic;
    this.stream = null;
  }

  async enumerate({ requestPermission = false } = {}) {
    if (!this.mediaDevices?.enumerateDevices) return { inputs: [], supported: false };
    if (requestPermission) {
      const temporary = await this.mediaDevices.getUserMedia({ audio: true });
      temporary.getTracks?.().forEach((track) => track.stop());
    }
    const devices = await this.mediaDevices.enumerateDevices();
    return { supported: true, inputs: devices.filter((device) => device.kind === "audioinput") };
  }

  async acquire(preferences = {}) {
    if (!this.mediaDevices?.getUserMedia) throw new Error("Microphone access is unavailable.");
    this.release();
    this.lease.acquire();
    try {
      this.stream = await this.mediaDevices.getUserMedia({ audio: buildAudioConstraints(preferences) });
      if (!this.hasAudioTrack()) throw new Error("No microphone track was provided.");
      this.onDiagnostic({ code: "microphone_acquired", settings: this.appliedAudioSettings() });
      return this.stream;
    } catch (error) {
      this.release();
      throw error;
    }
  }

  hasAudioTrack() {
    return (this.stream?.getAudioTracks?.() || []).length > 0;
  }

  addTracks(peerConnection) {
    if (!this.stream) throw new Error("Microphone has not been acquired.");
    this.stream.getTracks().forEach((track) => peerConnection.addTrack(track, this.stream));
  }

  setEnabled(enabled) {
    for (const track of this.stream?.getAudioTracks?.() || []) track.enabled = Boolean(enabled);
  }

  appliedAudioSettings() {
    const track = (this.stream?.getAudioTracks?.() || [])[0];
    return typeof track?.getSettings === "function" ? { ...track.getSettings() } : {};
  }

  async replaceAudioTrack(peerConnection, preferences = {}) {
    const oldStream = this.stream;
    const nextStream = await this.mediaDevices.getUserMedia({ audio: buildAudioConstraints(preferences) });
    const nextTrack = (nextStream.getAudioTracks?.() || [])[0];
    const sender = (peerConnection?.getSenders?.() || []).find((candidate) => candidate.track?.kind === "audio");
    if (!nextTrack || !sender?.replaceTrack) {
      nextStream.getTracks?.().forEach((track) => track.stop());
      throw new Error("Microphone replacement is unavailable.");
    }
    await sender.replaceTrack(nextTrack);
    this.stream = nextStream;
    oldStream?.getTracks?.().forEach((track) => track.stop());
    return this.appliedAudioSettings();
  }

  release() {
    this.stream?.getTracks?.().forEach((track) => track.stop());
    this.stream = null;
    this.lease?.release();
  }
}

function safeLocalStorage() {
  try { return globalThis.localStorage; }
  catch (_error) { return { getItem: () => null, setItem: () => {}, removeItem: () => {} }; }
}
