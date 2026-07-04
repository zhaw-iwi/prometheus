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

  await renderSampleBehaviour(page);
  await verifyBehaviourVisualState(page);

  await verifyColumnExpansion(page, testInfo, {
    key: "sensing",
    title: "Sensing",
    buttonTestId: "maximize-sensing-column",
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
