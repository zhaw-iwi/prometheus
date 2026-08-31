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

async function completeBrief(page, name, purpose, key) {
  await page.getByLabel("Agent name").fill(name);
  await page.getByLabel("Purpose, audience, and setting").fill(purpose);
  await page.getByLabel("Stable key").fill(key);
  await page.getByTestId("confirm-stable-key").click();
  await expect(page.getByTestId("confirm-stable-key")).toHaveText("Key confirmed");
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
