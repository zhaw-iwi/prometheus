import { test, expect } from "./fixtures/offline-ui-assets.mjs";
import { small } from "./fixtures/state-graphs.mjs";

const ids = ["71717171-7171-4171-8171-717171717171", "72727272-7272-4272-8272-727272727272"];
const snapshot = { stateName: "Reception", innerNames: [], states: ["Reception", "Routing", "Rock-scissor-paper", "Guess the person", "Co-created task"] };

async function openDiagnostics(page) {
  await page.locator("#open_diagnostics").click();
  await page.getByTestId("diagnostics-drawer-tab").click();
}

async function setup(page, context, theme = "light", options = {}) {
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.addInitScript(theme => {
    localStorage.setItem("prometheus.valerian.theme", theme);
    window.__stateStreams = [];
    window.EventSource = class extends EventTarget {
      static OPEN = 1;
      static CLOSED = 2;
      constructor(url) {
        super(); this.url = url; this.readyState = 1;
        window.__stateStreams.push(this);
        setTimeout(() => this.dispatchEvent(new Event("open")), 0);
      }
      close() { this.readyState = 2; }
    };
  }, theme);
  const agents = ids.map((id, index) => ({ id, name: `Agent ${index + 1}`, active: true, languageCode: "en",
    interactionProfile: { supportedObservations: ["obs.user_utterance"], supportedBehaviourModalities: ["speech"], profileTags: [] } }));
  await context.route("**/demo/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let data;
    if (path === "/demo/session") data = { accessCode: "STATE", agentTypes: [], agents };
    else if (path.endsWith("/transcription/capabilities")) data = { schemaVersion: 1, sessionType: "transcription",
      model: "gpt-live-transcribe", capabilities: { assistantOutput: false, inputTranscription: true }, settings: [] };
    else if (path.endsWith("/info")) data = agents.find(a => path.includes(a.id));
    else if (path.endsWith("/state-graph") && options.graph) return options.graph(route);
    else if (path.endsWith("/state")) data = { name: path.includes(ids[1]) ? "Second agent" : "Reception", innerNames: [], activeStateIds: options.graph ? ["conversation"] : [] };
    else if (path.endsWith("/states")) data = path.includes(ids[1]) ? ["Second agent"] : snapshot.states;
    else if (path.endsWith("/eventhistory") || path.endsWith("/storage")) data = [];
    else return route.fulfill({ status: 404, body: "" });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(data) });
  });
  await page.goto(`/valerian/?agentId=${ids[0]}`);
  await page.getByTestId("access-code-input").fill("STATE");
  await page.getByTestId("submit-access-code").click();
  await expect(page.getByTestId("agent-connection-state")).toContainText(`Connected to ${ids[0]}`);
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Reception");
  await openDiagnostics(page);
  return errors;
}

async function emit(page, data) {
  await page.evaluate(data => {
    const stream = window.__stateStreams.findLast(stream => stream.url.includes("/monitor/") && stream.readyState === 1);
    stream.dispatchEvent(new MessageEvent("snapshot", { data: JSON.stringify(data) }));
  }, data);
}

for (const theme of ["light", "dark"]) {
  test(`state view floats, moves, maximises and closes in ${theme} mode`, async ({ page, context }, info) => {
    const errors = await setup(page, context, theme);
    await page.getByRole("button", { name: "Float state view", exact: true }).click();
    const panel = page.locator("#state_view");
    await expect(panel).toHaveAttribute("data-state-mode", "floating");
    await expect(page.locator("#diagnostics_drawer")).toBeHidden();
    await page.getByTestId("text-input").fill("The cockpit stays interactive.");
    const before = await panel.boundingBox();
    const handle = page.locator("[data-state-drag]");
    const box = await handle.boundingBox();
    await page.mouse.move(box.x + 10, box.y + 10);
    await page.mouse.down();
    await page.mouse.move(box.x - 190, box.y + 110, { steps: 5 });
    await page.mouse.up();
    const moved = await panel.boundingBox();
    expect(moved.x).toBeLessThan(before.x - 100);
    expect(moved.y).toBeGreaterThan(before.y + 50);
    await handle.focus();
    await page.keyboard.press("ArrowLeft");
    expect((await panel.boundingBox()).x).toBe(moved.x - 20);
    await emit(page, { ...snapshot, stateName: "Rock-scissor-paper", innerNames: ["Round"] });
    await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Round");
    await expect(panel.locator("li").filter({ hasText: "Rock-scissor-paper" })).toContainText("current");
    await page.screenshot({ path: info.outputPath(`floating-${theme}.png`) });
    await page.getByRole("button", { name: "Maximise state view" }).click();
    expect((await panel.boundingBox()).width).toBe(1424);
    await page.screenshot({ path: info.outputPath(`maximised-${theme}.png`) });
    await page.getByRole("button", { name: "Restore state view" }).click();
    expect((await panel.boundingBox()).x).toBe(moved.x - 20);
    await page.getByRole("button", { name: "Close state view", exact: true }).click();
    await expect(panel).toBeHidden();
    await expect(page.getByTestId("text-input")).toHaveValue("The cockpit stays interactive.");
    await openDiagnostics(page);
    await expect(panel).toBeVisible();
    expect(await page.evaluate(() => window.__stateStreams.length)).toBe(2);
    expect(errors).toEqual([]);
  });
}

test("state view popup follows transitions, theme, switch, disconnect and native close", async ({ page, context }, info) => {
  const errors = await setup(page, context);
  const popupPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Open state view in separate window" }).click();
  const popup = await popupPromise;
  await expect(popup.locator("#state_view")).toBeVisible();
  await expect(popup.locator(".state-view-header")).toHaveCSS("display", "flex");
  await popup.evaluate(() => document.fonts.ready);
  await expect(page.locator("#state_view")).toHaveCount(0);
  expect(await popup.locator('script[src]').count()).toBe(0);
  await emit(page, { ...snapshot, stateName: "Guess the person" });
  await expect(popup.getByTestId("diagnostics-current-state")).toHaveText("Guess the person");
  await page.evaluate(() => setTheme("dark"));
  await expect(popup.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(popup.getByRole("button", { name: "Close state view", exact: true })).toHaveCSS("color", "rgb(243, 247, 246)");
  await popup.screenshot({ path: info.outputPath("popup-dark.png") });
  expect(await page.evaluate(() => window.__stateStreams.length)).toBe(2);
  await page.locator("#open_diagnostics").click();
  await page.getByTestId("agent-select").selectOption(ids[1]);
  await expect(popup.getByTestId("diagnostics-current-state")).toHaveText("Unknown");
  await expect(popup.getByTestId("diagnostics-state-list")).toHaveText("No states available.");
  await page.getByTestId("connect-agent").click();
  await expect(popup.getByTestId("diagnostics-current-state")).toHaveText("Second agent");
  await page.getByTestId("connect-agent").click();
  await expect(popup.getByTestId("diagnostics-current-state")).toHaveText("Unknown");
  await popup.close();
  await page.getByTestId("diagnostics-drawer-tab").click();
  await expect(page.locator("#state_view")).toHaveAttribute("data-state-mode", "docked");
  await page.getByRole("button", { name: "Float state view", exact: true }).click();
  await page.getByRole("button", { name: "Dock state view", exact: true }).click();
  await expect(page.getByTestId("diagnostics-drawer-tab")).toHaveClass(/active/);
  const secondPromise = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Open state view in separate window" }).click();
  const second = await secondPromise;
  await expect(page.locator("#diagnostics_drawer")).toBeHidden();
  await page.getByTestId("clear-access-code").click();
  await expect.poll(() => second.isClosed()).toBe(true);
  await expect(page.getByTestId("access-screen")).toBeVisible();
  expect(errors).toEqual([]);
});

test("state view popup closes when the cockpit navigates away", async ({ page, context }) => {
  await setup(page, context);
  const pending = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Open state view in separate window" }).click();
  const popup = await pending;
  await page.goto("about:blank");
  await expect.poll(() => popup.isClosed()).toBe(true);
});

test("state view handles blocked popups and stays reachable on small screens", async ({ page, context }, info) => {
  await setup(page, context, "dark");
  await page.evaluate(() => { window.open = () => null; });
  await page.getByRole("button", { name: "Open state view in separate window" }).click();
  await expect(page.locator("[data-state-status]")).toContainText("Window blocked");
  await page.getByRole("button", { name: "Float state view", exact: true }).click();
  await expect(page.locator("#diagnostics_drawer")).toBeHidden();
  await page.setViewportSize({ width: 390, height: 844 });
  await emit(page, { stateName: "Nested", innerNames: ["<script>literal label</script>"],
    states: ["Nested", "<script>literal label</script>", ...Array.from({ length: 30 }, (_, i) => `A long state description ${i}`)] });
  const panel = page.locator("#state_view");
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("<script>literal label</script>");
  const box = await panel.boundingBox();
  expect(box.x).toBeGreaterThanOrEqual(8);
  expect(box.x + box.width).toBeLessThanOrEqual(382);
  expect(box.y + box.height).toBeLessThanOrEqual(836);
  expect(await panel.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath("floating-mobile-dark.png") });
  await page.getByRole("button", { name: "Maximise state view" }).click();
  await page.screenshot({ path: info.outputPath("maximised-mobile-dark.png") });
  await page.keyboard.press("Escape");
  await expect(panel).toBeHidden();
});

test("state view tracks a persisted transition through real local HTTP and monitor SSE", async ({ page, request }, info) => {
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || !process.env.PROMETHEUS_LIVE_EXPECT_ENABLED, "Use the isolated acceptance runner.");
  const admin = { "X-Prometheus-Admin-Token": process.env.PROMETHEUS_ADMIN_TOKEN };
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const created = await request.post("/admin/access-codes", { headers: admin, data: { code, enabled: true } });
  expect(created.status()).toBe(201);
  const access = await created.json();
  expect((await request.put(`/admin/access-codes/${access.id}/agent-types`, { headers: admin,
    data: { agentTypeKeys: ["core.rock_scissor_paper"] } })).ok()).toBe(true);
  const headers = { "X-Prometheus-Access-Code": code };
  const creation = await request.post("/demo/agents", { headers, data: { agentDefinitionKey: "core.rock_scissor_paper" } });
  expect(creation.status()).toBe(201);
  const agent = await creation.json();
  const graphReads = [], streams = [];
  page.on("request", r => {
    const path = new URL(r.url()).pathname;
    if (path.endsWith("/state-graph")) graphReads.push(path);
    if (path.includes("/monitor/")) streams.push(path);
  });
  try {
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId("access-code-input").fill(code);
    await page.getByTestId("submit-access-code").click();
    await page.waitForFunction(() => state.monitorReady);
    const initial = await page.getByTestId("diagnostics-current-state").textContent();
    await openDiagnostics(page);
    const graph = await (await request.get(`/demo/agents/${agent.id}/state-graph`, { headers })).json();
    await expect(page.locator(".graph-node")).toHaveCount(graph.nodes.length);
    const positions = await page.locator("svg .graph-node").evaluateAll(nodes => nodes.map(n => n.getAttribute("transform")));
    await page.getByRole("button", { name: "Float state view", exact: true }).click();
    const detailReads = [];
    page.on("request", r => { if (/\/(state|states)$/.test(new URL(r.url()).pathname)) detailReads.push(r.url()); });
    await page.getByTestId("text-input").fill("I am ready to start a round");
    // The floating graph can overlap the Send button; the normal keyboard path stays available.
    await page.getByTestId("text-input").press("Enter");
    await expect(page.getByTestId("diagnostics-current-state")).not.toHaveText(initial);
    const persisted = await (await request.get(`/demo/agents/${agent.id}/state`, { headers })).json();
    const current = persisted.innerNames?.at(-1) || persisted.innerName || persisted.name;
    await expect(page.getByTestId("diagnostics-current-state")).toHaveText(current);
    for (const id of persisted.activeStateIds) await expect(page.locator(`svg [data-node-id="${id}"]`)).toHaveClass(/is-current/);
    expect(await page.locator("svg .graph-node").evaluateAll(nodes => nodes.map(n => n.getAttribute("transform")))).toEqual(positions);
    await expect(page.locator("#diagnostics_state_list li").filter({ hasText: current })).toContainText("current");
    const pending = page.waitForEvent("popup");
    await page.getByRole("button", { name: "Open state view in separate window" }).click();
    const popup = await pending;
    await expect(popup.getByTestId("diagnostics-current-state")).toHaveText(current);
    await expect(popup.locator("#state_view")).toBeVisible();
    await expect(popup.locator(".graph-node")).toHaveCount(graph.nodes.length);
    await popup.getByRole("button", { name: "Zoom in", exact: true }).click();
    await popup.evaluate(() => document.fonts.ready);
    await popup.screenshot({ path: info.outputPath("real-db-transition-popup.png") });
    // Exercise the actual manual-sensor emitter, including its round capture ID.
    await page.evaluate(() => submitHandSign("rock"));
    await expect(popup.getByTestId("diagnostics-current-state")).toContainText("Round Result");
    const resultState = await (await request.get(`/demo/agents/${agent.id}/state`, { headers })).json();
    for (const id of resultState.activeStateIds) await expect(popup.locator(`svg [data-node-id="${id}"]`)).toHaveClass(/is-current/);
    const stored = Object.fromEntries((await (await request.get(`/demo/agents/${agent.id}/storage`, { headers })).json())
      .map(entry => [entry.key, JSON.parse(entry.value)]));
    expect(stored["core.rps.round"].phase).toBe("RESULT");
    expect(stored["core.rps.completed"]).toBe(1);
    expect(stored.rps_last_round.winner).toBe("draw");
    await page.screenshot({ path: info.outputPath("real-db-rps-result.png") });
    // Reset produces another committed monitor snapshot while the nodes live in the popup.
    expect((await request.delete(`/demo/agents/${agent.id}/reset`, { headers })).ok()).toBe(true);
    await expect(popup.getByTestId("diagnostics-current-state")).toHaveText(initial);
    expect(graphReads).toHaveLength(1);
    expect(streams).toHaveLength(1);
    expect(detailReads).toEqual([]);
    await popup.getByRole("button", { name: "Dock state view", exact: true }).click();
    await expect.poll(() => popup.isClosed()).toBe(true);
    await expect(page.getByTestId("diagnostics-current-state")).toHaveText(initial);
    // The cockpit reset clears all panels, so it must explicitly rehydrate the graph.
    await page.getByTestId("agent-drawer-tab").click();
    page.once("dialog", dialog => dialog.accept());
    await page.locator("#reset_agent").click();
    await expect.poll(() => graphReads.length).toBe(2);
    await expect(page.locator(".graph-node")).toHaveCount(graph.nodes.length);
    await expect(page.locator(`svg [data-node-id="${graph.initialStateId}"]`)).toHaveClass(/is-current/);
  } finally {
    await page.goto("about:blank");
    expect((await request.delete(`/demo/agents/${agent.id}`, { headers })).status()).toBe(204);
  }
});

test("state graph popup keeps one connection, rejects stale streams and reloads on agent switch", async ({ page, context }) => {
  const reads = [];
  const errors = await setup(page, context, "light", { graph: route => {
    reads.push(route.request().url());
    const graph = structuredClone(small);
    if (route.request().url().includes(ids[1])) graph.nodes[0].name = "Second graph";
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(graph) });
  } });
  await expect(page.locator(".graph-node")).toHaveCount(3);
  const pending = page.waitForEvent("popup");
  await page.getByRole("button", { name: "Open state view in separate window" }).click();
  const popup = await pending;
  await expect(popup.locator(".graph-node.is-current")).toHaveAttribute("data-node-id", "conversation");
  await page.evaluate(() => { window.__oldMonitor = window.__stateStreams.find(s => s.url.includes("/monitor/")); window.__oldMonitor.onerror(); });
  await expect.poll(() => page.evaluate(() => window.__stateStreams.filter(s => s.url.includes("/monitor/") && s.readyState === 1).length)).toBe(1);
  await emit(page, { ...snapshot, activeStateIds: ["execute"] });
  await expect(popup.locator(".graph-node.is-current")).toHaveAttribute("data-node-id", "execute");
  await page.evaluate(() => window.__oldMonitor.dispatchEvent(new MessageEvent("snapshot", { data: JSON.stringify({activeStateIds:["finish"]}) })));
  await expect(popup.locator(".graph-node.is-current")).toHaveAttribute("data-node-id", "execute");
  expect(reads).toHaveLength(1);
  await page.evaluate(() => setTheme("dark"));
  await expect(popup.locator("html")).toHaveAttribute("data-theme", "dark");
  await popup.getByRole("button", { name: "List", exact: true }).click();
  await expect(popup.locator(".graph-list-node:visible")).toHaveCount(3);
  await popup.getByRole("button", { name: "Graph", exact: true }).click();
  await popup.getByRole("button", { name: "Zoom in", exact: true }).click();
  await page.locator("#open_diagnostics").click();
  await page.getByTestId("agent-select").selectOption(ids[1]);
  await expect(popup.locator("#state_graph")).toBeHidden();
  await page.getByTestId("connect-agent").click();
  await expect(popup.getByRole("button", { name: "State: Second graph", exact: true })).toBeVisible();
  expect(reads).toHaveLength(2);
  await page.getByTestId("connect-agent").click();
  await expect(popup.locator("#state_graph")).toBeHidden();
  await page.getByTestId("connect-agent").click();
  await expect(popup.locator(".graph-node")).toHaveCount(3);
  expect(reads).toHaveLength(3);
  expect(await page.evaluate(() => window.__stateStreams.filter(s => s.readyState === 1).length)).toBe(2);
  await page.locator("#diagnostics_drawer .btn-close").click();
  await page.getByTestId("clear-access-code").click();
  await expect.poll(() => popup.isClosed()).toBe(true);
  expect(errors).toEqual([]);
});

test("state graph slow response does not block connection or restore a disconnected graph", async ({ page, context }) => {
  let release, started, finished;
  const pending = new Promise(resolve => { release = resolve; });
  const requested = new Promise(resolve => { started = resolve; });
  const completed = new Promise(resolve => { finished = resolve; });
  await setup(page, context, "light", { graph: async route => {
    started(); await pending;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify(small) }).catch(() => {});
    finished();
  } });
  await requested;
  await expect(page.getByTestId("send-text")).toBeEnabled();
  expect(await page.evaluate(() => window.__stateStreams.length)).toBe(2);
  await expect(page.locator("#state_graph_status")).toHaveText("Loading state graph…");
  await page.getByTestId("agent-drawer-tab").click();
  await page.getByTestId("connect-agent").click();
  release();
  await completed;
  await expect(page.locator("#state_graph_status")).toBeEmpty();
  await expect(page.locator("#state_graph")).toBeHidden();
  await expect(page.locator(".graph-node")).toHaveCount(0);
});

test("state graph late initial snapshot preserves newer monitor state", async ({ page, context }) => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  await setup(page, context, "light", { graph: async route => {
    await pending;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify(small) });
  } });
  await emit(page, { ...snapshot, activeStateIds: ["finish"] });
  release();
  await expect(page.locator(".graph-node.is-current")).toHaveAttribute("data-node-id", "finish");
});

test("state graph failure keeps the state-name fallback visible", async ({ page, context }) => {
  await setup(page, context);
  await expect(page.locator("#state_graph_status")).toContainText("State graph unavailable");
  await expect(page.getByTestId("diagnostics-state-list")).toBeVisible();
  await expect(page.getByTestId("diagnostics-current-state")).toHaveText("Reception");
});
