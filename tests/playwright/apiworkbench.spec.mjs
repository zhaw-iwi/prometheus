import { expect, test } from "@playwright/test";

const ACCESS_CODE = "DEV42";
const AGENT_ID = "22222222-2222-2222-2222-222222222222";

test("API Workbench prepares endpoint templates and snippets", async ({ page }) => {
  await page.goto("/apiworkbench/index.html");

  await expect(page.getByTestId("apiworkbench-shell")).toBeVisible();
  await expect(page.getByTestId("apiworkbench-page-subtitle")).toHaveText("PROMETHEUS API Workbench");
  await expect(page.getByTestId("selected-endpoint-name")).toHaveText("/demo/session");
  await expect(page.getByTestId("snippet-output")).toContainText("fetch");

  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("agent-id-input").fill(AGENT_ID);
  await page.getByTestId("endpoint-search").fill("acknowledge");
  await expect(page.getByTestId("endpoint-demo-agent-acknowledge")).toBeVisible();
  await page.getByTestId("endpoint-demo-agent-acknowledge").click();

  await expect(page.getByTestId("selected-method")).toHaveText("POST");
  await expect(page.getByTestId("selected-endpoint-name")).toHaveText("/demo/agents/{agentId}/acknowledge");
  await expect(page.getByTestId("resolved-url")).toContainText(`/demo/agents/${AGENT_ID}/acknowledge?profile=full_plan`);
  await expect(page.getByTestId("request-headers")).toContainText("X-Prometheus-Access-Code");
  await expect(page.getByTestId("request-headers")).toContainText(ACCESS_CODE);
  await expect(page.getByTestId("request-body-editor")).toHaveValue(/obs\.user_utterance/);
  await expect(page.getByTestId("snippet-output")).toContainText("JSON.stringify");

  await page.getByTestId("copy-curl").click();
  await expect(page.getByTestId("snippet-output")).toContainText("curl -i -X POST");
  await expect(page.getByTestId("snippet-output")).toContainText(`/demo/agents/${AGENT_ID}/acknowledge?profile=full_plan`);
});

test("API Workbench lifecycle selects SSE stream templates", async ({ page }) => {
  await page.goto("/apiworkbench/index.html");

  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("agent-id-input").fill(AGENT_ID);
  await page.getByTestId("lifecycle-step-demo-behaviour-stream").click();

  await expect(page.getByTestId("selected-endpoint-name")).toHaveText("/demo/agents/{agentId}/behaviour/stream");
  await expect(page.getByTestId("selected-method")).toHaveText("GET");
  await expect(page.getByTestId("resolved-url")).toContainText(`/demo/agents/${AGENT_ID}/behaviour/stream?accessCode=${ACCESS_CODE}`);
  await expect(page.getByTestId("copy-sse")).toBeEnabled();
  await expect(page.getByTestId("snippet-output")).toContainText("new EventSource");
  await expect(page.getByTestId("sse-response-preview")).toContainText("Prepared stream endpoint.");

  await page.setViewportSize({ width: 390, height: 900 });
  await expect(page.getByTestId("apiworkbench-shell")).toBeVisible();
  await expect(page.getByTestId("endpoint-list")).toBeVisible();
  await expect(page.getByTestId("snippet-output")).toBeVisible();
});
