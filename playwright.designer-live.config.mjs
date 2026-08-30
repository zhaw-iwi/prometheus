import { defineConfig } from "@playwright/test";

const port = Number(process.env.PROMETHEUS_DESIGNER_LIVE_PORT || 18083);

export default defineConfig({
  testDir: "./tests/playwright",
  testMatch: "valerian-designer-live.spec.mjs",
  outputDir: "test-results/designer-live",
  globalSetup: "./tests/playwright/support/designer-live-global-setup.mjs",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    viewport: { width: 1440, height: 1000 },
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
});
