import { captureSummary, getPath, normalizeSetting } from "./settings.js";

const LABELS = {
  noiseReduction: "Provider noise reduction",
  "turnDetection.type": "Turn detection",
  "turnDetection.silenceDurationSeconds": "Silence duration (seconds)",
  transcriptionPrompt: "Transcription context",
  transcriptionKeywords: "Keywords",
  languages: "Expected languages",
  transcriptionDelay: "Transcription delay",
};

export class TranscriptionSettingsPanel {
  constructor({ root, preferences, media, onValidation = () => {} }) {
    if (!root) throw new Error("A transcription settings root is required.");
    this.root = root;
    this.preferences = preferences;
    this.media = media;
    this.onValidation = onValidation;
    this.appliedCapture = {};
    this.lifecycle = "IDLE";
    this.controls = new Map();
    this.render();
  }

  render() {
    this.root.replaceChildren();
    const provider = section("Provider transcription");
    for (const setting of this.preferences.descriptor.settings) {
      const wrapper = document.createElement("div");
      wrapper.className = "col-12 col-md-6 transcription-setting";
      wrapper.dataset.settingKey = setting.key;
      const label = document.createElement("label");
      label.className = "form-label metric-label";
      const id = `transcription_${setting.key.replaceAll(".", "_")}`;
      label.htmlFor = id;
      label.textContent = LABELS[setting.key] || setting.key;
      const control = createControl(setting, id, getPath(this.preferences.apiValues(), setting.key));
      control.classList.add(setting.control === "text" ? "form-control" : "form-select", "form-control-sm");
      if (["number", "string-list"].includes(setting.control)) {
        control.classList.remove("form-select");
        control.classList.add("form-control");
      }
      control.dataset.testid = `transcription-${setting.key.replaceAll(".", "-")}`;
      const feedback = document.createElement("div");
      feedback.className = "invalid-feedback";
      control.addEventListener("change", () => this.handleApiChange(setting, control, feedback));
      wrapper.append(label, control, feedback);
      provider.body.append(wrapper);
      this.controls.set(setting.key, { setting, wrapper, control, feedback });
    }
    this.root.append(provider.element);

    const capture = section("Browser capture");
    const deviceWrapper = document.createElement("div");
    deviceWrapper.className = "col-12";
    deviceWrapper.innerHTML = '<label class="form-label metric-label" for="transcription_input_device">Microphone</label>';
    this.deviceSelect = document.createElement("select");
    this.deviceSelect.id = "transcription_input_device";
    this.deviceSelect.className = "form-select form-select-sm";
    this.deviceSelect.dataset.testid = "transcription-input-device";
    this.deviceSelect.append(new Option("System / browser default", ""));
    this.deviceSelect.value = this.preferences.mediaValues().inputDeviceId;
    this.deviceSelect.addEventListener("change", () => {
      this.preferences.updateMedia({ inputDeviceId: this.deviceSelect.value });
      this.updateSummary();
    });
    deviceWrapper.append(this.deviceSelect);
    capture.body.append(deviceWrapper);
    const supported = globalThis.navigator?.mediaDevices?.getSupportedConstraints?.() || {};
    for (const [key, label] of [["echoCancellation", "Echo cancellation"], ["noiseSuppression", "Noise suppression"],
      ["autoGainControl", "Automatic amplification"], ["voiceIsolation", "Voice isolation"]]) {
      const wrapper = document.createElement("div");
      wrapper.className = "col-12 col-sm-6";
      const formCheck = document.createElement("div");
      formCheck.className = "form-check form-switch";
      const control = document.createElement("input");
      control.type = "checkbox";
      control.className = "form-check-input";
      control.id = `transcription_capture_${key}`;
      control.dataset.captureKey = key;
      control.dataset.testid = `transcription-capture-${camelToKebab(key)}`;
      control.checked = this.preferences.mediaValues()[key];
      if (key === "voiceIsolation" && supported.voiceIsolation !== true) {
        control.disabled = true;
        control.dataset.unsupported = "true";
      }
      control.addEventListener("change", () => {
        this.preferences.updateMedia({ [key]: control.checked });
        this.updateSummary();
      });
      const controlLabel = document.createElement("label");
      controlLabel.className = "form-check-label";
      controlLabel.htmlFor = control.id;
      controlLabel.textContent = key === "voiceIsolation" && supported.voiceIsolation !== true
        ? `${label} (unsupported)` : label;
      formCheck.append(control, controlLabel);
      wrapper.append(formCheck);
      capture.body.append(wrapper);
    }
    const refresh = document.createElement("button");
    refresh.type = "button";
    refresh.className = "btn btn-outline-ink btn-sm";
    refresh.dataset.testid = "transcription-refresh-devices";
    refresh.textContent = "Refresh microphones";
    refresh.addEventListener("click", () => void this.refreshDevices(true));
    const refreshWrapper = document.createElement("div");
    refreshWrapper.className = "col-12";
    refreshWrapper.append(refresh);
    capture.body.append(refreshWrapper);
    this.root.append(capture.element);

    const status = section("Effective settings");
    this.feedback = document.createElement("div");
    this.feedback.className = "small text-muted mb-2";
    this.feedback.dataset.testid = "transcription-settings-feedback";
    this.summary = document.createElement("pre");
    this.summary.className = "small mono mb-0 transcription-settings-summary";
    this.summary.dataset.testid = "transcription-settings-summary";
    status.body.classList.remove("row");
    status.body.classList.add("d-block");
    status.body.append(this.feedback, this.summary);
    this.root.append(status.element);
    this.refreshVisibility();
    this.updateSummary();
    void this.refreshDevices(false);
  }

  apiValues() {
    return this.preferences.validate();
  }

  mediaValues() {
    return this.preferences.mediaValues();
  }

  setLifecycle(lifecycle) {
    this.lifecycle = lifecycle;
    const locked = !["IDLE", "FAILED"].includes(lifecycle);
    this.root.querySelectorAll("input,select,textarea,button").forEach((control) => {
      control.disabled = locked || control.dataset.unsupported === "true";
    });
    this.updateSummary();
  }

  setAppliedCapture(appliedCapture = {}) {
    this.appliedCapture = { ...appliedCapture };
    this.updateSummary();
  }

  async refreshDevices(requestPermission) {
    try {
      const devices = await this.media.enumerate({ requestPermission });
      const selected = this.preferences.mediaValues().inputDeviceId;
      this.deviceSelect.replaceChildren(new Option("System / browser default", ""));
      devices.inputs.forEach((device, index) => this.deviceSelect.add(
        new Option(device.label || `Microphone ${index + 1}`, device.deviceId)));
      if (selected && devices.inputs.some((device) => device.deviceId === selected)) this.deviceSelect.value = selected;
      else if (selected) this.preferences.updateMedia({ inputDeviceId: "" });
      this.feedback.textContent = requestPermission ? "Microphones refreshed." : this.feedback.textContent;
    } catch (_error) {
      this.feedback.textContent = "Microphone permission was not granted.";
      this.feedback.className = "small text-danger mb-2";
    }
  }

  handleApiChange(setting, control, feedback) {
    try {
      const raw = control.multiple ? [...control.selectedOptions].map((option) => option.value) : control.value;
      this.preferences.updateApi(setting.key, normalizeSetting(setting, raw));
      control.classList.remove("is-invalid");
      feedback.textContent = "";
      this.refreshVisibility();
      this.updateSummary();
      this.onValidation({ valid: true, key: setting.key });
    } catch (error) {
      control.classList.add("is-invalid");
      feedback.textContent = error.message;
      this.onValidation({ valid: false, key: setting.key, message: error.message });
    }
  }

  refreshVisibility() {
    const values = this.preferences.apiValues();
    for (const { setting, wrapper } of this.controls.values()) {
      if (!setting.visibleWhen) wrapper.hidden = false;
      else {
        const [key, expected] = setting.visibleWhen.split("=");
        wrapper.hidden = String(getPath(values, key)) !== expected;
      }
    }
  }

  updateSummary() {
    const api = this.preferences.apiValues();
    const summary = {
      model: this.preferences.descriptor.model,
      schemaVersion: this.preferences.descriptor.schemaVersion,
      lifecycle: this.lifecycle,
      turnDetection: api.turnDetection,
      noiseReduction: api.noiseReduction,
      languages: api.languages,
      transcriptionDelay: api.transcriptionDelay,
      transcriptionContextConfigured: Boolean(api.transcriptionPrompt),
      transcriptionKeywordCount: api.transcriptionKeywords?.length || 0,
      capture: captureSummary(this.preferences.mediaValues(), this.appliedCapture),
    };
    this.summary.textContent = JSON.stringify(summary, null, 2);
    if (!this.feedback.textContent) this.feedback.textContent = "Requested/applied capture is shown below.";
  }
}

function section(title) {
  const element = document.createElement("section");
  element.className = "mb-3";
  const heading = document.createElement("div");
  heading.className = "metric-label mb-2";
  heading.textContent = title;
  const body = document.createElement("div");
  body.className = "row g-2";
  element.append(heading, body);
  return { element, body };
}

function createControl(setting, id, value) {
  let control;
  if (["select", "multi-select"].includes(setting.control)) {
    control = document.createElement("select");
    control.multiple = setting.control === "multi-select";
    for (const allowed of setting.allowedValues) control.add(new Option(displayValue(allowed), allowed));
    if (control.multiple) {
      const selected = new Set(Array.isArray(value) ? value : []);
      [...control.options].forEach((option) => { option.selected = selected.has(option.value); });
    } else control.value = value;
  } else if (setting.control === "text") {
    control = document.createElement("textarea");
    control.rows = 2;
    control.maxLength = setting.maxLength;
    control.value = value || "";
  } else {
    control = document.createElement("input");
    control.type = setting.control === "number" ? "number" : "text";
    control.value = setting.control === "string-list" ? (value || []).join(", ") : value;
    if (setting.minimum != null) control.min = setting.minimum;
    if (setting.maximum != null) control.max = setting.maximum;
    if (setting.step != null) control.step = setting.step;
  }
  control.id = id;
  return control;
}

function displayValue(value) {
  return String(value).replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase());
}

function camelToKebab(value) {
  return value.replace(/[A-Z]/g, (character) => `-${character.toLowerCase()}`);
}
