import { expect, test } from "@playwright/test";

test("participant wizard validates, reviews, submits, and restores local summary", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", {
    name: "Gestalte die Zukunft der Zusammenarbeit zwischen Menschen und KI",
  })).toBeVisible();

  await page.getByRole("button", { name: "Mitmachen" }).click();
  await expect(page.locator("[data-registration-dialog]")).toBeVisible();
  await expect(page.locator(".wizard-progress .nav-link.active")).toContainText("Angaben");

  await page.locator(".wizard-step.active [data-next]").click();
  await expect(page.locator("[data-validation-alert]")).toContainText("vollständigen Namen");

  await page.getByLabel("Vollständiger Name").fill("Max Muster");
  await page.getByLabel("Geburtsdatum").fill("1990-05-12");
  await page.getByLabel("E-Mail-Adresse").fill("max.muster@example.com");
  await page.locator(".wizard-step.active [data-next]").click();

  await expect(page.locator(".wizard-progress .nav-link.active")).toContainText("Termin");
  await page.locator('input[value="2026-08-17-morning"]').check();
  await page.locator(".wizard-step.active [data-next]").click();

  await expect(page.locator(".wizard-progress .nav-link.active")).toContainText("Prüfen");
  await expect(page.locator("[data-review-summary]")).toContainText("Max Muster");
  await expect(page.locator("[data-review-summary]")).toContainText("max.muster@example.com");
  await expect(page.locator("[data-review-summary]")).toContainText("09:00 bis 13:00");

  await page.locator(".wizard-step.active [data-open-privacy]").click();
  await expect(page.locator("[data-privacy-dialog]")).toBeVisible();
  await expect(page.locator("[data-privacy-dialog]")).toContainText("ZHAW/UZH");
  await page.getByRole("button", { name: "Verstanden" }).click();
  await expect(page.locator("[data-privacy-dialog]")).not.toBeVisible();

  await page.locator('.wizard-step.active button[type="submit"]').click();
  await expect(page.locator("#status_alert")).toContainText("Deine Anmeldung ist eingegangen");
  await expect(page.locator("[data-local-summary-section]")).toBeVisible();
  await expect(page.locator("[data-local-summary]")).toContainText("Max Muster");

  await page.reload();
  await expect(page.locator("[data-local-summary-section]")).toBeVisible();
  await expect(page.locator("[data-local-summary]")).toContainText("max.muster@example.com");
});

test("participant page remains usable on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");

  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.innerWidth);

  await page.getByRole("button", { name: "Mitmachen" }).click();
  await expect(page.locator("[data-registration-dialog]")).toBeVisible();
  await expect(page.locator(".wizard-progress")).toBeVisible();

  const modalOverflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(modalOverflow.scrollWidth).toBeLessThanOrEqual(modalOverflow.innerWidth);
});
