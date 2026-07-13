import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const mailDir = resolve(".web/participate/.tmp/mail");

function mailFiles() {
  try {
    return readdirSync(mailDir).filter((file) => file.endsWith(".eml"));
  } catch (error) {
    return [];
  }
}

test("participant wizard validates, reviews, submits, logs mail, and restores server summary", async ({ page }) => {
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

  await expect.poll(() => mailFiles().length).toBeGreaterThan(0);
  const firstMail = readFileSync(resolve(mailDir, mailFiles()[0]), "utf8");
  expect(firstMail).toContain("DO NOT REPLY TO THIS MAIL");
  expect(firstMail).toContain("Bcc: alexandre.despindler@zhaw.ch");
  expect(firstMail).toContain("max.muster@example.com");

  await page.reload();
  await expect(page.locator("[data-local-summary-section]")).toBeVisible();
  await expect(page.locator("[data-local-summary]")).toContainText("max.muster@example.com");

  const duplicate = await page.request.post("/api/register.php", {
    data: {
      fullName: "Max Muster",
      dateOfBirth: "1990-05-12",
      email: "max.muster@example.com",
      slotPreference: "2026-08-17-afternoon",
    },
  });
  expect(duplicate.status()).toBe(409);
  const duplicateBody = await duplicate.json();
  expect(duplicateBody.message).toContain("alexandre.despindler@zhaw.ch");
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

test("admin view searches, sorts, and exports all rows", async ({ page }) => {
  const registrations = [
    {
      fullName: "Admin Eins",
      dateOfBirth: "1988-01-15",
      email: "admin.one@example.com",
      slotPreference: "2026-08-17-morning",
    },
    {
      fullName: "Admin Zwei",
      dateOfBirth: "1992-02-20",
      email: "admin.two@example.com",
      slotPreference: "unavailable",
    },
  ];

  for (const registration of registrations) {
    const response = await page.request.post("/api/register.php", { data: registration });
    expect(response.status()).toBe(201);
  }

  await page.goto("/admin/");
  await expect(page.getByRole("heading", { name: "Admin Übersicht" })).toBeVisible();
  await expect(page.locator("[data-table-body]")).toContainText("admin.one@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("admin.two@example.com");

  await page.getByRole("button", { name: "E-Mail" }).click();
  await expect(page.getByRole("button", { name: "E-Mail" })).toHaveAttribute("data-sort-active", "asc");

  await page.getByLabel("Suche").fill("admin.one");
  await expect(page.locator("[data-table-body]")).toContainText("admin.one@example.com");
  await expect(page.locator("[data-table-body]")).not.toContainText("admin.two@example.com");

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "CSV exportieren" }).click(),
  ]);
  const downloadPath = await download.path();
  const csv = readFileSync(downloadPath, "utf8");
  expect(csv).toContain("admin.one@example.com");
  expect(csv).toContain("admin.two@example.com");

  await page.getByLabel("Suche").fill("admin.two");
  await page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Admin Zwei löschen" }).click();
  await expect(page.locator("[data-table-body]")).not.toContainText("admin.two@example.com");
  await expect(page.locator('[data-metric="unavailable"]')).toHaveText("0");

  await page.goto("/");
  await page.getByRole("button", { name: "Mitmachen" }).click();
  await expect(page.locator("[data-registration-dialog]")).toBeVisible();

  const resubscribe = await page.request.post("/api/register.php", {
    data: registrations[1],
  });
  expect(resubscribe.status()).toBe(201);
});
