import assert from "node:assert/strict";
import test from "node:test";
import {
  TranscriptionPreferences,
  buildAudioConstraints,
  captureSummary,
  validateDescriptor,
} from "../../../src/main/resources/public/transcription/settings.js";

const descriptor = {
  schemaVersion: 1,
  sessionType: "transcription",
  model: "gpt-live-transcribe",
  capabilities: { assistantOutput: false, inputTranscription: true },
  settings: [
    setting("noiseReduction", "select", "far_field", { allowedValues: ["near_field", "far_field", "off"] }),
    setting("turnDetection.type", "select", "local_vad", { allowedValues: ["local_vad", "manual"] }),
    setting("turnDetection.silenceDurationSeconds", "number", 1.5,
      { minimum: 0.5, maximum: 10, step: 0.1, visibleWhen: "turnDetection.type=local_vad" }),
    setting("transcriptionPrompt", "text", "", { maxLength: 1024, sensitive: true }),
    setting("transcriptionKeywords", "string-list", [],
      { maxLength: 100, maxItems: 100, minItems: 0, itemPattern: "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._'/-]*$", sensitive: true }),
    setting("languages", "multi-select", ["ar"], { allowedValues: ["ar", "de", "en"], minItems: 1, maxItems: 3 }),
    setting("transcriptionDelay", "select", "medium", { allowedValues: ["minimal", "low", "medium", "high", "xhigh"] }),
  ],
};

test("descriptor defaults and media capture reflect the agreed group-listening profile", () => {
  validateDescriptor(descriptor);
  const preferences = new TranscriptionPreferences(descriptor, { storage: memoryStorage() });

  assert.deepEqual(preferences.validate(), {
    noiseReduction: "far_field",
    turnDetection: { type: "local_vad", silenceDurationSeconds: 1.5 },
    transcriptionPrompt: "",
    transcriptionKeywords: [],
    languages: ["ar"],
    transcriptionDelay: "medium",
  });
  assert.deepEqual(buildAudioConstraints(preferences.mediaValues()), {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
  });
});

test("only non-sensitive provider preferences are restored", () => {
  const storage = memoryStorage();
  const first = new TranscriptionPreferences(descriptor, { storage });
  first.updateApi("noiseReduction", "near_field");
  first.updateApi("transcriptionPrompt", "private meeting context");
  first.updateApi("transcriptionKeywords", ["PROMETHEUS"]);
  first.updateMedia({ voiceIsolation: true, inputDeviceId: "mic-2" });

  const stored = JSON.parse(storage.getItem("prometheus.transcription.preferences.v1"));
  assert.equal(stored.api.transcriptionPrompt, undefined);
  assert.equal(stored.api.transcriptionKeywords, undefined);
  const restored = new TranscriptionPreferences(descriptor, { storage });
  assert.equal(restored.apiValues().noiseReduction, "near_field");
  assert.equal(restored.apiValues().transcriptionPrompt, "");
  assert.deepEqual(restored.apiValues().transcriptionKeywords, []);
  assert.equal(restored.mediaValues().voiceIsolation, true);
  assert.equal(restored.mediaValues().inputDeviceId, "mic-2");
});

test("settings and device validation reject unsupported values", () => {
  const preferences = new TranscriptionPreferences(descriptor, { storage: memoryStorage() });
  assert.throws(() => preferences.updateApi("noiseReduction", "studio"), /unsupported/);
  assert.throws(() => preferences.updateApi("languages", []), /unsupported/);
  assert.throws(() => preferences.updateApi("transcriptionKeywords", ["<unsafe>"]), /unsupported/);
  preferences.updateApi("turnDetection.type", "manual");
  assert.deepEqual(preferences.validate().turnDetection, { type: "manual" });
  assert.equal(preferences.updateMedia({ inputDeviceId: "bad\nvalue" }).inputDeviceId, "");
});

test("effective capture summary distinguishes requested and browser-applied values", () => {
  assert.deepEqual(captureSummary({ echoCancellation: true, noiseSuppression: false }, {
    echoCancellation: false,
  }), {
    requested: { echoCancellation: true, noiseSuppression: false, autoGainControl: true, voiceIsolation: false },
    applied: { echoCancellation: false, noiseSuppression: null, autoGainControl: null, voiceIsolation: null },
  });
});

function setting(key, control, defaultValue, overrides = {}) {
  return { key, control, defaultValue, allowedValues: [], minimum: null, maximum: null, step: null,
    maxLength: null, maxItems: null, minItems: null, itemPattern: null, activeSessionBehavior: "live-input-boundary",
    visibleWhen: null, sensitive: false, ...overrides };
}

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}
