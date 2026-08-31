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
