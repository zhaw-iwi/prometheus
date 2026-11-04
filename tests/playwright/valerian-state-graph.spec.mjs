import { test, expect } from "./fixtures/offline-ui-assets.mjs";
import { small, coordination, nested } from "./fixtures/state-graphs.mjs";

async function viewer(page, graph, theme = "light") {
  // Test the production renderer/styles without a backend or inference service.
  await page.goto("/valerian/state-view-window.html");
  await page.setContent(`<html data-theme="${theme}"><head><style>
    body { margin:20px; font:14px system-ui; background:${theme === "dark" ? "#121b1e" : "#fff"};
      --ink:${theme === "dark" ? "#f3f7f6" : "#172f37"}; --demo-panel:${theme === "dark" ? "#121b1e" : "#fff"}; color:var(--ink); }
    #graph { max-width:1000px; margin:auto; }
  </style></head><body><div id="graph" class="state-graph"></div></body></html>`);
  await page.addStyleTag({ url: "/valerian/state-graph.css" });
  await page.addScriptTag({ url: "/valerian/state-graph.js" });
  await page.evaluate((graph) => {
    window.viewer = PrometheusStateGraph.create(
      document.querySelector("#graph"),
    );
    window.viewer.setGraph(graph);
  }, graph);
}

for (const theme of ["light", "dark"]) {
  test(`state graph routing overview and transition inspection in ${theme}`, async ({
    page,
  }) => {
    await viewer(page, coordination, theme);
    await expect(page.locator(".graph-node")).toHaveCount(10);
    await expect(page.locator(".graph-edge")).toHaveCount(18);
    await expect(page.locator(".graph-summary")).toHaveText(
      "10 states · 47 transitions",
    );
    await expect(page.locator("#graph")).toHaveScreenshot(
      `coordination-${theme}.png`,
    );
    const positions = await page
      .locator(".graph-node")
      .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("transform")));
    await page.evaluate(() => window.viewer.setActive(["reflection"]));
    await expect(page.locator(".graph-node.is-current")).toHaveAttribute(
      "data-node-id",
      "reflection",
    );
    expect(
      await page
        .locator(".graph-node")
        .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("transform"))),
    ).toEqual(positions);
    await page
      .getByRole("button", {
        name: "Coordination execution to Coordination routing, 5 transitions",
        exact: true,
      })
      .focus();
    await page.keyboard.press("Enter");
    await expect(page.locator(".graph-details .graph-edge-title")).toHaveCount(
      5,
    );
    await expect(page.locator(".graph-details")).toContainText(
      "First matching outgoing transition wins",
    );
    await expect(page.locator(".graph-details")).toContainText("ExampleAction");
    const canvas = page.locator("svg"),
      before = await canvas.getAttribute("viewBox");
    await page.getByRole("button", { name: "Zoom in", exact: true }).click();
    expect(await canvas.getAttribute("viewBox")).not.toBe(before);
    await page.getByRole("button", { name: "Fit graph", exact: true }).click();
    await expect(canvas).toHaveAttribute("viewBox", before);
    await canvas.focus();
    await page.keyboard.press("ArrowRight");
    expect(await canvas.getAttribute("viewBox")).not.toBe(before);
    await page.getByRole("button", { name: "Current", exact: true }).click();
    await expect(canvas).not.toHaveAttribute("viewBox", before);
  });
}

test("state graph nested/shared regions, list search and narrow layout", async ({
  page,
}) => {
  await viewer(page, nested);
  await expect(page.locator(".graph-node.is-current")).toHaveCount(3);
  await expect(page.locator("#graph")).toHaveScreenshot("nested-expanded.png");
  await page.getByRole("button", { name: "State: Outer", exact: true }).click();
  await page.getByRole("button", { name: "Collapse inner states" }).click();
  await expect(page.locator(".graph-node")).toHaveCount(3);
  await expect(
    page.locator('.graph-node[data-node-id="shared"]'),
  ).toBeVisible();
  await page.getByRole("button", { name: "Expand inner states" }).click();
  await expect(page.locator(".graph-node")).toHaveCount(5);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("#graph")).toHaveScreenshot("nested-narrow-graph.png");
  await page.getByRole("button", { name: "List", exact: true }).click();
  await page.getByRole("searchbox", { name: "Find a state" }).fill("Duplicate");
  await expect(page.locator(".graph-list-node:visible")).toHaveCount(2);
  await page.locator('.graph-list-node[data-node-id="shared"]').click();
  await expect(page.locator(".graph-details")).toContainText(
    "Shared by: Outer, Other owner",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("#graph")).toHaveScreenshot(
    "nested-narrow-list.png",
  );
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(
    390,
  );
});

test("state graph final state, self loop and unconditional transition", async ({
  page,
}) => {
  await viewer(page, small);
  await expect(
    page.locator('.graph-node[data-node-id="finish"] .graph-inner-border'),
  ).toHaveCount(1);
  await expect(page.locator("#graph")).toHaveScreenshot("small.png");
  await page
    .getByRole("button", { name: "Execution to Finished, #1", exact: true })
    .focus();
  await page.keyboard.press("Enter");
  await expect(page.locator(".graph-details")).toContainText("Unconditional");
  await page
    .getByRole("button", {
      name: "Conversation to Conversation, #2",
      exact: true,
    })
    .focus();
  await page.keyboard.press("Enter");
  await expect(page.locator(".graph-details")).toContainText(
    "#2: Conversation → Conversation",
  );
});
