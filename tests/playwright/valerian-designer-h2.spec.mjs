import { expect, test } from "@playwright/test";

const ADMIN_TOKEN = "designer-h2-admin-token";

test("creates and reloads guided prompt and exact-text agents on isolated H2", async ({ page }) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  const suffix = Date.now();
  const promptKey = `designer.h2_prompt_${suffix}`;
  const exactKey = `designer.h2_exact_${suffix}`;

  await page.goto("/valerian-design/");
  await page.getByTestId("designer-token-input").fill(ADMIN_TOKEN);
  await page.getByTestId("submit-designer-token").click();
  await expect(page.getByTestId("catalog-populated")).toBeVisible();

  await page.getByTestId("create-definition").click();
  await completeBrief(page, "H2 prompt guide", "Helps a visitor choose a practical next step.", promptKey);
  await page.getByRole("button", { name: "Add card" }).click();
  await page.getByTestId("step-target-capabilities").click();
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Speak");
  await page.getByTestId("save-draft").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${promptKey.replaceAll(".", "\\.")}/revisions/1`));
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  await reopen(page, promptKey);
  await expect(page.getByLabel("Agent name")).toHaveValue("H2 prompt guide");
  await expect(page.getByTestId("guidance-card-objective")).toBeVisible();
  await page.getByTestId("step-target-capabilities").click();
  await expect(page.getByText("What the person says", { exact: true }).locator("xpath=ancestor::article").getByRole("checkbox")).toBeChecked();
  await expect(page.getByTestId("strategy-card-prompt-response")).toContainText("Used by Main");

  await page.getByTestId("back-to-catalog").click();
  await page.getByTestId("create-definition").click();
  await completeBrief(page, "H2 exact repeater", "Repeats a submitted utterance exactly without model generation.", exactKey);
  await page.getByTestId("step-target-capabilities").click();
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Speak");
  await page.getByTestId("strategy-card-exact-text-response").getByRole("button", { name: "Use for Main" }).click();
  await page.getByLabel("Maximum characters").fill("480");
  await page.getByTestId("save-draft").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${exactKey.replaceAll(".", "\\.")}/revisions/1`));
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  await reopen(page, exactKey);
  await page.getByTestId("step-target-capabilities").click();
  await expect(page.getByTestId("strategy-card-exact-text-response")).toContainText("Used by Main");
  await expect(page.getByLabel("Maximum characters")).toHaveValue("480");
  await expect(page.getByTestId("strategy-card-exact-text-response").getByText("prometheus.policy.exact-text")).not.toBeVisible();
  expect(errors).toEqual([]);
});

test("authors and validates orthogonal sensing stay rules on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_orthogonal_${Date.now()}`;
  await startNewAgent(page, "Orthogonal sensing guide", "Handles conversation and facial cues independently without changing phase.", key);
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Facial emotion cues");
  await chooseCapability(page, "Speak");
  await page.getByTestId("step-target-interaction").click();
  const main = page.getByTestId("situation-card-main");
  await main.getByLabel("When").selectOption("obs.user_utterance");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  await main.getByLabel("When").selectOption("obs.emotion.face");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  await expect(main.locator(".interaction-rule-card")).toHaveCount(2);
  await saveAndValidate(page, key);
  expect(errors).toEqual([]);
});

test("authors and validates a meaning-based role branch on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_role_branch_${Date.now()}`;
  await startNewAgent(page, "Role branch guide", "Chooses between guide and visitor roles from an explicit answer.", key);
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Speak");
  await page.getByTestId("step-target-interaction").click();
  await addSituation(page, "Guide asks");
  await addSituation(page, "Visitor asks");
  const main = page.getByTestId("situation-card-main");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  const rules = main.locator(".interaction-rule-card");
  await rules.nth(0).getByRole("button", { name: "Add condition" }).click();
  await rules.nth(0).getByLabel("Decision criterion").fill("The person chooses that the agent should ask the questions.");
  await rules.nth(0).getByLabel("Rule continuation").selectOption({ label: "Continue in Guide asks" });
  await rules.nth(1).getByRole("button", { name: "Add condition" }).click();
  await rules.nth(1).getByLabel("Decision criterion").fill("The person chooses to ask the questions themselves.");
  await rules.nth(1).getByLabel("Rule continuation").selectOption({ label: "Continue in Visitor asks" });
  await saveAndValidate(page, key);
  expect(errors).toEqual([]);
});

test("authors and validates a two-situation healthcare interaction on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_healthcare_${Date.now()}`;
  await startNewAgent(page, "Appointment preparation guide", "Helps a patient prepare questions and then confirms a safe hand-off to their clinician.", key);
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Speak");
  await page.getByTestId("step-target-interaction").click();
  await addSituation(page, "Confirm hand-off");
  const main = page.getByTestId("situation-card-main");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  await main.locator(".interaction-rule-card").getByRole("button", { name: "Add condition" }).click();
  await main.locator(".interaction-rule-card").getByLabel("Decision criterion").fill("The patient has listed the questions they want to discuss.");
  await main.locator(".interaction-rule-card").getByLabel("Rule continuation").selectOption({ label: "Continue in Confirm hand-off" });
  const handoff = page.getByTestId("situation-card-confirm-hand-off");
  await handoff.getByRole("button", { name: "Add card" }).click();
  await handoff.getByRole("button", { name: "Add interaction rule" }).click();
  await handoff.locator(".interaction-rule-card").getByLabel("Rule continuation").selectOption("finish");
  await saveAndValidate(page, key);
  expect(errors).toEqual([]);
});

test("clones, edits, and validates the registered RPS cycle without JSON editing on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_rps_${Date.now()}`;
  await enterCatalog(page);
  await page.getByTestId("open-definition-core.rock_scissor_paper").click();
  await expect(page.getByTestId("designer-editor")).toBeVisible();
  await page.getByTestId("step-target-review").click();
  await page.getByLabel("Clone target key").fill(key);
  await page.getByLabel("Target revision").fill("1");
  await page.getByTestId("clone-revision").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/1`));
  await page.getByTestId("step-target-interaction").click();
  await expect(page.getByTestId("derived-flow-overview")).toContainText("Valerian Core RPS Reveal Sign");
  await expect(page.getByTestId("derived-flow-overview")).toContainText("Valerian Core RPS Round Result");
  await page.getByLabel("Main name").fill("RPS round start");
  await saveAndValidate(page, key);
  expect(errors).toEqual([]);
});

test("edits, saves, reloads, and validates therapy typed context on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_therapy_context_${Date.now()}`;
  await enterCatalog(page);
  await page.getByTestId("open-definition-usecases.healthcare.therapy_appointment_reminder").click();
  await cloneCurrentRevision(page, key);
  await page.getByTestId("step-target-data-outcome").click();
  const choices = page.getByTestId("typed-choices-therapyAppointmentContext");
  await choices.getByLabel("Safe Focus").first().fill("safe walking, balance, and mobility practice");
  await saveAndValidate(page, key);
  await reopen(page, key);
  await page.getByTestId("step-target-data-outcome").click();
  await expect(page.getByTestId("typed-choices-therapyAppointmentContext").getByLabel("Safe Focus").first())
    .toHaveValue("safe walking, balance, and mobility practice");
  await expect(page.getByTestId("custom-outcome-outcome")).toBeVisible();
  expect(errors).toEqual([]);
});

test("edits, saves, reloads, and validates custom SMART outcome rules on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_smart_outcome_${Date.now()}`;
  await enterCatalog(page);
  await page.getByTestId("open-definition-usecases.healthcare.smart_goal_coaching").click();
  await cloneCurrentRevision(page, key);
  await page.getByTestId("step-target-data-outcome").click();
  const custom = page.getByTestId("custom-outcome-outcome");
  await custom.getByLabel("Instruction").first().fill(
    "Extract the completed SMART goal interaction as valid JSON only, using conversation evidence.",
  );
  await saveAndValidate(page, key);
  await reopen(page, key);
  await page.getByTestId("step-target-data-outcome").click();
  await expect(page.getByTestId("custom-outcome-outcome").getByLabel("Instruction").first()).toHaveValue(
    "Extract the completed SMART goal interaction as valid JSON only, using conversation evidence.",
  );
  expect(errors).toEqual([]);
});

test("runs an unsaved exact-text scenario through the isolated H2 application without persistence leakage", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_try_exact_${Date.now()}`;
  await enterCatalog(page);
  await page.getByTestId("open-definition-core.talk_to_me").click();
  await cloneCurrentRevision(page, key);
  await page.getByTestId("step-target-try").click();
  await page.getByRole("button", { name: "Add scenario" }).click();
  const scenario = page.getByTestId("scenario-card-0");
  await scenario.getByLabel("Scenario 1 name").fill("Exact H2 echo");
  await scenario.getByRole("button", { name: "What the person says" }).click();
  await scenario.getByLabel("Event payload").fill("H2 exact hello");
  await scenario.getByLabel("Active situation after all events (optional)").selectOption("talk");
  await scenario.getByRole("button", { name: "Add behaviour expectation" }).click();
  await scenario.getByLabel("Behaviour fragment 1 JSON value").fill('{"speech":"H2 exact hello"}');
  await scenario.getByRole("button", { name: "Apply JSON value" }).click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Unsaved changes");

  const revisionUrl = `/admin/agent-definitions/${key}/revisions/1`;
  const headers = { "X-Prometheus-Admin-Token": ADMIN_TOKEN };
  const before = await (await page.request.get(revisionUrl, { headers })).json();
  await scenario.getByTestId("run-scenario-0").click();
  await expect(scenario.getByText("All expectations passed")).toBeVisible();
  await expect(scenario.getByText(/disposable runtime session has been discarded/i)).toBeVisible();
  await expect(page.getByTestId("dirty-state")).toHaveText("Unsaved changes");
  const after = await (await page.request.get(revisionUrl, { headers })).json();
  expect(after).toEqual(before);

  await page.getByTestId("save-draft").click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  await reopen(page, key);
  await page.getByTestId("step-target-try").click();
  await expect(page.getByTestId("scenario-result-0")).toHaveCount(0);
  await page.getByTestId("run-scenario-0").click();
  await expect(page.getByText("All expectations passed")).toBeVisible();
  await page.getByRole("button", { name: "Clear result" }).click();
  await expect(page.getByTestId("scenario-result-0")).toHaveCount(0);
  expect(errors).toEqual([]);
});

test("completes create, save, validate, Try, free preview, export, publish, activate, clone, and archive on isolated H2", async ({ page }) => {
  const errors = collectErrors(page);
  const key = `designer.h2_review_lifecycle_${Date.now()}`;
  await startNewAgent(page, "H2 lifecycle repeater", "Repeats a test message through the complete review and revision lifecycle.", key);
  await chooseCapability(page, "What the person says");
  await chooseCapability(page, "Speak");
  await page.getByTestId("strategy-card-exact-text-response").getByRole("button", { name: "Use for Main" }).click();
  await page.getByTestId("step-target-interaction").click();
  const main = page.getByTestId("situation-card-main");
  await main.getByLabel("When").selectOption("obs.user_utterance");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  await expect(main.locator(".interaction-rule-card")).toHaveCount(1);
  await page.getByTestId("save-draft").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/1`));

  await page.getByTestId("step-target-try").click();
  await page.getByRole("button", { name: "Add scenario" }).click();
  const authoredScenario = page.getByTestId("scenario-card-0");
  await authoredScenario.getByLabel("Scenario 1 name").fill("Lifecycle echo");
  await authoredScenario.getByRole("button", { name: "What the person says" }).click();
  await authoredScenario.getByLabel("Event payload").fill("H2 lifecycle hello");
  await authoredScenario.getByLabel("Active situation after all events (optional)").selectOption("main");
  await authoredScenario.getByRole("button", { name: "Add behaviour expectation" }).click();
  await authoredScenario.getByLabel("Behaviour fragment 1 JSON value").fill('{"speech":"H2 lifecycle hello"}');
  await authoredScenario.getByRole("button", { name: "Apply JSON value" }).click();
  await authoredScenario.getByTestId("run-scenario-0").click();
  await expect(authoredScenario.getByText("All expectations passed")).toBeVisible();
  await page.getByTestId("save-draft").click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");

  await page.getByTestId("step-target-review").click();
  await expect(page.getByTestId("review-narrative")).toContainText("exact text responses");
  await page.getByTestId("advanced-review").locator("summary").first().click();
  await expect(page.getByTestId("advanced-flow-graph").locator("[data-state-id='main']")).toBeVisible();
  await page.getByTestId("start-preview").click();
  await page.getByLabel("Event templates").getByRole("button", { name: "What the person says" }).click();
  await page.getByTestId("preview-event-payload").fill("H2 lifecycle free preview");
  await page.getByTestId("send-preview-event").click();
  await expect(page.getByTestId("preview-transcript")).toContainText("H2 lifecycle free preview");
  await page.getByTestId("close-preview").click();
  await expect(page.getByTestId("preview-message")).toContainText("in-memory state was removed");
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("review-validation-state")).toContainText("current for this exact document");

  const download = page.waitForEvent("download");
  await page.getByTestId("export-revision").click();
  expect((await download).suggestedFilename()).toBe(`${key}-revision-1.json`);
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("publish-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("published and immutable");
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("activate-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("new instances only");

  await page.getByLabel("Clone target key").fill(key);
  await page.getByLabel("Target revision").fill("2");
  await page.getByTestId("clone-revision").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/2`));
  await page.getByTestId("step-target-review").click();
  await page.getByTestId("validate-review").click();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("publish-revision").click();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("activate-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("new instances only");

  await page.goto(`/valerian-design/#/definitions/${key}/revisions/1`);
  await expect(page.getByTestId("designer-editor")).toBeVisible();
  await page.getByTestId("step-target-review").click();
  await expect(page.getByTestId("archive-revision")).toBeEnabled();
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByTestId("archive-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("archived");

  const headers = { "X-Prometheus-Admin-Token": ADMIN_TOKEN };
  const revisionOne = await (await page.request.get(`/admin/agent-definitions/${key}/revisions/1`, { headers })).json();
  const catalog = await (await page.request.get("/admin/agent-definitions", { headers })).json();
  expect(revisionOne.status).toBe("ARCHIVED");
  expect(catalog.find((definition) => definition.key === key)?.activeRevision).toBe(2);
  expect(errors).toEqual([]);
});

async function completeBrief(page, name, purpose, key) {
  await page.getByLabel("Agent name").fill(name);
  await page.getByLabel("Purpose, audience, and setting").fill(purpose);
  await page.getByLabel("Stable key").fill(key);
  await page.getByTestId("confirm-stable-key").click();
  await expect(page.getByTestId("confirm-stable-key")).toHaveText("Key confirmed");
}

async function startNewAgent(page, name, purpose, key) {
  await enterCatalog(page);
  await page.getByTestId("create-definition").click();
  await completeBrief(page, name, purpose, key);
  await page.getByTestId("step-target-capabilities").click();
}

async function enterCatalog(page) {
  await page.goto("/valerian-design/");
  const token = page.getByTestId("designer-token-input");
  if (await token.isVisible()) {
    await token.fill(ADMIN_TOKEN);
    await page.getByTestId("submit-designer-token").click();
  }
  await expect(page.getByTestId("catalog-populated")).toBeVisible();
}

async function addSituation(page, name) {
  await page.getByLabel("Situation name").last().fill(name);
  await page.getByRole("button", { name: "Add situation" }).click();
  await expect(page.locator(`input.situation-name-input[value="${name}"]`)).toBeVisible();
}

async function saveAndValidate(page, key) {
  await page.getByTestId("save-draft").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/1`));
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  await page.getByTestId("step-target-review").click();
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("review-validation-state")).toContainText("current for this exact document");
}

async function cloneCurrentRevision(page, key) {
  await page.getByTestId("step-target-review").click();
  await page.getByLabel("Clone target key").fill(key);
  await page.getByLabel("Target revision").fill("1");
  await page.getByTestId("clone-revision").click();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/1`));
}

function collectErrors(page) {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  return errors;
}

async function chooseCapability(page, label) {
  const card = page.getByText(label, { exact: true }).locator("xpath=ancestor::article");
  const checkbox = card.getByRole("checkbox");
  if (!(await checkbox.isChecked())) await page.getByText(label, { exact: true }).click();
  await expect(checkbox).toBeChecked();
}

async function reopen(page, key) {
  await page.getByTestId("back-to-catalog").click();
  await expect(page.getByTestId(`open-definition-${key}`)).toBeVisible();
  await page.getByTestId(`open-definition-${key}`).click();
  await expect(page.getByTestId("designer-editor")).toBeVisible();
}
