import { expect, test } from "@playwright/test";

const ACCESS_CODE = "VX102";
const ADMIN_TOKEN = process.env.PROMETHEUS_ADMIN_TOKEN || "laure";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";

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
  });
});

async function verifyColumnExpansion(page, testInfo, options) {
  const { key, title, buttonTestId, afterRestore } = options;
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
