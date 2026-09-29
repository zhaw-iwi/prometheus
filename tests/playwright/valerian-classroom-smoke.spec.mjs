import { test, expect } from "@playwright/test";

// Real HTTP, MySQL and native EventSource. Run through the isolated acceptance runner.
test("eight classroom groups retain responsive, isolated cockpits", async ({ browser, request, baseURL }, info) => {
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== "true",
    "Use the isolated acceptance runner.");
  test.setTimeout(180_000);
  const admin = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const groups = [];
  const failures = [];
  const key = "core.facial_expression_sensitivity";
  const prefix = Math.random().toString(36).slice(2, 6).toUpperCase();

  async function create(page) {
    await page.getByTestId("agent-type-select").selectOption(key);
    const pending = page.waitForResponse(r => r.url().endsWith("/demo/agents") && r.request().method() === "POST");
    await page.getByTestId("create-agent-instance").click();
    const response = await pending;
    expect(response.status()).toBe(201);
    const agent = await response.json();
    await expect(page.getByTestId("agent-connection-state")).toContainText(`Selected ${agent.id}`);
    return agent;
  }
  async function connected(page, id) {
    await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${id}`);
    await expect(page.locator("#behaviour_status")).toHaveText("Behaviour Live");
    await expect.poll(() => page.evaluate(() => window.__classroomStreams.filter(s => s.readyState === EventSource.OPEN).length)).toBe(2);
  }
  async function released() {
    await expect.poll(async () => (await request.get("/__live-fixture/pool")).json())
      .toEqual({ active: 0, waiting: 0, maximum: 10 });
  }
  try {
    // Leave earlier groups connected while later groups log in and create instances.
    for (let index = 0; index < 8; index++) {
      const code = `${prefix}${index}`;
      const response = await request.post("/admin/access-codes", { headers: admin, data: { code, enabled: true } });
      expect(response.status()).toBe(201);
      const access = await response.json();
      expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, {
        headers: admin, data: { agentTypeKeys: [key] },
      })).ok()).toBe(true);
      const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1000 } });
      const group = { context, code, agents: [], headers: { "X-Prometheus-Access-Code": code }, text: `Classroom group ${index} is ready.` };
      groups.push(group);
      // Observe native transports without replacing their network or delivery behaviour.
      await context.addInitScript(() => {
        window.__classroomStreams = [];
        const NativeEventSource = window.EventSource;
        window.EventSource = class extends NativeEventSource {
          constructor(...args) { super(...args); window.__classroomStreams.push(this); }
        };
      });
      const page = group.page = await context.newPage();
      page.on("response", response => {
        if (response.url().startsWith(baseURL) && response.status() >= 500)
          failures.push(`${response.status()} ${new URL(response.url()).pathname}`);
      });
      await page.goto("/valerian/");
      await page.getByTestId("access-code-input").fill(code);
      await page.getByTestId("submit-access-code").click();
      await page.locator("#open_diagnostics").click();
      group.agents.push(await create(page));
      await page.getByTestId("connect-agent").click();
      await connected(page, group.agents[0].id);
      await page.keyboard.press("Escape");
    }
    await released();
    // All sixteen streams remain open while every group sends a turn.
    await Promise.all(groups.map(async ({ page, text, headers, agents }) => {
      const assistants = page.getByTestId("message-list").locator(".demo-message.assistant");
      const before = await assistants.count();
      await page.getByTestId("text-input").fill(text);
      await page.getByTestId("send-text").click();
      await expect(assistants).toHaveCount(before + 1);
      const history = await (await request.get(`/demo/agents/${agents[0].id}/eventhistory`, { headers })).json();
      expect(history.filter(e => e.type === "obs.user_utterance").map(e => e.payload)).toEqual([text]);
    }));
    await released();
    const first = groups[0], page = first.page, original = first.agents[0];
    expect((await request.get(`/demo/agents/${original.id}/eventhistory`, { headers: groups[1].headers })).status()).toBe(404);
    await page.locator("#open_diagnostics").click();
    await page.getByTestId("connect-agent").click();
    await expect(page.getByTestId("message-list").locator(".demo-message")).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => window.__classroomStreams.filter(s => s.readyState !== EventSource.CLOSED).length)).toBe(0);
    await page.getByTestId("connect-agent").click();
    await connected(page, original.id);
    await expect(page.getByTestId("message-list")).toContainText(first.text);
    first.agents.push(await create(page));
    await expect(page.getByTestId("message-list").locator(".demo-message")).toHaveCount(0);
    await page.getByTestId("connect-agent").click();
    await connected(page, first.agents[1].id);
    await expect(page.getByTestId("message-list")).not.toContainText(first.text);
    await page.getByTestId("agent-select").selectOption(original.id);
    await expect(page.getByTestId("agent-connection-state")).toContainText(`Selected ${original.id}`);
    await page.getByTestId("connect-agent").click();
    await connected(page, original.id);
    await expect(page.getByTestId("message-list")).toContainText(first.text);
    await page.goto(`/valerian/?agentId=${original.id}`);
    await connected(page, original.id);
    await expect(page.getByTestId("message-list")).toContainText(first.text);
    await released();
    expect(failures).toEqual([]);
    await page.screenshot({ path: info.outputPath("classroom-desktop.png"), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator('[data-column-panel="interaction"]').screenshot({ path: info.outputPath("classroom-mobile-conversation.png") });
  } finally {
    await Promise.all(groups.map(group => group.context.close()));
    for (const group of groups) for (const agent of group.agents)
      expect((await request.delete(`/demo/agents/${agent.id}`, { headers: group.headers })).status()).toBe(204);
  }
});
