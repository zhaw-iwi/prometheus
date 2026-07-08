import { expect, test } from "@playwright/test";

const ACCESS_CODE = "DEV42";
const AGENT_ID = "22222222-2222-2222-2222-222222222222";
const AGENT_TYPE = "core.rock_scissor_paper";

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

test("API Workbench executes scoped lifecycle requests and extracts session values", async ({ page }) => {
  await page.route("**/demo/session", async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().postDataJSON()).toEqual({ accessCode: ACCESS_CODE });
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        accessCode: ACCESS_CODE,
        agentTypes: [
          { key: AGENT_TYPE, displayName: "Rock Scissor Paper", packagePath: ["core"] },
        ],
        agents: [],
      }),
    });
  });
  await page.route("**/demo/agents", async (route) => {
    if (route.request().method() !== "POST") {
      await route.fallback();
      return;
    }
    expect(route.request().headers()["x-prometheus-access-code"]).toBe(ACCESS_CODE);
    expect(route.request().postDataJSON()).toEqual({ agentDefinitionKey: AGENT_TYPE });
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify(agentInfo()),
    });
  });
  await page.route(`**/demo/agents/${AGENT_ID}/info`, async (route) => {
    expect(route.request().headers()["x-prometheus-access-code"]).toBe(ACCESS_CODE);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(agentInfo()),
    });
  });

  await page.goto("/apiworkbench/index.html");
  await page.getByTestId("access-code-input").fill(ACCESS_CODE);
  await page.getByTestId("send-request").click();

  await expect(page.getByTestId("request-status")).toHaveText("200 OK");
  await expect(page.getByTestId("http-response-preview")).toContainText("agentTypes");
  await expect(page.getByTestId("agent-definition-key-input")).toHaveValue(AGENT_TYPE);

  await page.getByTestId("lifecycle-step-demo-agent-create").click();
  await page.getByTestId("send-request").click();
  await expect(page.getByTestId("request-status")).toHaveText("201 Created");
  await expect(page.getByTestId("agent-id-input")).toHaveValue(AGENT_ID);
  await expect(page.getByTestId("profile-preview")).toContainText("obs.hand.sign");

  await page.getByTestId("lifecycle-step-demo-agent-info").click();
  await page.getByTestId("send-request").click();
  await expect(page.getByTestId("request-status")).toHaveText("200 OK");
  await expect(page.getByTestId("profile-preview")).toContainText("motion.handSign");
});

test("API Workbench reports missing variables before sending", async ({ page }) => {
  await page.goto("/apiworkbench/index.html");

  await page.getByTestId("endpoint-search").fill("acknowledge");
  await page.getByTestId("endpoint-demo-agent-acknowledge").click();
  await page.getByTestId("send-request").click();

  await expect(page.getByTestId("request-status")).toHaveText("Error");
  await expect(page.getByTestId("http-response-preview")).toContainText("Missing variable");
});

function agentInfo() {
  return {
    id: AGENT_ID,
    name: "Rock Scissor Paper",
    description: "Deterministic API Workbench test agent.",
    active: true,
    languageCode: "en",
    interactionProfile: {
      supportedObservations: ["obs.user_utterance", "obs.hand.sign"],
      supportedBehaviourModalities: ["speech", "motion.handSign", "display"],
      profileTags: ["demo.valerian.rps"],
    },
  };
}
