import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect, test } from "@playwright/test";
import { installDesignerApiMock, VISUAL_KEY } from "./support/designer-api-mock.mjs";

const TOKEN = "visual-test-token";
const CATALOG_ROOT = resolve("src/main/resources/agent-definitions/catalog/main");

test("catalog renders deterministic loading and populated states", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  await installDesignerApiMock(page, { catalogDelay: 450 });
  await page.goto("/valerian-design/");
  await expect(page.getByTestId("designer-token-panel")).toBeVisible();
  await attach(page, testInfo, "catalog-locked-light");
  await enter(page);
  await expect(page.getByTestId("catalog-loading")).toBeVisible();
  await attach(page, testInfo, "catalog-loading-light");
  await expect(page.getByTestId("catalog-populated")).toBeVisible();
  await expect(page.getByText("Visual acceptance agent")).toBeVisible();
  await attach(page, testInfo, "catalog-populated-light");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("catalog renders deterministic error and empty states", async ({ browser }, testInfo) => {
  for (const mode of ["error", "empty"]) {
    const context = await browser.newContext({ viewport: { width: 1120, height: 820 }, colorScheme: "light" });
    const page = await context.newPage();
    const errors = collectPageErrors(page);
    await installDesignerApiMock(page, { catalogMode: mode });
    await page.goto("http://127.0.0.1:4175/valerian-design/");
    await enter(page);
    await expect(page.getByTestId(mode === "error" ? "catalog-error" : "catalog-empty")).toBeVisible();
    await attach(page, testInfo, `catalog-${mode}-light`);
    await assertNoOverflow(page);
    expect(mode === "error" ? errors.filter((message) => !message.includes("status of 503")) : errors).toEqual([]);
    await context.close();
  }
});

test("the exact six-step V2 shell projects canonical JSON without becoming dirty", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const scenario = await installDesignerApiMock(page);
  await openFixture(page, VISUAL_KEY);

  const steps = ["brief", "capabilities", "interaction", "data-outcome", "try", "review"];
  const titles = ["Brief", "Capabilities", "Interaction", "Data & outcome", "Try", "Review"];
  await expect(page.getByRole("tab")).toHaveText(titles.map((title) => new RegExp(title)));
  for (const step of steps) {
    await page.getByTestId(`step-target-${step}`).click();
    await expect(page.getByTestId(`step-panel-${step}`)).toBeVisible();
    await attach(page.getByTestId(`step-panel-${step}`), testInfo, `v2-step-${step}-light`);
  }

  const represented = JSON.parse(await page.getByTestId("canonical-json-editor").inputValue());
  expect(represented).toEqual(scenario.definition);
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");

  await page.getByTestId("step-target-brief").click();
  await page.getByTestId("step-target-brief").focus();
  await page.keyboard.press("Tab");
  await expect(page.getByTestId("step-target-capabilities")).toBeFocused();
  await expectVisibleFocus(page.getByTestId("step-target-capabilities"));
  const next = page.getByTestId("step-next-brief");
  await next.focus();
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Tab");
  await expect(next).toBeFocused();
  await expectVisibleFocus(next);
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("Review links V2 diagnostics, synchronizes JSON, and retains lifecycle gates", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const scenario = await installDesignerApiMock(page);
  scenario.readinessDiagnostics = true;
  await openFixture(page, VISUAL_KEY);
  await page.getByTestId("step-target-review").click();
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("diagnostic-group-brief")).toBeVisible();
  await expect(page.getByTestId("diagnostic-group-interaction")).toBeVisible();
  await attach(page, testInfo, "v2-review-diagnostics-light");

  await page.getByTestId("diagnostic-group-brief").locator("button").first().click();
  await expect(page.getByTestId("step-panel-brief")).toBeVisible();
  await expect(page.locator("#brief-display-name")).toBeFocused();
  await page.getByTestId("step-target-review").click();

  const jsonEditor = page.getByTestId("canonical-json-editor");
  await jsonEditor.fill('{"broken":');
  await page.getByTestId("apply-canonical-json").click();
  await expect(page.getByTestId("json-message")).toContainText("projected document was not changed");
  const nextDefinition = structuredClone(scenario.definition);
  nextDefinition.metadata.description = "Updated safely through the canonical JSON editor.";
  await jsonEditor.fill(JSON.stringify(nextDefinition, null, 2));
  await page.getByTestId("apply-canonical-json").click();
  await expect(page.getByTestId("json-message")).toContainText("JSON applied to the V2 projection");
  await page.getByTestId("save-draft").click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");

  scenario.readinessDiagnostics = false;
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("review-validation-state")).toContainText("current for this exact document");
  await expect(page.getByText("[context]\nOuter policy.")).toBeVisible();

  page.once("dialog", async (dialog) => {
    expect(dialog.message()).toContain("canonical JSON becomes immutable");
    await dialog.accept();
  });
  await page.getByTestId("publish-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("published and immutable");
  page.once("dialog", async (dialog) => {
    expect(dialog.message()).toContain("Existing instances remain pinned");
    await dialog.accept();
  });
  await page.getByTestId("activate-revision").click();
  await expect(page.getByTestId("lifecycle-message")).toContainText("new instances only");
  await attach(page, testInfo, "v2-review-published-light");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("prompt, exact-text, RPS, and healthcare revisions export unchanged after opening", async ({ browser }) => {
  const resources = [
    "core/multimodal_behaviour/revision-1.json",
    "core/talk_to_me/revision-1.json",
    "core/rock_scissor_paper/revision-1.json",
    "usecases/healthcare/therapy_appointment_reminder_intro/revision-1.json",
  ];
  for (const resource of resources) {
    const definition = JSON.parse(readFileSync(resolve(CATALOG_ROOT, resource), "utf8"));
    const context = await browser.newContext({ acceptDownloads: true });
    const page = await context.newPage();
    const errors = collectPageErrors(page);
    await installDesignerApiMock(page, { definition });
    await openFixture(page, definition.key);
    await page.getByTestId("step-target-review").click();
    expect(JSON.parse(await page.getByTestId("canonical-json-editor").inputValue())).toEqual(definition);
    await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
    const download = page.waitForEvent("download");
    await page.getByTestId("export-revision").click();
    expect((await download).suggestedFilename()).toBe(`${definition.key}-revision-1.json`);
    expect(errors).toEqual([]);
    await context.close();
  }
});

test("390-pixel mobile stacks the V2 stepper and keeps projected panels within the viewport", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ colorScheme: "dark", reducedMotion: "reduce" });
  await installDesignerApiMock(page);
  await openFixture(page, VISUAL_KEY);
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  const first = await page.getByTestId("step-target-brief").boundingBox();
  const second = await page.getByTestId("step-target-capabilities").boundingBox();
  expect(first).not.toBeNull();
  expect(second).not.toBeNull();
  expect(second.y).toBeGreaterThanOrEqual(first.y + first.height - 1);
  const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(background).toMatch(/rgb\((?:13|14), (?:23|24), (?:22|23)\)/);

  await page.getByTestId("step-target-interaction").click();
  await attach(page, testInfo, "v2-interaction-dark-mobile");
  await assertNoOverflow(page);
  await page.getByTestId("step-target-data-outcome").click();
  await attach(page, testInfo, "v2-data-outcome-dark-mobile");
  await assertNoOverflow(page);
  await page.getByTestId("step-target-review").click();
  await expect(page.getByTestId("review-panel")).toBeVisible();
  await attach(page, testInfo, "v2-review-dark-mobile");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

async function openFixture(page, key) {
  await page.addInitScript(({ token }) => sessionStorage.setItem("prometheus.valerianAdmin.adminToken", token), { token: TOKEN });
  await page.goto("/valerian-design/");
  await expect(page.getByTestId("catalog-populated")).toBeVisible();
  await page.getByTestId(`open-definition-${key}`).click();
  await expect(page.getByTestId("designer-editor")).toBeVisible();
}

async function enter(page) {
  await page.getByTestId("designer-token-input").fill(TOKEN);
  await page.getByTestId("submit-designer-token").click();
}

async function attach(target, testInfo, name) {
  const locator = typeof target.page === "function";
  const path = testInfo.outputPath(`${name}.png`);
  if (locator) await target.screenshot({ path, animations: "disabled" });
  else await target.screenshot({ path, fullPage: true, animations: "disabled" });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function assertNoOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
}

async function expectVisibleFocus(locator) {
  const focus = await locator.evaluate((element) => {
    const style = getComputedStyle(element);
    return { width: Number.parseFloat(style.outlineWidth), style: style.outlineStyle };
  });
  expect(focus.width).toBeGreaterThanOrEqual(2);
  expect(focus.style).not.toBe("none");
}

function collectPageErrors(page) {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
  return errors;
}
