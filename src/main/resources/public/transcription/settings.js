export const TRANSCRIPTION_MODEL = "gpt-live-transcribe";
export const TRANSCRIPTION_SESSION_TYPE = "transcription";
export const DEFAULT_MEDIA_PREFERENCES = Object.freeze({
  inputDeviceId: "",
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
  voiceIsolation: false,
});

export class TranscriptionPreferences {
  constructor(descriptor, {
    storage = safeLocalStorage(),
    storageKey = "prometheus.transcription.preferences.v1",
  } = {}) {
    validateDescriptor(descriptor);
    this.descriptor = descriptor;
    this.storage = storage;
    this.storageKey = storageKey;
    this.api = defaultsFromDescriptor(descriptor);
    this.media = { ...DEFAULT_MEDIA_PREFERENCES };
    this.restore();
  }

  apiValues() {
    return structuredClone(this.api);
  }

  mediaValues() {
    return { ...this.media };
  }

  updateApi(key, rawValue) {
    const setting = settingByKey(this.descriptor, key);
    const value = normalizeSetting(setting, rawValue);
    setPath(this.api, key, value);
    this.persist();
    return this.apiValues();
  }

  updateMedia(values) {
    this.media = sanitizeMediaPreferences({ ...this.media, ...values });
    this.persist();
    return this.mediaValues();
  }

  validate() {
    const normalized = {};
    for (const setting of this.descriptor.settings) {
      if (setting.visibleWhen && !matchesVisibility(this.api, setting.visibleWhen)) {
        continue;
      }
      setPath(normalized, setting.key, normalizeSetting(setting, getPath(this.api, setting.key)));
    }
    return normalized;
  }

  persist() {
    const api = {};
    for (const setting of this.descriptor.settings) {
      if (!setting.sensitive) {
        setPath(api, setting.key, structuredClone(getPath(this.api, setting.key)));
      }
    }
    try {
      this.storage.setItem(this.storageKey, JSON.stringify({
        version: 1,
        schemaVersion: this.descriptor.schemaVersion,
        api,
        media: this.media,
      }));
    } catch (_error) {
      // Storage is optional. Microphone operation must not depend on it.
    }
  }

  restore() {
    try {
      const stored = JSON.parse(this.storage.getItem(this.storageKey));
      if (stored?.version !== 1 || stored?.schemaVersion !== this.descriptor.schemaVersion) {
        return;
      }
      for (const setting of this.descriptor.settings) {
        if (setting.sensitive) continue;
        const value = getPath(stored.api, setting.key);
        if (value !== undefined) {
          try {
            setPath(this.api, setting.key, normalizeSetting(setting, value));
          } catch (_error) {
            // One invalid stored preference must not discard the other settings.
          }
        }
      }
      this.media = sanitizeMediaPreferences(stored.media);
    } catch (_error) {
      // Missing, invalid, or disabled storage falls back to server defaults.
    }
  }
}

export function validateDescriptor(descriptor) {
  if (!descriptor || descriptor.sessionType !== TRANSCRIPTION_SESSION_TYPE
      || descriptor.model !== TRANSCRIPTION_MODEL || !Number.isInteger(descriptor.schemaVersion)
      || !Array.isArray(descriptor.settings) || descriptor.capabilities?.assistantOutput !== false
      || descriptor.capabilities?.inputTranscription !== true) {
    throw new Error("Live-transcription capabilities are invalid.");
  }
  return descriptor;
}

export function defaultsFromDescriptor(descriptor) {
  const result = {};
  for (const setting of descriptor.settings) {
    setPath(result, setting.key, structuredClone(setting.defaultValue));
  }
  return result;
}

export function buildAudioConstraints(preferences = {}) {
  const media = sanitizeMediaPreferences(preferences);
  const audio = {
    echoCancellation: media.echoCancellation,
    noiseSuppression: media.noiseSuppression,
    autoGainControl: media.autoGainControl,
  };
  if (media.voiceIsolation) audio.voiceIsolation = true;
  if (media.inputDeviceId) audio.deviceId = { exact: media.inputDeviceId };
  return audio;
}

export function captureSummary(preferences, applied = {}) {
  const requested = sanitizeMediaPreferences(preferences);
  return {
    requested: {
      echoCancellation: requested.echoCancellation,
      noiseSuppression: requested.noiseSuppression,
      autoGainControl: requested.autoGainControl,
      voiceIsolation: requested.voiceIsolation,
    },
    applied: {
      echoCancellation: applied.echoCancellation ?? null,
      noiseSuppression: applied.noiseSuppression ?? null,
      autoGainControl: applied.autoGainControl ?? null,
      voiceIsolation: applied.voiceIsolation ?? null,
    },
  };
}

export function normalizeSetting(setting, rawValue) {
  if (setting.control === "number") {
    const value = Number(rawValue);
    if (!Number.isFinite(value) || value < setting.minimum || value > setting.maximum) {
      throw new Error(`${setting.key} must be between ${setting.minimum} and ${setting.maximum}.`);
    }
    return value;
  }
  if (setting.control === "multi-select") {
    const values = Array.isArray(rawValue) ? rawValue : [];
    const unique = [...new Set(values)];
    if (unique.length < setting.minItems || unique.length > setting.maxItems
        || unique.some((value) => !setting.allowedValues.includes(value))) {
      throw new Error(`${setting.key} contains unsupported values.`);
    }
    return unique;
  }
  if (setting.control === "string-list") {
    const values = Array.isArray(rawValue)
      ? rawValue : String(rawValue || "").split(",").map((value) => value.trim()).filter(Boolean);
    const unique = [...new Set(values)];
    const pattern = new RegExp(setting.itemPattern, "u");
    if (unique.length > setting.maxItems
        || unique.some((value) => typeof value !== "string" || value.length > setting.maxLength || !pattern.test(value))) {
      throw new Error(`${setting.key} contains unsupported values.`);
    }
    return unique;
  }
  const value = String(rawValue ?? "").trim();
  if (setting.maxLength != null && value.length > setting.maxLength) {
    throw new Error(`${setting.key} exceeds its maximum length.`);
  }
  if (setting.allowedValues?.length && !setting.allowedValues.includes(value)) {
    throw new Error(`${setting.key} contains an unsupported value.`);
  }
  return value;
}

export function sanitizeMediaPreferences(values = {}) {
  const inputDeviceId = typeof values.inputDeviceId === "string"
      && values.inputDeviceId.length <= 512 && !/[\r\n<>]/.test(values.inputDeviceId)
    ? values.inputDeviceId : "";
  return {
    inputDeviceId,
    echoCancellation: values.echoCancellation !== false,
    noiseSuppression: values.noiseSuppression !== false,
    autoGainControl: values.autoGainControl !== false,
    voiceIsolation: values.voiceIsolation === true,
  };
}

export function settingByKey(descriptor, key) {
  const setting = descriptor.settings.find((candidate) => candidate.key === key);
  if (!setting) throw new Error(`Unknown live-transcription setting: ${key}`);
  return setting;
}

export function getPath(object, path) {
  return path.split(".").reduce((value, part) => value?.[part], object);
}

export function setPath(object, path, value) {
  const parts = path.split(".");
  let cursor = object;
  for (const part of parts.slice(0, -1)) cursor = cursor[part] ||= {};
  cursor[parts.at(-1)] = value;
}

function matchesVisibility(values, expression) {
  const [key, expected] = expression.split("=");
  return String(getPath(values, key)) === expected;
}

function safeLocalStorage() {
  try {
    return globalThis.localStorage;
  } catch (_error) {
    return { getItem: () => null, setItem: () => {} };
  }
}
