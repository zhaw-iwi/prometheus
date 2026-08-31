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

test("Brief edits identity and adopts long ordered guidance only after explicit action", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  await installDesignerApiMock(page);
  await openFixture(page, VISUAL_KEY);
  await expect(page.getByTestId("brief-authoring")).toBeVisible();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");

  const example = page.getByTestId("guidance-example-helpful-guide");
  await example.locator("summary").click();
  await expect(example.getByText("Identity and role", { exact: false })).toBeVisible();
  await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
  await page.getByRole("button", { name: "Refresh preview" }).click();
  await expect(page.locator(".prompt-preview pre")).toHaveText("[context]\nOuter policy.");
  await page.getByLabel("Agent name").fill("Reception companion");
  await expect(page.getByText("The shown preview predates the latest edits.", { exact: false })).toBeVisible();
  await example.getByRole("button", { name: "Use as starting point" }).click();
  await expect(page.getByTestId("dirty-state")).toHaveText("Unsaved changes");
  await expect(page.getByTestId("step-panel-brief").locator(".guidance-card")).toHaveCount(7);
  const moveLater = page.getByRole("button", { name: "Move guidance later" }).first();
  await moveLater.focus();
  await page.keyboard.press("Tab");
  await page.keyboard.press("Shift+Tab");
  await expect(moveLater).toBeFocused();
  await expectVisibleFocus(moveLater);
  await attach(page.getByTestId("step-panel-brief"), testInfo, "v2-brief-long-guidance-light");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("Capabilities declares availability, shows usage, selects exact text, and groups RPS", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const scenario = await installDesignerApiMock(page);
  const originalStates = structuredClone(scenario.definition.states);
  const originalTransitions = structuredClone(scenario.definition.transitions);
  await openFixture(page, VISUAL_KEY);
  await page.getByTestId("step-target-capabilities").click();
  await expect(page.getByTestId("capabilities-authoring")).toBeVisible();
  await expect(page.getByText("Used in 1 configured place").first()).toBeVisible();

  await page.getByText("Facial emotion cues", { exact: true }).click();
  const facialCard = page.getByText("Facial emotion cues", { exact: true }).locator("xpath=ancestor::article");
  await expect(facialCard.getByText("Declared but not used.")).toBeVisible();
  await expect(page.getByTestId("strategy-card-exact-text-response")).toContainText("Repeat exact text");
  await page.getByTestId("strategy-card-exact-text-response").getByRole("button", { name: "Use for Main" }).click();
  await expect(page.getByTestId("exact-text-settings")).toBeVisible();
  await expect(page.getByTestId("operation-card-rock-scissor-paper")).toContainText("Four round values when installed");
  const exactCard = page.getByTestId("strategy-card-exact-text-response");
  await expect(exactCard.getByText("prometheus.policy.exact-text")).not.toBeVisible();
  await page.getByTestId("strategy-card-exact-text-response").locator("summary").click();
  await expect(exactCard.getByText("prometheus.policy.exact-text")).toBeVisible();

  await page.getByTestId("step-target-review").click();
  const represented = JSON.parse(await page.getByTestId("canonical-json-editor").inputValue());
  expect(represented.states).toHaveLength(originalStates.length);
  expect(represented.transitions).toEqual(originalTransitions);
  expect(represented.interaction.supportedObservations).toContain("obs.emotion.face");
  expect(represented.states.find((state) => state.id === "conversation").policy.kind).toBe("prometheus.policy.exact-text");

  await page.getByTestId("step-target-capabilities").click();
  await facialCard.getByRole("button", { name: "Use in Interaction" }).click();
  await expect(page.getByTestId("step-panel-interaction")).toBeVisible();
  await page.getByTestId("step-target-capabilities").click();
  await attach(page.getByTestId("step-panel-capabilities"), testInfo, "v2-capabilities-exact-rps-light");
  await assertNoOverflow(page);
  expect(errors).toEqual([]);
});

test("Interaction authors one event-condition-effect rule path, destinations, finish, and keyboard order", async ({ page }, testInfo) => {
  const errors = collectPageErrors(page);
  const definition = JSON.parse(readFileSync(resolve("src/test/resources/agent-definitions/valid/composite-flow.json"), "utf8"));
  definition.key = "designer.interaction_acceptance";
  definition.metadata.displayName = "Interaction acceptance agent";
  definition.transitions = [];
  const conversation = definition.states.find((state) => state.id === "conversation");
  conversation.policy = {
    kind: "prometheus.policy.prompt", version: 1,
    config: { responsePrompt: { sections: [] }, consumedObservations: [], emittedModalities: [] },
  };
  const scenario = await installDesignerApiMock(page, { definition });
  await openFixture(page, definition.key);
  await page.getByTestId("step-target-interaction").click();
  const main = page.getByTestId("situation-card-conversation");
  await main.getByLabel("When").selectOption("obs.social.context");
  await main.getByRole("button", { name: "Add interaction rule" }).click();
  let firstRule = main.locator(".interaction-rule-card").first();
  await firstRule.getByRole("button", { name: "Add condition" }).click();
  await firstRule.getByLabel("Decision criterion").fill("The person needs a clarification before continuing.");
  await firstRule.getByRole("button", { name: "Add positive example" }).click();
  await firstRule.getByLabel("Positive example").fill("They explicitly say that the instruction is unclear.");
  await firstRule.getByLabel("Effect type").selectOption("prometheus.action.prompt-behaviour");
  await firstRule.getByRole("button", { name: "Add effect" }).click();
  await firstRule.getByLabel("Response guidance").fill("Acknowledge the uncertainty and ask one concise clarifying question.");
  await firstRule.getByLabel("New destination situation").fill("Clarification");
  await firstRule.getByRole("button", { name: "Create and continue there" }).click();
  await expect(page.locator('input.situation-name-input[value="Clarification"]')).toBeVisible();

  await main.getByRole("button", { name: "Add interaction rule" }).click();
  const secondRule = main.locator(".interaction-rule-card").nth(1);
  await secondRule.getByLabel("Rule continuation").selectOption("finish");
  const moveEarlier = secondRule.getByRole("button", { name: "Move rule earlier" });
  await moveEarlier.focus();
  await expect(moveEarlier).toBeFocused();
  await moveEarlier.press("Enter");
  await expect(main.locator(".interaction-rule-card").first().getByLabel("Rule continuation")).toHaveValue("finish");
  await expect(page.getByText("prometheus.decision.prompt")).not.toBeVisible();
  await expect(page.getByRole("heading", { name: "Meaning-based condition" })).toBeVisible();
  await expect(page.getByTestId("derived-flow-overview")).toContainText("Clarification");
  await attach(page.getByTestId("step-panel-interaction"), testInfo, "v2-interaction-unified-rule-light");
  await assertNoOverflow(page);

  await page.getByTestId("step-target-review").click();
  const represented = JSON.parse(await page.getByTestId("canonical-json-editor").inputValue());
  expect(represented.transitions).toHaveLength(2);
  expect(represented.transitions.some((transition) => transition.targetStateId === "done")).toBe(true);
  expect(represented.transitions.some((transition) => transition.decisions.some((item) => item.kind === "prometheus.decision.prompt")
    && transition.actions.some((item) => item.kind === "prometheus.action.prompt-behaviour"))).toBe(true);
  expect(scenario.definition.transitions).toEqual([]);
  expect(errors).toEqual([]);
});

test("Interaction derives branch, cycle, and final storyboards without editing canonical topology", async ({ browser }) => {
  const fixtures = [
    ["core/role_clarification_guessing_game/revision-1.json", "Valerian Core guessing game - Valerian guesses", "Valerian Core guessing game - user guesses"],
    ["core/rock_scissor_paper/revision-1.json", "Valerian Core RPS Reveal Sign", "Valerian Core RPS Round Result"],
    ["usecases/healthcare/therapy_appointment_reminder_intro/revision-1.json", "Valerian Use Cases Healthcare therapy reminder introduction", "Valerian Use Cases Healthcare therapy reminder use case"],
  ];
  for (const [resource, firstSituation, secondSituation] of fixtures) {
    const definition = JSON.parse(readFileSync(resolve(CATALOG_ROOT, resource), "utf8"));
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = collectPageErrors(page);
    await installDesignerApiMock(page, { definition });
    await openFixture(page, definition.key);
    await page.getByTestId("step-target-interaction").click();
    await expect(page.locator(`input.situation-name-input[value="${firstSituation}"]`)).toBeVisible();
    await expect(page.locator(`input.situation-name-input[value="${secondSituation}"]`)).toBeVisible();
    await expect(page.getByTestId("derived-flow-overview")).toBeVisible();
    await expect(page.getByTestId("dirty-state")).toHaveText("Saved draft");
    await page.getByTestId("step-target-review").click();
    expect(JSON.parse(await page.getByTestId("canonical-json-editor").inputValue())).toEqual(definition);
    expect(errors).toEqual([]);
    await context.close();
  }
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

  await page.getByTestId("step-target-brief").click();
  await attach(page, testInfo, "v2-brief-dark-mobile");
  await assertNoOverflow(page);
  await page.getByTestId("step-target-capabilities").click();
  await attach(page, testInfo, "v2-capabilities-dark-mobile");
  await assertNoOverflow(page);
  await page.getByTestId("step-target-interaction").click();
  await expect(page.getByTestId("interaction-authoring")).toBeVisible();
  await page.getByTestId("interaction-authoring").getByRole("button", { name: "Add interaction rule" }).last().focus();
  await expect(page.getByTestId("interaction-authoring").getByRole("button", { name: "Add interaction rule" }).last()).toBeFocused();
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
