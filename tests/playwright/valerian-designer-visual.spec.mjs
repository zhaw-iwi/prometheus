import { expect, test } from "@playwright/test";
import { installDesignerApiMock, VISUAL_KEY } from "./support/designer-api-mock.mjs";

const TOKEN = "visual-test-token";

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

test("all six guided panels, graph/list, prompt adoption, and keyboard focus remain coherent", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const scenario = await installDesignerApiMock(page);
  await openFixture(page);
  await page.getByTestId("step-target-review").click();
  const represented = JSON.parse(await page.getByTestId("canonical-json-editor").inputValue());
  expect(represented).toEqual(scenario.definition);

  const steps = ["purpose", "sensing", "behaviour", "reactions", "state-flow", "review"];
  for (const step of steps) {
    await page.getByTestId(`step-target-${step}`).click();
    await expect(page.getByTestId(`step-panel-${step}`)).toBeVisible();
    await attach(page.getByTestId(`step-panel-${step}`), testInfo, `step-${step}-light`);
  }

  await page.getByTestId("step-target-purpose").click();
  await page.getByTestId("example-purpose.persona").locator("summary").click();
  const before = await page.getByTestId("prompt-purpose-persona").inputValue();
  await page.getByTestId("adopt-example-purpose.persona").click();
  await expect(page.getByTestId("prompt-purpose-persona")).not.toHaveValue(before);

  await page.getByTestId("step-target-state-flow").click();
  await expect(page.getByTestId("state-graph")).toBeVisible();
  await attach(page.getByTestId("step-panel-state-flow"), testInfo, "state-flow-graph-light");
  await page.getByTestId("show-list-view").click();
  await expect(page.getByTestId("state-flow-list")).toContainText("Moves in source priority order");
  await attach(page.getByTestId("step-panel-state-flow"), testInfo, "state-flow-list-light");

  await page.getByTestId("step-target-purpose").click();
  await page.getByTestId("step-target-purpose").focus();
  await page.keyboard.press("Tab");
  await expect(page.getByTestId("step-target-sensing")).toBeFocused();
  await expectVisibleFocus(page.getByTestId("step-target-sensing"));
  const next = page.getByTestId("step-next-purpose");
  await next.focus();
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Tab");
  await expect(next).toBeFocused();
  await expectVisibleFocus(next);
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("Review links diagnostics, synchronizes JSON, previews, and confirms publication", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const scenario = await installDesignerApiMock(page);
  scenario.readinessDiagnostics = true;
  await openFixture(page);
  await page.getByTestId("step-target-review").click();
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("diagnostic-group-purpose")).toBeVisible();
  await expect(page.getByTestId("diagnostic-group-state-flow")).toBeVisible();
  await attach(page, testInfo, "review-diagnostics-light");

  await page.getByTestId("diagnostic-group-purpose").locator("button").first().click();
  await expect(page.getByTestId("step-panel-purpose")).toBeVisible();
  await expect(page.locator("#purpose-display-name")).toBeFocused();
  await page.getByTestId("step-target-review").click();

  const jsonEditor = page.getByTestId("canonical-json-editor");
  await jsonEditor.fill('{"broken":');
  await page.getByTestId("apply-canonical-json").click();
  await expect(page.getByTestId("json-message")).toContainText("guided form was not changed");
  const nextDefinition = structuredClone(scenario.definition);
  nextDefinition.metadata.description = "Updated safely through the canonical JSON editor.";
  await jsonEditor.fill(JSON.stringify(nextDefinition, null, 2));
  await page.getByTestId("apply-canonical-json").click();
  await expect(page.getByTestId("json-message")).toContainText("JSON applied to the guided form");
  await page.getByTestId("save-draft").click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");

  scenario.readinessDiagnostics = false;
  await page.getByTestId("validate-review").click();
  await expect(page.getByTestId("review-validation-state")).toContainText("current for this exact document");
  await expect(page.getByText("[context]\nOuter policy.")).toBeVisible();

  await page.getByTestId("start-preview").click();
  await page.getByRole("button", { name: "obs.user_utterance" }).click();
  await page.getByTestId("preview-event-payload").fill("Preview this deterministic turn.");
  await page.getByTestId("send-preview-event").click();
  await expect(page.getByTestId("preview-transcript")).toContainText("repeat");
  await page.getByTestId("generate-preview").click();
  await expect(page.getByTestId("preview-transcript")).toContainText("Deterministic preview response.");

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
  const download = page.waitForEvent("download");
  await page.getByTestId("export-revision").click();
  expect((await download).suggestedFilename()).toBe(`${VISUAL_KEY}-revision-1.json`);
  await attach(page, testInfo, "review-preview-published-light");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("dark mobile stacks the stepper and keeps graph/list and Review within the viewport", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ colorScheme: "dark", reducedMotion: "reduce" });
  await installDesignerApiMock(page);
  await openFixture(page);
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  const first = await page.getByTestId("step-target-purpose").boundingBox();
  const second = await page.getByTestId("step-target-sensing").boundingBox();
  expect(first).not.toBeNull();
  expect(second).not.toBeNull();
  expect(second.y).toBeGreaterThanOrEqual(first.y + first.height - 1);
  const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(background).toMatch(/rgb\((?:13|14), (?:23|24), (?:22|23)\)/);

  await page.getByTestId("step-target-state-flow").click();
  await page.getByTestId("show-list-view").click();
  await attach(page, testInfo, "state-flow-list-dark-mobile");
  await assertNoOverflow(page);
  await page.getByTestId("step-target-review").click();
  await expect(page.getByTestId("review-panel")).toBeVisible();
  await attach(page, testInfo, "review-dark-mobile");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

async function openFixture(page) {
  await page.addInitScript(({ token }) => sessionStorage.setItem("prometheus.valerianAdmin.adminToken", token), { token: TOKEN });
  await page.goto("/valerian-design/");
  await expect(page.getByTestId("catalog-populated")).toBeVisible();
  await page.getByTestId(`open-definition-${VISUAL_KEY}`).click();
  await expect(page.getByTestId("designer-editor")).toBeVisible();
}

async function enter(page) {
  await page.getByTestId("designer-token-input").fill(TOKEN);
  await page.getByTestId("submit-designer-token").click();
}

async function attach(target, testInfo, name) {
  const locator = typeof target.page === "function";
  const path = testInfo.outputPath(`${name}.png`);
  if (locator) {
    await target.screenshot({ path, animations: "disabled" });
  } else {
    await target.screenshot({ path, fullPage: true, animations: "disabled" });
  }
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
