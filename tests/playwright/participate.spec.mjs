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
  await page.locator("[data-registration-form]").getByLabel("Geburtsdatum").fill("1990-05-12");
  await page.locator("[data-registration-form]").getByLabel("E-Mail-Adresse").fill("max.muster@example.com");
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
  await expect(page.locator('[data-metric="phase-1"]')).toHaveText("2");

  await page.goto("/");
  await page.getByRole("button", { name: "Mitmachen" }).click();
  await expect(page.locator("[data-registration-dialog]")).toBeVisible();

  const resubscribe = await page.request.post("/api/register.php", {
    data: registrations[1],
  });
  expect(resubscribe.status()).toBe(201);
});

test("admin controls overall and participant phases and edits assignments", async ({ page }) => {
  const registrations = [
    {
      fullName: "Phase Eins",
      dateOfBirth: "1985-03-11",
      email: "phase.one@example.com",
      slotPreference: "2026-08-17-morning",
    },
    {
      fullName: "Phase Zwei",
      dateOfBirth: "1987-04-12",
      email: "phase.two@example.com",
      slotPreference: "2026-08-17-afternoon",
    },
  ];
  for (const registration of registrations) {
    const response = await page.request.post("/api/register.php", { data: registration });
    expect(response.status()).toBe(201);
  }

  await page.goto("/admin/");
  await expect(page.getByRole("heading", { name: "Gesamtphase steuern" })).toBeVisible();
  await page.getByLabel("Suche").fill("phase.one@example.com");
  await page.getByRole("button", { name: "Phase Eins bearbeiten" }).click();
  await expect(page.locator("[data-participant-dialog]")).toBeVisible();
  await page.getByLabel("Individuelle Phase").selectOption("3");
  await page.getByLabel("Halbtag").fill("Morgen");
  await page.getByLabel("Zeitfenster").fill("09:45 - 11:00 Uhr");
  await page.getByRole("button", { name: "Änderungen speichern" }).click();

  await page.getByLabel("Suche").fill("phase.one@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 2 · Termin (angefordert: Phase 3)");
  await expect(page.locator("[data-table-body]")).toContainText("Zugangscode, Rolle, Team-ID, Raum");

  await page.getByRole("button", { name: "Phase Eins bearbeiten" }).click();
  await page.getByLabel("Zugangscode").fill("PHASE-ONE-CODE");
  await page.getByLabel("Rolle").fill("A");
  await page.getByLabel("Team-ID").fill("15");
  await page.getByLabel("Raum").fill("B");
  await page.getByRole("button", { name: "Änderungen speichern" }).click();

  await page.getByLabel("Suche").fill("phase.one@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 3 · Zuteilung");
  await expect(page.locator("[data-table-body]")).toContainText("PHASE-ONE-CODE");

  const phaseTwoId = await page.locator("#registration_data").evaluate((element) => {
    const entries = JSON.parse(element.textContent || "[]");
    return entries.find((entry) => entry.email === "phase.two@example.com")?.id;
  });
  const duplicateCode = await page.request.post("/admin/update.php", {
    data: {
      action: "save_participant",
      id: phaseTwoId,
      accessCode: "PHASE-ONE-CODE",
      role: "B",
      teamId: "15",
      halfDaySlot: "Nachmittag",
      timeSlot: "13:00 - 14:15 Uhr",
      room: "B",
    },
  });
  expect(duplicateCode.status()).toBe(409);
  expect((await duplicateCode.json()).code).toBe("duplicate_access_code");

  await page.getByLabel("Suche").fill("");
  await page.getByLabel("Gesamtphase").selectOption("4");
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Gesamtphase speichern" }).click();
  await expect(page.getByLabel("Gesamtphase")).toHaveValue("4");

  const closedSignup = await page.request.post("/api/register.php", {
    data: {
      fullName: "Zu spät",
      dateOfBirth: "1993-05-13",
      email: "signup.closed@example.com",
      slotPreference: "2026-08-17-morning",
    },
  });
  expect(closedSignup.status()).toBe(409);
  expect((await closedSignup.json()).code).toBe("signup_closed");

  await page.getByLabel("Suche").fill("phase.two@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 1 · Anmeldung (angefordert: Phase 4)");

  await page.getByLabel("Suche").fill("phase.one@example.com");
  await page.getByRole("button", { name: "Phase Eins bearbeiten" }).click();
  await page.getByLabel("Individuelle Phase").selectOption("");
  await page.getByRole("button", { name: "Änderungen speichern" }).click();
  await page.getByLabel("Suche").fill("phase.one@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 4 · Abschluss");

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "CSV exportieren" }).click(),
  ]);
  const csv = readFileSync(await download.path(), "utf8");
  expect(csv).toContain("Zugangscode");
  expect(csv).toContain("Ergebnisinfo geändert");
  expect(csv).toContain("PHASE-ONE-CODE");

  await page.getByRole("button", { name: "Phase Eins bearbeiten" }).click();
  await page.getByLabel("Zeitfenster").fill("");
  await page.getByRole("button", { name: "Änderungen speichern" }).click();
  await page.getByLabel("Suche").fill("phase.one@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 1 · Anmeldung (angefordert: Phase 4)");
  await expect(page.locator("[data-table-body]")).toContainText("Zeitfenster");
});

test("participants recover access across devices and receive only their active phase data", async ({ page, browser }, testInfo) => {
  const defaultPhaseOne = await page.request.post("/admin/update.php", {
    data: { action: "set_default_phase", defaultPhase: 1 },
  });
  expect(defaultPhaseOne.status()).toBe(200);

  const registration = {
    fullName: "Recovery Person",
    dateOfBirth: "1991-06-14",
    email: "recovery.person@example.com",
    slotPreference: "2026-08-17-morning",
  };
  const registered = await page.request.post("/api/register.php", { data: registration });
  expect(registered.status()).toBe(201);

  await page.goto("/admin/");
  const registrationId = await page.locator("#registration_data").evaluate((element) => {
    const entries = JSON.parse(element.textContent || "[]");
    return entries.find((entry) => entry.email === "recovery.person@example.com")?.id;
  });
  expect(registrationId).toBeTruthy();

  const assignment = {
    action: "save_participant",
    id: registrationId,
    phaseOverride: 2,
    accessCode: "RECOVERY-CODE-141",
    role: "B",
    teamId: "21",
    halfDaySlot: "Morgen",
    timeSlot: "10:30 - 11:45 Uhr",
    room: "C",
  };
  const phaseTwoUpdate = await page.request.post("/admin/update.php", { data: assignment });
  expect(phaseTwoUpdate.status()).toBe(200);

  const baseURL = String(testInfo.project.use.baseURL);
  const recoveryContext = await browser.newContext({ baseURL });
  const recoveryPage = await recoveryContext.newPage();
  await recoveryPage.goto("/");
  await expect(recoveryPage.getByRole("button", { name: "Bereits angemeldet?" })).toBeVisible();
  await expect(recoveryPage.locator("[data-participant-section]")).toBeHidden();

  await recoveryPage.getByRole("button", { name: "Bereits angemeldet?" }).click();
  await recoveryPage.getByLabel("E-Mail-Adresse", { exact: true }).last().fill("recovery.person@example.com");
  await recoveryPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-13");
  await recoveryPage.getByRole("button", { name: "Anmeldung aufrufen" }).click();
  await expect(recoveryPage.locator("[data-recovery-alert]")).toContainText("keiner aktiven Anmeldung");

  await recoveryPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-14");
  await recoveryPage.getByRole("button", { name: "Anmeldung aufrufen" }).click();
  await expect(recoveryPage.getByRole("heading", { name: "Dein Termin" })).toBeVisible();
  await expect(recoveryPage.locator('[data-assignment-field="participantId"]')).toContainText(String(registrationId));
  await expect(recoveryPage.locator('[data-assignment-field="halfDaySlot"]')).toContainText("Morgen");
  await expect(recoveryPage.locator('[data-assignment-field="timeSlot"]')).toContainText("10:30 - 11:45 Uhr");
  await expect(recoveryPage.locator('[data-assignment-field="date"]')).toContainText(
    "Montag, 17. August 2026, 09:00 bis 13:00",
  );
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("RECOVERY-CODE-141");

  const phaseTwoPayload = await recoveryPage.request.get("/api/registration.php");
  expect(phaseTwoPayload.status()).toBe(200);
  expect(Object.keys((await phaseTwoPayload.json()).assignment)).toEqual([
    "participantId",
    "halfDaySlot",
    "timeSlot",
    "date",
  ]);
  const prematureInterest = await recoveryPage.request.post("/api/results-interest.php", {
    data: { interest: true },
  });
  expect(prematureInterest.status()).toBe(409);
  expect((await prematureInterest.json()).code).toBe("interest_not_available");

  const phaseThreeUpdate = await page.request.post("/admin/update.php", {
    data: { ...assignment, phaseOverride: 3 },
  });
  expect(phaseThreeUpdate.status()).toBe(200);
  await recoveryPage.reload();
  await expect(recoveryPage.getByRole("heading", { name: "Deine Zuteilung" })).toBeVisible();
  await expect(recoveryPage.locator('[data-assignment-field="accessCode"]')).toContainText("RECOVERY-CODE-141");
  await expect(recoveryPage.locator('[data-assignment-field="role"]')).toContainText("B");
  await expect(recoveryPage.locator('[data-assignment-field="teamId"]')).toContainText("21");
  await expect(recoveryPage.locator('[data-assignment-field="room"]')).toContainText("C");
  await expect(recoveryPage.locator('[data-assignment-field="date"]')).toContainText(
    "Montag, 17. August 2026, 09:00 bis 13:00",
  );

  const phaseFourUpdate = await page.request.post("/admin/update.php", {
    data: { ...assignment, phaseOverride: 4 },
  });
  expect(phaseFourUpdate.status()).toBe(200);
  await recoveryPage.reload();
  await expect(recoveryPage.getByRole("heading", { name: "Vielen Dank für Deine Teilnahme" })).toBeVisible();
  await expect(recoveryPage.locator("[data-results-interest-form]")).toBeVisible();
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("RECOVERY-CODE-141");
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("10:30 - 11:45 Uhr");
  const phaseFourBody = await (await recoveryPage.request.get("/api/registration.php")).json();
  expect(phaseFourBody.assignment).toEqual({});

  const interestCheckbox = recoveryPage.getByLabel("Ich möchte informiert werden");
  await expect(interestCheckbox).not.toBeChecked();
  await expect(recoveryPage.locator("[data-interest-status]")).toContainText("Noch keine Auswahl");
  await interestCheckbox.check();
  await recoveryPage.getByRole("button", { name: "Auswahl speichern" }).click();
  await expect(recoveryPage.locator("[data-interest-status]")).toContainText("Gespeichert: Ja");
  await recoveryPage.reload();
  await expect(interestCheckbox).toBeChecked();

  await interestCheckbox.uncheck();
  await recoveryPage.getByRole("button", { name: "Auswahl speichern" }).click();
  await expect(recoveryPage.locator("[data-interest-status]")).toContainText("Gespeichert: Nein");
  await recoveryPage.reload();
  await expect(interestCheckbox).not.toBeChecked();
  await expect(recoveryPage.locator("[data-interest-status]")).toContainText("Gespeichert: Nein");

  const closeSignup = await page.request.post("/admin/update.php", {
    data: { action: "set_default_phase", defaultPhase: 4 },
  });
  expect(closeSignup.status()).toBe(200);
  const closedContext = await browser.newContext({ baseURL });
  const closedPage = await closedContext.newPage();
  await closedPage.goto("/");
  await expect(closedPage.locator("[data-signup-status]")).toContainText("Anmeldung ist geschlossen");
  await expect(closedPage.getByRole("button", { name: "Mitmachen" })).toBeHidden();
  await expect(closedPage.getByRole("button", { name: "Bereits angemeldet?" })).toBeVisible();

  await closedPage.getByRole("button", { name: "Bereits angemeldet?" }).click();
  await closedPage.getByLabel("E-Mail-Adresse", { exact: true }).last().fill("RECOVERY.PERSON@EXAMPLE.COM");
  await closedPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-14");
  await closedPage.getByRole("button", { name: "Anmeldung aufrufen" }).click();
  await expect(closedPage.getByRole("heading", { name: "Vielen Dank für Deine Teilnahme" })).toBeVisible();

  await page.goto("/admin/");
  await page.getByLabel("Suche").fill("recovery.person@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Nein");
  await expect(page.locator("[data-table-body]")).not.toContainText("Noch nicht beantwortet");

  await closedContext.close();
  await recoveryContext.close();
});
