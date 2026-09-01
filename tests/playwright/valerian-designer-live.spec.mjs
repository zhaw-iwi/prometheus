import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect, test } from "@playwright/test";

const ADMIN_TOKEN = "designer-live-admin-token";

test("packaged Designer completes a deterministic lifecycle on dedicated MySQL", async ({ page }, testInfo) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  const definition = JSON.parse(readFileSync(
    resolve("src/main/resources/agent-definitions/catalog/main/core/talk_to_me/revision-1.json"), "utf8"));
  const suffix = Date.now();
  const key = `designer.live_acceptance_${suffix}`;
  definition.key = key;
  definition.metadata.displayName = "Packaged designer acceptance";
  definition.metadata.description = "Dedicated MySQL lifecycle fixture; removed with its test schema.";
  definition.metadata.categoryPath = "designer.acceptance";

  await page.goto("/valerian-design/");
  await page.getByTestId("designer-token-input").fill(ADMIN_TOKEN);
  await page.getByTestId("submit-designer-token").click();
  await expect(page.getByTestId("catalog-populated")).toBeVisible();
  await page.getByTestId("show-import-definition").click();
  await page.getByTestId("import-definition-json").fill(JSON.stringify(definition, null, 2));
  await page.getByTestId("import-definition").click();
  await expect(page.getByTestId("designer-editor")).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`definitions/${key.replaceAll(".", "\\.")}/revisions/1`));

  await page.getByTestId("step-target-review").click();
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("review-validation-state")).toContainText("current for this exact document");

  await page.getByTestId("start-preview").click();
  await page.getByLabel("Event templates").getByRole("button", { name: "What the person says" }).click();
  const utterance = "Packaged deterministic preview response.";
  await page.getByTestId("preview-event-payload").fill(utterance);
  await page.getByTestId("send-preview-event").click();
  await expect(page.getByTestId("preview-transcript")).toContainText("Accepted moves: repeat");
  await page.getByTestId("generate-preview").click();
  await expect(page.getByTestId("preview-transcript")).toContainText(utterance);
  await expect(page.getByTestId("preview-workspace")).toContainText("talk");
  await page.getByTestId("preview-transcript").locator("summary").click();
  await expect(page.getByTestId("preview-transcript")).toContainText("{}");

  await acceptDialog(page, "canonical JSON becomes immutable", () => page.getByTestId("publish-revision").click());
  await expect(page.getByTestId("lifecycle-message")).toContainText("published and immutable");
  await acceptDialog(page, "Existing instances remain pinned", () => page.getByTestId("activate-revision").click());
  await expect(page.getByTestId("lifecycle-message")).toContainText("new instances only");

  const downloadPromise = page.waitForEvent("download");
  await page.getByTestId("export-revision").click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe(`${key}-revision-1.json`);
  const exported = JSON.parse(readFileSync(await download.path(), "utf8"));
  expect(exported).toEqual(definition);

  await page.getByTestId("close-preview").click();
  await expect(page.getByTestId("preview-workspace")).toHaveCount(0);
  const evidence = testInfo.outputPath("packaged-designer-lifecycle.png");
  await page.screenshot({ path: evidence, fullPage: true, animations: "disabled" });
  await testInfo.attach("packaged-designer-lifecycle", { path: evidence, contentType: "image/png" });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  expect(errors).toEqual([]);
});

async function acceptDialog(page, text, action) {
  page.once("dialog", async (dialog) => {
    expect(dialog.message()).toContain(text);
    await dialog.accept();
  });
  await action();
}
