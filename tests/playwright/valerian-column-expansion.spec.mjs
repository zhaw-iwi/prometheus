import { expect, test } from "@playwright/test";

const ACCESS_CODE = "VX102";
const ADMIN_TOKEN = process.env.PROMETHEUS_ADMIN_TOKEN || "laure";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";
const SAMPLE_BEHAVIOUR_PLAN = {
  speech: "Ich zeige kurz, worauf ich achte.",
  nonVerbal: {
    gesture: "OPEN_QUESTION",
    facialExpression: { type: "warm_smile", intensity: 0.72 },
    gaze: { direction: "toward_user", focus: "speaker" },
    motion: { energy: 0.64, stillness: 0.28 },
  },
  motion: { effector: "right_hand", handSign: "rock", energy: 0.64, stillness: 0.28 },
  display: { agentSign: "rock", userSign: "paper", round: 2, winner: "user", note: "visual test" },
};
const SAMPLE_EMOTION = {
  emotion: "surprised",
  confidence: 0.87,
  valence: 0.32,
  arousal: 0.74,
  expressions: {
    neutral: 0.04,
    happy: 0.22,
    sad: 0.01,
    angry: 0.02,
    fearful: 0.05,
    disgusted: 0.01,
    surprised: 0.87,
  },
  facePresent: true,
};
const SAMPLE_FACE_SCORE = 0.91;
const SAMPLE_CAMERA_EMOTION = {
  emotion: "happy",
  confidence: 0.92,
  valence: 0.87,
  arousal: 0.45,
  expressions: {
    neutral: 0.03,
    happy: 0.92,
    sad: 0.01,
    angry: 0.01,
    fearful: 0.01,
    disgusted: 0.01,
    surprised: 0.04,
  },
  facePresent: true,
};
const SAMPLE_CAMERA_FACE_SCORE = 0.94;
const SAMPLE_SOCIAL = {
  humanCount: 3,
  groupCount: 1,
  singletonCount: 1,
  largestGroupSize: 2,
  groups: [
    { members: [1, 2] },
    { members: [3] },
  ],
};
const SAMPLE_TRACKED_PEOPLE = [
  {
    id: 1,
    score: 0.92,
    activity: "moving",
    movementConfidence: 0.72,
    attention: {
      state: "attending",
      confidence: 0.76,
      personVisible: true,
      faceVisible: true,
      nearFrontal: true,
      centered: true,
      frontalCentered: true,
    },
  },
  {
    id: 2,
    score: 0.86,
    activity: "approaching",
    movementConfidence: 0.81,
    attention: {
      state: "not_attending",
      confidence: 0.44,
      personVisible: true,
      faceVisible: true,
      nearFrontal: true,
      centered: false,
      frontalCentered: false,
    },
  },
  {
    id: 3,
    score: 0.78,
    activity: "stationary",
    movementConfidence: 0.64,
    attention: {
      state: "unknown",
      confidence: 0.18,
      personVisible: true,
      faceVisible: false,
      nearFrontal: false,
      centered: false,
      frontalCentered: false,
    },
  },
];
const SAMPLE_HAND_SIGN = {
  sign: "paper",
  source: "valerian.hand.camera",
  detectionMode: "client_camera",
  confidence: 0.88,
  cannedGesture: "Open_Palm",
  stabilityFrames: 4,
};
const SAMPLE_WEATHER_FORECAST = {
  source: "open-meteo.client",
  kind: "forecast",
  location_label: "Zurich, Switzerland",
  days: [
    { date: "2026-07-04", condition: "rain", intensity: "medium", wind: "windy", temperature_min_c: 15.2, temperature_max_c: 22.4, precipitation_mm: 4.6 },
    { date: "2026-07-05", condition: "cloudy", intensity: "none", wind: "calm", temperature_min_c: 14.8, temperature_max_c: 24.1, precipitation_mm: 0 },
    { date: "2026-07-06", condition: "clear", intensity: "none", wind: "calm", temperature_min_c: 16.1, temperature_max_c: 26.7, precipitation_mm: 0 },
  ],
};

test.beforeAll(async ({ request }) => {
  await ensureAccessCode(request, ACCESS_CODE);
});

test("Valerian cockpit columns expand into a wider live modal viewport", async ({ page }, testInfo) => {
  await page.goto("/valerian/");
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();
  await expect(page.getByTestId("column-expansion-modal")).toBeHidden();
  await expect(page.locator("[data-column-panel=\"sensing\"]")).toBeVisible();
  await expect(page.locator("[data-column-panel=\"interaction\"]")).toBeVisible();
  await expect(page.locator("[data-column-panel=\"behaviour\"]")).toBeVisible();

  await page.getByTestId("continuous-speech-tab").click();
  await expect(page.locator("#continuous_speech_panel")).toHaveClass(/active/);

  await renderSampleEmotion(page);
  await verifyManualSocialDetailEditor(page);
  await verifyTrackMovementHeuristic(page);
  await verifySocialContextPayloadContract(page);
  await renderSampleSocial(page);
  await renderSampleHandSign(page);
  await renderSampleWeather(page);
  await openSensedSignals(page);
  await verifyEmotionReport(page);
  await verifySocialContextReport(page);
  await verifyHandSignReport(page);
  await verifyWeatherReport(page);

  await renderSampleBehaviour(page);
  await verifyBehaviourVisualState(page);

  await verifyColumnExpansion(page, testInfo, {
    key: "sensing",
    title: "Sensing",
    buttonTestId: "maximize-sensing-column",
    inModal: async (panelInModal) => {
      await panelInModal.getByTestId("emotion-report").scrollIntoViewIfNeeded();
      await expect(panelInModal.getByTestId("emotion-report")).toBeVisible();
      await expect(panelInModal.getByTestId("emotion-affect-marker"))
        .toHaveAttribute("aria-label", "Valence +0.32, arousal 0.74");
      await expect(panelInModal.getByTestId("emotion-valence-meter")).toHaveAttribute("aria-valuenow", "66");
      await expect(panelInModal.getByTestId("emotion-arousal-meter")).toHaveAttribute("aria-valuenow", "74");
      await expect(panelInModal.getByTestId("emotion-expression-surprised-value")).toHaveText("87%");
      await panelInModal.getByTestId("social-context-report").scrollIntoViewIfNeeded();
      await expect(panelInModal.getByTestId("social-context-report")).toBeVisible();
      await expect(panelInModal.getByTestId("social-context-human-count")).toHaveText("3");
      await expect(panelInModal.getByTestId("social-context-group-count")).toHaveText("1");
      await expect(panelInModal.getByTestId("social-group-1-size")).toHaveText("size 2");
      await expect(panelInModal.getByTestId("social-person-2-confidence")).toHaveText("conf 86%");
      await expect(panelInModal.getByTestId("social-person-2-activity")).toHaveText("activity approaching");
      await expect(panelInModal.getByTestId("social-person-2-movement-confidence")).toHaveText("movement 81%");
      await expect(panelInModal.getByTestId("social-person-2-attention")).toHaveText("attention not attending");
      await expect(panelInModal.getByTestId("social-person-2-attention-confidence")).toHaveText("attention 44%");
      await panelInModal.getByTestId("hand-sign-report").scrollIntoViewIfNeeded();
      await expect(panelInModal.getByTestId("hand-report-visual")).toHaveText("\u270B");
      await expect(panelInModal.getByTestId("hand-report-label")).toHaveText("Papier");
      await expect(panelInModal.getByTestId("hand-report-confidence-meter")).toHaveAttribute("aria-valuenow", "88");
      await expect(panelInModal.getByTestId("hand-report-source")).toHaveText("Camera");
      await panelInModal.getByTestId("weather-report").scrollIntoViewIfNeeded();
      await expect(panelInModal.getByTestId("weather-report-location")).toHaveText("Zurich, Switzerland");
      await expect(panelInModal.getByTestId("weather-report-condition")).toHaveText("Rain");
      await expect(panelInModal.getByTestId("weather-forecast-day-1-temperature")).toHaveText("15.2-22.4 C");
    },
  });
  await verifyColumnExpansion(page, testInfo, {
    key: "interaction",
    title: "Interaction",
    buttonTestId: "maximize-interaction-column",
    afterRestore: async () => {
      await expect(page.locator("#continuous_speech_panel")).toHaveClass(/active/);
    },
  });
  await verifyColumnExpansion(page, testInfo, {
    key: "behaviour",
    title: "Behaviour",
    buttonTestId: "maximize-behaviour-column",
    inModal: async (panelInModal) => {
      await expect(panelInModal.getByTestId("behaviour-state-board")).toBeVisible();
      await expect(panelInModal.getByTestId("behaviour-chip-gesture")).toHaveClass(/is-active/);
      await expect(panelInModal.getByTestId("gesture-icon")).toHaveClass(/bi-question-diamond/);
      await expect(panelInModal.getByTestId("face-intensity-meter")).toHaveAttribute("aria-valuenow", "72");
      await expect(panelInModal.getByTestId("motion-energy-meter")).toHaveAttribute("aria-valuenow", "64");
      await expect(panelInModal.getByTestId("motion-stillness-meter")).toHaveAttribute("aria-valuenow", "28");
      await expect(panelInModal.getByTestId("agent-sign-visual")).toHaveText("\u270A");
      await expect(panelInModal.getByTestId("user-sign-visual")).toHaveText("\u270B");
    },
  });
});

test("Valerian face emotion detector updates the report from the camera loop", async ({ page }) => {
  await page.goto("/valerian/");
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();

  await installMockFaceCamera(page);
  await enableEmotionDetectorForSmoke(page);
  await openSensedSignals(page);

  await page.evaluate(async () => {
    if (typeof window.startCamera !== "function") {
      throw new Error("startCamera is not available on the Valerian page.");
    }
    await window.startCamera();
  });

  await expect(page.getByTestId("camera-status")).toHaveText("Camera Live");
  await expect(page.getByTestId("emotion-value")).toHaveText("happy 0.92");
  await expect(page.getByTestId("emotion-valence-value")).toHaveText("+0.90");
  await expect(page.getByTestId("emotion-arousal-value")).toHaveText("0.37");
  await expect(page.getByTestId("emotion-face-confidence-value")).toHaveText("0.94");
  await expect(page.getByTestId("emotion-emit-status")).toHaveText("Live only");
  await expect(page.getByTestId("emotion-affect-marker"))
    .toHaveAttribute("aria-label", "Valence +0.90, arousal 0.37");
  await expect(page.getByTestId("emotion-affect-marker")).toHaveAttribute("data-emotion", "happy");
  await expect(page.getByTestId("emotion-expression-happy-value")).toHaveText("92%");
  expect(await overlayPixelCount(page)).toBeGreaterThan(100);

  await page.evaluate(() => window.stopCamera({ silent: true }));
});

test("Valerian social detector keeps reporting when face detector fails", async ({ page }) => {
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto("/valerian/");
  await expect(page.getByTestId("access-screen")).toBeVisible();
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("cockpit-shell")).toBeVisible();

  await installMockSocialCameraWithThrowingFace(page);
  await enableVisualDetectorsForSmoke(page, ["social", "emotion"]);
  await openSensedSignals(page);

  await page.evaluate(async () => {
    if (typeof window.startCamera !== "function") {
      throw new Error("startCamera is not available on the Valerian page.");
    }
    await window.startCamera();
  });

  await expect(page.getByTestId("camera-status")).toHaveText("Camera Live");
  await expect(page.getByTestId("human-count")).toHaveText("1");
  await expect(page.getByTestId("social-context-status")).toHaveText("1 person");
  await expect(page.getByTestId("social-context-human-count")).toHaveText("1");
  await expect(page.getByTestId("social-context-singleton-count")).toHaveText("1");
  await expect(page.getByTestId("social-person-1-confidence")).toHaveText("conf 93%");
  await expect(page.getByTestId("emotion-emit-status")).toHaveText("Detection error");
  expect(await overlayPixelCount(page)).toBeGreaterThan(100);
  expect(pageErrors).toEqual([]);

  await page.evaluate(() => window.stopCamera({ silent: true }));
});

async function verifyColumnExpansion(page, testInfo, options) {
  const { key, title, buttonTestId, afterRestore, inModal } = options;
  const column = page.locator(`[data-column-key="${key}"]`);
  const panelInColumn = page.locator(`[data-column-key="${key}"] > [data-column-panel="${key}"]`);
  const panelInModal = page.locator(`#column_expansion_body [data-column-panel="${key}"]`);
  const placeholder = page.locator(`[data-column-placeholder="${key}"]`);
  const modal = page.getByTestId("column-expansion-modal");
  const modalBody = page.getByTestId("column-expansion-body");

  await expect(panelInColumn).toBeVisible();
  await expect(placeholder).toBeHidden();
  const originalBox = await requiredBox(column, `${key} column`);

  await page.getByTestId(buttonTestId).click();
  await expect(modal).toBeVisible();
  await expect(page.getByTestId("column-expansion-title")).toHaveText(title);
  await expect(panelInModal).toBeVisible();
  await expect(placeholder).toBeVisible();
  await expect(panelInColumn).toHaveCount(0);

  const modalBodyBox = await requiredBox(modalBody, `${key} modal body`);
  const expandedPanelBox = await requiredBox(panelInModal, `${key} expanded panel`);
  expect(modalBodyBox.width).toBeGreaterThan(originalBox.width + 240);
  expect(expandedPanelBox.width).toBeGreaterThan(originalBox.width + 220);

  if (inModal) {
    await inModal(panelInModal);
  }

  const screenshot = await modal.screenshot({
    path: testInfo.outputPath(`${key}-expanded.png`),
  });
  expect(screenshot.length).toBeGreaterThan(10_000);

  await page.locator("#column_expansion_modal [data-bs-dismiss=\"modal\"]").click();
  await expect(modal).toBeHidden();
  await expect(panelInColumn).toBeVisible();
  await expect(placeholder).toBeHidden();
  await expect(panelInModal).toHaveCount(0);

  if (afterRestore) {
    await afterRestore();
  }
}

async function renderSampleBehaviour(page) {
  await page.evaluate((plan) => {
    if (typeof window.renderBehaviourPlan !== "function") {
      throw new Error("renderBehaviourPlan is not available on the Valerian page.");
    }
    window.renderBehaviourPlan(plan);
  }, SAMPLE_BEHAVIOUR_PLAN);
}

async function installMockFaceCamera(page) {
  await page.evaluate(({ emotion, faceScore }) => {
    const stream = new MediaStream();
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: {
        getUserMedia: async () => stream,
        enumerateDevices: async () => [{
          kind: "videoinput",
          deviceId: "mock-camera",
          groupId: "mock",
          label: "Mock camera",
        }],
        addEventListener: () => {},
      },
    });
    const video = document.getElementById("camera_video");
    Object.defineProperty(video, "videoWidth", { configurable: true, get: () => 640 });
    Object.defineProperty(video, "videoHeight", { configurable: true, get: () => 480 });
    Object.defineProperty(video, "readyState", {
      configurable: true,
      get: () => HTMLMediaElement.HAVE_ENOUGH_DATA,
    });
    video.play = async () => {};
    window.faceapi = {
      nets: {
        tinyFaceDetector: { loadFromUri: async () => true },
        faceExpressionNet: { loadFromUri: async () => true },
      },
      TinyFaceDetectorOptions: class TinyFaceDetectorOptions {
        constructor(options) {
          this.options = options;
        }
      },
      detectSingleFace: () => ({
        withFaceExpressions: async () => ({
          detection: {
            score: faceScore,
            box: { x: 120, y: 75, width: 180, height: 190 },
          },
          expressions: emotion.expressions,
        }),
      }),
    };
  }, { emotion: SAMPLE_CAMERA_EMOTION, faceScore: SAMPLE_CAMERA_FACE_SCORE });
}

async function enableEmotionDetectorForSmoke(page) {
  await enableVisualDetectorsForSmoke(page, ["emotion"]);
}

async function installMockSocialCameraWithThrowingFace(page) {
  await page.evaluate(() => {
    const stream = new MediaStream();
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: {
        getUserMedia: async () => stream,
        enumerateDevices: async () => [{
          kind: "videoinput",
          deviceId: "mock-camera",
          groupId: "mock",
          label: "Mock camera",
        }],
        addEventListener: () => {},
      },
    });
    const video = document.getElementById("camera_video");
    Object.defineProperty(video, "videoWidth", { configurable: true, get: () => 640 });
    Object.defineProperty(video, "videoHeight", { configurable: true, get: () => 480 });
    Object.defineProperty(video, "readyState", {
      configurable: true,
      get: () => HTMLMediaElement.HAVE_ENOUGH_DATA,
    });
    video.play = async () => {};
    window.cocoSsd = {
      load: async () => ({
        detect: async () => [{
          class: "person",
          score: 0.93,
          bbox: [140, 70, 170, 320],
        }],
      }),
    };
    window.faceapi = {
      nets: {
        tinyFaceDetector: { loadFromUri: async () => true },
        faceExpressionNet: { loadFromUri: async () => true },
      },
      TinyFaceDetectorOptions: class TinyFaceDetectorOptions {
        constructor(options) {
          this.options = options;
        }
      },
      detectSingleFace: () => ({
        withFaceExpressions: async () => {
          throw new TypeError("d is not a function");
        },
      }),
    };
  });
}

async function enableVisualDetectorsForSmoke(page, modes) {
  await page.evaluate(async (selectedModes) => {
    if (typeof window.handleSensorModeChange !== "function") {
      throw new Error("Detector controls are not available on the Valerian page.");
    }
    const modeIds = {
      emotion: "sensor_emotion_enabled",
      social: "sensor_social_enabled",
      hand: "sensor_hand_enabled",
    };
    for (const [mode, id] of Object.entries(modeIds)) {
      const input = document.getElementById(id);
      if (input) {
        input.disabled = false;
        input.checked = selectedModes.includes(mode);
      }
    }
    await window.handleSensorModeChange();
  }, modes);
}

async function overlayPixelCount(page) {
  return page.evaluate(() => {
    const canvas = document.getElementById("overlay_canvas");
    if (!canvas || canvas.width === 0 || canvas.height === 0) {
      return 0;
    }
    const context = canvas.getContext("2d");
    const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
    let visible = 0;
    for (let i = 3; i < data.length; i += 4) {
      if (data[i] !== 0) {
        visible += 1;
      }
    }
    return visible;
  });
}

async function renderSampleEmotion(page) {
  await page.evaluate(({ emotion, faceScore }) => {
    if (typeof window.renderEmotionMetrics !== "function") {
      throw new Error("renderEmotionMetrics is not available on the Valerian page.");
    }
    window.renderEmotionMetrics(emotion, faceScore);
  }, { emotion: SAMPLE_EMOTION, faceScore: SAMPLE_FACE_SCORE });
}

async function renderSampleSocial(page) {
  await page.evaluate(({ social, people }) => {
    if (typeof window.renderSocialMetrics !== "function") {
      throw new Error("renderSocialMetrics is not available on the Valerian page.");
    }
    window.renderSocialMetrics(social, people);
  }, { social: SAMPLE_SOCIAL, people: SAMPLE_TRACKED_PEOPLE });
}

async function verifyManualSocialDetailEditor(page) {
  await page.locator("[data-bs-target=\"#manual_social_events\"]").click();
  await expect(page.locator("#manual_social_events")).toHaveClass(/show/);
  await page.evaluate(() => {
    document
      .querySelectorAll("#manual_social_events input,#manual_social_events select,#manual_social_events button")
      .forEach((control) => {
        control.disabled = false;
      });
  });
  await page.getByTestId("manual-social-people-count").fill("2");
  await page.getByTestId("manual-social-group-preset").selectOption("pair");
  await expect(page.getByTestId("manual-social-person-2")).toBeVisible();
  await page.getByTestId("manual-social-person-1-movement").selectOption("moving");
  await page.getByTestId("manual-social-person-1-movement-confidence").fill("0.7");
  await page.getByTestId("manual-social-person-1-attention").selectOption("attending");
  await page.getByTestId("manual-social-person-1-attention-confidence").fill("0.8");
  await page.getByTestId("manual-social-person-1-face-visible").check();
  await page.getByTestId("manual-social-person-1-near-frontal").check();
  await page.getByTestId("manual-social-person-1-centered").check();
  await page.getByTestId("manual-social-person-2-movement").selectOption("receding");
  await page.getByTestId("manual-social-person-2-attention").selectOption("not_attending");
  const snapshot = await page.evaluate(() => {
    if (typeof window.manualSocialSnapshot !== "function") {
      throw new Error("manualSocialSnapshot is not available on the Valerian page.");
    }
    return window.manualSocialSnapshot();
  });
  expect(snapshot.social.humanCount).toBe(2);
  expect(snapshot.social.groupCount).toBe(1);
  expect(snapshot.social.groups[0]).toEqual({ members: [1, 2] });
  expect(snapshot.people[0].movementState).toBe("moving");
  expect(snapshot.people[0].movementConfidence).toBe(0.7);
  expect(snapshot.people[0].attention.state).toBe("attending");
  expect(snapshot.people[0].attention.confidence).toBe(0.8);
  expect(snapshot.people[0].attention.frontalCentered).toBe(true);
  expect(snapshot.people[1].movementState).toBe("receding");
  expect(snapshot.people[1].attention.state).toBe("not_attending");
}

async function renderSampleHandSign(page) {
  await page.evaluate((sample) => {
    if (typeof window.renderHandSignReport !== "function") {
      throw new Error("renderHandSignReport is not available on the Valerian page.");
    }
    window.renderHandSignReport(sample.sign, {
      source: sample.source,
      detectionMode: sample.detectionMode,
      confidence: sample.confidence,
      cannedGesture: sample.cannedGesture,
      stabilityFrames: sample.stabilityFrames,
      statusText: "Live",
      statusMode: "live",
    });
    document.getElementById("hand_sign_value").textContent = "Papier 0.88";
  }, SAMPLE_HAND_SIGN);
}

async function renderSampleWeather(page) {
  await page.evaluate((forecast) => {
    if (typeof window.renderWeatherPayload !== "function") {
      throw new Error("renderWeatherPayload is not available on the Valerian page.");
    }
    window.renderWeatherPayload(forecast);
  }, SAMPLE_WEATHER_FORECAST);
}

async function verifyTrackMovementHeuristic(page) {
  const movement = await page.evaluate(async () => {
    if (typeof window.updateTracks !== "function") {
      throw new Error("updateTracks is not available on the Valerian page.");
    }
    const video = document.getElementById("camera_video");
    const width = video && video.videoWidth ? video.videoWidth : 1;
    const height = video && video.videoHeight ? video.videoHeight : 1;
    const w = width * 0.22;
    const h = height * 0.55;
    const y = height * 0.12;
    const firstCx = width * 0.5;
    const secondCx = width * 0.56;
    window.updateTracks([{ x: firstCx - w / 2, y, w, h, score: 0.91, cx: firstCx, cy: y + h / 2 }]);
    await new Promise((resolve) => setTimeout(resolve, 5));
    const tracked = window.updateTracks([{ x: secondCx - w / 2, y, w, h, score: 0.93, cx: secondCx, cy: y + h / 2 }]);
    return tracked[0];
  });
  expect(movement.movementState).toBe("moving");
  expect(movement.activity).toBe("moving");
  expect(movement.movementConfidence).toBeGreaterThan(0.35);
  expect(movement.attention.state).toBe("attending");
  expect(movement.attention.personVisible).toBe(true);
  expect(movement.attention.confidence).toBeGreaterThan(0.6);
}

async function verifySocialContextPayloadContract(page) {
  const payload = await page.evaluate(({ social, people }) => {
    if (typeof window.socialContextPayload !== "function") {
      throw new Error("socialContextPayload is not available on the Valerian page.");
    }
    return window.socialContextPayload(social, people, "visual.social");
  }, { social: SAMPLE_SOCIAL, people: SAMPLE_TRACKED_PEOPLE });
  expect(payload.schemaVersion).toBe(1);
  expect(payload.source).toBe("visual.social");
  expect(payload.humanCount).toBe(3);
  expect(payload.groups[0]).toEqual({ memberIds: [1, 2], size: 2 });
  expect(payload.people[0].detectionConfidence).toBe(0.92);
  expect(payload.people[0].movement).toEqual({ state: "moving", confidence: 0.72 });
  expect(payload.people[0].attention.state).toBe("attending");
  expect(payload.people[0].attention.faceVisible).toBe(true);
  expect(payload.people[1].attention.state).toBe("not_attending");
}

async function openSensedSignals(page) {
  const panel = page.locator("#sensed_signals");
  const className = await panel.getAttribute("class");
  if (!className || !className.includes("show")) {
    await page.locator("[data-bs-target=\"#sensed_signals\"]").click();
  }
  await expect(panel).toHaveClass(/show/);
}

async function verifyEmotionReport(page) {
  await expect(page.getByTestId("emotion-report")).toBeVisible();
  await expect(page.getByTestId("emotion-value")).toHaveText("surprised 0.87");
  await expect(page.getByTestId("emotion-valence-value")).toHaveText("+0.32");
  await expect(page.getByTestId("emotion-arousal-value")).toHaveText("0.74");
  await expect(page.getByTestId("emotion-confidence-value")).toHaveText("0.87");
  await expect(page.getByTestId("emotion-face-confidence-value")).toHaveText("0.91");
  await expect(page.getByTestId("emotion-emit-status")).toHaveText("Live");
  await expect(page.getByTestId("emotion-affect-marker"))
    .toHaveAttribute("aria-label", "Valence +0.32, arousal 0.74");
  await expect(page.getByTestId("emotion-affect-marker")).toHaveAttribute("data-emotion", "surprised");
  await expect(page.getByTestId("emotion-affect-marker")).toHaveAttribute("style", /left: 66%; bottom: 74%;/);
  await expect(page.getByTestId("emotion-valence-meter")).toHaveAttribute("aria-valuenow", "66");
  await expect(page.getByTestId("emotion-arousal-meter")).toHaveAttribute("aria-valuenow", "74");
  await expect(page.getByTestId("emotion-confidence-meter")).toHaveAttribute("aria-valuenow", "87");
  await expect(page.getByTestId("emotion-face-confidence-meter")).toHaveAttribute("aria-valuenow", "91");
  await expect(page.getByTestId("emotion-expression-happy-value")).toHaveText("22%");
  await expect(page.getByTestId("emotion-expression-happy-meter")).toHaveAttribute("aria-valuenow", "22");
  await expect(page.getByTestId("emotion-expression-surprised-value")).toHaveText("87%");
  await expect(page.getByTestId("emotion-expression-surprised-meter")).toHaveAttribute("aria-valuenow", "87");
}

async function verifySocialContextReport(page) {
  await expect(page.getByTestId("social-context-report")).toBeVisible();
  await expect(page.getByTestId("human-count")).toHaveText("3");
  await expect(page.getByTestId("group-count")).toHaveText("1");
  await expect(page.getByTestId("social-context-status")).toHaveText("3 people");
  await expect(page.getByTestId("social-context-human-count")).toHaveText("3");
  await expect(page.getByTestId("social-context-group-count")).toHaveText("1");
  await expect(page.getByTestId("social-context-largest-group")).toHaveText("2");
  await expect(page.getByTestId("social-context-singleton-count")).toHaveText("1");
  await expect(page.getByTestId("social-group-1")).toContainText("Group 1");
  await expect(page.getByTestId("social-group-1-size")).toHaveText("size 2");
  await expect(page.getByTestId("social-group-1")).toContainText("ID 1");
  await expect(page.getByTestId("social-group-1")).toContainText("ID 2");
  await expect(page.getByTestId("social-group-2")).toContainText("Singleton 2");
  await expect(page.getByTestId("social-group-2")).toContainText("ID 3");
  await expect(page.getByTestId("social-person-1")).toContainText("activity moving");
  await expect(page.getByTestId("social-person-1-confidence")).toHaveText("conf 92%");
  await expect(page.getByTestId("social-person-1-movement-confidence")).toHaveText("movement 72%");
  await expect(page.getByTestId("social-person-1-attention")).toHaveText("attention attending");
  await expect(page.getByTestId("social-person-1-attention-confidence")).toHaveText("attention 76%");
  await expect(page.getByTestId("social-person-1-face-visible")).toHaveText("face likely");
  await expect(page.getByTestId("social-person-1-centered")).toHaveText("centered yes");
  await expect(page.getByTestId("social-person-2-activity")).toHaveText("activity approaching");
  await expect(page.getByTestId("social-person-2-attention")).toHaveText("attention not attending");
  await expect(page.getByTestId("social-person-3-activity")).toHaveText("activity stationary");
  await expect(page.getByTestId("social-person-3-attention")).toHaveText("attention unknown");
  await expect(page.getByTestId("social-person-3-face-visible")).toHaveText("face unclear");
}

async function verifyHandSignReport(page) {
  await expect(page.getByTestId("hand-sign-report")).toBeVisible();
  await expect(page.getByTestId("hand-sign-value")).toHaveText("Papier 0.88");
  await expect(page.getByTestId("hand-sign-status")).toHaveText("Live");
  await expect(page.getByTestId("hand-report-visual")).toHaveText("\u270B");
  await expect(page.getByTestId("hand-report-label")).toHaveText("Papier");
  await expect(page.getByTestId("hand-report-confidence")).toHaveText("88%");
  await expect(page.getByTestId("hand-report-confidence-meter")).toHaveAttribute("aria-valuenow", "88");
  await expect(page.getByTestId("hand-report-source")).toHaveText("Camera");
  await expect(page.getByTestId("hand-report-mode")).toHaveText("client camera");
  await expect(page.getByTestId("hand-report-canned")).toHaveText("Open_Palm");
  await expect(page.getByTestId("hand-report-stability")).toHaveText("4 frames");
}

async function verifyWeatherReport(page) {
  await expect(page.getByTestId("weather-report")).toBeVisible();
  await expect(page.getByTestId("weather-value")).toHaveText("Forecast Zurich, Switzerland: rain, 15.2-22.4 C");
  await expect(page.getByTestId("weather-report-status")).toHaveText("Forecast");
  await expect(page.getByTestId("weather-report-location")).toHaveText("Zurich, Switzerland");
  await expect(page.getByTestId("weather-report-condition")).toHaveText("Rain");
  await expect(page.getByTestId("weather-report-temperature")).toHaveText("15.2-22.4 C");
  await expect(page.getByTestId("weather-report-precipitation")).toHaveText("medium (4.6 mm)");
  await expect(page.getByTestId("weather-report-wind")).toHaveText("Windy");
  await expect(page.getByTestId("weather-report-light")).toHaveText("Forecast");
  await expect(page.getByTestId("weather-forecast-day-1-date")).toHaveText("07-04");
  await expect(page.getByTestId("weather-forecast-day-1-condition")).toHaveText("Rain");
  await expect(page.getByTestId("weather-forecast-day-2-condition")).toHaveText("Cloudy");
  await expect(page.getByTestId("weather-forecast-day-3-condition")).toHaveText("Clear");
}

async function verifyBehaviourVisualState(page) {
  await expect(page.getByTestId("behaviour-state-board")).toBeVisible();
  await expect(page.getByTestId("speech-preview")).toContainText(SAMPLE_BEHAVIOUR_PLAN.speech);
  await expect(page.getByTestId("gesture-icon")).toHaveClass(/bi-question-diamond/);
  await expect(page.getByTestId("gesture-value")).toHaveText("Open Question");
  await expect(page.getByTestId("gesture-hint")).toHaveText("Inviting response");
  await expect(page.getByTestId("face-value")).toHaveText("warm_smile");
  await expect(page.getByTestId("face-intensity-value")).toHaveText("72%");
  await expect(page.getByTestId("face-intensity-meter")).toHaveAttribute("aria-valuenow", "72");
  await expect(page.getByTestId("gaze-value")).toHaveText("toward_user");
  await expect(page.getByTestId("gaze-focus-value")).toHaveText("Focus speaker");
  await expect(page.getByTestId("motion-energy-value")).toHaveText("64%");
  await expect(page.getByTestId("motion-energy-meter")).toHaveAttribute("aria-valuenow", "64");
  await expect(page.getByTestId("motion-stillness-value")).toHaveText("28%");
  await expect(page.getByTestId("motion-stillness-meter")).toHaveAttribute("aria-valuenow", "28");
  await expect(page.getByTestId("agent-sign-label")).toHaveText("Stein");
  await expect(page.getByTestId("user-sign-label")).toHaveText("Papier");
  await expect(page.getByTestId("round-value")).toHaveText("2");
  await expect(page.getByTestId("winner-value")).toHaveText("User");
  await expect(page.getByTestId("behaviour-chip-speech")).toHaveClass(/is-active/);
  await expect(page.getByTestId("behaviour-chip-gesture")).toHaveClass(/is-active/);
  await expect(page.getByTestId("behaviour-chip-face")).toHaveClass(/is-active/);
  await expect(page.getByTestId("behaviour-chip-gaze")).toHaveClass(/is-active/);
  await expect(page.getByTestId("behaviour-chip-motion")).toHaveClass(/is-active/);
  await expect(page.getByTestId("behaviour-chip-display")).toHaveClass(/is-active/);
}

async function requiredBox(locator, name) {
  const box = await locator.boundingBox();
  expect(box, `${name} should have a visible bounding box`).not.toBeNull();
  return box;
}

async function ensureAccessCode(request, code) {
  const createResponse = await request.post("/admin/access-codes", {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
    data: { code, enabled: true },
  });
  if (createResponse.status() === 201) {
    return;
  }
  if (createResponse.status() !== 409) {
    throw new Error(`Unable to create access code ${code}: ${createResponse.status()} ${await createResponse.text()}`);
  }

  const listResponse = await request.get("/admin/access-codes", {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
  });
  if (!listResponse.ok()) {
    throw new Error(`Unable to list access codes after conflict: ${listResponse.status()}`);
  }
  const accessCodes = await listResponse.json();
  const existing = accessCodes.find((entry) => entry && entry.code === code);
  if (!existing) {
    throw new Error(`Access code ${code} already exists but was not returned by the admin list endpoint.`);
  }
  if (existing.enabled) {
    return;
  }

  const enableResponse = await request.patch(`/admin/access-codes/${existing.id}`, {
    headers: { [ADMIN_TOKEN_HEADER]: ADMIN_TOKEN },
    data: { enabled: true },
  });
  if (!enableResponse.ok()) {
    throw new Error(`Unable to re-enable access code ${code}: ${enableResponse.status()}`);
  }
}
