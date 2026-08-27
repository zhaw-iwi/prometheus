import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const mailDir = resolve(".web/participate/.tmp/mail");
const surveyUrl = "https://www.uzh.ch/zi/cl/surveys/index.php/922424?lang=de-easy";

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

  const sessionButton = page.locator("[data-participant-session-action]");
  await expect(sessionButton).toHaveAttribute("data-session-state", "anonymous");
  await expect(page.locator("[data-session-entry-icon]")).toBeVisible();
  await expect(page.locator("[data-session-exit-icon]")).toBeHidden();
  await sessionButton.click();
  const recoveryDialog = page.locator("[data-recovery-dialog]");
  await expect(recoveryDialog).toBeVisible();
  await expect(page.locator("[data-registration-dialog]")).toBeHidden();
  const recoveryDialogBox = await recoveryDialog.boundingBox();
  const recoveryFormBox = await page.locator("[data-recovery-form]").boundingBox();
  expect(recoveryDialogBox).not.toBeNull();
  expect(recoveryFormBox).not.toBeNull();
  expect(recoveryDialogBox.width).toBeLessThanOrEqual(720);
  expect(Math.abs(recoveryDialogBox.width - recoveryFormBox.width)).toBeLessThanOrEqual(4);
  expect(Math.abs(recoveryDialogBox.x - ((1440 - recoveryDialogBox.width) / 2))).toBeLessThanOrEqual(2);
  await recoveryDialog.getByRole("button", { name: "Abbrechen" }).click();

  const signupButton = page.getByRole("button", { name: "Mitmachen" });
  await expect(signupButton).toBeEnabled();
  await signupButton.click();
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
  await expect(sessionButton).toHaveAttribute("data-session-state", "registered");
  await expect(page.locator("[data-session-entry-icon]")).toBeHidden();
  await expect(page.locator("[data-session-exit-icon]")).toBeVisible();

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

  await sessionButton.click();
  await expect(page.locator("#status_alert")).toContainText("wurdest abgemeldet");
  await expect(page.locator("[data-local-summary-section]")).toBeHidden();
  await expect(sessionButton).toHaveAttribute("data-session-state", "anonymous");
  await expect(page.locator("[data-session-entry-icon]")).toBeVisible();
  await expect(page.locator("[data-session-exit-icon]")).toBeHidden();
  const loggedOutSession = await page.request.get("/api/registration.php");
  expect((await loggedOutSession.json()).registered).toBe(false);

  await sessionButton.click();
  await expect(page.locator("[data-recovery-dialog]")).toBeVisible();
  await expect(page.locator("[data-registration-dialog]")).toBeHidden();
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

  await page.locator("[data-registration-dialog]")
    .getByRole("button", { name: "Dialog schliessen" })
    .click();
  await page.locator("[data-participant-session-action]").click();
  const mobileRecoveryDialog = page.locator("[data-recovery-dialog]");
  await expect(mobileRecoveryDialog).toBeVisible();
  const mobileRecoveryBox = await mobileRecoveryDialog.boundingBox();
  expect(mobileRecoveryBox).not.toBeNull();
  expect(mobileRecoveryBox.width).toBeLessThanOrEqual(390);
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

test("admin creates minimal and complete participants and safely edits registration identity", async ({ page }) => {
  const mailCountBefore = mailFiles().length;
  const closeSignup = await page.request.post("/admin/update.php", {
    data: { action: "set_default_phase", defaultPhase: 2 },
  });
  expect(closeSignup.status()).toBe(200);

  await page.goto("/admin/");
  await page.getByRole("button", { name: "Teilnehmende Person erstellen" }).click();
  const participantDialog = page.locator("[data-participant-dialog]");
  await expect(participantDialog).toBeVisible();
  await expect(page.getByRole("heading", { name: "Teilnehmende Person erstellen" })).toBeVisible();
  await expect(page.getByLabel("Teilnehmenden-ID")).not.toHaveAttribute("required", "");
  await expect(page.getByLabel("E-Mail-Adresse")).toHaveAttribute("required", "");
  await expect(page.getByLabel("Geburtsdatum")).toHaveAttribute("required", "");
  await expect(page.getByLabel("Individuelle Phase")).toHaveValue("");
  await expect(page.getByLabel("Individuelle Phase").locator("option").first()).toContainText(
    "Phase 1 · Anmeldung",
  );

  await page.getByLabel("E-Mail-Adresse").fill("admin.minimal@example.com");
  await page.getByLabel("Geburtsdatum").fill("1990-07-15");
  await page.getByRole("button", { name: "Person erstellen", exact: true }).click();

  await page.getByLabel("Suche").fill("admin.minimal@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("admin.minimal@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 1 · Anmeldung");
  const minimalId = await page.locator("#registration_data").evaluate((element) => {
    const entries = JSON.parse(element.textContent || "[]");
    return entries.find((entry) => entry.email === "admin.minimal@example.com")?.id;
  });
  expect(minimalId).toBeTruthy();
  expect(mailFiles().length).toBe(mailCountBefore);

  const minimalRecovery = await page.request.post("/api/identify.php", {
    data: {
      email: "ADMIN.MINIMAL@EXAMPLE.COM",
      dateOfBirth: "1990-07-15",
    },
  });
  expect(minimalRecovery.status()).toBe(200);
  const minimalSession = await minimalRecovery.json();
  expect(minimalSession.registration.fullName).toBeNull();
  expect(minimalSession.registration.slotPreference).toBeNull();
  expect(minimalSession.phase.number).toBe(1);
  await page.goto("/");
  await expect(page.locator("[data-local-summary-section]")).toBeVisible();
  await expect(page.locator("[data-local-summary]")).toContainText("admin.minimal@example.com");

  const completeCreate = await page.request.post("/admin/update.php", {
    data: {
      action: "create_participant",
      participantId: 730001,
      fullName: "Admin Voll",
      email: "admin.full@example.com",
      dateOfBirth: "1984-08-16",
      slotId: 1,
      phaseOverride: 3,
      halfDaySlot: "Morgen",
      timeSlot: "09:45 - 11:00 Uhr",
      accessCode: "ADMIN-FULL-CODE",
      role: "A",
      teamId: "31",
      room: "B",
    },
  });
  expect(completeCreate.status()).toBe(201);
  const completeCreateBody = await completeCreate.json();
  expect(completeCreateBody.id).toBe(730001);
  expect(completeCreateBody.phase.effectivePhase).toBe(3);
  expect(completeCreateBody.mailSent).toBe(false);
  expect(completeCreateBody.confirmationRequired).toBe(false);

  const duplicateId = await page.request.post("/admin/update.php", {
    data: {
      action: "create_participant",
      participantId: 730001,
      email: "admin.other@example.com",
      dateOfBirth: "1980-01-01",
    },
  });
  expect(duplicateId.status()).toBe(409);
  expect((await duplicateId.json()).code).toBe("duplicate_participant_id");

  const duplicateEmail = await page.request.post("/admin/update.php", {
    data: {
      action: "create_participant",
      email: "ADMIN.FULL@EXAMPLE.COM",
      dateOfBirth: "1980-01-01",
    },
  });
  expect(duplicateEmail.status()).toBe(409);
  expect((await duplicateEmail.json()).code).toBe("duplicate_email");

  await page.goto("/admin/");
  await page.getByLabel("Suche").fill("admin.full@example.com");
  await page.getByRole("button", { name: "Admin Voll bearbeiten" }).click();
  await expect(page.getByRole("heading", { name: "Teilnehmende Person bearbeiten" })).toBeVisible();
  await expect(page.getByLabel("Teilnehmenden-ID")).toHaveAttribute("required", "");
  await page.getByLabel("Teilnehmenden-ID").fill("730002");
  await page.getByLabel("Name (optional)").fill("Admin Voll Editiert");
  await page.getByLabel("E-Mail-Adresse").fill("admin.full.edited@example.com");
  await page.getByLabel("Terminpräferenz (optional)").selectOption("2");
  await page.getByRole("button", { name: "Änderungen speichern" }).click();

  await page.getByLabel("Suche").fill("admin.full.edited@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("730002");
  await expect(page.locator("[data-table-body]")).toContainText("Admin Voll Editiert");
  await expect(page.locator("[data-table-body]")).toContainText("Phase 3 · Zuteilung");
  await expect(page.locator("[data-table-body]")).toContainText("ADMIN-FULL-CODE");

  const editedRecovery = await page.request.post("/api/identify.php", {
    data: {
      email: "admin.full.edited@example.com",
      dateOfBirth: "1984-08-16",
    },
  });
  expect(editedRecovery.status()).toBe(200);
  const editedSession = await editedRecovery.json();
  expect(editedSession.assignment.participantId).toBe(730002);
  expect(editedSession.assignment.accessCode).toBe("ADMIN-FULL-CODE");
  expect(editedSession.registration.slotPreference).toBe("2026-08-17-afternoon");

  const duplicateIdEdit = await page.request.post("/admin/update.php", {
    data: {
      action: "save_participant",
      id: minimalId,
      participantId: 730002,
    },
  });
  expect(duplicateIdEdit.status()).toBe(409);
  expect((await duplicateIdEdit.json()).code).toBe("duplicate_participant_id");

  const duplicateEmailEdit = await page.request.post("/admin/update.php", {
    data: {
      action: "save_participant",
      id: minimalId,
      email: "ADMIN.FULL.EDITED@EXAMPLE.COM",
    },
  });
  expect(duplicateEmailEdit.status()).toBe(409);
  expect((await duplicateEmailEdit.json()).code).toBe("duplicate_email");
  expect(mailFiles().length).toBe(mailCountBefore);

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("button", { name: "CSV exportieren" }).click(),
  ]);
  const csv = readFileSync(await download.path(), "utf8");
  expect(csv).toContain("admin.minimal@example.com");
  expect(csv).toContain("admin.full.edited@example.com");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/admin/");
  await page.getByRole("button", { name: "Teilnehmende Person erstellen" }).click();
  const mobileDialogBox = await participantDialog.boundingBox();
  expect(mobileDialogBox).not.toBeNull();
  expect(mobileDialogBox.width).toBeLessThanOrEqual(390);
  const mobileOverflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(mobileOverflow.scrollWidth).toBeLessThanOrEqual(mobileOverflow.innerWidth);
  await page.locator("[data-participant-submit]").scrollIntoViewIfNeeded();
  await expect(page.locator("[data-participant-submit]")).toBeVisible();
  await page.getByRole("button", { name: "Abbrechen" }).click();

  const reopenSignup = await page.request.post("/admin/update.php", {
    data: { action: "set_default_phase", defaultPhase: 1 },
  });
  expect(reopenSignup.status()).toBe(200);
});

test("admin controls overall and participant phases and edits assignments", async ({ page }) => {
  const defaultPhaseOne = await page.request.post("/admin/update.php", {
    data: { action: "set_default_phase", defaultPhase: 1 },
  });
  expect(defaultPhaseOne.status()).toBe(200);

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
  await recoveryContext.grantPermissions(["clipboard-read", "clipboard-write"], {
    origin: new URL(baseURL).origin,
  });
  await recoveryContext.route(surveyUrl, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<!doctype html><title>Survey test target</title>",
    });
  });
  const recoveryPage = await recoveryContext.newPage();
  await recoveryPage.goto("/");
  await expect(recoveryPage.getByRole("button", { name: "Bereits angemeldet?" })).toBeVisible();
  await expect(recoveryPage.locator("[data-participant-section]")).toBeHidden();

  await recoveryPage.getByRole("button", { name: "Bereits angemeldet?" }).click();
  await recoveryPage.getByLabel("E-Mail-Adresse", { exact: true }).last().fill("recovery.person@example.com");
  await recoveryPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-13");
  await recoveryPage.locator("[data-recovery-dialog]")
    .getByRole("button", { name: "Anmeldung aufrufen" })
    .click();
  await expect(recoveryPage.locator("[data-recovery-alert]")).toContainText("keiner aktiven Anmeldung");

  await recoveryPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-14");
  await recoveryPage.locator("[data-recovery-dialog]")
    .getByRole("button", { name: "Anmeldung aufrufen" })
    .click();
  await expect(recoveryPage.getByRole("heading", { name: "Dein Termin" })).toBeVisible();
  await expect(recoveryPage.locator('[data-assignment-field="participantId"]')).toContainText(String(registrationId));
  await expect(recoveryPage.locator('[data-assignment-field="halfDaySlot"]')).toContainText("Morgen");
  await expect(recoveryPage.locator('[data-assignment-field="timeSlot"]')).toContainText("10:30 - 11:45 Uhr");
  await expect(recoveryPage.locator('[data-assignment-field="date"]')).toContainText(
    "Montag, 17. August 2026, 09:00 bis 13:00",
  );
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("RECOVERY-CODE-141");
  await expect(recoveryPage.getByRole("button", { name: "Zugangscode kopieren" })).toHaveCount(0);
  await expect(recoveryPage.getByRole("link", { name: "Umfrage in neuem Tab öffnen" })).toHaveCount(0);

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
  const phaseThreeBody = await (await recoveryPage.request.get("/api/registration.php")).json();
  expect(Object.keys(phaseThreeBody.assignment)).toEqual([
    "participantId",
    "halfDaySlot",
    "timeSlot",
    "date",
    "accessCode",
    "role",
    "teamId",
    "room",
    "surveyUrl",
  ]);
  expect(phaseThreeBody.assignment.surveyUrl).toBe(surveyUrl);

  const copyAccessCode = recoveryPage.getByRole("button", { name: "Zugangscode kopieren" });
  await expect(copyAccessCode).toBeVisible();
  await expect(copyAccessCode.locator("svg")).toBeVisible();
  await copyAccessCode.click();
  await expect(recoveryPage.locator("#status_alert")).toContainText("Zugangscode wurde kopiert");
  expect(await recoveryPage.evaluate(() => navigator.clipboard.readText())).toBe("RECOVERY-CODE-141");

  const surveyLink = recoveryPage.getByRole("link", { name: "Umfrage in neuem Tab öffnen" });
  await expect(surveyLink).toBeVisible();
  await expect(surveyLink).toHaveAttribute("href", surveyUrl);
  await expect(surveyLink).toHaveAttribute("target", "_blank");
  await expect(surveyLink).toHaveAttribute("rel", "noopener noreferrer");
  const [surveyPage] = await Promise.all([
    recoveryPage.waitForEvent("popup"),
    surveyLink.click(),
  ]);
  await surveyPage.waitForLoadState("domcontentloaded");
  expect(surveyPage.url()).toBe(surveyUrl);
  await surveyPage.close();
  await recoveryPage.setViewportSize({ width: 390, height: 844 });
  const phaseThreeMobileOverflow = await recoveryPage.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(phaseThreeMobileOverflow.scrollWidth).toBeLessThanOrEqual(phaseThreeMobileOverflow.innerWidth);
  await expect(copyAccessCode).toBeVisible();
  await expect(surveyLink).toBeVisible();
  await recoveryPage.setViewportSize({ width: 1440, height: 1000 });

  const phaseFourUpdate = await page.request.post("/admin/update.php", {
    data: { ...assignment, phaseOverride: 4 },
  });
  expect(phaseFourUpdate.status()).toBe(200);
  await recoveryPage.reload();
  await expect(recoveryPage.getByRole("heading", { name: "Vielen Dank für Deine Teilnahme" })).toBeVisible();
  await expect(recoveryPage.locator("[data-results-interest-form]")).toBeVisible();
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("RECOVERY-CODE-141");
  await expect(recoveryPage.locator("[data-participant-section]")).not.toContainText("10:30 - 11:45 Uhr");
  await expect(recoveryPage.getByRole("button", { name: "Zugangscode kopieren" })).toHaveCount(0);
  await expect(recoveryPage.getByRole("link", { name: "Umfrage in neuem Tab öffnen" })).toHaveCount(0);
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
  await expect(closedPage.getByRole("button", { name: "Mitmachen" })).toBeVisible();
  await expect(closedPage.getByRole("button", { name: "Mitmachen" })).toBeDisabled();
  await expect(closedPage.getByRole("button", { name: "Bereits angemeldet?" })).toBeVisible();

  const closedSessionButton = closedPage.locator("[data-participant-session-action]");
  await expect(closedSessionButton).toHaveAttribute("aria-label", "Anmeldung aufrufen");
  await closedSessionButton.click();
  await expect(closedPage.locator("[data-recovery-dialog]")).toBeVisible();
  await closedPage.getByLabel("E-Mail-Adresse", { exact: true }).last().fill("RECOVERY.PERSON@EXAMPLE.COM");
  await closedPage.getByLabel("Geburtsdatum", { exact: true }).last().fill("1991-06-14");
  await closedPage.locator("[data-recovery-dialog]")
    .getByRole("button", { name: "Anmeldung aufrufen" })
    .click();
  await expect(closedPage.getByRole("heading", { name: "Vielen Dank für Deine Teilnahme" })).toBeVisible();

  await page.goto("/admin/");
  await page.getByLabel("Suche").fill("recovery.person@example.com");
  await expect(page.locator("[data-table-body]")).toContainText("Nein");
  await expect(page.locator("[data-table-body]")).not.toContainText("Noch nicht beantwortet");

  await closedContext.close();
  await recoveryContext.close();
});
